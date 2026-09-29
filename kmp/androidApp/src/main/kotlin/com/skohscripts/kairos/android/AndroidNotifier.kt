package com.skohscripts.kairos.android

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.skohscripts.kairos.ui.app.ChronoNotifier
import com.skohscripts.kairos.ui.app.NotifyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Notifications Android (docs/spec-v3/temps-reel-chrono.md § Android) : les
 * seuils du chrono sont notifiés par des **alarmes** ([ChronoSync]), même
 * application fermée ; l'application n'ajoute que son bandeau. L'autorisation
 * (Android 13 et plus) est demandée à l'opt-in, jamais au démarrage.
 */
class AndroidNotifier(private val context: Context) : ChronoNotifier {
    private val mutable = MutableStateFlow(read())
    override val state: StateFlow<NotifyState> = mutable
    override val systemHandlesAlerts: Boolean get() = state.value == NotifyState.ACTIVE

    /** Dernière réponse à la demande d'autorisation (un refus reste un refus pour la session). */
    private var refused = false

    fun refresh() {
        mutable.value = read()
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

    override suspend fun requestPermission() {
        if (Build.VERSION.SDK_INT < 33) return refresh()
        val activity = KairosProcess.activity ?: return
        refused = !activity.requestNotificationPermission()
        refresh()
    }

    override fun beep() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 500)
        }
    }
}
