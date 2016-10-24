package br.com.helpdev.velocimetroalerta;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import br.com.helpdev.velocimetroalerta.dialogs.ConfirmDialogFrag;
import br.com.helpdev.velocimetroalerta.gps.Gps;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CONFIG = 1;

    public interface CallbackNotify {
        void onChangeConfig();

        boolean isRunning();
    }

    private Gps mGps;
    private CallbackNotify callbackNotify;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (savedInstanceState == null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 12);
            }
            getGps();
        }
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

    @Override
    public void onBackPressed() {
        if (callbackNotify != null) {
            if (callbackNotify.isRunning()) {
                if (getSupportFragmentManager().findFragmentByTag("DIALOG") == null) {
                    ConfirmDialogFrag.getInstance("Deseja realmente sair?", true, new DialogInterface.OnClickListener() {
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
        getGps().onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivityForResult(new Intent(this, ConfigActivity.class), REQUEST_CONFIG);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CONFIG) {
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
