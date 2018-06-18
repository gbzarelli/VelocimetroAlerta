package br.com.helpdev.velocimetroalerta.sensors

import android.bluetooth.*
import android.content.Context
import android.util.Log
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class HMGatt(private val context: Context,
             private val bluetoothDevice: BluetoothDevice,
             private val bluetoothAdapter: BluetoothAdapter,
             private val hmGattCallback: HMGattCallback,
             autoReconnect: Boolean) : BluetoothGattCallback() {

    companion object {
        /*
        0x180D / 0000180d-0000-1000-8000-00805f9b34fb - HEART_RATE_SERVICE_UUID
        0x2A37 / 00002a37-0000-1000-8000-00805f9b34fb - HEART_RATE_MEASUREMENT_CHAR_UUID
        0x2A39 / 00002a39-0000-1000-8000-00805f9b34fb - HEART_RATE_CONTROL_POINT_CHAR_UUID
        0x2902 / 00002902-0000-1000-8000-00805f9b34fb - CLIENT_CHARACTERISTIC_CONFIG_UUID
        */
        private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_MEASUREMENT_CHAR_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_CONTROL_POINT_CHAR_UUID = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var sThreadPool: ScheduledExecutorService? = null
    private var gatt: BluetoothGatt? = null

    init {
        if (autoReconnect) {
            sThreadPool = Executors.newSingleThreadScheduledExecutor()
        }
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        Log.d(HMService.LOG, "onConnectionStateChange - $newState")
        if (BluetoothAdapter.STATE_CONNECTED == newState) {
            gatt.discoverServices()
        } else if (BluetoothAdapter.STATE_DISCONNECTED == newState) {
            connectToDevice()
        }
    }

    fun connectToDevice() {
        Log.d(HMService.LOG, "connectToDevice")

        gatt = if (bluetoothAdapter.isEnabled) {
            bluetoothDevice.connectGatt(context, false, this)
        } else {
            null
        }

        if (gatt == null) {
            Log.d(HMService.LOG, "connectToDevice: gatt is null or bluetooth is disable")
            sThreadPool?.schedule({ connectToDevice() }, 10, TimeUnit.SECONDS)
        }
    }

    fun disconnectDevice() {
        Log.d(HMService.LOG, "disconnectDevice")
        sThreadPool?.shutdownNow()
        gatt?.disconnect()
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        Log.d(HMService.LOG, "onServicesDiscovered - $status")

        val characteristic = gatt.getService(HEART_RATE_SERVICE_UUID).getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)
        if (characteristic == null) {
            Log.w(HMService.LOG, "Characteristic is null")
            return
        }

        gatt.setCharacteristicNotification(characteristic, true)

        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        Log.d(HMService.LOG, "onDescriptorWrite - $status")
        val characteristic = gatt.getService(HEART_RATE_SERVICE_UUID).getCharacteristic(HEART_RATE_CONTROL_POINT_CHAR_UUID)
        characteristic.value = byteArrayOf(1, 1)
        gatt.writeCharacteristic(characteristic)
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (HEART_RATE_MEASUREMENT_CHAR_UUID == characteristic.uuid) {
            val flag = characteristic.properties
            val format: Int
            format = if (flag and 0x01 == 0) {
                BluetoothGattCharacteristic.FORMAT_UINT8
            } else {
                BluetoothGattCharacteristic.FORMAT_UINT16
            }
            val heartRate = characteristic.getIntValue(format, 1)
            Log.d(HMService.LOG, String.format("Received heart rate: %d", heartRate))
            hmGattCallback.heartRate(heartRate)
        } else {
            val data = characteristic.value
            if (data != null && data.isNotEmpty()) {
                val stringBuilder = StringBuilder(data.size)
                for (byteChar in data)
                    stringBuilder.append(String.format("%02X ", byteChar))
                Log.d(HMService.LOG, stringBuilder.toString())
            } else {
                Log.d(HMService.LOG, "Data is null or empty")
            }
        }
    }

    interface HMGattCallback {
        fun heartRate(heartRate: Int)
        fun statusHMGatt(status: Int)
    }
}