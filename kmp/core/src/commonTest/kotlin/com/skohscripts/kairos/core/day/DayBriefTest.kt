package com.skohscripts.kairos.core.day

import com.skohscripts.kairos.core.model.Interruption
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.ReminderSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.reminders.DailySummary
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/** « Ta journée » (docs/spec/ta-journee.md, issue #274), le mardi 24 novembre 2026 (UTC), journée de 9 h à 18 h. */
class DayBriefTest {
    private val tz = TimeZone.UTC
    private val day = LocalDate(2026, 11, 24)
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")
    private val settings = Settings(taskTypes = "", holidaysFr = false)

    private fun task(id: Long, minutes: Int, deadline: LocalDate? = null, status: TaskStatus = TaskStatus.TODO, doneAt: Instant = t0) =
        Task(id, "T$id", priority = 1, fibonacciPoints = 3, estimatedMinutes = minutes, deadline = deadline, status = status, createdAt = t0, updatedAt = doneAt)

    /** Six jours d'historique dérangés une demi-heure à chaque heure de travail : la moitié du temps, à toute heure. */
    private fun halfInterrupted(): List<Interruption> {
        var id = 0L
        return (0 until 6).flatMap { k ->
            val d = LocalDate(2026, 11, 20).minus(DatePeriod(days = k))
            (9..17).map { h -> val at = d.atTime(h, 30).toInstant(tz); Interruption(++id, at, 30, createdAt = at) }
        }
    }

    private fun brief(snapshot: KairosSnapshot, now: LocalDateTime): DayBrief =
        DayBrief.build(DayView.build(snapshot, day, now, tz), snapshot.settings, now)

    @Test
    fun theMorningPresentsTheDayAndItFits() {
        val s = KairosSnapshot(tasks = listOf(task(1, 60, deadline = day), task(2, 60, deadline = LocalDate(2026, 11, 27))), settings = settings)
        val b = brief(s, day.atTime(8, 0))
        assertEquals(DayBrief.Phase.DAY, b.phase)
        assertEquals(DayBrief.Verdict.FITS, b.verdict)
        assertEquals(2, b.tasks)
        assertEquals(120, b.estimateMinutes)
        assertEquals(1, b.deadlinesToday)
        assertEquals(1, b.deadlinesWeek)
        assertEquals(540, b.availableMinutes)
        assertEquals(0, b.interruptionMinutes)
        assertEquals(540, b.effectiveMinutes)
        assertEquals(day.atTime(18, 0), b.dayEnd)
    }

    @Test
    fun afterSomethingIsDoneItTalksAboutTheRestOfTheDay() {
        val now = day.atTime(11, 0)
        val done = task(3, 30, status = TaskStatus.DONE, doneAt = now.toInstant(tz))
        val session = WorkSession(1, 3, day.atTime(10, 0).toInstant(tz), day.atTime(10, 30).toInstant(tz), t0)
        val s = KairosSnapshot(tasks = listOf(task(1, 60), done), workSessions = listOf(session), settings = settings)
        val b = brief(s, now)
        assertEquals(DayBrief.Phase.REST, b.phase)
        assertEquals(1, b.done)
        assertEquals(30, b.workedMinutes)
        assertEquals(1, b.tasks)
        assertEquals(7 * 60, b.availableMinutes)
    }

    @Test
    fun withoutHistoryAnOverflowNamesATaskToPostpone() {
        val s = KairosSnapshot(tasks = listOf(task(1, 300), task(2, 300)), settings = settings)
        val b = brief(s, day.atTime(9, 0))
        assertEquals(DayBrief.Verdict.POSTPONE, b.verdict)
        assertEquals(60, b.overflowMinutes)
        assertNull(b.deepWork)
        assertNotNull(b.postpone)
    }

    @Test
    fun aDeepWorkSlotIsProposedWhenTheInterruptionsItAvoidsCoverTheOverflow() {
        // Disponible 540, dont 270 de dérangements attendus : 270 effectives pour 300 estimées, 30 de dépassement. Une heure de
        // deep work à 9 h en évite 30.
        val s = KairosSnapshot(tasks = listOf(task(1, 300)), interruptions = halfInterrupted(), settings = settings)
        val b = brief(s, day.atTime(9, 0))
        assertEquals(270, b.interruptionMinutes)
        assertEquals(270, b.effectiveMinutes)
        assertEquals(30, b.overflowMinutes)
        assertEquals(DayBrief.Verdict.DEEP_WORK, b.verdict)
        assertEquals(DayBrief.DeepWork(day.atTime(9, 0), 60), b.deepWork)
    }

    @Test
    fun anEmptyDayAndADayOff() {
        assertEquals(DayBrief.Verdict.EMPTY, brief(KairosSnapshot(settings = settings), day.atTime(9, 0)).verdict)
        val off = KairosSnapshot(settings = settings.copy(extraHolidays = "2026-11-24"))
        assertEquals(DayBrief.Verdict.DAY_OFF, brief(off, day.atTime(9, 0)).verdict)
    }

    @Test
    fun theDailySummaryCarriesTheSameFigures() {
        val s = KairosSnapshot(tasks = listOf(task(1, 60, deadline = day)), settings = settings.copy(reminders = ReminderSettings(dailySummary = true)))
        val summary = assertNotNull(DailySummary.plan(s, day, null, tz))
        val b = assertNotNull(summary.brief)
        assertEquals(60, b.estimateMinutes)
        assertEquals(1, b.deadlinesToday)
        assertEquals(DayBrief.Verdict.FITS, b.verdict)
    }
}
