package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.ReminderSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.reminders.DailySummary
import com.skohscripts.kairos.core.settings.SettingsForm
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Durées réelles (docs/spec/durees-reelles.md, issue #271), le mardi 24 novembre 2026 à Paris. */
class SpentReviewTest {
    private val tz = TimeZone.of("Europe/Paris")
    private val today = LocalDate(2026, 11, 24)
    private val now = Instant.parse("2026-11-24T15:00:00Z")

    private fun task(id: Long, status: TaskStatus = TaskStatus.DONE, doneAgo: Int = 0, manual: Int? = null, parent: Long? = null, space: TaskSpace = TaskSpace.PERSONAL) =
        Task(
            id, "T$id", status = status, manualTimeSpentMinutes = manual, parentId = parent, space = space,
            createdAt = now - 30.days, updatedAt = now - doneAgo.days,
        )

    private fun session(id: Long, taskId: Long, open: Boolean = false) =
        WorkSession(id, taskId, now - 2.hours, if (open) null else now - 1.hours, createdAt = now - 2.hours)

    private fun snapshot(tasks: List<Task>, sessions: List<WorkSession> = emptyList(), settings: Settings = Settings()) =
        KairosSnapshot(tasks = tasks, workSessions = sessions, settings = settings)

    @Test
    fun untimed_means_no_session_no_typed_time_and_no_subtask() {
        val tasks = listOf(task(1), task(2, manual = 20), task(3, manual = 0), task(4), task(5), task(6, parent = 5))
        val s = snapshot(tasks, listOf(session(1, 4)))
        assertTrue(SpentReview.untimed(s, tasks[0]))
        assertFalse(SpentReview.untimed(s, tasks[1]), "temps saisi")
        assertFalse(SpentReview.untimed(s, tasks[2]), "0 min : ignorée par la revue, donc connue")
        assertFalse(SpentReview.untimed(s, tasks[3]), "chronométrée")
        assertFalse(SpentReview.untimed(s, tasks[4]), "mère de sous-tâches")
        assertTrue(SpentReview.untimed(s, tasks[5]))
        assertFalse(SpentReview.untimed(s, task(7, space = TaskSpace.TEAM)), "espace Équipe")
    }

    @Test
    fun asks_before_ticking_a_task_still_to_do_and_only_when_the_setting_is_on() {
        val todo = task(1, status = TaskStatus.TODO)
        assertTrue(SpentReview.asks(snapshot(listOf(todo)), todo))
        assertFalse(SpentReview.asks(snapshot(listOf(todo), settings = Settings(askSpentOnDone = false)), todo))
        // Rouvrir une tâche faite ne demande rien.
        val done = task(2)
        assertFalse(SpentReview.asks(snapshot(listOf(done)), done))
        // Le chrono tournait : la séance ouverte existe déjà avant la coche.
        assertFalse(SpentReview.asks(snapshot(listOf(todo), listOf(session(1, 1, open = true))), todo))
    }

    @Test
    fun two_ticks_less_than_a_minute_apart_are_a_batch() {
        assertFalse(SpentReview.batched(null, now))
        assertTrue(SpentReview.batched(now - 59.seconds, now))
        assertFalse(SpentReview.batched(now - 60.seconds, now))
        assertFalse(SpentReview.batched(now - 10.days, now))
    }

    @Test
    fun the_review_lists_the_untimed_done_tasks_of_the_last_seven_days_most_recent_first() {
        val tasks = listOf(
            task(1, doneAgo = 0), task(2, doneAgo = 6), task(3, doneAgo = 7), task(4, doneAgo = 2),
            task(5, status = TaskStatus.TODO), task(6, doneAgo = 1, manual = 0), task(7, doneAgo = 1, manual = 15),
            task(8, doneAgo = 3), task(9, doneAgo = 1, space = TaskSpace.TEAM),
        )
        val review = SpentReview.review(snapshot(tasks, listOf(session(1, 8))), today, tz)
        assertEquals(listOf(1L, 4L, 2L), review.map { it.id })
    }

    @Test
    fun the_setting_is_on_by_default_and_written_null_when_switched_back_on() {
        assertTrue(Settings().askSpent)
        val field = SettingsForm.FIELDS.first { it.key == "askSpentOnDone" }
        val off = field.write(Settings(), "false")
        assertEquals(false, off.askSpentOnDone)
        assertNull(field.write(off, "true").askSpentOnDone)
        assertEquals("true", field.read(Settings()))
    }

    @Test
    fun the_daily_summary_counts_the_tasks_to_review() {
        val reminders = ReminderSettings(dailySummary = true)
        val planned = Task(10, "Demain", priority = 1, fibonacciPoints = 3, estimatedMinutes = 30, createdAt = now, updatedAt = now)
        val s = snapshot(listOf(planned, task(1, doneAgo = 1), task(2, doneAgo = 2)), settings = Settings(taskTypes = "", reminders = reminders))
        assertEquals(2, DailySummary.plan(s, today, null, tz)?.untimed)
    }
}
