//
//  FeatureFlags.swift
//  TelemetryApp
//
//  Created by Alex on 29.01.26.
//

import Foundation

enum BuildFlavor: String {
    case developer
    case publicAlpha
}

enum FeatureFlags {
//    static let buildFlavor: BuildFlavor = {
//        #if DEBUG
//        return .developer
//        #else
//        return .publicAlpha
//        #endif
//    }()
    static let buildFlavor: BuildFlavor = .publicAlpha

    static var isDeveloperBuild: Bool { buildFlavor == .developer }
    static var isPublicAlphaBuild: Bool { buildFlavor == .publicAlpha }

    // Developer-only runtime behavior
    static var manualTuning: Bool { isDeveloperBuild }
//    static var manualTuning: Bool { false }
    
    static var showsDeveloperDiagnostics: Bool { isDeveloperBuild }

    // Temporary QA stub:
    // selected local state should be removed together with the app on uninstall
    // Удалаем все с устрйоства при переустановке -в режиме true
    static let uninstallSafeLocalPersistence = true
}
