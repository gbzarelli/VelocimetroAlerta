package br.com.helpdev.velocimetroalerta.dialogs;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import br.com.helpdev.velocimetroalerta.R;
import br.com.helpdev.velocimetroalerta.bluetooth.Bluetooth;

public class DialogPrefVelAlertBLModule extends DialogPreference {

    public DialogPrefVelAlertBLModule(Context context, AttributeSet attrs) {
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
            View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_velalert_bluetooth, null);
            builder.setView(view);
            builder.setNegativeButton(R.string.bt_disable_module, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    getEditor().putBoolean(getKey(), false).apply();
                }
            });

            try {
                ListView listView = view.findViewById(R.id.listView);
                final ArrayList<BluetoothDevice> bondedDevices = Bluetooth.getBondedDevices();
                List<String> names = new ArrayList<>();
                for (BluetoothDevice bd : bondedDevices) {
                    names.add(bd.getName());
                }
                listView.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, names));
                listView.setEmptyView(view.findViewById(R.id.emptyView));
                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        getEditor().putBoolean(getKey(), true).apply();
                        getEditor().putString(
                                getContext().getString(R.string.pref_module_vel_alert_name),
                                bondedDevices.get(position).getName()).apply();
                        getEditor().putString(
                                getContext().getString(R.string.pref_module_vel_alert_address),
                                bondedDevices.get(position).getAddress()).apply();
                        getDialog().dismiss();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }

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
    public void onDismiss(DialogInterface dialog) {
        if (null != getOnPreferenceChangeListener()) {
            getOnPreferenceChangeListener().onPreferenceChange(this, getSummary());
        }
        super.onDismiss(dialog);
    }

    @Override
    public CharSequence getSummary() {
        boolean status = getSharedPreferences().getBoolean(getKey(), false);
        String valueToSummary = getContext().getString(R.string.disable);
        if (status) {
            valueToSummary = getSharedPreferences().getString(getContext().getString(R.string.pref_module_vel_alert_name), getContext().getString(R.string.disable));
        }
        return valueToSummary;
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
    }
}
