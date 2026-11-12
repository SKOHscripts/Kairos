package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.WorkSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Corriger le temps passé par son total (docs/spec/fiche-tache.md § Temps passé). */
class TimeCorrectionTest {
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")
    private val now = Instant.parse("2026-11-11T18:00:00Z")
    private fun at(text: String) = Instant.parse("2026-11-${text}Z")
    private fun s(id: Long, from: String, to: String?) = WorkSession(id, 1, at(from), to?.let(::at), t0)

    private fun applied(sessions: List<WorkSession>, plan: TimeCorrection.Plan) =
        TimeCorrection.total(sessions.map { it.copy(endedAt = plan.ends[it.id] ?: it.endedAt) }, plan.manual, now)

    // Lundi 1 h, mardi un chrono oublié de 6 h.
    private val forgotten = listOf(s(1, "09T09:00:00", "09T10:00:00"), s(2, "10T09:00:00", "10T15:00:00"))

    @Test
    fun raisingTheTotalAddsToTheTimeEnteredByHand() {
        val plan = TimeCorrection.plan(forgotten, manual = 30, target = 8 * 60, now = now, trimmable = true)
        assertEquals(60, plan.manual)
        assertEquals(emptyMap(), plan.ends)
        assertEquals(8 * 60, applied(forgotten, plan))
    }

    @Test
    fun loweringItFirstDropsTheManualTimeThenTrimsTheLatestSessions() {
        // 7 h chronométrées + 30 min saisies → 6 h 45 : le temps saisi disparaît, puis 15 min sur la session de mardi.
        val plan = TimeCorrection.plan(forgotten, manual = 30, target = 6 * 60 + 45, now = now, trimmable = true)
        assertEquals(null, plan.manual)
        assertEquals(mapOf(2L to at("10T14:45:00")), plan.ends)
        // Le chrono oublié ramené à 1 h : la session de mardi seule bouge.
        val forgot = TimeCorrection.plan(forgotten, manual = null, target = 2 * 60, now = now, trimmable = true)
        assertEquals(mapOf(2L to at("10T10:00:00")), forgot.ends)
        // Sous le total de la plus récente : elle est vidée, puis la précédente raccourcie.
        val deep = TimeCorrection.plan(forgotten, manual = null, target = 30, now = now, trimmable = true)
        assertEquals(mapOf(2L to at("10T09:00:00"), 1L to at("09T09:30:00")), deep.ends)
        assertEquals(30, applied(forgotten, deep))
    }

    @Test
    fun overlappingSessionsReachTheExactTarget() {
        // Deux sessions qui se chevauchent d'une heure : 3 h au total (union), pas 4.
        val overlap = listOf(s(1, "10T09:00:00", "10T11:00:00"), s(2, "10T10:00:00", "10T12:00:00"))
        assertEquals(180, TimeCorrection.total(overlap, null, now))
        for (target in listOf(150, 90, 45, 0)) {
            val plan = TimeCorrection.plan(overlap, null, target, now, trimmable = true)
            assertEquals(target, applied(overlap, plan), "cible $target")
        }
    }

    @Test
    fun aRunningTimerAndTeamTasksSetTheFloor() {
        val running = forgotten + s(3, "11T17:20:00", null) // 40 min en cours
        val plan = TimeCorrection.plan(running, manual = null, target = 10, now = now, trimmable = true)
        assertEquals(40, plan.floor)
        assertEquals(40, applied(running, plan))
        assertTrue(3L !in plan.ends)
        // Tâche d'équipe ou reçue : rien ne raccourcit, le plancher est le chronométré.
        val team = TimeCorrection.plan(forgotten, manual = 20, target = 60, now = now, trimmable = false)
        assertEquals(7 * 60, team.floor)
        assertEquals(TimeCorrection.Plan(null, emptyMap(), 7 * 60), team)
        // Total inchangé : le même temps saisi, aucune fin.
        assertEquals(TimeCorrection.Plan(30, emptyMap(), 0), TimeCorrection.plan(forgotten, 30, 7 * 60 + 30, now, trimmable = true))
    }
}
