package com.skohscripts.kairos.core.team.exchange

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.dep
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.pack
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.packTask
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.personal
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.received
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.stamp
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Réception d'un paquet côté membre (docs/spec/equipe-echanges.md § Réception). */
class PackMergeTest {
    private val now = Instant.parse("2026-10-01T09:00:00Z")

    private fun plan(tasks: List<Task>, pack: TeamPack, deps: List<com.skohscripts.kairos.core.model.TaskDependency> = emptyList(), settings: Settings = Settings()) =
        PackMerge.plan(KairosSnapshot(tasks = tasks, dependencies = deps, settings = settings), pack, now)

    @Test
    fun receivingCreatesPersonalTasksMarkedWithTheirOrigin() {
        val p = pack(
            listOf(
                packTask("a", points = 5, priority = 0).copy(description = "Détail", taskType = "Dev", deadline = LocalDate(2026, 10, 10), estimatedMinutes = 120, progressPercent = 30),
                packTask("b", parent = "a"),
            ),
        )
        val r = plan(listOf(personal(1)), p)
        assertTrue(r.accepted)
        assertEquals(2, r.created.size)
        val a = r.created[0].task
        assertEquals(0, a.id)
        assertEquals("a", a.teamUid)
        assertEquals(com.skohscripts.kairos.core.model.TaskSpace.PERSONAL, a.space)
        assertNull(a.assigneeId)
        assertEquals(ExchangeFixtures.origin.encode(), a.origin)
        assertFalse(a.originRemoved)
        assertEquals(TaskStatus.TODO, a.status)
        assertEquals("Détail", a.description)
        assertEquals(0, a.priority)
        assertEquals(5, a.fibonacciPoints)
        assertEquals("Dev", a.taskType)
        assertEquals(LocalDate(2026, 10, 10), a.deadline)
        assertEquals(120, a.estimatedMinutes)
        assertEquals(30, a.progressPercent)
        assertEquals(now, a.createdAt)
        assertNull(r.created[0].parentUid)
        assertEquals("a", r.created[1].parentUid)
        assertTrue(r.updated.isEmpty() && r.removed.isEmpty() && r.unchanged.isEmpty())
    }

    @Test
    fun aMotherIsCreatedBeforeItsChildrenWhateverTheOrderOfThePack() {
        val p = pack(listOf(packTask("c", parent = "b"), packTask("b", parent = "a"), packTask("a")))
        assertEquals(listOf("a", "b", "c"), plan(emptyList(), p).created.map { it.task.teamUid })
    }

    @Test
    fun invalidValuesOfThePackAreCleaned() {
        val p = pack(listOf(PackTask("a", null, " Titre ", priority = 7, fibonacciPoints = 4, estimatedMinutes = -5, progressPercent = 250)))
        val t = plan(emptyList(), p).created.single().task
        assertEquals("Titre", t.title)
        assertNull(t.priority)
        assertNull(t.fibonacciPoints)
        assertNull(t.estimatedMinutes)
        assertEquals(100, t.progressPercent)
    }

    @Test
    fun receivingTheSamePackTwiceChangesNothing() {
        val p = pack(listOf(packTask("a"), packTask("b", parent = "a")), deps = listOf(PackDependency("b", "a")))
        val first = plan(emptyList(), p)
        // On applique le plan à la main : ids 1 et 2, parent résolu, dépendance posée.
        val tasks = first.created.mapIndexed { i, n -> n.task.copy(id = i + 1L, parentId = if (n.parentUid == null) null else 1) }
        val second = plan(tasks, p, deps = listOf(dep(2, 1)))
        assertTrue(second.created.isEmpty())
        assertTrue(second.updated.isEmpty())
        assertTrue(second.removed.isEmpty())
        assertEquals(2, second.unchanged.size)
        assertTrue(second.dependencyAdds.isEmpty() && second.dependencyRemoves.isEmpty())
        assertTrue(second.isNoop)
    }

    @Test
    fun anUpdateOverwritesTheManagersFieldsAndKeepsTheMembers() {
        val mine = received(5, "a", title = "Ancien titre").copy(
            description = "vieux", priority = 2, fibonacciPoints = 8, taskType = "Ops", deadline = LocalDate(2026, 10, 1), estimatedMinutes = 60,
            // Côté membre :
            status = TaskStatus.DONE, progressPercent = 70, manualTimeSpentMinutes = 45, startedOn = LocalDate(2026, 9, 30),
            scheduledDate = LocalDate(2026, 10, 2), pinnedStart = LocalDate(2026, 10, 2).let { kotlinx.datetime.LocalDateTime(2026, 10, 2, 9, 0) },
            projectTag = "perso", updatedAt = Instant.parse("2026-10-01T08:00:00Z"),
        )
        val p = pack(
            listOf(
                PackTask("a", null, "Nouveau titre", "neuf", 0, 3, "Dev", LocalDate(2026, 10, 20), 240, progressPercent = 10),
            ),
        )
        val r = plan(listOf(mine), p)
        val u = r.updated.single()
        assertEquals(setOf(PackField.TITLE, PackField.DESCRIPTION, PackField.PRIORITY, PackField.POINTS, PackField.CATEGORY, PackField.DEADLINE, PackField.ESTIMATE), u.changed)
        val after = u.after
        assertEquals("Nouveau titre", after.title)
        assertEquals("neuf", after.description)
        assertEquals(0, after.priority)
        assertEquals(3, after.fibonacciPoints)
        assertEquals("Dev", after.taskType)
        assertEquals(LocalDate(2026, 10, 20), after.deadline)
        assertEquals(240, after.estimatedMinutes)
        // Champs du membre : intacts, y compris l'avancement (le paquet n'écrase pas celui du membre).
        assertEquals(mine.copy(
            title = after.title, description = after.description, priority = 0, fibonacciPoints = 3, taskType = "Dev",
            deadline = after.deadline, estimatedMinutes = 240,
        ), after)
        assertEquals(TaskStatus.DONE, after.status)
        assertEquals(70, after.progressPercent)
        assertEquals(45, after.manualTimeSpentMinutes)
        assertEquals(mine.pinnedStart, after.pinnedStart)
        assertEquals(mine.scheduledDate, after.scheduledDate)
        assertEquals(mine.updatedAt, after.updatedAt)
    }

    @Test
    fun removedTasksAreFlaggedNotDeleted() {
        val tasks = listOf(received(1, "a"), received(2, "b"), personal(3))
        val r = plan(tasks, pack(listOf(packTask("a"))))
        assertEquals(listOf(2L), r.removed.map { it.id })
        assertTrue(r.created.isEmpty())
        // Rien n'est jamais supprimé : le plan ne contient aucune suppression, la tâche personnelle n'est pas touchée.
        assertEquals(listOf(1L), r.unchanged.map { it.id })
        // Une tâche déjà retirée et toujours absente ne change plus.
        val again = plan(listOf(received(1, "a"), received(2, "b", removed = true)), pack(listOf(packTask("a"))))
        assertTrue(again.removed.isEmpty())
        assertEquals(listOf(1L, 2L), again.unchanged.map { it.id }.sorted())
        assertTrue(again.isNoop)
    }

    @Test
    fun aRemovedTaskReceivedAgainIsRestored() {
        val r = plan(listOf(received(2, "b", removed = true)), pack(listOf(packTask("b"))))
        val u = r.updated.single()
        assertTrue(u.restored)
        assertFalse(u.after.originRemoved)
        assertTrue(u.isVisible)
        assertEquals(2L, u.after.id)
        assertTrue(r.created.isEmpty())
    }

    @Test
    fun matchingIsByUidAndOriginTeam() {
        val other = ExchangeFixtures.origin.copy(teamUid = "team-2", managerName = "Autre")
        // Même teamUid, autre équipe d'origine : ni mise à jour ni retirée, une nouvelle tâche est créée.
        val tasks = listOf(received(1, "a", origin = other))
        val r = plan(tasks, pack(listOf(packTask("a"))))
        assertEquals(1, r.created.size)
        assertTrue(r.updated.isEmpty() && r.removed.isEmpty())
        // Et les tâches de l'autre équipe ne sont jamais retirées par un paquet de celle-ci.
        val r2 = plan(tasks, pack(emptyList()))
        assertTrue(r2.removed.isEmpty())
    }

    @Test
    fun aTaskKeepsNoTraceOfAnotherTaskWithTheSameUidWithoutOrigin() {
        // Une tâche personnelle sans `teamUid` ou une tâche d'équipe du manager ne sont jamais rattachées.
        val team = personal(1).copy(space = com.skohscripts.kairos.core.model.TaskSpace.TEAM, teamUid = "a")
        val r = plan(listOf(team), pack(listOf(packTask("a"))))
        assertEquals(1, r.created.size)
    }

    @Test
    fun aSubtaskTheMemberAlreadyReportedIsAdoptedNotDuplicated() {
        val mother = received(1, "a")
        val mine = personal(2, "Ma sous-tâche").copy(parentId = 1, teamUid = "s1", priority = 2, fibonacciPoints = 1)
        val p = pack(listOf(packTask("a"), PackTask("s1", "a", "Ma sous-tâche")))
        val r = plan(listOf(mother, mine), p)
        assertTrue(r.created.isEmpty())
        val u = r.updated.single()
        assertTrue(u.adopted)
        assertEquals(ExchangeFixtures.origin.encode(), u.after.origin)
        // Le paquet ne qualifie pas la sous-tâche : les valeurs du membre sont gardées.
        assertEquals(2, u.after.priority)
        assertEquals(1, u.after.fibonacciPoints)
        assertEquals(1L, u.after.parentId)
    }

    @Test
    fun ownTeamPackIsRefused() {
        val settings = Settings(team = TeamSettings(enabled = true, identity = ExchangeFixtures.TEAM_UID))
        val r = plan(emptyList(), pack(listOf(packTask("a"))), settings = settings)
        assertEquals(PackRefusal.OWN_TEAM, r.refusal)
        assertFalse(r.accepted)
        assertTrue(r.created.isEmpty())
    }

    // --- Dépendances ---------------------------------------------------------------------------------------------------

    @Test
    fun theDependenciesOfThePackReplaceThoseBetweenReceivedTasksAndKeepTheMembers() {
        val tasks = listOf(received(1, "a"), received(2, "b"), received(3, "c"), personal(4))
        // Avant : b <- a (reçue) ; mienne : c <- perso 4 ; mienne : perso 4 <- b (membre pose sa tâche derrière une reçue).
        val deps = listOf(dep(2, 1), dep(3, 4), dep(4, 2))
        // Nouveau paquet : c <- b ; plus de b <- a.
        val r = plan(tasks, pack(listOf(packTask("a"), packTask("b"), packTask("c")), deps = listOf(PackDependency("c", "b"))), deps)
        assertEquals(listOf(DependencyRef("c", "b")), r.dependencyAdds)
        assertEquals(listOf(DependencyRef("b", "a")), r.dependencyRemoves)
        assertTrue(r.skippedDependencies.isEmpty())
    }

    @Test
    fun aPackDependencyThatWouldCloseACycleWithTheMembersOwnIsSkipped() {
        val tasks = listOf(received(1, "a"), received(2, "b"), personal(3))
        // Le membre a posé : sa tâche 3 est bloquée par b (reçue) ; b est bloquée par 3 ? non : a bloquée par 3, 3 bloquée par b.
        val deps = listOf(dep(1, 3), dep(3, 2)) // a <- 3 <- b
        // Le paquet voudrait b <- a : boucle a <- 3 <- b <- a.
        val r = plan(tasks, pack(listOf(packTask("a"), packTask("b")), deps = listOf(PackDependency("b", "a"))), deps)
        assertTrue(r.dependencyAdds.isEmpty())
        assertEquals(listOf(DependencyRef("b", "a")), r.skippedDependencies)
    }

    @Test
    fun dependenciesOfNewTasksAreCarriedByTheirUid() {
        val r = plan(emptyList(), pack(listOf(packTask("a"), packTask("b")), deps = listOf(PackDependency("b", "a"), PackDependency("b", "zzz"))))
        assertEquals(listOf(DependencyRef("b", "a")), r.dependencyAdds)
    }

    @Test
    fun aDependencyOnARemovedTaskIsDropped() {
        val tasks = listOf(received(1, "a"), received(2, "gone"))
        val r = plan(tasks, pack(listOf(packTask("a"))), listOf(dep(1, 2)))
        assertEquals(listOf(DependencyRef("a", "gone")), r.dependencyRemoves)
        assertEquals(listOf(2L), r.removed.map { it.id })
    }

    // --- Informations ----------------------------------------------------------------------------------------------------

    @Test
    fun externalBlockersAreReadOnlyInformationOfThePlan() {
        val p = pack(listOf(packTask("a")), external = listOf(ExternalBlocker("a", "API v2", "Marc"), ExternalBlocker("nope", "x", null)))
        val r = plan(emptyList(), p)
        assertEquals(listOf(ExternalBlocker("a", "API v2", "Marc")), r.externalBlockers)
        // Ils n'ajoutent aucune dépendance.
        assertTrue(r.dependencyAdds.isEmpty())
    }

    @Test
    fun aPackForAnotherMemberOfTheSameTeamIsFlagged() {
        val other = ExchangeFixtures.origin.copy(memberUid = "member-marc", memberName = "Marc")
        assertTrue(plan(listOf(received(1, "a", origin = other)), pack(listOf(packTask("a")))).memberChanged)
        assertFalse(plan(listOf(received(1, "a")), pack(listOf(packTask("a")))).memberChanged)
    }

    @Test
    fun namesOfTheOriginAreRefreshedWithoutShowingAnUpdate() {
        val renamed = ExchangeFixtures.origin.copy(teamName = "Ancien nom")
        val r = plan(listOf(received(1, "a", origin = renamed)), pack(listOf(packTask("a"))))
        val u = r.updated.single()
        assertFalse(u.isVisible)
        assertEquals(ExchangeFixtures.origin.encode(), u.after.origin)
        assertTrue(r.visibleUpdates.isEmpty())
        assertEquals(stamp, u.after.updatedAt)
    }
}
