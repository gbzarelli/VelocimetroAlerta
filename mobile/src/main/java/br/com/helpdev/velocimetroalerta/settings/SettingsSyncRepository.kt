package br.com.helpdev.velocimetroalerta.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import br.com.helpdev.velocimetroalerta.R
import br.com.helpdev.velocimetroalerta.FirebaseServices

class SettingsSyncRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
    private var registration: ListenerRegistration? = null
    private var currentUid: String? = null
    private var lastRemoteSnapshot: Map<String, Any> = emptyMap()

    fun loadInitial(uid: String, callback: (Result<Map<String, Any>?>) -> Unit) {
        val firestore = FirebaseServices.firestore(appContext)
        if (firestore == null) {
            callback(Result.failure(IllegalStateException(appContext.getString(R.string.firebase_not_configured))))
            return
        }
        firestore.document(settingsPath(uid)).get()
            .addOnSuccessListener { snapshot ->
                callback(Result.success(if (snapshot.exists()) snapshot.data else null))
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun firstLogin(uid: String, callback: (Result<Unit>) -> Unit) {
        writeLocalProfile(uid, callback)
    }

    fun restoreRemote(uid: String, data: Map<String, Any>) {
        stop()
        currentUid = uid
        lastRemoteSnapshot = data
        SyncableSettings.apply(appContext, data, preferences)
        listen(uid)
    }

    fun useLocalProfile(uid: String, callback: (Result<Unit>) -> Unit) {
        writeLocalProfile(uid, callback)
    }

    fun start(uid: String) {
        stop()
        currentUid = uid
        listen(uid)
    }

    fun isTracking(uid: String): Boolean = currentUid == uid

    fun stop() {
        registration?.remove()
        registration = null
        activePreferenceListener?.let(preferences::unregisterOnSharedPreferenceChangeListener)
        activePreferenceListener = null
        currentUid = null
        lastRemoteSnapshot = emptyMap()
    }

    fun setStravaAutoSyncEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(SyncableSettings.AUTO_SYNC_KEY, enabled).apply()
        currentUid?.let { writeSnapshot(it) }
    }

    private fun writeLocalProfile(uid: String, callback: (Result<Unit>) -> Unit) {
        val firestore = FirebaseServices.firestore(appContext)
        if (firestore == null) {
            callback(Result.failure(IllegalStateException(appContext.getString(R.string.firebase_not_configured))))
            return
        }
        stop()
        val local = SyncableSettings.snapshot(appContext, preferences)
        // Choosing the local profile is an explicit overwrite: omitted keys must not
        // survive in Firestore and be reapplied by the snapshot listener.
        firestore.document(settingsPath(uid)).set(local)
            .addOnSuccessListener {
                currentUid = uid
                listen(uid)
                callback(Result.success(Unit))
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    private fun listen(uid: String) {
        val firestore = FirebaseServices.firestore(appContext) ?: return
        registration = firestore.document(settingsPath(uid)).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists() || currentUid != uid) return@addSnapshotListener
            val data = snapshot.data ?: return@addSnapshotListener
            lastRemoteSnapshot = data
            val local = SyncableSettings.snapshot(appContext, preferences)
            if (!samePortableValues(local, data)) SyncableSettings.apply(appContext, data, preferences)
        }
        val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key in SyncableSettings.keys(appContext) && currentUid == uid) {
                val local = SyncableSettings.snapshot(appContext, preferences)
                if (!samePortableValues(local, lastRemoteSnapshot)) writeSnapshot(uid)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(changeListener)
        activePreferenceListener = changeListener
    }

    private fun writeSnapshot(uid: String) {
        val data = SyncableSettings.snapshot(appContext, preferences)
        FirebaseServices.firestore(appContext)?.document(settingsPath(uid))?.set(data, SetOptions.merge())
    }

    private fun samePortableValues(left: Map<String, Any>, right: Map<String, Any>): Boolean {
        fun canonical(values: Map<String, Any>) = values
            .filterKeys { it in SyncableSettings.keys(appContext) }
            .mapValues { (_, value) -> if (value is Number) value.toLong() else value }
        return canonical(left) == canonical(right)
    }

    private fun settingsPath(uid: String) = "users/$uid/settings/current"

    private var activePreferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

}
