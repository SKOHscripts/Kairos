package com.skohscripts.kairos.core.team.exchange

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.TeamMember
import kotlin.time.Instant

/** Données communes aux tests d'échange (jalon E6) : une équipe, un manager, un membre « Léa » et ses tâches. */
internal object ExchangeFixtures {
    val stamp: Instant = Instant.parse("2026-09-30T07:00:00Z")
    val later: Instant = Instant.parse("2026-10-03T16:00:00Z")

    const val TEAM_UID = "team-1"
    val team = PackTeam(TEAM_UID, "Équipe Plateforme", "Corentin")
    val lea = PackMember("member-lea", "Léa")
    val origin = TeamOrigin(TEAM_UID, "Équipe Plateforme", "Corentin", lea.uid, lea.name)

    fun packTask(uid: String, parent: String? = null, title: String = "Tâche $uid", points: Int? = 3, priority: Int? = 1) =
        PackTask(uid, parent, title, priority = priority, fibonacciPoints = points)

    fun pack(
        tasks: List<PackTask>,
        deps: List<PackDependency> = emptyList(),
        external: List<ExternalBlocker> = emptyList(),
        id: String = "pack-1",
        exportedAt: Instant = stamp,
    ) = TeamPack(id, exportedAt, team, lea, tasks, deps, external)

    /** Tâche personnelle d'un membre (Kairos solo). */
    fun personal(id: Long, title: String = "Perso $id", status: TaskStatus = TaskStatus.TODO) =
        Task(id, title, priority = 1, fibonacciPoints = 2, status = status, createdAt = stamp, updatedAt = stamp)

    /** Tâche reçue d'un manager (côté membre). */
    fun received(
        id: Long,
        uid: String,
        title: String = "Tâche $uid",
        status: TaskStatus = TaskStatus.TODO,
        removed: Boolean = false,
        origin: TeamOrigin = ExchangeFixtures.origin,
        parentId: Long? = null,
    ) = Task(
        id, title, priority = 1, fibonacciPoints = 3, status = status, parentId = parentId, createdAt = stamp, updatedAt = stamp,
        teamUid = uid, origin = origin.encode(), originRemoved = removed,
    )

    fun dep(blocked: Long, blocker: Long) = TaskDependency(blocked * 1000 + blocker, blocked, blocker, stamp)

    // --- Côté manager ---------------------------------------------------------------

    fun member(id: Long, uid: String = "member-$id", name: String = "M$id", archived: Boolean = false, lastReportAt: Instant? = null) =
        TeamMember(id, uid, name, hoursPerDay = 7.0, archived = archived, createdAt = stamp, updatedAt = stamp, lastReportAt = lastReportAt)

    /** Tâche d'équipe du manager. */
    fun teamTask(
        id: Long,
        uid: String = "t$id",
        assignee: Long? = null,
        status: TaskStatus = TaskStatus.TODO,
        parentId: Long? = null,
        progress: Int? = null,
        title: String = "Tâche $id",
    ) = Task(
        id, title, priority = 1, fibonacciPoints = 3, status = status, parentId = parentId, createdAt = stamp, updatedAt = stamp,
        space = TaskSpace.TEAM, assigneeId = assignee, progressPercent = progress, teamUid = uid,
    )

    val managerSettings = Settings(team = TeamSettings(enabled = true, name = "Équipe Plateforme", managerName = "Corentin", identity = TEAM_UID))

    fun managerSnapshot(tasks: List<Task>, members: List<TeamMember>, deps: List<TaskDependency> = emptyList()) =
        KairosSnapshot(tasks = tasks, dependencies = deps, members = members, settings = managerSettings)
}
