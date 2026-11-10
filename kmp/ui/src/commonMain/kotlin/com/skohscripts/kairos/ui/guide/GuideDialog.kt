package com.skohscripts.kairos.ui.guide

import com.skohscripts.kairos.ui.day.verticalScrollWithThumb
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.theme.KairosTextButton
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.guide_finish
import com.skohscripts.kairos.ui.generated.resources.feedback_action
import com.skohscripts.kairos.ui.generated.resources.guide_first_task_go
import com.skohscripts.kairos.ui.generated.resources.guide_next
import com.skohscripts.kairos.ui.generated.resources.guide_previous
import com.skohscripts.kairos.ui.generated.resources.guide_see_screen
import com.skohscripts.kairos.ui.generated.resources.guide_skip
import com.skohscripts.kairos.ui.generated.resources.guide_step_counter
import org.jetbrains.compose.resources.stringResource

/**
 * Visite guidée (docs/spec/accueil.md § La visite guidée, docs/spec/visite-guidee.md) : une étape à la fois dans un dialogue MD3 (icône,
 * titre, « Étape n sur N », texte). « Voir l’écran » appelle [onOpen] avec la cible de l'étape et ferme la visite
 * (c'est à l'appelant d'aller à l'écran) ; fermer le dialogue de n'importe quelle façon (clic à côté, Échap, retour)
 * appelle [onDismiss]. L'étape courante survit à une rotation (`rememberSaveable`), rien n'est mémorisé au-delà.
 */
@Composable
fun GuideDialog(kind: GuideKind, onOpen: (GuideTarget) -> Unit, onDismiss: () -> Unit) {
    GuideDialog(remember(kind) { GuideSteps.of(kind) }, kind.name, onOpen, onDismiss)
}

/**
 * Le même dialogue sur n'importe quelle sélection du catalogue (un chapitre du hub « Découvrir Kairos »,
 * docs/spec/visite-guidee.md) ; [key] distingue l'étape courante d'une sélection à l'autre.
 */
@Composable
fun GuideDialog(
    steps: List<FeatureCard>,
    key: String,
    onOpen: (GuideTarget) -> Unit,
    onDismiss: () -> Unit,
    /** La sélection est parcourue jusqu'au bout : « Terminer » ou « Voir l'écran » sur sa dernière carte (issue #94). */
    onFinish: () -> Unit = {},
) {
    var index by rememberSaveable(key) { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val last = index >= steps.lastIndex
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(step.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
        title = {
            // Hauteur du plus long titre pour toutes les étapes (« Tes données et ton manager » passe sur deux lignes à
            // 400 dp) : les boutons ne sautent pas. Les autres titres sont là, transparents et sans sémantique.
            Box {
                steps.forEach { other ->
                    val shown = other === step
                    Text(
                        stringResource(other.title),
                        modifier = if (shown) Modifier.heading() else Modifier.alpha(0f).clearAndSetSemantics { },
                    )
                }
            }
        },
        text = {
            // Défile sur un écran bas (téléphone en paysage, fenêtre réduite) : rien n’est coupé. Un défilement par étape :
            // la suivante s'ouvre en haut, pas au milieu d'une phrase.
            Column(Modifier.verticalScrollWithThumb(rememberSaveable(index, saver = ScrollState.Saver) { ScrollState(0) }), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.guide_step_counter, index + 1, steps.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Hauteur de la plus longue étape pour toutes : « Précédent » et « Suivant » ne sautent pas d'une étape à
                // l'autre. Les textes des autres étapes sont là, transparents et absents de l'accessibilité.
                Box {
                    steps.forEach { other ->
                        val shown = other === step
                        Text(
                            stringResource(other.body),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = if (shown) Modifier else Modifier.alpha(0f).clearAndSetSemantics { },
                        )
                    }
                }
                KairosTextButton(onClick = { if (last) onFinish(); onOpen(step.target) }) {
                    // La carte « Donner un avis » ouvre un dialogue, pas un écran (docs/spec/retours.md).
                    val action = when (step.target) {
                        GuideTarget.FirstTask -> Res.string.guide_first_task_go
                        GuideTarget.Feedback -> Res.string.feedback_action
                        else -> Res.string.guide_see_screen
                    }
                    Text(stringResource(action))
                }
            }
        },
        confirmButton = {
            KairosTextButton(onClick = { if (last) { onFinish(); onDismiss() } else index++ }) {
                Text(stringResource(if (last) Res.string.guide_finish else Res.string.guide_next))
            }
        },
        dismissButton = {
            KairosTextButton(onClick = { if (index == 0) onDismiss() else index-- }) {
                Text(stringResource(if (index == 0) Res.string.guide_skip else Res.string.guide_previous))
            }
        },
    )
}
