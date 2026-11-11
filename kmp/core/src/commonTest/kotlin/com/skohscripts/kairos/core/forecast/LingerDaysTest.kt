package com.skohscripts.kairos.core.forecast

import com.skohscripts.kairos.core.day.DayView
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Échéance implicite apprise du rythme : 80ᵉ centile de Kaplan-Meier (docs/spec/taches-qui-trainent.md § Échéance apprise). */
class LingerDaysTest {
    private val now = Instant.parse("2026-11-11T12:00:00Z")
    private val utc = TimeZone.UTC
    private var nextId = 1L

    /** Faite à l'âge [age] (sa dernière modification), ou encore ouverte et âgée de [age] jours. */
    private fun task(age: Int, done: Boolean = true, dated: Boolean = false): Task {
        val created = now - 200.days
        return Task(
            nextId++, "T", priority = 1, fibonacciPoints = 3, deadline = if (dated) LocalDate(2026, 1, 1) else null,
            status = if (done) TaskStatus.DONE else TaskStatus.TODO,
            createdAt = if (done) created else now - age.days, updatedAt = if (done) created + age.days else now,
        )
    }

    private fun linger(tasks: List<Task>, settings: Settings = Settings(taskTypes = "")) =
        Survival.lingerDays(KairosSnapshot(tasks = tasks, settings = settings), now, utc)

    @Test
    fun theAgeByWhichFourTasksOutOfFiveAreDone() {
        // 40 tâches faites aux âges 0 à 39 : la part ouverte tombe à 1 sur 5 quand 32 sont faites, à 31 j.
        assertEquals(31, linger((0 until 40).map { task(it) }))
    }

    @Test
    fun openTasksCountInsteadOfBeingIgnored() {
        // Les mêmes 40, plus 10 encore ouvertes à 100 j : il faut toutes les faites pour tomber à 1 sur 5.
        assertEquals(39, linger((0 until 40).map { task(it) } + List(10) { task(100, done = false) }))
    }

    @Test
    fun neverBelowTheUrgencyHorizon() {
        assertEquals(14, linger(List(40) { task(1) }))
    }

    @Test
    fun nothingWhenTheHistoryIsTooShortOrTooMuchStaysOpen() {
        assertNull(linger((0 until 29).map { task(it) }))
        assertNull(linger(List(20) { task(1) } + List(20) { task(50, done = false) }))
    }

    @Test
    fun undatedTasksFirstAllTasksBelowThirty() {
        // 20 sans date (faites de 20 à 39 j) et 20 datées (de 0 à 19 j) : trop peu sans date, on prend tout.
        assertEquals(31, linger((20 until 40).map { task(it) } + (0 until 20).map { task(it, dated = true) }))
        // 40 sans date (faites à 30 j) : les datées, rapides, ne comptent pas.
        assertEquals(30, linger(List(40) { task(30) } + List(40) { task(1, dated = true) }))
    }

    @Test
    fun offWhenNotLearnedOrDisabled() {
        val tasks = (0 until 40).map { task(it) }
        assertNull(linger(tasks, Settings(taskTypes = "", implicitDeadlineAuto = false)))
        assertNull(linger(tasks, Settings(taskTypes = "", implicitDeadlineDays = 0)))
    }

    @Test
    fun theDayViewScoresWithTheLearnedValue() {
        val tasks = (0 until 40).map { task(it) }
        val day = LocalDate(2026, 11, 11)
        val learned = DayView.build(KairosSnapshot(tasks = tasks, settings = Settings(taskTypes = "")), day, null, utc, instant = now)
        assertEquals(31, learned.scoringSettings.implicitDeadlineDays)
        assertTrue(learned.implicitDeadlineLearned)
        val manual = DayView.build(KairosSnapshot(tasks = tasks, settings = Settings(taskTypes = "", implicitDeadlineAuto = false)), day, null, utc, instant = now)
        assertEquals(30, manual.scoringSettings.implicitDeadlineDays)
        assertFalse(manual.implicitDeadlineLearned)
    }
}
