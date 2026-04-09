import Foundation

protocol DashcamSettingsStore {
    var enableMicrophone: Bool { get }
    var preCrashSeconds: Int { get }
    var postCrashSeconds: Int { get }
    var storageQuotaBytes: Int64 { get }
    var maxSegmentDurationSeconds: Int { get }
    var exportToPhotosEnabled: Bool { get }
    var previewEnabledByDefault: Bool { get }
}

struct UserDefaultsDashcamSettingsStore: DashcamSettingsStore {
    private let defaults: UserDefaults = .standard

    
    var preCrashSeconds: Int {
        let value = defaults.integer(forKey: "dashcam.preCrashSeconds")
        return value > 0 ? value : 10
    }
    var postCrashSeconds: Int {
        let value = defaults.integer(forKey: "dashcam.postCrashSeconds")
        return value > 0 ? value : 10
    }
    var storageQuotaBytes: Int64 {
        let gb = defaults.integer(forKey: "dashcam.storageQuotaGB")
        let effectiveGB = gb > 0 ? gb : 10
        return Int64(effectiveGB) * 1024 * 1024 * 1024
    }
    var maxSegmentDurationSeconds: Int {
        let value = defaults.integer(forKey: "dashcam.maxSegmentDurationSeconds")
        return value > 0 ? value : 120
    }
    var exportToPhotosEnabled: Bool { defaults.bool(forKey: "dashcam.exportToPhotos") }
    var previewEnabledByDefault: Bool { defaults.bool(forKey: "dashcam.previewByDefault") }
    var enableMicrophone: Bool {
        get {
            if UserDefaults.standard.object(forKey: "dashcam.enableMicrophone") == nil {
                return true
            }
            return UserDefaults.standard.bool(forKey: "dashcam.enableMicrophone")
        }
        set {
            UserDefaults.standard.set(newValue, forKey: "dashcam.enableMicrophone")
        }
    }
}
