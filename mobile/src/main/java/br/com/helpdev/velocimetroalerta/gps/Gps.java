package br.com.helpdev.velocimetroalerta.gps;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;

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
    private Activity activity;
    private LocationManager locationManager;
    private boolean permissao = false;

    /**
     * Método chama loadGPS();
     * <p/>
     * Implementar na activity chamada no método onRequestPermissionsResult()
     *
     * @param context
     * @throws RuntimeException
     */
    public Gps(Activity context) throws RuntimeException {
        this.activity = context;
        views = -1;
        location = null;
        loadGPS();
    }

    public void loadGPS() {
        locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION);
            return;
        }
        permissao = true;
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadGPS();
            }
        }
    }

    public void close() {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.removeUpdates(this);
    }

    public boolean isGpsEnable() {
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

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public int getViews() {
        return views;
    }

    public Location getLocation() {
        views++;
        return location;
    }

}
