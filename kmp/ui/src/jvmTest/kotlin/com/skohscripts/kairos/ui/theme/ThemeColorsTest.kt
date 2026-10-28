package com.skohscripts.kairos.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.palettes.TonalPalette
import com.skohscripts.kairos.core.model.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    fun the_embedded_algorithm_gives_the_charter_colors_for_the_default_seed() {
        val computed = roles(ThemeColors.seedScheme(0x2F6FED))
        assertEquals(36, computed.size)
        assertEquals(roles(KairosLightColors), computed)
    }

    @Test
    fun the_embedded_algorithm_gives_the_generated_dark_colors_for_the_default_seed() {
        assertEquals(roles(KairosDarkColors), roles(ThemeColors.seedScheme(0x2F6FED, dark = true)))
        assertSame(KairosDarkColors, ThemeColors.schemeFor(Settings.DEFAULT_THEME_COLOR, null, dark = true))
        assertSame(KairosDarkColors, ThemeColors.schemeFor("illisible", null, dark = true))
        // Une autre graine en sombre : même algorithme, fond sombre, rouge d'erreur sombre fixe.
        val honey = ThemeColors.schemeFor(Settings.HONEY_THEME_COLOR, null, dark = true)
        assertTrue(honey.surface.luminance() < 0.05f)
        assertEquals(KairosDarkColors.error, honey.error)
    }

    @Test
    fun the_dark_ok_color_mirrors_the_dark_error_roles() {
        val light = KairosExtraColors()
        val dark = KairosExtraColors.dark()
        // Comme l'erreur : le conteneur sombre est le texte du conteneur clair, et inversement (tons 30 et 90).
        assertEquals(KairosLightColors.onErrorContainer, KairosDarkColors.errorContainer)
        assertEquals(light.onOkContainer, dark.okContainer)
        assertEquals(light.okContainer, dark.onOkContainer)
        // Ton 80 de la palette de l'« ok » (teinte 150°, chroma 36), comme `error` sombre.
        assertEquals(TonalPalette.fromHueAndChroma(150.0, 36.0).tone(80), dark.ok.toArgb())
    }

    @Test
    fun the_default_seed_is_served_from_the_generated_scheme_and_other_seeds_are_computed() {
        assertSame(KairosLightColors, ThemeColors.schemeFor(Settings.DEFAULT_THEME_COLOR, null))
        assertSame(KairosLightColors, ThemeColors.schemeFor("#2f6fed", null))
        assertSame(KairosLightColors, ThemeColors.schemeFor("illisible", null))
        // Le miel, ancienne graine par défaut, est une couleur au choix : calculée comme les autres.
        val honey = ThemeColors.schemeFor(Settings.HONEY_THEME_COLOR, null)
        assertNotEquals(KairosLightColors.primary, honey.primary)
        // Le rouge d'erreur ne dépend pas de la graine.
        assertEquals(KairosLightColors.error, honey.error)
    }

    @Test
    fun system_colors_are_used_only_when_provided() {
        val system = lightColorScheme(primary = Color(0xFF006A6A))
        val systemDark = darkColorScheme(primary = Color(0xFF4CDADA))
        val provided = { dark: Boolean -> if (dark) systemDark else system }
        assertSame(system, ThemeColors.schemeFor(Settings.SYSTEM_THEME, provided))
        assertSame(systemDark, ThemeColors.schemeFor(Settings.SYSTEM_THEME, provided, dark = true))
        assertSame(KairosLightColors, ThemeColors.schemeFor(Settings.SYSTEM_THEME, null))
        assertSame(KairosDarkColors, ThemeColors.schemeFor(Settings.SYSTEM_THEME, null, dark = true))
    }

    @Test
    fun every_preset_is_a_valid_distinct_color() {
        val colors = ThemeColors.PRESETS.map { it.color }
        assertEquals(colors.distinct(), colors)
        assertEquals(Settings.DEFAULT_THEME_COLOR, colors.first())
        colors.forEach { assertEquals(it, com.skohscripts.kairos.core.settings.SettingsForm.normalizeColor(it)) }
    }
}
