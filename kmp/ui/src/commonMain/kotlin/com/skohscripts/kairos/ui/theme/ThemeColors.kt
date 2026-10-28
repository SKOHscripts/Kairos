package com.skohscripts.kairos.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
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
    /** L'océan en tête (la charte), puis le miel du logo ; ni rouge (urgences, erreurs) ni vert (« fait »). */
    val PRESETS: List<ThemePreset> = listOf(
        ThemePreset(Res.string.theme_preset_ocean, Settings.DEFAULT_THEME_COLOR),
        ThemePreset(Res.string.theme_preset_honey, Settings.HONEY_THEME_COLOR),
        ThemePreset(Res.string.theme_preset_teal, "#1B8A8A"),
        ThemePreset(Res.string.theme_preset_indigo, "#5B5FC7"),
        ThemePreset(Res.string.theme_preset_lavender, "#8A5CC2"),
        ThemePreset(Res.string.theme_preset_raspberry, "#B83E7A"),
        ThemePreset(Res.string.theme_preset_slate, "#56687A"),
        ThemePreset(Res.string.theme_preset_earth, "#9A5B3F"),
    )

    /**
     * Schéma de [themeColor], clair ou sombre ([dark], docs/spec/apparence.md § Thème sombre) : les couleurs du système si
     * demandé et fournies ([system]), les valeurs générées de la charte pour la graine par défaut (l'océan), le schéma calculé
     * sinon (le miel compris) ; la graine par défaut pour une valeur illisible.
     */
    fun schemeFor(themeColor: String, system: ((dark: Boolean) -> ColorScheme)?, dark: Boolean = false): ColorScheme {
        if (themeColor == Settings.SYSTEM_THEME && system != null) return system(dark)
        val brand = if (dark) KairosDarkColors else KairosLightColors
        val rgb = SettingsForm.parseColor(themeColor) ?: return brand
        if (rgb == SettingsForm.parseColor(Settings.DEFAULT_THEME_COLOR)) return brand
        return seedScheme(rgb, dark)
    }

    /** Les 36 rôles MD3 du schéma Tonal spot (spec 2021, contraste standard), clair ou sombre, de la graine [rgb]. */
    fun seedScheme(rgb: Int, dark: Boolean = false): ColorScheme {
        val s = SchemeTonalSpot(
            sourceColorHct = Hct.fromInt(0xFF000000.toInt() or rgb),
            isDark = dark,
            contrastLevel = 0.0,
            specVersion = ColorSpec.SpecVersion.SPEC_2021,
        )
        fun c(argb: Int) = Color(argb)
        // Les 36 rôles calculés remplacent ceux du schéma de base ; la base (claire ou sombre) ne fournit que les rôles
        // hors calcul (couleurs fixes).
        return (if (dark) darkColorScheme() else lightColorScheme()).copy(
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
