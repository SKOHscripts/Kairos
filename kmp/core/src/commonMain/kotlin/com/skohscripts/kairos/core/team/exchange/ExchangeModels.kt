package com.skohscripts.kairos.core.team.exchange

import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

// Contenu des fichiers d'échange entre un manager et ses membres (docs/spec/equipe-echanges.md § Formats).
// Modèles purs : le JSON est l'affaire de `TeamExchangeCodec` (module data). Aucun identifiant entier
// de base n'y figure : seules les identités stables (UUID) sortent d'une base.

/** Équipe d'un paquet : identité (`TeamSettings.identity`), nom et nom du manager (Réglages → Équipe). */
data class PackTeam(val uid: String, val name: String, val manager: String)

/** Membre désigné par un paquet ou un rapport : `TeamMember.uid` et nom (affichage seulement). */
data class PackMember(val uid: String, val name: String)

/**
 * Tâche d'un paquet. [uid] = `Task.teamUid` côté manager ; [parentUid] = tâche mère **dans le paquet**
 * (`null` : tâche de premier niveau du paquet). Priorité, points, durée et avancement `null` = non renseignés.
 */
data class PackTask(
    val uid: String,
    val parentUid: String? = null,
    val title: String,
    val description: String = "",
    val priority: Int? = null,
    val fibonacciPoints: Int? = null,
    val taskType: String = "",
    val deadline: LocalDate? = null,
    val estimatedMinutes: Int? = null,
    val progressPercent: Int? = null,
)

/** [taskUid] est bloquée par [blockerUid] : deux tâches du même paquet. */
data class PackDependency(val taskUid: String, val blockerUid: String)

/**
 * Bloqueur de [taskUid] tenu hors du paquet (par un autre membre, ou resté au backlog si [assignee] est
 * `null`) : information en lecture seule, qui ne bloque pas l'ordonnancement du membre.
 */
data class ExternalBlocker(val taskUid: String, val title: String, val assignee: String? = null)

/** Paquet « manager -> membre » (`kairos-team-pack`). */
data class TeamPack(
    val packId: String,
    val exportedAt: Instant,
    val team: PackTeam,
    val member: PackMember,
    val tasks: List<PackTask> = emptyList(),
    val dependencies: List<PackDependency> = emptyList(),
    val externalBlockers: List<ExternalBlocker> = emptyList(),
)

/**
 * État d'une tâche reçue dans un rapport. [status] vaut [TaskStatus.TODO] ou [TaskStatus.DONE] ;
 * [doneOn] n'a de sens que pour une tâche faite ; [spentMinutes] est le **total** (sessions + saisie manuelle).
 */
data class ReportTask(
    val uid: String,
    val status: TaskStatus,
    val doneOn: LocalDate? = null,
    val startedOn: LocalDate? = null,
    val progressPercent: Int? = null,
    val spentMinutes: Int = 0,
)

/** Sous-tâche créée par le membre sous une tâche reçue ; [uid] est posé côté membre et reste stable d'un rapport à l'autre. */
data class ReportSubtask(val uid: String, val parentUid: String, val title: String, val status: TaskStatus = TaskStatus.TODO)

/** Rapport « membre -> manager » (`kairos-team-report`). */
data class TeamReport(
    val reportedAt: Instant,
    val teamUid: String,
    val member: PackMember,
    val tasks: List<ReportTask> = emptyList(),
    val newSubtasks: List<ReportSubtask> = emptyList(),
)
