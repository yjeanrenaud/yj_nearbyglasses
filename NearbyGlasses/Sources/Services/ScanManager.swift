import Foundation
import Combine
import CoreBluetooth
import os.log

/// Manages Bluetooth scanning lifecycle, replaces Android's BluetoothScanService.
/// On iOS there is no foreground service concept — background BLE scanning is handled
/// via CoreBluetooth background modes declared in Info.plist.
class ScanManager: ObservableObject {
    static let shared = ScanManager()

    private let logger = Logger(subsystem: "ch.pocketpc.nearbyglasses", category: "ScanManager")
    private let preferences = PreferencesManager.shared
    private let notificationHelper = NotificationHelper.shared

    @Published private(set) var isScanning = false
    @Published var detectionLog: [DetectionEvent] = []
    @Published var logLines: [String] = []

    private var bluetoothScanner: BluetoothScanner?
    private var lastNotificationTime: Date = .distantPast
    private var cancellables = Set<AnyCancellable>()

    var detectionListeners: [(DetectionEvent) -> Void] = []
    var debugListeners: [(String) -> Void] = []

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f
    }()

    private init() {}

    func startScanning() {
        guard bluetoothScanner == nil || !isScanning else {
            logger.warning("Already scanning")
            return
        }

        let rssiThreshold = preferences.rssiThreshold
        let debugEnabled = preferences.debugEnabled
        let debugCompanyIds = preferences.debugCompanyIds

        bluetoothScanner = BluetoothScanner(
            rssiThreshold: rssiThreshold,
            debugEnabled: debugEnabled,
            debugCompanyIds: debugCompanyIds,
            onDebugLog: { [weak self] msg in
                guard let self = self else { return }
                let advOnly = self.preferences.debugAdvOnly
                if !advOnly || msg.hasPrefix("ADV ") {
                    self.emitDebug(msg)
                }
            },
            onDeviceDetected: { [weak self] event in
                self?.handleDetection(event)
            }
        )

        let success = bluetoothScanner?.startScanning() ?? false
        if success {
            isScanning = true
            logger.info("Scanning started")
        } else {
            logger.error("Failed to start scanning")
        }

        // Observe scanner's isScanning to stay in sync
        bluetoothScanner?.$isScanning
            .receive(on: DispatchQueue.main)
            .sink { [weak self] scanning in
                self?.isScanning = scanning
            }
            .store(in: &cancellables)
    }

    func stopScanning() {
        bluetoothScanner?.stopScanning()
        bluetoothScanner = nil
        isScanning = false
        cancellables.removeAll()
    }

    // MARK: - Detection Handling
    private func handleDetection(_ event: DetectionEvent) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.detectionLog.append(event)
            self.detectionListeners.forEach { $0(event) }

            // Add to log lines
            if self.preferences.loggingEnabled {
                let line = event.toLogString()
                self.appendLine(line)
            }

            // Notification cooldown
            let cooldownSeconds = Double(self.preferences.cooldownMs) / 1000.0
            let now = Date()
            if now.timeIntervalSince(self.lastNotificationTime) >= cooldownSeconds {
                if self.preferences.notificationsEnabled {
                    self.notificationHelper.showDetectionNotification(event: event)
                }
                self.lastNotificationTime = now
            } else {
                self.logger.debug("Detection within cooldown period, notification suppressed")
            }
        }
    }

    // MARK: - Debug
    private func emitDebug(_ msg: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.debugListeners.forEach { $0(msg) }

            if self.preferences.debugEnabled {
                let timestamp = self.dateFormatter.string(from: Date())
                let debugPrefix = NSLocalizedString("log_debug_prefix", comment: "")
                let line = String(format: NSLocalizedString("log_debug_line", comment: ""), timestamp, debugPrefix, msg)
                self.appendLine(line)
            }
        }
    }

    // MARK: - Log Management
    func appendLine(_ line: String) {
        guard !line.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }

        let maxLines = max(50, preferences.debugEnabled ? preferences.debugMaxLines : 100)
        logLines.append(line)
        while logLines.count > maxLines {
            logLines.removeFirst()
        }
    }

    func buildLogText() -> String {
        if logLines.isEmpty { return "" }
        return logLines.joined(separator: "\n") + "\n"
    }

    func clearLog() {
        detectionLog.removeAll()
        logLines.removeAll()
    }

    func exportLogText() -> String? {
        guard !detectionLog.isEmpty || !logLines.isEmpty else { return nil }
        return buildLogText()
    }
}
