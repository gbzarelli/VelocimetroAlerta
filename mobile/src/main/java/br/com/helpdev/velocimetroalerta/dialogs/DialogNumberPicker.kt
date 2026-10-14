package br.com.helpdev.velocimetroalerta.dialogs

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.os.Bundle
import br.com.helpdev.velocimetroalerta.R
import br.com.helpdev.velocimetroalerta.databinding.DialogNumberPickerBinding
import android.view.LayoutInflater

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

    private lateinit var binding: DialogNumberPickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogNumberPickerBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)
        binding.dialogMessage.text = message
        binding.numberPicker.minValue = minValue
        binding.numberPicker.maxValue = maxValue
        binding.numberPicker.value = value
        binding.btOk.setOnClickListener {
            buttonClick(BT_TYPE_OK, binding.numberPicker.value)
            dismiss()
        }
        binding.btCancel.setOnClickListener {
            buttonClick(BT_TYPE_CANCEL, value)
            dismiss()
        }
    }


}
