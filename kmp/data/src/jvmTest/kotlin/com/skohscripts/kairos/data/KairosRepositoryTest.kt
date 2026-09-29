package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.coroutines.test.runTest
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.TaskRecurrence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
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
    private val today = LocalDate(2026, 9, 28) // lundi
    private val examples = { ExampleData.snapshot(LocalDate(2026, 9, 28), now, "fr") }

    private suspend fun open(file: File? = null): KairosRepository {
        val driver = JdbcSqliteDriver(if (file == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:${file.path}")
        return KairosRepository.open(KairosStore.open(driver), examples, clock, timeZone = TimeZone.UTC)
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
        repo.updateTask(
            id,
            TaskEdit(title = " ", description = "Détails", priority = 0, points = 8, deadline = LocalDate(2026, 10, 1),
                estimatedMinutes = 0, projectTag = " Kairos ", taskType = "Réunion", pinDay = today),
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

    private fun edit(title: String = "T", vararg changes: (TaskEdit) -> TaskEdit): TaskEdit =
        changes.fold(TaskEdit(title = title, pinDay = today)) { e, f -> f(e) }

    @Test
    fun completingARecurringTaskCreatesTheNextOccurrenceOnce() = runTest {
        val repo = empty()
        val id = repo.createTask("Point hebdo")!!
        repo.updateTask(id, TaskEdit("Point hebdo", priority = 1, points = 3, deadline = LocalDate(2026, 9, 24),
            recurrence = TaskRecurrence.WEEKLY, pinTime = LocalTime(10, 30), pinDay = today))
        val weekly = repo.snapshot.value.tasks.single()
        assertEquals(3, weekly.recurrenceDayOfWeek) // jeudi, jour de l'échéance
        assertEquals(LocalDateTime(2026, 9, 28, 10, 30), weekly.pinnedStart)
        repo.toggleDone(id)
        val next = repo.snapshot.value.tasks.single { it.id != id }
        assertEquals(LocalDate(2026, 10, 1), next.deadline) // jeudi suivant, pas lundi + 7
        assertEquals(next.deadline, next.scheduledDate)
        assertEquals(LocalDateTime(2026, 10, 1, 10, 30), next.pinnedStart)
        assertEquals(TaskStatus.TODO, next.status)
        assertEquals(1 to 3, next.priority to next.fibonacciPoints)
        // Rouvrir puis refaire : aucune seconde occurrence.
        repo.toggleDone(id)
        repo.toggleDone(id)
        assertEquals(2, repo.snapshot.value.tasks.size)
    }

    @Test
    fun calendarSeriesGetsThisMonthOccurrenceOnce() = runTest {
        val repo = empty()
        val id = repo.createTask("Note de frais")!!
        repo.updateTask(id, edit("Note de frais", { it.copy(recurrence = TaskRecurrence.MONTHLY_ON_DAY, recurrenceDayOfMonth = 23, deadline = LocalDate(2026, 8, 21)) }))
        repo.toggleDone(id) // une série calendaire ne se recrée pas à la complétion
        assertEquals(1, repo.snapshot.value.tasks.size)
        repo.ensureCalendarOccurrences(LocalDate(2026, 11, 2))
        repo.ensureCalendarOccurrences(LocalDate(2026, 11, 20))
        val occ = repo.snapshot.value.tasks.single { it.id != id }
        assertEquals(LocalDate(2026, 11, 23), occ.deadline)
        assertEquals("2026-11", occ.recurrencePeriod)
        assertEquals(2, repo.snapshot.value.tasks.size)
    }

    @Test
    fun snoozeSkipsWeekendAndHolidays() = runTest {
        val repo = empty()
        val id = repo.createTask("T")!!
        repo.updateTask(id, edit(changes = arrayOf({ it.copy(deadline = LocalDate(2026, 10, 30)) }))) // vendredi
        repo.snooze(id)
        // Lundi 2 novembre (le 1er, dimanche, est férié de toute façon).
        assertEquals(LocalDate(2026, 11, 2), repo.snapshot.value.tasks.single().deadline)
        repo.updateTask(id, edit(changes = arrayOf({ it.copy(deadline = null) })))
        repo.snooze(id)
        assertEquals(LocalDate(2026, 9, 29), repo.snapshot.value.tasks.single().deadline) // lendemain ouvré d'aujourd'hui
    }

    @Test
    fun blockersAreATargetSetAndCyclesAreIgnored() = runTest {
        val repo = empty()
        val a = repo.createTask("A")!!
        val b = repo.createTask("B")!!
        val c = repo.createTask("C")!!
        repo.updateTask(a, edit("A", { it.copy(blockerIds = setOf(b, c, a)) }))
        assertEquals(setOf(b, c), repo.snapshot.value.dependencies.filter { it.taskId == a }.map { it.blockerId }.toSet())
        // B bloqué par A bouclerait : ignoré, le reste de l'enregistrement passe.
        repo.updateTask(b, edit("B renommée", { it.copy(blockerIds = setOf(a)) }))
        assertEquals("B renommée", repo.snapshot.value.tasks.single { it.id == b }.title)
        assertTrue(repo.snapshot.value.dependencies.none { it.taskId == b })
        repo.updateTask(a, edit("A", { it.copy(blockerIds = setOf(c)) }))
        assertEquals(listOf(c), repo.snapshot.value.dependencies.map { it.blockerId })
    }

    @Test
    fun subtasksInBatchAndPinOnScheduledDate() = runTest {
        val repo = empty()
        val id = repo.createTask("Mère")!!
        repo.updateTask(id, edit("Mère", { it.copy(newSubtasks = "Une\n  \n Deux ", scheduledDate = LocalDate(2026, 10, 2), pinTime = LocalTime(9, 15)) }))
        val tasks = repo.snapshot.value.tasks
        assertEquals(listOf("Une", "Deux"), tasks.filter { it.parentId == id }.map { it.title })
        assertEquals(LocalDateTime(2026, 10, 2, 9, 15), tasks.single { it.id == id }.pinnedStart)
        assertNull(repo.snapshot.value.tasks.single { it.title == "Une" }.priority)
        val sub = repo.createTask("Trois", parentId = 999)!!
        assertNull(repo.snapshot.value.tasks.single { it.id == sub }.parentId)
    }

    @Test
    fun blocksAreValidatedAndEditedAsTemplates() = runTest {
        val repo = empty()
        val start = LocalDateTime(2026, 9, 28, 12, 0)
        assertTrue(!repo.createBlock(BlockEdit("Faux", start, start)))
        assertTrue(repo.createBlock(BlockEdit(" Déjeuner ", start, LocalDateTime(2026, 9, 28, 13, 0), recurrence = BlockRecurrence.DAILY)))
        val block = repo.snapshot.value.timeBlocks.single()
        assertEquals("Déjeuner", block.title)
        assertTrue(repo.updateBlock(block.id, BlockEdit("Focus", start, LocalDateTime(2026, 9, 28, 14, 0), BlockKind.DEEPWORK)))
        val edited = repo.snapshot.value.timeBlocks.single()
        assertEquals(BlockKind.DEEPWORK, edited.kind)
        assertEquals(BlockRecurrence.NONE, edited.recurrence)
        repo.deleteBlock(block.id)
        assertTrue(repo.snapshot.value.timeBlocks.isEmpty())
    }

    @Test
    fun onlyOneTimerRunsAtATime() = runTest {
        val repo = empty()
        val a = repo.createTask("A")!!
        val b = repo.createTask("B")!!
        repo.startTimer(a)
        repo.startTimer(b)
        val sessions = repo.snapshot.value.workSessions
        assertEquals(2, sessions.size)
        assertEquals(listOf(b), sessions.filter { it.endedAt == null }.map { it.taskId })
        assertEquals(now, sessions.single { it.taskId == a }.endedAt)
        repo.stopTimer()
        assertTrue(repo.snapshot.value.workSessions.all { it.endedAt != null })
        // Une tâche faite ne démarre pas de chrono.
        repo.toggleDone(a)
        repo.startTimer(a)
        assertEquals(2, repo.snapshot.value.workSessions.size)
    }

    @Test
    fun notesAreCapturedEditedConvertedArchivedAndDeleted() = runTest {
        val repo = empty()
        assertTrue(!repo.createNote("   "))
        assertTrue(repo.createNote("  Idée \n\n  - détail  "))
        val note = repo.snapshot.value.notes.single()
        assertEquals("Idée \n\n  - détail", note.body)
        repo.editNote(note.id, " ")
        assertEquals(note.body, repo.snapshot.value.notes.single().body)
        repo.editNote(note.id, "Idée\n  - détail\n  - autre")
        val taskId = assertNotNull(repo.convertNote(note.id))
        val task = repo.snapshot.value.tasks.single()
        assertEquals(taskId, task.id)
        assertEquals("Idée" to "  - détail\n  - autre", task.title to task.description)
        assertTrue(task.needsProcessing)
        val converted = repo.snapshot.value.notes.single()
        assertEquals(com.skohscripts.kairos.core.model.NoteStatus.ARCHIVED, converted.status)
        assertEquals(taskId, converted.convertedTaskId)
        assertNull(repo.convertNote(note.id)) // déjà convertie
        repo.createNote("Autre")
        val other = repo.snapshot.value.notes.single { it.body == "Autre" }
        repo.archiveNote(other.id)
        assertEquals(com.skohscripts.kairos.core.model.NoteStatus.ARCHIVED, repo.snapshot.value.notes.single { it.id == other.id }.status)
        repo.deleteNote(other.id)
        assertEquals(1, repo.snapshot.value.notes.size)
    }
}
