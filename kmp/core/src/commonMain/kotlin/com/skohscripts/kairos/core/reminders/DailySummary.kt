package com.skohscripts.kairos.core.reminders

import com.skohscripts.kairos.core.day.DayView
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.ReminderSettings
import com.skohscripts.kairos.core.settings.SettingsForm
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime

/** Une tâche du récapitulatif : son titre et son heure de début prévue. */
data class SummaryTask(val title: String, val start: LocalDateTime)

/**
 * Récapitulatif du jour (docs/spec/rappels.md § Récapitulatif du jour, issue #151) : à envoyer à [at], [tasks] tâches placées et
 * [slots] créneaux occupés, dont les [first] premières tâches du plan (trois au plus). [key] : une seule fois par jour.
 */
data class DailySummary(val at: LocalDateTime, val tasks: Int, val slots: Int, val first: List<SummaryTask>) {
    val key: String get() = "summary-${at.date}"

    companion object {
        /** Tâches citées dans la notification dépliée ; au-delà, « … ». */
        const val SHOWN = 3

        /**
         * Le récapitulatif de [day] tel que le plan le prévoit à l'heure choisie ; `null` si l'option est désactivée (ou jamais
         * réglée), si l'heure est déjà passée à [now] (rien n'est rattrapé ; `null` = début du jour) ou si le jour n'a ni tâche
         * placée ni créneau occupé (une notification vide apprend à l'ignorer). Même `DayView` que les rappels : une tâche faite,
         * bloquée ou programmée un autre jour n'y est pas.
         */
        fun plan(snapshot: KairosSnapshot, day: LocalDate, now: LocalDateTime?, timeZone: TimeZone): DailySummary? {
            val settings = snapshot.settings.reminders ?: return null
            if (!settings.dailySummary) return null
            val time = SettingsForm.parseTime(settings.dailySummaryTime)
                ?: SettingsForm.parseTime(ReminderSettings.DEFAULT_SUMMARY_TIME)!!
            val at = day.atTime(time)
            if (now != null && now >= at) return null
            // Le plan tel qu'il sera à l'heure du récapitulatif : placé à partir de cette heure.
            val view = DayView.build(snapshot, day, at, timeZone)
            val scheduled = view.schedule.scheduled
            val slots = view.dayBlocks.count { it.kind == BlockKind.BUSY }
            if (scheduled.isEmpty() && slots == 0) return null
            return DailySummary(at, scheduled.size, slots, scheduled.take(SHOWN).map { SummaryTask(it.task.title, it.start) })
        }
    }
}
