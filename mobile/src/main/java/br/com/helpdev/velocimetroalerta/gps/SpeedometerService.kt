package br.com.helpdev.velocimetroalerta.gps

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.location.Location
import android.os.*
import android.preference.PreferenceManager
import android.util.Log

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import br.com.helpdev.velocimetroalerta.MainActivity
import br.com.helpdev.velocimetroalerta.MySpeechSpeed
import br.com.helpdev.velocimetroalerta.R
import br.com.helpdev.velocimetroalerta.gpx.GpxMath
import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx
import br.com.helpdev.velocimetroalerta.gpx.objects.MetaData
import br.com.helpdev.velocimetroalerta.gpx.objects.TrackPointExtension
import br.com.helpdev.velocimetroalerta.gpx.objects.Trk
import br.com.helpdev.velocimetroalerta.gpx.objects.TrkPt
import br.com.helpdev.velocimetroalerta.sensors.*
import br.com.helpdev.velocimetroalerta.utils.NotificationUtils
import kotlinx.android.synthetic.main.content_config_hrz.*

/**
 * Created by Guilherme Biff Zarelli on 04/04/16.
 */
class SpeedometerService : Service(), Runnable, ServiceConnection, SharedPreferences.OnSharedPreferenceChangeListener {

    private var firstBase: Long = 0
    private var baseTime: Long = 0

    private var statusService: Int = 0
    private var gpsStatusSignal = CallbackGpsThread.GPS_ATUALIZADO
    private var gpsStatusPaused = CallbackGpsThread.GPS_RETOMADO
    private var gpsStatusPrecision = CallbackGpsThread.GPS_PRECISAO_OK

    var gpx: Gpx = Gpx("Velocimetro Alerta Android")
        private set
    private var obSpeedometerAlert = ObSpeedometerAlert()

    private val mySpeechSpeed = MySpeechSpeed()
    private val gps = Gps()

    private var pauseAutomaticEnable = true
    private var tmpMillisPaused: Long = 0
    private var tmpCurrentTimeMillis: Long? = null
    private var tmpDistancePaused = -1.0
    private var vibratorService: Vibrator? = null

    private var callbackGpsThread: CallbackGpsThread? = null
    private var myNotificationBuilder: Notification.Builder? = null
    private var notificationManager: NotificationManager? = null
    private var sensorVelAlertModule: VAMService.SensorsBinder? = null
    private var sensorHMService: HMService.HMBinder? = null
    private var itServiceVelAlertModule: Intent? = null
    private var itServiceHM: Intent? = null

    @Volatile
    private var tmpCalculatingAltimetry = false
    private var tmpIndexTrkAltimetry = 0
    private var tmpDistanceCalcAltimetry = 0.0

    val isRunning: Boolean
        get() = statusService != STATUS_FINALIZADO

    val isPause: Boolean
        get() = statusService == STATUS_PAUSADO

    private val chronometerTime: Long
        get() = SystemClock.elapsedRealtime() - baseTime

    private val trkPts: List<TrkPt>?
        get() = gpx.trk.trkseg.trkPts

    interface CallbackGpsThread {

        fun updateValues(obSpeedometerAlert: ObSpeedometerAlert)

        fun setGpsSituation(gpsSituation: Int)

        fun setGpsPause(gpsPause: Int)

        fun setGpsPrecision(precision: Int)

        fun setPauseAutomatic(pause: Boolean)

        fun setBaseChronometer(base: Long, resume: Boolean)

        fun onErrorProcessingData(t: Throwable)

        companion object {

            const val GPS_ATUALIZADO = 1
            const val GPS_DESATUALIZADO = 2
            //--
            const val GPS_PAUSADO = 3
            const val GPS_RETOMADO = 4
            //--
            const val GPS_SEM_PRECISAO = 5
            const val GPS_PRECISAO_OK = 6
        }
    }

    inner class MyBinder(val speedometerService: SpeedometerService) : Binder()

    override fun onCreate() {
        super.onCreate()
        itServiceVelAlertModule = Intent(this, VAMService::class.java)
        itServiceHM = Intent(this, HMService::class.java)

        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        pref.registerOnSharedPreferenceChangeListener(this)
        onSharedPreferenceChanged(pref, getString(R.string.pref_module_vel_alert))
        onSharedPreferenceChanged(pref, getString(R.string.pref_hr_sensor))

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mySpeechSpeed.init(this)
        gps.init(this)

        myNotificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtils.createNotificationChannel(this, "speedometerService", getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH)
            Notification.Builder(this, "speedometerService")
        } else {
            Notification.Builder(this)
        }

        myNotificationBuilder!!
                .setSmallIcon(R.drawable.ic_notfiy)
                .setContentTitle(getString(R.string.app_name))
                .setOngoing(true)
                .setOnlyAlertOnce(true)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        myNotificationBuilder!!.setContentIntent(pendingIntent)

        statusService = STATUS_FINALIZADO
    }

    private fun getConfigHR(): ObConfigHR {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)

        val max = sp.getInt(getString(R.string.pref_hr_max_value), resources.getInteger(R.integer.default_max_hr))
        val rest = sp.getInt(getString(R.string.pref_hr_rest_value), resources.getInteger(R.integer.default_rest_hr))
        val z5 = sp.getInt(getString(R.string.pref_hr_z5_value), resources.getInteger(R.integer.default_z5))
        val z4 = sp.getInt(getString(R.string.pref_hr_z4_value), resources.getInteger(R.integer.default_z4))
        val z3 = sp.getInt(getString(R.string.pref_hr_z3_value), resources.getInteger(R.integer.default_z3))
        val z2 = sp.getInt(getString(R.string.pref_hr_z2_value), resources.getInteger(R.integer.default_z2))
        val z1 = sp.getInt(getString(R.string.pref_hr_z1_value), resources.getInteger(R.integer.default_z1))

        return ObConfigHR(max, rest, z5, z4, z3, z2, z1)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        stop()

        try {
            mySpeechSpeed.close()
        } catch (t: Throwable) {
        }

        try {
            gps.close()
        } catch (t: Throwable) {
        }

        try {
            unbindService(this)
        } catch (t: Throwable) {
        }

        try {
            stopService(itServiceHM)
        } catch (t: Throwable) {
        }

        try {
            stopService(itServiceVelAlertModule)
        } catch (t: Throwable) {
        }

        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(pref: SharedPreferences, key: String) {
        try {
            if (key == getString(R.string.pref_module_vel_alert)) {
                if (pref.getBoolean(key, false)) {
                    startService(itServiceVelAlertModule)
                    bindService(itServiceVelAlertModule, this, Context.BIND_AUTO_CREATE)
                } else {
                    stopService(itServiceVelAlertModule)
                }
            } else if (key == getString(R.string.pref_module_vel_alert_address)) {
                if (sensorVelAlertModule != null) {
                    val newMacAddress = pref.getString(key, null)
                    if (newMacAddress == null) {
                        stopService(itServiceVelAlertModule)
                    } else {
                        sensorVelAlertModule!!.start(newMacAddress)
                    }
                }
            } else if (key == getString(R.string.pref_hr_sensor)) {
                if (pref.getBoolean(key, false)) {
                    startService(itServiceHM)
                    bindService(itServiceHM, this, Context.BIND_AUTO_CREATE)
                } else {
                    sensorHMService?.stop()
                    stopService(itServiceHM)
                }
            } else if (key == getString(R.string.pref_hr_sensor_address)) {
                if (sensorHMService != null) {
                    val newMacAddress = pref.getString(key, null)
                    if (newMacAddress == null) {
                        sensorHMService?.stop()
                        stopService(itServiceHM)
                    } else {
                        sensorHMService!!.start(newMacAddress)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(LOG, "onSharedPreferenceChanged", t)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return MyBinder(this)
    }

    fun reloadPreferences() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        this.pauseAutomaticEnable = sp.getBoolean(getString(R.string.pref_pause_automatico), true)
        mySpeechSpeed.reloadPreferences(this)
    }

    @Synchronized
    fun pause(pause: Boolean) {
        if (pause) {
            this.statusService = STATUS_PAUSADO
            if (gpsStatusPaused == CallbackGpsThread.GPS_RETOMADO) {
                gpsStatusPaused = CallbackGpsThread.GPS_PAUSADO
                callbackGpsThread?.setGpsPause(gpsStatusPaused)
                startPause(false)
            }
        } else {
            this.statusService = STATUS_RODANDO
            if (gpsStatusPaused == CallbackGpsThread.GPS_PAUSADO) {
                gpsStatusPaused = CallbackGpsThread.GPS_RETOMADO
                callbackGpsThread?.setGpsPause(gpsStatusPaused)
                resumePause(false)
            }
        }
    }

    @Synchronized
    fun stop() {
        this.statusService = STATUS_FINALIZADO
        try {
            stopForeground(true)
        } catch (t: Throwable) {
        }

    }

    @Synchronized
    fun start(callbackGpsThread: CallbackGpsThread) {
        this.callbackGpsThread = callbackGpsThread
        if (statusService == STATUS_FINALIZADO) {
            val myThread = Thread(this)
            myThread.isDaemon = true
            myThread.name = "TH-" + SpeedometerService::class.java.name
            myThread.start()
        }
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        if (service is VAMService.SensorsBinder) {
            sensorVelAlertModule = service
            val pref = PreferenceManager.getDefaultSharedPreferences(this)
            val addressModuleBT = pref.getString(getString(R.string.pref_module_vel_alert_address), null)
            if (addressModuleBT != null) {
                sensorVelAlertModule!!.start(addressModuleBT)
            }
        } else if (service is HMService.HMBinder) {
            sensorHMService = service
            val pref = PreferenceManager.getDefaultSharedPreferences(this)
            val addressModuleBT = pref.getString(getString(R.string.pref_hr_sensor_address), null)
            if (addressModuleBT != null) {
                sensorHMService!!.start(addressModuleBT)
            }
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        sensorVelAlertModule = null
    }

    private fun reset() {
        gpx = Gpx("Velocimeter_Alert")
        obSpeedometerAlert = ObSpeedometerAlert(getConfigHR())
        val base = SystemClock.elapsedRealtime()
        firstBase = base
        baseTime = base
        obSpeedometerAlert.inPauseAutomatic = false
        obSpeedometerAlert.inPauseActivity = false
        if (callbackGpsThread != null) callbackGpsThread!!.setBaseChronometer(base, true)
        tmpCurrentTimeMillis = null
        tmpIndexTrkAltimetry = 0
        tmpDistanceCalcAltimetry = 0.0
        tmpDistancePaused = -1.0
        tmpMillisPaused = 0
        gpsStatusSignal = CallbackGpsThread.GPS_ATUALIZADO
        gpsStatusPaused = CallbackGpsThread.GPS_RETOMADO
        gpsStatusPrecision = CallbackGpsThread.GPS_PRECISAO_OK
    }

    override fun run() {
        reset()
        statusService = STATUS_RODANDO
        startForeground(ID_NOTIFICATION_FOREGROUND, myNotificationBuilder!!.build())

        while (statusService != STATUS_FINALIZADO) {
            try {
                process(gps.views, gps.getLocation())
            } catch (t: Throwable) {
                Log.e(LOG, "Erro no processamento", t)
                if (callbackGpsThread != null) callbackGpsThread!!.onErrorProcessingData(t)
            }

            try {
                Thread.sleep(1000)
            } catch (e: Exception) {
            }
        }
    }


    @Synchronized
    private fun resumePause(automatic: Boolean) {
        vibrate()
        if (!obSpeedometerAlert.inPauseActivity) return
        obSpeedometerAlert.inPauseActivity = false
        notifyBase(true, true)
        if (automatic) {
            obSpeedometerAlert.inPauseAutomatic = false
            callbackGpsThread?.setPauseAutomatic(obSpeedometerAlert.inPauseAutomatic)
        }
    }

    @Synchronized
    private fun startPause(automatic: Boolean) {
        vibrate()
        if (obSpeedometerAlert.inPauseActivity) return
        obSpeedometerAlert.inPauseActivity = true
        tmpDistancePaused = obSpeedometerAlert.distance
        tmpMillisPaused = SystemClock.elapsedRealtime()
        calculateAltimetryAsync(true)
        if (automatic) {
            obSpeedometerAlert.inPauseAutomatic = true
            callbackGpsThread?.setPauseAutomatic(obSpeedometerAlert.inPauseAutomatic)
        }
    }

    private fun notifyBase(activityPause: Boolean, resume: Boolean) {
        if (activityPause) {
            obSpeedometerAlert.addTimePaused((SystemClock.elapsedRealtime() - tmpMillisPaused).toDouble())
            baseTime = firstBase + obSpeedometerAlert.timePaused
            tmpMillisPaused = SystemClock.elapsedRealtime()
        }
        if (callbackGpsThread != null) callbackGpsThread!!.setBaseChronometer(baseTime, resume)
    }

    private fun process(views: Int, location: Location?) {
        var currentSpeed = 0.0
        var accuracy = -1.0
        try {
            //Get extensions
            val trackPointExtension: TrackPointExtension? = getExtensions()

            //Verify GPS status
            if (views > 2 || location == null) {
                if (gpsStatusSignal != CallbackGpsThread.GPS_DESATUALIZADO) {
                    gpsStatusSignal = CallbackGpsThread.GPS_DESATUALIZADO
                    callbackGpsThread?.setGpsSituation(gpsStatusSignal)
                }
            } else {
                if (gpsStatusSignal != CallbackGpsThread.GPS_ATUALIZADO) {
                    gpsStatusSignal = CallbackGpsThread.GPS_ATUALIZADO
                    callbackGpsThread?.setGpsSituation(gpsStatusSignal)
                }
                currentSpeed = (location.speed * 3.6f).toDouble()
                accuracy = location.accuracy.toDouble()
                obSpeedometerAlert.altitude = location.altitude
                obSpeedometerAlert.accuracyGPS = accuracy
            }

            //Verify pause button
            if (statusService == STATUS_PAUSADO) {
                return
            }

            //Verify Automatic pause
            if (pauseAutomaticEnable) {
                if (currentSpeed <= CONST_VELOCIDADE_PAUSA_AUTOMATICA && accuracy <= CONST_PRECISAO_MINIMA) {
                    if (tmpCurrentTimeMillis == null) {
                        tmpCurrentTimeMillis = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - tmpCurrentTimeMillis!! > 3000) {
                        if (!obSpeedometerAlert.inPauseActivity) {
                            startPause(true)
                        }
                        return
                    }
                } else if (obSpeedometerAlert.inPauseActivity) {
                    tmpCurrentTimeMillis = null
                    resumePause(true)
                }
            }

            //If don't have location! do nothing
            if (location == null) {
                return
            }

            //If has minimal accuracy calculate data:
            if (accuracy <= CONST_PRECISAO_MINIMA) {
                //Define time on GPX for first location.
                if (gpx.metaData == null) {
                    obSpeedometerAlert.dateTimeStart = Date(location.time)
                    val metaData = MetaData()
                    metaData.time = Gpx.getUtcGpxTime(location.time)
                    gpx.metaData = metaData

                    val trk = Trk()
                    trk.name = "VEL_ALERTA_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(obSpeedometerAlert.dateTimeStart)
                    gpx.trk = trk
                }

                calculateDistances(location)
                calculateAltimetryAsync(false)
                calculateSpeedAVG()

                if (currentSpeed > obSpeedometerAlert.speedMax) {
                    obSpeedometerAlert.speedMax = currentSpeed
                }

                if (trackPointExtension == null) {
                    gpx.trk.trkseg.addTrkPt(location)
                } else {
                    gpx.trk.trkseg.addTrkPt(location, trackPointExtension)
                }
            }
            obSpeedometerAlert.time = chronometerTime

        } finally {
            obSpeedometerAlert.speed = currentSpeed
            val statusPrecision = if (accuracy > 0 && accuracy <= CONST_PRECISAO_MINIMA) {
                CallbackGpsThread.GPS_PRECISAO_OK
            } else {
                CallbackGpsThread.GPS_SEM_PRECISAO
            }
            if (gpsStatusPrecision != statusPrecision) {
                gpsStatusPrecision = statusPrecision
                callbackGpsThread?.setGpsPrecision(gpsStatusPrecision)
            }
            notifyUpdate()
        }
    }

    /**
     * Convert in TrackPointExternsions and set values in obSpeedometerAlert
     */
    private fun getExtensions(): TrackPointExtension? {
        var trackPointExtension: TrackPointExtension? = TrackPointExtension()
        if (sensorVelAlertModule != null) {
            trackPointExtension = sensorVelAlertModule!!.getTrackPointExtension()
            if (trackPointExtension.cad != null && !trackPointExtension.cad.isEmpty()) {
                obSpeedometerAlert.cadence = Integer.parseInt(trackPointExtension.cad)
            } else {
                obSpeedometerAlert.cadence = -1
            }
            if (trackPointExtension.atemp != null && !trackPointExtension.atemp.isEmpty()) {
                obSpeedometerAlert.temperature = Integer.parseInt(trackPointExtension.atemp)
            } else {
                obSpeedometerAlert.temperature = -1
            }
            if (trackPointExtension.rhu != null && !trackPointExtension.rhu.isEmpty()) {
                obSpeedometerAlert.humidity = Integer.parseInt(trackPointExtension.rhu)
            } else {
                obSpeedometerAlert.humidity = -1
            }
        }
        if (sensorHMService != null) {
            trackPointExtension!!.hr = sensorHMService!!.getHeartHate().toString()
            obSpeedometerAlert.bpm = sensorHMService!!.getHeartHate()
        }

        if (sensorHMService == null && sensorVelAlertModule == null) {
            trackPointExtension = null
        }
        return trackPointExtension
    }

    private fun notifyUpdate() {
        mySpeechSpeed.updateValues(obSpeedometerAlert)
        callbackGpsThread?.updateValues(obSpeedometerAlert)
        notifyNotificationUpdate()
    }

    private fun notifyNotificationUpdate() {
        var title = getString(R.string.notification_execute)
        if (statusService == STATUS_PAUSADO) {
            title = getString(R.string.notification_pause)
        } else if (obSpeedometerAlert.inPauseAutomatic) {
            title = getString(R.string.notification_pause_automatic)
        }
        myNotificationBuilder!!.setContentTitle(title)
        myNotificationBuilder!!.setContentText(obSpeedometerAlert.toStringNotification())
        notificationManager!!.notify(ID_NOTIFICATION_FOREGROUND, myNotificationBuilder!!.build())
    }

    private fun calculateAltimetryAsync(force: Boolean) {
        if (tmpCalculatingAltimetry) return
        object : Thread() {
            override fun run() {
                try {
                    tmpCalculatingAltimetry = true
                    calculateAltimetry(force)
                } catch (t: Throwable) {
                    Log.e(LOG, "Falaha ao calcular elevação", t)
                } finally {
                    tmpCalculatingAltimetry = false
                }
            }
        }.start()
    }

    private fun calculateAltimetry(force: Boolean) {
        val locationsHistory = trkPts
        if (locationsHistory == null || locationsHistory.isEmpty() || !force && obSpeedometerAlert.distance <= tmpDistanceCalcAltimetry + CONST_INTERVALO_DISTANCIA_CALCULO_GANHO) {
            return
        }
        if (force) {
            tmpIndexTrkAltimetry = 0
            obSpeedometerAlert.gainAlt = 0.0
            obSpeedometerAlert.lostAlt = 0.0
        }
        tmpDistanceCalcAltimetry = obSpeedometerAlert.distance

        val calculateGain = GpxMath.calculateGain(tmpIndexTrkAltimetry, locationsHistory)

        obSpeedometerAlert.addGainAlt(calculateGain.first)
        obSpeedometerAlert.addLostAlt(calculateGain.second)

        tmpIndexTrkAltimetry = locationsHistory.size - 1
        if (force) {
            notifyUpdate()
        }
    }


    private fun calculateSpeedAVG() {
        val hours = BigDecimal(chronometerTime)
                .divide(BigDecimal.valueOf(3600000), 10, BigDecimal.ROUND_HALF_UP)
                .toDouble()
        obSpeedometerAlert.speedAvg =
                (obSpeedometerAlert.distance - obSpeedometerAlert.distancePaused) / hours

    }

    private fun calculateDistances(location: Location) {
        val trkPts = trkPts ?: return
        if (trkPts.isEmpty()) return
        val distance = GpsUtils.distanceCalculate(
                trkPts[trkPts.size - 1].latitude,
                trkPts[trkPts.size - 1].longitude,
                location.latitude,
                location.longitude
        )
        obSpeedometerAlert.addDistance(distance)
        //** CASO A DISTANCIA ESTEJA PAUSADA, AO FAZER O CALCULO DA NOVA DISTANCIA, ADICIONA NA VARIAVEL QUE CONSTROLA
        //A DISTANCIA QUE FICOU PAUSADA, PARA NAO LEVAR EM CONSIDERAÇÃO NO CALCULO DE MEDIA.;
        if (tmpDistancePaused > 0) {
            obSpeedometerAlert.addDistancePaused(
                    obSpeedometerAlert.distance - tmpDistancePaused
            )
            tmpDistancePaused = -1.0
        }

    }

    fun setCallbackGpsThread(callbackGpsThread: CallbackGpsThread?) {
        this.callbackGpsThread = callbackGpsThread
        if (this.callbackGpsThread != null && statusService != STATUS_FINALIZADO) {

            notifyBase(obSpeedometerAlert.inPauseActivity, gpsStatusPaused ==
                    CallbackGpsThread.GPS_RETOMADO
                    && !(pauseAutomaticEnable && obSpeedometerAlert.inPauseAutomatic))

            this.callbackGpsThread!!.setGpsSituation(gpsStatusSignal)
            this.callbackGpsThread!!.setGpsPrecision(gpsStatusPrecision)
            this.callbackGpsThread!!.setGpsPause(gpsStatusPaused)
            this.callbackGpsThread!!.updateValues(obSpeedometerAlert)
            if (pauseAutomaticEnable) {
                this.callbackGpsThread!!.setPauseAutomatic(obSpeedometerAlert.inPauseAutomatic)
            }
        }
    }

    private fun vibrate() {
        Thread(Runnable {
            if (vibratorService == null) {
                vibratorService = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibratorService!!.vibrate(300)
        }).start()
    }

    companion object {

        private const val ID_NOTIFICATION_FOREGROUND = 10
        private const val LOG = "SpeedometerService"

        private const val STATUS_RODANDO = 1
        private const val STATUS_PAUSADO = 2
        private const val STATUS_FINALIZADO = 3

        private const val CONST_VELOCIDADE_PAUSA_AUTOMATICA = 1.0//km/h
        private const val CONST_PRECISAO_MINIMA = 15
        private const val CONST_INTERVALO_DISTANCIA_CALCULO_GANHO = 1.0//em km
    }

}
