package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class WorkspacesTest {
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")

    private fun task(id: Long, space: TaskSpace = TaskSpace.PERSONAL, assignee: Long? = null, status: TaskStatus = TaskStatus.TODO) =
        Task(id, "T$id", status = status, space = space, assigneeId = assignee, createdAt = t0, updatedAt = t0)

    private fun member(id: Long, self: Boolean = false, archived: Boolean = false) =
        TeamMember(id, "uid-$id", "M$id", hoursPerDay = 7.0, isSelf = self, archived = archived, createdAt = t0, updatedAt = t0)

    private fun dep(id: Long, task: Long, blocker: Long) = TaskDependency(id, task, blocker, t0)

    private val enabled = Settings(team = TeamSettings(enabled = true))

    private fun snapshot(
        tasks: List<Task>,
        settings: Settings = enabled,
        members: List<TeamMember> = listOf(member(1, self = true), member(2)),
        dependencies: List<TaskDependency> = emptyList(),
        sessions: List<WorkSession> = emptyList(),
    ) = KairosSnapshot(tasks = tasks, dependencies = dependencies, workSessions = sessions, settings = settings, members = members)

    @Test
    fun a_snapshot_without_team_task_is_returned_as_is() {
        val solo = snapshot(listOf(task(1), task(2)), members = listOf(member(1, self = true)))
        assertSame(solo, Workspaces.personalView(solo))
        val empty = KairosSnapshot()
        assertSame(empty, Workspaces.personalView(empty))
    }

    @Test
    fun the_example_data_is_untouched_in_both_languages() {
        for (language in listOf("fr", "en")) {
            val s = ExampleData.snapshot(LocalDate(2026, 9, 28), t0, language)
            assertSame(s, Workspaces.personalView(s), language)
        }
    }

    @Test
    fun a_disabled_team_space_shows_no_team_task() {
        val tasks = listOf(task(1), task(2, TaskSpace.TEAM, assignee = 1), task(3, TaskSpace.TEAM))
        for (settings in listOf(Settings(), Settings(team = TeamSettings(enabled = false)))) {
            assertEquals(listOf(1L), Workspaces.personalView(snapshot(tasks, settings)).tasks.map { it.id })
        }
    }

    @Test
    fun an_enabled_team_space_only_adds_the_tasks_assigned_to_me() {
        val tasks = listOf(
            task(1),
            task(2, TaskSpace.TEAM, assignee = 1), // moi
            task(3, TaskSpace.TEAM, assignee = 2), // un autre
            task(4, TaskSpace.TEAM), // backlog
        )
        assertEquals(listOf(1L, 2L), Workspaces.personalView(snapshot(tasks)).tasks.map { it.id })
    }

    @Test
    fun an_archived_self_member_or_no_self_member_adds_nothing() {
        val tasks = listOf(task(1), task(2, TaskSpace.TEAM, assignee = 1))
        val archived = snapshot(tasks, members = listOf(member(1, self = true, archived = true)))
        assertEquals(listOf(1L), Workspaces.personalView(archived).tasks.map { it.id })
        val nobody = snapshot(tasks, members = listOf(member(1), member(2)))
        assertEquals(listOf(1L), Workspaces.personalView(nobody).tasks.map { it.id })
    }

    @Test
    fun an_edge_is_kept_when_its_blocker_is_out_of_view_but_still_to_do() {
        val tasks = listOf(task(1), task(2, TaskSpace.TEAM, assignee = 2), task(3, TaskSpace.TEAM, assignee = 2, status = TaskStatus.DONE))
        val deps = listOf(dep(1, 1, 2), dep(2, 1, 3))
        val view = Workspaces.personalView(snapshot(tasks, dependencies = deps))
        // Le bloqueur 2 (hors vue, à faire) bloque toujours ; le 3 (fait) ne bloque plus.
        assertEquals(listOf(1L), view.dependencies.map { it.id })
    }

    @Test
    fun an_edge_to_an_unknown_blocker_is_kept_like_the_day_view_reads_it() {
        val tasks = listOf(task(1), task(2, TaskSpace.TEAM))
        val view = Workspaces.personalView(snapshot(tasks, dependencies = listOf(dep(1, 1, 99))))
        assertEquals(listOf(1L), view.dependencies.map { it.id })
    }

    @Test
    fun an_edge_whose_blocked_task_is_out_of_view_is_dropped() {
        val tasks = listOf(task(1), task(2, TaskSpace.TEAM, assignee = 2), task(3, TaskSpace.TEAM, assignee = 2))
        val view = Workspaces.personalView(snapshot(tasks, dependencies = listOf(dep(1, 2, 3), dep(2, 2, 1))))
        assertTrue(view.dependencies.isEmpty())
    }

    @Test
    fun an_archived_out_of_view_blocker_does_not_block() {
        val tasks = listOf(task(1), task(2, TaskSpace.TEAM, status = TaskStatus.ARCHIVED))
        assertTrue(Workspaces.personalView(snapshot(tasks, dependencies = listOf(dep(1, 1, 2)))).dependencies.isEmpty())
    }

    @Test
    fun sessions_follow_the_kept_tasks() {
        val tasks = listOf(task(1), task(2, TaskSpace.TEAM, assignee = 1), task(3, TaskSpace.TEAM, assignee = 2))
        val sessions = (1L..3L).map { WorkSession(it, it, t0, null, t0) }
        val view = Workspaces.personalView(snapshot(tasks, sessions = sessions))
        assertEquals(listOf(1L, 2L), view.workSessions.map { it.taskId })
    }

    @Test
    fun notes_blocks_settings_and_members_are_unchanged() {
        val s = snapshot(listOf(task(1), task(2, TaskSpace.TEAM)))
        val view = Workspaces.personalView(s)
        assertEquals(s.notes, view.notes)
        assertEquals(s.timeBlocks, view.timeBlocks)
        assertEquals(s.settings, view.settings)
        assertEquals(s.members, view.members)
    }

    @Test
    fun team_data_is_a_member_a_team_task_or_an_assignee() {
        assertFalse(Workspaces.hasTeamData(KairosSnapshot()))
        assertFalse(Workspaces.hasTeamData(snapshot(listOf(task(1)), settings = Settings(), members = emptyList())))
        assertTrue(Workspaces.hasTeamData(snapshot(listOf(task(1)), settings = Settings())))
        assertTrue(Workspaces.hasTeamData(snapshot(listOf(task(1, TaskSpace.TEAM)), members = emptyList())))
        assertTrue(Workspaces.hasTeamData(snapshot(listOf(task(1, assignee = 5)), members = emptyList())))
    }

    @Test
    fun an_unknown_space_code_reads_as_personal() {
        assertEquals(TaskSpace.PERSONAL, TaskSpace.fromCode(42))
        assertEquals(TaskSpace.TEAM, TaskSpace.fromCode(1))
    }
}
