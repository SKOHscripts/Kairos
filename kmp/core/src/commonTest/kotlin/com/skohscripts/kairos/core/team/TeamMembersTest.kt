package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TeamMembersTest {
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")
    private val today = LocalDate(2026, 10, 14)

    private fun member(id: Long, name: String, self: Boolean = false, archived: Boolean = false) =
        TeamMember(id, "uid-$id", name, hoursPerDay = 7.0, isSelf = self, archived = archived, createdAt = t0, updatedAt = t0)

    private fun absence(id: Long, member: Long, start: LocalDate, end: LocalDate = start) =
        MemberAbsence(id, member, start, end, createdAt = t0)

    private fun task(id: Long, assignee: Long?, status: TaskStatus = TaskStatus.TODO, space: TaskSpace = TaskSpace.TEAM) =
        Task(id, "T$id", status = status, space = space, assigneeId = assignee, createdAt = t0, updatedAt = t0)

    @Test
    fun meComesFirstThenTheNameIgnoringCase() {
        val members = listOf(member(1, "zoé"), member(2, "Bruno"), member(3, "alice"), member(4, "Moi", self = true), member(5, "Émile"))
        // Comparaison lexicographique sur le nom en minuscules : « émile » passe après « zoé » (pas de collation locale dans core).
        assertEquals(listOf(4L, 3L, 2L, 1L, 5L), TeamMembers.ordered(members).map { it.id })
    }

    @Test
    fun sameNamesFallBackOnTheId() {
        val members = listOf(member(9, "Alex"), member(3, "alex"))
        assertEquals(listOf(3L, 9L), TeamMembers.ordered(members).map { it.id })
    }

    @Test
    fun activeAndArchivedMembersAreSplitAndOrdered() {
        val members = listOf(member(1, "Zed"), member(2, "Ann", archived = true), member(3, "Moi", self = true), member(4, "Bob", archived = true), member(5, "Cy"))
        assertEquals(listOf(3L, 5L, 1L), TeamMembers.active(members).map { it.id })
        assertEquals(listOf(2L, 4L), TeamMembers.archived(members).map { it.id })
    }

    @Test
    fun theNextAbsenceIsTheEarliestOneNotYetOver() {
        val absences = listOf(
            absence(1, 1, LocalDate(2026, 10, 1), LocalDate(2026, 10, 5)), // passée
            absence(2, 1, LocalDate(2026, 11, 2), LocalDate(2026, 11, 6)),
            absence(3, 1, LocalDate(2026, 10, 20), LocalDate(2026, 10, 21)),
            absence(4, 2, LocalDate(2026, 10, 15)), // un autre membre
        )
        assertEquals(3L, TeamMembers.nextAbsence(absences, 1, today)?.id)
        assertEquals(4L, TeamMembers.nextAbsence(absences, 2, today)?.id)
        assertNull(TeamMembers.nextAbsence(absences, 3, today))
    }

    @Test
    fun anAbsenceInProgressCountsAndComesBeforeAFutureOne() {
        val absences = listOf(
            absence(1, 1, LocalDate(2026, 10, 20)),
            absence(2, 1, LocalDate(2026, 10, 12), LocalDate(2026, 10, 16)),
        )
        assertEquals(2L, TeamMembers.nextAbsence(absences, 1, today)?.id)
    }

    @Test
    fun anAbsenceEndingTodayIsStillInProgressAndOneEndedYesterdayIsNot() {
        assertEquals(1L, TeamMembers.nextAbsence(listOf(absence(1, 1, LocalDate(2026, 10, 10), today)), 1, today)?.id)
        assertNull(TeamMembers.nextAbsence(listOf(absence(1, 1, LocalDate(2026, 10, 10), LocalDate(2026, 10, 13))), 1, today))
    }

    @Test
    fun absencesOfAMemberAreSortedChronologically() {
        val absences = listOf(
            absence(1, 1, LocalDate(2026, 11, 2)),
            absence(2, 2, LocalDate(2026, 10, 1)),
            absence(3, 1, LocalDate(2026, 10, 20)),
        )
        assertEquals(listOf(3L, 1L), TeamMembers.absencesOf(absences, 1).map { it.id })
    }

    @Test
    fun aMemberHasHadATaskWhateverItsStatusOrSpace() {
        val tasks = listOf(
            task(1, 10, TaskStatus.DONE),
            task(2, 11, TaskStatus.ARCHIVED),
            task(3, 12, TaskStatus.TODO),
            task(4, 13, TaskStatus.TODO, TaskSpace.PERSONAL),
            task(5, null),
        )
        for (id in 10L..13L) assertTrue(TeamMembers.hasHadTask(tasks, id), "$id")
        assertFalse(TeamMembers.hasHadTask(tasks, 14))
        assertFalse(TeamMembers.hasHadTask(emptyList(), 10))
    }

    @Test
    fun openTeamTasksAreTheTodoTeamTasksOfTheMember() {
        val tasks = listOf(
            task(1, 10),
            task(2, 10),
            task(3, 10, TaskStatus.DONE),
            task(4, 10, TaskStatus.ARCHIVED),
            task(5, 10, space = TaskSpace.PERSONAL),
            task(6, 11),
        )
        assertEquals(2, TeamMembers.openTaskCount(tasks, 10))
        assertEquals(1, TeamMembers.openTaskCount(tasks, 11))
        assertEquals(0, TeamMembers.openTaskCount(tasks, 12))
    }

    @Test
    fun anAbsenceAloneIsTeamData() {
        assertFalse(Workspaces.hasTeamData(KairosSnapshot()))
        assertTrue(Workspaces.hasTeamData(KairosSnapshot(absences = listOf(absence(1, 1, today)))))
    }

    @Test
    fun a_member_in_the_journal_has_had_a_task_even_after_the_task_is_gone() {
        fun event(id: Long, member: Long?, kind: TeamEventKind, from: String? = null, to: String? = null) =
            TeamEvent(id, 9, "Supprimée", member, kind, from, to, TeamEventSource.MANUAL, t0)
        val held = listOf(event(1, 1, TeamEventKind.ASSIGNED, null, "1"), event(2, 1, TeamEventKind.DELETED))
        assertTrue(TeamMembers.hasHadTask(emptyList(), 1, held))
        assertFalse(TeamMembers.hasHadTask(emptyList(), 2, held))
        assertFalse(TeamMembers.hasHadTask(emptyList(), 1))
        // Seulement cité comme ancien titulaire par une remise au backlog (archivage) : pas de trace propre.
        assertFalse(TeamMembers.hasHadTask(emptyList(), 3, listOf(event(3, null, TeamEventKind.ASSIGNED, "3", null))))
        // Une tâche à son nom suffit toujours.
        assertTrue(TeamMembers.hasHadTask(listOf(task(1, 4)), 4, held))
    }
}
