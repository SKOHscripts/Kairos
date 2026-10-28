package com.skohscripts.kairos.ui.reminders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.skohscripts.kairos.core.reminders.DailySummary
import com.skohscripts.kairos.core.reminders.Reminders
import com.skohscripts.kairos.ui.app.AppServices
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Veille des rappels, pour toute l'application (docs/spec/rappels.md) : le plan des rappels est recalculé quand la base change
 * (une tâche faite, décalée, désépinglée annule le sien) ou que le jour change ; chaque seconde, un rappel dont l'heure vient de
 * passer ajoute un bandeau (celui des alertes du chrono) et, si le système ne s'en charge pas déjà (alarmes Android), une
 * notification. Un rappel n'est montré qu'une fois par session ([shown]) ; rien n'est rattrapé. Le récapitulatif du jour
 * (`reminders.dailySummary`, indépendant des rappels) suit la même règle, sous sa clé `summary-<jour>`.
 */
@Composable
fun ReminderWatcher(services: AppServices, alerts: SnapshotStateList<String>) {
    val snapshot by services.repository.personalSnapshot.collectAsState()
    val shown = remember { mutableSetOf<String>() }
    val settings = snapshot.settings.reminders
    if (settings?.enabled != true && settings?.dailySummary != true) return
    val notifier = services.notifier
    val clock = services.clock
    LaunchedEffect(snapshot) {
        val timeZone = TimeZone.currentSystemDefault()
        var since = clock.now().toLocalDateTime(timeZone)
        var day = since.date
        var plan = Reminders.plan(snapshot, day, since, timeZone)
        var summary = DailySummary.plan(snapshot, day, since, timeZone)
        while (true) {
            delay(1_000)
            val now = clock.now().toLocalDateTime(timeZone)
            if (now.date != day) {
                // Nouveau jour : son plan depuis le début de la journée (les rappels d'avant `since` restent écartés).
                day = now.date
                plan = Reminders.plan(snapshot, day, null, timeZone)
                summary = DailySummary.plan(snapshot, day, null, timeZone)
            }
            summary?.takeIf { it.at > since && it.at <= now && shown.add(it.key) }?.let {
                val title = ReminderTexts.summaryTitle()
                alerts += "$title · ${ReminderTexts.summaryCounts(it)}"
                if (!notifier.systemHandlesAlerts) notifier.notify(title, ReminderTexts.summaryDetail(it), it.key)
            }
            for (reminder in plan) {
                if (reminder.at <= since || reminder.at > now || !shown.add(reminder.key)) continue
                val body = ReminderTexts.body(reminder)
                alerts += "${reminder.title} · $body"
                if (!notifier.systemHandlesAlerts) notifier.notify(reminder.title, body, "reminder-${reminder.key}")
            }
            since = now
        }
    }
}
