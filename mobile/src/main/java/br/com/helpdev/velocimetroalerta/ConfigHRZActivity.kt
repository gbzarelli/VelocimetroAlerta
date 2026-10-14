package br.com.helpdev.velocimetroalerta

import android.os.Bundle
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import br.com.helpdev.velocimetroalerta.dialogs.DialogNumberPicker
import br.com.helpdev.velocimetroalerta.databinding.ActivityConfigHrzBinding

class ConfigHRZActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigHrzBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigHrzBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val content = binding.contentConfigHrz
        setSupportActionBar(binding.toolbar)
        if (supportActionBar != null) supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val sp = getDefaultSharedPreferences(this)
        content.tvMaxHr.text = sp.getInt(getString(R.string.pref_hr_max_value), resources.getInteger(R.integer.default_max_hr)).toString()
        content.tvRestHr.text = sp.getInt(getString(R.string.pref_hr_rest_value), resources.getInteger(R.integer.default_rest_hr)).toString()
        content.tvZ5Hr.text = sp.getInt(getString(R.string.pref_hr_z5_value), resources.getInteger(R.integer.default_z5)).toString()
        content.tvZ4Hr.text = sp.getInt(getString(R.string.pref_hr_z4_value), resources.getInteger(R.integer.default_z4)).toString()
        content.tvZ3Hr.text = sp.getInt(getString(R.string.pref_hr_z3_value), resources.getInteger(R.integer.default_z3)).toString()
        content.tvZ2Hr.text = sp.getInt(getString(R.string.pref_hr_z2_value), resources.getInteger(R.integer.default_z2)).toString()
        content.tvZ1Hr.text = sp.getInt(getString(R.string.pref_hr_z1_value), resources.getInteger(R.integer.default_z1)).toString()

        content.layoutMaxHr.setOnClickListener { showDialog(getString(R.string.pref_hr_max_value), resources.getInteger(R.integer.default_max_hr), getString(R.string.max_heart_rate), content.tvMaxHr, 50, 220) }
        content.layoutRestHr.setOnClickListener { showDialog(getString(R.string.pref_hr_rest_value), resources.getInteger(R.integer.default_rest_hr), getString(R.string.resting_heart_rate), content.tvRestHr, 30, 120) }
        content.layoutZ5Hr.setOnClickListener {
            // max 100 / min z4+1
            val min = sp.getInt(getString(R.string.pref_hr_z4_value), resources.getInteger(R.integer.default_z4)) + 1
            showDialog(getString(R.string.pref_hr_z5_value), resources.getInteger(R.integer.default_z5), getString(R.string.z5_anaerobic), content.tvZ5Hr, min, 100)
        }
        content.layoutZ4Hr.setOnClickListener {
            // max z5-1 / min z3+1
            val min = sp.getInt(getString(R.string.pref_hr_z3_value), resources.getInteger(R.integer.default_z3)) + 1
            val max = sp.getInt(getString(R.string.pref_hr_z5_value), resources.getInteger(R.integer.default_z5)) - 1
            showDialog(getString(R.string.pref_hr_z4_value), resources.getInteger(R.integer.default_z4), getString(R.string.z4_threshold), content.tvZ4Hr, min, max)
        }
        content.layoutZ3Hr.setOnClickListener {
            // max z4-1 / min z2+1
            val min = sp.getInt(getString(R.string.pref_hr_z2_value), resources.getInteger(R.integer.default_z2)) + 1
            val max = sp.getInt(getString(R.string.pref_hr_z4_value), resources.getInteger(R.integer.default_z4)) - 1
            showDialog(getString(R.string.pref_hr_z3_value), resources.getInteger(R.integer.default_z3), getString(R.string.z3_time), content.tvZ3Hr, min, max)
        }
        content.layoutZ2Hr.setOnClickListener {
            // max z3-1 / min z1+1
            val min = sp.getInt(getString(R.string.pref_hr_z1_value), resources.getInteger(R.integer.default_z1)) + 1
            val max = sp.getInt(getString(R.string.pref_hr_z3_value), resources.getInteger(R.integer.default_z3)) - 1
            showDialog(getString(R.string.pref_hr_z2_value), resources.getInteger(R.integer.default_z2), getString(R.string.z2_moderate), content.tvZ2Hr, min, max)
        }
        content.layoutZ1Hr.setOnClickListener {
            // max z2-1 / min 1
            val max = sp.getInt(getString(R.string.pref_hr_z2_value), resources.getInteger(R.integer.default_z2)) - 1
            showDialog(getString(R.string.pref_hr_z1_value), resources.getInteger(R.integer.default_z1), getString(R.string.z1_endurance), content.tvZ1Hr, 1, max)
        }
    }

    private fun showDialog(keyPref: String, defaultPref: Int, message: String, textView: TextView, min: Int, max: Int) {
        val sp = getDefaultSharedPreferences(this)
        val valuePreference = sp.getInt(keyPref, defaultPref)

        DialogNumberPicker(this, message, valuePreference, min, max) { btType: Int, value: Int ->
            if (DialogNumberPicker.BT_TYPE_OK == btType) {
                sp.edit().putInt(keyPref, value).apply()
                textView.text = value.toString()
            }
        }.show()
    }


}
