package com.skohscripts.kairos.android

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.reminders.DailySummary
import com.skohscripts.kairos.core.reminders.Reminders
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.reminders.ReminderTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Les rappels hors de l'application (docs/spec/rappels.md § Android) : à chaque changement de la base (réglages compris), les
 * alarmes de rappel sont annulées puis reposées pour aujourd'hui (à partir de maintenant) et demain (depuis le début du jour),
 * une alarme inexacte par rappel, titre et texte déjà rédigés. L'alarme poste la notification même application fermée
 * ([ChronoReceiver]). Sans permission ajoutée. Le récapitulatif du jour (`reminders.dailySummary`, indépendant des rappels) a ses
 * deux alarmes, aujourd'hui (si l'heure n'est pas passée) et demain, sur ses propres codes de requête.
 */
object ReminderSync {
    const val CHANNEL_REMINDERS = "reminders"

    /** Réservoir fixe de codes de requête : annuler « toutes les alarmes de rappel » sans rien mémoriser. */
    private const val MAX_ALARMS = 24
    private const val REQUEST_BASE = 1_000
    private const val NOTIFICATION_BASE = 100
    /** Récapitulatif du jour : deux codes (aujourd'hui, demain), une seule notification (la plus récente remplace l'autre). */
    private const val SUMMARY_REQUEST_BASE = 2_000
    private const val SUMMARY_NOTIFICATION = 200

    fun start(context: Context, repository: KairosRepository, scope: CoroutineScope) {
        scope.launch { repository.personalSnapshot.collect { apply(context, it) } }
    }

    private suspend fun apply(context: Context, snapshot: KairosSnapshot) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        (0 until MAX_ALARMS).forEach { alarms.cancel(intent(context, it, null, null)) }
        (0 until 2).forEach { alarms.cancel(summaryIntent(context, it, null, null)) }
        val settings = snapshot.settings.reminders
        if (settings?.enabled != true && settings?.dailySummary != true) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, ReminderTexts.channel(), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val timeZone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(timeZone)
        val tomorrow = now.date.plus(DatePeriod(days = 1))
        listOf(DailySummary.plan(snapshot, now.date, now, timeZone), DailySummary.plan(snapshot, tomorrow, null, timeZone)).forEachIndexed { slot, summary ->
            if (summary == null) return@forEachIndexed
            alarms.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                summary.at.toInstant(timeZone).toEpochMilliseconds(),
                summaryIntent(context, slot, ReminderTexts.summaryTitle(), ReminderTexts.summaryDetail(summary)),
            )
        }
        val reminders = Reminders.plan(snapshot, now.date, now, timeZone) + Reminders.plan(snapshot, tomorrow, null, timeZone)
        reminders.take(MAX_ALARMS).forEachIndexed { slot, reminder ->
            // Alarme inexacte autorisée en veille, comme les alertes du chrono (docs/spec/temps-reel-chrono.md).
            alarms.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.at.toInstant(timeZone).toEpochMilliseconds(),
                intent(context, slot, reminder.title, ReminderTexts.body(reminder)),
            )
        }
    }

    /** Intention de l'alarme du créneau [slot] du réservoir, remplacée à chaque programmation. */
    private fun intent(context: Context, slot: Int, title: String?, body: String?): PendingIntent {
        val intent = Intent(context, ChronoReceiver::class.java)
            .setAction(ChronoReceiver.ACTION_REMINDER)
            .setData(android.net.Uri.parse("kairos://reminder/$slot"))
            .putExtra(ChronoReceiver.EXTRA_KIND, slot)
            .putExtra(ChronoReceiver.EXTRA_TITLE, title)
            .putExtra(ChronoReceiver.EXTRA_BODY, body)
        return PendingIntent.getBroadcast(context, REQUEST_BASE + slot, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Intention de l'alarme du récapitulatif [slot] (0 aujourd'hui, 1 demain), remplacée à chaque programmation. */
    private fun summaryIntent(context: Context, slot: Int, title: String?, body: String?): PendingIntent {
        val intent = Intent(context, ChronoReceiver::class.java)
            .setAction(ChronoReceiver.ACTION_SUMMARY)
            .setData(android.net.Uri.parse("kairos://summary/$slot"))
            .putExtra(ChronoReceiver.EXTRA_TITLE, title)
            .putExtra(ChronoReceiver.EXTRA_BODY, body)
        return PendingIntent.getBroadcast(context, SUMMARY_REQUEST_BASE + slot, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * Poste le récapitulatif (appelé par l'alarme) : [body] porte le compte puis les premières tâches, une par ligne ; replié, la
     * notification n'en montre que la première ligne. Un toucher ouvre la vue Jour (destination externe, docs/spec/widgets-android.md).
     */
    fun postSummary(context: Context, title: String, body: String) {
        val open = PendingIntent.getActivity(
            context, SUMMARY_REQUEST_BASE,
            Intent(context, MainActivity::class.java)
                .setData(android.net.Uri.parse("kairos://open/${Destination.DAY.name}"))
                .putExtra(MainActivity.EXTRA_DESTINATION, Destination.DAY.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(NotificationManager::class.java).notify(
            SUMMARY_NOTIFICATION,
            Notification.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body.substringBefore('\n'))
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build(),
        )
    }

    /** Poste le rappel (appelé par l'alarme). */
    fun post(context: Context, slot: Int, title: String, body: String) {
        context.getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_BASE + slot,
            Notification.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(ChronoSync.openApp(context))
                .build(),
        )
    }
}
