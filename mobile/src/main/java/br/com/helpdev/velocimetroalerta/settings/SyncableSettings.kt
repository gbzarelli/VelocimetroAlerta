package br.com.helpdev.velocimetroalerta.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import br.com.helpdev.velocimetroalerta.R
import org.json.JSONObject

object SyncableSettings {
    const val AUTO_SYNC_KEY = "stravaAutoSyncEnabled"

    private val booleanSettings = listOf(
        R.string.pref_informar_tempo to false,
        R.string.pref_informar_distancia to false,
        R.string.pref_informar_vatual to false,
        R.string.pref_informar_vmedia to true,
        R.string.pref_informar_vmax to false,
        R.string.pref_vibrar to false,
        R.string.pref_pause_automatico to true
    )
    private val integerSettings = listOf(
        R.string.pref_hr_max_value to R.integer.default_max_hr,
        R.string.pref_hr_rest_value to R.integer.default_rest_hr,
        R.string.pref_hr_z1_value to R.integer.default_z1,
        R.string.pref_hr_z2_value to R.integer.default_z2,
        R.string.pref_hr_z3_value to R.integer.default_z3,
        R.string.pref_hr_z4_value to R.integer.default_z4,
        R.string.pref_hr_z5_value to R.integer.default_z5
    )
    private val intervalResource = R.string.pref_intervalo
    private val intervalValueResource = R.string.pref_intervalo_valor
    fun keys(context: Context): Set<String> = buildSet {
        booleanSettings.forEach { (resource, _) -> add(context.getString(resource)) }
        integerSettings.forEach { (resource, _) -> add(context.getString(resource)) }
        add(context.getString(intervalResource))
        add(context.getString(intervalValueResource))
        add(AUTO_SYNC_KEY)
    }

    fun snapshot(context: Context, preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)): Map<String, Any> {
        val values = preferences.all
        val result = linkedMapOf<String, Any>()
        booleanSettings.forEach { (id, defaultValue) ->
            val key = context.getString(id)
            result[key] = values[key] as? Boolean ?: defaultValue
        }
        integerSettings.forEach { (id, defaultResource) ->
            val key = context.getString(id)
            result[key] = (values[key] as? Number)?.toInt() ?: context.resources.getInteger(defaultResource)
        }
        val intervalKey = context.getString(intervalResource)
        (values[intervalKey] as? String)?.let { local ->
            normalizeIntervalMode(local)?.let { result[intervalKey] = it }
        } ?: run { result[intervalKey] = "time" }
        val intervalValueKey = context.getString(intervalValueResource)
        result[intervalValueKey] = normalizedIntervalValue(values[intervalValueKey] as? String, context)
        result[AUTO_SYNC_KEY] = values[AUTO_SYNC_KEY] as? Boolean ?: false
        return result
    }

    fun apply(context: Context, data: Map<String, Any>, preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)) {
        val editor = preferences.edit()
        booleanSettings.forEach { (id, defaultValue) ->
            val key = context.getString(id)
            editor.putBoolean(key, data[key] as? Boolean ?: defaultValue)
        }
        integerSettings.forEach { (id, defaultResource) ->
            val key = context.getString(id)
            editor.putInt(key, (data[key] as? Number)?.toInt() ?: context.resources.getInteger(defaultResource))
        }
        val intervalKey = context.getString(intervalResource)
        val intervalValues = context.resources.getStringArray(R.array.intervalo_values)
        val intervalMode = data[intervalKey] as? String ?: "time"
        editor.putString(intervalKey, if (intervalMode == "distance") intervalValues[0] else intervalValues[1])
        val intervalValueKey = context.getString(intervalValueResource)
        editor.putString(intervalValueKey, normalizedIntervalValue(data[intervalValueKey] as? String, context))
        editor.putBoolean(AUTO_SYNC_KEY, data[AUTO_SYNC_KEY] as? Boolean ?: false)
        editor.apply()
    }

    fun toJson(data: Map<String, Any>): String = JSONObject(data).toString()

    fun fromJson(json: String): Map<String, Any> {
        val objectValue = JSONObject(json)
        return objectValue.keys().asSequence().associateWith { key ->
            when (val value = objectValue.get(key)) {
                is Boolean, is String -> value
                is Number -> value
                else -> throw IllegalArgumentException("Unsupported saved profile value")
            }
        }
    }

    private fun normalizeIntervalMode(localValue: String): String? = when (localValue.trim().lowercase()) {
        "kilometers", "quilômetro", "quilometro", "quilômetros", "quilometros", "distance" -> "distance"
        "minutes", "minutos", "minute", "time" -> "time"
        else -> null
    }

    private fun normalizedIntervalValue(value: String?, context: Context): String =
        value?.toIntOrNull()?.takeIf { it in 1..999 }?.toString() ?: context.getString(R.string.valor_default)
}
