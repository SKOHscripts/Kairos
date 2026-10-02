package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.model.TaskSpace
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Espace Équipe, jalon E6 : migration 5 -> 6 (`5.sqm`), sans perte, et chaîne complète 1 -> 6. */
class ExchangeMigrationTest {
    private val now = Instant.parse("2026-09-28T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val examples = { ExampleData.snapshot(LocalDate(2026, 9, 28), now, "fr") }

    private fun driver(file: File) = JdbcSqliteDriver("jdbc:sqlite:${file.path}")

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

    private fun userVersion(f: File) = java.sql.DriverManager.getConnection("jdbc:sqlite:${f.path}").use { c ->
        c.createStatement().executeQuery("PRAGMA user_version").use { it.getInt(1) }
    }

    private fun freshDatabase(): File {
        val f = Files.createTempFile("kairos-fresh", ".db").toFile().also { it.delete() }
        kotlinx.coroutines.runBlocking { KairosStore.open(driver(f)) }
        return f
    }

    /** Schéma de la version 5 (fin du jalon E5), recopié en dur : il ne doit jamais suivre `Kairos.sq`. */
    private val schemaV5 = listOf(
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
        """CREATE TABLE team_scenario (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, modifications TEXT NOT NULL,
            created_at TEXT NOT NULL, updated_at TEXT NOT NULL)""",
        "CREATE TABLE settings (id INTEGER PRIMARY KEY CHECK (id = 1), json TEXT NOT NULL)",
        """INSERT INTO task (id, title, priority, fibonacci_points, space, assignee_id, progress_percent, started_on, team_uid, created_at, updated_at) VALUES
            (1, 'Perso', 1, 3, 0, NULL, NULL, NULL, NULL, '2026-09-01T08:00:00Z', '2026-09-01T08:00:00Z'),
            (2, 'Équipe', 0, 5, 1, 1, 40, '2026-09-30', 'uid-task-2', '2026-09-02T08:00:00Z', '2026-09-02T08:00:00Z')""",
        """INSERT INTO team_member (id, uid, name, role, availability_percent, hours_per_day, is_self, archived, created_at, updated_at)
            VALUES (1, 'uid-1', 'Alex', 'Dev', 80, 6.5, 1, 0, '2026-09-01T08:00:00Z', '2026-09-01T08:00:00Z')""",
        "INSERT INTO team_event (id, task_id, task_title, member_id, kind, from_value, to_value, source, at) VALUES (1, 2, 'Équipe', 1, 'assigned', NULL, '1', 'manual', '2026-09-02T08:00:00Z')",
        "INSERT INTO team_scenario (id, name, modifications, created_at, updated_at) VALUES (1, 'Et si', '[]', '2026-09-03T08:00:00Z', '2026-09-03T08:00:00Z')",
        """INSERT INTO settings (id, json) VALUES (1, '{"meetingBufferMinutes":9,"team":{"enabled":true,"name":"P","identity":"id-1"}}')""",
        "PRAGMA user_version = 5",
    )

    @Test
    fun aSchemaFiveDatabaseIsMigratedWithoutLosingARow() = runTest {
        val file = databaseFrom(schemaV5)
        val opened = KairosStore.open(driver(file))
        assertFalse(opened.created)
        assertEquals(6, userVersion(file))
        val repo = KairosRepository.open(opened, examples, clock, timeZone = TimeZone.UTC)

        val all = repo.snapshot.value
        assertEquals(listOf(1L, 2L), all.tasks.map { it.id })
        assertEquals(listOf("Perso", "Équipe"), all.tasks.map { it.title })
        assertEquals(TaskSpace.TEAM, all.tasks[1].space)
        assertEquals(40, all.tasks[1].progressPercent)
        assertEquals("uid-task-2", all.tasks[1].teamUid)
        // Les nouvelles colonnes naissent vides : aucune tâche n'est « reçue », aucun temps rapporté.
        assertTrue(all.tasks.all { it.origin == null && !it.originRemoved && it.reportedMinutes == null })
        assertEquals("Alex", all.members.single().name)
        assertNull(all.members.single().lastReportAt)
        assertEquals(1, all.teamEvents.size)
        assertEquals(1, all.teamScenarios.size)
        assertEquals("id-1", all.settings.team!!.identity)
        assertEquals(9, all.settings.meetingBufferMinutes)

        // Les nouvelles colonnes servent sur la base migrée.
        repo.integrateReport(
            TeamExchangeCodec.encodeReport(
                com.skohscripts.kairos.core.team.exchange.TeamReport(
                    now, "id-1", com.skohscripts.kairos.core.team.exchange.PackMember("uid-1", "Alex"),
                    listOf(com.skohscripts.kairos.core.team.exchange.ReportTask("uid-task-2", com.skohscripts.kairos.core.model.TaskStatus.TODO, null, null, 60, 90)),
                ),
                "3",
            ),
        )
        assertEquals(90, repo.snapshot.value.tasks[1].reportedMinutes)
        assertEquals(now, repo.snapshot.value.members.single().lastReportAt)

        // Réouverture : rien ne migre plus, tout est gardé.
        val again = KairosRepository.open(KairosStore.open(driver(file)), examples, clock, timeZone = TimeZone.UTC)
        assertEquals(repo.snapshot.value, again.snapshot.value)
        file.delete()
    }

    @Test
    fun aMigratedAndAFreshDatabaseHaveTheSameColumnsInTheSameOrder() = runTest {
        val migrated = databaseFrom(schemaV5)
        KairosStore.open(driver(migrated))
        val fresh = freshDatabase()
        for (table in listOf("task", "team_member", "team_event", "member_absence", "team_scenario")) {
            assertEquals(columns(fresh, table), columns(migrated, table), table)
        }
        assertEquals(listOf("origin", "origin_removed", "reported_minutes"), columns(fresh, "task").takeLast(3))
        assertEquals("last_report_at", columns(fresh, "team_member").last())
        migrated.delete()
        fresh.delete()
    }

    @Test
    fun aSchemaOneDatabaseGoesThroughTheWholeChainToSix() = runTest {
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
        assertEquals(6, userVersion(file))
        val all = repo.snapshot.value
        assertEquals(listOf(1L), all.tasks.map { it.id })
        assertTrue(all.members.isEmpty() && all.teamEvents.isEmpty() && all.teamScenarios.isEmpty())
        assertSame(all, repo.personalSnapshot.value)
        assertNull(all.tasks.single().origin)
        // Un Kairos qui n'a jamais reçu de paquet est inchangé : ni bouton, ni export d'équipe.
        assertTrue(repo.origins().isEmpty())
        assertTrue("\"formatVersion\": 1" in ExportCodec.encode(all, "3.0.0", now))
        val fresh = freshDatabase()
        for (table in listOf("task", "team_member", "team_event", "member_absence", "team_scenario")) {
            assertEquals(columns(fresh, table), columns(file, table), table)
        }
        file.delete()
        fresh.delete()
    }
}
