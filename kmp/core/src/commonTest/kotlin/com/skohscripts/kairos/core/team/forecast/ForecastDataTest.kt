package com.skohscripts.kairos.core.team.forecast

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.team.TeamFixtures
import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.monday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ForecastDataTest {
    private val now = ForecastFixtures.now
    private val tz = ForecastFixtures.timeZone

    /** Tâche d'équipe faite le [doneAt], estimée à 2 h par défaut. */
    private fun done(id: Long, assignee: Long? = 1, doneAt: String = "2026-09-29T10:00:00Z", hours: Double? = 2.0, points: Int? = 3, manual: Int? = null): Task =
        TeamFixtures.task(id, assignee, hours, points = points, status = TaskStatus.DONE, updatedAt = Instant.parse(doneAt))
            .copy(manualTimeSpentMinutes = manual, teamUid = "uid-$id")

    private fun session(id: Long, taskId: Long, start: String, minutes: Int) =
        WorkSession(id, taskId, Instant.parse(start), Instant.parse(start) + minutes.minutes, Instant.parse(start))

    private fun snap(tasks: List<Task>, sessions: List<WorkSession> = emptyList(), team: TeamSettings = TeamSettings(focusFactor = 1.0)): KairosSnapshot =
        TeamFixtures.snapshot(tasks, listOf(member(1), member(2)), settings = TeamFixtures.settings(team)).copy(workSessions = sessions)

    private fun build(s: KairosSnapshot) = ForecastData.build(s, monday, now, tz)

    @Test
    fun errorFactorsAreActualOverBaseSortedAndBounded() {
        val tasks = listOf(
            done(1), done(2), done(3), done(4), done(5), done(6),
            done(7, hours = null, points = 5), // pas d'estimation, un palier sans calibration fiable : 5 points × 2 h/point = 10 h
        )
        val sessions = listOf(
            session(1, 1, "2026-09-28T08:00:00Z", 60), // 0,5
            session(2, 2, "2026-09-28T08:00:00Z", 120), // 1
            session(3, 3, "2026-09-28T08:00:00Z", 180), // 1,5
            session(4, 4, "2026-09-28T08:00:00Z", 20 * 60), // 10 -> borné à 5
            session(5, 5, "2026-09-28T08:00:00Z", 6), // 0,05 -> borné à 0,2
            session(6, 6, "2026-09-28T08:00:00Z", 60), session(7, 6, "2026-09-29T08:00:00Z", 60), // 2 sessions : 2 h = 1
            session(8, 7, "2026-09-28T08:00:00Z", 600), // 10 h sur 10 h = 1
        )
        val d = build(snap(tasks, sessions))
        assertEquals(listOf(0.2, 0.5, 1.0, 1.0, 1.0, 1.5, 5.0), d.errorFactors)
        assertEquals(DataSourceKind.MIXED, d.estimationSource.kind) // 7 < 8
        assertFalse(d.estimationSource.reliable)
        assertEquals(7, d.estimationSource.samples)
        assertEquals(12, d.estimationSource.windowWeeks)
    }

    @Test
    fun manualTimeCountsAndOneMoreTaskReachesTheMinimum() {
        val tasks = (1L..8L).map { done(it, manual = 120) } // 2 h saisies à la main sur 2 h estimées
        val d = build(snap(tasks))
        assertEquals(List(8) { 1.0 }, d.errorFactors)
        assertEquals(DataSourceKind.HISTORY, d.estimationSource.kind)
        assertTrue(d.estimationSource.reliable)
    }

    @Test
    fun tasksWithoutBaseOrTimeOrOutsideTheWindowAreIgnored() {
        val tasks = listOf(
            done(1), // pas de temps passé : ignorée
            done(2, hours = null, points = null, manual = 60), // ni estimation ni points : pas de base
            done(3, doneAt = "2026-04-01T10:00:00Z", manual = 60), // avant la fenêtre de 12 semaines
            done(4, manual = 60), // la seule retenue : 0,5
            TeamFixtures.task(5, 1, 2.0, status = TaskStatus.TODO).copy(manualTimeSpentMinutes = 60, teamUid = "uid-5"), // pas faite
        )
        assertEquals(listOf(0.5), build(snap(tasks)).errorFactors)
        // La fenêtre se règle : sur 40 semaines, la tâche d'avril compte.
        assertEquals(listOf(0.5, 0.5), build(snap(tasks, team = TeamSettings(focusFactor = 1.0, historyWeeks = 40))).errorFactors)
    }

    @Test
    fun throughputCountsFinishedTasksPerWeekIncludingZerosOnCompleteWeeksOnly() {
        // Aujourd'hui lundi 5 octobre : la fenêtre va du 13 juillet au 4 octobre (12 semaines complètes).
        val tasks = listOf(
            done(1, 1, "2026-09-29T10:00:00Z"), done(2, 2, "2026-09-30T10:00:00Z"), // semaine du 28 sept. (dernière, indice 11)
            done(3, 1, "2026-09-22T10:00:00Z"), // semaine du 21 sept. (indice 10)
            done(4, 1, "2026-07-14T10:00:00Z"), // première semaine (indice 0)
            done(5, 1, "2026-10-05T07:00:00Z"), // semaine en cours : pas dans les débits
            done(6, 1, "2026-07-12T10:00:00Z"), // avant la fenêtre
        )
        val d = build(snap(tasks))
        assertEquals(12, d.teamThroughput.size)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2), d.teamThroughput)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1), d.throughputOf(1L))
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1), d.throughputOf(2L))
        assertTrue(d.throughputOf(99L).isEmpty())
        // 3 semaines actives : indisponible.
        assertFalse(d.throughputAvailable())
        assertEquals(DataSourceKind.MIXED, d.throughputSource().kind)
        assertEquals(4, d.throughputSource().samples)
        assertEquals(3, d.throughputSource().activeWeeks)
        // Une quatrième semaine active : disponible (mais peu fiable sous 8 semaines actives).
        val more = build(snap(tasks + done(7, 1, "2026-09-15T10:00:00Z")))
        assertTrue(more.throughputAvailable())
        assertFalse(more.throughputSource().reliable)
        // 8 semaines actives : fiable.
        val eight = build(snap((0 until 8).map { done(10L + it, 1, "${kotlinx.datetime.LocalDate.fromEpochDays(LocalDateOf("2026-07-20").toEpochDays() + 7 * it)}T10:00:00Z") }))
        assertEquals(DataSourceKind.HISTORY, eight.throughputSource().kind)
        assertEquals(DataSourceKind.DEFAULT, build(snap(emptyList())).throughputSource().kind)
    }

    @Test
    fun capacityFactorsAreRealHoursOverPlannedCapacityPerPastWeek() {
        // Membre 1 : 8 h/j, 100 %, focus 1 : 40 h par semaine. 8 semaines à 30 h chronométrées : 0,75.
        val weekStarts = listOf("2026-08-10", "2026-08-17", "2026-08-24", "2026-08-31", "2026-09-07", "2026-09-14", "2026-09-21", "2026-09-28")
        val work = done(1, 1)
        val sessions = weekStarts.mapIndexed { i, w -> session(i + 1L, 1, "${w}T08:00:00Z", 30 * 60) }
        val d = build(snap(listOf(work), sessions))
        assertEquals(List(8) { 0.75 }, d.capacityFactors[1L])
        assertTrue(d.hasCapacityHistory(1L))
        assertFalse(d.hasCapacityHistory(2L))
        // Sept semaines seulement : sous le minimum, pas d'aléa de capacité (facteur 1).
        val seven = build(snap(listOf(work), sessions.take(7)))
        assertFalse(seven.hasCapacityHistory(1L))
        // Heures supérieures à la capacité : borné à 1,5 ; très inférieures : borné à 0,3.
        val over = build(snap(listOf(work), weekStarts.mapIndexed { i, w -> session(i + 1L, 1, "${w}T08:00:00Z", (if (i % 2 == 0) 90 else 6) * 60) }))
        assertEquals(listOf(0.3, 0.3, 0.3, 0.3, 1.5, 1.5, 1.5, 1.5), over.capacityFactors[1L])
    }

    @Test
    fun aWeekOfAbsenceOrWithoutRecordedHoursIsNotASample() {
        val work = done(1, 1)
        val starts = listOf("2026-08-03", "2026-08-10", "2026-08-17", "2026-08-24", "2026-08-31", "2026-09-07", "2026-09-14", "2026-09-21", "2026-09-28")
        val sessions = starts.mapIndexed { i, w -> session(i + 1L, 1, "${w}T08:00:00Z", 20 * 60) }
        // Neuf semaines chronométrées dont une en congé complet (capacité 0 : pas d'échantillon) : 8 échantillons.
        val away = TeamFixtures.absence(1, 1, LocalDateOf("2026-08-10"), LocalDateOf("2026-08-16"))
        val s = snap(listOf(work), sessions).copy(absences = listOf(away))
        assertEquals(8, build(s).capacityFactors[1L]!!.size)
        // Sans la neuvième semaine ni l'absence : 8 échantillons aussi ; sans une semaine de plus : 7, donc rien.
        assertFalse(build(snap(listOf(work), sessions.drop(2))).hasCapacityHistory(1L))
    }

    @Test
    fun buildIsIndependentOfTheOrderOfTheInput() {
        val tasks = (1L..9L).map { done(it, manual = 30 + it.toInt() * 15) }
        val a = build(snap(tasks))
        val b = build(snap(tasks.reversed()))
        assertEquals(a, b)
        assertEquals(a.errorFactors.sorted(), a.errorFactors)
    }

    private fun LocalDateOf(iso: String) = kotlinx.datetime.LocalDate.parse(iso)
}
