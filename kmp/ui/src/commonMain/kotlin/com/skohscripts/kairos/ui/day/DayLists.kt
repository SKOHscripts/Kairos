package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus

/**
 * Répartition des tâches de la vue Jour au jalon M1 (docs/spec-v3/vue-jour.md
 * § Listes) : « À traiter » (non qualifiées), « À faire » (qualifiées) et
 * « Fait ». L'ordre de « À faire » est **provisoire** jusqu'au moteur
 * d'ordonnancement (M2) : priorité, puis échéance (sans échéance en dernier),
 * puis ancienneté.
 */
data class DayLists(val inbox: List<Task>, val todo: List<Task>, val done: List<Task>) {
    companion object {
        fun of(tasks: List<Task>): DayLists {
            val open = tasks.filter { it.status == TaskStatus.TODO }
            return DayLists(
                inbox = open.filter { it.needsProcessing }.sortedBy { it.id },
                todo = open.filterNot { it.needsProcessing }.sortedWith(
                    compareBy<Task>({ it.priority }, { it.deadline == null }, { it.deadline }, { it.id }),
                ),
                done = tasks.filter { it.status == TaskStatus.DONE }.sortedByDescending { it.updatedAt },
            )
        }
    }
}
