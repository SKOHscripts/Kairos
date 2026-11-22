package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.engine.Recurrence
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Séries « le N du mois » (docs/spec/recurrence.md, issue #282) : une occurrence supprimée ne revient plus quand on le demande,
 * et les sous-tâches restent sous leur mère. Le cas d'un utilisateur : « PV EDF » le 23, trois sous-tâches en septembre, recréées
 * chaque mois au premier niveau ; aujourd'hui le mercredi 2 décembre 2026.
 */
class CalendarSeriesTest {
    private val now = Instant.parse("2026-12-02T08:00:00Z")
    private val today = LocalDate(2026, 12, 2)
    private val t0 = Instant.parse("2026-08-01T00:00:00Z")

    private fun t(id: Long, title: String, period: String, parent: Long? = null, status: TaskStatus = TaskStatus.DONE) = Task(
        id, title, status = status, recurrence = TaskRecurrence.MONTHLY_ON_DAY, recurrenceDayOfMonth = 23, recurrencePeriod = period,
        deadline = LocalDate.parse("$period-23"), scheduledDate = LocalDate.parse("$period-23"), parentId = parent, priority = 1,
        fibonacciPoints = 2, createdAt = t0, updatedAt = t0,
    )

    private val tasks = listOf(
        t(254, "PV EDF", "2026-09"),
        t(281, "Faire la réception", "2026-09", parent = 254),
        t(282, "Envoyer pour signature", "2026-09", parent = 254),
        t(283, "Enregistrer les pièces jointes", "2026-09", parent = 254),
        // Orphelines créées chaque mois par l'ancien comportement.
        t(413, "Faire la réception", "2026-10"),
        t(414, "Envoyer pour signature", "2026-10"),
        t(412, "PV EDF", "2026-10"),
        t(466, "PV EDF", "2026-11", status = TaskStatus.TODO),
        t(467, "Faire la réception", "2026-11", status = TaskStatus.TODO),
    )

    private suspend fun repo(): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, object : Clock {
            override fun now() = now
        }, timeZone = TimeZone.UTC).also { it.replaceAll(KairosSnapshot(tasks = tasks, settings = Settings(holidaysFr = false))) }

    private fun KairosRepository.december() = snapshot.value.tasks.filter { it.recurrencePeriod == "2026-12" || it.deadline?.let { d -> d.month.ordinal == 11 } == true }

    @Test
    fun theMonthOccurrenceComesWithItsSubtasksAndNoOrphanSeries() = runTest {
        val r = repo()
        r.ensureCalendarOccurrences(today)
        val created = r.december()
        val mother = created.single { it.parentId == null }
        assertEquals("PV EDF", mother.title)
        assertEquals(LocalDate(2026, 12, 23), mother.deadline)
        val children = created.filter { it.parentId == mother.id }
        assertEquals(listOf("Faire la réception", "Envoyer pour signature", "Enregistrer les pièces jointes"), children.map { it.title })
        assertTrue(children.all { it.recurrence == TaskRecurrence.NONE && it.deadline == mother.deadline && it.status == TaskStatus.TODO })
        // Rien d'autre : les séries des sous-tâches (orphelines comprises) ne sont pas recréées.
        assertEquals(4, created.size)
        r.ensureCalendarOccurrences(today)
        assertEquals(4, r.december().size)
    }

    @Test
    fun onlyThisMonthDeletesItAndItDoesNotComeBack() = runTest {
        val r = repo()
        r.ensureCalendarOccurrences(today)
        val mother = r.december().single { it.parentId == null }
        r.skipCalendarMonth(mother.id)
        assertTrue(r.december().isEmpty(), "l'occurrence et ses sous-tâches")
        assertEquals("2026-12", r.snapshot.value.settings.recurrence?.skipIgnored?.get(Recurrence.seriesKey(23, "PV EDF")))
        r.ensureCalendarOccurrences(today)
        assertTrue(r.december().isEmpty())
        // Le mois suivant, la série revient.
        r.ensureCalendarOccurrences(LocalDate(2027, 1, 4))
        assertEquals(1, r.snapshot.value.tasks.count { it.title == "PV EDF" && it.recurrencePeriod == "2027-01" })
    }

    @Test
    fun stoppingTheSeriesEndsItAndKeepsTheHistory() = runTest {
        val r = repo()
        r.stopCalendarSeries(466)
        val pv = r.snapshot.value.tasks.filter { it.title == "PV EDF" }
        assertEquals(listOf(254L, 412L), pv.map { it.id })
        assertTrue(pv.all { it.recurrence == TaskRecurrence.NONE && it.updatedAt == t0 })
        r.ensureCalendarOccurrences(today)
        r.ensureCalendarOccurrences(LocalDate(2027, 1, 4))
        assertTrue(r.snapshot.value.tasks.none { it.title == "PV EDF" && it.status == TaskStatus.TODO })
        // Une orpheline restante se supprime comme toute tâche, et ne revient pas.
        r.deleteTask(467)
        r.ensureCalendarOccurrences(today)
        assertTrue(r.snapshot.value.tasks.none { it.id == 467L || (it.title == "Faire la réception" && it.status == TaskStatus.TODO) })
    }
}
