package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.engine.minutesBetween
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus

/**
 * Capacité des membres (docs/spec/equipe-charge.md § Capacité) : les heures que
 * chacun peut consacrer aux tâches, jour par jour. Pur : le jour, les jours
 * fériés, les absences et les réglages sont des paramètres.
 *
 * Capacité d'un jour ouvré hors absence : `heures par jour × quotité × taux de
 * focus`. Elle est **journalière** (le manager ne tient pas l'agenda des
 * membres) et en heures décimales.
 */
object Capacity {
    /** Jours ouvrés d'une semaine nominale (lundi au vendredi), pour la capacité hebdomadaire. */
    const val WORKDAYS_PER_WEEK = 5

    private fun focus(settings: Settings): Double = (settings.team ?: TeamSettings()).focusFactor

    /** Fin de l'horizon : dimanche de la dernière des [weeks] semaines, la première étant celle de [today]. */
    fun horizonEnd(today: LocalDate, weeks: Int): LocalDate =
        Workdays.endOfWeek(today.plus(DatePeriod(days = 7 * (weeks.coerceAtLeast(1) - 1))))

    /**
     * Part restante de la journée de travail à [now] (heure locale), 0 à 1 : même
     * règle que la carte « Maintenant » (`Scheduling.buildDaySchedule` : le
     * placement part de l'heure courante si elle est dans la journée). 1 avant le
     * début de la journée, 0 à sa fin ou après.
     */
    fun todayFraction(now: LocalDateTime, settings: Settings): Double {
        val start = now.date.atTime(settings.workdayStartHour, 0)
        val end = now.date.atTime(settings.workdayEndHour, 0)
        if (now <= start) return 1.0
        if (now >= end) return 0.0
        return minutesBetween(now, end).toDouble() / minutesBetween(start, end).toDouble()
    }

    /** Heures d'un jour ouvré ordinaire d'un membre : `heures par jour × quotité × taux de focus`. */
    fun nominalDayHours(member: TeamMember, settings: Settings): Double =
        member.hoursPerDay * member.availabilityPercent / 100.0 * focus(settings)

    /** Capacité d'une semaine nominale de cinq jours ouvrés, sans férié ni absence. */
    fun weeklyHours(member: TeamMember, settings: Settings): Double = nominalDayHours(member, settings) * WORKDAYS_PER_WEEK

    /** [day] est couvert par une absence de [member] (plage incluse). */
    fun isAbsent(member: TeamMember, day: LocalDate, absences: List<MemberAbsence>): Boolean =
        absences.any { it.memberId == member.id && day >= it.start && day <= it.end }

    /**
     * Heures de [member] le [day] : 0 hors jour ouvré (week-end ou [holidays]) et
     * un jour d'absence ; sinon la capacité nominale multipliée par
     * [todayFraction] (1 sauf pour « moi » aujourd'hui, voir [todayFraction]).
     */
    fun dailyHours(
        member: TeamMember,
        day: LocalDate,
        holidays: Set<LocalDate>,
        absences: List<MemberAbsence>,
        settings: Settings,
        todayFraction: Double = 1.0,
    ): Double {
        if (!Workdays.isWorkday(day, holidays) || isAbsent(member, day, absences)) return 0.0
        return nominalDayHours(member, settings) * todayFraction.coerceIn(0.0, 1.0)
    }

    /**
     * Capacité de [member] de [start] à [end] inclus. [todayFraction] ne
     * s'applique qu'au jour [today] et seulement si le membre est « moi » (le
     * manager ne connaît pas l'heure des autres) ; [today] `null` = pas de jour
     * entamé.
     */
    fun overRange(
        member: TeamMember,
        start: LocalDate,
        end: LocalDate,
        holidays: Set<LocalDate>,
        absences: List<MemberAbsence>,
        settings: Settings,
        today: LocalDate? = null,
        todayFraction: Double = 1.0,
    ): Double {
        val mine = absences.filter { it.memberId == member.id }
        var total = 0.0
        var day = start
        while (day <= end) {
            val fraction = if (member.isSelf && day == today) todayFraction else 1.0
            total += dailyHours(member, day, holidays, mine, settings, fraction)
            day = day.plus(DatePeriod(days = 1))
        }
        return total
    }

    /** Capacité de l'équipe : somme des membres **actifs** (les archivés sont exclus). */
    fun team(
        members: List<TeamMember>,
        start: LocalDate,
        end: LocalDate,
        holidays: Set<LocalDate>,
        absences: List<MemberAbsence>,
        settings: Settings,
        today: LocalDate? = null,
        todayFraction: Double = 1.0,
    ): Double = TeamMembers.active(members).sumOf { overRange(it, start, end, holidays, absences, settings, today, todayFraction) }

    /**
     * Le membre a au moins un jour ouvré hors absence entre [start] et [end] :
     * c'est la condition pour qu'une suggestion puisse lui confier une tâche
     * (« absent sur tout l'horizon » = faux). Ignore la fraction du jour.
     */
    fun isAvailableDuring(
        member: TeamMember,
        start: LocalDate,
        end: LocalDate,
        holidays: Set<LocalDate>,
        absences: List<MemberAbsence>,
        settings: Settings,
    ): Boolean = overRange(member, start, end, holidays, absences, settings) > 0.0
}
