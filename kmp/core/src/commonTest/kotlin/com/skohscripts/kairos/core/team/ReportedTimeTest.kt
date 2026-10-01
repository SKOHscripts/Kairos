package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.stats.TaskStats.Calibration
import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.monday
import com.skohscripts.kairos.core.team.forecast.ForecastData
import com.skohscripts.kairos.core.team.forecast.ForecastFixtures
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Jalon E6 (docs/spec/equipe-echanges.md § Intégration) : le temps rapporté par un membre (`Task.reportedMinutes`)
 * **s'ajoute** aux sessions et à la saisie manuelle dans tous les calculs de temps passé d'une tâche d'équipe.
 */
class ReportedTimeTest {
    private val stamp = TeamFixtures.stamp
    private fun at(s: String) = Instant.parse(s)

    private fun session(id: Long, taskId: Long, start: String, minutes: Int) =
        WorkSession(id, taskId, at(start), at(start) + minutes.minutes, stamp)

    private fun done(id: Long, reported: Int? = null, manual: Int? = null, hours: Double? = 2.0) =
        TeamFixtures.task(id, 1, hours, status = TaskStatus.DONE, updatedAt = at("2026-09-29T10:00:00Z"))
            .copy(reportedMinutes = reported, manualTimeSpentMinutes = manual, teamUid = "uid-$id")

    @Test
    fun spentMinutesAddsSessionsManualAndReportedTime() {
        val task = done(1, reported = 360, manual = 30)
        val spent = TimeTracking.spentMinutesByTask(listOf(session(1, 1, "2026-09-28T09:00:00Z", 60)), stamp, listOf(task))
        assertEquals(60 + 30 + 360, spent[1L])
        // Sans temps rapporté : inchangé. Temps rapporté seul : il suffit.
        assertEquals(30 + 60, TimeTracking.spentMinutesByTask(listOf(session(1, 1, "2026-09-28T09:00:00Z", 60)), stamp, listOf(task.copy(reportedMinutes = null)))[1L])
        assertEquals(360, TimeTracking.spentMinutesByTask(emptyList(), stamp, listOf(task.copy(manualTimeSpentMinutes = null)))[1L])
        assertEquals(emptyMap(), TimeTracking.spentMinutesByTask(emptyList(), stamp, listOf(task.copy(manualTimeSpentMinutes = null, reportedMinutes = 0))))
    }

    @Test
    fun aNewReportReplacesTheTotalSoNothingIsCountedTwice() {
        val first = done(1, reported = 360)
        val second = first.copy(reportedMinutes = 480) // le deuxième rapport porte le total, pas 360 + 480
        assertEquals(480, TimeTracking.spentMinutesByTask(emptyList(), stamp, listOf(second))[1L])
    }

    @Test
    fun teamCalibrationCountsTheReportedTime() {
        // Trois tâches faites à 3 points : 90 (sessions) / 45 (rapporté) / 120 (30 saisies + 90 rapportées).
        val tasks = listOf(done(1, hours = null), done(2, reported = 45, hours = null), done(3, reported = 90, manual = 30, hours = null))
        val s = TeamFixtures.snapshot(tasks, listOf(member(1))).copy(
            workSessions = listOf(session(1, 1, "2026-09-01T09:00:00Z", 90)),
        )
        assertEquals(listOf(Calibration("3", 3, 90)), Effort.teamCalibration(s, at("2026-10-01T00:00:00Z")))
        // Sans le temps rapporté, la tâche 2 n'a aucun temps (exclue) : 90 et 30, médiane 60.
        val without = s.copy(tasks = tasks.map { it.copy(reportedMinutes = null) })
        assertEquals(listOf(Calibration("3", 2, 60)), Effort.teamCalibration(without, at("2026-10-01T00:00:00Z")))
    }

    @Test
    fun errorFactorsOfTheForecastCountTheReportedTime() {
        val tasks = (1L..8L).map { done(it, reported = 120) } // 2 h rapportées sur 2 h estimées
        val s = TeamFixtures.snapshot(tasks, listOf(member(1), member(2)), settings = TeamFixtures.settings(TeamSettings(focusFactor = 1.0)))
        val data = ForecastData.build(s, monday, ForecastFixtures.now, TimeZone.UTC)
        assertEquals(List(8) { 1.0 }, data.errorFactors)
        // Le double rapporté double le facteur.
        val doubled = s.copy(tasks = tasks.map { it.copy(reportedMinutes = 240) })
        assertEquals(List(8) { 2.0 }, ForecastData.build(doubled, monday, ForecastFixtures.now, TimeZone.UTC).errorFactors)
    }

    @Test
    fun reportedTimeIsNotDatedSoItDoesNotFeedTheCapacityFactors() {
        // Une semaine passée complète, des heures seulement rapportées : aucun échantillon de capacité.
        val tasks = listOf<Task>(done(1, reported = 600))
        val s = TeamFixtures.snapshot(tasks, listOf(member(1))).copy(settings = TeamFixtures.settings(TeamSettings(focusFactor = 1.0, minSamples = 1)))
        assertEquals(emptyMap(), ForecastData.build(s, monday, ForecastFixtures.now, TimeZone.UTC).capacityFactors)
    }
}
