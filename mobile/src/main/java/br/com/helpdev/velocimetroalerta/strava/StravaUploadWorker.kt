package br.com.helpdev.velocimetroalerta.strava

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.functions.FirebaseFunctionsException
import br.com.helpdev.velocimetroalerta.FirebaseServices
import br.com.helpdev.velocimetroalerta.activities.ActivityRepository
import br.com.helpdev.velocimetroalerta.activities.ActivitySummary
import br.com.helpdev.velocimetroalerta.settings.SyncableSettings
import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class StravaUploadWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    private val store = StravaSyncStore(context)
    private val connectionRepository = StravaConnectionRepository(context)

    override fun doWork(): Result {
        val (uidKey, activityKey, originKey) = StravaSyncCoordinator.inputKeys()
        val uid = inputData.getString(uidKey) ?: return Result.failure()
        val activityId = inputData.getString(activityKey) ?: return Result.failure()
        val origin = runCatching { StravaUploadOrigin.valueOf(inputData.getString(originKey).orEmpty()) }
            .getOrDefault(StravaUploadOrigin.MANUAL)
        val summary = ActivityRepository(applicationContext.filesDir).loadSummary(activityId) ?: return Result.failure()
        val sportType = summary.activityType?.stravaSportType ?: run {
            store.updateStatus(uid, activityId, origin, StravaUploadStatus.RETRYABLE_ERROR, uploadId = null)
            return Result.failure()
        }
        val currentUid = FirebaseServices.auth(applicationContext)?.currentUser?.uid
        if (currentUid != uid) {
            store.updateStatus(uid, activityId, origin, StravaUploadStatus.RETRYABLE_ERROR)
            return Result.failure()
        }
        if (origin == StravaUploadOrigin.AUTOMATIC && !autoSyncEnabled()) {
            store.updateStatus(uid, activityId, origin, StravaUploadStatus.NOT_UPLOADED, uploadId = null, stravaActivityId = null)
            return Result.success()
        }

        val gpx = ActivityRepository(applicationContext.filesDir).gpxFile(activityId)
        if (gpx == null) {
            store.updateStatus(uid, activityId, origin, StravaUploadStatus.RETRYABLE_ERROR, uploadId = null)
            return Result.failure()
        }

        val token = try {
            connectionRepository.accessTokenBlocking()
        } catch (error: Exception) {
            Log.w(TAG, "Unable to obtain the Strava upload token (${error.javaClass.simpleName})")
            if ((error as? FirebaseFunctionsException)?.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION) {
                connectionRepository.markReconnectRequired(uid)
            }
            store.updateStatus(uid, activityId, origin, StravaUploadStatus.RETRYABLE_ERROR)
            return Result.failure()
        }
        if (FirebaseServices.auth(applicationContext)?.currentUser?.uid != uid) {
            store.updateStatus(uid, activityId, origin, StravaUploadStatus.RETRYABLE_ERROR)
            return Result.failure()
        }
        if (origin == StravaUploadOrigin.AUTOMATIC && !autoSyncEnabled()) {
            store.updateStatus(uid, activityId, origin, StravaUploadStatus.NOT_UPLOADED, uploadId = null, stravaActivityId = null)
            return Result.success()
        }

        var record = store.getOrCreate(uid, activityId, origin)
        if (record.status == StravaUploadStatus.SYNCED || record.status == StravaUploadStatus.MANUALLY_CONFIRMED) return Result.success()
        if (record.uploadId.isNullOrBlank()) {
            if (origin == StravaUploadOrigin.AUTOMATIC && !autoSyncEnabled()) {
                store.updateStatus(uid, activityId, origin, StravaUploadStatus.NOT_UPLOADED, uploadId = null)
                return Result.success()
            }
            val postStartedAt = System.currentTimeMillis()
            store.updateStatus(uid, activityId, origin, StravaUploadStatus.UPLOADING, uploadId = null, postStartedAtEpochMillis = postStartedAt)
            val uploaded = try {
                postActivity(token, summary, gpx, sportType)
            } catch (error: RateLimited) {
                store.updateStatus(uid, activityId, origin, StravaUploadStatus.QUEUED, uploadId = null, postStartedAtEpochMillis = null)
                StravaSyncCoordinator.enqueueRateLimitedRetry(applicationContext, uid, activityId, origin, error.retryAfterSeconds)
                return Result.success()
            } catch (error: Unauthorized) {
                connectionRepository.markReconnectRequired(uid)
                store.updateStatus(uid, activityId, origin, StravaUploadStatus.RETRYABLE_ERROR, uploadId = null)
                return Result.failure()
            } catch (error: RejectedUpload) {
                Log.w(TAG, "Strava rejected the upload: HTTP ${error.statusCode}${error.message?.let { ": $it" }.orEmpty()}")
                store.updateStatus(
                    uid, activityId, origin, StravaUploadStatus.REJECTED,
                    uploadId = null,
                    postStartedAtEpochMillis = postStartedAt,
                    failureHttpStatus = error.statusCode,
                    failureMessage = error.message
                )
                return Result.failure()
            } catch (error: AmbiguousPost) {
                store.updateStatus(uid, activityId, origin, StravaUploadStatus.NEEDS_ACTION, uploadId = null, postStartedAtEpochMillis = postStartedAt)
                return Result.failure()
            } catch (error: Exception) {
                Log.w(TAG, "Strava upload failed before confirmation (${error.javaClass.simpleName})")
                store.updateStatus(uid, activityId, origin, StravaUploadStatus.RETRYABLE_ERROR, uploadId = null, postStartedAtEpochMillis = null)
                return Result.failure()
            }
            record = store.updateStatus(
                uid, activityId, origin, StravaUploadStatus.PROCESSING,
                uploadId = uploaded.uploadId,
                stravaActivityId = null,
                postStartedAtEpochMillis = postStartedAt
            )
        }
        return pollUntilProcessed(token, uid, activityId, origin, record.uploadId!!)
    }

    private fun postActivity(token: String, summary: ActivitySummary, gpx: java.io.File, sportType: String): UploadAccepted {
        val boundary = "----VelocimetroAlerta${UUID.randomUUID().toString().replace("-", "")}"
        val connection = (URL("https://www.strava.com/api/v3/uploads").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setChunkedStreamingMode(64 * 1024)
        }
        try {
            DataOutputStream(connection.outputStream).use { output ->
                writeField(output, boundary, "name", summary.name)
                writeField(output, boundary, "sport_type", sportType)
                writeField(output, boundary, "data_type", "gpx")
                writeField(output, boundary, "external_id", "velocimetro-alerta-${summary.id}.gpx")
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${summary.id}.gpx\"\r\n")
                output.writeBytes("Content-Type: application/gpx+xml\r\n\r\n")
                gpx.inputStream().use { it.copyTo(output) }
                output.writeBytes("\r\n--$boundary--\r\n")
            }
            when (val status = connection.responseCode) {
                201 -> {
                    val response = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                    val uploadId = response.optString("id_str").takeIf(String::isNotBlank)
                        ?: response.optLong("id").takeIf { it > 0 }?.toString()
                    if (uploadId == null || (!response.isNull("error") && response.optString("error").isNotBlank())) throw AmbiguousPost()
                    return UploadAccepted(uploadId)
                }
                else -> {
                    val errorBody = runCatching {
                        (connection.errorStream ?: connection.inputStream).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }.getOrDefault("")
                    when (val failure = classifyStravaUploadHttpFailure(status, errorBody)) {
                        StravaUploadHttpFailure.Unauthorized -> throw Unauthorized()
                        StravaUploadHttpFailure.RateLimited ->
                            throw RateLimited(parseRetryAfter(connection.getHeaderField("Retry-After")))
                        StravaUploadHttpFailure.Ambiguous -> throw AmbiguousPost()
                        is StravaUploadHttpFailure.Rejected ->
                            throw RejectedUpload(failure.statusCode, failure.message)
                    }
                }
            }
        } catch (error: RateLimited) {
            throw error
        } catch (error: Unauthorized) {
            throw error
        } catch (error: RejectedUpload) {
            throw error
        } catch (error: AmbiguousPost) {
            throw error
        } catch (error: Exception) {
            // Once multipart bytes have started, a lost response does not prove that Strava rejected the upload.
            throw AmbiguousPost()
        } finally {
            connection.disconnect()
        }
    }

    private fun pollUntilProcessed(
        token: String,
        uid: String,
        activityId: String,
        origin: StravaUploadOrigin,
        uploadId: String
    ): Result {
        val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(7)
        var pollDelayMillis = 1_000L
        while (!isStopped && System.currentTimeMillis() < deadline) {
            if (FirebaseServices.auth(applicationContext)?.currentUser?.uid != uid) {
                store.updateStatus(uid, activityId, origin, StravaUploadStatus.RETRYABLE_ERROR, uploadId = uploadId)
                return Result.failure()
            }
            try {
                val connection = (URL("https://www.strava.com/api/v3/uploads/$uploadId").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    setRequestProperty("Authorization", "Bearer $token")
                }
                val status = connection.responseCode
                if (status == 401) {
                    connection.disconnect()
                    connectionRepository.markReconnectRequired(uid)
                    store.updateStatus(uid, activityId, origin, StravaUploadStatus.RETRYABLE_ERROR, uploadId = uploadId)
                    return Result.failure()
                }
                if (status == 429) {
                    val retryAfter = parseRetryAfter(connection.getHeaderField("Retry-After"))
                    connection.disconnect()
                    store.updateStatus(uid, activityId, origin, StravaUploadStatus.PROCESSING, uploadId = uploadId)
                    StravaSyncCoordinator.enqueueRateLimitedRetry(applicationContext, uid, activityId, origin, retryAfter)
                    return Result.success()
                }
                if (status !in 200..299) {
                    connection.disconnect()
                    store.updateStatus(uid, activityId, origin, StravaUploadStatus.PROCESSING, uploadId = uploadId)
                    return Result.retry()
                }
                val response = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                connection.disconnect()
                val stravaActivityId = response.optLong("activity_id").takeIf { it > 0 }?.toString()
                if (stravaActivityId != null) {
                    store.updateStatus(
                        uid, activityId, origin, StravaUploadStatus.SYNCED,
                        uploadId = uploadId,
                        stravaActivityId = stravaActivityId
                    )
                    return Result.success()
                }
                if (response.optString("error").isNotBlank()) {
                    store.updateStatus(uid, activityId, origin, StravaUploadStatus.NEEDS_ACTION, uploadId = uploadId)
                    return Result.failure()
                }
            } catch (error: Exception) {
                Log.w(TAG, "Strava upload status could not be read (${error.javaClass.simpleName})")
                store.updateStatus(uid, activityId, origin, StravaUploadStatus.PROCESSING, uploadId = uploadId)
                return Result.retry()
            }
            try {
                Thread.sleep(pollDelayMillis)
                pollDelayMillis = (pollDelayMillis * 2).coerceAtMost(15_000L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return Result.retry()
            }
        }
        store.updateStatus(uid, activityId, origin, StravaUploadStatus.PROCESSING, uploadId = uploadId)
        return Result.retry()
    }

    private fun writeField(output: DataOutputStream, boundary: String, name: String, value: String) {
        output.writeBytes("--$boundary\r\n")
        output.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
        output.write(value.toByteArray(Charsets.UTF_8))
        output.writeBytes("\r\n")
    }

    private fun parseRetryAfter(value: String?): Long {
        val seconds = value?.toLongOrNull()
        if (seconds != null) return seconds.coerceIn(1, TimeUnit.HOURS.toSeconds(6))
        val retryAfterDate = value ?: return 60
        val date = runCatching {
            ZonedDateTime.parse(retryAfterDate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull()
        return if (date == null) 60 else TimeUnit.MILLISECONDS.toSeconds((date - System.currentTimeMillis()).coerceAtLeast(1_000)).coerceAtMost(TimeUnit.HOURS.toSeconds(6))
    }

    private fun autoSyncEnabled(): Boolean = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        .getBoolean(SyncableSettings.AUTO_SYNC_KEY, false)

    private data class UploadAccepted(val uploadId: String)
    private class RateLimited(val retryAfterSeconds: Long) : IOException()
    private class Unauthorized : IOException()
    private class RejectedUpload(val statusCode: Int, message: String?) : IOException(message)
    private class AmbiguousPost : IOException()

    private companion object { const val TAG = "StravaUpload" }
}
