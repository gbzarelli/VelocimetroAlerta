package br.com.helpdev.velocimetroalerta.strava

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import android.util.Base64

enum class StravaUploadStatus {
    NOT_UPLOADED,
    QUEUED,
    UPLOADING,
    PROCESSING,
    SYNCED,
    MANUALLY_CONFIRMED,
    RETRYABLE_ERROR,
    REJECTED,
    NEEDS_ACTION
}

enum class StravaUploadOrigin { MANUAL, AUTOMATIC }

data class StravaUploadRecord(
    val uid: String,
    val activityId: String,
    val origin: StravaUploadOrigin,
    val status: StravaUploadStatus,
    val uploadId: String? = null,
    val stravaActivityId: String? = null,
    val postStartedAtEpochMillis: Long? = null,
    val failureHttpStatus: Int? = null,
    val failureMessage: String? = null,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

class StravaSyncStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY)
    private val gson = Gson()

    fun get(uid: String, activityId: String): StravaUploadRecord? {
        val file = recordFile(uid, activityId)
        if (!file.isFile) return null
        return runCatching { file.reader(Charsets.UTF_8).use { gson.fromJson(it, StravaUploadRecord::class.java) } }.getOrNull()
    }

    fun getOrCreate(uid: String, activityId: String, origin: StravaUploadOrigin): StravaUploadRecord =
        get(uid, activityId) ?: StravaUploadRecord(uid, activityId, origin, StravaUploadStatus.NOT_UPLOADED)

    @Synchronized
    fun save(record: StravaUploadRecord) {
        val file = recordFile(record.uid, record.activityId)
        val parent = file.parentFile ?: throw IOException("Strava sync state path is invalid")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Unable to create Strava sync state directory")
        val atomicFile = AtomicFile(file)
        val output = atomicFile.startWrite()
        try {
            output.write(gson.toJson(record.copy(updatedAtEpochMillis = System.currentTimeMillis())).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    fun updateStatus(
        uid: String,
        activityId: String,
        origin: StravaUploadOrigin,
        status: StravaUploadStatus,
        uploadId: String? = get(uid, activityId)?.uploadId,
        stravaActivityId: String? = get(uid, activityId)?.stravaActivityId,
        postStartedAtEpochMillis: Long? = get(uid, activityId)?.postStartedAtEpochMillis,
        failureHttpStatus: Int? = null,
        failureMessage: String? = null
    ): StravaUploadRecord {
        val record = StravaUploadRecord(
            uid = uid,
            activityId = activityId,
            origin = origin,
            status = status,
            uploadId = uploadId,
            stravaActivityId = stravaActivityId,
            postStartedAtEpochMillis = postStartedAtEpochMillis,
            failureHttpStatus = failureHttpStatus,
            failureMessage = failureMessage
        )
        save(record)
        return record
    }

    fun allRecords(): List<StravaUploadRecord> = directory.listFiles()?.asSequence().orEmpty()
        .filter { it.isDirectory }
        .flatMap { uidDirectory -> uidDirectory.listFiles()?.asSequence().orEmpty() }
        .filter { it.isFile && it.name.endsWith(".json") }
        .mapNotNull { file -> runCatching { file.reader(Charsets.UTF_8).use { gson.fromJson(it, StravaUploadRecord::class.java) } }.getOrNull() }
        .toList()

    fun deleteActivity(activityId: String) {
        allRecords().filter { it.activityId == activityId }.forEach { record -> recordFile(record.uid, activityId).delete() }
    }

    private fun recordFile(uid: String, activityId: String): File {
        require(activityId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid activity ID" }
        return File(File(directory, uidHash(uid)), "$activityId.json")
    }

    private fun uidHash(uid: String): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8)),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )

    private companion object { const val DIRECTORY = "strava_sync" }
}
