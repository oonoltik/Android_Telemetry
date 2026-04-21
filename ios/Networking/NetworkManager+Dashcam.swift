import Foundation

enum NetworkSendResult {
    case success
    case retryableFailure
    case permanentFailure
}

final class DashcamUploadSessionDelegate: NSObject, URLSessionDelegate {
    func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        DispatchQueue.main.async {
            DashcamBackgroundSessionBridge.shared.completionHandler?()
            DashcamBackgroundSessionBridge.shared.completionHandler = nil
        }
    }
}

extension NetworkManager {
    
    private static let dashcamUploadSessionDelegate = DashcamUploadSessionDelegate()

    private static let dashcamUploadSession: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 60
        config.timeoutIntervalForResource = 300
        config.waitsForConnectivity = true
        config.allowsExpensiveNetworkAccess = true
        config.allowsConstrainedNetworkAccess = true

        return URLSession(configuration: config)
    }()

    private func mimeType(for fileURL: URL) -> String {
        switch fileURL.pathExtension.lowercased() {
        case "mov":
            return "video/quicktime"
        case "mp4":
            return "video/mp4"
        default:
            return "application/octet-stream"
        }
    }

    private func makeCrashClipMultipartFile(
        videoSessionId: String,
        crashClipId: String,
        fileURL: URL,
        boundary: String
    ) throws -> URL {
        let tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("dashcam-crash-upload-\(UUID().uuidString).tmp")

        FileManager.default.createFile(atPath: tempURL.path, contents: nil)

        let writeHandle = try FileHandle(forWritingTo: tempURL)
        defer { try? writeHandle.close() }

        func writeString(_ string: String) throws {
            if let data = string.data(using: .utf8) {
                try writeHandle.write(contentsOf: data)
            }
        }

        try writeString("--\(boundary)\r\n")
        try writeString("Content-Disposition: form-data; name=\"video_session_id\"\r\n\r\n")
        try writeString("\(videoSessionId)\r\n")

        try writeString("--\(boundary)\r\n")
        try writeString("Content-Disposition: form-data; name=\"crash_clip_id\"\r\n\r\n")
        try writeString("\(crashClipId)\r\n")

        let filename = fileURL.lastPathComponent
        let mime = mimeType(for: fileURL)

        try writeString("--\(boundary)\r\n")
        try writeString("Content-Disposition: form-data; name=\"file\"; filename=\"\(filename)\"\r\n")
        try writeString("Content-Type: \(mime)\r\n\r\n")

        let readHandle = try FileHandle(forReadingFrom: fileURL)
        defer { try? readHandle.close() }

        while true {
            let chunk = try readHandle.read(upToCount: 1024 * 1024) ?? Data()
            if chunk.isEmpty { break }
            try writeHandle.write(contentsOf: chunk)
        }

        try writeString("\r\n")
        try writeString("--\(boundary)--\r\n")

        return tempURL
    }
    func startVideoSession(_ request: VideoSessionStartRequest) async throws {
        try await postDashcamJSON(path: "/video/session/start", body: request)
    }

    func stopVideoSession(_ request: VideoSessionStopRequest) async throws {
        try await postDashcamJSON(path: "/video/session/stop", body: request)
    }

    func postCrashClip(_ request: CrashClipEventRequest) async throws {
        try await postDashcamJSON(path: "/video/crash-clip", body: request)
    }
        
    
    
            
func uploadCrashClip(
    videoSessionId: String,
    crashClipId: String,
    fileURL: URL
) async throws {

    let baseURL = euBaseURL
    let url = baseURL.appendingPathComponent("video/crash-clip/upload")

    let deviceId = try resolveDashcamDeviceId(from: [
        "video_session_id": videoSessionId
    ])

    let bearer = try await ensureBearerWithFallback(deviceId: deviceId)

    let boundary = "Boundary-\(UUID().uuidString)"

    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.timeoutInterval = 60
    request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
    request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")

    print("🚀 uploadCrashClip START crashClipId=\(crashClipId)")
    print("📁 fileURL=\(fileURL.path)")

    let fileExists = FileManager.default.fileExists(atPath: fileURL.path)
    print("📁 file exists=\(fileExists)")

    if fileExists {
        do {
            let attrs = try FileManager.default.attributesOfItem(atPath: fileURL.path)
            let size = (attrs[.size] as? NSNumber)?.int64Value ?? -1
            print("📁 file size=\(size)")
        } catch {
            print("❌ file attrs error: \(error)")
        }
    } else {
        print("❌ FILE NOT FOUND → upload abort")
        throw URLError(.fileDoesNotExist)
    }

    let multipartFileURL = try makeCrashClipMultipartFile(
        videoSessionId: videoSessionId,
        crashClipId: crashClipId,
        fileURL: fileURL,
        boundary: boundary
    )
    defer { try? FileManager.default.removeItem(at: multipartFileURL) }

    do {
        let attrs = try FileManager.default.attributesOfItem(atPath: multipartFileURL.path)
        let size = (attrs[.size] as? NSNumber)?.int64Value ?? -1
        print("📦 multipart file ready, bytes=\(size)")
    } catch {
        print("❌ multipart attrs error: \(error)")
    }

    print("🌐 sending POST /video/crash-clip/upload ...")

    let (data, response): (Data, URLResponse)
    do {
        (data, response) = try await withCheckedThrowingContinuation { continuation in
            let task = Self.dashcamUploadSession.uploadTask(with: request, fromFile: multipartFileURL) { data, response, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }

                guard let response else {
                    continuation.resume(throwing: URLError(.badServerResponse))
                    return
                }

                continuation.resume(returning: (data ?? Data(), response))
            }
            task.resume()
        }
    } catch {
        print("❌ URLSession error: \(error)")
        throw error
    }

    guard let http = response as? HTTPURLResponse else {
        print("❌ No HTTP response")
        throw URLError(.badServerResponse)
    }

    let responseBody = String(data: data, encoding: .utf8) ?? ""

    print("🌐 upload response status=\(http.statusCode)")
    print("🌐 upload response body=\(responseBody)")

    if http.statusCode == 507 {
        throw NSError(
            domain: "CrashClipUpload",
            code: 507,
            userInfo: [NSLocalizedDescriptionKey: "server_storage_limit_reached"]
        )
    }

    guard (200...299).contains(http.statusCode) else {
        let backendMessage: String
        if let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let detail = object["detail"] as? String {
            backendMessage = detail
        } else {
            backendMessage = "bad_server_response"
        }

        throw NSError(
            domain: "CrashClipUpload",
            code: http.statusCode,
            userInfo: [NSLocalizedDescriptionKey: backendMessage]
        )
    }
}
    
    func uploadCrashClipResult(
        videoSessionId: String,
        crashClipId: String,
        fileURL: URL
    ) async -> NetworkSendResult {
        do {
            try await uploadCrashClip(
                videoSessionId: videoSessionId,
                crashClipId: crashClipId,
                fileURL: fileURL
            )
            return .success
        } catch {
            return classifyDashcamSendError(error)
        }
    }

    func postDashcamCameraLog(_ request: DashcamCameraLogRequest) async throws {
        try await postDashcamJSON(path: "/video/camera-log", body: request)
    }

    func postCrashLog(_ request: CrashLogRequest) async throws {
        try await postDashcamJSON(
            path: "/video/crash-log",
            body: request
        )
    }

    private func postDashcamJSON<T: Encodable>(path: String, body: T) async throws {
        let baseURL = euBaseURL
        let url = baseURL.appendingPathComponent(
            path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        )

        let deviceId = try resolveDashcamDeviceId(from: body)
        let bearer = try await ensureBearerWithFallback(deviceId: deviceId)

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 20
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONEncoder().encode(body)

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let http = response as? HTTPURLResponse else {
            throw NSError(
                domain: "DashcamHTTP",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "No HTTP response"]
            )
        }

        let responseBody = String(data: data, encoding: .utf8) ?? ""

        if !(200..<300).contains(http.statusCode) {
            print("❌ DASHCAM HTTP \(http.statusCode) path=\(path) body=\(responseBody)")
            throw NSError(
                domain: "DashcamHTTP",
                code: http.statusCode,
                userInfo: [
                    NSLocalizedDescriptionKey: responseBody.isEmpty
                        ? "HTTP \(http.statusCode)"
                        : responseBody
                ]
            )
        }

        print("✅ DASHCAM HTTP \(http.statusCode) path=\(path) body=\(responseBody)")
    }
    private func resolveDashcamDeviceId<T: Encodable>(from body: T) throws -> String {
        let data = try JSONEncoder().encode(body)

        if let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
           let deviceId = object["device_id"] as? String,
           !deviceId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return deviceId
        }

        if let stored = KeychainStore.shared.get("telemetry_device_id_v1"),
           let deviceId = String(data: stored, encoding: .utf8),
           !deviceId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return deviceId
        }

        throw URLError(.userAuthenticationRequired)
    }
    
    private func classifyDashcamSendError(_ error: Error) -> NetworkSendResult {
        if let urlError = error as? URLError {
            switch urlError.code {
            case .notConnectedToInternet,
                 .timedOut,
                 .networkConnectionLost,
                 .cannotFindHost,
                 .cannotConnectToHost,
                 .dnsLookupFailed,
                 .internationalRoamingOff,
                 .callIsActive,
                 .dataNotAllowed:
                return .retryableFailure
            default:
                return .permanentFailure
            }
        }

        let nsError = error as NSError

        if nsError.domain == "DashcamHTTP" || nsError.domain == "CrashClipUpload" {
            switch nsError.code {
            case 429:
                return .retryableFailure
            case 500...599:
                return .retryableFailure
            case 400, 401, 403, 404, 422, 507:
                return .permanentFailure
            default:
                return .permanentFailure
            }
        }

        return .retryableFailure
    }
    
    
}
