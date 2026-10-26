package br.com.helpdev.velocimetroalerta.activities

import br.com.helpdev.velocimetroalerta.gps.ObSpeedometerAlert
import br.com.helpdev.velocimetroalerta.gpx.GpxFileUtils
import br.com.helpdev.velocimetroalerta.gpx.objects.Gpx
import br.com.helpdev.velocimetroalerta.gpx.objects.TrkPt
import br.com.helpdev.velocimetroalerta.gps.RoutePoint
import com.google.gson.GsonBuilder
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type

class ActivityRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun savesCanonicalGpxAndSummaryAndListsNewestFirst() {
        val repository = repository()
        val earlier = draft("earlier", startedAt = 100, savedAt = 100)
        val later = draft("later", startedAt = 200, savedAt = 200)

        val saved = repository.save(earlier, "  Morning ride  ")
        repository.save(later, "Evening ride")

        assertEquals("Morning ride", saved.name)
        assertTrue(File(temporaryFolder.root, "activities/earlier/activity.gpx").isFile)
        assertTrue(File(temporaryFolder.root, "activities/earlier/summary.json").isFile)
        assertEquals(listOf("later", "earlier"), repository.list().map { it.id })
        assertEquals("Morning ride", repository.loadSummary("earlier")?.name)
        assertTrue(File(temporaryFolder.root, "activities/earlier/activity.gpx").readText().contains("-23.5,-46.6"))
    }

    @Test
    fun loadsFullRouteInOriginalOrder() {
        val repository = repository()
        repository.save(draft("route", startedAt = 100, savedAt = 100), "Route")

        assertEquals(
            listOf(RoutePoint(-23.5, -46.6), RoutePoint(-23.6, -46.7)),
            repository.loadRoute("route")
        )
    }

    @Test
    fun renameUpdatesSummaryAndGpxTrackName() {
        val repository = repository()
        repository.save(draft("rename-me", startedAt = 100, savedAt = 100), "Before")

        val renamed = repository.rename("rename-me", "  After  ")

        assertEquals("After", renamed?.name)
        assertEquals("After", repository.loadSummary("rename-me")?.name)
        assertTrue(requireNotNull(repository.gpxFile("rename-me")).readText().contains("track-name:After"))
    }

    @Test
    fun renameAndDeleteRejectInvalidOrMalformedActivityIds() {
        val repository = repository()
        repository.save(draft("keep-me", startedAt = 100, savedAt = 100), "Keep")
        val malformedDirectory = File(temporaryFolder.root, "activities/malformed")
        malformedDirectory.mkdirs()
        File(malformedDirectory, "summary.json").writeText("not json")

        assertNull(repository.rename("../keep-me", "Corrupted"))
        assertFalse(repository.delete("../keep-me"))
        assertNull(repository.rename("malformed", "Corrupted"))
        assertFalse(repository.delete("malformed"))
        assertEquals("Keep", repository.loadSummary("keep-me")?.name)
    }

    @Test
    fun deleteRemovesSavedActivityAndReportsMissingIds() {
        val repository = repository()
        repository.save(draft("delete-me", startedAt = 100, savedAt = 100), "Delete")

        assertTrue(repository.delete("delete-me"))
        assertNull(repository.loadSummary("delete-me"))
        assertFalse(repository.delete("missing"))
    }

    @Test
    fun skipsMalformedSummariesAndIncompleteDirectories() {
        val repository = repository()
        repository.save(draft("valid", startedAt = 100, savedAt = 100), "Valid")
        File(temporaryFolder.root, "activities/incomplete").mkdirs()
        File(temporaryFolder.root, "activities/broken").apply {
            mkdirs()
            resolve("activity.gpx").writeText("-23.5,-46.6")
            resolve("summary.json").writeText("not json")
        }

        assertEquals(listOf("valid"), repository.list().map { it.id })
        assertNull(repository.loadSummary("incomplete"))
        assertNull(repository.loadSummary("broken"))
    }

    @Test
    fun skipsDirectoriesWhoseNameDoesNotMatchTheStoredActivityId() {
        val repository = repository()
        repository.save(draft("actual-id", startedAt = 100, savedAt = 100), "Valid")
        val misplaced = File(temporaryFolder.root, "activities/wrong-directory")
        File(temporaryFolder.root, "activities/actual-id").renameTo(misplaced)

        assertTrue(repository.list().isEmpty())
        assertNull(repository.loadSummary("wrong-directory"))
    }

    @Test
    fun gpxWriteFailureLeavesNoFinalDirectoryAndCleansTemporaryDirectory() {
        val root = temporaryFolder.root
        val repository = ActivityRepository(root, gpxFileUtils = TextGpxFileUtils(failWrite = true))

        val failure = runCatching { repository.save(draft("failed", 100, 100), "Failed") }.exceptionOrNull()

        assertNotNull(failure)
        assertFalse(File(root, "activities/failed").exists())
        assertEquals(emptyList<File>(), File(root, "activities").listFiles()?.toList())
    }

    @Test
    fun summaryWriteFailureLeavesNoFinalDirectoryAndCleansTemporaryDirectory() {
        val root = temporaryFolder.root
        val failingGson = GsonBuilder()
            .registerTypeAdapter(ActivitySummary::class.java, JsonSerializer<ActivitySummary> { _, _: Type, _: JsonSerializationContext ->
                throw IllegalStateException("disk full while writing summary")
            })
            .create()
        val repository = ActivityRepository(root, gson = failingGson, gpxFileUtils = TextGpxFileUtils())

        val failure = runCatching { repository.save(draft("failed", 100, 100), "Failed") }.exceptionOrNull()

        assertNotNull(failure)
        assertFalse(File(root, "activities/failed").exists())
        assertEquals(emptyList<File>(), File(root, "activities").listFiles()?.toList())
    }

    private fun repository() = ActivityRepository(temporaryFolder.root, gpxFileUtils = TextGpxFileUtils())

    private fun draft(id: String, startedAt: Long, savedAt: Long): ActivityDraft {
        val alert = ObSpeedometerAlert().apply {
            dateTimeStart = java.util.Date(startedAt)
            time = 60_000
        }
        val gpx = Gpx("Test").apply {
            trk.trkseg.trkPts.add(point(-23.5, -46.6))
            trk.trkseg.trkPts.add(point(-23.6, -46.7))
        }
        val summary = ActivityDraftFactory.from(gpx, alert, "Suggested", id).summary.copy(savedAtEpochMillis = savedAt)
        return ActivityDraft(summary, gpx)
    }

    private fun point(latitude: Double, longitude: Double) = TrkPt().apply {
        setLat(latitude)
        setLon(longitude)
    }

    private class TextGpxFileUtils(private val failWrite: Boolean = false) : GpxFileUtils() {
        override fun writeGpx(gpx: Gpx, output: OutputStream) {
            if (failWrite) throw IllegalStateException("disk full while writing gpx")
            output.write("track-name:${gpx.trk?.name}\n".toByteArray())
            gpx.trk.trkseg.trkPts.forEach { point ->
                output.write("${point.latitude},${point.longitude}\n".toByteArray())
            }
        }

        override fun readGpx(input: InputStream): Gpx = Gpx("Test").apply {
            trk.trkseg.trkPts.addAll(input.bufferedReader().readLines().mapNotNull { line ->
                val values = line.split(",")
                if (values.size != 2) return@mapNotNull null
                TrkPt().apply {
                    setLat(values[0].toDouble())
                    setLon(values[1].toDouble())
                }
            })
        }
    }
}
