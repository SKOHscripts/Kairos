package com.skohscripts.kairos.ui.chrono

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.alerts.ChronoAlerts
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.alert_close
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Minutes déjà passées sur la tâche du chrono **avant** la session en cours (sessions closes et saisie manuelle). */
fun baseMinutes(snapshot: KairosSnapshot, running: WorkSession): Int {
    val closed = snapshot.workSessions.filter { it.taskId == running.taskId && it.id != running.id }
    val manual = snapshot.tasks.firstOrNull { it.id == running.taskId }?.manualTimeSpentMinutes ?: 0
    return closed.sumOf { TimeTracking.sessionMinutes(it, it.endedAt ?: it.startedAt) } + manual
}

/** Minutes écoulées depuis [start], plus [base], rafraîchies chaque seconde (minuteur vivant). */
@Composable
fun rememberLiveMinutes(start: Instant, base: Int, clock: Clock): State<Int> = produceState(base + minutesSince(start, clock.now()), start, base) {
    while (true) {
        value = base + minutesSince(start, clock.now())
        delay(1_000)
    }
}

private fun minutesSince(start: Instant, now: Instant) = maxOf(0L, (now - start).inWholeSeconds / 60).toInt()

/**
 * Veille du chrono, pour toute l'application (docs/spec/temps-reel-chrono.md
 * § Alertes) : chaque seconde, le titre de fenêtre montre le temps qui tourne ;
 * un seuil franchi **pendant** la veille ajoute un bandeau (toujours), une
 * notification système (sauf si le système s'en charge déjà), et à défaut le
 * titre clignotant pendant une minute et le son s'il est activé.
 */
@Composable
fun ChronoWatcher(services: AppServices, alerts: SnapshotStateList<String>) {
    val snapshot by services.repository.snapshot.collectAsState()
    val running = TimeTracking.runningSession(snapshot.workSessions)
    val task = running?.let { r -> snapshot.tasks.firstOrNull { it.id == r.taskId } }
    val base = running?.let { baseMinutes(snapshot, it) } ?: 0
    val settings = snapshot.settings
    val notifier = services.notifier
    val clock = services.clock

    LaunchedEffect(running?.id, task?.title, task?.estimatedMinutes, base, settings.timerIdleAlertMinutes, settings.pomodoroFocusMinutes, settings.timerAlertSound) {
        if (running == null || task == null) {
            notifier.setTitle(null)
            return@LaunchedEffect
        }
        val thresholds = ChronoAlerts.thresholds(running.startedAt, base, task.estimatedMinutes, settings.timerIdleAlertMinutes, settings.pomodoroFocusMinutes)
        var since = clock.now()
        var flashUntil = Instant.DISTANT_PAST
        var flashText = ""
        var flashOn = false
        try {
            while (true) {
                val now = clock.now()
                for (alert in ChronoAlerts.crossed(thresholds, since, now)) {
                    val body = ChronoTexts.alertBody(alert)
                    val message = "${task.title} · $body"
                    alerts += message
                    if (notifier.systemHandlesAlerts) continue
                    if (!notifier.notify(ChronoTexts.alertTitle(task.title), body, alert.kind.name)) {
                        flashText = message
                        flashUntil = now + 60.seconds
                        if (settings.timerAlertSound) notifier.beep()
                    }
                }
                since = now
                if (now < flashUntil) {
                    flashOn = !flashOn
                    notifier.setTitle(if (flashOn) "⚠ $flashText" else null)
                } else {
                    val total = base + minutesSince(running.startedAt, now)
                    notifier.setTitle("(${total / 60}:${(total % 60).toString().padStart(2, '0')}) ${task.title}")
                }
                delay(1_000)
            }
        } finally {
            notifier.setTitle(null)
        }
    }
}

/**
 * Bandeaux d'alerte flottants (surface inverse, charte « snackbar
 * d'alerte ») : persistants, un clic les ferme ; une alerte manquée serait
 * une alerte perdue.
 */
@Composable
fun AlertBanners(alerts: SnapshotStateList<String>, modifier: Modifier = Modifier) {
    if (alerts.isEmpty()) return
    val close = stringResource(Res.string.alert_close)
    Column(modifier.widthIn(max = 560.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        alerts.forEachIndexed { index, message ->
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = MaterialTheme.shapes.small,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                    Icon(KairosIcons.Warning, contentDescription = null)
                    Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 14.dp))
                    IconButton(onClick = { if (index < alerts.size) alerts.removeAt(index) }) {
                        Icon(KairosIcons.Close, contentDescription = close)
                    }
                }
            }
        }
    }
}
