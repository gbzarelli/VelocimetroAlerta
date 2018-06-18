package br.com.helpdev.velocimetroalerta.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AlertDialog

import br.com.helpdev.velocimetroalerta.R

/**
 * Created by Guilherme Biff Zarelli on 06/04/16.
 */
class ConfirmDialogFrag : android.support.v4.app.DialogFragment() {

    private var onClickListener: DialogInterface.OnClickListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(activity!!)
                .setTitle(R.string.app_name)
                .setMessage(arguments!!.getString("mensagem"))
                .setPositiveButton(R.string.bt_ok, onClickListener)
                .setNegativeButton(R.string.bt_cancel, onClickListener)
                .setCancelable(arguments!!.getBoolean("cancelable"))
                .create()
    }

    companion object {

        fun getInstance(msg: String, cancelable: Boolean, onClickListener: DialogInterface.OnClickListener): ConfirmDialogFrag {
            val confirmDialogFrag = ConfirmDialogFrag()
            val bundle = Bundle()
            bundle.putString("mensagem", msg)
            bundle.putBoolean("cancelable", cancelable)
            confirmDialogFrag.arguments = bundle
            confirmDialogFrag.onClickListener = onClickListener
            return confirmDialogFrag
        }
    }


}
