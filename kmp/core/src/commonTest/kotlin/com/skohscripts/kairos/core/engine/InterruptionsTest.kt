package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.Interruption
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.team.Workspaces
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Découpe de la séance par un dérangement (docs/spec/derangements.md § Découpe de la séance). */
class InterruptionsTest {
    private val now = Instant.parse("2026-11-24T10:00:00Z")

    private fun session(id: Long, startedAgo: Int, open: Boolean = true) =
        WorkSession(id, 7, now - startedAgo.minutes, if (open) null else now - 1.minutes, createdAt = now - startedAgo.minutes)

    @Test
    fun withoutATimerNothingChanges() {
        val change = Interruptions.report(listOf(session(1, 120, open = false)), now, 15)
        assertNull(change.running)
        assertNull(change.resumeAt)
    }

    @Test
    fun theOpenSessionStopsWhenTheInterruptionBeganAndResumesNow() {
        val change = Interruptions.report(listOf(session(1, 60)), now, 15)
        assertEquals(1L, change.running?.id)
        assertEquals(now - 15.minutes, change.cutAt)
        assertEquals(now, change.resumeAt)
    }

    @Test
    fun anInterruptionLongerThanTheSessionRemovesIt() {
        val change = Interruptions.report(listOf(session(1, 10)), now, 15)
        assertEquals(1L, change.before?.id)
        assertNull(change.cutAt)
        assertEquals(now, change.resumeAt)
    }

    @Test
    fun interruptionsAreNeverTeamData() {
        // Données personnelles : une base solo qui en a reste solo (export version 1, aucun espace Équipe ouvert).
        val snapshot = KairosSnapshot(interruptions = listOf(Interruption(1, now, 5, createdAt = now)))
        assertFalse(Workspaces.hasTeamData(snapshot))
        assertEquals(snapshot.interruptions, Workspaces.personalView(snapshot).interruptions)
    }
}
