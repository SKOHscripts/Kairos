package com.skohscripts.kairos.data

import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.model.GuideSettings
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.DaySchedule
import com.skohscripts.kairos.core.model.ScheduleSettings
import com.skohscripts.kairos.core.model.BestHoursSettings
import com.skohscripts.kairos.core.model.WorkContext
import com.skohscripts.kairos.core.model.WorkWindow
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
    fun theGuideMemoryIsWrittenOnlyWhenUsed() = runTest {
        // Ce que le guide retient (docs/spec/visite-guidee.md § Persistance) : nul tant que rien n'est retenu.
        val repo = empty()
        assertEquals(null, repo.snapshot.value.settings.guide)
        repo.updateGuide { it }
        assertEquals(null, repo.snapshot.value.settings.guide)
        repo.updateGuide { it.copy(readChapters = it.readChapters + "PLAN") }
        repo.updateGuide { it.copy(firstTaskDone = true) }
        assertEquals(GuideSettings(readChapters = listOf("PLAN"), firstTaskDone = true), repo.snapshot.value.settings.guide)
        // Revenir aux valeurs par défaut enregistre nul, comme « Revoir le guide depuis le début ».
        repo.updateGuide { it.copy(readChapters = emptyList(), firstTaskDone = false) }
        assertEquals(null, repo.snapshot.value.settings.guide)
        repo.updateGuide { it.copy(newsHidden = "3.7") }
        repo.resetGuide()
        assertEquals(null, repo.snapshot.value.settings.guide)
    }

    @Test
    fun theGuideMemorySurvivesAnExportAndIsAbsentByDefault() {
        val now = Instant.parse("2026-10-22T07:00:00Z")
        val plain = ExportCodec.encode(KairosSnapshot(settings = Settings()), "3.7.0", now)
        assertTrue("guide" !in plain, "sans rien de retenu, l'export ne porte aucun objet guide")
        val memory = Settings(guide = GuideSettings(newsHidden = "3.7", readChapters = listOf("PLAN", "FOLLOW"), firstTaskDone = true))
        assertEquals(memory, ExportCodec.decode(ExportCodec.encode(KairosSnapshot(settings = memory), "3.7.0", now)).settings)
        // Un seul champ retenu : les autres ne sont pas écrits.
        val one = ExportCodec.encode(KairosSnapshot(settings = Settings(guide = GuideSettings(firstTaskDone = true))), "3.7.0", now)
        assertTrue("readChapters" !in one && "newsHidden" !in one)
    }

    // --- Mois sautés (docs/spec/recurrence.md § Avis des mois sautés, issue #111) ----------------------------------------------

    private fun noteDeFrais(id: Long, period: String) = Task(
        id, "Note de frais", priority = 1, fibonacciPoints = 2, recurrence = TaskRecurrence.MONTHLY_ON_DAY, recurrenceDayOfMonth = 14,
        recurrencePeriod = period, deadline = LocalDate.parse("$period-14"), createdAt = now, updatedAt = now,
    )

    @Test
    fun recreatingSkippedOccurrencesIsIdempotent() = runTest {
        val repo = empty()
        repo.replaceAll(KairosSnapshot(tasks = listOf(noteDeFrais(1, "2026-06"))))
        val created = repo.recreateSkippedOccurrences("Note de frais", 14, today)
        // Juillet et août sautés : deux occurrences, en retard, et l'avis disparaît.
        assertEquals(2, created.size)
        val made = repo.snapshot.value.tasks.filter { it.id in created }
        assertEquals(listOf("2026-07", "2026-08"), made.map { it.recurrencePeriod }.sorted())
        assertTrue(made.all { it.status == TaskStatus.TODO && it.deadline!! < today })
        assertEquals(emptyList(), com.skohscripts.kairos.core.engine.Recurrence.skippedSeries(repo.snapshot.value.tasks, today, emptyMap()))
        assertEquals(emptyList(), repo.recreateSkippedOccurrences("Note de frais", 14, today))
        // « Annuler » : les occurrences créées sont retirées.
        created.forEach { repo.deleteTask(it) }
        assertEquals(listOf(1L), repo.snapshot.value.tasks.map { it.id })
    }

    @Test
    fun ignoringASkipNoticeWritesTheWatermarkAndUndoRestoresIt() = runTest {
        val repo = empty()
        repo.replaceAll(KairosSnapshot(tasks = listOf(noteDeFrais(1, "2026-06"))))
        assertNull(repo.ignoreSkippedOccurrences("Note de frais", 14, today))
        assertEquals(mapOf("14|Note de frais" to "2026-08"), repo.snapshot.value.settings.recurrence?.skipIgnored)
        assertEquals(emptyList(), com.skohscripts.kairos.core.engine.Recurrence.skippedSeries(
            repo.snapshot.value.tasks, today, repo.snapshot.value.settings.recurrence!!.skipIgnored,
        ))
        // Plus rien de sauté : un second « Ignorer » n'écrit rien et rend le filigrane en place.
        assertEquals("2026-08", repo.ignoreSkippedOccurrences("Note de frais", 14, today))
        repo.restoreSkipNotice("Note de frais", 14, null)
        // Revenu à sa valeur par défaut : le sous-objet est de nouveau nul.
        assertNull(repo.snapshot.value.settings.recurrence)
    }

    @Test
    fun theSkipNoticeSurvivesAnExportAndIsAbsentByDefault() {
        val now = Instant.parse("2026-10-28T07:00:00Z")
        val plain = ExportCodec.encode(KairosSnapshot(settings = Settings()), "3.13.0", now)
        assertTrue("recurrence" !in plain && "skipIgnored" !in plain, "sans rien d'ignoré, l'export ne porte aucun objet recurrence")
        val memory = Settings(recurrence = com.skohscripts.kairos.core.model.RecurrenceSettings(mapOf("14|Note de frais" to "2026-08")))
        assertEquals(memory, ExportCodec.decode(ExportCodec.encode(KairosSnapshot(settings = memory), "3.13.0", now)).settings)
    }

    @Test
    fun theScheduleSurvivesAnExportAndIsAbsentWhenNotCustomised() {
        val now = Instant.parse("2026-09-29T07:00:00Z")
        val plain = ExportCodec.encode(KairosSnapshot(settings = Settings()), "3.4.0", now)
        assertTrue("schedule" !in plain, "sans horaire propre, l'export ne porte aucun objet schedule")
        val custom = Settings(schedule = ScheduleSettings(mapOf(5 to DaySchedule(startHour = 10, endHour = 14), 6 to DaySchedule(free = true))))
        val back = ExportCodec.decode(ExportCodec.encode(KairosSnapshot(settings = custom), "3.4.0", now)).settings
        assertEquals(custom, back)
    }

    @Test
    fun severalWindowsAndContextsSurviveAnExportAndTheFormerShapeGainsNoNewKey() {
        val now = Instant.parse("2026-09-29T07:00:00Z")
        // La plage unique de la 3.4 s'exporte comme avant : ni `windows` ni `contexts`.
        val legacy = Settings(schedule = ScheduleSettings(mapOf(5 to DaySchedule(startHour = 10, endHour = 14))))
        val legacyText = ExportCodec.encode(KairosSnapshot(settings = legacy), "3.5.0", now)
        assertTrue("windows" !in legacyText && "contexts" !in legacyText)
        val rich = Settings(
            schedule = ScheduleSettings(
                mapOf(5 to DaySchedule(windows = listOf(WorkWindow(9, 12, "Work"), WorkWindow(14, 17)))),
                listOf(WorkContext("Work", listOf("dev", "Réunion"))),
            ),
        )
        assertEquals(rich, ExportCodec.decode(ExportCodec.encode(KairosSnapshot(settings = rich), "3.5.0", now)).settings)
    }

    @Test
    fun theBestHoursSurviveAnExportAreAbsentByDefaultAndAreCheckedOnImport() {
        val now = Instant.parse("2026-10-19T07:00:00Z")
        val plain = ExportCodec.encode(KairosSnapshot(settings = Settings(schedule = ScheduleSettings(mapOf(5 to DaySchedule(free = true))))), "3.6.0", now)
        assertTrue("bestHours" !in plain, "sans meilleures heures, l'export n'a pas la clé")
        val on = Settings(schedule = ScheduleSettings(bestHours = BestHoursSettings(true, 2, mapOf("dev" to 9))))
        assertEquals(on, ExportCodec.decode(ExportCodec.encode(KairosSnapshot(settings = on), "3.6.0", now)).settings)
        val bad = ExportCodec.encode(KairosSnapshot(settings = Settings(schedule = ScheduleSettings(bestHours = BestHoursSettings(true, 1, mapOf("dev" to 99))))), "3.6.0", now)
        assertEquals(ImportException.Reason.CORRUPTED, assertFailsWith<ImportException> { ExportCodec.decode(bad) }.reason)
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
    fun importRefusesAnExportBreakingTheAppRulesAndAcceptsABom() {
        val task = """{"id":1,"title":"x","createdAt":"2026-09-28T07:00:00Z","updatedAt":"2026-09-28T07:00:00Z"}"""
        fun export(body: String) = """{"format":"kairos-export","formatVersion":1,$body}"""
        fun reason(text: String) = assertFailsWith<ImportException> { ExportCodec.decode(text) }.reason
        // Réglages hors bornes (une heure 25 faisait planter les vues Jour et Semaine).
        assertEquals(ImportException.Reason.CORRUPTED, reason(export(""""settings":{"workdayStartHour":25},"tasks":[]""")))
        // Identifiants en double, priorité et points inconnus, dépendance vers elle-même.
        assertEquals(ImportException.Reason.CORRUPTED, reason(export(""""tasks":[$task,$task]""")))
        assertEquals(ImportException.Reason.CORRUPTED, reason(export(""""tasks":[${task.replace("\"title\"", "\"priority\":7,\"title\"")}]""")))
        assertEquals(ImportException.Reason.CORRUPTED, reason(export(""""tasks":[${task.replace("\"title\"", "\"fibonacciPoints\":-3,\"title\"")}]""")))
        assertEquals(
            ImportException.Reason.CORRUPTED,
            reason(export(""""tasks":[$task],"dependencies":[{"id":1,"taskId":1,"blockerId":1,"createdAt":"2026-09-28T07:00:00Z"}]""")),
        )
        // Mal typé : illisible, pas « pas un export ».
        assertEquals(ImportException.Reason.CORRUPTED, reason(export(""""tasks":[{"id":"abc"}]""")))
        // Marque d'ordre des octets (Bloc-notes de Windows) : l'export reste lisible.
        assertEquals(listOf("x"), ExportCodec.decode("\uFEFF" + export(""""tasks":[$task]""")).tasks.map { it.title })
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
    fun aCalendarSeriesSavedWithoutDeadlineIsThisMonthsOccurrence() = runTest {
        val repo = empty()
        val id = repo.createTask("Loyer")!!
        repo.updateTask(id, edit("Loyer", { it.copy(recurrence = TaskRecurrence.MONTHLY_ON_DAY, recurrenceDayOfMonth = 30) }))
        val task = repo.snapshot.value.tasks.single()
        assertEquals(LocalDate(2026, 9, 30), task.deadline)
        assertEquals("2026-09", task.recurrencePeriod)
        // Pas de doublon à l'affichage de la vue Jour.
        repo.ensureCalendarOccurrences(LocalDate(2026, 9, 29))
        assertEquals(1, repo.snapshot.value.tasks.size)
    }

    @Test
    fun aCalendarSeriesWhoseDayHasPassedStartsNextMonth() = runTest {
        val repo = empty()
        val id = repo.createTask("Loyer")!!
        // Aujourd'hui : mardi 29 sept. 2026 ; le 5 est passé.
        repo.updateTask(id, edit("Loyer", { it.copy(recurrence = TaskRecurrence.MONTHLY_ON_DAY, recurrenceDayOfMonth = 5) }))
        val task = repo.snapshot.value.tasks.single()
        assertEquals(LocalDate(2026, 10, 5), task.deadline)
        assertEquals("2026-10", task.recurrencePeriod)
        repo.ensureCalendarOccurrences(LocalDate(2026, 9, 29))
        assertEquals(1, repo.snapshot.value.tasks.size)
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
    fun snoozeAlsoSchedulesAPersonalTaskOnItsNewDeadline() = runTest {
        // « Décaler » masque la tâche d'« Aujourd'hui » jusqu'à sa nouvelle échéance (docs/spec/planning-personnalise.md, lot A).
        val repo = empty()
        val id = repo.createTask("T")!!
        repo.updateTask(id, edit(changes = arrayOf({ it.copy(deadline = LocalDate(2026, 10, 30)) }))) // vendredi
        repo.snooze(id)
        val task = repo.snapshot.value.tasks.single()
        assertEquals(LocalDate(2026, 11, 2), task.deadline)
        assertEquals(LocalDate(2026, 11, 2), task.scheduledDate)
    }

    @Test
    fun scheduleMovesAPersonalTaskWithoutTouchingItsDeadline() = runTest {
        val repo = empty()
        val id = repo.createTask("T")!!
        repo.updateTask(id, edit(changes = arrayOf({ it.copy(deadline = LocalDate(2026, 10, 30)) })))
        repo.schedule(id, LocalDate(2026, 10, 5))
        val moved = repo.snapshot.value.tasks.single()
        assertEquals(LocalDate(2026, 10, 5), moved.scheduledDate)
        assertEquals(LocalDate(2026, 10, 30), moved.deadline)
        repo.schedule(id, null) // « Annuler » : la date programmée d'avant (aucune) revient
        assertEquals(null, repo.snapshot.value.tasks.single().scheduledDate)
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
    fun oneOccurrenceIsSkippedChangedOrRestoredWithoutTouchingTheSeries() = runTest {
        // Issue #110 : déjeuner quotidien depuis le lundi 28 septembre.
        val repo = empty()
        val start = LocalDateTime(2026, 9, 28, 12, 0)
        assertTrue(repo.createBlock(BlockEdit("Déjeuner", start, LocalDateTime(2026, 9, 28, 13, 0), recurrence = BlockRecurrence.DAILY)))
        val id = repo.snapshot.value.timeBlocks.single().id
        val tuesday = LocalDate(2026, 9, 29)
        assertTrue(repo.skipOccurrence(id, tuesday))
        assertTrue(repo.overrideOccurrence(id, LocalDate(2026, 9, 30), "  Déjeuner  ", LocalTime(13, 0), LocalTime(13, 30)))
        val exceptions = repo.snapshot.value.blockExceptions
        assertEquals(listOf(true, false), exceptions.map { it.skipped })
        // Titre égal à celui du modèle : rien n'est remplacé, seules les heures.
        assertNull(exceptions[1].title)
        assertEquals(LocalTime(13, 0), exceptions[1].start)
        // Le modèle n'a pas bougé.
        assertEquals(start, repo.snapshot.value.timeBlocks.single().start)
        // Refusés : fin avant début, jour d'avant l'origine, créneau inconnu.
        assertTrue(!repo.overrideOccurrence(id, tuesday, "", LocalTime(14, 0), LocalTime(13, 0)))
        assertTrue(!repo.skipOccurrence(id, LocalDate(2026, 9, 27)))
        assertTrue(!repo.skipOccurrence(999, tuesday))
        // Une seule exception par jour : remplacer le saut par des heures.
        assertTrue(repo.overrideOccurrence(id, tuesday, "Client", LocalTime(12, 30), LocalTime(14, 0)))
        assertEquals(2, repo.snapshot.value.blockExceptions.size)
        assertEquals("Client", repo.snapshot.value.blockExceptions.single { it.date == tuesday }.title)
        // Rétablir, puis remettre l'exception d'avant (annulation).
        val before = repo.snapshot.value.blockExceptions.single { it.date == tuesday }
        repo.restoreOccurrence(id, tuesday)
        assertEquals(1, repo.snapshot.value.blockExceptions.size)
        repo.putOccurrenceException(id, tuesday, before)
        assertEquals("Client", repo.snapshot.value.blockExceptions.single { it.date == tuesday }.title)
        // Toute la série modifiée : les exceptions restent ; devenue ponctuelle, elles partent.
        assertTrue(repo.updateBlock(id, BlockEdit("Repas", start, LocalDateTime(2026, 9, 28, 13, 0), recurrence = BlockRecurrence.WEEKDAYS)))
        assertEquals(2, repo.snapshot.value.blockExceptions.size)
        assertTrue(repo.updateBlock(id, BlockEdit("Repas", start, LocalDateTime(2026, 9, 28, 13, 0))))
        assertTrue(repo.snapshot.value.blockExceptions.isEmpty())
    }

    @Test
    fun deletingASeriesDeletesItsExceptionsAndExportsRoundTripThem() = runTest {
        val repo = empty()
        val start = LocalDateTime(2026, 9, 28, 9, 30)
        repo.createBlock(BlockEdit("Revue", start, LocalDateTime(2026, 9, 28, 10, 0), recurrence = BlockRecurrence.MONTHLY_WEEKDAY))
        repo.createBlock(BlockEdit("Stand-up", start, LocalDateTime(2026, 9, 28, 9, 45), recurrence = BlockRecurrence.MONTHLY_DAY))
        // Sans exception, l'export n'en dit rien : celui d'une base qui n'en a jamais posé ne change pas.
        val plain = ExportCodec.encode(repo.snapshot.value, "3.8.0", now)
        assertTrue("blockExceptions" !in plain)
        val (review, standUp) = repo.snapshot.value.timeBlocks.map { it.id }
        // 4e lundi d'octobre 2026 : le 26 ; le 28 octobre pour « le 28 du mois ».
        assertTrue(repo.skipOccurrence(review, LocalDate(2026, 10, 26)))
        assertTrue(repo.overrideOccurrence(standUp, LocalDate(2026, 10, 28), "Stand-up long", LocalTime(9, 30), LocalTime(10, 30)))
        val text = ExportCodec.encode(repo.snapshot.value, "3.8.0", now)
        assertTrue("\"monthly_weekday\"" in text && "\"monthly_day\"" in text)
        val copy = empty()
        copy.replaceAll(ExportCodec.decode(text))
        assertEquals(repo.snapshot.value.blockExceptions, copy.snapshot.value.blockExceptions)
        assertEquals(repo.snapshot.value.timeBlocks, copy.snapshot.value.timeBlocks)
        repo.deleteBlock(review)
        assertEquals(listOf(standUp), repo.snapshot.value.blockExceptions.map { it.blockId })
    }

    @Test
    fun correctingEstimatesWritesFinishedPersonalTasksOnlyAndKeepsTheirCompletionDate() = runTest {
        // Issue #101 : action explicite, annulable ; `updatedAt` (date de fin) ne bouge pas.
        val repo = empty()
        val done = Instant.parse("2026-09-20T10:00:00Z")
        repo.replaceAll(
            KairosSnapshot(
                tasks = listOf(
                    Task(1, "Faite", estimatedMinutes = 30, status = TaskStatus.DONE, createdAt = done, updatedAt = done),
                    Task(2, "À faire", estimatedMinutes = 30, createdAt = done, updatedAt = done),
                    Task(3, "Équipe", estimatedMinutes = 30, status = TaskStatus.DONE, space = TaskSpace.TEAM, createdAt = done, updatedAt = done),
                ),
            ),
        )
        assertEquals(1, repo.setEstimates(mapOf(1L to 45, 2L to 45, 3L to 45, 99L to 45)))
        val tasks = repo.snapshot.value.tasks.associateBy { it.id }
        assertEquals(45, tasks.getValue(1).estimatedMinutes)
        assertEquals(done, tasks.getValue(1).updatedAt)
        assertEquals(30, tasks.getValue(2).estimatedMinutes)
        assertEquals(30, tasks.getValue(3).estimatedMinutes)
        // Annulation : les valeurs d'avant, « sans estimation » compris.
        assertEquals(1, repo.setEstimates(mapOf(1L to null)))
        assertNull(repo.snapshot.value.tasks.single { it.id == 1L }.estimatedMinutes)
        assertEquals(0, repo.setEstimates(emptyMap()))
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
