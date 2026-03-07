import Foundation
import CoreBluetooth
import Combine
import os.log

class BluetoothScanner: NSObject, ObservableObject {
    private var centralManager: CBCentralManager?
    private let logger = Logger(subsystem: "ch.pocketpc.nearbyglasses", category: "BluetoothScanner")

    private let rssiThreshold: Int
    private let debugEnabled: Bool
    private let debugCompanyIds: Set<UInt16>
    private let onDebugLog: ((String) -> Void)?
    private let onDeviceDetected: (DetectionEvent) -> Void

    @Published private(set) var isScanning = false

    private var lastUiDebugAt: Date = .distantPast

    init(
        rssiThreshold: Int,
        debugEnabled: Bool,
        debugCompanyIds: Set<UInt16>,
        onDebugLog: ((String) -> Void)?,
        onDeviceDetected: @escaping (DetectionEvent) -> Void
    ) {
        self.rssiThreshold = rssiThreshold
        self.debugEnabled = debugEnabled
        self.debugCompanyIds = debugCompanyIds
        self.onDebugLog = onDebugLog
        self.onDeviceDetected = onDeviceDetected
        super.init()
    }

    func startScanning() -> Bool {
        guard centralManager == nil else {
            if isScanning {
                logger.warning("Already scanning")
                return false
            }
            return false
        }

        centralManager = CBCentralManager(delegate: self, queue: .main)
        // Actual scanning starts after centralManagerDidUpdateState confirms .poweredOn
        return true
    }

    func stopScanning() {
        guard isScanning else { return }
        centralManager?.stopScan()
        isScanning = false
        centralManager = nil
        logger.info("BLE scanning stopped")
    }

    var isBluetoothEnabled: Bool {
        return centralManager?.state == .poweredOn
    }

    // MARK: - Debug Helpers
    private func d(_ msg: String) {
        guard debugEnabled else { return }
        onDebugLog?(msg)
    }

    private func dThrottled(_ msg: String, minInterval: TimeInterval = 0.25) {
        guard debugEnabled else { return }
        let now = Date()
        guard now.timeIntervalSince(lastUiDebugAt) >= minInterval else { return }
        lastUiDebugAt = now
        onDebugLog?(msg)
    }

    // MARK: - Scan Result Processing
    private func processScanResult(peripheral: CBPeripheral, advertisementData: [String: Any], rssi: NSNumber) {
        let rssiValue = rssi.intValue

        // Ignore invalid RSSI values (127 means RSSI not available)
        guard rssiValue != 127 else { return }

        let deviceAddress = peripheral.identifier.uuidString

        // Check RSSI threshold
        if rssiValue < rssiThreshold {
            if debugEnabled {
                logger.debug("Filtered by RSSI: \(deviceAddress) rssi=\(rssiValue)")
                d(String(format: NSLocalizedString("dbg_filtered_rssi", comment: ""), deviceAddress, rssiValue))
            }
            return
        }

        // Get device name
        let deviceName: String? = {
            if let localName = advertisementData[CBAdvertisementDataLocalNameKey] as? String, !localName.isEmpty {
                return localName
            }
            if let name = peripheral.name, !name.isEmpty {
                return name
            }
            return nil
        }()

        // Extract manufacturer data
        var companyId: UInt16?
        var manufacturerDataHex: String?

        if let mfgData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data, mfgData.count >= 2 {
            // CoreBluetooth: first 2 bytes are company ID (little-endian)
            companyId = UInt16(mfgData[0]) | (UInt16(mfgData[1]) << 8)
            let payload = mfgData.dropFirst(2)
            manufacturerDataHex = payload.map { String(format: "%02X", $0) }.joined()
        }

        // Debug throttled ADV log
        let nameSafe = deviceName ?? NSLocalizedString("dbg_placeholder_unknown", comment: "")
        let companySafe = companyId.map { String(format: "0x%04X", $0) } ?? NSLocalizedString("dbg_placeholder_none", comment: "")
        dThrottled(String(format: NSLocalizedString("dbg_adv_short", comment: ""),
                          deviceAddress, nameSafe, rssiValue, companySafe,
                          (manufacturerDataHex?.count ?? 0) / 2))

        // Check if smart glasses
        let (isSmartGlassesReal, reasonReal) = DetectionEvent.isSmartGlasses(companyId: companyId, deviceName: deviceName)
        let overrideMatch = debugEnabled && companyId != nil && debugCompanyIds.contains(companyId!)

        let isSmartGlasses = isSmartGlassesReal || overrideMatch
        let reason: String
        if overrideMatch && !isSmartGlassesReal {
            reason = String(format: NSLocalizedString("reason_debug_override_company_id", comment: ""),
                          String(format: "0x%04X", companyId!))
        } else {
            reason = reasonReal
        }

        if debugEnabled {
            logger.debug("ADV addr=\(deviceAddress) name=\(nameSafe) rssi=\(rssiValue) companyId=\(companySafe) smartglasses=\(isSmartGlasses) reason=\(reason)")
        }

        if isSmartGlasses {
            let event = DetectionEvent(
                timestamp: Date(),
                deviceAddress: deviceAddress,
                deviceName: deviceName,
                rssi: rssiValue,
                companyId: companyId.map { String(format: "0x%04X", $0) },
                companyName: companyId.map { DetectionEvent.getCompanyName($0) } ?? NSLocalizedString("company_unknown_plain", comment: ""),
                manufacturerData: manufacturerDataHex,
                detectionReason: reason
            )

            logger.debug("Smart glasses detected: \(event.deviceName ?? "?") (\(event.rssi) dBm)")
            onDeviceDetected(event)
        }
    }
}

// MARK: - CBCentralManagerDelegate
extension BluetoothScanner: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            logger.info("Bluetooth powered on, starting scan")
            // Scan for all peripherals (no service filter) to detect BLE advertisements
            central.scanForPeripherals(
                withServices: nil,
                options: [CBCentralManagerScanOptionAllowDuplicatesKey: true]
            )
            isScanning = true
            if debugEnabled {
                logger.info("BLE scanning started. RSSI threshold=\(self.rssiThreshold)")
            }
        case .poweredOff:
            logger.warning("Bluetooth is powered off")
            isScanning = false
        case .unauthorized:
            logger.error("Bluetooth unauthorized")
            isScanning = false
        case .unsupported:
            logger.error("Bluetooth not supported")
            isScanning = false
        default:
            break
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        processScanResult(peripheral: peripheral, advertisementData: advertisementData, rssi: RSSI)
    }
}
