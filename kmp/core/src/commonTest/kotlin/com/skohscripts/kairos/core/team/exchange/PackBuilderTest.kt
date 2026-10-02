package com.skohscripts.kairos.core.team.exchange

import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.managerSnapshot
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.member
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.stamp
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.teamTask
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Paquet d'un membre depuis la base du manager (docs/spec/equipe-echanges.md § Formats). */
class PackBuilderTest {
    private val lea = member(1, name = "Léa")
    private val marc = member(2, name = "Marc")

    private fun build(snapshot: KairosSnapshot, memberId: Long = 1) = PackBuilder.build(snapshot, memberId, "pack-1", stamp)

    @Test
    fun theMembersOpenTeamTasksAndTheirSubtasksAreSent() {
        val tasks = listOf(
            teamTask(10, assignee = 1, progress = 40).copy(description = "Détail", deadline = LocalDate(2026, 10, 10), estimatedMinutes = 480, taskType = "Dev"),
            teamTask(11, assignee = 1, parentId = 10),
            teamTask(12, assignee = 1, parentId = 11), // sous-sous-tâche
            teamTask(13, assignee = null, parentId = 10), // sous-tâche sans assigné : suit sa mère
            teamTask(14, assignee = 1, status = TaskStatus.DONE), // faite : pas envoyée
            teamTask(15, assignee = 2), // autre membre
            teamTask(16, assignee = 1, parentId = 14), // sous-tâche d'une tâche faite, mais à faire et assignée : envoyée sans mère
            teamTask(17, assignee = 1, parentId = 10, status = TaskStatus.DONE), // sous-tâche faite : pas envoyée
        )
        val pack = build(managerSnapshot(tasks, listOf(lea, marc)))!!
        assertEquals(listOf("t10", "t11", "t12", "t13", "t16"), pack.tasks.map { it.uid })
        assertEquals(listOf(null, "t10", "t11", "t10", null), pack.tasks.map { it.parentUid })
        val first = pack.tasks.first()
        assertEquals("Détail", first.description)
        assertEquals(LocalDate(2026, 10, 10), first.deadline)
        assertEquals(480, first.estimatedMinutes)
        assertEquals("Dev", first.taskType)
        assertEquals(40, first.progressPercent)
        assertEquals(1, first.priority)
        assertEquals(3, first.fibonacciPoints)
        assertEquals("pack-1", pack.packId)
        assertEquals(stamp, pack.exportedAt)
        assertEquals(PackTeam("team-1", "Équipe Plateforme", "Corentin"), pack.team)
        assertEquals(PackMember("member-1", "Léa"), pack.member)
    }

    @Test
    fun dependenciesStayBetweenTheTasksOfThePack() {
        val tasks = listOf(teamTask(10, assignee = 1), teamTask(11, assignee = 1), teamTask(15, assignee = 1, status = TaskStatus.DONE))
        val deps = listOf(ExchangeFixtures.dep(11, 10), ExchangeFixtures.dep(11, 15), ExchangeFixtures.dep(15, 10))
        val pack = build(managerSnapshot(tasks, listOf(lea), deps))!!
        assertEquals(listOf(PackDependency("t11", "t10")), pack.dependencies)
        // Le bloqueur fait n'est pas un bloqueur externe.
        assertTrue(pack.externalBlockers.isEmpty())
    }

    @Test
    fun aBlockerHeldByAnotherMemberIsAnExternalBlocker() {
        val tasks = listOf(
            teamTask(10, assignee = 1),
            teamTask(20, assignee = 2, title = "API v2"),
            teamTask(21, assignee = null, title = "Au backlog"),
            teamTask(22, assignee = 2, status = TaskStatus.DONE, title = "Faite"),
        )
        val deps = listOf(ExchangeFixtures.dep(10, 20), ExchangeFixtures.dep(10, 21), ExchangeFixtures.dep(10, 22))
        val pack = build(managerSnapshot(tasks, listOf(lea, marc), deps))!!
        assertEquals(
            listOf(ExternalBlocker("t10", "API v2", "Marc"), ExternalBlocker("t10", "Au backlog", null)),
            pack.externalBlockers,
        )
        assertEquals(listOf("t10"), pack.tasks.map { it.uid })
    }

    @Test
    fun noPackWithoutATeamIdentityOrForAnUnknownOrArchivedMember() {
        val snapshot = managerSnapshot(listOf(teamTask(10, assignee = 1)), listOf(lea, member(3, archived = true)))
        assertNotNull(build(snapshot))
        assertNull(build(snapshot, memberId = 99))
        assertNull(build(snapshot, memberId = 3))
        assertNull(build(snapshot.copy(settings = snapshot.settings.copy(team = null))))
        assertNull(build(snapshot.copy(settings = snapshot.settings.copy(team = snapshot.settings.team!!.copy(identity = "")))))
    }

    @Test
    fun aMemberWithoutTasksGetsAnEmptyPack() {
        val pack = build(managerSnapshot(listOf(teamTask(10, assignee = 2)), listOf(lea, marc)))!!
        assertTrue(pack.tasks.isEmpty())
        assertTrue(pack.dependencies.isEmpty())
    }
}
