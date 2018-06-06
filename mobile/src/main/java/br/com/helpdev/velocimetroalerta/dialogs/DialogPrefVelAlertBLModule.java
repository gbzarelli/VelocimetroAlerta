package br.com.helpdev.velocimetroalerta;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import br.com.helpdev.velocimetroalerta.bluetooth.Bluetooth;

public class ListPreferenceBL extends DialogPreference {

    public ListPreferenceBL(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        return super.onCreateView(parent);
    }

    @Override
    public CharSequence getTitle() {
        return super.getTitle();
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        if (Bluetooth.isAdapterEnabled()) {
            builder.setPositiveButton(R.string.bt_ok, null);
            builder.setSingleChoiceItems(new String[]{"Disable", "1", "2", "3"}, 0, null);
        } else {
            builder.setMessage(R.string.disabled_bluetooth);
            builder.setPositiveButton(R.string.bt_enable, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Bluetooth.enableAdapter();
                }
            });
        }
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
    }
}
