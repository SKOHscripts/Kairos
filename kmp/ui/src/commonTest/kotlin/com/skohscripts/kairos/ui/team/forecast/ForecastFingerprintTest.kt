package com.skohscripts.kairos.ui.team.forecast

import com.skohscripts.kairos.core.model.GuideSettings
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Empreinte d'une prévision (docs/spec/equipe-simulation.md) : ce qui ne change pas le calcul ne la rend pas « périmée ». */
class ForecastFingerprintTest {
    private val day = LocalDate(2026, 10, 6)
    private val base = KairosSnapshot(settings = Settings(team = TeamSettings(enabled = true)))

    @Test fun theGuideMemoryDoesNotMakeAForecastStale() {
        val seen = base.copy(settings = base.settings.copy(guide = GuideSettings(seenScreens = listOf("team_forecast"))))
        assertEquals(ForecastFingerprint.of(base, day), ForecastFingerprint.of(seen, day))
    }

    @Test fun aSettingThatChangesTheComputationDoes() {
        val longer = base.copy(settings = base.settings.copy(team = TeamSettings(enabled = true, horizonWeeks = 6)))
        assertNotEquals(ForecastFingerprint.of(base, day), ForecastFingerprint.of(longer, day))
    }
}
