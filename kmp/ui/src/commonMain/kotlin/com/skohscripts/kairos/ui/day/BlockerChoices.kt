package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.core.engine.Dependencies
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskTree

/**
 * Une case de la liste « Bloquée par » du dialogue d'édition (docs/spec/dependances.md) : son libellé, et si la cocher
 * créerait un cycle ([cyclic] : la tâche candidate attend déjà, directement ou non, la tâche éditée).
 */
internal data class BlockerChoice(val task: Task, val label: String, val cyclic: Boolean)

internal object BlockerChoices {
    /**
     * Les bloqueurs possibles de [task] parmi [candidates] (elle exclue, et ceux d'un autre espace), dans l'ordre reçu. Libellé : la
     * lignée puis le titre pour une sous-tâche (« A › C › D », comme dans les listes de Jour, docs/spec/sous-taches.md) ; deux
     * libellés identiques (une tâche et sa prochaine occurrence récurrente, par exemple) se distinguent par leur date
     * (échéance, sinon date prévue), sinon par leur numéro. [tasksById] donne toute tâche (les mères ne sont pas forcément
     * candidates).
     */
    fun of(task: Task, candidates: List<Task>, dependencies: List<TaskDependency>, tasksById: Map<Long, Task>, language: String): List<BlockerChoice> {
        val edges = dependencies.map { Dependencies.Edge(blocked = it.taskId, blocker = it.blockerId) }
        // Même espace seulement : le dépôt refuse un bloqueur d'un autre espace (docs/spec/equipe.md § Espaces) ; une tâche
        // d'équipe de « moi », visible dans l'espace Perso, n'est donc jamais proposée à une tâche perso, ni l'inverse.
        val others = candidates.filter { it.id != task.id && it.space == task.space }
        val base = others.associate { t -> t.id to (TaskTree.path(t.id, tasksById)?.let { it + TaskTree.SEPARATOR + t.title } ?: t.title) }
        val repeated = base.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        return others.map { t ->
            val label = base.getValue(t.id)
            val distinct = if (label in repeated) {
                val date = t.deadline ?: t.scheduledDate
                "$label (${date?.let { Dates.short(it, language) } ?: "#${t.id}"})"
            } else {
                label
            }
            BlockerChoice(t, distinct, Dependencies.wouldCreateCycle(edges, task.id, t.id))
        }
    }
}
