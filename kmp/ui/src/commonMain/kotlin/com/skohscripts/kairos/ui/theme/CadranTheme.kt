package com.skohscripts.kairos.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.hct.Hct
import kotlinx.coroutines.launch

/**
 * Le style « Cadran », inspiré d'iOS (docs/spec/style-cadran.md) : schéma (gris d'iOS, accent tiré de la graine, rouge,
 * vert et violet d'iOS à des tons lisibles), formes, typographie et retour au toucher. Material reste la référence de
 * la charte : Cadran en garde les rôles, leur usage et la densité.
 */
object CadranTheme {
    /** Ton HCT des couleurs « de rôle » (accent, rouge, vert, violet) : 4,5:1 au moins sur les fonds clairs. */
    const val LIGHT_TONE = 45.0

    /** Même chose en sombre, sur le fond noir, la carte `#1C1C1E`, le dialogue et le remplissage. */
    const val DARK_TONE = 70.0

    /** Couleurs système d'iOS : rouge (P0, erreurs), vert (« fait »), violet (deep work). Teinte et chroma seulement. */
    const val IOS_RED = 0xFF3B30
    const val IOS_GREEN = 0x34C759
    const val IOS_PURPLE = 0xAF52DE

    /** Une famille de rôles : la couleur, son texte, son conteneur et le texte du conteneur. */
    class Family(val role: Color, val onRole: Color, val container: Color, val onContainer: Color)

    /**
     * La famille de la teinte de [rgb] (`0xRRGGBB`) : rôle au ton 45 / 70, texte au ton 100 / 10, conteneur au ton 90 / 30,
     * texte du conteneur au ton 10 / 90 (clair / sombre). La chroma est celle de [rgb] (32 au plus pour le conteneur et son
     * texte), ramenée dans la gamme sRGB.
     */
    fun family(rgb: Int, dark: Boolean): Family {
        val source = Hct.fromInt(0xFF000000.toInt() or rgb)
        fun tone(t: Double, chroma: Double = source.chroma) = Color(Hct.from(source.hue, chroma, t).toInt())
        // Conteneurs à chroma plafonnée (comme les palettes de Material) : en pleine chroma, le conteneur sombre (ton 30)
        // d'un violet d'iOS rendait les hachures du deep work criardes.
        val soft = minOf(source.chroma, CONTAINER_CHROMA)
        return if (dark) {
            Family(tone(DARK_TONE), tone(10.0), tone(30.0, soft), tone(90.0, soft))
        } else {
            Family(tone(LIGHT_TONE), tone(100.0), tone(90.0, soft), tone(10.0, soft))
        }
    }

    /** Chroma maximale des conteneurs (et de leur texte). */
    const val CONTAINER_CHROMA = 32.0

    /**
     * Le schéma Cadran : les gris d'iOS (fixes), l'accent de la graine [accent] (`0xRRGGBB`, à défaut le `primary` de
     * [material], cas des couleurs du système), rouge et violet d'iOS ; le reste de la famille d'accent (conteneurs,
     * secondaire, primaire inverse) vient du schéma [material] de la même graine.
     */
    fun scheme(material: ColorScheme, accent: Int?, dark: Boolean): ColorScheme {
        val primary = family(accent ?: (material.primary.toArgb() and 0xFFFFFF), dark)
        val inverse = family(accent ?: (material.primary.toArgb() and 0xFFFFFF), !dark)
        val red = family(IOS_RED, dark)
        val purple = family(IOS_PURPLE, dark)
        val g = if (dark) DarkGreys else LightGreys
        return material.copy(
            primary = primary.role,
            onPrimary = primary.onRole,
            inversePrimary = inverse.role,
            surfaceTint = Color.Transparent,
            tertiary = purple.role,
            onTertiary = purple.onRole,
            tertiaryContainer = purple.container,
            onTertiaryContainer = purple.onContainer,
            error = red.role,
            onError = red.onRole,
            errorContainer = red.container,
            onErrorContainer = red.onContainer,
            background = g.page,
            onBackground = g.label,
            surface = g.page,
            onSurface = g.label,
            surfaceVariant = g.fill,
            onSurfaceVariant = g.secondaryLabel,
            surfaceContainerLowest = g.lowest,
            surfaceContainerLow = g.card,
            surfaceContainer = g.card,
            surfaceContainerHigh = g.raised,
            surfaceContainerHighest = g.fill,
            surfaceBright = g.bright,
            surfaceDim = g.dim,
            outline = g.outline,
            outlineVariant = g.separator,
            inverseSurface = g.inverse,
            inverseOnSurface = g.onInverse,
            scrim = Color.Black,
        )
    }

    /** Couleur « fait / ok » en Cadran : le vert d'iOS, au même ton que les autres rôles. */
    fun extraColors(dark: Boolean): KairosExtraColors = family(IOS_GREEN, dark).let {
        KairosExtraColors(ok = it.role, okContainer = it.container, onOkContainer = it.onContainer)
    }

    /** Champs à 10 dp, dialogues à 22 dp (la charte Material : 4 et 28) ; le reste comme Material. */
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(22.dp),
    )

    /**
     * L'échelle de [base] (mêmes tailles : la densité de la charte vaut pour les deux styles), titres en gras, sans
     * espacement supplémentaire entre les lettres (Material en ajoute jusqu'à 0,5 sp, iOS n'en met pas).
     */
    fun typography(base: Typography): Typography {
        fun TextStyle.flat() = copy(letterSpacing = 0.sp)
        fun TextStyle.bold() = copy(letterSpacing = 0.sp, fontWeight = FontWeight.Bold)
        return Typography(
            displayLarge = base.displayLarge.bold(),
            displayMedium = base.displayMedium.bold(),
            displaySmall = base.displaySmall.bold(),
            headlineLarge = base.headlineLarge.bold(),
            headlineMedium = base.headlineMedium.bold(),
            headlineSmall = base.headlineSmall.bold(),
            titleLarge = base.titleLarge.bold(),
            titleMedium = base.titleMedium.flat(),
            titleSmall = base.titleSmall.flat(),
            bodyLarge = base.bodyLarge.flat(),
            bodyMedium = base.bodyMedium.flat(),
            bodySmall = base.bodySmall.flat(),
            labelLarge = base.labelLarge.flat(),
            labelMedium = base.labelMedium.flat(),
            labelSmall = base.labelSmall.flat(),
        )
    }

    /**
     * Les gris d'iOS. Kairos peint la page en `surface` / `background` et ses blocs en `surfaceContainer*` : en Material
     * les blocs sont plus foncés que la page, en Cadran ils sont blancs sur une page grise (la liste groupée d'iOS).
     */
    private class Greys(
        val page: Color, val lowest: Color, val card: Color, val raised: Color, val fill: Color, val bright: Color,
        val dim: Color, val label: Color, val secondaryLabel: Color, val outline: Color, val separator: Color,
        val inverse: Color, val onInverse: Color,
    )

    // Texte secondaire : la variante lisible d'iOS (#636366, #AEAEB2), pas `secondaryLabel` (60 % d'opacité, 3,5:1) ;
    // 4,5:1 jusque sur le remplissage (badges). Contour : 3:1 sur la page grise.
    private val LightGreys = Greys(
        page = Color(0xFFF2F2F7), lowest = Color(0xFFFFFFFF), card = Color(0xFFFFFFFF), raised = Color(0xFFFFFFFF),
        fill = Color(0xFFE5E5EA), bright = Color(0xFFFFFFFF), dim = Color(0xFFE5E5EA),
        label = Color(0xFF000000), secondaryLabel = Color(0xFF636366), outline = Color(0xFF8A8A8E), separator = Color(0xFFC6C6C8),
        inverse = Color(0xFF1C1C1E), onInverse = Color(0xFFFFFFFF),
    )

    private val DarkGreys = Greys(
        page = Color(0xFF000000), lowest = Color(0xFF000000), card = Color(0xFF1C1C1E), raised = Color(0xFF2C2C2E),
        fill = Color(0xFF3A3A3C), bright = Color(0xFF2C2C2E), dim = Color(0xFF000000),
        label = Color(0xFFFFFFFF), secondaryLabel = Color(0xFFAEAEB2), outline = Color(0xFF8E8E93), separator = Color(0xFF38383A),
        inverse = Color(0xFFF2F2F7), onInverse = Color(0xFF000000),
    )
}

/**
 * Retour au toucher d'un élément cliquable en Cadran (le surlignage d'une cellule iOS) : un voile de la couleur du
 * contenu, 10 % à l'appui et au focus clavier, 5 % au survol, sans onde. Posé en `LocalIndication` par `KairosTheme`.
 */
object CadranHighlight : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = HighlightNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0x6CADA
}

private class HighlightNode(private val source: InteractionSource) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private val pressed = mutableSetOf<Interaction>()
    private val hovered = mutableSetOf<Interaction>()
    private val focused = mutableSetOf<Interaction>()

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> pressed += i
                    is PressInteraction.Release -> pressed -= i.press
                    is PressInteraction.Cancel -> pressed -= i.press
                    is HoverInteraction.Enter -> hovered += i
                    is HoverInteraction.Exit -> hovered -= i.enter
                    is FocusInteraction.Focus -> focused += i
                    is FocusInteraction.Unfocus -> focused -= i.focus
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val alpha = when {
            pressed.isNotEmpty() || focused.isNotEmpty() -> 0.10f
            hovered.isNotEmpty() -> 0.05f
            else -> 0f
        }
        if (alpha > 0f) drawRect(currentValueOf(LocalContentColor).copy(alpha = alpha))
    }
}

/**
 * Bouton iOS « highlighted » : l'élément pâlit (45 %) pendant l'appui, d'un coup, et revient en 150 ms. Pour les
 * composants dont la forme n'est pas un rectangle (pilules), où le voile de [CadranHighlight] déborderait.
 */
@Composable
internal fun Modifier.cadranPressDim(source: InteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.45f else 1f, if (pressed) snap() else tween(150), label = "cadranPress")
    return graphicsLayer { this.alpha = alpha }
}
