package br.com.helpdev.velocimetroalerta.gps

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.location.Location
import android.os.*
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import androidx.preference.PreferenceManager
import android.util.Log

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

import br.com.helpdev.velocimetroalerta.MainActivity
import br.com.helpdev.velocimetroalerta.MySpeechSpeed
import br.com.helpdev.velocimetroalerta.R
import br.com.helpdev.velocimetroalerta.activities.ActivityDraft
import br.com.helpdev.velocimetroalerta.activities.ActivityDraftFactory
import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx
import br.com.helpdev.velocimetroalerta.gpx.objects.MetaData
import br.com.helpdev.velocimetroalerta.gpx.objects.TrackPointExtension
import br.com.helpdev.velocimetroalerta.gpx.objects.Trk
import br.com.helpdev.velocimetroalerta.gpx.objects.TrkPt
import br.com.helpdev.velocimetroalerta.sensors.*
import br.com.helpdev.velocimetroalerta.utils.NotificationUtils

/**
 * Created by Guilherme Biff Zarelli on 04/04/16.
 */
class SpeedometerService : Service(), Runnable, SharedPreferences.OnSharedPreferenceChangeListener {

    private var firstBase: Long = 0
    private var baseTime: Long = 0

    private var statusService: Int = 0
    private var gpsStatusSignal = CallbackGpsThread.GPS_ATUALIZADO
    private var gpsStatusPaused = CallbackGpsThread.GPS_RETOMADO
    private var gpsStatusPrecision = CallbackGpsThread.GPS_PRECISAO_OK

    var gpx: Gpx = Gpx("Velocimetro Alerta Android")
        private set
    private val activeRoute = ActiveRoute()
    private var obSpeedometerAlert = ObSpeedometerAlert()

    private val mySpeechSpeed = MySpeechSpeed()
    private val gps = Gps()

    private var pauseAutomaticEnable = true
    private var tmpMillisPaused: Long = 0
    private var tmpDistancePaused = -1.0
    private var vibratorService: Vibrator? = null
    private val elevationSession = ElevationTrackingSession()
    private val fixDeduplicator = MonotonicFixDeduplicator()
    private val automaticPauseController = AutomaticPauseController()

    private var callbackGpsThread: CallbackGpsThread? = null
    private var myNotificationBuilder: Notification.Builder? = null
    private var notificationManager: NotificationManager? = null
    private var sensorVelAlertModule: VAMService.SensorsBinder? = null
    private var sensorHMService: HMService.HMBinder? = null
    private val sensorStates = ConcurrentHashMap<SensorKind, SensorConnectionState>()
    private var isVamServiceBound = false
    private var isHmServiceBound = false
    private lateinit var itServiceVelAlertModule: Intent
    private lateinit var itServiceHM: Intent

    private val vamServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) = handleVamServiceConnected(service)
        override fun onServiceDisconnected(name: ComponentName) {
            sensorVelAlertModule = null
            reportSensorStatus(SensorKind.VAM, SensorConnectionState.RECONNECTING)
        }
    }

    private val hmServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) = handleHmServiceConnected(service)
        override fun onServiceDisconnected(name: ComponentName) {
            sensorHMService = null
            reportSensorStatus(SensorKind.HEART_RATE, SensorConnectionState.RECONNECTING)
        }
    }

    val isRunning: Boolean
        get() = statusService != STATUS_FINALIZADO

    val isPause: Boolean
        get() = statusService == STATUS_PAUSADO

    val isTracking: Boolean
        get() = gps.isTracking

    fun subscribeRoute(observer: RouteObserver): RouteSnapshot = activeRoute.subscribe(observer)

    fun unsubscribeRoute(observer: RouteObserver) = activeRoute.unsubscribe(observer)

    val hasLocationFix: Boolean
        get() = gps.hasLocationFix

    @Synchronized
    fun snapshotActivityDraft(suggestedName: String): ActivityDraft =
        ActivityDraftFactory.from(gpx, obSpeedometerAlert, suggestedName)

    var isForegroundTracking: Boolean = false
        private set

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

        fun setSensorStatus(sensor: SensorKind, state: SensorConnectionState)

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
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_TRACKING) {
            beginTracking()
            if (gps.isTracking) {
                activateConfiguredSensors()
            }
        }
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

        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(pref: SharedPreferences, key: String?) {
        val preferenceKey = key ?: return
        try {
            if (preferenceKey == getString(R.string.pref_module_vel_alert)) {
                if (pref.getBoolean(preferenceKey, false)) {
                    activateVamSensor()
                } else {
                    deactivateVamSensor()
                }
            } else if (preferenceKey == getString(R.string.pref_module_vel_alert_address)) {
                if (pref.getBoolean(getString(R.string.pref_module_vel_alert), false) && gps.isTracking) {
                    if (pref.getString(preferenceKey, null).isNullOrBlank()) deactivateVamSensor()
                    else activateVamSensor()
                }
            } else if (preferenceKey == getString(R.string.pref_hr_sensor)) {
                if (pref.getBoolean(preferenceKey, false)) {
                    activateHeartRateSensor()
                } else {
                    deactivateHeartRateSensor()
                }
            } else if (preferenceKey == getString(R.string.pref_hr_sensor_address)) {
                if (pref.getBoolean(getString(R.string.pref_hr_sensor), false) && gps.isTracking) {
                    if (pref.getString(preferenceKey, null).isNullOrBlank()) deactivateHeartRateSensor()
                    else activateHeartRateSensor()
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

    private fun activateConfiguredSensors() {
        activateVamSensor()
        activateHeartRateSensor()
    }

    fun retryConfiguredSensorsAfterPermissionGrant() {
        if (!gps.isTracking || !hasBluetoothConnectPermission()) return

        if (sensorStates[SensorKind.HEART_RATE] == SensorConnectionState.PERMISSION_DENIED) {
            activateHeartRateSensor()
        }
        if (sensorStates[SensorKind.VAM] == SensorConnectionState.PERMISSION_DENIED) {
            activateVamSensor()
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun isSensorAllowed(sensor: SensorKind, configured: Boolean): Boolean {
        val hasPermission = hasBluetoothConnectPermission()
        val allowed = SensorActivationPolicy.shouldActivate(gps.isTracking, configured, hasPermission)
        if (gps.isTracking && configured && !hasPermission) {
            reportSensorStatus(sensor, SensorConnectionState.PERMISSION_DENIED)
        }
        return allowed
    }

    private fun activateVamSensor() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val configured = preferences.getBoolean(getString(R.string.pref_module_vel_alert), false)
        if (!isSensorAllowed(SensorKind.VAM, configured)) return
        val address = preferences.getString(getString(R.string.pref_module_vel_alert_address), null)
        if (address.isNullOrBlank()) return
        if (sensorVelAlertModule != null) {
            sensorVelAlertModule?.start(address)
        } else if (!isVamServiceBound) {
            isVamServiceBound = bindService(itServiceVelAlertModule, vamServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun activateHeartRateSensor() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val configured = preferences.getBoolean(getString(R.string.pref_hr_sensor), false)
        if (!isSensorAllowed(SensorKind.HEART_RATE, configured)) return
        val address = preferences.getString(getString(R.string.pref_hr_sensor_address), null)
        if (address.isNullOrBlank()) return
        if (sensorHMService != null) {
            sensorHMService?.start(address)
        } else if (!isHmServiceBound) {
            isHmServiceBound = bindService(itServiceHM, hmServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun deactivateVamSensor() {
        sensorVelAlertModule?.setStatusListener(null)
        sensorVelAlertModule?.stop()
        sensorVelAlertModule = null
        if (isVamServiceBound) {
            try {
                unbindService(vamServiceConnection)
            } catch (error: IllegalArgumentException) {
                Log.w(LOG, "VAM service was already unbound", error)
            }
            isVamServiceBound = false
        }
        stopService(itServiceVelAlertModule)
    }

    private fun deactivateHeartRateSensor() {
        sensorHMService?.setStatusListener(null)
        sensorHMService?.stop()
        sensorHMService = null
        if (isHmServiceBound) {
            try {
                unbindService(hmServiceConnection)
            } catch (error: IllegalArgumentException) {
                Log.w(LOG, "Heart-rate service was already unbound", error)
            }
            isHmServiceBound = false
        }
        stopService(itServiceHM)
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
        publishElevationEstimate(elevationSession.finish())
        this.statusService = STATUS_FINALIZADO
        gps.stopTracking()
        deactivateHeartRateSensor()
        deactivateVamSensor()
        isForegroundTracking = false
        try {
            stopForeground(true)
        } catch (t: Throwable) {
        }
        stopSelf()
    }

    @Synchronized
    fun start(callbackGpsThread: CallbackGpsThread) {
        this.callbackGpsThread = callbackGpsThread
        beginTracking()
    }

    @Synchronized
    private fun beginTracking() {
        if (statusService == STATUS_FINALIZADO) {
            try {
                val notification = myNotificationBuilder!!.build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        ID_NOTIFICATION_FOREGROUND,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else {
                    startForeground(ID_NOTIFICATION_FOREGROUND, notification)
                }
                isForegroundTracking = true
                gps.startTracking()
                statusService = STATUS_RODANDO
                val myThread = Thread(this)
                myThread.isDaemon = true
                myThread.name = "TH-" + SpeedometerService::class.java.name
                myThread.start()
            } catch (error: SecurityException) {
                statusService = STATUS_FINALIZADO
                gps.stopTracking()
                isForegroundTracking = false
                try {
                    stopForeground(true)
                } catch (_: Throwable) {
                }
                stopSelf()
                callbackGpsThread?.onErrorProcessingData(error)
            }
        }
    }

    private fun handleVamServiceConnected(service: IBinder) {
        if (service !is VAMService.SensorsBinder) return
        sensorVelAlertModule = service
        service.setStatusListener(SensorStatusListener(::reportSensorStatus))
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val configured = preferences.getBoolean(getString(R.string.pref_module_vel_alert), false)
        val address = preferences.getString(getString(R.string.pref_module_vel_alert_address), null)
        if (isSensorAllowed(SensorKind.VAM, configured) && !address.isNullOrBlank()) service.start(address)
        else service.stop()
    }

    private fun handleHmServiceConnected(service: IBinder) {
        if (service !is HMService.HMBinder) return
        sensorHMService = service
        service.setStatusListener(SensorStatusListener(::reportSensorStatus))
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val configured = preferences.getBoolean(getString(R.string.pref_hr_sensor), false)
        val address = preferences.getString(getString(R.string.pref_hr_sensor_address), null)
        if (isSensorAllowed(SensorKind.HEART_RATE, configured) && !address.isNullOrBlank()) service.start(address)
        else service.stop()
    }

    private fun reset() {
        gpx = Gpx("Velocimeter_Alert")
        activeRoute.reset()
        obSpeedometerAlert = ObSpeedometerAlert(getConfigHR())
        val base = SystemClock.elapsedRealtime()
        firstBase = base
        baseTime = base
        obSpeedometerAlert.inPauseAutomatic = false
        obSpeedometerAlert.inPauseActivity = false
        if (callbackGpsThread != null) callbackGpsThread!!.setBaseChronometer(base, true)
        automaticPauseController.reset()
        elevationSession.reset()
        fixDeduplicator.reset()
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
        publishElevationEstimate(elevationSession.resume())
        notifyBase(true, true)
        if (obSpeedometerAlert.inPauseAutomatic) {
            obSpeedometerAlert.inPauseAutomatic = false
            callbackGpsThread?.setPauseAutomatic(obSpeedometerAlert.inPauseAutomatic)
        }
        if (!automatic) automaticPauseController.reset()
    }

    @Synchronized
    private fun startPause(automatic: Boolean) {
        vibrate()
        if (obSpeedometerAlert.inPauseActivity) return
        obSpeedometerAlert.inPauseActivity = true
        tmpDistancePaused = obSpeedometerAlert.distance
        tmpMillisPaused = SystemClock.elapsedRealtime()
        val estimate = if (automatic) elevationSession.confirmAutomaticPause() else elevationSession.pauseManually()
        publishElevationEstimate(estimate)
        automaticPauseController.reset()
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

    @Synchronized
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
                obSpeedometerAlert.accuracyGPS = accuracy
            }

            //Verify pause button
            if (statusService == STATUS_PAUSADO) {
                return
            }

            //Verify Automatic pause
            if (pauseAutomaticEnable) {
                when (automaticPauseController.update(
                    nowElapsedRealtime = SystemClock.elapsedRealtime(),
                    speedKmh = currentSpeed,
                    horizontalAccuracyMeters = accuracy,
                    automaticallyPaused = obSpeedometerAlert.inPauseAutomatic
                )) {
                    AutomaticPauseDecision.HOLD_FOR_PAUSE -> elevationSession.beginAutomaticPauseCandidate()
                    AutomaticPauseDecision.CONFIRM_PAUSE -> {
                        startPause(true)
                        return
                    }
                    AutomaticPauseDecision.KEEP_PAUSED -> return
                    AutomaticPauseDecision.RESUME -> resumePause(true)
                    AutomaticPauseDecision.TRACK ->
                        publishElevationEstimate(elevationSession.cancelAutomaticPauseCandidate())
                }
            } else {
                automaticPauseController.reset()
                publishElevationEstimate(elevationSession.cancelAutomaticPauseCandidate())
            }

            //If don't have location! do nothing
            if (location == null) {
                return
            }

            //If has minimal accuracy calculate data:
            if (accuracy > 0.0 && accuracy <= CONST_PRECISAO_MINIMA &&
                fixDeduplicator.shouldAccept(location.elapsedRealtimeNanos)
            ) {
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
                publishElevationEstimate(elevationSession.accept(location.toElevationSample()))
                calculateSpeedAVG()

                if (currentSpeed > obSpeedometerAlert.speedMax) {
                    obSpeedometerAlert.speedMax = currentSpeed
                }

                if (trackPointExtension == null) {
                    gpx.trk.trkseg.addTrkPt(location)
                } else {
                    gpx.trk.trkseg.addTrkPt(location, trackPointExtension)
                }
                activeRoute.append(RoutePoint(location.latitude, location.longitude))
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

    private fun publishElevationEstimate(estimate: ElevationEstimate) {
        estimate.filteredAltitudeMeters?.let { altitude ->
            obSpeedometerAlert.altitude = altitude
            obSpeedometerAlert.hasAltitude = true
        }
        obSpeedometerAlert.elevationQuality = estimate.quality
        obSpeedometerAlert.gainAlt = estimate.gainMeters
        obSpeedometerAlert.lostAlt = estimate.lossMeters
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
        this.callbackGpsThread?.let { callback ->
            sensorStates.forEach { (sensor, state) -> callback.setSensorStatus(sensor, state) }
        }
    }

    fun reportSensorStatus(sensor: SensorKind, state: SensorConnectionState) {
        sensorStates[sensor] = state
        callbackGpsThread?.setSensorStatus(sensor, state)
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

        const val ACTION_START_TRACKING = "br.com.helpdev.velocimetroalerta.action.START_TRACKING"

        private const val ID_NOTIFICATION_FOREGROUND = 10
        private const val LOG = "SpeedometerService"

        private const val STATUS_RODANDO = 1
        private const val STATUS_PAUSADO = 2
        private const val STATUS_FINALIZADO = 3

        private const val CONST_VELOCIDADE_PAUSA_AUTOMATICA = 1.0//km/h
        private const val CONST_PRECISAO_MINIMA = 15
    }

}
