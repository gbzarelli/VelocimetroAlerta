package br.com.helpdev.velocimetroalerta.dialogs

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.DialogInterface
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.ListView

import java.io.IOException
import java.util.ArrayList

import br.com.helpdev.velocimetroalerta.R
import br.com.helpdev.velocimetroalerta.bluetooth.Bluetooth


class DialogHR(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs), BluetoothAdapter.LeScanCallback {

    override fun onLeScan(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray?) {
        devices.forEach({ d ->
            if (d.address == device.address) {
                return
            }
        })
        devices.add(MyDevice(device))
        (listView?.adapter as BaseAdapter).notifyDataSetInvalidated()
    }

    class MyDevice(private val blDevice: BluetoothDevice) {
        val name: String
            get() = blDevice.name
        val address: String
            get() = blDevice.address

        override fun toString(): String {
            return blDevice.name + "\n" + blDevice.address
        }
    }

    private val devices = ArrayList<MyDevice>()
    private var listView: ListView? = null

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        if (Bluetooth.isAdapterEnabled) {
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_config_hr, null)
            builder.setView(view)
            builder.setNegativeButton(R.string.bt_disable_module) { _, _ -> editor.putBoolean(key, false).apply() }

            try {

                listView = view.findViewById(R.id.listView)

                listView!!.adapter = ArrayAdapter<MyDevice>(context, android.R.layout.simple_list_item_1, devices)
                listView!!.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, id ->
                    editor.putBoolean(key, true).apply()
                    editor.putString(
                            context.getString(R.string.pref_hr_sensor_name),
                            devices[position].name).apply()
                    editor.putString(
                            context.getString(R.string.pref_hr_sensor_address),
                            devices[position].address).apply()
                    dialog.dismiss()
                }

                Bluetooth.bluetoothAdapter.startLeScan(this)
            } catch (e: IOException) {
                e.printStackTrace()
            }

        } else {
            builder.setMessage(R.string.disabled_bluetooth)
            builder.setPositiveButton(R.string.bt_enable) { _, _ -> Bluetooth.enableAdapter() }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        Bluetooth.bluetoothAdapter.stopLeScan(this)
        onPreferenceChangeListener?.onPreferenceChange(this, summary)
        super.onDismiss(dialog)
    }

    override fun getSummary(): CharSequence {
        val status = sharedPreferences.getBoolean(key, false)
        var valueToSummary: String? = context.getString(R.string.disable)
        if (status) {
            valueToSummary = sharedPreferences.getString(context.getString(R.string.pref_hr_sensor_name), context.getString(R.string.disable))
        }
        return valueToSummary.toString()
    }

}
