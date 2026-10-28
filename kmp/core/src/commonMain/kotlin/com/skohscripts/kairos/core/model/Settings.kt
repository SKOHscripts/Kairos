package com.skohscripts.kairos.core.model

import com.skohscripts.kairos.core.settings.UiLanguage
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Réglages de Kairos (docs/spec/modele-donnees.md § Réglages) avec leurs valeurs par
 * défaut (celles de l'ordonnancement sont figées par les fichiers d'or, docs/spec/ordonnancement.md).
 * Stockés en JSON dans la base ; un champ absent du JSON prend sa valeur par défaut (ajout de réglage sans migration).
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
    /** Thème : [THEME_MODE_SYSTEM] (suit le système), [THEME_MODE_LIGHT] ou [THEME_MODE_DARK] (docs/spec/apparence.md, issue #72). */
    val themeMode: String = THEME_MODE_SYSTEM,
    /** Langue de l'interface : [LANGUAGE_SYSTEM] (suit le système) ou l'une des langues de `UiLanguage.SUPPORTED` (docs/spec/i18n.md). */
    val language: String = LANGUAGE_SYSTEM,
    /**
     * Réglages de l'espace Équipe (docs/spec/equipe.md). `null` tant que
     * l'espace n'a jamais été activé : un champ nul n'est pas écrit (export,
     * JSON en base), alors qu'un réglage d'équipe à plat serait écrit par
     * `encodeDefaults = true` jusque dans une base solo. Ne jamais ajouter de
     * réglage d'équipe en dehors de [TeamSettings].
     */
    val team: TeamSettings? = null,
    /**
     * Horaires par jour de la semaine (docs/spec/planning-personnalise.md § Lot B1). `null` tant que rien n'est
     * personnalisé : même règle que [team], pour que l'export et la base d'une installation qui n'y touche pas ne changent pas.
     */
    val schedule: ScheduleSettings? = null,
    /**
     * Durées apprises des tâches sans estimation (docs/spec/durees-apprises.md). `null` = les valeurs par défaut de
     * [LearningSettings] (actif) ; même règle que [team] : sauver le formulaire sans y toucher n'en crée jamais.
     */
    val learning: LearningSettings? = null,
    /**
     * Ce que le guide a retenu (docs/spec/visite-guidee.md § Persistance, issue #94). `null` tant qu'il n'a rien retenu :
     * même règle que [team], une base ou un export qui n'y a jamais touché ne change pas.
     */
    val guide: GuideSettings? = null,
    /**
     * Rappels des tâches à heure fixe et de la tâche suivante (docs/spec/rappels.md, issue #103). `null` = les valeurs par
     * défaut de [ReminderSettings] (désactivés) ; même règle que [learning].
     */
    val reminders: ReminderSettings? = null,
    /**
     * Noms choisis des niveaux de priorité et de points (docs/spec/planning-personnalise.md § Lot D, issue #93). `null` tant
     * qu'aucun nom n'est choisi : même règle que [team]. Affichage seulement : le calcul et les échanges gardent les valeurs.
     */
    val levelNames: LevelNames? = null,
    /**
     * Mémoire de « Ignorer » de l'avis des mois sautés (docs/spec/recurrence.md § Avis des mois sautés, issue #111). `null` tant
     * que rien n'est ignoré : même règle que [guide]. Hors du formulaire des Réglages, et gardée par « Restaurer les valeurs par
     * défaut » : c'est une décision sur des données, pas une préférence.
     */
    val recurrence: RecurrenceSettings? = null,
) {
    /** L'espace Équipe est activé (valeur enregistrée). */
    val teamModeEnabled: Boolean get() = team?.enabled == true

    /** Types de tâche, dans l'ordre, sans vides. */
    val taskTypeList: List<String> get() = taskTypes.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        /** L'océan, graine de la charte (docs/DESIGN_SYSTEM.md, docs/spec/apparence.md) ; avant, le miel. */
        const val DEFAULT_THEME_COLOR = "#2F6FED"

        /** Le miel du logo : l'ancienne graine par défaut, une couleur au choix dans Réglages → Apparence. */
        const val HONEY_THEME_COLOR = "#C28417"

        /** Couleurs du système (Android 12 et plus), à défaut la graine par défaut. */
        const val SYSTEM_THEME = "system"

        /** Thème : celui du système (sombre si le système l'est), ou choisi. */
        const val THEME_MODE_SYSTEM = "system"
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"

        /** Langue de l'interface : celle du système si elle est traduite, l'anglais sinon (`UiLanguage.SUPPORTED`). */
        const val LANGUAGE_SYSTEM = "system"
        const val LANGUAGE_FR = "fr"
        const val LANGUAGE_EN = "en"
        const val LANGUAGE_ES = "es"
        const val LANGUAGE_DE = "de"
        const val LANGUAGE_RU = "ru"
        const val LANGUAGE_IT = "it"

        /** Types de tâche par défaut. */
        const val DEFAULT_TASK_TYPES_FR =
            "Développement,Revue de code,Réunion,Documentation,Administratif,Veille/formation"
        const val DEFAULT_TASK_TYPES_EN =
            "Development,Code review,Meeting,Documentation,Administration,Learning"
        const val DEFAULT_TASK_TYPES_ES =
            "Desarrollo,Revisión de código,Reunión,Documentación,Administrativo,Formación"
        const val DEFAULT_TASK_TYPES_DE =
            "Entwicklung,Code-Review,Meeting,Dokumentation,Verwaltung,Weiterbildung"
        const val DEFAULT_TASK_TYPES_RU =
            "Разработка,Код-ревью,Встреча,Документация,Администрирование,Обучение"
        const val DEFAULT_TASK_TYPES_IT =
            "Sviluppo,Revisione del codice,Riunione,Documentazione,Amministrazione,Formazione"

        /** Types de tâche par défaut dans [language] (étiquette de langue ; anglais pour une langue non traduite). */
        fun defaultTaskTypes(language: String): String = when (UiLanguage.lang(language)) {
            LANGUAGE_FR -> DEFAULT_TASK_TYPES_FR
            LANGUAGE_ES -> DEFAULT_TASK_TYPES_ES
            LANGUAGE_DE -> DEFAULT_TASK_TYPES_DE
            LANGUAGE_RU -> DEFAULT_TASK_TYPES_RU
            LANGUAGE_IT -> DEFAULT_TASK_TYPES_IT
            else -> DEFAULT_TASK_TYPES_EN
        }

        /** Réglages d'une base neuve, types de tâche dans la langue de l'interface ([language] : celle du système). */
        fun defaults(language: String): Settings = Settings(taskTypes = defaultTaskTypes(language))
    }
}

/**
 * Réglages de l'espace Équipe (sous-objet [Settings.team]). Mêmes règles que
 * [Settings] : champ absent = valeur par défaut, champ inconnu ignoré.
 */
@OptIn(ExperimentalSerializationApi::class)
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
    /**
     * Propositions de membres refusées (docs/spec/equipe-echanges.md § Propositions, issue #121) : leurs identités, pour qu'un
     * rapport suivant ne les repropose pas. Jamais écrite vide : un export d'équipe d'avant ne change pas.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val rejectedProposals: List<String> = emptyList(),
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

/**
 * Mémoire du guide, sous-objet [Settings.guide] : [newsHidden] la version « majeur.mineur » dont la section « Nouveau dans
 * cette version » est masquée, [readChapters] les chapitres du hub parcourus jusqu'au bout (noms de `GuideChapter`),
 * [firstTaskDone] l'accompagnement de la première tâche mené à son terme. Aucun n'est écrit à sa valeur par défaut.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GuideSettings(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val newsHidden: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val readChapters: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val firstTaskDone: Boolean = false,
)

/**
 * Mémoire de l'avis des mois sautés, sous-objet [Settings.recurrence] : [skipIgnored] clé de série (`jour|titre`) → dernier mois
 * ignoré (`AAAA-MM`), un filigrane qui masque ce mois et les précédents. Jamais écrit vide.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RecurrenceSettings(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val skipIgnored: Map<String, String> = emptyMap(),
)

/**
 * Noms choisis des niveaux, sous-objet [Settings.levelNames] : [priority] valeur de `PRIORITY_VALUES` → nom, [points] palier
 * de `FIBONACCI_SCALE` → nom. Un niveau sans nom est absent (il garde son nom par défaut) ; aucun n'est écrit vide.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LevelNames(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val priority: Map<Int, String> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val points: Map<Int, String> = emptyMap(),
) {
    companion object {
        /** Un badge doit tenir dans une ligne de tâche à 360 dp. */
        const val MAX_LENGTH = 16
    }
}
