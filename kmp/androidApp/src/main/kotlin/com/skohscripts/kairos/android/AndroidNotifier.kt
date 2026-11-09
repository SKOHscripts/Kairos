package com.skohscripts.kairos.android

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.skohscripts.kairos.core.alerts.Bell
import com.skohscripts.kairos.ui.app.ChronoNotifier
import com.skohscripts.kairos.ui.app.NotifyHint
import com.skohscripts.kairos.ui.app.NotifyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Notifications Android (docs/spec/temps-reel-chrono.md § Android) : les
 * seuils du chrono sont notifiés par des **alarmes** ([ChronoSync]), même
 * application fermée, exactes quand l'autorisation est accordée ; application ouverte, la veille les poste tout de suite
 * ([notifyChrono], issue #219). L'autorisation de notifier (Android 13 et plus) est demandée à l'opt-in, jamais au
 * démarrage ; les freins connus ([hints]) se lèvent dans les réglages du système, jamais demandés d'office.
 */
class AndroidNotifier(private val context: Context) : ChronoNotifier {
    private val mutable = MutableStateFlow(read())
    override val state: StateFlow<NotifyState> = mutable
    override val systemHandlesAlerts: Boolean get() = state.value == NotifyState.ACTIVE

    private val mutableHints = MutableStateFlow(readHints())
    override val hints: StateFlow<Set<NotifyHint>> = mutableHints

    /** Dernière réponse à la demande d'autorisation (un refus reste un refus pour la session). */
    private var refused = false

    fun refresh() {
        mutable.value = read()
        mutableHints.value = readHints()
    }

    private fun read(): NotifyState {
        val manager = context.getSystemService(NotificationManager::class.java)
        val granted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return when {
            granted && manager.areNotificationsEnabled() -> NotifyState.ACTIVE
            granted -> NotifyState.DENIED // coupées dans les réglages du système
            refused -> NotifyState.DENIED
            else -> NotifyState.CAN_REQUEST
        }
    }

    /** Alarmes exactes non accordées (Android 12 et plus) ; Kairos soumis à l'optimisation de la batterie. */
    private fun readHints(): Set<NotifyHint> = buildSet {
        if (!ChronoSync.canExact(context.getSystemService(AlarmManager::class.java))) add(NotifyHint.EXACT_ALARMS)
        val power = context.getSystemService(PowerManager::class.java)
        if (power != null && !power.isIgnoringBatteryOptimizations(context.packageName)) add(NotifyHint.BATTERY)
    }

    override suspend fun requestPermission() {
        if (Build.VERSION.SDK_INT < 33) return refresh()
        val activity = KairosProcess.activity ?: return
        refused = !activity.requestNotificationPermission()
        refresh()
    }

    /** Seuil franchi application ouverte : la notification part tout de suite, l'alarme du seuil est annulée (issue #219). */
    override suspend fun notifyChrono(title: String, body: String, tag: String): Boolean {
        if (state.value != NotifyState.ACTIVE) return false
        ChronoSync.postNow(context, tag, title, body)
        return true
    }

    /**
     * Réglage du système qui lève [hint] : la page « Alarmes et rappels » de Kairos, ou la liste de l'optimisation de la
     * batterie (aucune permission `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` : c'est l'utilisateur qui choisit).
     */
    override fun openHint(hint: NotifyHint) {
        val intent = when (hint) {
            NotifyHint.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= 31) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            } else {
                return
            }
            NotifyHint.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** `notifyChrono` ne rend vrai qu'en postant, et la notification porte la cloche (canal, issue #234). */
    override val ringsWithNotification: Boolean get() = true

    /** Notification bloquée : la cloche sonne quand même, par le lecteur du système (usage notification). */
    override suspend fun ring(bell: Bell) {
        runCatching {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            MediaPlayer.create(context, ChronoSync.bellRes(bell), attributes, 0)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }
}
