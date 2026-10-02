package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TeamStatesTest {
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")
    private val day = LocalDate(2026, 9, 30)

    private fun task(
        id: Long,
        assignee: Long? = null,
        startedOn: LocalDate? = null,
        status: TaskStatus = TaskStatus.TODO,
        space: TaskSpace = TaskSpace.TEAM,
    ) = Task(id, "T$id", status = status, space = space, assigneeId = assignee, startedOn = startedOn, createdAt = t0, updatedAt = t0)

    @Test
    fun the_state_table() {
        assertEquals(TeamState.BACKLOG, TeamStates.of(task(1)))
        assertEquals(TeamState.TODO, TeamStates.of(task(2, assignee = 7)))
        assertEquals(TeamState.IN_PROGRESS, TeamStates.of(task(3, assignee = 7, startedOn = day)))
        assertEquals(TeamState.DONE, TeamStates.of(task(4, assignee = 7, startedOn = day, status = TaskStatus.DONE)))
        assertEquals(TeamState.DONE, TeamStates.of(task(5, status = TaskStatus.DONE)))
        assertNull(TeamStates.of(task(6, assignee = 7, status = TaskStatus.ARCHIVED)))
    }

    @Test
    fun an_unassigned_task_is_in_the_backlog_even_with_a_start_day() {
        // État incohérent (import) : sans assigné, la tâche ne peut pas être « en cours ».
        assertEquals(TeamState.BACKLOG, TeamStates.of(task(1, startedOn = day)))
    }

    @Test
    fun every_open_team_task_is_in_exactly_one_state() {
        val random = Random(20260930)
        repeat(500) { n ->
            val assignee = listOf(null, 1L, 2L, 3L).random(random)
            val started = if (random.nextBoolean()) day else null
            val t = task(n.toLong(), assignee, started)
            val state = TeamStates.of(t)
            val matching = listOf(TeamState.BACKLOG, TeamState.TODO, TeamState.IN_PROGRESS).count { it == state }
            assertEquals(1, matching, "$t")
            assertEquals(assignee == null, state == TeamState.BACKLOG, "$t")
            assertEquals(assignee != null && started != null, state == TeamState.IN_PROGRESS, "$t")
            assertEquals(assignee != null && started == null, state == TeamState.TODO, "$t")
        }
    }

    @Test
    fun every_finished_team_task_is_done_whatever_its_other_fields() {
        val random = Random(7)
        repeat(100) { n ->
            val t = task(n.toLong(), listOf(null, 1L).random(random), if (random.nextBoolean()) day else null, TaskStatus.DONE)
            assertEquals(TeamState.DONE, TeamStates.of(t))
        }
    }

    @Test
    fun blocked_uses_the_team_edges_and_open_blockers_only() {
        fun dep(id: Long, task: Long, blocker: Long) = TaskDependency(id, task, blocker, t0)
        val s = KairosSnapshot(
            tasks = listOf(
                task(1), task(2), // 1 bloquée par 2 (à faire)
                task(3), task(4, status = TaskStatus.DONE), // 3 bloquée par 4 (faite) : libre
                task(5, space = TaskSpace.PERSONAL), task(6), // arête Perso -> Équipe : hors du périmètre d'équipe
                task(7), task(8), // cycle : neutralisé
            ),
            dependencies = listOf(dep(1, 1, 2), dep(2, 3, 4), dep(3, 6, 5), dep(4, 7, 8), dep(5, 8, 7)),
        )
        assertEquals(setOf(1L), TeamStates.blockedIds(s))
    }

    @Test
    fun blocked_does_not_change_the_state() {
        val blocked = task(1, assignee = 7, startedOn = day)
        assertEquals(TeamState.IN_PROGRESS, TeamStates.of(blocked))
        assertTrue(TeamStates.blockedIds(KairosSnapshot(tasks = listOf(blocked, task(2)), dependencies = listOf(TaskDependency(1, 1, 2, t0)))).contains(1L))
    }
}
