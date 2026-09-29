package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.day.DayView
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_done_labelled
import com.skohscripts.kairos.ui.generated.resources.action_snooze_labelled
import com.skohscripts.kairos.ui.generated.resources.now_label
import com.skohscripts.kairos.ui.generated.resources.stats_done
import com.skohscripts.kairos.ui.generated.resources.stats_load
import com.skohscripts.kairos.ui.generated.resources.stats_overflow
import com.skohscripts.kairos.ui.generated.resources.stats_todo
import com.skohscripts.kairos.ui.generated.resources.todo_empty
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.LocalKairosExtraColors
import org.jetbrains.compose.resources.stringResource

/**
 * « Maintenant » : le seul bloc teinté de l'écran (conteneur primaire). La
 * prochaine tâche et ses actions nommées (« Fait » plein, « Décaler » texte ;
 * le chrono arrive au jalon M3), puis le bilan de la journée : faites, à
 * faire, requis contre disponible, débordement.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NowCard(view: DayView, language: String, onDone: (Task) -> Unit, onSnooze: (Task) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val next = view.nextUp
            if (next == null) {
                Text(stringResource(Res.string.todo_empty), style = MaterialTheme.typography.titleLarge)
            } else {
                val label = stringResource(Res.string.now_label)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(label) }
                        append(" ")
                        append(next.title)
                        view.nextUpStart?.let { append(" (${Dates.time(it, language)})") }
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { onDone(next) }) {
                        Icon(KairosIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.action_done_labelled), modifier = Modifier.padding(start = 6.dp))
                    }
                    TextButton(onClick = { onSnooze(next) }) {
                        Icon(KairosIcons.Redo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.action_snooze_labelled), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            val stats = view.schedule.stats
            val extra = LocalKairosExtraColors.current
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Badge(stringResource(Res.string.stats_done, view.doneToday.size), extra.okContainer, extra.onOkContainer, icon = KairosIcons.CheckCircle)
                Badge(stringResource(Res.string.stats_todo, view.schedule.scheduled.size + view.schedule.unscheduled.size))
                Text(
                    stringResource(Res.string.stats_load, duration(stats.requiredMinutes), duration(stats.availableMinutes)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (stats.overflowMinutes > 0) ErrorBadge(stringResource(Res.string.stats_overflow, duration(stats.overflowMinutes)))
            }
        }
    }
}
