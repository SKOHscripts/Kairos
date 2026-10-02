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
    /** Graine du thème `#RRGGBB`, ou [SYSTEM_THEME] (docs/spec/apparence.md). */
    val themeColor: String = DEFAULT_THEME_COLOR,
    /**
     * Réglages de l'espace Équipe (docs/spec/equipe.md). `null` tant que
     * l'espace n'a jamais été activé : un champ nul n'est pas écrit (export,
     * JSON en base), alors qu'un réglage d'équipe à plat serait écrit par
     * `encodeDefaults = true` jusque dans une base solo. Ne jamais ajouter de
     * réglage d'équipe en dehors de [TeamSettings].
     */
    val team: TeamSettings? = null,
) {
    /** L'espace Équipe est activé (valeur enregistrée). */
    val teamModeEnabled: Boolean get() = team?.enabled == true

    /** Types de tâche, dans l'ordre, sans vides. */
    val taskTypeList: List<String> get() = taskTypes.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        /** Le miel du logo, graine de la charte (docs/DESIGN_SYSTEM.md). */
        const val DEFAULT_THEME_COLOR = "#C28417"

        /** Couleurs du système (Android 12 et plus), à défaut le miel. */
        const val SYSTEM_THEME = "system"

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

/**
 * Réglages de l'espace Équipe (sous-objet [Settings.team]). Mêmes règles que
 * [Settings] : champ absent = valeur par défaut, champ inconnu ignoré.
 */
@Serializable
data class TeamSettings(
    val enabled: Boolean = false,
    val name: String = "",
    val managerName: String = "",
    /** UUID de l'équipe, posé par le dépôt à la première activation ; technique, non affiché. */
    val identity: String = "",
    /** Dernier espace affiché ([SPACE_PERSONAL] ou [SPACE_TEAM]) ; technique. */
    val lastSpace: String = SPACE_PERSONAL,
    /** Suivi (docs/spec/equipe-backlog-suivi.md § Signaux) : jours ouvrés sans avancement avant le signal « sans avancement » (≥ 1). */
    val staleProgressDays: Int = DEFAULT_STALE_PROGRESS_DAYS,
    /** Nombre de réaffectations d'un membre à un autre à partir duquel une tâche est « ballottée » (≥ 2). */
    val churnThreshold: Int = DEFAULT_CHURN_THRESHOLD,
    /** Tâches en cours par membre au-delà desquelles le signal « trop d'en-cours » s'allume (≥ 0 ; 0 = sans limite). */
    val wipLimit: Int = DEFAULT_WIP_LIMIT,
    /** Charge (docs/spec/equipe-charge.md) : horizon glissant par défaut, en semaines (1-26). */
    val horizonWeeks: Int = DEFAULT_HORIZON_WEEKS,
    /** Part du temps réellement disponible pour les tâches (> 0 et ≤ 1) : réunions, support et imprévus en moins. */
    val focusFactor: Double = DEFAULT_FOCUS_FACTOR,
    /** Heures par point de Fibonacci quand ni estimation ni calibration ne servent (> 0). */
    val hoursPerPoint: Double = DEFAULT_HOURS_PER_POINT,
    /** Taux de charge (en %, 1-100) au-delà duquel un membre est « à surveiller ». */
    val loadWarnPercent: Int = DEFAULT_LOAD_WARN_PERCENT,
    /** Écart de fin (en jours ouvrés, ≥ 0) dans lequel la suggestion préfère l'affinité de catégorie. */
    val affinityDays: Int = DEFAULT_AFFINITY_DAYS,
    /** Prévisions (docs/spec/equipe-simulation.md) : nombre de tirages d'une simulation (500-50 000). */
    val simulationRuns: Int = DEFAULT_SIMULATION_RUNS,
    /** Fenêtre d'historique des facteurs d'erreur, des débits et de la capacité réelle, en semaines (1-104). */
    val historyWeeks: Int = DEFAULT_HISTORY_WEEKS,
    /** Nombre minimal d'échantillons pour se fier à l'historique (≥ 3) ; en dessous, « peu fiable » et loi par défaut mêlée. */
    val minSamples: Int = DEFAULT_MIN_SAMPLES,
    /** Probabilité de tenir une échéance (en %, 1-99) sous laquelle elle est « en danger ». */
    val deadlineRiskPercent: Int = DEFAULT_DEADLINE_RISK_PERCENT,
) {
    companion object {
        const val DEFAULT_STALE_PROGRESS_DAYS = 5
        const val DEFAULT_CHURN_THRESHOLD = 3
        const val DEFAULT_WIP_LIMIT = 3
        const val DEFAULT_HORIZON_WEEKS = 4
        const val DEFAULT_FOCUS_FACTOR = 0.8
        const val DEFAULT_HOURS_PER_POINT = 2.0
        const val DEFAULT_LOAD_WARN_PERCENT = 90
        const val DEFAULT_AFFINITY_DAYS = 2
        const val DEFAULT_SIMULATION_RUNS = 5000
        const val DEFAULT_HISTORY_WEEKS = 12
        const val DEFAULT_MIN_SAMPLES = 8
        const val DEFAULT_DEADLINE_RISK_PERCENT = 70

        const val SPACE_PERSONAL = "personal"
        const val SPACE_TEAM = "team"
    }
}
