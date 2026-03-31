import Combine
import SwiftUI

@MainActor
final class LanguageManager: ObservableObject {

    private enum StorageKeys {
        static let language = "app_language"
    }

    @Published private(set) var currentLanguage: AppLanguage = .english

    init() {
        if
            let raw = UserDefaults.standard.string(forKey: StorageKeys.language),
            let stored = AppLanguage(rawValue: raw),
            AppLanguageRegistry.enabledInUI.contains(stored)
        {
            currentLanguage = stored
        } else {
            currentLanguage = Self.detectSystemDefaultLanguage()
        }
    }

    func setLanguage(_ language: AppLanguage) {
        currentLanguage = language
        UserDefaults.standard.set(language.rawValue, forKey: StorageKeys.language)
    }

    func text(_ key: LocalizationKey) -> String {

        if let value = LocalizationCatalog.catalog[currentLanguage]?[key] {
            return value
        }

        if let fallback = LocalizationCatalog.catalog[AppLanguage.fallback]?[key] {
            return fallback
        }

        return key.rawValue
    }

    func locale() -> Locale {
        Locale(identifier: currentLanguage.localeIdentifier)
    }
    
    private static func detectSystemDefaultLanguage() -> AppLanguage {
            let preferred = Locale.preferredLanguages

            if let first = preferred.first {
                let normalized = first.lowercased()
                if normalized.hasPrefix("ru") {
                    return .russian
                }
            }

            return .english
        }
}
