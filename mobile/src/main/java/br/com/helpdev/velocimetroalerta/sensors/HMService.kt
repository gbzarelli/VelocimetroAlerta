package br.com.helpdev.velocimetroalerta.sensors

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log


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
            hmGatt?.disconnectDevice()
        }
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var hmGatt: HMGatt? = null
    private var lastHeartHate: Int = 0
    private var macAddress: String? = null

    fun start(macAddress: String) {
        this.macAddress = macAddress

        if (hmGatt != null) {
            hmGatt!!.disconnectDevice()
        }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothAdapter!!.enable()
        bluetoothDevice = bluetoothAdapter!!.getRemoteDevice(macAddress)

        if (bluetoothDevice == null) {
            Log.d(LOG, "BluetoothDevice == null")
        } else {
            hmGatt = HMGatt(this, bluetoothDevice!!, bluetoothAdapter!!, this, true)
            hmGatt!!.connectToDevice()
        }
    }


    override fun onBind(intent: Intent?): IBinder? {
        return HMBinder(this)
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }


    override fun onDestroy() {
        hmGatt?.disconnectDevice()
        super.onDestroy()
    }

    override fun heartRate(heartRate: Int) {
        lastHeartHate = heartRate
    }

    override fun statusHMGatt(status: Int) {
        //TODO
    }

}