package br.com.helpdev.velocimetroalerta.sensors

import android.app.Service
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

import java.io.EOFException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

import br.com.helpdev.velocimetroalerta.bluetooth.Bluetooth
import br.com.helpdev.velocimetroalerta.gpx.objects.TrackPointExtension

class VAMService : Service(), Runnable {

    @Volatile
    private var status = STOP
    private var bluetoothDevice: BluetoothDevice? = null
    private var addressModuleBT: String? = null
    @Volatile
    private var lastValueModule: TrackPointExtension = TrackPointExtension()
    @Volatile
    private var activeSocket: BluetoothSocket? = null
    @Volatile
    internal var statusListener: SensorStatusListener? = null
    @Volatile
    private var workerThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        status = STOP
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return Service.START_NOT_STICKY
    }

    inner class SensorsBinder internal constructor(private val sensorVAM: VAMService) : Binder() {

        fun getTrackPointExtension() = sensorVAM.lastValueModule

        fun getCadence() = sensorVAM.lastValueModule.cad

        fun getTemperature() = sensorVAM.lastValueModule.atemp

        fun getHumidity() = sensorVAM.lastValueModule.rhu

        fun start(macAddress: String) {
            sensorVAM.start(macAddress)
        }

        fun setStatusListener(listener: SensorStatusListener?) {
            sensorVAM.statusListener = listener
        }

        fun stop() {
            sensorVAM.stop()
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return SensorsBinder(this)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    @Synchronized
    fun start(macAddress: String) {
        addressModuleBT = macAddress
        if (status == STOP) {
            status = RUNNING
            Thread(this, "Thr-VelAlertBTModule").also {
                workerThread = it
                it.start()
            }
        } else {
            status = CHANGE_MODULE
            closeSocket()
        }
    }

    @Synchronized
    fun stop() {
        status = STOP
        closeSocket()
        workerThread?.interrupt()
        workerThread = null
        lastValueModule = TrackPointExtension()
    }


    override fun run() {
        var retryDelay = INITIAL_RETRY_DELAY_MS
        var connectedBefore = false
        val currentThread = Thread.currentThread()
        while (STOP != status && currentThread === workerThread) {
            try {
                if (!Bluetooth.hasPermission(this, br.com.helpdev.velocimetroalerta.permissions.RuntimePermissionPolicy.Feature.BLUETOOTH_CONNECT)) {
                    reportStatus(SensorConnectionState.PERMISSION_DENIED)
                    lastValueModule = TrackPointExtension()
                    status = STOP
                    workerThread = null
                    return
                }
                if (!Bluetooth.isSupported(this)) {
                    reportStatus(SensorConnectionState.UNSUPPORTED)
                    lastValueModule = TrackPointExtension()
                    status = STOP
                    workerThread = null
                    return
                }
                if (!Bluetooth.isAdapterEnabled(this)) {
                    Log.w(LOG, "Bluetooth is disabled; waiting before reconnecting the VAM sensor")
                    reportStatus(SensorConnectionState.BLUETOOTH_DISABLED)
                } else {
                    if (CHANGE_MODULE == status || null == bluetoothDevice) {
                        defineBluetoothDevice()
                        if (bluetoothDevice == null) throw IOException("Paired VAM sensor $addressModuleBT is unavailable")
                        status = RUNNING
                    }
                    val btSocket = bluetoothDevice!!.createInsecureRfcommSocketToServiceRecord(UUID.fromString(UUID_SERIAL_BL))
                    activeSocket = btSocket
                    try {
                        btSocket.use {
                            reportStatus(SensorConnectionState.CONNECTING)
                            btSocket.connect()
                            connectedBefore = true
                            reportStatus(SensorConnectionState.CONNECTED)
                            val frameReader = VamFrameReader(btSocket.inputStream)
                            while (RUNNING == status && currentThread === workerThread) {
                                val futureTask = FutureTask(Callable {
                                    btSocket.outputStream.write(REQUEST_VALUE)
                                    val frame = frameReader.readFrame()
                                        ?: throw EOFException("VAM sensor closed the RFCOMM socket")
                                    TrackPointExtensionJsonParser.parse(frame) ?: TrackPointExtension()
                                })
                                Thread(futureTask, "Thr-VelAlertBTFrame").start()
                                try {
                                    lastValueModule = futureTask.get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                    retryDelay = INITIAL_RETRY_DELAY_MS
                                } catch (t: Throwable) {
                                    futureTask.cancel(true)
                                    throw t
                                }
                                Thread.sleep(POLL_DELAY_MS)
                            }
                        }
                    } finally {
                        if (activeSocket === btSocket) activeSocket = null
                    }
                }
            } catch (t: Throwable) {
                if (STOP == status || currentThread !== workerThread) break
                Log.w(LOG, "VAM sensor disconnected; retrying", t)
                lastValueModule = TrackPointExtension()
                reportStatus(
                    if (connectedBefore) SensorConnectionState.RECONNECTING
                    else if (t is SecurityException) SensorConnectionState.PERMISSION_DENIED
                    else SensorConnectionState.CONNECTION_FAILED
                )
            }

            if (RUNNING == status) {
                try {
                    Thread.sleep(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                } catch (_: InterruptedException) {
                    if (STOP == status || currentThread !== workerThread) break
                }

            }
        }
    }

    @Throws(IOException::class)
    private fun defineBluetoothDevice() {
        bluetoothDevice = Bluetooth.bondedDevices(this).firstOrNull { it.address == addressModuleBT }
    }

    private fun closeSocket() {
        try {
            activeSocket?.close()
        } catch (_: IOException) {
        }
    }

    private fun reportStatus(state: SensorConnectionState) {
        statusListener?.onSensorStatusChanged(SensorKind.VAM, state)
    }

    companion object {

        private const val LOG = "VAMService"
        private const val STOP = 1
        private const val CHANGE_MODULE = 2
        private const val RUNNING = 3
        private const val UUID_SERIAL_BL = "00001101-0000-1000-8000-00805F9B34FB"
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val POLL_DELAY_MS = 1_000L
        private const val READ_TIMEOUT_SECONDS = 5L
        private val REQUEST_VALUE = "1".toByteArray(StandardCharsets.US_ASCII)
    }
}
