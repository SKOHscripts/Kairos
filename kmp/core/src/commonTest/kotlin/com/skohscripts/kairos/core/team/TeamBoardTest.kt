package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class TeamBoardTest {
    // Mercredi 30 septembre 2026.
    private val day = LocalDate(2026, 9, 30)
    private val utc = TimeZone.UTC
    private val fresh = Instant.parse("2026-09-29T08:00:00Z")
    private val enabled = Settings(team = TeamSettings(enabled = true))

    private fun task(
        id: Long,
        title: String = "T$id",
        assignee: Long? = null,
        startedOn: LocalDate? = null,
        priority: Int? = 1,
        points: Int? = 3,
        deadline: LocalDate? = null,
        status: TaskStatus = TaskStatus.TODO,
        type: String = "",
        space: TaskSpace = TaskSpace.TEAM,
        updatedAt: Instant = fresh,
    ) = Task(
        id, title, priority = priority, fibonacciPoints = points, deadline = deadline, status = status, taskType = type,
        space = space, assigneeId = assignee, startedOn = startedOn, createdAt = fresh, updatedAt = updatedAt,
    )

    private fun member(id: Long, name: String, self: Boolean = false, archived: Boolean = false) =
        TeamMember(id, "uid-$id", name, hoursPerDay = 7.0, isSelf = self, archived = archived, createdAt = fresh, updatedAt = fresh)

    private var eventId = 1L
    private fun event(taskId: Long, kind: TeamEventKind, at: String, from: String? = null, to: String? = null) =
        TeamEvent(eventId++, taskId, "T$taskId", null, kind, from, to, TeamEventSource.MANUAL, Instant.parse(at))

    private fun snapshot(
        tasks: List<Task>,
        members: List<TeamMember> = listOf(member(1, "Zoé"), member(2, "Alex"), member(3, "Moi", self = true)),
        events: List<TeamEvent> = emptyList(),
        deps: List<TaskDependency> = emptyList(),
        settings: Settings = enabled,
    ) = KairosSnapshot(tasks = tasks, members = members, teamEvents = events, dependencies = deps, settings = settings)

    private fun build(s: KairosSnapshot, filter: TeamBoardFilter = TeamBoardFilter()) = TeamBoard.build(s, day, utc, filter)

    // --- Backlog ------------------------------------------------------------------

    @Test
    fun backlogUsesTheWsjfOrder() {
        val random = Random(42)
        val tasks = (1L..40L).map {
            task(
                it, priority = listOf(0, 1, 2).random(random), points = listOf(1, 2, 3, 5, 8, 13, 21).random(random),
                deadline = if (random.nextInt(3) == 0) day.plus(DatePeriod(days = random.nextInt(-10, 20))) else null,
            )
        }
        val s = snapshot(tasks)
        val expected = tasks.sortedBy { Scheduling.sortKey(it, day, s.settings) }.map { it.id }
        assertEquals(expected, build(s).ready.map { it.task.id })
        // Les en-retard passent d'abord (palier dur).
        val firstNotLate = build(s).ready.indexOfFirst { !Scheduling.isOverdue(it.task, day) }
        assertTrue(build(s).ready.drop(firstNotLate).none { Scheduling.isOverdue(it.task, day) })
    }

    @Test
    fun backlogSplitsToQualifyFromReady() {
        val s = snapshot(
            listOf(
                task(1, priority = null, points = null),
                task(2, priority = 0, points = null),
                task(3, priority = null, points = 5),
                task(4, priority = 2, points = 2),
                task(5, assignee = 1), // assignée : pas dans le backlog
                task(6, status = TaskStatus.DONE), // faite : pas dans le backlog
            ),
        )
        val board = build(s)
        assertEquals(listOf(1L, 2L, 3L), board.toQualify.map { it.task.id })
        assertEquals(listOf(4L), board.ready.map { it.task.id })
        assertTrue(board.toQualify.all { it.state == TeamState.BACKLOG })
    }

    @Test
    fun aBlockerInheritsTheUrgencyOfWhatItBlocks() {
        // 2 (P2, faible) bloque 1 (P0 en retard) : comme la vue Jour, 2 remonte devant 3 (P1).
        val s = snapshot(
            listOf(task(1, priority = 0, deadline = LocalDate(2026, 9, 1)), task(2, priority = 2), task(3, priority = 1)),
            deps = listOf(TaskDependency(1, 1, 2, fresh)),
        )
        val ready = build(s).ready
        assertEquals(listOf(1L, 2L, 3L), ready.map { it.task.id })
        assertTrue(ready.first { it.task.id == 1L }.blocked)
        assertFalse(ready.first { it.task.id == 2L }.blocked)
    }

    @Test
    fun aBacklogTaskIsNeverInThePersonalView() {
        val tasks = listOf(
            task(1, space = TaskSpace.PERSONAL), task(2), task(3, assignee = 3), task(4, assignee = 1),
        )
        val s = snapshot(tasks)
        val personal = Workspaces.personalView(s).tasks.map { it.id }
        assertEquals(listOf(1L, 3L), personal)
        val backlog = build(s).let { it.ready + it.toQualify }.map { it.task.id }
        assertEquals(listOf(2L), backlog)
        assertTrue(backlog.none { it in personal })
    }

    @Test
    fun personalAndArchivedTasksAreIgnored() {
        val s = snapshot(
            listOf(
                task(1, space = TaskSpace.PERSONAL), task(2, status = TaskStatus.ARCHIVED),
                task(3, assignee = 1, status = TaskStatus.ARCHIVED),
            ),
        )
        val board = build(s)
        assertTrue(board.ready.isEmpty() && board.toQualify.isEmpty())
        assertTrue(board.lanes.all { it.inProgress.isEmpty() && it.todo.isEmpty() && it.done.isEmpty() })
        assertEquals(TeamKeyFigures(0, 0, 0, 0), board.keyFigures)
    }

    // --- Lignes de membres -------------------------------------------------------------

    @Test
    fun laneOrderIsMeFirstThenByNameAndSkipsArchivedMembers() {
        val s = snapshot(
            emptyList(),
            members = listOf(member(1, "Zoé"), member(2, "alex"), member(3, "Moi", self = true), member(4, "Bob", archived = true), member(5, "Léa")),
        )
        assertEquals(listOf("Moi", "alex", "Léa", "Zoé"), build(s).lanes.map { it.member.name })
    }

    @Test
    fun laneCardsAreGroupedByState() {
        val s = snapshot(
            listOf(
                task(1, assignee = 1, startedOn = day),
                task(2, assignee = 1),
                task(3, assignee = 1, status = TaskStatus.DONE),
                task(4, assignee = 2, startedOn = day),
                task(5), // backlog : dans aucune ligne
            ),
            events = listOf(event(3, TeamEventKind.DONE, "2026-09-29T10:00:00Z")),
        )
        val lanes = build(s).lanes.associateBy { it.member.id }
        val zoe = lanes.getValue(1)
        assertEquals(listOf(1L), zoe.inProgress.map { it.task.id })
        assertEquals(listOf(2L), zoe.todo.map { it.task.id })
        assertEquals(listOf(3L), zoe.done.map { it.task.id })
        assertEquals(listOf(4L), lanes.getValue(2).inProgress.map { it.task.id })
        assertTrue(lanes.getValue(3).let { it.inProgress.isEmpty() && it.todo.isEmpty() && it.done.isEmpty() })
        assertEquals(TeamState.IN_PROGRESS, zoe.inProgress.single().state)
    }

    @Test
    fun wipSignalIsCarriedByTheLaneAndDoesNotDependOnTheFilter() {
        val inProgress = (1L..4L).map { task(it, assignee = 1, startedOn = day, type = if (it == 1L) "Dev" else "Ops") }
        val s = snapshot(inProgress)
        val lane = build(s).lanes.first { it.member.id == 1L }
        assertTrue(lane.wipExceeded)
        assertEquals(4, lane.inProgressCount)
        assertFalse(build(s).lanes.first { it.member.id == 2L }.wipExceeded)
        // Filtre par catégorie : une seule carte visible, mais le membre reste en surcharge.
        val filtered = build(s, TeamBoardFilter(category = "Dev")).lanes.first { it.member.id == 1L }
        assertEquals(1, filtered.inProgress.size)
        assertTrue(filtered.wipExceeded)
        // Sans limite, jamais de signal.
        val noLimit = s.copy(settings = enabled.copy(team = TeamSettings(enabled = true, wipLimit = 0)))
        assertFalse(build(noLimit).lanes.first { it.member.id == 1L }.wipExceeded)
    }

    @Test
    fun doneColumnKeepsTheLastSevenDays() {
        val s = snapshot(
            listOf(
                task(1, assignee = 1, status = TaskStatus.DONE), // 30 sept.
                task(2, assignee = 1, status = TaskStatus.DONE), // 24 sept. : 6 jours avant, incluse
                task(3, assignee = 1, status = TaskStatus.DONE), // 23 sept. : 7 jours avant, exclue
                task(4, assignee = 1, status = TaskStatus.DONE, updatedAt = Instant.parse("2026-09-28T09:00:00Z")), // sans événement : updatedAt
                task(5, assignee = 1, status = TaskStatus.DONE, updatedAt = Instant.parse("2026-09-01T09:00:00Z")), // ancienne
            ),
            events = listOf(
                event(1, TeamEventKind.DONE, "2026-09-30T08:00:00Z"),
                event(2, TeamEventKind.DONE, "2026-09-24T00:00:00Z"),
                event(3, TeamEventKind.DONE, "2026-09-23T23:59:00Z"),
            ),
        )
        val done = build(s).lanes.first { it.member.id == 1L }.done
        assertEquals(listOf(1L, 4L, 2L), done.map { it.task.id }) // la plus récente d'abord
        assertEquals(LocalDate(2026, 9, 28), done[1].doneOn)
    }

    @Test
    fun doneOnIsTheLastDoneEventInTheLocalTimeZone() {
        val t = task(1, status = TaskStatus.DONE, updatedAt = Instant.parse("2026-09-01T09:00:00Z"))
        val events = listOf(
            event(1, TeamEventKind.DONE, "2026-09-10T10:00:00Z"),
            event(1, TeamEventKind.REOPENED, "2026-09-11T10:00:00Z"),
            event(1, TeamEventKind.DONE, "2026-09-12T23:30:00Z"),
        )
        assertEquals(LocalDate(2026, 9, 12), TeamBoard.doneOn(t, events, utc))
        assertEquals(LocalDate(2026, 9, 13), TeamBoard.doneOn(t, events, TimeZone.of("Europe/Paris")))
        assertEquals(LocalDate(2026, 9, 1), TeamBoard.doneOn(t, emptyList(), utc))
    }

    // --- Chiffres clés ---------------------------------------------------------------------

    @Test
    fun keyFigures() {
        val s = snapshot(
            listOf(
                task(1, assignee = 1, startedOn = day), // en cours
                task(2, assignee = 2, startedOn = day, deadline = LocalDate(2026, 9, 29)), // en cours, en retard
                task(3, assignee = 1, deadline = LocalDate(2026, 9, 10)), // à faire, en retard (et traîne)
                task(4, deadline = LocalDate(2026, 9, 30)), // backlog, en retard
                task(5, assignee = 1, status = TaskStatus.DONE), // faite mardi 29
                task(6, assignee = 2, status = TaskStatus.DONE), // faite le dimanche 27 : semaine précédente
                task(7, assignee = 2, status = TaskStatus.DONE), // faite lundi 28
                task(8, assignee = 2, deadline = LocalDate(2026, 10, 30)), // rien à signaler
            ),
            events = listOf(
                event(5, TeamEventKind.DONE, "2026-09-29T08:00:00Z"),
                event(6, TeamEventKind.DONE, "2026-09-27T08:00:00Z"),
                event(7, TeamEventKind.DONE, "2026-09-28T00:00:00Z"),
            ),
        )
        val k = build(s).keyFigures
        assertEquals(2, k.inProgress)
        assertEquals(2, k.doneThisWeek)
        assertEquals(3, k.overdue)
        assertEquals(3, k.watched)
    }

    @Test
    fun watchedCountsEveryTaskWithAtLeastOneSignalOnce() {
        val s = snapshot(
            listOf(
                task(1, assignee = 1, startedOn = LocalDate(2026, 9, 1), deadline = LocalDate(2026, 9, 1)), // trois signaux
                task(2, assignee = 1),
            ),
        )
        val board = build(s)
        assertEquals(1, board.keyFigures.watched)
        assertEquals(1, board.keyFigures.overdue)
        assertEquals(3, board.lanes.first { it.member.id == 1L }.inProgress.single().signals.size)
    }

    // --- Filtres ----------------------------------------------------------------------------

    private fun filterScenario() = snapshot(
        listOf(
            task(1, title = "Migrer la base", type = "Dev", priority = 0, deadline = LocalDate(2026, 10, 15)),
            task(2, title = "Revue de code", type = "Revue de code", priority = 1),
            task(3, title = "Migrer le wiki", type = "", priority = 1, assignee = 1),
            task(4, title = "Réunion équipe", type = "Dev", priority = 2, assignee = 2, startedOn = day, deadline = LocalDate(2026, 9, 1)),
        ),
    )

    private fun ids(board: TeamBoard) =
        (board.ready + board.toQualify + board.lanes.flatMap { it.inProgress + it.todo + it.done }).map { it.task.id }.toSet()

    @Test
    fun filterByTextIgnoresCase() {
        assertEquals(setOf(1L, 3L), ids(build(filterScenario(), TeamBoardFilter(text = " MIGRER "))))
    }

    @Test
    fun filterByCategory() {
        assertEquals(setOf(1L, 4L), ids(build(filterScenario(), TeamBoardFilter(category = "Dev"))))
        assertEquals(setOf(3L), ids(build(filterScenario(), TeamBoardFilter(category = ""))))
    }

    @Test
    fun filterByPriority() {
        assertEquals(setOf(1L), ids(build(filterScenario(), TeamBoardFilter(priority = 0))))
        assertEquals(setOf(2L, 3L), ids(build(filterScenario(), TeamBoardFilter(priority = 1))))
    }

    @Test
    fun filterByMemberKeepsOnlyHisLaneAndEmptiesTheBacklog() {
        val board = build(filterScenario(), TeamBoardFilter(memberId = 2))
        assertEquals(listOf(2L), board.lanes.map { it.member.id })
        assertEquals(setOf(4L), ids(board))
        assertTrue(board.ready.isEmpty() && board.toQualify.isEmpty())
    }

    @Test
    fun filterOnlyWatchedAndWithDeadline() {
        assertEquals(setOf(4L), ids(build(filterScenario(), TeamBoardFilter(onlyWatched = true))))
        assertEquals(setOf(1L, 4L), ids(build(filterScenario(), TeamBoardFilter(withDeadline = true))))
    }

    @Test
    fun filtersCombineAndAlsoFeedTheKeyFigures() {
        val f = TeamBoardFilter(text = "migrer", category = "Dev")
        assertEquals(setOf(1L), ids(build(filterScenario(), f)))
        assertEquals(1, build(filterScenario(), TeamBoardFilter(category = "Dev")).keyFigures.inProgress)
        assertEquals(0, build(filterScenario(), TeamBoardFilter(category = "")).keyFigures.inProgress)
    }

    @Test
    fun aDisabledSpaceChangesNothingForTheBoard() {
        // Le tableau lit la base complète : l'activation ne filtre pas (c'est l'affaire de l'interface).
        val s = filterScenario()
        assertEquals(ids(build(s)), ids(build(s.copy(settings = Settings()))))
    }
}
