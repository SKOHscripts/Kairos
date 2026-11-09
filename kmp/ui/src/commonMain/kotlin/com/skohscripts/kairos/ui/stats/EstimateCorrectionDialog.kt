package com.skohscripts.kairos.ui.stats

import com.skohscripts.kairos.ui.day.verticalScrollWithThumb
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.stats.EstimateCorrection
import com.skohscripts.kairos.ui.day.duration
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.stats_correct_all
import com.skohscripts.kairos.ui.generated.resources.stats_correct_apply
import com.skohscripts.kairos.ui.generated.resources.stats_correct_intro
import com.skohscripts.kairos.ui.generated.resources.stats_correct_no_estimate
import com.skohscripts.kairos.ui.generated.resources.stats_correct_none
import com.skohscripts.kairos.ui.generated.resources.stats_correct_row
import com.skohscripts.kairos.ui.generated.resources.stats_correct_title
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.stringResource

/**
 * Aperçu de « Corriger les estimations… » (docs/spec/statistiques.md, issue #101) : chaque tâche avec « 30 min → 45 min »,
 * cochée par défaut ; rien n'est écrit avant « Appliquer (N) ». [onApply] reçoit les tâches cochées.
 */
@Composable
internal fun EstimateCorrectionDialog(
    candidates: List<EstimateCorrection.Candidate>,
    onDismiss: () -> Unit,
    onApply: (List<EstimateCorrection.Candidate>) -> Unit,
) {
    val unticked = remember(candidates) { mutableStateListOf<Long>() }
    val chosen = candidates.filter { it.task.id !in unticked }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.stats_correct_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.stats_correct_intro), style = MaterialTheme.typography.bodyMedium)
                Row {
                    KairosTextButton(onClick = { unticked.clear() }, enabled = unticked.isNotEmpty()) { Text(stringResource(Res.string.stats_correct_all)) }
                    KairosTextButton(onClick = { unticked.clear(); unticked.addAll(candidates.map { it.task.id }) }, enabled = chosen.isNotEmpty()) {
                        Text(stringResource(Res.string.stats_correct_none))
                    }
                }
                Column(Modifier.heightIn(max = 360.dp).verticalScrollWithThumb()) {
                    val none = stringResource(Res.string.stats_correct_no_estimate)
                    candidates.forEach { c ->
                        val checked = c.task.id !in unticked
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(checked, role = Role.Checkbox) { if (it) unticked.remove(c.task.id) else unticked.add(c.task.id) },
                        ) {
                            Checkbox(checked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(Res.string.stats_correct_row, c.oldMinutes?.let { duration(it) } ?: none, duration(c.realMinutes)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            KairosButton(enabled = chosen.isNotEmpty(), onClick = { onApply(chosen) }) { Text(stringResource(Res.string.stats_correct_apply, chosen.size)) }
        },
        dismissButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
