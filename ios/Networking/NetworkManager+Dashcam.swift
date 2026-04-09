import Foundation

extension NetworkManager {
    func startVideoSession(_ request: VideoSessionStartRequest) async throws {
        try await postDashcamJSON(path: "/video/session/start", body: request)
    }

    func stopVideoSession(_ request: VideoSessionStopRequest) async throws {
        try await postDashcamJSON(path: "/video/session/stop", body: request)
    }

    func postCrashClip(_ request: CrashClipEventRequest) async throws {
        try await postDashcamJSON(path: "/video/crash-clip", body: request)
    }

    func postDashcamCameraLog(_ request: DashcamCameraLogRequest) async throws {
        try await postDashcamJSON(path: "/video/camera-log", body: request)
    }

    private func postDashcamJSON<T: Encodable>(path: String, body: T) async throws {
        let baseURL = euBaseURL
        let url = baseURL.appendingPathComponent(path.trimmingCharacters(in: CharacterSet(charactersIn: "/")))

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 20
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)

        let (_, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
    }
}
