package com.skohscripts.kairos.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/**
 * Alarmes de seuil et action « Arrêter » de la notification permanente
 * (docs/spec/temps-reel-chrono.md § Android). Fonctionne application
 * fermée : la base est ouverte par [KairosProcess] si besoin.
 */
class ChronoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ALERT -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: return
                val body = intent.getStringExtra(EXTRA_BODY) ?: return
                ChronoSync.postAlert(context, intent.getIntExtra(EXTRA_KIND, 0), title, body)
            }
            ACTION_STOP -> {
                val pending = goAsync()
                KairosProcess.scope.launch {
                    try {
                        KairosProcess.services(context).await().repository.stopTimer()
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_ALERT = "com.skohscripts.kairos.CHRONO_ALERT"
        const val ACTION_STOP = "com.skohscripts.kairos.CHRONO_STOP"
        const val EXTRA_KIND = "kind"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
    }
}

/**
 * Redémarrage du téléphone ou mise à jour de l'application : les alarmes ont
 * été effacées par le système. Ouvrir la base suffit : [ChronoSync] remet la
 * notification permanente et les alarmes des seuils encore à venir.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        KairosProcess.scope.launch {
            try {
                KairosProcess.services(context).await()
            } finally {
                pending.finish()
            }
        }
    }
}
