package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Habitudes dans le dépôt, l'export et la fiche (docs/spec/habitudes.md § Données). */
class HabitRepositoryTest {
    private var now = Instant.parse("2026-11-24T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val t0 = Instant.parse("2026-11-01T08:00:00Z")
    private val day = LocalDate(2026, 11, 24)

    private suspend fun repo(vararg tasks: Task): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock, timeZone = TimeZone.UTC)
            .also { it.replaceAll(KairosSnapshot(tasks = tasks.toList())) }

    private fun daily(id: Long = 1, space: TaskSpace = TaskSpace.PERSONAL, recurrence: TaskRecurrence = TaskRecurrence.DAILY) =
        Task(id, "Méditer", recurrence = recurrence, deadline = day, scheduledDate = day, priority = 1, fibonacciPoints = 1, space = space, createdAt = t0, updatedAt = t0)

    private fun edit(task: Task, habit: HabitChange?, pin: LocalTime? = LocalTime(7, 0)) = TaskEdit(
        title = task.title, priority = task.priority, points = task.fibonacciPoints, deadline = task.deadline,
        scheduledDate = task.scheduledDate, recurrence = task.recurrence, pinTime = pin, pinDay = day, habit = habit,
    )

    @Test
    fun declaringFromTheSheetSetsTheHabitAndTheNextOccurrenceInheritsIt() = runTest {
        val r = repo(daily())
        r.updateTask(1, edit(daily(), HabitChange(on = true, after = "  Après mon café  ")))
        val declared = r.snapshot.value.tasks.single()
        assertEquals(now, declared.habitSince)
        assertEquals("Après mon café", declared.habitAfter)
        assertEquals(LocalTime(7, 0), declared.pinnedStart?.time)
        r.toggleDone(1)
        val next = r.snapshot.value.tasks.single { it.status == TaskStatus.TODO }
        assertEquals(now, next.habitSince)
        assertEquals("Après mon café", next.habitAfter)
        assertEquals(LocalTime(7, 0), next.pinnedStart?.time)
        assertNull(next.habitScore)
    }

    @Test
    fun aTeamOrMonthlyTaskCannotBecomeAHabit() = runTest {
        val r = repo(daily(1, space = TaskSpace.TEAM), daily(2, recurrence = TaskRecurrence.MONTHLY))
        r.updateTask(1, edit(daily(1, space = TaskSpace.TEAM), HabitChange(on = true)))
        r.updateTask(2, edit(daily(2, recurrence = TaskRecurrence.MONTHLY), HabitChange(on = true)))
        assertTrue(r.snapshot.value.tasks.all { it.habitSince == null })
    }

    @Test
    fun endingTheHabitStopsTheInheritanceButKeepsTheAnswers() = runTest {
        val r = repo(daily())
        r.updateTask(1, edit(daily(), HabitChange(on = true)))
        r.toggleDone(1)
        r.answerHabit(1, 5)
        val open = r.snapshot.value.tasks.single { it.status == TaskStatus.TODO }
        r.updateTask(open.id, edit(open, HabitChange(on = false)))
        assertNull(r.snapshot.value.tasks.single { it.id == open.id }.habitSince)
        assertEquals(5, r.snapshot.value.tasks.single { it.id == 1L }.habitScore)
    }

    @Test
    fun theAnswerDoesNotMoveTheEndDateAndCongratulatesOnlyOnThePassage() = runTest {
        val r = repo(daily())
        r.updateTask(1, edit(daily(), HabitChange(on = true)))
        val scores = listOf(3, 6, 7, 7)
        val installed = scores.map { score ->
            val open = r.snapshot.value.tasks.single { it.status == TaskStatus.TODO }
            r.toggleDone(open.id)
            val doneAt = r.snapshot.value.tasks.single { it.id == open.id }.updatedAt
            now += 15.days
            val passage = r.answerHabit(open.id, score)
            assertEquals(doneAt, r.snapshot.value.tasks.single { it.id == open.id }.updatedAt)
            passage
        }
        assertEquals(listOf(false, false, true, false), installed)
    }

    @Test
    fun aSnapshotWithoutHabitExportsWithoutHabitKeysAndHabitsSurviveARoundTrip() = runTest {
        val plain = ExportCodec.encode(KairosSnapshot(tasks = listOf(daily())), "4.6.0", now)
        assertFalse("habit" in plain)
        val habit = daily().copy(habitSince = t0, habitAfter = "Après le déjeuner", habitScore = 6, status = TaskStatus.DONE)
        val back = ExportCodec.decode(ExportCodec.encode(KairosSnapshot(tasks = listOf(habit)), "4.6.0", now)).tasks.single()
        assertEquals(t0, back.habitSince)
        assertEquals("Après le déjeuner", back.habitAfter)
        assertEquals(6, back.habitScore)
    }

    @Test
    fun habitColumnsSurviveTheDatabase() = runTest {
        val habit = daily().copy(habitSince = t0, habitAfter = "Après le déjeuner", habitScore = 6)
        val r = repo(habit)
        assertEquals(habit, r.snapshot.value.tasks.single())
    }
}
