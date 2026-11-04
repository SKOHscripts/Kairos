package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.engine.Dependencies.Edge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cycles de dépendances et tâches en aval (QA du 2026-11-04, docs/spec/dependances.md). */
class DependencyCyclesTest {
    private val allTodo: (Long) -> Boolean = { true }

    @Test
    fun aTaskDownstreamOfACycleStaysBlocked() {
        // 1 ⇄ 2 forment un cycle ; 3 attend 1.
        val edges = listOf(Edge(1, 2), Edge(2, 1), Edge(3, 1))
        assertEquals(setOf(1L, 2L), Dependencies.cycleNodes(edges))
        assertEquals(listOf(Edge(3, 1)), Dependencies.acyclicEdges(edges))
        assertEquals(setOf(3L), Dependencies.blockedTaskIds(edges, allTodo))
        assertEquals(mapOf(3L to listOf(1L)), Dependencies.openBlockers(edges, allTodo))
    }

    @Test
    fun aChainBehindACycleIsBlockedStepByStep() {
        // 1 ⇄ 2, 3 attend 2, 4 attend 3 : 3 et 4 bloquées, et 2 hérite de l'urgence de 4.
        val edges = listOf(Edge(1, 2), Edge(2, 1), Edge(3, 2), Edge(4, 3))
        assertEquals(setOf(3L, 4L), Dependencies.blockedTaskIds(edges, allTodo))
        val derived = Dependencies.derivedUrgency(edges, mapOf(1L to 5, 2L to 5, 3L to 5, 4L to 1))
        assertEquals(1, derived[2L])
        assertEquals(5, derived[1L])
    }

    @Test
    fun twoCyclesLinkedKeepTheirLink() {
        // 1 ⇄ 2 et 3 ⇄ 4 ; 3 attend 1 : l'arête entre les deux cycles reste.
        val edges = listOf(Edge(1, 2), Edge(2, 1), Edge(3, 4), Edge(4, 3), Edge(3, 1))
        assertEquals(setOf(1L, 2L, 3L, 4L), Dependencies.cycleNodes(edges))
        assertEquals(listOf(Edge(3, 1)), Dependencies.acyclicEdges(edges))
        assertEquals(setOf(3L), Dependencies.blockedTaskIds(edges, allTodo))
    }

    @Test
    fun aSelfLoopIsACycleAndIsIgnored() {
        val edges = listOf(Edge(1, 1), Edge(2, 1))
        assertEquals(setOf(1L), Dependencies.cycleNodes(edges))
        assertEquals(listOf(Edge(2, 1)), Dependencies.acyclicEdges(edges))
    }

    @Test
    fun aLongChainDoesNotOverflowTheStack() {
        val n = 20_000L
        val edges = (1 until n).map { Edge(it + 1, it) } + Edge(1, n)
        assertEquals(n.toInt(), Dependencies.cycleNodes(edges).size)
        assertTrue(Dependencies.acyclicEdges(edges).isEmpty())
    }
}
