package br.com.helpdev.velocimetroalerta.dialogs

import android.app.Activity
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import br.com.helpdev.velocimetroalerta.R
import br.com.helpdev.velocimetroalerta.bluetooth.Bluetooth
import br.com.helpdev.velocimetroalerta.permissions.RuntimePermissionPolicy

class DialogHR(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    private val devices = mutableListOf<MyDevice>()
    private var listView: ListView? = null
    private var scanner: BluetoothLeScanner? = null
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addDevice(result.device)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { addDevice(it.device) }
        }
    }

    override fun onClick() {
        val activity = context as? Activity ?: return
        val requiredPermissions = RuntimePermissionPolicy.permissionsFor(
            RuntimePermissionPolicy.Feature.BLUETOOTH_SCAN,
            Build.VERSION.SDK_INT
        ) + RuntimePermissionPolicy.permissionsFor(
            RuntimePermissionPolicy.Feature.BLUETOOTH_CONNECT,
            Build.VERSION.SDK_INT
        )
        val missingPermissions = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missingPermissions.toTypedArray(), REQUEST_BLUETOOTH_PERMISSIONS)
            return
        }
        openScanner()
    }

    fun onBluetoothPermissionResult(granted: Boolean) {
        if (granted) {
            openScanner()
        } else {
            android.widget.Toast.makeText(context, R.string.bluetooth_permission_limited, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openScanner() {
        if (!Bluetooth.isSupported(context)) {
            android.widget.Toast.makeText(context, R.string.bluetooth_not_supported, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (!Bluetooth.isAdapterEnabled(context)) {
            AlertDialog.Builder(context)
                .setMessage(R.string.disabled_bluetooth)
                .setPositiveButton(R.string.bt_enable) { _, _ -> requestEnableBluetooth() }
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
            try {
                scanner?.stopScan(scanCallback)
            } catch (_: SecurityException) {
            }
            scanner = null
            onPreferenceChangeListener?.onPreferenceChange(this, summary)
        }
        dialog.show()

        scanner = Bluetooth.adapter(context)?.bluetoothLeScanner
        if (scanner == null) {
            dialog.dismiss()
            android.widget.Toast.makeText(context, R.string.bluetooth_not_supported, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        try {
            scanner?.startScan(scanCallback)
        } catch (_: SecurityException) {
            dialog.dismiss()
            onBluetoothPermissionResult(false)
        }
    }

    private fun requestEnableBluetooth() {
        val activity = context as? Activity ?: return
        activity.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    private fun addDevice(device: BluetoothDevice) {
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

    companion object {
        const val REQUEST_BLUETOOTH_PERMISSIONS = 301
    }
}
