package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.field_type_none
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Type de tâche (catégorie dans l'espace Équipe) : la liste des Réglages, « Aucun » en tête ; une valeur retirée de la liste
 * reste affichée (jamais perdue). Partagé par la fiche d'une tâche et la qualification en ligne de « À traiter » et
 * « À qualifier » (docs/spec/vue-jour.md, issue #166). Le menu montre une barre de défilement dès qu'il ne tient pas en
 * entier : sans elle, rien ne disait qu'une longue liste de types continuait plus bas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TypeField(value: String, types: List<String>, label: StringResource, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val none = stringResource(Res.string.field_type_none)
    val options = (listOf("") + types + listOfNotNull(value.takeIf { it.isNotEmpty() && it !in types })).distinct()
    val scroll = rememberScrollState()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value.ifEmpty { none },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            scrollState = scroll,
            modifier = Modifier.scrollIndicator(scroll, MaterialTheme.colorScheme.outline),
        ) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.ifEmpty { none }) }, onClick = { onChange(option); expanded = false })
            }
        }
    }
}

/**
 * Barre de défilement verticale dessinée sur un contenu qui défile avec [state] (le même sur Android, le bureau et le web,
 * où `VerticalScrollbar` n'existe pas) : un pouce arrondi de 4 dp au bord droit, de hauteur proportionnelle à la part
 * visible ; rien quand tout tient. Purement visuelle : le défilement reste celui du contenu (molette, glisser, clavier).
 * À poser **avant** `verticalScroll` : on dessine alors dans la fenêtre visible, pas dans le contenu déroulé.
 */
internal fun Modifier.scrollIndicator(state: ScrollState, color: Color): Modifier = drawWithContent {
    drawContent()
    val max = state.maxValue
    if (max <= 0 || max == Int.MAX_VALUE) return@drawWithContent
    val width = 4.dp.toPx()
    val margin = 2.dp.toPx()
    val track = size.height - 2 * margin
    val thumb = (track * size.height / (size.height + max)).coerceAtLeast(24.dp.toPx()).coerceAtMost(track)
    val top = margin + (track - thumb) * state.value / max
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - width - margin, top),
        size = Size(width, thumb),
        cornerRadius = CornerRadius(width / 2),
    )
}
