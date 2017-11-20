package br.com.helpdev.velocimetroalerta.gps;

import android.location.Location;
import android.os.SystemClock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Guilherme Biff Zarelli on 04/04/16.
 */
public class GPSVelocimetro extends Thread {

    public interface CallbackGpsThread {

        int GPS_ATUALIZADO = 1;
        int GPS_DESATUALIZADO = 2;
        int GPS_PAUSADO = 3;
        int GPS_RETOMADO = 4;
        int GPS_SEM_PRECISAO = 5;
        int GPS_PRECISAO_OK = 6;

        void updateLocation(Location location);

        void updateValues(ObVelocimentroAlerta obVelocimentroAlerta);

        void setGpsStatus(int status);

        void setPauseAutomatic(boolean pause);

        void setBase(long base);

        boolean isPauseAutomaticEneble();

        void debug(String s);
    }

    private static final int STATUS_RODANDO = 1;
    private static final int STATUS_PAUSADO = 2;
    private static final int STATUS_FINALIZADO = 3;

    private static final double CONST_VELOCIDADE_PAUSA_AUTOMATICA = 1;//km/h
    private static final int CONST_PRECISAO_MINIMA = 15;
    private static final int CONST_INTERVALO_DISTANCIA_CALCULO_GANHO = 1_000;//em metros

    private volatile boolean atividadePausada = false;
    private volatile int statusGps = -1;

    private volatile double velocidadeMedia;
    private volatile double velocidadeMaxima;
    private volatile double distanciaTotal;
    private volatile double ganhoAltitude;
    private volatile double ganhoAltitudeNegativa;

    private volatile double tmpDistanciaGanhoAlt;
    private volatile int indexCurso;

    private long firstBase;
    private long baseTempo;
    private long tmpMillisPausa;
    private long tempoPausado;

    //VARIAVAEL RECEBE DISTANCIA TOTAL QUANDO A ATIVIDADE É PAUSADA.
    private double tmpDistanciaPause = -1;
    private double distanciaPausada;

    private List<Location> locationsHistory;
    private ObVelocimentroAlerta obVelocimentroAlerta;

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

    public synchronized void pausar(boolean pause) {
        if (pause) {
            this.status = STATUS_PAUSADO;
        } else {
            this.status = STATUS_RODANDO;
        }
    }

    public List<Location> getLocations() {
        return locationsHistory;
    }

    public void finalizar() {
        this.status = STATUS_FINALIZADO;
    }

    @Override
    public void run() {
        locationsHistory = new ArrayList<>();
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
            if (!atividadePausada) {
                startPause();
            }
            try {
                Thread.sleep(1_000);
            } catch (Exception e) {
            }
        }
        if (pause && status != STATUS_FINALIZADO) {
            if (atividadePausada) {
                callbackGpsThread.setGpsStatus(CallbackGpsThread.GPS_RETOMADO);
                resumePause();
            }
        }
    }

    private synchronized void resumePause() {
        if (!atividadePausada) return;
        atividadePausada = false;
        tempoPausado += SystemClock.elapsedRealtime() - tmpMillisPausa;
        baseTempo = firstBase + tempoPausado;
        callbackGpsThread.setBase(baseTempo);
    }

    private synchronized void startPause() {
        if (atividadePausada) return;
        atividadePausada = true;
        tmpDistanciaPause = distanciaTotal;
        tmpMillisPausa = SystemClock.elapsedRealtime();
        calculaGanhoAltitudeAsync(true);
    }


    private Long tmpCurrentTimeMillis = null;

    private synchronized void process(int views, Location location) {
        double velocidadeAtual = 0;
        double accuracy = 0;
        try {
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
                accuracy = location.getAccuracy();
            }

            if (callbackGpsThread.isPauseAutomaticEneble()) {
                if (velocidadeAtual <= CONST_VELOCIDADE_PAUSA_AUTOMATICA && accuracy <= CONST_PRECISAO_MINIMA) {
                    if (tmpCurrentTimeMillis == null) {
                        tmpCurrentTimeMillis = System.currentTimeMillis();
                    } else if ((System.currentTimeMillis() - tmpCurrentTimeMillis) > 3_000) {
                        if (!atividadePausada) {
                            startPause();
                            callbackGpsThread.setPauseAutomatic(true);
                        }
                        return;
                    }
                } else if (atividadePausada) {
                    tmpCurrentTimeMillis = null;
                    resumePause();
                    callbackGpsThread.setPauseAutomatic(false);
                }
            }

            if (location == null) {
                return;
            }

            if (accuracy <= CONST_PRECISAO_MINIMA) {
                callbackGpsThread.setGpsStatus(CallbackGpsThread.GPS_PRECISAO_OK);

                try {
                    calcularDistancias(location);
                    calcularVelocidadeMedia();
                    calculaGanhoAltitudeAsync(false);
                } catch (Throwable e) {
                    e.printStackTrace();
                }

                locationsHistory.add(location);

                if (velocidadeAtual > velocidadeMaxima) {
                    velocidadeMaxima = velocidadeAtual;
                }
            } else {
                callbackGpsThread.setGpsStatus(CallbackGpsThread.GPS_SEM_PRECISAO);
            }
        } finally {
            notifyUpdate(location, velocidadeAtual);
        }
    }

    private void notifyUpdate(Location location, double velocidadeAtual) {
        obVelocimentroAlerta = new ObVelocimentroAlerta(
                locationsHistory.isEmpty() ? new Date() : new Date(locationsHistory.get(0).getTime()),
                getTempoAtividade(),
                velocidadeMedia,
                velocidadeAtual,
                velocidadeMaxima,
                distanciaTotal,
                location == null ? 0 : location.getAltitude(),
                ganhoAltitude,
                ganhoAltitudeNegativa,
                location == null ? 0 : location.getAccuracy());

        callbackGpsThread.updateValues(obVelocimentroAlerta);
    }

    private volatile boolean calculandoAltitude = false;

    private void calculaGanhoAltitudeAsync(final boolean forcar) {
        if (calculandoAltitude) return;
        new Thread() {
            @Override
            public void run() {
                try {
                    calculandoAltitude = true;
                    calculaGanhoAltitude(forcar);
                } finally {
                    calculandoAltitude = false;
                }
            }
        }.start();
    }

    private void calculaGanhoAltitude(boolean forcar) {
        if (locationsHistory == null || locationsHistory.isEmpty() || (!forcar && distanciaTotal <= (tmpDistanciaGanhoAlt + CONST_INTERVALO_DISTANCIA_CALCULO_GANHO))) {
            return;
        }

        double constMediaAccuracy = 0;
        for (Location loc : locationsHistory) {
            constMediaAccuracy += loc.getAccuracy();
        }
        constMediaAccuracy = constMediaAccuracy / locationsHistory.size();

        if (forcar) {
            indexCurso = 0;
            ganhoAltitude = 0;
            ganhoAltitudeNegativa = 0;
        }
        tmpDistanciaGanhoAlt = distanciaTotal;

        double altA, altB;
        int indexA, indexB;
        boolean climb = false;

        altA = locationsHistory.get(indexCurso).getAltitude();
        indexA = indexCurso;

        for (indexB = indexCurso; indexB < locationsHistory.size(); indexB++) {
            altB = locationsHistory.get(indexB).getAltitude();
            double difAlt = altB - altA;

            if (difAlt > 0 && difAlt >= constMediaAccuracy) {//GANHANDO ALTITUDE
                if (!climb) {//IF PARA ACHAR PICO NEGATIVO, QUANDO SAI DE UMA DESCIDA E INICIA UMA SUBIDA
                    ganhoAltitudeNegativa += getGanhoPico(false, indexA, indexB, altA);
                }
                ganhoAltitude += difAlt;
                climb = true;
            } else if (difAlt < 0 && ((difAlt * -1) >= constMediaAccuracy)) {//PERDENDO ALTITUDE
                if (climb) {//IF PARA ACHAR PICO POSITIVO, QUANDO SAI DE UMA SUBIDA E INICIA UMA DESCIDA
                    ganhoAltitude += getGanhoPico(true, indexA, indexB, altA);
                }
                ganhoAltitudeNegativa += (difAlt * -1);
                climb = false;
            } else {
                continue;
            }
            //SE ENTROU EM ALGUM IF REDEFINE OS INDEX;
            indexA = indexB;
            altA = altB;
        }
        indexCurso = locationsHistory.size() - 1;

        if (forcar) {
            notifyUpdate(locationsHistory.get(locationsHistory.size() - 1), 0);
        }
    }

    private double getGanhoPico(boolean positivo, int indexA, int indexB, double altA) {
        double constA = 0;
        for (int indexPico = indexA + 1; indexPico <= indexB; indexPico++) {
            double constB = locationsHistory.get(indexPico).getAltitude() - altA;
            if (positivo && constA < constB || !positivo && constA > constB) {
                constA = constB;
            }
        }
        if (positivo && constA > 0) {
            return constA;
        } else if (!positivo && constA < 0) {
            return constA * -1;
        }
        return 0;
    }

    private void calcularDistancias(Location location) {
        if (locationsHistory.isEmpty()) return;
        distanciaTotal += calculaDistancia(locationsHistory.get(locationsHistory.size() - 1).getLatitude(),
                locationsHistory.get(locationsHistory.size() - 1).getLongitude(), location.getLatitude(), location.getLongitude());

        //** CASO A DISTANCIA ESTEJA PAUSADA, AO FAZER O CALCULO DA NOVA DISTANCIA, ADICIONA NA VARIAVEL QUE CONSTROLA
        //A DISTANCIA QUE FICOU PAUSADA, PARA NAO LEVAR EM CONSIDERAÇÃO NO CALCULO DE MEDIA.;
        if (tmpDistanciaPause > 0) {
            distanciaPausada += distanciaTotal - tmpDistanciaPause;
            tmpDistanciaPause = -1;
        }

    }

    private void calcularVelocidadeMedia() throws Throwable {
        double hours = new BigDecimal(getTempoAtividade())
                .divide(BigDecimal.valueOf(3_600_000), 10, BigDecimal.ROUND_HALF_UP)
                .doubleValue();
        velocidadeMedia = (distanciaTotal - distanciaPausada) / hours;
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
        return earthRadius * c;
    }

    public void setCallbackGpsThread(CallbackGpsThread callbackGpsThread) {
        this.callbackGpsThread = callbackGpsThread;
    }

    /**
     * @return Dureção em millis
     */
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
