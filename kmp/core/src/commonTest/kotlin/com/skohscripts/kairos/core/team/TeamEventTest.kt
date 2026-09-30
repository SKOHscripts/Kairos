package com.skohscripts.kairos.core.team

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class TeamEventTest {
    private fun event(id: Long, task: Long, at: String) =
        TeamEvent(id, task, "T$task", null, TeamEventKind.PROGRESS, null, "10", TeamEventSource.MANUAL, Instant.parse(at))

    @Test
    fun kindCodesRoundTripAndAnUnknownCodeIsKept() {
        for (kind in TeamEventKind.entries) assertEquals(kind, TeamEventKind.fromCode(kind.code))
        assertEquals(TeamEventKind.UNKNOWN, TeamEventKind.fromCode("report"))
        assertEquals(TeamEventKind.UNKNOWN, TeamEventKind.fromCode(""))
    }

    @Test
    fun sourceCodesRoundTripAndAnUnknownCodeIsManual() {
        for (source in TeamEventSource.entries) assertEquals(source, TeamEventSource.fromCode(source.code))
        assertEquals(TeamEventSource.MANUAL, TeamEventSource.fromCode("martien"))
    }

    @Test
    fun qualifiedValuesCarryTheirField() {
        assertEquals("priority:1", TeamEvents.qualifiedValue(QualifiedField.PRIORITY, "1"))
        assertEquals("type:", TeamEvents.qualifiedValue(QualifiedField.TYPE, null))
        assertEquals(QualifiedField.DEADLINE to "2026-10-03", TeamEvents.parseQualified("deadline:2026-10-03"))
        assertEquals(QualifiedField.TYPE to "a:b", TeamEvents.parseQualified("type:a:b"))
        assertEquals(QualifiedField.POINTS to "", TeamEvents.parseQualified("points:"))
        assertNull(TeamEvents.parseQualified("couleur:rouge"))
        assertNull(TeamEvents.parseQualified("sans deux points"))
        assertNull(TeamEvents.parseQualified(null))
    }

    @Test
    fun historyIsNewestFirstAndPerTask() {
        val events = listOf(
            event(1, 5, "2026-09-28T08:00:00Z"), event(2, 6, "2026-09-29T08:00:00Z"),
            event(3, 5, "2026-09-30T08:00:00Z"), event(4, 5, "2026-09-30T08:00:00Z"),
        )
        assertEquals(listOf(4L, 3L, 1L), TeamEvents.historyOf(events, 5).map { it.id })
    }
}
