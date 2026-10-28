package com.skohscripts.kairos.android.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import com.skohscripts.kairos.android.MainActivity
import com.skohscripts.kairos.android.R
import com.skohscripts.kairos.core.model.LevelNames
import com.skohscripts.kairos.core.settings.UiLanguage
import com.skohscripts.kairos.core.widgets.ConfirmState
import com.skohscripts.kairos.core.widgets.DayRow
import com.skohscripts.kairos.core.widgets.DayRowKind
import com.skohscripts.kairos.core.widgets.DayRowState
import com.skohscripts.kairos.core.widgets.DayWidget
import com.skohscripts.kairos.core.widgets.NowState
import com.skohscripts.kairos.core.widgets.NowWidget
import com.skohscripts.kairos.core.widgets.WidgetPlan
import com.skohscripts.kairos.core.widgets.WidgetTask
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.widgets.WidgetTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Les deux widgets de l'écran d'accueil (docs/spec/widgets-android.md) : recalcule leur contenu ([WidgetPlan]) à chaque
 * changement de la vue Perso, et ne redessine que si ce contenu, les couleurs ou la langue ont changé ; pose une alarme
 * inexacte à la prochaine frontière du plan ([WidgetPlan.nextChange]) au lieu d'un réveil périodique. Le chrono du widget
 * compte seul (vue `Chronometer`). Tout passe par ce processus : une seule base ouverte ([com.skohscripts.kairos.android.KairosProcess]).
 */
object WidgetSync {
    /** Hauteur (dp) en dessous de laquelle « Maintenant » prend sa disposition compacte (une seule ligne de haut). */
    private const val COMPACT_MAX_DP = 100
    private const val TICK_REQUEST = 3_000
    private const val EXPIRE_REQUEST = 3_001

    /** Une ligne de « Journée » déjà rédigée et colorée, lue par [DayWidgetService]. */
    internal class RowView(
        val time: CharSequence,
        val title: CharSequence,
        val tag: CharSequence,
        val timeColor: Int,
        val titleColor: Int,
        val tagColor: Int,
        val highlight: Int?,
    )

    private class Armed(val taskId: Long, val at: Instant)

    @Volatile private var services: AppServices? = null
    private val mutex = Mutex()
    private var lastKey: Any? = null

    /** Lignes du dernier calcul de « Journée » ; `null` tant qu'aucun calcul n'a eu lieu dans ce processus. */
    @Volatile internal var dayRows: List<RowView>? = null
        private set

    /** Premier toucher sur « Fait » : en mémoire du processus seulement (s'il meurt, le toucher suivant est un premier toucher). */
    @Volatile private var armed: Armed? = null

    fun start(context: Context, services: AppServices, scope: CoroutineScope) {
        this.services = services
        val app = context.applicationContext
        scope.launch { services.repository.personalSnapshot.collect { refresh(app, force = false) } }
    }

    /** Le processus a ouvert la base : un changement de configuration (mode nuit, langue du système) peut redessiner. */
    val started: Boolean get() = services != null

    /**
     * Recalcule et redessine les widgets placés ; [force] redessine même si rien n'a changé (nouveau widget, taille changée,
     * frontière du plan). Sans widget placé, ne fait rien et retire l'alarme.
     */
    suspend fun refresh(context: Context, force: Boolean = true): Unit = mutex.withLock {
        val s = services ?: return@withLock
        val manager = AppWidgetManager.getInstance(context)
        val nowIds = manager.getAppWidgetIds(ComponentName(context, NowWidgetProvider::class.java))
        val dayIds = manager.getAppWidgetIds(ComponentName(context, DayWidgetProvider::class.java))
        if (nowIds.isEmpty() && dayIds.isEmpty()) {
            alarms(context).cancel(tickIntent(context))
            lastKey = null
            return@withLock
        }
        val snapshot = s.repository.personalSnapshot.value
        val settings = snapshot.settings
        val timeZone = TimeZone.currentSystemDefault()
        val instant = s.clock.now()
        val now = instant.toLocalDateTime(timeZone)
        val language = UiLanguage.resolve(settings.language, s.systemLanguage ?: Locale.getDefault().language)
        // Processus ouvert par une alarme ou le lanceur, sans activité : la langue choisie n'est pas encore posée.
        s.applyLanguage(language)
        val colors = WidgetColors.of(context, settings)
        val nowWidget = WidgetPlan.now(snapshot, now, timeZone)
        val dayWidget = WidgetPlan.day(snapshot, now, timeZone)
        val armedTask = armed?.takeIf { WidgetPlan.confirm(it.at, instant) == ConfirmState.ARMED }?.taskId
        scheduleTick(context, WidgetPlan.nextChange(snapshot, now, timeZone), timeZone)
        val names = settings.levelNames ?: LevelNames()
        val key = listOf(nowWidget, dayWidget, colors, language, names, armedTask, nowIds.toList(), dayIds.toList())
        if (!force && key == lastKey) return@withLock
        lastKey = key
        for (id in nowIds) {
            val minHeight = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val compact = minHeight in 1 until COMPACT_MAX_DP
            manager.updateAppWidget(id, nowViews(context, nowWidget, compact, colors, language, names, armedTask, instant))
        }
        if (dayIds.isNotEmpty()) {
            dayRows = rows(dayWidget, colors, language, names)
            val scrollTo = scrollPosition(dayWidget.rows)
            for (id in dayIds) manager.updateAppWidget(id, dayViews(context, id, dayWidget, colors, language, scrollTo))
            manager.notifyAppWidgetViewDataChanged(dayIds, R.id.day_list)
        }
    }

    /** La base ne s'ouvre pas : chaque widget dit « Ouvrir Kairos », un toucher ouvre l'application (qui affiche l'erreur). */
    suspend fun renderUnavailable(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val text = WidgetTexts.openApp()
        for (provider in listOf(NowWidgetProvider::class.java, DayWidgetProvider::class.java)) {
            for (id in manager.getAppWidgetIds(ComponentName(context, provider))) {
                val views = RemoteViews(context.packageName, R.layout.widget_now_compact)
                views.setTextViewText(R.id.now_title, text)
                views.setViewVisibility(R.id.now_time, View.GONE)
                for (button in listOf(R.id.now_done, R.id.now_timer, R.id.now_add)) views.setViewVisibility(button, View.GONE)
                views.setOnClickPendingIntent(R.id.widget_body, openApp(context, Destination.DAY))
                manager.updateAppWidget(id, views)
            }
        }
    }

    /**
     * « Fait » : le premier toucher arme le bouton (« Confirmer ? » pendant [WidgetPlan.CONFIRM_SECONDS] s), le second dans ce
     * délai termine la tâche, seulement si elle est encore à faire (un broadcast tardif ne rouvre jamais une tâche faite).
     */
    suspend fun onDone(context: Context, taskId: Long, scope: CoroutineScope) {
        val s = services ?: return
        val now = s.clock.now()
        val current = armed
        if (current != null && current.taskId == taskId && WidgetPlan.confirm(current.at, now) == ConfirmState.ARMED) {
            armed = null
            alarms(context).cancel(expireIntent(context))
            if (WidgetPlan.canComplete(s.repository.personalSnapshot.value, taskId)) s.repository.toggleDone(taskId)
            refresh(context)
            return
        }
        armed = Armed(taskId, now)
        refresh(context)
        // Désarmement : dans ce processus après le délai ; l'alarme reprend la main s'il meurt avant.
        alarms(context).set(AlarmManager.RTC, (now + WidgetPlan.CONFIRM_SECONDS.seconds).toEpochMilliseconds(), expireIntent(context))
        scope.launch {
            delay(WidgetPlan.CONFIRM_SECONDS.seconds)
            expire(context)
        }
    }

    /** Fin du délai de confirmation : « Fait » redevient « Fait » si le second toucher n'est pas venu. */
    suspend fun expire(context: Context) {
        val s = services ?: return
        val current = armed ?: return
        if (WidgetPlan.confirm(current.at, s.clock.now()) == ConfirmState.ARMED) return
        armed = null
        refresh(context)
    }

    /** Chrono : arrête celui de la tâche affichée s'il tourne, sinon le lance sur elle (une seule session ouverte à la fois). */
    suspend fun onTimer(taskId: Long) {
        val repository = services?.repository ?: return
        val running = repository.personalSnapshot.value.workSessions.firstOrNull { it.endedAt == null }
        if (running?.taskId == taskId) repository.stopTimer() else repository.startTimer(taskId)
    }

    private suspend fun nowViews(
        context: Context,
        widget: NowWidget,
        compact: Boolean,
        colors: WidgetColors,
        language: String,
        names: LevelNames,
        armedTask: Long?,
        instant: Instant,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, if (compact) R.layout.widget_now_compact else R.layout.widget_now)
        val ink = colors.onPrimaryContainer
        views.setInt(R.id.widget_bg, "setColorFilter", colors.primaryContainer)
        views.setOnClickPendingIntent(R.id.widget_body, openApp(context, Destination.DAY))
        for (id in listOf(R.id.now_title, R.id.now_time, R.id.now_chrono, R.id.now_next, R.id.now_inbox)) views.setTextColor(id, ink)
        // Pastilles rondes du chrono et de la capture : surface sur le conteneur primaire.
        for (bg in listOf(R.id.now_timer_bg, R.id.now_add_bg)) views.setInt(bg, "setColorFilter", colors.surface)
        for (icon in listOf(R.id.now_timer_icon, R.id.now_add_icon)) views.setInt(icon, "setColorFilter", colors.onSurface)
        views.setInt(R.id.now_done_bg, "setColorFilter", colors.primary)
        views.setInt(R.id.now_done_icon, "setColorFilter", colors.onPrimary)
        views.setTextColor(R.id.now_done_text, colors.onPrimary)
        views.setOnClickPendingIntent(R.id.now_add, openApp(context, Destination.NOTES))
        views.setContentDescription(R.id.now_add, WidgetTexts.capture())
        if (!compact) {
            if (widget.inbox > 0) {
                views.setViewVisibility(R.id.now_inbox, View.VISIBLE)
                views.setTextViewText(R.id.now_inbox, WidgetTexts.inbox(widget.inbox))
                views.setOnClickPendingIntent(R.id.now_inbox, openApp(context, Destination.DAY))
            } else {
                views.setViewVisibility(R.id.now_inbox, View.GONE)
            }
        }
        val task = widget.task
        if (task == null) {
            views.setTextViewText(R.id.now_title, if (widget.state == NowState.ALL_DONE) WidgetTexts.allDone() else WidgetTexts.nothingPlanned())
            for (id in listOf(R.id.now_time, R.id.now_chrono, R.id.now_next, R.id.now_done, R.id.now_timer)) views.setViewVisibility(id, View.GONE)
            views.setChronometer(R.id.now_chrono, SystemClock.elapsedRealtime(), null, false)
            if (!compact) views.setTextViewText(R.id.now_badges, "")
            return views
        }
        views.setTextViewText(R.id.now_title, task.title)
        if (!compact) views.setTextViewText(R.id.now_badges, badges(task, colors, names))
        val running = widget.state == NowState.RUNNING && widget.runningSince != null
        if (running) {
            // Base dans l'horloge de démarrage du téléphone : le lanceur fait tourner le chronomètre sans réveiller Kairos.
            val elapsed = (instant - widget.runningSince!!).inWholeMilliseconds + widget.spentBeforeMinutes * 60_000L
            views.setViewVisibility(R.id.now_chrono, View.VISIBLE)
            views.setChronometer(R.id.now_chrono, SystemClock.elapsedRealtime() - elapsed, null, true)
            val estimate = task.estimateMinutes
            views.setViewVisibility(R.id.now_time, if (estimate != null) View.VISIBLE else View.GONE)
            if (estimate != null) views.setTextViewText(R.id.now_time, WidgetTexts.estimate(estimate))
        } else {
            views.setChronometer(R.id.now_chrono, SystemClock.elapsedRealtime(), null, false)
            views.setViewVisibility(R.id.now_chrono, View.GONE)
            views.setViewVisibility(R.id.now_time, View.VISIBLE)
            views.setTextViewText(R.id.now_time, WidgetTexts.span(task.start, task.end, language))
        }
        if (!compact) {
            val next = widget.next
            views.setViewVisibility(R.id.now_next, if (next != null) View.VISIBLE else View.GONE)
            if (next != null) views.setTextViewText(R.id.now_next, WidgetTexts.next(next, language))
        }
        val isArmed = armedTask == task.id
        views.setViewVisibility(R.id.now_done, View.VISIBLE)
        views.setTextViewText(R.id.now_done_text, if (isArmed) WidgetTexts.confirm() else WidgetTexts.done())
        // Compacte : l'icône seule, le texte n'apparaît qu'armé (« Confirmer ? »).
        views.setViewVisibility(R.id.now_done_text, if (!compact || isArmed) View.VISIBLE else View.GONE)
        views.setContentDescription(R.id.now_done, if (isArmed) WidgetTexts.confirm() else WidgetTexts.done())
        views.setOnClickPendingIntent(R.id.now_done, action(context, WidgetActionReceiver.ACTION_DONE, task.id))
        views.setViewVisibility(R.id.now_timer, View.VISIBLE)
        views.setImageViewResource(R.id.now_timer_icon, if (running) R.drawable.ic_widget_stop else R.drawable.ic_widget_play)
        views.setContentDescription(R.id.now_timer, if (running) WidgetTexts.timerStop() else WidgetTexts.timerStart())
        views.setOnClickPendingIntent(R.id.now_timer, action(context, WidgetActionReceiver.ACTION_TIMER, task.id))
        return views
    }

    /** « P0 · projet · type · deep work » : P0 en rouge, deep work en tertiaire, le reste neutre. */
    private suspend fun badges(task: WidgetTask, colors: WidgetColors, names: LevelNames): CharSequence {
        val text = SpannableStringBuilder()
        fun add(part: String, color: Int?, bold: Boolean = false) {
            if (part.isEmpty()) return
            if (text.isNotEmpty()) text.append(" · ")
            val start = text.length
            text.append(part)
            color?.let { text.setSpan(ForegroundColorSpan(it), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            if (bold) text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        task.priority?.let { add(WidgetTexts.priority(it, names), if (it == 0) colors.error else null, bold = true) }
        add(task.project, null)
        add(task.type, null)
        if (task.deepWork) add(WidgetTexts.deepWork(), colors.tertiary)
        return text
    }

    @Suppress("DEPRECATION")
    private suspend fun dayViews(context: Context, id: Int, widget: DayWidget, colors: WidgetColors, language: String, scrollTo: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day)
        views.setInt(R.id.widget_bg, "setColorFilter", colors.surface)
        views.setTextViewText(R.id.day_date, WidgetTexts.header(widget.day, language))
        views.setTextColor(R.id.day_date, colors.onSurface)
        views.setTextViewText(R.id.day_count, WidgetTexts.doneCount(widget.doneCount, widget.total))
        views.setTextColor(R.id.day_count, colors.onSurfaceVariant)
        views.setInt(R.id.day_add_bg, "setColorFilter", colors.surfaceContainerHigh)
        views.setInt(R.id.day_add_icon, "setColorFilter", colors.onSurface)
        views.setContentDescription(R.id.day_add, WidgetTexts.capture())
        views.setOnClickPendingIntent(R.id.day_add, openApp(context, Destination.NOTES))
        views.setOnClickPendingIntent(R.id.day_header, openApp(context, Destination.DAY))
        views.setTextViewText(R.id.day_empty, WidgetTexts.nothingPlanned())
        views.setTextColor(R.id.day_empty, colors.onSurfaceVariant)
        views.setOnClickPendingIntent(R.id.day_empty, openApp(context, Destination.DAY))
        // Une adresse par widget : le lanceur garde une fabrique de lignes par instance.
        val adapter = Intent(context, DayWidgetService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .setData(Uri.parse("kairos://widget/day/$id"))
        views.setRemoteAdapter(R.id.day_list, adapter)
        views.setEmptyView(R.id.day_list, R.id.day_empty)
        // Gabarit des touchers sur les lignes : modifiable (le système y ajoute l'intention de la ligne), composant explicite.
        views.setPendingIntentTemplate(
            R.id.day_list,
            PendingIntent.getActivity(context, 0, openIntent(context, Destination.DAY), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE),
        )
        if (scrollTo > 0) views.setScrollPosition(R.id.day_list, scrollTo)
        return views
    }

    /**
     * Ouverture de la liste : sur la ligne en cours (le passé reste au-dessus, jamais caché) ; en haut s'il y a des échéances
     * sans créneau, qui priment.
     */
    private fun scrollPosition(rows: List<DayRow>): Int {
        if (rows.any { it.kind == DayRowKind.DEADLINE }) return 0
        return rows.indexOfFirst { it.state == DayRowState.CURRENT }.takeIf { it >= 0 }
            ?: rows.indexOfFirst { it.state == DayRowState.UPCOMING }.coerceAtLeast(0)
    }

    private suspend fun rows(widget: DayWidget, colors: WidgetColors, language: String, names: LevelNames): List<RowView> = widget.rows.map { row ->
        val current = row.state == DayRowState.CURRENT
        val base = when {
            current -> colors.onPrimaryContainer
            row.state == DayRowState.DONE || row.kind == DayRowKind.BUSY -> colors.onSurfaceVariant
            else -> colors.onSurface
        }
        val ink = if (row.state == DayRowState.PAST) WidgetColors.faded(base) else base
        val muted = when {
            current -> colors.onPrimaryContainer
            row.state == DayRowState.PAST -> WidgetColors.faded(colors.onSurfaceVariant)
            else -> colors.onSurfaceVariant
        }
        val time: CharSequence = when (row.kind) {
            DayRowKind.DEADLINE -> ""
            DayRowKind.TASK -> row.start?.let { WidgetTexts.time(it, language) }.orEmpty()
            DayRowKind.BUSY, DayRowKind.DEEPWORK -> WidgetTexts.span(row.start, row.end, language)
        }
        val (tag, tagColor) = when {
            row.kind == DayRowKind.DEADLINE -> WidgetTexts.deadline() to (if (row.overdue) colors.error else muted)
            row.kind == DayRowKind.DEEPWORK -> WidgetTexts.deepWork() to colors.tertiary
            row.state == DayRowState.DONE -> "" to muted
            row.priority != null -> WidgetTexts.priority(row.priority!!, names) to (if (row.priority == 0) colors.error else muted)
            else -> "" to muted
        }
        val title: CharSequence = if (row.state == DayRowState.DONE) {
            SpannableStringBuilder(row.title).apply { setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        } else {
            row.title
        }
        RowView(time, title, tag, muted, ink, tagColor, if (current) colors.primaryContainer else null)
    }

    private fun scheduleTick(context: Context, at: LocalDateTime, timeZone: TimeZone) {
        // Alarme non réveillante : écran éteint, le widget n'est pas vu ; elle part au réveil suivant. Une seule, remplacée.
        alarms(context).set(AlarmManager.RTC, at.toInstant(timeZone).toEpochMilliseconds(), tickIntent(context))
    }

    private fun alarms(context: Context): AlarmManager = context.getSystemService(AlarmManager::class.java)

    private fun tickIntent(context: Context): PendingIntent = broadcast(context, TICK_REQUEST, WidgetActionReceiver.ACTION_TICK, null)

    private fun expireIntent(context: Context): PendingIntent = broadcast(context, EXPIRE_REQUEST, WidgetActionReceiver.ACTION_EXPIRE, null)

    /** Action d'un bouton sur la tâche [taskId] ; l'adresse distingue les tâches (une intention par tâche et par action). */
    private fun action(context: Context, action: String, taskId: Long): PendingIntent =
        broadcast(context, action.hashCode(), action, taskId)

    private fun broadcast(context: Context, request: Int, action: String, taskId: Long?): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("kairos://widget/$action/${taskId ?: ""}"))
        taskId?.let { intent.putExtra(WidgetActionReceiver.EXTRA_TASK, it) }
        return PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openIntent(context: Context, destination: Destination): Intent =
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("kairos://open/${destination.name}"))
            .putExtra(MainActivity.EXTRA_DESTINATION, destination.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Toucher sur un widget : ouvre Kairos sur [destination], application déjà ouverte comprise (`onNewIntent`). */
    private fun openApp(context: Context, destination: Destination): PendingIntent =
        PendingIntent.getActivity(context, destination.ordinal + 1, openIntent(context, destination), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
