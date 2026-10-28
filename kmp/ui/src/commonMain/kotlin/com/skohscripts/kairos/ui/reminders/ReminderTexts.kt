package com.skohscripts.kairos.ui.reminders

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

    /** « 5 tâches · 2 créneaux », « 1 tâche », « 1 créneau » : un compte nul est omis. */
    suspend fun summaryCounts(summary: DailySummary): String = listOfNotNull(
        summary.tasks.takeIf { it > 0 }?.let { if (it == 1) getString(Res.string.reminder_summary_tasks_one) else getString(Res.string.reminder_summary_tasks_many, it) },
        summary.slots.takeIf { it > 0 }?.let { if (it == 1) getString(Res.string.reminder_summary_slots_one) else getString(Res.string.reminder_summary_slots_many, it) },
    ).joinToString(" · ")

    /** Notification dépliée : le compte, puis les premières tâches avec leur heure (« 09h30 Point d'équipe »), « … » s'il y en a plus. */
    suspend fun summaryDetail(summary: DailySummary): String {
        val lines = listOf(summaryCounts(summary)) +
            summary.first.map { getString(Res.string.reminder_summary_line, clock(it.start), it.title) } +
            listOfNotNull(if (summary.tasks > summary.first.size) getString(Res.string.reminder_summary_more) else null)
        return lines.joinToString("\n")
    }
}
