package com.skohscripts.kairos.desktop

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.data.ExportCodec
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.DemoSession
import com.skohscripts.kairos.ui.app.DemoSource
import com.skohscripts.kairos.ui.app.NoNotifier
import com.skohscripts.kairos.ui.app.forDemo
import com.skohscripts.kairos.ui.app.openDemo
import com.skohscripts.kairos.ui.guide.demo.DemoSteps
import com.skohscripts.kairos.ui.guide.demo.DemoTour
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Démonstration jetable (docs/spec/visite-guidee.md § Visite interactive, issue #96), sans interface : le script de six
 * étapes, l'isolation des services et de la base réelle. Horloge figée au mardi 29 septembre 2026.
 */
class DemoStepsTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val today = LocalDate(2026, 9, 29)
    private val title = "Prepare Monday’s meeting"

    private fun demo(): DemoSession = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        openDemo(KairosStore.open(driver), "en", close = { driver.close() }, clock = clock)
    }

    @Test
    fun sixStepsInOrderEachDoneByItsActOnAFreshDemo() = runBlocking {
        val session = demo()
        val tour = DemoTour(session.repository, today = { today })
        assertEquals(listOf("capture", "priority", "points", "timer_start", "timer_stop", "move"), tour.steps.map { it.id })
        // Le jeu d'exemples seul : aucune tâche de l'utilisateur.
        assertNull(DemoSteps.trackedTask(session.repository.snapshot.value))
        tour.steps.forEachIndexed { i, step ->
            tour.observe(session.repository.snapshot.value)
            assertEquals(i, tour.index, step.id)
            assertFalse(step.done(session.repository.snapshot.value, tour.context(title)), "${step.id} : déjà fait avant d'agir")
            step.act(tour.context(title))
            assertTrue(step.done(session.repository.snapshot.value, tour.context(title)), "${step.id} : « Faire pour moi » sans effet")
        }
        tour.observe(session.repository.snapshot.value)
        assertTrue(tour.finished)
        val task = session.repository.snapshot.value.tasks.single { it.id == tour.taskId }
        assertEquals(title, task.title)
        assertEquals(today.plusDays(1), task.scheduledDate)
        session.close()
    }

    @Test
    fun theTrackedTaskThatDisappearsBringsTheTourBackToTheFirstStep() = runBlocking {
        val session = demo()
        val tour = DemoTour(session.repository, today = { today })
        tour.steps[0].act(tour.context(title))
        tour.observe(session.repository.snapshot.value)
        assertEquals(1, tour.index)
        val id = assertNotNull(tour.taskId)
        session.repository.deleteTask(id)
        tour.observe(session.repository.snapshot.value)
        assertEquals(0, tour.index)
        assertNull(tour.taskId)
        session.close()
    }

    @Test
    fun skippedStepsStillLeadToTheirEffect() = runBlocking {
        // « Passer l’étape » jusqu'à l'arrêt du chrono : « Faire pour moi » crée la tâche et démarre avant d'arrêter.
        val session = demo()
        val tour = DemoTour(session.repository, today = { today })
        repeat(4) { tour.next() }
        assertEquals("timer_stop", tour.step.id)
        tour.step.act(tour.context(title))
        tour.observe(session.repository.snapshot.value)
        assertEquals("move", tour.step.id)
        session.close()
    }

    @Test
    fun demoServicesCarryNothingFromTheRealOnes() = runBlocking {
        val dir = Files.createTempDirectory("kairos-demo").toFile()
        try {
            // Les vrais services du bureau, garnis : fichier lié, mises à jour, emplacement, démonstration.
            val real = DesktopServices.open(dir, diagnostics = FileDiagnostics(dir))
            assertNotNull(real.linkedStorage)
            assertNotNull(real.updates)
            assertNotNull(real.demo)
            val session = demo()
            val demo = real.forDemo(session)
            assertSame(session.repository, demo.repository)
            assertSame(real.clock, demo.clock)
            assertSame(NoNotifier, demo.notifier)
            assertEquals(real.systemLanguage, demo.systemLanguage)
            assertFalse(demo.files.saveText("x.json", "{}"))
            assertNull(demo.files.openText())
            // Chaque champ d'AppServices est décidé dans forDemo : un champ nouveau fait échouer ce test.
            val fields = AppServices::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("$") }.toSet()
            val carried = setOf("repository", "files", "backups", "clock", "notifier", "teamUi", "systemLanguage", "applyLanguage")
            val empty = setOf("dataLocation", "linkedStorage", "firstLaunch", "updates", "downloadPage", "dataFolder", "diagnostics", "calendars", "shortcuts", "demo")
            assertEquals(carried + empty, fields)
            for (name in empty) {
                val field = AppServices::class.java.getDeclaredField(name).apply { isAccessible = true }
                val value = field.get(demo)
                assertTrue(value == null || value == false, "$name : repris des vrais services")
            }
            session.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aWholeDemoLeavesTheRealBaseAndItsExportUntouched() = runBlocking {
        var changes = 0
        val real = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock, onChanged = { changes++ })
        real.createTask("Real task")
        val before = real.snapshot.value
        val export = ExportCodec.encode(before, "3.13.0", now)
        val writes = changes
        val source = DemoSource { lang ->
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            openDemo(KairosStore.open(driver), lang, close = { driver.close() }, clock = clock)
        }
        val session = source.open("en")
        val tour = DemoTour(session.repository, today = { today })
        while (!tour.finished) {
            tour.step.act(tour.context(title))
            tour.observe(session.repository.snapshot.value)
        }
        session.close()
        assertEquals(before, real.snapshot.value)
        assertEquals(export, ExportCodec.encode(real.snapshot.value, "3.13.0", now))
        assertEquals(writes, changes)
    }

    private fun LocalDate.plusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() + n)
}
