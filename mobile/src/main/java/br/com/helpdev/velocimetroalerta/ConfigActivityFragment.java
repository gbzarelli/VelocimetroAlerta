package br.com.helpdev.velocimetroalerta;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import br.com.helpdev.velocimetroalerta.dialogs.DialogPrefVelAlertBLModule;

/**
 * Created by Guilherme Biff Zarelli on 05/04/16.
 */
public class ConfigActivityFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.configs);
        ListPreference listPreference = (ListPreference) findPreference(getString(R.string.pref_intervalo));
        EditTextPreference editTextPreference = (EditTextPreference) findPreference(getString(R.string.pref_intervalo_valor));
        DialogPrefVelAlertBLModule dialogPrefVelAlertModule = (DialogPrefVelAlertBLModule) findPreference(getString(R.string.pref_module_vel_alert));
        editSummary(listPreference);
        editSummary(editTextPreference);
        editSummary(dialogPrefVelAlertModule);
    }


    private void editSummary(Preference preference) {
        preference.setOnPreferenceChangeListener(this);
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        Object value = pref.getString(preference.getKey(), "");
        onPreferenceChange(preference, value);
    }

    private void editSummary(DialogPrefVelAlertBLModule preference) {
        preference.setOnPreferenceChangeListener(this);
        onPreferenceChange(preference, preference.getSummary());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        preference.setSummary(newValue.toString());
        return true;
    }
}

