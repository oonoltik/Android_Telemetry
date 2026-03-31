//
//  KeychainStore.swift
//  TelemetryApp
//
//  Created by Alex on 14.01.26.
//

import Foundation
import Security

enum KeychainError: Error {
    case unhandled(OSStatus)
}

final class KeychainStore {
    static let shared = KeychainStore()

    private let service = "TelemetryApp"
    private let userDefaultsPrefix = "volatile.keychain."

    // Эти ключи не должны переживать uninstall, когда включён QA-флаг.
    private let volatileKeys: Set<String> = [
        "auth_bearer_token",
        "telemetry_device_id_v1"
    ]

    private init() {}

    func set(_ value: Data, for key: String) throws {
        if shouldUseUserDefaults(for: key) {
            UserDefaults.standard.set(value, forKey: userDefaultsKey(for: key))
            return
        }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]

        let attributes: [String: Any] = [
            kSecValueData as String: value
        ]

        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var addQuery = query
            addQuery[kSecValueData as String] = value
            let addStatus = SecItemAdd(addQuery as CFDictionary, nil)
            guard addStatus == errSecSuccess else { throw KeychainError.unhandled(addStatus) }
        } else if status != errSecSuccess {
            throw KeychainError.unhandled(status)
        }
    }

    func get(_ key: String) -> Data? {
        if shouldUseUserDefaults(for: key) {
            return UserDefaults.standard.data(forKey: userDefaultsKey(for: key))
        }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess else { return nil }
        return result as? Data
    }

    func delete(_ key: String) {
        if shouldUseUserDefaults(for: key) {
            UserDefaults.standard.removeObject(forKey: userDefaultsKey(for: key))
        }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]
        SecItemDelete(query as CFDictionary)
    }

    private func shouldUseUserDefaults(for key: String) -> Bool {
        FeatureFlags.uninstallSafeLocalPersistence && volatileKeys.contains(key)
    }

    private func userDefaultsKey(for key: String) -> String {
        userDefaultsPrefix + key
    }
}
