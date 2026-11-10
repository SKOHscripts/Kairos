package com.skohscripts.kairos.core.forecast

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** File solo rejouée et semaine réaliste (docs/spec/previsions-taches.md § Étape C). */
class SoloForecastTest {
    private val zone = TimeZone.UTC
    // Une semaine sans jour férié (celle du 9 contient le 11 novembre : voir `aComingHolidayIsDrawnAmongTheSundays`).
    private var monday = LocalDate(2026, 11, 16)
    private val now get() = monday.atTime(7, 0).toInstant(zone)
    private fun day(n: Int) = monday.plus(DatePeriod(days = n))

    private fun open(id: Long, minutes: Int? = 60, deadline: LocalDate? = null, scheduled: LocalDate? = null, space: TaskSpace = TaskSpace.PERSONAL) =
        Task(id, "Ouverte $id", priority = 1, estimatedMinutes = minutes, deadline = deadline, scheduledDate = scheduled, space = space, createdAt = now, updatedAt = now)

    /**
     * Huit semaines d'historique : [perDay] minutes chaque jour ouvré (lundi à vendredi, [mondays] donne celles des lundis),
     * portées par huit tâches faites dont l'estimation vaut `ratio` fois le temps passé (facteurs réel ÷ estimé tous égaux).
     */
    private fun history(perDay: Int = 120, mondays: (Int) -> Int = { perDay }, estimatedShare: Double = 1.0, space: TaskSpace = TaskSpace.PERSONAL, firstId: Long = 1000): Pair<List<Task>, List<WorkSession>> {
        val sessions = ArrayList<WorkSession>()
        val spent = IntArray(8)
        var id = firstId
        for (week in 0 until 8) for (weekday in 0 until 5) {
            val minutes = if (weekday == 0) mondays(week) else perDay
            if (minutes == 0) continue
            val start = monday.plus(DatePeriod(days = -56 + 7 * week + weekday)).atTime(9, 0).toInstant(zone)
            val task = (week * 5 + weekday) % 8
            sessions += WorkSession(id++, firstId + task, start, start + minutes.minutes, start)
            spent[task] += minutes
        }
        val tasks = (0 until 8).map { k ->
            Task(firstId + k, "Faite $k", status = TaskStatus.DONE, estimatedMinutes = (spent[k] * estimatedShare).toInt(), space = space, createdAt = now, updatedAt = now)
        }
        return tasks to sessions
    }

    private fun snapshot(open: List<Task>, past: Pair<List<Task>, List<WorkSession>> = history(), dependencies: List<TaskDependency> = emptyList()) =
        KairosSnapshot(tasks = open + past.first, workSessions = past.second, dependencies = dependencies)

    private fun run(s: KairosSnapshot, seed: Long = 1L, runs: Int = 200, extra: List<Task> = emptyList()) = SoloForecast.run(s, monday, now, zone, seed, runs, extra)

    @Test
    fun withoutRandomnessTheQueueEndsOnTheExactDays() {
        // 120 min par jour ouvré, estimations justes (facteur 1), six tâches de 60 min : deux par jour, lundi, mardi, mercredi.
        val r = assertNotNull(run(snapshot((1L..6L).map { open(it) })))
        for (id in 1L..6L) {
            val o = r.outlooks.getValue(id)
            assertEquals(day(((id - 1) / 2).toInt()), o.p50, "tâche $id")
            assertEquals(o.p50, o.p85)
        }
        assertEquals(200, r.outlooks.getValue(1L).todayDraws)
        assertEquals(0, r.outlooks.getValue(3L).todayDraws)
        assertEquals(8, r.capacity.activeWeeks)
        assertEquals(120, r.capacity.workedDayMinutes)
        assertEquals(8, r.factorSamples)
        assertNull(r.outlooks.getValue(1L).onTimeDraws, "pas d'échéance, pas de probabilité")
    }

    @Test
    fun aComingHolidayIsDrawnAmongTheSundays() {
        monday = LocalDate(2026, 11, 9)
        // Mercredi 11 novembre férié : les tâches 5 et 6 passent au jeudi.
        val r = run(snapshot((1L..6L).map { open(it) }))!!
        assertEquals(day(1), r.outlooks.getValue(4L).p50)
        assertEquals(day(3), r.outlooks.getValue(5L).p50)
    }

    @Test
    fun theWeekendIsLearnedFromTheHistoryNotFromACalendar() {
        // Dix tâches de 60 min à 120 min par jour ouvré : cinq jours ouvrés, la dernière finit le vendredi, pas le samedi.
        val r = run(snapshot((1L..12L).map { open(it) }))!!
        assertEquals(day(4), r.outlooks.getValue(10L).p85)
        assertEquals(day(7), r.outlooks.getValue(11L).p50, "la onzième attend le lundi suivant")
    }

    @Test
    fun aScheduledTaskWaitsForItsDayAndABlockedTaskForItsBlocker() {
        val scheduled = open(1, scheduled = day(3))
        val blocked = open(2)
        val blocker = open(3, minutes = 240)
        val r = run(snapshot(listOf(scheduled, blocked, blocker), dependencies = listOf(TaskDependency(1, taskId = 2, blockerId = 3, createdAt = now))))!!
        assertEquals(day(3), r.outlooks.getValue(1L).p50)
        assertEquals(day(1), r.outlooks.getValue(3L).p50, "240 min à 120 min par jour")
        assertTrue(r.outlooks.getValue(2L).p50!! >= r.outlooks.getValue(3L).p50!!)
    }

    @Test
    fun irregularDaysAndEstimatesGiveARangeAndADeadlineProbability() {
        // Un lundi sur deux sans travail, et des tâches qui prennent en vrai le double de l'estimation une fois sur deux.
        val past = history(mondays = { if (it % 2 == 0) 0 else 120 })
        val doubled = past.first.mapIndexed { k, t -> if (k % 2 == 0) t.copy(estimatedMinutes = t.estimatedMinutes!! / 2) else t }
        // La même échéance pour toutes : l'ordre de la file reste celui des identifiants, la huitième passe en dernier.
        val r = run(snapshot((1L..8L).map { open(it, deadline = day(8)) }, doubled to past.second), runs = 500)!!
        val last = r.outlooks.getValue(8L)
        assertTrue(last.p50!! < last.p85!!, "pas de fourchette : ${last.p50}")
        val onTime = last.onTimeDraws!!
        assertTrue(onTime in 1 until 500, "échéance tenue $onTime fois sur 500")
        // P85 ≤ échéance ⟺ au moins 85 % des tirages à l'heure.
        assertEquals(onTime >= 425, last.p85 <= day(8))
    }

    @Test
    fun sameSeedSameResultAndFixedValuesOnEveryTarget() {
        val past = history(mondays = { if (it % 2 == 0) 0 else 120 }, estimatedShare = 0.8)
        val s = snapshot((1L..8L).map { open(it, minutes = 30 + 15 * (it % 4).toInt()) }, past)
        val a = run(s, seed = 20_261_109L, runs = 300)!!
        val b = run(s.copy(tasks = s.tasks.reversed(), workSessions = s.workSessions.reversed()), seed = 20_261_109L, runs = 300)!!
        assertEquals(a.outlooks, b.outlooks, "l'ordre d'entrée ne change rien")
        // Valeurs figées : toute divergence entre JVM, Android et wasm les casse.
        assertEquals(EXPECTED, a.outlooks.values.map { listOf(monday.daysUntilOrMinus(it.p50), monday.daysUntilOrMinus(it.p85), it.todayDraws) })
    }

    private fun LocalDate.daysUntilOrMinus(other: LocalDate?): Int = if (other == null) -1 else (other.toEpochDays() - toEpochDays()).toInt()

    @Test
    fun acceptingOneMoreUrgentTaskNeverBringsAnotherOneEarlier() {
        val past = history(mondays = { if (it % 2 == 0) 0 else 120 }, estimatedShare = 0.7)
        val s = snapshot((1L..8L).map { open(it) }, past)
        val before = run(s, runs = 300)!!
        // Même graine : les tirages des tâches et des jours sont communs ; seule la tâche ajoutée (prioritaire) diffère.
        val urgent = Task(-1, "Hypothétique", priority = 0, estimatedMinutes = 180, createdAt = now, updatedAt = now)
        val after = run(s, runs = 300, extra = listOf(urgent))!!
        var later = 0
        for (id in 1L..8L) {
            val a = before.outlooks.getValue(id)
            val b = after.outlooks.getValue(id)
            assertTrue(b.p50!! >= a.p50!! && b.p85!! >= a.p85!!, "tâche $id")
            if (b.p85 > a.p85) later++
        }
        assertTrue(later > 0, "la tâche ajoutée ne repousse rien : la propriété serait vide")
        assertNotNull(after.outlooks[-1L])
    }

    @Test
    fun tooLittleTrackedTimeGivesNoForecastAtAll() {
        // Trois semaines actives seulement : rien n'est affiché plutôt qu'une date fragile.
        val past = history()
        val three = past.second.filter { it.startedAt >= day(-21).atTime(0, 0).toInstant(zone) }
        assertNull(run(snapshot(listOf(open(1)), past.first to three)))
        assertNull(run(snapshot(emptyList())), "file vide")
    }

    @Test
    fun teamTasksAndTheirSessionsChangeNothing() {
        val s = snapshot((1L..6L).map { open(it) })
        val team = history(perDay = 300, space = TaskSpace.TEAM, firstId = 5000)
        val mixed = s.copy(tasks = s.tasks + team.first + open(900, space = TaskSpace.TEAM), workSessions = s.workSessions + team.second)
        assertEquals(run(s)!!.outlooks, run(mixed)!!.outlooks)
    }

    @Test
    fun theRealisticWeekComesFromTheEightLastWeeks() {
        // Semaines de 600 min, sauf un lundi sur deux sans travail (480) : la fourchette va de 480 à 600.
        val s = snapshot(
            listOf(open(1, minutes = 90, deadline = day(2)), open(2, minutes = 45, scheduled = day(4)), open(3, deadline = day(9)), open(4)),
            history(mondays = { if (it % 2 == 0) 0 else 120 }),
        )
        val c = SoloForecast.capacity(s, monday, now, zone)
        assertEquals(listOf(480, 480, 600), listOf(c.weekMinutes(15), c.weekMinutes(50), c.weekMinutes(85)))
        assertEquals(135, SoloForecast.committedMinutes(s, monday, now, zone), "échéance ou date programmée dans la semaine")
        assertEquals(60, SoloForecast.committedMinutes(s, day(7), now, zone))
    }

    private companion object {
        /** Par tâche, dans l'ordre de la file : jours jusqu'au P50, jusqu'au P85, tirages finis le jour même (sur 300). */
        val EXPECTED: List<List<Int>> = listOf(
            listOf(0, 1, 157), listOf(0, 1, 157), listOf(1, 2, 0), listOf(1, 2, 0), listOf(2, 3, 0), listOf(2, 3, 0), listOf(3, 4, 0), listOf(4, 8, 0),
        )
    }
}
