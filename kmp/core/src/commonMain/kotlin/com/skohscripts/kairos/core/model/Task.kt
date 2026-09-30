package com.skohscripts.kairos.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

/**
 * Tâche (docs/spec/modele-donnees.md). Mêmes champs que la table `task` de
 * Kairos 2, moins ceux des intégrations retirées (`source`, `external_id`,
 * `linked_ticket_id`).
 *
 * Conventions reprises de Kairos 2 : les dates et heures « métier » (échéance,
 * date programmée, épinglage) sont **locales et naïves** ; les horodatages
 * techniques (`createdAt`, `updatedAt`) sont des instants UTC.
 */
data class Task(
    val id: Long,
    val title: String,
    val description: String = "",
    /** 0 (P0, le plus prioritaire) à 2 ; `null` = non renseignée. */
    val priority: Int? = null,
    val deadline: LocalDate? = null,
    val projectTag: String = "",
    val status: TaskStatus = TaskStatus.TODO,
    /** `null` = non renseignée : repli sur le réglage de durée par défaut au moment de planifier. */
    val estimatedMinutes: Int? = null,
    val pinnedStart: LocalDateTime? = null,
    /** Tâche mère (référence « molle », jamais contrainte). */
    val parentId: Long? = null,
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    val scheduledDate: LocalDate? = null,
    val recurrenceDayOfMonth: Int? = null,
    /** 0 = lundi … 6 = dimanche (convention de Kairos 2). */
    val recurrenceDayOfWeek: Int? = null,
    val recurrencePeriod: String = "",
    val taskType: String = "",
    /** Valeur de [FIBONACCI_SCALE] ; `null` = non renseignés. */
    val fibonacciPoints: Int? = null,
    val manualTimeSpentMinutes: Int? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Espace de la tâche (docs/spec/equipe.md) : les tâches d'équipe ne sont jamais lues par l'espace Perso, sauf celles assignées à « moi ». */
    val space: TaskSpace = TaskSpace.PERSONAL,
    /** Membre d'équipe assigné (`TeamMember.id`, référence « molle »). Sans effet sur une tâche [TaskSpace.PERSONAL]. */
    val assigneeId: Long? = null,
    /** Avancement déclaré d'une tâche d'équipe (0-100, pas de 10) ; `null` = jamais renseigné (docs/spec/equipe-backlog-suivi.md). */
    val progressPercent: Int? = null,
    /** Jour où la tâche d'équipe a été commencée (date locale) ; `null` = pas commencée. Sert à dériver l'état « En cours ». */
    val startedOn: LocalDate? = null,
    /** Identité stable d'une tâche d'équipe (UUID texte), posée par le dépôt à sa création ; `null` pour une tâche Perso. */
    val teamUid: String? = null,
) {
    /**
     * Pas encore clarifiée (GTD) : il manque la priorité **ou** les points. Elle
     * reste dans « À traiter » et n'entre dans aucun tri (règle de
     * `tasks_scheduling.py` de Kairos 2 : `priority is None or fibonacci_points is None`).
     */
    val needsProcessing: Boolean get() = priority == null || fibonacciPoints == null

    /** Ce qui manque pour qualifier la tâche (libellé de la boîte de réception). */
    val missingQualification: MissingQualification?
        get() = when {
            priority == null && fibonacciPoints == null -> MissingQualification.BOTH
            priority == null -> MissingQualification.PRIORITY
            fibonacciPoints == null -> MissingQualification.POINTS
            else -> null
        }
}

/**
 * Espace d'une tâche ; [code] = valeur stockée (entier). Un code inconnu se lit
 * comme [PERSONAL] : une donnée illisible ne doit jamais faire apparaître une
 * tâche dans l'espace Équipe, ni la faire disparaître de l'espace Perso.
 */
enum class TaskSpace(val code: Int) {
    PERSONAL(0),
    TEAM(1),
    ;

    companion object {
        fun fromCode(code: Int): TaskSpace = entries.firstOrNull { it.code == code } ?: PERSONAL
    }
}

enum class MissingQualification { PRIORITY, POINTS, BOTH }

/** Statut d'une tâche ; [code] = valeur stockée (identique à Kairos 2). */
enum class TaskStatus(val code: String) {
    TODO("todo"),
    DONE("done"),

    /** Kairos 2 : tâche importée disparue de sa source. Conservée pour l'import, jamais produite en v3. */
    ARCHIVED("archived"),
    ;

    companion object {
        fun fromCode(code: String): TaskStatus = entries.firstOrNull { it.code == code } ?: TODO
    }
}

/**
 * Récurrence d'une tâche ; [code] = valeur stockée (identique à Kairos 2).
 * `MONTHLY_ON_DAY` est calendaire, les autres se recréent à la complétion
 * (moteur au jalon M2, docs/spec/recurrence.md).
 */
enum class TaskRecurrence(val code: String) {
    NONE(""),
    DAILY("daily"),
    WEEKDAYS("weekdays"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    MONTHLY_ON_DAY("monthly_on_day"),
    ;

    companion object {
        fun fromCode(code: String): TaskRecurrence = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/** Échelle fixe des points de Fibonacci (pas de ½, 0 ni 100). */
val FIBONACCI_SCALE: List<Int> = listOf(1, 2, 3, 5, 8, 13, 21)

/** Priorités possibles, de la plus forte à la plus faible. */
val PRIORITY_VALUES: List<Int> = listOf(0, 1, 2)
