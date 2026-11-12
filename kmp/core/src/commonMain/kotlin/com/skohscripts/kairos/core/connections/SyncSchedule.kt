package com.skohscripts.kairos.core.connections

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

object SyncSchedule {
    data class Failure(val failures: Int, val nextAttemptAt: Instant)

    fun due(connection: CalendarConnection, now: Instant, force: Boolean, hasSecret: Boolean): Boolean {
        if (!connection.syncEnabled || !hasSecret) return false
        if (force) return true
        if (connection.interval == SyncInterval.MANUAL) return false
        if (connection.nextAttemptAt?.let { now < it } == true) return false
        val lastAttempt = connection.lastAttemptAt ?: return true
        val interval = connection.interval.minutes?.minutes ?: 15.minutes
        return now - lastAttempt >= interval
    }

    @Suppress("UNUSED_PARAMETER")
    fun afterFailure(connection: CalendarConnection, error: ConnectionError, retryAfterSeconds: Long?, now: Instant): Failure {
        val failures = connection.failures + 1
        // Délai = max(15 min, 6 h dès le 5e échec, Retry-After borné à 0..24 h), quelle que soit l’erreur.
        val minimum = if (failures >= 5) 6.hours else 15.minutes
        val retryAfter = (retryAfterSeconds ?: 0L).coerceIn(0L, 86_400L).seconds
        return Failure(failures, now + maxOf(minimum, retryAfter))
    }
}
