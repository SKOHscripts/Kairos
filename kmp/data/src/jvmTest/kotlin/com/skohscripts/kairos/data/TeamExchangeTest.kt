package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.TeamEvent
import com.skohscripts.kairos.core.team.TeamEventKind
import com.skohscripts.kairos.core.team.TeamEventSource
import com.skohscripts.kairos.core.team.TeamBoard
import com.skohscripts.kairos.core.team.exchange.IgnoredReason
import com.skohscripts.kairos.core.team.exchange.PackRefusal
import com.skohscripts.kairos.core.team.exchange.ReceivedTasks
import com.skohscripts.kairos.core.team.exchange.ReportChange
import com.skohscripts.kairos.core.team.exchange.ReportRefusal
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Espace Équipe, jalon E6 (docs/spec/equipe-echanges.md) : paquets et rapports entre deux bases en mémoire, l'une
 * pour le manager (Corentin), l'autre pour un membre qui n'a jamais activé l'espace Équipe (Léa).
 */
class TeamExchangeTest {
    /** Horloge commune aux deux bases, que les tests font avancer. */
    private class Tick(var current: Instant) : Clock {
        override fun now() = current
        fun at(iso: String) {
            current = Instant.parse(iso)
        }
    }

    private val tick = Tick(Instant.parse("2026-09-30T07:00:00Z"))
    private val utc = TimeZone.UTC
    private val day = LocalDate(2026, 9, 30)

    private fun driver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    private suspend fun blank(): KairosRepository =
        KairosRepository.open(KairosStore.open(driver()), { KairosSnapshot() }, tick, timeZone = utc)

    /** Le manager : équipe « Équipe Plateforme », Léa et Marc, quatre tâches (voir [Manager]). */
    private class Manager(
        val repo: KairosRepository, val lea: Long, val marc: Long,
        val a: Long, val a1: Long, val b: Long, val c: Long,
    ) {
        val snap get() = repo.snapshot.value
        fun task(id: Long) = snap.tasks.single { it.id == id }
        fun events(id: Long) = snap.teamEvents.filter { it.taskId == id }
        val leaUid get() = snap.members.single { it.id == lea }.uid
    }

    private suspend fun manager(): Manager {
        val repo = blank()
        repo.updateSettings(Settings(team = TeamSettings(enabled = true, name = "Équipe Plateforme", managerName = "Corentin")))
        val lea = repo.createMember("Léa", "Dev", 100, 7.0, false)!!
        val marc = repo.createMember("Marc", "Dev", 100, 7.0, false)!!
        val a = repo.createTeamTask("Migration API")!!
        val c = repo.createTeamTask("API v2")!!
        repo.updateTask(
            a,
            TaskEdit(
                title = "Migration API", description = "Brief du manager", priority = 1, points = 5, taskType = "Développement",
                deadline = LocalDate(2026, 10, 10), estimatedMinutes = 480, pinDay = day, reassign = Reassignment(lea),
            ),
        )
        val a1 = repo.createTeamTask("Écrire les migrations", a)!! // prend l'assigné de sa mère
        val b = repo.createTeamTask("Recette")!!
        repo.updateTask(b, TaskEdit(title = "Recette", priority = 2, points = 2, pinDay = day, reassign = Reassignment(lea), blockerIds = setOf(a, c)))
        repo.updateTask(c, TaskEdit(title = "API v2", priority = 1, points = 3, pinDay = day, reassign = Reassignment(marc)))
        repo.setProgress(a, 40)
        return Manager(repo, lea, marc, a, a1, b, c)
    }

    private class Member(val repo: KairosRepository) {
        val snap get() = repo.snapshot.value
        fun byUid(uid: String) = snap.tasks.single { it.teamUid == uid }
    }

    /** Un membre avec ses affaires personnelles (tâche, note, créneau, session) et aucun espace Équipe. */
    private suspend fun member(): Member {
        val repo = blank()
        val mine = repo.createTask("Rendez-vous médecin")!!
        repo.setPriority(mine, 1)
        repo.setPoints(mine, 2)
        repo.createNote("Idée de démission")
        repo.createBlock(BlockEdit("Thérapie", LocalDateTime(2026, 10, 3, 9, 0), LocalDateTime(2026, 10, 3, 10, 0)))
        repo.startTimer(mine)
        tick.current += 30.minutes
        repo.stopTimer()
        return Member(repo)
    }

    /** Tout ce qui est au membre : ses tâches (hors reçues et sous-tâches remontées), leurs sessions et dépendances, notes, créneaux, réglages. */
    private fun personalPart(s: KairosSnapshot, reportedSubtasks: Set<String> = emptySet()): KairosSnapshot {
        val tasks = s.tasks.filter { it.origin == null && it.title !in reportedSubtasks }
        val ids = tasks.mapTo(HashSet()) { it.id }
        return s.copy(
            tasks = tasks,
            workSessions = s.workSessions.filter { it.taskId in ids },
            dependencies = s.dependencies.filter { it.taskId in ids && it.blockerId in ids },
        )
    }

    // --- Paquet : côté manager --------------------------------------------------------------------------------------------

    @Test
    fun exportPackBuildsTheMembersPackAndJournalsEachTask() = runTest {
        val m = manager()
        val before = m.snap.teamEvents.size
        tick.at("2026-09-30T08:00:00Z")
        val text = m.repo.exportPack(m.lea, "pack-1")!!
        assertEquals(TeamExchangeCodec.Kind.PACK, TeamExchangeCodec.detect(text))
        val pack = TeamExchangeCodec.decodePack(text)
        assertEquals("pack-1", pack.packId)
        assertEquals(Instant.parse("2026-09-30T08:00:00Z"), pack.exportedAt)
        assertEquals(m.snap.settings.team!!.identity, pack.team.uid)
        assertEquals("Équipe Plateforme", pack.team.name)
        assertEquals("Corentin", pack.team.manager)
        assertEquals(m.leaUid, pack.member.uid)
        assertEquals(listOf("Migration API", "Écrire les migrations", "Recette"), pack.tasks.map { it.title })
        assertEquals(m.task(m.a).teamUid, pack.tasks[1].parentUid)
        assertEquals(40, pack.tasks[0].progressPercent)
        // b est bloquée par a (dans le paquet) et par c (à Marc : externe).
        assertEquals(1, pack.dependencies.size)
        assertEquals("API v2", pack.externalBlockers.single().title)
        assertEquals("Marc", pack.externalBlockers.single().assignee)
        // Journal : « envoyée dans un paquet » sur chaque tâche du paquet, pas sur les autres.
        val sent = m.snap.teamEvents.drop(before).filter { it.kind == TeamEventKind.SENT }
        assertEquals(setOf(m.a, m.a1, m.b), sent.map { it.taskId }.toSet())
        assertEquals(3, sent.size)
        assertTrue(sent.all { it.toValue == "pack-1" && it.memberId == m.lea && it.source == TeamEventSource.MANUAL })
        assertTrue(m.events(m.c).none { it.kind == TeamEventKind.SENT })
    }

    @Test
    fun exportPackRefusesAnUnknownOrArchivedMemberAndASoloBase() = runTest {
        val m = manager()
        assertNull(m.repo.exportPack(999))
        m.repo.archiveMember(m.marc)
        assertNull(m.repo.exportPack(m.marc))
        assertNull(member().repo.exportPack(1))
        // Aucun événement écrit par les refus.
        assertTrue(m.snap.teamEvents.none { it.kind == TeamEventKind.SENT })
        // Un identifiant de paquet est tiré par le dépôt s'il n'est pas donné.
        val pack = TeamExchangeCodec.decodePack(m.repo.exportPack(m.lea)!!)
        assertTrue(pack.packId.length >= 32)
    }

    // --- Paquet : côté membre ---------------------------------------------------------------------------------------------

    @Test
    fun previewWritesNothingAndReceiveCreatesPersonalTasks() = runTest {
        val m = manager()
        val text = m.repo.exportPack(m.lea, "pack-1")!!
        val lea = member()
        val before = lea.snap

        val preview = lea.repo.previewPack(text)
        assertTrue(preview.accepted)
        assertEquals(3, preview.created.size)
        assertEquals("Corentin", preview.origin.managerName)
        assertEquals("Équipe Plateforme", preview.origin.teamName)
        assertEquals(listOf("API v2"), preview.externalBlockers.map { it.title })
        assertEquals(before, lea.snap) // l'aperçu n'écrit rien

        val report = lea.repo.receivePack(text)
        assertTrue(report.applied)
        val s = lea.snap
        assertEquals(before.tasks.size + 3, s.tasks.size)
        val a = lea.byUid(m.task(m.a).teamUid!!)
        assertEquals(TaskSpace.PERSONAL, a.space)
        assertNull(a.assigneeId)
        assertEquals("Migration API", a.title)
        assertEquals("Brief du manager", a.description)
        assertEquals(480, a.estimatedMinutes)
        assertEquals(5, a.fibonacciPoints)
        assertEquals(40, a.progressPercent)
        assertNotNull(ReceivedTasks.originOf(a))
        assertEquals(a.id, lea.byUid(m.task(m.a1).teamUid!!).parentId)
        // La dépendance du paquet est posée ; le bloqueur externe ne l'est pas.
        val bId = lea.byUid(m.task(m.b).teamUid!!).id
        assertEquals(listOf(a.id), s.dependencies.filter { it.taskId == bId }.map { it.blockerId })
        // Elle est dans l'espace Perso, comme une tâche personnelle ordinaire, sans espace Équipe activé.
        assertTrue(lea.repo.personalSnapshot.value.tasks.any { it.id == a.id })
        assertNull(s.settings.team)
        assertTrue(s.members.isEmpty() && s.teamEvents.isEmpty())
        // Aucun événement de journal côté membre.
        assertTrue(s.teamEvents.isEmpty())
        // Origines proposées pour renvoyer l'avancement.
        assertEquals(listOf("Corentin"), lea.repo.origins().map { it.managerName })
    }

    @Test
    fun receivingTwiceTheSamePackCreatesNoDuplicate() = runTest {
        val m = manager()
        val text = m.repo.exportPack(m.lea, "pack-1")!!
        val lea = member()
        lea.repo.receivePack(text)
        val first = lea.snap
        val again = lea.repo.receivePack(text)
        assertTrue(again.applied)
        assertTrue(again.plan.isNoop)
        assertEquals(3, again.plan.unchanged.size)
        assertEquals(first.tasks, lea.snap.tasks)
        assertEquals(first.dependencies, lea.snap.dependencies)
    }

    @Test
    fun anUpdateOverwritesTheManagersFieldsAndKeepsTheMembersAndRemovedTasksAreFlagged() = runTest {
        val m = manager()
        val lea = member()
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-1")!!)
        val aUid = m.task(m.a).teamUid!!
        val bUid = m.task(m.b).teamUid!!
        val a = lea.byUid(aUid)

        // Le membre travaille : avancement, saisie manuelle, chrono, sa propre note, une dépendance de sa tâche personnelle.
        tick.at("2026-10-01T09:00:00Z")
        lea.repo.setReceivedProgress(a.id, 70)
        lea.repo.updateTask(a.id, TaskEdit(title = "Titre local", description = "ma description", priority = 2, points = 8, manualTimeSpentMinutes = 45, pinDay = day, taskType = "Local"))
        lea.repo.startTimer(a.id)
        tick.current += 20.minutes
        lea.repo.stopTimer()
        val mine = lea.snap.tasks.first { it.origin == null }
        lea.repo.updateTask(mine.id, TaskEdit(title = mine.title, priority = 1, points = 2, pinDay = day, blockerIds = setOf(a.id)))
        val worked = lea.byUid(aUid)

        // Le manager change ses champs, retire la sous-tâche (supprimée) et la recette (réaffectée à Marc).
        val a1Uid = m.task(m.a1).teamUid!!
        m.repo.updateTask(
            m.a,
            TaskEdit(title = "Migration API v2", description = "Nouveau brief", priority = 0, points = 13, taskType = "Ops", deadline = LocalDate(2026, 10, 20), estimatedMinutes = 600, pinDay = day, reassign = Reassignment(m.lea)),
        )
        m.repo.deleteTask(m.a1)
        m.repo.assign(listOf(m.b), m.marc)
        val second = lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-2")!!)
        assertEquals(listOf("Recette", "Écrire les migrations").sorted(), second.plan.removed.map { it.title }.sorted())

        val after = lea.byUid(aUid)
        assertEquals("Migration API v2", after.title)
        assertEquals("Nouveau brief", after.description)
        assertEquals(0, after.priority)
        assertEquals(13, after.fibonacciPoints)
        assertEquals("Ops", after.taskType)
        assertEquals(LocalDate(2026, 10, 20), after.deadline)
        assertEquals(600, after.estimatedMinutes)
        // Champs du membre gardés.
        assertEquals(70, after.progressPercent)
        assertEquals(45, after.manualTimeSpentMinutes)
        assertEquals(worked.startedOn, after.startedOn)
        assertEquals(worked.updatedAt, after.updatedAt)
        assertEquals(1, lea.snap.workSessions.count { it.taskId == a.id })
        // Les retirées sont signalées, jamais supprimées.
        val b = lea.byUid(bUid)
        assertTrue(b.originRemoved)
        assertTrue(lea.byUid(a1Uid).originRemoved)
        assertFalse(after.originRemoved)
        assertEquals(lea.snap.tasks.size, lea.snap.tasks.map { it.id }.distinct().size)
        // La dépendance que le membre a posée avec sa tâche est gardée ; celle du paquet (b <- a) est retirée.
        assertTrue(lea.snap.dependencies.any { it.taskId == mine.id && it.blockerId == a.id })
        assertTrue(lea.snap.dependencies.none { it.taskId == b.id })

        // Recevoir à nouveau la recette (réaffectée à Léa) remet « retirée » à faux, sans créer de doublon.
        m.repo.assign(listOf(m.b), m.lea)
        val third = lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-3")!!)
        assertEquals(listOf("Recette"), third.plan.updated.filter { it.restored }.map { it.after.title })
        assertFalse(lea.byUid(bUid).originRemoved)
        assertEquals(1, lea.snap.tasks.count { it.teamUid == bUid })
    }

    @Test
    fun aPackOfThisVeryBaseIsRefusedAndWritesNothing() = runTest {
        val m = manager()
        val own = m.repo.receivePack(m.repo.exportPack(m.lea)!!)
        assertFalse(own.applied)
        assertEquals(PackRefusal.OWN_TEAM, own.plan.refusal)
        assertTrue(m.snap.tasks.none { it.origin != null })
    }

    @Test
    fun receivePackIsAtomic() = runTest {
        val lea = member()
        val before = lea.snap
        assertFailsWith<ImportException> { lea.repo.receivePack("pas un paquet") }
        assertFailsWith<ImportException> { lea.repo.previewPack("{}") }
        assertEquals(before, lea.snap)
    }

    // --- Rapport et aller-retour --------------------------------------------------------------------------------------------

    @Test
    fun roundTrip() = runTest {
        val m = manager()
        val lea = member()
        tick.at("2026-09-30T08:00:00Z")
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-1")!!)
        val personalBefore = personalPart(lea.snap)
        assertEquals(1, personalBefore.tasks.size)

        // Le membre travaille.
        val aUid = m.task(m.a).teamUid!!
        val bUid = m.task(m.b).teamUid!!
        val a = lea.byUid(aUid)
        val b = lea.byUid(bUid)
        tick.at("2026-10-01T09:00:00Z")
        lea.repo.startTimer(a.id)
        tick.at("2026-10-01T10:30:00Z")
        lea.repo.stopTimer()
        lea.repo.updateTask(a.id, TaskEdit(title = a.title, description = a.description, priority = a.priority, points = a.fibonacciPoints, estimatedMinutes = a.estimatedMinutes, taskType = a.taskType, deadline = a.deadline, manualTimeSpentMinutes = 30, pinDay = day, newSubtasks = "Écrire les tests"))
        lea.repo.setReceivedProgress(a.id, 70)
        tick.at("2026-10-02T15:00:00Z")
        lea.repo.toggleDone(b.id)

        tick.at("2026-10-03T16:00:00Z")
        val origin = lea.repo.origins().single()
        val text = lea.repo.buildReport(origin.key)!!
        assertEquals(TeamExchangeCodec.Kind.REPORT, TeamExchangeCodec.detect(text))

        // Côté manager : aperçu, puis intégration.
        val preview = m.repo.previewReport(text)
        assertTrue(preview.accepted)
        assertEquals(2, preview.updates.size)
        val previewA = preview.updates.first { it.before.id == m.a }
        assertEquals(ReportChange.Progress(40, 70), previewA.changes.filterIsInstance<ReportChange.Progress>().single())
        assertEquals(ReportChange.Spent(null, 120), previewA.changes.filterIsInstance<ReportChange.Spent>().single())
        assertEquals("Écrire les tests", preview.newSubtasks.single().title)
        assertEquals(m.snap.tasks, m.repo.snapshot.value.tasks) // l'aperçu n'écrit rien

        val integration = m.repo.integrateReport(text)
        assertTrue(integration.applied)
        val ma = m.task(m.a)
        val mb = m.task(m.b)
        val la = lea.byUid(aUid)
        val lb = lea.byUid(bUid)
        // État, avancement et temps passé égaux de part et d'autre.
        assertEquals(la.status, ma.status)
        assertEquals(lb.status, mb.status)
        assertEquals(TaskStatus.DONE, mb.status)
        assertEquals(la.progressPercent, ma.progressPercent)
        assertEquals(70, ma.progressPercent)
        assertEquals(100, mb.progressPercent)
        val memberSpent = TimeTracking.spentMinutesByTask(lea.snap.workSessions, tick.now(), lea.snap.tasks)
        val managerSpent = TimeTracking.spentMinutesByTask(m.snap.workSessions, tick.now(), m.snap.tasks)
        assertEquals(120, memberSpent[la.id]) // 90 de chrono + 30 saisies
        assertEquals(memberSpent[la.id], managerSpent[ma.id])
        // Le manager avait déjà un début (30 sept.) : le rapport (1er oct.) ne le repousse pas.
        assertEquals(LocalDate(2026, 9, 30), ma.startedOn)
        // Les champs du manager n'ont pas bougé.
        assertEquals("Migration API", ma.title)
        assertEquals(m.lea, ma.assigneeId)
        assertEquals(480, ma.estimatedMinutes)
        // Journal : source `report`, auteur = le membre, `done` daté du jour de fin du rapport.
        val reportEvents = m.snap.teamEvents.filter { it.source == TeamEventSource.REPORT }
        assertTrue(reportEvents.isNotEmpty() && reportEvents.all { it.memberId == m.lea })
        assertEquals(
            listOf(TeamEventKind.PROGRESS, TeamEventKind.TIME),
            m.events(m.a).filter { it.source == TeamEventSource.REPORT }.map { it.kind },
        )
        val done = m.events(m.b).single { it.kind == TeamEventKind.DONE && it.source == TeamEventSource.REPORT }
        assertEquals(LocalDate(2026, 10, 2), TeamBoard.doneOn(mb, m.events(m.b), utc))
        assertEquals("100", done.toValue)
        // Nouvelle sous-tâche : tâche d'équipe du même assigné, sous la mère, journalisée.
        val sub = m.snap.tasks.single { it.title == "Écrire les tests" }
        assertEquals(TaskSpace.TEAM, sub.space)
        assertEquals(m.lea, sub.assigneeId)
        assertEquals(m.a, sub.parentId)
        assertEquals(lea.snap.tasks.single { it.title == "Écrire les tests" }.teamUid, sub.teamUid)
        assertEquals(TeamEventKind.CREATED, m.events(sub.id).single().kind)
        assertEquals(tick.now(), m.snap.members.single { it.id == m.lea }.lastReportAt)

        // Chez le membre : aucune donnée personnelle modifiée hors des tâches reçues (et de la sous-tâche remontée,
        // qui a seulement reçu une identité technique).
        assertEquals(personalBefore, personalPart(lea.snap, reportedSubtasks = setOf("Écrire les tests")))
        assertEquals(1, lea.snap.tasks.count { it.title == "Écrire les tests" && it.origin == null })
    }

    @Test
    fun noTimeIsCountedTwiceAcrossSuccessiveReports() = runTest {
        val m = manager()
        val lea = member()
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-1")!!)
        val a = lea.byUid(m.task(m.a).teamUid!!)
        tick.at("2026-10-01T09:00:00Z")
        lea.repo.startTimer(a.id)
        tick.at("2026-10-01T10:00:00Z")
        lea.repo.stopTimer()
        tick.at("2026-10-01T18:00:00Z")
        val first = lea.repo.buildReport(lea.repo.origins().single().key)!!
        m.repo.integrateReport(first)
        assertEquals(60, m.task(m.a).reportedMinutes)

        tick.at("2026-10-02T09:00:00Z")
        lea.repo.startTimer(a.id)
        tick.at("2026-10-02T09:30:00Z")
        lea.repo.stopTimer()
        tick.at("2026-10-02T18:00:00Z")
        val second = lea.repo.buildReport(lea.repo.origins().single().key)!!
        val integration = m.repo.integrateReport(second)
        assertEquals(listOf(ReportChange.Spent(60, 90)), integration.plan.updates.single().changes)
        assertEquals(90, m.task(m.a).reportedMinutes) // le total, pas 60 + 90
        assertEquals(90, TimeTracking.spentMinutesByTask(m.snap.workSessions, tick.now(), m.snap.tasks)[m.a])

        // Le même rapport une deuxième fois, ou le premier, est refusé : rien ne change.
        val before = m.snap
        for (text in listOf(second, first)) {
            val replay = m.repo.integrateReport(text)
            assertFalse(replay.applied)
            assertEquals(ReportRefusal.OLDER, replay.plan.refusal)
        }
        assertEquals(before, m.snap)
    }

    @Test
    fun aReportOfAnotherTeamOrAnUnknownMemberChangesNothing() = runTest {
        val m = manager()
        val lea = member()
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-1")!!)
        val text = lea.repo.buildReport(lea.repo.origins().single().key)!!
        val other = manager() // un autre manager : autre identité d'équipe
        val before = other.snap
        val refused = other.repo.integrateReport(text)
        assertFalse(refused.applied)
        assertEquals(ReportRefusal.WRONG_TEAM, refused.plan.refusal)
        assertEquals(before, other.snap)
        // Même équipe, membre supprimé.
        m.repo.deleteMember(m.marc) // Marc est supprimable (aucune tâche… sauf c)
        m.repo.updateMember(m.lea, "Léa", "", 100, 7.0, false)
        val unknown = text.replace(m.leaUid, "member-fantome")
        assertEquals(ReportRefusal.UNKNOWN_MEMBER, m.repo.integrateReport(unknown).plan.refusal)
        assertTrue(m.snap.teamEvents.none { it.source == TeamEventSource.REPORT })
    }

    @Test
    fun aReassignedTaskKeepsItsAssigneeButTheReportIsIntegratedAndFlagged() = runTest {
        val m = manager()
        val lea = member()
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-1")!!)
        val a = lea.byUid(m.task(m.a).teamUid!!)
        lea.repo.setReceivedProgress(a.id, 50)
        val text = lea.repo.buildReport(lea.repo.origins().single().key)!!
        m.repo.assign(listOf(m.a), m.marc) // réaffectée avant l'arrivée du rapport
        tick.current += 60.minutes
        val integration = m.repo.integrateReport(text)
        assertTrue(integration.applied)
        assertEquals(listOf(m.a), integration.plan.fromFormerHolder.map { it.before.id })
        assertEquals(50, m.task(m.a).progressPercent)
        assertEquals(m.marc, m.task(m.a).assigneeId)
        val event = m.events(m.a).last { it.kind == TeamEventKind.PROGRESS }
        assertEquals(TeamEventSource.REPORT, event.source)
        assertEquals(m.lea, event.memberId) // l'auteur du travail, pas le titulaire actuel
    }

    @Test
    fun anUnknownTaskIsIgnoredAndAnArchivedMemberIsIntegrated() = runTest {
        val m = manager()
        val lea = member()
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-1")!!)
        val a = lea.byUid(m.task(m.a).teamUid!!)
        lea.repo.setReceivedProgress(a.id, 60)
        val text = lea.repo.buildReport(lea.repo.origins().single().key)!!
        m.repo.deleteTask(m.b) // supprimée depuis le paquet
        m.repo.archiveMember(m.lea)
        tick.current += 60.minutes
        val integration = m.repo.integrateReport(text)
        assertTrue(integration.applied)
        assertTrue(integration.plan.memberArchived)
        assertEquals(listOf(IgnoredReason.UNKNOWN_TASK), integration.plan.ignored.map { it.reason })
        assertEquals(60, m.task(m.a).progressPercent)
        assertNotNull(m.snap.members.single { it.id == m.lea }.lastReportAt)
    }

    @Test
    fun subtasksAreReportedOnceAndAdoptedByTheNextPack() = runTest {
        val m = manager()
        val lea = member()
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-1")!!)
        val a = lea.byUid(m.task(m.a).teamUid!!)
        val mineId = lea.repo.createTask("Écrire les tests", a.id)!!
        tick.current += 60.minutes
        val first = lea.repo.buildReport(lea.repo.origins().single().key)!!
        val uid = lea.snap.tasks.single { it.id == mineId }.teamUid
        assertNotNull(uid)
        m.repo.integrateReport(first)
        assertEquals(1, m.snap.tasks.count { it.title == "Écrire les tests" })

        // Deuxième rapport : la sous-tâche porte la même identité et n'est pas recréée ; son statut suit.
        lea.repo.toggleDone(mineId)
        tick.current += 60.minutes
        val second = lea.repo.buildReport(lea.repo.origins().single().key)!!
        assertEquals(uid, lea.snap.tasks.single { it.id == mineId }.teamUid)
        val integration = m.repo.integrateReport(second)
        assertTrue(integration.plan.newSubtasks.isEmpty())
        assertEquals(1, m.snap.tasks.count { it.title == "Écrire les tests" })
        assertEquals(TaskStatus.DONE, m.snap.tasks.single { it.title == "Écrire les tests" }.status)

        // Le paquet suivant la contient : elle est rattachée (adoptée), pas dupliquée.
        m.repo.updateTask(m.snap.tasks.single { it.title == "Écrire les tests" }.id, TaskEdit(title = "Écrire les tests", priority = 1, points = 2, pinDay = day, ))
        m.repo.toggleDone(m.snap.tasks.single { it.title == "Écrire les tests" }.id) // rouverte côté manager : à faire
        val pack = lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-2")!!)
        assertTrue(pack.plan.created.none { it.task.title == "Écrire les tests" })
        assertEquals(1, lea.snap.tasks.count { it.title == "Écrire les tests" })
        assertNotNull(lea.snap.tasks.single { it.id == mineId }.origin)
    }

    @Test
    fun aFinishedRecurringTaskSpawnsItsNextOccurrenceWhenReportedDone() = runTest {
        val m = manager()
        m.repo.updateTask(
            m.a,
            TaskEdit(
                title = "Migration API", priority = 1, points = 5, deadline = LocalDate(2026, 10, 2), pinDay = day,
                recurrence = com.skohscripts.kairos.core.model.TaskRecurrence.DAILY, reassign = Reassignment(m.lea),
            ),
        )
        val lea = member()
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-1")!!)
        val a = lea.byUid(m.task(m.a).teamUid!!)
        tick.at("2026-10-02T10:00:00Z")
        lea.repo.toggleDone(a.id)
        val text = lea.repo.buildReport(lea.repo.origins().single().key)!!
        m.repo.integrateReport(text)
        assertEquals(TaskStatus.DONE, m.task(m.a).status)
        val next = m.snap.tasks.single { it.title == "Migration API" && it.status == TaskStatus.TODO }
        assertEquals(m.lea, next.assigneeId)
        assertEquals(TaskSpace.TEAM, next.space)
    }

    @Test
    fun buildReportNeedsAReceivedTask() = runTest {
        val lea = member()
        assertNull(lea.repo.buildReport("team-inconnue"))
        assertTrue(lea.repo.origins().isEmpty())
    }

    @Test
    fun setReceivedProgressOnlyAppliesToOpenReceivedTasks() = runTest {
        val m = manager()
        val lea = member()
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-1")!!)
        val a = lea.byUid(m.task(m.a).teamUid!!)
        val mine = lea.snap.tasks.first { it.origin == null }
        assertFalse(lea.repo.setReceivedProgress(mine.id, 50))
        assertTrue(lea.repo.setReceivedProgress(a.id, 77)) // arrondi à la dizaine
        assertEquals(80, lea.byUid(a.teamUid!!).progressPercent)
        assertFalse(lea.repo.setReceivedProgress(a.id, 80)) // rien ne change
        lea.repo.toggleDone(a.id)
        assertFalse(lea.repo.setReceivedProgress(a.id, 10))
    }

    // --- Stockage ------------------------------------------------------------------------------------------------------------

    @Test
    fun theNewColumnsSurviveAReplaceAll() = runTest {
        val m = manager()
        val lea = member()
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-1")!!)
        m.repo.assign(listOf(m.b), m.marc)
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-2")!!) // b est retirée
        tick.current += 60.minutes
        m.repo.integrateReport(lea.repo.buildReport(lea.repo.origins().single().key)!!.also { check(it.isNotEmpty()) })
        val other = blank()
        other.replaceAll(lea.snap)
        assertEquals(lea.snap, other.snapshot.value)
        assertTrue(other.snapshot.value.tasks.any { it.originRemoved })
        val managerCopy = blank()
        managerCopy.replaceAll(m.snap)
        assertEquals(m.snap, managerCopy.snapshot.value)
        assertNotNull(managerCopy.snapshot.value.members.single { it.id == m.lea }.lastReportAt)
        // L'export complet les emporte aussi.
        assertEquals(m.snap, ExportCodec.decode(ExportCodec.encode(m.snap, "3.1.0", tick.now())))
        assertEquals(lea.snap, ExportCodec.decode(ExportCodec.encode(lea.snap, "3.1.0", tick.now())))
    }

    @Test
    fun clearTeamDataLeavesTheReceivedTasksAlone() = runTest {
        val m = manager()
        val lea = member()
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-1")!!)
        // Un membre qui est aussi manager d'une autre équipe : ses données d'équipe partent, ses tâches reçues restent.
        lea.repo.updateSettings(Settings(team = TeamSettings(enabled = true, name = "Mon équipe")))
        val mine = lea.repo.createMember("Moi", "", 100, 7.0, true)!!
        lea.repo.createTeamTask("À moi")
        val received = lea.snap.tasks.filter { it.origin != null }
        assertEquals(3, received.size)
        lea.repo.clearTeamData()
        assertEquals(received.map { it.id }, lea.snap.tasks.filter { it.origin != null }.map { it.id })
        assertTrue(lea.snap.tasks.none { it.space == TaskSpace.TEAM })
        assertTrue(lea.snap.members.none { it.id == mine })
        assertEquals(1, lea.repo.origins().size)
    }

    // --- Journal de l'envoi seulement après l'enregistrement du fichier ---------------------------------------------------

    @Test
    fun preparingAPackWritesNothingAndRecordingJournalsTheSentTasks() = runTest {
        val m = manager()
        val before = m.snap.teamEvents
        val prepared = m.repo.preparePack(m.lea, "pack-1")!!
        assertEquals(before, m.snap.teamEvents)
        assertEquals(TeamExchangeCodec.Kind.PACK, TeamExchangeCodec.detect(prepared.text))
        assertEquals(setOf(m.task(m.a).teamUid, m.task(m.a1).teamUid, m.task(m.b).teamUid), prepared.taskUids)
        assertEquals("pack-1", prepared.packId)
        assertFalse(prepared.previouslySent)

        m.repo.recordPackSent(prepared)
        val sent = m.snap.teamEvents.filter { it.kind == TeamEventKind.SENT }
        assertEquals(setOf(m.a, m.a1, m.b), sent.map { it.taskId }.toSet())
        assertTrue(sent.all { it.toValue == "pack-1" && it.memberId == m.lea && it.source == TeamEventSource.MANUAL })
        assertTrue(m.repo.preparePack(m.lea)!!.previouslySent)
        // Un autre membre n'a rien reçu.
        assertFalse(m.repo.preparePack(m.marc)!!.previouslySent)
    }

    @Test
    fun aTaskDeletedBetweenPreparingAndRecordingGetsNoSentEvent() = runTest {
        val m = manager()
        val prepared = m.repo.preparePack(m.lea, "pack-1")!!
        m.repo.deleteTask(m.b)
        m.repo.recordPackSent(prepared)
        assertEquals(setOf(m.a, m.a1), m.snap.teamEvents.filter { it.kind == TeamEventKind.SENT }.map { it.taskId }.toSet())
    }

    @Test
    fun anEmptyPackIsPreparedAndMarksEveryReceivedTaskAsRemoved() = runTest {
        val m = manager()
        val lea = member()
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-1")!!)
        // Réaffectation complète : Léa n'a plus aucune tâche à faire.
        m.repo.assign(listOf(m.a, m.b), m.marc)
        m.repo.deleteTask(m.a1)
        val prepared = m.repo.preparePack(m.lea, "pack-2")!!
        assertTrue(prepared.taskUids.isEmpty())
        assertTrue(prepared.previouslySent)
        m.repo.recordPackSent(prepared) // rien à journaliser
        val result = lea.repo.receivePack(prepared.text)
        assertTrue(result.applied)
        assertEquals(3, result.plan.removed.size)
        assertTrue(lea.snap.tasks.filter { it.origin != null }.all { it.originRemoved })
    }

    // --- Garder une tâche retirée ----------------------------------------------------------------------------------------

    @Test
    fun detachingARemovedTaskMakesItPersonalAndKeepsItsDate() = runTest {
        val m = manager()
        val lea = member()
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-1")!!)
        m.repo.assign(listOf(m.b), m.marc)
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-2")!!)
        val removed = lea.snap.tasks.single { it.originRemoved }
        tick.at("2026-10-02T09:00:00Z")
        assertTrue(lea.repo.detachOrigin(removed.id))
        val kept = lea.snap.tasks.single { it.id == removed.id }
        assertNull(kept.origin)
        assertFalse(kept.originRemoved)
        assertNull(kept.teamUid)
        assertEquals(removed.updatedAt, kept.updatedAt)
        assertEquals(removed.title, kept.title)
        assertFalse(ReceivedTasks.isReceived(kept))
        // Un nouveau paquet qui la contiendrait de nouveau en crée une autre au lieu de l'écraser.
        m.repo.assign(listOf(m.b), m.lea)
        val third = lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-3")!!)
        assertEquals(1, third.plan.created.size)
    }

    @Test
    fun detachingRefusesATaskThatIsNotAReceivedRemovedOne() = runTest {
        val m = manager()
        val lea = member()
        lea.repo.receivePack(m.repo.exportPack(m.lea, "pack-1")!!)
        val received = lea.snap.tasks.first { it.origin != null }
        val mine = lea.snap.tasks.first { it.origin == null }
        val before = lea.snap
        assertFalse(lea.repo.detachOrigin(received.id)) // reçue, pas retirée
        assertFalse(lea.repo.detachOrigin(mine.id)) // personnelle
        assertFalse(lea.repo.detachOrigin(9999))
        assertEquals(before, lea.snap)
    }
}
