package com.skohscripts.kairos.ui.guide.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.demo_banner
import com.skohscripts.kairos.ui.generated.resources.demo_do_it
import com.skohscripts.kairos.ui.generated.resources.demo_done_body
import com.skohscripts.kairos.ui.generated.resources.demo_done_exit
import com.skohscripts.kairos.ui.generated.resources.demo_done_restart
import com.skohscripts.kairos.ui.generated.resources.demo_done_title
import com.skohscripts.kairos.ui.generated.resources.demo_exit
import com.skohscripts.kairos.ui.generated.resources.demo_my_turn
import com.skohscripts.kairos.ui.generated.resources.demo_skip
import com.skohscripts.kairos.ui.generated.resources.guide_previous
import com.skohscripts.kairos.ui.generated.resources.guide_step_counter
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.stringResource

/**
 * Bandeau de la démonstration, sous la barre du haut, hors de la zone défilante : surface neutre et filet (jamais un second
 * bloc teinté), icône, texte et « Quitter », toujours visibles et atteignables en premier.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoBanner(onExit: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(KairosSpacing.s),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = KairosSpacing.l, end = KairosSpacing.s),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = KairosSpacing.s)) {
                    Icon(KairosIcons.Science, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(Res.string.demo_banner), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = KairosSpacing.s))
                }
                KairosTextButton(onClick = onExit) { Text(stringResource(Res.string.demo_exit)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * Panneau de l'étape, en bas de l'écran : carte à contour sans ombre, non bloquante, qui ne prend jamais le focus ; l'étape
 * est annoncée par une région vivante polie (« Étape n sur N », titre, texte). « Faire pour moi » et « À moi de jouer »
 * toujours ensemble ; « Précédent » dès la deuxième étape ; « Passer l’étape » : aucune étape ne bloque.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoPanel(tour: DemoTour, onDoIt: () -> Unit, onMyTurn: () -> Unit, modifier: Modifier = Modifier) {
    val step = tour.step
    OutlinedCard(modifier.fillMaxWidth().padding(horizontal = KairosSpacing.s, vertical = KairosSpacing.xs)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.guide_step_counter, tour.index + 1, tour.steps.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(step.title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.heading())
                Text(stringResource(step.body), style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                KairosButton(onClick = onDoIt) { Text(stringResource(Res.string.demo_do_it)) }
                KairosOutlinedButton(onClick = onMyTurn) { Text(stringResource(Res.string.demo_my_turn)) }
                if (tour.index > 0) KairosTextButton(onClick = tour::previous) { Text(stringResource(Res.string.guide_previous)) }
                KairosTextButton(onClick = tour::next) { Text(stringResource(Res.string.demo_skip)) }
            }
        }
    }
}

/** Fin de la démonstration : « Tu as fait le tour », puis la quitter ou en recommencer une neuve. */
@Composable
fun DemoDoneDialog(onExit: () -> Unit, onRestart: () -> Unit) {
    AlertDialog(
        onDismissRequest = onExit,
        icon = { Icon(KairosIcons.CheckCircleFilled, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(Res.string.demo_done_title), modifier = Modifier.heading()) },
        text = { Text(stringResource(Res.string.demo_done_body)) },
        confirmButton = { KairosButton(onClick = onExit) { Text(stringResource(Res.string.demo_done_exit)) } },
        dismissButton = { KairosTextButton(onClick = onRestart) { Text(stringResource(Res.string.demo_done_restart)) } },
    )
}
