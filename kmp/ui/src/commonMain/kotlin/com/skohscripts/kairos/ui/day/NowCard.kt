package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.skohscripts.kairos.ui.generated.resources.action_timer_start
import com.skohscripts.kairos.ui.generated.resources.action_timer_stop
import com.skohscripts.kairos.ui.generated.resources.alerts_active
import com.skohscripts.kairos.ui.generated.resources.alerts_denied
import com.skohscripts.kairos.ui.generated.resources.alerts_enable
import com.skohscripts.kairos.ui.generated.resources.alerts_unavailable
import com.skohscripts.kairos.ui.generated.resources.now_running_empty
import com.skohscripts.kairos.ui.generated.resources.now_running_estimate
import com.skohscripts.kairos.ui.generated.resources.now_running_label
import com.skohscripts.kairos.ui.generated.resources.stats_spent_today
import com.skohscripts.kairos.ui.generated.resources.now_label
import com.skohscripts.kairos.ui.generated.resources.stats_done
import com.skohscripts.kairos.ui.generated.resources.stats_load
import com.skohscripts.kairos.ui.generated.resources.stats_overflow
import com.skohscripts.kairos.ui.generated.resources.stats_todo
import com.skohscripts.kairos.ui.generated.resources.todo_empty
import com.skohscripts.kairos.ui.app.NotifyState
import com.skohscripts.kairos.ui.chrono.rememberLiveMinutes
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.LocalKairosExtraColors
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Alertes du chrono : bouton d'autorisation tant qu'elle est à demander,
 * sinon ce qui va réellement se passer (Kairos 2, issue #34).
 */
@Composable
private fun AlertsOptIn(state: NotifyState, onEnable: () -> Unit) {
    if (state == NotifyState.CAN_REQUEST) {
        KairosTextButton(onClick = onEnable, contentPadding = KairosButtonIconPadding) {
            Icon(KairosIcons.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.alerts_enable), modifier = Modifier.padding(start = 6.dp))
        }
        return
    }
    val text = when (state) {
        NotifyState.ACTIVE -> Res.string.alerts_active
        NotifyState.DENIED -> Res.string.alerts_denied
        else -> Res.string.alerts_unavailable
    }
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall)
}

/**
 * « En ce moment » (surface inverse, seul élément sombre posé dans la page) :
 * la tâche du chrono, son minuteur vivant, l'estimé, « Arrêter le chrono ».
 */
@Composable
internal fun RunningCard(view: DayView, clock: Clock, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.inverseSurface, contentColor = scheme.inverseOnSurface),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.now_running_label), style = MaterialTheme.typography.labelLarge)
            val running = view.running
            val task = view.runningTask
            if (running == null || task == null) {
                Text(stringResource(Res.string.now_running_empty), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            val minutes by rememberLiveMinutes(running.startedAt, view.runningBaseMinutes, clock)
            Text(duration(minutes), style = MaterialTheme.typography.displaySmall)
            task.estimatedMinutes?.takeIf { it > 0 }?.let {
                Text(stringResource(Res.string.now_running_estimate, duration(it)), style = MaterialTheme.typography.bodySmall)
            }
            KairosButton(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.inversePrimary, contentColor = scheme.onPrimaryContainer),
                contentPadding = KairosButtonIconPadding,
            ) {
                Icon(KairosIcons.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.action_timer_stop), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/**
 * « Maintenant » : le seul bloc teinté de l'écran (conteneur primaire). La
 * prochaine tâche et ses actions nommées (« Fait » plein, chrono en contour,
 * « Décaler » texte), puis le bilan de la journée : faites, à faire, requis
 * contre disponible, débordement, temps travaillé aujourd'hui par type ; et
 * l'état des alertes du chrono.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NowCard(
    view: DayView,
    language: String,
    notifyState: NotifyState,
    onDone: (Task) -> Unit,
    onSnooze: (Task) -> Unit,
    onToggleTimer: (Task) -> Unit,
    onEnableAlerts: () -> Unit,
) {
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
                    KairosButton(onClick = { onDone(next) }, contentPadding = KairosButtonIconPadding) {
                        Icon(KairosIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.action_done_labelled), modifier = Modifier.padding(start = 6.dp))
                    }
                    val running = view.running?.taskId == next.id
                    KairosOutlinedButton(onClick = { onToggleTimer(next) }, contentPadding = KairosButtonIconPadding) {
                        Icon(if (running) KairosIcons.Stop else KairosIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(if (running) Res.string.action_timer_stop else Res.string.action_timer_start),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    KairosTextButton(onClick = { onSnooze(next) }, contentPadding = KairosButtonIconPadding) {
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
                val parts = mutableListOf<String>()
                for ((type, minutes) in view.spentByTypeToday) parts += "$type ${duration(minutes)}"
                val byType = parts.joinToString(" · ")
                Text(
                    stringResource(Res.string.stats_spent_today, duration(view.spentToday)) + if (byType.isEmpty()) "" else " ($byType)",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            AlertsOptIn(notifyState, onEnableAlerts)
        }
    }
}
