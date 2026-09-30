package com.skohscripts.kairos.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.settings.SettingsForm
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.theme_preset_honey
import com.skohscripts.kairos.ui.generated.resources.theme_preset_indigo
import com.skohscripts.kairos.ui.generated.resources.theme_preset_lavender
import com.skohscripts.kairos.ui.generated.resources.theme_preset_ocean
import com.skohscripts.kairos.ui.generated.resources.theme_preset_raspberry
import com.skohscripts.kairos.ui.generated.resources.theme_preset_slate
import com.skohscripts.kairos.ui.generated.resources.theme_preset_earth
import com.skohscripts.kairos.ui.generated.resources.theme_preset_teal
import org.jetbrains.compose.resources.StringResource

/** Une graine proposée dans la carte Apparence : son nom et sa valeur `#RRGGBB`. */
class ThemePreset(val name: StringResource, val color: String)

/**
 * Couleur du thème (docs/spec/apparence.md) : graines proposées, et schéma
 * MD3 complet dérivé d'une graine par le même algorithme que la charte
 * (Tonal spot, spec 2021 ; `kmp/tools/make_theme.py`).
 */
object ThemeColors {
    /** Miel en tête (la charte) ; ni rouge (urgences, erreurs) ni vert (« fait »). */
    val PRESETS: List<ThemePreset> = listOf(
        ThemePreset(Res.string.theme_preset_honey, Settings.DEFAULT_THEME_COLOR),
        ThemePreset(Res.string.theme_preset_ocean, "#2F6FED"),
        ThemePreset(Res.string.theme_preset_teal, "#1B8A8A"),
        ThemePreset(Res.string.theme_preset_indigo, "#5B5FC7"),
        ThemePreset(Res.string.theme_preset_lavender, "#8A5CC2"),
        ThemePreset(Res.string.theme_preset_raspberry, "#B83E7A"),
        ThemePreset(Res.string.theme_preset_slate, "#56687A"),
        ThemePreset(Res.string.theme_preset_earth, "#9A5B3F"),
    )

    /**
     * Schéma de [themeColor] : les couleurs du système si demandé et fournies
     * ([system]), les valeurs générées de la charte pour le miel, le schéma
     * calculé sinon ; le miel pour une valeur illisible.
     */
    fun schemeFor(themeColor: String, system: ColorScheme?): ColorScheme {
        if (themeColor == Settings.SYSTEM_THEME && system != null) return system
        val rgb = SettingsForm.parseColor(themeColor) ?: return KairosLightColors
        if (rgb == SettingsForm.parseColor(Settings.DEFAULT_THEME_COLOR)) return KairosLightColors
        return seedScheme(rgb)
    }

    /** Les 36 rôles MD3 du schéma Tonal spot (spec 2021, clair, contraste standard) de la graine [rgb]. */
    fun seedScheme(rgb: Int): ColorScheme {
        val s = SchemeTonalSpot(
            sourceColorHct = Hct.fromInt(0xFF000000.toInt() or rgb),
            isDark = false,
            contrastLevel = 0.0,
            specVersion = ColorSpec.SpecVersion.SPEC_2021,
        )
        fun c(argb: Int) = Color(argb)
        return lightColorScheme(
            primary = c(s.primary),
            onPrimary = c(s.onPrimary),
            primaryContainer = c(s.primaryContainer),
            onPrimaryContainer = c(s.onPrimaryContainer),
            inversePrimary = c(s.inversePrimary),
            secondary = c(s.secondary),
            onSecondary = c(s.onSecondary),
            secondaryContainer = c(s.secondaryContainer),
            onSecondaryContainer = c(s.onSecondaryContainer),
            tertiary = c(s.tertiary),
            onTertiary = c(s.onTertiary),
            tertiaryContainer = c(s.tertiaryContainer),
            onTertiaryContainer = c(s.onTertiaryContainer),
            background = c(s.background),
            onBackground = c(s.onBackground),
            surface = c(s.surface),
            onSurface = c(s.onSurface),
            surfaceVariant = c(s.surfaceVariant),
            onSurfaceVariant = c(s.onSurfaceVariant),
            surfaceTint = c(s.surfaceTint),
            inverseSurface = c(s.inverseSurface),
            inverseOnSurface = c(s.inverseOnSurface),
            error = c(s.error),
            onError = c(s.onError),
            errorContainer = c(s.errorContainer),
            onErrorContainer = c(s.onErrorContainer),
            outline = c(s.outline),
            outlineVariant = c(s.outlineVariant),
            scrim = c(s.scrim),
            surfaceBright = c(s.surfaceBright),
            surfaceContainer = c(s.surfaceContainer),
            surfaceContainerHigh = c(s.surfaceContainerHigh),
            surfaceContainerHighest = c(s.surfaceContainerHighest),
            surfaceContainerLow = c(s.surfaceContainerLow),
            surfaceContainerLowest = c(s.surfaceContainerLowest),
            surfaceDim = c(s.surfaceDim),
        )
    }
}
