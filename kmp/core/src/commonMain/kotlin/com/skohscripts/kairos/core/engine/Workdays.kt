package com.skohscripts.kairos.core.engine

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Jours ouvrés et jours fériés (docs/spec/ordonnancement.md § Jours ouvrés). Pur.
 */
object Workdays {
    /** Lundi = 0 … dimanche = 6 (convention du projet). */
    fun weekday(day: LocalDate): Int = day.dayOfWeek.isoDayNumber - 1

    /** Ouvré : du lundi au vendredi, et pas dans [holidays]. */
    fun isWorkday(day: LocalDate, holidays: Set<LocalDate> = emptySet()): Boolean =
        weekday(day) < 5 && day !in holidays

    /** [start] plus [days] jours ouvrés ; `days ≤ 0` rend [start]. */
    fun addBusinessDays(start: LocalDate, days: Int, holidays: Set<LocalDate> = emptySet()): LocalDate {
        if (days <= 0) return start
        var current = start
        var remaining = days
        while (remaining > 0) {
            current = current.plus(DatePeriod(days = 1))
            if (isWorkday(current, holidays)) remaining--
        }
        return current
    }

    /** Jour ouvré strictement précédent [start]. */
    fun previousBusinessDay(start: LocalDate, holidays: Set<LocalDate> = emptySet()): LocalDate {
        var current = start.minus(DatePeriod(days = 1))
        while (!isWorkday(current, holidays)) current = current.minus(DatePeriod(days = 1))
        return current
    }

    /** [day] s'il est ouvré, sinon le jour ouvré précédent (jamais en avant). */
    fun onOrBeforeBusinessDay(day: LocalDate, holidays: Set<LocalDate> = emptySet()): LocalDate =
        if (isWorkday(day, holidays)) day else previousBusinessDay(day, holidays)

    /** Jours ouvrés dans `]start, end]` (0 si `end ≤ start`). */
    fun businessDaysBetween(start: LocalDate, end: LocalDate, holidays: Set<LocalDate> = emptySet()): Int {
        if (end <= start) return 0
        var days = 0
        var current = start
        while (current < end) {
            current = current.plus(DatePeriod(days = 1))
            if (isWorkday(current, holidays)) days++
        }
        return days
    }

    /** Dimanche de Pâques (algorithme de Meeus/Butcher). */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate(year, month, day)
    }

    /**
     * Jours fériés à exclure : fériés nationaux de [country] pour [years] (`PublicHolidays`, docs/spec/jours-feries.md ;
     * nul = aucun pays), plus les dates [extra] (texte ISO « AAAA-MM-JJ », séparées par des virgules ; une date illisible est
     * ignorée).
     */
    fun buildHolidays(years: IntRange, country: String?, extra: String = ""): Set<LocalDate> {
        val out = mutableSetOf<LocalDate>()
        if (country != null) for (y in years) out += PublicHolidays.dates(country, y)
        extra.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { text ->
            runCatching { LocalDate.parse(text.take(10)) }.getOrNull()?.let { out += it }
        }
        return out
    }

    /** Dimanche de la semaine de [day]. */
    fun endOfWeek(day: LocalDate): LocalDate = day.plus(DatePeriod(days = 6 - weekday(day)))

    /**
     * Jours fériés des réglages : ceux de [country] (`Settings.holidayCountryCode`), plus [extra], de l'année de [today] − 1
     * à + 2 ; vide si rien n'est demandé.
     */
    fun holidaysFor(today: LocalDate, country: String?, extra: String): Set<LocalDate> {
        if (country == null && extra.split(',').none { it.isNotBlank() }) return emptySet()
        return buildHolidays((today.year - 1)..(today.year + 2), country, extra)
    }
}
