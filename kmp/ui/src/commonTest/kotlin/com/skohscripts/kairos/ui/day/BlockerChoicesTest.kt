package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskSpace
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class BlockerChoicesTest {
    private val t0 = Instant.parse("2026-10-10T07:00:00Z")
    private fun task(id: Long, title: String, parent: Long? = null, deadline: LocalDate? = null) =
        Task(id, title, parentId = parent, deadline = deadline, createdAt = t0, updatedAt = t0)

    @Test
    fun theSearchIgnoresCaseAndAccentsAndKeepsTheCheckedOnes() {
        val tasks = listOf(task(1, "Été au lac"), task(2, "Réunion client"), task(3, "Devis"))
        val choices = BlockerChoices.of(task(9, "Éditée"), tasks, emptyList(), tasks.associateBy { it.id }, "fr")
        assertEquals(listOf(2L), BlockerChoices.filter(choices, "reunion", emptySet()).map { it.task.id })
        assertEquals(listOf(1L, 2L), BlockerChoices.filter(choices, "ETE", setOf(2L)).map { it.task.id }) // cochée : toujours là
        assertEquals(choices, BlockerChoices.filter(choices, "  ", emptySet()))
    }

    @Test
    fun aGrandchildShowsItsWholeLineage() {
        val tasks = listOf(task(1, "A"), task(3, "C", parent = 1), task(4, "D", parent = 3))
        val choices = BlockerChoices.of(task(9, "Éditée"), tasks, emptyList(), tasks.associateBy { it.id }, "fr")
        assertEquals(listOf("A", "A › C", "A › C › D"), choices.map { it.label })
    }

    @Test
    fun subtasksShowTheirParentAndTwinsTheirDate() {
        val parent = task(1, "Dentiste")
        val tasks = listOf(
            parent, task(2, "sous 1", parent = 1), task(3, "Réserver", deadline = LocalDate(2026, 10, 15)),
            task(4, "Réserver", deadline = LocalDate(2026, 11, 15)), task(5, "Réserver"), task(6, "Seule"),
        )
        val choices = BlockerChoices.of(task(9, "Éditée"), tasks, emptyList(), tasks.associateBy { it.id }, "fr")
        assertEquals(
            listOf("Dentiste", "Dentiste › sous 1", "Réserver (15 oct.)", "Réserver (15 nov.)", "Réserver (#5)", "Seule"),
            choices.map { it.label },
        )
    }

    @Test
    fun aCandidateThatAlreadyWaitsOnTheTaskWouldMakeACycle() {
        val a = task(1, "A")
        val b = task(2, "B")
        val c = task(3, "C")
        // A attend B, B attend C : C ne peut pas attendre A ; B non plus.
        val deps = listOf(TaskDependency(1, taskId = 1, blockerId = 2, createdAt = t0), TaskDependency(2, taskId = 2, blockerId = 3, createdAt = t0))
        val choices = BlockerChoices.of(c, listOf(a, b, c), deps, emptyMap(), "fr")
        assertEquals(listOf(1L to true, 2L to true), choices.map { it.task.id to it.cyclic })
        assertEquals(listOf(false, false), BlockerChoices.of(a, listOf(a, b, c), deps, emptyMap(), "fr").map { it.cyclic })
    }

    @Test
    fun aTaskOfAnotherSpaceIsNeverOffered() {
        val team = Task(2, "Refonte API", space = TaskSpace.TEAM, createdAt = t0, updatedAt = t0)
        val personal = task(3, "Impôts")
        assertEquals(listOf(3L), BlockerChoices.of(task(1, "Perso"), listOf(team, personal), emptyList(), emptyMap(), "fr").map { it.task.id })
        assertEquals(listOf(2L), BlockerChoices.of(team.copy(id = 4), listOf(team, personal), emptyList(), emptyMap(), "fr").map { it.task.id })
    }
}
