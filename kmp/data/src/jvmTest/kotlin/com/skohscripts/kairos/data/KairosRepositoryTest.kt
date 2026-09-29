package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class KairosRepositoryTest {
    private val now = Instant.parse("2026-09-28T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val examples = { ExampleData.snapshot(LocalDate(2026, 9, 28), now, "fr") }

    private suspend fun open(file: File? = null): KairosRepository {
        val driver = JdbcSqliteDriver(if (file == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:${file.path}")
        return KairosRepository.open(KairosStore.open(driver), examples, clock)
    }

    private suspend fun empty(): KairosRepository = open().also { it.replaceAll(KairosSnapshot()) }

    @Test
    fun newDatabaseGetsTheExamplesOnceOnly() = runTest {
        val file = Files.createTempFile("kairos", ".db").toFile().also { it.delete() }
        val first = open(file)
        assertEquals(10, first.snapshot.value.tasks.size)
        // Même si l'utilisateur supprime tous les exemples, la réouverture ne ressème pas.
        first.snapshot.value.tasks.forEach { first.deleteTask(it.id) }
        val second = open(file)
        assertTrue(second.snapshot.value.tasks.isEmpty())
    }

    @Test
    fun captureCreatesAnUnqualifiedTask() = runTest {
        val repo = empty()
        assertNull(repo.createTask("   "))
        val id = assertNotNull(repo.createTask("  Appeler le client  "))
        val task = repo.snapshot.value.tasks.single { it.id == id }
        assertEquals("Appeler le client", task.title)
        assertEquals(TaskStatus.TODO, task.status)
        assertTrue(task.needsProcessing)
        assertEquals(now, task.createdAt)
    }

    @Test
    fun qualificationIgnoresValuesOutsideTheScales() = runTest {
        val repo = empty()
        val id = repo.createTask("T")!!
        repo.setPriority(id, 1)
        repo.setPoints(id, 5)
        assertTrue(!repo.snapshot.value.tasks.single().needsProcessing)
        repo.setPriority(id, 7)
        repo.setPoints(id, 4)
        val task = repo.snapshot.value.tasks.single()
        assertNull(task.priority)
        assertNull(task.fibonacciPoints)
    }

    @Test
    fun doneClosesTheRunningTimerAndCanBeUndone() = runTest {
        val repo = empty()
        val id = repo.createTask("T")!!
        val started = Instant.parse("2026-09-28T06:00:00Z")
        repo.replaceAll(
            repo.snapshot.value.copy(workSessions = listOf(WorkSession(1, id, started, null, started))),
        )
        repo.toggleDone(id)
        assertEquals(TaskStatus.DONE, repo.snapshot.value.tasks.single().status)
        assertEquals(now, repo.snapshot.value.workSessions.single().endedAt)
        repo.toggleDone(id)
        assertEquals(TaskStatus.TODO, repo.snapshot.value.tasks.single().status)
    }

    @Test
    fun archivedTasksAreNeverToggled() = runTest {
        val repo = empty()
        val id = repo.createTask("T")!!
        val archived = repo.snapshot.value.tasks.single().copy(status = TaskStatus.ARCHIVED)
        repo.replaceAll(repo.snapshot.value.copy(tasks = listOf(archived)))
        repo.toggleDone(id)
        assertEquals(TaskStatus.ARCHIVED, repo.snapshot.value.tasks.single().status)
    }

    @Test
    fun deleteRemovesItsDependenciesButKeepsSubtasks() = runTest {
        val repo = open()
        val s = repo.snapshot.value
        val dep = s.dependencies.single()
        val parent = s.tasks.single { it.title.endsWith("Rédiger la documentation") }
        repo.deleteTask(dep.blockerId)
        repo.deleteTask(parent.id)
        val after = repo.snapshot.value
        assertTrue(after.dependencies.isEmpty())
        assertEquals(2, after.tasks.count { it.parentId == parent.id })
    }

    @Test
    fun essentialsEditKeepsTheTitleWhenBlank() = runTest {
        val repo = empty()
        val id = repo.createTask("Titre")!!
        repo.updateEssentials(
            id,
            TaskEdit(title = " ", description = "Détails", priority = 0, points = 8, deadline = LocalDate(2026, 10, 1),
                estimatedMinutes = 0, projectTag = " Kairos ", taskType = "Réunion"),
        )
        val t = repo.snapshot.value.tasks.single()
        assertEquals("Titre", t.title)
        assertEquals("Détails", t.description)
        assertEquals(0, t.priority)
        assertEquals(8, t.fibonacciPoints)
        assertEquals(LocalDate(2026, 10, 1), t.deadline)
        assertNull(t.estimatedMinutes)
        assertEquals("Kairos", t.projectTag)
    }

    @Test
    fun exportThenImportGivesBackTheSameDatabase() = runTest {
        val source = open()
        source.createTask("Une de plus")
        source.updateSettings(source.snapshot.value.settings.copy(meetingBufferMinutes = 10))
        val text = ExportCodec.encode(source.snapshot.value, "3.0.0-alpha.2", now)
        val target = empty()
        target.replaceAll(ExportCodec.decode(text))
        assertEquals(source.snapshot.value, target.snapshot.value)
        // Les identifiants continuent après le plus grand importé.
        val newId = target.createTask("Après import")!!
        assertTrue(newId > source.snapshot.value.tasks.maxOf { it.id })
    }

    @Test
    fun importRejectsWhatIsNotAKairosExport() {
        assertEquals(ImportException.Reason.NOT_AN_EXPORT, assertFailsWith<ImportException> { ExportCodec.decode("pas du json") }.reason)
        assertEquals(ImportException.Reason.NOT_AN_EXPORT, assertFailsWith<ImportException> { ExportCodec.decode("""{"format":"autre","formatVersion":1}""") }.reason)
        assertEquals(ImportException.Reason.TOO_NEW, assertFailsWith<ImportException> { ExportCodec.decode("""{"format":"kairos-export","formatVersion":99}""") }.reason)
        val bad = """{"format":"kairos-export","formatVersion":1,"tasks":[{"id":1,"title":"x","deadline":"2026-13-45","createdAt":"2026-09-28T07:00:00Z","updatedAt":"2026-09-28T07:00:00Z"}]}"""
        assertEquals(ImportException.Reason.CORRUPTED, assertFailsWith<ImportException> { ExportCodec.decode(bad) }.reason)
    }

    @Test
    fun missingSettingsTakeTheirDefaults() {
        val text = """{"format":"kairos-export","formatVersion":1,"settings":{"meetingBufferMinutes":12,"inconnu":true}}"""
        val snapshot = ExportCodec.decode(text)
        assertEquals(12, snapshot.settings.meetingBufferMinutes)
        assertEquals(Settings().workdayStartHour, snapshot.settings.workdayStartHour)
    }

    @Test
    fun changesAreReportedToTheListener() = runTest {
        val seen = mutableListOf<Int>()
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val repo = KairosRepository.open(KairosStore.open(driver), { KairosSnapshot() }, clock) { seen += it.tasks.size }
        repo.createTask("A")
        repo.createTask("B")
        assertEquals(listOf(0, 1, 2), seen)
    }
}
