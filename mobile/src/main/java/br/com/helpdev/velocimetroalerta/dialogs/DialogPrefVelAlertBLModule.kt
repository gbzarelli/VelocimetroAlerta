package br.com.helpdev.velocimetroalerta.dialogs

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import br.com.helpdev.velocimetroalerta.R
import br.com.helpdev.velocimetroalerta.bluetooth.Bluetooth
import java.io.IOException

class DialogPrefVelAlertBLModule(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    override fun onClick() {
        if (!Bluetooth.isAdapterEnabled) {
            AlertDialog.Builder(context)
                .setMessage(R.string.disabled_bluetooth)
                .setPositiveButton(R.string.bt_enable) { _, _ -> Bluetooth.enableAdapter() }
                .show()
            return
        }

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_velalert_bluetooth, null)
        val list = view.findViewById<ListView>(R.id.listView)
        val bondedDevices = try {
            Bluetooth.bondedDevices
        } catch (_: IOException) {
            emptyList()
        }
        val names = bondedDevices.map { it.name.orEmpty() }
        list.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, names)
        list.emptyView = view.findViewById(R.id.emptyView)

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setNegativeButton(R.string.bt_disable_module) { _, _ -> saveBoolean(false) }
            .create()
        list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selected = bondedDevices[position]
            saveBoolean(true)
            persistString(context.getString(R.string.pref_module_vel_alert_name), selected.name)
            persistString(context.getString(R.string.pref_module_vel_alert_address), selected.address)
            dialog.dismiss()
        }
        dialog.setOnDismissListener {
            onPreferenceChangeListener?.onPreferenceChange(this, summary)
        }
        dialog.show()
    }

    override fun getSummary(): CharSequence {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return if (preferences.getBoolean(key, false)) {
            preferences.getString(
                context.getString(R.string.pref_module_vel_alert_name),
                context.getString(R.string.disable)
            ).orEmpty()
        } else {
            context.getString(R.string.disable)
        }
    }

    private fun saveBoolean(value: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(key, value).apply()
    }

    private fun persistString(key: String, value: String?) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(key, value).apply()
    }
}
