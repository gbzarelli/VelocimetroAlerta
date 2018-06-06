package br.com.helpdev.velocimetroalerta;

import android.Manifest;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import br.com.helpdev.velocimetroalerta.dialogs.ConfirmDialogFrag;
import br.com.helpdev.velocimetroalerta.gps.ServiceVelocimetro;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, ServiceConnection {

    private static final int REQUEST_CONFIG_AUDIO = 2;
    private static final int REQUEST_MY_ACTIVIES = 29;
    private static final String SP_KEEP_ALIVE = "keep_alive";
    private static final String LOG = "MainActivity";

    public interface CallbackNotify {
        void onServiceConnected(ServiceVelocimetro serviceVelocimetro);

        void onBeforeDisconnect(ServiceVelocimetro serviceVelocimetro);

        void onCloseProgram();
    }

    private CallbackNotify callbackNotify;
    private ServiceVelocimetro serviceVelocimetro;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (savedInstanceState == null) {
            loadPermissions();
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    private void loadPermissions() {
        if (isPermissionsGranted()) {
            Intent service = new Intent(this, ServiceVelocimetro.class);
            startService(service);
            bindService(service, this, BIND_AUTO_CREATE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION}, 12);
        }
    }

    public boolean isPermissionsGranted() {
        return (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                &&
                (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        switch (id) {
            case R.id.nav_star:
                openAppInGooglePlay();
                break;
            case R.id.nav_atividades:
                startActivityForResult(new Intent(this, MyActivities.class), REQUEST_MY_ACTIVIES);
                break;
            case R.id.nav_config:
                startActivityForResult(new Intent(this, ConfigActivity.class), REQUEST_CONFIG_AUDIO);
                break;
            case R.id.nav_share:
                shareApp();
                break;
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return false;
    }

    private void openAppInGooglePlay() {
        final String appPackageName = getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
        } catch (android.content.ActivityNotFoundException e) { // if there is no Google Play on device
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        }
    }

    private void shareApp() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.app_name) + " https://play.google.com/store/apps/details?id=" + getPackageName());
        sendIntent.setType("text/plain");
        startActivity(sendIntent);
    }

    private SharedPreferences getSharePref() {
        return getSharedPreferences("sp_main_activity", MODE_PRIVATE);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
            return;
        }

        if (serviceVelocimetro != null && serviceVelocimetro.isRunning()) {
            if (getSupportFragmentManager().findFragmentByTag("DIALOG") == null) {
                ConfirmDialogFrag.getInstance(getString(R.string.confirme_sair), true, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            stopService();
                            MainActivity.this.finish();
                        }
                    }
                }).show(getSupportFragmentManager(), "DIALOG");
            }
            return;
        } else {
            stopService();
        }

        super.onBackPressed();
    }

    private void stopService() {
        if (callbackNotify != null) callbackNotify.onCloseProgram();
        stopService(new Intent(this, ServiceVelocimetro.class));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int i : grantResults) {
            if (i != PackageManager.PERMISSION_GRANTED) {
                loadPermissions();
                break;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        loadIconKeepAlive(menu.getItem(0));
        return true;
    }

    private void loadIconKeepAlive(MenuItem item) {
        if (getSharePref().getBoolean(SP_KEEP_ALIVE, false)) {
            item.setIcon(R.drawable.ic_settings_brightness_black_48dp);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            item.setIcon(R.drawable.ic_settings_brightness_black_48dp_off);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivityForResult(new Intent(this, ConfigActivity.class), REQUEST_CONFIG_AUDIO);
        } else if (id == R.id.action_keep_alive) {
            String msg;
            if (getSharePref().getBoolean(SP_KEEP_ALIVE, false)) {
                getSharePref().edit().putBoolean(SP_KEEP_ALIVE, false).apply();
                msg = getString(R.string.bloqueio_ativado);
            } else {
                getSharePref().edit().putBoolean(SP_KEEP_ALIVE, true).apply();
                msg = getString(R.string.bloqueio_desativado);
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            loadIconKeepAlive(item);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isPermissionsGranted()) {
            bindService(new Intent(this, ServiceVelocimetro.class), this, BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (serviceVelocimetro != null) {
            try {
                if (callbackNotify != null) callbackNotify.onBeforeDisconnect(serviceVelocimetro);
            } catch (Throwable t) {
                Log.e(LOG, "onBeeforeDisconnect");
            }
            try {
                unbindService(this);
            } catch (Throwable t) {
                Log.e(LOG, "unbindService");
            }
        }
    }

    public ServiceVelocimetro getServiceVelocimetro() {
        return serviceVelocimetro;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CONFIG_AUDIO) {
            configureGpsVelocimetro();
        }
    }

    public void configureGpsVelocimetro() {
        if (serviceVelocimetro != null) {
            serviceVelocimetro.configure();
        }
    }


    public void setCallbackNotify(CallbackNotify callbackNotify) {
        this.callbackNotify = callbackNotify;
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        if (iBinder instanceof ServiceVelocimetro.MyBinder) {
            serviceVelocimetro = ((ServiceVelocimetro.MyBinder) iBinder).getServiceVelocimetro();
            configureGpsVelocimetro();
            if (callbackNotify != null) callbackNotify.onServiceConnected(serviceVelocimetro);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        serviceVelocimetro = null;
    }
}
