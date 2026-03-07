import SwiftUI

struct MainView: View {
    @StateObject private var scanManager = ScanManager.shared
    @StateObject private var preferences = PreferencesManager.shared
    @State private var showSettings = false
    @State private var showClearConfirmation = false
    @State private var showExportSheet = false
    @State private var exportURL: URL?
    @State private var showToast = false
    @State private var toastMessage = ""
    @State private var showPermissionAlert = false

    var body: some View {
        NavigationStack {
            ScrollViewReader { scrollProxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        // Warning block
                        warningSection

                        // Scan button
                        scanButton

                        // Status
                        statusSection

                        // Info text
                        infoSection

                        // Log card
                        logCard(scrollProxy: scrollProxy)
                    }
                    .padding(16)
                }
            }
            .navigationTitle(NSLocalizedString("app_name", comment: ""))
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.red500, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button {
                        exportLog()
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                    }

                    Button {
                        showClearConfirmation = true
                    } label: {
                        Image(systemName: "trash")
                    }

                    Button {
                        showSettings = true
                    } label: {
                        Image(systemName: "gearshape")
                    }
                }
            }
            .sheet(isPresented: $showSettings) {
                SettingsView()
            }
            .alert(NSLocalizedString("dialog_clear_log_title", comment: ""),
                   isPresented: $showClearConfirmation) {
                Button(NSLocalizedString("dialog_clear", comment: ""), role: .destructive) {
                    scanManager.clearLog()
                    showToastMessage(NSLocalizedString("toast_log_cleared", comment: ""))
                }
                Button(NSLocalizedString("dialog_cancel", comment: ""), role: .cancel) {}
            } message: {
                Text(NSLocalizedString("dialog_clear_log_message", comment: ""))
            }
            .alert(NSLocalizedString("dialog_permissions_title", comment: ""),
                   isPresented: $showPermissionAlert) {
                Button(NSLocalizedString("dialog_open_settings", comment: "")) {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                }
                Button(NSLocalizedString("dialog_cancel", comment: ""), role: .cancel) {}
            } message: {
                Text(NSLocalizedString("dialog_permissions_message", comment: ""))
            }
            .sheet(isPresented: $showExportSheet) {
                if let url = exportURL {
                    ShareSheet(items: [url])
                }
            }
            .overlay(alignment: .bottom) {
                if showToast {
                    Text(toastMessage)
                        .font(.footnote)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(.ultraThinMaterial)
                        .clipShape(Capsule())
                        .padding(.bottom, 32)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
        }
    }

    // MARK: - Subviews
    private var warningSection: some View {
        VStack(alignment: .center, spacing: 6) {
            Text(NSLocalizedString("warning_title", comment: ""))
                .font(.body)
                .fontWeight(.bold)
                .frame(maxWidth: .infinity)

            Text(NSLocalizedString("warning_text", comment: ""))
                .font(.body)
                .fontWeight(.bold)
                .foregroundStyle(.secondary)
        }
    }

    private var scanButton: some View {
        Button {
            if scanManager.isScanning {
                scanManager.stopScanning()
            } else {
                requestPermissionsAndScan()
            }
        } label: {
            HStack {
                Image(systemName: "antenna.radiowaves.left.and.right")
                Text(scanManager.isScanning
                     ? NSLocalizedString("stopScanning", comment: "")
                     : NSLocalizedString("startScanning", comment: ""))
            }
            .frame(maxWidth: .infinity, minHeight: 44)
        }
        .buttonStyle(.borderedProminent)
        .tint(.red500)
    }

    private var statusSection: some View {
        Text(scanManager.isScanning
             ? NSLocalizedString("textScanning", comment: "")
             : NSLocalizedString("notScanning", comment: ""))
            .font(.body)
            .fontWeight(.bold)
    }

    private var infoSection: some View {
        Text(NSLocalizedString("info_text", comment: ""))
            .font(.body)
            .foregroundStyle(.secondary)
    }

    private func logCard(scrollProxy: ScrollViewProxy) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(NSLocalizedString("debug_log", comment: ""))
                .font(.headline)
                .fontWeight(.bold)

            ScrollView {
                let logText = logDisplayText
                Text(logText)
                    .font(.system(.caption, design: .monospaced))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .textSelection(.enabled)
                    .id("logBottom")
                    .onTapGesture {
                        // Copy last line to clipboard
                        if let lastLine = scanManager.logLines.last {
                            UIPasteboard.general.string = lastLine
                            showToastMessage(String(format: NSLocalizedString("clipboard_copied", comment: ""), lastLine))
                        }
                    }
                    .onLongPressGesture {
                        // Copy full log
                        let fullLog = scanManager.buildLogText()
                        if !fullLog.isEmpty {
                            UIPasteboard.general.string = fullLog
                            showToastMessage(String(format: NSLocalizedString("clipboard_copied", comment: ""),
                                                    NSLocalizedString("clipboard_label_log", comment: "")))
                        }
                    }
            }
            .frame(height: 128)
            .padding(8)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 6))
        }
        .padding(12)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .shadow(color: .black.opacity(0.1), radius: 2, y: 1)
    }

    private var logDisplayText: String {
        let show = preferences.loggingEnabled || preferences.debugEnabled
        if show {
            return scanManager.buildLogText()
        } else {
            return NSLocalizedString("notLogging", comment: "")
        }
    }

    // MARK: - Actions
    private func requestPermissionsAndScan() {
        // On iOS, CoreBluetooth handles permission prompts automatically
        // when you create a CBCentralManager. We just need notification permissions.
        NotificationHelper.shared.requestAuthorization { _ in }
        scanManager.startScanning()
    }

    private func exportLog() {
        guard let text = scanManager.exportLogText() else {
            showToastMessage(NSLocalizedString("nothing_to_export", comment: ""))
            return
        }

        do {
            let fileName = "nearby_glasses_detected_\(Int(Date().timeIntervalSince1970 * 1000)).txt"
            let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
            try text.write(to: tempURL, atomically: true, encoding: .utf8)
            exportURL = tempURL
            showExportSheet = true
        } catch {
            showToastMessage(String(format: NSLocalizedString("toast_export_error", comment: ""), error.localizedDescription))
        }
    }

    private func showToastMessage(_ message: String) {
        toastMessage = message
        withAnimation {
            showToast = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            withAnimation {
                showToast = false
            }
        }
    }
}

// MARK: - Share Sheet
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

// MARK: - Color Extension
extension Color {
    static let red500 = Color(red: 244/255, green: 67/255, blue: 54/255)
    static let red700 = Color(red: 211/255, green: 47/255, blue: 47/255)
}
