package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class TeamSignalsTest {
    // Mercredi 30 septembre 2026.
    private val day = LocalDate(2026, 9, 30)
    private val utc = TimeZone.UTC
    private val fresh = Instant.parse("2026-09-29T08:00:00Z")
    private val settings = Settings()

    private fun task(
        id: Long = 1,
        assignee: Long? = 7,
        startedOn: LocalDate? = null,
        deadline: LocalDate? = null,
        scheduledDate: LocalDate? = null,
        status: TaskStatus = TaskStatus.TODO,
        updatedAt: Instant = fresh,
    ) = Task(
        id, "T$id", status = status, space = TaskSpace.TEAM, assigneeId = assignee, startedOn = startedOn,
        deadline = deadline, scheduledDate = scheduledDate, createdAt = updatedAt, updatedAt = updatedAt,
    )

    private var nextEvent = 1L
    private fun event(taskId: Long, kind: TeamEventKind, at: String, from: String? = null, to: String? = null) =
        TeamEvent(nextEvent++, taskId, "T$taskId", null, kind, from, to, TeamEventSource.MANUAL, Instant.parse(at))

    private fun signals(t: Task, events: List<TeamEvent> = emptyList(), s: Settings = settings, holidays: Set<LocalDate> = emptySet()) =
        TeamSignals.of(t, events, day, utc, s, holidays)

    // --- En retard ------------------------------------------------------------

    @Test
    fun overdue_follows_the_engine_rule() {
        assertTrue(TeamSignal.OVERDUE in signals(task(deadline = day)))
        assertTrue(TeamSignal.OVERDUE in signals(task(deadline = LocalDate(2026, 9, 1))))
        assertTrue(TeamSignal.OVERDUE in signals(task(scheduledDate = day)))
        assertFalse(TeamSignal.OVERDUE in signals(task(deadline = LocalDate(2026, 10, 1))))
        assertFalse(TeamSignal.OVERDUE in signals(task()))
    }

    // --- Traîne ---------------------------------------------------------------

    @Test
    fun stale_after_the_overdue_threshold() {
        // Seuil des réglages : 7 jours de retard.
        assertFalse(TeamSignal.STALE in signals(task(deadline = LocalDate(2026, 9, 23)))) // 7 jours : pas encore
        assertTrue(TeamSignal.STALE in signals(task(deadline = LocalDate(2026, 9, 22)))) // 8 jours
        assertTrue(TeamSignal.STALE in signals(task(deadline = LocalDate(2026, 9, 25)), s = settings.copy(staleOverdueDays = 4)))
    }

    @Test
    fun stale_when_untouched_without_any_date() {
        assertFalse(TeamSignal.STALE in signals(task(updatedAt = Instant.parse("2026-09-16T08:00:00Z")))) // 14 jours
        assertTrue(TeamSignal.STALE in signals(task(updatedAt = Instant.parse("2026-09-15T08:00:00Z")))) // 15 jours
    }

    // --- Sans avancement --------------------------------------------------------

    @Test
    fun no_progress_counts_business_days_since_the_last_start_or_progress() {
        val started = LocalDate(2026, 9, 22)
        val t = task(startedOn = started)
        // Mardi 22 -> mercredi 30 : 6 jours ouvrés (23, 24, 25, 28, 29, 30) > 5.
        assertTrue(TeamSignal.NO_PROGRESS in signals(t, listOf(event(1, TeamEventKind.STARTED, "2026-09-22T09:00:00Z"))))
        // Mercredi 23 -> 30 : exactement 5 : seuil non franchi.
        assertFalse(TeamSignal.NO_PROGRESS in signals(t, listOf(event(1, TeamEventKind.STARTED, "2026-09-23T09:00:00Z"))))
        // Un avancement plus récent repousse le signal.
        val events = listOf(event(1, TeamEventKind.STARTED, "2026-09-22T09:00:00Z"), event(1, TeamEventKind.PROGRESS, "2026-09-29T09:00:00Z"))
        assertFalse(TeamSignal.NO_PROGRESS in signals(t, events))
    }

    @Test
    fun no_progress_skips_weekends_and_holidays() {
        val t = task(startedOn = LocalDate(2026, 9, 22))
        val started = listOf(event(1, TeamEventKind.STARTED, "2026-09-22T09:00:00Z"))
        // Un férié en semaine retire un jour ouvré : 5, seuil non franchi.
        assertFalse(TeamSignal.NO_PROGRESS in signals(t, started, holidays = setOf(LocalDate(2026, 9, 28))))
        // Un férié un samedi ne change rien.
        assertTrue(TeamSignal.NO_PROGRESS in signals(t, started, holidays = setOf(LocalDate(2026, 9, 26))))
        // Un vendredi vu le lundi suivant : 1 jour ouvré, pas de signal malgré 3 jours calendaires.
        assertFalse(TeamSignals.noProgress(t, listOf(event(1, TeamEventKind.STARTED, "2026-09-25T09:00:00Z")), LocalDate(2026, 9, 28), utc, 1))
        assertTrue(TeamSignals.noProgress(t, listOf(event(1, TeamEventKind.STARTED, "2026-09-24T09:00:00Z")), LocalDate(2026, 9, 28), utc, 1))
    }

    @Test
    fun no_progress_threshold_comes_from_the_team_settings() {
        val t = task(startedOn = LocalDate(2026, 9, 28))
        val started = listOf(event(1, TeamEventKind.STARTED, "2026-09-28T09:00:00Z")) // 2 jours ouvrés
        assertFalse(TeamSignal.NO_PROGRESS in signals(t, started))
        assertFalse(TeamSignal.NO_PROGRESS in signals(t, started, settings.copy(team = TeamSettings(staleProgressDays = 2))))
        assertTrue(TeamSignal.NO_PROGRESS in signals(t, started, settings.copy(team = TeamSettings(staleProgressDays = 1))))
    }

    @Test
    fun no_progress_needs_an_in_progress_task() {
        val old = listOf(event(1, TeamEventKind.STARTED, "2026-09-01T09:00:00Z"))
        assertFalse(TeamSignal.NO_PROGRESS in signals(task(startedOn = null), old)) // à faire, pas commencée
        assertFalse(TeamSignal.NO_PROGRESS in signals(task(assignee = null, startedOn = LocalDate(2026, 9, 1)), old)) // backlog
        assertTrue(signals(task(startedOn = LocalDate(2026, 9, 1), status = TaskStatus.DONE), old).isEmpty()) // faite : aucun signal
    }

    @Test
    fun other_events_do_not_count_as_progress() {
        val t = task(startedOn = LocalDate(2026, 9, 1))
        val events = listOf(
            event(1, TeamEventKind.STARTED, "2026-09-01T09:00:00Z"),
            event(1, TeamEventKind.QUALIFIED, "2026-09-29T09:00:00Z"),
            event(1, TeamEventKind.ASSIGNED, "2026-09-29T09:00:00Z", "1", "2"),
            event(2, TeamEventKind.PROGRESS, "2026-09-29T09:00:00Z"), // une autre tâche
        )
        assertTrue(TeamSignal.NO_PROGRESS in signals(t, events))
    }

    @Test
    fun no_progress_falls_back_to_the_start_day_without_event() {
        assertTrue(TeamSignal.NO_PROGRESS in signals(task(startedOn = LocalDate(2026, 9, 1))))
        assertFalse(TeamSignal.NO_PROGRESS in signals(task(startedOn = LocalDate(2026, 9, 29))))
    }

    // --- Ballottée ----------------------------------------------------------------

    private fun assigned(from: String?, to: String?) = event(1, TeamEventKind.ASSIGNED, "2026-09-20T09:00:00Z", from, to)

    @Test
    fun churn_counts_member_to_member_reassignments_only() {
        assertEquals(0, TeamSignals.churn(listOf(assigned(null, "1")))) // premier assignement
        assertEquals(0, TeamSignals.churn(listOf(assigned("1", null)))) // retour au backlog
        assertEquals(0, TeamSignals.churn(listOf(assigned(null, "2")))) // nouvelle sortie du backlog
        assertEquals(1, TeamSignals.churn(listOf(assigned("1", "2"))))
        assertEquals(0, TeamSignals.churn(listOf(assigned("1", "1")))) // aucun changement réel
    }

    @Test
    fun churn_signal_needs_the_threshold() {
        val two = listOf(assigned(null, "1"), assigned("1", "2"), assigned("2", null), assigned(null, "3"), assigned("3", "1"))
        assertEquals(2, TeamSignals.churn(two))
        assertFalse(TeamSignal.CHURN in signals(task(), two))
        val three = two + assigned("1", "2")
        assertTrue(TeamSignal.CHURN in signals(task(), three))
        // Seuil des réglages : 2 suffit.
        assertTrue(TeamSignal.CHURN in signals(task(), two, settings.copy(team = TeamSettings(churnThreshold = 2))))
        assertFalse(TeamSignal.CHURN in signals(task(), three, settings.copy(team = TeamSettings(churnThreshold = 4))))
    }

    // --- Trop d'en-cours ------------------------------------------------------------

    private fun inProgress(count: Int, member: Long = 7) =
        (1..count).map { task(it.toLong(), assignee = member, startedOn = day) }

    @Test
    fun wip_is_exceeded_above_the_limit() {
        assertFalse(TeamSignals.wipExceeded(inProgress(3), 7, settings)) // limite 3 : atteinte, pas dépassée
        assertTrue(TeamSignals.wipExceeded(inProgress(4), 7, settings))
        assertFalse(TeamSignals.wipExceeded(inProgress(4), 8, settings)) // un autre membre
        assertTrue(TeamSignals.wipExceeded(inProgress(2), 7, settings.copy(team = TeamSettings(wipLimit = 1))))
    }

    @Test
    fun wip_limit_zero_means_no_limit() {
        assertFalse(TeamSignals.wipExceeded(inProgress(50), 7, settings.copy(team = TeamSettings(wipLimit = 0))))
    }

    @Test
    fun wip_counts_only_started_open_tasks() {
        val tasks = inProgress(3) + task(10, assignee = 7) + task(11, assignee = 7, startedOn = day, status = TaskStatus.DONE)
        assertEquals(3, TeamSignals.inProgressCount(tasks, 7))
        assertFalse(TeamSignals.wipExceeded(tasks, 7, settings))
    }

    @Test
    fun a_task_can_carry_several_signals() {
        val t = task(startedOn = LocalDate(2026, 9, 1), deadline = LocalDate(2026, 9, 1))
        assertEquals(setOf(TeamSignal.OVERDUE, TeamSignal.STALE, TeamSignal.NO_PROGRESS), signals(t))
    }
}
