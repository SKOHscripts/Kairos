package com.skohscripts.kairos.core.widgets

import com.skohscripts.kairos.core.day.DayView
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.team.TeamMember
import com.skohscripts.kairos.core.team.Workspaces
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Widgets Android (docs/spec/widgets-android.md, issue #150) le mardi 29 septembre 2026, journée de 9 h à 18 h. */
class WidgetPlanTest {
    private val day = LocalDate(2026, 9, 29)
    private val at9 = LocalDateTime(2026, 9, 29, 9, 0)
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")
    private val tz = TimeZone.of("Europe/Paris")

    private fun task(
        id: Long,
        p: Int = 1,
        minutes: Int = 30,
        pinned: LocalDateTime? = null,
        status: TaskStatus = TaskStatus.TODO,
        deadline: LocalDate? = null,
        updated: Instant = t0,
        space: TaskSpace = TaskSpace.PERSONAL,
        assignee: Long? = null,
    ) = Task(
        id, "T$id", priority = p, fibonacciPoints = 3, estimatedMinutes = minutes, pinnedStart = pinned, status = status,
        deadline = deadline, createdAt = t0, updatedAt = updated, space = space, assigneeId = assignee,
    )

    private fun block(id: Long, from: Int, to: Int, kind: BlockKind = BlockKind.BUSY) =
        TimeBlock(id, "B$id", LocalDateTime(2026, 9, 29, from, 0), LocalDateTime(2026, 9, 29, to, 0), kind, createdAt = t0)

    private fun at(hour: Int, minute: Int = 0) = LocalDateTime(2026, 9, 29, hour, minute)

    @Test
    fun now_shows_the_now_card_of_the_day_view_and_the_next_task() {
        val snapshot = KairosSnapshot(tasks = listOf(task(1, p = 0, minutes = 60), task(2), task(3, p = 2)))
        val widget = WidgetPlan.now(snapshot, at9, tz)
        val view = DayView.build(snapshot, day, at9, tz)
        assertEquals(NowState.UPCOMING, widget.state)
        assertEquals(view.nextUp?.id, widget.task?.id)
        assertEquals(at9, widget.task?.start)
        assertEquals(at(10), widget.task?.end)
        assertEquals(60, widget.task?.estimateMinutes)
        assertEquals(view.schedule.scheduled[1].task.id, widget.next?.id)
        assertEquals(view.schedule.scheduled[1].start, widget.next?.start)
    }

    @Test
    fun now_follows_the_running_timer() {
        val started = at(9, 20).toInstant(tz)
        val snapshot = KairosSnapshot(
            tasks = listOf(task(1, p = 0), task(2, minutes = 45)),
            workSessions = listOf(
                WorkSession(1, taskId = 2, startedAt = at(8, 0).toInstant(tz), endedAt = at(8, 12).toInstant(tz), createdAt = t0),
                WorkSession(2, taskId = 2, startedAt = started, createdAt = t0),
            ),
        )
        val widget = WidgetPlan.now(snapshot, at(9, 30), tz)
        assertEquals(NowState.RUNNING, widget.state)
        assertEquals(2L, widget.task?.id)
        assertEquals(started, widget.runningSince)
        assertEquals(12, widget.spentBeforeMinutes)
        assertEquals(45, widget.task?.estimateMinutes)
        // La suivante n'est jamais la tâche du chrono elle-même.
        assertEquals(1L, widget.next?.id)
    }

    @Test
    fun now_empty_states() {
        assertEquals(NowState.NOTHING_PLANNED, WidgetPlan.now(KairosSnapshot(), at9, tz).state)
        val doneToday = task(1, status = TaskStatus.DONE, updated = at(8, 30).toInstant(tz))
        val widget = WidgetPlan.now(KairosSnapshot(tasks = listOf(doneToday)), at9, tz)
        assertEquals(NowState.ALL_DONE, widget.state)
        assertNull(widget.task)
        // Faite hier : rien de prévu aujourd'hui, pas « tout est fait ».
        val doneYesterday = task(1, status = TaskStatus.DONE, updated = LocalDateTime(2026, 9, 28, 17, 0).toInstant(tz))
        assertEquals(NowState.NOTHING_PLANNED, WidgetPlan.now(KairosSnapshot(tasks = listOf(doneYesterday)), at9, tz).state)
    }

    @Test
    fun now_counts_the_inbox() {
        val unqualified = Task(9, "À qualifier", createdAt = t0, updatedAt = t0)
        val widget = WidgetPlan.now(KairosSnapshot(tasks = listOf(task(1), unqualified)), at9, tz)
        assertEquals(1, widget.inbox)
    }

    @Test
    fun day_lists_deadlines_first_then_the_plan_blocks_and_done_tasks_by_time() {
        val snapshot = KairosSnapshot(
            tasks = listOf(
                task(1, p = 0, minutes = 60),
                // Épinglée sur toute la journée : les autres ne trouvent plus de place au curseur.
                task(2, minutes = 540, pinned = at(9)),
                task(3, status = TaskStatus.DONE, updated = at(8, 15).toInstant(tz)),
                // Bloquée : ni placée ni en tête.
                task(4, deadline = day),
                // Échéance dépassée et échéance du jour sans place (trop longues pour le deep work).
                task(5, minutes = 600, deadline = LocalDate(2026, 9, 27)),
                task(6, minutes = 600, deadline = day),
            ),
            dependencies = listOf(TaskDependency(1, taskId = 4, blockerId = 1, createdAt = t0)),
            timeBlocks = listOf(block(1, 11, 12), block(2, 15, 16, BlockKind.DEEPWORK), block(3, 7, 8)),
        )
        val widget = WidgetPlan.day(snapshot, at9, tz)
        val view = DayView.build(snapshot, day, at9, tz)
        val deadlines = widget.rows.takeWhile { it.kind == DayRowKind.DEADLINE }
        assertEquals(listOf(5L, 6L), deadlines.map { it.id })
        assertEquals(listOf(true, false), deadlines.map { it.overdue })
        val rest = widget.rows.drop(deadlines.size)
        assertTrue(rest.none { it.kind == DayRowKind.DEADLINE })
        assertEquals(rest.sortedBy { it.start }, rest)
        // Les mêmes tâches placées, dans le même ordre, que l'agenda de la vue Jour.
        assertEquals(view.agenda.map { it.task.id }, rest.filter { it.kind == DayRowKind.TASK && it.state != DayRowState.DONE }.map { it.id })
        assertEquals(DayRowKind.DEEPWORK, rest.single { it.id == 2L && it.kind != DayRowKind.TASK }.kind)
        assertEquals(DayRowState.PAST, rest.single { it.kind == DayRowKind.BUSY && it.id == 3L }.state)
        val done = rest.single { it.state == DayRowState.DONE }
        assertEquals(3L, done.id)
        assertEquals(at(8, 15), done.start)
        assertTrue(widget.rows.none { it.id == 4L && it.kind != DayRowKind.BUSY })
        assertEquals(DayRowState.CURRENT, rest.single { it.kind == DayRowKind.TASK && it.id == view.nextUp?.id }.state)
        assertEquals(1, widget.doneCount)
        assertEquals(1 + view.schedule.scheduled.size + 2, widget.total)
    }

    @Test
    fun a_colleague_task_never_shows_and_mine_does() {
        val members = listOf(
            TeamMember(1, "u1", "Moi", hoursPerDay = 7.0, isSelf = true, createdAt = t0, updatedAt = t0),
            TeamMember(2, "u2", "Sam", hoursPerDay = 7.0, createdAt = t0, updatedAt = t0),
        )
        val full = KairosSnapshot(
            tasks = listOf(
                task(1, space = TaskSpace.TEAM, assignee = 1),
                task(2, p = 0, space = TaskSpace.TEAM, assignee = 2),
            ),
            members = members,
            settings = Settings(team = TeamSettings(enabled = true)),
        )
        val personal = Workspaces.personalView(full)
        val now = WidgetPlan.now(personal, at9, tz)
        assertEquals(1L, now.task?.id)
        assertNull(now.next)
        val rows = WidgetPlan.day(personal, at9, tz).rows
        assertEquals(listOf(1L), rows.map { it.id })
    }

    @Test
    fun next_change_is_the_next_edge_of_the_plan_or_midnight() {
        val snapshot = KairosSnapshot(tasks = listOf(task(1, minutes = 45)), timeBlocks = listOf(block(1, 11, 12)))
        assertEquals(at(9, 45), WidgetPlan.nextChange(snapshot, at9, tz))
        assertEquals(at(11), WidgetPlan.nextChange(KairosSnapshot(timeBlocks = listOf(block(1, 11, 12))), at9, tz))
        assertEquals(LocalDateTime(2026, 9, 30, 0, 0), WidgetPlan.nextChange(KairosSnapshot(), at9, tz))
    }

    @Test
    fun confirmation() {
        val armed = Instant.parse("2026-09-29T07:00:00Z")
        assertEquals(ConfirmState.IDLE, WidgetPlan.confirm(null, armed))
        assertEquals(ConfirmState.ARMED, WidgetPlan.confirm(armed, armed))
        assertEquals(ConfirmState.ARMED, WidgetPlan.confirm(armed, armed + 4.seconds))
        assertEquals(ConfirmState.IDLE, WidgetPlan.confirm(armed, armed + 5.seconds))
        // Horloge reculée : jamais armé par erreur.
        assertEquals(ConfirmState.IDLE, WidgetPlan.confirm(armed, armed - 1.seconds))
    }

    @Test
    fun a_done_task_is_never_reopened_by_a_late_tap() {
        val snapshot = KairosSnapshot(tasks = listOf(task(1), task(2, status = TaskStatus.DONE)))
        assertTrue(WidgetPlan.canComplete(snapshot, 1))
        assertFalse(WidgetPlan.canComplete(snapshot, 2))
        assertFalse(WidgetPlan.canComplete(snapshot, 99))
    }
}
