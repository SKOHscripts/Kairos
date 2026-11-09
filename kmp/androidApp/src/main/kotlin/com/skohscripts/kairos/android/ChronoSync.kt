package com.skohscripts.kairos.android

import kotlinx.datetime.TimeZone
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.content.Context
import android.content.Intent
import android.os.Build
import com.skohscripts.kairos.core.alerts.Bell
import com.skohscripts.kairos.core.alerts.ChronoAlertKind
import com.skohscripts.kairos.core.alerts.ChronoAlerts
import com.skohscripts.kairos.core.engine.DurationEstimator
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
 * tâche ou changer les réglages remet tout d'accord. Le garde-fou (docs/spec/temps-reel-chrono.md § Garde-fou) a son
 * alarme, qui ferme la session à l'instant limite application fermée ; une session déjà au-delà est close aussitôt.
 */
object ChronoSync {
    const val CHANNEL_CHRONO = "chrono"
    const val CHANNEL_ALERTS = "chrono-alerts"

    /** Canaux des cloches (issue #234) : le son d'un canal ne change plus après sa création, d'où un canal par cloche. */
    private fun bellChannel(bell: Bell) = if (bell == Bell.DOUBLE) "chrono-bell-2" else "chrono-bell-1"
    const val NOTIFICATION_RUNNING = 1
    private const val NOTIFICATION_ALERT_BASE = 10

    /** Indice de notification du garde-fou, après ceux des trois alertes (`NOTIFICATION_ALERT_BASE + indice`). */
    private val AUTO_STOP_KIND = ChronoAlertKind.entries.size

    /** Ce dont dépendent la notification et les alarmes. */
    private data class Key(
        val sessionId: Long?,
        val startedAt: Instant?,
        val title: String?,
        val base: Int,
        val estimate: Int?,
        val idle: Int,
        val pomodoro: Int,
        val autoStop: Int,
        val sound: Boolean,
    )

    private fun keyOf(snapshot: KairosSnapshot): Key {
        val running = TimeTracking.runningSession(snapshot.workSessions)
        val task = running?.let { r -> snapshot.tasks.firstOrNull { it.id == r.taskId } }
        return Key(
            running?.id, running?.startedAt, task?.title, running?.let { baseMinutes(snapshot, it) } ?: 0,
            task?.let { DurationEstimator.effectiveMinutes(it, snapshot, Clock.System.now(), TimeZone.currentSystemDefault()) },
            snapshot.settings.timerIdleAlertMinutes, snapshot.settings.pomodoroFocusMinutes, snapshot.settings.timerAutoStop,
            snapshot.settings.timerAlertSound,
        )
    }

    /** Dernier état appliqué, pour [reschedule] (issue #219). */
    @Volatile private var last: Triple<Context, KairosRepository, Key>? = null

    fun start(context: Context, repository: KairosRepository, scope: CoroutineScope) {
        scope.launch {
            repository.personalSnapshot.map(::keyOf).distinctUntilChanged().collect {
                last = Triple(context.applicationContext, repository, it)
                apply(context, repository, it)
            }
        }
    }

    /**
     * Repasse le dernier état (retour dans l'application) : les alarmes deviennent exactes quand l'autorisation vient
     * d'être accordée, inexactes quand elle a été retirée (le système annule alors les alarmes exactes de l'application).
     */
    suspend fun reschedule() {
        val (context, repository, key) = last ?: return
        apply(context, repository, key)
    }

    /** Alarmes exactes possibles : Android 11 et moins, ou autorisation « Alarmes et rappels » accordée (issue #219). */
    fun canExact(alarms: AlarmManager): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    /** Programme [intent] à [at] : exacte si possible, sinon inexacte (docs/spec/temps-reel-chrono.md § Décisions). */
    private fun schedule(alarms: AlarmManager, at: Instant, intent: PendingIntent) {
        if (canExact(alarms)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilliseconds(), intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilliseconds(), intent)
        }
    }

    /**
     * Seuil franchi application ouverte (issue #219) : l'alarme de ce seuil est annulée et la même notification part tout
     * de suite. [tag] : `OVER`, `IDLE`, `POMODORO` ou `AUTO_STOP`.
     */
    fun postNow(context: Context, tag: String, title: String, body: String) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val kind = ChronoAlertKind.entries.firstOrNull { it.name == tag }
        if (kind != null) alarms.cancel(alertIntent(context, kind, null, null)) else alarms.cancel(autoStopIntent(context, 0L, null, null, 0))
        postAlert(context, kind?.ordinal ?: AUTO_STOP_KIND, title, body, last?.third?.sound ?: false)
    }

    private suspend fun apply(context: Context, repository: KairosRepository, key: Key) {
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannels(context, manager)
        val alarms = context.getSystemService(AlarmManager::class.java)
        ChronoAlertKind.entries.forEach { alarms.cancel(alertIntent(context, it, null, null)) }
        alarms.cancel(autoStopIntent(context, 0L, null, null, 0))
        if (key.sessionId == null || key.startedAt == null || key.title == null) {
            manager.cancel(NOTIFICATION_RUNNING)
            return
        }
        val stopAt = ChronoAlerts.autoStopAt(key.startedAt, key.autoStop)
        if (stopAt != null && Clock.System.now() >= stopAt) {
            // Déjà au-delà de la limite (téléphone éteint, alarme manquée) : close à l'instant limite ; l'état change, on repasse ici.
            autoStop(context, repository, key.sessionId, stopAt, key.title, key.autoStop)
            return
        }
        manager.notify(NOTIFICATION_RUNNING, running(context, key.title, key.startedAt, key.base))
        if (stopAt != null) schedule(alarms, stopAt, autoStopIntent(context, key.sessionId, stopAt, key.title, key.autoStop))
        val thresholds = ChronoAlerts.beforeStop(ChronoAlerts.thresholds(key.startedAt, key.base, key.estimate, key.idle, key.pomodoro), stopAt)
        for (alert in ChronoAlerts.upcoming(thresholds, Clock.System.now())) {
            val title = ChronoTexts.alertTitle(key.title)
            val body = ChronoTexts.alertBody(alert)
            // Exacte si l'autorisation est accordée, sinon inexacte (quelques minutes de retard possibles en veille profonde).
            schedule(alarms, alert.at, alertIntent(context, alert.kind, title, body, key.sound))
        }
    }

    private suspend fun ensureChannels(context: Context, manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CHRONO, ChronoTexts.chronoChannel(), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, ChronoTexts.alertsChannel(), NotificationManager.IMPORTANCE_HIGH),
        )
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        Bell.entries.forEach { bell ->
            manager.createNotificationChannel(
                NotificationChannel(bellChannel(bell), ChronoTexts.bellChannel(bell), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(bellUri(context, bell), attributes)
                },
            )
        }
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

    /** Le fichier de [bell] dans `res/raw`, généré par `kmp/tools/make_bells.py`. */
    fun bellRes(bell: Bell): Int = if (bell == Bell.DOUBLE) R.raw.bell_double else R.raw.bell_single

    private fun bellUri(context: Context, bell: Bell): android.net.Uri =
        android.net.Uri.parse("android.resource://${context.packageName}/${bellRes(bell)}")

    /** Intention d'une alarme de seuil (une par nature d'alerte, remplacée à chaque programmation). */
    private fun alertIntent(context: Context, kind: ChronoAlertKind, title: String?, body: String?, sound: Boolean = false): PendingIntent {
        val intent = Intent(context, ChronoReceiver::class.java)
            .setAction(ChronoReceiver.ACTION_ALERT)
            .setData(android.net.Uri.parse("kairos://alert/${kind.name}"))
            .putExtra(ChronoReceiver.EXTRA_KIND, kind.ordinal)
            .putExtra(ChronoReceiver.EXTRA_TITLE, title)
            .putExtra(ChronoReceiver.EXTRA_BODY, body)
            .putExtra(ChronoReceiver.EXTRA_SOUND, sound)
        return PendingIntent.getBroadcast(context, kind.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Intention de l'alarme du garde-fou (une seule, remplacée à chaque programmation). */
    private fun autoStopIntent(context: Context, sessionId: Long, at: Instant?, title: String?, limit: Int): PendingIntent {
        val intent = Intent(context, ChronoReceiver::class.java)
            .setAction(ChronoReceiver.ACTION_AUTO_STOP)
            .setData(android.net.Uri.parse("kairos://alert/AUTO_STOP"))
            .putExtra(ChronoReceiver.EXTRA_SESSION, sessionId)
            .putExtra(ChronoReceiver.EXTRA_AT, at?.toEpochMilliseconds() ?: 0L)
            .putExtra(ChronoReceiver.EXTRA_TITLE, title)
            .putExtra(ChronoReceiver.EXTRA_LIMIT, limit)
        return PendingIntent.getBroadcast(context, AUTO_STOP_REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private const val AUTO_STOP_REQUEST = 100

    /**
     * Garde-fou : ferme la session à [at] ; si c'est cet appel qui l'a fermée (la veille de l'application a pu le faire
     * avant), poste « chrono arrêté automatiquement après… ».
     */
    suspend fun autoStop(context: Context, repository: KairosRepository, sessionId: Long, at: Instant, title: String, limit: Int) {
        if (repository.stopTimerAt(sessionId, at)) {
            postAlert(context, AUTO_STOP_KIND, ChronoTexts.alertTitle(title), ChronoTexts.autoStopBody(limit), repository.snapshot.value.settings.timerAlertSound)
        }
    }

    /** Poste l'alerte d'un seuil (appelé par l'alarme) ; [sound] : sur le canal de sa cloche (issue #234). */
    fun postAlert(context: Context, kind: Int, title: String, body: String, sound: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val bell = ChronoAlertKind.entries.getOrNull(kind)?.let(Bell::of) ?: Bell.AUTO_STOP
        manager.notify(
            NOTIFICATION_ALERT_BASE + kind,
            Notification.Builder(context, if (sound) bellChannel(bell) else CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_REMINDER)
                // Reposter une alerte encore affichée (veille puis alarme déjà partie) ne sonne pas deux fois.
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(openApp(context))
                .build(),
        )
    }
}
