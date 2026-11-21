package com.skohscripts.kairos.core.engine

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fériés par pays (docs/spec/jours-feries.md), contre python-holidays 0.109 et l'ancienne règle française. */
class PublicHolidaysTest {
    private fun days(year: Int, vararg mmdd: String) = mmdd.mapTo(HashSet()) { LocalDate(year, it.take(2).toInt(), it.drop(2).toInt()) }

    @Test fun theThirteenCountriesOfTheSixLanguages() {
        assertEquals(listOf("AT", "BE", "CA", "CH", "DE", "ES", "FR", "GB", "IE", "IT", "LU", "RU", "US"), PublicHolidays.COUNTRIES)
        assertTrue(PublicHolidays.SOURCE.startsWith("python-holidays "))
    }

    @Test fun everyCountryMatchesTheSource2026() {
        val expected = mapOf(
            "AT" to days(2026, "0101", "0106", "0406", "0501", "0514", "0525", "0604", "0815", "1026", "1101", "1208", "1225", "1226"),
            "BE" to days(2026, "0101", "0405", "0406", "0501", "0514", "0524", "0525", "0721", "0815", "1101", "1111", "1225"),
            "CA" to days(2026, "0101", "0403", "0701", "0907", "1225"),
            "CH" to days(2026, "0101", "0514", "0801", "1225"),
            "DE" to days(2026, "0101", "0403", "0406", "0501", "0514", "0525", "1003", "1225", "1226"),
            "ES" to days(2026, "0101", "0106", "0403", "0501", "0815", "1012", "1208", "1225"),
            "FR" to days(2026, "0101", "0406", "0501", "0508", "0514", "0525", "0714", "0815", "1101", "1111", "1225"),
            // Boxing Day (samedi) reporté au lundi 28.
            "GB" to days(2026, "0101", "0403", "0504", "0525", "1225", "1226", "1228"),
            "IE" to days(2026, "0101", "0202", "0317", "0406", "0504", "0601", "0803", "1026", "1225", "1226"),
            "IT" to days(2026, "0101", "0106", "0405", "0406", "0425", "0501", "0602", "0815", "1004", "1101", "1208", "1225", "1226"),
            "LU" to days(2026, "0101", "0406", "0501", "0509", "0514", "0525", "0623", "0815", "1101", "1225", "1226"),
            // Ponts décrétés : 9 janvier et 31 décembre reportés des 3 et 4 janvier.
            "RU" to days(2026, "0101", "0102", "0103", "0104", "0105", "0106", "0107", "0108", "0109", "0223", "0308", "0309", "0501", "0509", "0511", "0612", "1104", "1231"),
            // Independence Day (samedi) observé le vendredi 3 juillet.
            "US" to days(2026, "0101", "0119", "0216", "0525", "0619", "0703", "0704", "0907", "1012", "1111", "1126", "1225"),
        )
        for (code in PublicHolidays.COUNTRIES) assertEquals(expected.getValue(code), PublicHolidays.dates(code, 2026), code)
    }

    /**
     * L'ancienne règle française de `Workdays` (8 fixes, lundi de Pâques, Ascension, lundi de Pentecôte), gardée en oracle. Seul
     * écart : le lundi de Pentecôte de 2005 à 2007, « journée de solidarité » travaillée, que python-holidays ne compte pas.
     */
    @Test fun franceMatchesTheFormerRule() {
        for (year in PublicHolidays.FIRST_YEAR..PublicHolidays.LAST_YEAR) {
            val easter = Workdays.easterSunday(year)
            val whitMonday = if (year in 2005..2007) emptyList() else listOf(50)
            val former = days(year, "0101", "0501", "0508", "0714", "0815", "1101", "1111", "1225") +
                (listOf(1, 39) + whitMonday).map { easter.plus(DatePeriod(days = it)) }
            assertEquals(former, PublicHolidays.dates("FR", year), "$year")
        }
    }

    @Test fun outsideTheTableOrUnknownCountryGivesNothing() {
        assertEquals(emptySet(), PublicHolidays.dates("FR", PublicHolidays.FIRST_YEAR - 1))
        assertEquals(emptySet(), PublicHolidays.dates("FR", PublicHolidays.LAST_YEAR + 1))
        assertEquals(emptySet(), PublicHolidays.dates("XX", 2026))
    }

    @Test fun theDefaultFollowsASupportedRegion() {
        assertEquals("DE", PublicHolidays.defaultFor("de"))
        assertEquals("GB", PublicHolidays.defaultFor("UK"))
        assertEquals("BE", PublicHolidays.defaultFor(" BE "))
        assertEquals("", PublicHolidays.defaultFor("JP"))
        assertEquals("", PublicHolidays.defaultFor(""))
        assertEquals("", PublicHolidays.defaultFor(null))
    }

    @Test fun workdaysReadTheCountryAndTheExtraDates() {
        val holidays = Workdays.holidaysFor(LocalDate(2026, 5, 1), "DE", "2026-12-24")
        assertTrue(LocalDate(2026, 10, 3) in holidays && LocalDate(2026, 12, 24) in holidays)
        assertTrue(LocalDate(2026, 7, 14) !in holidays)
        assertEquals(emptySet(), Workdays.holidaysFor(LocalDate(2026, 5, 1), null, ""))
    }
}
