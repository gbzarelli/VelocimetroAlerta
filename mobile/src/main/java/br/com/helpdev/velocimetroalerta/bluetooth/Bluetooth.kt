package br.com.helpdev.velocimetroalerta.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import br.com.helpdev.velocimetroalerta.permissions.RuntimePermissionPolicy
import java.io.IOException

object Bluetooth {
    fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    fun isSupported(context: Context): Boolean = adapter(context) != null

    fun hasPermission(context: Context, feature: RuntimePermissionPolicy.Feature): Boolean =
        RuntimePermissionPolicy.permissionsFor(feature, Build.VERSION.SDK_INT).all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission")
    fun isAdapterEnabled(context: Context): Boolean {
        if (!hasPermission(context, RuntimePermissionPolicy.Feature.BLUETOOTH_CONNECT)) return false
        return adapter(context)?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    fun bondedDevices(context: Context): List<BluetoothDevice> {
        if (!hasPermission(context, RuntimePermissionPolicy.Feature.BLUETOOTH_CONNECT)) {
            throw SecurityException("Bluetooth connect permission is required to read paired devices")
        }
        return adapter(context)?.bondedDevices?.toList().orEmpty()
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(context: Context, mac: String, password: String): Boolean {
        if (!hasPermission(context, RuntimePermissionPolicy.Feature.BLUETOOTH_SCAN)) return false
        val adapter = adapter(context) ?: return false
        return startDiscovery(context, adapter, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) {
                if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == intent.action) {
                    stopDiscovery(context, adapter, this)
                    return
                }
                if (BluetoothDevice.ACTION_FOUND != intent.action) return
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                if (device.address.equals(mac, ignoreCase = true)) {
                    stopDiscovery(context, adapter, this)
                    pairDevice(context, device, password)
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(context: Context, device: BluetoothDevice, password: String): Boolean {
        if (!hasPermission(context, RuntimePermissionPolicy.Feature.BLUETOOTH_CONNECT)) return false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) {
                if (BluetoothDevice.ACTION_PAIRING_REQUEST != intent.action) return
                val pairingDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                if (pairingDevice.address == device.address) {
                    pairingDevice.setPin(password.toByteArray())
                    unregisterReceiverSafely(context, this)
                }
            }
        }
        registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST))
        return try {
            device.createBond()
        } catch (error: SecurityException) {
            unregisterReceiverSafely(context, receiver)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(context: Context, adapter: BluetoothAdapter, receiver: BroadcastReceiver): Boolean {
        if (!hasPermission(context, RuntimePermissionPolicy.Feature.BLUETOOTH_SCAN)) return false
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(context, receiver, filter)
        val started = try {
            adapter.startDiscovery()
        } catch (_: SecurityException) {
            false
        }
        if (!started) unregisterReceiverSafely(context, receiver)
        return started
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery(context: Context, adapter: BluetoothAdapter, receiver: BroadcastReceiver): Boolean {
        unregisterReceiverSafely(context, receiver)
        return try {
            adapter.cancelDiscovery()
        } catch (_: SecurityException) {
            false
        }
    }

    private fun registerReceiver(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    private fun unregisterReceiverSafely(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
    }
}
