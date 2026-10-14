package br.com.helpdev.velocimetroalerta

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import br.com.helpdev.velocimetroalerta.dialogs.DialogHR

import br.com.helpdev.velocimetroalerta.dialogs.DialogPrefVelAlertBLModule

/**
 * Created by Guilherme Biff Zarelli on 05/04/16.
 */
class ConfigActivityFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.configs, rootKey)
        val listPreference = requireNotNull(findPreference<ListPreference>(getString(R.string.pref_intervalo)))
        val editTextPreference = requireNotNull(findPreference<EditTextPreference>(getString(R.string.pref_intervalo_valor)))
        val dialogPrefVelAlertModule = requireNotNull(findPreference<DialogPrefVelAlertBLModule>(getString(R.string.pref_module_vel_alert)))
        val dialogPrefHr = requireNotNull(findPreference<DialogHR>(getString(R.string.pref_hr_sensor)))
        editSummary(listPreference)
        editSummary(editTextPreference)
        editSummary(dialogPrefVelAlertModule)
        editSummary(dialogPrefHr)
    }


    private fun editSummary(preference: Preference) {
        preference.onPreferenceChangeListener = this
        val pref = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val value = pref.getString(preference.key, "")
        onPreferenceChange(preference, value)
    }

    private fun editSummary(preference: DialogPrefVelAlertBLModule) {
        preference.onPreferenceChangeListener = this
        onPreferenceChange(preference, preference.summary)
    }

    private fun editSummary(preference: DialogHR) {
        preference.onPreferenceChangeListener = this
        onPreferenceChange(preference, preference.summary)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        preference.summary = newValue.toString()
        return true
    }
}

