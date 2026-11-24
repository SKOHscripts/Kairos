package com.skohscripts.kairos.core.habit

import com.skohscripts.kairos.core.engine.Recurrence
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Habitudes (docs/spec/habitudes.md, issue #284), le mardi 24 novembre 2026 à Paris (UTC+1). */
class HabitsTest {
    private val tz = TimeZone.of("Europe/Paris")
    private val today = LocalDate(2026, 11, 24)
    private val now = Instant.parse("2026-11-24T15:00:00Z")

    /** Minuit à Paris du jour [daysAgo] avant aujourd'hui, plus [minutes] (heure locale). */
    private fun at(daysAgo: Int, minutes: Int) = Instant.parse("2026-11-23T23:00:00Z") - daysAgo.days + minutes.minutes

    private fun occurrence(
        id: Long,
        daysAgo: Int,
        minute: Int = 7 * 60,
        status: TaskStatus = TaskStatus.DONE,
        recurrence: TaskRecurrence = TaskRecurrence.DAILY,
        habitSince: Instant? = null,
        score: Int? = null,
        series: Long? = 1,
        space: TaskSpace = TaskSpace.PERSONAL,
    ) = Task(
        id, "Méditer", recurrence = recurrence, status = status, seriesId = series, space = space,
        habitSince = habitSince, habitScore = score,
        createdAt = at(daysAgo, 0) - 1.hours, updatedAt = at(daysAgo, minute),
    )

    /** Série quotidienne : première tâche (id 1) il y a [days] jours, une occurrence faite par jour à [minute], une à faire. */
    private fun daily(days: Int, minute: Int = 7 * 60, skip: Set<Int> = emptySet()): List<Task> {
        val done = (days downTo 1).filter { it !in skip }.map { d -> occurrence(100L + d, d, minute, series = 1) }
        val first = occurrence(1, days + 1, minute, series = null)
        val open = occurrence(2, 0, status = TaskStatus.TODO, series = 1).copy(createdAt = at(1, minute), updatedAt = at(1, minute))
        return listOf(first) + done + open
    }

    @Test
    fun onlyPersonalDailyWeekdaysAndWeeklySeriesAreEligible() {
        assertTrue(Habits.eligible(occurrence(1, 0)))
        assertTrue(Habits.eligible(occurrence(1, 0, recurrence = TaskRecurrence.WEEKDAYS)))
        assertTrue(Habits.eligible(occurrence(1, 0, recurrence = TaskRecurrence.WEEKLY)))
        assertFalse(Habits.eligible(occurrence(1, 0, recurrence = TaskRecurrence.MONTHLY)))
        assertFalse(Habits.eligible(occurrence(1, 0, recurrence = TaskRecurrence.MONTHLY_ON_DAY)))
        assertFalse(Habits.eligible(occurrence(1, 0, recurrence = TaskRecurrence.NONE)))
        assertFalse(Habits.eligible(occurrence(1, 0, space = TaskSpace.TEAM)))
        assertFalse(Habits.eligible(occurrence(1, 0).copy(origin = "team")))
        assertFalse(Habits.isHabit(occurrence(1, 0, recurrence = TaskRecurrence.MONTHLY, habitSince = now)))
    }

    @Test
    fun theNextOccurrenceInheritsTheHabitButNotTheAnswer() {
        val task = occurrence(5, 0, status = TaskStatus.TODO, habitSince = now - 3.days, score = 6).copy(habitAfter = "Après mon café")
        val next = Recurrence.nextOccurrence(task, today, emptyList(), now)!!
        assertEquals(task.habitSince, next.habitSince)
        assertEquals("Après mon café", next.habitAfter)
        assertNull(next.habitScore)
    }

    @Test
    fun installedNeedsThreeAnswersWithTheLastTwoHigh() {
        assertEquals(HabitStatus.IN_PROGRESS, Habits.status(emptyList()))
        assertEquals(HabitStatus.IN_PROGRESS, Habits.status(listOf(6, 7)))
        assertEquals(HabitStatus.INSTALLED, Habits.status(listOf(2, 6, 7)))
        assertEquals(HabitStatus.IN_PROGRESS, Habits.status(listOf(6, 7, 5)))
        assertEquals(HabitStatus.IN_PROGRESS, Habits.status(listOf(6, 5, 7)))
    }

    @Test
    fun installedComesBackToInProgressOnlyAtFourOrLess() {
        assertEquals(HabitStatus.INSTALLED, Habits.status(listOf(3, 6, 6, 5)))
        assertEquals(HabitStatus.IN_PROGRESS, Habits.status(listOf(3, 6, 6, 4)))
        assertEquals(HabitStatus.INSTALLED, Habits.status(listOf(3, 6, 6, 4, 6, 7)))
    }

    @Test
    fun congratulationsOnlyOnThePassageToInstalled() {
        assertTrue(Habits.justInstalled(listOf(3, 6), 7))
        assertFalse(Habits.justInstalled(listOf(3, 6, 7), 7))
        assertFalse(Habits.justInstalled(listOf(6), 7))
    }

    @Test
    fun answersCountOnlySinceTheLastDeclaration() {
        val since = at(5, 0)
        val series = listOf(
            occurrence(10, 8, score = 2, habitSince = at(10, 0)),
            occurrence(11, 4, score = 6, habitSince = since),
            occurrence(12, 3, score = 7, habitSince = since),
            occurrence(13, 0, status = TaskStatus.TODO, habitSince = since),
        )
        assertEquals(listOf(6, 7), Habits.answers(series).map { it.score })
    }

    @Test
    fun theQuestionComesAtTheFirstOccurrenceThenEveryFourteenDays() {
        val since = now - 30.days
        val open = occurrence(2, 0, status = TaskStatus.TODO, habitSince = since)
        assertTrue(Habits.asks(KairosSnapshot(tasks = listOf(open)), open, now, batched = false))
        val recent = occurrence(50, 13, score = 4, habitSince = since)
        assertFalse(Habits.asks(KairosSnapshot(tasks = listOf(recent, open)), open, now, batched = false))
        val old = occurrence(50, 15, score = 4, habitSince = since)
        assertTrue(Habits.asks(KairosSnapshot(tasks = listOf(old, open)), open, now, batched = false))
    }

    @Test
    fun theQuestionIsAskedAgainAfterTwentyEightDaysWithoutADoneOccurrence() {
        val since = now - 60.days
        val lastDone = occurrence(50, 29, score = 7, habitSince = since)
        val open = occurrence(2, 0, status = TaskStatus.TODO, habitSince = since)
        assertTrue(Habits.asks(KairosSnapshot(tasks = listOf(lastDone, open)), open, now, batched = false))
    }

    @Test
    fun noQuestionForAGroupedCheckADoneTaskOrWhenHabitsAreOff() {
        val open = occurrence(2, 0, status = TaskStatus.TODO, habitSince = now - 1.days)
        assertFalse(Habits.asks(KairosSnapshot(tasks = listOf(open)), open, now, batched = true))
        val off = KairosSnapshot(tasks = listOf(open), settings = Settings(habitsEnabled = false))
        assertFalse(Habits.asks(off, open, now, batched = false))
        val done = open.copy(status = TaskStatus.DONE)
        assertFalse(Habits.asks(KairosSnapshot(tasks = listOf(done)), done, now, batched = false))
        val plain = occurrence(3, 0, status = TaskStatus.TODO)
        assertFalse(Habits.asks(KairosSnapshot(tasks = listOf(plain)), plain, now, batched = false))
    }

    @Test
    fun aDailySeriesDoneEveryMorningIsRegularAndProposesItsHour() {
        val snapshot = KairosSnapshot(tasks = daily(30, minute = 7 * 60 + 5))
        val suggestions = Habits.suggestions(snapshot, today, tz, emptySet(), emptySet())
        assertEquals(1, suggestions.size)
        assertEquals(2L, suggestions.single().task.id)
        assertEquals(7 * 60, suggestions.single().regularity.minuteOfDay)
    }

    @Test
    fun aSeriesYoungerThanFourteenDaysIsNotRegular() {
        assertTrue(Habits.suggestions(KairosSnapshot(tasks = daily(12)), today, tz, emptySet(), emptySet()).isEmpty())
        assertEquals(1, Habits.suggestions(KairosSnapshot(tasks = daily(14)), today, tz, emptySet(), emptySet()).size)
    }

    @Test
    fun lessThanThreeQuartersDoneIsNotRegular() {
        val skip = (1..30).filter { it % 3 == 0 }.toSet() // 10 manquées sur 30 : 67 %
        assertTrue(Habits.suggestions(KairosSnapshot(tasks = daily(30, skip = skip)), today, tz, emptySet(), emptySet()).isEmpty())
        val few = (1..30).filter { it % 5 == 0 }.toSet() // 6 manquées : 80 %
        assertEquals(1, Habits.suggestions(KairosSnapshot(tasks = daily(30, skip = few)), today, tz, emptySet(), emptySet()).size)
    }

    @Test
    fun scatteredHoursAreNotRegular() {
        val tasks = daily(30).map { t ->
            if (t.status != TaskStatus.DONE) t else t.copy(updatedAt = at((t.id - 100).toInt(), if (t.id % 2 == 0L) 7 * 60 else 19 * 60))
        }
        assertTrue(Habits.suggestions(KairosSnapshot(tasks = tasks), today, tz, emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun theTimerStartWinsOverTheCheckTime() {
        val tasks = daily(20, minute = 22 * 60)
        val sessions = tasks.filter { it.status == TaskStatus.DONE }.mapIndexed { i, t ->
            val start = at((t.id - 100).toInt().coerceAtLeast(0), 7 * 60)
            WorkSession(i + 1L, t.id, start, start + 20.minutes, createdAt = start)
        }
        val s = Habits.suggestions(KairosSnapshot(tasks = tasks, workSessions = sessions), today, tz, emptySet(), emptySet())
        assertEquals(7 * 60, s.single().regularity.minuteOfDay)
    }

    @Test
    fun groupedChecksDoNotTellTheHour() {
        // Chaque soir, « Méditer » est cochée quelques secondes après une autre tâche : l'heure de la coche ne dit rien.
        val series = daily(30, minute = 21 * 60)
        val batchLeaders = series.filter { it.status == TaskStatus.DONE }.map { t ->
            Task(t.id + 1000, "Ranger", status = TaskStatus.DONE, createdAt = t.createdAt, updatedAt = t.updatedAt - 20.seconds)
        }
        val batched = Habits.batchedChecks(series + batchLeaders)
        assertTrue(series.filter { it.status == TaskStatus.DONE }.all { it.id in batched })
        assertTrue(Habits.suggestions(KairosSnapshot(tasks = series + batchLeaders), today, tz, emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun aDismissedSeriesOrAnExistingHabitIsNotProposed() {
        val tasks = daily(30)
        assertTrue(Habits.suggestions(KairosSnapshot(tasks = tasks), today, tz, emptySet(), setOf(1L)).isEmpty())
        val habit = tasks.map { if (it.status == TaskStatus.TODO) it.copy(habitSince = now - 1.days) else it }
        assertTrue(Habits.suggestions(KairosSnapshot(tasks = habit), today, tz, emptySet(), emptySet()).isEmpty())
        val off = KairosSnapshot(tasks = tasks, settings = Settings(habitsEnabled = false))
        assertTrue(Habits.suggestions(off, today, tz, emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun aWeeklySeriesNeedsFiveOfSixWeeks() {
        // Hebdomadaire du mardi (aujourd'hui est un mardi) : 6 mardis dans la fenêtre de 42 jours.
        fun weekly(skip: Set<Int>): List<Task> {
            val done = (1..6).filter { it !in skip }.map { w ->
                occurrence(100L + w, 7 * w, recurrence = TaskRecurrence.WEEKLY).copy(recurrenceDayOfWeek = 1)
            }
            val first = occurrence(1, 50, recurrence = TaskRecurrence.WEEKLY, series = null).copy(recurrenceDayOfWeek = 1)
            val open = occurrence(2, 0, status = TaskStatus.TODO, recurrence = TaskRecurrence.WEEKLY).copy(recurrenceDayOfWeek = 1)
            return listOf(first) + done + open
        }
        assertEquals(1, Habits.suggestions(KairosSnapshot(tasks = weekly(setOf(3))), today, tz, emptySet(), emptySet()).size)
        assertTrue(Habits.suggestions(KairosSnapshot(tasks = weekly(setOf(2, 4))), today, tz, emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun weekdaySeriesSkipWeekendsAndHolidays() {
        val monday = LocalDate(2026, 11, 23)
        val friday = LocalDate(2026, 11, 27)
        val task = occurrence(1, 0, recurrence = TaskRecurrence.WEEKDAYS)
        assertEquals(5, Habits.expected(task, monday, LocalDate(2026, 11, 29), emptySet()))
        assertEquals(4, Habits.expected(task, monday, friday, setOf(LocalDate(2026, 11, 25))))
    }
}
