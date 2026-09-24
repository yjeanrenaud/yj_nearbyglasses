/*
 * 2026, Yves Jeanrenaud https://github.com/yjeanrenaud/yj_nearbyglasses
 */
import Foundation
import CoreBluetooth
/*
 * the scan functions' heuristics
 */
struct SmartGlassesHeuristics {
    static let metaCompanyID1 = 0x01AB
    static let metaCompanyID2 = 0x058E
    static let essilorCompanyID = 0x0D53
    static let snapCompanyID = 0x03C2

    static func reasons(companyID: Int?, deviceName: String?, l10n: LanguageManager) -> [String] {
        var reasons: [String] = []

        if companyID == metaCompanyID1 {
            reasons.append(l10n.text("reason_meta_company_id", "0x01AB"))
        }
        if companyID == metaCompanyID2 {
            reasons.append(l10n.text("reason_meta_company_id", "0x058E"))
        }
        if companyID == essilorCompanyID {
            reasons.append(l10n.text("reason_essilor_company_id", "0x0D53"))
        }
        if companyID == snapCompanyID {
            reasons.append(l10n.text("reason_snap_company_id", "0x03C2"))
        }

        if let name = deviceName?.lowercased() {
            if name.contains("rayban") { reasons.append(l10n.text("reason_name_contains", "rayban")) }
            if name.contains("ray-ban") { reasons.append(l10n.text("reason_name_contains", "ray-ban")) }
            if name.contains("ray ban") { reasons.append(l10n.text("reason_name_contains", "ray ban")) }
            if name.contains("heycyan") { reasons.append(l10n.text("reason_name_contains", "HeyCyan")) }
            if name.contains("oakley") { reasons.append(l10n.text("reason_name_contains", "Oakley")) } // Oakley HSTN Meta (e.g. "Oakley Meta 099H")
        }

        return reasons
    }

    static func companyName(for companyID: Int, l10n: LanguageManager) -> String {
        switch companyID {
        case metaCompanyID1, metaCompanyID2:
            return l10n.text("company_meta")
        case essilorCompanyID:
            return l10n.text("company_essilor")
        case snapCompanyID:
            return l10n.text("company_snap")
        default:
            return l10n.text("company_unknown", String(format: "0x%04X", companyID))
        }
    }
}

@MainActor
final class BLEScanner: NSObject, CBCentralManagerDelegate {
    struct Configuration {
        let rssiThreshold: Int
        let debugEnabled: Bool
        let debugCompanyIDs: Set<Int>
    }

    private lazy var centralManager = CBCentralManager(delegate: self, queue: .main)
    private let configuration: () -> Configuration
    private let languageManager: LanguageManager
    private let onDebug: (String) -> Void
    private let onDetection: (DetectionEvent) -> Void
    private let onStateMessage: (String?) -> Void
    private let onScanningChanged: (Bool) -> Void

    private var pendingStart = false
    private var lastDebugAt: Date = .distantPast
    private(set) var isScanning = false

    init(
        configuration: @escaping () -> Configuration,
        languageManager: LanguageManager,
        onDebug: @escaping (String) -> Void,
        onDetection: @escaping (DetectionEvent) -> Void,
        onStateMessage: @escaping (String?) -> Void,
        onScanningChanged: @escaping (Bool) -> Void
    ) {
        self.configuration = configuration
        self.languageManager = languageManager
        self.onDebug = onDebug
        self.onDetection = onDetection
        self.onStateMessage = onStateMessage
        self.onScanningChanged = onScanningChanged
        super.init()
    }

    func start() {
        pendingStart = true
        switch centralManager.state {
        case .poweredOn:
            startScanningIfPossible()
        case .poweredOff:
            onScanningChanged(false)
            onStateMessage(languageManager.text("ios_bluetooth_powered_off_message"))
        case .unauthorized:
            onStateMessage(languageManager.text("ios_bluetooth_denied_message"))
        case .unsupported:
            onStateMessage(languageManager.text("toast_bluetooth_not_supported"))
        default:
            break
        }
    }

    func stop() {
        pendingStart = false
        guard isScanning else { return }
        centralManager.stopScan()
        isScanning = false
        onScanningChanged(false)
        onDebug(languageManager.text("dbg_ble_stopped"))
        onStateMessage(nil)
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            onStateMessage(nil)
            if pendingStart { startScanningIfPossible() }
        case .poweredOff:
            isScanning = false
            onScanningChanged(false)
            onStateMessage(languageManager.text("ios_bluetooth_powered_off_message"))
        case .unauthorized:
            isScanning = false
            onScanningChanged(false)
            onStateMessage(languageManager.text("ios_bluetooth_denied_message"))
        case .unsupported:
            isScanning = false
            onScanningChanged(false)
            onStateMessage(languageManager.text("toast_bluetooth_not_supported"))
        default:
            isScanning = false
            onScanningChanged(false)
        }
    }

    private func startScanningIfPossible() {
        guard !isScanning else {
            onDebug(languageManager.text("dbg_already_scanning"))
            return
        }

        centralManager.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
        isScanning = true
        onScanningChanged(true)
        let config = configuration()
        if config.debugEnabled {
            onDebug(languageManager.text("dbg_ble_started_verbose", config.rssiThreshold))
        } else {
            onDebug(languageManager.text("dbg_ble_started_simple", config.rssiThreshold))
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String : Any],
        rssi RSSI: NSNumber
    ) {
        let config = configuration()
        let rssi = RSSI.intValue
        let deviceIdentifier = peripheral.identifier.uuidString
        if rssi < config.rssiThreshold {
            if config.debugEnabled {
                onDebug(languageManager.text("dbg_filtered_rssi", deviceIdentifier, rssi))
            }
            return
        }

        let deviceName = (advertisementData[CBAdvertisementDataLocalNameKey] as? String) ?? peripheral.name
        let manufacturerData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data
        let companyID = manufacturerData.flatMap(Self.companyID(from:))
        let manufacturerHex = manufacturerData?.map { String(format: "%02X", $0) }.joined()

        let companyString = companyID.map { String(format: "0x%04X", $0) } ?? languageManager.text("dbg_placeholder_none")
        let safeName = deviceName?.isEmpty == false ? deviceName! : languageManager.text("dbg_placeholder_unknown")
        throttledDebug(
            enabled: config.debugEnabled,
            message: languageManager.text(
                "dbg_adv_short",
                deviceIdentifier,
                safeName,
                rssi,
                companyString,
                (manufacturerHex?.count ?? 0) / 2
            )
        )

        let reasons = SmartGlassesHeuristics.reasons(companyID: companyID, deviceName: deviceName, l10n: languageManager)
        let overrideMatch = config.debugEnabled && companyID.map(config.debugCompanyIDs.contains) == true
        let isSmartGlasses = !reasons.isEmpty || overrideMatch
        let reasonText: String
        if overrideMatch, let companyID {
            reasonText = languageManager.text("reason_debug_override_company_id", String(format: "0x%04X", companyID))
        } else {
            reasonText = reasons.joined(separator: ", ")
        }

        if config.debugEnabled {
            onDebug(languageManager.text(
                "dbg_adv_full",
                deviceIdentifier,
                safeName,
                rssi,
                companyString,
                (manufacturerHex?.count ?? 0) / 2,
                isSmartGlasses ? "true" : "false",
                reasonText
            ))
        }

        guard isSmartGlasses else { return }
        let event = DetectionEvent(
            deviceIdentifier: deviceIdentifier,
            deviceName: deviceName,
            rssi: rssi,
            companyIDHex: companyID.map { String(format: "0x%04X", $0) },
            companyName: companyID.map { SmartGlassesHeuristics.companyName(for: $0, l10n: languageManager) } ?? languageManager.text("company_unknown_plain"),
            manufacturerDataHex: manufacturerHex,
            detectionReason: reasonText.isEmpty ? languageManager.text("ios_detection_reason_none") : reasonText
        )
        onDetection(event)
    }

    private static func companyID(from data: Data) -> Int? {
        guard data.count >= 2 else { return nil }
        return Int(data[data.startIndex]) | (Int(data[data.startIndex.advanced(by: 1)]) << 8)
    }

    private func throttledDebug(enabled: Bool, message: String) {
        guard enabled else { return }
        let now = Date()
        guard now.timeIntervalSince(lastDebugAt) >= 0.25 else { return }
        lastDebugAt = now
        onDebug(message)
    }
}
