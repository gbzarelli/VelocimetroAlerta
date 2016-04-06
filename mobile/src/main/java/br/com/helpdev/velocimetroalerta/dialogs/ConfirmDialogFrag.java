package br.com.helpdev.velocimetroalerta.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import br.com.helpdev.velocimetroalerta.R;

/**
 * Created by Guilherme Biff Zarelli on 06/04/16.
 */
public class ConfirmDialogFrag extends android.support.v4.app.DialogFragment {

    public static ConfirmDialogFrag getInstance(String msg, boolean cancelable, DialogInterface.OnClickListener onClickListener) {
        ConfirmDialogFrag confirmDialogFrag = new ConfirmDialogFrag();
        Bundle bundle = new Bundle();
        bundle.putString("mensagem", msg);
        bundle.putBoolean("cancelable", cancelable);
        confirmDialogFrag.setArguments(bundle);
        confirmDialogFrag.onClickListener = onClickListener;
        return confirmDialogFrag;
    }

    private DialogInterface.OnClickListener onClickListener;
    private String msg;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.app_name)
                .setMessage(getArguments().getString("mensagem"))
                .setPositiveButton(R.string.bt_ok, onClickListener)
                .setNegativeButton(R.string.bt_cancel, onClickListener)
                .setCancelable(getArguments().getBoolean("cancelable"))
                .create();
    }


}
