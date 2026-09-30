package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Espace Équipe, jalon E1 (docs/spec/equipe.md) : stockage, export et isolation du mode solo. */
class TeamIsolationTest {
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

    private fun member(id: Long, name: String, self: Boolean = false, archived: Boolean = false) = TeamMember(
        id = id, uid = "uid-$id", name = name, role = "Dev", availabilityPercent = 80, hoursPerDay = 6.5,
        isSelf = self, archived = archived, createdAt = now, updatedAt = now,
    )

    private fun task(id: Long, space: TaskSpace = TaskSpace.PERSONAL, assignee: Long? = null, status: TaskStatus = TaskStatus.TODO) =
        Task(id, "T$id", priority = 1, fibonacciPoints = 3, status = status, space = space, assigneeId = assignee, createdAt = now, updatedAt = now)

    private fun teamSnapshot() = KairosSnapshot(
        tasks = listOf(task(1), task(2, TaskSpace.TEAM, assignee = 10), task(3, TaskSpace.TEAM, assignee = 11), task(4, TaskSpace.TEAM)),
        members = listOf(member(10, "Moi", self = true), member(11, "Autre", archived = true)),
        settings = Settings(team = TeamSettings(enabled = true, name = "Plateforme", managerName = "Claire", identity = "id-1", lastSpace = "team")),
    )

    // --- Export ---------------------------------------------------------------

    @Test
    fun soloExportIsByteIdentical() {
        val golden = javaClass.getResource("/golden/solo-export-v1.json")!!.readText()
        assertEquals(golden, ExportCodec.encode(examples(), "3.0.0", now))
    }

    @Test
    fun soloExportHasNoTeamField() {
        val text = ExportCodec.encode(examples(), "3.0.0", now)
        assertTrue("\"formatVersion\": 1" in text)
        for (word in listOf("members", "space", "assigneeId", "\"team\"")) assertFalse(word in text, word)
    }

    @Test
    fun anEnabledButEmptyTeamSpaceStillExportsVersionOne() {
        val snapshot = KairosSnapshot(settings = Settings(team = TeamSettings(enabled = true)))
        val text = ExportCodec.encode(snapshot, "3.0.0", now)
        assertTrue("\"formatVersion\": 1" in text)
        assertFalse("members" in text)
        assertEquals(snapshot, ExportCodec.decode(text))
    }

    @Test
    fun teamExportRoundTripsInVersionTwo() {
        val snapshot = teamSnapshot()
        val text = ExportCodec.encode(snapshot, "3.0.0", now)
        assertTrue("\"formatVersion\": 2" in text)
        assertEquals(snapshot, ExportCodec.decode(text))
        // `space` n'est écrit que pour les tâches d'équipe.
        assertEquals(3, Regex("\"space\": \"team\"").findAll(text).count())
    }

    @Test
    fun aMemberAloneIsTeamData() {
        val snapshot = KairosSnapshot(members = listOf(member(1, "A")))
        val text = ExportCodec.encode(snapshot, "3.0.0", now)
        assertTrue("\"formatVersion\": 2" in text)
        assertEquals(snapshot, ExportCodec.decode(text))
    }

    @Test
    fun aVersionOneExportStillDecodes() {
        val golden = javaClass.getResource("/golden/solo-export-v1.json")!!.readText()
        val decoded = ExportCodec.decode(golden)
        assertEquals(examples(), decoded)
        assertTrue(decoded.members.isEmpty())
        assertTrue(decoded.tasks.all { it.space == TaskSpace.PERSONAL && it.assigneeId == null })
        assertNull(decoded.settings.team)
    }

    @Test
    fun aNewerFormatIsRefused() {
        val e = kotlin.runCatching { ExportCodec.decode("""{"format":"kairos-export","formatVersion":3}""") }.exceptionOrNull()
        assertEquals(ImportException.Reason.TOO_NEW, (e as ImportException).reason)
    }

    @Test
    fun anUnknownSpaceCodeInAnExportReadsAsPersonal() {
        val text = """{"format":"kairos-export","formatVersion":2,"tasks":[{"id":1,"title":"x","space":"martien","createdAt":"2026-09-28T07:00:00Z","updatedAt":"2026-09-28T07:00:00Z"}]}"""
        assertEquals(TaskSpace.PERSONAL, ExportCodec.decode(text).tasks.single().space)
    }

    // --- Base et dépôt ----------------------------------------------------------

    @Test
    fun replaceAllStoresMembersAndTaskFieldsWithTheirIds() = runTest {
        val repo = open()
        val snapshot = teamSnapshot()
        repo.replaceAll(snapshot)
        assertEquals(snapshot, repo.snapshot.value)
        // Une seconde réouverture de la même base rend la même chose (lecture depuis SQLite).
        repo.load()
        assertEquals(snapshot.members, repo.snapshot.value.members)
        repo.replaceAll(KairosSnapshot())
        assertTrue(repo.snapshot.value.members.isEmpty())
    }

    @Test
    fun personalSnapshotFollowsTheWrites() = runTest {
        val repo = open()
        // Base d'exemples solo : la vue Perso est la base elle-même.
        assertEquals(repo.snapshot.value, repo.personalSnapshot.value)
        assertSame(repo.snapshot.value, repo.personalSnapshot.value)

        repo.replaceAll(teamSnapshot())
        assertEquals(listOf(1L, 2L), repo.personalSnapshot.value.tasks.map { it.id })
        assertEquals(4, repo.snapshot.value.tasks.size)

        // Désactiver l'espace masque la tâche assignée à « moi » sans rien supprimer.
        repo.updateSettings(repo.snapshot.value.settings.copy(team = repo.snapshot.value.settings.team!!.copy(enabled = false)))
        assertEquals(listOf(1L), repo.personalSnapshot.value.tasks.map { it.id })
        assertEquals(4, repo.snapshot.value.tasks.size)

        repo.updateSettings(repo.snapshot.value.settings.copy(team = repo.snapshot.value.settings.team!!.copy(enabled = true)))
        assertEquals(listOf(1L, 2L), repo.personalSnapshot.value.tasks.map { it.id })

        // Une capture reste Perso et apparaît dans les deux vues.
        val id = repo.createTask("Nouvelle")!!
        assertEquals(TaskSpace.PERSONAL, repo.snapshot.value.tasks.single { it.id == id }.space)
        assertTrue(repo.personalSnapshot.value.tasks.any { it.id == id })
    }

    @Test
    fun aTeamTaskKeepsItsSpaceAndAssigneeWhenEdited() = runTest {
        val repo = open()
        repo.replaceAll(teamSnapshot())
        repo.setPriority(2, 0)
        repo.toggleDone(2)
        val t = repo.snapshot.value.tasks.single { it.id == 2L }
        assertEquals(TaskSpace.TEAM, t.space)
        assertEquals(10L, t.assigneeId)
        assertEquals(TaskStatus.DONE, t.status)
    }

    @Test
    fun aRecurringTeamTaskSpawnsItsNextOccurrenceInTheTeamSpace() = runTest {
        val repo = open()
        val recurring = task(2, TaskSpace.TEAM, assignee = 10).copy(
            recurrence = com.skohscripts.kairos.core.model.TaskRecurrence.DAILY, deadline = day,
        )
        repo.replaceAll(teamSnapshot().copy(tasks = listOf(recurring)))
        repo.toggleDone(2)
        val next = repo.snapshot.value.tasks.single { it.status == TaskStatus.TODO }
        assertEquals(TaskSpace.TEAM, next.space)
        assertEquals(10L, next.assigneeId)
        assertTrue(repo.personalSnapshot.value.tasks.any { it.id == next.id })
    }

    @Test
    fun updateTaskIgnoresABlockerFromAnotherSpace() = runTest {
        val repo = open()
        repo.replaceAll(
            teamSnapshot().copy(tasks = listOf(task(1), task(5), task(2, TaskSpace.TEAM, assignee = 10), task(3, TaskSpace.TEAM))),
        )
        val edit = { blockers: Set<Long> -> TaskEdit(title = "T", pinDay = day, blockerIds = blockers) }
        repo.updateTask(1, edit(setOf(2, 3, 5)))
        assertEquals(listOf(5L), repo.snapshot.value.dependencies.filter { it.taskId == 1L }.map { it.blockerId })
        repo.updateTask(2, edit(setOf(1, 5, 3)))
        assertEquals(listOf(3L), repo.snapshot.value.dependencies.filter { it.taskId == 2L }.map { it.blockerId })
    }

    @Test
    fun updateSettingsGivesTheTeamAnIdentityOnce() = runTest {
        val repo = open()
        val solo = repo.snapshot.value.settings
        repo.updateSettings(solo)
        assertNull(repo.snapshot.value.settings.team) // mode solo : rien n'est posé

        repo.updateSettings(solo.copy(team = TeamSettings(enabled = true, name = "P")))
        val identity = repo.snapshot.value.settings.team!!.identity
        assertTrue(identity.length >= 32)

        // Un formulaire qui renvoie une identité vide ne la remplace pas.
        repo.updateSettings(repo.snapshot.value.settings.copy(team = TeamSettings(enabled = true, name = "Q")))
        assertEquals(identity, repo.snapshot.value.settings.team!!.identity)
        assertEquals("Q", repo.snapshot.value.settings.team!!.name)
        assertNotEquals("", identity)
    }

    @Test
    fun soloSettingsStoredInTheDatabaseHaveNoTeamKey() = runTest {
        val file = Files.createTempFile("kairos", ".db").toFile().also { it.delete() }
        val repo = open(file)
        repo.updateSettings(repo.snapshot.value.settings.copy(meetingBufferMinutes = 7))
        val json = java.sql.DriverManager.getConnection("jdbc:sqlite:${file.path}").use { c ->
            c.createStatement().executeQuery("SELECT json FROM settings WHERE id = 1").use { it.next(); it.getString(1) }
        }
        assertFalse("team" in json, json)
    }

    // --- Migration 1 -> 2 -------------------------------------------------------

    /** Schéma de la version 1, **avant** l'espace Équipe, recopié en dur : il ne doit jamais suivre `Kairos.sq`. */
    private val schemaV1 = listOf(
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
            (1, 'Ancienne tâche', 1, 3, '2026-09-01T08:00:00Z', '2026-09-01T08:00:00Z'),
            (7, 'Autre tâche', 0, 5, '2026-09-02T08:00:00Z', '2026-09-02T08:00:00Z')""",
        "INSERT INTO task_dependency (id, task_id, blocker_id, created_at) VALUES (1, 7, 1, '2026-09-02T08:00:00Z')",
        """INSERT INTO settings (id, json) VALUES (1, '{"meetingBufferMinutes":9}')""",
        "PRAGMA user_version = 1",
    )

    @Test
    fun aSchemaOneDatabaseIsMigratedWithoutLosingARow() = runTest {
        val file = Files.createTempFile("kairos-v1", ".db").toFile().also { it.delete() }
        java.sql.DriverManager.getConnection("jdbc:sqlite:${file.path}").use { c ->
            c.createStatement().use { st -> schemaV1.forEach { st.execute(it) } }
        }

        val opened = KairosStore.open(driver(file))
        assertFalse(opened.created)
        val repo = KairosRepository.open(opened, examples, clock, timeZone = TimeZone.UTC)

        // Aucune ligne perdue, toutes dans l'espace Perso, sans assigné, et la vue Perso est l'identité.
        val all = repo.snapshot.value
        assertEquals(listOf(1L, 7L), all.tasks.map { it.id })
        assertTrue(all.tasks.all { it.space == TaskSpace.PERSONAL && it.assigneeId == null })
        assertEquals(1, all.dependencies.size)
        assertEquals(9, all.settings.meetingBufferMinutes)
        assertNull(all.settings.team)
        assertTrue(all.members.isEmpty())
        assertSame(all, repo.personalSnapshot.value)
        assertFalse(Workspaces.hasTeamData(all))

        // La table des membres est utilisable, et les nouvelles colonnes s'écrivent.
        repo.replaceAll(all.copy(members = listOf(member(1, "A", self = true)), tasks = all.tasks + task(9, TaskSpace.TEAM, assignee = 1)))
        assertEquals(1, repo.snapshot.value.members.size)
        assertEquals(TaskSpace.TEAM, repo.snapshot.value.tasks.single { it.id == 9L }.space)

        // La version est à jour : une réouverture ne migre plus et garde tout.
        val again = open(file)
        assertEquals(3, again.snapshot.value.tasks.size)
        assertEquals(1, again.snapshot.value.members.size)
        file.delete()
    }

    @Test
    fun aMigratedAndAFreshDatabaseHaveTheSameTaskColumns() = runTest {
        val migrated = Files.createTempFile("kairos-m", ".db").toFile().also { it.delete() }
        java.sql.DriverManager.getConnection("jdbc:sqlite:${migrated.path}").use { c ->
            c.createStatement().use { st -> schemaV1.forEach { st.execute(it) } }
        }
        KairosStore.open(driver(migrated))
        val fresh = Files.createTempFile("kairos-f", ".db").toFile().also { it.delete() }
        KairosStore.open(driver(fresh))
        fun columns(f: File) = java.sql.DriverManager.getConnection("jdbc:sqlite:${f.path}").use { c ->
            c.createStatement().executeQuery("SELECT name FROM pragma_table_info('task')").use { rs ->
                generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
            }
        }
        assertEquals(columns(fresh), columns(migrated))
        // E3 a ajouté trois colonnes à la suite de celles d'E1 (mêmes positions qu'en base migrée).
        assertEquals(listOf("space", "assignee_id", "progress_percent", "started_on", "team_uid"), columns(fresh).takeLast(5))
        migrated.delete()
        fresh.delete()
    }
}
