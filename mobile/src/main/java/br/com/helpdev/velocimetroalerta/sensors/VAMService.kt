package br.com.helpdev.velocimetroalerta.sensors

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Binder
import android.os.IBinder

import com.google.gson.Gson

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

import br.com.helpdev.velocimetroalerta.bluetooth.Bluetooth
import br.com.helpdev.velocimetroalerta.gpx.objects.TrackPointExtension

class VAMService : Service(), Runnable {

    private var status = STOP
    private var bluetoothDevice: BluetoothDevice? = null
    private var addressModuleBT: String? = null
    private var lastValueModule: TrackPointExtension = TrackPointExtension()

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
    }

    override fun onBind(intent: Intent): IBinder? {
        return SensorsBinder(this)
    }

    override fun onDestroy() {
        status = STOP
        super.onDestroy()
    }

    fun start(macAddress: String) {
        addressModuleBT = macAddress
        if (status == STOP) {
            Thread(this, "Thr-VelAlertBTModule").start()
        } else {
            status = CHANGE_MODULE
        }
    }


    override fun run() {
        status = RUNNING
        while (STOP != status) {
            try {
                if (!Bluetooth.isAdapterEnabled) {
                    Bluetooth.enableAdapter()
                } else {
                    if (CHANGE_MODULE == status || null == bluetoothDevice) {
                        defineBluetoothDevice()
                        status = RUNNING
                    }
                    bluetoothDevice!!.createInsecureRfcommSocketToServiceRecord(UUID.fromString(UUID_SERIAL_BL)).use { btSocket ->
                        btSocket.connect()

                        while (RUNNING == status) {
                            val futureTask = FutureTask(Callable {
                                val baos = ByteArrayOutputStream()
                                var rData: Int
                                val data = ByteArray(64)
                                var line: String
                                btSocket.outputStream.write("1".toByteArray())
                                do {
                                    Thread.sleep(100)
                                    rData = btSocket.inputStream.read(data)
                                    baos.write(data, 0, rData)
                                    line = baos.toString()
                                } while (!line.endsWith("\r\n"))

                                val valueExtract = baos.toString()

                                try {
                                    return@Callable Gson().fromJson(valueExtract, TrackPointExtension::class.java)
                                } catch (t: Throwable) {
                                    t.printStackTrace()
                                }

                                TrackPointExtension()
                            })
                            Thread(futureTask).start()
                            lastValueModule = futureTask.get(5, TimeUnit.SECONDS)
                            Thread.sleep(1000)
                        }
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                lastValueModule = TrackPointExtension()
            }

            if (RUNNING == status) {
                try {
                    Thread.sleep(5000)
                } catch (t2: Throwable) {
                }

            }
        }
    }

    @Throws(IOException::class)
    private fun defineBluetoothDevice() {
        for (bd in Bluetooth.bondedDevices) {
            if (bd.address == addressModuleBT) {
                bluetoothDevice = bd
            }
        }
    }

    companion object {

        private const val STOP = 1
        private const val CHANGE_MODULE = 2
        private const val RUNNING = 3
        private const val UUID_SERIAL_BL = "00001101-0000-1000-8000-00805F9B34FB"
    }
}
