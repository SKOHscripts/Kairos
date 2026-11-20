package com.skohscripts.kairos.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.settings.SettingsForm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Schéma du style Cadran (docs/spec/style-cadran.md § Critères de succès) : lisibilité pour chaque graine proposée. */
class CadranThemeTest {
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance() + 0.05, b.luminance() + 0.05).sortedDescending()
        return hi / lo
    }

    private fun schemes() = ThemeColors.PRESETS.flatMap { preset ->
        val rgb = SettingsForm.parseColor(preset.color)!!
        listOf(false, true).map { dark ->
            "${preset.color} ${if (dark) "sombre" else "clair"}" to CadranTheme.scheme(ThemeColors.schemeFor(preset.color, null, dark), rgb, dark)
        }
    }

    @Test
    fun accentAndReservedColorsReadOnPageCardAndDialog() {
        val failures = schemes().flatMap { (name, s) ->
            val green = CadranTheme.extraColors(dark = s.background.luminance() < 0.5f).ok
            listOf("accent" to s.primary, "rouge" to s.error, "violet" to s.tertiary, "vert" to green).flatMap { (role, color) ->
                listOf("page" to s.background, "carte" to s.surfaceContainerLow, "dialogue" to s.surfaceContainerHigh)
                    .filter { (_, bg) -> contrast(color, bg) < 4.5 }
                    .map { (bg, _) -> "$name : $role sur $bg" }
            }
        }
        assertEquals(emptyList(), failures)
    }

    @Test
    fun secondaryTextAndOutlineStayReadable() {
        for ((name, s) in schemes()) {
            for (bg in listOf(s.background, s.surfaceContainerLow, s.surfaceContainerHighest)) {
                assertTrue(contrast(s.onSurfaceVariant, bg) >= 4.5, "$name : texte secondaire")
            }
            assertTrue(contrast(s.outline, s.background) >= 3.0 && contrast(s.outline, s.surfaceContainerLow) >= 3.0, "$name : contour")
            assertTrue(contrast(s.onPrimary, s.primary) >= 4.5, "$name : texte sur l'accent")
        }
    }

    @Test
    fun greysAreTheIosOnesWhateverTheSeed() {
        val light = CadranTheme.scheme(ThemeColors.schemeFor(Settings.HONEY_THEME_COLOR, null), 0xC28417, dark = false)
        assertEquals(Color(0xFFF2F2F7), light.background)
        assertEquals(Color(0xFFFFFFFF), light.surfaceContainerLow)
        assertEquals(Color(0xFF000000), light.onSurface)
        val dark = CadranTheme.scheme(ThemeColors.schemeFor(Settings.HONEY_THEME_COLOR, null, dark = true), 0xC28417, dark = true)
        assertEquals(Color(0xFF000000), dark.background)
        assertEquals(Color(0xFF1C1C1E), dark.surfaceContainerLow)
        assertEquals(Color.Transparent, dark.surfaceTint)
    }

    @Test
    fun theAccentKeepsTheSeedHue() {
        // L'océan en Cadran reste un bleu : la composante bleue domine.
        val ocean = CadranTheme.scheme(KairosLightColors, 0x2F6FED, dark = false).primary
        assertTrue(ocean.blue > ocean.red && ocean.blue > ocean.green, "accent océan : ${Integer.toHexString(ocean.toArgb())}")
    }

    @Test
    fun theAccentSeedFollowsTheThemeColor() {
        assertEquals(0xC28417, ThemeColors.accentFor("#C28417", systemAvailable = false))
        assertNull(ThemeColors.accentFor(Settings.SYSTEM_THEME, systemAvailable = true))
        assertEquals(0x2F6FED, ThemeColors.accentFor(Settings.SYSTEM_THEME, systemAvailable = false))
        assertEquals(0x2F6FED, ThemeColors.accentFor("bleu", systemAvailable = false))
    }
}
