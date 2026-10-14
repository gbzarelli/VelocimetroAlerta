package br.com.helpdev.velocimetroalerta.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class HMGatt(
    private val context: Context,
    private val bluetoothDevice: BluetoothDevice,
    private val bluetoothAdapter: BluetoothAdapter,
    private val hmGattCallback: HMGattCallback,
    autoReconnect: Boolean
) : BluetoothGattCallback() {

    companion object {
        private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_MEASUREMENT_CHAR_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_CONTROL_POINT_CHAR_UUID = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val RECONNECT_DELAY_SECONDS = 10L
        private const val LOG = "HMGatt"
    }

    private val reconnectExecutor: ScheduledExecutorService? =
        if (autoReconnect) Executors.newSingleThreadScheduledExecutor() else null
    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var reconnectScheduled = false
    @Volatile private var stopped = false

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        Log.d(LOG, "onConnectionStateChange - status=$status state=$newState")
        if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
            this.gatt = gatt
            hmGattCallback.statusHMGatt(SensorConnectionState.CONNECTED)
            if (!hasConnectPermission()) {
                hmGattCallback.statusHMGatt(SensorConnectionState.PERMISSION_DENIED)
                return
            }
            try {
                gatt.discoverServices()
            } catch (_: SecurityException) {
                hmGattCallback.statusHMGatt(SensorConnectionState.PERMISSION_DENIED)
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
            closeGatt(gatt)
            if (!stopped) {
                hmGattCallback.statusHMGatt(SensorConnectionState.RECONNECTING)
                scheduleReconnect()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice() {
        if (stopped) return
        if (!hasConnectPermission()) {
            hmGattCallback.statusHMGatt(SensorConnectionState.PERMISSION_DENIED)
            return
        }
        val adapterEnabled = try {
            bluetoothAdapter.isEnabled
        } catch (_: SecurityException) {
            hmGattCallback.statusHMGatt(SensorConnectionState.PERMISSION_DENIED)
            return
        }
        if (!adapterEnabled) {
            hmGattCallback.statusHMGatt(SensorConnectionState.BLUETOOTH_DISABLED)
            scheduleReconnect()
            return
        }

        hmGattCallback.statusHMGatt(SensorConnectionState.CONNECTING)
        try {
            closeGatt(gatt)
            gatt = bluetoothDevice.connectGatt(context, false, this)
            if (gatt == null) {
                hmGattCallback.statusHMGatt(SensorConnectionState.CONNECTION_FAILED)
                scheduleReconnect()
            }
        } catch (_: SecurityException) {
            hmGattCallback.statusHMGatt(SensorConnectionState.PERMISSION_DENIED)
        } catch (error: RuntimeException) {
            Log.w(LOG, "Unable to connect to heart-rate sensor", error)
            hmGattCallback.statusHMGatt(SensorConnectionState.CONNECTION_FAILED)
            scheduleReconnect()
        }
    }

    fun disconnectDevice() {
        stopped = true
        reconnectExecutor?.shutdownNow()
        closeGatt(gatt)
        gatt = null
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            hmGattCallback.statusHMGatt(SensorConnectionState.CONNECTION_FAILED)
            scheduleReconnect()
            return
        }
        if (!hasConnectPermission()) {
            hmGattCallback.statusHMGatt(SensorConnectionState.PERMISSION_DENIED)
            return
        }

        try {
            val service = gatt.getService(HEART_RATE_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)
            val descriptor = characteristic?.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (characteristic == null || descriptor == null) {
                Log.w(LOG, "Heart-rate measurement characteristic or descriptor is missing")
                hmGattCallback.statusHMGatt(SensorConnectionState.CONNECTION_FAILED)
                closeGatt(gatt)
                scheduleReconnect()
                return
            }
            gatt.setCharacteristicNotification(characteristic, true)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (!gatt.writeDescriptor(descriptor)) {
                hmGattCallback.statusHMGatt(SensorConnectionState.CONNECTION_FAILED)
                scheduleReconnect()
            }
        } catch (_: SecurityException) {
            hmGattCallback.statusHMGatt(SensorConnectionState.PERMISSION_DENIED)
        }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS || !hasConnectPermission()) {
            hmGattCallback.statusHMGatt(
                if (!hasConnectPermission()) SensorConnectionState.PERMISSION_DENIED
                else SensorConnectionState.CONNECTION_FAILED
            )
            if (status != BluetoothGatt.GATT_SUCCESS) scheduleReconnect()
            return
        }
        try {
            gatt.getService(HEART_RATE_SERVICE_UUID)
                ?.getCharacteristic(HEART_RATE_CONTROL_POINT_CHAR_UUID)
                ?.let { characteristic ->
                    characteristic.value = byteArrayOf(1, 1)
                    gatt.writeCharacteristic(characteristic)
                }
        } catch (_: SecurityException) {
            hmGattCallback.statusHMGatt(SensorConnectionState.PERMISSION_DENIED)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        onHeartRateValue(characteristic, characteristic.value)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        onHeartRateValue(characteristic, value)
    }

    private fun onHeartRateValue(characteristic: BluetoothGattCharacteristic, value: ByteArray?) {
        if (HEART_RATE_MEASUREMENT_CHAR_UUID != characteristic.uuid || value == null || value.isEmpty()) return
        val format = if (value[0].toInt() and 0x01 == 0) {
            BluetoothGattCharacteristic.FORMAT_UINT8
        } else {
            BluetoothGattCharacteristic.FORMAT_UINT16
        }
        val heartRate = characteristic.getIntValue(format, 1) ?: return
        Log.d(LOG, "Received heart rate: $heartRate")
        hmGattCallback.heartRate(heartRate)
    }

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun closeGatt(target: BluetoothGatt?) {
        if (target == null || !hasConnectPermission()) return
        try {
            target.disconnect()
        } catch (_: SecurityException) {
        }
        try {
            target.close()
        } catch (_: SecurityException) {
        }
    }

    private fun scheduleReconnect() {
        val executor = reconnectExecutor ?: return
        if (stopped || reconnectScheduled || executor.isShutdown) return
        reconnectScheduled = true
        executor.schedule({
            reconnectScheduled = false
            connectToDevice()
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS)
    }

    interface HMGattCallback {
        fun heartRate(heartRate: Int)
        fun statusHMGatt(status: SensorConnectionState)
    }
}
