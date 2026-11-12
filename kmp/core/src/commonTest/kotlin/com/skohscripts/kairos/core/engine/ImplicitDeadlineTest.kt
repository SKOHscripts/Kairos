package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Échéance implicite des tâches sans date (docs/spec/taches-qui-trainent.md). */
class ImplicitDeadlineTest {
    private val today = LocalDate(2026, 11, 11)
    private val settings = Settings(taskTypes = "") // 30 j, horizon 14 j, pic 8

    /** Tâche créée il y a [age] jours (minuit UTC). */
    private fun task(id: Long, age: Int, deadline: LocalDate? = null, scheduled: LocalDate? = null): Task {
        val created = Instant.parse("${today.minus(DatePeriod(days = age))}T00:00:00Z")
        return Task(id, "T$id", priority = 1, fibonacciPoints = 3, deadline = deadline, scheduledDate = scheduled, createdAt = created, updatedAt = created)
    }

    @Test
    fun aDatelessTaskClimbsTheUsualRamp() {
        val crit = { age: Int -> Scheduling.timeCriticality(task(1, age), today, settings) }
        assertEquals(0.0, crit(0))
        assertEquals(0.0, crit(16)) // 14 j avant l'échéance implicite : hors horizon
        assertEquals(settings.urgencyPeak * 7 / 14, crit(23))
        assertEquals(settings.urgencyPeak, crit(30))
        assertEquals(settings.urgencyPeak, crit(200))
    }

    @Test
    fun realDatesAndTheSwitchWin() {
        // Une tâche datée garde sa propre rampe, même très vieille.
        val dated = task(1, 200, deadline = today.plus(DatePeriod(days = 20)))
        assertEquals(0.0, Scheduling.timeCriticality(dated, today, settings))
        // Réglage à 0 : rien.
        assertEquals(0.0, Scheduling.timeCriticality(task(2, 200), today, settings.copy(implicitDeadlineDays = 0)))
    }

    @Test
    fun neverOverdue() {
        val old = task(1, 200)
        assertFalse(Scheduling.isOverdue(old, today))
        assertEquals(1, Scheduling.sortKey(old, today, settings).tier)
        assertEquals(4, Scheduling.urgencyBucket(old, today))
        val why = Scheduling.wsjfBreakdown(old, today, settings)
        assertEquals(Scheduling.DateKind.IMPLICIT, why.dateKind)
        assertEquals(-170, why.daysUntil)
        assertFalse(why.overdue)
    }

    @Test
    fun anOldDatelessTaskPassesAFreshDatedOne() {
        val old = task(1, 60)
        val dated = task(2, 1, deadline = today.plus(DatePeriod(days = 7)))
        assertTrue(Scheduling.sortKey(old, today, settings) < Scheduling.sortKey(dated, today, settings))
        // Sans échéance implicite, la tâche datée passait devant : le défaut constaté.
        val off = settings.copy(implicitDeadlineDays = 0)
        assertTrue(Scheduling.sortKey(dated, today, off) < Scheduling.sortKey(old, today, off))
    }

    @Test
    fun lingeringFromTheImplicitDeadline() {
        assertFalse(Scheduling.isLingering(task(1, 29), today, settings))
        assertTrue(Scheduling.isLingering(task(1, 30), today, settings))
        assertFalse(Scheduling.isLingering(task(1, 90, scheduled = today.plus(DatePeriod(days = 3))), today, settings))
        assertFalse(Scheduling.isLingering(task(1, 90), today, settings.copy(implicitDeadlineDays = 0)))
    }
}
