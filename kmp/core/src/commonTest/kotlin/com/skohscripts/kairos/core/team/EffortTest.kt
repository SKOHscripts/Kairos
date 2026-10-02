package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.stats.TaskStats.Calibration
import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.snapshot
import com.skohscripts.kairos.core.team.TeamFixtures.stamp
import com.skohscripts.kairos.core.team.TeamFixtures.task
import com.skohscripts.kairos.core.model.TaskSpace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class EffortTest {
    private val settings = Settings(team = TeamSettings(hoursPerPoint = 1.5))
    private val reliable = listOf(Calibration("3", 8, 360), Calibration("5", 2, 600))

    @Test
    fun estimateComesFirst() {
        val t = task(1, hours = 1.5, points = 3)
        assertEquals(1.5 to EffortSource.ESTIMATE, Effort.base(t, reliable, settings))
    }

    @Test
    fun reliableMedianComesSecond() {
        val t = task(1, hours = null, points = 3)
        assertEquals(6.0 to EffortSource.CALIBRATED, Effort.base(t, reliable, settings))
    }

    @Test
    fun pointsRateComesThird() {
        val t = task(1, hours = null, points = 8)
        assertEquals(12.0 to EffortSource.POINTS_RATE, Effort.base(t, reliable, settings))
        assertEquals(12.0 to EffortSource.POINTS_RATE, Effort.base(t, emptyList(), settings))
    }

    @Test
    fun unreliableMedianIsIgnored() {
        // Palier 5 : n = 2 < 3, la médiane (10 h) est écartée au profit de 5 x 1,5.
        val t = task(1, hours = null, points = 5)
        assertEquals(7.5 to EffortSource.POINTS_RATE, Effort.base(t, reliable, settings))
    }

    @Test
    fun defaultHoursPerPointIsTwo() {
        val t = task(1, hours = null, points = 3)
        assertEquals(6.0 to EffortSource.POINTS_RATE, Effort.base(t, emptyList(), Settings()))
    }

    @Test
    fun noEstimateNoPointsIsNeverZero() {
        val t = task(1, hours = null, points = null)
        assertEquals(null to EffortSource.NONE, Effort.base(t, reliable, settings))
        assertEquals(null to EffortSource.NONE, Effort.remaining(t, reliable, settings))
        // Le plan la pose avec l'effort par défaut (3 points x 1,5 h), marquée non estimée.
        val planned = Effort.planned(t, reliable, settings)
        assertEquals(Effort.Planned(4.5, EffortSource.NONE, unestimated = true), planned)
        // Zéro point = non renseigné.
        assertEquals(null to EffortSource.NONE, Effort.base(task(2, hours = null, points = 0), reliable, settings))
    }

    @Test
    fun progressScalesTheRemainingEffort() {
        assertEquals(3.0 to EffortSource.CALIBRATED, Effort.remaining(task(1, hours = null, points = 3, progress = 50), reliable, settings))
        assertEquals(1.0 to EffortSource.ESTIMATE, Effort.remaining(task(2, hours = 2.0, progress = 50), reliable, settings))
        assertEquals(2.0 to EffortSource.ESTIMATE, Effort.remaining(task(3, hours = 2.0, progress = null), reliable, settings))
        assertEquals(0.0 to EffortSource.ESTIMATE, Effort.remaining(task(4, hours = 2.0, progress = 100), reliable, settings))
        // Non estimée à moitié faite : la valeur par défaut est elle aussi réduite.
        assertEquals(2.25, Effort.planned(task(5, hours = null, points = null, progress = 50), reliable, settings).hours, 1e-9)
    }

    @Test
    fun teamCalibrationUsesDoneTeamTasksWithSessionsAndManualTime() {
        fun at(s: String) = Instant.parse(s)
        val done = TaskStatus.DONE
        val tasks = listOf(
            // Trois tâches d'équipe faites à 3 points : 60 + 30 (sessions) / 45 / 120 (saisie manuelle).
            task(1, hours = null, points = 3, status = done),
            task(2, hours = null, points = 3, status = done),
            task(3, hours = null, points = 3, status = done).copy(manualTimeSpentMinutes = 120),
            // Faite mais Perso : exclue. À faire : exclue.
            task(4, hours = null, points = 3, status = done, space = TaskSpace.PERSONAL),
            task(5, hours = null, points = 3),
        )
        val sessions = listOf(
            WorkSession(1, 1, at("2026-09-01T09:00:00Z"), at("2026-09-01T10:00:00Z"), stamp),
            WorkSession(2, 1, at("2026-09-02T09:00:00Z"), at("2026-09-02T09:30:00Z"), stamp),
            WorkSession(3, 2, at("2026-09-03T09:00:00Z"), at("2026-09-03T09:45:00Z"), stamp),
            WorkSession(4, 4, at("2026-09-03T09:00:00Z"), at("2026-09-03T19:00:00Z"), stamp),
        )
        val s = snapshot(tasks, listOf(member(1))).copy(workSessions = sessions)
        val calibration = Effort.teamCalibration(s, at("2026-10-01T00:00:00Z"))
        // 90, 45 et 120 minutes : médiane 90, n = 3, fiable.
        assertEquals(listOf(Calibration("3", 3, 90)), calibration)
        assertEquals(true, calibration.single().reliable)
        assertEquals(1.5 to EffortSource.CALIBRATED, Effort.base(task(9, hours = null, points = 3), calibration, settings))
        assertNull(Effort.teamCalibration(snapshot(emptyList(), emptyList()), stamp).firstOrNull())
    }
}
