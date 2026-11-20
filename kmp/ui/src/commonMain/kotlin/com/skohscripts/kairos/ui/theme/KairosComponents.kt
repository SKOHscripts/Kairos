package com.skohscripts.kairos.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.FilledTonalButton as M3FilledTonalButton
import androidx.compose.material3.OutlinedButton as M3OutlinedButton
import androidx.compose.material3.TextButton as M3TextButton

/**
 * Composants à la densité de Kairos (docs/spec/densite.md) : boutons de 36 dp,
 * puces de 32 dp, segmentés de 40 dp, actions de ligne de 40 dp. Le dessin est
 * plus fin que la cible : l'aire tactile de 48 dp vient de
 * `minimumInteractiveComponentSize`, que Material 3 applique lui-même à chacun
 * de ces composants. On NE force donc PAS 48 dp sur le composant (`heightIn(48)`).
 *
 * Ces enveloppes sont le seul endroit où l'on emploie `Button`,
 * `OutlinedButton`, `FilledTonalButton`, `TextButton`, `FilterChip` et
 * `SingleChoiceSegmentedButtonRow` de Material 3 (`ComponentDensityTest`).
 */

/** Hauteur dessinée d'un bouton (`ButtonDefaults.MinHeight` vaut 40 dp). */
internal val KairosButtonHeight = 36.dp

/** Marges d'un bouton : 16 dp de part et d'autre, 6 dp en haut et en bas. */
val KairosButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)

/** Marges d'un bouton dont le contenu commence par une icône : 12 dp côté icône. */
val KairosButtonIconPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 16.dp, bottom = 6.dp)

/** Bouton plein (action principale d'une zone) ; remplace `Button`. */
@Composable
fun KairosButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = KairosButtonPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalKairosStyle.current == KairosStyle.CADRAN) {
        val (background, foreground) = cadranButtonColors(CadranButtonKind.FILLED, colors, ButtonDefaults.buttonColors())
        CadranButton(onClick, modifier, enabled, shape, background, foreground, border, contentPadding, interactionSource, content)
        return
    }
    M3Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = KairosButtonHeight),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** Bouton à contour (action secondaire) ; remplace `OutlinedButton`. */
@Composable
fun KairosOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = KairosButtonPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalKairosStyle.current == KairosStyle.CADRAN) {
        val (background, foreground) = cadranButtonColors(CadranButtonKind.SECONDARY, colors, ButtonDefaults.outlinedButtonColors())
        // Le contour par défaut tombe (bouton gris plein d'iOS) ; un contour choisi par l'appelant reste.
        val defaultBorder = ButtonDefaults.outlinedButtonBorder(enabled)
        CadranButton(onClick, modifier, enabled, shape, background, foreground, border.takeIf { it != defaultBorder }, contentPadding, interactionSource, content)
        return
    }
    M3OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = KairosButtonHeight),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** Bouton tonal ; remplace `FilledTonalButton`. */
@Composable
fun KairosTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = KairosButtonPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalKairosStyle.current == KairosStyle.CADRAN) {
        val (background, foreground) = cadranButtonColors(CadranButtonKind.TONAL, colors, ButtonDefaults.filledTonalButtonColors())
        CadranButton(onClick, modifier, enabled, shape, background, foreground, border, contentPadding, interactionSource, content)
        return
    }
    M3FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = KairosButtonHeight),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** Bouton de texte (action tertiaire, boutons de dialogue) ; remplace `TextButton`. */
@Composable
fun KairosTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = KairosButtonPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalKairosStyle.current == KairosStyle.CADRAN) {
        val (background, foreground) = cadranButtonColors(CadranButtonKind.TEXT, colors, ButtonDefaults.textButtonColors())
        CadranButton(onClick, modifier, enabled, shape, background, foreground, border, contentPadding, interactionSource, content)
        return
    }
    M3TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = KairosButtonHeight),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * Puce de filtre ou de qualification : 32 dp dessinés (`FilterChipDefaults.Height`),
 * aire tactile de 48 dp ; remplace `FilterChip`.
 */
@Composable
fun KairosFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    if (LocalKairosStyle.current == KairosStyle.CADRAN) {
        // Pilule grise sans contour, la choisie en `secondaryContainer` (sélection de la charte) ; des couleurs ou un
        // contour choisis par l'appelant restent.
        val scheme = MaterialTheme.colorScheme
        val source = interactionSource ?: remember { MutableInteractionSource() }
        val defaultColors = FilterChipDefaults.filterChipColors()
        val defaultBorder = FilterChipDefaults.filterChipBorder(enabled, selected)
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = modifier.cadranPressDim(source),
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = if (shape == FilterChipDefaults.shape) CircleShape else shape,
            colors = if (colors != defaultColors) {
                colors
            } else {
                FilterChipDefaults.filterChipColors(
                    containerColor = scheme.surfaceContainerHighest,
                    labelColor = scheme.onSurface,
                    iconColor = scheme.onSurfaceVariant,
                )
            },
            elevation = elevation,
            border = border.takeIf { it != defaultBorder },
            interactionSource = source,
        )
        return
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
    )
}

/** Sélecteur segmenté à choix exclusif : 40 dp dessinés, aire tactile de 48 dp ; remplace `SingleChoiceSegmentedButtonRow`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KairosSegmentedRow(
    modifier: Modifier = Modifier,
    content: @Composable SingleChoiceSegmentedButtonRowScope.() -> Unit,
) {
    if (LocalKairosStyle.current == KairosStyle.CADRAN) CadranSegmentedRow(modifier, content)
    else SingleChoiceSegmentedButtonRow(modifier = modifier, content = content)
}

/**
 * Segment d'un [KairosSegmentedRow]. `shape` vient de
 * `SegmentedButtonDefaults.itemShape(index, count)` ; `icon` remplace la coche
 * du segment choisi (par défaut celle de Material 3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleChoiceSegmentedButtonRowScope.KairosSegmentedButton(
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = SegmentedButtonDefaults.ContentPadding,
    icon: @Composable () -> Unit = { SegmentedButtonDefaults.Icon(selected) },
    label: @Composable () -> Unit,
) {
    if (LocalKairosStyle.current == KairosStyle.CADRAN) {
        CadranSegment(selected, onClick, modifier, enabled, label)
        return
    }
    SegmentedButton(
        selected = selected,
        onClick = onClick,
        shape = shape,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
        icon = icon,
        label = label,
    )
}

/** Côté dessiné d'une action de ligne (aire tactile : 48 dp). */
internal val KairosRowIconButtonSize = 40.dp

/** Côté de l'icône d'une action de ligne. */
internal val KairosRowIconSize = 20.dp

/**
 * Action de ligne de tâche, de note ou de bloc (chrono, décaler, modifier,
 * supprimer, menu ⋮) : bouton-icône de 40 dp, icône de 20 dp, aire tactile de
 * 48 dp. Ni la navigation ni les barres d'application : leurs `IconButton`
 * gardent leur icône de 24 dp. `tint` non précisée : couleur de contenu du bouton.
 */
@Composable
fun KairosRowIconButton(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
) {
    if (LocalKairosStyle.current == KairosStyle.CADRAN) {
        // Sans onde : l'icône pâlit sous le doigt, un voile au survol et au focus.
        val source = remember { MutableInteractionSource() }
        val color = if (tint == Color.Unspecified) LocalContentColor.current else tint
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .minimumInteractiveComponentSize()
                .size(KairosRowIconButtonSize)
                .cadranPressDim(source)
                .clip(CircleShape)
                .clickable(source, CadranHighlight, enabled, role = Role.Button, onClick = onClick),
        ) {
            Icon(
                icon,
                contentDescription = description,
                modifier = Modifier.size(KairosRowIconSize),
                tint = if (enabled) color else color.copy(alpha = DISABLED_CONTENT_ALPHA),
            )
        }
        return
    }
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        if (tint == Color.Unspecified) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(KairosRowIconSize))
        } else {
            Icon(icon, contentDescription = description, modifier = Modifier.size(KairosRowIconSize), tint = tint)
        }
    }
}

/**
 * Interrupteur d'affichage (la ligne qui le porte reçoit le toucher et le rôle `Switch`) ; remplace `Switch`. Material :
 * le `Switch` de Material 3. Cadran : celui d'iOS, rail de 51 × 31 dp à la couleur d'accent quand il est allumé (pas le
 * vert d'iOS, réservé au « fait »), rond blanc de 27 dp.
 */
@Composable
fun KairosSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    if (LocalKairosStyle.current != KairosStyle.CADRAN) {
        Switch(checked = checked, onCheckedChange = null, modifier = modifier)
        return
    }
    val scheme = MaterialTheme.colorScheme
    val track by animateColorAsState(if (checked) scheme.primary else scheme.surfaceContainerHighest, tween(200), label = "switchTrack")
    val knob by animateDpAsState(if (checked) CadranSwitchWidth - CadranKnob - CadranKnobInset else CadranKnobInset, tween(200), label = "switchKnob")
    Box(modifier.size(CadranSwitchWidth, CadranSwitchHeight).background(track, CircleShape)) {
        // Le rond reste blanc en clair comme en sombre (iOS) : aucun rôle du schéma n'est blanc dans les deux thèmes.
        Box(
            Modifier
                .offset(x = knob, y = CadranKnobInset)
                .size(CadranKnob)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

private val CadranSwitchWidth = 51.dp
private val CadranSwitchHeight = 31.dp
private val CadranKnob = 27.dp
private val CadranKnobInset = 2.dp

/**
 * Carte à contour ; remplace `OutlinedCard`. Material : celle de Material 3. Cadran : une carte pleine `surfaceContainerLow`
 * (blanche sur la page grise, la liste groupée d'iOS), sans contour.
 */
@Composable
fun KairosOutlinedCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.outlinedShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalKairosStyle.current == KairosStyle.CADRAN) {
        Card(modifier = modifier, shape = shape, colors = cadranCardColors(), content = content)
    } else {
        OutlinedCard(modifier = modifier, shape = shape, content = content)
    }
}

/** Carte à contour cliquable ; en Cadran, le voile gris d'iOS (`LocalIndication`) au lieu de l'onde. */
@Composable
fun KairosOutlinedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.outlinedShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalKairosStyle.current == KairosStyle.CADRAN) {
        Card(
            modifier = modifier.clip(shape).clickable(role = Role.Button, onClick = onClick),
            shape = shape,
            colors = cadranCardColors(),
            content = content,
        )
    } else {
        OutlinedCard(onClick = onClick, modifier = modifier, shape = shape, content = content)
    }
}

@Composable
private fun cadranCardColors() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

/** Opacité du contenu d'un composant désactivé (celle de Material 3). */
private const val DISABLED_CONTENT_ALPHA = 0.38f

/** Les quatre boutons de la charte, en Cadran. */
private enum class CadranButtonKind { FILLED, TONAL, SECONDARY, TEXT }

/**
 * Fond et contenu d'un bouton Cadran : ceux de [colors] s'ils ont été choisis par l'appelant (différents de [defaults],
 * par exemple un bouton de suppression en `error`), sinon ceux du style : plein → accent ; tonal → celui de Material
 * (`secondaryContainer`) ; secondaire → gris plein d'iOS, libellé `onSurface` (un texte d'accent tomberait sous 4,5:1 sur
 * ce gris) ; texte → accent sans fond.
 */
@Composable
private fun cadranButtonColors(kind: CadranButtonKind, colors: ButtonColors, defaults: ButtonColors): Pair<Color, Color> {
    if (colors != defaults) return colors.containerColor to colors.contentColor
    val scheme = MaterialTheme.colorScheme
    return when (kind) {
        CadranButtonKind.FILLED -> scheme.primary to scheme.onPrimary
        CadranButtonKind.TONAL -> scheme.secondaryContainer to scheme.onSecondaryContainer
        CadranButtonKind.SECONDARY -> scheme.surfaceContainerHighest to scheme.onSurface
        CadranButtonKind.TEXT -> Color.Transparent to scheme.primary
    }
}

/**
 * Bouton Cadran : mêmes dimensions que Material (36 dp dessinés, cible de 48 dp, 58 dp de large au moins, libellé en
 * `labelLarge`), sans élévation ni onde : il pâlit sous le doigt et prend un voile au survol et au focus.
 */
@Composable
private fun CadranButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    container: Color,
    contentColor: Color,
    border: BorderStroke?,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource?,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val background = when {
        enabled || container.alpha == 0f -> container
        else -> scheme.onSurface.copy(alpha = 0.12f)
    }
    val foreground = if (enabled) contentColor else scheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = KairosButtonHeight)
            .cadranPressDim(source)
            .clip(shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .background(background, shape)
            .clickable(source, CadranHighlight, enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides foreground) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
        }
    }
}

/** Portée d'un rail de segments Cadran : celle d'une `Row` (les segments y prennent `weight`). */
private class CadranSegmentScope(row: RowScope) : SingleChoiceSegmentedButtonRowScope, RowScope by row

/**
 * Rail de segments d'iOS : 32 dp dessinés, gris (`surfaceContainerHighest`), arrondi à 9 dp ; la rangée fait 48 dp de
 * haut (cible tactile). Largeur comme Material : segments égaux, au plus large d'entre eux.
 */
@Composable
private fun CadranSegmentedRow(modifier: Modifier, content: @Composable SingleChoiceSegmentedButtonRowScope.() -> Unit) {
    val rail = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = modifier
            .selectableGroup()
            .defaultMinSize(minHeight = CadranSegmentTarget)
            .width(IntrinsicSize.Min)
            .height(IntrinsicSize.Min)
            .drawBehind {
                val h = CadranRailHeight.toPx()
                drawRoundRect(rail, Offset(0f, (size.height - h) / 2), Size(size.width, h), CornerRadius(CadranRailRadius.toPx()))
            }
            .padding(horizontal = CadranRailInset),
    ) { CadranSegmentScope(this).content() }
}

/**
 * Segment d'iOS : le choisi est une pastille `surface` claire (en sombre, `outline` à 50 % sur le rail, le gris
 * `#636366` d'iOS), libellé en gras ; aucune coche. Le segment pâlit sous le doigt ; au focus clavier, un contour d'accent.
 */
@Composable
private fun SingleChoiceSegmentedButtonRowScope.CadranSegment(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    label: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val thumb = if (dark) scheme.outline.copy(alpha = 0.5f) else scheme.surfaceContainerLowest
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val focus = scheme.primary
    val style = MaterialTheme.typography.labelLarge
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .defaultMinSize(minWidth = CadranSegmentTarget)
            .selectable(selected, source, indication = null, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .cadranPressDim(source)
            .drawBehind {
                val h = CadranRailHeight.toPx() - 2 * CadranRailInset.toPx()
                val top = (size.height - h) / 2
                val radius = CornerRadius(CadranThumbRadius.toPx())
                if (selected) drawRoundRect(thumb, Offset(0f, top), Size(size.width, h), radius)
                if (focused) drawRoundRect(focus, Offset(0f, top), Size(size.width, h), radius, style = Stroke(2.dp.toPx()))
            }
            .padding(horizontal = 12.dp),
    ) {
        val color = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)
        CompositionLocalProvider(LocalContentColor provides color) {
            ProvideTextStyle(if (selected) style.copy(fontWeight = FontWeight.Bold) else style) { label() }
        }
    }
}

private val CadranSegmentTarget = 48.dp
private val CadranRailHeight = 32.dp
private val CadranRailRadius = 9.dp
private val CadranThumbRadius = 7.dp
private val CadranRailInset = 2.dp
