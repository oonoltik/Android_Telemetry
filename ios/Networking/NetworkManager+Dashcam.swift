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
    
    private struct CrashClipUploadInitResponse: Decodable {
        let session_id: String
        let chunk_size: Int
        let total_chunks: Int
    }

    private struct CrashClipUploadCompleteResponse: Decodable {
        let status: String
    }
    
    private struct CrashClipUploadStatusResponse: Decodable {
        let status: String
        let upload_session_id: String?
        let chunk_size: Int
        let total_chunks: Int
        let uploaded_chunks: [Int]
        let next_chunk_index: Int
    }

    private struct MultipartFormDataBuilder {
        private let boundary: String
        private var data = Data()

        init(boundary: String) {
            self.boundary = boundary
        }

        func addField(name: String, value: String) -> MultipartFormDataBuilder {
            var copy = self
            copy.data.append("--\(boundary)\r\n".utf8Data)
            copy.data.append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".utf8Data)
            copy.data.append("\(value)\r\n".utf8Data)
            return copy
        }

        func addFileField(
            name: String,
            filename: String,
            mimeType: String,
            data fileData: Data
        ) -> MultipartFormDataBuilder {
            var copy = self
            copy.data.append("--\(boundary)\r\n".utf8Data)
            copy.data.append("Content-Disposition: form-data; name=\"\(name)\"; filename=\"\(filename)\"\r\n".utf8Data)
            copy.data.append("Content-Type: \(mimeType)\r\n\r\n".utf8Data)
            copy.data.append(fileData)
            copy.data.append("\r\n".utf8Data)
            return copy
        }

        func build() -> Data {
            var final = data
            final.append("--\(boundary)--\r\n".utf8Data)
            return final
        }
    }

    private func decodeCrashClipResponse<T: Decodable>(
        _ type: T.Type,
        data: Data,
        response: URLResponse
    ) throws -> T {
        guard let http = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }

        guard (200...299).contains(http.statusCode) else {
            let backendMessage: String
            if let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let detail = object["detail"] as? String {
                backendMessage = detail
            } else {
                backendMessage = String(data: data, encoding: .utf8) ?? "bad_server_response"
            }

            throw NSError(
                domain: "CrashClipUpload",
                code: http.statusCode,
                userInfo: [NSLocalizedDescriptionKey: backendMessage]
            )
        }

        do {
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            throw NSError(
                domain: "CrashClipUpload",
                code: -2,
                userInfo: [NSLocalizedDescriptionKey: "invalid_server_response"]
            )
        }
    }

    private func validateCrashClipStatusOnly(
        data: Data,
        response: URLResponse
    ) throws {
        guard let http = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }

        guard (200...299).contains(http.statusCode) else {
            let backendMessage: String
            if let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let detail = object["detail"] as? String {
                backendMessage = detail
            } else {
                backendMessage = String(data: data, encoding: .utf8) ?? "bad_server_response"
            }

            throw NSError(
                domain: "CrashClipUpload",
                code: http.statusCode,
                userInfo: [NSLocalizedDescriptionKey: backendMessage]
            )
        }
    }
    
    private func shouldRetryCrashClipChunkUpload(_ error: Error) -> Bool {
        if let urlError = error as? URLError {
            switch urlError.code {
            case .timedOut,
                 .networkConnectionLost,
                 .notConnectedToInternet,
                 .cannotConnectToHost,
                 .cannotFindHost,
                 .dnsLookupFailed,
                 .internationalRoamingOff,
                 .callIsActive,
                 .dataNotAllowed:
                return true
            default:
                break
            }
        }

        let nsError = error as NSError
        if nsError.domain == "CrashClipUpload" {
            switch nsError.code {
            case 408, 425, 429, 500, 502, 503, 504:
                return true
            default:
                return false
            }
        }

        if nsError.domain == NSURLErrorDomain {
            switch nsError.code {
            case NSURLErrorTimedOut,
                 NSURLErrorNetworkConnectionLost,
                 NSURLErrorNotConnectedToInternet,
                 NSURLErrorCannotConnectToHost,
                 NSURLErrorCannotFindHost,
                 NSURLErrorDNSLookupFailed:
                return true
            default:
                return false
            }
        }

        return false
    }

    private func uploadCrashClipChunkWithRetry(
        uploadSessionId: String,
        chunkIndex: Int,
        chunkData: Data,
        bearer: String,
        maxAttempts: Int = 6
    ) async throws {
        var attempt = 0

        while true {
            do {
                try await uploadCrashClipChunk(
                    uploadSessionId: uploadSessionId,
                    chunkIndex: chunkIndex,
                    chunkData: chunkData,
                    bearer: bearer
                )
                return
            } catch {
                attempt += 1

                let retryable = shouldRetryCrashClipChunkUpload(error)
                if !retryable || attempt >= maxAttempts {
                    print("❌ chunk \(chunkIndex) failed permanently after \(attempt) attempts: \(error)")
                    logEvent("❌ chunk \(chunkIndex) failed permanently after \(attempt) attempts: \(error)")
                    throw error
                }

                let delaySeconds = min(pow(2.0, Double(attempt - 1)), 60.0)
                print("🔁 retry chunk \(chunkIndex) attempt=\(attempt) delay=\(delaySeconds)s error=\(error)")
                logEvent("🔁 retry chunk \(chunkIndex) attempt=\(attempt) delay=\(delaySeconds)s error=\(error)")
                try await Task.sleep(nanoseconds: UInt64(delaySeconds * 1_000_000_000))
            }
        }
    }

    private func crashClipFileSize(_ fileURL: URL) throws -> Int {
        let values = try fileURL.resourceValues(forKeys: [.fileSizeKey])
        guard let size = values.fileSize else {
            throw URLError(.fileDoesNotExist)
        }
        return size
    }

    private func initCrashClipChunkUpload(
        crashClipId: String,
        totalSize: Int,
        chunkSize: Int,
        bearer: String
    ) async throws -> CrashClipUploadInitResponse {
        let url = euBaseURL.appendingPathComponent("crash-clips/upload/init")

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 30
        request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")

        let boundary = "Boundary-\(UUID().uuidString)"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        let body = MultipartFormDataBuilder(boundary: boundary)
            .addField(name: "crash_clip_id", value: crashClipId)
            .addField(name: "total_size", value: String(totalSize))
            .addField(name: "chunk_size", value: String(chunkSize))
            .build()

        request.httpBody = body

        let (data, response) = try await URLSession.shared.data(for: request)
        return try decodeCrashClipResponse(CrashClipUploadInitResponse.self, data: data, response: response)
    }

    private func uploadCrashClipChunk(
        uploadSessionId: String,
        chunkIndex: Int,
        chunkData: Data,
        bearer: String
    ) async throws {
        let url = euBaseURL.appendingPathComponent("crash-clips/upload/chunk")

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 45
        request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")

        let boundary = "Boundary-\(UUID().uuidString)"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        let body = MultipartFormDataBuilder(boundary: boundary)
            .addField(name: "upload_session_id", value: uploadSessionId)
            .addField(name: "chunk_index", value: String(chunkIndex))
            .addFileField(
                name: "file",
                filename: "chunk-\(chunkIndex).part",
                mimeType: "application/octet-stream",
                data: chunkData
            )
            .build()

        request.httpBody = body

        let (data, response) = try await URLSession.shared.data(for: request)
        try validateCrashClipStatusOnly(data: data, response: response)
    }

    private func completeCrashClipChunkUpload(
        crashClipId: String,
        bearer: String
    ) async throws {
        let url = euBaseURL.appendingPathComponent("crash-clips/upload/complete")

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 30
        request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")

        let boundary = "Boundary-\(UUID().uuidString)"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        let body = MultipartFormDataBuilder(boundary: boundary)
            .addField(name: "crash_clip_id", value: crashClipId)
            .build()

        request.httpBody = body

        let (data, response) = try await URLSession.shared.data(for: request)
        _ = try decodeCrashClipResponse(CrashClipUploadCompleteResponse.self, data: data, response: response)
    }

    private func getCrashClipChunkUploadStatus(
        crashClipId: String,
        bearer: String
    ) async throws -> CrashClipUploadStatusResponse {
        var components = URLComponents(
            url: euBaseURL.appendingPathComponent("crash-clips/upload/status"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [
            URLQueryItem(name: "crash_clip_id", value: crashClipId)
        ]

        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        request.timeoutInterval = 30
        request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")

        let (data, response) = try await URLSession.shared.data(for: request)
        return try decodeCrashClipResponse(CrashClipUploadStatusResponse.self, data: data, response: response)
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

        let deviceId = try resolveDashcamDeviceId(from: [
            "video_session_id": videoSessionId
        ])

        let bearer = try await ensureBearerWithFallback(deviceId: deviceId)

        let fileExists = FileManager.default.fileExists(atPath: fileURL.path)
        print("🚀 chunk uploadCrashClip START crashClipId=\(crashClipId)")
        logEvent("🚀 chunk uploadCrashClip START crashClipId=\(crashClipId)")
        print("📁 fileURL=\(fileURL.path)")
        logEvent("📁 fileURL=\(fileURL.path)")
        print("📁 file exists=\(fileExists)")
        logEvent("📁 file exists=\(fileExists)")

        guard fileExists else {
            print("❌ FILE NOT FOUND → upload abort")
            logEvent("❌ FILE NOT FOUND → upload abort")
            throw URLError(.fileDoesNotExist)
        }

        let totalSize = try crashClipFileSize(fileURL)
        print("📁 file size=\(totalSize)")
        logEvent("📁 file size=\(totalSize)")

        let preferredChunkSize = 64 * 1024

        let statusResponse = try await getCrashClipChunkUploadStatus(
            crashClipId: crashClipId,
            bearer: bearer
        )

        if statusResponse.status == "uploaded" {
            print("✅ upload already completed on server crashClipId=\(crashClipId)")
            logEvent("✅ upload already completed on server crashClipId=\(crashClipId)")
            return
        }

        let uploadSessionId: String
        let chunkSize: Int
        let totalChunks: Int
        let startChunkIndex: Int

        if let existingSessionId = statusResponse.upload_session_id,
           statusResponse.chunk_size > 0,
           statusResponse.total_chunks > 0 {
            uploadSessionId = existingSessionId
            chunkSize = statusResponse.chunk_size
            totalChunks = statusResponse.total_chunks
            startChunkIndex = statusResponse.next_chunk_index

            print("♻️ resume upload session_id=\(uploadSessionId) next_chunk=\(startChunkIndex)/\(totalChunks)")
            logEvent("♻️ resume upload session_id=\(uploadSessionId) next_chunk=\(startChunkIndex)/\(totalChunks)")
        } else {
            let initResponse = try await initCrashClipChunkUpload(
                crashClipId: crashClipId,
                totalSize: totalSize,
                chunkSize: preferredChunkSize,
                bearer: bearer
            )

            uploadSessionId = initResponse.session_id
            chunkSize = initResponse.chunk_size
            totalChunks = initResponse.total_chunks
            startChunkIndex = 0

            print("🌐 init upload session_id=\(uploadSessionId) total_chunks=\(totalChunks) chunk_size=\(chunkSize)")
            logEvent("🌐 init upload session_id=\(uploadSessionId) total_chunks=\(totalChunks) chunk_size=\(chunkSize)")
        }

        let fileHandle = try FileHandle(forReadingFrom: fileURL)
        defer { try? fileHandle.close() }

        if startChunkIndex < totalChunks {
            for chunkIndex in startChunkIndex..<totalChunks {
                let offset = UInt64(chunkIndex * chunkSize)
                try fileHandle.seek(toOffset: offset)

                let remaining = totalSize - (chunkIndex * chunkSize)
                let bytesToRead = min(chunkSize, remaining)

                guard let chunkData = try fileHandle.read(upToCount: bytesToRead), !chunkData.isEmpty else {
                    throw NSError(
                        domain: "CrashClipUpload",
                        code: -3,
                        userInfo: [NSLocalizedDescriptionKey: "failed_to_read_chunk_\(chunkIndex)"]
                    )
                }

                print("📦 uploading chunk \(chunkIndex + 1)/\(totalChunks) bytes=\(chunkData.count)")
                logEvent("📦 uploading chunk \(chunkIndex + 1)/\(totalChunks) bytes=\(chunkData.count)")

                try await uploadCrashClipChunkWithRetry(
                    uploadSessionId: uploadSessionId,
                    chunkIndex: chunkIndex,
                    chunkData: chunkData,
                    bearer: bearer
                )

                print("⏳ debug pause after chunk \(chunkIndex + 1)/\(totalChunks)")
                logEvent("⏳ debug pause after chunk \(chunkIndex + 1)/\(totalChunks)")
                try await Task.sleep(nanoseconds: 2_000_000_000)
            }
        }

        try await completeCrashClipChunkUpload(
            crashClipId: crashClipId,
            bearer: bearer
        )

        print("✅ chunk uploadCrashClip COMPLETE crashClipId=\(crashClipId)")
        logEvent("✅ chunk uploadCrashClip COMPLETE crashClipId=\(crashClipId)")
    }
    
    func uploadCrashClipResult(
        videoSessionId: String,
        crashClipId: String,
        fileURL: URL
    ) async -> NetworkSendResult {
        let outcome = await uploadCrashClipResultWithError(
            videoSessionId: videoSessionId,
            crashClipId: crashClipId,
            fileURL: fileURL
        )
        return outcome.result
    }
    
    func uploadCrashClipResultWithError(
        videoSessionId: String,
        crashClipId: String,
        fileURL: URL
    ) async -> (result: NetworkSendResult, errorText: String?) {
        var attempt = 0
        let maxAttempts = 3

        while true {
            do {
                try await uploadCrashClip(
                    videoSessionId: videoSessionId,
                    crashClipId: crashClipId,
                    fileURL: fileURL
                )
                return (.success, nil)
            } catch {
                attempt += 1

                let retryable = shouldRetryCrashClipChunkUpload(error)
                if !retryable || attempt >= maxAttempts {
                    let nsError = error as NSError
                    let errorText = "[\(nsError.domain):\(nsError.code)] \(nsError.localizedDescription)"
                    return (classifyDashcamSendError(error), errorText)
                }

                let delaySeconds = min(pow(2.0, Double(attempt - 1)), 8.0)
                print("🔁 whole upload retry crashClipId=\(crashClipId) attempt=\(attempt) delay=\(delaySeconds)s error=\(error)")
                logEvent("🔁 whole upload retry crashClipId=\(crashClipId) attempt=\(attempt) delay=\(delaySeconds)s error=\(error)")
                do {
                    try await Task.sleep(nanoseconds: UInt64(delaySeconds * 1_000_000_000))
                } catch {
                    let nsError = error as NSError
                    let errorText = "[\(nsError.domain):\(nsError.code)] \(nsError.localizedDescription)"
                    return (classifyDashcamSendError(error), errorText)
                }
            }
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
        
        if Task.isCancelled {
            throw CancellationError()
        }

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
            logEvent("❌ DASHCAM HTTP \(http.statusCode) path=\(path) body=\(responseBody)")
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
        logEvent("✅ DASHCAM HTTP \(http.statusCode) path=\(path) body=\(responseBody)")
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
private extension String {
    var utf8Data: Data { Data(self.utf8) }
}
