package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.TeamEventKind
import com.skohscripts.kairos.core.team.TeamEventSource
import com.skohscripts.kairos.core.team.Workspaces
import com.skohscripts.kairos.core.team.forecast.IgnoredModification
import com.skohscripts.kairos.core.team.forecast.Scenario.SkipReason
import com.skohscripts.kairos.core.team.forecast.ScenarioModification
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.AddAbsence
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.AddMember
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.AddTasks
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.Reassign
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.RemoveMember
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.SetAvailability
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.SetDeadline
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.SetFocus
import com.skohscripts.kairos.core.team.forecast.ScenarioModification.SetPriority
import com.skohscripts.kairos.core.team.forecast.TeamScenario
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

/** Espace Équipe, jalon E5 (docs/spec/equipe-simulation.md § Scénarios) : table `team_scenario`, dépôt, export et migration 4 -> 5. */
class ScenarioRepositoryTest {
    private val now = Instant.parse("2026-10-05T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val day = LocalDate(2026, 10, 5)
    private val examples = { ExampleData.snapshot(day, now, "fr") }

    private fun driver(file: File? = null) =
        JdbcSqliteDriver(if (file == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:${file.path}")

    private suspend fun open(file: File? = null, onChanged: suspend (KairosSnapshot) -> Unit = {}): KairosRepository =
        KairosRepository.open(KairosStore.open(driver(file)), examples, clock, timeZone = TimeZone.UTC, onChanged = onChanged)

    /** Base vide, espace Équipe activé : Alex et Bea, quatre tâches (A et B chez Alex, C chez Bea, D au backlog), prêtes. */
    private class Fixture(val repo: KairosRepository, val alex: Long, val bea: Long, val a: Long, val b: Long, val c: Long, val d: Long) {
        val snap get() = repo.snapshot.value
        fun task(id: Long) = snap.tasks.single { it.id == id }
        fun uid(id: Long) = task(id).teamUid!!
        fun eventsOf(id: Long) = snap.teamEvents.filter { it.taskId == id }
    }

    private suspend fun fixture(onChanged: suspend (KairosSnapshot) -> Unit = {}): Fixture {
        val repo = open(onChanged = onChanged)
        repo.replaceAll(KairosSnapshot(settings = Settings(team = TeamSettings(enabled = true, identity = "id-1"))))
        val alex = repo.createMember("Alex", "", 100, 7.0, false)!!
        val bea = repo.createMember("Bea", "", 100, 7.0, false)!!
        suspend fun team(title: String, assignee: Long?, priority: Int): Long {
            val id = repo.createTeamTask(title)!!
            repo.setPriority(id, priority)
            repo.setPoints(id, 3)
            if (assignee != null) repo.assign(listOf(id), assignee)
            return id
        }
        return Fixture(repo, alex, bea, team("A", alex, 1), team("B", alex, 2), team("C", bea, 1), team("D", null, 1))
    }

    private val monday = LocalDate(2026, 10, 5)

    // --- CRUD ----------------------------------------------------------------------------------------------------------

    @Test
    fun createUpdateDuplicateAndDeleteAScenario() = runTest {
        val repo = open()
        assertTrue(repo.snapshot.value.teamScenarios.isEmpty())
        val mods = listOf(SetFocus(0.6), AddMember(-1, "Hypo", 80, 7.0))
        val id = repo.createScenario("  Plus de focus  ", mods)!!
        val s = repo.snapshot.value.teamScenarios.single()
        assertEquals(TeamScenario(id, "Plus de focus", mods, now, now), s)
        // Un nom vide est refusé, rien n'est écrit.
        assertNull(repo.createScenario("   ", mods))
        assertEquals(1, repo.snapshot.value.teamScenarios.size)

        assertTrue(repo.updateScenario(id, "Renommé", listOf(SetFocus(0.5))))
        assertEquals("Renommé", repo.snapshot.value.teamScenarios.single().name)
        assertEquals(listOf<ScenarioModification>(SetFocus(0.5)), repo.snapshot.value.teamScenarios.single().modifications)
        assertFalse(repo.updateScenario(id, "  ", mods))
        assertFalse(repo.updateScenario(999, "X", mods))

        val copy = repo.duplicateScenario(id, "Renommé (copie)")!!
        assertNotEquals(id, copy)
        assertEquals(2, repo.snapshot.value.teamScenarios.size)
        assertEquals("Renommé (copie)", repo.snapshot.value.teamScenarios.single { it.id == copy }.name)
        assertEquals(repo.snapshot.value.teamScenarios[0].modifications, repo.snapshot.value.teamScenarios[1].modifications)
        assertEquals("Renommé", repo.duplicateScenario(id)!!.let { dup -> repo.snapshot.value.teamScenarios.single { it.id == dup }.name })
        assertNull(repo.duplicateScenario(999))

        repo.deleteScenario(id)
        assertTrue(repo.snapshot.value.teamScenarios.none { it.id == id })
        assertTrue(repo.snapshot.value.teamScenarios.any { it.id == copy })
        // Un identifiant supprimé n'est jamais réutilisé (AUTOINCREMENT).
        assertTrue(repo.createScenario("Neuf", emptyList())!! > copy)
    }

    @Test
    fun scenariosSurviveReopeningAndStayInTheFullSnapshotNotInTheSoloOne() = runTest {
        val file = Files.createTempFile("kairos-sc", ".db").toFile().also { it.delete() }
        val repo = open(file)
        val id = repo.createScenario("Persisté", listOf(AddAbsence(1, monday, monday)))!!
        val again = open(file)
        assertEquals(listOf(id), again.snapshot.value.teamScenarios.map { it.id })
        assertEquals(listOf<ScenarioModification>(AddAbsence(1, monday, monday)), again.snapshot.value.teamScenarios.single().modifications)
        assertTrue(Workspaces.hasTeamData(again.snapshot.value))
        file.delete()
    }

    @Test
    fun anUnknownModificationTypeInTheDatabaseIsIgnoredAndReported() = runTest {
        val file = Files.createTempFile("kairos-sc2", ".db").toFile().also { it.delete() }
        val repo = open(file)
        repo.createScenario("Futur", listOf(SetFocus(0.7)))
        java.sql.DriverManager.getConnection("jdbc:sqlite:${file.path}").use { c ->
            c.createStatement().use {
                it.execute("""UPDATE team_scenario SET modifications = '[{"type":"setFocus","factor":0.7},{"type":"teleport","to":"mars"}]'""")
            }
        }
        val s = open(file).snapshot.value.teamScenarios.single()
        assertEquals(listOf<ScenarioModification>(SetFocus(0.7)), s.modifications)
        assertEquals(listOf(IgnoredModification(1, "teleport")), s.ignored)
        file.delete()
    }

    @Test
    fun clearTeamDataRemovesTheScenarios() = runTest {
        val f = fixture()
        f.repo.createScenario("S", listOf(SetFocus(0.5)))
        assertEquals(1, f.snap.teamScenarios.size)
        f.repo.clearTeamData()
        assertTrue(f.snap.teamScenarios.isEmpty())
        // Les réglages d'équipe sont gardés.
        assertTrue(f.snap.settings.team!!.enabled)
    }

    @Test
    fun replaceAllWritesTheScenariosWithTheirIdentifiersAndReplacesThePreviousOnes() = runTest {
        val repo = open()
        repo.createScenario("Ancien", emptyList())
        val imported = TeamScenario(42, "Importé", listOf(SetFocus(0.9), Reassign("u", null)), now, now)
        repo.replaceAll(KairosSnapshot(teamScenarios = listOf(imported)))
        assertEquals(listOf(imported), repo.snapshot.value.teamScenarios)
        repo.replaceAll(KairosSnapshot())
        assertTrue(repo.snapshot.value.teamScenarios.isEmpty())
    }

    // --- Appliquer -----------------------------------------------------------------------------------------------------

    @Test
    fun applyingAScenarioChangesOnlyRealDataAndJournalsEachTaskChangeWithTheScenarioSource() = runTest {
        val f = fixture()
        val deadline = LocalDate(2026, 10, 30)
        val mods = listOf(
            AddMember(-1, "Hypothétique", 100, 8.0), // jamais créé
            AddTasks(3, 5, "Dev", 1), // jamais créées
            Reassign(f.uid(f.a), -1), // vers un membre hypothétique : non applicable
            Reassign(f.uid(f.b), f.bea), // réaffectation réelle
            SetPriority(f.uid(f.c), 0),
            SetDeadline(f.uid(f.a), deadline),
            AddAbsence(f.bea, monday, LocalDate(2026, 10, 9)),
            SetAvailability(f.alex, 60),
            SetFocus(0.7),
        )
        val id = f.repo.createScenario("Mix", mods)!!
        val membersBefore = f.snap.members.size
        val tasksBefore = f.snap.tasks.size
        val eventsBefore = f.snap.teamEvents.size

        val report = f.repo.applyScenario(id)

        assertTrue(report.found)
        assertEquals(mods.drop(3), report.applied)
        assertTrue(report.skipped.isEmpty())
        assertEquals(listOf(mods[0], mods[1], mods[2]), report.notApplicable)
        // Ni membre ni tâche hypothétique n'a été créé.
        assertEquals(membersBefore, f.snap.members.size)
        assertEquals(tasksBefore, f.snap.tasks.size)
        assertTrue(f.snap.members.none { it.id < 0 } && f.snap.tasks.none { it.id < 0 })
        // Les changements réels.
        assertEquals(f.bea, f.task(f.b).assigneeId)
        assertEquals(0, f.task(f.c).priority)
        assertEquals(deadline, f.task(f.a).deadline)
        assertEquals(listOf(f.bea to monday), f.snap.absences.map { it.memberId to it.start })
        assertEquals(60, f.snap.members.single { it.id == f.alex }.availabilityPercent)
        assertEquals(0.7, f.snap.settings.team!!.focusFactor)
        assertEquals("id-1", f.snap.settings.team!!.identity)
        // Journal : un événement par changement de tâche, tous de source `scenario`.
        val added = f.snap.teamEvents.drop(eventsBefore)
        assertEquals(3, added.size)
        assertTrue(added.all { it.source == TeamEventSource.SCENARIO })
        assertEquals(listOf(f.b to TeamEventKind.ASSIGNED, f.c to TeamEventKind.QUALIFIED, f.a to TeamEventKind.QUALIFIED), added.map { it.taskId to it.kind })
        assertEquals(f.alex.toString() to f.bea.toString(), added[0].fromValue to added[0].toValue)
        assertEquals("priority:1" to "priority:0", added[1].fromValue to added[1].toValue)
        assertEquals("deadline:" to "deadline:2026-10-30", added[2].fromValue to added[2].toValue)
        // Le scénario lui-même est inchangé.
        assertEquals(mods, f.snap.teamScenarios.single().modifications)
    }

    @Test
    fun applyingAScenarioIsOneTransaction() = runTest {
        var changes = 0
        val f = fixture { changes++ }
        val id = f.repo.createScenario(
            "Tout", listOf(Reassign(f.uid(f.a), f.bea), Reassign(f.uid(f.b), f.bea), SetPriority(f.uid(f.c), 2), AddAbsence(f.alex, monday, monday), SetFocus(0.5)),
        )!!
        changes = 0
        f.repo.applyScenario(id)
        // Une seule écriture publiée pour cinq modifications.
        assertEquals(1, changes)
    }

    @Test
    fun laterModificationsSeeTheEffectOfEarlierOnesInTheSameTransaction() = runTest {
        val f = fixture()
        // A passe chez Bea, puis sa priorité et son échéance changent : la ligne finale cumule les trois ; le journal aussi.
        val id = f.repo.createScenario(
            "Cumul", listOf(Reassign(f.uid(f.a), f.bea), SetPriority(f.uid(f.a), 0), SetDeadline(f.uid(f.a), LocalDate(2026, 11, 2)), Reassign(f.uid(f.a), null)),
        )!!
        val report = f.repo.applyScenario(id)
        assertEquals(4, report.applied.size)
        val t = f.task(f.a)
        assertNull(t.assigneeId)
        assertEquals(0, t.priority)
        assertEquals(LocalDate(2026, 11, 2), t.deadline)
        assertEquals(
            listOf(TeamEventKind.ASSIGNED, TeamEventKind.QUALIFIED, TeamEventKind.QUALIFIED, TeamEventKind.ASSIGNED),
            f.eventsOf(f.a).filter { it.source == TeamEventSource.SCENARIO }.map { it.kind },
        )
    }

    @Test
    fun aModificationThatNoLongerAppliesIsSkippedWithItsReasonAndTheRestIsApplied() = runTest {
        val f = fixture()
        f.repo.toggleDone(f.c) // C est faite depuis l'écriture du scénario
        f.repo.archiveMember(f.bea)
        val mods = listOf(
            SetPriority(f.uid(f.c), 0), // tâche faite
            Reassign("uid-disparu", f.alex), // tâche inconnue
            Reassign(f.uid(f.d), f.bea), // membre archivé
            Reassign(f.uid(f.d), 4242), // membre inconnu
            SetPriority(f.uid(f.a), 5), // hors bornes
            SetAvailability(f.alex, 0), // hors bornes
            AddAbsence(f.alex, monday.plus1(), monday), // dates inversées
            SetFocus(1.5),
            Reassign(f.uid(f.d), f.alex), // la seule qui s'applique
        )
        val id = f.repo.createScenario("Périmé", mods)!!
        val report = f.repo.applyScenario(id)
        assertEquals(listOf<ScenarioModification>(mods.last()), report.applied)
        assertEquals(
            listOf(
                SkipReason.TASK_NOT_OPEN, SkipReason.TASK_NOT_FOUND, SkipReason.MEMBER_ARCHIVED, SkipReason.MEMBER_NOT_FOUND,
                SkipReason.INVALID_VALUE, SkipReason.INVALID_VALUE, SkipReason.INVALID_VALUE, SkipReason.INVALID_VALUE,
            ),
            report.skipped.map { it.reason },
        )
        assertEquals((0..7).toList(), report.skipped.map { it.index })
        assertEquals(f.alex, f.task(f.d).assigneeId)
        assertEquals(TaskStatus.DONE, f.task(f.c).status)
        assertEquals(1, f.task(f.c).priority)
    }

    private fun LocalDate.plus1() = LocalDate.fromEpochDays(toEpochDays() + 1)

    @Test
    fun applyingTwiceChangesNothingTheSecondTime() = runTest {
        val f = fixture()
        val id = f.repo.createScenario(
            "Idempotent",
            listOf(Reassign(f.uid(f.a), f.bea), SetPriority(f.uid(f.b), 0), SetDeadline(f.uid(f.c), LocalDate(2026, 11, 1)), AddAbsence(f.alex, monday, monday), SetAvailability(f.alex, 50), SetFocus(0.6)),
        )!!
        assertEquals(6, f.repo.applyScenario(id).applied.size)
        val events = f.snap.teamEvents.size
        val absences = f.snap.absences.size
        val again = f.repo.applyScenario(id)
        assertTrue(again.applied.isEmpty())
        assertEquals(List(6) { SkipReason.ALREADY_APPLIED }, again.skipped.map { it.reason })
        assertEquals(events, f.snap.teamEvents.size)
        assertEquals(absences, f.snap.absences.size)
    }

    @Test
    fun removingAMemberArchivesHimAndSendsHisOpenTasksBackToTheBacklogWithAScenarioEvent() = runTest {
        val f = fixture()
        val id = f.repo.createScenario("Départ", listOf(RemoveMember(f.alex), Reassign(f.uid(f.a), f.alex), AddAbsence(f.alex, monday, monday)))!!
        val report = f.repo.applyScenario(id)
        // Les deux suivantes visent un membre archivé par la première.
        assertEquals(listOf<ScenarioModification>(RemoveMember(f.alex)), report.applied)
        assertEquals(listOf(SkipReason.MEMBER_ARCHIVED, SkipReason.MEMBER_ARCHIVED), report.skipped.map { it.reason })
        assertTrue(f.snap.members.single { it.id == f.alex }.archived)
        assertNull(f.task(f.a).assigneeId)
        assertNull(f.task(f.b).assigneeId)
        assertEquals(f.bea, f.task(f.c).assigneeId)
        val back = f.eventsOf(f.a).last()
        assertEquals(TeamEventKind.ASSIGNED, back.kind)
        assertEquals(TeamEventSource.SCENARIO, back.source)
        assertEquals(f.alex.toString() to null, back.fromValue to back.toValue)
        assertNull(back.memberId)
    }

    @Test
    fun applyingAnUnknownScenarioDoesNothing() = runTest {
        val f = fixture()
        val events = f.snap.teamEvents.size
        val report = f.repo.applyScenario(404)
        assertFalse(report.found)
        assertTrue(report.applied.isEmpty() && report.skipped.isEmpty() && report.notApplicable.isEmpty())
        assertEquals(events, f.snap.teamEvents.size)
    }

    @Test
    fun theUnreadModificationsOfAScenarioAreReportedWhenApplying() = runTest {
        val file = Files.createTempFile("kairos-sc3", ".db").toFile().also { it.delete() }
        val repo = open(file)
        repo.replaceAll(KairosSnapshot(settings = Settings(team = TeamSettings(enabled = true, identity = "id-1"))))
        repo.createScenario("Futur", listOf(SetFocus(0.7)))
        java.sql.DriverManager.getConnection("jdbc:sqlite:${file.path}").use { c ->
            c.createStatement().use { it.execute("""UPDATE team_scenario SET modifications = '[{"type":"setFocus","factor":0.7},{"type":"teleport"}]'""") }
        }
        val again = open(file)
        val report = again.applyScenario(again.snapshot.value.teamScenarios.single().id)
        assertEquals(1, report.applied.size)
        assertEquals(listOf(IgnoredModification(1, "teleport")), report.ignored)
        assertEquals(0.7, again.snapshot.value.settings.team!!.focusFactor)
        file.delete()
    }

    // --- Export --------------------------------------------------------------------------------------------------------

    private val scenario = TeamScenario(
        3, "Absence de Léa", listOf(AddAbsence(1, monday, LocalDate(2026, 10, 9)), AddMember(-1, "Renfort", 50, 6.0), SetFocus(0.75)), now, now,
    )

    @Test
    fun anExportWithScenariosIsVersionTwoAndRoundTrips() {
        val snapshot = KairosSnapshot(teamScenarios = listOf(scenario), settings = Settings(team = TeamSettings(enabled = true, identity = "x")))
        val text = ExportCodec.encode(snapshot, "3.0.0", now)
        assertTrue("\"formatVersion\": 2" in text)
        assertTrue("\"teamScenarios\"" in text)
        assertTrue("\"type\": \"addAbsence\"" in text)
        assertEquals(snapshot, ExportCodec.decode(text))
        // Une base qui n'a que des scénarios est une base d'équipe : version 2.
        assertTrue(Workspaces.hasTeamData(snapshot))
    }

    @Test
    fun aSoloExportHasNoScenarioField() {
        val text = ExportCodec.encode(examples(), "3.0.0", now)
        assertTrue("\"formatVersion\": 1" in text)
        assertFalse("teamScenarios" in text)
        assertFalse("modifications" in text)
    }

    @Test
    fun anExportFromBeforeScenariosStillDecodes() {
        val text = """{"format":"kairos-export","formatVersion":2,"members":[{"id":1,"uid":"u","name":"A","hoursPerDay":7.0,"createdAt":"2026-09-28T07:00:00Z","updatedAt":"2026-09-28T07:00:00Z"}]}"""
        assertTrue(ExportCodec.decode(text).teamScenarios.isEmpty())
    }

    @Test
    fun anUnknownModificationInAnExportIsIgnoredAndReported() {
        val text = """{"format":"kairos-export","formatVersion":2,"teamScenarios":[{"id":1,"name":"S","modifications":[{"type":"setFocus","factor":0.5},{"type":"teleport"}],"createdAt":"2026-10-05T07:00:00Z","updatedAt":"2026-10-05T07:00:00Z"}]}"""
        val s = ExportCodec.decode(text).teamScenarios.single()
        assertEquals(listOf<ScenarioModification>(SetFocus(0.5)), s.modifications)
        assertEquals(listOf(IgnoredModification(1, "teleport")), s.ignored)
    }

    @Test
    fun scenariosGoThroughExportAndImportOfARealDatabase() = runTest {
        val f = fixture()
        f.repo.createScenario("S1", listOf(SetFocus(0.5), AddTasks(2, 3, "Dev", 1)))
        f.repo.createScenario("S2", listOf(Reassign(f.uid(f.a), f.bea)))
        val text = ExportCodec.encode(f.snap, "3.0.0", now)
        val other = open()
        other.replaceAll(ExportCodec.decode(text))
        assertEquals(f.snap.teamScenarios, other.snapshot.value.teamScenarios)
        assertEquals(f.snap, other.snapshot.value)
    }

    // --- Migration 4 -> 5 ----------------------------------------------------------------------------------------------

    /** Schéma de la version 4 (fin du jalon E3), recopié en dur : il ne doit jamais suivre `Kairos.sq`. */
    private val schemaV4 = listOf(
        """CREATE TABLE task (
            id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '',
            priority INTEGER, deadline TEXT, project_tag TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'todo',
            estimated_minutes INTEGER, pinned_start TEXT, parent_id INTEGER, recurrence TEXT NOT NULL DEFAULT '',
            scheduled_date TEXT, recurrence_day_of_month INTEGER, recurrence_day_of_week INTEGER,
            recurrence_period TEXT NOT NULL DEFAULT '', task_type TEXT NOT NULL DEFAULT '', fibonacci_points INTEGER,
            manual_time_spent_minutes INTEGER, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
            space INTEGER NOT NULL DEFAULT 0, assignee_id INTEGER, progress_percent INTEGER, started_on TEXT, team_uid TEXT)""",
        "CREATE INDEX task_status ON task(status)",
        "CREATE INDEX task_parent ON task(parent_id)",
        "CREATE INDEX task_space ON task(space)",
        "CREATE INDEX task_assignee ON task(assignee_id)",
        "CREATE INDEX task_team_uid ON task(team_uid)",
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
        """CREATE TABLE team_event (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER NOT NULL, task_title TEXT NOT NULL,
            member_id INTEGER, kind TEXT NOT NULL, from_value TEXT, to_value TEXT, source TEXT NOT NULL, at TEXT NOT NULL)""",
        "CREATE INDEX team_event_task ON team_event(task_id)",
        "CREATE INDEX team_event_member ON team_event(member_id)",
        "CREATE TABLE settings (id INTEGER PRIMARY KEY CHECK (id = 1), json TEXT NOT NULL)",
        """INSERT INTO task (id, title, priority, fibonacci_points, space, assignee_id, progress_percent, started_on, team_uid, created_at, updated_at) VALUES
            (1, 'Perso', 1, 3, 0, NULL, NULL, NULL, NULL, '2026-09-01T08:00:00Z', '2026-09-01T08:00:00Z'),
            (2, 'Équipe', 0, 5, 1, 1, 40, '2026-09-30', 'uid-task-2', '2026-09-02T08:00:00Z', '2026-09-02T08:00:00Z')""",
        """INSERT INTO team_member (id, uid, name, role, availability_percent, hours_per_day, is_self, archived, created_at, updated_at)
            VALUES (1, 'uid-1', 'Alex', 'Dev', 80, 6.5, 1, 0, '2026-09-01T08:00:00Z', '2026-09-01T08:00:00Z')""",
        "INSERT INTO member_absence (id, member_id, start, end, label, created_at) VALUES (1, 1, '2026-10-01', '2026-10-05', 'Congés', '2026-09-01T08:00:00Z')",
        "INSERT INTO team_event (id, task_id, task_title, member_id, kind, from_value, to_value, source, at) VALUES (1, 2, 'Équipe', 1, 'assigned', NULL, '1', 'manual', '2026-09-02T08:00:00Z')",
        """INSERT INTO settings (id, json) VALUES (1, '{"meetingBufferMinutes":9,"team":{"enabled":true,"name":"P","identity":"id-1"}}')""",
        "PRAGMA user_version = 4",
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
    fun aSchemaFourDatabaseIsMigratedWithoutLosingARow() = runTest {
        val file = databaseFrom(schemaV4)
        val opened = KairosStore.open(driver(file))
        assertFalse(opened.created)
        val repo = KairosRepository.open(opened, examples, clock, timeZone = TimeZone.UTC)

        val all = repo.snapshot.value
        assertEquals(listOf(1L, 2L), all.tasks.map { it.id })
        assertEquals("uid-task-2", all.tasks[1].teamUid)
        assertEquals(40, all.tasks[1].progressPercent)
        assertEquals(LocalDate(2026, 9, 30), all.tasks[1].startedOn)
        assertEquals("Alex", all.members.single().name)
        assertEquals(1, all.absences.size)
        assertEquals(1, all.teamEvents.size)
        assertEquals(9, all.settings.meetingBufferMinutes)
        assertEquals("id-1", all.settings.team!!.identity)
        // La table des scénarios naît vide : une base migrée se comporte comme avant.
        assertTrue(all.teamScenarios.isEmpty())

        // Elle est utilisable sur la base migrée, et les scénarios s'appliquent.
        val id = repo.createScenario("Après migration", listOf(SetPriority("uid-task-2", 2)))!!
        assertEquals(1, repo.applyScenario(id).applied.size)
        assertEquals(2, repo.snapshot.value.tasks.single { it.id == 2L }.priority)
        assertEquals(TeamEventSource.SCENARIO, repo.snapshot.value.teamEvents.last().source)

        // Réouverture : la version est à jour, rien ne migre plus, tout est gardé.
        val again = open(file)
        assertEquals(1, again.snapshot.value.teamScenarios.size)
        assertEquals(2, again.snapshot.value.teamEvents.size)
        file.delete()
    }

    @Test
    fun aMigratedAndAFreshDatabaseHaveTheSameScenarioColumns() = runTest {
        val migrated = databaseFrom(schemaV4)
        KairosStore.open(driver(migrated))
        val fresh = Files.createTempFile("kairos-fresh", ".db").toFile().also { it.delete() }
        KairosStore.open(driver(fresh))
        assertEquals(listOf("id", "name", "modifications", "created_at", "updated_at"), columns(fresh, "team_scenario"))
        for (table in listOf("task", "team_event", "team_member", "member_absence", "team_scenario")) {
            assertEquals(columns(fresh, table), columns(migrated, table), table)
        }
        migrated.delete()
        fresh.delete()
    }

    @Test
    fun aSchemaOneDatabaseGoesThroughTheWholeChainToFive() = runTest {
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
        assertTrue(all.teamScenarios.isEmpty() && all.members.isEmpty() && all.teamEvents.isEmpty())
        assertSame(all, repo.personalSnapshot.value)
        for (table in listOf("task", "team_scenario")) assertEquals(columns(fresh(), table), columns(file, table), table)
        assertNotNull(repo.createScenario("Chaîne", listOf(SetFocus(0.8))))
        assertEquals(1, repo.snapshot.value.teamScenarios.size)
        file.delete()
    }

    private fun fresh(): File {
        val f = Files.createTempFile("kairos-fresh", ".db").toFile().also { it.delete() }
        kotlinx.coroutines.runBlocking { KairosStore.open(driver(f)) }
        return f
    }
}
