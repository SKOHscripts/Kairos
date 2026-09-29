package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.core.legacy.Kairos2Import
import com.skohscripts.kairos.core.legacy.NotALegacyDatabase
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.NoteStatus
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Migration de vraies bases Kairos 2 (docs/spec/migration-2x.md § Tests),
 * générées par `kmp/tools/gen_legacy_db.py` avec les modèles SQLAlchemy de
 * Kairos 2 : schéma final et schéma de la phase 1.
 */
class LegacyMigrationTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")

    private fun resource(path: String): File {
        // Copie : la ressource peut être dans un JAR, et on vérifie que l'original n'est pas modifié.
        val source = requireNotNull(javaClass.getResource("/legacy/$path")) { "base de test absente : legacy/$path (gen_legacy_db.py)" }
        val dir = createTempDirectory("kairos2").toFile()
        return File(dir, File(path).name).also { target -> source.openStream().use { Files.copy(it, target.toPath()) } }
    }

    @Test
    fun theFinalSchemaMigratesWithoutLoss() {
        val db = resource("current/tasks.db")
        val before = db.readBytes()
        val (snapshot, report) = Kairos2Import.convert(LegacyFiles.read(db, resource("current/settings.json")), "fr", now)
        assertTrue(before.contentEquals(db.readBytes()), "l'ancienne base ne doit jamais être modifiée")

        // Comptes : 5 tâches, 1 créneau (TimeTree ignoré), 1 dépendance (l'orpheline écartée), 2 sessions, 2 notes.
        assertEquals(listOf(5, 1, 1, 2, 2, 1), with(report) { listOf(tasks, timeBlocks, dependencies, sessions, notes, skippedExternalBlocks) })

        val t = snapshot.tasks.associateBy { it.id }
        with(t.getValue(1)) {
            assertEquals("Voir le ticket\nsur deux lignes", description)
            assertEquals(0, priority)
            assertEquals(5, fibonacciPoints)
            assertEquals(LocalDate(2026, 10, 2), deadline)
            assertEquals(Instant.parse("2026-09-01T08:00:00Z"), createdAt)
            assertEquals(Instant.parse("2026-09-20T09:15:00Z"), updatedAt)
        }
        // GitLab → tâche native, projet gardé ; ancienne clé de type remplacée par son libellé.
        with(t.getValue(2)) {
            assertEquals("infra", projectTag)
            assertEquals(TaskStatus.DONE, status)
            assertEquals("Développement", taskType)
        }
        with(t.getValue(3)) {
            assertEquals(1L, parentId)
            assertEquals(TaskRecurrence.WEEKLY, recurrence)
            assertEquals(2, recurrenceDayOfWeek)
            assertEquals(LocalDateTime(2026, 9, 29, 14, 30), pinnedStart)
            assertEquals(20, manualTimeSpentMinutes)
        }
        with(t.getValue(4)) {
            assertEquals(TaskStatus.ARCHIVED, status)
            assertEquals(TaskRecurrence.MONTHLY_ON_DAY, recurrence)
            assertEquals(23, recurrenceDayOfMonth)
            assertEquals("2026-09", recurrencePeriod)
        }
        // Hors échelle : la tâche revient « À traiter » plutôt que de porter une valeur impossible.
        with(t.getValue(5)) {
            assertNull(priority)
            assertNull(fibonacciPoints)
            assertNull(estimatedMinutes)
        }

        val block = snapshot.timeBlocks.single()
        assertEquals(BlockKind.DEEPWORK to BlockRecurrence.WEEKLY, block.kind to block.recurrence)
        assertEquals(LocalDateTime(2026, 9, 29, 10, 0), block.start)
        assertEquals(1L to 3L, snapshot.dependencies.single().let { it.taskId to it.blockerId })
        assertNull(snapshot.workSessions.single { it.id == 2L }.endedAt)
        assertEquals(NoteStatus.ARCHIVED to 2L, snapshot.notes.single { it.id == 2L }.let { it.status to it.convertedTaskId })

        // Réglages repris champ par champ ; secrets et intégrations retirées jamais repris.
        with(snapshot.settings) {
            assertEquals(8, workdayStartHour)
            assertEquals(2.5, priorityValueBase)
            assertEquals(false, cognitiveDipEnabled)
            assertEquals("Dev,Ops", taskTypes)
            assertEquals("2026-12-24", extraHolidays)
        }
        assertTrue("gitlab_token" in report.ignoredSettings && "timetree_email" in report.ignoredSettings)
        assertTrue("workday_start_hour" !in report.ignoredSettings)
    }

    @Test
    fun thePhase1SchemaMigratesWithDefaults() {
        val (snapshot, report) = Kairos2Import.convert(LegacyFiles.read(resource("phase1/tasks.db")), "en", now)
        assertEquals(listOf(2, 1, 0, 0, 0, 1), with(report) { listOf(tasks, timeBlocks, dependencies, sessions, notes, skippedExternalBlocks) })
        val old = snapshot.tasks.single { it.id == 1L }
        assertEquals(TaskRecurrence.NONE, old.recurrence)
        assertNull(old.fibonacciPoints)
        assertTrue(old.needsProcessing)
        assertEquals("Development,Code review,Meeting,Documentation,Administration,Learning", snapshot.settings.taskTypes)
        assertEquals(BlockKind.BUSY, snapshot.timeBlocks.single().kind)
    }

    @Test
    fun theUsualLocationIsFoundAndAConfiguredPathWins() {
        val root = createTempDirectory("kairos-root").toFile()
        val env = mapOf("KAIROS_DATA_DIR" to root.absolutePath)
        assertNull(LegacyFiles.find(env))
        resource("current/tasks.db").copyTo(File(root, "tasks.db"))
        assertEquals(File(root, "tasks.db"), LegacyFiles.find(env)?.database)
        val elsewhere = resource("phase1/tasks.db")
        File(root, "settings.json").writeText("""{"settings": {"tasks_database_path": "${elsewhere.absolutePath.replace("\\", "\\\\")}"}}""")
        val found = LegacyFiles.find(env)!!
        assertEquals(elsewhere, found.database)
        assertEquals(File(root, "settings.json"), found.settings)
    }

    @Test
    fun anythingElseIsRefused() {
        val text = File.createTempFile("export", ".json").apply { writeText("{}") }
        assertFailsWith<NotALegacyDatabase> { LegacyFiles.read(text) }
        // Une base SQLite qui n'est pas celle de Kairos 2 (pas de table `task`).
        val other = File.createTempFile("other", ".db")
        java.sql.DriverManager.getConnection("jdbc:sqlite:${other.absolutePath}").use { it.createStatement().execute("CREATE TABLE x(id INTEGER)") }
        assertFailsWith<NotALegacyDatabase> { Kairos2Import.convert(LegacyFiles.read(other), "fr", now) }
    }
}
