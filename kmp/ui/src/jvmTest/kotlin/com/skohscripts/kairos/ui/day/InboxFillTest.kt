package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.core.model.Task
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Durée posée quand une tâche de « À traiter » vient d'être entièrement qualifiée (docs/spec/vue-jour.md, issue #166) :
 * seulement avec priorité, points et type, jamais sur une durée existante ; la médiane du type d'abord, sinon des points.
 */
class InboxFillTest {
    private val t0 = Instant.parse("2026-09-29T07:00:00Z")
    private val estimates = Estimates(minutesByPoints = mapOf(3 to 60), minutesByType = mapOf("Writing" to 45))

    private fun task(priority: Int? = 1, points: Int? = 3, type: String = "Writing", minutes: Int? = null) =
        Task(1, "Draft", priority = priority, fibonacciPoints = points, taskType = type, estimatedMinutes = minutes, createdAt = t0, updatedAt = t0)

    @Test
    fun aFullyQualifiedTaskWithoutDurationTakesItsTypeMedianFirst() {
        assertEquals(Estimates.Fill(45, "Writing"), estimates.inboxFill(task()))
        // Type sans médiane fiable : celle des points.
        assertEquals(Estimates.Fill(60, null), estimates.inboxFill(task(type = "Admin")))
        // Ni l'un ni l'autre : rien.
        assertNull(estimates.inboxFill(task(points = 5, type = "Admin")))
    }

    @Test
    fun nothingIsFilledBeforeTheThreeFieldsAreSetNorOverAnExistingDuration() {
        assertNull(estimates.inboxFill(task(priority = null)))
        assertNull(estimates.inboxFill(task(points = null)))
        assertNull(estimates.inboxFill(task(type = "")))
        assertNull(estimates.inboxFill(task(minutes = 30)))
        // Une durée de 0 compte comme vide, comme partout ailleurs.
        assertEquals(Estimates.Fill(45, "Writing"), estimates.inboxFill(task(minutes = 0)))
    }
}
