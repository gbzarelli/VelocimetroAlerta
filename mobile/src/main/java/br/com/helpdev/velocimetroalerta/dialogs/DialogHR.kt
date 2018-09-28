package br.com.helpdev.velocimetroalerta.dialogs

import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView

import java.io.IOException
import java.util.ArrayList

import br.com.helpdev.velocimetroalerta.R
import br.com.helpdev.velocimetroalerta.bluetooth.Bluetooth

class DialogPrefVelAlertBLModule(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        if (Bluetooth.isAdapterEnabled) {
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_velalert_bluetooth, null)
            builder.setView(view)
            builder.setNegativeButton(R.string.bt_disable_module) { _, _ -> editor.putBoolean(key, false).apply() }

            try {
                val listView = view.findViewById<ListView>(R.id.listView)
                val bondedDevices = Bluetooth.bondedDevices
                val names = ArrayList<String>()
                for (bd in bondedDevices) {
                    names.add(bd.name)
                }
                listView.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, names)
                listView.emptyView = view.findViewById(R.id.emptyView)
                listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, id ->
                    editor.putBoolean(key, true).apply()
                    editor.putString(
                            context.getString(R.string.pref_module_vel_alert_name),
                            bondedDevices[position].name).apply()
                    editor.putString(
                            context.getString(R.string.pref_module_vel_alert_address),
                            bondedDevices[position].address).apply()
                    dialog.dismiss()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

        } else {
            builder.setMessage(R.string.disabled_bluetooth)
            builder.setPositiveButton(R.string.bt_enable) { _, _ -> Bluetooth.enableAdapter() }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        onPreferenceChangeListener?.onPreferenceChange(this, summary)
        super.onDismiss(dialog)
    }

    override fun getSummary(): CharSequence {
        val status = sharedPreferences.getBoolean(key, false)
        var valueToSummary: String? = context.getString(R.string.disable)
        if (status) {
            valueToSummary = sharedPreferences.getString(context.getString(R.string.pref_module_vel_alert_name), context.getString(R.string.disable))
        }
        return valueToSummary.toString()
    }

}
