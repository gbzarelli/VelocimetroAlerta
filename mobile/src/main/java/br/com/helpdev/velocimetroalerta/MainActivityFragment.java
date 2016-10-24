package br.com.helpdev.velocimetroalerta;

import android.content.Context;
import android.graphics.Color;
import android.location.Location;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import br.com.helpdev.velocimetroalerta.gps.GPSVelocimetro;
import br.com.helpdev.velocimetroalerta.gps.ObVelocimentroAlerta;

import static java.lang.String.format;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment implements View.OnClickListener, GPSVelocimetro.CallbackGpsThread, MainActivity.CallbackNotify {

    private static final Integer KEY_VELOCIDADE_ATUAL = 1;
    private static final Integer KEY_VELOCIDADE_MEDIA = 2;
    private static final Integer KEY_VELOCIDADE_MAXIMA = 3;

    private volatile static boolean rodandoAtividade = false;
    private volatile static boolean contandoTempo = false;

    private MySpeechSpeed mySpeechSpeed;
    private ImageButton btStartStop, btRefresh;
    private Chronometer chronometer;
    private TextView pausadoAutomaticamente, gpsDesatualizado, distancia, altitude, ganhoAltitude, precisao;
    private GPSVelocimetro processoGPS;

    private HashMap<Integer, TextView[]> velocidades;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getView().findViewById(R.id.bt_play_pause).setOnClickListener(this);
        btStartStop = (ImageButton) getView().findViewById(R.id.bt_play_pause);
        btRefresh = (ImageButton) getView().findViewById(R.id.bt_refresh);
        btRefresh.setOnClickListener(this);
        chronometer = (Chronometer) getView().findViewById(R.id.chronometer);
        pausadoAutomaticamente = (TextView) getView().findViewById(R.id.tv_pausado_automaticamete);
        gpsDesatualizado = (TextView) getView().findViewById(R.id.tv_gps_desatualizado);
        getView().findViewById(R.id.velocidade_1).setOnClickListener(this);
        getView().findViewById(R.id.velocidade_2).setOnClickListener(this);
        getView().findViewById(R.id.velocidade_3).setOnClickListener(this);
        distancia = (TextView) getView().findViewById(R.id.distancia);
        altitude = (TextView) getView().findViewById(R.id.altitude);
        ganhoAltitude = (TextView) getView().findViewById(R.id.ganho_altitude);
        precisao = (TextView) getView().findViewById(R.id.precisao);

        mySpeechSpeed = new MySpeechSpeed(getActivity());
        updateLayout(KEY_VELOCIDADE_ATUAL);
        clear();
    }

    @Override
    public void onDestroy() {
        if (mySpeechSpeed != null) {
            mySpeechSpeed.close();
        }
        if (processoGPS != null) {
            processoGPS.finalizar();
        }
        super.onDestroy();
    }

    private void updateLayout(Integer keyMaster) {
        List<Integer> listaKeys = new ArrayList<>();
        listaKeys.add(KEY_VELOCIDADE_ATUAL);
        listaKeys.add(KEY_VELOCIDADE_MEDIA);
        listaKeys.add(KEY_VELOCIDADE_MAXIMA);
        listaKeys.remove(keyMaster);
        velocidades = new HashMap<>();
        velocidades.put(keyMaster, new TextView[]{(TextView) getView().findViewById(R.id.velocidade_1), (TextView) getView().findViewById(R.id.texto_velocidade_1)});
        velocidades.put(listaKeys.get(0), new TextView[]{(TextView) getView().findViewById(R.id.velocidade_2), (TextView) getView().findViewById(R.id.texto_velocidade_2)});
        velocidades.put(listaKeys.get(1), new TextView[]{(TextView) getView().findViewById(R.id.velocidade_3), (TextView) getView().findViewById(R.id.texto_velocidade_3)});
        for (int key : velocidades.keySet()) {
            int resIdTitle = 0;
            if (key == KEY_VELOCIDADE_ATUAL) {
                resIdTitle = R.string.velocidade_atual;
            } else if (key == KEY_VELOCIDADE_MEDIA) {
                resIdTitle = R.string.velocidade_media;
            } else if (key == KEY_VELOCIDADE_MAXIMA) {
                resIdTitle = R.string.velocidade_maxima;
            }
            velocidades.get(key)[1].setText(resIdTitle);
        }
        updateValuesText();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.bt_refresh) {
            clear();
        } else if (v.getId() == R.id.bt_play_pause) {
            playPause();
        } else if (v.getId() == R.id.velocidade_1) {
//            List<Integer> lista = new ArrayList(velocidades.keySet());
//            Integer key = lista.get(new Random().nextInt(2));
//            updateLayout(key);
        } else {
            for (Integer key : velocidades.keySet()) {
                if (velocidades.get(key)[0].getId() == v.getId()) {
                    updateLayout(key);
                    return;
                }
            }
        }
    }

    private void clear() {
        btStartStop.setEnabled(true);
        rodandoAtividade = false;
        btRefresh.setVisibility(View.GONE);
        if (processoGPS != null) {
            processoGPS.finalizar();
            processoGPS = null;
        }
        gpsDesatualizado.setVisibility(View.GONE);
        pausadoAutomaticamente.setVisibility(View.GONE);

        velocidades.get(KEY_VELOCIDADE_ATUAL)[0].setText("- -");
        velocidades.get(KEY_VELOCIDADE_MEDIA)[0].setText("- -");
        velocidades.get(KEY_VELOCIDADE_MAXIMA)[0].setText("- -");
        distancia.setText("- -");

        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.stop();
    }

    private void playPause() {
        if (!rodandoAtividade) {
            processoGPS = new GPSVelocimetro(getMyActivity().getGps(), this);
            processoGPS.start();
        }

        if (pausadoAutomaticamente.getVisibility() == View.VISIBLE) {
            pausadoAutomaticamente.setVisibility(View.GONE);
        }
        if (gpsDesatualizado.getVisibility() == View.VISIBLE) {
            gpsDesatualizado.setVisibility(View.GONE);
        }

        if (contandoTempo) {
            contandoTempo = false;
            processoGPS.pausar(true);
            chronometer.stop();
            btRefresh.setVisibility(View.VISIBLE);
        } else {
            contandoTempo = true;
            processoGPS.pausar(false);
            chronometer.start();
            btRefresh.setVisibility(View.GONE);
        }

        if (rodandoAtividade) {
            btStartStop.setEnabled(false);
        } else {
            rodandoAtividade = true;
        }

        btStartStop.setImageResource(contandoTempo ? R.drawable.pause : R.drawable.play);
    }


    private MainActivity getMyActivity() {
        return (MainActivity) super.getActivity();
    }


    private void updateValuesText() {
        if (processoGPS != null) {
            ObVelocimentroAlerta obVelocimentroAlerta = processoGPS.getObVelocimentroAlerta();
            velocidades.get(KEY_VELOCIDADE_ATUAL)[0].setText(String.valueOf((int) obVelocimentroAlerta.getvAtual()));
            velocidades.get(KEY_VELOCIDADE_MAXIMA)[0].setText(format("%.1f", obVelocimentroAlerta.getvMaxima()));
            velocidades.get(KEY_VELOCIDADE_MEDIA)[0].setText(format("%.1f", obVelocimentroAlerta.getvMedia()));
            distancia.setText(format("%.1f", obVelocimentroAlerta.getDistanciaTotal()));
            altitude.setText(format("%.1f", obVelocimentroAlerta.getAltitude()));
            ganhoAltitude.setText(format("%.1f", obVelocimentroAlerta.getGanhoAltitude()));
            precisao.setText(format("%.1f", obVelocimentroAlerta.getPrecisao()));
        }
    }


    @Override
    public void updateLocation(Location location) {

    }

    @Override
    public void updateValues(final ObVelocimentroAlerta obVelocimentroAlerta) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateValuesText();
                mySpeechSpeed.updateValues(obVelocimentroAlerta);
            }
        });
    }

    @Override
    public void setGpsStatus(final int status) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if ((status == GPS_RETOMADO || status == GPS_PAUSADO) && !btStartStop.isEnabled()) {
                    btStartStop.setEnabled(true);
                }
                if (status == GPS_ATUALIZADO && gpsDesatualizado.getVisibility() == View.VISIBLE) {
                    gpsDesatualizado.setVisibility(View.GONE);
                } else if (status == GPS_DESATUALIZADO && gpsDesatualizado.getVisibility() == View.GONE) {
                    gpsDesatualizado.setVisibility(View.VISIBLE);
                }
                if (status == GPS_SEM_PRECISAO && precisao.getTag() == null) {
                    precisao.setTextColor(Color.RED);
                    precisao.setTag(1);
                } else if (status == GPS_PRECISAO_OK && precisao.getTag() != null) {
                    precisao.setTextColor(Color.BLACK);
                    precisao.setTag(null);

                }
                updateValuesText();
            }
        });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        getMyActivity().setCallbackNotify(this);
    }

    @Override
    public void setPauseAutomatic(final boolean pause) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (pause) {
                    pausadoAutomaticamente.setVisibility(View.VISIBLE);
                    chronometer.stop();
                } else {
                    pausadoAutomaticamente.setVisibility(View.GONE);
                    chronometer.start();
                }
                updateValuesText();
            }
        });
    }

    @Override
    public void setBase(final long base) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                chronometer.setBase(base);
            }
        });
    }

    @Override
    public boolean isPauseAutomatic() {
        return true;
    }

    @Override
    public void onChangeConfig() {
        mySpeechSpeed.recarregarConfiguracoes(getActivity());
    }

    @Override
    public boolean isRunning() {
        return processoGPS != null;
    }
}
