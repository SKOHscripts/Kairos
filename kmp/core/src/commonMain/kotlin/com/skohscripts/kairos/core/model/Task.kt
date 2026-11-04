package com.skohscripts.kairos.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

/**
 * Tâche (docs/spec/modele-donnees.md). Mêmes champs que la table `task`.
 *
 * Conventions : les dates et heures « métier » (échéance,
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
    /** 0 = lundi … 6 = dimanche (convention du projet). */
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
    /**
     * Tâche **reçue** d'un manager par paquet (docs/spec/equipe-echanges.md) : origine encodée par
     * `TeamOrigin` (identité de l'équipe, nom du manager…) ; `null` = tâche qui n'a pas été reçue.
     * Une tâche reçue reste une tâche [TaskSpace.PERSONAL] ordinaire chez le membre.
     */
    val origin: String? = null,
    /** Tâche reçue absente du dernier paquet du manager : jamais supprimée, signalée « retirée » (jalon E6). */
    val originRemoved: Boolean = false,
    /**
     * Dernier total de temps passé **rapporté** par le membre (minutes), côté manager ; il s'ajoute aux
     * sessions et au temps saisi à la main dans tous les calculs de temps passé (`TimeTracking.spentMinutesByTask`).
     */
    val reportedMinutes: Int? = null,
    /**
     * Tâche perso **exclue** de l'espace Équipe (docs/spec/equipe-moi.md) : par défaut, une tâche perso compte dans la
     * charge et les prévisions de « moi » ; vrai la retire de ces calculs. Sans effet sans gestion d'équipe ni « moi ».
     */
    val teamExcluded: Boolean = false,
    /**
     * Série d'une tâche récurrente (docs/spec/durees-apprises.md) : l'identifiant de la **première** tâche de la série, hérité
     * par chaque occurrence engendrée ([com.skohscripts.kairos.core.engine.Recurrence.nextOccurrence]). `null` pour une tâche qui
     * n'a pas (encore) d'occurrence suivante, et pour toute tâche d'avant la 3.4 (pas de rétro-remplissage : elles se rattachent
     * par leur titre). Sert à retrouver le temps passé sur les occurrences passées.
     */
    val seriesId: Long? = null,
    /**
     * Notes des échanges d'équipe (docs/spec/equipe-echanges.md § Notes, issue #120) : [noteOut], la note que ce côté enverra
     * avec le prochain paquet (manager) ou rapport (membre), vide par défaut ; [noteIn], la dernière note reçue de l'autre côté.
     */
    val noteOut: String = "",
    val noteIn: ExchangeNote? = null,
    /**
     * Tâche personnelle proposée au manager d'une équipe d'origine (docs/spec/equipe-echanges.md § Propositions, issue #121) :
     * la clé `TeamOrigin.key` de cette équipe ; `null` = non proposée.
     */
    val proposedTo: String? = null,
) {
    /**
     * Pas encore clarifiée (GTD, docs/spec/vue-jour.md § Décisions) : il manque la priorité, les points **ou**, si
     * [typeRequired] ([Settings.typeRequired] : la liste des types n'est pas vide), le type. Elle reste dans « À traiter »
     * (« À qualifier » pour une tâche d'équipe) et n'entre dans aucun tri.
     */
    fun needsProcessing(typeRequired: Boolean): Boolean = missing(typeRequired) != null

    /** Ce qui manque pour qualifier la tâche (libellé de la boîte de réception) ; `null` si rien. */
    fun missing(typeRequired: Boolean): Missing? {
        val m = Missing(priority = priority == null, points = fibonacciPoints == null, type = typeRequired && taskType.isBlank())
        return m.takeIf { it.priority || it.points || it.type }
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

/** Ce qui manque pour qualifier une tâche ([Task.missing]). */
data class Missing(val priority: Boolean, val points: Boolean, val type: Boolean)

/** Statut d'une tâche ; [code] = valeur stockée (valeur stockée, figée). */
enum class TaskStatus(val code: String) {
    TODO("todo"),
    DONE("done"),

    /** Tâche archivée : conservée dans la base et l'export, jamais produite par l'interface actuelle. */
    ARCHIVED("archived"),
    ;

    companion object {
        fun fromCode(code: String): TaskStatus = entries.firstOrNull { it.code == code } ?: TODO
    }
}

/**
 * Récurrence d'une tâche ; [code] = valeur stockée (valeur stockée, figée).
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
