package br.com.helpdev.velocimetroalerta.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import android.util.Base64
import java.security.MessageDigest

class LocalProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val profiles: SharedPreferences = appContext.getSharedPreferences("account_profiles", Context.MODE_PRIVATE)
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)

    fun captureGuest() = capture(PROFILE_GUEST)

    fun captureAccount(uid: String) = capture(accountProfileKey(uid))

    fun hasAccount(uid: String): Boolean = profiles.contains(accountProfileKey(uid))

    fun restoreAccount(uid: String): Boolean = restore(accountProfileKey(uid))

    fun restoreGuest(): Boolean {
        val restored = restore(PROFILE_GUEST)
        if (!restored) preferences.edit().putBoolean(SyncableSettings.AUTO_SYNC_KEY, false).apply()
        return restored
    }

    private fun capture(key: String) {
        val json = SyncableSettings.toJson(SyncableSettings.snapshot(appContext, preferences))
        profiles.edit().putString(key, json).apply()
    }

    private fun restore(key: String): Boolean {
        val json = profiles.getString(key, null) ?: return false
        val snapshot = runCatching { SyncableSettings.fromJson(json) }.getOrNull() ?: return false
        SyncableSettings.apply(appContext, snapshot, preferences)
        return true
    }

    private fun accountProfileKey(uid: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
        return "account_${Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)}"
    }

    private companion object {
        const val PROFILE_GUEST = "guest"
    }
}
