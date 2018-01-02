package br.com.helpdev.velocimetroalerta.gps;

import android.location.Location;
import android.os.Environment;

import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import br.com.helpdev.velocimetroalerta.objetos.Gpx;
import br.com.helpdev.velocimetroalerta.objetos.MetaData;
import br.com.helpdev.velocimetroalerta.objetos.Trk;
import br.com.helpdev.velocimetroalerta.objetos.TrkPt;
import br.com.helpdev.velocimetroalerta.objetos.TrkSeg;

/**
 * Created by gbzarelli on 11/14/17.
 */

public class GpxUtils {
    private List<Location> tempLocation;

    public GpxUtils(List<Location> tempLocation) {
        this.tempLocation = tempLocation;
    }

    public File gravarGpx(String nomeArquivo) throws Exception {
        File base = new File(Environment.getExternalStorageDirectory(), "/velocimetro_alerta/");
        if (!base.exists()) {
            base.mkdir();
        }
        if (tempLocation != null && !tempLocation.isEmpty()) {

            String bkpNome = nomeArquivo;
            File file;
            int i = 1;
            while ((file = new File(base, nomeArquivo + ".gpx")).exists()) {
                nomeArquivo = bkpNome + "(" + (i++) + ")";
            }
            file.createNewFile();

            Gpx gpx = new Gpx("Velocimetro Alerta Android");
            MetaData metaData = new MetaData();
            metaData.setTime(getUtcGpxTime(tempLocation.get(0).getTime()));
            Trk trk = new Trk();
            trk.setName(bkpNome);
            TrkSeg trkSeg = new TrkSeg();
            ArrayList<TrkPt> trkPtArrayList = new ArrayList<>();
            for (Location loc : tempLocation) {
                TrkPt trkPt = new TrkPt();
                trkPt.setLat(String.valueOf(loc.getLatitude()));
                trkPt.setLon(String.valueOf(loc.getLongitude()));
                trkPt.setEle(loc.getAltitude());
                trkPt.setTime(getUtcGpxTime(loc.getTime()));
                trkPtArrayList.add(trkPt);
            }

            trkSeg.setTrkPts(trkPtArrayList);
            trk.setTrkseg(trkSeg);
            gpx.setTrk(trk);
            gpx.setMetaData(metaData);

            Persister persister = new Persister();
            persister.write(gpx, file);

            return file;
        }
        return null;
    }

    private void gravaArquivoAltitude(String nomeArquivo, File base) {
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

    private String getUtcGpxTime(long data) {//2017-04-11T09:02:40Z
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(data));
    }


}
