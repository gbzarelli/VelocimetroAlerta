package br.com.helpdev.velocimetroalerta;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;

import br.com.helpdev.velocimetroalerta.gps.ObVelocimentroAlerta;
import br.com.helpdev.velocimetroalerta.gps.SensorsService;
import br.com.helpdev.velocimetroalerta.gps.ServiceVelocimetro;
import br.com.helpdev.velocimetroalerta.gpx.GpxFileUtils;

import static java.lang.String.format;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment
        implements View.OnClickListener, ServiceVelocimetro.CallbackGpsThread,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String LOG = "MainFragment";

    private ImageButton btStartStop, btRefresh, btSave;
    private ProgressDialog progressDialog;
    private Chronometer chronometer;
    private ObVelocimentroAlerta obVelocimentroAlerta;
    private TextView pausadoAutomaticamente,
            gpsDesatualizado,
            distancia,
            altitude,
            ganhoAltitude,
            perdaAltitude,
            precision,
            tvPrecisionInfo,
            velocidadeAtual,
            velocidadeMedia,
            velocidadeMaxima,
            temperature,
            humidity,
            cadence;

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
        if (getView() == null) return;
        getView().findViewById(R.id.bt_play_pause).setOnClickListener(this);
        btStartStop = getView().findViewById(R.id.bt_play_pause);
        btSave = getView().findViewById(R.id.bt_save);
        btRefresh = getView().findViewById(R.id.bt_refresh);
        btSave.setOnClickListener(this);
        btRefresh.setOnClickListener(this);
        chronometer = getView().findViewById(R.id.chronometer);
        pausadoAutomaticamente = getView().findViewById(R.id.tv_pausado_automaticamete);
        gpsDesatualizado = getView().findViewById(R.id.tv_gps_desatualizado);
        velocidadeAtual = getView().findViewById(R.id.velocidade_atual);
        velocidadeMedia = getView().findViewById(R.id.velocidade_media);
        velocidadeMaxima = getView().findViewById(R.id.velocidade_maxima);
        distancia = getView().findViewById(R.id.distancia);
        altitude = getView().findViewById(R.id.altitude_atual);
        ganhoAltitude = getView().findViewById(R.id.ganho_altitude);
        perdaAltitude = getView().findViewById(R.id.ganho_neg_altitude);
        precision = getView().findViewById(R.id.precisao_atual);
        tvPrecisionInfo = getView().findViewById(R.id.tv_gps_impreciso);

        temperature = getView().findViewById(R.id.temperature);
        humidity = getView().findViewById(R.id.humidity);
        cadence = getView().findViewById(R.id.cadence);

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

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sp.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(sp, getString(R.string.pref_module_vel_alert));

        clear();
    }

    private ServiceVelocimetro getProcessoGPS() {
        return getMyActivity() == null ? null : getMyActivity().getServiceVelocimetro();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.bt_save) {
            save();
        } else if (v.getId() == R.id.bt_refresh) {
            clear();
        } else if (v.getId() == R.id.bt_play_pause) {
            playPause();
        }
    }

    private void save() {
        if (getActivity() == null) return;

        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getMyActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 12);
            return;
        }

        if (getProcessoGPS() == null || obVelocimentroAlerta == null || obVelocimentroAlerta.getDistancia() < 0.3f) {
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
                                "VEL_ALERTA_" +
                                        new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                                                .format(obVelocimentroAlerta.getDateInicio())
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
            gpsDesatualizado.setVisibility(View.INVISIBLE);
            pausadoAutomaticamente.setVisibility(View.INVISIBLE);

            velocidadeAtual.setText(R.string.text_null_value);
            velocidadeMedia.setText(R.string.text_null_value);
            velocidadeMaxima.setText(R.string.text_null_value);

            distancia.setText(R.string.text_null_value);
            altitude.setText(R.string.text_null_value);
            precision.setText(R.string.text_null_value);
            ganhoAltitude.setText(R.string.text_null_value);
            perdaAltitude.setText(R.string.text_null_value);

            cadence.setText(R.string.text_null_value);
            temperature.setText(R.string.text_null_value);
            humidity.setText(R.string.text_null_value);

            chronometer.setBase(SystemClock.elapsedRealtime());
            chronometer.stop();
        } catch (Throwable t) {
            Log.e(LOG, "clear", t);
        }
    }

    private void playPause() {
        if (getProcessoGPS() == null) return;

        if (getProcessoGPS().isRunning()) {
            if (pausadoAutomaticamente.getVisibility() == View.VISIBLE) {
                pausadoAutomaticamente.setVisibility(View.INVISIBLE);
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
            if (obVelocimentroAlerta != null) {
                try {
                    velocidadeAtual.setText(format(Locale.getDefault(), "%.1f", obVelocimentroAlerta.getvAtual()));
                    velocidadeMaxima.setText(format(Locale.getDefault(), "%.1f", obVelocimentroAlerta.getvMaxima()));
                    velocidadeMedia.setText(format(Locale.getDefault(), "%.1f", obVelocimentroAlerta.getvMedia()));

                    distancia.setText(format(Locale.getDefault(), "%.1f", obVelocimentroAlerta.getDistancia()));
                    altitude.setText(format(Locale.getDefault(), "%.1f", obVelocimentroAlerta.getAltitudeAtual()));
                    ganhoAltitude.setText(format(Locale.getDefault(), "%.1f", obVelocimentroAlerta.getGanhoAltitude()));
                    perdaAltitude.setText(format(Locale.getDefault(), "%.1f", obVelocimentroAlerta.getPerdaAltitude()));
                    precision.setText(format(Locale.getDefault(), "%.1f", obVelocimentroAlerta.getPrecisaoAtual()));

                    if (obVelocimentroAlerta.getCadence() < 0) {
                        cadence.setText(R.string.text_null_value);
                    } else {
                        cadence.setText(String.valueOf(obVelocimentroAlerta.getCadence()));
                    }
                    if (obVelocimentroAlerta.getTemperature() < 0) {
                        temperature.setText(R.string.text_null_value);
                    } else {
                        temperature.setText(String.valueOf(obVelocimentroAlerta.getTemperature()));
                    }
                    if (obVelocimentroAlerta.getHumidity() < 0) {
                        humidity.setText(R.string.text_null_value);
                    } else {
                        humidity.setText(String.valueOf(obVelocimentroAlerta.getHumidity()));
                    }

                } catch (Throwable t) {
                    Log.e(LOG, "updateValuesText", t);
                }
            }
        }
    }

    @Override
    public void updateValues(final ObVelocimentroAlerta obVelocimentroAlerta) {
        this.obVelocimentroAlerta = obVelocimentroAlerta;
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
                    gpsDesatualizado.setVisibility(View.INVISIBLE);
                } else if (status == GPS_DESATUALIZADO && gpsDesatualizado.getVisibility() == View.INVISIBLE) {
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
                if (status == GPS_SEM_PRECISAO && precision.getTag() == null) {
                    precision.setTextColor(Color.RED);
                    precision.setTypeface(precision.getTypeface(), Typeface.BOLD);
                    precision.setTag(1);
                    tvPrecisionInfo.setVisibility(View.VISIBLE);
                } else if (status == GPS_PRECISAO_OK && precision.getTag() != null) {
                    precision.setTextColor(Color.BLACK);
                    precision.setTypeface(precision.getTypeface(), Typeface.NORMAL);
                    tvPrecisionInfo.setVisibility(View.GONE);
                    precision.setTag(null);

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
                        pausadoAutomaticamente.setVisibility(View.INVISIBLE);
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

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (getView() == null || !key.equals(getString(R.string.pref_module_vel_alert))) return;

        if (sharedPreferences.getBoolean(key, false)) {
            getView().findViewById(R.id.layout_sensors).setVisibility(View.VISIBLE);
        } else {
            getView().findViewById(R.id.layout_sensors).setVisibility(View.GONE);
        }
    }
}
