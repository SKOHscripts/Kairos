package com.skohscripts.kairos.android.widgets

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.skohscripts.kairos.android.KairosProcess
import com.skohscripts.kairos.android.R
import kotlinx.coroutines.runBlocking

/**
 * Lignes du widget « Journée » (docs/spec/widgets-android.md § Rendu) : lit le dernier calcul de [WidgetSync] ; le refait si
 * le lanceur vient de démarrer le processus (rien n'est encore calculé). Chaque ligne ouvre la vue Jour.
 */
class DayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Rows(applicationContext)

    private class Rows(private val context: Context) : RemoteViewsFactory {
        private var rows: List<WidgetSync.RowView> = emptyList()

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            // Fil du lanceur (liaison), pas le fil principal : l'attente est permise ici.
            if (WidgetSync.dayRows == null) {
                runBlocking { runCatching { KairosProcess.services(context).await() }.onSuccess { WidgetSync.refresh(context) } }
            }
            rows = WidgetSync.dayRows.orEmpty()
        }

        override fun getCount(): Int = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_day_row)
            val row = rows.getOrNull(position) ?: return views
            views.setTextViewText(R.id.row_time, row.time)
            views.setTextColor(R.id.row_time, row.timeColor)
            views.setTextViewText(R.id.row_title, row.title)
            views.setTextColor(R.id.row_title, row.titleColor)
            views.setTextViewText(R.id.row_tag, row.tag)
            views.setTextColor(R.id.row_tag, row.tagColor)
            views.setViewVisibility(R.id.row_tag, if (row.tag.isEmpty()) View.GONE else View.VISIBLE)
            val highlight = row.highlight
            views.setViewVisibility(R.id.row_bg, if (highlight != null) View.VISIBLE else View.GONE)
            if (highlight != null) views.setInt(R.id.row_bg, "setColorFilter", highlight)
            // Le gabarit du widget (vue Jour) suffit : la ligne n'ajoute rien.
            views.setOnClickFillInIntent(R.id.row, Intent())
            return views
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = false

        override fun onDestroy() = Unit
    }
}
