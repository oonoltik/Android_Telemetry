//
//  DebugLogSink.swift
//  TelemetryApp
//
//  Created by Alex on 23.04.26.
//

import Foundation

enum DebugLogSink {
    @inline(__always)
    static func write(_ message: String) {
        print(message)
        FileLogger.shared.log(message)
    }
}
