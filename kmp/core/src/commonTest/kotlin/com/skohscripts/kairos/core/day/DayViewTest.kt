package com.skohscripts.kairos.core.day

import kotlinx.datetime.LocalTime
import com.skohscripts.kairos.core.model.BlockException
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
    fun a_blocker_lists_the_tasks_it_blocks_while_it_is_open() {
        val deps = listOf(TaskDependency(1, taskId = 2, blockerId = 1, createdAt = t0), TaskDependency(2, taskId = 3, blockerId = 1, createdAt = t0))
        val open = build(KairosSnapshot(tasks = listOf(task(1), task(2), task(3)), dependencies = deps))
        assertEquals(listOf("T2", "T3"), open.blocking[1L])
        // Le bloqueur terminé ne bloque plus rien : plus de badge.
        val done = build(KairosSnapshot(tasks = listOf(task(1, status = TaskStatus.DONE), task(2), task(3)), dependencies = deps))
        assertEquals(null, done.blocking[1L])
    }

    @Test
    fun a_blocked_unqualified_task_is_listed_only_in_blocked() {
        // Bloquée et pas encore qualifiée : « Bloquées » seulement, jamais aussi dans « À traiter » (une tâche, une section).
        val tasks = listOf(task(1), task(2, p = null))
        val deps = listOf(TaskDependency(1, taskId = 2, blockerId = 1, createdAt = t0))
        val view = build(KairosSnapshot(tasks = tasks, dependencies = deps))
        assertEquals(listOf(2L), view.blocked.map { it.task.id })
        assertEquals(emptyList(), view.inbox.map { it.id })
    }

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
    fun a_deep_work_window_only_fills_its_part_to_come() {
        val block = TimeBlock(1, "Deep", LocalDateTime(2026, 9, 29, 10, 0), LocalDateTime(2026, 9, 29, 11, 30), BlockKind.DEEPWORK, createdAt = t0)
        val snapshot = KairosSnapshot(tasks = listOf(task(1)), timeBlocks = listOf(block), settings = Settings(workdayStartHour = 9, workdayEndHour = 18))
        // Fenêtre finie : la tâche se pose à maintenant, hors deep work, jamais à 10 h.
        val late = build(snapshot, now = LocalDateTime(2026, 9, 29, 16, 0)).agenda.single()
        assertEquals(LocalDateTime(2026, 9, 29, 16, 0), late.start)
        assertFalse(late.deepwork)
        // Fenêtre entamée : seule sa part à venir est remplie.
        val during = build(snapshot, now = LocalDateTime(2026, 9, 29, 10, 15)).agenda.single()
        assertEquals(LocalDateTime(2026, 9, 29, 10, 15), during.start)
        assertTrue(during.deepwork)
    }

    @Test
    fun the_subtasks_of_a_blocked_parent_are_blocked_and_never_planned() {
        val tasks = listOf(
            task(1, title = "Mère"), task(2, parent = 1, title = "Fille"), task(3, parent = 2, title = "Petite-fille"),
            task(4, title = "Bloqueuse"), task(5, title = "Libre"), task(6, parent = 5, title = "Fille libre"),
        )
        val deps = listOf(TaskDependency(1, taskId = 1, blockerId = 4, createdAt = t0))
        val view = build(KairosSnapshot(tasks = tasks, dependencies = deps))
        assertEquals(setOf(1L, 2L, 3L), view.blocked.map { it.task.id }.toSet())
        assertEquals(listOf("Mère"), view.blocked.single { it.task.id == 2L }.blockers)
        assertEquals(listOf("Mère"), view.blocked.single { it.task.id == 3L }.blockers) // la plus proche mère bloquée
        // Ni la fille ni la petite-fille ne sont placées ; la fille libre l'est.
        assertEquals(setOf(4L, 6L), view.agenda.map { it.task.id }.toSet() + view.unscheduled.map { it.id }.toSet())
    }

    @Test
    fun a_pinned_task_of_the_past_is_still_up_next() {
        val pinned = task(1, title = "Point d'équipe").copy(pinnedStart = LocalDateTime(2026, 9, 29, 9, 30))
        val view = build(KairosSnapshot(tasks = listOf(pinned, task(2))), now = LocalDateTime(2026, 9, 29, 17, 0))
        assertEquals(1L, view.nextUp?.id) // la tâche épinglée reste à son heure (9 h 30) et s'affiche « maintenant » tant qu'elle n'est pas faite
        assertEquals(LocalDateTime(2026, 9, 29, 9, 30), view.nextUpStart)
    }

    @Test
    fun a_blocked_parent_is_only_in_blocked() {
        val tasks = listOf(task(1, title = "Mère"), task(2, parent = 1), task(3, title = "Bloqueuse"))
        val deps = listOf(TaskDependency(1, taskId = 1, blockerId = 3, createdAt = t0))
        val view = build(KairosSnapshot(tasks = tasks, dependencies = deps))
        // La mère et sa sous-tâche (bloquée par héritage) sont dans « Bloquées » ; aucune « tâche mère en cours ».
        assertEquals(listOf(1L, 2L), view.blocked.map { it.task.id }.sorted())
        assertTrue(view.parents.isEmpty())
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
    fun the_incomplete_facet_keeps_tasks_missing_any_chosen_field() {
        // Issue #166 : sans type, sans points ou sans durée, comme la carte « Complétude » des statistiques.
        val typed = task(1).copy(taskType = "Admin")
        val noType = task(2)
        val noDuration = task(3, minutes = null).copy(taskType = "Admin")
        val zeroDuration = task(4, minutes = 0).copy(taskType = "Admin")
        val noPoints = task(5, pts = null).copy(taskType = "Admin")
        val all = listOf(typed, noType, noDuration, zeroDuration, noPoints)
        fun kept(vararg fields: MissingField) = all.filter { DayFilter(missing = fields.toSet()).matches(it) }.map { it.id }
        assertEquals(listOf(2L), kept(MissingField.TYPE))
        assertEquals(listOf(3L, 4L), kept(MissingField.DURATION))
        assertEquals(listOf(5L), kept(MissingField.POINTS))
        // Plusieurs champs : il suffit qu'un seul manque.
        assertEquals(listOf(2L, 3L, 4L), kept(MissingField.TYPE, MissingField.DURATION))
        assertEquals(all.map { it.id }, kept())
        assertTrue(DayFilter(missing = setOf(MissingField.TYPE)).active)
        // La vue : le filtre réduit les listes (« À traiter » comprise), jamais le planning.
        val view = build(KairosSnapshot(tasks = all), filter = DayFilter(missing = setOf(MissingField.POINTS)))
        assertEquals(listOf(5L), view.inbox.map { it.id })
        assertTrue(view.agenda.isEmpty())
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

    @Test
    fun a_weekday_slot_is_not_on_a_public_holiday() {
        // Créneau « jours ouvrés » commencé le lundi 9 novembre 2026 ; le mercredi 11 est férié (calendrier français).
        val standUp = TimeBlock(
            7, "Stand-up", LocalDateTime(2026, 11, 9, 9, 30), LocalDateTime(2026, 11, 9, 9, 45),
            recurrence = BlockRecurrence.WEEKDAYS, createdAt = t0,
        )
        val snapshot = KairosSnapshot(timeBlocks = listOf(standUp), settings = Settings(holidaysFr = true))
        val armistice = DayView.build(snapshot, LocalDate(2026, 11, 11), null, paris)
        assertEquals(emptyList(), armistice.dayBlocks)
        assertEquals(emptyList(), armistice.editableBlocks)
        assertEquals(listOf("Stand-up"), DayView.build(snapshot, LocalDate(2026, 11, 12), null, paris).dayBlocks.map { it.title })
        // Sans le calendrier français, le mercredi redevient un jour ouvré.
        val noFrench = snapshot.copy(settings = Settings(holidaysFr = false))
        assertEquals(listOf(7L), DayView.build(noFrench, LocalDate(2026, 11, 11), null, paris).dayBlocks.map { it.id })
    }

    @Test
    fun one_occurrence_skipped_or_changed_shows_in_the_day_and_stays_editable() {
        // Déjeuner quotidien ; le mardi 29 septembre est sauté, le mercredi 30 décalé à 13 h-13 h 30 (issue #110).
        val lunch = TimeBlock(1, "Déjeuner", LocalDateTime(2026, 9, 1, 12, 0), LocalDateTime(2026, 9, 1, 13, 0), recurrence = BlockRecurrence.DAILY, createdAt = t0)
        val exceptions = listOf(
            BlockException(1, 1, day, skipped = true, createdAt = t0),
            BlockException(2, 1, LocalDate(2026, 9, 30), title = "Déjeuner client", start = LocalTime(13, 0), end = LocalTime(13, 30), createdAt = t0),
        )
        val snapshot = KairosSnapshot(timeBlocks = listOf(lunch), blockExceptions = exceptions)
        val skipped = build(snapshot)
        assertEquals(emptyList(), skipped.dayBlocks)
        assertTrue(skipped.timeline.none { it.title == "Déjeuner" })
        // Toujours listé, sauté : on peut le rétablir.
        assertEquals(listOf(true), skipped.editableBlocks.map { it.skipped })
        val changed = DayView.build(snapshot, LocalDate(2026, 9, 30), null, paris)
        assertEquals(listOf(LocalDateTime(2026, 9, 30, 13, 0)), changed.dayBlocks.map { it.start })
        assertEquals("Déjeuner client", changed.editableBlocks.single().occurrence.title)
        assertEquals("Déjeuner", changed.editableBlocks.single().template.title)
        // Le jeudi, rien ne change.
        assertEquals(listOf(LocalDateTime(2026, 10, 1, 12, 0)), DayView.build(snapshot, LocalDate(2026, 10, 1), null, paris).dayBlocks.map { it.start })
    }
}
