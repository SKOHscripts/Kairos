package com.skohscripts.kairos.core.model

import kotlinx.serialization.Serializable

/**
 * Réglages de Kairos (docs/spec/modele-donnees.md § Réglages) : ceux de
 * Kairos 2 que la v3 garde (plan § 2.3), mêmes noms (en camelCase) et mêmes
 * valeurs par défaut. Stockés en JSON dans la base ; un champ absent du JSON
 * prend sa valeur par défaut (ajout de réglage sans migration).
 * Bornes, validation par champ et écran : `settings/SettingsForm`
 * (docs/spec/reglages.md).
 */
@Serializable
data class Settings(
    val defaultTaskDurationMinutes: Int = 30,
    val meetingBufferMinutes: Int = 5,
    val workdayStartHour: Int = 9,
    val workdayEndHour: Int = 18,
    val staleOverdueDays: Int = 7,
    val staleUntouchedDays: Int = 14,
    val priorityOverloadThreshold: Int = 5,
    val priorityValueBase: Double = 4.0,
    val urgencyHorizonDays: Int = 14,
    val urgencyPeak: Double = 8.0,
    val defaultFibonacciPoints: Int = 3,
    val cognitiveDipEnabled: Boolean = true,
    val cognitiveDipStartHour: Int = 13,
    val cognitiveDipTroughHour: Int = 15,
    val cognitiveDipEndHour: Int = 16,
    val cognitiveDipPenalty: Double = 1.0,
    val statsWindowWeeks: Int = 8,
    val timerIdleAlertMinutes: Int = 180,
    val pomodoroFocusMinutes: Int = 50,
    val timerAlertSound: Boolean = false,
    val holidaysFr: Boolean = true,
    val extraHolidays: String = "",
    /** Types proposés dans la fiche, séparés par des virgules (libellé = valeur stockée). */
    val taskTypes: String = DEFAULT_TASK_TYPES_FR,
    /** Vérification des nouvelles versions, bureau seulement (docs/spec/mises-a-jour.md). */
    val updateCheckEnabled: Boolean = true,
) {
    /** Types de tâche, dans l'ordre, sans vides. */
    val taskTypeList: List<String> get() = taskTypes.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        /** Types par défaut de Kairos 2, moins « Pilotage/dette technique » (périmètre retiré). */
        const val DEFAULT_TASK_TYPES_FR =
            "Développement,Revue de code,Réunion,Documentation,Administratif,Veille/formation"
        const val DEFAULT_TASK_TYPES_EN =
            "Development,Code review,Meeting,Documentation,Administration,Learning"

        /** Réglages d'une base neuve, types de tâche dans la langue de l'interface. */
        fun defaults(language: String): Settings =
            Settings(taskTypes = if (language.lowercase().startsWith("en")) DEFAULT_TASK_TYPES_EN else DEFAULT_TASK_TYPES_FR)
    }
}
