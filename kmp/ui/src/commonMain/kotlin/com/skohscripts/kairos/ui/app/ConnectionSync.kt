package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.calendar.CalendarImport
import com.skohscripts.kairos.core.connections.CalendarConnection
import com.skohscripts.kairos.core.connections.ConnectionError
import com.skohscripts.kairos.core.connections.ConnectionKind
import com.skohscripts.kairos.core.connections.SyncSchedule
import com.skohscripts.kairos.data.KairosRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class ConnectionSync(
    private val repository: KairosRepository,
    private val gate: OnlineGate,
    private val secrets: SecretStore,
    private val now: () -> Instant = { Clock.System.now() },
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    private val mutex = Mutex()

    suspend fun run(sourceId: Long, force: Boolean): Unit = mutex.withLock {
        val connection = repository.connections.value.firstOrNull { it.sourceId == sourceId } ?: return@withLock
        val instant = now()
        val url = secrets.get("url:$sourceId")
        if (!connection.syncEnabled) return@withLock
        if (url == null) {
            if (connection.lastError != ConnectionError.NO_SECRET) failed(connection, ConnectionError.NO_SECRET, null, instant)
            return@withLock
        }
        if (!SyncSchedule.due(connection, instant, force, true)) return@withLock
        if (connection.kind != ConnectionKind.URL) {
            failed(connection, ConnectionError.UNSUPPORTED, null, instant)
            return@withLock
        }
        try {
            withTimeout(60_000) {
                val today = instant.toLocalDateTime(zone).date
                when (val result = gate.fetch(sourceId, url, today)) {
                    OnlineGate.Result.GateClosed -> Unit
                    is OnlineGate.Result.Failed -> failed(connection, result.error, result.retryAfterSeconds, instant)
                    is OnlineGate.Result.Confirm -> failed(connection, ConnectionError.REDIRECT_HOST, null, instant)
                    is OnlineGate.Result.Success -> {
                        if (result.response.status == 304) {
                            repository.touchSync(sourceId, instant)
                        } else {
                            val plan = withContext(Dispatchers.Default) {
                                CalendarImport.plan(result.response.body.decodeToString(), today, zone)
                            }
                            repository.syncCalendar(sourceId, plan, today, result.response.headers["etag"], instant)
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            failed(connection, ConnectionError.OFFLINE, null, instant)
        } catch (e: IllegalArgumentException) {
            failed(connection, ConnectionError.NOT_CALENDAR, null, instant)
        }
        Unit
    }

    suspend fun runDue(now: Instant) {
        for (connection in repository.connections.value) {
            val hasSecret = secrets.get("url:${connection.sourceId}") != null
            if ((connection.syncEnabled && !hasSecret && connection.lastError != ConnectionError.NO_SECRET) ||
                SyncSchedule.due(connection, now, false, hasSecret)) {
                run(connection.sourceId, force = false)
            }
        }
    }

    private suspend fun failed(connection: CalendarConnection, error: ConnectionError, retryAfter: Long?, instant: Instant) {
        val failure = SyncSchedule.afterFailure(connection, error, retryAfter, instant)
        repository.recordSyncFailure(connection.sourceId, error, instant, failure.failures, failure.nextAttemptAt)
    }
}
