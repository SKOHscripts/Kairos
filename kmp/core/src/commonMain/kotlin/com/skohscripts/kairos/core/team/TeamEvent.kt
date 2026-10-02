package com.skohscripts.kairos.core.team

import kotlin.time.Instant

/**
 * Événement du journal d'une tâche d'équipe (docs/spec/equipe-backlog-suivi.md
 * § Journal). Écrit par le dépôt, dans la transaction de la modification.
 *
 * - [taskTitle] : titre recopié à l'écriture, pour que l'activité d'un membre
 *   reste lisible après la suppression de la tâche ;
 * - [memberId] : assigné de la tâche **au moment de l'événement** (`null` au
 *   backlog) ; pour un événement [TeamEventKind.ASSIGNED], c'est le nouveau
 *   titulaire ([toValue]) ;
 * - [fromValue] / [toValue] : valeurs en texte, selon [kind] (voir
 *   [TeamEventKind]).
 */
data class TeamEvent(
    val id: Long,
    val taskId: Long,
    val taskTitle: String,
    val memberId: Long?,
    val kind: TeamEventKind,
    val fromValue: String? = null,
    val toValue: String? = null,
    val source: TeamEventSource,
    val at: Instant,
)

/**
 * Nature d'un événement ; [code] = valeur stockée. Un code inconnu (journal
 * écrit par une version plus récente) se lit [UNKNOWN] : l'événement est gardé
 * et affiché « Modification », jamais une erreur.
 *
 * Valeurs portées :
 * - `created` : `toValue` = titre ;
 * - `qualified` : `fromValue` / `toValue` = `priority:P1`, `points:5`,
 *   `type:Réunion`, `deadline:2026-10-03` (voir [TeamEvents]) ; un événement par
 *   champ changé ;
 * - `assigned` : identifiants de membre, `null` = backlog ;
 * - `started` : `toValue` = jour (date ISO) ;
 * - `progress` : pourcentages ;
 * - `done` : `fromValue` = avancement d'avant la fin (restauré à la
 *   réouverture), `toValue` = « 100 » ;
 * - `reopened` : `toValue` = avancement restauré ;
 * - `deleted` : sans valeur ;
 * - `sent` (jalon E6) : tâche envoyée dans un paquet, `toValue` = identifiant du paquet ; `memberId` = destinataire ;
 * - `time` (jalon E6) : temps passé rapporté par le membre, `fromValue` / `toValue` = total en minutes
 *   (`fromValue` nul au premier rapport). Pour tout événement de source `report`, `memberId` est le
 *   membre qui a **rapporté** (et non l'assigné actuel : une tâche réaffectée depuis garde l'auteur du travail).
 */
enum class TeamEventKind(val code: String) {
    CREATED("created"),
    QUALIFIED("qualified"),
    ASSIGNED("assigned"),
    STARTED("started"),
    PROGRESS("progress"),
    DONE("done"),
    REOPENED("reopened"),
    DELETED("deleted"),
    SENT("sent"),
    TIME("time"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromCode(code: String): TeamEventKind = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/**
 * Origine d'une modification ; [code] = valeur stockée. `manual` : faite par le
 * manager dans l'espace Équipe ; `self` : faite depuis l'espace Perso sur une
 * tâche assignée à « moi » ; `report` : intégration d'un rapport de membre (E6) ; `scenario` : application
 * d'un scénario (E5). Un code
 * inconnu se lit [MANUAL].
 */
enum class TeamEventSource(val code: String) {
    MANUAL("manual"),
    SELF("self"),
    REPORT("report"),
    SCENARIO("scenario"),
    ;

    companion object {
        fun fromCode(code: String): TeamEventSource = entries.firstOrNull { it.code == code } ?: MANUAL
    }
}

/** Champ qualifié, préfixe des valeurs d'un événement `qualified` (`priority:1`). */
enum class QualifiedField(val code: String) {
    PRIORITY("priority"),
    POINTS("points"),
    TYPE("type"),
    DEADLINE("deadline"),
}

/** Lectures et écritures pures sur le journal (textes des valeurs, historique d'une tâche). */
object TeamEvents {
    /** Valeur d'un événement `qualified` : `champ:valeur` (valeur vide si le champ est vidé). */
    fun qualifiedValue(field: QualifiedField, value: String?): String = "${field.code}:${value.orEmpty()}"

    /** Champ et valeur d'un texte `qualified` (`null` si le préfixe est inconnu : journal d'une version plus récente). */
    fun parseQualified(text: String?): Pair<QualifiedField, String>? {
        if (text == null) return null
        val cut = text.indexOf(':')
        if (cut < 0) return null
        val field = QualifiedField.entries.firstOrNull { it.code == text.substring(0, cut) } ?: return null
        return field to text.substring(cut + 1)
    }

    /** Événements d'une tâche, du plus récent au plus ancien (`id` décroissant à instant égal). */
    fun historyOf(events: List<TeamEvent>, taskId: Long): List<TeamEvent> =
        events.filter { it.taskId == taskId }.sortedWith(compareByDescending<TeamEvent> { it.at }.thenByDescending { it.id })
}
