//
//  LogEvent.swift
//  TelemetryApp
//
//  Created by Alex on 22.04.26.
//

import Foundation

@inline(__always)
func logEvent(_ message: String) {
    print(message)
    FileLogger.shared.log(message)
}
