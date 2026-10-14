package br.com.helpdev.velocimetroalerta.dialogs

import android.app.Activity
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import br.com.helpdev.velocimetroalerta.R
import br.com.helpdev.velocimetroalerta.bluetooth.Bluetooth
import br.com.helpdev.velocimetroalerta.permissions.RuntimePermissionPolicy
import java.io.IOException

class DialogPrefVelAlertBLModule(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    override fun onClick() {
        val activity = context as? Activity ?: return
        val requiredPermissions = RuntimePermissionPolicy.permissionsFor(
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
        openDevicePicker()
    }

    fun onBluetoothPermissionResult(granted: Boolean) {
        if (granted) {
            openDevicePicker()
        } else {
            android.widget.Toast.makeText(context, R.string.bluetooth_permission_limited, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openDevicePicker() {
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

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_velalert_bluetooth, null)
        val list = view.findViewById<ListView>(R.id.listView)
        val bondedDevices = try {
            Bluetooth.bondedDevices(context)
        } catch (_: IOException) {
            emptyList()
        } catch (_: SecurityException) {
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

    private fun requestEnableBluetooth() {
        val activity = context as? Activity ?: return
        activity.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
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

    companion object {
        const val REQUEST_BLUETOOTH_PERMISSIONS = 302
    }
}
