package br.com.helpdev.velocimetroalerta;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

import br.com.helpdev.velocimetroalerta.gps.ObVelocimentroAlerta;

/**
 * Created by Guilherme Biff Zarelli on 05/04/16.
 */
public class MySpeechSpeed {
    private static final int INTERVALO_DISTANCIA = 1;
    private static final int INTERVALO_TEMPO = 2;

    private volatile boolean reproduzindo = false;
    private TextToSpeech speech;
    private int intervalo;
    private int valorIntervalo;

    private boolean repAtual;
    private boolean repMedia;
    private boolean repMax;
    private boolean repDistancia;
    private boolean repTempo;
    private boolean vibrar;
    private Context context;

    public MySpeechSpeed(Context context) {
        speech = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                speech.setLanguage(Locale.getDefault());
            }
        });
        recarregarConfiguracoes(context);
    }

    public void recarregarConfiguracoes(Context context) {
        this.context = context;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String inter = sp.getString(context.getString(R.string.pref_intervalo), "minutos");
        if (inter.equals("minutos")) {
            intervalo = INTERVALO_TEMPO;
        } else {
            intervalo = INTERVALO_DISTANCIA;
        }
        valorIntervalo = Integer.parseInt(sp.getString(context.getString(R.string.pref_intervalo_valor), "1"));
        repAtual = sp.getBoolean(context.getString(R.string.pref_informar_vatual), false);
        repMedia = sp.getBoolean(context.getString(R.string.pref_informar_vmedia), true);
        repMax = sp.getBoolean(context.getString(R.string.pref_informar_vmax), false);
        repDistancia = sp.getBoolean(context.getString(R.string.pref_informar_distancia), false);
        repTempo = sp.getBoolean(context.getString(R.string.pref_informar_tempo), false);
        vibrar = sp.getBoolean(context.getString(R.string.pref_vibrar), false);
    }

    private double tmpDistancia;
    private double distanciaPercorrida;

    private void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (Exception e) {
        }
    }

    public void updateValues(ObVelocimentroAlerta obVelocimentroAlerta) {
        this.distanciaPercorrida = obVelocimentroAlerta.getDistanciaTotal();
        if (reproduzindo) {
            return;
        }
        reproduzindo = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (intervalo == INTERVALO_TEMPO) {
                    sleep(valorIntervalo * 60 * 1000);
                } else if (intervalo == INTERVALO_DISTANCIA) {
                    int d = 0;
                    do {
                        sleep(1000);
                        d = (int) (distanciaPercorrida - tmpDistancia);
                    } while (d < valorIntervalo);
                    tmpDistancia = distanciaPercorrida;
                }
                reproduzindo = false;
            }
        }).start();

        if (vibrar) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                        vibrator.vibrate(3000);
                    } catch (Exception e) {
                    }
                }
            }).start();
        }

        if (distanciaPercorrida <= 0.1d) {
            return;
        }

        if (!speech.isSpeaking()) {
            if (repAtual) {
                reproduzir(context.getString(R.string.speek_velocidade_atual, (int) obVelocimentroAlerta.getvAtual()));
            }
            if (repMedia) {
                reproduzir(context.getString(R.string.speek_media, String.format("%.1f", obVelocimentroAlerta.getvMedia()).replaceAll(",", ".")));
            }
            if (repMax) {
                reproduzir(context.getString(R.string.speek_maxima, (int) obVelocimentroAlerta.getvMaxima()));
            }
            if (repDistancia) {
                reproduzir(context.getString(R.string.speek_distancia, String.format("%.1f", obVelocimentroAlerta.getDistanciaTotal()).replaceAll(",", ".")));
            }
            if (repTempo) {
                reproduzir(context.getString(R.string.speek_tempo, (obVelocimentroAlerta.getTempoAtividade() / 60_000)));
            }
        }
    }

    private void reproduzir(String text) {
        text = text.replaceAll(",", ".");
        speech.speak(text, TextToSpeech.QUEUE_ADD, null);
        speech.playSilence(300, TextToSpeech.QUEUE_ADD, null);
    }

    public void close() {
        speech.shutdown();
    }
}
