package br.com.helpdev.velocimetroalerta

import android.os.Bundle
import android.preference.PreferenceManager.getDefaultSharedPreferences
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.widget.TextView
import br.com.helpdev.velocimetroalerta.dialogs.DialogNumberPicker
import kotlinx.android.synthetic.main.content_config_hrz.*

class ConfigHRZActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_hrz)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (supportActionBar != null) supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val sp = getDefaultSharedPreferences(this)
        tv_max_hr.text = sp.getInt(getString(R.string.pref_hr_max_value), resources.getInteger(R.integer.default_max_hr)).toString()
        tv_rest_hr.text = sp.getInt(getString(R.string.pref_hr_rest_value), resources.getInteger(R.integer.default_rest_hr)).toString()
        tv_z5_hr.text = sp.getInt(getString(R.string.pref_hr_z5_value), resources.getInteger(R.integer.default_z5)).toString()
        tv_z4_hr.text = sp.getInt(getString(R.string.pref_hr_z4_value), resources.getInteger(R.integer.default_z4)).toString()
        tv_z3_hr.text = sp.getInt(getString(R.string.pref_hr_z3_value), resources.getInteger(R.integer.default_z3)).toString()
        tv_z2_hr.text = sp.getInt(getString(R.string.pref_hr_z2_value), resources.getInteger(R.integer.default_z2)).toString()
        tv_z1_hr.text = sp.getInt(getString(R.string.pref_hr_z1_value), resources.getInteger(R.integer.default_z1)).toString()

        layout_max_hr.setOnClickListener { showDialog(getString(R.string.pref_hr_max_value), resources.getInteger(R.integer.default_max_hr), getString(R.string.max_heart_rate), tv_max_hr, 50, 220) }
        layout_rest_hr.setOnClickListener { showDialog(getString(R.string.pref_hr_rest_value), resources.getInteger(R.integer.default_rest_hr), getString(R.string.resting_heart_rate), tv_rest_hr, 30, 120) }
        layout_z5_hr.setOnClickListener {
            // max 100 / min z4+1
            val min = sp.getInt(getString(R.string.pref_hr_z4_value), resources.getInteger(R.integer.default_z4)) + 1
            showDialog(getString(R.string.pref_hr_z5_value), resources.getInteger(R.integer.default_z5), getString(R.string.z5_anaerobic), tv_z5_hr, min, 100)
        }
        layout_z4_hr.setOnClickListener {
            // max z5-1 / min z3+1
            val min = sp.getInt(getString(R.string.pref_hr_z3_value), resources.getInteger(R.integer.default_z3)) + 1
            val max = sp.getInt(getString(R.string.pref_hr_z5_value), resources.getInteger(R.integer.default_z5)) - 1
            showDialog(getString(R.string.pref_hr_z4_value), resources.getInteger(R.integer.default_z4), getString(R.string.z4_threshold), tv_z4_hr, min, max)
        }
        layout_z3_hr.setOnClickListener {
            // max z4-1 / min z2+1
            val min = sp.getInt(getString(R.string.pref_hr_z2_value), resources.getInteger(R.integer.default_z2)) + 1
            val max = sp.getInt(getString(R.string.pref_hr_z4_value), resources.getInteger(R.integer.default_z4)) - 1
            showDialog(getString(R.string.pref_hr_z3_value), resources.getInteger(R.integer.default_z3), getString(R.string.z3_time), tv_z3_hr, min, max)
        }
        layout_z2_hr.setOnClickListener {
            // max z3-1 / min z1+1
            val min = sp.getInt(getString(R.string.pref_hr_z1_value), resources.getInteger(R.integer.default_z1)) + 1
            val max = sp.getInt(getString(R.string.pref_hr_z3_value), resources.getInteger(R.integer.default_z3)) - 1
            showDialog(getString(R.string.pref_hr_z2_value), resources.getInteger(R.integer.default_z2), getString(R.string.z2_moderate), tv_z2_hr, min, max)
        }
        layout_z1_hr.setOnClickListener {
            // max z2-1 / min 1
            val max = sp.getInt(getString(R.string.pref_hr_z2_value), resources.getInteger(R.integer.default_z2)) - 1
            showDialog(getString(R.string.pref_hr_z1_value), resources.getInteger(R.integer.default_z1), getString(R.string.z1_endurance), tv_z1_hr, 1, max)
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