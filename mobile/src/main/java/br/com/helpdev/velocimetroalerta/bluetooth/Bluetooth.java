package br.com.grupocriar.swapandroid.bluetooth;

/**
 * Created by Guilherme Biff Zarelli on 25/07/15.
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.RequiresPermission;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;

/**
 *
 * @author Guilherme Biff Zarelli
 */
public class Bluetooth {

    private Bluetooth() {
        throw new RuntimeException("No Bluetooth!");
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH)
    public static ArrayList<BluetoothDevice> getBondedDevices() throws IOException {
        Set<BluetoothDevice> pairedDevices = getBluetoothAdapter().getBondedDevices();
        return new ArrayList<>(pairedDevices);
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH)
    public static boolean isAdapterEnabled() {
        return getBluetoothAdapter().isEnabled();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public static boolean enableAdapter() {
        return getBluetoothAdapter().enable();
    }

    public static BluetoothAdapter getBluetoothAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public static void desabilitarAdapter() {
        getBluetoothAdapter().disable();
    }

    public static void unpairDevice(BluetoothDevice device) throws Throwable {
        Method m = device.getClass().getMethod("removeBond", (Class[]) null);
        m.invoke(device, (Object[]) null);
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public static boolean pairDevice(Context context, final String mac, final String passwd) {
        return startDiscovery(context, new BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    BluetoothDevice deviceFound = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (deviceFound.getAddress().equals(mac.toUpperCase())) {
                        pairDevice(context, deviceFound, passwd);
                        context.unregisterReceiver(this);
                    }
                }
            }
        });
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public static boolean pairDevice(Context context, final BluetoothDevice device, final String passwd) {
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(intent.getAction())) {
                    BluetoothDevice deviceFound = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (deviceFound.getAddress().equals(device.getAddress())) {
                        deviceFound.setPin(passwd.getBytes());
                        context.unregisterReceiver(this);
                    }
                }
            }
        }, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
        return device.createBond();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public static boolean cancelDiscovery(Context context, BroadcastReceiver broadcastReceiver) {
        try {
            context.unregisterReceiver(broadcastReceiver);
        } catch (Throwable t) {
        }
        return getBluetoothAdapter().cancelDiscovery();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public static boolean startDiscovery(Context context, BroadcastReceiver broadcastReceiver) {
        context.registerReceiver(broadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        if (!getBluetoothAdapter().startDiscovery()) {
            context.unregisterReceiver(broadcastReceiver);
            return false;
        }
        return true;
    }

}
