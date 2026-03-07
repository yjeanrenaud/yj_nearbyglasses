import Foundation
import Combine

class PreferencesManager: ObservableObject {
    static let shared = PreferencesManager()

    private let defaults = UserDefaults.standard

    // MARK: - Keys
    private enum Keys {
        static let rssiThreshold = "rssi_threshold"
        static let cooldownMs = "cooldown_ms"
        static let foregroundService = "foreground_service"
        static let enableNotifications = "enable_notifications"
        static let loggingEnabled = "logging_enabled"
        static let debugEnabled = "debug_enabled"
        static let debugMaxLines = "debug_max_lines"
        static let debugAdvOnly = "debug_advonly"
        static let debugCompanyIds = "debug_company_ids"
    }

    // MARK: - Defaults
    private static let defaultRssiThreshold = -75
    private static let defaultCooldownMs: Int = 10000
    private static let defaultForegroundService = true
    private static let defaultNotifications = true
    private static let defaultLoggingEnabled = true
    private static let defaultDebugEnabled = false
    private static let defaultDebugMaxLines = 200
    private static let defaultDebugAdvOnly = true

    init() {
        let defaultValues: [String: Any] = [
            Keys.rssiThreshold: Self.defaultRssiThreshold,
            Keys.cooldownMs: Self.defaultCooldownMs,
            Keys.foregroundService: Self.defaultForegroundService,
            Keys.enableNotifications: Self.defaultNotifications,
            Keys.loggingEnabled: Self.defaultLoggingEnabled,
            Keys.debugEnabled: Self.defaultDebugEnabled,
            Keys.debugMaxLines: Self.defaultDebugMaxLines,
            Keys.debugAdvOnly: Self.defaultDebugAdvOnly,
            Keys.debugCompanyIds: ""
        ]
        defaults.register(defaults: defaultValues)
    }

    @Published var rssiThreshold: Int {
        didSet { defaults.set(rssiThreshold, forKey: Keys.rssiThreshold) }
    }

    @Published var cooldownMs: Int {
        didSet { defaults.set(cooldownMs, forKey: Keys.cooldownMs) }
    }

    @Published var foregroundServiceEnabled: Bool {
        didSet { defaults.set(foregroundServiceEnabled, forKey: Keys.foregroundService) }
    }

    @Published var notificationsEnabled: Bool {
        didSet { defaults.set(notificationsEnabled, forKey: Keys.enableNotifications) }
    }

    @Published var loggingEnabled: Bool {
        didSet { defaults.set(loggingEnabled, forKey: Keys.loggingEnabled) }
    }

    @Published var debugEnabled: Bool {
        didSet { defaults.set(debugEnabled, forKey: Keys.debugEnabled) }
    }

    @Published var debugMaxLines: Int {
        didSet { defaults.set(debugMaxLines, forKey: Keys.debugMaxLines) }
    }

    @Published var debugAdvOnly: Bool {
        didSet { defaults.set(debugAdvOnly, forKey: Keys.debugAdvOnly) }
    }

    @Published var debugCompanyIdsString: String {
        didSet { defaults.set(debugCompanyIdsString, forKey: Keys.debugCompanyIds) }
    }

    var debugCompanyIds: Set<UInt16> {
        let raw = debugCompanyIdsString
        return Set(
            raw.split(separator: ",")
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }
                .compactMap { token -> UInt16? in
                    let t = token.lowercased()
                    if t.hasPrefix("0x") {
                        return UInt16(t.dropFirst(2), radix: 16)
                    } else if t.allSatisfy({ $0.isNumber }) {
                        return UInt16(t, radix: 10)
                    } else {
                        return UInt16(t, radix: 16)
                    }
                }
        )
    }

    // Load stored values — call this to hydrate @Published properties from UserDefaults
    func load() {
        rssiThreshold = defaults.integer(forKey: Keys.rssiThreshold)
        if rssiThreshold == 0 && !defaults.contains(key: Keys.rssiThreshold) {
            rssiThreshold = Self.defaultRssiThreshold
        }
        cooldownMs = defaults.integer(forKey: Keys.cooldownMs)
        if cooldownMs == 0 && !defaults.contains(key: Keys.cooldownMs) {
            cooldownMs = Self.defaultCooldownMs
        }
        cooldownMs = max(0, min(cooldownMs, 600_000))
        foregroundServiceEnabled = defaults.bool(forKey: Keys.foregroundService)
        notificationsEnabled = defaults.bool(forKey: Keys.enableNotifications)
        loggingEnabled = defaults.bool(forKey: Keys.loggingEnabled)
        debugEnabled = defaults.bool(forKey: Keys.debugEnabled)
        debugMaxLines = defaults.integer(forKey: Keys.debugMaxLines)
        if debugMaxLines == 0 { debugMaxLines = Self.defaultDebugMaxLines }
        debugMaxLines = max(50, min(debugMaxLines, 5000))
        debugAdvOnly = defaults.bool(forKey: Keys.debugAdvOnly)
        debugCompanyIdsString = defaults.string(forKey: Keys.debugCompanyIds) ?? ""
    }
}

private extension UserDefaults {
    func contains(key: String) -> Bool {
        return object(forKey: key) != nil
    }
}
