package br.com.helpdev.velocimetroalerta.bluetooth

/**
 * Created by Guilherme Biff Zarelli on 25/07/15.
 */

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.annotation.RequiresPermission

import java.io.IOException
import java.lang.reflect.Method
import java.util.ArrayList

/**
 *
 * @author Guilherme Biff Zarelli
 */
class Bluetooth private constructor() {

    init {
        throw RuntimeException("No Bluetooth!")
    }

    companion object {

        val bondedDevices: ArrayList<BluetoothDevice>
            @RequiresPermission(Manifest.permission.BLUETOOTH)
            @Throws(IOException::class)
            get() {
                val pairedDevices = bluetoothAdapter.bondedDevices
                return ArrayList(pairedDevices)
            }

        val isAdapterEnabled: Boolean
            @RequiresPermission(Manifest.permission.BLUETOOTH)
            get() = bluetoothAdapter.isEnabled

        @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
        fun enableAdapter(): Boolean {
            return bluetoothAdapter.enable()
        }

        val bluetoothAdapter: BluetoothAdapter
            get() = BluetoothAdapter.getDefaultAdapter()

        @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
        fun desabilitarAdapter() {
            bluetoothAdapter.disable()
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
        fun pairDevice(context: Context, mac: String, passwd: String): Boolean {
            return startDiscovery(context, object : BroadcastReceiver() {
                @SuppressLint("MissingPermission")
                override fun onReceive(context: Context, intent: Intent) {
                    if (BluetoothDevice.ACTION_FOUND == intent.action) {
                        val deviceFound = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (deviceFound.address == mac.toUpperCase()) {
                            pairDevice(context, deviceFound, passwd)
                            context.unregisterReceiver(this)
                        }
                    }
                }
            })
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
        fun pairDevice(context: Context, device: BluetoothDevice, passwd: String): Boolean {
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (BluetoothDevice.ACTION_PAIRING_REQUEST == intent.action) {
                        val deviceFound = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (deviceFound.address == device.address) {
                            deviceFound.setPin(passwd.toByteArray())
                            context.unregisterReceiver(this)
                        }
                    }
                }
            }, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            return device.createBond()
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
        fun cancelDiscovery(context: Context, broadcastReceiver: BroadcastReceiver): Boolean {
            try {
                context.unregisterReceiver(broadcastReceiver)
            } catch (t: Throwable) {
            }

            return bluetoothAdapter.cancelDiscovery()
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
        fun startDiscovery(context: Context, broadcastReceiver: BroadcastReceiver): Boolean {
            context.registerReceiver(broadcastReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
            if (!bluetoothAdapter.startDiscovery()) {
                context.unregisterReceiver(broadcastReceiver)
                return false
            }
            return true
        }
    }

}
