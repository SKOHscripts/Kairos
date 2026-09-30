package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.engine.Staleness
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Signaux « à surveiller » portés par une tâche (le signal WIP est porté par un membre). */
enum class TeamSignal { OVERDUE, STALE, NO_PROGRESS, CHURN }

/**
 * Signaux du suivi (docs/spec/equipe-backlog-suivi.md § Signaux). Pur : le jour,
 * le fuseau et les jours fériés sont des paramètres. Seule une tâche **à faire**
 * porte un signal.
 */
object TeamSignals {
    private fun teamSettings(settings: Settings): TeamSettings = settings.team ?: TeamSettings()

    /**
     * Signaux de [task] au jour [day]. [events] : le journal (ou au moins les
     * événements de la tâche) ; [holidays] : les jours fériés à exclure des jours
     * ouvrés (`Workdays.holidaysFor`).
     */
    fun of(
        task: Task,
        events: List<TeamEvent>,
        day: LocalDate,
        timeZone: TimeZone,
        settings: Settings,
        holidays: Set<LocalDate> = emptySet(),
    ): Set<TeamSignal> {
        if (task.status != TaskStatus.TODO) return emptySet()
        val mine = events.filter { it.taskId == task.id }
        val team = teamSettings(settings)
        return buildSet {
            if (Scheduling.isOverdue(task, day)) add(TeamSignal.OVERDUE)
            if (Staleness.daysStale(task, day, settings.staleOverdueDays, settings.staleUntouchedDays) != null) add(TeamSignal.STALE)
            if (noProgress(task, mine, day, timeZone, team.staleProgressDays, holidays)) add(TeamSignal.NO_PROGRESS)
            if (churn(mine) >= team.churnThreshold) add(TeamSignal.CHURN)
        }
    }

    /**
     * Sans avancement : la tâche est **en cours** et son dernier événement de
     * commencement ou d'avancement date de plus de [staleDays] jours ouvrés.
     * Sans événement (base importée), le jour de `startedOn` fait foi.
     */
    fun noProgress(
        task: Task,
        events: List<TeamEvent>,
        day: LocalDate,
        timeZone: TimeZone,
        staleDays: Int,
        holidays: Set<LocalDate> = emptySet(),
    ): Boolean {
        if (TeamStates.of(task) != TeamState.IN_PROGRESS) return false
        val last = events
            .filter { it.taskId == task.id && (it.kind == TeamEventKind.STARTED || it.kind == TeamEventKind.PROGRESS) }
            .maxOfOrNull { it.at }
            ?.toLocalDateTime(timeZone)?.date
            ?: task.startedOn
            ?: return false
        return Workdays.businessDaysBetween(last, day, holidays) > staleDays
    }

    /**
     * Réaffectations d'un membre à un autre : événements `assigned` avec un
     * ancien **et** un nouveau titulaire différents (le premier assignement et
     * les retours au backlog n'en sont pas).
     */
    fun churn(events: List<TeamEvent>): Int =
        events.count { it.kind == TeamEventKind.ASSIGNED && it.fromValue != null && it.toValue != null && it.fromValue != it.toValue }

    /** Tâches **en cours** de [memberId] (à faire, assignées, commencées). */
    fun inProgressCount(tasks: List<Task>, memberId: Long): Int =
        tasks.count { it.space == TaskSpace.TEAM && it.assigneeId == memberId && TeamStates.of(it) == TeamState.IN_PROGRESS }

    /** Trop d'en-cours : plus de `wipLimit` tâches en cours (sans effet si la limite est 0). */
    fun wipExceeded(tasks: List<Task>, memberId: Long, settings: Settings): Boolean {
        val limit = teamSettings(settings).wipLimit
        return limit > 0 && inProgressCount(tasks, memberId) > limit
    }
}
