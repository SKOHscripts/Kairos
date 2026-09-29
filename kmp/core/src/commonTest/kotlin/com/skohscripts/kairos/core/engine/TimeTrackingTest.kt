package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Portage de tests/test_tasks_time.py et des tests du rail « réel » (Kairos 2). */
class TimeTrackingTest {
    private val now = Instant.parse("2026-07-02T12:00:00Z")
    private var nextId = 1L

    private fun session(task: Long, start: Instant, end: Instant?) = WorkSession(nextId++, task, start, end, start)

    @Test
    fun session_duration_in_whole_minutes() {
        assertEquals(45, TimeTracking.sessionMinutes(session(1, now - 45.minutes, now), now))
        assertEquals(10, TimeTracking.sessionMinutes(session(1, now - 10.minutes, null), now))
        assertEquals(0, TimeTracking.sessionMinutes(session(1, now - 59.seconds, now), now))
        assertEquals(0, TimeTracking.sessionMinutes(session(1, now, now - 5.minutes), now)) // jamais négative
    }

    @Test
    fun spent_by_task_adds_manual_time_on_top_of_sessions() {
        val sessions = listOf(
            session(1, now - 30.minutes, now - 10.minutes),
            session(1, now - 10.minutes, now),
            session(2, now - 5.minutes, now),
        )
        assertEquals(mapOf(1L to 30, 2L to 5), TimeTracking.spentMinutesByTask(sessions, now))
        val tasks = listOf(task(1, manual = 15), task(3, manual = 20), task(4, manual = 0), task(5))
        assertEquals(mapOf(1L to 45, 2L to 5, 3L to 20), TimeTracking.spentMinutesByTask(sessions, now, tasks))
    }

    @Test
    fun running_session_is_the_most_recent_open_one() {
        val old = session(1, now - 50.minutes, null)
        val recent = session(2, now - 5.minutes, null)
        assertEquals(recent, TimeTracking.runningSession(listOf(old, session(3, now - 2.minutes, now), recent)))
        assertNull(TimeTracking.runningSession(listOf(session(1, now - 5.minutes, now))))
    }

    @Test
    fun day_filter_uses_the_local_start_date_and_includes_both_bounds() {
        val paris = TimeZone.of("Europe/Paris")
        val lateEvening = session(1, Instant.parse("2026-07-01T22:30:00Z"), Instant.parse("2026-07-01T23:00:00Z")) // 0 h 30 à Paris le 2
        val yesterday = session(1, now - 1.days, now - 1.days + 20.minutes)
        val today = session(2, now - 20.minutes, now)
        val day = LocalDate(2026, 7, 2)
        assertEquals(listOf(lateEvening, today), TimeTracking.sessionsOnDay(listOf(lateEvening, yesterday, today), day, paris))
        assertEquals(listOf(today), TimeTracking.sessionsOnDay(listOf(lateEvening, yesterday, today), day, TimeZone.UTC))
        assertEquals(3, TimeTracking.sessionsInRange(listOf(lateEvening, yesterday, today), LocalDate(2026, 7, 1), day, TimeZone.UTC).size)
        assertEquals(70, TimeTracking.totalMinutes(listOf(lateEvening, yesterday, today), now))
    }

    @Test
    fun spent_by_type_groups_and_falls_back_to_empty_key() {
        val sessions = listOf(session(1, now - 30.minutes, now), session(2, now - 10.minutes, now), session(9, now - 5.minutes, now))
        assertEquals(mapOf("Dev" to 40, "" to 5), TimeTracking.spentMinutesByType(sessions, mapOf(1L to "Dev", 2L to "Dev"), now))
    }

    @Test
    fun session_rail_is_clamped_to_the_workday() {
        val settings = Settings(workdayStartHour = 9, workdayEndHour = 18)
        val day = LocalDate(2026, 7, 2)
        val utc = TimeZone.UTC
        val closed = session(7, Instant.parse("2026-07-02T10:00:00Z"), Instant.parse("2026-07-02T11:30:00Z"))
        val entry = TimeTracking.sessionTimeline(listOf(closed), day, mapOf(7L to "Rédaction"), settings, now, utc).single()
        assertEquals(Scheduling.TimelineKind.SESSION, entry.kind)
        assertEquals("Rédaction", entry.title)
        assertEquals(60 to 90, entry.topMinutes to entry.heightMinutes)
        val running = session(1, Instant.parse("2026-07-02T09:00:00Z"), null)
        assertEquals(30, TimeTracking.sessionTimeline(listOf(running), day, emptyMap(), settings, Instant.parse("2026-07-02T09:30:00Z"), utc).single().heightMinutes)
        val early = session(1, Instant.parse("2026-07-02T06:00:00Z"), Instant.parse("2026-07-02T07:00:00Z"))
        assertEquals(emptyList(), TimeTracking.sessionTimeline(listOf(early), day, emptyMap(), settings, now, utc))
    }

    private fun task(id: Long, manual: Int? = null) = Task(id, "T$id", manualTimeSpentMinutes = manual, createdAt = now, updatedAt = now)
}
