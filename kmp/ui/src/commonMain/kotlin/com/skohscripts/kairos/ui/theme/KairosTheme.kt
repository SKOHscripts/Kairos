package com.skohscripts.kairos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.roboto_bold
import com.skohscripts.kairos.ui.generated.resources.roboto_medium
import com.skohscripts.kairos.ui.generated.resources.roboto_regular
import org.jetbrains.compose.resources.Font

/**
 * Couleur personnalisée « fait / ok » (docs/DESIGN_SYSTEM.md § Couleur
 * personnalisée) : hors schéma MD3, volontairement NON harmonisée vers la
 * graine (elle virait à l'olive, trop proche de la tertiaire). Mêmes tons que
 * les rôles d'erreur : 40 / 90 / 30 en clair.
 */
@Immutable
data class KairosExtraColors(
    val ok: Color = Color(0xFF36693D),
    val okContainer: Color = Color(0xFFB7F1B8),
    val onOkContainer: Color = Color(0xFF1D5127),
) {
    companion object {
        /**
         * Le « ok » du thème sombre (docs/spec/apparence.md § Thème sombre), comme l'erreur sombre : ton 80 de la même
         * palette (teinte 150°, chroma 36), conteneur et texte du conteneur échangés avec ceux du clair (tons 30 / 90).
         */
        fun dark() = KairosExtraColors(ok = Color(0xFF9CD49F), okContainer = Color(0xFF1D5127), onOkContainer = Color(0xFFB7F1B8))
    }
}

val LocalKairosExtraColors = staticCompositionLocalOf { KairosExtraColors() }

/** Formes MD3 de la charte : 4 / 8 / 12 / 16 / 28 dp. */
internal val KairosShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
private fun roboto(): FontFamily = FontFamily(
    Font(Res.font.roboto_regular, FontWeight.Normal),
    Font(Res.font.roboto_medium, FontWeight.Medium),
    Font(Res.font.roboto_bold, FontWeight.Bold),
)

/** Échelle typographique MD3 par défaut, en Roboto embarquée (jamais de police distante). */
@Composable
private fun kairosTypography(): Typography {
    val family = roboto()
    val base = Typography()
    fun TextStyle.roboto() = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.roboto(),
        displayMedium = base.displayMedium.roboto(),
        displaySmall = base.displaySmall.roboto(),
        headlineLarge = base.headlineLarge.roboto(),
        headlineMedium = base.headlineMedium.roboto(),
        headlineSmall = base.headlineSmall.roboto(),
        titleLarge = base.titleLarge.roboto().copy(fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = base.titleMedium.roboto(),
        titleSmall = base.titleSmall.roboto(),
        bodyLarge = base.bodyLarge.roboto(),
        bodyMedium = base.bodyMedium.roboto(),
        bodySmall = base.bodySmall.roboto(),
        labelLarge = base.labelLarge.roboto(),
        labelMedium = base.labelMedium.roboto(),
        labelSmall = base.labelSmall.roboto(),
    )
}

/**
 * Couleurs du système fournies par la plateforme (Android 12 et plus), `null`
 * ailleurs : la carte Apparence ne propose « Couleurs du système » que si
 * elles existent (docs/spec/apparence.md).
 */
val LocalSystemColorScheme = staticCompositionLocalOf<ColorScheme?> { null }

/** Le système est en sombre (posé par `KairosApp`) : l'interrupteur de la carte Apparence en dépend. */
val LocalSystemDark = staticCompositionLocalOf { false }

/**
 * Le schéma **clair** de la graine courante, même en thème sombre : ce qui doit rester clair (l'image exportée d'une
 * carte des Stats, docs/spec/statistiques.md § Export) s'y redessine.
 */
val LocalLightColorScheme = staticCompositionLocalOf { KairosLightColors }

/**
 * Thème Kairos, clair ou sombre ([dark], docs/spec/apparence.md § Thème sombre), du schéma de la charte (océan) par
 * défaut ou de celui de la couleur choisie (`ThemeColors.schemeFor`) ; [light] : le schéma clair de la même graine,
 * exposé par [LocalLightColorScheme].
 */
@Composable
fun KairosTheme(
    colorScheme: ColorScheme = KairosLightColors,
    dark: Boolean = false,
    light: ColorScheme = if (dark) KairosLightColors else colorScheme,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = kairosTypography(),
        shapes = KairosShapes,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalKairosExtraColors provides if (dark) KairosExtraColors.dark() else KairosExtraColors(),
            LocalLightColorScheme provides light,
            content = content,
        )
    }
}

/** Le thème par défaut (océan), clair ou sombre comme le système : avant que les réglages soient lus. */
@Composable
fun KairosDefaultTheme(dark: Boolean, content: @Composable () -> Unit) =
    KairosTheme(if (dark) KairosDarkColors else KairosLightColors, dark, KairosLightColors, content)
