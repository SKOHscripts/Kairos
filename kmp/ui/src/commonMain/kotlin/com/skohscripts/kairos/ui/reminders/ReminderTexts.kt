package com.skohscripts.kairos.ui.reminders

import com.skohscripts.kairos.ui.generated.resources.day_brief_postpone_plain
import com.skohscripts.kairos.ui.generated.resources.day_brief_postpone
import com.skohscripts.kairos.ui.generated.resources.day_brief_deep_work
import com.skohscripts.kairos.ui.generated.resources.day_brief_fits_plain
import com.skohscripts.kairos.ui.generated.resources.day_brief_fits
import com.skohscripts.kairos.ui.generated.resources.day_brief_available_effective
import com.skohscripts.kairos.ui.generated.resources.day_brief_available
import com.skohscripts.kairos.ui.generated.resources.reminder_summary_deadlines
import com.skohscripts.kairos.ui.generated.resources.reminder_summary_estimate
import com.skohscripts.kairos.ui.day.durationText
import com.skohscripts.kairos.core.day.DayBrief
import com.skohscripts.kairos.core.reminders.DailySummary
import com.skohscripts.kairos.core.reminders.Reminder
import com.skohscripts.kairos.core.reminders.ReminderKind
import com.skohscripts.kairos.ui.chrono.ChronoTexts
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.notif_channel_reminders
import com.skohscripts.kairos.ui.generated.resources.reminder_clock
import com.skohscripts.kairos.ui.generated.resources.reminder_fixed_body
import com.skohscripts.kairos.ui.generated.resources.reminder_fixed_now_body
import com.skohscripts.kairos.ui.generated.resources.reminder_next_body
import com.skohscripts.kairos.ui.generated.resources.reminder_summary_line
import com.skohscripts.kairos.ui.generated.resources.reminder_summary_more
import com.skohscripts.kairos.ui.generated.resources.reminder_summary_slots_many
import com.skohscripts.kairos.ui.generated.resources.reminder_summary_slots_one
import com.skohscripts.kairos.ui.generated.resources.reminder_summary_tasks_many
import com.skohscripts.kairos.ui.generated.resources.reminder_summary_tasks_one
import com.skohscripts.kairos.ui.generated.resources.reminder_summary_title
import com.skohscripts.kairos.ui.generated.resources.reminder_summary_untimed_many
import com.skohscripts.kairos.ui.generated.resources.reminder_summary_untimed_one
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.getString

/**
 * Textes des rappels hors composition (docs/spec/rappels.md), dans la langue de l'appareil : pour la veille de l'application
 * et pour les alarmes Android, construites hors de l'interface.
 */
object ReminderTexts {
    /** « 14h00 », « 14:00 ». */
    suspend fun clock(t: LocalDateTime): String =
        getString(Res.string.reminder_clock, t.hour.toString().padStart(2, '0'), t.minute.toString().padStart(2, '0'))

    /** « Commence à 14h00 (dans 10 min) », « Commence à 14h00 », « C'est l'heure : prévue jusqu'à 11h30 ». */
    suspend fun body(reminder: Reminder): String = when (reminder.kind) {
        ReminderKind.FIXED -> {
            val lead = ((reminder.start.hour * 60 + reminder.start.minute) - (reminder.at.hour * 60 + reminder.at.minute)).let { if (it < 0) it + 24 * 60 else it }
            if (lead == 0) getString(Res.string.reminder_fixed_now_body, clock(reminder.start))
            else getString(Res.string.reminder_fixed_body, clock(reminder.start), ChronoTexts.duration(lead))
        }
        ReminderKind.NEXT -> getString(Res.string.reminder_next_body, clock(reminder.end))
    }

    suspend fun channel(): String = getString(Res.string.notif_channel_reminders)

    /** Titre du récapitulatif du jour : « Ta journée ». */
    suspend fun summaryTitle(): String = getString(Res.string.reminder_summary_title)

    /**
     * « 5 tâches · ~4 h 30 · 2 créneaux · 2 échéances aujourd'hui » (docs/spec/ta-journee.md § Notification) : un compte nul est
     * omis.
     */
    suspend fun summaryCounts(summary: DailySummary): String = listOfNotNull(
        summary.tasks.takeIf { it > 0 }?.let { if (it == 1) getString(Res.string.reminder_summary_tasks_one) else getString(Res.string.reminder_summary_tasks_many, it) },
        summary.brief?.estimateMinutes?.takeIf { it > 0 }?.let { getString(Res.string.reminder_summary_estimate, durationText(it)) },
        summary.slots.takeIf { it > 0 }?.let { if (it == 1) getString(Res.string.reminder_summary_slots_one) else getString(Res.string.reminder_summary_slots_many, it) },
        summary.brief?.deadlinesToday?.takeIf { it > 0 }?.let { getString(Res.string.reminder_summary_deadlines, it) },
    ).joinToString(" · ")

    /** Les lignes « Disponible » et verdict de « Ta journée », pour la notification dépliée ; vide sans tâche. */
    suspend fun briefLines(brief: DayBrief): List<String> {
        if (brief.verdict == DayBrief.Verdict.EMPTY || brief.verdict == DayBrief.Verdict.DAY_OFF) return emptyList()
        val available = if (brief.interruptionMinutes > 0) {
            getString(Res.string.day_brief_available_effective, durationText(brief.availableMinutes), durationText(brief.interruptionMinutes), durationText(brief.effectiveMinutes))
        } else {
            getString(Res.string.day_brief_available, durationText(brief.availableMinutes))
        }
        val verdict = when (brief.verdict) {
            DayBrief.Verdict.FITS -> brief.dayEnd?.let { getString(Res.string.day_brief_fits, clock(it)) } ?: getString(Res.string.day_brief_fits_plain)
            DayBrief.Verdict.DEEP_WORK -> getString(Res.string.day_brief_deep_work, durationText(brief.overflowMinutes))
            DayBrief.Verdict.POSTPONE -> brief.postpone?.let { getString(Res.string.day_brief_postpone, durationText(brief.overflowMinutes), it.title) }
                ?: getString(Res.string.day_brief_postpone_plain, durationText(brief.overflowMinutes))
            else -> null
        }
        return listOfNotNull(available, verdict)
    }

    /**
     * Notification dépliée : le compte, puis les premières tâches avec leur heure (« 09h30 Point d'équipe »), « … » s'il y en a plus,
     * puis les tâches faites sans temps à compléter dans le Bilan, s'il y en a.
     */
    suspend fun summaryDetail(summary: DailySummary): String {
        val lines = listOf(summaryCounts(summary)) +
            summary.brief?.let { briefLines(it) }.orEmpty() +
            summary.first.map { getString(Res.string.reminder_summary_line, clock(it.start), it.title) } +
            listOfNotNull(if (summary.tasks > summary.first.size) getString(Res.string.reminder_summary_more) else null) +
            // Tâches faites sans temps à revoir (docs/spec/durees-reelles.md) : une ligne de plus, en dernier.
            listOfNotNull(
                summary.untimed.takeIf { it > 0 }?.let {
                    if (it == 1) getString(Res.string.reminder_summary_untimed_one) else getString(Res.string.reminder_summary_untimed_many, it)
                },
            )
        return lines.joinToString("\n")
    }
}
