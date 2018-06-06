package br.com.helpdev.velocimetroalerta.gps;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import br.com.helpdev.velocimetroalerta.R;
import br.com.helpdev.velocimetroalerta.bluetooth.Bluetooth;
import br.com.helpdev.velocimetroalerta.gpx.objects.TrackPointExtension;

public class SensorsService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener,
        Runnable {

    public static final String ACTION_NOTIFY_SENSOR = "br.com.helpdev.velocimetroalerta.ACTION_NOTIFY_SENSOR";
    public static final String PARAM_OBJECT_VELALERTBLMODULE = "PARAM_OBJECT_VELALERTBLMODULE";

    private static final int STOP = 1;
    private static final int CHANGE_MODULE = 2;
    private static final int RUNNING = 3;


    private int status = STOP;
    private BluetoothDevice bluetoothDevice;
    private String addressModuleBT;
    private TrackPointExtension lastValueModule;

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        pref.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(pref, getString(R.string.pref_module_vel_alert));
        addressModuleBT = pref.getString(getString(R.string.pref_module_vel_alert_address), null);
        lastValueModule = new TrackPointExtension();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    public class SensorsBinder extends Binder {
        private SensorsService sensorsService;

        SensorsBinder(SensorsService sensorsService) {
            this.sensorsService = sensorsService;
        }

        public TrackPointExtension getTrackPointExtension() {
            return sensorsService.lastValueModule;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new SensorsBinder(this);
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences pref, String key) {
        if (key.equals(getString(R.string.pref_module_vel_alert))) {
            if (pref.getBoolean(key, false)) {
                start();
            } else {
                stop();
            }
        }

        if (key.equals(getString(R.string.pref_module_vel_alert_address))) {
            addressModuleBT = pref.getString(key, null);
            changeModule();
        }
    }


    public void stop() {
        status = STOP;
    }

    public void start() {
        if (status == STOP) {
            new Thread(this, "Thr-VelAlertBTModule").start();
        }
    }

    public void changeModule() {
        status = CHANGE_MODULE;
    }


    @Override
    public void run() {
        status = RUNNING;
        while (STOP != status) {
            try {
                if (!Bluetooth.isAdapterEnabled()) {
                    Bluetooth.enableAdapter();
                } else {
                    if (CHANGE_MODULE == status || null == bluetoothDevice) {
                        defineBluetoothDevice();
                        status = RUNNING;
                    }

                    BluetoothSocket btSocket = bluetoothDevice
                            .createInsecureRfcommSocketToServiceRecord(
                                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                            );
                    btSocket.connect();

                    while (RUNNING == status) {
                        if (btSocket.getInputStream().available() > 0) {
                            byte[] data = new byte[128];
                            int rData = btSocket.getInputStream().read(data);
                            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                                Gson gson = new Gson();
                                baos.write(data, 0, rData);
                                String value = baos.toString();
                                lastValueModule = gson.fromJson(value, TrackPointExtension.class);
                                Intent it = new Intent(ACTION_NOTIFY_SENSOR);
                                it.putExtra(PARAM_OBJECT_VELALERTBLMODULE, lastValueModule);
                                LocalBroadcastManager.getInstance(this).sendBroadcast(it);
                            }
                        }
                        Thread.sleep(500);
                    }
                    btSocket.close();
                }
            } catch (Throwable t) {
                t.printStackTrace();
                lastValueModule = new TrackPointExtension();
            }
            if (RUNNING == status) {
                try {
                    Thread.sleep(5_000);
                } catch (Throwable t2) {
                }
            }
        }
    }

    private void defineBluetoothDevice() throws IOException {
        for (BluetoothDevice bd : Bluetooth.getBondedDevices()) {
            if (bd.getAddress().equals(addressModuleBT)) {
                bluetoothDevice = bd;
            }
        }
    }
}
