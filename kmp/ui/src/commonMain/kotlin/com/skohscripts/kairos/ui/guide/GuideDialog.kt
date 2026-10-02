package com.skohscripts.kairos.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.guide_finish
import com.skohscripts.kairos.ui.generated.resources.guide_next
import com.skohscripts.kairos.ui.generated.resources.guide_previous
import com.skohscripts.kairos.ui.generated.resources.guide_see_screen
import com.skohscripts.kairos.ui.generated.resources.guide_skip
import com.skohscripts.kairos.ui.generated.resources.guide_step_counter
import org.jetbrains.compose.resources.stringResource

/**
 * Visite guidée (docs/spec/accueil.md § La visite guidée) : une étape à la fois dans un dialogue MD3 (icône,
 * titre, « Étape n sur N », texte). « Voir l’écran » appelle [onOpen] avec la cible de l'étape et ferme la visite
 * (c'est à l'appelant d'aller à l'écran) ; fermer le dialogue de n'importe quelle façon (clic à côté, Échap, retour)
 * appelle [onDismiss]. L'étape courante survit à une rotation (`rememberSaveable`), rien n'est mémorisé au-delà.
 */
@Composable
fun GuideDialog(kind: GuideKind, onOpen: (GuideTarget) -> Unit, onDismiss: () -> Unit) {
    val steps = remember(kind) { GuideSteps.of(kind) }
    var index by rememberSaveable(kind) { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val last = index >= steps.lastIndex
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(step.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
        title = { Text(stringResource(step.title), modifier = Modifier.heading()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.guide_step_counter, index + 1, steps.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(step.body), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { onOpen(step.target) }) { Text(stringResource(Res.string.guide_see_screen)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (last) onDismiss() else index++ }) {
                Text(stringResource(if (last) Res.string.guide_finish else Res.string.guide_next))
            }
        },
        dismissButton = {
            TextButton(onClick = { if (index == 0) onDismiss() else index-- }) {
                Text(stringResource(if (index == 0) Res.string.guide_skip else Res.string.guide_previous))
            }
        },
    )
}
