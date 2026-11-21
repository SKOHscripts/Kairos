package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.calendar.CalendarImport
import com.skohscripts.kairos.core.connections.CalendarConnection
import com.skohscripts.kairos.core.connections.ConnectionError
import com.skohscripts.kairos.core.connections.ConnectionKind
import com.skohscripts.kairos.core.connections.SyncSchedule
import com.skohscripts.kairos.data.KairosRepository
import kotlinx.coroutines.CancellationException
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
    private val caldav: CalDavClient = CalDavClient(gate),
) {
    private val mutex = Mutex()

    /**
     * Les secrets dont la connexion a besoin sont sur cet appareil : `url:<id>` pour un lien ICS ; `remote:<id>` et un compte lisible
     * `account:<compte>` pour CalDAV (docs/spec/connexions-en-ligne.md § CalDAV).
     */
    private fun hasSecrets(connection: CalendarConnection): Boolean = when (connection.kind) {
        ConnectionKind.CALDAV -> secrets.get("remote:${connection.sourceId}") != null && account(connection) != null
        else -> secrets.get("url:${connection.sourceId}") != null
    }

    private fun account(connection: CalendarConnection): CalDavAccount? =
        connection.account?.let { CalDavAccount.fromJson(secrets.get("account:$it")) }

    suspend fun run(sourceId: Long, force: Boolean): Unit = mutex.withLock {
        val connection = repository.connections.value.firstOrNull { it.sourceId == sourceId } ?: return@withLock
        val instant = now()
        if (!connection.syncEnabled) return@withLock
        if (!hasSecrets(connection)) {
            if (connection.lastError != ConnectionError.NO_SECRET) failed(connection, ConnectionError.NO_SECRET, null, instant)
            return@withLock
        }
        if (!SyncSchedule.due(connection, instant, force, true)) return@withLock
        if (connection.kind != ConnectionKind.URL && connection.kind != ConnectionKind.CALDAV) {
            failed(connection, ConnectionError.UNSUPPORTED, null, instant)
            return@withLock
        }
        try {
            withTimeout(60_000) {
                val today = instant.toLocalDateTime(zone).date
                if (connection.kind == ConnectionKind.CALDAV) {
                    val url = secrets.get("remote:$sourceId") ?: return@withTimeout
                    val account = account(connection) ?: return@withTimeout
                    when (val read = caldav.read(sourceId, account, url, today)) {
                        CalDavClient.Read.GateClosed -> Unit
                        is CalDavClient.Read.Failed -> failed(connection, read.error, read.retryAfterSeconds, instant)
                        is CalDavClient.Read.Success -> {
                            val plan = withContext(Dispatchers.Default) { CalendarImport.plan(read.text, today, zone) }
                            repository.syncCalendar(sourceId, plan, today, null, instant)
                        }
                    }
                    return@withTimeout
                }
                val url = secrets.get("url:$sourceId") ?: return@withTimeout
                when (val result = gate.fetch(sourceId, url, today)) {
                    OnlineGate.Result.GateClosed -> Unit
                    is OnlineGate.Result.Failed -> failed(connection, result.error, result.retryAfterSeconds, instant)
                    is OnlineGate.Result.Confirm -> failed(connection, ConnectionError.REDIRECT_HOST, null, instant)
                    is OnlineGate.Result.Success -> {
                        if (result.response.status == 304) {
                            repository.touchSync(sourceId, instant)
                        } else {
                            val plan = withContext(Dispatchers.Default) {
                                CalendarImport.plan(result.text, today, zone)
                            }
                            repository.syncCalendar(sourceId, plan, today, result.response.headers["etag"], instant)
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            failed(connection, ConnectionError.OFFLINE, null, instant)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            failed(connection, ConnectionError.NOT_CALENDAR, null, instant)
        } catch (e: Exception) {
            // Anything else (parser, storage, transport) is a failure of this connection with backoff, never a dead watcher or a crash at each launch.
            failed(connection, ConnectionError.UNAVAILABLE, null, instant)
        }
        Unit
    }

    suspend fun runDue(now: Instant) {
        for (connection in repository.connections.value) {
            val hasSecret = hasSecrets(connection)
            if ((connection.syncEnabled && !hasSecret && connection.lastError != ConnectionError.NO_SECRET) ||
                SyncSchedule.due(connection, now, false, hasSecret)) {
                try {
                    run(connection.sourceId, force = false)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // `run` already recorded what it could; the remaining connections still synchronize.
                }
            }
        }
    }

    private suspend fun failed(connection: CalendarConnection, error: ConnectionError, retryAfter: Long?, instant: Instant) {
        val failure = SyncSchedule.afterFailure(connection, error, retryAfter, instant)
        repository.recordSyncFailure(connection.sourceId, error, instant, failure.failures, failure.nextAttemptAt)
    }
}
