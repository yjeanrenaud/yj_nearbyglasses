/*
 * 2026, Yves Jeanrenaud https://github.com/yjeanrenaud/yj_nearbyglasses
 */
import SwiftUI
/*
 * generates the settings screen
 */
struct SettingsView: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var l10n: LanguageManager
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section(l10n.text("ios_scanning_section_title")) {
                    Stepper(value: Binding(
                        get: { settings.rssiThreshold },
                        set: { settings.rssiThreshold = min(0, max(-120, $0)) }
                    ), in: -120...0) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(l10n.text("titleThreshold"))
                            Text(l10n.text("summaryThreshold", String(settings.rssiThreshold)))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }

                    Stepper(value: Binding(
                        get: { settings.cooldownMs },
                        set: { settings.cooldownMs = min(600_000, max(0, $0)) }
                    ), in: 0...600_000, step: 1000) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(l10n.text("titleCooldown"))
                            Text(l10n.text("summaryCooldown", String(settings.cooldownMs)))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text(l10n.text("titleIgnoredDetections"))
                        TextField("0x058E, Meta, Ray-Ban", text: $settings.ignoredDetectionsText, axis: .vertical)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .font(.body.monospaced())
                        Text(l10n.text(
                            "summaryIgnoredDetections",
                            settings.ignoredDetectionsText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? l10n.text("none_in_parentheses") : settings.ignoredDetectionsText
                        ))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    }

                    Picker(l10n.text("pref_language_title"), selection: $settings.selectedLanguageRaw) {
                        ForEach(AppLanguage.allCases) { language in
                            Text(language.displayName).tag(language.rawValue)
                        }
                    }
                    Text(l10n.text("ios_settings_language_footer"))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section(l10n.text("ios_debug_section_title")) {
                    Toggle(isOn: $settings.debugEnabled) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(l10n.text("titleDebug"))
                            Text(l10n.text("summaryDebug"))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }

                    Toggle(isOn: $settings.debugAdvOnly) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(l10n.text("titleDebugAdvonly"))
                            Text(l10n.text("summaryDebugAdvonly"))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .disabled(!settings.debugEnabled)

                    Stepper(value: Binding(
                        get: { settings.debugMaxLines },
                        set: { settings.debugMaxLines = min(5000, max(50, $0)) }
                    ), in: 50...5000, step: 50) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(l10n.text("titleDebugSize"))
                            Text(l10n.text("summaryDebugSize", String(settings.debugMaxLines)))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .disabled(!settings.debugEnabled)

                    VStack(alignment: .leading, spacing: 8) {
                        Text(l10n.text("titleDebugCompanyIds"))
                        TextField("0x01AB,0x058E,0x0D53", text: $settings.debugCompanyIDsText, axis: .vertical)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .font(.body.monospaced())
                        Text(l10n.text(
                            "summaryDebugCompanyIds",
                            settings.debugCompanyIDsText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? l10n.text("none_in_parentheses") : settings.debugCompanyIDsText
                        ))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    }
                    .disabled(!settings.debugEnabled)

                    Text(l10n.text("ios_settings_debug_footer"))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section(l10n.text("titleCategoryAbout")) {
                    NavigationLink(destination: AboutView()) {
                        Text(l10n.text("titleLiability"))
                    }
                }
            }
            .navigationTitle(l10n.text("ios_settings_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(l10n.text("ios_done")) { dismiss() }
                }
            }
        }
    }
}
