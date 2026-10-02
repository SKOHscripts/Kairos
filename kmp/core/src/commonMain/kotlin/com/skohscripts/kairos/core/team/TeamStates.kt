package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.engine.Dependencies
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus

/** États d'une tâche d'équipe (docs/spec/equipe-backlog-suivi.md § États et avancement). */
enum class TeamState { BACKLOG, TODO, IN_PROGRESS, DONE }

/**
 * États **dérivés** de `status`, `assigneeId` et `startedOn` : aucune colonne
 * d'état, donc aucune combinaison incohérente (« En cours » sans assigné).
 * Pur.
 */
object TeamStates {
    /**
     * État de [task] : Faite si `status = done` ; sinon, à faire : Backlog sans
     * assigné, En cours si `startedOn` est posé, À faire sinon. Une tâche
     * archivée (import de Kairos 2) n'a aucun état (`null`).
     */
    fun of(task: Task): TeamState? = when {
        task.status == TaskStatus.ARCHIVED -> null
        task.status == TaskStatus.DONE -> TeamState.DONE
        task.assigneeId == null -> TeamState.BACKLOG
        task.startedOn != null -> TeamState.IN_PROGRESS
        else -> TeamState.TODO
    }

    /**
     * Tâches d'équipe **bloquées** : à faire et bloquées par une tâche d'équipe à
     * faire (`Dependencies.blockedTaskIds` sur les arêtes entre tâches d'équipe).
     * « Bloquée » s'affiche en plus de À faire / En cours, ce n'est pas un état.
     */
    fun blockedIds(snapshot: KairosSnapshot): Set<Long> {
        val teamIds = snapshot.tasks.filter { it.space == TaskSpace.TEAM }.mapTo(HashSet()) { it.id }
        val statusById = snapshot.tasks.associate { it.id to it.status }
        val edges = snapshot.dependencies
            .filter { it.taskId in teamIds && it.blockerId in teamIds }
            .map { Dependencies.Edge(blocked = it.taskId, blocker = it.blockerId) }
        return Dependencies.blockedTaskIds(edges) { (statusById[it] ?: TaskStatus.TODO) == TaskStatus.TODO }
    }
}
