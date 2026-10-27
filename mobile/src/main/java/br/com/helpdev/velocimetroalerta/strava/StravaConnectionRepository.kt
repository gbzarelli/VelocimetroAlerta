package br.com.helpdev.velocimetroalerta.strava

import android.content.Context
import com.google.android.gms.tasks.Tasks
import br.com.helpdev.velocimetroalerta.FirebaseServices
import java.util.concurrent.TimeUnit

class StravaConnectionRepository(context: Context) {
    private val appContext = context.applicationContext
    private val localState = appContext.getSharedPreferences("strava_connection_state", Context.MODE_PRIVATE)

    fun isConnectedLocally(uid: String): Boolean =
        localState.getString(KEY_CONNECTED_UID, null) == uid && localState.getString(KEY_RECONNECT_UID, null) != uid

    fun markReconnectRequired(uid: String) {
        localState.edit().remove(KEY_CONNECTED_UID).putString(KEY_RECONNECT_UID, uid).apply()
    }

    fun clearLocalState(uid: String? = null) {
        if (uid == null || localState.getString(KEY_CONNECTED_UID, null) == uid) {
            localState.edit().remove(KEY_CONNECTED_UID).remove(KEY_ATHLETE_NAME).remove(KEY_RECONNECT_UID).apply()
        } else if (localState.getString(KEY_RECONNECT_UID, null) == uid) {
            localState.edit().remove(KEY_RECONNECT_UID).remove(KEY_ATHLETE_NAME).apply()
        }
    }

    fun getState(callback: (Result<StravaConnectionState>) -> Unit) {
        val auth = FirebaseServices.auth(appContext)?.currentUser
        val functions = FirebaseServices.functions(appContext)
        if (auth == null || functions == null) {
            clearLocalState()
            callback(Result.success(StravaConnectionState(connected = false)))
            return
        }
        functions.getHttpsCallable("getStravaConnectionState").call()
            .addOnSuccessListener { result ->
                val data = result.data as? Map<*, *>
                val connected = data?.get("connected") as? Boolean == true
                val athleteName = data?.get("athleteName") as? String
                val reconnectRequired = localState.getString(KEY_RECONNECT_UID, null) == auth.uid
                if (connected && !reconnectRequired) {
                    localState.edit().putString(KEY_CONNECTED_UID, auth.uid).putString(KEY_ATHLETE_NAME, athleteName).apply()
                } else if (reconnectRequired) {
                    localState.edit().remove(KEY_CONNECTED_UID).putString(KEY_RECONNECT_UID, auth.uid).apply()
                } else clearLocalState(auth.uid)
                callback(Result.success(StravaConnectionState(connected && !reconnectRequired, athleteName, reconnectRequired)))
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun beginConnect(callback: (Result<String>) -> Unit) {
        val functions = FirebaseServices.functions(appContext)
        if (FirebaseServices.auth(appContext)?.currentUser == null || functions == null) {
            callback(Result.failure(IllegalStateException("Sign in with Google before connecting Strava.")))
            return
        }
        functions.getHttpsCallable("beginStravaConnect").call()
            .addOnSuccessListener { result ->
                val url = (result.data as? Map<*, *>)?.get("authorizationUrl") as? String
                if (url.isNullOrBlank()) callback(Result.failure(IllegalStateException("Strava authorization URL is missing.")))
                else callback(Result.success(url))
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun completeConnect(code: String, state: String, callback: (Result<Unit>) -> Unit) {
        val functions = FirebaseServices.functions(appContext)
        if (FirebaseServices.auth(appContext)?.currentUser == null || functions == null) {
            callback(Result.failure(IllegalStateException("Google account session is unavailable.")))
            return
        }
        functions.getHttpsCallable("completeStravaConnect").call(mapOf("code" to code, "state" to state))
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun cancelConnect(state: String, callback: (Result<Unit>) -> Unit = {}) {
        val functions = FirebaseServices.functions(appContext)
        if (functions == null) {
            callback(Result.failure(IllegalStateException("Cloud account features are not configured.")))
            return
        }
        functions.getHttpsCallable("cancelStravaConnect").call(mapOf("state" to state))
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun disconnect(callback: (Result<Unit>) -> Unit) {
        val functions = FirebaseServices.functions(appContext)
        if (functions == null) {
            callback(Result.failure(IllegalStateException("Cloud account features are not configured.")))
            return
        }
        functions.getHttpsCallable("disconnectStrava").call()
            .addOnSuccessListener {
                clearLocalState(FirebaseServices.auth(appContext)?.currentUser?.uid)
                callback(Result.success(Unit))
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun accessTokenBlocking(): String {
        val functions = FirebaseServices.functions(appContext)
            ?: throw IllegalStateException("Cloud account features are not configured.")
        val response = Tasks.await(functions.getHttpsCallable("getStravaAccessToken").call(), 45, TimeUnit.SECONDS)
        val token = (response.data as? Map<*, *>)?.get("accessToken") as? String
        return token?.takeIf(String::isNotBlank) ?: throw IllegalStateException("Strava access token is unavailable.")
    }

    private companion object {
        const val KEY_CONNECTED_UID = "connected_uid"
        const val KEY_ATHLETE_NAME = "athlete_name"
        const val KEY_RECONNECT_UID = "reconnect_required_uid"
    }
}
