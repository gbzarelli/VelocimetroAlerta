package br.com.helpdev.velocimetroalerta

import android.content.Context
import android.os.Vibrator
import android.preference.PreferenceManager
import android.speech.tts.TextToSpeech

import java.util.Locale

import br.com.helpdev.velocimetroalerta.gps.ObSpeedometerAlert

/**
 * Created by Guilherme Biff Zarelli on 05/04/16.
 */
class MySpeechSpeed {

    @Volatile
    private var reproduzindo = false
    private var speech: TextToSpeech? = null
    private var intervalo: Int = 0
    private var valorIntervalo: Int = 0

    private var repAtual: Boolean = false
    private var repMedia: Boolean = false
    private var repMax: Boolean = false
    private var repDistancia: Boolean = false
    private var repTempo: Boolean = false
    private var vibrar: Boolean = false
    private var context: Context? = null

    private var tmpDistancia: Double = 0.toDouble()
    private var distanciaPercorrida: Double = 0.toDouble()

    fun init(context: Context) {
        speech = TextToSpeech(context, TextToSpeech.OnInitListener { speech!!.language = Locale.getDefault() })
        reloadPreferences(context)
    }

    fun reloadPreferences(context: Context) {
        this.context = context
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val inter = sp.getString(context.getString(R.string.pref_intervalo), "minutos")
        intervalo = if (inter == "minutos") {
            INTERVAL_TEMPO
        } else {
            INTERVAL_DISTANCE
        }
        valorIntervalo = Integer.parseInt(sp.getString(context.getString(R.string.pref_intervalo_valor), "1"))
        repAtual = sp.getBoolean(context.getString(R.string.pref_informar_vatual), false)
        repMedia = sp.getBoolean(context.getString(R.string.pref_informar_vmedia), true)
        repMax = sp.getBoolean(context.getString(R.string.pref_informar_vmax), false)
        repDistancia = sp.getBoolean(context.getString(R.string.pref_informar_distancia), false)
        repTempo = sp.getBoolean(context.getString(R.string.pref_informar_tempo), false)
        vibrar = sp.getBoolean(context.getString(R.string.pref_vibrar), false)
    }

    private fun sleep(time: Int) {
        try {
            Thread.sleep(time.toLong())
        } catch (e: Exception) {
        }

    }

    fun updateValues(obSpeedometerAlert: ObSpeedometerAlert) {
        if (speech == null) {
            throw RuntimeException("Not init MySpeechSpeed!")
        }
        this.distanciaPercorrida = obSpeedometerAlert.distance
        if (reproduzindo) {
            return
        }
        reproduzindo = true
        Thread(Runnable {
            if (intervalo == INTERVAL_TEMPO) {
                sleep(valorIntervalo * 60 * 1000)
            } else if (intervalo == INTERVAL_DISTANCE) {
                var d = 0
                do {
                    sleep(1000)
                    d = (distanciaPercorrida - tmpDistancia).toInt()
                } while (d < valorIntervalo)
                tmpDistancia = distanciaPercorrida
            }
            reproduzindo = false
        }).start()

        if (vibrar) {
            Thread(Runnable {
                try {
                    val vibrator = context!!.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(3000)
                } catch (e: Exception) {
                }
            }).start()
        }

        if (distanciaPercorrida <= 0.1) {
            return
        }

        if (!speech!!.isSpeaking) {
            if (repAtual) {
                play(context!!.getString(R.string.speek_velocidade_atual, obSpeedometerAlert.speed.toInt()))
            }
            if (repMedia) {
                play(context!!.getString(R.string.speek_media, String.format("%.1f", obSpeedometerAlert.speedAvg).replace(",".toRegex(), ".")))
            }
            if (repMax) {
                play(context!!.getString(R.string.speek_maxima, obSpeedometerAlert.speedMax.toInt()))
            }
            if (repDistancia) {
                play(context!!.getString(R.string.speek_distancia, String.format("%.1f", obSpeedometerAlert.distance).replace(",".toRegex(), ".")))
            }
            if (repTempo) {
                play(context!!.getString(R.string.speek_tempo, (obSpeedometerAlert.time.toDouble() / 60000.toDouble()).toInt()))
            }
        }
    }

    private fun play(text: String) {
        val mText = text.replace(",".toRegex(), ".")
        speech!!.speak(mText, TextToSpeech.QUEUE_ADD, null)
        speech!!.playSilence(300, TextToSpeech.QUEUE_ADD, null)
    }

    fun close() {
        speech!!.shutdown()
    }

    companion object {
        private const val INTERVAL_DISTANCE = 1
        private const val INTERVAL_TEMPO = 2
    }
}
