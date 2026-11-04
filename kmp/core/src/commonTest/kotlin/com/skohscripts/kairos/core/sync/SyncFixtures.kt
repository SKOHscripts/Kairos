package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.model.BlockException
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.BlockSource
import com.skohscripts.kairos.core.model.CalendarSource
import com.skohscripts.kairos.core.model.DayMark
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.NoteStatus
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/** Jeux de données des tests de synchronisation. */
internal object SyncFixtures {
    val t0: Instant = Instant.parse("2026-10-01T08:00:00Z")
    fun at(minutes: Int): Instant = Instant.fromEpochMilliseconds(t0.toEpochMilliseconds() + minutes * 60_000L)

    fun task(id: Long, title: String, minutes: Int = 0, vararg tweaks: (Task) -> Task): Task =
        tweaks.fold(Task(id, title, priority = 1, fibonacciPoints = 3, createdAt = at(minutes), updatedAt = at(minutes))) { t, f -> f(t) }

    /** Une base qui touche chaque genre de la portée, et des données hors portée (tâche d'équipe, tâche reçue). */
    fun rich(): KairosSnapshot = KairosSnapshot(
        tasks = listOf(
            task(1, "Rédiger le rapport", 0, { it.copy(description = "trimestriel", deadline = LocalDate(2026, 10, 9), projectTag = "Atlas", estimatedMinutes = 45) }),
            task(2, "Relire", 1, { it.copy(parentId = 1, pinnedStart = LocalDateTime(2026, 10, 2, 9, 30)) }),
            task(3, "Courrier", 2, { it.copy(recurrence = TaskRecurrence.WEEKLY, recurrenceDayOfWeek = 0, seriesId = 3, taskType = "Admin") }),
            task(4, "Loyer", 3, { it.copy(recurrence = TaskRecurrence.MONTHLY_ON_DAY, recurrenceDayOfMonth = 5, recurrencePeriod = "2026-10") }),
            task(5, "Fini", 4, { it.copy(status = TaskStatus.DONE, manualTimeSpentMinutes = 20) }),
            task(6, "Tâche d'équipe", 5, { it.copy(space = TaskSpace.TEAM, assigneeId = 1, teamUid = "tu-6") }),
            task(7, "Reçue du manager", 6, { it.copy(origin = "o|team|Paul") }),
        ),
        timeBlocks = listOf(
            TimeBlock(1, "Réunion", LocalDateTime(2026, 10, 2, 14, 0), LocalDateTime(2026, 10, 2, 15, 0), createdAt = at(10)),
            TimeBlock(2, "Focus", LocalDateTime(2026, 10, 2, 9, 0), LocalDateTime(2026, 10, 2, 11, 0), BlockKind.DEEPWORK, BlockRecurrence.WEEKDAYS, at(11)),
            TimeBlock(3, "Agenda pro", LocalDateTime(2026, 10, 3, 10, 0), LocalDateTime(2026, 10, 3, 11, 0), createdAt = at(12), source = BlockSource.Imported(1, "ev-1")),
        ),
        dependencies = listOf(TaskDependency(1, taskId = 2, blockerId = 1, createdAt = at(20)), TaskDependency(2, taskId = 6, blockerId = 1, createdAt = at(21))),
        workSessions = listOf(
            WorkSession(1, 5, at(30), at(50), at(30)),
            WorkSession(2, 1, at(60), null, at(60)),
            WorkSession(3, 6, at(70), at(80), at(70)),
        ),
        notes = listOf(Note(1, "Idée", NoteStatus.OPEN, null, at(40), at(40)), Note(2, "Faite", NoteStatus.ARCHIVED, 1, at(41), at(42))),
        settings = Settings(workdayStartHour = 8, language = "fr", themeMode = Settings.THEME_MODE_DARK),
        blockExceptions = listOf(BlockException(1, 2, LocalDate(2026, 10, 5), title = "Focus court", start = LocalTime(9, 0), end = LocalTime(10, 0), createdAt = at(90))),
        calendarSources = listOf(CalendarSource(1, "Agenda Pro", hideTitles = true, importedAt = at(100), createdAt = at(100))),
        dayMarks = listOf(DayMark(1, "Férié", LocalDate(2026, 11, 11), LocalDate(2026, 11, 11), BlockSource.Imported(1, "ev-2"), at(101))),
    )

    /** Générateur d'`uid` aléatoires reproductibles pour les tests. */
    class Uids(private val prefix: String = "u") : () -> String {
        private var n = 0
        override fun invoke(): String = "$prefix${++n}"
    }
}
