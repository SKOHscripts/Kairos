package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun the_theme_color_is_normalised_or_refused() {
        assertEquals("#C28417", SettingsForm.values(base)["themeColor"])
        assertEquals("#2F6FED", validate("themeColor" to " 2f6fed ").settings!!.themeColor)
        assertEquals("#2F6FED", validate("themeColor" to "#2F6FED").settings!!.themeColor)
        assertEquals(Settings.SYSTEM_THEME, validate("themeColor" to "System").settings!!.themeColor)
        for (bad in listOf("bleu", "#12345", "#1234567", "", "#GGGGGG")) {
            val result = validate("themeColor" to bad)
            assertNull(result.settings, bad)
            assertEquals(FieldError(FieldErrorKind.INVALID_COLOR, bad.trim()), result.fieldErrors["themeColor"], bad)
        }
        assertEquals(0x2F6FED, SettingsForm.parseColor("#2f6fed"))
        assertNull(SettingsForm.parseColor(Settings.SYSTEM_THEME))
        assertNull(SettingsForm.parseColor("bleu"))
    }

    @Test
    fun saving_the_form_in_solo_mode_never_creates_the_team_object() {
        assertNull(base.team)
        assertEquals("false", SettingsForm.values(base)["team.enabled"])
        assertEquals("", SettingsForm.values(base)["team.name"])
        assertNull(validate("statsWindowWeeks" to "4").settings!!.team)
        assertNull(validate("team.enabled" to "false", "team.name" to "  ", "team.managerName" to "").settings!!.team)
    }

    @Test
    fun enabling_the_team_space_creates_the_team_settings() {
        val s = validate("team.enabled" to "true", "team.name" to " Plateforme ", "team.managerName" to "Claire").settings!!
        assertEquals(TeamSettings(enabled = true, name = "Plateforme", managerName = "Claire"), s.team)
        assertTrue(s.teamModeEnabled)
    }

    @Test
    fun disabling_keeps_the_team_settings_and_their_technical_fields() {
        val enabled = base.copy(team = TeamSettings(enabled = true, name = "P", identity = "abc", lastSpace = "team"))
        val s = SettingsForm.validate(SettingsForm.values(enabled) + ("team.enabled" to "false"), enabled).settings!!
        assertEquals(TeamSettings(enabled = false, name = "P", identity = "abc", lastSpace = "team"), s.team)
        assertTrue(!s.teamModeEnabled)
    }

    @Test
    fun follow_up_settings_have_their_defaults_and_bounds() {
        val values = SettingsForm.values(base)
        assertEquals("5", values["team.staleProgressDays"])
        assertEquals("3", values["team.churnThreshold"])
        assertEquals("3", values["team.wipLimit"])

        fun team(key: String, value: String) = validate(key to value)
        assertEquals(1, team("team.staleProgressDays", "1").settings!!.team!!.staleProgressDays)
        assertEquals(FieldErrorKind.TOO_SMALL, team("team.staleProgressDays", "0").fieldErrors["team.staleProgressDays"]!!.kind)
        assertEquals(2, team("team.churnThreshold", "2").settings!!.team!!.churnThreshold)
        assertEquals("2", team("team.churnThreshold", "1").fieldErrors["team.churnThreshold"]!!.bound)
        assertEquals(0, team("team.wipLimit", "0").settings!!.team!!.wipLimit)
        assertEquals(FieldErrorKind.TOO_SMALL, team("team.wipLimit", "-1").fieldErrors["team.wipLimit"]!!.kind)
        assertEquals(FieldErrorKind.NOT_INTEGER, team("team.wipLimit", "beaucoup").fieldErrors["team.wipLimit"]!!.kind)
        assertEquals(FieldErrorKind.REQUIRED, team("team.churnThreshold", " ").fieldErrors["team.churnThreshold"]!!.kind)
    }

    @Test
    fun writing_the_default_follow_up_values_keeps_the_team_object_null() {
        assertNull(validate("team.staleProgressDays" to "5", "team.churnThreshold" to "3", "team.wipLimit" to "3").settings!!.team)
        // Une valeur non défaut crée l'objet ; la sauvegarde de toutes les valeurs l'aller-retour.
        val s = validate("team.wipLimit" to "4").settings!!
        assertEquals(TeamSettings(wipLimit = 4), s.team)
        assertEquals(s, SettingsForm.validate(SettingsForm.values(s), s).settings)
    }

    @Test
    fun load_settings_have_their_defaults_and_bounds() {
        val values = SettingsForm.values(base)
        assertEquals("4", values["team.horizonWeeks"])
        assertEquals("0.8", values["team.focusFactor"])
        assertEquals("2", values["team.hoursPerPoint"])
        assertEquals("90", values["team.loadWarnPercent"])
        assertEquals("2", values["team.affinityDays"])
        assertEquals(FieldKind.DECIMAL, SettingsForm.field("team.focusFactor").kind)
        assertEquals(FieldKind.DECIMAL, SettingsForm.field("team.hoursPerPoint").kind)
        assertEquals(FieldKind.INT, SettingsForm.field("team.horizonWeeks").kind)

        fun team(key: String, value: String) = validate(key to value)
        // Horizon : 1 à 26 semaines.
        assertEquals(1, team("team.horizonWeeks", "1").settings!!.team!!.horizonWeeks)
        assertEquals(26, team("team.horizonWeeks", "26").settings!!.team!!.horizonWeeks)
        assertEquals(FieldErrorKind.TOO_SMALL, team("team.horizonWeeks", "0").fieldErrors["team.horizonWeeks"]!!.kind)
        assertEquals(FieldError(FieldErrorKind.TOO_LARGE, "26"), team("team.horizonWeeks", "27").fieldErrors["team.horizonWeeks"])
        // Focus : > 0 (borne exclue) et <= 1 ; la virgule est acceptée.
        assertEquals(1.0, team("team.focusFactor", "1").settings!!.team!!.focusFactor)
        assertEquals(0.5, team("team.focusFactor", "0,5").settings!!.team!!.focusFactor)
        assertTrue(SettingsForm.field("team.focusFactor").minExclusive)
        assertEquals(FieldError(FieldErrorKind.TOO_SMALL, "0"), team("team.focusFactor", "0").fieldErrors["team.focusFactor"])
        assertEquals(FieldError(FieldErrorKind.TOO_LARGE, "1"), team("team.focusFactor", "1.01").fieldErrors["team.focusFactor"])
        assertEquals(FieldErrorKind.NOT_NUMBER, team("team.focusFactor", "beaucoup").fieldErrors["team.focusFactor"]!!.kind)
        // Heures par point : > 0.
        assertEquals(1.5, team("team.hoursPerPoint", "1,5").settings!!.team!!.hoursPerPoint)
        assertEquals(FieldErrorKind.TOO_SMALL, team("team.hoursPerPoint", "0").fieldErrors["team.hoursPerPoint"]!!.kind)
        assertEquals(FieldErrorKind.TOO_SMALL, team("team.hoursPerPoint", "-2").fieldErrors["team.hoursPerPoint"]!!.kind)
        // Seuil d'alerte : 1 à 100 %.
        assertEquals(100, team("team.loadWarnPercent", "100").settings!!.team!!.loadWarnPercent)
        assertEquals(FieldErrorKind.TOO_SMALL, team("team.loadWarnPercent", "0").fieldErrors["team.loadWarnPercent"]!!.kind)
        assertEquals("100", team("team.loadWarnPercent", "101").fieldErrors["team.loadWarnPercent"]!!.bound)
        // Affinité : 0 ou plus.
        assertEquals(0, team("team.affinityDays", "0").settings!!.team!!.affinityDays)
        assertEquals(FieldErrorKind.TOO_SMALL, team("team.affinityDays", "-1").fieldErrors["team.affinityDays"]!!.kind)
        assertEquals(FieldErrorKind.NOT_INTEGER, team("team.affinityDays", "1,5").fieldErrors["team.affinityDays"]!!.kind)
        assertEquals(FieldErrorKind.REQUIRED, team("team.horizonWeeks", " ").fieldErrors["team.horizonWeeks"]!!.kind)
    }

    @Test
    fun writing_the_default_load_values_keeps_the_team_object_null() {
        val defaults = mapOf(
            "team.horizonWeeks" to "4", "team.focusFactor" to "0.8", "team.hoursPerPoint" to "2",
            "team.loadWarnPercent" to "90", "team.affinityDays" to "2",
        )
        assertNull(SettingsForm.validate(defaults, base).settings!!.team)
        // La virgule donne la même valeur par défaut : toujours rien d'écrit.
        assertNull(validate("team.focusFactor" to "0,8", "team.hoursPerPoint" to "2,0").settings!!.team)
        // Une valeur non défaut crée l'objet, les autres champs gardent leur défaut ; l'aller-retour est stable.
        val s = validate("team.focusFactor" to "0.7", "team.horizonWeeks" to "8").settings!!
        assertEquals(TeamSettings(focusFactor = 0.7, horizonWeeks = 8), s.team)
        assertEquals(s, SettingsForm.validate(SettingsForm.values(s), s).settings)
        assertEquals("0.7", SettingsForm.values(s)["team.focusFactor"])
        assertEquals("8", SettingsForm.values(s)["team.horizonWeeks"])
    }

    @Test
    fun forecast_settings_have_their_defaults_and_bounds() {
        val values = SettingsForm.values(base)
        assertEquals("5000", values["team.simulationRuns"])
        assertEquals("12", values["team.historyWeeks"])
        assertEquals("8", values["team.minSamples"])
        assertEquals("70", values["team.deadlineRiskPercent"])
        assertEquals(listOf(5000, 12, 8, 70), TeamSettings().let { listOf(it.simulationRuns, it.historyWeeks, it.minSamples, it.deadlineRiskPercent) })
        for (key in listOf("team.simulationRuns", "team.historyWeeks", "team.minSamples", "team.deadlineRiskPercent")) {
            assertEquals(FieldKind.INT, SettingsForm.field(key).kind)
        }
        fun team(key: String, value: String) = validate(key to value)
        // Tirages : 500 à 50 000.
        assertEquals(500, team("team.simulationRuns", "500").settings!!.team!!.simulationRuns)
        assertEquals(50_000, team("team.simulationRuns", "50000").settings!!.team!!.simulationRuns)
        assertEquals(FieldError(FieldErrorKind.TOO_SMALL, "500"), team("team.simulationRuns", "499").fieldErrors["team.simulationRuns"])
        assertEquals(FieldError(FieldErrorKind.TOO_LARGE, "50000"), team("team.simulationRuns", "50001").fieldErrors["team.simulationRuns"])
        // Fenêtre d'historique : 1 à 104 semaines.
        assertEquals(1, team("team.historyWeeks", "1").settings!!.team!!.historyWeeks)
        assertEquals(104, team("team.historyWeeks", "104").settings!!.team!!.historyWeeks)
        assertEquals(FieldErrorKind.TOO_SMALL, team("team.historyWeeks", "0").fieldErrors["team.historyWeeks"]!!.kind)
        assertEquals(FieldError(FieldErrorKind.TOO_LARGE, "104"), team("team.historyWeeks", "105").fieldErrors["team.historyWeeks"])
        // Minimum d'échantillons : 3 ou plus, sans plafond.
        assertEquals(3, team("team.minSamples", "3").settings!!.team!!.minSamples)
        assertEquals(1000, team("team.minSamples", "1000").settings!!.team!!.minSamples)
        assertEquals(FieldError(FieldErrorKind.TOO_SMALL, "3"), team("team.minSamples", "2").fieldErrors["team.minSamples"])
        assertEquals(FieldErrorKind.NOT_INTEGER, team("team.minSamples", "huit").fieldErrors["team.minSamples"]!!.kind)
        // Seuil de risque d'échéance : 1 à 99 %.
        assertEquals(1, team("team.deadlineRiskPercent", "1").settings!!.team!!.deadlineRiskPercent)
        assertEquals(99, team("team.deadlineRiskPercent", "99").settings!!.team!!.deadlineRiskPercent)
        assertEquals(FieldErrorKind.TOO_SMALL, team("team.deadlineRiskPercent", "0").fieldErrors["team.deadlineRiskPercent"]!!.kind)
        assertEquals(FieldError(FieldErrorKind.TOO_LARGE, "99"), team("team.deadlineRiskPercent", "100").fieldErrors["team.deadlineRiskPercent"])
        assertEquals(FieldErrorKind.REQUIRED, team("team.simulationRuns", " ").fieldErrors["team.simulationRuns"]!!.kind)
    }

    @Test
    fun writing_the_default_forecast_values_keeps_the_team_object_null() {
        val defaults = mapOf(
            "team.simulationRuns" to "5000", "team.historyWeeks" to "12", "team.minSamples" to "8", "team.deadlineRiskPercent" to "70",
        )
        assertNull(SettingsForm.validate(defaults, base).settings!!.team)
        // Une valeur non défaut crée l'objet, les autres champs gardent leur défaut ; l'aller-retour est stable.
        val s = validate("team.simulationRuns" to "10000", "team.deadlineRiskPercent" to "80").settings!!
        assertEquals(TeamSettings(simulationRuns = 10_000, deadlineRiskPercent = 80), s.team)
        assertEquals(s, SettingsForm.validate(SettingsForm.values(s), s).settings)
        assertEquals("10000", SettingsForm.values(s)["team.simulationRuns"])
        assertEquals("80", SettingsForm.values(s)["team.deadlineRiskPercent"])
    }
}
