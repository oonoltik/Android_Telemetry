import Foundation
import DeviceCheck
import CryptoKit

final class AuthManager {
    static let shared = AuthManager()

    private let tokenKey = "auth_bearer_token"
    private let keyIdKey = "appattest_key_id"

    private let lock = NSLock()
    private var inFlight: Task<String, Error>?
    private let authFastFailTimeout: TimeInterval = 8.0

    private lazy var authFastFailSession: URLSession = {
        let cfg = URLSessionConfiguration.ephemeral
        cfg.waitsForConnectivity = false
        cfg.timeoutIntervalForRequest = authFastFailTimeout
        cfg.timeoutIntervalForResource = authFastFailTimeout
        cfg.httpMaximumConnectionsPerHost = 1
        cfg.allowsCellularAccess = true
        return URLSession(configuration: cfg)
    }()

    private init() {}

    // MARK: - Debug logging

    @inline(__always)
    private func debugAuthLog(_ message: @autoclosure () -> String) {
        #if DEBUG
        print(message())
        #endif
    }

    // MARK: - Public

    func currentToken() -> String? {
        guard let data = KeychainStore.shared.get(tokenKey),
              let s = String(data: data, encoding: .utf8),
              !s.isEmpty else { return nil }
        return s
    }

    var bearerToken: String? { currentToken() }

    func clearToken() {
        KeychainStore.shared.delete(tokenKey)
    }

    /// Ensure token exists (and refresh it if missing).
    /// The call is deduplicated: concurrent callers will await the same task.
    func ensureToken(baseURL: URL, deviceId: String) async throws -> String {
        let t0 = Date()

        if let t = currentToken() {
            debugAuthLog("[AUTH] cache HIT token len=\(t.count) dt=\(Date().timeIntervalSince(t0))s")
            return t
        }

        debugAuthLog("[AUTH] cache MISS token; base=\(baseURL.host ?? "?") deviceId=\(deviceId.prefix(8))...")

        lock.lock()
        if let task = inFlight {
            lock.unlock()
            debugAuthLog("[AUTH] await inFlight")
            return try await task.value
        }

        let task = Task<String, Error> {
            defer {
                self.lock.lock()
                self.inFlight = nil
                self.lock.unlock()
            }

            let token = try await self.registerAndGetToken(baseURL: baseURL, deviceId: deviceId)
            try KeychainStore.shared.set(Data(token.utf8), for: self.tokenKey)
            self.debugAuthLog("[AUTH] stored token len=\(token.count)")
            return token
        }

        inFlight = task
        lock.unlock()

        return try await task.value
    }

    func clearAllAuthState() {
        lock.lock()
        inFlight?.cancel()
        inFlight = nil
        lock.unlock()

        KeychainStore.shared.delete(tokenKey)
        KeychainStore.shared.delete(keyIdKey)
    }

    // MARK: - Core flow

    private func registerAndGetToken(baseURL: URL, deviceId: String) async throws -> String {
        guard DCAppAttestService.shared.isSupported else {
            #if targetEnvironment(simulator)
            debugAuthLog("[AUTH] App Attest unsupported in Simulator -> skip raw App Attest error")
            throw NSError(
                domain: "AuthManager",
                code: -1001,
                userInfo: [NSLocalizedDescriptionKey: "Simulator is not supported for secure device attestation. Use a real iPhone or iPad."]
            )
            #else
            debugAuthLog("[AUTH] App Attest unsupported on this device")
            throw NSError(
                domain: "AuthManager",
                code: -1002,
                userInfo: [NSLocalizedDescriptionKey: "This device does not support secure device attestation."]
            )
            #endif
        }

        debugAuthLog("[AUTH] start registerAndGetToken supported=\(DCAppAttestService.shared.isSupported)")
        KeychainStore.shared.delete(tokenKey)

        // 1) challenge
        let challengeResp = try await postJSON(
            url: baseURL.appendingPathComponent("auth/challenge"),
            json: ["device_id": deviceId],
            fastFail: true
        )

        let challengeId = try requireString(challengeResp, "challenge_id")
        let challengeB64 = try requireString(challengeResp, "challenge_b64")

        guard let challengeData = Data(base64Encoded: challengeB64) else {
            throw NSError(
                domain: "AuthManager",
                code: -2,
                userInfo: [NSLocalizedDescriptionKey: "Invalid challenge_b64"]
            )
        }

        debugAuthLog("[AUTH] got challenge id=\(challengeId.prefix(8))... bytes=\(challengeData.count)")

        // 2) keyId (persisted)
        var keyId: String
        if let existing = KeychainStore.shared.get(keyIdKey),
           let s = String(data: existing, encoding: .utf8),
           !s.isEmpty {
            keyId = s
            debugAuthLog("[AUTH] using existing keyId=\(keyId.prefix(8))...")
        } else {
            keyId = try await generateKeyId()
            try KeychainStore.shared.set(Data(keyId.utf8), for: keyIdKey)
            debugAuthLog("[AUTH] generated new keyId=\(keyId.prefix(8))...")
        }

        // 3) attestKey (with one retry on devicecheck error 2)
        let clientDataHash = Data(SHA256.hash(data: challengeData))
        debugAuthLog("[AUTH] clientDataHash.b64=\(clientDataHash.base64EncodedString())")
        debugAuthLog("[AUTH] clientDataHash.count=\(clientDataHash.count) (must be 32)")

        do {
            let attestationObject = try await attestKey(keyId: keyId, clientDataHash: clientDataHash)
            let attestationB64 = attestationObject.base64EncodedString()
            debugAuthLog("[AUTH] got attestation (\(attestationObject.count) bytes)")

            // 4) register => token
            let registerResp = try await postJSON(
                url: baseURL.appendingPathComponent("auth/register"),
                json: [
                    "device_id": deviceId,
                    "key_id": keyId,
                    "challenge_id": challengeId,
                    "attestation_object_b64": attestationB64
                ],
                fastFail: true
            )

            debugAuthLog("[AUTH] register OK")

            if let t = (registerResp["token"] as? String), !t.isEmpty { return t }
            if let t = (registerResp["access_token"] as? String), !t.isEmpty { return t }
            if let t = (registerResp["bearer"] as? String), !t.isEmpty { return t }
            if let t = (registerResp["jwt"] as? String), !t.isEmpty { return t }

            throw NSError(
                domain: "AuthManager",
                code: -3,
                userInfo: [NSLocalizedDescriptionKey: "No token in register response: \(registerResp)"]
            )

        } catch {
            let ns = error as NSError
            debugAuthLog("[AUTH] AppAttest error domain=\(ns.domain) code=\(ns.code) userInfo=\(ns.userInfo)")

            let isDeviceCheckCode2 =
                (ns.domain == "com.apple.devicecheck.error" || ns.domain == "com.apple.devicecheck")
                && ns.code == 2

            if isDeviceCheckCode2 {
                debugAuthLog("[AUTH] devicecheck error 2 -> deleting keyId and retrying once")
                KeychainStore.shared.delete(keyIdKey)

                let newKeyId = try await generateKeyId()
                try KeychainStore.shared.set(Data(newKeyId.utf8), for: keyIdKey)
                debugAuthLog("[AUTH] newKeyId=\(newKeyId.prefix(8))...")

                let attestationObject = try await attestKey(keyId: newKeyId, clientDataHash: clientDataHash)
                let attestationB64 = attestationObject.base64EncodedString()
                debugAuthLog("[AUTH] got attestation after retry (\(attestationObject.count) bytes)")

                let registerResp = try await postJSON(
                    url: baseURL.appendingPathComponent("auth/register"),
                    json: [
                        "device_id": deviceId,
                        "key_id": newKeyId,
                        "challenge_id": challengeId,
                        "attestation_object_b64": attestationB64
                    ],
                    fastFail: true
                )

                debugAuthLog("[AUTH] register OK after retry")

                if let t = (registerResp["token"] as? String), !t.isEmpty { return t }
                if let t = (registerResp["access_token"] as? String), !t.isEmpty { return t }
                if let t = (registerResp["bearer"] as? String), !t.isEmpty { return t }
                if let t = (registerResp["jwt"] as? String), !t.isEmpty { return t }

                throw NSError(
                    domain: "AuthManager",
                    code: -3,
                    userInfo: [
                        NSLocalizedDescriptionKey: "No token in register response after retry: \(registerResp)"
                    ]
                )
            }

            throw error
        }
    }

    // MARK: - DeviceCheck async helpers

    private func generateKeyId() async throws -> String {
        try await withCheckedThrowingContinuation { cont in
            DCAppAttestService.shared.generateKey { keyId, error in
                if let error = error {
                    cont.resume(throwing: error)
                    return
                }

                guard let keyId = keyId else {
                    cont.resume(throwing: NSError(
                        domain: "AuthManager",
                        code: -10,
                        userInfo: [NSLocalizedDescriptionKey: "generateKey returned nil"]
                    ))
                    return
                }

                cont.resume(returning: keyId)
            }
        }
    }

    private func attestKey(keyId: String, clientDataHash: Data) async throws -> Data {
        try await withCheckedThrowingContinuation { cont in
            DCAppAttestService.shared.attestKey(keyId, clientDataHash: clientDataHash) { data, error in
                if let error = error {
                    cont.resume(throwing: error)
                    return
                }

                guard let data = data else {
                    cont.resume(throwing: NSError(
                        domain: "AuthManager",
                        code: -11,
                        userInfo: [NSLocalizedDescriptionKey: "attestKey returned nil"]
                    ))
                    return
                }

                cont.resume(returning: data)
            }
        }
    }

    // MARK: - Networking helpers

    private func postJSON(url: URL, json: [String: Any], fastFail: Bool = false) async throws -> [String: Any] {
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.timeoutInterval = fastFail ? authFastFailTimeout : 20
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: json, options: [])

        let session = fastFail ? authFastFailSession : URLSession.shared
        let (data, resp) = try await session.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? -1
        let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]

        if !(200...299).contains(code) {
            let detail = obj["detail"] ?? String(data: data, encoding: .utf8) ?? ""
            throw NSError(
                domain: "AuthManager",
                code: code,
                userInfo: [NSLocalizedDescriptionKey: "HTTP \(code) from \(url.path): \(detail)"]
            )
        }

        return obj
    }

    private func requireString(_ dict: [String: Any], _ key: String) throws -> String {
        guard let s = dict[key] as? String, !s.isEmpty else {
            throw NSError(
                domain: "AuthManager",
                code: -20,
                userInfo: [NSLocalizedDescriptionKey: "Missing field '\(key)' in response: \(dict)"]
            )
        }
        return s
    }

    func resetAppAttestStateForDebug() {
        KeychainStore.shared.delete(keyIdKey)
    }
}
