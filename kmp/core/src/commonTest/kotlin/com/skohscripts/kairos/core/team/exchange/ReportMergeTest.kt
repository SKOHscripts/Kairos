package com.skohscripts.kairos.core.team.exchange

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.later
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.managerSnapshot
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.member
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.teamTask
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Intégration d'un rapport côté manager (docs/spec/equipe-echanges.md § Intégration). */
class ReportMergeTest {
    private val utc = TimeZone.UTC
    private val lea = member(1, uid = "member-lea", name = "Léa")
    private val marc = member(2, name = "Marc")

    private fun snapshot(vararg tasks: com.skohscripts.kairos.core.model.Task, members: List<com.skohscripts.kairos.core.team.TeamMember> = listOf(lea, marc)) =
        managerSnapshot(tasks.toList(), members)

    private fun report(
        tasks: List<ReportTask> = emptyList(),
        subtasks: List<ReportSubtask> = emptyList(),
        at: Instant = later,
        team: String = ExchangeFixtures.TEAM_UID,
        member: String = "member-lea",
    ) = TeamReport(at, team, PackMember(member, "Léa"), tasks, subtasks)

    private fun line(uid: String, status: TaskStatus = TaskStatus.TODO, progress: Int? = null, spent: Int = 0, started: LocalDate? = null, doneOn: LocalDate? = null) =
        ReportTask(uid, status, doneOn, started, progress, spent)

    @Test
    fun anOpenTaskGetsTheMembersProgressStartAndTime() {
        val s = snapshot(teamTask(10, assignee = 1, progress = 40))
        val plan = ReportMerge.plan(s, report(listOf(line("t10", progress = 70, spent = 360, started = LocalDate(2026, 10, 1)))), utc)
        assertTrue(plan.accepted)
        val u = plan.updates.single()
        assertEquals(
            listOf(ReportChange.Started(null, LocalDate(2026, 10, 1)), ReportChange.Progress(40, 70), ReportChange.Spent(null, 360)),
            u.changes,
        )
        assertEquals(70, u.after.progressPercent)
        assertEquals(360, u.after.reportedMinutes)
        assertEquals(LocalDate(2026, 10, 1), u.after.startedOn)
        assertEquals(TaskStatus.TODO, u.after.status)
        assertFalse(u.formerHolder)
        assertNull(u.doneOn)
        assertEquals(lea, plan.member)
        assertFalse(plan.memberArchived)
    }

    @Test
    fun onlyTheMembersFieldsAreApplied() {
        val task = teamTask(10, assignee = 1, progress = 40).copy(description = "d", deadline = LocalDate(2026, 10, 9), estimatedMinutes = 60, taskType = "Dev")
        val u = ReportMerge.plan(snapshot(task), report(listOf(line("t10", progress = 90, spent = 10))), utc).updates.single()
        assertEquals(task.copy(progressPercent = 90, reportedMinutes = 10), u.after)
    }

    @Test
    fun aTaskReportedDoneBecomesDoneWithTheReportedDay() {
        val s = snapshot(teamTask(10, assignee = 1, progress = 40))
        val u = ReportMerge.plan(s, report(listOf(line("t10", TaskStatus.DONE, progress = 100, spent = 90, doneOn = LocalDate(2026, 10, 2)))), utc).updates.single()
        assertEquals(TaskStatus.DONE, u.after.status)
        assertEquals(100, u.after.progressPercent)
        assertEquals(LocalDate(2026, 10, 2), u.doneOn)
        assertTrue(ReportChange.Status(TaskStatus.TODO, TaskStatus.DONE) in u.changes)
        // Sans jour dans le rapport : le jour du rapport.
        val noDay = ReportMerge.plan(s, report(listOf(line("t10", TaskStatus.DONE))), utc).updates.single()
        assertEquals(LocalDate(2026, 10, 3), noDay.doneOn)
    }

    @Test
    fun aDoneTaskReopenedByTheMemberGoesBackToTodo() {
        val s = snapshot(teamTask(10, assignee = 1, progress = 100, status = TaskStatus.DONE))
        val u = ReportMerge.plan(s, report(listOf(line("t10", TaskStatus.TODO, progress = 50))), utc).updates.single()
        assertEquals(TaskStatus.TODO, u.after.status)
        assertEquals(50, u.after.progressPercent)
        assertNull(u.doneOn)
        val noProgress = ReportMerge.plan(s, report(listOf(line("t10", TaskStatus.TODO))), utc).updates.single()
        assertEquals(0, noProgress.after.progressPercent)
    }

    @Test
    fun aReportWithNothingNewChangesNothing() {
        val task = teamTask(10, assignee = 1, progress = 40).copy(reportedMinutes = 120, startedOn = LocalDate(2026, 10, 1))
        val plan = ReportMerge.plan(snapshot(task), report(listOf(line("t10", progress = 40, spent = 120, started = LocalDate(2026, 10, 1)))), utc)
        assertTrue(plan.updates.isEmpty())
        assertEquals(listOf(task), plan.unchanged)
    }

    @Test
    fun anOlderOrEqualReportIsRefused() {
        val last = Instant.parse("2026-09-28T10:00:00Z")
        val s = managerSnapshot(listOf(teamTask(10, assignee = 1)), listOf(member(1, uid = "member-lea", lastReportAt = last)))
        val older = ReportMerge.plan(s, report(listOf(line("t10", progress = 90)), at = Instant.parse("2026-09-27T10:00:00Z")), utc)
        assertEquals(ReportRefusal.OLDER, older.refusal)
        assertEquals(last, older.lastReportAt)
        assertTrue(older.updates.isEmpty())
        assertEquals(ReportRefusal.OLDER, ReportMerge.plan(s, report(listOf(line("t10", progress = 90)), at = last), utc).refusal)
        assertTrue(ReportMerge.plan(s, report(listOf(line("t10", progress = 90)), at = Instant.parse("2026-09-28T10:00:01Z")), utc).accepted)
    }

    @Test
    fun aReportForAnotherTeamIsRefused() {
        val s = snapshot(teamTask(10, assignee = 1))
        val plan = ReportMerge.plan(s, report(listOf(line("t10", progress = 90)), team = "team-autre"), utc)
        assertEquals(ReportRefusal.WRONG_TEAM, plan.refusal)
        assertTrue(plan.updates.isEmpty())
        // Une base sans espace Équipe refuse aussi.
        assertEquals(ReportRefusal.WRONG_TEAM, ReportMerge.plan(KairosSnapshot(settings = Settings()), report(), utc).refusal)
    }

    @Test
    fun anUnknownMemberIsRefused() {
        val plan = ReportMerge.plan(snapshot(teamTask(10, assignee = 1)), report(member = "member-inconnu"), utc)
        assertEquals(ReportRefusal.UNKNOWN_MEMBER, plan.refusal)
    }

    @Test
    fun aReassignedTaskIsIntegratedAndFlagged() {
        val s = snapshot(teamTask(10, assignee = 2, progress = 0), teamTask(11, assignee = null))
        val plan = ReportMerge.plan(s, report(listOf(line("t10", progress = 50), line("t11", progress = 20))), utc)
        assertEquals(2, plan.updates.size)
        assertTrue(plan.updates.all { it.formerHolder })
        assertEquals(2, plan.fromFormerHolder.size)
        assertEquals(50, plan.updates.first().after.progressPercent)
        // L'assigné ne change pas.
        assertEquals(2L, plan.updates.first().after.assigneeId)
    }

    @Test
    fun anUnknownTaskIsIgnoredAndListed() {
        val plan = ReportMerge.plan(snapshot(teamTask(10, assignee = 1)), report(listOf(line("disparue", progress = 50), line("t10", progress = 10))), utc)
        assertEquals(listOf(IgnoredReportLine("disparue", null, IgnoredReason.UNKNOWN_TASK)), plan.ignored)
        assertEquals(1, plan.updates.size)
    }

    @Test
    fun aPersonalTaskWithTheSameUidIsNotAnInterlocutor() {
        val personal = ExchangeFixtures.personal(10).copy(teamUid = "t10")
        val plan = ReportMerge.plan(managerSnapshot(listOf(personal), listOf(lea)), report(listOf(line("t10", progress = 50))), utc)
        assertEquals(IgnoredReason.UNKNOWN_TASK, plan.ignored.single().reason)
    }

    @Test
    fun anArchivedMemberIsIntegratedWithAWarning() {
        val s = managerSnapshot(listOf(teamTask(10, assignee = 1, progress = 0)), listOf(member(1, uid = "member-lea", archived = true)))
        val plan = ReportMerge.plan(s, report(listOf(line("t10", progress = 50))), utc)
        assertTrue(plan.accepted)
        assertTrue(plan.memberArchived)
        assertEquals(1, plan.updates.size)
    }

    @Test
    fun newSubtasksBecomeTeamTasksOfTheMothersAssignee() {
        val s = snapshot(teamTask(10, assignee = 1))
        val plan = ReportMerge.plan(
            s,
            report(
                subtasks = listOf(
                    ReportSubtask("s1", "t10", "Écrire les tests"),
                    ReportSubtask("s2", "s1", "Cas limites", TaskStatus.DONE),
                    ReportSubtask("s3", "inconnue", "Orpheline"),
                    ReportSubtask("s4", "t10", "  "),
                ),
            ),
            utc,
        )
        assertEquals(
            listOf(
                NewTeamSubtask("s1", "t10", 10, "Écrire les tests", TaskStatus.TODO, 1),
                NewTeamSubtask("s2", "s1", null, "Cas limites", TaskStatus.DONE, 1),
            ),
            plan.newSubtasks,
        )
        assertEquals(
            listOf(IgnoredReportLine("s3", "Orpheline", IgnoredReason.UNKNOWN_PARENT), IgnoredReportLine("s4", "  ", IgnoredReason.EMPTY_TITLE)),
            plan.ignored,
        )
    }

    @Test
    fun aSubtaskReportedTwiceIsNotCreatedTwice() {
        val known = teamTask(20, uid = "s1", assignee = 1, parentId = 10)
        val s = snapshot(teamTask(10, assignee = 1), known)
        val plan = ReportMerge.plan(s, report(subtasks = listOf(ReportSubtask("s1", "t10", "Écrire les tests"))), utc)
        assertTrue(plan.newSubtasks.isEmpty())
        assertEquals(listOf(known), plan.unchanged)
        // Mais son statut suit.
        val done = ReportMerge.plan(s, report(subtasks = listOf(ReportSubtask("s1", "t10", "Écrire les tests", TaskStatus.DONE))), utc)
        assertTrue(done.newSubtasks.isEmpty())
        assertEquals(TaskStatus.DONE, done.updates.single().after.status)
        assertEquals(100, done.updates.single().after.progressPercent)
    }

    @Test
    fun theReportedTimeIsATotalThatReplacesThePreviousOne() {
        val first = ReportMerge.plan(snapshot(teamTask(10, assignee = 1)), report(listOf(line("t10", spent = 360))), utc).updates.single().after
        assertEquals(360, first.reportedMinutes)
        // Deuxième rapport : total 480 (et non 360 + 480).
        val s2 = snapshot(first)
        val second = ReportMerge.plan(s2, report(listOf(line("t10", spent = 480)), at = later.plus(kotlin.time.Duration.parse("2d"))), utc).updates.single()
        assertEquals(480, second.after.reportedMinutes)
        assertEquals(ReportChange.Spent(360, 480), second.changes.single())
    }

    @Test
    fun aDuplicatedLineIsAppliedOnce() {
        val plan = ReportMerge.plan(snapshot(teamTask(10, assignee = 1)), report(listOf(line("t10", progress = 10), line("t10", progress = 90))), utc)
        assertEquals(10, plan.updates.single().after.progressPercent)
    }
}
