package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.BlockSource
import com.skohscripts.kairos.core.model.CalendarSource
import com.skohscripts.kairos.core.model.DayMark
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.sync.SyncRecords
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Journées entières d'un calendrier importé comptées comme fériés (docs/spec/jours-feries.md § Voie B). */
class ImportedHolidaysTest {
    private val at = Instant.parse("2026-11-01T00:00:00Z")
    private fun source(id: Long, holidays: Boolean) = CalendarSource(id, "c$id", importedAt = at, createdAt = at, countsAsHolidays = holidays)
    private fun mark(id: Long, calendar: Long, start: LocalDate, end: LocalDate) = DayMark(id, "", start, end, BlockSource.Imported(calendar, "u$id"), at)

    @Test fun onlyTheMarksOfACountedCalendarAreHolidaysWithinTheWindow() {
        val snapshot = KairosSnapshot(
            settings = Settings(holidaysFr = false),
            calendarSources = listOf(source(1, true), source(2, false)),
            dayMarks = listOf(
                mark(1, 1, LocalDate(2026, 8, 3), LocalDate(2026, 8, 5)),
                mark(2, 2, LocalDate(2026, 9, 1), LocalDate(2026, 9, 1)),
                // Une journée qui déborde de la fenêtre (année − 1 à + 2) en est coupée.
                mark(3, 1, LocalDate(2028, 12, 31), LocalDate(2029, 1, 2)),
            ),
        )
        val holidays = Workdays.holidaysFor(LocalDate(2026, 6, 1), snapshot)
        assertEquals(setOf(LocalDate(2026, 8, 3), LocalDate(2026, 8, 4), LocalDate(2026, 8, 5), LocalDate(2028, 12, 31)), holidays)
    }

    @Test fun theCountryAndTheExtraDatesStay() {
        val snapshot = KairosSnapshot(
            settings = Settings(holidayCountry = "FR", extraHolidays = "2026-08-14"),
            calendarSources = listOf(source(1, true)),
            dayMarks = listOf(mark(1, 1, LocalDate(2026, 8, 3), LocalDate(2026, 8, 3))),
        )
        val holidays = Workdays.holidaysFor(LocalDate(2026, 6, 1), snapshot)
        assertTrue(LocalDate(2026, 7, 14) in holidays && LocalDate(2026, 8, 14) in holidays && LocalDate(2026, 8, 3) in holidays)
        assertEquals(Workdays.holidaysFor(LocalDate(2026, 6, 1), "FR", "2026-08-14"), Workdays.holidaysFor(LocalDate(2026, 6, 1), snapshot.copy(dayMarks = emptyList())))
    }

    @Test fun theChoiceIsAlwaysInTheSyncRecordSoTurningItOffSpreads() {
        val off = SyncRecords.lift(source(1, false))
        assertEquals("false", off.fields["holidays"])
        assertEquals(true, SyncRecords.buildCalendar(1, SyncRecords.lift(source(1, true))).countsAsHolidays)
        // Un enregistrement d'avant la 4.4.0 n'a pas le champ : faux.
        assertEquals(false, SyncRecords.buildCalendar(1, off.copy(fields = off.fields - "holidays")).countsAsHolidays)
    }
}
