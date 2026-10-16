package br.com.helpdev.velocimetroalerta.gpx

import android.util.Xml
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.helpdev.velocimetroalerta.gpx.objects.Extensions
import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx
import br.com.helpdev.velocimetroalerta.gpx.objects.MetaData
import br.com.helpdev.velocimetroalerta.gpx.objects.Trk
import br.com.helpdev.velocimetroalerta.gpx.objects.TrkPt
import br.com.helpdev.velocimetroalerta.gpx.objects.TrackPointExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class GpxFileUtilsTest {
    @Test
    fun writesGpx11TrackAndSensorExtensions() {
        val gpx = Gpx("Velocimetro Alerta").apply {
            metaData = MetaData().apply { time = "2026-10-14T12:00:00Z" }
            trk = Trk().apply {
                name = "Ride & Route"
                trkseg.addTrkPt(point(-23.55, -46.63, 760.25, "2026-10-14T12:00:01Z").apply {
                    extensions = Extensions(TrackPointExtension().apply {
                        atemp = "26"
                        cad = "82"
                        hr = "144"
                        rhu = "63"
                    })
                })
                trkseg.addTrkPt(point(-23.56, -46.64, 761.5, "2026-10-14T12:00:02Z"))
            }
        }
        val output = CloseTrackingOutputStream()

        GpxFileUtils().writeGpx(gpx, output)

        assertFalse("The serializer must leave the caller's stream open", output.closed)
        val parser = Xml.newPullParser().apply {
            setInput(ByteArrayInputStream(output.toByteArray()), "UTF-8")
        }
        val points = mutableListOf<Triple<String, String, String>>()
        var creator: String? = null
        var metadataTime: String? = null
        var elevation: String? = null
        var firstPointTime: String? = null
        var cadence: String? = null
        var heartRate: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when {
                parser.name == "gpx" -> {
                    assertEquals(GPX_NAMESPACE, parser.namespace)
                    assertEquals("1.1", parser.getAttributeValue(null, "version"))
                    creator = parser.getAttributeValue(null, "creator")
                }
                parser.name == "metadata" -> metadataTime = parser.readTextOf("time", GPX_NAMESPACE)
                parser.name == "trkpt" && parser.namespace == GPX_NAMESPACE -> {
                    val lat = parser.getAttributeValue(null, "lat")
                    val lon = parser.getAttributeValue(null, "lon")
                    val pointParser = parser
                    var done = false
                    while (!done && pointParser.next() != XmlPullParser.END_DOCUMENT) {
                        if (pointParser.eventType == XmlPullParser.START_TAG) {
                            when {
                                pointParser.name == "ele" && pointParser.namespace == GPX_NAMESPACE -> {
                                    val pointElevation = pointParser.nextText()
                                    if (elevation == null) elevation = pointElevation
                                }
                                pointParser.name == "time" && pointParser.namespace == GPX_NAMESPACE -> firstPointTime = pointParser.nextText()
                                pointParser.name == "cad" && pointParser.namespace == TRACK_POINT_NAMESPACE -> cadence = pointParser.nextText()
                                pointParser.name == "hr" && pointParser.namespace == TRACK_POINT_NAMESPACE -> heartRate = pointParser.nextText()
                            }
                        } else if (pointParser.eventType == XmlPullParser.END_TAG && pointParser.name == "trkpt") {
                            done = true
                        }
                    }
                    points += Triple(lat, lon, firstPointTime.orEmpty())
                    firstPointTime = null
                }
            }
        }

        assertEquals("Velocimetro Alerta", creator)
        assertEquals("2026-10-14T12:00:00Z", metadataTime)
        assertEquals(2, points.size)
        assertEquals("-23.55", points[0].first)
        assertEquals("-46.63", points[0].second)
        assertEquals("2026-10-14T12:00:01Z", points[0].third)
        assertEquals("-23.56", points[1].first)
        assertEquals("-46.64", points[1].second)
        assertEquals("2026-10-14T12:00:02Z", points[1].third)
        assertEquals("760.25", elevation)
        assertEquals("82", cadence)
        assertEquals("144", heartRate)
    }

    @Test
    fun readsWriterOutputAndPreservesRouteMetadataAndSensorExtensions() {
        val source = Gpx("Velocimetro Alerta").apply {
            metaData = MetaData().apply { time = "2026-10-14T12:00:00Z" }
            trk.name = "Ride & Route"
            trk.trkseg.addTrkPt(point(-23.55, -46.63, 760.25, "2026-10-14T12:00:01Z").apply {
                extensions = Extensions(TrackPointExtension().apply {
                    atemp = "26"
                    cad = "82"
                    hr = "144"
                    rhu = "63"
                })
            })
            trk.trkseg.addTrkPt(point(-23.56, -46.64, 761.5, "2026-10-14T12:00:02Z"))
        }
        val output = ByteArrayOutputStream()
        val utils = GpxFileUtils()
        utils.writeGpx(source, output)

        val restored = utils.readGpx(ByteArrayInputStream(output.toByteArray()))
        val firstPoint = restored.trk.trkseg.trkPts.first()
        val extension = firstPoint.extensions.trackPointExtension

        assertEquals("Velocimetro Alerta", restored.creator)
        assertEquals("2026-10-14T12:00:00Z", restored.metaData.time)
        assertEquals("Ride & Route", restored.trk.name)
        assertEquals(2, restored.trk.trkseg.trkPts.size)
        assertEquals(-23.55, firstPoint.latitude, 0.0)
        assertEquals(-46.63, firstPoint.longitude, 0.0)
        assertEquals(760.25, firstPoint.ele, 0.0)
        assertEquals("2026-10-14T12:00:01Z", firstPoint.time)
        assertEquals("26", extension.atemp)
        assertEquals("82", extension.cad)
        assertEquals("144", extension.hr)
        assertEquals("63", extension.rhu)
    }

    @Test
    fun writeGpxRejectsEmptyTrack() {
        assertThrows(IllegalArgumentException::class.java) {
            GpxFileUtils().writeGpx(Gpx("Velocimetro Alerta"), ByteArrayOutputStream())
        }
    }

    private fun point(lat: Double, lon: Double, ele: Double, time: String) = TrkPt().apply {
        setLat(lat)
        setLon(lon)
        this.ele = ele
        this.time = time
    }

    private fun XmlPullParser.readTextOf(name: String, namespace: String): String? {
        var text: String? = null
        while (next() != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && this.name == name && this.namespace == namespace) {
                text = nextText()
                break
            }
            if (eventType == XmlPullParser.END_TAG && this.name == "metadata") break
        }
        return text
    }

    private class CloseTrackingOutputStream : ByteArrayOutputStream() {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }

    private companion object {
        const val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"
        const val TRACK_POINT_NAMESPACE = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1"
    }
}
