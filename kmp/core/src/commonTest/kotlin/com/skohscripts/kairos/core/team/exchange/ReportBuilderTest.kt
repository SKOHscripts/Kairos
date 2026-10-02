package com.skohscripts.kairos.core.team.exchange

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.personal
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.received
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.stamp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Rapport d'un membre (docs/spec/equipe-echanges.md § Formats). */
class ReportBuilderTest {
    private val now = Instant.parse("2026-10-03T16:00:00Z")
    private val utc = TimeZone.UTC

    private fun session(id: Long, taskId: Long, start: String, end: String?) =
        WorkSession(id, taskId, Instant.parse(start), end?.let(Instant::parse), stamp)

    private fun build(snapshot: KairosSnapshot, key: String = ExchangeFixtures.TEAM_UID) = ReportBuilder.build(snapshot, key, now, utc)

    @Test
    fun onlyTheTasksReceivedFromThisManagerAreReported() {
        val other = ExchangeFixtures.origin.copy(teamUid = "team-2")
        val snapshot = KairosSnapshot(
            tasks = listOf(
                received(1, "a"), received(2, "b", removed = true), received(3, "x", origin = other), personal(4, "Rendez-vous médecin"),
                received(5, "c", status = TaskStatus.ARCHIVED),
            ),
        )
        val report = build(snapshot)!!
        assertEquals(listOf("a"), report.tasks.map { it.uid })
        assertEquals("team-1", report.teamUid)
        assertEquals(PackMember("member-lea", "Léa"), report.member)
        assertEquals(now, report.reportedAt)
    }

    @Test
    fun noReportWithoutAReceivedTask() {
        assertNull(build(KairosSnapshot(tasks = listOf(personal(1)))))
        assertNull(build(KairosSnapshot(tasks = listOf(received(1, "a", removed = true)))))
    }

    @Test
    fun spentTimeIsTheTotalOfSessionsAndManualTime() {
        val task = received(1, "a").copy(manualTimeSpentMinutes = 30)
        val snapshot = KairosSnapshot(
            tasks = listOf(task),
            workSessions = listOf(
                session(1, 1, "2026-10-01T09:00:00Z", "2026-10-01T10:00:00Z"), // 60
                session(2, 1, "2026-10-02T09:00:00Z", "2026-10-02T09:45:00Z"), // 45
                session(3, 99, "2026-10-02T09:00:00Z", "2026-10-02T12:00:00Z"), // autre tâche
                session(4, 1, "2026-10-03T15:00:00Z", null), // ouverte : 60 min jusqu'à `now`
            ),
        )
        assertEquals(60 + 45 + 60 + 30, build(snapshot)!!.tasks.single().spentMinutes)
    }

    @Test
    fun stateProgressAndStartAreTheMembers() {
        val snapshot = KairosSnapshot(
            tasks = listOf(
                received(1, "done", status = TaskStatus.DONE).copy(updatedAt = Instant.parse("2026-10-02T20:00:00Z"), progressPercent = 40),
                received(2, "going").copy(progressPercent = 60, startedOn = LocalDate(2026, 10, 1)),
                received(3, "sessioned"),
                received(4, "declared").copy(progressPercent = 20),
                received(5, "idle"),
            ),
            workSessions = listOf(session(1, 3, "2026-10-02T09:00:00Z", "2026-10-02T10:00:00Z")),
        )
        val byUid = build(snapshot)!!.tasks.associateBy { it.uid }
        val done = byUid.getValue("done")
        assertEquals(TaskStatus.DONE, done.status)
        assertEquals(LocalDate(2026, 10, 2), done.doneOn)
        assertEquals(100, done.progressPercent) // une tâche faite est à 100
        assertEquals(60, byUid.getValue("going").progressPercent)
        assertEquals(LocalDate(2026, 10, 1), byUid.getValue("going").startedOn)
        assertEquals(LocalDate(2026, 10, 2), byUid.getValue("sessioned").startedOn) // jour de la première session
        assertEquals(LocalDate(2026, 10, 3), byUid.getValue("declared").startedOn) // avancement déclaré sans autre trace
        assertNull(byUid.getValue("idle").startedOn)
        assertNull(byUid.getValue("idle").doneOn)
        assertEquals(TaskStatus.TODO, byUid.getValue("idle").status)
    }

    @Test
    fun newSubtasksAreTheMembersOwnUnderAReceivedTask() {
        val snapshot = KairosSnapshot(
            tasks = listOf(
                received(1, "a"),
                personal(2, "Ma sous-tâche").copy(parentId = 1, teamUid = "s1"),
                personal(3, "Faite").copy(parentId = 1, teamUid = "s2", status = TaskStatus.DONE),
                personal(4, "Sous-sous").copy(parentId = 2, teamUid = "s3"),
                personal(5, "Ailleurs").copy(parentId = 99, teamUid = "s4"),
                personal(6, "Sans identité").copy(parentId = 1),
                received(7, "adoptée", parentId = 1), // déjà reçue : dans `tasks`, pas dans les nouvelles
            ),
        )
        val report = build(snapshot)!!
        assertEquals(
            listOf(
                ReportSubtask("s1", "a", "Ma sous-tâche", TaskStatus.TODO),
                ReportSubtask("s2", "a", "Faite", TaskStatus.DONE),
                ReportSubtask("s3", "s1", "Sous-sous", TaskStatus.TODO),
            ),
            report.newSubtasks,
        )
        assertEquals(listOf("a", "adoptée"), report.tasks.map { it.uid })
        // Le dépôt pose une identité à celle qui n'en a pas avant de construire le rapport.
        assertEquals(listOf(6L), ReportBuilder.subtasksNeedingUid(snapshot, "team-1").map { it.id })
    }

    @Test
    fun nothingPersonalLeavesTheMembersBase() {
        val snapshot = KairosSnapshot(
            tasks = listOf(received(1, "a").copy(description = "secret du manager", projectTag = "P"), personal(2, "Mon secret")),
            notes = listOf(Note(1, "Note intime", createdAt = stamp, updatedAt = stamp)),
            timeBlocks = listOf(TimeBlock(1, "Psy", LocalDateTime(2026, 10, 3, 9, 0), LocalDateTime(2026, 10, 3, 10, 0), createdAt = stamp)),
        )
        val report = build(snapshot)!!
        val flat = report.toString()
        for (secret in listOf("Mon secret", "Note intime", "Psy", "secret du manager")) assertTrue(secret !in flat, secret)
        assertEquals(1, report.tasks.size)
        assertTrue(report.newSubtasks.isEmpty())
    }
}
