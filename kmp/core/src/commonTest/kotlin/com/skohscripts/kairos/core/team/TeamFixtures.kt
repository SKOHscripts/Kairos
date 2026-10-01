package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Données communes aux tests de charge (jalon E4) : un lundi, des réglages sans focus ni férié, des tâches estimées en heures. */
internal object TeamFixtures {
    /** Lundi 5 octobre 2026 (« J »). */
    val monday = LocalDate(2026, 10, 5)
    val stamp: Instant = Instant.parse("2026-10-01T08:00:00Z")

    /** 7 h du matin UTC : avant le début de la journée de travail, donc la journée de « moi » compte en entier. */
    val mondayMorning: Instant = Instant.parse("2026-10-05T07:00:00Z")

    fun settings(team: TeamSettings = TeamSettings(focusFactor = 1.0)) =
        Settings(holidaysFr = false, team = team.copy(enabled = true))

    fun member(
        id: Long,
        name: String = "M$id",
        hoursPerDay: Double = 8.0,
        percent: Int = 100,
        self: Boolean = false,
        archived: Boolean = false,
    ) = TeamMember(id, "uid-$id", name, availabilityPercent = percent, hoursPerDay = hoursPerDay, isSelf = self, archived = archived, createdAt = stamp, updatedAt = stamp)

    fun absence(id: Long, memberId: Long, start: LocalDate, end: LocalDate) = MemberAbsence(id, memberId, start, end, createdAt = stamp)

    /** Tâche d'équipe à faire, durée estimée en [hours] (`null` + [points] `null` = non estimée). */
    fun task(
        id: Long,
        assignee: Long? = null,
        hours: Double? = 8.0,
        priority: Int? = 1,
        points: Int? = 3,
        deadline: LocalDate? = null,
        type: String = "",
        status: TaskStatus = TaskStatus.TODO,
        progress: Int? = null,
        startedOn: LocalDate? = null,
        updatedAt: Instant = stamp,
        space: TaskSpace = TaskSpace.TEAM,
    ) = Task(
        id, "T$id", priority = priority, deadline = deadline, status = status,
        estimatedMinutes = hours?.let { (it * 60).toInt() }, taskType = type, fibonacciPoints = points,
        createdAt = stamp, updatedAt = updatedAt, space = space, assigneeId = assignee,
        progressPercent = progress, startedOn = startedOn,
    )

    /** [blocked] est bloquée par [blocker]. */
    fun dep(blocked: Long, blocker: Long) = TaskDependency(blocked * 1000 + blocker, blocked, blocker, stamp)

    fun snapshot(
        tasks: List<Task>,
        members: List<TeamMember>,
        absences: List<MemberAbsence> = emptyList(),
        deps: List<TaskDependency> = emptyList(),
        settings: Settings = settings(),
        events: List<TeamEvent> = emptyList(),
    ) = KairosSnapshot(tasks = tasks, members = members, absences = absences, dependencies = deps, settings = settings, teamEvents = events)
}
