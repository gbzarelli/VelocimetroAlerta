package br.com.helpdev.velocimetroalerta.dialogs

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import br.com.helpdev.velocimetroalerta.R
import br.com.helpdev.velocimetroalerta.bluetooth.Bluetooth

class DialogHR(context: Context, attrs: AttributeSet) : Preference(context, attrs), BluetoothAdapter.LeScanCallback {

    private val devices = mutableListOf<MyDevice>()
    private var listView: ListView? = null
    private var scanningAdapter: BluetoothAdapter? = null

    override fun onClick() {
        if (!Bluetooth.isAdapterEnabled) {
            AlertDialog.Builder(context)
                .setMessage(R.string.disabled_bluetooth)
                .setPositiveButton(R.string.bt_enable) { _, _ -> Bluetooth.enableAdapter() }
                .show()
            return
        }

        devices.clear()
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_config_hr, null)
        val list = view.findViewById<ListView>(R.id.listView)
        listView = list
        list.adapter = ArrayAdapter<MyDevice>(context, android.R.layout.simple_list_item_1, devices)

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
                .setNegativeButton(R.string.bt_disable_module) { _, _ -> saveBoolean(false) }
            .create()

        list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selected = devices[position]
            saveBoolean(true)
            persistString(context.getString(R.string.pref_hr_sensor_name), selected.name)
            persistString(context.getString(R.string.pref_hr_sensor_address), selected.address)
            dialog.dismiss()
        }
        dialog.setOnDismissListener {
            scanningAdapter?.stopLeScan(this)
            scanningAdapter = null
            onPreferenceChangeListener?.onPreferenceChange(this, summary)
        }
        dialog.show()

        scanningAdapter = Bluetooth.bluetoothAdapter
        scanningAdapter?.startLeScan(this)
    }

    override fun onLeScan(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray?) {
        if (devices.any { it.address == device.address }) return
        devices.add(MyDevice(device))
        listView?.post { (listView?.adapter as? BaseAdapter)?.notifyDataSetChanged() }
    }

    override fun getSummary(): CharSequence {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return if (preferences.getBoolean(key, false)) {
            preferences.getString(
                context.getString(R.string.pref_hr_sensor_name),
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

    class MyDevice(private val device: BluetoothDevice) {
        val name: String
            get() = device.name.orEmpty()
        val address: String
            get() = device.address

        override fun toString(): String = "$name\n$address"
    }
}
