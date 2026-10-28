package com.skohscripts.kairos.core.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Rappels (docs/spec/rappels.md, issue #103), sous-objet [Settings.reminders] : `null` tant que rien n'est réglé, ce qui veut
 * dire les valeurs ci-dessous, **désactivés** (même règle que [Settings.learning] : sauver le formulaire sans y toucher ne
 * crée jamais cet objet, l'export d'une base qui ne s'en sert pas ne change pas).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ReminderSettings(
    /** Rappeler les tâches à heure fixe. */
    val enabled: Boolean = false,
    /** Minutes avant le début de la tâche à heure fixe, de 0 (à l'heure même) à [MAX_LEAD]. */
    val leadMinutes: Int = 10,
    /** Signaler aussi la tâche suivante du plan, à son début prévu (ne sert que si [enabled]). */
    val nextTask: Boolean = false,
    /**
     * Récapitulatif du jour (3.12.0, issue #151) : une notification « Ta journée » à [dailySummaryTime], indépendante de [enabled].
     * Absent du JSON à sa valeur par défaut : l'export d'une base qui ne s'en sert pas ne change pas.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dailySummary: Boolean = false,
    /** Heure du récapitulatif, « HH:MM » (00:00 à 23:59), validée par le formulaire. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dailySummaryTime: String = DEFAULT_SUMMARY_TIME,
) {
    companion object {
        const val MAX_LEAD = 120
        const val DEFAULT_SUMMARY_TIME = "08:30"
    }
}
