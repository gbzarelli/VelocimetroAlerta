package br.com.helpdev.velocimetroalerta

import android.Manifest
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import br.com.helpdev.velocimetroalerta.gps.ObSpeedometerAlert
import br.com.helpdev.velocimetroalerta.gps.SpeedometerService
import br.com.helpdev.velocimetroalerta.gpx.GpxFileUtils
import kotlinx.android.synthetic.main.fragment_main.*
import java.io.File
import java.lang.String.format
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * A placeholder fragment containing a simple view.
 */
class MainActivityFragment : Fragment(), View.OnClickListener, SpeedometerService.CallbackGpsThread, SharedPreferences.OnSharedPreferenceChangeListener {

    private var progressDialog: ProgressDialog? = null
    private var obSpeedometerAlert: ObSpeedometerAlert? = null

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
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (view == null) return
        btStartStop!!.setOnClickListener(this)
        btSave!!.setOnClickListener(this)
        btRefresh!!.setOnClickListener(this)


        val onClickGanho = View.OnClickListener {
            val dialog = AlertDialog.Builder(activity!!)
            dialog.setTitle(R.string.app_name)
            dialog.setMessage(R.string.info_ganho)
            dialog.setPositiveButton(R.string.bt_ok, null)
            dialog.create().show()
        }
        layout_ganho_vl.setOnClickListener(onClickGanho)
        layout_ganho_tx.setOnClickListener(onClickGanho)

        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
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
        if (activity == null) return

        if (ActivityCompat.checkSelfPermission(activity!!, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(myActivity!!, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 12)
            return
        }

        if (gpsProcess == null || obSpeedometerAlert == null || obSpeedometerAlert!!.time < TimeUnit.MINUTES.toMillis(1)) {
            val builder = AlertDialog.Builder(activity!!)
            builder.setTitle(R.string.app_name)
            builder.setMessage(R.string.atividades_sem_dados)
            builder.setCancelable(false)
            builder.setPositiveButton(R.string.bt_ok, null)
            builder.create().show()
        } else {
            progressDialog = ProgressDialog(context)
            progressDialog!!.setMessage(getString(R.string.aguarde_salvando))
            progressDialog!!.setCancelable(false)
            progressDialog!!.show()

            object : Thread() {
                override fun run() {
                    var mensagem: String
                    val builder = AlertDialog.Builder(activity!!)
                    try {
                        val gpxFileUtils = GpxFileUtils()
                        val file = gpxFileUtils.writeGpx(
                                gpsProcess!!.gpx,
                                File(Environment.getExternalStorageDirectory(), "/velocimetro_alerta/"),
                                "VEL_ALERTA_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                                        .format(obSpeedometerAlert!!.dateTimeStart)
                        )

                        mensagem = getString(R.string.arquivo_gravado_sucesso, file.absolutePath)
                        builder.setNeutralButton(R.string.share) { _, _ ->
                            val intent = Intent(Intent.ACTION_SEND)
                            intent.type = "text/*"
                            intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + file.absolutePath))
                            startActivity(Intent.createChooser(intent, getString(R.string.title_share)))
                        }

                    } catch (t: Throwable) {
                        t.printStackTrace()
                        mensagem = getString(R.string.erro_gravar_gpx, t.message)
                    }

                    activity!!.runOnUiThread {
                        try {
                            if (progressDialog != null) progressDialog!!.dismiss()
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        }

                        builder.setTitle(R.string.app_name)
                        builder.setMessage(mensagem)
                        builder.setCancelable(false)
                        builder.setPositiveButton(R.string.bt_ok, null)
                        builder.create().show()
                    }
                }
            }.start()
        }
    }

    private fun clear() {
        try {
            btStartStop!!.isEnabled = true

            btSave!!.visibility = View.GONE
            btRefresh!!.visibility = View.GONE

            if (gpsProcess != null) {
                gpsProcess!!.stop()
            }
            gpsDesatualizado.visibility = View.INVISIBLE
            pausadoAutomaticamente.visibility = View.INVISIBLE
            search_sensor_vam.visibility = View.INVISIBLE
            search_sensor_hr.visibility = View.INVISIBLE

            velocidadeAtual.setText(R.string.text_null_value)
            velocidadeMedia.setText(R.string.text_null_value)
            velocidadeMaxima.setText(R.string.text_null_value)

            distancia.setText(R.string.text_null_value)
            altitude.setText(R.string.text_null_value)
            precision.setText(R.string.text_null_value)
            ganhoAltitude.setText(R.string.text_null_value)
            perdaAltitude.setText(R.string.text_null_value)

            cadence.setText(R.string.text_null_value)
            temperature.setText(R.string.text_null_value)
            humidity.setText(R.string.text_null_value)
            hm.setText(R.string.text_null_value)

            maxHm.setText(R.string.text_null_value)
            avgHm.setText(R.string.text_null_value)
            avgCadence.setText(R.string.text_null_value)
            zone.setText(R.string.text_null_value)

            chronometer.base = SystemClock.elapsedRealtime()
            chronometer.stop()
        } catch (t: Throwable) {
            Log.e(LOG, "clear", t)
        }

    }

    private fun playPause() {
        if (gpsProcess == null) return

        if (gpsProcess!!.isRunning) {
            if (pausadoAutomaticamente.visibility == View.VISIBLE) {
                pausadoAutomaticamente.visibility = View.INVISIBLE
            }

            if (gpsProcess!!.isRunning) {
                btStartStop!!.isEnabled = false
            }

            if (gpsProcess!!.isPause) {
                gpsProcess!!.pause(false)
                btRefresh!!.visibility = View.GONE
            } else {
                gpsProcess!!.pause(true)
                btRefresh!!.visibility = View.VISIBLE
            }


        } else {
            gpsProcess!!.start(this)
        }
        btSave.visibility = if (gpsProcess!!.isPause) View.VISIBLE else View.GONE
        btStartStop.setImageResource(if (gpsProcess!!.isPause) R.drawable.play else R.drawable.pause)
    }

    private fun updateValuesText() {
        if (gpsProcess != null) {
            if (obSpeedometerAlert != null) {
                try {
                    velocidadeAtual.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.speed)
                    velocidadeMaxima.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.speedMax)
                    velocidadeMedia.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.speedAvg)

                    distancia.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.distance)
                    altitude.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.altitude)
                    ganhoAltitude.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.gainAlt)
                    perdaAltitude.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.lostAlt)
                    precision.text = format(Locale.getDefault(), "%.1f", obSpeedometerAlert!!.accuracyGPS)

                    if (obSpeedometerAlert!!.cadence < 0) {
                        cadence.setText(R.string.text_null_value)
                        search_sensor_vam.visibility = View.VISIBLE
                    } else {
                        search_sensor_vam.visibility = View.INVISIBLE
                        cadence.text = obSpeedometerAlert!!.cadence.toString()
                        avgCadence.text = obSpeedometerAlert!!.cadenceAvg.toString()
                    }
                    if (obSpeedometerAlert!!.temperature < 0) {
                        temperature.setText(R.string.text_null_value)
                    } else {
                        temperature.text = obSpeedometerAlert!!.temperature.toString()
                    }
                    if (obSpeedometerAlert!!.humidity < 0) {
                        humidity.setText(R.string.text_null_value)
                    } else {
                        humidity.text = obSpeedometerAlert!!.humidity.toString()
                    }

                    if (obSpeedometerAlert!!.bpm <= 0) {
                        search_sensor_hr.visibility = View.VISIBLE
                        hm.setText(R.string.text_null_value)
                        zone.setText(R.string.text_null_value)
                    } else {
                        search_sensor_hr.visibility = View.INVISIBLE
                        hm.text = obSpeedometerAlert!!.bpm.toString()
                        zone.text = obSpeedometerAlert!!.bpmZoneString
                        avgHm.text = obSpeedometerAlert!!.bpmAvgZoneString
                        maxHm.text = obSpeedometerAlert!!.bpmMax.toString()
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
        if (activity != null)
            activity!!.runOnUiThread {
                if (gpsSituation == SpeedometerService.CallbackGpsThread.GPS_ATUALIZADO && gpsDesatualizado!!.visibility == View.VISIBLE) {
                    gpsDesatualizado.visibility = View.INVISIBLE
                } else if (gpsSituation == SpeedometerService.CallbackGpsThread.GPS_DESATUALIZADO && gpsDesatualizado!!.visibility == View.INVISIBLE) {
                    if (tvPrecisionInfo.visibility == View.VISIBLE) {
                        tvPrecisionInfo.visibility = View.GONE
                    }
                    gpsDesatualizado.visibility = View.VISIBLE
                }
                updateValuesText()
            }
    }

    @Synchronized
    override fun setGpsPause(gpsPause: Int) {
        if (activity != null)
            activity!!.runOnUiThread {
                if (gpsPause == SpeedometerService.CallbackGpsThread.GPS_PAUSADO) {
                    chronometer.stop()
                }
                if (!btStartStop.isEnabled) {
                    btStartStop.isEnabled = true
                }
                updateValuesText()
            }
    }

    @Synchronized
    override fun setGpsPrecision(precision: Int) {
        if (activity == null) return

        activity!!.runOnUiThread {
            if (precision == SpeedometerService.CallbackGpsThread.GPS_SEM_PRECISAO && this.precision!!.tag == null) {
                this.precision!!.setTextColor(Color.RED)
                this.precision!!.setTypeface(this.precision!!.typeface, Typeface.BOLD)
                if (gpsDesatualizado.visibility == View.INVISIBLE) {
                    tvPrecisionInfo.visibility = View.VISIBLE
                    this.precision!!.tag = 1
                }
            } else if (precision == SpeedometerService.CallbackGpsThread.GPS_PRECISAO_OK && this.precision!!.tag != null) {
                this.precision.setTextColor(Color.BLACK)
                this.precision.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
                tvPrecisionInfo.visibility = View.GONE
                this.precision.tag = null
            }
        }
    }

    override fun onAttach(context: Context?) {
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
        if (!gpsProcess!!.isPause) {
            if (activity != null)
                activity!!.runOnUiThread {
                    if (pause) {
                        pausadoAutomaticamente.visibility = View.VISIBLE
                        chronometer!!.stop()
                    } else {
                        pausadoAutomaticamente.visibility = View.INVISIBLE
                    }
                    updateValuesText()
                }
        }
    }

    override fun setBaseChronometer(base: Long, resume: Boolean) {
        if (activity != null)
            activity!!.runOnUiThread {
                chronometer.base = base
                if (resume) {
                    chronometer.start()
                }
            }
    }

    override fun onErrorProcessingData(t: Throwable) {

    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (view == null) return

        if (key == getString(R.string.pref_module_vel_alert)) {
            if (sharedPreferences.getBoolean(key, false)) {
                layout_sensors_vam.visibility = View.VISIBLE
            } else {
                layout_sensors_vam.visibility = View.GONE
            }
        } else if (key == getString(R.string.pref_hr_sensor)) {
            if (sharedPreferences.getBoolean(key, false)) {
                layout_sensors_hm.visibility = View.VISIBLE
            } else {
                layout_sensors_hm.visibility = View.GONE
            }
        }
    }

    companion object {
        private const val LOG = "MainFragment"
    }
}
