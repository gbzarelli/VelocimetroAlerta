package br.com.helpdev.velocimetroalerta.gps;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import br.com.helpdev.velocimetroalerta.MainActivity;
import br.com.helpdev.velocimetroalerta.MySpeechSpeed;
import br.com.helpdev.velocimetroalerta.R;
import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx;
import br.com.helpdev.velocimetroalerta.gpx.objects.MetaData;
import br.com.helpdev.velocimetroalerta.gpx.objects.TrackPointExtension;
import br.com.helpdev.velocimetroalerta.gpx.objects.Trk;
import br.com.helpdev.velocimetroalerta.gpx.objects.TrkPt;

/**
 * Created by Guilherme Biff Zarelli on 04/04/16.
 */
public class ServiceVelocimetro extends Service implements Runnable, ServiceConnection {


    private static final int ID_NOTIFICATION_FOREGROUND = 10;
    private static final String LOG = "ServiceVelocimetro";

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

        void onErrorProcessingData(Throwable t);
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
    private SensorsService.SensorsBinder sensorsBinder;

    @Override
    public void onCreate() {
        super.onCreate();

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mySpeechSpeed.init(this);
        gps.init(this);
        myNotificationBuilder = new Notification.Builder(this);
        myNotificationBuilder
                .setSmallIcon(R.drawable.ic_notfiy)
                .setContentTitle(getString(R.string.app_name))
                .setOngoing(true);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        myNotificationBuilder.setContentIntent(pendingIntent);
        statusService = STATUS_FINALIZADO;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    public boolean isRunning() {
        return statusService != STATUS_FINALIZADO;
    }

    public boolean isPause() {
        return statusService == STATUS_PAUSADO;
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

    public void configure() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        this.pauseAutomaticEneble = sp.getBoolean(getString(R.string.pref_pause_automatico), true);
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

    @Override
    public void onDestroy() {
        stop();
        try {
            mySpeechSpeed.close();
        } catch (Throwable t) {
        }
        try {
            gps.close();
        } catch (Throwable t) {
        }
//        try {
//            stopSelf();
//        } catch (Throwable t) {
//        }
        super.onDestroy();
    }

    public void stop() {
        this.statusService = STATUS_FINALIZADO;
        try {
            unbindService(this);
        } catch (Throwable t) {
        }
        stopService(new Intent(this, SensorsService.class));
        try {
            stopForeground(true);
        } catch (Throwable t) {
        }
    }

    public void start(CallbackGpsThread callbackGpsThread) {
        this.callbackGpsThread = callbackGpsThread;
        Intent it = new Intent(this, SensorsService.class);
        startService(it);
        bindService(it, this, BIND_AUTO_CREATE);

        if (statusService == STATUS_FINALIZADO) {
            Thread myThread = new Thread(this);
            myThread.setDaemon(true);
            myThread.setName("TH-" + ServiceVelocimetro.class.getName());
            myThread.start();
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        if (service instanceof SensorsService.SensorsBinder) {
            sensorsBinder = (SensorsService.SensorsBinder) service;
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        sensorsBinder = null;
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
            try {
                process(gps.getViews(), gps.getLocation());
            } catch (Throwable t) {
                Log.e(LOG, "Erro no processamento", t);
                if (callbackGpsThread != null) callbackGpsThread.onErrorProcessingData(t);
            }
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
                notifyNotificationUpdate();
            }
            pause = true;
            if (!atividadePausada) {
                startPause(false);
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
        vibrate();
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
            obVelocimentroAlerta.addDuracaoPausado(SystemClock.elapsedRealtime() - tmpMillisPausa);
            baseTime = firstBase + obVelocimentroAlerta.getDuracaoPausado();
            tmpMillisPausa = SystemClock.elapsedRealtime();
        }
        if (callbackGpsThread != null) callbackGpsThread.setBaseChronometer(baseTime, resume);
    }

    private synchronized void startPause(boolean automatic) {
        vibrate();
        if (atividadePausada) return;
        atividadePausada = true;
        tmpDistanciaPause = obVelocimentroAlerta.getDistancia();
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
                obVelocimentroAlerta.setAltitudeAtual(location.getAltitude());
                obVelocimentroAlerta.setPrecisaoAtual(accuracy);
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

            TrackPointExtension trackPointExtension = null;
            if (sensorsBinder != null) {
                trackPointExtension = sensorsBinder.getTrackPointExtension();
                if (trackPointExtension.getCad() != null && !trackPointExtension.getCad().isEmpty()) {
                    obVelocimentroAlerta.setCadence(Integer.parseInt(trackPointExtension.getCad()));
                } else {
                    obVelocimentroAlerta.setCadence(-1);
                }
                if (trackPointExtension.getAtemp() != null && !trackPointExtension.getAtemp().isEmpty()) {
                    obVelocimentroAlerta.setTemperature(Integer.parseInt(trackPointExtension.getAtemp()));
                } else {
                    obVelocimentroAlerta.setTemperature(-1);
                }
                if (trackPointExtension.getRhu() != null && !trackPointExtension.getRhu().isEmpty()) {
                    obVelocimentroAlerta.setHumidity(Integer.parseInt(trackPointExtension.getRhu()));
                } else {
                    obVelocimentroAlerta.setHumidity(-1);
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
                    obVelocimentroAlerta.setDateInicio(new Date(location.getTime()));
                    MetaData metaData = new MetaData();
                    metaData.setTime(Gpx.getUtcGpxTime(location.getTime()));
                    gpx.setMetaData(metaData);

                    Trk trk = new Trk();
                    trk.setName("VEL_ALERTA_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(obVelocimentroAlerta.getDateInicio()));
                    gpx.setTrk(trk);
                }

                if (trackPointExtension == null) {
                    gpx.getTrk().getTrkseg().addTrkPt(location);
                } else {
                    gpx.getTrk().getTrkseg().addTrkPt(location, trackPointExtension);

                }

                if (velocidadeAtual > obVelocimentroAlerta.getvMaxima()) {
                    obVelocimentroAlerta.setvMaxima(velocidadeAtual);
                }
            } else {
                gpsPrecisao = CallbackGpsThread.GPS_SEM_PRECISAO;
                if (callbackGpsThread != null) callbackGpsThread.setGpsPrecisao(gpsPrecisao);
            }

            obVelocimentroAlerta.setDuracao(getTempoCronometro());
        } finally {
            obVelocimentroAlerta.setvAtual(velocidadeAtual);
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

    private volatile boolean calculandoAltitude = false;

    private void calculaGanhoAltitudeAsync(final boolean forcar) {
        if (calculandoAltitude) return;
        new Thread() {
            @Override
            public void run() {
                try {
                    calculandoAltitude = true;
                    calculaGanhoAltitude(forcar);
                } catch (Throwable t) {
                    Log.e(LOG, "Falaha ao calcular elevação", t);
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
        if (locationsHistory == null || locationsHistory.isEmpty() || (!forcar && obVelocimentroAlerta.getDistancia() <= (tmpDistanciaGanhoAlt + CONST_INTERVALO_DISTANCIA_CALCULO_GANHO))) {
            return;
        }

        double constMediaAccuracy = 0;
        int size = locationsHistory.size();
        for (int i = 0; i < size; i++) {
            constMediaAccuracy += locationsHistory.get(i).getAccuracy();
        }
        constMediaAccuracy = constMediaAccuracy / size;

        if (forcar) {
            indexCurso = 0;
            obVelocimentroAlerta.setGanhoAltitude(0);
            obVelocimentroAlerta.setPerdaAltitude(0);
        }
        tmpDistanciaGanhoAlt = obVelocimentroAlerta.getDistancia();

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
                    obVelocimentroAlerta.addPerdaAltitude(getGanhoPico(false, indexA, indexB, altA));
                }
                obVelocimentroAlerta.addGanhoAltitude(difAlt);
                climb = true;
            } else if (difAlt < 0 && ((difAlt * -1) >= constMediaAccuracy)) {//PERDENDO ALTITUDE
                if (climb) {//IF PARA ACHAR PICO POSITIVO, QUANDO SAI DE UMA SUBIDA E INICIA UMA DESCIDA
                    obVelocimentroAlerta.addGanhoAltitude(getGanhoPico(true, indexA, indexB, altA));
                }
                obVelocimentroAlerta.addPerdaAltitude((difAlt * -1));
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
        obVelocimentroAlerta.setvMedia(
                (obVelocimentroAlerta.getDistancia() - obVelocimentroAlerta.getDistanciaPausada()) / hours
        );
    }

    private void calcularDistancias(Location location) {
        List<TrkPt> trkPts = getTrkPts();
        if (trkPts.isEmpty()) return;
        double distance = GpsUtils.distanceCalculate(
                trkPts.get(trkPts.size() - 1).getLatitude(),
                trkPts.get(trkPts.size() - 1).getLongitude(),
                location.getLatitude(),
                location.getLongitude()
        );
        obVelocimentroAlerta.addDistancia(distance);
        //** CASO A DISTANCIA ESTEJA PAUSADA, AO FAZER O CALCULO DA NOVA DISTANCIA, ADICIONA NA VARIAVEL QUE CONSTROLA
        //A DISTANCIA QUE FICOU PAUSADA, PARA NAO LEVAR EM CONSIDERAÇÃO NO CALCULO DE MEDIA.;
        if (tmpDistanciaPause > 0) {
            obVelocimentroAlerta.addDistanciaPausada(
                    obVelocimentroAlerta.getDistancia() - tmpDistanciaPause
            );
            tmpDistanciaPause = -1;
        }

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

    private void vibrate() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (vibrator != null) {
                    vibrator.vibrate(300);
                }
            }
        }).start();
    }

}
