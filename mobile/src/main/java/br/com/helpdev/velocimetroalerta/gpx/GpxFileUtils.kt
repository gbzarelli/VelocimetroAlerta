package br.com.helpdev.velocimetroalerta.gpx

import android.location.Location
import android.util.Xml
import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx
import java.io.OutputStream

class GpxFileUtils {
    /** Writes GPX 1.1 to [output] and leaves ownership of the stream with the caller. */
    @Throws(Exception::class)
    fun writeGpx(gpx: Gpx, output: OutputStream) {
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

    private companion object {
        const val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"
        const val TRACK_POINT_NAMESPACE = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1"
        const val XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance"
    }
}
