package br.com.helpdev.velocimetroalerta.gps;

import android.location.Location;
import android.os.Environment;
import android.os.SystemClock;

import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
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
 * Created by Guilherme Biff Zarelli on 04/04/16.
 */
public class GPSVelocimetro extends Thread {

    public static final int STATUS_RODANDO = 1;
    public static final int STATUS_PAUSADO = 2;
    public static final int STATUS_FINALIZADO = 3;

    private volatile boolean pauseAutomatico = false;
    private volatile int statusGps = -1;

    private volatile double velocidadeMedia;
    private volatile double velocidadeMaxima;
    private volatile double distanciaTotal;
    private volatile double ganhoAltitude;
    private volatile double tempMediaGanho;

    private long firstBase;
    private long baseTempo;
    private long tmpMillisPausa;
    private long tempoPausado;

    private List<Location> tempLocation;
    private ObVelocimentroAlerta obVelocimentroAlerta;

    public interface CallbackGpsThread {

        public static final int GPS_ATUALIZADO = 1;
        public static final int GPS_DESATUALIZADO = 2;
        public static final int GPS_PAUSADO = 3;
        public static final int GPS_RETOMADO = 4;
        public static final int GPS_SEM_PRECISAO = 5;
        public static final int GPS_PRECISAO_OK = 6;

        void updateLocation(Location location);

        void updateValues(ObVelocimentroAlerta obVelocimentroAlerta);

        void setGpsStatus(int status);

        void setPauseAutomatic(boolean pause);

        void setBase(long base);

        boolean isPauseAutomatic();
    }

    private volatile int status;
    private CallbackGpsThread callbackGpsThread;
    private Gps gps;

    public GPSVelocimetro(Gps gps, CallbackGpsThread callbackGpsThread) {
        super();
        this.gps = gps;
        this.callbackGpsThread = callbackGpsThread;
    }

    @Override
    public synchronized void start() {
        super.start();
        firstBase = SystemClock.elapsedRealtime();
        baseTempo = SystemClock.elapsedRealtime();
        callbackGpsThread.setBase(baseTempo);
        status = STATUS_RODANDO;
    }

    public void pausar(boolean pause) {
        if (pause) {
            this.status = STATUS_PAUSADO;
        } else {
            this.status = STATUS_RODANDO;
        }
    }

    public void finalizar() {
        this.status = STATUS_FINALIZADO;
    }

    public String gravarGpx(String nomeArquivo) throws Exception {
        if (tempLocation != null && !tempLocation.isEmpty()) {
            File base = new File(Environment.getExternalStorageDirectory(), "/velocimetro_alerta/");
            if (!base.exists()) {
                base.mkdir();
            }
            String bkpNome = nomeArquivo;
            File file;
            int i = 1;
            while ((file = new File(base, nomeArquivo + ".gpx")).exists()) {
                nomeArquivo = bkpNome + "(" + (i++) + ")";
            }

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

            return file.getAbsolutePath();
        }
        return null;
    }

    private String getUtcGpxTime(long data) {//2017-04-11T09:02:40Z
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(data));
    }

    @Override
    public void run() {
        while (status != STATUS_FINALIZADO) {
            process(gps.getViews(), gps.getLocation());
            try {
                Thread.sleep(1_000);
            } catch (Exception e) {
            }
            verificarBtPausa();
        }
    }

    private void verificarBtPausa() {
        boolean pause = false;
        while (status == STATUS_PAUSADO) {
            if (!pause) {
                callbackGpsThread.setGpsStatus(CallbackGpsThread.GPS_PAUSADO);
            }
            pause = true;
            if (!pauseAutomatico) {
                startPause();
            }
            try {
                Thread.sleep(1_000);
            } catch (Exception e) {
            }
        }
        if (pause && status != STATUS_FINALIZADO) {
            if (pauseAutomatico) {
                callbackGpsThread.setGpsStatus(CallbackGpsThread.GPS_RETOMADO);
                resumePause();
            }
        }
    }

    private void resumePause() {
        pauseAutomatico = false;
        tempoPausado += SystemClock.elapsedRealtime() - tmpMillisPausa;
        baseTempo = firstBase + tempoPausado;
        callbackGpsThread.setBase(baseTempo);
    }

    private void startPause() {
        pauseAutomatico = true;
        tmpMillisPausa = SystemClock.elapsedRealtime();
    }


    private void process(int views, Location location) {
        double velocidadeAtual = 0;
        double altitude = 0;
        if (views > 2 || location == null) {
            if (statusGps != CallbackGpsThread.GPS_DESATUALIZADO) {
                statusGps = CallbackGpsThread.GPS_DESATUALIZADO;
                callbackGpsThread.setGpsStatus(CallbackGpsThread.GPS_DESATUALIZADO);
            }
        } else {
            if (statusGps != CallbackGpsThread.GPS_ATUALIZADO) {
                statusGps = CallbackGpsThread.GPS_ATUALIZADO;
                callbackGpsThread.setGpsStatus(CallbackGpsThread.GPS_ATUALIZADO);
            }
            callbackGpsThread.updateLocation(location);
            velocidadeAtual = location.getSpeed() * 3.6f;
            altitude = location.getAltitude();
        }

        if (callbackGpsThread.isPauseAutomatic()) {
            if (velocidadeAtual <= 0) {
                if (!pauseAutomatico) {
                    startPause();
                    callbackGpsThread.setPauseAutomatic(true);
                }
                return;
            } else if (pauseAutomatico) {
                resumePause();
                callbackGpsThread.setPauseAutomatic(false);
            }
        }

        if (location == null) {
            return;
        }

        if (tempLocation != null && tempLocation.size() > 0) {
            try {
                calcularDistancia(location);
                calcularVelocidadeMedia();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        } else {
            tempLocation = new ArrayList<>();
        }

        if (location.getAccuracy() < 10) {
            callbackGpsThread.setGpsStatus(CallbackGpsThread.GPS_PRECISAO_OK);
            tempLocation.add(location);
        } else {
            callbackGpsThread.setGpsStatus(CallbackGpsThread.GPS_SEM_PRECISAO);
        }

        if (velocidadeAtual > velocidadeMaxima) {
            velocidadeMaxima = velocidadeAtual;
        }

        obVelocimentroAlerta = new ObVelocimentroAlerta(
                tempLocation.isEmpty() ? new Date() : new Date(tempLocation.get(0).getTime()),
                getTempoAtividade(),
                velocidadeMedia,
                velocidadeAtual,
                velocidadeMaxima,
                distanciaTotal,
                altitude,
                ganhoAltitude,
                location.getAccuracy());

        callbackGpsThread.updateValues(obVelocimentroAlerta);
    }

    private int lastIndexCalc;


    private void calcularGanhoAltitude() {
        Location startClimb = tempLocation.get(lastIndexCalc);
        Location endClimb = null;

        int count = 0;
        if ((lastIndexCalc + 1) < tempLocation.size()) {
            for (int i = (lastIndexCalc + 1); i < tempLocation.size(); i++) {
                Location atualLoc = tempLocation.get(i);
                if (atualLoc.getAltitude() < startClimb.getAltitude()) {
                    count += 1;
                } else {
                    endClimb = atualLoc;
                    count = 0;
                }
            }
        }
    }

    private void calcularDistancia(Location location) {
        distanciaTotal += calculaDistancia(tempLocation.get(tempLocation.size() - 1).getLatitude(),
                tempLocation.get(tempLocation.size() - 1).getLongitude(), location.getLatitude(), location.getLongitude());
    }

    private void calcularVelocidadeMedia() throws Throwable {
        double hours = new BigDecimal(getTempoAtividade())
                .divide(BigDecimal.valueOf(3_600_000), 10, BigDecimal.ROUND_HALF_UP)
                .doubleValue();
        velocidadeMedia = distanciaTotal / hours;
    }

    private double calculaDistancia(double lat1, double lng1, double lat2, double lng2) {
        //double earthRadius = 3958.75;//miles
        double earthRadius = 6371;//kilometers
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double sindLat = Math.sin(dLat / 2);
        double sindLng = Math.sin(dLng / 2);
        double a = Math.pow(sindLat, 2) + Math.pow(sindLng, 2)
                * Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double dist = earthRadius * c;

        return dist;
    }

    public void setCallbackGpsThread(CallbackGpsThread callbackGpsThread) {
        this.callbackGpsThread = callbackGpsThread;
    }

    private long getTempoAtividade() {
        return SystemClock.elapsedRealtime() - baseTempo;
    }

    public ObVelocimentroAlerta getObVelocimentroAlerta() {
        if (obVelocimentroAlerta == null) {
            obVelocimentroAlerta = new ObVelocimentroAlerta();
        }
        return obVelocimentroAlerta;
    }
}
