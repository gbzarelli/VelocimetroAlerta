package br.com.helpdev.velocimetroalerta.activities

import br.com.helpdev.velocimetroalerta.gps.ObSpeedometerAlert
import br.com.helpdev.velocimetroalerta.gpx.objects.Extensions
import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx
import br.com.helpdev.velocimetroalerta.gpx.objects.MetaData
import br.com.helpdev.velocimetroalerta.gpx.objects.TrackPointExtension
import br.com.helpdev.velocimetroalerta.gpx.objects.Trk
import br.com.helpdev.velocimetroalerta.gpx.objects.TrkPt
import br.com.helpdev.velocimetroalerta.gpx.objects.TrkSeg
import java.util.Date
import java.util.UUID

data class ActivityPreviewPoint(val latitude: Double, val longitude: Double)

data class ActivitySummary(
    val id: String,
    val name: String,
    val startedAtEpochMillis: Long,
    val savedAtEpochMillis: Long,
    val distanceKm: Double,
    val distancePausedKm: Double,
    val movingDurationMillis: Long,
    val pausedDurationMillis: Long,
    val totalDurationMillis: Long,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val finalAltitudeMeters: Double,
    val gpsAccuracyMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val averageCadenceRpm: Int?,
    val maxCadenceRpm: Int?,
    val averageHeartRateBpm: Int?,
    val maxHeartRateBpm: Int?,
    val temperatureCelsius: Int?,
    val humidityPercent: Int?,
    val hasCadenceData: Boolean,
    val hasHeartRateData: Boolean,
    val hasTemperatureData: Boolean,
    val hasHumidityData: Boolean,
    val routePreview: List<ActivityPreviewPoint>
)

data class ActivityDraft(val summary: ActivitySummary, val gpx: Gpx)

object ActivityDraftFactory {
    fun from(
        gpx: Gpx,
        alert: ObSpeedometerAlert,
        suggestedName: String,
        id: String = UUID.randomUUID().toString()
    ): ActivityDraft {
        val startMillis = requireNotNull(alert.dateTimeStart) { "An activity must have a start time" }.time
        val routePoints = gpx.trk?.trkseg?.trkPts.orEmpty()
        val hasCadence = routePoints.any { it.extensions?.trackPointExtension?.cad.isPresent() }
        val hasHeartRate = routePoints.any { it.extensions?.trackPointExtension?.hr.isPresent() }
        val hasTemperature = routePoints.any { it.extensions?.trackPointExtension?.atemp.isPresent() }
        val hasHumidity = routePoints.any { it.extensions?.trackPointExtension?.rhu.isPresent() }

        val summary = ActivitySummary(
            id = id,
            name = suggestedName.trim(),
            startedAtEpochMillis = startMillis,
            savedAtEpochMillis = System.currentTimeMillis(),
            distanceKm = alert.distance,
            distancePausedKm = alert.distancePaused,
            movingDurationMillis = alert.time,
            pausedDurationMillis = alert.timePaused,
            totalDurationMillis = alert.time + alert.timePaused,
            averageSpeedKmh = alert.speedAvg,
            maxSpeedKmh = alert.speedMax,
            finalAltitudeMeters = alert.altitude,
            gpsAccuracyMeters = alert.accuracyGPS,
            elevationGainMeters = alert.gainAlt,
            elevationLossMeters = alert.lostAlt,
            averageCadenceRpm = alert.cadenceAvg.takeIf { hasCadence },
            maxCadenceRpm = alert.cadenceMax.takeIf { hasCadence },
            averageHeartRateBpm = alert.bpmAvg.takeIf { hasHeartRate },
            maxHeartRateBpm = alert.bpmMax.takeIf { hasHeartRate },
            temperatureCelsius = alert.temperature.takeIf { hasTemperature },
            humidityPercent = alert.humidity.takeIf { hasHumidity },
            hasCadenceData = hasCadence,
            hasHeartRateData = hasHeartRate,
            hasTemperatureData = hasTemperature,
            hasHumidityData = hasHumidity,
            routePreview = RoutePreviewSampler.sample(routePoints)
        )
        return ActivityDraft(summary, snapshotGpx(gpx))
    }

    internal fun snapshotGpx(gpx: Gpx): Gpx {
        val points = gpx.trk?.trkseg?.trkPts.orEmpty().map { point ->
            TrkPt().apply {
                setLat(point.latitude)
                setLon(point.longitude)
                ele = point.ele
                time = point.time
                accuracy = point.accuracy
                val extension = point.extensions?.trackPointExtension
                if (extension != null) {
                    extensions = Extensions(
                        TrackPointExtension().apply {
                            atemp = extension.atemp
                            hr = extension.hr
                            cad = extension.cad
                            rhu = extension.rhu
                        }
                    )
                }
            }
        }
        val oldTrack = gpx.trk
        return Gpx(gpx.creator).apply {
            version = gpx.version
            metaData = gpx.metaData?.let { oldMetadata ->
                MetaData().apply { time = oldMetadata.time }
            }
            trk = if (oldTrack == null) null else Trk().apply {
                name = oldTrack.name
                trkseg = TrkSeg().apply { trkPts.addAll(points) }
            }
        }
    }

    private fun String?.isPresent() = !isNullOrBlank()
}

object RoutePreviewSampler {
    fun sample(points: List<TrkPt>, maximumPoints: Int = 120): List<ActivityPreviewPoint> {
        require(maximumPoints > 0) { "maximumPoints must be greater than zero" }
        if (points.size <= maximumPoints) {
            return points.map { ActivityPreviewPoint(it.latitude, it.longitude) }
        }
        require(maximumPoints >= 2) { "At least two preview points are required to retain route endpoints" }
        val finalIndex = points.lastIndex
        return (0 until maximumPoints).map { sampleIndex ->
            val sourceIndex = (sampleIndex * finalIndex.toDouble() / (maximumPoints - 1)).toInt()
            val point = points[sourceIndex]
            ActivityPreviewPoint(point.latitude, point.longitude)
        }
    }
}
