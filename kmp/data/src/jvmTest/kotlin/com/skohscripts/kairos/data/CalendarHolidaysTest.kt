package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.calendar.CalendarPlan
import com.skohscripts.kairos.core.calendar.PlannedDay
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.data.db.KairosDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Calendrier importé compté comme fériés (docs/spec/jours-feries.md § Voie B) : colonne, import, interrupteur, export. */
class CalendarHolidaysTest {
    private val today = LocalDate(2026, 11, 11)
    private val plan = CalendarPlan("Usine", emptyList(), listOf(PlannedDay(LocalDate(2026, 12, 24), LocalDate(2026, 12, 26), "Fermeture", "f")), emptyMap())

    @Test fun schemaFourteenGainsTheColumnOffForExistingCalendars() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val first = KairosRepository(KairosStore.open(driver).database).also { it.load() }
        first.importCalendar("Usine", false, plan, today)
        driver.execute(null, "ALTER TABLE task DROP COLUMN habit_since", 0).await()
        driver.execute(null, "ALTER TABLE task DROP COLUMN habit_after", 0).await()
        driver.execute(null, "ALTER TABLE task DROP COLUMN habit_score", 0).await()
        driver.execute(null, "DROP TABLE interruption", 0).await()
        driver.execute(null, "ALTER TABLE calendar_source DROP COLUMN counts_as_holidays", 0).await()
        assertEquals(14L, KairosStore.actualVersion(driver))
        val opened = KairosStore.open(driver)
        assertEquals(KairosDatabase.Schema.version, KairosStore.actualVersion(driver))
        val repo = KairosRepository(opened.database).also { it.load() }
        assertFalse(repo.snapshot.value.calendarSources.single().countsAsHolidays)
    }

    @Test fun importSwitchAndReimportKeepTheChoice() = runTest {
        val f = ConnectionFixture()
        val repo = f.open()
        val id = repo.importCalendar("Usine", false, plan, today, holidays = true)!!
        assertTrue(repo.snapshot.value.calendarSources.single().countsAsHolidays)
        // Réimport sans choix (nul) : le choix du calendrier reste.
        repo.importCalendar("Usine", false, plan, today)
        assertTrue(repo.snapshot.value.calendarSources.single().countsAsHolidays)
        repo.setCalendarHolidays(id, false)
        assertFalse(repo.snapshot.value.calendarSources.single().countsAsHolidays)
    }

    @Test fun theMarksOfACalendarCountedAsHolidaysAreSkipped() = runTest {
        val f = ConnectionFixture()
        val repo = f.open()
        val id = repo.importCalendar("Usine", false, plan, today)!!
        repo.updateSettings(Settings(holidaysFr = false))
        assertTrue(Workdays.holidaysFor(today, repo.snapshot.value).isEmpty())
        repo.setCalendarHolidays(id, true)
        assertEquals(
            setOf(LocalDate(2026, 12, 24), LocalDate(2026, 12, 25), LocalDate(2026, 12, 26)),
            Workdays.holidaysFor(today, repo.snapshot.value),
        )
    }

    @Test fun theChoiceTravelsInTheExportOnlyWhenOn() = runTest {
        val f = ConnectionFixture()
        val repo = f.open()
        val id = repo.importCalendar("Usine", false, plan, today)!!
        assertFalse("countsAsHolidays" in ExportCodec.encode(repo.snapshot.value, "test", f.now))
        repo.setCalendarHolidays(id, true)
        val text = ExportCodec.encode(repo.snapshot.value, "test", f.now)
        assertTrue(ExportCodec.decode(text).calendarSources.single().countsAsHolidays)
    }
}
