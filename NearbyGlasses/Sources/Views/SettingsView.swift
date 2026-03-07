import SwiftUI

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var preferences = PreferencesManager.shared

    @State private var rssiText: String = ""
    @State private var cooldownText: String = ""
    @State private var debugMaxLinesText: String = ""
    @State private var debugCompanyIdsText: String = ""

    var body: some View {
        NavigationStack {
            Form {
                // Scanning Settings
                Section(header: Text(NSLocalizedString("titleCategoryScanningSettings", comment: ""))) {
                    Toggle(isOn: $preferences.foregroundServiceEnabled) {
                        VStack(alignment: .leading) {
                            Text(NSLocalizedString("titleForeground", comment: ""))
                            Text(NSLocalizedString("summaryForeground", comment: ""))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text(NSLocalizedString("titleThreshold", comment: ""))
                        TextField("-75", text: $rssiText)
                            .keyboardType(.numbersAndPunctuation)
                            .onChange(of: rssiText) { _, newValue in
                                if let v = Int(newValue), (-120...0).contains(v) {
                                    preferences.rssiThreshold = v
                                }
                            }
                        Text(String(format: NSLocalizedString("summaryThreshold", comment: ""), rssiText))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                // Notification Settings
                Section(header: Text(NSLocalizedString("titleCategoryNotifications", comment: ""))) {
                    Toggle(isOn: $preferences.notificationsEnabled) {
                        VStack(alignment: .leading) {
                            Text(NSLocalizedString("titleNotifications", comment: ""))
                            Text(NSLocalizedString("summaryNotifications", comment: ""))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text(NSLocalizedString("titleCooldown", comment: ""))
                        TextField("10000", text: $cooldownText)
                            .keyboardType(.numberPad)
                            .onChange(of: cooldownText) { _, newValue in
                                if let v = Int(newValue), (0...600_000).contains(v) {
                                    preferences.cooldownMs = v
                                }
                            }
                        Text(String(format: NSLocalizedString("summaryCooldown", comment: ""), cooldownText))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                // Logging Settings
                Section(header: Text(NSLocalizedString("titleCategoryLogging", comment: ""))) {
                    Toggle(isOn: $preferences.loggingEnabled) {
                        VStack(alignment: .leading) {
                            Text(NSLocalizedString("titleLogging", comment: ""))
                            Text(NSLocalizedString("summaryLogging", comment: ""))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    Toggle(isOn: $preferences.debugEnabled) {
                        VStack(alignment: .leading) {
                            Text(NSLocalizedString("titleDebug", comment: ""))
                            Text(NSLocalizedString("summaryDebug", comment: ""))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    if preferences.debugEnabled {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(NSLocalizedString("titleDebugSize", comment: ""))
                            TextField("200", text: $debugMaxLinesText)
                                .keyboardType(.numberPad)
                                .onChange(of: debugMaxLinesText) { _, newValue in
                                    if let v = Int(newValue), (50...5000).contains(v) {
                                        preferences.debugMaxLines = v
                                    }
                                }
                            Text(String(format: NSLocalizedString("summaryDebugSize", comment: ""), debugMaxLinesText))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Toggle(isOn: $preferences.debugAdvOnly) {
                            VStack(alignment: .leading) {
                                Text(NSLocalizedString("titleDebugAdvonly", comment: ""))
                                Text(NSLocalizedString("summaryDebugAdvonly", comment: ""))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text(NSLocalizedString("titleDebugCompanyIds", comment: ""))
                            TextField("0x01AB,0x01AC,...", text: $debugCompanyIdsText)
                                .keyboardType(.asciiCapable)
                                .autocapitalization(.none)
                                .onChange(of: debugCompanyIdsText) { _, newValue in
                                    preferences.debugCompanyIdsString = newValue
                                }
                            let display = debugCompanyIdsText.trimmingCharacters(in: .whitespaces).isEmpty
                                ? NSLocalizedString("none_in_parentheses", comment: "")
                                : debugCompanyIdsText
                            Text(String(format: NSLocalizedString("summaryDebugCompanyIds", comment: ""), display))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                // About
                Section(header: Text(NSLocalizedString("titleCategoryAbout", comment: ""))) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(NSLocalizedString("app_name", comment: ""))
                            .font(.headline)
                        Text(NSLocalizedString("summaryApp", comment: ""))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text(NSLocalizedString("titleMethod", comment: ""))
                            .font(.headline)
                        Text(NSLocalizedString("summaryMethod", comment: ""))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text(NSLocalizedString("titleLiability", comment: ""))
                            .font(.headline)
                        // Render a simplified version of the liability text
                        // (HTML rendering not needed on iOS — plain text summary)
                        Text(liabilityPlainText)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle(NSLocalizedString("settings", comment: ""))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                    }
                }
            }
            .onAppear {
                rssiText = "\(preferences.rssiThreshold)"
                cooldownText = "\(preferences.cooldownMs)"
                debugMaxLinesText = "\(preferences.debugMaxLines)"
                debugCompanyIdsText = preferences.debugCompanyIdsString
            }
        }
    }

    private var liabilityPlainText: String {
        """
        The app Nearby Glasses has one sole purpose: look for smart glasses nearby and warn you.

        The app's author (Yves Jeanrenaud) takes no liability whatsoever for this app, nor its functionality. Use at your own risk. By technical design, detecting Bluetooth LE devices might sometimes just not work as expected.

        False positives are likely. This means, the app may notify you of smart glasses nearby when there might be in fact a VR headset of the same manufacturer or another product.

        This app is and will always be free and open source (FOSS). Source code: https://github.com/yjeanrenaud/yj_nearbyglasses

        The app does not store any details about you or collect any information about you or your phone. There is no telemetry, no ads, and no other nuisance.

        Use with extreme caution! There is no guarantee detected smart glasses are really nearby.

        \u{26A0}\u{FE0F} WARNING! \u{26A0}\u{FE0F}
        HARASSING someone because you think they are wearing a covert surveillance device can be a criminal offence.
        \u{26A0}\u{FE0F} DO NOT HARASS ANYONE AT ALL \u{26A0}\u{FE0F}

        App Icon: Based on Eyeglass icons created by Freepik - Flaticon.

        Licensed under GNU AGPL 3.0.
        """
    }
}
