package br.com.helpdev.velocimetroalerta.gps

import java.io.Serializable
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Created by guilherme on 17/07/16.
 */
class ObSpeedometerAlert(var obConfigHr: ObConfigHR = ObConfigHR()) : Serializable {

    var inPauseActivity = false
    var inPauseAutomatic = false

    var dateTimeStart: Date? = null
    var speedAvg = 0.toDouble()
    var speed = 0.toDouble()
    var speedMax = 0.toDouble()
    var distance = 0.toDouble()
    var distancePaused = 0.toDouble()
    var altitude = 0.toDouble()
    var accuracyGPS = 0.toDouble()
    var gainAlt = 0.toDouble()
    var lostAlt = 0.toDouble()
    var time = 0.toLong()
    var timePaused = 0.toLong()

    var temperature = 0
    var humidity = 0

    private var cadenceCountAvg = 0
    private var cadenceSumAvg = 0
    private var bpmCountAvg = 0
    private var bpmSumAvg = 0

    var cadenceMax = 0
    var cadenceAvg = 0
    var bpmMax = 0
    var bpmAvg = 0

    var cadence = 0
        set(value) {
            if (value > 0) {
                if (value > cadenceMax) {
                    cadenceMax = value
                }
                if (!inPauseActivity) {
                    cadenceCountAvg++
                    cadenceSumAvg += value
                    cadenceAvg = cadenceSumAvg / cadenceCountAvg
                }
            }
            field = value
        }

    var bpmAvgZoneString = ""
        get() = bpmAvg.toString() + " (" + getStringBPMZone(getPercentageOfMax(bpmAvg)) + ")"

    var bpmZoneString = ""
        get() = getStringBPMZone(bpmPercentage) + " - " + bpmPercentage + "%"

    var bpm = 0
        set(value) {
            if (value > 0) {
                if (value > bpmMax) {
                    bpmMax = value
                }
                if (!inPauseActivity) {
                    bpmCountAvg++
                    bpmSumAvg += value
                    bpmAvg = bpmSumAvg / bpmCountAvg
                }
            }
            field = value
        }

    init {
        this.dateTimeStart = Date()
    }

    private fun getStringBPMZone(bpmPercentage: Int) = when {
        bpmPercentage > obConfigHr.percentZ5 -> "Z5"
        bpmPercentage > obConfigHr.percentZ4 -> "Z4"
        bpmPercentage > obConfigHr.percentZ3 -> "Z3"
        bpmPercentage > obConfigHr.percentZ2 -> "Z2"
        bpmPercentage > obConfigHr.percentZ1 -> "Z1"
        else -> "Z0"
    }

    private fun getPercentageOfMax(bpm: Int): Int {
        if (bpm <= 0) return 0
        return (bpm / obConfigHr.maxHr.toFloat() * 100f).toInt()
    }

    private var bpmPercentage = 0
        get() = getPercentageOfMax(bpm)

    fun addTimePaused(timePaused: Double) {
        this.timePaused += timePaused.toLong()
    }

    fun addDistancePaused(distancePaused: Double) {
        this.distancePaused += distancePaused
    }

    fun addLostAlt(lostAlt: Double) {
        this.lostAlt += lostAlt
    }

    fun addDistance(distance: Double) {
        this.distance += distance
    }

    fun addGainAlt(gainAlt: Double) {
        this.gainAlt += gainAlt
    }

    fun toStringNotification(): String {
        val time = String.format("%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(time),
                TimeUnit.MILLISECONDS.toMinutes(time) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(time)),
                TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time)))
        return String.format("%s - %.1fKm", time, distance)
    }

    override fun toString(): String {
        return "ObSpeedometerAlert(dateTimeStart=$dateTimeStart, speedAvg=$speedAvg, speed=$speed, speedMax=$speedMax, distance=$distance, distancePaused=$distancePaused, altitude=$altitude, accuracyGPS=$accuracyGPS, gainAlt=$gainAlt, lostAlt=$lostAlt, time=$time, timePaused=$timePaused, cadence=$cadence, bpm=$bpm, temperature=$temperature, humidity=$humidity, cadenceMax=$cadenceMax, cadenceAvg=$cadenceAvg, bpmMax=$bpmMax, bpmAvg=$bpmAvg)"
    }
}
