package br.com.helpdev.velocimetroalerta.gps;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

/**
 * Permissions <uses-permission
 * android:name="android.permission.ACCESS_FINE_LOCATION"/>
 *
 * @author Guilherme Biff Zarelli
 */
public class Gps implements LocationListener {

    private static final int REQUEST_PERMISSION = 141;

    private int views;
    private Location location;
    private Context context;
    private LocationManager locationManager;
    private boolean permissao = false;

    /**
     * Método chama init();
     * <p/>
     * Implementar na context chamada no método onRequestPermissionsResult()
     *
     * @param context
     * @throws RuntimeException
     */
    public Gps(Context context) throws RuntimeException {
        this();
        init(context);
    }

    public Gps() {
        views = -1;
        location = null;
    }

    @SuppressLint("MissingPermission")
    public void init(Context context) {
        this.context = context;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        permissao = true;
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }

    public void close() {
        if (locationManager == null) {
            throw new RuntimeException("No init Gps");
        }
        locationManager.removeUpdates(this);
    }

    public boolean isGpsEnable() {
        if (locationManager == null) {
            throw new RuntimeException("No init Gps");
        }
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    public boolean isPermission() {
        return permissao;
    }

    @Override
    public void onLocationChanged(Location location) {
        this.location = location;
        views = 0;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    public void setContext(Activity context) {
        this.context = context;
    }

    public int getViews() {
        return views;
    }

    public Location getLocation() {
        if (locationManager == null) {
            throw new RuntimeException("No init Gps");
        }
        views++;
        return location;
    }

}
