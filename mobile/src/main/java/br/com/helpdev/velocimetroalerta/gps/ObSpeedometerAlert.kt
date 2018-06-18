package br.com.helpdev.velocimetroalerta.gps

import java.io.Serializable
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Created by guilherme on 17/07/16.
 */
class ObSpeedometerAlert : Serializable {

    var dateInicio: Date? = null
    private var vMedia: Double = 0.toDouble()
    private var vAtual: Double = 0.toDouble()
    private var vMaxima: Double = 0.toDouble()
    var distancia: Double = 0.toDouble()
    var distanciaPausada: Double = 0.toDouble()
    var altitudeAtual: Double = 0.toDouble()
    var precisaoAtual: Double = 0.toDouble()
    var ganhoAltitude: Double = 0.toDouble()
    var perdaAltitude: Double = 0.toDouble()
    private var duracao: Long = 0
    var duracaoPausado: Long = 0
    var cadence: Int = 0
    var bpm: Int = 0
    var temperature: Int = 0
    var humidity: Int = 0

    init {
        this.dateInicio = Date()
    }

    fun addDuracaoPausado(duracaoPausado: Double) {
        this.duracaoPausado += duracaoPausado.toLong()
    }

    fun getDuracao(): Double {
        return duracao.toDouble()
    }

    fun setDuracao(duracao: Long) {
        this.duracao = duracao
    }

    fun addDistanciaPausada(distanciaPausada: Double) {
        this.distanciaPausada += distanciaPausada
    }

    fun addPerdaAltitude(perdaAltitude: Double) {
        this.perdaAltitude += perdaAltitude
    }

    fun getvMedia(): Double {
        return vMedia
    }

    fun setvMedia(vMedia: Double) {
        this.vMedia = vMedia
    }

    fun getvAtual(): Double {
        return vAtual
    }

    fun setvAtual(vAtual: Double) {
        this.vAtual = vAtual
    }

    fun getvMaxima(): Double {
        return vMaxima
    }

    fun setvMaxima(vMaxima: Double) {
        this.vMaxima = vMaxima
    }

    fun addDistancia(distancia: Double) {
        this.distancia += distancia
    }

    fun addGanhoAltitude(ganhoAltitude: Double) {
        this.ganhoAltitude += ganhoAltitude
    }

    override fun toString(): String {
        return "ObSpeedometerAlert{" +
                "dateInicio=" + dateInicio +
                ", vMedia=" + vMedia +
                ", vAtual=" + vAtual +
                ", vMaxima=" + vMaxima +
                ", distancia=" + distancia +
                ", distanciaPausada=" + distanciaPausada +
                ", altitudeAtual=" + altitudeAtual +
                ", precisaoAtual=" + precisaoAtual +
                ", ganhoAltitude=" + ganhoAltitude +
                ", perdaAltitude=" + perdaAltitude +
                ", duracao=" + duracao +
                ", duracaoPausado=" + duracaoPausado +
                ", cadence=" + cadence +
                ", bpm=" + bpm +
                ", temperature=" + temperature +
                ", humidity=" + humidity +
                '}'.toString()
    }

    fun toStringNotification(): String {
        val time = String.format("%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(duracao),
                TimeUnit.MILLISECONDS.toMinutes(duracao) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(duracao)),
                TimeUnit.MILLISECONDS.toSeconds(duracao) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duracao)))


        return String.format("%s - %.1fKm", time, distancia)
    }
}
