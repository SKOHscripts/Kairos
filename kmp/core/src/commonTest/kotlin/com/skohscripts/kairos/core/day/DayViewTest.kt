package com.skohscripts.kairos.core.day

import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DayViewTest {
    private val day = LocalDate(2026, 9, 29) // mardi
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")
    private val paris = TimeZone.of("Europe/Paris")

    private fun task(
        id: Long, p: Int? = 1, pts: Int? = 3, status: TaskStatus = TaskStatus.TODO, deadline: LocalDate? = null,
        scheduled: LocalDate? = null, parent: Long? = null, project: String = "", minutes: Int? = 60, updated: Instant = t0,
        title: String = "T$id",
    ) = Task(
        id, title, priority = p, fibonacciPoints = pts, status = status, deadline = deadline, scheduledDate = scheduled,
        parentId = parent, projectTag = project, estimatedMinutes = minutes, createdAt = t0, updatedAt = updated,
    )

    private fun build(snapshot: KairosSnapshot, now: LocalDateTime? = null, filter: DayFilter = DayFilter()) =
        DayView.build(snapshot, day, now, paris, filter)

    @Test
    fun each_open_task_lands_in_exactly_one_section() {
        val tasks = listOf(
            task(1, p = null), // à traiter
            task(2), // agenda
            task(3, scheduled = LocalDate(2026, 10, 5)), // plus tard
            task(4), // bloquée par 2
            task(5, status = TaskStatus.ARCHIVED),
            task(6), // la journée (9 h-10 h) est pleine : sans créneau
        )
        val deps = listOf(TaskDependency(1, taskId = 4, blockerId = 2, createdAt = t0))
        val view = build(KairosSnapshot(tasks = tasks, dependencies = deps, settings = Settings(workdayStartHour = 9, workdayEndHour = 10)))
        assertEquals(listOf(1L), view.inbox.map { it.id })
        assertEquals(listOf(2L), view.agenda.map { it.task.id })
        assertEquals(listOf(3L), view.later.map { it.id })
        assertEquals(listOf(4L), view.blocked.map { it.task.id })
        assertEquals(listOf("T2"), view.blocked.single().blockers)
        assertEquals(listOf(6L), view.unscheduled.map { it.id })
        assertEquals(2L, view.nextUp?.id)
        assertEquals(LocalDateTime(2026, 9, 29, 9, 0), view.nextUpStart)
    }

    @Test
    fun blocker_inherits_urgency_of_more_urgent_blocked_task() {
        val tasks = listOf(task(1, p = 2), task(2, p = 0, deadline = day))
        val deps = listOf(TaskDependency(1, taskId = 2, blockerId = 1, createdAt = t0))
        val view = build(KairosSnapshot(tasks = tasks, dependencies = deps))
        assertEquals(setOf(1L), view.raised)
        assertEquals(listOf(1L), view.agenda.map { it.task.id })
    }

    @Test
    fun score_only_for_qualified_tasks_and_backlog_without_dates() {
        val tasks = listOf(task(1, p = null), task(2, p = 2), task(3, deadline = day), task(4, p = 0))
        val view = build(KairosSnapshot(tasks = tasks))
        assertEquals(setOf(2L, 3L, 4L), view.why.keys)
        assertEquals(listOf(4L, 2L, 1L), view.backlog.map { it.id }) // priorité puis titre, sans priorité en dernier
    }

    @Test
    fun parents_progress_and_breadcrumb() {
        val tasks = listOf(
            task(1, title = "Mère"), task(2, parent = 1, status = TaskStatus.DONE), task(3, parent = 1, title = "Fille"),
            task(4, title = "Autre"),
        )
        val deps = listOf(TaskDependency(1, taskId = 4, blockerId = 3, createdAt = t0))
        val view = build(KairosSnapshot(tasks = tasks, dependencies = deps))
        val parent = view.parents.single()
        assertEquals(1L, parent.task.id)
        assertEquals(1 to 2, parent.done to parent.total)
        assertEquals("Mère", view.parentTitle[3])
        assertEquals(listOf("Mère › Fille"), view.blocked.single().blockers)
        assertFalse(view.agenda.any { it.task.id == 1L }) // une mère n'est pas une unité de travail
    }

    @Test
    fun done_today_uses_local_day_most_recent_first() {
        val tasks = listOf(
            task(1, status = TaskStatus.DONE, updated = Instant.parse("2026-09-28T22:30:00Z")), // 00:30 à Paris le 29
            task(2, status = TaskStatus.DONE, updated = Instant.parse("2026-09-29T10:00:00Z")),
            task(3, status = TaskStatus.DONE, updated = Instant.parse("2026-09-28T12:00:00Z")),
        )
        assertEquals(listOf(2L, 1L), build(KairosSnapshot(tasks = tasks)).doneToday.map { it.id })
    }

    @Test
    fun filter_reduces_lists_but_never_the_schedule() {
        val tasks = listOf(task(1, project = "Kairos", title = "Écrire la spec"), task(2, title = "Autre"), task(3, p = null, title = "spec à qualifier"))
        val all = build(KairosSnapshot(tasks = tasks))
        val filtered = build(KairosSnapshot(tasks = tasks), filter = DayFilter(query = "SPEC"))
        assertTrue(filtered.agenda.all { it.task.id == 1L })
        assertEquals(listOf(3L), filtered.inbox.map { it.id })
        assertEquals(all.schedule, filtered.schedule)
        assertEquals(listOf(1L), build(KairosSnapshot(tasks = tasks), filter = DayFilter(project = "Kairos")).agenda.map { it.task.id })
        assertTrue(DayFilter(points = 3).active)
        assertFalse(DayFilter(query = "  ").active)
    }

    @Test
    fun recurring_blocks_count_on_their_days_and_stay_editable_as_templates() {
        val monday = LocalDate(2026, 9, 28)
        val blocks = listOf(
            TimeBlock(1, "Déjeuner", LocalDateTime(2026, 9, 1, 12, 0), LocalDateTime(2026, 9, 1, 13, 0), recurrence = BlockRecurrence.DAILY, createdAt = t0),
            TimeBlock(2, "Hebdo lundi", monday.let { LocalDateTime(it.year, it.month, it.day, 10, 0) }, LocalDateTime(2026, 9, 28, 11, 0), recurrence = BlockRecurrence.WEEKLY, createdAt = t0),
            TimeBlock(3, "Deep", LocalDateTime(2026, 9, 29, 14, 0), LocalDateTime(2026, 9, 29, 16, 0), kind = BlockKind.DEEPWORK, createdAt = t0),
            TimeBlock(4, "Hier", LocalDateTime(2026, 9, 28, 9, 0), LocalDateTime(2026, 9, 28, 10, 0), createdAt = t0),
        )
        val view = build(KairosSnapshot(tasks = listOf(task(1)), timeBlocks = blocks))
        assertEquals(listOf(1L, 3L), view.editableBlocks.map { it.id })
        assertEquals(listOf("Déjeuner", "Deep"), view.dayBlocks.map { it.title })
        assertEquals(LocalDateTime(2026, 9, 29, 12, 0), view.dayBlocks.first().start)
    }

    @Test
    fun overload_banner_counts_unblocked_p0_only() {
        val tasks = (1L..3L).map { task(it, p = 0) }
        val settings = Settings(priorityOverloadThreshold = 2)
        assertTrue(build(KairosSnapshot(tasks = tasks, settings = settings)).priorityOverload)
        val deps = listOf(TaskDependency(1, taskId = 3, blockerId = 1, createdAt = t0))
        assertFalse(build(KairosSnapshot(tasks = tasks, dependencies = deps, settings = settings)).priorityOverload)
    }

    @Test
    fun nothing_to_do_means_no_next_step() {
        assertNull(build(KairosSnapshot()).nextUp)
    }
}
