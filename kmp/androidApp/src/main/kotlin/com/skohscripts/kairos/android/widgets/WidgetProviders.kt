package com.skohscripts.kairos.android.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.skohscripts.kairos.android.KairosProcess
import kotlinx.coroutines.launch

/**
 * Point d'entrée commun des deux widgets : un widget placé, redimensionné, ou l'application mise à jour ou le téléphone
 * redémarré (`onUpdate`) redessine tout depuis la base (docs/spec/widgets-android.md § Rendu et synchronisation).
 */
abstract class KairosWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = redraw(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = redraw(context)

    private fun redraw(context: Context) {
        val app = context.applicationContext
        val pending = goAsync()
        KairosProcess.scope.launch {
            try {
                runCatching { KairosProcess.services(app).await() }
                    .onSuccess { WidgetSync.refresh(app) }
                    .onFailure { WidgetSync.renderUnavailable(app) }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Widget « Maintenant » : la carte « Maintenant » de la vue Jour et ses boutons. */
class NowWidgetProvider : KairosWidgetProvider()

/** Widget « Journée » : la liste du jour ([DayWidgetService]). */
class DayWidgetProvider : KairosWidgetProvider()
