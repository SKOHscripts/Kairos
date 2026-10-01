package com.skohscripts.kairos.ui.theme

import androidx.compose.foundation.BorderStroke
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
) = M3Button(
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
) = M3OutlinedButton(
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
) = M3FilledTonalButton(
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
) = M3TextButton(
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
) = FilterChip(
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

/** Sélecteur segmenté à choix exclusif : 40 dp dessinés, aire tactile de 48 dp ; remplace `SingleChoiceSegmentedButtonRow`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KairosSegmentedRow(
    modifier: Modifier = Modifier,
    content: @Composable SingleChoiceSegmentedButtonRowScope.() -> Unit,
) = SingleChoiceSegmentedButtonRow(modifier = modifier, content = content)

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
) = SegmentedButton(
    selected = selected,
    onClick = onClick,
    shape = shape,
    modifier = modifier,
    enabled = enabled,
    contentPadding = contentPadding,
    icon = icon,
    label = label,
)

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
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        if (tint == Color.Unspecified) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(KairosRowIconSize))
        } else {
            Icon(icon, contentDescription = description, modifier = Modifier.size(KairosRowIconSize), tint = tint)
        }
    }
}
