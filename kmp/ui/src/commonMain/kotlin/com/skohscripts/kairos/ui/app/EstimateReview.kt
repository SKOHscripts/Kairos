package com.skohscripts.kairos.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.engine.model.EstimateChanges
import com.skohscripts.kairos.ui.day.duration
import com.skohscripts.kairos.ui.day.durationText
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_ok
import com.skohscripts.kairos.ui.generated.resources.action_revert
import com.skohscripts.kairos.ui.generated.resources.action_view
import com.skohscripts.kairos.ui.generated.resources.estimates_changed_banner
import com.skohscripts.kairos.ui.generated.resources.estimates_changed_title
import com.skohscripts.kairos.ui.generated.resources.review_applied
import com.skohscripts.kairos.ui.generated.resources.review_applied_all
import com.skohscripts.kairos.ui.generated.resources.review_apply
import com.skohscripts.kairos.ui.generated.resources.review_apply_all
import com.skohscripts.kairos.ui.generated.resources.review_changed_header
import com.skohscripts.kairos.ui.generated.resources.review_changed_row
import com.skohscripts.kairos.ui.generated.resources.review_empty
import com.skohscripts.kairos.ui.generated.resources.review_typed_header
import com.skohscripts.kairos.ui.generated.resources.review_typed_row
import com.skohscripts.kairos.ui.generated.resources.update_later
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosTextButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Ce que la veille retient d'une session à l'autre du même écran : la référence et le jour de la dernière annonce. */
private class WatchState {
    var baseline: Map<Long, Int>? = null
    var announcedOn: LocalDate? = null
}

/**
 * Veille des estimations (docs/spec/estimateur-modele.md § Estimations qui changent, besoin 12) : au premier relevé de la
 * session, la référence, sans rien dire ; à chaque changement de l'instantané perso, le relevé est comparé à la référence ; des
 * changements nets sont annoncés par [onChanges] (bandeau) et, si le système ne s'en charge pas, une notification, **au plus
 * une fois par jour et par session** ; la référence devient le relevé. Pas de calcul application fermée.
 */
@Composable
fun EstimateWatcher(services: AppServices, onChanges: (List<EstimateChanges.Change>) -> Unit) {
    val snapshot by services.repository.personalSnapshot.collectAsState()
    val state = remember { WatchState() }
    LaunchedEffect(snapshot) {
        val timeZone = TimeZone.currentSystemDefault()
        val now = services.clock.now()
        val after = withContext(Dispatchers.Default) { EstimateChanges.applied(snapshot, now, timeZone) }
        val before = state.baseline
        state.baseline = after
        if (before == null) return@LaunchedEffect
        val changes = EstimateChanges.diff(before, after)
        if (changes.isEmpty()) return@LaunchedEffect
        val today = now.toLocalDateTime(timeZone).date
        if (state.announcedOn == today) return@LaunchedEffect
        state.announcedOn = today
        onChanges(changes)
        if (!services.notifier.systemHandlesAlerts) {
            services.notifier.notify(getString(Res.string.estimates_changed_title), getString(Res.string.estimates_changed_banner, changes.size), "estimates")
        }
    }
}

/** Bandeau neutre (comme celui des mises à jour) : « 3 estimations ont changé », « Voir » et « Plus tard ». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EstimateChangesBanner(count: Int, onView: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Icon(KairosIcons.Model, contentDescription = null)
            Text(stringResource(Res.string.estimates_changed_banner, count), style = MaterialTheme.typography.bodyMedium)
            KairosTextButton(onClick = onView) { Text(stringResource(Res.string.action_view)) }
            KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.update_later)) }
        }
    }
}

/**
 * La revue (besoins 12 et 13) : « Ce qui a changé » ([changes], avant → après) et « Estimations saisies à revoir », chaque
 * ligne avec « Passer à … », plus « Tout appliquer » ; chaque application propose « Annuler », qui remet la durée saisie.
 */
@Composable
fun EstimateReviewDialog(services: AppServices, changes: List<EstimateChanges.Change>, scope: CoroutineScope, onDismiss: () -> Unit) {
    // [scope] est celui de l'hôte : « Annuler » vit dans la snackbar de la coquille et peut servir après la fermeture du
    // dialogue (cliquer dessus le ferme) ; une portée du dialogue serait annulée avec lui, et l'annulation perdue.
    val snapshot by services.repository.personalSnapshot.collectAsState()
    val undo = LocalUndoMessages.current
    val titles = remember(snapshot) { snapshot.tasks.associate { it.id to it.title } }
    val proposals = remember(snapshot) { EstimateChanges.proposals(snapshot, services.clock.now(), TimeZone.currentSystemDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.estimates_changed_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val shown = changes.filter { it.taskId in titles }
                if (shown.isNotEmpty()) {
                    Text(stringResource(Res.string.review_changed_header), style = MaterialTheme.typography.titleSmall)
                    shown.forEach { c ->
                        Text(stringResource(Res.string.review_changed_row, titles.getValue(c.taskId), duration(c.before), duration(c.after)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (proposals.isEmpty()) {
                    Text(stringResource(Res.string.review_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(Res.string.review_typed_header, proposals.size), style = MaterialTheme.typography.titleSmall)
                    proposals.forEach { p ->
                        val e = p.estimate
                        Text(
                            stringResource(
                                Res.string.review_typed_row, titles[p.taskId].orEmpty(), duration(p.typed), duration(e.minutes),
                                duration(e.low ?: e.minutes), duration(e.high ?: e.minutes),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        KairosTextButton(onClick = {
                            scope.launch {
                                services.repository.setEstimate(p.taskId, e.minutes)
                                undo(getString(Res.string.review_applied, durationText(e.minutes)), getString(Res.string.action_revert)) {
                                    scope.launch { services.repository.setEstimate(p.taskId, p.typed) }
                                }
                            }
                        }) { Text(stringResource(Res.string.review_apply, duration(e.minutes))) }
                    }
                    KairosTextButton(onClick = {
                        val applied = proposals
                        scope.launch {
                            applied.forEach { services.repository.setEstimate(it.taskId, it.estimate.minutes) }
                            undo(getString(Res.string.review_applied_all, applied.size), getString(Res.string.action_revert)) {
                                scope.launch { applied.forEach { services.repository.setEstimate(it.taskId, it.typed) } }
                            }
                        }
                    }) { Text(stringResource(Res.string.review_apply_all)) }
                }
            }
        },
        confirmButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_ok)) } },
    )
}
