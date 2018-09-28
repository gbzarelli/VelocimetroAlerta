package br.com.helpdev.velocimetroalerta.dialogs

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import br.com.helpdev.velocimetroalerta.R
import kotlinx.android.synthetic.main.dialog_number_picker.*

class DialogNumberPicker(context: Context,
                         private val message: String,
                         private val value: Int,
                         private val minValue: Int,
                         private val maxValue: Int,
                         private val buttonClick: (btType: Int, value: Int) -> Unit
) : AlertDialog(context) {
    companion object {
        const val BT_TYPE_OK = 10
        const val BT_TYPE_CANCEL = 11
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_number_picker)
        dialogMessage.text = message
        numberPicker.minValue = minValue
        numberPicker.maxValue = maxValue
        numberPicker.value = value
        btOk.setOnClickListener {
            buttonClick(BT_TYPE_OK, numberPicker.value)
            dismiss()
        }
        btCancel.setOnClickListener {
            buttonClick(BT_TYPE_CANCEL, value)
            dismiss()
        }
    }


}