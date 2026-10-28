package com.skohscripts.kairos.ui.widgets

import com.skohscripts.kairos.core.model.LevelNames
import com.skohscripts.kairos.core.widgets.WidgetTask
import com.skohscripts.kairos.ui.chrono.ChronoTexts
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_done_labelled
import com.skohscripts.kairos.ui.generated.resources.action_timer_start
import com.skohscripts.kairos.ui.generated.resources.action_timer_stop
import com.skohscripts.kairos.ui.generated.resources.capture_title
import com.skohscripts.kairos.ui.generated.resources.tag_deepwork
import com.skohscripts.kairos.ui.generated.resources.widget_all_done
import com.skohscripts.kairos.ui.generated.resources.widget_confirm
import com.skohscripts.kairos.ui.generated.resources.widget_deadline
import com.skohscripts.kairos.ui.generated.resources.widget_done_count
import com.skohscripts.kairos.ui.generated.resources.widget_estimate
import com.skohscripts.kairos.ui.generated.resources.widget_inbox
import com.skohscripts.kairos.ui.generated.resources.widget_next
import com.skohscripts.kairos.ui.generated.resources.widget_no_slot
import com.skohscripts.kairos.ui.generated.resources.widget_nothing_planned
import com.skohscripts.kairos.ui.generated.resources.widget_open_app
import com.skohscripts.kairos.ui.generated.resources.widget_time_span
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.getString

/**
 * Textes des widgets Android (docs/spec/widgets-android.md § Textes), rédigés hors composition comme
 * [com.skohscripts.kairos.ui.reminders.ReminderTexts] : la langue est celle posée par `applyLanguage` avant l'appel.
 * [language] (« fr », « en »…) ne sert qu'aux heures et aux dates, formatées comme dans l'application.
 */
object WidgetTexts {
    suspend fun nothingPlanned(): String = getString(Res.string.widget_nothing_planned)

    suspend fun allDone(): String = getString(Res.string.widget_all_done)

    suspend fun done(): String = getString(Res.string.action_done_labelled)

    suspend fun confirm(): String = getString(Res.string.widget_confirm)

    suspend fun timerStart(): String = getString(Res.string.action_timer_start)

    suspend fun timerStop(): String = getString(Res.string.action_timer_stop)

    suspend fun capture(): String = getString(Res.string.capture_title)

    suspend fun openApp(): String = getString(Res.string.widget_open_app)

    suspend fun deepWork(): String = getString(Res.string.tag_deepwork)

    suspend fun deadline(): String = getString(Res.string.widget_deadline)

    /** « À traiter : 3 ». */
    suspend fun inbox(count: Int): String = getString(Res.string.widget_inbox, count)

    /** « 3 / 7 faites ». */
    suspend fun doneCount(done: Int, total: Int): String = getString(Res.string.widget_done_count, done, total)

    /** « / 45 min » après le chronomètre. */
    suspend fun estimate(minutes: Int): String = getString(Res.string.widget_estimate, ChronoTexts.duration(minutes))

    /** « 10h30–11h15 », « 10:30 » sans fin connue, « sans créneau » sans heure. */
    suspend fun span(start: LocalDateTime?, end: LocalDateTime?, language: String): String = when {
        start == null -> getString(Res.string.widget_no_slot)
        end == null -> Dates.time(start, language)
        else -> getString(Res.string.widget_time_span, Dates.time(start, language), Dates.time(end, language))
    }

    /** « Ensuite 11h30 · Relire le contrat ». */
    suspend fun next(task: WidgetTask, language: String): String =
        getString(Res.string.widget_next, task.start?.let { Dates.time(it, language) } ?: getString(Res.string.widget_no_slot), task.title)

    /** En-tête de « Journée » : « mardi 27 oct. », « Tuesday Oct 27 ». */
    fun header(day: LocalDate, language: String): String = "${Dates.weekdayName(day, language)} ${Dates.short(day, language)}"

    fun time(t: LocalDateTime, language: String): String = Dates.time(t, language)

    /** Pastille de priorité : le nom choisi (docs/spec/planning-personnalise.md § Lot D), sinon « P0 ». */
    fun priority(p: Int, names: LevelNames): String = names.priority[p] ?: "P$p"
}
