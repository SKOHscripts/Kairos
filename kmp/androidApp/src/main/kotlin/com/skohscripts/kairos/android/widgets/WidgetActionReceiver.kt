package com.skohscripts.kairos.android.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.skohscripts.kairos.android.KairosProcess
import kotlinx.coroutines.launch

/**
 * Boutons et alarmes des widgets (docs/spec/widgets-android.md § Actions) : « Fait » (double toucher), chrono, frontière du
 * plan, fin du délai de confirmation. Fonctionne application fermée : la base est ouverte par [KairosProcess] si besoin ;
 * aucune action n'ouvre l'application.
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        KairosProcess.scope.launch {
            try {
                runCatching { KairosProcess.services(app).await() }.onFailure {
                    WidgetSync.renderUnavailable(app)
                    return@launch
                }
                val taskId = intent.getLongExtra(EXTRA_TASK, -1L)
                when (intent.action) {
                    ACTION_DONE -> if (taskId >= 0) WidgetSync.onDone(app, taskId, KairosProcess.scope)
                    ACTION_TIMER -> if (taskId >= 0) WidgetSync.onTimer(taskId)
                    ACTION_TICK -> WidgetSync.refresh(app)
                    ACTION_EXPIRE -> WidgetSync.expire(app)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.skohscripts.kairos.WIDGET_DONE"
        const val ACTION_TIMER = "com.skohscripts.kairos.WIDGET_TIMER"
        const val ACTION_TICK = "com.skohscripts.kairos.WIDGET_TICK"
        const val ACTION_EXPIRE = "com.skohscripts.kairos.WIDGET_EXPIRE"
        const val EXTRA_TASK = "taskId"
    }
}
