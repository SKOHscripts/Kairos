package com.skohscripts.kairos.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** « À traiter » tant qu'il manque la priorité, les points ou le type (docs/spec/vue-jour.md § Décisions, 2026-11-03). */
class QualificationTest {
    private val at = Instant.fromEpochMilliseconds(0)
    private fun task(priority: Int? = null, points: Int? = null, type: String = "") =
        Task(id = 1, title = "T", priority = priority, fibonacciPoints = points, taskType = type, createdAt = at, updatedAt = at)

    @Test
    fun theTypeKeepsTheTaskInTheInboxWhenTypesAreDefined() {
        val pointsFirst = task(points = 3)
        assertEquals(Missing(priority = true, points = false, type = true), pointsFirst.missing(typeRequired = true))
        assertTrue(task(priority = 1, points = 3).needsProcessing(typeRequired = true))
        assertFalse(task(priority = 1, points = 3, type = "Dev").needsProcessing(typeRequired = true))
        assertNull(task(priority = 1, points = 3, type = "Dev").missing(typeRequired = true))
    }

    @Test
    fun anEmptyTypeListDoesNotRequireAType() {
        assertFalse(task(priority = 1, points = 3).needsProcessing(typeRequired = false))
        assertEquals(Missing(priority = false, points = true, type = false), task(priority = 1).missing(typeRequired = false))
        assertTrue(Settings().copy(taskTypes = "Dev, Admin").typeRequired)
        assertFalse(Settings().copy(taskTypes = " , ").typeRequired)
    }
}
