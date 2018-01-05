package br.com.helpdev.velocimetroalerta.gps;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.annotation.Nullable;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import br.com.helpdev.velocimetroalerta.MainActivity;
import br.com.helpdev.velocimetroalerta.MySpeechSpeed;
import br.com.helpdev.velocimetroalerta.R;
import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx;
import br.com.helpdev.velocimetroalerta.gpx.objects.MetaData;
import br.com.helpdev.velocimetroalerta.gpx.objects.Trk;
import br.com.helpdev.velocimetroalerta.gpx.objects.TrkPt;

/**
 * Created by Guilherme Biff Zarelli on 04/04/16.
 */
public class ServiceVelocimetro extends Service implements Runnable {


    private static final int ID_NOTIFICATION_FOREGROUND = 10;

    public interface CallbackGpsThread {

        int GPS_ATUALIZADO = 1;
        int GPS_DESATUALIZADO = 2;
        //--
        int GPS_PAUSADO = 3;
        int GPS_RETOMADO = 4;
        //--
        int GPS_SEM_PRECISAO = 5;
        int GPS_PRECISAO_OK = 6;

        void updateValues(ObVelocimentroAlerta obVelocimentroAlerta);

        void setGpsSituacao(int gpsSituacao);

        void setGpsPausa(int gpsPausa);

        void setGpsPrecisao(int precisao);

        void setPauseAutomatic(boolean pause);

        void setBaseChronometer(long base, boolean resume);

    }

    private static final int STATUS_RODANDO = 1;
    private static final int STATUS_PAUSADO = 2;
    private static final int STATUS_FINALIZADO = 3;

    private static final double CONST_VELOCIDADE_PAUSA_AUTOMATICA = 1;//km/h
    private static final int CONST_PRECISAO_MINIMA = 15;
    private static final double CONST_INTERVALO_DISTANCIA_CALCULO_GANHO = 1;//em km

    private long firstBase;
    private long baseTime;

    private volatile int statusService;

    private volatile int gpsSituacao = CallbackGpsThread.GPS_ATUALIZADO;
    private volatile boolean inPauseAutomatic = false;
    private volatile int gpsPausa = CallbackGpsThread.GPS_RETOMADO;
    private volatile int gpsPrecisao = CallbackGpsThread.GPS_PRECISAO_OK;

    private volatile int indexCurso = 0;
    private volatile boolean atividadePausada = false;
    private volatile double tmpDistanciaGanhoAlt = 0;

    private volatile Gpx gpx;
    private volatile ObVelocimentroAlerta obVelocimentroAlerta;

    private final MySpeechSpeed mySpeechSpeed = new MySpeechSpeed();
    private final Gps gps = new Gps();

    private boolean pauseAutomaticEneble = true;
    private long tmpMillisPausa = 0;
    private Long tmpCurrentTimeMillis = null;
    //VARIAVAEL RECEBE DISTANCIA TOTAL QUANDO A ATIVIDADE É PAUSADA.
    private double tmpDistanciaPause = -1;
    private Vibrator vibrator;

    private CallbackGpsThread callbackGpsThread;
    private Notification.Builder myNotificationBuilder;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mySpeechSpeed.init(this);
        gps.init(this);
        myNotificationBuilder = new Notification.Builder(this);
        myNotificationBuilder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setOngoing(true);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        myNotificationBuilder.setContentIntent(pendingIntent);
        statusService = STATUS_FINALIZADO;
    }

    public boolean isRunning() {
        return statusService != STATUS_FINALIZADO;
    }

    public class MyBinder extends Binder {
        private ServiceVelocimetro serviceVelocimetro;

        public MyBinder(ServiceVelocimetro serviceVelocimetro) {
            this.serviceVelocimetro = serviceVelocimetro;
        }

        public ServiceVelocimetro getServiceVelocimetro() {
            return serviceVelocimetro;
        }
    }

    public void configure(boolean pauseAutomaticEneble) {
        this.pauseAutomaticEneble = pauseAutomaticEneble;
        mySpeechSpeed.recarregarConfiguracoes(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new MyBinder(this);
    }

    public synchronized void pausar(boolean pause) {
        if (pause) {
            this.statusService = STATUS_PAUSADO;
        } else {
            this.statusService = STATUS_RODANDO;
        }
    }

    public void stop() {
        this.statusService = STATUS_FINALIZADO;
        stopForeground(true);
    }

    public void finalizeService() {
        mySpeechSpeed.close();
        gps.close();
        stopSelf();
    }

    public void start(CallbackGpsThread callbackGpsThread) {
        this.callbackGpsThread = callbackGpsThread;
        if (statusService == STATUS_FINALIZADO) {
            Thread myThread = new Thread(this);
            myThread.setDaemon(true);
            myThread.setName("TH-" + ServiceVelocimetro.class.getName());
            myThread.start();
        }
    }

    private void reset() {
        gpx = new Gpx("Velocimetro Alerta Android");
        obVelocimentroAlerta = new ObVelocimentroAlerta();
        long base = SystemClock.elapsedRealtime();
        firstBase = base;
        baseTime = base;
        inPauseAutomatic = false;
        atividadePausada = false;
        if (callbackGpsThread != null) callbackGpsThread.setBaseChronometer(base, true);
        tmpCurrentTimeMillis = null;
        indexCurso = 0;
        tmpDistanciaGanhoAlt = 0;
        tmpDistanciaPause = -1;
        pauseAutomaticEneble = true;
        tmpMillisPausa = 0;
        gpsSituacao = CallbackGpsThread.GPS_ATUALIZADO;
        gpsPausa = CallbackGpsThread.GPS_RETOMADO;
        gpsPrecisao = CallbackGpsThread.GPS_PRECISAO_OK;
    }

    @Override
    public void run() {
        reset();
        statusService = STATUS_RODANDO;
        startForeground(ID_NOTIFICATION_FOREGROUND, myNotificationBuilder.getNotification());

        while (statusService != STATUS_FINALIZADO) {
            process(gps.getViews(), gps.getLocation());
            try {
                Thread.sleep(1_000);
            } catch (Exception e) {
            }
            verificarStatusPausado();
        }
    }

    public Gpx getGpx() {
        return gpx;
    }

    private void verificarStatusPausado() {
        boolean pause = false;
        while (statusService == STATUS_PAUSADO) {
            if (!pause) {
                gpsPausa = CallbackGpsThread.GPS_PAUSADO;
                if (callbackGpsThread != null) callbackGpsThread.setGpsPausa(gpsPausa);
            }
            pause = true;
            if (!atividadePausada) {
                startPause(false);
                notifyNotificationUpdate();
            }
            try {
                Thread.sleep(1_000);
            } catch (Exception e) {
            }
        }
        if (pause && statusService != STATUS_FINALIZADO) {
            if (atividadePausada) {
                gpsPausa = CallbackGpsThread.GPS_RETOMADO;
                if (callbackGpsThread != null) callbackGpsThread.setGpsPausa(gpsPausa);
                resumePause(false);
            }
        }
    }

    public long getTempoCronometro() {
        return SystemClock.elapsedRealtime() - baseTime;
    }

    private synchronized void resumePause(boolean automatic) {
        vibrar();
        if (!atividadePausada) return;
        atividadePausada = false;
        notifyBase(true, true);
        if (automatic) {
            inPauseAutomatic = false;
            if (callbackGpsThread != null) callbackGpsThread.setPauseAutomatic(inPauseAutomatic);
        }
    }

    private void notifyBase(boolean atividadePausada, boolean resume) {
        if (atividadePausada) {
            getObVelocimentroAlerta().addDuracaoPausado(SystemClock.elapsedRealtime() - tmpMillisPausa);
            baseTime = firstBase + getObVelocimentroAlerta().getDuracaoPausado();
            tmpMillisPausa = SystemClock.elapsedRealtime();
        }
        if (callbackGpsThread != null) callbackGpsThread.setBaseChronometer(baseTime, resume);
    }

    private synchronized void startPause(boolean automatic) {
        vibrar();
        if (atividadePausada) return;
        atividadePausada = true;
        tmpDistanciaPause = getObVelocimentroAlerta().getDistancia();
        tmpMillisPausa = SystemClock.elapsedRealtime();
        calculaGanhoAltitudeAsync(true);
        if (automatic) {
            inPauseAutomatic = true;
            if (callbackGpsThread != null) callbackGpsThread.setPauseAutomatic(inPauseAutomatic);
        }
    }

    private synchronized void process(int views, Location location) {
        double velocidadeAtual = 0;
        double accuracy = 0;
        try {
            if (views > 2 || location == null) {
                if (gpsSituacao != CallbackGpsThread.GPS_DESATUALIZADO) {
                    gpsSituacao = CallbackGpsThread.GPS_DESATUALIZADO;
                    if (callbackGpsThread != null) callbackGpsThread.setGpsSituacao(gpsSituacao);
                }
            } else {
                if (gpsSituacao != CallbackGpsThread.GPS_ATUALIZADO) {
                    gpsSituacao = CallbackGpsThread.GPS_ATUALIZADO;
                    if (callbackGpsThread != null) callbackGpsThread.setGpsSituacao(gpsSituacao);
                }
                velocidadeAtual = location.getSpeed() * 3.6f;
                accuracy = location.getAccuracy();
                getObVelocimentroAlerta().setAltitudeAtual(location.getAltitude());
                getObVelocimentroAlerta().setPrecisaoAtual(accuracy);
            }

            if (pauseAutomaticEneble) {
                if (velocidadeAtual <= CONST_VELOCIDADE_PAUSA_AUTOMATICA && accuracy <= CONST_PRECISAO_MINIMA) {
                    if (tmpCurrentTimeMillis == null) {
                        tmpCurrentTimeMillis = System.currentTimeMillis();
                    } else if ((System.currentTimeMillis() - tmpCurrentTimeMillis) > 3_000) {
                        if (!atividadePausada) {
                            startPause(true);
                        }
                        return;
                    }
                } else if (atividadePausada) {
                    tmpCurrentTimeMillis = null;
                    resumePause(true);
                }
            }

            if (location == null) {
                return;
            }

            if (accuracy <= CONST_PRECISAO_MINIMA) {
                gpsPrecisao = CallbackGpsThread.GPS_PRECISAO_OK;
                if (callbackGpsThread != null) callbackGpsThread.setGpsPrecisao(gpsPrecisao);

                try {
                    calcularDistancias(location);
                    calculaGanhoAltitudeAsync(false);
                    calculaVelocidadeMedia();
                } catch (Throwable e) {
                    e.printStackTrace();
                }

                if (gpx.getMetaData() == null) {
                    getObVelocimentroAlerta().setDateInicio(new Date(location.getTime()));
                    MetaData metaData = new MetaData();
                    metaData.setTime(Gpx.getUtcGpxTime(location.getTime()));
                    gpx.setMetaData(metaData);

                    Trk trk = new Trk();
                    trk.setName("VEL_ALERTA_" + new SimpleDateFormat("yyyyMMdd_HHmm").format(getObVelocimentroAlerta().getDateInicio()));
                    gpx.setTrk(trk);
                }
                gpx.getTrk().getTrkseg().addTrkPt(location);

                if (velocidadeAtual > getObVelocimentroAlerta().getvMaxima()) {
                    getObVelocimentroAlerta().setvMaxima(velocidadeAtual);
                }
            } else {
                gpsPrecisao = CallbackGpsThread.GPS_SEM_PRECISAO;
                if (callbackGpsThread != null) callbackGpsThread.setGpsPrecisao(gpsPrecisao);
            }

            getObVelocimentroAlerta().setDuracao(getTempoCronometro());
        } finally {
            getObVelocimentroAlerta().setvAtual(velocidadeAtual);
            notifyUpdate();
        }
    }

    private void notifyUpdate() {
        mySpeechSpeed.updateValues(obVelocimentroAlerta);
        if (callbackGpsThread != null) callbackGpsThread.updateValues(obVelocimentroAlerta);
        notifyNotificationUpdate();
    }

    private void notifyNotificationUpdate() {
        String title = getString(R.string.notification_execute);
        if (statusService == STATUS_PAUSADO) {
            title = getString(R.string.notification_pause);
        } else if (inPauseAutomatic) {
            title = getString(R.string.notification_pause_automatic);
        }
        myNotificationBuilder.setContentTitle(title);
        myNotificationBuilder.setContentText(obVelocimentroAlerta.toStringNotification());
        notificationManager.notify(ID_NOTIFICATION_FOREGROUND, myNotificationBuilder.getNotification());
    }

    private void updateNotification(String title, @Nullable String contentText) {

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

    private List<TrkPt> getTrkPts() {
        return gpx.getTrk().getTrkseg().getTrkPts();
    }

    private void calculaGanhoAltitude(boolean forcar) {
        List<TrkPt> locationsHistory = getTrkPts();
        if (locationsHistory == null || locationsHistory.isEmpty() || (!forcar && getObVelocimentroAlerta().getDistancia() <= (tmpDistanciaGanhoAlt + CONST_INTERVALO_DISTANCIA_CALCULO_GANHO))) {
            return;
        }

        double constMediaAccuracy = 0;
        for (TrkPt loc : locationsHistory) {
            constMediaAccuracy += loc.getAccuracy();
        }
        constMediaAccuracy = constMediaAccuracy / locationsHistory.size();

        if (forcar) {
            indexCurso = 0;
            getObVelocimentroAlerta().setGanhoAltitude(0);
            getObVelocimentroAlerta().setPerdaAltitude(0);
        }
        tmpDistanciaGanhoAlt = getObVelocimentroAlerta().getDistancia();

        double altA, altB;
        int indexA, indexB;
        boolean climb = false;

        altA = locationsHistory.get(indexCurso).getEle();
        indexA = indexCurso;

        for (indexB = indexCurso; indexB < locationsHistory.size(); indexB++) {
            altB = locationsHistory.get(indexB).getEle();
            double difAlt = altB - altA;

            if (difAlt > 0 && difAlt >= constMediaAccuracy) {//GANHANDO ALTITUDE
                if (!climb) {//IF PARA ACHAR PICO NEGATIVO, QUANDO SAI DE UMA DESCIDA E INICIA UMA SUBIDA
                    getObVelocimentroAlerta().addPerdaAltitude(getGanhoPico(false, indexA, indexB, altA));
                }
                getObVelocimentroAlerta().addGanhoAltitude(difAlt);
                climb = true;
            } else if (difAlt < 0 && ((difAlt * -1) >= constMediaAccuracy)) {//PERDENDO ALTITUDE
                if (climb) {//IF PARA ACHAR PICO POSITIVO, QUANDO SAI DE UMA SUBIDA E INICIA UMA DESCIDA
                    getObVelocimentroAlerta().addGanhoAltitude(getGanhoPico(true, indexA, indexB, altA));
                }
                getObVelocimentroAlerta().addPerdaAltitude((difAlt * -1));
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
            notifyUpdate();
        }
    }

    private double getGanhoPico(boolean positivo, int indexA, int indexB, double altA) {
        double constA = 0;
        for (int indexPico = indexA + 1; indexPico <= indexB; indexPico++) {
            double constB = getTrkPts().get(indexPico).getEle() - altA;
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

    private void calculaVelocidadeMedia() {
        double hours = new BigDecimal(getTempoCronometro())
                .divide(BigDecimal.valueOf(3_600_000), 10, BigDecimal.ROUND_HALF_UP)
                .doubleValue();
        getObVelocimentroAlerta().setvMedia(
                (getObVelocimentroAlerta().getDistancia() - getObVelocimentroAlerta().getDistanciaPausada()) / hours
        );
    }

    private void calcularDistancias(Location location) {
        List<TrkPt> trkPts = getTrkPts();
        if (trkPts.isEmpty()) return;
        getObVelocimentroAlerta().addDistancia(
                calculaDistancia(
                        trkPts.get(trkPts.size() - 1).getLatitude(),
                        trkPts.get(trkPts.size() - 1).getLongitude(),
                        location.getLatitude(),
                        location.getLongitude()
                )
        );
        //** CASO A DISTANCIA ESTEJA PAUSADA, AO FAZER O CALCULO DA NOVA DISTANCIA, ADICIONA NA VARIAVEL QUE CONSTROLA
        //A DISTANCIA QUE FICOU PAUSADA, PARA NAO LEVAR EM CONSIDERAÇÃO NO CALCULO DE MEDIA.;
        if (tmpDistanciaPause > 0) {
            getObVelocimentroAlerta().addDistanciaPausada(
                    getObVelocimentroAlerta().getDistancia() - tmpDistanciaPause
            );
            tmpDistanciaPause = -1;
        }

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
        if (this.callbackGpsThread != null && statusService != STATUS_FINALIZADO) {
            notifyBase(atividadePausada, gpsPausa == CallbackGpsThread.GPS_RETOMADO && !(pauseAutomaticEneble && inPauseAutomatic));
            this.callbackGpsThread.setGpsSituacao(gpsSituacao);
            this.callbackGpsThread.setGpsPrecisao(gpsPrecisao);
            this.callbackGpsThread.setGpsPausa(gpsPausa);
            this.callbackGpsThread.updateValues(obVelocimentroAlerta);
            if (pauseAutomaticEneble) {
                this.callbackGpsThread.setPauseAutomatic(inPauseAutomatic);
            }
        }
    }

    private void vibrar() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (vibrator != null) {
                    vibrator.vibrate(300);
                }
            }
        }).start();
    }

    public ObVelocimentroAlerta getObVelocimentroAlerta() {
        return obVelocimentroAlerta;
    }
}
