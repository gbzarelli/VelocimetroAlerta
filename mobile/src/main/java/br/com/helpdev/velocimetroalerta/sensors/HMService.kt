package br.com.helpdev.velocimetroalerta.sensors

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Binder
import android.os.IBinder
import android.util.Log
import br.com.helpdev.velocimetroalerta.bluetooth.Bluetooth


class HMService : Service(), HMGatt.HMGattCallback {

    companion object {
        const val LOG = "HMService"
    }

    inner class HMBinder internal constructor(private val hmService: HMService) : Binder() {
        fun getHeartHate(): Int {
            return lastHeartHate
        }

        fun start(macAddress: String) {
            hmService.start(macAddress)
        }

        fun stop() {
            hmService.stop()
        }

        fun setStatusListener(listener: SensorStatusListener?) {
            hmService.statusListener = listener
        }
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var hmGatt: HMGatt? = null
    private var lastHeartHate: Int = 0
    private var macAddress: String? = null
    private var bluetoothStateReceiver: BroadcastReceiver? = null
    @Volatile
    internal var statusListener: SensorStatusListener? = null

    fun start(macAddress: String) {
        this.macAddress = macAddress

        hmGatt?.disconnectDevice()
        hmGatt = null
        connectOrWaitForBluetooth()
    }

    private fun connectOrWaitForBluetooth() {
        if (!Bluetooth.hasPermission(this, br.com.helpdev.velocimetroalerta.permissions.RuntimePermissionPolicy.Feature.BLUETOOTH_CONNECT)) {
            reportStatus(SensorConnectionState.PERMISSION_DENIED)
            return
        }
        bluetoothAdapter = Bluetooth.adapter(this)
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.w(LOG, "This device does not support Bluetooth")
            reportStatus(SensorConnectionState.UNSUPPORTED)
            return
        }
        if (!Bluetooth.isAdapterEnabled(this)) {
            Log.i(LOG, "Bluetooth is disabled; waiting for it to turn on")
            reportStatus(SensorConnectionState.BLUETOOTH_DISABLED)
            registerBluetoothStateReceiver()
            return
        }
        unregisterBluetoothStateReceiver()

        val address = macAddress ?: return
        reportStatus(SensorConnectionState.CONNECTING)
        bluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (error: IllegalArgumentException) {
            Log.w(LOG, "Invalid heart-rate sensor address", error)
            reportStatus(SensorConnectionState.CONNECTION_FAILED)
            return
        }

        if (bluetoothDevice == null) {
            Log.d(LOG, "BluetoothDevice == null")
        } else {
            hmGatt = HMGatt(this, bluetoothDevice!!, adapter, this, true)
            hmGatt!!.connectToDevice()
        }
    }

    private fun registerBluetoothStateReceiver() {
        if (bluetoothStateReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (BluetoothAdapter.ACTION_STATE_CHANGED != intent.action) return
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON) {
                    unregisterBluetoothStateReceiver()
                    hmGatt?.disconnectDevice()
                    hmGatt = null
                    reportStatus(SensorConnectionState.RECONNECTING)
                    connectOrWaitForBluetooth()
                }
            }
        }
        bluetoothStateReceiver = receiver
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun unregisterBluetoothStateReceiver() {
        val receiver = bluetoothStateReceiver ?: return
        try {
            unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
        bluetoothStateReceiver = null
    }

    private fun stop() {
        unregisterBluetoothStateReceiver()
        hmGatt?.disconnectDevice()
        hmGatt = null
        lastHeartHate = 0
    }

    private fun reportStatus(state: SensorConnectionState) {
        if (state != SensorConnectionState.CONNECTED) lastHeartHate = 0
        statusListener?.onSensorStatusChanged(SensorKind.HEART_RATE, state)
    }


    override fun onBind(intent: Intent?): IBinder? {
        return HMBinder(this)
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }


    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    override fun heartRate(heartRate: Int) {
        lastHeartHate = heartRate
    }

    override fun statusHMGatt(status: SensorConnectionState) {
        Log.d(LOG, "Heart-rate sensor state changed: $status")
        reportStatus(status)
    }

}
