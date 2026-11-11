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
import com.skohscripts.kairos.ui.day.duration
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.stats_correct_all
import com.skohscripts.kairos.ui.generated.resources.stats_correct_apply
import com.skohscripts.kairos.ui.generated.resources.stats_correct_none
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.stringResource
import com.skohscripts.kairos.core.stats.SuspectSessions
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.app.uiLanguage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.skohscripts.kairos.ui.generated.resources.stats_suspect_title
import com.skohscripts.kairos.ui.generated.resources.stats_suspect_intro
import com.skohscripts.kairos.ui.generated.resources.stats_suspect_row

/**
 * Aperçu de « Chronos à vérifier… » (docs/spec/previsions-taches.md § Étape G) : chaque session suspecte avec « mar. 10/11 :
 * 6 h → 30 min », **décochée** par défaut (une longue session peut être vraie : c'est l'utilisateur qui le sait) ; rien n'est
 * écrit avant « Appliquer (N) ». [onApply] reçoit les sessions cochées.
 */
@Composable
internal fun SuspectSessionsDialog(
    suspects: List<SuspectSessions.Suspect>,
    onDismiss: () -> Unit,
    onApply: (List<SuspectSessions.Suspect>) -> Unit,
) {
    val ticked = remember(suspects) { mutableStateListOf<Long>() }
    val chosen = suspects.filter { it.session.id in ticked }
    val language = uiLanguage()
    val zone = remember { TimeZone.currentSystemDefault() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.stats_suspect_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.stats_suspect_intro), style = MaterialTheme.typography.bodyMedium)
                Row {
                    KairosTextButton(onClick = { ticked.clear(); ticked.addAll(suspects.map { it.session.id }) }, enabled = chosen.size < suspects.size) {
                        Text(stringResource(Res.string.stats_correct_all))
                    }
                    KairosTextButton(onClick = { ticked.clear() }, enabled = chosen.isNotEmpty()) { Text(stringResource(Res.string.stats_correct_none)) }
                }
                Column(Modifier.heightIn(max = 360.dp).verticalScrollWithThumb()) {
                    suspects.forEach { s ->
                        val checked = s.session.id in ticked
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(checked, role = Role.Checkbox) { if (it) ticked.add(s.session.id) else ticked.remove(s.session.id) },
                        ) {
                            Checkbox(checked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(
                                        Res.string.stats_suspect_row,
                                        Dates.dayShort(s.session.startedAt.toLocalDateTime(zone).date, language), duration(s.minutes), duration(s.usualMinutes),
                                    ),
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
