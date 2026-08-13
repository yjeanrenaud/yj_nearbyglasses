/*
 * 2026, Yves Jeanrenaud https://github.com/yjeanrenaud/yj_nearbyglasses
 */
import Foundation
import Combine
/*
 * helper to store settings. For convenience, I handle all other helper functions within here, too.
 */
@MainActor
final class ScannerStore: ObservableObject {
    @Published private(set) var isScanning = false
    @Published private(set) var canaryDetected = false
    @Published private(set) var canaryFlipped = false
    @Published private(set) var logEntries: [LogEntry] = []
    @Published var statusMessage: String?

    private let settings: AppSettings
    private let languageManager: LanguageManager
    private lazy var scanner = BLEScanner(
        configuration: { [unowned self] in
            BLEScanner.Configuration(
                rssiThreshold: self.settings.rssiThreshold,
                debugEnabled: self.settings.debugEnabled,
                debugCompanyIDs: self.settings.debugCompanyIDs,
                ignoredCompanyIDs: self.settings.ignoredCompanyIDs,
                ignoredTokens: self.settings.ignoredTokens
            )
        },
        languageManager: languageManager,
        onDebug: { [weak self] in self?.appendDebug($0) },
        onDetection: { [weak self] in self?.handleDetection($0) },
        onStateMessage: { [weak self] in self?.statusMessage = $0 },
        onScanningChanged: { [weak self] scanning in
            self?.isScanning = scanning
            if scanning {
                self?.startFlipLoopIfNeeded()
            } else {
                self?.flipTimer?.invalidate()
                self?.flipTimer = nil
            }
        }
    )

    private var flipTimer: Timer?
    private var canaryResetWorkItem: DispatchWorkItem?
    private var lastDetectionPresentedAt: Date = .distantPast
    private var cancellables = Set<AnyCancellable>()

    init(settings: AppSettings, languageManager: LanguageManager) {
        self.settings = settings
        self.languageManager = languageManager

        settings.$debugMaxLines
            .sink { [weak self] _ in self?.trimLogsIfNeeded() }
            .store(in: &cancellables)
    }

    deinit {
        flipTimer?.invalidate()
        canaryResetWorkItem?.cancel()
    }

    func startScanning() {
        canaryDetected = false
        canaryFlipped = false
        statusMessage = nil
        scanner.start()
    }

    func stopScanning(backgrounded: Bool = false) {
        scanner.stop()
        isScanning = false
        canaryDetected = false
        canaryFlipped = false
        canaryResetWorkItem?.cancel()
        canaryResetWorkItem = nil
        flipTimer?.invalidate()
        flipTimer = nil
        if backgrounded {
            appendInfo(languageManager.text("ios_stop_when_backgrounded"))
        }
    }

    func clearLogs() {
        logEntries.removeAll()
    }

    func exportLogURL() throws -> URL {
        let formatter = ISO8601DateFormatter()
        let body = logEntries.map(\.text).joined(separator: "\n") + (logEntries.isEmpty ? "" : "\n")
        let filename = languageManager.text("ios_export_filename_prefix") + formatter.string(from: .now).replacingOccurrences(of: ":", with: "-") + ".txt"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(filename)
        try body.write(to: url, atomically: true, encoding: .utf8)
        return url
    }

    private func handleDetection(_ event: DetectionEvent) {
        let now = Date()
        let cooldown = TimeInterval(settings.cooldownMs) / 1000.0
        guard now.timeIntervalSince(lastDetectionPresentedAt) >= cooldown else { return }
        lastDetectionPresentedAt = now

        canaryDetected = true
        scheduleCanaryReset(after: cooldown)
        appendDetection(event.logLine(using: languageManager))
    }

    private func scheduleCanaryReset(after seconds: TimeInterval) {
        canaryResetWorkItem?.cancel()
        let work = DispatchWorkItem { [weak self] in
            Task { @MainActor [weak self] in
                self?.canaryDetected = false
            }
        }
        canaryResetWorkItem = work
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds, execute: work)
    }

    private func startFlipLoopIfNeeded() {
        flipTimer?.invalidate()
        flipTimer = Timer.scheduledTimer(withTimeInterval: 3.0, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self, self.isScanning else { return }
                self.canaryFlipped.toggle()
            }
        }
    }

    private func appendDetection(_ line: String) {
        logEntries.append(LogEntry(text: line, kind: .detection))
        trimLogsIfNeeded()
    }

    private func appendInfo(_ line: String) {
        logEntries.append(LogEntry(text: line, kind: .info))
        trimLogsIfNeeded()
    }

    private func appendDebug(_ line: String) {
        guard settings.debugEnabled else { return }
        if settings.debugAdvOnly && !line.hasPrefix("ADV ") { return }

        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        let timestamp = formatter.string(from: .now)
        let formatted = languageManager.text("log_debug_line", timestamp, languageManager.text("log_debug_prefix"), line)
        logEntries.append(LogEntry(text: formatted, kind: .debug))
        trimLogsIfNeeded()
    }

    private func trimLogsIfNeeded() {
        let maxLines = settings.debugEnabled ? settings.debugMaxLines : 100
        if logEntries.count > maxLines {
            logEntries.removeFirst(logEntries.count - maxLines)
        }
    }
}
