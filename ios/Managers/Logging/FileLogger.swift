//
//  FileLogger.swift
//  TelemetryApp
//
//  Created by Alex on 22.04.26.
//

import Foundation

final class FileLogger {
    static let shared = FileLogger()

    private let queue = DispatchQueue(label: "telemetry.filelogger.queue")
    private let fileURL: URL
    private let formatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private init() {
        let fm = FileManager.default
        let appSupport = try! fm.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )

        let logsDir = appSupport.appendingPathComponent("Logs", isDirectory: true)

        if !fm.fileExists(atPath: logsDir.path) {
            try? fm.createDirectory(at: logsDir, withIntermediateDirectories: true)
        }

        fileURL = logsDir.appendingPathComponent("session.log")

        if !fm.fileExists(atPath: fileURL.path) {
            fm.createFile(atPath: fileURL.path, contents: nil)
        }
    }

    func log(_ message: String) {
        let line = "[\(formatter.string(from: Date()))] \(message)\n"

        queue.sync {
            guard let data = line.data(using: .utf8) else { return }

            do {
                let handle = try FileHandle(forWritingTo: self.fileURL)
                defer {
                    try? handle.close()
                }

                try handle.seekToEnd()
                try handle.write(contentsOf: data)

                if #available(iOS 13.0, *) {
                    try? handle.synchronize()
                }
            } catch {
                print("❌ FileLogger write failed: \(error)")
            }
        }
    }

    func reset() {
        queue.sync {
            do {
                try Data().write(to: self.fileURL, options: .atomic)
            } catch {
                print("❌ FileLogger reset failed: \(error)")
            }
        }
    }

    func currentLogURL() -> URL {
        fileURL
    }
    
    
}
