package com.skohscripts.kairos.core.team.exchange

import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Construit le rapport d'avancement d'un membre pour un manager (docs/spec/equipe-echanges.md § Formats). Pur :
 * l'instant et le fuseau sont des paramètres. **Rien d'autre que les tâches reçues de ce manager n'en sort**
 * (ni tâche personnelle, ni description, ni note, ni créneau, ni session).
 */
object ReportBuilder {
    /** Tâches reçues de [originKey] qui entrent dans le rapport : reçues de cette équipe, **non retirées**, ni archivées. */
    fun reportedTasks(snapshot: KairosSnapshot, originKey: String): List<Task> =
        snapshot.tasks.filter {
            !it.originRemoved && it.status != TaskStatus.ARCHIVED && it.teamUid != null && ReceivedTasks.originOf(it)?.key == originKey
        }.sortedBy { it.id }

    /**
     * Sous-tâches **nouvelles** : créées par le membre (aucune origine), personnelles, de statut à faire ou fait, sous
     * une tâche rapportée ou sous une autre nouvelle sous-tâche, dans l'ordre des identifiants (une mère avant ses filles).
     * Elles n'ont d'identité (`teamUid`) que si le dépôt la leur a déjà posée : voir [subtasksNeedingUid].
     */
    fun newSubtasks(snapshot: KairosSnapshot, originKey: String): List<Task> {
        val parents = reportedTasks(snapshot, originKey).mapTo(HashSet()) { it.id }
        val found = ArrayList<Task>()
        val candidates = snapshot.tasks.filter {
            it.origin == null && it.space == TaskSpace.PERSONAL && it.status != TaskStatus.ARCHIVED && it.parentId != null
        }.sortedBy { it.id }
        var grew = true
        while (grew) {
            grew = false
            for (t in candidates) {
                if (t.parentId in parents && parents.add(t.id)) {
                    found += t
                    grew = true
                }
            }
        }
        return found.sortedBy { it.id }
    }

    /** Nouvelles sous-tâches sans identité stable : le dépôt leur en pose une avant de construire le rapport. */
    fun subtasksNeedingUid(snapshot: KairosSnapshot, originKey: String): List<Task> =
        newSubtasks(snapshot, originKey).filter { it.teamUid == null }

    /**
     * Le rapport pour [originKey] (identité de l'équipe), ou `null` s'il n'y a aucune tâche reçue non retirée de cette équipe.
     *
     * Pour chaque tâche reçue : statut (fait ou à faire), jour de fin (jour local de `updatedAt` pour une tâche faite : même
     * règle que les statistiques), avancement (100 pour une tâche faite), jour de commencement (celui de la tâche, sinon le
     * jour de sa première session, sinon le jour du rapport si un avancement est déclaré), temps passé total (sessions, session
     * ouverte jusqu'à [now], plus saisie manuelle). Les nouvelles sous-tâches sans identité sont écartées.
     */
    fun build(snapshot: KairosSnapshot, originKey: String, now: Instant, timeZone: TimeZone): TeamReport? {
        val tasks = reportedTasks(snapshot, originKey)
        if (tasks.isEmpty()) return null
        val origin = ReceivedTasks.originOf(tasks.last())!!
        val spent = TimeTracking.spentMinutesByTask(snapshot.workSessions, now, tasks)
        val firstSession = snapshot.workSessions.groupBy { it.taskId }
            .mapValues { (_, list) -> list.minOf { it.startedAt }.toLocalDateTime(timeZone).date }
        val today = now.toLocalDateTime(timeZone).date

        val reported = tasks.map { t ->
            val done = t.status == TaskStatus.DONE
            val progress = if (done) 100 else t.progressPercent
            ReportTask(
                uid = t.teamUid!!,
                status = if (done) TaskStatus.DONE else TaskStatus.TODO,
                doneOn = if (done) t.updatedAt.toLocalDateTime(timeZone).date else null,
                startedOn = t.startedOn ?: firstSession[t.id] ?: today.takeIf { !done && (progress ?: 0) > 0 },
                progressPercent = progress,
                spentMinutes = spent[t.id] ?: 0,
            )
        }

        val uidById = (tasks + newSubtasks(snapshot, originKey)).filter { it.teamUid != null }.associate { it.id to it.teamUid!! }
        val subtasks = newSubtasks(snapshot, originKey).filter { it.teamUid != null }.mapNotNull { t ->
            val parent = uidById[t.parentId] ?: return@mapNotNull null
            ReportSubtask(t.teamUid!!, parent, t.title, if (t.status == TaskStatus.DONE) TaskStatus.DONE else TaskStatus.TODO)
        }

        return TeamReport(
            reportedAt = now,
            teamUid = origin.teamUid,
            member = PackMember(origin.memberUid, origin.memberName),
            tasks = reported,
            newSubtasks = subtasks,
        )
    }
}
