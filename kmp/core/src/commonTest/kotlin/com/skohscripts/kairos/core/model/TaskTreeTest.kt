package com.skohscripts.kairos.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Lignée et fil d'Ariane des sous-tâches (docs/spec/sous-taches.md § Arbre des tâches). */
class TaskTreeTest {
    private val t0 = Instant.parse("2026-11-05T08:00:00Z")
    private fun t(id: Long, title: String, parent: Long? = null) = Task(id, title, parentId = parent, createdAt = t0, updatedAt = t0)

    @Test
    fun aGrandchildShowsItsWholeLineage() {
        // Issue #201 : A ⊃ {B, C}, C ⊃ D ; D se lisait « C › D ».
        val byId = listOf(t(1, "A"), t(2, "B", 1), t(3, "C", 1), t(4, "D", 3)).associateBy { it.id }
        assertEquals("A › C", TaskTree.path(4, byId))
        assertEquals("A", TaskTree.path(3, byId))
        assertNull(TaskTree.path(1, byId))
        assertEquals(listOf(1L, 3L), TaskTree.ancestors(4, byId).map { it.id })
    }

    @Test
    fun aLongLineageKeepsTheRootAndTheLastTwo() {
        val byId = (1L..6L).map { t(it, "T$it", if (it == 1L) null else it - 1) }.associateBy { it.id }
        assertEquals("T1 › T2 › T3", TaskTree.path(4, byId))
        assertEquals("T1 › … › T4 › T5", TaskTree.path(6, byId))
    }

    @Test
    fun aMissingMotherOrALoopEndsTheLineage() {
        assertNull(TaskTree.path(2, mapOf(2L to t(2, "Orpheline", 99))))
        val loop = listOf(t(1, "A", 2), t(2, "B", 1)).associateBy { it.id }
        assertEquals("B", TaskTree.path(1, loop))
        assertEquals(listOf(2L), TaskTree.ancestors(1, loop).map { it.id })
    }

    @Test
    fun theFloorIsTheStrongestAncestorPriority() {
        val byId = listOf(t(1, "A").copy(priority = 1), t(2, "B", 1).copy(priority = 0), t(3, "C", 2), t(4, "D", 3).copy(priority = 2))
            .associateBy { it.id }
        assertEquals(0, TaskTree.priorityFloor(4, byId))
        assertEquals("B", TaskTree.floorTask(4, byId)?.title)
        assertEquals(0, TaskTree.inheritedPriority(3, byId))
        assertNull(TaskTree.priorityFloor(1, byId))
        assertEquals(0, TaskTree.bounded(2, 0))
        assertEquals(0, TaskTree.bounded(null, 0))
        assertEquals(1, TaskTree.bounded(1, 2)) // plus prioritaire que sa mère : permis
        assertNull(TaskTree.bounded(null, null))
    }

    @Test
    fun raisingCascadesToOpenDescendantsOnly() {
        val byId = listOf(
            t(1, "A").copy(priority = 0), t(2, "B", 1).copy(priority = 2), t(3, "C", 2), t(4, "Faite", 1).copy(priority = 2, status = TaskStatus.DONE),
        ).associateBy { it.id }
        assertEquals(mapOf(2L to 0, 3L to 0), TaskTree.raisedDescendants(1, byId).associate { it.id to it.priority })
        assertEquals(listOf(2L, 4L, 3L), TaskTree.descendants(1, byId.values).map { it.id })
    }
}
