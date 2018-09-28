package br.com.helpdev.velocimetroalerta

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import br.com.helpdev.velocimetroalerta.dialogs.DialogHR

import br.com.helpdev.velocimetroalerta.dialogs.DialogPrefVelAlertBLModule

/**
 * Created by Guilherme Biff Zarelli on 05/04/16.
 */
class ConfigActivityFragment : PreferenceFragment(), Preference.OnPreferenceChangeListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.configs)
        val listPreference = findPreference(getString(R.string.pref_intervalo)) as ListPreference
        val editTextPreference = findPreference(getString(R.string.pref_intervalo_valor)) as EditTextPreference
        val dialogPrefVelAlertModule = findPreference(getString(R.string.pref_module_vel_alert)) as DialogPrefVelAlertBLModule
        val dialogPrefHr = findPreference(getString(R.string.pref_hr_sensor)) as DialogHR
        editSummary(listPreference)
        editSummary(editTextPreference)
        editSummary(dialogPrefVelAlertModule)
        editSummary(dialogPrefHr)
    }


    private fun editSummary(preference: Preference) {
        preference.onPreferenceChangeListener = this
        val pref = PreferenceManager.getDefaultSharedPreferences(activity)
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

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        preference.summary = newValue.toString()
        return true
    }
}

