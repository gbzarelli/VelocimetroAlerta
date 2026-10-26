package br.com.helpdev.velocimetroalerta.activities

import br.com.helpdev.velocimetroalerta.gps.RoutePoint
import br.com.helpdev.velocimetroalerta.gpx.GpxFileUtils
import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

class ActivityRepository(
    filesDir: File,
    private val gson: Gson = Gson(),
    private val gpxFileUtils: GpxFileUtils = GpxFileUtils()
) {
    private val activitiesDirectory = File(filesDir, ACTIVITIES_DIRECTORY)

    fun save(draft: ActivityDraft, confirmedName: String): ActivitySummary {
        val name = confirmedName.trim()
        require(name.isNotEmpty()) { "An activity name is required" }
        require(ID_PATTERN.matches(draft.summary.id)) { "Invalid activity ID" }
        val finalDirectory = File(activitiesDirectory, draft.summary.id)
        check(!finalDirectory.exists()) { "An activity with this ID already exists" }
        if (!activitiesDirectory.exists() && !activitiesDirectory.mkdirs()) {
            throw IOException("Unable to create activities directory")
        }
        val temporaryDirectory = File(activitiesDirectory, "${draft.summary.id}.tmp")
        if (temporaryDirectory.exists() && !temporaryDirectory.deleteRecursively()) {
            throw IOException("Unable to clear temporary activity directory")
        }
        if (!temporaryDirectory.mkdirs()) throw IOException("Unable to create temporary activity directory")

        val summary = draft.summary.copy(name = name)
        val gpx = copyGpxWithName(draft.gpx, name)
        try {
            val gpxFile = File(temporaryDirectory, GPX_FILENAME)
            gpxFile.outputStream().use { gpxFileUtils.writeGpx(gpx, it) }
            File(temporaryDirectory, SUMMARY_FILENAME).writeText(gson.toJson(summary), Charsets.UTF_8)
            validateStoredFiles(temporaryDirectory, summary.id)
            if (!temporaryDirectory.renameTo(finalDirectory)) {
                throw IOException("Unable to finish saving the activity")
            }
            return summary
        } catch (error: Exception) {
            temporaryDirectory.deleteRecursively()
            throw error
        }
    }

    /** Renames a valid saved activity and keeps its summary and GPX track name in sync. */
    fun rename(id: String, confirmedName: String): ActivitySummary? {
        val name = confirmedName.trim()
        require(name.isNotEmpty()) { "An activity name is required" }
        val directory = activityDirectory(id) ?: return null
        val current = loadSummary(id) ?: return null
        val currentGpxFile = gpxFile(id) ?: return null
        val currentGpx = currentGpxFile.inputStream().use { gpxFileUtils.readGpx(it) }
        val renamedSummary = current.copy(name = name)
        val renamedGpx = copyGpxWithName(currentGpx, name)
        val summaryFile = File(directory, SUMMARY_FILENAME)
        val gpxFile = File(directory, GPX_FILENAME)
        val temporarySummary = File(directory, "$SUMMARY_FILENAME.rename-tmp")
        val temporaryGpx = File(directory, "$GPX_FILENAME.rename-tmp")
        val backupSummary = File(directory, "$SUMMARY_FILENAME.rename-backup")
        val backupGpx = File(directory, "$GPX_FILENAME.rename-backup")

        try {
            temporaryGpx.outputStream().use { gpxFileUtils.writeGpx(renamedGpx, it) }
            temporarySummary.writeText(gson.toJson(renamedSummary), Charsets.UTF_8)
            if (temporaryGpx.length() == 0L || temporarySummary.length() == 0L) {
                throw IOException("Unable to prepare the renamed activity")
            }
            if (backupSummary.exists() && !backupSummary.delete()) throw IOException("Unable to clear old summary backup")
            if (backupGpx.exists() && !backupGpx.delete()) throw IOException("Unable to clear old GPX backup")

            if (!summaryFile.renameTo(backupSummary)) throw IOException("Unable to protect the activity summary")
            if (!gpxFile.renameTo(backupGpx)) throw IOException("Unable to protect the activity GPX")
            if (!temporarySummary.renameTo(summaryFile)) throw IOException("Unable to replace the activity summary")
            if (!temporaryGpx.renameTo(gpxFile)) throw IOException("Unable to replace the activity GPX")

            backupSummary.delete()
            backupGpx.delete()
            return renamedSummary
        } catch (error: Exception) {
            restoreBackup(backupSummary, summaryFile)
            restoreBackup(backupGpx, gpxFile)
            if (error is IOException) throw error
            throw IOException("Unable to rename the saved activity", error)
        } finally {
            temporarySummary.delete()
            temporaryGpx.delete()
        }
    }

    /** Deletes only a valid activity whose stored ID matches its directory. */
    fun delete(id: String): Boolean {
        val directory = activityDirectory(id) ?: return false
        if (loadSummary(id) == null) return false
        return directory.deleteRecursively()
    }

    fun list(): List<ActivitySummary> {
        val directories = activitiesDirectory.listFiles()?.asSequence().orEmpty()
        return directories
            .filter { it.isDirectory && !it.name.endsWith(TEMPORARY_SUFFIX) }
            .mapNotNull { directory ->
                runCatching {
                    val summary = readSummary(directory)
                    if (summary.id != directory.name) throw IOException("Activity ID does not match its directory")
                    validateStoredFiles(directory, summary.id)
                    summary
                }.onFailure { error ->
                    LOGGER.log(Level.WARNING, "Skipping invalid saved activity directory ${directory.name}", error)
                }.getOrNull()
            }
            .sortedByDescending { it.startedAtEpochMillis }
            .toList()
    }

    fun loadSummary(id: String): ActivitySummary? {
        val directory = activityDirectory(id) ?: return null
        return runCatching {
            val summary = readSummary(directory)
            if (summary.id != id) throw IOException("Activity ID does not match its directory")
            validateStoredFiles(directory, summary.id)
            summary
        }.onFailure { error -> LOGGER.log(Level.WARNING, "Unable to load saved activity $id", error) }.getOrNull()
    }

    fun loadRoute(id: String): List<RoutePoint>? {
        val file = gpxFile(id) ?: return null
        return runCatching {
            file.inputStream().use { input ->
                gpxFileUtils.readGpx(input).trk?.trkseg?.trkPts.orEmpty().map {
                    RoutePoint(it.latitude, it.longitude)
                }.takeIf { it.isNotEmpty() }
            }
        }.onFailure { error -> LOGGER.log(Level.WARNING, "Unable to read saved route $id", error) }.getOrNull()
    }

    fun gpxFile(id: String): File? {
        val directory = activityDirectory(id) ?: return null
        val summary = loadSummary(id) ?: return null
        if (summary.id != id) return null
        return File(directory, GPX_FILENAME).takeIf { it.isFile && it.length() > 0L }
    }

    private fun activityDirectory(id: String): File? {
        if (!ID_PATTERN.matches(id)) return null
        return File(activitiesDirectory, id).takeIf { it.isDirectory }
    }

    private fun readSummary(directory: File): ActivitySummary {
        val summaryFile = File(directory, SUMMARY_FILENAME)
        if (!summaryFile.isFile) throw IOException("Activity summary is missing")
        return summaryFile.reader(Charsets.UTF_8).use { gson.fromJson(it, ActivitySummary::class.java) }
            ?: throw IOException("Activity summary is empty")
    }

    private fun validateStoredFiles(directory: File, expectedId: String) {
        val gpxFile = File(directory, GPX_FILENAME)
        if (!gpxFile.isFile || gpxFile.length() == 0L) throw IOException("Activity GPX is missing")
        val summary = readSummary(directory)
        if (summary.id != expectedId || summary.name.isBlank()) throw IOException("Activity summary is invalid")
        val route = gpxFile.inputStream().use { gpxFileUtils.readGpx(it) }.trk?.trkseg?.trkPts.orEmpty()
        if (route.isEmpty()) throw IOException("Activity GPX has no route points")
    }

    private fun copyGpxWithName(source: Gpx, name: String): Gpx = ActivityDraftFactory.snapshotGpx(source).apply {
        trk?.name = name
    }

    private fun restoreBackup(backup: File, target: File) {
        if (backup.exists()) {
            target.delete()
            if (!backup.renameTo(target)) {
                LOGGER.warning("Unable to restore activity file ${target.name} after a failed rename")
            }
        }
    }

    private companion object {
        const val ACTIVITIES_DIRECTORY = "activities"
        const val GPX_FILENAME = "activity.gpx"
        const val SUMMARY_FILENAME = "summary.json"
        const val TEMPORARY_SUFFIX = ".tmp"
        val LOGGER: Logger = Logger.getLogger(ActivityRepository::class.java.name)
        val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,80}")
    }
}
