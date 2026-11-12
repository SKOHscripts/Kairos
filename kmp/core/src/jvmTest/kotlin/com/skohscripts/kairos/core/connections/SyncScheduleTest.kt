package com.skohscripts.kairos.core.connections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SyncScheduleTest {
    private val now = Instant.parse("2026-11-11T12:00:00Z")
    private val connection = CalendarConnection(sourceId = 1, kind = ConnectionKind.URL)

    private fun due(value: CalendarConnection = connection, force: Boolean = false, hasSecret: Boolean = true) =
        SyncSchedule.due(value, now, force, hasSecret)

    @Test
    fun pausedAndMissingSecretConnectionsNeverRunEvenWhenForced() {
        for (force in listOf(false, true)) {
            assertFalse(due(connection.copy(syncEnabled = false), force))
            assertFalse(due(force = force, hasSecret = false))
        }
    }

    @Test
    fun forceBypassesTheIntervalAndRetryDelay() {
        assertTrue(due(connection.copy(interval = SyncInterval.MANUAL, nextAttemptAt = now + 24.hours, lastAttemptAt = now), force = true))
    }

    @Test
    fun manualConnectionsNeverRunAutomatically() {
        assertFalse(due(connection.copy(interval = SyncInterval.MANUAL)))
        assertFalse(due(connection.copy(interval = SyncInterval.MANUAL, lastAttemptAt = now - 48.hours)))
    }

    @Test
    fun nextAttemptAtBlocksUntilItsExactBoundary() {
        assertFalse(due(connection.copy(nextAttemptAt = now + 1.seconds)))
        assertTrue(due(connection.copy(nextAttemptAt = now)))
        assertTrue(due(connection.copy(nextAttemptAt = now - 1.seconds)))
    }

    @Test
    fun onOpenRunsInitiallyAndAfterFifteenMinutesSinceTheLastAttempt() {
        assertTrue(due())
        assertFalse(due(connection.copy(lastAttemptAt = now - 15.minutes + 1.seconds)))
        assertTrue(due(connection.copy(lastAttemptAt = now - 15.minutes)))
        assertFalse(due(connection.copy(lastAttemptAt = now + 1.minutes)))
        assertFalse(due(connection.copy(lastAttemptAt = now, lastSyncAt = now - 24.hours)))
    }

    @Test
    fun regularIntervalsUseTheirExactMinuteBoundaries() {
        for (interval in listOf(SyncInterval.HOURLY, SyncInterval.SIX_HOURS, SyncInterval.DAILY)) {
            val value = connection.copy(interval = interval)
            val duration = requireNotNull(interval.minutes).minutes
            assertTrue(due(value), interval.name)
            assertFalse(due(value.copy(lastAttemptAt = now - duration + 1.seconds)), interval.name)
            assertTrue(due(value.copy(lastAttemptAt = now - duration)), interval.name)
            assertFalse(due(value.copy(lastAttemptAt = now - duration, nextAttemptAt = now + 1.seconds)), interval.name)
        }
    }

    @Test
    fun failuresIncrementAndTheMinimumDelayIsFifteenMinutesForEveryError() {
        for (error in ConnectionError.entries) {
            val failure = SyncSchedule.afterFailure(connection, error, null, now)
            assertEquals(SyncSchedule.Failure(1, now + 15.minutes), failure, error.name)
        }
        for (retryAfter in listOf(-1L, 0L, 899L, 900L)) {
            assertEquals(now + 15.minutes, SyncSchedule.afterFailure(connection, ConnectionError.UNAVAILABLE, retryAfter, now).nextAttemptAt)
        }
    }

    @Test
    fun retryAfterIsHonoredAndCappedAtTwentyFourHours() {
        assertEquals(now + 901.seconds, SyncSchedule.afterFailure(connection, ConnectionError.UNAVAILABLE, 901, now).nextAttemptAt)
        assertEquals(now + 2.hours, SyncSchedule.afterFailure(connection, ConnectionError.UNAVAILABLE, 7_200, now).nextAttemptAt)
        for (retryAfter in listOf(86_400L, 86_401L, Long.MAX_VALUE)) {
            assertEquals(now + 24.hours, SyncSchedule.afterFailure(connection, ConnectionError.UNAVAILABLE, retryAfter, now).nextAttemptAt)
        }
    }

    @Test
    fun theFifthAndLaterConsecutiveFailuresHaveASixHourMinimum() {
        assertEquals(SyncSchedule.Failure(4, now + 15.minutes), SyncSchedule.afterFailure(connection.copy(failures = 3), ConnectionError.OFFLINE, null, now))
        for (previous in listOf(4, 5, 20)) {
            val value = connection.copy(failures = previous)
            assertEquals(SyncSchedule.Failure(previous + 1, now + 6.hours), SyncSchedule.afterFailure(value, ConnectionError.OFFLINE, null, now))
            assertEquals(now + 6.hours, SyncSchedule.afterFailure(value, ConnectionError.UNAVAILABLE, 3_600, now).nextAttemptAt)
            assertEquals(now + 12.hours, SyncSchedule.afterFailure(value, ConnectionError.UNAVAILABLE, 43_200, now).nextAttemptAt)
            assertEquals(now + 24.hours, SyncSchedule.afterFailure(value, ConnectionError.UNAVAILABLE, Long.MAX_VALUE, now).nextAttemptAt)
        }
    }
}
