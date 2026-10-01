package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.QualifiedField
import com.skohscripts.kairos.core.team.TeamEvent
import com.skohscripts.kairos.core.team.TeamEventKind
import com.skohscripts.kairos.core.team.TeamEventSource
import com.skohscripts.kairos.core.team.TeamEvents
import com.skohscripts.kairos.core.team.TeamMember
import com.skohscripts.kairos.core.team.Workspaces
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Espace Équipe, jalon E3 (docs/spec/equipe-backlog-suivi.md) : tâches d'équipe, journal, export et migration 3 -> 4. */
class TeamRepositoryTest {
    private val now = Instant.parse("2026-09-28T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val day = LocalDate(2026, 9, 28)
    private val examples = { ExampleData.snapshot(day, now, "fr") }

    private fun driver(file: File? = null) =
        JdbcSqliteDriver(if (file == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:${file.path}")

    private suspend fun open(file: File? = null): KairosRepository =
        KairosRepository.open(KairosStore.open(driver(file)), examples, clock, timeZone = TimeZone.UTC)

    /** Base vide, espace Équipe activé, trois membres : Alex, Bea et « moi ». */
    private class Fixture(val repo: KairosRepository, val alex: Long, val bea: Long, val me: Long) {
        val events get() = repo.snapshot.value.teamEvents
        fun task(id: Long) = repo.snapshot.value.tasks.single { it.id == id }
        fun eventsOf(id: Long) = events.filter { it.taskId == id }
    }

    private suspend fun fixture(): Fixture {
        val repo = open()
        repo.replaceAll(KairosSnapshot(settings = Settings(team = TeamSettings(enabled = true, identity = "id-1"))))
        val alex = repo.createMember("Alex", "", 100, 7.0, false)!!
        val bea = repo.createMember("Bea", "", 100, 7.0, false)!!
        val me = repo.createMember("Moi", "", 100, 7.0, true)!!
        return Fixture(repo, alex, bea, me)
    }

    private fun assigned(e: TeamEvent) = e.fromValue to e.toValue

    // --- Création ---------------------------------------------------------------------

    @Test
    fun createTeamTaskLandsInTheBacklogWithAnIdentityAndACreatedEvent() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("  Migrer la base  ")!!
        val t = f.task(id)
        assertEquals("Migrer la base", t.title)
        assertEquals(TaskSpace.TEAM, t.space)
        assertNull(t.assigneeId)
        assertNull(t.startedOn)
        assertNull(t.progressPercent)
        assertTrue(t.teamUid!!.length >= 32)
        assertTrue(t.needsProcessing)
        val e = f.events.single()
        assertEquals(TeamEvent(e.id, id, "Migrer la base", null, TeamEventKind.CREATED, null, "Migrer la base", TeamEventSource.MANUAL, now), e)
        // Deux tâches : deux identités.
        assertNotEquals(t.teamUid, f.task(f.repo.createTeamTask("Autre")!!).teamUid)
        // Hors vue Perso.
        assertTrue(f.repo.personalSnapshot.value.tasks.none { it.space == TaskSpace.TEAM })
        assertNull(f.repo.createTeamTask("   "))
        assertEquals(2, f.events.size)
    }

    @Test
    fun aTeamSubtaskTakesTheAssigneeOfItsMother() = runTest {
        val f = fixture()
        val mother = f.repo.createTeamTask("Mère")!!
        f.repo.assign(listOf(mother), f.alex)
        val child = f.repo.createTeamTask("Fille", mother)!!
        assertEquals(mother, f.task(child).parentId)
        assertEquals(f.alex, f.task(child).assigneeId)
        assertEquals(f.alex, f.eventsOf(child).single().memberId) // l'événement created porte l'assigné
        // Une mère Perso ou inconnue est ignorée : la tâche naît au premier niveau.
        val personal = f.repo.createTask("Perso")!!
        assertNull(f.task(f.repo.createTeamTask("A", personal)!!).parentId)
        assertNull(f.task(f.repo.createTeamTask("B", 999)!!).parentId)
    }

    // --- Assignation ------------------------------------------------------------------------

    @Test
    fun reassignmentIsJournaled() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        assertEquals(1, f.repo.assign(listOf(id), f.alex))
        assertEquals(1, f.repo.assign(listOf(id), f.bea))
        assertEquals(1, f.repo.assign(listOf(id), null))

        val events = f.eventsOf(id).filter { it.kind == TeamEventKind.ASSIGNED }
        assertEquals(3, events.size)
        assertEquals(
            listOf(null to "${f.alex}", "${f.alex}" to "${f.bea}", "${f.bea}" to null),
            events.map(::assigned),
        )
        assertEquals(listOf<Long?>(f.alex, f.bea, null), events.map { it.memberId })
        assertTrue(events.all { it.source == TeamEventSource.MANUAL && it.taskTitle == "T" && it.at == now })
        assertEquals(listOf(TeamEventKind.CREATED, TeamEventKind.ASSIGNED, TeamEventKind.ASSIGNED, TeamEventKind.ASSIGNED), f.eventsOf(id).map { it.kind })
        assertNull(f.task(id).assigneeId)
    }

    @Test
    fun anArchivedOrUnknownMemberIsRefusedAndNothingIsWritten() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        f.repo.assign(listOf(id), f.alex)
        f.repo.archiveMember(f.bea)
        val before = f.events
        assertEquals(0, f.repo.assign(listOf(id), f.bea))
        assertEquals(0, f.repo.assign(listOf(id), 999))
        assertEquals(f.alex, f.task(id).assigneeId)
        assertEquals(before, f.events)
    }

    @Test
    fun assigningToTheSameMemberOrToIneligibleTasksWritesNothing() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        f.repo.assign(listOf(id), f.alex)
        val done = f.repo.createTeamTask("Faite")!!
        f.repo.toggleDone(done)
        val personal = f.repo.createTask("Perso")!!
        val count = f.events.size
        assertEquals(0, f.repo.assign(listOf(id), f.alex)) // déjà chez lui
        assertEquals(0, f.repo.assign(listOf(done, personal, 4242), f.bea)) // faite, Perso, inconnue
        assertEquals(count, f.events.size)
        assertNull(f.task(personal).assigneeId)
        assertNull(f.task(done).assigneeId)
        // Plusieurs tâches d'un coup : un événement par tâche réellement changée.
        val b = f.repo.createTeamTask("B")!!
        assertEquals(1, f.repo.assign(listOf(id, b, b), f.alex)) // id déjà chez Alex ; b donné une fois
        assertEquals(count + 2, f.events.size) // created de b + assigned de b
    }

    @Test
    fun aReassignmentKeepsProgressAndOnlyKeepsInProgressOnRequest() = runTest {
        val f = fixture()
        val a = f.repo.createTeamTask("A")!!
        val b = f.repo.createTeamTask("B")!!
        f.repo.assign(listOf(a, b), f.alex)
        f.repo.setProgress(a, 40)
        f.repo.setProgress(b, 20)
        assertNotNull(f.task(a).startedOn)

        f.repo.assign(listOf(a), f.bea, keepInProgress = false)
        assertNull(f.task(a).startedOn) // « À faire » chez le nouveau titulaire
        assertEquals(40, f.task(a).progressPercent) // l'avancement est gardé

        f.repo.assign(listOf(b), f.bea, keepInProgress = true)
        assertEquals(day, f.task(b).startedOn) // « En cours » gardé
        assertEquals(20, f.task(b).progressPercent)

        // Retour au backlog : jamais « en cours ».
        f.repo.assign(listOf(b), null, keepInProgress = true)
        assertNull(f.task(b).startedOn)
    }

    // --- Commencer, avancement ----------------------------------------------------------------

    @Test
    fun startTeamTaskSetsTodayOnceAndJournals() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        assertFalse(f.repo.startTeamTask(id)) // sans assigné : refusé
        f.repo.assign(listOf(id), f.alex)
        assertTrue(f.repo.startTeamTask(id))
        assertEquals(day, f.task(id).startedOn)
        val started = f.eventsOf(id).last()
        assertEquals(TeamEventKind.STARTED, started.kind)
        assertEquals("2026-09-28", started.toValue)
        assertEquals(f.alex, started.memberId)
        val count = f.events.size
        assertFalse(f.repo.startTeamTask(id)) // déjà commencée : rien
        assertFalse(f.repo.startTeamTask(4242))
        assertEquals(count, f.events.size)
    }

    @Test
    fun setProgressRoundsToTenAndStartsTheTask() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        assertFalse(f.repo.setProgress(id, 50)) // sans assigné
        f.repo.assign(listOf(id), f.alex)

        assertTrue(f.repo.setProgress(id, 44))
        assertEquals(40, f.task(id).progressPercent)
        assertEquals(day, f.task(id).startedOn) // > 0 commence la tâche
        val events = f.eventsOf(id).drop(2) // created, assigned
        assertEquals(listOf(TeamEventKind.STARTED, TeamEventKind.PROGRESS), events.map { it.kind })
        assertEquals("0" to "40", events[1].fromValue to events[1].toValue)

        assertTrue(f.repo.setProgress(id, 45))
        assertEquals(50, f.task(id).progressPercent)
        assertTrue(f.repo.setProgress(id, 250))
        assertEquals(100, f.task(id).progressPercent)
        assertTrue(f.repo.setProgress(id, -8))
        assertEquals(0, f.task(id).progressPercent)
        val last = f.eventsOf(id).last()
        assertEquals("100" to "0", last.fromValue to last.toValue)
        // Un seul événement `started`.
        assertEquals(1, f.eventsOf(id).count { it.kind == TeamEventKind.STARTED })
    }

    @Test
    fun setProgressWithoutChangeWritesNothing() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        f.repo.assign(listOf(id), f.alex)
        val count = f.events.size
        assertFalse(f.repo.setProgress(id, 0)) // 0 sur une tâche sans avancement : pas de changement, pas de début
        assertFalse(f.repo.setProgress(id, 3)) // arrondi à 0
        assertNull(f.task(id).startedOn)
        assertNull(f.task(id).progressPercent)
        assertEquals(count, f.events.size)
        f.repo.setProgress(id, 30)
        val after = f.events.size
        assertFalse(f.repo.setProgress(id, 34)) // arrondi à 30 : inchangé
        assertEquals(after, f.events.size)
    }

    @Test
    fun aTaskStartedByTheTimerOrByProgressIsInProgress() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        f.repo.assign(listOf(id), f.me)
        f.repo.startTimer(id)
        assertEquals(day, f.task(id).startedOn)
        val started = f.eventsOf(id).last()
        assertEquals(TeamEventKind.STARTED, started.kind)
        assertEquals(TeamEventSource.SELF, started.source) // assignée à « moi », lancée depuis l'espace Perso
        val count = f.events.size
        f.repo.stopTimer()
        f.repo.startTimer(id) // déjà commencée : rien de plus
        assertEquals(count, f.events.size)
        // Une tâche d'équipe non assignée ne se « commence » pas par le chrono.
        val backlog = f.repo.createTeamTask("B")!!
        f.repo.startTimer(backlog)
        assertNull(f.task(backlog).startedOn)
    }

    // --- Fin, réouverture, suppression -------------------------------------------------------------

    @Test
    fun doneSetsHundredPercentAndReopenRestoresTheProgress() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        f.repo.assign(listOf(id), f.alex)
        f.repo.setProgress(id, 60)

        f.repo.toggleDone(id)
        assertEquals(TaskStatus.DONE, f.task(id).status)
        assertEquals(100, f.task(id).progressPercent)
        val done = f.eventsOf(id).last()
        assertEquals(TeamEventKind.DONE, done.kind)
        assertEquals("60" to "100", done.fromValue to done.toValue)

        f.repo.toggleDone(id)
        assertEquals(TaskStatus.TODO, f.task(id).status)
        assertEquals(60, f.task(id).progressPercent)
        assertEquals(day, f.task(id).startedOn) // toujours en cours
        val reopened = f.eventsOf(id).last()
        assertEquals(TeamEventKind.REOPENED, reopened.kind)
        assertEquals("100" to "60", reopened.fromValue to reopened.toValue)
    }

    @Test
    fun reopeningATaskFinishedWithoutProgressRestoresNoProgress() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        f.repo.toggleDone(id)
        assertEquals(100, f.task(id).progressPercent)
        assertNull(f.eventsOf(id).last().fromValue)
        f.repo.toggleDone(id)
        assertNull(f.task(id).progressPercent)
    }

    @Test
    fun deletionKeepsTheEventsAndCopiesTheTitle() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("À supprimer")!!
        f.repo.assign(listOf(id), f.alex)
        f.repo.deleteTask(id)
        assertTrue(f.repo.snapshot.value.tasks.none { it.id == id })
        val events = f.eventsOf(id)
        assertEquals(listOf(TeamEventKind.CREATED, TeamEventKind.ASSIGNED, TeamEventKind.DELETED), events.map { it.kind })
        assertTrue(events.all { it.taskTitle == "À supprimer" })
        assertEquals(f.alex, events.last().memberId)
    }

    // --- Qualification --------------------------------------------------------------------------------

    @Test
    fun qualificationChangesAreJournaledFieldByField() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        f.repo.setPriority(id, 1)
        f.repo.setPoints(id, 5)
        f.repo.setPriority(id, 0)
        f.repo.updateTask(id, TaskEdit(title = "T", priority = 0, points = 5, taskType = "Dev", deadline = LocalDate(2026, 10, 3), pinDay = day))
        val q = f.eventsOf(id).filter { it.kind == TeamEventKind.QUALIFIED }
        fun pair(e: TeamEvent) = TeamEvents.parseQualified(e.fromValue)!!.let { (field, from) -> Triple(field, from, TeamEvents.parseQualified(e.toValue)!!.second) }
        assertEquals(
            listOf(
                Triple(QualifiedField.PRIORITY, "", "1"),
                Triple(QualifiedField.POINTS, "", "5"),
                Triple(QualifiedField.PRIORITY, "1", "0"),
                Triple(QualifiedField.TYPE, "", "Dev"),
                Triple(QualifiedField.DEADLINE, "", "2026-10-03"),
            ),
            q.map(::pair),
        )
    }

    @Test
    fun snoozeIsAQualificationOfTheDeadline() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        f.repo.updateTask(id, TaskEdit(title = "T", deadline = LocalDate(2026, 9, 28), pinDay = day))
        f.repo.snooze(id)
        val last = f.eventsOf(id).last()
        assertEquals(TeamEventKind.QUALIFIED, last.kind)
        assertEquals("deadline:2026-09-28" to "deadline:2026-09-29", last.fromValue to last.toValue)
    }

    @Test
    fun aModificationWithoutRealChangeWritesNoEvent() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        f.repo.assign(listOf(id), f.alex)
        f.repo.updateTask(id, TaskEdit(title = "T", priority = 1, points = 3, taskType = "Dev", deadline = LocalDate(2026, 10, 3), pinDay = day))
        val count = f.events.size
        // Mêmes valeurs, titre et description changés (hors journal).
        f.repo.updateTask(id, TaskEdit(title = "T renommée", description = "notes", priority = 1, points = 3, taskType = "Dev", deadline = LocalDate(2026, 10, 3), pinDay = day))
        f.repo.setPriority(id, 1)
        f.repo.setPoints(id, 3)
        f.repo.updateTask(id, TaskEdit(title = "T renommée", priority = 1, points = 3, taskType = "Dev", deadline = LocalDate(2026, 10, 3), pinDay = day, reassign = Reassignment(f.alex)))
        assertEquals(count, f.events.size)
        assertEquals("T renommée", f.task(id).title)
    }

    @Test
    fun anEditCanReassignAndIsJournaled() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        f.repo.assign(listOf(id), f.alex)
        f.repo.setProgress(id, 30)
        val edit = TaskEdit(title = "T", pinDay = day, reassign = Reassignment(f.bea, keepInProgress = true))
        f.repo.updateTask(id, edit)
        assertEquals(f.bea, f.task(id).assigneeId)
        assertEquals(day, f.task(id).startedOn)
        val e = f.eventsOf(id).last()
        assertEquals(TeamEventKind.ASSIGNED, e.kind)
        assertEquals("${f.alex}" to "${f.bea}", e.fromValue to e.toValue)
        // Sans « garder », l'état repasse à « À faire » ; un membre archivé est refusé sans bloquer le reste.
        f.repo.updateTask(id, edit.copy(reassign = Reassignment(f.alex)))
        assertNull(f.task(id).startedOn)
        f.repo.archiveMember(f.bea)
        f.repo.updateTask(id, edit.copy(description = "ok", reassign = Reassignment(f.bea)))
        assertEquals("ok", f.task(id).description)
        assertEquals(f.alex, f.task(id).assigneeId)
        // Retour au backlog par l'édition.
        f.repo.updateTask(id, edit.copy(reassign = Reassignment(null)))
        assertNull(f.task(id).assigneeId)
        // Sans effet sur une tâche Perso.
        val personal = f.repo.createTask("Perso")!!
        f.repo.updateTask(personal, TaskEdit(title = "Perso", pinDay = day, reassign = Reassignment(f.alex)))
        assertNull(f.task(personal).assigneeId)
    }

    // --- Source -----------------------------------------------------------------------------------------

    @Test
    fun theSourceIsSelfForATaskAssignedToMeAndManualOtherwise() = runTest {
        val f = fixture()
        val mine = f.repo.createTeamTask("Mienne")!!
        val theirs = f.repo.createTeamTask("Leur")!!
        val backlog = f.repo.createTeamTask("Backlog")!!
        f.repo.assign(listOf(mine), f.me)
        f.repo.assign(listOf(theirs), f.alex)

        for (id in listOf(mine, theirs, backlog)) {
            f.repo.setPriority(id, 0)
            f.repo.setPoints(id, 2)
            f.repo.toggleDone(id)
        }
        val sources = { id: Long -> f.eventsOf(id).filter { it.kind != TeamEventKind.CREATED && it.kind != TeamEventKind.ASSIGNED }.map { it.source }.toSet() }
        assertEquals(setOf(TeamEventSource.SELF), sources(mine))
        assertEquals(setOf(TeamEventSource.MANUAL), sources(theirs))
        assertEquals(setOf(TeamEventSource.MANUAL), sources(backlog))

        // Une source explicite l'emporte (écran d'équipe, rapport).
        f.repo.toggleDone(mine, TeamEventSource.MANUAL)
        assertEquals(TeamEventSource.MANUAL, f.eventsOf(mine).last().source)
        f.repo.setPriority(mine, 2, TeamEventSource.REPORT)
        assertEquals(TeamEventSource.REPORT, f.eventsOf(mine).last().source)
        f.repo.deleteTask(mine)
        assertEquals(TeamEventKind.DELETED, f.eventsOf(mine).last().kind)
        assertEquals(TeamEventSource.SELF, f.eventsOf(mine).last().source)
    }

    @Test
    fun anArchivedSelfMemberIsNotMe() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        f.repo.assign(listOf(id), f.me)
        f.repo.archiveMember(f.me) // le membre n'est plus « moi », la tâche repart au backlog
        f.repo.setPriority(id, 0)
        assertEquals(TeamEventSource.MANUAL, f.eventsOf(id).last().source)
    }

    // --- Récurrence, sous-tâches ------------------------------------------------------------------------

    @Test
    fun aRecurringTeamTaskSpawnsANewIdentityAndACreatedEvent() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("Point hebdo")!!
        f.repo.updateTask(id, TaskEdit(title = "Point hebdo", priority = 1, points = 2, deadline = day, recurrence = TaskRecurrence.DAILY, pinDay = day))
        f.repo.assign(listOf(id), f.alex)
        f.repo.setProgress(id, 50)
        val uid = f.task(id).teamUid

        f.repo.toggleDone(id)
        val next = f.repo.snapshot.value.tasks.single { it.status == TaskStatus.TODO }
        assertNotEquals(id, next.id)
        assertEquals(TaskSpace.TEAM, next.space)
        assertEquals(f.alex, next.assigneeId)
        assertNotNull(next.teamUid)
        assertNotEquals(uid, next.teamUid)
        assertNull(next.progressPercent)
        assertNull(next.startedOn)
        val created = f.eventsOf(next.id).single()
        assertEquals(TeamEventKind.CREATED, created.kind)
        assertEquals(f.alex, created.memberId)
        // Et la tâche terminée garde sa propre identité et son journal.
        assertEquals(uid, f.task(id).teamUid)
        assertEquals(TeamEventKind.DONE, f.eventsOf(id).last().kind)
    }

    @Test
    fun calendarOccurrencesOfATeamSeriesAreTeamTasksWithTheirOwnIdentity() = runTest {
        val f = fixture()
        val seed = f.repo.createTeamTask("Revue mensuelle")!!
        f.repo.updateTask(
            seed,
            TaskEdit(title = "Revue mensuelle", priority = 1, points = 3, deadline = LocalDate(2026, 8, 5), recurrence = TaskRecurrence.MONTHLY_ON_DAY, recurrenceDayOfMonth = 5, pinDay = day),
        )
        f.repo.assign(listOf(seed), f.alex)
        // Une série Perso de même titre et même jour : distincte.
        val personal = f.repo.createTask("Revue mensuelle")!!
        f.repo.updateTask(personal, TaskEdit(title = "Revue mensuelle", priority = 1, points = 3, deadline = LocalDate(2026, 8, 5), recurrence = TaskRecurrence.MONTHLY_ON_DAY, recurrenceDayOfMonth = 5, pinDay = day))

        f.repo.ensureCalendarOccurrences(day)
        val created = f.repo.snapshot.value.tasks.filter { it.id > personal }
        assertEquals(setOf(TaskSpace.TEAM to f.alex, TaskSpace.PERSONAL to null), created.map { it.space to it.assigneeId }.toSet())
        val team = created.single { it.space == TaskSpace.TEAM }
        assertNotNull(team.teamUid)
        assertNotEquals(f.task(seed).teamUid, team.teamUid)
        assertEquals(TeamEventKind.CREATED, f.eventsOf(team.id).single().kind)
        assertNull(created.single { it.space == TaskSpace.PERSONAL }.teamUid)
        // Idempotent.
        val count = f.repo.snapshot.value.tasks.size
        f.repo.ensureCalendarOccurrences(day)
        assertEquals(count, f.repo.snapshot.value.tasks.size)
    }

    @Test
    fun batchSubtasksTakeTheSpaceAndAssigneeOfTheirMother() = runTest {
        val f = fixture()
        val mother = f.repo.createTeamTask("Mère")!!
        f.repo.assign(listOf(mother), f.alex)
        f.repo.updateTask(mother, TaskEdit(title = "Mère", pinDay = day, newSubtasks = "Un\n\n  Deux  \nTrois"))
        val kids = f.repo.snapshot.value.tasks.filter { it.parentId == mother }
        assertEquals(listOf("Un", "Deux", "Trois"), kids.map { it.title })
        assertTrue(kids.all { it.space == TaskSpace.TEAM && it.assigneeId == f.alex })
        assertEquals(3, kids.map { it.teamUid }.toSet().size)
        assertTrue(kids.all { k -> f.eventsOf(k.id).map { it.kind } == listOf(TeamEventKind.CREATED) })
        // Avec une réaffectation dans la même édition, les filles suivent le nouvel assigné.
        f.repo.updateTask(mother, TaskEdit(title = "Mère", pinDay = day, newSubtasks = "Quatre", reassign = Reassignment(f.bea)))
        assertEquals(f.bea, f.repo.snapshot.value.tasks.single { it.title == "Quatre" }.assigneeId)
    }

    @Test
    fun batchSubtasksOfAPersonalMotherStayPersonalAndSilent() = runTest {
        val f = fixture()
        val mother = f.repo.createTask("Mère perso")!!
        f.repo.updateTask(mother, TaskEdit(title = "Mère perso", pinDay = day, newSubtasks = "A\nB"))
        val kids = f.repo.snapshot.value.tasks.filter { it.parentId == mother }
        assertEquals(2, kids.size)
        assertTrue(kids.all { it.space == TaskSpace.PERSONAL && it.assigneeId == null && it.teamUid == null })
        assertTrue(f.events.isEmpty())
    }

    // --- Membres --------------------------------------------------------------------------------------------

    @Test
    fun archivingAMemberJournalsEachTaskSentBackToTheBacklog() = runTest {
        val f = fixture()
        val a = f.repo.createTeamTask("A")!!
        val b = f.repo.createTeamTask("B")!!
        val done = f.repo.createTeamTask("Faite")!!
        val other = f.repo.createTeamTask("Autre")!!
        f.repo.assign(listOf(a, b, done), f.alex)
        f.repo.assign(listOf(other), f.bea)
        f.repo.setProgress(a, 30)
        f.repo.toggleDone(done)

        assertTrue(f.repo.archiveMember(f.alex))
        for (id in listOf(a, b)) {
            val e = f.eventsOf(id).last()
            assertEquals(TeamEventKind.ASSIGNED, e.kind)
            assertEquals("${f.alex}" to null, e.fromValue to e.toValue)
            assertNull(e.memberId)
            assertNull(f.task(id).assigneeId)
        }
        assertNull(f.task(a).startedOn) // plus « en cours » : elle est au backlog
        assertEquals(30, f.task(a).progressPercent)
        assertEquals(TeamEventKind.DONE, f.eventsOf(done).last().kind) // la faite reste à son nom, sans événement
        assertEquals(f.alex, f.task(done).assigneeId)
        assertEquals(f.bea, f.task(other).assigneeId)
        assertEquals(TeamEventKind.ASSIGNED, f.eventsOf(other).last().kind)
        // Déjà archivé : rien de plus.
        val count = f.events.size
        assertFalse(f.repo.archiveMember(f.alex))
        assertEquals(count, f.events.size)
    }

    @Test
    fun aMemberWhoAppearsInTheJournalCannotBeDeleted() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        f.repo.assign(listOf(id), f.alex)
        f.repo.deleteTask(id) // plus aucune tâche à son nom, mais le journal garde sa trace
        assertTrue(f.repo.snapshot.value.tasks.none { it.assigneeId == f.alex })
        assertFalse(f.repo.deleteMember(f.alex))
        assertTrue(f.repo.snapshot.value.members.any { it.id == f.alex })
        // Bea n'a jamais rien eu : supprimable.
        assertTrue(f.repo.deleteMember(f.bea))
    }

    @Test
    fun clearTeamDataAlsoClearsTheJournal() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        f.repo.assign(listOf(id), f.alex)
        val personal = f.repo.createTask("Perso")!!
        assertTrue(f.events.isNotEmpty())
        f.repo.clearTeamData()
        assertTrue(f.events.isEmpty())
        assertTrue(f.repo.snapshot.value.tasks.none { it.space == TaskSpace.TEAM })
        assertEquals(listOf(personal), f.repo.snapshot.value.tasks.map { it.id })
    }

    // --- Isolation du mode solo ----------------------------------------------------------------------------------

    @Test
    fun personalOperationsNeverWriteAnEvent() = runTest {
        val repo = open()
        val id = repo.createTask("Perso")!!
        repo.setPriority(id, 0)
        repo.setPoints(id, 3)
        repo.updateTask(id, TaskEdit(title = "Perso", priority = 0, points = 3, deadline = day, taskType = "Dev", pinDay = day, newSubtasks = "Fille"))
        repo.startTimer(id)
        repo.snooze(id)
        repo.toggleDone(id)
        repo.toggleDone(id)
        repo.deleteTask(id)
        assertTrue(repo.snapshot.value.teamEvents.isEmpty())
        assertFalse(Workspaces.hasTeamData(repo.snapshot.value))
        assertSame(repo.snapshot.value, repo.personalSnapshot.value)
        assertTrue(repo.snapshot.value.tasks.all { it.progressPercent == null && it.startedOn == null && it.teamUid == null })
    }

    // --- Export -----------------------------------------------------------------------------------------------------

    @Test
    fun exportRoundTripsEventsAndTaskFields() = runTest {
        val f = fixture()
        val id = f.repo.createTeamTask("T")!!
        f.repo.assign(listOf(id), f.alex)
        f.repo.setPriority(id, 1)
        f.repo.setProgress(id, 40)
        val snapshot = f.repo.snapshot.value
        assertTrue(snapshot.teamEvents.size >= 5)

        val text = ExportCodec.encode(snapshot, "3.0.0", now)
        assertTrue("\"formatVersion\": 2" in text)
        assertTrue("\"teamEvents\"" in text)
        val decoded = ExportCodec.decode(text)
        assertEquals(snapshot, decoded)
        assertEquals(40, decoded.tasks.single().progressPercent)
        assertEquals(day, decoded.tasks.single().startedOn)
        assertNotNull(decoded.tasks.single().teamUid)

        // Réimport dans une autre base : identifiants et journal identiques.
        val other = open()
        other.replaceAll(decoded)
        assertEquals(snapshot, other.snapshot.value)
        assertEquals(snapshot.teamEvents, other.snapshot.value.teamEvents)
    }

    @Test
    fun anEventAloneIsTeamData() {
        val event = TeamEvent(1, 5, "Supprimée", null, TeamEventKind.DELETED, null, null, TeamEventSource.MANUAL, now)
        val snapshot = KairosSnapshot(teamEvents = listOf(event))
        assertTrue(Workspaces.hasTeamData(snapshot))
        val text = ExportCodec.encode(snapshot, "3.0.0", now)
        assertTrue("\"formatVersion\": 2" in text)
        assertEquals(snapshot, ExportCodec.decode(text))
    }

    @Test
    fun anUnknownEventKindIsKeptAndReadable() {
        val text = """{"format":"kairos-export","formatVersion":2,"teamEvents":[{"id":1,"taskId":2,"taskTitle":"T","kind":"futur","source":"martien","at":"2026-09-28T07:00:00Z"}]}"""
        val e = ExportCodec.decode(text).teamEvents.single()
        assertEquals(TeamEventKind.UNKNOWN, e.kind)
        assertEquals(TeamEventSource.MANUAL, e.source)
        assertEquals("T", e.taskTitle)
    }

    @Test
    fun aVersionTwoExportFromBeforeTheJournalStillDecodes() {
        val text = """{"format":"kairos-export","formatVersion":2,"members":[{"id":1,"uid":"u","name":"A","hoursPerDay":7.0,"createdAt":"2026-09-28T07:00:00Z","updatedAt":"2026-09-28T07:00:00Z"}],"tasks":[{"id":1,"title":"x","space":"team","createdAt":"2026-09-28T07:00:00Z","updatedAt":"2026-09-28T07:00:00Z"}]}"""
        val s = ExportCodec.decode(text)
        assertTrue(s.teamEvents.isEmpty())
        assertNull(s.tasks.single().teamUid)
        assertNull(s.tasks.single().startedOn)
    }

    // --- Migrations -----------------------------------------------------------------------------------------------------

    /** Schéma de la version 3 (fin du jalon E2), recopié en dur : il ne doit jamais suivre `Kairos.sq`. */
    private val schemaV3 = listOf(
        """CREATE TABLE task (
            id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '',
            priority INTEGER, deadline TEXT, project_tag TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'todo',
            estimated_minutes INTEGER, pinned_start TEXT, parent_id INTEGER, recurrence TEXT NOT NULL DEFAULT '',
            scheduled_date TEXT, recurrence_day_of_month INTEGER, recurrence_day_of_week INTEGER,
            recurrence_period TEXT NOT NULL DEFAULT '', task_type TEXT NOT NULL DEFAULT '', fibonacci_points INTEGER,
            manual_time_spent_minutes INTEGER, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
            space INTEGER NOT NULL DEFAULT 0, assignee_id INTEGER)""",
        "CREATE INDEX task_status ON task(status)",
        "CREATE INDEX task_parent ON task(parent_id)",
        "CREATE INDEX task_space ON task(space)",
        "CREATE INDEX task_assignee ON task(assignee_id)",
        """CREATE TABLE time_block (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL DEFAULT '', start TEXT NOT NULL,
            end TEXT NOT NULL, kind TEXT NOT NULL DEFAULT 'busy', recurrence TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL)""",
        "CREATE INDEX time_block_start ON time_block(start)",
        """CREATE TABLE task_dependency (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER NOT NULL, blocker_id INTEGER NOT NULL,
            created_at TEXT NOT NULL, UNIQUE (task_id, blocker_id))""",
        "CREATE INDEX task_dependency_task ON task_dependency(task_id)",
        "CREATE INDEX task_dependency_blocker ON task_dependency(blocker_id)",
        """CREATE TABLE work_session (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER NOT NULL, started_at TEXT NOT NULL,
            ended_at TEXT, created_at TEXT NOT NULL)""",
        "CREATE INDEX work_session_task ON work_session(task_id)",
        """CREATE TABLE note (id INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'open',
            converted_task_id INTEGER, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)""",
        "CREATE INDEX note_status ON note(status)",
        """CREATE TABLE team_member (id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL, name TEXT NOT NULL,
            role TEXT NOT NULL DEFAULT '', availability_percent INTEGER NOT NULL DEFAULT 100, hours_per_day REAL NOT NULL,
            is_self INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)""",
        """CREATE TABLE member_absence (id INTEGER PRIMARY KEY AUTOINCREMENT, member_id INTEGER NOT NULL, start TEXT NOT NULL,
            end TEXT NOT NULL, label TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL)""",
        "CREATE INDEX member_absence_member ON member_absence(member_id)",
        "CREATE TABLE settings (id INTEGER PRIMARY KEY CHECK (id = 1), json TEXT NOT NULL)",
        """INSERT INTO task (id, title, priority, fibonacci_points, space, assignee_id, created_at, updated_at) VALUES
            (1, 'Perso', 1, 3, 0, NULL, '2026-09-01T08:00:00Z', '2026-09-01T08:00:00Z'),
            (2, 'Équipe assignée', 0, 5, 1, 1, '2026-09-02T08:00:00Z', '2026-09-02T08:00:00Z'),
            (3, 'Équipe backlog', NULL, NULL, 1, NULL, '2026-09-03T08:00:00Z', '2026-09-03T08:00:00Z')""",
        "INSERT INTO task_dependency (id, task_id, blocker_id, created_at) VALUES (1, 2, 3, '2026-09-02T08:00:00Z')",
        """INSERT INTO team_member (id, uid, name, role, availability_percent, hours_per_day, is_self, archived, created_at, updated_at)
            VALUES (1, 'uid-1', 'Alex', 'Dev', 80, 6.5, 1, 0, '2026-09-01T08:00:00Z', '2026-09-01T08:00:00Z')""",
        "INSERT INTO member_absence (id, member_id, start, end, label, created_at) VALUES (1, 1, '2026-10-01', '2026-10-05', 'Congés', '2026-09-01T08:00:00Z')",
        """INSERT INTO settings (id, json) VALUES (1, '{"meetingBufferMinutes":9,"team":{"enabled":true,"name":"P","identity":"id-1"}}')""",
        "PRAGMA user_version = 3",
    )

    private fun databaseFrom(ddl: List<String>): File {
        val file = Files.createTempFile("kairos-fixture", ".db").toFile().also { it.delete() }
        java.sql.DriverManager.getConnection("jdbc:sqlite:${file.path}").use { c ->
            c.createStatement().use { st -> ddl.forEach { st.execute(it) } }
        }
        return file
    }

    private fun columns(f: File, table: String) = java.sql.DriverManager.getConnection("jdbc:sqlite:${f.path}").use { c ->
        c.createStatement().executeQuery("SELECT name FROM pragma_table_info('$table')").use { rs ->
            generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
        }
    }

    @Test
    fun aSchemaThreeDatabaseIsMigratedWithoutLosingARow() = runTest {
        val file = databaseFrom(schemaV3)
        val opened = KairosStore.open(driver(file))
        assertFalse(opened.created)
        val repo = KairosRepository.open(opened, examples, clock, timeZone = TimeZone.UTC)

        val all = repo.snapshot.value
        assertEquals(listOf(1L, 2L, 3L), all.tasks.map { it.id })
        assertEquals(listOf(TaskSpace.PERSONAL, TaskSpace.TEAM, TaskSpace.TEAM), all.tasks.map { it.space })
        assertEquals(1L, all.tasks[1].assigneeId)
        assertTrue(all.tasks.all { it.progressPercent == null && it.startedOn == null && it.teamUid == null })
        assertEquals(listOf("Perso", "Équipe assignée", "Équipe backlog"), all.tasks.map { it.title })
        assertEquals(1, all.dependencies.size)
        assertEquals(1, all.members.size)
        assertEquals("Alex", all.members.single().name)
        assertEquals(1, all.absences.size)
        assertEquals(9, all.settings.meetingBufferMinutes)
        assertEquals("id-1", all.settings.team!!.identity)
        assertTrue(all.teamEvents.isEmpty())

        // Les nouvelles colonnes et la table du journal s'écrivent, sur la base migrée.
        repo.assign(listOf(3), 1)
        repo.setProgress(3, 30)
        repo.setPriority(2, 2)
        assertEquals(30, repo.snapshot.value.tasks.single { it.id == 3L }.progressPercent)
        assertEquals(4, repo.snapshot.value.teamEvents.size) // assigned, started, progress, qualified
        val newTask = repo.createTeamTask("Nouvelle")!!
        assertNotNull(repo.snapshot.value.tasks.single { it.id == newTask }.teamUid)

        // Réouverture : la version est à jour, rien ne migre plus, tout est gardé.
        val again = open(file)
        assertEquals(4, again.snapshot.value.tasks.size)
        assertEquals(5, again.snapshot.value.teamEvents.size)
        file.delete()
    }

    @Test
    fun aMigratedAndAFreshDatabaseHaveTheSameColumnsInTheSameOrder() = runTest {
        val migrated = databaseFrom(schemaV3)
        KairosStore.open(driver(migrated))
        val fresh = Files.createTempFile("kairos-fresh", ".db").toFile().also { it.delete() }
        KairosStore.open(driver(fresh))
        for (table in listOf("task", "team_event", "team_member", "member_absence")) {
            assertEquals(columns(fresh, table), columns(migrated, table), table)
        }
        assertEquals(
            listOf("progress_percent", "started_on", "team_uid"),
            // Les colonnes du jalon E6 (origin, origin_removed, reported_minutes) viennent à leur suite.
            columns(fresh, "task").dropLast(3).takeLast(3),
        )
        assertEquals(
            listOf("id", "task_id", "task_title", "member_id", "kind", "from_value", "to_value", "source", "at"),
            columns(fresh, "team_event"),
        )
        migrated.delete()
        fresh.delete()
    }

    @Test
    fun aSchemaOneDatabaseGoesAllTheWayToFour() = runTest {
        // Schéma 1 : celui d'avant l'espace Équipe (aucune colonne ni table d'équipe).
        val v1 = listOf(
            """CREATE TABLE task (
                id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '',
                priority INTEGER, deadline TEXT, project_tag TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'todo',
                estimated_minutes INTEGER, pinned_start TEXT, parent_id INTEGER, recurrence TEXT NOT NULL DEFAULT '',
                scheduled_date TEXT, recurrence_day_of_month INTEGER, recurrence_day_of_week INTEGER,
                recurrence_period TEXT NOT NULL DEFAULT '', task_type TEXT NOT NULL DEFAULT '', fibonacci_points INTEGER,
                manual_time_spent_minutes INTEGER, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)""",
            "CREATE INDEX task_status ON task(status)",
            "CREATE INDEX task_parent ON task(parent_id)",
            """CREATE TABLE time_block (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL DEFAULT '', start TEXT NOT NULL,
                end TEXT NOT NULL, kind TEXT NOT NULL DEFAULT 'busy', recurrence TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL)""",
            "CREATE INDEX time_block_start ON time_block(start)",
            """CREATE TABLE task_dependency (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER NOT NULL, blocker_id INTEGER NOT NULL,
                created_at TEXT NOT NULL, UNIQUE (task_id, blocker_id))""",
            "CREATE INDEX task_dependency_task ON task_dependency(task_id)",
            "CREATE INDEX task_dependency_blocker ON task_dependency(blocker_id)",
            """CREATE TABLE work_session (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER NOT NULL, started_at TEXT NOT NULL,
                ended_at TEXT, created_at TEXT NOT NULL)""",
            "CREATE INDEX work_session_task ON work_session(task_id)",
            """CREATE TABLE note (id INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'open',
                converted_task_id INTEGER, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)""",
            "CREATE INDEX note_status ON note(status)",
            "CREATE TABLE settings (id INTEGER PRIMARY KEY CHECK (id = 1), json TEXT NOT NULL)",
            """INSERT INTO task (id, title, priority, fibonacci_points, created_at, updated_at) VALUES
                (1, 'Ancienne', 1, 3, '2026-09-01T08:00:00Z', '2026-09-01T08:00:00Z')""",
            "PRAGMA user_version = 1",
        )
        val file = databaseFrom(v1)
        val repo = KairosRepository.open(KairosStore.open(driver(file)), examples, clock, timeZone = TimeZone.UTC)
        val all = repo.snapshot.value
        assertEquals(listOf(1L), all.tasks.map { it.id })
        assertTrue(all.tasks.single().let { it.space == TaskSpace.PERSONAL && it.progressPercent == null && it.teamUid == null })
        assertTrue(all.teamEvents.isEmpty() && all.members.isEmpty() && all.absences.isEmpty())
        assertSame(all, repo.personalSnapshot.value)
        assertEquals(columns(fresh(), "task"), columns(file, "task"))
        // Une tâche d'équipe s'écrit sur la base migrée depuis la version 1.
        val id = repo.createTeamTask("Nouvelle")!!
        assertEquals(1, repo.snapshot.value.teamEvents.size)
        assertEquals(id, repo.snapshot.value.teamEvents.single().taskId)
        file.delete()
    }

    private fun fresh(): File {
        val f = Files.createTempFile("kairos-fresh", ".db").toFile().also { it.delete() }
        kotlinx.coroutines.runBlocking { KairosStore.open(driver(f)) }
        return f
    }
}
