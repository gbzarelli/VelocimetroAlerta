package br.com.helpdev.velocimetroalerta;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import br.com.helpdev.velocimetroalerta.dialogs.ConfirmDialogFrag;
import br.com.helpdev.velocimetroalerta.gps.Gps;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final int REQUEST_CONFIG_AUDIO = 2;

    public interface CallbackNotify {
        void onChangeConfig();

        boolean isRunning();
    }

    private Gps mGps;
    private CallbackNotify callbackNotify;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (savedInstanceState == null) {
            load();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    private void load() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION}, 12);
        } else {
            getGps();
        }
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

                break;
            case R.id.nav_config:
                startActivityForResult(new Intent(this, ConfigAudioActivity.class), REQUEST_CONFIG_AUDIO);
                break;
            case R.id.nav_share:
                shareApp();
                break;
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
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

    public void setCallbackNotify(CallbackNotify callbackNotify) {
        this.callbackNotify = callbackNotify;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGps != null) {
            mGps.setActivity(this);
        }
    }

    private SharedPreferences getSharePref() {
        return getSharedPreferences("sp_main_activity", MODE_PRIVATE);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
            return;
        }

        if (callbackNotify != null) {
            if (callbackNotify.isRunning()) {
                if (getSupportFragmentManager().findFragmentByTag("DIALOG") == null) {
                    ConfirmDialogFrag.getInstance(getString(R.string.confirme_sair), true, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == DialogInterface.BUTTON_POSITIVE) {
                                MainActivity.this.finish();
                            }
                        }
                    }).show(getSupportFragmentManager(), "DIALOG");
                }
                return;
            }
        }
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int i : grantResults) {
            if (i != PackageManager.PERMISSION_GRANTED) {
                load();
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
        if (getSharePref().getBoolean("keep_alive", false)) {
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
            startActivityForResult(new Intent(this, ConfigAudioActivity.class), REQUEST_CONFIG_AUDIO);
        } else if (id == R.id.action_keep_alive) {
            String msg;
            if (getSharePref().getBoolean("keep_alive", false)) {
                getSharePref().edit().putBoolean("keep_alive", false).apply();
                msg = getString(R.string.bloqueio_ativado);
            } else {
                getSharePref().edit().putBoolean("keep_alive", true).apply();
                msg = getString(R.string.bloqueio_desativado);
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            loadIconKeepAlive(item);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CONFIG_AUDIO) {
            if (callbackNotify != null) {
                callbackNotify.onChangeConfig();
            }
        }
    }

    public Gps getGps() {
        if (mGps == null) {
            mGps = new Gps(this);
        }
        return mGps;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mGps != null) {
            mGps.close();
        }
    }
}