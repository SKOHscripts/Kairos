package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Portage de tests/test_tasks_recurrence.py (occurrence suivante, séries « le N du mois »). */
class RecurrenceTest {
    private val today = LocalDate(2026, 7, 1)
    private val now = Instant.parse("2026-07-01T08:00:00Z")

    private fun task(
        id: Long = 1,
        title: String = "Point hebdo",
        recurrence: TaskRecurrence = TaskRecurrence.WEEKLY,
        deadline: LocalDate? = today,
        status: TaskStatus = TaskStatus.DONE,
        dayOfWeek: Int? = null,
        dayOfMonth: Int? = null,
        period: String = "",
        priority: Int? = null,
        points: Int? = null,
        minutes: Int? = null,
        pinned: LocalDateTime? = null,
        type: String = "",
    ) = Task(
        id = id, title = title, recurrence = recurrence, deadline = deadline, status = status,
        recurrenceDayOfWeek = dayOfWeek, recurrenceDayOfMonth = dayOfMonth, recurrencePeriod = period,
        priority = priority, fibonacciPoints = points, estimatedMinutes = minutes, pinnedStart = pinned,
        taskType = type, createdAt = now, updatedAt = now,
    )

    @Test
    fun spawn_copies_fields_and_advances_deadline() {
        val done = task(priority = 1, points = 3, minutes = 30, type = "reunion").copy(description = "Ordre du jour", projectTag = "MSI")
        val next = assertNotNull(Recurrence.nextOccurrence(done, today, listOf(done), now))
        assertEquals(0, next.id)
        assertEquals("Point hebdo", next.title)
        assertEquals("Ordre du jour", next.description)
        assertEquals(1, next.priority)
        assertEquals(3, next.fibonacciPoints)
        assertEquals(30, next.estimatedMinutes)
        assertEquals("MSI", next.projectTag)
        assertEquals("reunion", next.taskType)
        assertEquals(TaskRecurrence.WEEKLY, next.recurrence)
        assertEquals(TaskStatus.TODO, next.status)
        assertEquals(today.plus(DatePeriod(days = 7)), next.deadline)
        assertEquals(next.deadline, next.scheduledDate)
        assertNull(next.pinnedStart)
    }

    @Test
    fun spawn_shifts_pinned_time_to_new_deadline_same_hour() {
        val done = task(pinned = LocalDateTime(2026, 7, 1, 10, 30))
        val next = assertNotNull(Recurrence.nextOccurrence(done, today, emptyList(), now))
        assertEquals(LocalDateTime(2026, 7, 8, 10, 30), next.pinnedStart)
    }

    @Test
    fun spawn_does_nothing_for_non_recurring_or_calendar_series() {
        assertNull(Recurrence.nextOccurrence(task(recurrence = TaskRecurrence.NONE), today, emptyList(), now))
        assertNull(Recurrence.nextOccurrence(task(recurrence = TaskRecurrence.MONTHLY_ON_DAY), today, emptyList(), now))
    }

    @Test
    fun spawn_overdue_recurring_restarts_from_today() {
        val done = task(recurrence = TaskRecurrence.DAILY, deadline = LocalDate(2026, 6, 26))
        assertEquals(LocalDate(2026, 7, 2), Recurrence.nextOccurrence(done, today, emptyList(), now)?.deadline)
    }

    @Test
    fun spawn_guards_against_duplicates() {
        val done = task(recurrence = TaskRecurrence.DAILY)
        val first = assertNotNull(Recurrence.nextOccurrence(done, today, listOf(done), now))
        assertNull(Recurrence.nextOccurrence(done, today, listOf(done, first.copy(id = 2)), now))
        // Une occurrence identique déjà faite ne bloque pas.
        assertNotNull(Recurrence.nextOccurrence(done, today, listOf(first.copy(id = 2, status = TaskStatus.DONE)), now))
    }

    @Test
    fun weekly_anchor_survives_late_completions() {
        val thursday = LocalDate(2026, 7, 2)
        val done = task(deadline = thursday, dayOfWeek = Workdays.weekday(thursday))
        val first = assertNotNull(Recurrence.nextOccurrence(done, LocalDate(2026, 7, 3), emptyList(), now))
        assertEquals(LocalDate(2026, 7, 9), first.deadline)
        assertEquals(3, first.recurrenceDayOfWeek)
        val second = assertNotNull(Recurrence.nextOccurrence(first.copy(status = TaskStatus.DONE), LocalDate(2026, 7, 13), emptyList(), now))
        assertEquals(LocalDate(2026, 7, 16), second.deadline)
        // Sans ancre stockée : repli sur le jour de l'échéance d'origine.
        val legacy = task(deadline = thursday)
        assertEquals(LocalDate(2026, 7, 9), Recurrence.nextOccurrence(legacy, LocalDate(2026, 7, 3), emptyList(), now)?.deadline)
    }

    private fun series(vararg members: Task) = members.map { it.copy(recurrence = TaskRecurrence.MONTHLY_ON_DAY) }

    @Test
    fun calendar_generates_this_month_once() {
        val seed = series(
            task(title = "Rapport mensuel", dayOfMonth = 15, priority = 2, points = 5, minutes = 45, deadline = LocalDate(2026, 5, 20),
                pinned = LocalDateTime(2026, 5, 15, 9, 0), type = "dev"),
        )
        val created = Recurrence.calendarOccurrences(seed, LocalDate(2026, 7, 3), emptySet(), now)
        assertEquals(1, created.size)
        val occ = created.single()
        assertEquals(LocalDate(2026, 7, 15), occ.deadline)
        assertEquals(occ.deadline, occ.scheduledDate)
        assertEquals(2, occ.priority)
        assertEquals(5, occ.fibonacciPoints)
        assertEquals(45, occ.estimatedMinutes)
        assertEquals("dev", occ.taskType)
        assertEquals(LocalDateTime(2026, 7, 15, 9, 0), occ.pinnedStart)
        assertEquals("2026-07", occ.recurrencePeriod)
        assertEquals(15, occ.recurrenceDayOfMonth)
        assertTrue(Recurrence.calendarOccurrences(seed + occ.copy(id = 2), LocalDate(2026, 7, 20), emptySet(), now).isEmpty())
    }

    @Test
    fun calendar_seed_deadline_this_month_counts_as_occurrence() {
        val seed = series(task(title = "Cotisation", dayOfMonth = 23, deadline = LocalDate(2026, 7, 23), status = TaskStatus.TODO))
        assertTrue(Recurrence.calendarOccurrences(seed, LocalDate(2026, 7, 1), emptySet(), now).isEmpty())
    }

    @Test
    fun calendar_moves_back_to_previous_business_day() {
        val sunday = series(task(title = "Note de frais", dayOfMonth = 23, deadline = null))
        assertEquals(LocalDate(2026, 8, 21), Recurrence.calendarOccurrences(sunday, LocalDate(2026, 8, 5), emptySet(), now).single().deadline)
        val holiday = series(task(title = "Suivi budget", dayOfMonth = 14, deadline = null))
        assertEquals(
            LocalDate(2026, 2, 12),
            Recurrence.calendarOccurrences(holiday, LocalDate(2026, 2, 2), setOf(LocalDate(2026, 2, 13)), now).single().deadline,
        )
        val endOfMonth = series(task(title = "Clôture", dayOfMonth = 31, deadline = null))
        assertEquals(LocalDate(2026, 2, 27), Recurrence.calendarOccurrences(endOfMonth, LocalDate(2026, 2, 1), emptySet(), now).single().deadline)
    }

    @Test
    fun calendar_uses_most_recent_representative_and_keeps_open_past_month() {
        val members = series(
            task(id = 1, title = "Revue mensuelle", dayOfMonth = 5, priority = 2, points = 13, deadline = LocalDate(2026, 5, 5), period = "2026-05"),
            task(id = 2, title = "Revue mensuelle", dayOfMonth = 5, priority = 0, points = 2, minutes = 90, deadline = LocalDate(2026, 6, 5),
                period = "2026-06", status = TaskStatus.TODO),
        )
        val occ = Recurrence.calendarOccurrences(members, LocalDate(2026, 7, 1), emptySet(), now).single()
        assertEquals(0, occ.priority)
        assertEquals(2, occ.fibonacciPoints)
        assertEquals(90, occ.estimatedMinutes)
        assertEquals(LocalDate(2026, 7, 3), occ.deadline)
    }

    @Test
    fun calendar_series_key_includes_space_but_not_assignee() {
        // Une série Perso et une série d'équipe de même titre et de même jour restent distinctes
        // (docs/spec/equipe.md § Questions ouvertes) : chacune reçoit son occurrence, dans son espace.
        val personal = task(id = 1, title = "Point mensuel", dayOfMonth = 5, deadline = LocalDate(2026, 6, 5))
        val team = task(id = 2, title = "Point mensuel", dayOfMonth = 5, deadline = LocalDate(2026, 6, 5))
            .copy(space = TaskSpace.TEAM, assigneeId = 7)
        // L'occurrence de juin a été réaffectée à 8 : même série, pas d'occurrence en plus pour 7.
        val reassigned = team.copy(id = 3, assigneeId = 8)
        val created = Recurrence.calendarOccurrences(series(personal, team, reassigned), LocalDate(2026, 7, 1), emptySet(), now)
        assertEquals(2, created.size)
        assertEquals(
            setOf(TaskSpace.PERSONAL to null, TaskSpace.TEAM to 8L),
            created.map { it.space to it.assigneeId }.toSet(),
        )
        // La couverture du mois se juge série par série : une occurrence d'équipe ne couvre pas la série Perso.
        val covered = team.copy(id = 4, recurrencePeriod = "2026-07")
        val rest = Recurrence.calendarOccurrences(series(personal, team, reassigned, covered), LocalDate(2026, 7, 1), emptySet(), now)
        assertEquals(listOf(TaskSpace.PERSONAL to null), rest.map { it.space to it.assigneeId })
    }

    @Test
    fun next_occurrence_of_a_team_task_starts_clean() {
        val done = task(priority = 1, points = 3).copy(
            space = TaskSpace.TEAM, assigneeId = 4, progressPercent = 100, startedOn = today, teamUid = "uid-1",
        )
        val next = assertNotNull(Recurrence.nextOccurrence(done, today, emptyList(), now))
        assertEquals(TaskSpace.TEAM, next.space)
        assertEquals(4L, next.assigneeId)
        assertNull(next.progressPercent)
        assertNull(next.startedOn)
        assertNull(next.teamUid)
    }
}
