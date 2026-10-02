package com.skohscripts.kairos.core.team.exchange

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlin.time.Instant

/**
 * Construit le paquet d'un membre depuis la base du manager (docs/spec/equipe-echanges.md § Formats).
 * Pur : l'identifiant du paquet et l'horodatage sont des paramètres (aucun hasard, aucune horloge).
 */
object PackBuilder {
    /**
     * Le paquet de [memberId], ou `null` si l'envoi est impossible : espace Équipe sans identité
     * (jamais activé), membre inconnu ou archivé.
     *
     * Contenu : les tâches d'équipe **à faire** assignées au membre, plus leurs sous-tâches (à faire, quel que
     * soit leur assigné : une sous-tâche suit sa mère), à tout niveau. Une tâche sans `teamUid` (impossible
     * tant que le dépôt les pose toutes) est écartée. Dépendances : celles dont les deux tâches sont dans le
     * paquet. Bloqueurs externes : pour une tâche du paquet, chaque bloqueur d'équipe **encore à faire** qui n'y
     * est pas, avec son titre et le nom de son titulaire (omis au backlog).
     */
    fun build(snapshot: KairosSnapshot, memberId: Long, packId: String, exportedAt: Instant): TeamPack? {
        val team = snapshot.settings.team?.takeIf { it.identity.isNotEmpty() } ?: return null
        val member = snapshot.members.firstOrNull { it.id == memberId && !it.archived } ?: return null

        val openTeam = snapshot.tasks.filter { it.space == TaskSpace.TEAM && it.status == TaskStatus.TODO && it.teamUid != null }
        val included = LinkedHashMap<Long, Task>()
        openTeam.filter { it.assigneeId == memberId }.forEach { included[it.id] = it }
        // Sous-tâches, à tout niveau : on itère jusqu'à ce que plus rien ne s'ajoute.
        do {
            val before = included.size
            openTeam.filter { it.parentId in included && it.id !in included }.forEach { included[it.id] = it }
        } while (included.size > before)

        val ordered = included.values.sortedBy { it.id }
        val uidById = ordered.associate { it.id to it.teamUid!! }

        val tasks = ordered.map { t ->
            PackTask(
                uid = t.teamUid!!,
                parentUid = t.parentId?.let(uidById::get),
                title = t.title,
                description = t.description,
                priority = t.priority,
                fibonacciPoints = t.fibonacciPoints,
                taskType = t.taskType,
                deadline = t.deadline,
                estimatedMinutes = t.estimatedMinutes,
                progressPercent = t.progressPercent,
            )
        }

        val edges = snapshot.dependencies.filter { it.taskId in included }.sortedWith(compareBy({ it.taskId }, { it.blockerId }))
        val dependencies = edges.filter { it.blockerId in included }
            .map { PackDependency(uidById.getValue(it.taskId), uidById.getValue(it.blockerId)) }

        val byId = snapshot.tasks.associateBy { it.id }
        val nameById = snapshot.members.associate { it.id to it.name }
        val external = edges.filter { it.blockerId !in included }.mapNotNull { dep ->
            val blocker = byId[dep.blockerId]?.takeIf { it.space == TaskSpace.TEAM && it.status == TaskStatus.TODO } ?: return@mapNotNull null
            ExternalBlocker(uidById.getValue(dep.taskId), blocker.title, blocker.assigneeId?.let(nameById::get))
        }

        return TeamPack(
            packId = packId,
            exportedAt = exportedAt,
            team = PackTeam(team.identity, team.name, team.managerName),
            member = PackMember(member.uid, member.name),
            tasks = tasks,
            dependencies = dependencies,
            externalBlockers = external,
        )
    }
}
