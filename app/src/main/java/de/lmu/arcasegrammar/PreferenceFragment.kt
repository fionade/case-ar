package de.lmu.arcasegrammar

import android.os.Bundle
import androidx.navigation.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class PreferenceFragment: PreferenceFragmentCompat() {


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val infoPreference: Preference? = findPreference("info")
        infoPreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            view?.findNavController()?.navigate(R.id.navigation_prefs_info)
            true
        }
    }

}