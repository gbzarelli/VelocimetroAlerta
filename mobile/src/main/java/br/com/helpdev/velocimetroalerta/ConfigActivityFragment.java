package br.com.helpdev.velocimetroalerta;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

/**
 * Created by Guilherme Biff Zarelli on 05/04/16.
 */
public class ConfigActivityFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {

    private ListPreference listPreference;
    private EditTextPreference editTextPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.configuracoes);
        listPreference = (ListPreference) findPreference(getString(R.string.pref_intervalo));
        editTextPreference = (EditTextPreference) findPreference(getString(R.string.pref_intervalo_valor));
        preencherSumario(listPreference);
        preencherSumario(editTextPreference);
    }


    private void preencherSumario(Preference preference) {
        preference.setOnPreferenceChangeListener(this);
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        Object value = pref.getString(preference.getKey(), "");
        onPreferenceChange(preference, value);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.equals(editTextPreference)) {
            editTextPreference.setSummary(newValue.toString());
        } else if (preference.equals(listPreference)) {
            listPreference.setSummary(newValue.toString());
        }
        return true;
    }
}

