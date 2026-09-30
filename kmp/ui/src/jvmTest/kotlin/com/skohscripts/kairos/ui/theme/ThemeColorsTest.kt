package com.skohscripts.kairos.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.skohscripts.kairos.core.model.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

/** docs/spec/apparence.md § Critères de succès. */
class ThemeColorsTest {
    /** Les 36 rôles, nommés, d'un schéma. */
    private fun roles(s: ColorScheme): Map<String, Int> = mapOf(
        "primary" to s.primary, "onPrimary" to s.onPrimary, "primaryContainer" to s.primaryContainer,
        "onPrimaryContainer" to s.onPrimaryContainer, "inversePrimary" to s.inversePrimary,
        "secondary" to s.secondary, "onSecondary" to s.onSecondary, "secondaryContainer" to s.secondaryContainer,
        "onSecondaryContainer" to s.onSecondaryContainer, "tertiary" to s.tertiary, "onTertiary" to s.onTertiary,
        "tertiaryContainer" to s.tertiaryContainer, "onTertiaryContainer" to s.onTertiaryContainer,
        "background" to s.background, "onBackground" to s.onBackground, "surface" to s.surface,
        "onSurface" to s.onSurface, "surfaceVariant" to s.surfaceVariant, "onSurfaceVariant" to s.onSurfaceVariant,
        "surfaceTint" to s.surfaceTint, "inverseSurface" to s.inverseSurface, "inverseOnSurface" to s.inverseOnSurface,
        "error" to s.error, "onError" to s.onError, "errorContainer" to s.errorContainer,
        "onErrorContainer" to s.onErrorContainer, "outline" to s.outline, "outlineVariant" to s.outlineVariant,
        "scrim" to s.scrim, "surfaceBright" to s.surfaceBright, "surfaceContainer" to s.surfaceContainer,
        "surfaceContainerHigh" to s.surfaceContainerHigh, "surfaceContainerHighest" to s.surfaceContainerHighest,
        "surfaceContainerLow" to s.surfaceContainerLow, "surfaceContainerLowest" to s.surfaceContainerLowest,
        "surfaceDim" to s.surfaceDim,
    ).mapValues { it.value.toArgb() }

    @Test
    fun the_embedded_algorithm_gives_the_charter_colors_for_honey() {
        val computed = roles(ThemeColors.seedScheme(0xC28417))
        assertEquals(36, computed.size)
        assertEquals(roles(KairosLightColors), computed)
    }

    @Test
    fun honey_is_served_from_the_generated_scheme_and_other_seeds_are_computed() {
        assertSame(KairosLightColors, ThemeColors.schemeFor(Settings.DEFAULT_THEME_COLOR, null))
        assertSame(KairosLightColors, ThemeColors.schemeFor("#c28417", null))
        assertSame(KairosLightColors, ThemeColors.schemeFor("illisible", null))
        val ocean = ThemeColors.schemeFor("#2F6FED", null)
        assertNotEquals(KairosLightColors.primary, ocean.primary)
        // Le rouge d'erreur ne dépend pas de la graine.
        assertEquals(KairosLightColors.error, ocean.error)
    }

    @Test
    fun system_colors_are_used_only_when_provided() {
        val system = lightColorScheme(primary = Color(0xFF006A6A))
        assertSame(system, ThemeColors.schemeFor(Settings.SYSTEM_THEME, system))
        assertSame(KairosLightColors, ThemeColors.schemeFor(Settings.SYSTEM_THEME, null))
    }

    @Test
    fun every_preset_is_a_valid_distinct_color() {
        val colors = ThemeColors.PRESETS.map { it.color }
        assertEquals(colors.distinct(), colors)
        assertEquals(Settings.DEFAULT_THEME_COLOR, colors.first())
        colors.forEach { assertEquals(it, com.skohscripts.kairos.core.settings.SettingsForm.normalizeColor(it)) }
    }
}
