package br.com.helpdev.velocimetroalerta.strava

import android.content.Context

/** Holds a single manual upload request while Google/Strava authorization screens are open. */
class PendingManualActivityStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(activityId: String, uid: String?) {
        preferences.edit()
            .putString(KEY_ACTIVITY_ID, activityId)
            .putString(KEY_UID, uid)
            .apply()
    }

    fun bindTo(uid: String): Boolean {
        val owner = preferences.getString(KEY_UID, null)
        if (owner != null) return owner == uid
        if (preferences.getString(KEY_ACTIVITY_ID, null).isNullOrBlank()) return false
        preferences.edit().putString(KEY_UID, uid).apply()
        return true
    }

    fun peek(uid: String): String? =
        preferences.getString(KEY_ACTIVITY_ID, null).takeIf { preferences.getString(KEY_UID, null) == uid }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "strava_pending_manual_activity"
        const val KEY_ACTIVITY_ID = "activity_id"
        const val KEY_UID = "uid"
    }
}
