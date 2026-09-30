package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.team.MemberAbsence
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

/** Espace Équipe, jalon E2 (docs/spec/equipe.md § Dépôt) : membres, absences, suppression des données d'équipe. */
class TeamMembersRepositoryTest {
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

    /** Dépôt sur une base vide (sans exemples) pour compter exactement. */
    private suspend fun emptyRepo(): KairosRepository = open().also {
        it.replaceAll(KairosSnapshot(settings = Settings(team = TeamSettings(enabled = true, identity = "id-1"))))
    }

    private fun task(id: Long, space: TaskSpace, assignee: Long?, status: TaskStatus = TaskStatus.TODO) =
        Task(id, "T$id", priority = 1, fibonacciPoints = 3, status = status, space = space, assigneeId = assignee, createdAt = now, updatedAt = now)

    // --- Membres ----------------------------------------------------------------

    @Test
    fun createMemberCleansTheNameAndDrawsAnUid() = runTest {
        val repo = emptyRepo()
        val a = repo.createMember("  Alex  ", " Dev ", 80, 7.0, false)!!
        val b = repo.createMember("Sam", "", 100, 6.5, false)!!
        val m = repo.snapshot.value.members.single { it.id == a }
        assertEquals("Alex", m.name)
        assertEquals("Dev", m.role)
        assertEquals(80, m.availabilityPercent)
        assertEquals(7.0, m.hoursPerDay)
        assertFalse(m.isSelf)
        assertFalse(m.archived)
        assertEquals(now, m.createdAt)
        assertEquals(now, m.updatedAt)
        assertTrue(m.uid.length >= 32)
        assertNotEquals(m.uid, repo.snapshot.value.members.single { it.id == b }.uid)
        assertEquals(6.5, repo.snapshot.value.members.single { it.id == b }.hoursPerDay)
    }

    @Test
    fun aBlankNameCreatesNothing() = runTest {
        val repo = emptyRepo()
        assertNull(repo.createMember("   ", "Dev", 100, 7.0, false))
        assertNull(repo.createMember("Alex", "Dev", 100, Double.NaN, false))
        assertTrue(repo.snapshot.value.members.isEmpty())
    }

    @Test
    fun outOfRangeValuesAreBoundedAsAGuard() = runTest {
        val repo = emptyRepo()
        val id = repo.createMember("A", "", 250, 99.0, false)!!
        val m = repo.snapshot.value.members.single { it.id == id }
        assertEquals(100, m.availabilityPercent)
        assertEquals(24.0, m.hoursPerDay)
        repo.updateMember(id, "A", "", 0, 0.1, false)
        val u = repo.snapshot.value.members.single { it.id == id }
        assertEquals(1, u.availabilityPercent)
        assertEquals(1.0, u.hoursPerDay)
    }

    @Test
    fun oneMemberAtMostIsMe() = runTest {
        val repo = emptyRepo()
        val a = repo.createMember("A", "", 100, 7.0, true)!!
        val b = repo.createMember("B", "", 100, 7.0, false)!!
        assertEquals(listOf(a), repo.snapshot.value.members.filter { it.isSelf }.map { it.id })

        // Créer un second « moi » retire le premier.
        val c = repo.createMember("C", "", 100, 7.0, true)!!
        assertEquals(listOf(c), repo.snapshot.value.members.filter { it.isSelf }.map { it.id })

        // Cocher « moi » sur B le retire à C.
        assertTrue(repo.updateMember(b, "B", "", 100, 7.0, true))
        assertEquals(listOf(b), repo.snapshot.value.members.filter { it.isSelf }.map { it.id })

        // Décocher ne le donne à personne.
        assertTrue(repo.updateMember(b, "B", "", 100, 7.0, false))
        assertTrue(repo.snapshot.value.members.none { it.isSelf })
    }

    @Test
    fun updateMemberChangesTheFieldsAndRefusesBadInput() = runTest {
        val repo = emptyRepo()
        val id = repo.createMember("Alex", "Dev", 100, 7.0, false)!!
        assertTrue(repo.updateMember(id, " Alexandre ", " Lead ", 60, 6.5, false))
        val m = repo.snapshot.value.members.single()
        assertEquals("Alexandre", m.name)
        assertEquals("Lead", m.role)
        assertEquals(60, m.availabilityPercent)
        assertEquals(6.5, m.hoursPerDay)
        assertEquals("Alexandre", m.name)
        assertFalse(repo.updateMember(id, "  ", "x", 50, 5.0, false))
        assertFalse(repo.updateMember(999, "Z", "", 50, 5.0, false))
        assertEquals(m, repo.snapshot.value.members.single())
    }

    @Test
    fun aMemberKeepsItsUidAndIdWhenEdited() = runTest {
        val repo = emptyRepo()
        val id = repo.createMember("Alex", "", 100, 7.0, false)!!
        val before = repo.snapshot.value.members.single()
        repo.updateMember(id, "Alex B", "", 100, 7.0, true)
        val after = repo.snapshot.value.members.single()
        assertEquals(before.uid, after.uid)
        assertEquals(before.id, after.id)
        assertEquals(before.createdAt, after.createdAt)
    }

    @Test
    fun archivingSendsOpenTeamTasksBackToTheBacklogAndKeepsTheDoneOnes() = runTest {
        val repo = emptyRepo()
        val a = repo.createMember("A", "", 100, 7.0, true)!!
        val b = repo.createMember("B", "", 100, 7.0, false)!!
        repo.replaceAll(
            repo.snapshot.value.copy(
                tasks = listOf(
                    task(1, TaskSpace.TEAM, a),
                    task(2, TaskSpace.TEAM, a, TaskStatus.DONE),
                    task(3, TaskSpace.TEAM, b),
                    task(4, TaskSpace.PERSONAL, null),
                    task(5, TaskSpace.TEAM, a, TaskStatus.ARCHIVED),
                ),
            ),
        )
        // Avant : la tâche assignée à « moi » est dans la vue Perso.
        assertEquals(listOf(1L, 2L, 4L, 5L), repo.personalSnapshot.value.tasks.map { it.id })

        assertTrue(repo.archiveMember(a))
        val all = repo.snapshot.value
        assertNull(all.tasks.single { it.id == 1L }.assigneeId) // au backlog
        assertEquals(TaskSpace.TEAM, all.tasks.single { it.id == 1L }.space)
        assertEquals(a, all.tasks.single { it.id == 2L }.assigneeId) // faite : gardée
        assertEquals(a, all.tasks.single { it.id == 5L }.assigneeId) // archivée : gardée
        assertEquals(b, all.tasks.single { it.id == 3L }.assigneeId)
        assertTrue(all.members.single { it.id == a }.archived)
        assertFalse(all.members.single { it.id == a }.isSelf)
        // La vue Perso n'a plus les tâches de l'archivé (plus de « moi »).
        assertEquals(listOf(4L), repo.personalSnapshot.value.tasks.map { it.id })
        // Déjà archivé : rien à faire.
        assertFalse(repo.archiveMember(a))
        assertFalse(repo.archiveMember(999))
    }

    @Test
    fun restoringBringsTheMemberBackWithoutItsOldTasks() = runTest {
        val repo = emptyRepo()
        val a = repo.createMember("A", "", 100, 7.0, true)!!
        repo.replaceAll(repo.snapshot.value.copy(tasks = listOf(task(1, TaskSpace.TEAM, a))))
        repo.archiveMember(a)
        assertTrue(repo.restoreMember(a))
        val m = repo.snapshot.value.members.single()
        assertFalse(m.archived)
        assertFalse(m.isSelf) // « moi » ne revient pas tout seul
        assertNull(repo.snapshot.value.tasks.single().assigneeId)
        assertFalse(repo.restoreMember(a))
        assertFalse(repo.restoreMember(999))
    }

    @Test
    fun anArchivedMemberCannotBecomeMe() = runTest {
        val repo = emptyRepo()
        val a = repo.createMember("A", "", 100, 7.0, false)!!
        repo.archiveMember(a)
        assertTrue(repo.updateMember(a, "A", "", 100, 7.0, true))
        assertFalse(repo.snapshot.value.members.single().isSelf)
    }

    @Test
    fun aMemberWhoNeverHadATaskCanBeDeletedWithItsAbsences() = runTest {
        val repo = emptyRepo()
        val a = repo.createMember("A", "", 100, 7.0, false)!!
        val b = repo.createMember("B", "", 100, 7.0, false)!!
        repo.addAbsence(a, day, day)
        repo.addAbsence(b, day, LocalDate(2026, 9, 29))
        assertTrue(repo.deleteMember(a))
        assertEquals(listOf(b), repo.snapshot.value.members.map { it.id })
        assertEquals(listOf(b), repo.snapshot.value.absences.map { it.memberId })
        assertFalse(repo.deleteMember(a)) // inconnu
    }

    @Test
    fun aMemberWhoHadATaskCannotBeDeletedWhateverTheStatus() = runTest {
        val repo = emptyRepo()
        val ids = listOf(TaskStatus.TODO, TaskStatus.DONE, TaskStatus.ARCHIVED).map { status ->
            repo.createMember("M$status", "", 100, 7.0, false)!!.also { id ->
                repo.replaceAll(repo.snapshot.value.copy(tasks = repo.snapshot.value.tasks + task(100 + id, TaskSpace.TEAM, id, status)))
            }
        }
        for (id in ids) {
            assertFalse(repo.deleteMember(id), "$id")
            assertTrue(repo.snapshot.value.members.any { it.id == id })
        }
        // Archivé (tâche remise au backlog), il garde l'historique s'il a une tâche faite, donc reste non supprimable.
        repo.archiveMember(ids[1])
        assertFalse(repo.deleteMember(ids[1]))
        // Une fois la tâche du premier remise au backlog par l'archivage, il n'a plus d'assigné : supprimable.
        repo.archiveMember(ids[0])
        assertTrue(repo.deleteMember(ids[0]))
    }

    // --- Absences ---------------------------------------------------------------

    @Test
    fun absencesAreAddedUpdatedAndDeleted() = runTest {
        val repo = emptyRepo()
        val a = repo.createMember("A", "", 100, 7.0, false)!!
        val from = LocalDate(2026, 10, 12)
        val to = LocalDate(2026, 10, 16)
        val id = repo.addAbsence(a, from, to, "  Congés ")!!
        assertEquals(MemberAbsence(id, a, from, to, "Congés", now), repo.snapshot.value.absences.single())

        assertTrue(repo.updateAbsence(id, from, from, ""))
        assertEquals(MemberAbsence(id, a, from, from, "", now), repo.snapshot.value.absences.single())

        // Un seul jour (fin = début) est valide.
        assertNotNull(repo.addAbsence(a, to, to))

        repo.deleteAbsence(id)
        assertEquals(1, repo.snapshot.value.absences.size)
        repo.deleteAbsence(12345) // inconnue : sans effet
        assertEquals(1, repo.snapshot.value.absences.size)
    }

    @Test
    fun anAbsenceWhoseEndPrecedesItsStartIsRefused() = runTest {
        val repo = emptyRepo()
        val a = repo.createMember("A", "", 100, 7.0, false)!!
        val from = LocalDate(2026, 10, 12)
        assertNull(repo.addAbsence(a, from, LocalDate(2026, 10, 11)))
        val id = repo.addAbsence(a, from, from)!!
        assertFalse(repo.updateAbsence(id, from, LocalDate(2026, 10, 11), "x"))
        assertEquals(from, repo.snapshot.value.absences.single().end)
        assertTrue(repo.snapshot.value.absences.size == 1)
    }

    @Test
    fun anAbsenceForAnUnknownMemberOrAbsenceIsRefused() = runTest {
        val repo = emptyRepo()
        assertNull(repo.addAbsence(42, day, day))
        assertFalse(repo.updateAbsence(42, day, day, ""))
        assertTrue(repo.snapshot.value.absences.isEmpty())
    }

    // --- clearTeamData ----------------------------------------------------------

    @Test
    fun clearTeamDataRemovesEverythingOfTheTeamAndNothingElse() = runTest {
        val repo = open()
        val solo = repo.snapshot.value
        val maxId = solo.tasks.maxOf { it.id }
        val personalId = solo.tasks.first().id
        val a = maxId + 1
        val teamTask = maxId + 2
        val teamTask2 = maxId + 3
        val settings = solo.settings.copy(team = TeamSettings(enabled = true, name = "P", managerName = "C", identity = "id-1", lastSpace = "team"))
        val member = TeamMember(1, "u1", "A", hoursPerDay = 7.0, isSelf = true, createdAt = now, updatedAt = now)
        repo.replaceAll(
            solo.copy(
                settings = settings,
                members = listOf(member),
                absences = listOf(MemberAbsence(1, 1, day, day, "x", now)),
                tasks = solo.tasks.map { if (it.id == personalId) it.copy(assigneeId = 1) else it } +
                    task(teamTask, TaskSpace.TEAM, 1) + task(teamTask2, TaskSpace.TEAM, null) + task(a, TaskSpace.PERSONAL, 1),
                dependencies = solo.dependencies + TaskDependency(900, teamTask, teamTask2, now) + TaskDependency(901, personalId, teamTask, now),
                workSessions = solo.workSessions + WorkSession(900, teamTask, now, now, now),
            ),
        )
        assertTrue(Workspaces.hasTeamData(repo.snapshot.value))

        repo.clearTeamData()
        val after = repo.snapshot.value
        assertTrue(after.members.isEmpty())
        assertTrue(after.absences.isEmpty())
        assertTrue(after.tasks.none { it.space == TaskSpace.TEAM })
        assertTrue(after.tasks.all { it.assigneeId == null })
        // Les tâches Perso (dont celle qui avait un assigné résiduel) sont toutes là.
        assertEquals(solo.tasks.map { it.id }.toSet() + a, after.tasks.map { it.id }.toSet())
        assertEquals(solo.dependencies, after.dependencies)
        assertEquals(solo.workSessions, after.workSessions)
        assertEquals(solo.notes, after.notes)
        assertEquals(solo.timeBlocks, after.timeBlocks)
        // Les réglages d'équipe restent.
        assertEquals(settings, after.settings)
        assertFalse(Workspaces.hasTeamData(after))
        assertSame(after, repo.personalSnapshot.value)
    }

    @Test
    fun clearTeamDataOnASoloDatabaseChangesNothing() = runTest {
        val repo = open()
        val before = repo.snapshot.value
        repo.clearTeamData()
        assertEquals(before, repo.snapshot.value)
    }

    // --- Export et remplacement ---------------------------------------------------

    private fun teamSnapshot() = KairosSnapshot(
        tasks = listOf(task(2, TaskSpace.TEAM, 10)),
        members = listOf(
            TeamMember(10, "uid-10", "Moi", "Lead", 80, 6.5, true, false, now, now),
            TeamMember(11, "uid-11", "Ancien", hoursPerDay = 7.0, archived = true, createdAt = now, updatedAt = now),
        ),
        absences = listOf(
            MemberAbsence(1, 10, LocalDate(2026, 10, 12), LocalDate(2026, 10, 16), "Congés", now),
            MemberAbsence(2, 11, LocalDate(2026, 11, 2), LocalDate(2026, 11, 2), "", now),
        ),
        settings = Settings(team = TeamSettings(enabled = true, identity = "id-1")),
    )

    @Test
    fun absencesRoundTripThroughTheExportInVersionTwo() {
        val snapshot = teamSnapshot()
        val text = ExportCodec.encode(snapshot, "3.0.0", now)
        assertTrue("\"formatVersion\": 2" in text)
        assertTrue("\"absences\"" in text)
        assertEquals(snapshot, ExportCodec.decode(text))
    }

    @Test
    fun anAbsenceAloneMakesTheExportVersionTwo() {
        val snapshot = KairosSnapshot(absences = listOf(MemberAbsence(1, 5, day, day, "", now)))
        val text = ExportCodec.encode(snapshot, "3.0.0", now)
        assertTrue("\"formatVersion\": 2" in text)
        assertEquals(snapshot, ExportCodec.decode(text))
    }

    @Test
    fun aSoloExportHasNoAbsenceField() {
        val text = ExportCodec.encode(examples(), "3.0.0", now)
        assertFalse("absences" in text)
        assertTrue(ExportCodec.decode(text).absences.isEmpty())
    }

    @Test
    fun aVersionTwoExportWithoutAbsencesStillDecodes() {
        val text = """{"format":"kairos-export","formatVersion":2,"members":[{"id":1,"uid":"u","name":"A","hoursPerDay":7.0,"createdAt":"2026-09-28T07:00:00Z","updatedAt":"2026-09-28T07:00:00Z"}]}"""
        val decoded = ExportCodec.decode(text)
        assertEquals(1, decoded.members.size)
        assertTrue(decoded.absences.isEmpty())
    }

    @Test
    fun replaceAllStoresTheAbsencesWithTheirIds() = runTest {
        val repo = open()
        val snapshot = teamSnapshot()
        repo.replaceAll(snapshot)
        assertEquals(snapshot, repo.snapshot.value)
        repo.load()
        assertEquals(snapshot.absences, repo.snapshot.value.absences)
        repo.replaceAll(KairosSnapshot())
        assertTrue(repo.snapshot.value.absences.isEmpty())
    }

    @Test
    fun aDeletedAbsenceIdIsNotReusedForTheNextOne() = runTest {
        val repo = emptyRepo()
        val a = repo.createMember("A", "", 100, 7.0, false)!!
        val first = repo.addAbsence(a, day, day)!!
        repo.deleteAbsence(first)
        val second = repo.addAbsence(a, day, day)!!
        assertNotEquals(first, second)
    }

    // --- Migration 2 -> 3 -------------------------------------------------------

    /** Schéma de la version 1 recopié en dur (il ne doit jamais suivre `Kairos.sq`). */
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
    )

    /** Ce que `1.sqm` ajoute : le schéma 2, celui de l'espace Équipe au jalon E1. */
    private val schemaV2Additions = listOf(
        "ALTER TABLE task ADD COLUMN space INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE task ADD COLUMN assignee_id INTEGER",
        "CREATE INDEX task_space ON task(space)",
        "CREATE INDEX task_assignee ON task(assignee_id)",
        """CREATE TABLE team_member (id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL, name TEXT NOT NULL,
            role TEXT NOT NULL DEFAULT '', availability_percent INTEGER NOT NULL DEFAULT 100, hours_per_day REAL NOT NULL,
            is_self INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)""",
    )

    private val schemaV2Data = listOf(
        """INSERT INTO task (id, title, priority, fibonacci_points, created_at, updated_at, space, assignee_id) VALUES
            (1, 'Perso', 1, 3, '2026-09-01T08:00:00Z', '2026-09-01T08:00:00Z', 0, NULL),
            (7, 'Équipe', 0, 5, '2026-09-02T08:00:00Z', '2026-09-02T08:00:00Z', 1, 3)""",
        """INSERT INTO team_member (id, uid, name, role, availability_percent, hours_per_day, is_self, archived, created_at, updated_at) VALUES
            (3, 'uid-3', 'Alex', 'Dev', 80, 6.5, 1, 0, '2026-09-01T08:00:00Z', '2026-09-01T08:00:00Z')""",
        """INSERT INTO settings (id, json) VALUES (1, '{"meetingBufferMinutes":9,"team":{"enabled":true,"identity":"id-1"}}')""",
    )

    private fun createDatabase(version: Int, vararg statements: List<String>): File {
        val file = Files.createTempFile("kairos-v$version", ".db").toFile().also { it.delete() }
        java.sql.DriverManager.getConnection("jdbc:sqlite:${file.path}").use { c ->
            c.createStatement().use { st ->
                statements.forEach { list -> list.forEach { st.execute(it) } }
                st.execute("PRAGMA user_version = $version")
            }
        }
        return file
    }

    @Test
    fun aSchemaTwoDatabaseIsMigratedWithoutLosingARow() = runTest {
        val file = createDatabase(2, schemaV1, schemaV2Additions, schemaV2Data)

        val opened = KairosStore.open(driver(file))
        assertFalse(opened.created)
        val repo = KairosRepository.open(opened, examples, clock, timeZone = TimeZone.UTC)

        val all = repo.snapshot.value
        assertEquals(listOf(1L, 7L), all.tasks.map { it.id })
        assertEquals(TaskSpace.TEAM, all.tasks.single { it.id == 7L }.space)
        assertEquals(3L, all.tasks.single { it.id == 7L }.assigneeId)
        val member = all.members.single()
        assertEquals("Alex", member.name)
        assertEquals(6.5, member.hoursPerDay)
        assertTrue(member.isSelf)
        assertEquals(9, all.settings.meetingBufferMinutes)
        assertTrue(all.settings.team!!.enabled)
        // La nouvelle table naît vide : rien d'équipe de plus qu'avant.
        assertTrue(all.absences.isEmpty())

        // Elle est utilisable, et l'archivage/suppression fonctionnent sur la base migrée.
        val absence = repo.addAbsence(3, day, LocalDate(2026, 9, 29), "Congés")!!
        assertEquals(1, repo.snapshot.value.absences.size)
        assertTrue(repo.archiveMember(3))
        assertNull(repo.snapshot.value.tasks.single { it.id == 7L }.assigneeId)
        repo.deleteAbsence(absence)
        repo.clearTeamData()
        assertEquals(listOf(1L), repo.snapshot.value.tasks.map { it.id })

        // La version est à jour : une réouverture ne migre plus.
        val again = open(file)
        assertEquals(1, again.snapshot.value.tasks.size)
        file.delete()
    }

    @Test
    fun aSchemaOneDatabaseGoesThroughTheWholeChainToThree() = runTest {
        val file = createDatabase(
            1, schemaV1,
            listOf("INSERT INTO task (id, title, priority, fibonacci_points, created_at, updated_at) VALUES (1, 'Ancienne', 1, 3, '2026-09-01T08:00:00Z', '2026-09-01T08:00:00Z')"),
        )
        val repo = KairosRepository.open(KairosStore.open(driver(file)), examples, clock, timeZone = TimeZone.UTC)
        assertEquals(listOf(1L), repo.snapshot.value.tasks.map { it.id })
        assertTrue(repo.snapshot.value.absences.isEmpty())
        val member = repo.createMember("A", "", 100, 7.0, false)!!
        assertNotNull(repo.addAbsence(member, day, day))
        file.delete()
    }

    @Test
    fun aMigratedAndAFreshDatabaseHaveTheSameAbsenceColumns() = runTest {
        val migrated = createDatabase(2, schemaV1, schemaV2Additions)
        KairosStore.open(driver(migrated))
        val fresh = Files.createTempFile("kairos-f", ".db").toFile().also { it.delete() }
        KairosStore.open(driver(fresh))
        fun columns(f: File) = java.sql.DriverManager.getConnection("jdbc:sqlite:${f.path}").use { c ->
            c.createStatement().executeQuery("SELECT name FROM pragma_table_info('member_absence')").use { rs ->
                generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
            }
        }
        assertEquals(listOf("id", "member_id", "start", "end", "label", "created_at"), columns(fresh))
        assertEquals(columns(fresh), columns(migrated))
        migrated.delete()
        fresh.delete()
    }
}
