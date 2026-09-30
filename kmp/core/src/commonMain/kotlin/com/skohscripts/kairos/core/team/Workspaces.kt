package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus

/**
 * Espaces Perso et Équipe (docs/spec/equipe.md § Espaces et filtre central).
 * Pur : aucune horloge, aucun hasard, aucune E/S.
 */
object Workspaces {
    /**
     * **Le** filtre qui protège le mode solo : ce que l'espace Perso a le droit
     * de lire. Tâches gardées : toutes les `PERSONAL`, plus, si l'espace Équipe
     * est activé, les `TEAM` assignées au membre « moi » (non archivé).
     *
     * Sans aucune tâche `TEAM`, l'entrée est rendue **telle quelle** (même
     * instance, pas seulement une copie égale) : les moteurs reçoivent alors
     * exactement ce qu'ils recevaient avant l'espace Équipe, sans allocation,
     * et le test d'identité prouve l'isolation du mode solo.
     *
     * Une dépendance est gardée si sa tâche bloquée l'est et que son bloqueur
     * l'est aussi **ou** reste à faire dans la base complète : un bloqueur hors
     * vue bloque tant qu'il est à faire (`DayView` lit un identifiant inconnu
     * comme « à faire », et la vue fait de même), mais ne doit pas bloquer une fois fait ou archivé.
     */
    fun personalView(snapshot: KairosSnapshot): KairosSnapshot {
        if (snapshot.tasks.none { it.space == TaskSpace.TEAM }) return snapshot
        val selfId = if (snapshot.settings.teamModeEnabled) {
            snapshot.members.firstOrNull { it.isSelf && !it.archived }?.id
        } else {
            null
        }
        val tasks = snapshot.tasks.filter {
            it.space == TaskSpace.PERSONAL || (selfId != null && it.assigneeId == selfId)
        }
        val kept = tasks.mapTo(HashSet()) { it.id }
        val statusById = snapshot.tasks.associate { it.id to it.status }
        return snapshot.copy(
            tasks = tasks,
            dependencies = snapshot.dependencies.filter {
                it.taskId in kept && (it.blockerId in kept || (statusById[it.blockerId] ?: TaskStatus.TODO) == TaskStatus.TODO)
            },
            workSessions = snapshot.workSessions.filter { it.taskId in kept },
        )
    }

    /** La base contient au moins une donnée d'équipe (membre, absence, événement du journal, tâche d'équipe ou tâche assignée). */
    fun hasTeamData(snapshot: KairosSnapshot): Boolean =
        snapshot.members.isNotEmpty() ||
            snapshot.absences.isNotEmpty() ||
            snapshot.teamEvents.isNotEmpty() ||
            snapshot.tasks.any { it.space == TaskSpace.TEAM || it.assigneeId != null }
}
