package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DayListsTest {
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")
    private fun task(id: Long, p: Int? = 1, pts: Int? = 3, deadline: LocalDate? = null, status: TaskStatus = TaskStatus.TODO, updated: Instant = t0) =
        Task(id, "T$id", priority = p, fibonacciPoints = pts, deadline = deadline, status = status, createdAt = t0, updatedAt = updated)

    @Test
    fun unqualifiedTasksGoToTheInboxAndNowhereElse() {
        val lists = DayLists.of(listOf(task(1, p = null), task(2, pts = null), task(3, p = null, pts = null), task(4)))
        assertEquals(listOf(1L, 2L, 3L), lists.inbox.map { it.id })
        assertEquals(listOf(4L), lists.todo.map { it.id })
    }

    @Test
    fun provisionalOrderIsPriorityThenDeadlineThenAge() {
        val d1 = LocalDate(2026, 10, 1)
        val d2 = LocalDate(2026, 10, 9)
        val lists = DayLists.of(listOf(task(1, p = 2, deadline = d1), task(2, p = 1), task(3, p = 1, deadline = d2), task(4, p = 1, deadline = d1), task(5, p = 0)))
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), lists.todo.map { it.id })
    }

    @Test
    fun doneIsMostRecentFirstAndArchivedIsHidden() {
        val lists = DayLists.of(
            listOf(
                task(1, status = TaskStatus.DONE, updated = Instant.parse("2026-09-27T07:00:00Z")),
                task(2, status = TaskStatus.DONE, updated = Instant.parse("2026-09-28T09:00:00Z")),
                task(3, status = TaskStatus.ARCHIVED),
            ),
        )
        assertEquals(listOf(2L, 1L), lists.done.map { it.id })
        assertEquals(emptyList(), lists.inbox + lists.todo)
    }

    @Test
    fun shortDatesPerLanguage() {
        assertEquals("30 sept.", Dates.short(LocalDate(2026, 9, 30), "fr"))
        assertEquals("Sep 30", Dates.short(LocalDate(2026, 9, 30), "en"))
        assertEquals("1 janv.", Dates.short(LocalDate(2027, 1, 1), "de"))
    }
}
