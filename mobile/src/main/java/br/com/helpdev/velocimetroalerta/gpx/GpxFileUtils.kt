package br.com.helpdev.velocimetroalerta.gpx

import android.location.Location

import org.simpleframework.xml.core.Persister
import org.simpleframework.xml.stream.Format

import java.io.File
import java.io.FileOutputStream

import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx
import br.com.helpdev.velocimetroalerta.gpx.objects.MetaData
import br.com.helpdev.velocimetroalerta.gpx.objects.Trk
import br.com.helpdev.velocimetroalerta.gpx.objects.TrkSeg

/**
 * Created by gbzarelli on 11/14/17.
 */

class GpxFileUtils {

    @Throws(Exception::class)
    fun writeGpx(gpx: Gpx, base: File, nameFile: String): File {
        var nFile = nameFile
        if (!base.exists()) {
            base.mkdir()
        }

        var file: File
        var i = 1

        do {
            file = File(base, "$nFile.gpx")
            nFile = nameFile + "(" + i++ + ")"
        } while (file.exists())

        file.createNewFile()

        val persister = Persister(Format("<?xml version=\"1.0\" encoding= \"UTF-8\" ?>"))
        persister.write(gpx, file)

        return file

    }

    @Throws(Exception::class)
    fun writeGpxOnlyLocation(tempLocation: List<Location>?, base: File, nomeArquivo: String, gpxCreator: String): File? {
        if (tempLocation != null && !tempLocation.isEmpty()) {
            val gpx = Gpx(gpxCreator)
            val metaData = MetaData()
            metaData.time = Gpx.getUtcGpxTime(tempLocation[0].time)
            val trk = Trk()
            trk.name = nomeArquivo
            val trkSeg = TrkSeg()
            trkSeg.addTrkPts(tempLocation)
            trk.trkseg = trkSeg
            gpx.trk = trk
            gpx.metaData = metaData

            return writeGpx(gpx, base, nomeArquivo)
        }
        return null
    }

    private fun writeAltitudeDebugFile(tempLocation: List<Location>, nomeArquivo: String, base: File) {
        try {
            var sb: StringBuilder? = null
            for (lc in tempLocation) {
                if (sb == null) {
                    sb = StringBuilder("{")
                } else {
                    sb.append(",")
                }
                sb.append(lc.altitude)
            }
            if (sb != null) {
                sb.append("}")
                val fileAlt = File(base, "$nomeArquivo.alt")
                val fos = FileOutputStream(fileAlt)
                fos.write(sb.toString().toByteArray())
                fos.flush()
                fos.close()
            }
        } catch (t: Throwable) {
        }

    }

}
