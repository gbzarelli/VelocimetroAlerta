package br.com.helpdev.velocimetroalerta.gpx;

import android.location.Location;

import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx;
import br.com.helpdev.velocimetroalerta.gpx.objects.MetaData;
import br.com.helpdev.velocimetroalerta.gpx.objects.Trk;
import br.com.helpdev.velocimetroalerta.gpx.objects.TrkSeg;

/**
 * Created by gbzarelli on 11/14/17.
 */

public class GpxUtils {

    public File gravarGpx(Gpx gpx, File base, String nomeArquivo) throws Exception {
        if (!base.exists()) {
            base.mkdir();
        }

        String bkpNome = nomeArquivo;
        File file;
        int i = 1;
        while ((file = new File(base, nomeArquivo + ".gpx")).exists()) {
            nomeArquivo = bkpNome + "(" + (i++) + ")";
        }
        file.createNewFile();

        Persister persister = new Persister();
        persister.write(gpx, file);

        return file;

    }

    public File gravarGpx(List<Location> tempLocation, File base, String nomeArquivo, String gpxCreator) throws Exception {
        if (tempLocation != null && !tempLocation.isEmpty()) {
            Gpx gpx = new Gpx(gpxCreator);
            MetaData metaData = new MetaData();
            metaData.setTime(getUtcGpxTime(tempLocation.get(0).getTime()));
            Trk trk = new Trk();
            trk.setName(nomeArquivo);
            TrkSeg trkSeg = new TrkSeg();
            trkSeg.addTrkPts(tempLocation);
            trk.setTrkseg(trkSeg);
            gpx.setTrk(trk);
            gpx.setMetaData(metaData);

            return gravarGpx(gpx, base, nomeArquivo);
        }
        return null;
    }

    private void gravaArquivoAltitude(List<Location> tempLocation, String nomeArquivo, File base) {
        try {
            StringBuilder sb = null;
            for (Location lc : tempLocation) {
                if (sb == null) {
                    sb = new StringBuilder("{");
                } else {
                    sb.append(",");
                }
                sb.append(lc.getAltitude());
            }
            if (sb != null) {
                sb.append("}");
                File fileAlt = new File(base, nomeArquivo + ".alt");
                FileOutputStream fos = new FileOutputStream(fileAlt);
                fos.write(sb.toString().getBytes());
                fos.flush();
                fos.close();
            }
        } catch (Throwable t) {
        }
    }

    public static String getUtcGpxTime(long data) {//2017-04-11T09:02:40Z
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(data));
    }


}
