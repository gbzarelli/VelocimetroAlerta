package br.com.helpdev.velocimetroalerta;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import br.com.helpdev.velocimetroalerta.gps.ObVelocimentroAlerta;
import br.com.helpdev.velocimetroalerta.gps.ServiceVelocimetro;
import br.com.helpdev.velocimetroalerta.gpx.GpxFileUtils;

import static java.lang.String.format;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment implements View.OnClickListener, ServiceVelocimetro.CallbackGpsThread {

    private static final Integer KEY_VELOCIDADE_ATUAL = 1;
    private static final Integer KEY_VELOCIDADE_MEDIA = 2;
    private static final Integer KEY_VELOCIDADE_MAXIMA = 3;
    private static final String LOG = "MainFragment";

    private ImageButton btStartStop, btRefresh, btSave;
    private ProgressDialog progressDialog;
    private Chronometer chronometer;
    private TextView pausadoAutomaticamente, gpsDesatualizado, distancia, altitude, ganhoAltitude, perdaAltitude, precisao, tvAvisoPrecisao;

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
        btStartStop = getView().findViewById(R.id.bt_play_pause);
        btSave = getView().findViewById(R.id.bt_save);
        btRefresh = getView().findViewById(R.id.bt_refresh);
        btSave.setOnClickListener(this);
        btRefresh.setOnClickListener(this);
        chronometer = getView().findViewById(R.id.chronometer);
        pausadoAutomaticamente = getView().findViewById(R.id.tv_pausado_automaticamete);
        gpsDesatualizado = getView().findViewById(R.id.tv_gps_desatualizado);
        getView().findViewById(R.id.velocidade_1).setOnClickListener(this);
        getView().findViewById(R.id.layout_velocidade_1).setOnClickListener(this);
        getView().findViewById(R.id.texto_velocidade_1).setOnClickListener(this);
        getView().findViewById(R.id.velocidade_2).setOnClickListener(this);
        getView().findViewById(R.id.layout_velocidade_2).setOnClickListener(this);
        getView().findViewById(R.id.texto_velocidade_2).setOnClickListener(this);
        getView().findViewById(R.id.velocidade_3).setOnClickListener(this);
        getView().findViewById(R.id.layout_velocidade_3).setOnClickListener(this);
        getView().findViewById(R.id.texto_velocidade_3).setOnClickListener(this);
        distancia = getView().findViewById(R.id.distancia);
        altitude = getView().findViewById(R.id.altitude_atual);
        ganhoAltitude = getView().findViewById(R.id.ganho_altitude);
        perdaAltitude = getView().findViewById(R.id.ganho_neg_altitude);
        precisao = getView().findViewById(R.id.precisao_atual);
        tvAvisoPrecisao = getView().findViewById(R.id.tv_gps_impreciso);

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


        updateLayout(KEY_VELOCIDADE_ATUAL);
        clear();
    }

    private ServiceVelocimetro getProcessoGPS() {
        return getMyActivity().getServiceVelocimetro();
    }

    private void updateLayout(Integer keyMaster) {
        List<Integer> listaKeys = new ArrayList<>();
        listaKeys.add(KEY_VELOCIDADE_ATUAL);
        listaKeys.add(KEY_VELOCIDADE_MEDIA);
        listaKeys.add(KEY_VELOCIDADE_MAXIMA);
        listaKeys.remove(keyMaster);
        velocidades = new HashMap<>();
        velocidades.put(keyMaster, new TextView[]{getView().findViewById(R.id.velocidade_1), getView().findViewById(R.id.texto_velocidade_1)});
        velocidades.put(listaKeys.get(0), new TextView[]{getView().findViewById(R.id.velocidade_2), getView().findViewById(R.id.texto_velocidade_2)});
        velocidades.put(listaKeys.get(1), new TextView[]{getView().findViewById(R.id.velocidade_3), getView().findViewById(R.id.texto_velocidade_3)});
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
        } else if (v.getId() == R.id.velocidade_1 || v.getId() == R.id.layout_velocidade_1 || v.getId() == R.id.texto_velocidade_1) {
            List<Integer> lista = new ArrayList<>(velocidades.keySet());
            Integer key = lista.get(new Random().nextInt(2));
            updateLayout(key);
        } else {
            int id = v.getId();
            if (id == R.id.layout_velocidade_2 || id == R.id.texto_velocidade_2) {
                id = R.id.velocidade_2;
            } else if (id == R.id.layout_velocidade_3 || id == R.id.texto_velocidade_3) {
                id = R.id.velocidade_3;
            }
            for (Integer key : velocidades.keySet()) {
                if (velocidades.get(key)[0].getId() == id) {
                    updateLayout(key);
                    return;
                }
            }
        }
    }

    private void save() {
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getMyActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 12);
            return;
        }

        if (getProcessoGPS() == null || getProcessoGPS().getObVelocimentroAlerta().getDistancia() < 0.3f) {
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
                    final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    try {
                        GpxFileUtils gpxFileUtils = new GpxFileUtils();
                        final File file = gpxFileUtils.writeGpx(
                                getProcessoGPS().getGpx(),
                                new File(Environment.getExternalStorageDirectory(), "/velocimetro_alerta/"),
                                "VEL_ALERTA_" + new SimpleDateFormat("yyyyMMdd_HHmm").format(getProcessoGPS().getObVelocimentroAlerta().getDateInicio())
                        );
                        if (file == null) {
                            throw new Exception(getString(R.string.impossivel_gravar_disco));
                        } else {
                            mensagem = getString(R.string.arquivo_gravado_sucesso, file.getAbsolutePath());
                            builder.setNeutralButton(R.string.share, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    Intent intent = new Intent(Intent.ACTION_SEND);
                                    intent.setType("text/*");
                                    intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + file.getAbsolutePath()));
                                    startActivity(Intent.createChooser(intent, getString(R.string.title_share)));
                                }
                            });
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
        try {
            btStartStop.setEnabled(true);

            btSave.setVisibility(View.GONE);
            btRefresh.setVisibility(View.GONE);

            if (getProcessoGPS() != null) {
                getProcessoGPS().stop();
            }
            gpsDesatualizado.setVisibility(View.GONE);
            pausadoAutomaticamente.setVisibility(View.GONE);

            velocidades.get(KEY_VELOCIDADE_ATUAL)[0].setText("- -");
            velocidades.get(KEY_VELOCIDADE_MEDIA)[0].setText("- -");
            velocidades.get(KEY_VELOCIDADE_MAXIMA)[0].setText("- -");
            distancia.setText("- -");
            altitude.setText("- -");
            precisao.setText("- -");
            ganhoAltitude.setText("- -");
            perdaAltitude.setText("- -");

            chronometer.setBase(SystemClock.elapsedRealtime());
            chronometer.stop();
        } catch (Throwable t) {
            Log.e(LOG, "clear", t);
        }
    }

    private void playPause() {
        if (getProcessoGPS().isRunning()) {
            if (pausadoAutomaticamente.getVisibility() == View.VISIBLE) {
                pausadoAutomaticamente.setVisibility(View.GONE);
            }

            if (getProcessoGPS().isPause()) {
                getProcessoGPS().pausar(false);
                btRefresh.setVisibility(View.GONE);
            } else {
                getProcessoGPS().pausar(true);
                btRefresh.setVisibility(View.VISIBLE);
            }

            if (getProcessoGPS().isRunning()) {
                btStartStop.setEnabled(false);
            }
        } else {
            getProcessoGPS().start(this);
        }
        btSave.setVisibility(getProcessoGPS().isPause() ? View.VISIBLE : View.GONE);
        btStartStop.setImageResource(getProcessoGPS().isPause() ? R.drawable.play : R.drawable.pause);
    }


    private MainActivity getMyActivity() {
        return (MainActivity) super.getActivity();
    }


    private void updateValuesText() {
        if (getProcessoGPS() != null) {
            ObVelocimentroAlerta obVelocimentroAlerta = getProcessoGPS().getObVelocimentroAlerta();
            velocidades.get(KEY_VELOCIDADE_ATUAL)[0].setText(format("%.1f", obVelocimentroAlerta.getvAtual()));
            velocidades.get(KEY_VELOCIDADE_MAXIMA)[0].setText(format("%.1f", obVelocimentroAlerta.getvMaxima()));
            velocidades.get(KEY_VELOCIDADE_MEDIA)[0].setText(format("%.1f", obVelocimentroAlerta.getvMedia()));
            distancia.setText(format("%.1f", obVelocimentroAlerta.getDistancia()));
            altitude.setText(format("%.1f", obVelocimentroAlerta.getAltitudeAtual()));
            ganhoAltitude.setText(format("%.1f", obVelocimentroAlerta.getGanhoAltitude()));
            perdaAltitude.setText(format("%.1f", obVelocimentroAlerta.getPerdaAltitude()));
            precisao.setText(format("%.1f", obVelocimentroAlerta.getPrecisaoAtual()));
        }
    }

    @Override
    public void updateValues(final ObVelocimentroAlerta obVelocimentroAlerta) {
        if (getActivity() != null) getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateValuesText();
            }
        });
    }

    @Override
    public synchronized void setGpsSituacao(final int status) {
        if (getActivity() != null) getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (status == GPS_ATUALIZADO && gpsDesatualizado.getVisibility() == View.VISIBLE) {
                    gpsDesatualizado.setVisibility(View.GONE);
                } else if (status == GPS_DESATUALIZADO && gpsDesatualizado.getVisibility() == View.GONE) {
                    gpsDesatualizado.setVisibility(View.VISIBLE);
                }
                updateValuesText();
            }
        });
    }

    @Override
    public synchronized void setGpsPausa(final int status) {
        if (getActivity() != null) getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (status == GPS_PAUSADO) {
                    chronometer.stop();
                }
                if ((status == GPS_RETOMADO || status == GPS_PAUSADO) && !btStartStop.isEnabled()) {
                    btStartStop.setEnabled(true);
                }
                updateValuesText();
            }
        });
    }

    @Override
    public synchronized void setGpsPrecisao(final int status) {
        if (getActivity() != null) getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (status == GPS_SEM_PRECISAO && precisao.getTag() == null) {
                    precisao.setTextColor(Color.RED);
                    precisao.setTypeface(precisao.getTypeface(), Typeface.BOLD);
                    precisao.setTag(1);
                    tvAvisoPrecisao.setVisibility(View.VISIBLE);
                } else if (status == GPS_PRECISAO_OK && precisao.getTag() != null) {
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
    public void onAttach(Context context) {
        super.onAttach(context);
        getMyActivity().setCallbackNotify(new MainActivity.CallbackNotify() {
            @Override
            public void onServiceConnected(ServiceVelocimetro serviceVelocimetro) {
                serviceVelocimetro.setCallbackGpsThread(MainActivityFragment.this);
            }

            @Override
            public void onBeforeDisconnect(ServiceVelocimetro serviceVelocimetro) {
                serviceVelocimetro.setCallbackGpsThread(null);
            }

            @Override
            public void onCloseProgram() {
                clear();
            }
        });
    }

    @Override
    public synchronized void setPauseAutomatic(final boolean pause) {
        if (!getProcessoGPS().isPause()) {
            if (getActivity() != null) getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (pause) {
                        pausadoAutomaticamente.setVisibility(View.VISIBLE);
                        chronometer.stop();
                    } else {
                        pausadoAutomaticamente.setVisibility(View.GONE);
                    }
                    updateValuesText();
                }
            });
        }
    }

    @Override
    public void setBaseChronometer(final long base, final boolean resume) {
        if (getActivity() != null) getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                chronometer.setBase(base);
                if (resume) {
                    chronometer.start();
                }
            }
        });
    }

    @Override
    public void onErrorProcessingData(Throwable t) {

    }

}
