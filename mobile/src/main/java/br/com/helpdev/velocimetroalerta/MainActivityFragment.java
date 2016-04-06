package br.com.helpdev.velocimetroalerta;

import android.content.Context;
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
    private TextView pausadoAutomaticamente, gpsDesatualizado, distancia;
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
            rodandoAtividade = true;
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

        btStartStop.setImageResource(contandoTempo ? R.drawable.pause : R.drawable.play);
    }


    private MainActivity getMyActivity() {
        return (MainActivity) super.getActivity();
    }


    private void updateValuesText() {
        if (processoGPS != null) {
            velocidades.get(KEY_VELOCIDADE_ATUAL)[0].setText(String.valueOf((int) processoGPS.getVelocidadeAtual()));
            velocidades.get(KEY_VELOCIDADE_MAXIMA)[0].setText(String.format("%.1f", processoGPS.getVelocidadeMaxima()));
            velocidades.get(KEY_VELOCIDADE_MEDIA)[0].setText(String.format("%.1f", processoGPS.getVelocidadeMedia()));
            distancia.setText(String.format("%.1f", processoGPS.getDistanciaTotal()));
        }
    }


    @Override
    public void updateLocation(Location location) {

    }

    @Override
    public void updateValues(final long tempo, final double vMedia, final double vAtual, final double vMaxima, final double distanciaTotal) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateValuesText();
                mySpeechSpeed.updateValues(tempo, vMedia, vAtual, vMaxima, distanciaTotal);
            }
        });
    }

    @Override
    public void setGpsStatus(final int status) {
        System.out.println("setGpsStatus: " + status);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (status == GPS_ATUALIZADO && gpsDesatualizado.getVisibility() == View.VISIBLE) {
                    gpsDesatualizado.setVisibility(View.GONE);
                } else if (status == GPS_DESATUALIZADO && gpsDesatualizado.getVisibility() == View.GONE) {
                    gpsDesatualizado.setVisibility(View.VISIBLE);
                }
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
        System.out.println("setPauseAutomatic: " + pause);
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
