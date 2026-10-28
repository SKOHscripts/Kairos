package com.skohscripts.kairos.android.widgets

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.settings.ThemeMode
import com.skohscripts.kairos.ui.theme.ThemeColors

/**
 * Couleurs des widgets (docs/spec/widgets-android.md § Thème) : les rôles MD3 du thème que l'application affiche, calculés par
 * [ThemeColors] depuis la couleur choisie, en clair ou en sombre selon « Thème sombre » et le mode nuit du système. Jamais les
 * couleurs du fond d'écran : « Couleurs du système » donne ici la graine par défaut (le réglage reste propre à l'application).
 */
internal data class WidgetColors(
    val surface: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val surfaceContainerHigh: Int,
    val error: Int,
    val tertiary: Int,
) {
    companion object {
        fun of(context: Context, settings: Settings): WidgetColors {
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val s = ThemeColors.schemeFor(settings.themeColor, system = null, dark = ThemeMode.isDark(settings.themeMode, night))
            return WidgetColors(
                surface = s.surface.toArgb(),
                onSurface = s.onSurface.toArgb(),
                onSurfaceVariant = s.onSurfaceVariant.toArgb(),
                primary = s.primary.toArgb(),
                onPrimary = s.onPrimary.toArgb(),
                primaryContainer = s.primaryContainer.toArgb(),
                onPrimaryContainer = s.onPrimaryContainer.toArgb(),
                surfaceContainerHigh = s.surfaceContainerHigh.toArgb(),
                error = s.error.toArgb(),
                tertiary = s.tertiary.toArgb(),
            )
        }

        /** [color] à 60 % : le passé de la journée, grisé. */
        fun faded(color: Int): Int = (color and 0x00FFFFFF) or (0x99 shl 24)
    }
}
