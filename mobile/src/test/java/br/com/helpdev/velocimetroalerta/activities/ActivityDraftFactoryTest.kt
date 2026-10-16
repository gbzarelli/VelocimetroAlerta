package br.com.helpdev.velocimetroalerta.activities

import br.com.helpdev.velocimetroalerta.gps.ObSpeedometerAlert
import br.com.helpdev.velocimetroalerta.gpx.objects.Extensions
import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx
import br.com.helpdev.velocimetroalerta.gpx.objects.TrackPointExtension
import br.com.helpdev.velocimetroalerta.gpx.objects.TrkPt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ActivityDraftFactoryTest {
    @Test
    fun createsSummaryFromTrackingMetricsAndCopiesGpx() {
        val alert = ObSpeedometerAlert().apply {
            dateTimeStart = Date(1_760_000_000_000)
            distance = 24.6
            distancePaused = 0.4
            time = 4_704_000
            timePaused = 456_000
            speedAvg = 18.9
            speedMax = 42.3
            gainAlt = 312.0
            lostAlt = 287.0
            altitude = 741.0
            accuracyGPS = 5.0
            cadenceAvg = 82
            cadenceMax = 109
            bpmAvg = 142
            bpmMax = 168
            temperature = 26
            humidity = 63
        }
        val gpx = gpxWithPoints(2).apply {
            trk.name = "original"
            trk.trkseg.trkPts[0].extensions = extension(atemp = "26", hr = "142", cad = "82", rhu = "63")
        }

        val draft = ActivityDraftFactory.from(gpx, alert, "Atividade 16 out, 14:30", "activity-id")

        assertEquals("activity-id", draft.summary.id)
        assertEquals("Atividade 16 out, 14:30", draft.summary.name)
        assertEquals(1_760_000_000_000, draft.summary.startedAtEpochMillis)
        assertEquals(24.6, draft.summary.distanceKm, 0.0)
        assertEquals(0.4, draft.summary.distancePausedKm, 0.0)
        assertEquals(4_704_000, draft.summary.movingDurationMillis)
        assertEquals(456_000, draft.summary.pausedDurationMillis)
        assertEquals(5_160_000, draft.summary.totalDurationMillis)
        assertEquals(18.9, draft.summary.averageSpeedKmh, 0.0)
        assertEquals(42.3, draft.summary.maxSpeedKmh, 0.0)
        assertEquals(312.0, draft.summary.elevationGainMeters, 0.0)
        assertEquals(287.0, draft.summary.elevationLossMeters, 0.0)
        assertEquals(82, draft.summary.averageCadenceRpm)
        assertEquals(109, draft.summary.maxCadenceRpm)
        assertEquals(142, draft.summary.averageHeartRateBpm)
        assertEquals(168, draft.summary.maxHeartRateBpm)
        assertEquals(26, draft.summary.temperatureCelsius)
        assertEquals(63, draft.summary.humidityPercent)
        assertTrue(draft.summary.hasCadenceData)
        assertTrue(draft.summary.hasHeartRateData)
        assertTrue(draft.summary.hasTemperatureData)
        assertTrue(draft.summary.hasHumidityData)
        assertNotSame(gpx, draft.gpx)
        assertNotSame(gpx.trk.trkseg.trkPts[0], draft.gpx.trk.trkseg.trkPts[0])

        gpx.trk.name = "mutated"
        gpx.trk.trkseg.trkPts.clear()
        assertEquals("original", draft.gpx.trk.name)
        assertEquals(2, draft.gpx.trk.trkseg.trkPts.size)
    }

    @Test
    fun treatsSensorsWithoutSamplesAsUnavailableInsteadOfZero() {
        val gpx = gpxWithPoints(1)
        val draft = ActivityDraftFactory.from(gpx, ObSpeedometerAlert(), "Atividade", "empty-sensors")

        assertFalse(draft.summary.hasCadenceData)
        assertFalse(draft.summary.hasHeartRateData)
        assertFalse(draft.summary.hasTemperatureData)
        assertFalse(draft.summary.hasHumidityData)
        assertNull(draft.summary.averageCadenceRpm)
        assertNull(draft.summary.maxCadenceRpm)
        assertNull(draft.summary.averageHeartRateBpm)
        assertNull(draft.summary.maxHeartRateBpm)
        assertNull(draft.summary.temperatureCelsius)
        assertNull(draft.summary.humidityPercent)
    }

    @Test
    fun samplesLongRouteEvenlyAndRetainsEndpointsAndOrder() {
        val points = (0 until 400).map { index ->
            TrkPt().apply {
                setLat(-23.0 - index / 10_000.0)
                setLon(-46.0 - index / 10_000.0)
            }
        }

        val sampled = RoutePreviewSampler.sample(points)

        assertEquals(120, sampled.size)
        assertEquals(points.first().latitude, sampled.first().latitude, 0.0)
        assertEquals(points.first().longitude, sampled.first().longitude, 0.0)
        assertEquals(points.last().latitude, sampled.last().latitude, 0.0)
        assertEquals(points.last().longitude, sampled.last().longitude, 0.0)
        assertTrue(sampled.zipWithNext().all { (a, b) -> b.latitude < a.latitude })
    }

    @Test
    fun doesNotDownsampleRoutesAlreadyWithinLimit() {
        val points = (0 until 20).map { index ->
            TrkPt().apply { setLat(index.toDouble()); setLon(-index.toDouble()) }
        }

        assertEquals(points.map { ActivityPreviewPoint(it.latitude, it.longitude) }, RoutePreviewSampler.sample(points))
    }

    private fun gpxWithPoints(count: Int) = Gpx("Velocimetro Alerta").apply {
        trk.trkseg.trkPts.addAll((0 until count).map { index ->
            TrkPt().apply {
                setLat(-23.5 - index / 1000.0)
                setLon(-46.6 - index / 1000.0)
                ele = 700.0 + index
                time = "2026-10-16T14:30:0${index}Z"
            }
        })
    }

    private fun extension(atemp: String, hr: String, cad: String, rhu: String) = Extensions(
        TrackPointExtension().apply {
            this.atemp = atemp
            this.hr = hr
            this.cad = cad
            this.rhu = rhu
        }
    )
}
