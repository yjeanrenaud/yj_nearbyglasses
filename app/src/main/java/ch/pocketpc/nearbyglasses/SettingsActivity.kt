package ch.pocketpc.nearbyglasses

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
//import androidx.core.text.HtmlCompat
import androidx.preference.EditTextPreference
//import android.widget.EditText
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.ListPreference
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            val cooldownPref = findPreference<EditTextPreference>("cooldown_ms")
            val rssiPref = findPreference<EditTextPreference>("rssi_threshold")
            val debugIdsPref = findPreference<EditTextPreference>("debug_company_ids")
            val debugMaxLinesPref = findPreference<EditTextPreference>("debug_max_lines")

            fun refreshSummaries() {
                cooldownPref?.summary = getString(
                    R.string.summaryCooldown,
                    cooldownPref?.text ?: "10000"
                )
                rssiPref?.summary = getString(
                    R.string.summaryThreshold,
                    rssiPref?.text ?: "-75"
                )
                debugMaxLinesPref?.summary = getString(
                    R.string.summaryDebugSize,
                    debugMaxLinesPref?.text ?: "200"
                )

                val ids = debugIdsPref?.text?.trim().orEmpty()
                debugIdsPref?.summary = getString(
                    R.string.summaryDebugCompanyIds,
                    //if (ids.isBlank()) "(none)" else ids
                    if (ids.isBlank()) getString(R.string.none_in_parentheses) else ids
                )
            }

            // numeric input
            cooldownPref?.setOnBindEditTextListener { editText ->
                editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                editText.setSingleLine(true)
                editText.post {editText.setSelection(editText.text.length)}
            }
            rssiPref?.setOnBindEditTextListener { editText ->
                editText.inputType =
                    android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                editText.setSingleLine(true)
                editText.post {editText.setSelection(editText.text.length)}
            }
            debugMaxLinesPref?.setOnBindEditTextListener { editText ->
                editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                editText.setSingleLine(true)
                editText.post {editText.setSelection(editText.text.length)}
            }
            debugIdsPref?.setOnBindEditTextListener { editText ->
                editText.hint = "0x01AB,0x01AC,..."
                editText.setSingleLine(true)
                editText.post {editText.setSelection(editText.text.length)}
            }

            // validation + live summary update
            cooldownPref?.setOnPreferenceChangeListener { pref, newValue ->
                val s = (newValue as? String)?.trim().orEmpty()
                val v = s.toLongOrNull()
                val ok = v != null && v in 0..600_000L
                if (ok) {
                    pref.summary = getString(R.string.summaryCooldown, s)
                }
                ok
            }

            rssiPref?.setOnPreferenceChangeListener { pref, newValue ->
                val s = (newValue as? String)?.trim().orEmpty()
                val v = s.toIntOrNull()
                val ok = v != null && v in -120..0
                if (ok) {
                    pref.summary = getString(R.string.summaryThreshold, s)
                }
                ok
            }

            debugMaxLinesPref?.setOnPreferenceChangeListener { pref, newValue ->
                val s = (newValue as? String)?.trim().orEmpty()
                val v = s.toIntOrNull()
                val ok = v != null && v in 50..5000
                if (ok) {
                    pref.summary = getString(R.string.summaryDebugSize, s)
                }
                ok
            }

            debugIdsPref?.setOnPreferenceChangeListener { pref, newValue ->
                val s = (newValue as? String)?.trim().orEmpty()
                pref.summary = getString(
                    R.string.summaryDebugCompanyIds,
                    //if (s.isBlank()) "(none)" else s
                    if (s.isBlank()) getString(R.string.none_in_parentheses) else s
                )
                true
            }
            // now get current language
            val languagePref = findPreference<ListPreference>("app_language")
            //val currentLang = AppCompatDelegate.getApplicationLocales().toLanguageTags() // Get current app language
            val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            val normalized = if (tags.isBlank()) "" else tags.toString() // tags.substringBefore(',').substringBefore('-')  // "fr-FR" -> "fr"; make sure there is ""
            languagePref?.value = normalized //= currentLang // Set selected value, if emtpy, sets to system default)

//            languagePref?.setOnPreferenceChangeListener { _, newValue ->
//                val langTag = newValue as String
//                applyAppLanguage(langTag)
//                true
//            }
            languagePref?.setOnPreferenceChangeListener { pref, newValue ->
                val langTag = (newValue as? String).orEmpty()
                // Apply locales
                applyAppLanguage(langTag)

                // Recreate on next loop tick (lets preference UI finish closing cleanly)
                Handler(Looper.getMainLooper()).post {
                    requireActivity().recreate()
                }

                true // let ListPreference do the magic
            }

            // set initial summaries
            refreshSummaries()
        }

        private fun applyAppLanguage(tag: String) {
            val locales = if (tag.isBlank()) {
                LocaleListCompat.getEmptyLocaleList() // follow system
            } else {
                LocaleListCompat.forLanguageTags(tag) // e.g. "de"
            }
            AppCompatDelegate.setApplicationLocales(locales)
            //requireActivity().recreate() // to make sure they update
        }
    }
}
