package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.BlockException
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
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
    fun weekdays_never_lands_on_a_holiday() {
        val tuesday = LocalDate(2026, 11, 10)
        val armistice = LocalDate(2026, 11, 11) // mercredi férié
        assertEquals(armistice, Recurrence.nextDeadline(TaskRecurrence.WEEKDAYS, tuesday))
        assertEquals(LocalDate(2026, 11, 12), Recurrence.nextDeadline(TaskRecurrence.WEEKDAYS, tuesday, holidays = setOf(armistice)))
        // Le vendredi avant un lundi férié : mardi.
        val monday = LocalDate(2026, 11, 16)
        assertEquals(LocalDate(2026, 11, 17), Recurrence.nextDeadline(TaskRecurrence.WEEKDAYS, LocalDate(2026, 11, 13), holidays = setOf(monday)))
        val done = task(recurrence = TaskRecurrence.WEEKDAYS, deadline = tuesday)
        val next = Recurrence.nextOccurrence(done, tuesday, emptyList(), Instant.parse("2026-11-10T08:00:00Z"), setOf(armistice))
        assertEquals(LocalDate(2026, 11, 12), next!!.deadline)
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

    // --- Créneaux « jours ouvrés » et fériés (issue #109) --------------------------------------------------------------

    private fun slot(recurrence: BlockRecurrence, origin: LocalDate = LocalDate(2026, 11, 2)) =
        TimeBlock(1, "Stand-up", origin.atTime(9, 30), origin.atTime(9, 45), recurrence = recurrence, createdAt = Instant.parse("2026-11-01T08:00:00Z"))

    private fun days(blocks: List<TimeBlock>) = blocks.map { it.start.date }

    @Test
    fun weekday_slots_skip_holidays_like_weekday_tasks() {
        // Semaine du 9 novembre 2026 : mercredi 11 férié (Armistice), vendredi 13 déclaré jour de pont.
        val monday = LocalDate(2026, 11, 9)
        val holidays = setOf(LocalDate(2026, 11, 11), LocalDate(2026, 11, 13))
        val got = Recurrence.expandRecurringBlocks(listOf(slot(BlockRecurrence.WEEKDAYS)), monday, monday.plus(DatePeriod(days = 6)), holidays)
        assertEquals(listOf(LocalDate(2026, 11, 9), LocalDate(2026, 11, 10), LocalDate(2026, 11, 12)), days(got))
        // Même heure, même durée, même identifiant que le modèle.
        assertEquals(LocalDateTime(2026, 11, 12, 9, 30), got.last().start)
        assertEquals(LocalDateTime(2026, 11, 12, 9, 45), got.last().end)
        assertEquals(1L, got.last().id)
        // Le même jour, une tâche « jours ouvrés » aurait sauté les mêmes fériés.
        assertEquals(LocalDate(2026, 11, 12), Recurrence.nextDeadline(TaskRecurrence.WEEKDAYS, LocalDate(2026, 11, 10), holidays = holidays))
    }

    @Test
    fun weekday_slots_keep_every_workday_of_a_week_without_holiday() {
        val monday = LocalDate(2026, 11, 16)
        val got = Recurrence.expandRecurringBlocks(listOf(slot(BlockRecurrence.WEEKDAYS)), monday, monday.plus(DatePeriod(days = 6)), setOf(LocalDate(2026, 11, 11)))
        assertEquals((0..4).map { monday.plus(DatePeriod(days = it)) }, days(got))
        // Sans fériés passés (fichiers d'or), seul le week-end est sauté, comme avant.
        val armisticeWeek = Recurrence.expandRecurringBlocks(listOf(slot(BlockRecurrence.WEEKDAYS)), LocalDate(2026, 11, 9), LocalDate(2026, 11, 15))
        assertEquals(5, armisticeWeek.size)
    }

    @Test
    fun daily_and_weekly_slots_ignore_holidays() {
        val wednesday = LocalDate(2026, 11, 11)
        val holidays = setOf(wednesday)
        assertEquals(listOf(wednesday), days(Recurrence.expandRecurringBlocks(listOf(slot(BlockRecurrence.DAILY)), wednesday, wednesday, holidays)))
        // Hebdomadaire ancré sur le mercredi 4 novembre : le mercredi férié garde son occurrence.
        val weekly = slot(BlockRecurrence.WEEKLY, origin = LocalDate(2026, 11, 4))
        assertEquals(listOf(wednesday), days(Recurrence.expandRecurringBlocks(listOf(weekly), wednesday, wednesday, holidays)))
    }

    // --- Créneaux mensuels (issue #108) ------------------------------------------------------------------------------

    private fun expand(slot: TimeBlock, from: LocalDate, to: LocalDate, holidays: Set<LocalDate> = emptySet()) =
        days(Recurrence.expandRecurringBlocks(listOf(slot), from, to, holidays))

    @Test
    fun monthly_day_slots_clamp_to_the_end_of_month_and_move_back_to_a_workday() {
        // Le 31, posé un samedi 31 janvier : janvier garde l'origine ; février 28 (samedi) → vendredi 27 ; mars 31
        // (mardi) ; avril 30 (jour borné) ; mai 31 (dimanche) → vendredi 29.
        val slot = slot(BlockRecurrence.MONTHLY_DAY, origin = LocalDate(2026, 1, 31))
        assertEquals(
            listOf(LocalDate(2026, 1, 31), LocalDate(2026, 2, 27), LocalDate(2026, 3, 31), LocalDate(2026, 4, 30), LocalDate(2026, 5, 29)),
            expand(slot, LocalDate(2026, 1, 1), LocalDate(2026, 5, 31)),
        )
        // Année bissextile : le 31 tombe le mardi 29 février 2028.
        assertEquals(listOf(LocalDate(2028, 2, 29)), expand(slot, LocalDate(2028, 2, 1), LocalDate(2028, 2, 29)))
        // Même heure, même durée, identifiant du modèle.
        val got = Recurrence.expandRecurringBlocks(listOf(slot), LocalDate(2026, 3, 31), LocalDate(2026, 3, 31)).single()
        assertEquals(LocalDateTime(2026, 3, 31, 9, 30), got.start)
        assertEquals(LocalDateTime(2026, 3, 31, 9, 45), got.end)
        assertEquals(1L, got.id)
    }

    @Test
    fun monthly_day_slots_skip_holidays_like_monthly_tasks() {
        // Le 11 : le mercredi 11 novembre 2026 est férié → mardi 10, comme une tâche « le 11 du mois ».
        val slot = slot(BlockRecurrence.MONTHLY_DAY, origin = LocalDate(2026, 10, 11))
        val holidays = setOf(LocalDate(2026, 11, 11))
        assertEquals(listOf(LocalDate(2026, 11, 10)), expand(slot, LocalDate(2026, 11, 1), LocalDate(2026, 11, 30), holidays))
        assertEquals(Recurrence.calendarDeadline(11, LocalDate(2026, 11, 1), holidays), LocalDate(2026, 11, 10))
        // Jamais avant l'origine : rien en septembre.
        assertEquals(emptyList(), expand(slot, LocalDate(2026, 9, 1), LocalDate(2026, 10, 10)))
    }

    @Test
    fun a_monthly_day_slot_moved_back_can_land_in_the_previous_month() {
        // Le 1er août 2026 est un samedi : son occurrence recule au vendredi 31 juillet ; juillet a donc l'origine et
        // l'occurrence d'août, août n'en a aucune, septembre a la sienne.
        val slot = slot(BlockRecurrence.MONTHLY_DAY, origin = LocalDate(2026, 7, 1))
        assertEquals(
            listOf(LocalDate(2026, 7, 1), LocalDate(2026, 7, 31), LocalDate(2026, 9, 1)),
            expand(slot, LocalDate(2026, 7, 1), LocalDate(2026, 9, 30)),
        )
    }

    @Test
    fun monthly_weekday_slots_keep_the_rank_of_their_origin() {
        // 3e mardi : 17 novembre 2026, 15 décembre, 19 janvier 2027.
        val third = slot(BlockRecurrence.MONTHLY_WEEKDAY, origin = LocalDate(2026, 11, 17))
        assertEquals(3, Recurrence.weekdayRank(LocalDate(2026, 11, 17)))
        assertEquals(
            listOf(LocalDate(2026, 11, 17), LocalDate(2026, 12, 15), LocalDate(2027, 1, 19)),
            expand(third, LocalDate(2026, 11, 1), LocalDate(2027, 1, 31)),
        )
        // Le 30 octobre 2026 est le 5e vendredi : il devient « dernier vendredi » (27 novembre, 4e ; 25 décembre, férié
        // gardé comme en hebdomadaire).
        val last = slot(BlockRecurrence.MONTHLY_WEEKDAY, origin = LocalDate(2026, 10, 30))
        assertEquals(Recurrence.LAST_RANK, Recurrence.weekdayRank(LocalDate(2026, 10, 30)))
        assertEquals(
            listOf(LocalDate(2026, 10, 30), LocalDate(2026, 11, 27), LocalDate(2026, 12, 25)),
            expand(last, LocalDate(2026, 10, 1), LocalDate(2026, 12, 31), setOf(LocalDate(2026, 12, 25))),
        )
    }

    @Test
    fun unknown_block_codes_read_as_one_off_and_the_monthly_codes_are_stable() {
        assertEquals(BlockRecurrence.MONTHLY_DAY, BlockRecurrence.fromCode("monthly_day"))
        assertEquals(BlockRecurrence.MONTHLY_WEEKDAY, BlockRecurrence.fromCode("monthly_weekday"))
        assertEquals(BlockRecurrence.NONE, BlockRecurrence.fromCode("yearly"))
    }

    // --- Une seule occurrence (issue #110) -----------------------------------------------------------------------------

    @Test
    fun an_exception_skips_or_changes_one_occurrence_only() {
        val weekly = slot(BlockRecurrence.WEEKLY, origin = LocalDate(2026, 11, 2))
        val created = Instant.parse("2026-11-01T08:00:00Z")
        val exceptions = listOf(
            BlockException(1, blockId = 1, date = LocalDate(2026, 11, 9), skipped = true, createdAt = created),
            BlockException(2, blockId = 1, date = LocalDate(2026, 11, 16), title = "Revue", start = LocalTime(16, 0), end = LocalTime(17, 0), createdAt = created),
            // Exception d'un autre modèle : sans effet sur celui-ci.
            BlockException(3, blockId = 2, date = LocalDate(2026, 11, 23), skipped = true, createdAt = created),
        )
        val got = Recurrence.expandRecurringBlocks(listOf(weekly), LocalDate(2026, 11, 2), LocalDate(2026, 11, 29), exceptions = exceptions)
        assertEquals(listOf(LocalDate(2026, 11, 2), LocalDate(2026, 11, 16), LocalDate(2026, 11, 23)), days(got))
        val changed = got[1]
        assertEquals("Revue", changed.title)
        assertEquals(LocalDateTime(2026, 11, 16, 16, 0), changed.start)
        assertEquals(LocalDateTime(2026, 11, 16, 17, 0), changed.end)
        // Les autres occurrences gardent le modèle.
        assertEquals("Stand-up", got[2].title)
        assertEquals(LocalDateTime(2026, 11, 23, 9, 30), got[2].start)
        // Une exception un jour sans occurrence ne crée rien.
        val stray = listOf(BlockException(4, blockId = 1, date = LocalDate(2026, 11, 3), title = "X", start = LocalTime(8, 0), end = LocalTime(9, 0), createdAt = created))
        assertEquals(4, Recurrence.expandRecurringBlocks(listOf(weekly), LocalDate(2026, 11, 2), LocalDate(2026, 11, 29), exceptions = stray).size)
    }

    // --- Mois sautés (docs/spec/recurrence.md § Avis des mois sautés, issue #111) : mardi 29 septembre 2026 -------------------

    private val skipToday = LocalDate(2026, 9, 29)
    private val skipNow = Instant.parse("2026-09-29T07:00:00Z")

    private fun monthly(
        id: Long,
        period: String,
        day: Int = 14,
        title: String = "Note de frais",
        status: TaskStatus = TaskStatus.TODO,
        space: TaskSpace = TaskSpace.PERSONAL,
        deadline: LocalDate? = null,
    ) = Task(
        id, title, priority = 1, fibonacciPoints = 2, recurrence = TaskRecurrence.MONTHLY_ON_DAY, recurrenceDayOfMonth = day,
        recurrencePeriod = period, deadline = deadline, status = status, space = space, createdAt = skipNow, updatedAt = skipNow,
    )

    private fun months(vararg ym: String) = ym.map { LocalDate.parse("$it-01") }

    @Test
    fun skipped_months_lie_between_the_series_start_and_the_current_month() {
        val tasks = listOf(monthly(1, "2026-05"), monthly(2, "2026-09"))
        val series = Recurrence.skippedSeries(tasks, skipToday, emptyMap()).single()
        assertEquals("Note de frais", series.title)
        assertEquals(14, series.dayOfMonth)
        assertEquals(months("2026-06", "2026-07", "2026-08"), series.months)
        assertEquals("14|Note de frais", series.key)
    }

    @Test
    fun a_done_or_archived_occurrence_covers_its_month() {
        val tasks = listOf(
            monthly(1, "2026-05"),
            monthly(2, "2026-06", status = TaskStatus.DONE),
            monthly(3, "2026-07", status = TaskStatus.ARCHIVED),
        )
        assertEquals(months("2026-08"), Recurrence.skippedSeries(tasks, skipToday, emptyMap()).single().months)
    }

    @Test
    fun a_deadline_inside_a_month_covers_it() {
        val tasks = listOf(monthly(1, "2026-05"), monthly(2, "", deadline = LocalDate(2026, 7, 10)))
        assertEquals(months("2026-06", "2026-08"), Recurrence.skippedSeries(tasks, skipToday, emptyMap()).single().months)
    }

    @Test
    fun only_the_last_twelve_months_are_offered() {
        val series = Recurrence.skippedSeries(listOf(monthly(1, "2024-01")), skipToday, emptyMap()).single()
        assertEquals(12, series.months.size)
        assertEquals(LocalDate(2025, 9, 1), series.months.first())
        assertEquals(LocalDate(2026, 8, 1), series.months.last())
    }

    @Test
    fun ignoring_hides_months_up_to_the_watermark_and_a_later_gap_shows_again() {
        val tasks = listOf(monthly(1, "2026-05"))
        assertEquals(emptyList(), Recurrence.skippedSeries(tasks, skipToday, mapOf("14|Note de frais" to "2026-08")))
        assertEquals(months("2026-08"), Recurrence.skippedSeries(tasks, skipToday, mapOf("14|Note de frais" to "2026-07")).single().months)
        // Un mois plus tard, septembre (non couvert) devient un trou postérieur au filigrane : il réapparaît.
        val october = LocalDate(2026, 10, 2)
        assertEquals(months("2026-09"), Recurrence.skippedSeries(tasks, october, mapOf("14|Note de frais" to "2026-08")).single().months)
    }

    @Test
    fun a_series_started_this_month_a_team_series_and_other_rules_have_no_notice() {
        val weekly = Task(9, "Revue", recurrence = TaskRecurrence.WEEKLY, createdAt = skipNow, updatedAt = skipNow)
        val tasks = listOf(
            monthly(1, "2026-09"),
            monthly(2, "2026-03", title = "Rapport d'équipe", space = TaskSpace.TEAM),
            weekly,
        )
        assertEquals(emptyList(), Recurrence.skippedSeries(tasks, skipToday, emptyMap()))
    }

    @Test
    fun series_are_keyed_by_title_and_day_of_month() {
        val tasks = listOf(monthly(1, "2026-07"), monthly(2, "2026-06", day = 5), monthly(3, "2026-06", title = "loyer"))
        val got = Recurrence.skippedSeries(tasks, skipToday, emptyMap())
        // Tri par titre sans casse, puis jour.
        assertEquals(listOf("loyer" to 14, "Note de frais" to 5, "Note de frais" to 14), got.map { it.title to it.dayOfMonth })
        assertEquals(months("2026-08"), got.last().months)
        assertEquals(months("2026-07", "2026-08"), got[1].months)
    }

    @Test
    fun recreated_occurrences_fall_on_the_business_day_before_day_n_of_each_skipped_month() {
        val pinned = monthly(1, "2026-05").copy(pinnedStart = LocalDateTime(2026, 5, 14, 9, 0), estimatedMinutes = 25, projectTag = "Admin")
        val tasks = listOf(pinned)
        val series = Recurrence.skippedSeries(tasks, skipToday, emptyMap()).single()
        val holidays = setOf(LocalDate(2026, 7, 14))
        val made = Recurrence.recreateSkipped(tasks, series, holidays, skipNow)
        // 14 juin 2026 : dimanche → vendredi 12 ; 14 juillet : férié → lundi 13 ; 14 août : vendredi.
        assertEquals(listOf(LocalDate(2026, 6, 12), LocalDate(2026, 7, 13), LocalDate(2026, 8, 14)), made.map { it.deadline })
        assertEquals(made.map { it.deadline }, made.map { it.scheduledDate })
        assertEquals(listOf("2026-06", "2026-07", "2026-08"), made.map { it.recurrencePeriod })
        assertTrue(made.all { it.id == 0L && it.pinnedStart == null && it.status == TaskStatus.TODO })
        assertTrue(made.all { it.estimatedMinutes == 25 && it.projectTag == "Admin" && it.recurrenceDayOfMonth == 14 && it.seriesId == 1L })
        // Le calcul ne recrée rien de lui-même : `calendarOccurrences` ne regarde que le mois courant.
        assertEquals(listOf("2026-09"), Recurrence.calendarOccurrences(tasks, skipToday, holidays, skipNow).map { it.recurrencePeriod })
    }
}
