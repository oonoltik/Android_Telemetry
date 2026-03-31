//
//  AppLanguage.swift
//  TelemetryApp
//
//  Created by Alex on 15.03.26.
//

import Foundation

enum AppLanguage: String, CaseIterable, Codable, Identifiable, Hashable {
    case english = "en"
    case russian = "ru"
    case spanish = "es"
    case french = "fr"
    case chineseSimplified = "zh-Hans"
    case hindi = "hi"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .english: return "English"
        case .russian: return "Русский"
        case .spanish: return "Español"
        case .french: return "Français"
        case .chineseSimplified: return "简体中文"
        case .hindi: return "हिन्दी"
        }
    }

    var localeIdentifier: String {
        switch self {
        case .english: return "en_US"
        case .russian: return "ru_RU"
        case .spanish: return "es_ES"
        case .french: return "fr_FR"
        case .chineseSimplified: return "zh_Hans_CN"
        case .hindi: return "hi_IN"
        }
    }

    static let fallback: AppLanguage = .english
}

enum AppLanguageRegistry {

    /// Языки, которые уже реализованы архитектурно
    static let implemented: [AppLanguage] = [
        .english,
        .russian,
        .spanish,
        .french,
        .chineseSimplified,
        .hindi
    ]

    /// Языки, которые сейчас доступны пользователю
    static let enabledInUI: [AppLanguage] = [
        .english,
        .russian
    ]
}
