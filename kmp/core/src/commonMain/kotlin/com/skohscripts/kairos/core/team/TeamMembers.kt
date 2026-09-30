package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.LocalDate

/**
 * Lectures pures sur les membres (docs/spec/equipe.md § Membres) : ordre
 * d'affichage, actifs et archivés, prochaine absence, historique de tâches.
 */
object TeamMembers {
    /**
     * Ordre d'affichage : « moi » d'abord, puis par nom sans tenir compte de la
     * casse (`id` en dernier recours, pour un ordre stable).
     */
    fun ordered(members: List<TeamMember>): List<TeamMember> =
        members.sortedWith(
            compareByDescending<TeamMember> { it.isSelf }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id },
        )

    /** Membres non archivés, dans l'ordre d'affichage. */
    fun active(members: List<TeamMember>): List<TeamMember> = ordered(members).filter { !it.archived }

    /** Membres archivés (« Anciens membres »), dans l'ordre d'affichage. */
    fun archived(members: List<TeamMember>): List<TeamMember> = ordered(members).filter { it.archived }

    /**
     * Prochaine absence de [memberId] à venir ou en cours à [today] : celle dont
     * la fin n'est pas passée et qui commence le plus tôt (une absence en cours
     * passe donc avant une absence à venir).
     */
    fun nextAbsence(absences: List<MemberAbsence>, memberId: Long, today: LocalDate): MemberAbsence? =
        absences.filter { it.memberId == memberId && it.end >= today }
            .minWithOrNull(compareBy<MemberAbsence> { it.start }.thenBy { it.end }.thenBy { it.id })

    /** Absences d'un membre, de la plus ancienne à la plus récente. */
    fun absencesOf(absences: List<MemberAbsence>, memberId: Long): List<MemberAbsence> =
        absences.filter { it.memberId == memberId }.sortedWith(compareBy<MemberAbsence> { it.start }.thenBy { it.end }.thenBy { it.id })

    /**
     * Le membre a déjà eu une tâche : une tâche quelconque, de tout statut, lui
     * est assignée, ou il est le titulaire d'un événement du journal [events]
     * (même si la tâche a été supprimée depuis). Seul cas où la suppression est
     * refusée.
     */
    fun hasHadTask(tasks: List<Task>, memberId: Long, events: List<TeamEvent> = emptyList()): Boolean =
        tasks.any { it.assigneeId == memberId } || appearsInEvents(events, memberId)

    /**
     * Le membre est le titulaire (`memberId`) d'au moins un événement : pour une
     * assignation, c'est le **nouveau** titulaire. Un membre seulement cité comme
     * ancien titulaire (`fromValue`) a forcément un événement antérieur à son nom,
     * sauf après l'archivage qui remet ses tâches au backlog : cet événement-là
     * (`memberId` nul) ne l'empêche pas d'être supprimé, comme en E2.
     */
    fun appearsInEvents(events: List<TeamEvent>, memberId: Long): Boolean = events.any { it.memberId == memberId }

    /**
     * Tâche ouverte d'équipe d'un membre : à faire, dans l'espace Équipe, à son
     * nom. C'est ce qu'un archivage remet au backlog.
     */
    fun isOpenTeamTaskOf(task: Task, memberId: Long): Boolean =
        task.space == TaskSpace.TEAM && task.status == TaskStatus.TODO && task.assigneeId == memberId

    /** Nombre de tâches qu'un archivage de [memberId] remettrait au backlog. */
    fun openTaskCount(tasks: List<Task>, memberId: Long): Int = tasks.count { isOpenTeamTaskOf(it, memberId) }
}
