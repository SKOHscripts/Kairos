package com.skohscripts.kairos.core.team.exchange

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.team.TeamMember
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Pourquoi un rapport n'est pas intégré (rien n'est changé). */
enum class ReportRefusal {
    /** Le rapport est adressé à une autre équipe (autre manager), ou cette base n'a pas d'espace Équipe. */
    WRONG_TEAM,

    /** Le membre du rapport n'existe plus dans l'équipe (fiche supprimée). */
    UNKNOWN_MEMBER,

    /** Le rapport n'est pas plus récent que le dernier intégré pour ce membre ([ReportMergePlan.lastReportAt]). */
    OLDER,
}

/** Pourquoi une ligne du rapport est ignorée. */
enum class IgnoredReason {
    /** Tâche inconnue du manager (supprimée depuis). */
    UNKNOWN_TASK,

    /** Mère d'une nouvelle sous-tâche inconnue du manager. */
    UNKNOWN_PARENT,

    /** Nouvelle sous-tâche sans titre. */
    EMPTY_TITLE,
}

/** Ce qu'un rapport change sur une tâche (de → vers), pour l'aperçu et le journal. */
sealed interface ReportChange {
    data class Status(val from: TaskStatus, val to: TaskStatus) : ReportChange
    data class Progress(val from: Int?, val to: Int?) : ReportChange
    data class Started(val from: LocalDate?, val to: LocalDate?) : ReportChange

    /** Total de temps rapporté, en minutes ([from] `null` : premier rapport). */
    data class Spent(val from: Int?, val to: Int) : ReportChange
}

/**
 * Mise à jour d'une tâche d'équipe par le rapport. [formerHolder] : la tâche n'est plus assignée au membre qui
 * rapporte (réaffectée ou remise au backlog depuis le paquet) : l'avancement est intégré, signalé « reçu de l'ancien
 * titulaire ». [doneOn] : jour de fin à journaliser, pour une tâche qui devient faite.
 */
data class ReportTaskUpdate(
    val before: Task,
    val after: Task,
    val changes: List<ReportChange>,
    val formerHolder: Boolean,
    val doneOn: LocalDate? = null,
)

/**
 * Sous-tâche à créer chez le manager, tâche d'équipe du même assigné que sa mère ([assigneeId]). [parentTaskId] : la
 * mère si elle existe déjà ; sinon [parentUid] désigne une autre nouvelle sous-tâche du même rapport, créée avant.
 */
data class NewTeamSubtask(
    val uid: String,
    val parentUid: String,
    val parentTaskId: Long?,
    val title: String,
    val status: TaskStatus,
    val assigneeId: Long?,
)

/** Ligne ignorée : [uid], [title] (sous-tâches seulement) et la raison. */
data class IgnoredReportLine(val uid: String, val title: String?, val reason: IgnoredReason)

/**
 * Plan d'intégration d'un rapport, côté manager (docs/spec/equipe-echanges.md § Intégration) : l'aperçu lisible (ce qui
 * change, de → vers) que l'interface affiche avant d'appliquer, et ce que `KairosRepository.integrateReport` écrit.
 *
 * - [refusal] non nul : rien n'est appliqué, les autres listes sont vides ;
 * - [member] : le membre du rapport ; [memberArchived] : il est archivé (le travail est réel, l'intégration est faite avec avertissement) ;
 * - [updates] : tâches changées ; [unchanged] : tâches déjà à jour ; [newSubtasks] : sous-tâches à créer ; [ignored] : lignes écartées.
 */
data class ReportMergePlan(
    val refusal: ReportRefusal? = null,
    val reportedAt: Instant,
    val member: TeamMember? = null,
    val memberArchived: Boolean = false,
    val lastReportAt: Instant? = null,
    val updates: List<ReportTaskUpdate> = emptyList(),
    val unchanged: List<Task> = emptyList(),
    val newSubtasks: List<NewTeamSubtask> = emptyList(),
    val ignored: List<IgnoredReportLine> = emptyList(),
) {
    val accepted: Boolean get() = refusal == null

    /** Mises à jour signalées « avancement reçu de l'ancien titulaire ». */
    val fromFormerHolder: List<ReportTaskUpdate> get() = updates.filter { it.formerHolder }
}

/**
 * Fusion d'un rapport dans la base du manager (docs/spec/equipe-echanges.md § Intégration). Pur.
 *
 * Seuls les champs **du membre** sont appliqués : état, avancement, commencement, temps passé. Ceux du manager
 * (titre, priorité, assigné, échéance…) ne bougent pas.
 */
object ReportMerge {
    fun plan(snapshot: KairosSnapshot, report: TeamReport, timeZone: TimeZone): ReportMergePlan {
        fun refused(reason: ReportRefusal, member: TeamMember? = null) =
            ReportMergePlan(reason, report.reportedAt, member, lastReportAt = member?.lastReportAt)

        val identity = snapshot.settings.team?.identity.orEmpty()
        if (identity.isEmpty() || identity != report.teamUid) return refused(ReportRefusal.WRONG_TEAM)
        val member = snapshot.members.firstOrNull { it.uid == report.member.uid } ?: return refused(ReportRefusal.UNKNOWN_MEMBER)
        val last = member.lastReportAt
        if (last != null && report.reportedAt <= last) return refused(ReportRefusal.OLDER, member)

        val reportDay = report.reportedAt.toLocalDateTime(timeZone).date
        val teamTasks = snapshot.tasks.filter { it.space == TaskSpace.TEAM && it.teamUid != null }
        val byUid = LinkedHashMap<String, Task>()
        teamTasks.sortedBy { it.id }.forEach { byUid.getOrPut(it.teamUid!!) { it } }

        val updates = ArrayList<ReportTaskUpdate>()
        val unchanged = ArrayList<Task>()
        val ignored = ArrayList<IgnoredReportLine>()
        val seen = HashSet<String>()

        for (line in report.tasks) {
            if (!seen.add(line.uid)) continue
            val task = byUid[line.uid]
            if (task == null) {
                ignored += IgnoredReportLine(line.uid, null, IgnoredReason.UNKNOWN_TASK)
                continue
            }
            val update = apply(task, member, line.status, line.doneOn ?: reportDay, line.startedOn, line.progressPercent, line.spentMinutes)
            if (update == null) unchanged += task else updates += update
        }

        val created = ArrayList<NewTeamSubtask>()
        val assigneeOfNew = HashMap<String, Long?>()
        for (sub in report.newSubtasks) {
            if (!seen.add(sub.uid)) continue
            val known = byUid[sub.uid]
            if (known != null) {
                // Déjà remontée par un rapport précédent : seul son statut peut avoir changé.
                val update = apply(known, member, sub.status, reportDay, null, null, null)
                if (update == null) unchanged += known else updates += update
                continue
            }
            val parent = byUid[sub.parentUid]
            when {
                sub.title.isBlank() -> ignored += IgnoredReportLine(sub.uid, sub.title, IgnoredReason.EMPTY_TITLE)
                parent != null -> {
                    created += NewTeamSubtask(sub.uid, sub.parentUid, parent.id, sub.title, sub.status, parent.assigneeId)
                    assigneeOfNew[sub.uid] = parent.assigneeId
                }
                sub.parentUid in assigneeOfNew -> {
                    created += NewTeamSubtask(sub.uid, sub.parentUid, null, sub.title, sub.status, assigneeOfNew[sub.parentUid])
                    assigneeOfNew[sub.uid] = assigneeOfNew[sub.parentUid]
                }
                else -> ignored += IgnoredReportLine(sub.uid, sub.title, IgnoredReason.UNKNOWN_PARENT)
            }
        }

        return ReportMergePlan(
            reportedAt = report.reportedAt,
            member = member,
            memberArchived = member.archived,
            lastReportAt = last,
            updates = updates,
            unchanged = unchanged,
            newSubtasks = created,
            ignored = ignored,
        )
    }

    /**
     * La mise à jour de [task] par une ligne de rapport, ou `null` si rien ne change. [spent] `null` : la ligne ne
     * porte pas de temps (sous-tâche déjà remontée). Règles :
     * - statut : celui du membre (une tâche archivée garde le sien) ; faite -> avancement 100, jour de fin [doneOn] ;
     *   rouverte -> avancement du rapport, à défaut 0 ;
     * - avancement : celui du rapport s'il est donné, sinon celui du manager ; borné à 0-100, sans arrondi ;
     * - commencement : celui du rapport s'il est donné (la valeur du membre l'emporte), sinon celui du manager ;
     * - temps : le total du rapport **remplace** le précédent total rapporté (jamais d'addition de deux rapports).
     */
    private fun apply(
        task: Task,
        member: TeamMember,
        status: TaskStatus,
        doneOn: LocalDate,
        startedOn: LocalDate?,
        progress: Int?,
        spent: Int?,
    ): ReportTaskUpdate? {
        var after = task
        val changes = ArrayList<ReportChange>()

        if (startedOn != null && startedOn != task.startedOn) {
            after = after.copy(startedOn = startedOn)
            changes += ReportChange.Started(task.startedOn, startedOn)
        }

        val target = if (task.status == TaskStatus.ARCHIVED) task.status else status
        val reopened = task.status == TaskStatus.DONE && target == TaskStatus.TODO
        val newProgress = when {
            target == TaskStatus.DONE -> 100
            reopened -> (progress ?: 0).coerceIn(0, 100)
            else -> (progress ?: task.progressPercent)?.coerceIn(0, 100)
        }
        if ((newProgress ?: 0) != (task.progressPercent ?: 0)) {
            after = after.copy(progressPercent = newProgress)
            changes += ReportChange.Progress(task.progressPercent, newProgress)
        }

        if (spent != null) {
            val total = spent.coerceAtLeast(0)
            if (total != (task.reportedMinutes ?: 0)) {
                after = after.copy(reportedMinutes = total)
                changes += ReportChange.Spent(task.reportedMinutes, total)
            }
        }

        var doneDay: LocalDate? = null
        if (target != task.status) {
            after = after.copy(status = target)
            changes += ReportChange.Status(task.status, target)
            if (target == TaskStatus.DONE) doneDay = doneOn
        }

        if (changes.isEmpty()) return null
        return ReportTaskUpdate(task, after, changes, formerHolder = task.assigneeId != member.id, doneOn = doneDay)
    }
}
