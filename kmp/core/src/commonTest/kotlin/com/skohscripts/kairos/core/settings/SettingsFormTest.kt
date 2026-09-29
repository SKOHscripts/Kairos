package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsFormTest {
    private val base = Settings()

    private fun validate(vararg pairs: Pair<String, String>) = SettingsForm.validate(SettingsForm.values(base) + pairs, base)

    @Test
    fun every_field_of_the_defaults_round_trips() {
        val result = SettingsForm.validate(SettingsForm.values(base), base)
        assertEquals(base, result.settings)
        assertEquals("4", SettingsForm.values(base)["priorityValueBase"])
        assertEquals("true", SettingsForm.values(base)["cognitiveDipEnabled"])
    }

    @Test
    fun values_are_parsed_and_decimals_accept_a_comma() {
        val s = validate(
            "workdayStartHour" to " 8 ", "priorityValueBase" to "2,5", "cognitiveDipEnabled" to "false",
            "taskTypes" to " Dev, Ops ", "extraHolidays" to " 2026-12-24 ,, 2026-12-31 ",
        ).settings!!
        assertEquals(8, s.workdayStartHour)
        assertEquals(2.5, s.priorityValueBase)
        assertEquals(false, s.cognitiveDipEnabled)
        assertEquals("Dev, Ops", s.taskTypes)
        assertEquals("2026-12-24,2026-12-31", s.extraHolidays)
    }

    @Test
    fun each_field_reports_its_own_error_and_nothing_is_saved() {
        val result = validate(
            "defaultTaskDurationMinutes" to "0", "workdayEndHour" to "24", "urgencyHorizonDays" to "",
            "staleOverdueDays" to "1.5", "urgencyPeak" to "beaucoup", "priorityValueBase" to "0",
            "extraHolidays" to "2026-12-24, 25/12",
        )
        assertNull(result.settings)
        assertEquals(FieldError(FieldErrorKind.TOO_SMALL, "1"), result.fieldErrors["defaultTaskDurationMinutes"])
        assertEquals(FieldError(FieldErrorKind.TOO_LARGE, "23"), result.fieldErrors["workdayEndHour"])
        assertEquals(FieldError(FieldErrorKind.REQUIRED), result.fieldErrors["urgencyHorizonDays"])
        assertEquals(FieldError(FieldErrorKind.NOT_INTEGER), result.fieldErrors["staleOverdueDays"])
        assertEquals(FieldError(FieldErrorKind.NOT_NUMBER), result.fieldErrors["urgencyPeak"])
        assertEquals(FieldError(FieldErrorKind.TOO_SMALL, "0"), result.fieldErrors["priorityValueBase"]) // borne exclue (gt=0)
        assertEquals(FieldError(FieldErrorKind.INVALID_DATE, "25/12"), result.fieldErrors["extraHolidays"])
        assertEquals(7, result.fieldErrors.size)
    }

    @Test
    fun cross_field_rules_of_kairos_2() {
        assertEquals(GeneralError.WORKDAY_ORDER, validate("workdayStartHour" to "18").general)
        assertEquals(GeneralError.DIP_ORDER, validate("cognitiveDipTroughHour" to "17").general)
        assertNull(validate("cognitiveDipTroughHour" to "17").settings)
        // Début = tronc = fin reste permis (≤).
        val s = validate("cognitiveDipStartHour" to "14", "cognitiveDipTroughHour" to "14", "cognitiveDipEndHour" to "14").settings
        assertEquals(14, s!!.cognitiveDipTroughHour)
    }

    @Test
    fun a_missing_key_keeps_the_base_value() {
        val result = SettingsForm.validate(mapOf("statsWindowWeeks" to "4"), base.copy(workdayStartHour = 7))
        assertEquals(7, result.settings!!.workdayStartHour)
        assertEquals(4, result.settings!!.statsWindowWeeks)
    }
}
