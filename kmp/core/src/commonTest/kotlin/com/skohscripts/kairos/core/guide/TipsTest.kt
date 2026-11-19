package com.skohscripts.kairos.core.guide

import com.skohscripts.kairos.core.model.BestHoursSettings
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.CalendarSource
import com.skohscripts.kairos.core.model.GuideSettings
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.ReminderSettings
import com.skohscripts.kairos.core.model.ScheduleSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.team.TeamEvent
import com.skohscripts.kairos.core.team.TeamEventKind
import com.skohscripts.kairos.core.team.TeamEventSource
import com.skohscripts.kairos.core.team.TeamMember
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Conditions des conseils (docs/spec/conseils.md § Conseils de l'espace Perso et de l'espace Équipe) : chaque conseil est vrai
 * sur un instantané qui remplit sa condition et faux dès qu'une condition manque, aux bornes. Chaque cas écarte tous les autres
 * conseils par la mémoire ([only]) pour lire une seule condition à la fois.
 */
class TipsTest {
    /** Un mercredi à midi (UTC). */
    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private val tz = TimeZone.UTC
    private val desktop = TipFacts(TipPlatform.DESKTOP, darkTheme = true, linkedFile = true, calendarImport = false)

    private var nextId = 1L
    private fun at(daysAgo: Int, hour: Int = 10): Instant = now - daysAgo.days + (hour - 12).hours

    private fun task(
        daysAgo: Int = 1,
        status: TaskStatus = TaskStatus.TODO,
        title: String = "Tâche ${nextId}",
        estimate: Int? = null,
        type: String = "",
        points: Int? = null,
        deadline: LocalDate? = null,
        doneDaysAgo: Int = daysAgo,
        space: TaskSpace = TaskSpace.PERSONAL,
        assignee: Long? = null,
        recurrence: TaskRecurrence = TaskRecurrence.NONE,
        pinned: LocalDateTime? = null,
        priority: Int? = null,
    ) = Task(
        nextId++, title, status = status, estimatedMinutes = estimate, taskType = type, fibonacciPoints = points, deadline = deadline,
        space = space, assigneeId = assignee, recurrence = recurrence, pinnedStart = pinned, priority = priority,
        createdAt = at(daysAgo), updatedAt = at(if (status == TaskStatus.DONE) doneDaysAgo else daysAgo),
    )

    private fun session(task: Task, daysAgo: Int, minutes: Int, hour: Int = 10) =
        WorkSession(nextId++, task.id, at(daysAgo, hour), at(daysAgo, hour) + minutes.minutes, createdAt = at(daysAgo, hour))

    /** Une note qui fixe l'âge de la base à [days] jours. */
    private fun age(days: Int) = Note(nextId++, "Première note", createdAt = at(days), updatedAt = at(days))

    private fun only(id: TipId, memory: GuideSettings = GuideSettings()) =
        memory.copy(tipsDismissed = TipId.entries.filter { it != id }.map { it.key })

    private fun due(
        snapshot: KairosSnapshot,
        id: TipId,
        facts: TipFacts = desktop,
        memory: GuideSettings = GuideSettings(),
        space: TipSpace = id.space,
        at: Instant = now,
    ): Tip? = Tips.due(snapshot, only(id, memory), facts, space, at, tz)

    private fun date(daysAgo: Int): LocalDate = (now - daysAgo.days).toLocalDateTime(tz).date
    private fun local(daysAgo: Int, hour: Int): LocalDateTime = at(daysAgo, hour).toLocalDateTime(tz)

    // ---- Sûreté des données ----

    @Test
    fun dataSafetyNeedsThirtyDaysFiftyTasksNoLinkedFileAndNoRecentExport() {
        val facts = desktop.copy(linkedFile = false)
        val tasks = List(50) { task() }
        val base = KairosSnapshot(tasks = tasks, notes = listOf(age(30)))
        assertEquals(Tip(TipId.DATA_SAFETY, count = 50), due(base, TipId.DATA_SAFETY, facts))
        assertNull(due(base.copy(tasks = tasks.drop(1)), TipId.DATA_SAFETY, facts))
        assertNull(due(base.copy(notes = listOf(age(29))), TipId.DATA_SAFETY, facts))
        assertNull(due(base, TipId.DATA_SAFETY, desktop))
        assertNull(due(base, TipId.DATA_SAFETY, facts, GuideSettings(lastExport = date(89).toString())))
        assertEquals(TipId.DATA_SAFETY, due(base, TipId.DATA_SAFETY, facts, GuideSettings(lastExport = date(90).toString()))?.id)
    }

    // ---- Statistiques ----

    @Test
    fun statsFirstNeedsSevenDaysFiveDoneAndStatsNeverOpened() {
        val done = List(5) { task(status = TaskStatus.DONE) }
        val base = KairosSnapshot(tasks = done, notes = listOf(age(7)))
        assertEquals(Tip(TipId.STATS_FIRST, count = 5), due(base, TipId.STATS_FIRST))
        assertNull(due(base.copy(tasks = done.drop(1)), TipId.STATS_FIRST))
        assertNull(due(base.copy(notes = listOf(age(6))), TipId.STATS_FIRST))
        assertNull(due(base, TipId.STATS_FIRST, memory = GuideSettings(seenScreens = listOf(Tips.SCREEN_STATS))))
    }

    @Test
    fun statsCalibrationNeedsTenTimedEstimatedDoneTasks() {
        val done = List(10) { task(status = TaskStatus.DONE, estimate = 30) }
        val sessions = done.map { session(it, 2, 30) }
        val base = KairosSnapshot(tasks = done, workSessions = sessions, notes = listOf(age(10)))
        assertEquals(Tip(TipId.STATS_CALIBRATION, count = 10), due(base, TipId.STATS_CALIBRATION))
        assertNull(due(base.copy(workSessions = sessions.drop(1)), TipId.STATS_CALIBRATION))
        // Temps saisi à la main seulement : rien de chronométré.
        assertNull(due(base.copy(tasks = done.map { it.copy(manualTimeSpentMinutes = 30) }, workSessions = emptyList()), TipId.STATS_CALIBRATION))
    }

    @Test
    fun statsCorrectNeedsAReliableBiasOfAQuarterOrMore() {
        fun snapshot(real: Int, count: Int = 3): KairosSnapshot {
            val done = List(count) { task(status = TaskStatus.DONE, estimate = 60, type = "Dev") }
            return KairosSnapshot(tasks = done, workSessions = done.map { session(it, 2, real) }, notes = listOf(age(10)))
        }
        val tip = due(snapshot(90), TipId.STATS_CORRECT)
        assertEquals(TipId.STATS_CORRECT, tip?.id)
        assertEquals("Dev", tip?.name)
        assertEquals(1.5, tip?.ratio)
        assertEquals(TipId.STATS_CORRECT, due(snapshot(75), TipId.STATS_CORRECT)?.id) // 1,25 : la borne compte
        assertNull(due(snapshot(74), TipId.STATS_CORRECT))
        assertEquals(TipId.STATS_CORRECT, due(snapshot(48), TipId.STATS_CORRECT)?.id) // 0,8
        assertNull(due(snapshot(49), TipId.STATS_CORRECT))
        assertNull(due(snapshot(90, count = 2), TipId.STATS_CORRECT)) // pas fiable
    }

    @Test
    fun statsCorrectAlsoReadsPointsLevels() {
        val done = List(3) { task(status = TaskStatus.DONE, estimate = 60, points = 5) }
        val tip = due(KairosSnapshot(tasks = done, workSessions = done.map { session(it, 2, 30) }, notes = listOf(age(10))), TipId.STATS_CORRECT)
        assertEquals(5, tip?.level)
        assertEquals(1, tip?.variant)
        assertEquals(0.5, tip?.ratio)
    }

    @Test
    fun statsHoursAndBestHoursNeedAReliableType() {
        val tasks = List(5) { task(status = TaskStatus.DONE, type = "Dev") }
        val sessions = tasks.mapIndexed { i, t -> session(t, i + 1, 30) }
        val base = KairosSnapshot(tasks = tasks, workSessions = sessions, notes = listOf(age(10)))
        assertEquals(Tip(TipId.STATS_HOURS, name = "Dev"), due(base, TipId.STATS_HOURS))
        assertNull(due(base.copy(workSessions = sessions.drop(1)), TipId.STATS_HOURS)) // 4 sessions
        assertNull(due(base.copy(workSessions = tasks.mapIndexed { i, t -> session(t, i + 1, 20) }), TipId.STATS_HOURS)) // 100 min
        assertEquals(Tip(TipId.BEST_HOURS, name = "Dev"), due(base, TipId.BEST_HOURS))
        val on = Settings(schedule = ScheduleSettings(bestHours = BestHoursSettings(enabled = true)))
        assertNull(due(base.copy(settings = on), TipId.BEST_HOURS))
    }

    @Test
    fun weekReviewEarlyInTheWeekAfterFiveTasksDoneLastWeek() {
        // Mercredi 18 : la semaine passée va du lundi 9 au dimanche 15 (3 à 9 jours avant).
        val done = (3..7).map { task(daysAgo = 10, status = TaskStatus.DONE, doneDaysAgo = it) }
        val base = KairosSnapshot(tasks = done, notes = listOf(age(10)))
        assertEquals(Tip(TipId.WEEK_REVIEW, count = 5), due(base, TipId.WEEK_REVIEW))
        assertNull(due(base.copy(tasks = done.drop(1)), TipId.WEEK_REVIEW))
        assertNull(due(base, TipId.WEEK_REVIEW, at = now + 1.days)) // jeudi
        // Le lundi 16 est de cette semaine, pas de la précédente.
        assertNull(due(base.copy(tasks = done.drop(1) + task(daysAgo = 10, status = TaskStatus.DONE, doneDaysAgo = 2)), TipId.WEEK_REVIEW))
    }

    // ---- Réglages ----

    @Test
    fun workHoursAfterThreeDaysOutsideDefaultHours() {
        val t = task(daysAgo = 20)
        val late = (1..3).map { session(t, it, 30, hour = 20) }
        val base = KairosSnapshot(tasks = listOf(t), workSessions = late, notes = listOf(age(20)))
        assertEquals(Tip(TipId.WORK_HOURS, count = 3), due(base, TipId.WORK_HOURS))
        assertNull(due(base.copy(workSessions = late.drop(1)), TipId.WORK_HOURS))
        assertNull(due(base.copy(workSessions = (1..3).map { session(t, it, 30, hour = 17) }), TipId.WORK_HOURS))
        assertNull(due(base.copy(workSessions = (14..16).map { session(t, it, 30, hour = 20) }), TipId.WORK_HOURS)) // hors des 14 jours
        assertNull(due(base.copy(settings = Settings(workdayEndHour = 21)), TipId.WORK_HOURS))
    }

    @Test
    fun remindersAfterMissedDeadlinesOrFixedTimeTasks() {
        val missed = List(3) { task(daysAgo = 20, deadline = date(2)) }
        val base = KairosSnapshot(tasks = missed, notes = listOf(age(20)))
        assertEquals(Tip(TipId.REMINDERS, count = 3), due(base, TipId.REMINDERS))
        assertNull(due(base.copy(tasks = missed.drop(1)), TipId.REMINDERS))
        // Faite après l'échéance : manquée aussi ; faite à temps : non.
        val late = task(daysAgo = 20, deadline = date(5), status = TaskStatus.DONE, doneDaysAgo = 3)
        val onTime = task(daysAgo = 20, deadline = date(3), status = TaskStatus.DONE, doneDaysAgo = 3)
        assertEquals(TipId.REMINDERS, due(base.copy(tasks = missed.drop(1) + late), TipId.REMINDERS)?.id)
        assertNull(due(base.copy(tasks = missed.drop(1) + onTime), TipId.REMINDERS))
        val pinned = List(3) { task(pinned = LocalDateTime(date(-2), kotlinx.datetime.LocalTime(9, 0))) }
        assertEquals(Tip(TipId.REMINDERS, count = 3, variant = 1), due(base.copy(tasks = pinned), TipId.REMINDERS))
        assertNull(due(base.copy(settings = Settings(reminders = ReminderSettings(enabled = true))), TipId.REMINDERS))
    }

    @Test
    fun dailySummaryAfterTwoWeeksOfFiveActiveDays() {
        val tasks = ((0..4) + (7..11)).map { task(daysAgo = it) }
        val base = KairosSnapshot(tasks = tasks, notes = listOf(age(20)))
        assertEquals(TipId.DAILY_SUMMARY, due(base, TipId.DAILY_SUMMARY)?.id)
        assertNull(due(base.copy(tasks = tasks.drop(1)), TipId.DAILY_SUMMARY))
        assertNull(due(base.copy(settings = Settings(reminders = ReminderSettings(dailySummary = true))), TipId.DAILY_SUMMARY))
    }

    @Test
    fun timerLimitsAfterASessionAsLongAsTheForgottenTimerAlert() {
        val t = task(daysAgo = 20)
        val base = KairosSnapshot(tasks = listOf(t), workSessions = listOf(session(t, 3, 180)), notes = listOf(age(20)))
        assertEquals(Tip(TipId.TIMER_LIMITS, count = 180), due(base, TipId.TIMER_LIMITS))
        assertNull(due(base.copy(workSessions = listOf(session(t, 3, 179))), TipId.TIMER_LIMITS))
        assertNull(due(base.copy(workSessions = listOf(session(t, 15, 300))), TipId.TIMER_LIMITS))
    }

    @Test
    fun calendarImportAfterFiveManualBusySlots() {
        val facts = desktop.copy(calendarImport = true)
        fun block(daysAgo: Int) = TimeBlock(nextId++, "Réunion", local(daysAgo, 14), local(daysAgo, 15), BlockKind.BUSY, createdAt = at(daysAgo))
        val blocks = (1..5).map(::block)
        val base = KairosSnapshot(timeBlocks = blocks, notes = listOf(age(20)))
        assertEquals(Tip(TipId.CALENDAR_IMPORT, count = 5), due(base, TipId.CALENDAR_IMPORT, facts))
        assertNull(due(base.copy(timeBlocks = blocks.drop(1)), TipId.CALENDAR_IMPORT, facts))
        assertNull(due(base, TipId.CALENDAR_IMPORT, desktop))
        val source = CalendarSource(1, "Travail", importedAt = now, createdAt = now)
        assertNull(due(base.copy(calendarSources = listOf(source)), TipId.CALENDAR_IMPORT, facts))
    }

    @Test
    fun darkThemeAfterFiveLateDaysInALightTheme() {
        val t = task(daysAgo = 20)
        val base = KairosSnapshot(tasks = listOf(t), workSessions = (1..5).map { session(t, it, 30, hour = 21) }, notes = listOf(age(20)))
        val light = desktop.copy(darkTheme = false)
        assertEquals(Tip(TipId.DARK_THEME, count = 5), due(base, TipId.DARK_THEME, light))
        assertNull(due(base, TipId.DARK_THEME, desktop))
        assertNull(due(base.copy(workSessions = (1..4).map { session(t, it, 30, hour = 21) }), TipId.DARK_THEME, light))
    }

    // ---- Fonctionnalités ----

    @Test
    fun timerWhenNeverUsedAfterSevenDaysAndFiveDone() {
        val done = List(5) { task(status = TaskStatus.DONE) }
        val base = KairosSnapshot(tasks = done, notes = listOf(age(7)))
        assertEquals(Tip(TipId.TIMER, count = 5), due(base, TipId.TIMER))
        assertNull(due(base.copy(workSessions = listOf(session(done[0], 1, 10))), TipId.TIMER))
        assertNull(due(base.copy(notes = listOf(age(6))), TipId.TIMER))
    }

    @Test
    fun recurrenceAfterTheSameTitleThreeTimes() {
        val same = listOf(task(title = "Rapport hebdo"), task(title = "rapport HEBDO "), task(title = "Rapport hébdo"))
        val base = KairosSnapshot(tasks = same, notes = listOf(age(20)))
        val tip = due(base, TipId.RECURRENCE)
        assertEquals(3, tip?.count)
        assertNull(due(base.copy(tasks = same.drop(1)), TipId.RECURRENCE))
        assertNull(due(base.copy(tasks = same + task(recurrence = TaskRecurrence.WEEKLY)), TipId.RECURRENCE))
        assertNull(due(base.copy(tasks = same.drop(1) + task(daysAgo = 61, title = "Rapport hebdo")), TipId.RECURRENCE))
    }

    @Test
    fun recapAfterFourteenDaysIfNeverOpened() {
        val base = KairosSnapshot(notes = listOf(age(14)))
        assertEquals(Tip(TipId.RECAP), due(base, TipId.RECAP))
        assertNull(due(base.copy(notes = listOf(age(13))), TipId.RECAP))
        assertNull(due(base, TipId.RECAP, memory = GuideSettings(seenScreens = listOf(Tips.SCREEN_RECAP))))
    }

    @Test
    fun widgetsOnAndroidAfterTenActiveDaysWithoutAWidget() {
        val tasks = (0..9).map { task(daysAgo = it) }
        val base = KairosSnapshot(tasks = tasks, notes = listOf(age(20)))
        val android = TipFacts(TipPlatform.ANDROID, darkTheme = true, linkedFile = true, widgetPlaced = false)
        assertEquals(Tip(TipId.WIDGETS, count = 10), due(base, TipId.WIDGETS, android))
        assertNull(due(base.copy(tasks = tasks.drop(1)), TipId.WIDGETS, android))
        assertNull(due(base, TipId.WIDGETS, android.copy(widgetPlaced = true)))
        assertNull(due(base, TipId.WIDGETS, desktop))
    }

    @Test
    fun shortcutsOnDesktopAndWebOnly() {
        val base = KairosSnapshot(notes = listOf(age(7)))
        assertEquals(Tip(TipId.SHORTCUTS), due(base, TipId.SHORTCUTS))
        assertEquals(Tip(TipId.SHORTCUTS), due(base, TipId.SHORTCUTS, desktop.copy(platform = TipPlatform.WEB)))
        assertNull(due(base, TipId.SHORTCUTS, desktop.copy(platform = TipPlatform.ANDROID)))
        assertNull(due(base, TipId.SHORTCUTS, memory = GuideSettings(seenScreens = listOf(Tips.SCREEN_SHORTCUT))))
        assertNull(due(base.copy(notes = listOf(age(6))), TipId.SHORTCUTS))
    }

    // ---- Espace Équipe ----

    private val teamOn = Settings(team = TeamSettings(enabled = true))
    private fun member(id: Long, self: Boolean = false, hoursPerDay: Double = 8.0) =
        TeamMember(id, "m$id", if (self) "Moi" else "Alex", hoursPerDay = hoursPerDay, isSelf = self, createdAt = at(30), updatedAt = at(30))

    @Test
    fun teamSignalsAfterThreeFlaggedTasks() {
        val overdue = List(3) { task(daysAgo = 20, space = TaskSpace.TEAM, deadline = date(3)) }
        val base = KairosSnapshot(tasks = overdue, notes = listOf(age(20)), settings = teamOn)
        assertEquals(Tip(TipId.TEAM_SIGNALS, count = 3), due(base, TipId.TEAM_SIGNALS))
        assertNull(due(base.copy(tasks = overdue.drop(1)), TipId.TEAM_SIGNALS))
        assertNull(due(base.copy(settings = Settings()), TipId.TEAM_SIGNALS)) // espace Équipe coupé
    }

    @Test
    fun teamLoadWhenAMemberIsAboveTheWarningRate() {
        val alex = member(2, hoursPerDay = 1.0)
        val heavy = List(3) { task(daysAgo = 5, space = TaskSpace.TEAM, assignee = 2, estimate = 600, priority = 1, points = 3, type = "Dev") }
        val base = KairosSnapshot(tasks = heavy, members = listOf(member(1, self = true), alex), notes = listOf(age(20)), settings = teamOn)
        val tip = due(base, TipId.TEAM_LOAD)
        assertEquals(TipId.TEAM_LOAD, tip?.id)
        assertEquals("Alex", tip?.name)
        assertNull(due(base.copy(tasks = emptyList()), TipId.TEAM_LOAD))
    }

    @Test
    fun teamForecastNeedsHistoryADeadlineAndForecastsNeverOpened() {
        val finished = List(8) { task(daysAgo = 20, space = TaskSpace.TEAM, status = TaskStatus.DONE, doneDaysAgo = 5) }
        val dated = task(daysAgo = 5, space = TaskSpace.TEAM, deadline = date(-10))
        val base = KairosSnapshot(tasks = finished + dated, notes = listOf(age(20)), settings = teamOn)
        assertEquals(Tip(TipId.TEAM_FORECAST, count = 8), due(base, TipId.TEAM_FORECAST))
        assertNull(due(base.copy(tasks = finished.drop(1) + dated), TipId.TEAM_FORECAST))
        assertNull(due(base.copy(tasks = finished), TipId.TEAM_FORECAST))
        assertNull(due(base, TipId.TEAM_FORECAST, memory = GuideSettings(seenScreens = listOf(Tips.SCREEN_TEAM_FORECAST))))
    }

    @Test
    fun teamSuggestWithFiveQualifiedUnassignedTasksAndTwoMembers() {
        val open = List(5) { task(daysAgo = 5, space = TaskSpace.TEAM, priority = 1, points = 3, type = "Dev") }
        val base = KairosSnapshot(tasks = open, members = listOf(member(1, self = true), member(2)), notes = listOf(age(20)), settings = teamOn)
        assertEquals(Tip(TipId.TEAM_SUGGEST, count = 5), due(base, TipId.TEAM_SUGGEST))
        assertNull(due(base.copy(tasks = open.drop(1) + task(daysAgo = 5, space = TaskSpace.TEAM)), TipId.TEAM_SUGGEST)) // à qualifier
        assertNull(due(base.copy(members = listOf(member(1, self = true))), TipId.TEAM_SUGGEST))
    }

    @Test
    fun teamExchangesUntilAFirstPackIsSent() {
        val assigned = List(5) { task(daysAgo = 14, space = TaskSpace.TEAM, assignee = 2) }
        val members = listOf(member(1, self = true), member(2))
        val base = KairosSnapshot(tasks = assigned, members = members, notes = listOf(age(20)), settings = teamOn)
        assertEquals(Tip(TipId.TEAM_EXCHANGES, count = 5), due(base, TipId.TEAM_EXCHANGES))
        assertNull(due(base.copy(tasks = assigned.drop(1)), TipId.TEAM_EXCHANGES))
        assertNull(due(base.copy(tasks = assigned.map { it.copy(createdAt = at(13)) }), TipId.TEAM_EXCHANGES))
        assertNull(due(base.copy(tasks = assigned.map { it.copy(assigneeId = 1) }), TipId.TEAM_EXCHANGES)) // assignées à « moi »
        val sent = TeamEvent(1, assigned[0].id, "T", 2, TeamEventKind.SENT, source = TeamEventSource.MANUAL, at = at(2))
        assertNull(due(base.copy(teamEvents = listOf(sent)), TipId.TEAM_EXCHANGES))
    }
}
