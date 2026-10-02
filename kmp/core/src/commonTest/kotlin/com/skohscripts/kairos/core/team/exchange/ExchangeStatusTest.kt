package com.skohscripts.kairos.core.team.exchange

import com.skohscripts.kairos.core.team.TeamEvent
import com.skohscripts.kairos.core.team.TeamEventKind
import com.skohscripts.kairos.core.team.TeamEventSource
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.later
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.member
import com.skohscripts.kairos.core.team.exchange.ExchangeFixtures.stamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** État d'échange par membre (docs/spec/equipe-echanges.md § Onglet « Échanges »). */
class ExchangeStatusTest {
    private fun event(id: Long, memberId: Long?, at: Instant, kind: TeamEventKind = TeamEventKind.SENT) =
        TeamEvent(id, taskId = 100 + id, taskTitle = "T$id", memberId = memberId, kind = kind, source = TeamEventSource.MANUAL, at = at)

    @Test
    fun aMemberWithoutAnyExchangeIsNotAwaitingAnything() {
        val status = ExchangeStatus.of(listOf(member(1)), emptyList()).single()
        assertNull(status.lastPackAt)
        assertNull(status.lastReportAt)
        assertFalse(status.awaitingReport)
    }

    @Test
    fun aPackWithoutReportIsAwaitingOne() {
        val status = ExchangeStatus.of(listOf(member(1)), listOf(event(1, 1, stamp), event(2, 1, later))).single()
        assertEquals(later, status.lastPackAt) // le plus récent des envois
        assertTrue(status.awaitingReport)
    }

    @Test
    fun aReportAfterThePackSettlesIt() {
        val reported = member(1, lastReportAt = later)
        assertFalse(ExchangeStatus.of(listOf(reported), listOf(event(1, 1, stamp))).single().awaitingReport)
    }

    @Test
    fun aNewerPackReopensTheWait() {
        val reported = member(1, lastReportAt = stamp)
        val status = ExchangeStatus.of(listOf(reported), listOf(event(1, 1, later))).single()
        assertEquals(stamp, status.lastReportAt)
        assertTrue(status.awaitingReport)
    }

    @Test
    fun onlyPackEventsOfThatMemberCount() {
        val members = listOf(member(1), member(2))
        val events = listOf(event(1, 2, later), event(2, 1, later, TeamEventKind.DONE), event(3, null, later))
        val (first, second) = ExchangeStatus.of(members, events)
        assertNull(first.lastPackAt)
        assertEquals(later, second.lastPackAt)
    }

    @Test
    fun selfAndFormerMembersAreLeftOut() {
        val members = listOf(member(1).copy(isSelf = true), member(2, archived = true), member(3))
        assertEquals(listOf(3L), ExchangeStatus.of(members, emptyList()).map { it.member.id })
    }
}
