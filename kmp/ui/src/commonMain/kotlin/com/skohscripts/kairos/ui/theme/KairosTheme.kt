package com.skohscripts.kairos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
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
 * graine (elle virait à l'olive, trop proche de la tertiaire).
 */
@Immutable
data class KairosExtraColors(
    val ok: Color = Color(0xFF36693D),
    val okContainer: Color = Color(0xFFB7F1B8),
    val onOkContainer: Color = Color(0xFF1D5127),
)

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

/** Thème Kairos : un seul thème clair (charte « miel », docs/spec/navigation-theme.md). */
@Composable
fun KairosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KairosLightColors,
        typography = kairosTypography(),
        shapes = KairosShapes,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalKairosExtraColors provides KairosExtraColors(),
            content = content,
        )
    }
}
