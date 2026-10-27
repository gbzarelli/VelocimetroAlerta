package br.com.helpdev.velocimetroalerta.strava

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import br.com.helpdev.velocimetroalerta.FirebaseServices
import br.com.helpdev.velocimetroalerta.activities.ActivityRepository
import br.com.helpdev.velocimetroalerta.activities.ActivitySummary
import br.com.helpdev.velocimetroalerta.settings.SyncableSettings
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import android.util.Base64

object StravaSyncCoordinator {
    private const val TAG = "StravaSync"
    private const val KEY_UID = "uid"
    private const val KEY_ACTIVITY_ID = "activityId"
    private const val KEY_ORIGIN = "origin"

    fun enqueueManual(context: Context, uid: String, activityId: String) {
        enqueue(context, uid, activityId, StravaUploadOrigin.MANUAL)
    }

    fun retryAfterAmbiguousResult(context: Context, uid: String, activityId: String) {
        StravaSyncStore(context).updateStatus(
            uid, activityId, StravaUploadOrigin.MANUAL, StravaUploadStatus.NOT_UPLOADED,
            uploadId = null, stravaActivityId = null, postStartedAtEpochMillis = null
        )
        enqueue(context, uid, activityId, StravaUploadOrigin.MANUAL)
    }

    fun enqueueAutoIfEligible(context: Context, summary: ActivitySummary) {
        val uid = FirebaseServices.auth(context)?.currentUser?.uid ?: return
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!preferences.getBoolean(SyncableSettings.AUTO_SYNC_KEY, false)) return
        if (summary.activityType?.stravaSportType == null) return
        if (!StravaConnectionRepository(context).isConnectedLocally(uid)) return
        enqueue(context, uid, summary.id, StravaUploadOrigin.AUTOMATIC)
    }

    fun cancelQueuedAutomatic(context: Context) {
        val currentUid = FirebaseServices.auth(context)?.currentUser?.uid ?: return
        val manager = WorkManager.getInstance(context)
        val store = StravaSyncStore(context)
        store.allRecords().filter {
            it.uid == currentUid && it.origin == StravaUploadOrigin.AUTOMATIC && it.status == StravaUploadStatus.QUEUED
        }
            .forEach { record ->
                val workName = uniqueName(record.uid, record.activityId)
                Thread({
                    runCatching {
                        val work = manager.getWorkInfosForUniqueWork(workName).get().firstOrNull()
                        if (work?.state == androidx.work.WorkInfo.State.ENQUEUED) {
                            manager.cancelUniqueWork(workName)
                            store.updateStatus(record.uid, record.activityId, record.origin, StravaUploadStatus.NOT_UPLOADED, uploadId = null)
                        }
                    }.onFailure { Log.w(TAG, "Could not cancel queued automatic Strava work") }
                }, "strava-cancel-queued").apply { isDaemon = true }.start()
            }
    }

    fun cancelActivity(context: Context, activityId: String) {
        val manager = WorkManager.getInstance(context)
        val store = StravaSyncStore(context)
        store.allRecords().filter { it.activityId == activityId }.forEach { record ->
            manager.cancelUniqueWork(uniqueName(record.uid, record.activityId))
        }
        store.deleteActivity(activityId)
    }

    internal fun uniqueName(uid: String, activityId: String): String {
        val uidHash = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "strava-upload-$uidHash-$activityId"
    }

    internal fun buildRequest(
        uid: String,
        activityId: String,
        origin: StravaUploadOrigin,
        delaySeconds: Long = 0
    ): OneTimeWorkRequest = OneTimeWorkRequestBuilder<StravaUploadWorker>()
        .setInputData(
            Data.Builder()
                .putString(KEY_UID, uid)
                .putString(KEY_ACTIVITY_ID, activityId)
                .putString(KEY_ORIGIN, origin.name)
                .build()
        )
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .setInitialDelay(delaySeconds.coerceAtLeast(0), TimeUnit.SECONDS)
        .addTag(uniqueName(uid, activityId))
        .build()

    private fun enqueue(context: Context, uid: String, activityId: String, origin: StravaUploadOrigin) {
        val summary = ActivityRepository(context.applicationContext.filesDir).loadSummary(activityId) ?: return
        if (summary.activityType?.stravaSportType == null) return
        val store = StravaSyncStore(context)
        val previous = store.get(uid, activityId)
        if (previous?.status == StravaUploadStatus.SYNCED || previous?.status == StravaUploadStatus.MANUALLY_CONFIRMED) return
        if (previous?.status == StravaUploadStatus.UPLOADING || previous?.status == StravaUploadStatus.PROCESSING) return
        val workName = uniqueName(uid, activityId)
        store.updateStatus(uid, activityId, origin, StravaUploadStatus.QUEUED)
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.KEEP,
            buildRequest(uid, activityId, origin)
        )
    }

    internal fun enqueueRateLimitedRetry(context: Context, uid: String, activityId: String, origin: StravaUploadOrigin, delaySeconds: Long) {
        val name = uniqueName(uid, activityId)
        WorkManager.getInstance(context).enqueueUniqueWork(
            name,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            buildRequest(uid, activityId, origin, delaySeconds)
        )
    }

    internal fun inputKeys() = Triple(KEY_UID, KEY_ACTIVITY_ID, KEY_ORIGIN)
}
