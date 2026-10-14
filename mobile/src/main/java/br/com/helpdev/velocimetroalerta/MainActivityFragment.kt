package br.com.helpdev.velocimetroalerta

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.ClipData
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.PreferenceManager
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AlertDialog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import br.com.helpdev.velocimetroalerta.gps.ObSpeedometerAlert
import br.com.helpdev.velocimetroalerta.gps.SpeedometerService
import br.com.helpdev.velocimetroalerta.gpx.GpxFileUtils
import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx
import br.com.helpdev.velocimetroalerta.databinding.FragmentMainBinding
import br.com.helpdev.velocimetroalerta.sensors.SensorConnectionState
import br.com.helpdev.velocimetroalerta.sensors.SensorKind
import java.io.IOException
import java.lang.String.format
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A placeholder fragment containing a simple view.
 */
class MainActivityFragment : Fragment(), View.OnClickListener, SpeedometerService.CallbackGpsThread, SharedPreferences.OnSharedPreferenceChangeListener {

    private var _binding: FragmentMainBinding? = null
    private val binding: FragmentMainBinding
        get() = _binding ?: error("FragmentMainBinding is only available between onCreateView and onDestroyView")

    private var progressDialog: ProgressDialog? = null
    private var obSpeedometerAlert: ObSpeedometerAlert? = null
    private val sensorStates = mutableMapOf<SensorKind, SensorConnectionState>()
    private var pendingExport: PendingExport? = null
    private val exportExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "gpx-export").apply { isDaemon = true }
    }
    private val createGpxDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        val export = pendingExport
        pendingExport = null
        if (uri != null && export != null) writeGpxToDocument(uri, export)
    }

    private val gpsProcess: SpeedometerService?
        get() = if (myActivity == null) null else myActivity!!.speedometerService


    private val myActivity: MainActivity?
        get() = super.getActivity() as MainActivity?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (_binding == null) return
        binding.btStartStop!!.setOnClickListener(this)
        binding.btSave!!.setOnClickListener(this)
        binding.btRefresh!!.setOnClickListener(this)


        val onClickGanho = View.OnClickListener {
            val dialog = AlertDialog.Builder(activity!!)
            dialog.setTitle(R.string.app_name)
            dialog.setMessage(R.string.info_ganho)
            dialog.setPositiveButton(R.string.bt_ok, null)
            dialog.create().show()
        }
        binding.layoutGanhoVl.setOnClickListener(onClickGanho)
        binding.layoutGanhoTx.setOnClickListener(onClickGanho)

        val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sp.registerOnSharedPreferenceChangeListener(this)
        onSharedPreferenceChanged(sp, getString(R.string.pref_module_vel_alert))
        onSharedPreferenceChanged(sp, getString(R.string.pref_hr_sensor))

        clear()
    }

    override fun onClick(v: View) {
        when {
            v.id == R.id.btSave -> save()
            v.id == R.id.btRefresh -> clear()
            v.id == R.id.btStartStop -> playPause()
        }
    }

    private fun save() {
        val host = activity ?: return
        val service = gpsProcess
        val alert = obSpeedometerAlert
        val startDate = alert?.dateTimeStart
        val points = service?.gpx?.trk?.trkseg?.trkPts.orEmpty()
        if (service == null || alert == null || startDate == null || alert.time < TimeUnit.MINUTES.toMillis(1) || points.isEmpty()) {
            val builder = AlertDialog.Builder(host)
            builder.setTitle(R.string.app_name)
            builder.setMessage(R.string.atividades_sem_dados)
            builder.setCancelable(false)
            builder.setPositiveButton(R.string.bt_ok, null)
            builder.create().show()
        } else {
            val filenameTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(startDate)
            val filename = "VEL_ALERTA_${filenameTimestamp}_${UUID.randomUUID().toString().take(8)}.gpx"
            pendingExport = PendingExport(service.gpx, filename)
            createGpxDocument.launch(filename)
        }
    }

    private fun writeGpxToDocument(uri: Uri, export: PendingExport) {
        val host = activity ?: return
        val appContext = host.applicationContext
        progressDialog = ProgressDialog(host).apply {
            setMessage(getString(R.string.aguarde_salvando))
            setCancelable(false)
            show()
        }

        exportExecutor.execute {
            val result = runCatching {
                val output = appContext.contentResolver.openOutputStream(uri, "w")
                    ?: throw IOException("The document provider did not open an output stream")
                output.use { GpxFileUtils().writeGpx(export.gpx, it) }
            }
            activity?.runOnUiThread {
                progressDialog?.dismiss()
                progressDialog = null
                val currentActivity = activity ?: return@runOnUiThread
                val dialog = AlertDialog.Builder(currentActivity)
                    .setTitle(R.string.app_name)
                    .setCancelable(false)
                    .setPositiveButton(R.string.bt_ok, null)
                result.onSuccess {
                    dialog.setMessage(R.string.arquivo_gravado_sucesso)
                    dialog.setNeutralButton(R.string.share) { _, _ -> shareGpx(uri, export.filename) }
                }.onFailure { error ->
                    dialog.setMessage(getString(R.string.erro_gravar_gpx, error.message ?: ""))
                }
                dialog.show()
            }
        }
    }

    private fun shareGpx(uri: Uri, filename: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(requireContext().contentResolver, filename, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.title_share)))
    }

    private data class PendingExport(val gpx: Gpx, val filename: String)

    private fun clear() {
        try {
            binding.btStartStop!!.isEnabled = true

            binding.btSave!!.visibility = View.GONE
            binding.btRefresh!!.visibility = View.GONE

            if (gpsProcess != null) {
                gpsProcess!!.stop()
            }
            binding.gpsDesatualizado.visibility = View.INVISIBLE
            binding.pausadoAutomaticamente.visibility = View.INVISIBLE
            binding.searchSensorVam.visibility = View.INVISIBLE
            binding.searchSensorHr.visibility = View.INVISIBLE

            binding.velocidadeAtual.setText(R.string.text_null_value)
            binding.velocidadeMedia.setText(R.string.text_null_value)
            binding.velocidadeMaxima.setText(R.string.text_null_value)

            binding.distancia.setText(R.string.text_null_value)
            binding.altitude.setText(R.string.text_null_value)
            binding.precision.setText(R.string.text_null_value)
            binding.ganhoAltitude.setText(R.string.text_null_value)
            binding.perdaAltitude.setText(R.string.text_null_value)

            binding.cadence.setText(R.string.text_null_value)
            binding.temperature.setText(R.string.text_null_value)
            binding.humidity.setText(R.string.text_null_value)
            binding.hm.setText(R.string.text_null_value)

            binding.maxHm.setText(R.string.text_null_value)
            binding.avgHm.setText(R.string.text_null_value)
            binding.avgCadence.setText(R.string.text_null_value)
            binding.zone.setText(R.string.text_null_value)

            binding.chronometer.base = SystemClock.elapsedRealtime()
            binding.chronometer.stop()
        } catch (t: Throwable) {
            Log.e(LOG, "clear", t)
        }

    }

    private fun playPause() {
        if (gpsProcess == null) return

        if (gpsProcess!!.isRunning) {
            if (binding.pausadoAutomaticamente.visibility == View.VISIBLE) {
                binding.pausadoAutomaticamente.visibility = View.INVISIBLE
            }

            if (gpsProcess!!.isRunning) {
                binding.btStartStop!!.isEnabled = false
            }

            if (gpsProcess!!.isPause) {
                gpsProcess!!.pause(false)
                binding.btRefresh!!.visibility = View.GONE
            } else {
                gpsProcess!!.pause(true)
                binding.btRefresh!!.visibility = View.VISIBLE
            }


        } else {
            myActivity?.requestTrackingStart()
            return
        }
        binding.btSave.visibility = if (gpsProcess!!.isPause) View.VISIBLE else View.GONE
        binding.btStartStop.setImageResource(if (gpsProcess!!.isPause) R.drawable.play else R.drawable.pause)
    }

    private fun updateValuesText() {
        if (_binding == null) return
        if (gpsProcess != null) {
            if (obSpeedometerAlert != null) {
                try {
                    binding.velocidadeAtual.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.speed)
                    binding.velocidadeMaxima.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.speedMax)
                    binding.velocidadeMedia.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.speedAvg)

                    binding.distancia.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.distance)
                    binding.altitude.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.altitude)
                    binding.ganhoAltitude.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.gainAlt)
                    binding.perdaAltitude.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.lostAlt)
                    binding.precision.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.accuracyGPS)

                    if (obSpeedometerAlert!!.cadence < 0) {
                        binding.cadence.setText(R.string.text_null_value)
                        binding.searchSensorVam.text = sensorStatusText(sensorStates[SensorKind.VAM])
                        binding.searchSensorVam.visibility = View.VISIBLE
                    } else {
                        binding.searchSensorVam.visibility = View.INVISIBLE
                        binding.cadence.text = obSpeedometerAlert!!.cadence.toString()
                        binding.avgCadence.text = obSpeedometerAlert!!.cadenceAvg.toString()
                    }
                    if (obSpeedometerAlert!!.temperature < 0) {
                        binding.temperature.setText(R.string.text_null_value)
                    } else {
                        binding.temperature.text = obSpeedometerAlert!!.temperature.toString()
                    }
                    if (obSpeedometerAlert!!.humidity < 0) {
                        binding.humidity.setText(R.string.text_null_value)
                    } else {
                        binding.humidity.text = obSpeedometerAlert!!.humidity.toString()
                    }

                    if (obSpeedometerAlert!!.bpm <= 0) {
                        binding.searchSensorHr.text = sensorStatusText(sensorStates[SensorKind.HEART_RATE])
                        binding.searchSensorHr.visibility = View.VISIBLE
                        binding.hm.setText(R.string.text_null_value)
                        binding.zone.setText(R.string.text_null_value)
                    } else {
                        binding.searchSensorHr.visibility = View.INVISIBLE
                        binding.hm.text = obSpeedometerAlert!!.bpm.toString()
                        binding.zone.text = obSpeedometerAlert!!.bpmZoneString
                        binding.avgHm.text = obSpeedometerAlert!!.bpmAvgZoneString
                        binding.maxHm.text = obSpeedometerAlert!!.bpmMax.toString()
                    }

                } catch (t: Throwable) {
                    Log.e(LOG, "updateValuesText", t)
                }
            }
        }
    }

    override fun updateValues(obSpeedometerAlert: ObSpeedometerAlert) {
        this.obSpeedometerAlert = obSpeedometerAlert
        if (activity != null)
            activity!!.runOnUiThread { updateValuesText() }
    }

    @Synchronized
    override fun setGpsSituation(gpsSituation: Int) {
        val host = activity ?: return
        host.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                if (gpsSituation == SpeedometerService.CallbackGpsThread.GPS_ATUALIZADO && binding.gpsDesatualizado!!.visibility == View.VISIBLE) {
                    binding.gpsDesatualizado.visibility = View.INVISIBLE
                } else if (gpsSituation == SpeedometerService.CallbackGpsThread.GPS_DESATUALIZADO && binding.gpsDesatualizado!!.visibility == View.INVISIBLE) {
                    if (binding.tvPrecisionInfo.visibility == View.VISIBLE) {
                        binding.tvPrecisionInfo.visibility = View.GONE
                    }
                    binding.gpsDesatualizado.visibility = View.VISIBLE
                }
                updateValuesText()
            }
    }

    @Synchronized
    override fun setGpsPause(gpsPause: Int) {
        val host = activity ?: return
        host.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                if (gpsPause == SpeedometerService.CallbackGpsThread.GPS_PAUSADO) {
                    binding.chronometer.stop()
                }
                if (!binding.btStartStop.isEnabled) {
                    binding.btStartStop.isEnabled = true
                }
                updateValuesText()
            }
    }

    @Synchronized
    override fun setGpsPrecision(gpsPrecision: Int) {
        val host = activity ?: return

        host.runOnUiThread {
            if (_binding == null) return@runOnUiThread
            if (gpsPrecision == SpeedometerService.CallbackGpsThread.GPS_SEM_PRECISAO && binding.precision!!.tag == null) {
                binding.precision!!.setTextColor(Color.RED)
                binding.precision!!.setTypeface(binding.precision!!.typeface, Typeface.BOLD)
                if (binding.gpsDesatualizado.visibility == View.INVISIBLE) {
                    binding.tvPrecisionInfo.visibility = View.VISIBLE
                    binding.precision!!.tag = 1
                }
            } else if (gpsPrecision == SpeedometerService.CallbackGpsThread.GPS_PRECISAO_OK && binding.precision!!.tag != null) {
                binding.precision.setTextColor(Color.BLACK)
                binding.precision.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
                binding.tvPrecisionInfo.visibility = View.GONE
                binding.precision.tag = null
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        myActivity!!.setCallbackNotify(object : MainActivity.CallbackNotify {
            override fun onServiceConnected(speedometerService: SpeedometerService?) {
                speedometerService!!.setCallbackGpsThread(this@MainActivityFragment)
            }

            override fun onBeforeDisconnect(speedometerService: SpeedometerService) {
                speedometerService.setCallbackGpsThread(null)
            }

            override fun onCloseProgram() {
                clear()
            }
        })
    }

    @Synchronized
    override fun setPauseAutomatic(pause: Boolean) {
        val service = gpsProcess ?: return
        val host = activity ?: return
        if (!service.isPause) {
            host.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    if (pause) {
                        binding.pausadoAutomaticamente.visibility = View.VISIBLE
                        binding.chronometer!!.stop()
                    } else {
                        binding.pausadoAutomaticamente.visibility = View.INVISIBLE
                    }
                    updateValuesText()
                }
        }
    }

    override fun setBaseChronometer(base: Long, resume: Boolean) {
        val host = activity ?: return
        host.runOnUiThread {
                if (_binding == null) return@runOnUiThread
                binding.chronometer.base = base
                if (resume) {
                    binding.chronometer.start()
                    binding.btStartStop.setImageResource(R.drawable.pause)
                }
            }
    }

    override fun onErrorProcessingData(t: Throwable) {
        if (t is SecurityException) {
            activity?.runOnUiThread {
                clear()
                android.widget.Toast.makeText(requireContext(), R.string.precise_location_required, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun setSensorStatus(sensor: SensorKind, state: SensorConnectionState) {
        sensorStates[sensor] = state
        val host = activity ?: return
        host.runOnUiThread {
            if (_binding == null) return@runOnUiThread
            when (sensor) {
                SensorKind.HEART_RATE -> binding.searchSensorHr.text = sensorStatusText(state)
                SensorKind.VAM -> binding.searchSensorVam.text = sensorStatusText(state)
            }
        }
    }

    private fun sensorStatusText(state: SensorConnectionState?): String = getString(
        when (state) {
            SensorConnectionState.UNSUPPORTED -> R.string.bluetooth_sensor_unsupported
            SensorConnectionState.PERMISSION_DENIED -> R.string.bluetooth_sensor_permission_denied
            SensorConnectionState.BLUETOOTH_DISABLED -> R.string.bluetooth_sensor_disabled
            SensorConnectionState.CONNECTING -> R.string.bluetooth_sensor_connecting
            SensorConnectionState.CONNECTED -> R.string.bluetooth_sensor_connected
            SensorConnectionState.RECONNECTING -> R.string.bluetooth_sensor_reconnecting
            SensorConnectionState.CONNECTION_FAILED -> R.string.bluetooth_sensor_connection_failed
            null -> R.string.search_sensor
        }
    )

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (_binding == null) return
        val preferenceKey = key ?: return

        if (preferenceKey == getString(R.string.pref_module_vel_alert)) {
            if (sharedPreferences.getBoolean(preferenceKey, false)) {
                binding.layoutSensorsVam.visibility = View.VISIBLE
            } else {
                binding.layoutSensorsVam.visibility = View.GONE
            }
        } else if (preferenceKey == getString(R.string.pref_hr_sensor)) {
            if (sharedPreferences.getBoolean(preferenceKey, false)) {
                binding.layoutSensorsHm.visibility = View.VISIBLE
            } else {
                binding.layoutSensorsHm.visibility = View.GONE
            }
        }
    }

    companion object {
        private const val LOG = "MainFragment"
    }

    override fun onDestroyView() {
        activity?.let { PreferenceManager.getDefaultSharedPreferences(it).unregisterOnSharedPreferenceChangeListener(this) }
        gpsProcess?.setCallbackGpsThread(null)
        progressDialog?.dismiss()
        progressDialog = null
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        exportExecutor.shutdown()
        super.onDestroy()
    }
}
