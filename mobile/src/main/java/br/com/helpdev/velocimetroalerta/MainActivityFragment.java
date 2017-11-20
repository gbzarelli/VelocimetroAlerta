package br.com.helpdev.velocimetroalerta;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import br.com.helpdev.velocimetroalerta.gps.GPSVelocimetro;
import br.com.helpdev.velocimetroalerta.gps.GpxUtils;
import br.com.helpdev.velocimetroalerta.gps.ObVelocimentroAlerta;

import static java.lang.String.format;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment implements View.OnClickListener, GPSVelocimetro.CallbackGpsThread, MainActivity.CallbackNotify {

    private static final Integer KEY_VELOCIDADE_ATUAL = 1;
    private static final Integer KEY_VELOCIDADE_MEDIA = 2;
    private static final Integer KEY_VELOCIDADE_MAXIMA = 3;

    private static GPSVelocimetro processoGPS;
    private static boolean rodandoAtividade = false;
    private static boolean contandoTempo = false;

    private boolean pauseAutomatic;
    private MySpeechSpeed mySpeechSpeed;
    private ImageButton btStartStop, btRefresh, btSave;
    private Chronometer chronometer;
    private TextView pausadoAutomaticamente, gpsDesatualizado, distancia, altitude, ganhoAltitude, precisao, tvPrecisao, tvAvisoPrecisao;

    private HashMap<Integer, TextView[]> velocidades;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        loadConfigs();
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
        btSave = (ImageButton) getView().findViewById(R.id.bt_save);
        btRefresh = (ImageButton) getView().findViewById(R.id.bt_refresh);
        btSave.setOnClickListener(this);
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
        tvPrecisao = (TextView) getView().findViewById(R.id.tv_precisao);
        tvAvisoPrecisao = (TextView) getView().findViewById(R.id.tv_gps_impreciso);

        View.OnClickListener onClickGanho = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
                dialog.setTitle(R.string.app_name);
                dialog.setMessage(R.string.info_ganho);
                dialog.setPositiveButton(R.string.bt_ok, null);
                dialog.create().show();
            }
        };
        getView().findViewById(R.id.layout_ganho_vl).setOnClickListener(onClickGanho);
        getView().findViewById(R.id.layout_ganho_tx).setOnClickListener(onClickGanho);

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
            processoGPS = null;
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
        if (v.getId() == R.id.bt_save) {
            save();
        } else if (v.getId() == R.id.bt_refresh) {
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

    private ProgressDialog progressDialog;

    private void save() {
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getMyActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 12);
            return;
        }

        if (processoGPS == null || processoGPS.getObVelocimentroAlerta().getDistanciaTotal() < 0.3f) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.app_name);
            builder.setMessage(R.string.atividades_sem_dados);
            builder.setCancelable(false);
            builder.setPositiveButton(R.string.bt_ok, null);
            builder.create().show();
        } else {
            progressDialog = new ProgressDialog(getContext());
            progressDialog.setMessage(getString(R.string.aguarde_salvando));
            progressDialog.setCancelable(false);
            progressDialog.show();

            new Thread() {
                @Override
                public void run() {
                    String mensagem;
                    try {
                        GpxUtils gpxUtils = new GpxUtils(processoGPS.getLocations());
                        String file = gpxUtils.gravarGpx("VEL_ALERTA_" + new SimpleDateFormat("yyyyMMdd_HHmm").format(processoGPS.getObVelocimentroAlerta().getDateInicio()));
                        if (file == null) {
                            throw new Exception(getString(R.string.impossivel_gravar_disco));
                        } else {
                            mensagem = getString(R.string.arquivo_gravado_sucesso, file);
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                        mensagem = getString(R.string.erro_gravar_gpx, t.getMessage());
                    }
                    final String finalMensagem = mensagem;
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (progressDialog != null) progressDialog.dismiss();
                            } catch (Throwable t) {
                                t.printStackTrace();
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                            builder.setTitle(R.string.app_name);
                            builder.setMessage(finalMensagem);
                            builder.setCancelable(false);
                            builder.setPositiveButton(R.string.bt_ok, null);
                            builder.create().show();
                        }
                    });
                }
            }.start();
        }
    }

    private void clear() {
        btStartStop.setEnabled(true);
        rodandoAtividade = false;

        btSave.setVisibility(View.GONE);
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
        altitude.setText("- -");
        ganhoAltitude.setText("- -");

        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.stop();
    }

    private void vibrar() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Vibrator vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) {
                    vibrator.vibrate(300);
                }
            }
        }).start();
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

        vibrar();
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

        btSave.setVisibility(contandoTempo ? View.GONE : View.VISIBLE);
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
            precisao.setText(format("%.1f", obVelocimentroAlerta.getPrecisao()) + "m");
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
    public synchronized void setGpsStatus(final int status) {
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
                    tvPrecisao.setTypeface(precisao.getTypeface(), Typeface.BOLD);
                    tvPrecisao.setTextColor(Color.RED);
                    precisao.setTextColor(Color.RED);
                    precisao.setTypeface(precisao.getTypeface(), Typeface.BOLD);
                    precisao.setTag(1);
                    tvAvisoPrecisao.setVisibility(View.VISIBLE);
                } else if (status == GPS_PRECISAO_OK && precisao.getTag() != null) {
                    tvPrecisao.setTypeface(precisao.getTypeface(), Typeface.NORMAL);
                    tvPrecisao.setTextColor(Color.BLACK);
                    precisao.setTextColor(Color.BLACK);
                    precisao.setTypeface(precisao.getTypeface(), Typeface.NORMAL);
                    tvAvisoPrecisao.setVisibility(View.GONE);
                    precisao.setTag(null);

                }
                updateValuesText();
            }
        });
    }

    @Override
    public void debug(final String s) {
        getActivity().runOnUiThread(new Thread() {
            @Override
            public void run() {
                Toast.makeText(getContext(), s, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        getMyActivity().setCallbackNotify(this);
    }

    @Override
    public synchronized void setPauseAutomatic(final boolean pause) {
        vibrar();
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
    public boolean isPauseAutomaticEneble() {
        return pauseAutomatic;
    }

    @Override
    public void onChangeConfig() {
        loadConfigs();
        mySpeechSpeed.recarregarConfiguracoes(getActivity());
    }

    private void loadConfigs() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        pauseAutomatic = sp.getBoolean(getString(R.string.pref_pause_automatico), true);
    }

    @Override
    public boolean isRunning() {
        return processoGPS != null;
    }
}
