import Foundation

final class FileLogger {
    static let shared = FileLogger()

    private let queue = DispatchQueue(label: "telemetry.filelogger.queue")
    private let fileURL: URL

    private let maxLogSizeBytes: UInt64 = 10 * 1024 * 1024 // 10 MB
    private let trimToBytes: UInt64 = 5 * 1024 * 1024     // оставляем последние 5 MB

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

        // применяем ВСЕГДА
        try? fm.setAttributes(
            [.protectionKey: FileProtectionType.none],
            ofItemAtPath: fileURL.path
        )

        trimIfNeeded()
    }

    func log(_ message: String) {
        let line = "[\(formatter.string(from: Date()))] \(message)\n"

        queue.sync {
            guard let data = line.data(using: .utf8) else { return }

            do {
                try trimIfNeededLocked()

                let handle = try FileHandle(forWritingTo: self.fileURL)
                defer { try? handle.close() }

                try handle.seekToEnd()
                try handle.write(contentsOf: data)

                if #available(iOS 13.0, *) {
                    try? handle.synchronize()
                }

                try trimIfNeededLocked()
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

    private func trimIfNeeded() {
        queue.sync {
            try? trimIfNeededLocked()
        }
    }

    private func trimIfNeededLocked() throws {
        let fm = FileManager.default

        guard
            let attributes = try? fm.attributesOfItem(atPath: fileURL.path),
            let size = attributes[.size] as? UInt64,
            size > maxLogSizeBytes
        else {
            return
        }

        let data = try Data(contentsOf: fileURL)

        if UInt64(data.count) <= trimToBytes {
            return
        }

        let startIndex = data.count - Int(trimToBytes)
        let trimmedData = data.subdata(in: startIndex..<data.count)

        try trimmedData.write(to: fileURL, options: .atomic)

        print("🧹 FileLogger trimmed from \(size) bytes to \(trimToBytes) bytes")
    }
}
