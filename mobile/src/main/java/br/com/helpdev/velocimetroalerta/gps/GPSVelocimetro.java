package br.com.helpdev.velocimetroalerta.gps;

import android.location.Location;
import android.os.SystemClock;

import java.math.BigDecimal;

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
    private volatile double velocidadeAtual;
    private volatile double distanciaTotal;

    private long firstBase;
    private long baseTempo;
    private long tmpMillisPausa;
    private long tempoPausado;

    private Location tempLocation;

    public interface CallbackGpsThread {

        public static final int GPS_ATUALIZADO = 1;
        public static final int GPS_DESATUALIZADO = 2;

        void updateLocation(Location location);

        void updateValues(long tempo, double vMedia, double vAtual, double vMaxima, double distanciaTotal);

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


    @Override
    public void run() {
        while (status != STATUS_FINALIZADO) {
            process(gps.getViews(), gps.getLocation());
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
            verificarBtPausa();
        }
    }

    private void verificarBtPausa() {
        boolean pause = false;
        while (status == STATUS_PAUSADO) {
            pause = true;
            if (!pauseAutomatico) {
                startPause();
            }
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
        }
        if (pause && status != STATUS_FINALIZADO) {
            if (pauseAutomatico) {
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
        this.velocidadeAtual = 0;
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
        }

        if (callbackGpsThread.isPauseAutomatic()) {
            if (velocidadeAtual <= 0) {
                if (!pauseAutomatico) {
                    startPause();
                    callbackGpsThread.setPauseAutomatic(true);
                }
                return;
            } else {
                if (pauseAutomatico) {
                    resumePause();
                    callbackGpsThread.setPauseAutomatic(false);
                }
            }
        }

        if (tempLocation != null) {
            distanciaTotal += calculaDistancia(tempLocation.getLatitude(), tempLocation.getLongitude(), location.getLatitude(), location.getLongitude());
            try {
                double hours = new BigDecimal(getTempoAtividade())
                        .divide(BigDecimal.valueOf(3_600_000), 10, BigDecimal.ROUND_HALF_UP)
                        .doubleValue();
                velocidadeMedia = distanciaTotal / hours;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        tempLocation = location;

        if (velocidadeAtual > velocidadeMaxima) {
            velocidadeMaxima = velocidadeAtual;
        }

        callbackGpsThread.updateValues(getTempoAtividade(), velocidadeMedia, velocidadeAtual, velocidadeMaxima, distanciaTotal);
    }


    public static double calculaDistancia(double lat1, double lng1, double lat2, double lng2) {
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

    public long getTempoAtividade() {
        return SystemClock.elapsedRealtime() - baseTempo;
    }

    public double getVelocidadeMedia() {
        return velocidadeMedia;
    }

    public double getVelocidadeMaxima() {
        return velocidadeMaxima;
    }

    public double getVelocidadeAtual() {
        return velocidadeAtual;
    }

    public double getDistanciaTotal() {
        return distanciaTotal;
    }
}
