package br.com.helpdev.velocimetroalerta.gps

import br.com.helpdev.velocimetroalerta.gpx.GpxMath
import br.com.helpdev.velocimetroalerta.gpx.objects.TrkPt
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ElevationGpxReplayTest {
    @Test
    fun replaysTheThreePrivateStravaTracksThroughTheKotlinEstimator() {
        val gpxDirectory = System.getenv("ELEVATION_GPX_DIR")
        assumeTrue("Set ELEVATION_GPX_DIR to run the private GPX replay", !gpxDirectory.isNullOrBlank())
        val directory = File(requireNotNull(gpxDirectory))
        val references = listOf(
            "19463623301" to 2174.0,
            "20049386131" to 1303.0,
            "20338280241" to 1184.0
        )

        references.forEach { (id, stravaGainMeters) ->
            val fixes = readFixes(File(directory, "$id.gpx"))
            assertTrue("Expected an extensive track for $id", fixes.size > 1_000)
            val estimator = ElevationEstimator()
            val firstTimestamp = fixes.first().timestampNanos
            fixes.forEach { fix ->
                estimator.accept(
                    ElevationSample(
                        altitudeMeters = fix.altitudeMeters,
                        verticalAccuracyMeters = null,
                        fixElapsedRealtimeNanos = fix.timestampNanos - firstTimestamp + 1L
                    )
                )
            }
            val estimate = estimator.finish()
            val rawGain = GpxMath.calculateGain(
                0,
                fixes.map { TrkPt().apply { ele = it.altitudeMeters; accuracy = 0.0 } }
            ).first
            val currentFullWithAssumedFiveMeters = GpxMath.calculateGain(
                0,
                fixes.map { TrkPt().apply { ele = it.altitudeMeters; accuracy = 5.0 } }
            ).first
            val currentChunkedWithAssumedFiveMeters = replayLegacyKilometerChunks(fixes).first

            assertTrue("New gain should be finite for $id", estimate.gainMeters.isFinite())
            assertTrue("New loss should be finite for $id", estimate.lossMeters.isFinite())
            assertTrue("The replay should detect climbing for $id", estimate.gainMeters > 0.0)
            assertTrue("The replay should detect descending for $id", estimate.lossMeters > 0.0)
            assertTrue("Filtering should avoid summing every raw fluctuation for $id", estimate.gainMeters < rawGain)
            assertTrue("The replay should stay within a broad plausible range for $id", estimate.gainMeters < stravaGainMeters * 1.25)

            println(
                "elevation-replay id=$id points=${fixes.size} strava_m=$stravaGainMeters " +
                    "current_full_assumed_5m_m=${"%.1f".format(currentFullWithAssumedFiveMeters)} " +
                    "current_1km_assumed_5m_m=${"%.1f".format(currentChunkedWithAssumedFiveMeters)} " +
                    "new_incremental_m=${"%.1f".format(estimate.gainMeters)} " +
                    "new_loss_m=${"%.1f".format(estimate.lossMeters)} raw_m=${"%.1f".format(rawGain)}"
            )
        }
    }

    private fun readFixes(file: File): List<ReplayFix> {
        require(file.isFile) { "Missing private GPX file: $file" }
        val factory = XMLInputFactory.newFactory().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty("javax.xml.stream.isSupportingExternalEntities", false)
        }
        val fixes = mutableListOf<ReplayFix>()
        file.inputStream().use { input ->
            val reader = factory.createXMLStreamReader(input)
            var inTrackPoint = false
            var altitude: Double? = null
            var timestampNanos: Long? = null
            var latitude: Double? = null
            var longitude: Double? = null
            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                        "trkpt" -> {
                            inTrackPoint = true
                            altitude = null
                            timestampNanos = null
                            latitude = reader.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            longitude = reader.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        }
                        "ele" -> if (inTrackPoint) altitude = reader.elementText.toDoubleOrNull()
                        "time" -> if (inTrackPoint) {
                            timestampNanos = runCatching { Instant.parse(reader.elementText).toEpochMilli() * 1_000_000L }
                                .getOrNull()
                        }
                    }
                    XMLStreamConstants.END_ELEMENT -> if (reader.localName == "trkpt") {
                        if (altitude != null && timestampNanos != null) {
                            if (latitude != null && longitude != null) {
                                fixes += ReplayFix(
                                    requireNotNull(altitude), requireNotNull(timestampNanos),
                                    requireNotNull(latitude), requireNotNull(longitude)
                                )
                            }
                        }
                        inTrackPoint = false
                    }
                }
            }
            reader.close()
        }
        return fixes
    }

    /** Mirrors the old service trigger: calculate the stored prefix before appending the current fix. */
    private fun replayLegacyKilometerChunks(fixes: List<ReplayFix>): Pair<Double, Double> {
        val points = mutableListOf<TrkPt>()
        var previous: ReplayFix? = null
        var distanceKm = 0.0
        var lastCalculationDistanceKm = 0.0
        var startIndex = 0
        var gain = 0.0
        var loss = 0.0

        fixes.forEach { fix ->
            previous?.let { prior ->
                distanceKm += distanceKilometers(prior, fix)
                if (distanceKm > lastCalculationDistanceKm + 1.0 && points.isNotEmpty()) {
                    val chunk = GpxMath.calculateGain(startIndex, points)
                    gain += chunk.first
                    loss += chunk.second
                    startIndex = points.lastIndex
                    lastCalculationDistanceKm = distanceKm
                }
            }
            points += TrkPt().apply {
                ele = fix.altitudeMeters
                accuracy = 5.0
            }
            previous = fix
        }
        return gain to loss
    }

    private fun distanceKilometers(a: ReplayFix, b: ReplayFix): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val haversine = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 6371.0 * 2.0 * atan2(sqrt(haversine), sqrt(1.0 - haversine))
    }

    private data class ReplayFix(
        val altitudeMeters: Double,
        val timestampNanos: Long,
        val latitude: Double,
        val longitude: Double
    )
}
