package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** [TeamSettings.bounded] rend toujours des réglages que le formulaire accepte (QA du 2026-11-04). */
class TeamSettingsBoundsTest {
    private fun accepted(team: TeamSettings) {
        val s = Settings(team = team)
        assertNotNull(SettingsForm.validate(SettingsForm.values(s), s).settings, "refusé : $team")
    }

    @Test
    fun extremeValuesComeBackWithinTheFormBounds() {
        val low = TeamSettings(
            staleProgressDays = -5, churnThreshold = 0, wipLimit = -1, horizonWeeks = 0, focusFactor = -0.5, hoursPerPoint = 0.0,
            loadWarnPercent = 0, affinityDays = -3, simulationRuns = 0, historyWeeks = 0, minSamples = 0, deadlineRiskPercent = 0,
        )
        val high = TeamSettings(
            horizonWeeks = 1000, focusFactor = 7.0, loadWarnPercent = 500, simulationRuns = 10_000_000, historyWeeks = 999,
            deadlineRiskPercent = 100,
        )
        val odd = TeamSettings(focusFactor = Double.NaN, hoursPerPoint = Double.POSITIVE_INFINITY)
        for (t in listOf(low, high, odd)) accepted(t.bounded())
        assertEquals(1.0, high.bounded().focusFactor)
        assertEquals(TeamSettings.DEFAULT_FOCUS_FACTOR, low.bounded().focusFactor)
        assertEquals(TeamSettings.DEFAULT_HOURS_PER_POINT, odd.bounded().hoursPerPoint)
    }

    @Test
    fun valuesAlreadyWithinTheBoundsDoNotChange() {
        val t = TeamSettings(enabled = true, name = "Plateforme", horizonWeeks = 26, focusFactor = 1.0, simulationRuns = 500, deadlineRiskPercent = 99)
        assertEquals(t, t.bounded())
        assertEquals(TeamSettings(), TeamSettings().bounded())
    }
}
