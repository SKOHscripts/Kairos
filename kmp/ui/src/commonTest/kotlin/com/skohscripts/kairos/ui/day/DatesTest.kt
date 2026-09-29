package com.skohscripts.kairos.ui.day

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatesTest {
    @Test
    fun shortDatesPerLanguage() {
        assertEquals("30 sept.", Dates.short(LocalDate(2026, 9, 30), "fr"))
        assertEquals("Sep 30", Dates.short(LocalDate(2026, 9, 30), "en"))
        assertEquals("1 janv.", Dates.short(LocalDate(2027, 1, 1), "de"))
    }

    @Test
    fun timesPerLanguage() {
        assertEquals("09h05", Dates.time(LocalTime(9, 5), "fr"))
        assertEquals("09:05", Dates.time(LocalTime(9, 5), "en"))
        assertEquals("9h", Dates.hour(9, "fr"))
    }

    @Test
    fun timeFieldAcceptsCommonForms() {
        assertEquals(LocalTime(9, 30), Dates.parseTime("9:30"))
        assertEquals(LocalTime(9, 30), Dates.parseTime(" 09h30 "))
        assertEquals(LocalTime(14, 0), Dates.parseTime("14h"))
        assertNull(Dates.parseTime("24:00"))
        assertNull(Dates.parseTime("9"))
        assertEquals("07:05", Dates.field(LocalTime(7, 5)))
    }

    @Test
    fun scoreHasOneDecimalAtMostWithoutTrailingZero() {
        assertEquals("16", Dates.number(16.0))
        assertEquals("6.2", Dates.number(6.2142))
        assertEquals("0.1", Dates.number(0.1111))
        assertEquals("2", Dates.number(1.96))
    }
}
