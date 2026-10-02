package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.TeamFixtures.absence
import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.monday
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapacityTest {
    private val default = Settings(holidaysFr = false, team = TeamSettings(enabled = true))
    private fun day(n: Int) = monday.plus(DatePeriod(days = n))
    private val sunday = day(6)

    @Test
    fun specExample_80PercentSevenHoursFocus80_oneHolidayOneAbsentDay() {
        // Semaine du 5 au 9 octobre : mardi férié, mercredi absent -> lundi, jeudi, vendredi.
        val m = member(1, hoursPerDay = 7.0, percent = 80)
        val holidays = setOf(day(1))
        val absences = listOf(absence(1, 1, day(2), day(2)))
        val hours = Capacity.overRange(m, monday, sunday, holidays, absences, default)
        assertEquals(13.44, hours, 1e-9)
        // 3 jours x 7 h x 0,8 x 0,8
        assertEquals(3 * 7 * 0.8 * 0.8, hours, 1e-9)
    }

    @Test
    fun dailyHoursIsZeroOnWeekendsHolidaysAndAbsences_rangeEndsIncluded() {
        val m = member(1)
        val focus1 = Settings(holidaysFr = false, team = TeamSettings(focusFactor = 1.0))
        val absences = listOf(absence(1, 1, day(1), day(3)), absence(2, 2, monday, monday))
        fun h(d: LocalDate, holidays: Set<LocalDate> = emptySet()) = Capacity.dailyHours(m, d, holidays, absences, focus1)
        assertEquals(8.0, h(monday), 0.0) // l'absence du membre 2 ne compte pas
        assertEquals(0.0, h(day(1)), 0.0) // début de plage
        assertEquals(0.0, h(day(2)), 0.0)
        assertEquals(0.0, h(day(3)), 0.0) // fin de plage incluse
        assertEquals(8.0, h(day(4)), 0.0)
        assertEquals(0.0, h(day(5)), 0.0) // samedi
        assertEquals(0.0, h(day(6)), 0.0) // dimanche
        assertEquals(0.0, h(day(4), setOf(day(4))), 0.0) // férié
    }

    @Test
    fun defaultFocusIs80Percent() {
        val m = member(1, hoursPerDay = 8.0)
        assertEquals(6.4, Capacity.dailyHours(m, monday, emptySet(), emptyList(), default), 1e-9)
        assertEquals(32.0, Capacity.weeklyHours(m, default), 1e-9)
        assertEquals(sunday, Capacity.horizonEnd(monday, 1))
        assertEquals(day(27), Capacity.horizonEnd(monday, 4))
        assertEquals(day(27), Capacity.horizonEnd(day(3), 4)) // jeudi : même dimanche
    }

    @Test
    fun todayFractionFollowsTheRemainingWorkday() {
        // Journée 9 h - 18 h par défaut.
        fun at(h: Int, m: Int = 0) = LocalDateTime(2026, 10, 5, h, m)
        assertEquals(1.0, Capacity.todayFraction(at(7), default), 0.0)
        assertEquals(1.0, Capacity.todayFraction(at(9), default), 0.0)
        assertEquals(0.5, Capacity.todayFraction(at(13, 30), default), 1e-9)
        assertEquals(1.0 / 9.0, Capacity.todayFraction(at(17), default), 1e-9)
        assertEquals(0.0, Capacity.todayFraction(at(18), default), 0.0)
        assertEquals(0.0, Capacity.todayFraction(at(22), default), 0.0)
    }

    @Test
    fun todayFractionOnlyAppliesToSelfOnToday() {
        val me = member(1, self = true)
        val other = member(2)
        val focus1 = Settings(holidaysFr = false, team = TeamSettings(focusFactor = 1.0))
        fun range(m: TeamMember) = Capacity.overRange(m, monday, day(1), emptySet(), emptyList(), focus1, today = monday, todayFraction = 0.5)
        assertEquals(4.0 + 8.0, range(me), 1e-9)
        assertEquals(8.0 + 8.0, range(other), 1e-9)
        // Sans « aujourd'hui », la fraction n'agit pas.
        assertEquals(16.0, Capacity.overRange(me, monday, day(1), emptySet(), emptyList(), focus1, todayFraction = 0.5), 1e-9)
        assertEquals(4.0, Capacity.dailyHours(me, monday, emptySet(), emptyList(), focus1, 0.5), 1e-9)
    }

    @Test
    fun archivedMembersAreExcludedFromTheTeamCapacity() {
        val members = listOf(member(1), member(2), member(3, archived = true))
        val focus1 = Settings(holidaysFr = false, team = TeamSettings(focusFactor = 1.0))
        assertEquals(80.0, Capacity.team(members, monday, day(6), emptySet(), emptyList(), focus1), 1e-9)
    }

    @Test
    fun availabilityDuringTheHorizonRequiresOneFreeWorkday() {
        val m = member(1)
        val week = listOf(absence(1, 1, monday, day(4)))
        assertTrue(Capacity.isAvailableDuring(m, monday, day(7), emptySet(), week, default)) // lundi suivant libre
        assertFalse(Capacity.isAvailableDuring(m, monday, sunday, emptySet(), week, default))
        assertTrue(Capacity.isAvailableDuring(m, monday, sunday, emptySet(), emptyList(), default))
        assertTrue(Workdays.isWorkday(day(7)))
    }
}
