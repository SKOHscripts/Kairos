package com.skohscripts.kairos.android

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.skohscripts.kairos.core.alerts.ChronoAlertKind
import com.skohscripts.kairos.core.alerts.ChronoAlerts
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.ui.chrono.ChronoTexts
import com.skohscripts.kairos.ui.chrono.baseMinutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Le chrono hors de l'application (docs/spec/temps-reel-chrono.md §
 * Android) : tant qu'une session est ouverte, une notification permanente
 * affiche le chronomètre du système (il tourne sans l'application) avec
 * « Arrêter » ; chaque seuil encore à venir a son alarme, qui poste l'alerte
 * même application fermée. Suit l'état de la base : ouvrir, arrêter, finir la
 * tâche ou changer les réglages remet tout d'accord.
 */
object ChronoSync {
    const val CHANNEL_CHRONO = "chrono"
    const val CHANNEL_ALERTS = "chrono-alerts"
    const val NOTIFICATION_RUNNING = 1
    private const val NOTIFICATION_ALERT_BASE = 10

    /** Ce dont dépendent la notification et les alarmes. */
    private data class Key(
        val sessionId: Long?,
        val startedAt: Instant?,
        val title: String?,
        val base: Int,
        val estimate: Int?,
        val idle: Int,
        val pomodoro: Int,
    )

    private fun keyOf(snapshot: KairosSnapshot): Key {
        val running = TimeTracking.runningSession(snapshot.workSessions)
        val task = running?.let { r -> snapshot.tasks.firstOrNull { it.id == r.taskId } }
        return Key(
            running?.id, running?.startedAt, task?.title, running?.let { baseMinutes(snapshot, it) } ?: 0, task?.estimatedMinutes,
            snapshot.settings.timerIdleAlertMinutes, snapshot.settings.pomodoroFocusMinutes,
        )
    }

    fun start(context: Context, repository: KairosRepository, scope: CoroutineScope) {
        scope.launch {
            repository.snapshot.map(::keyOf).distinctUntilChanged().collect { apply(context, it) }
        }
    }

    private suspend fun apply(context: Context, key: Key) {
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannels(context, manager)
        val alarms = context.getSystemService(AlarmManager::class.java)
        ChronoAlertKind.entries.forEach { alarms.cancel(alertIntent(context, it, null, null)) }
        if (key.sessionId == null || key.startedAt == null || key.title == null) {
            manager.cancel(NOTIFICATION_RUNNING)
            return
        }
        manager.notify(NOTIFICATION_RUNNING, running(context, key.title, key.startedAt, key.base))
        val thresholds = ChronoAlerts.thresholds(key.startedAt, key.base, key.estimate, key.idle, key.pomodoro)
        for (alert in ChronoAlerts.upcoming(thresholds, Clock.System.now())) {
            val title = ChronoTexts.alertTitle(key.title)
            val body = ChronoTexts.alertBody(alert)
            // Alarme inexacte autorisée en veille : aucune autorisation d'alarme exacte
            // demandée, quelques minutes de retard possibles téléphone en veille profonde.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alert.at.toEpochMilliseconds(), alertIntent(context, alert.kind, title, body))
        }
    }

    private suspend fun ensureChannels(context: Context, manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CHRONO, ChronoTexts.chronoChannel(), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, ChronoTexts.alertsChannel(), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    /** Notification permanente : le chronomètre du système part de « début − temps déjà passé ». */
    private suspend fun running(context: Context, title: String, startedAt: Instant, base: Int): Notification {
        val stop = PendingIntent.getBroadcast(
            context, 0, Intent(context, ChronoReceiver::class.java).setAction(ChronoReceiver.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL_CHRONO)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(ChronoTexts.running())
            .setWhen(startedAt.toEpochMilliseconds() - base * 60_000L)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setContentIntent(openApp(context))
            .addAction(Notification.Action.Builder(null, ChronoTexts.stop(), stop).build())
            .build()
    }

    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Intention d'une alarme de seuil (une par nature d'alerte, remplacée à chaque programmation). */
    private fun alertIntent(context: Context, kind: ChronoAlertKind, title: String?, body: String?): PendingIntent {
        val intent = Intent(context, ChronoReceiver::class.java)
            .setAction(ChronoReceiver.ACTION_ALERT)
            .setData(android.net.Uri.parse("kairos://alert/${kind.name}"))
            .putExtra(ChronoReceiver.EXTRA_KIND, kind.ordinal)
            .putExtra(ChronoReceiver.EXTRA_TITLE, title)
            .putExtra(ChronoReceiver.EXTRA_BODY, body)
        return PendingIntent.getBroadcast(context, kind.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Poste l'alerte d'un seuil (appelé par l'alarme). */
    fun postAlert(context: Context, kind: Int, title: String, body: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ALERT_BASE + kind,
            Notification.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openApp(context))
                .build(),
        )
    }
}
