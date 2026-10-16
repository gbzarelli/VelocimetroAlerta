package br.com.helpdev.velocimetroalerta.gpx

import android.location.Location
import android.util.Xml
import br.com.helpdev.velocimetroalerta.gpx.objects.Extensions
import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx
import br.com.helpdev.velocimetroalerta.gpx.objects.MetaData
import br.com.helpdev.velocimetroalerta.gpx.objects.TrackPointExtension
import br.com.helpdev.velocimetroalerta.gpx.objects.Trk
import br.com.helpdev.velocimetroalerta.gpx.objects.TrkPt
import java.io.OutputStream
import java.io.InputStream
import java.io.IOException
import org.xmlpull.v1.XmlPullParser

open class GpxFileUtils {
    /** Writes GPX 1.1 to [output] and leaves ownership of the stream with the caller. */
    @Throws(Exception::class)
    open fun writeGpx(gpx: Gpx, output: OutputStream) {
        val points = gpx.trk?.trkseg?.trkPts.orEmpty()
        require(points.isNotEmpty()) { "A GPX track must contain at least one point" }

        val serializer = Xml.newSerializer()
        serializer.setOutput(output, "UTF-8")
        serializer.startDocument("UTF-8", true)
        serializer.setPrefix("", GPX_NAMESPACE)
        serializer.setPrefix("xsi", XSI_NAMESPACE)
        serializer.setPrefix("gpxtpx", TRACK_POINT_NAMESPACE)
        serializer.startTag(GPX_NAMESPACE, "gpx")
        serializer.attribute(null, "version", "1.1")
        serializer.attribute(null, "creator", gpx.creator ?: "Velocimetro Alerta")
        serializer.attribute(
            XSI_NAMESPACE,
            "schemaLocation",
            "$GPX_NAMESPACE http://www.topografix.com/GPX/1/1/gpx.xsd " +
                "$TRACK_POINT_NAMESPACE http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd"
        )

        gpx.metaData?.time?.let { time ->
            serializer.startTag(GPX_NAMESPACE, "metadata")
            serializer.startTag(GPX_NAMESPACE, "time").text(time).endTag(GPX_NAMESPACE, "time")
            serializer.endTag(GPX_NAMESPACE, "metadata")
        }

        val track = gpx.trk
        if (track != null) {
            serializer.startTag(GPX_NAMESPACE, "trk")
            track.name?.let { name ->
                serializer.startTag(GPX_NAMESPACE, "name").text(name).endTag(GPX_NAMESPACE, "name")
            }
            serializer.startTag(GPX_NAMESPACE, "trkseg")
            points.forEach { point ->
                val latitude = requireNotNull(point.lat) { "A GPX track point must have latitude" }
                val longitude = requireNotNull(point.lon) { "A GPX track point must have longitude" }
                serializer.startTag(GPX_NAMESPACE, "trkpt")
                serializer.attribute(null, "lat", latitude)
                serializer.attribute(null, "lon", longitude)
                serializer.startTag(GPX_NAMESPACE, "ele").text(point.ele.toString()).endTag(GPX_NAMESPACE, "ele")
                point.time?.let { time ->
                    serializer.startTag(GPX_NAMESPACE, "time").text(time).endTag(GPX_NAMESPACE, "time")
                }
                point.extensions?.trackPointExtension?.let { extension ->
                    val values = listOf(
                        "atemp" to extension.atemp,
                        "hr" to extension.hr,
                        "cad" to extension.cad,
                        "rhu" to extension.rhu
                    ).filter { (_, value) -> value != null }
                    if (values.isNotEmpty()) {
                        serializer.startTag(GPX_NAMESPACE, "extensions")
                        serializer.startTag(TRACK_POINT_NAMESPACE, "TrackPointExtension")
                        values.forEach { (element, value) ->
                            serializer.startTag(TRACK_POINT_NAMESPACE, element).text(value).endTag(TRACK_POINT_NAMESPACE, element)
                        }
                        serializer.endTag(TRACK_POINT_NAMESPACE, "TrackPointExtension")
                        serializer.endTag(GPX_NAMESPACE, "extensions")
                    }
                }
                serializer.endTag(GPX_NAMESPACE, "trkpt")
            }
            serializer.endTag(GPX_NAMESPACE, "trkseg")
            serializer.endTag(GPX_NAMESPACE, "trk")
        }

        serializer.endTag(GPX_NAMESPACE, "gpx")
        serializer.endDocument()
        serializer.flush()
    }

    /** Reads the GPX 1.1 track subset written by this app without closing [input]. */
    @Throws(IOException::class)
    open fun readGpx(input: InputStream): Gpx {
        try {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
                setInput(input, "UTF-8")
            }
            val gpx = Gpx()
            val track = Trk()
            var currentPoint: TrkPt? = null
            var inTrack = false
            var inMetadata = false
            var metadataTime: String? = null
            val points = mutableListOf<TrkPt>()

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "gpx" -> {
                            gpx.creator = parser.getAttributeValue(null, "creator")
                            gpx.version = parser.getAttributeValue(null, "version") ?: "1.1"
                        }
                        "metadata" -> inMetadata = true
                        "trk" -> inTrack = true
                        "trkpt" -> {
                            val latitude = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                ?: throw IOException("GPX track point has invalid latitude")
                            val longitude = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                ?: throw IOException("GPX track point has invalid longitude")
                            if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
                                !longitude.isFinite() || longitude !in -180.0..180.0
                            ) throw IOException("GPX track point coordinates are out of range")
                            currentPoint = TrkPt().apply {
                                setLat(latitude)
                                setLon(longitude)
                            }
                        }
                        "name" -> if (inTrack && currentPoint == null) track.name = parser.nextText()
                        "ele" -> {
                            val elevation = parser.nextText().toDoubleOrNull()
                                ?: throw IOException("GPX track point has invalid elevation")
                            currentPoint?.ele = elevation
                        }
                        "time" -> {
                            val value = parser.nextText()
                            if (currentPoint != null) currentPoint.time = value
                            else if (inMetadata) metadataTime = value
                        }
                        "atemp", "hr", "cad", "rhu" -> {
                            if (currentPoint != null && parser.namespace == TRACK_POINT_NAMESPACE) {
                                val value = parser.nextText()
                                val extension = currentPoint.extensions?.trackPointExtension
                                    ?: TrackPointExtension().also { currentPoint.extensions = Extensions(it) }
                                when (parser.name) {
                                    "atemp" -> extension.atemp = value
                                    "hr" -> extension.hr = value
                                    "cad" -> extension.cad = value
                                    "rhu" -> extension.rhu = value
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "trkpt" -> currentPoint?.let { points += it; currentPoint = null }
                        "trk" -> inTrack = false
                        "metadata" -> inMetadata = false
                    }
                }
            }
            if (points.isEmpty()) throw IOException("GPX track has no route points")
            track.trkseg.trkPts.addAll(points)
            gpx.trk = track
            if (metadataTime != null) gpx.metaData = MetaData().apply { time = metadataTime }
            return gpx
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("Invalid GPX document", error)
        }
    }

    private companion object {
        const val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"
        const val TRACK_POINT_NAMESPACE = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1"
        const val XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance"
    }
}
