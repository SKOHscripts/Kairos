package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.core.calendar.CalendarImport
import com.skohscripts.kairos.core.calendar.ImportedCalendars
import com.skohscripts.kairos.core.connections.CalendarConnection
import com.skohscripts.kairos.core.connections.ConnectionKind
import com.skohscripts.kairos.core.connections.HttpPolicy
import com.skohscripts.kairos.core.connections.SyncInterval
import com.skohscripts.kairos.data.ExportCodec
import com.skohscripts.kairos.data.KairosRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class DefaultOnlineConnections(
    private val repository: KairosRepository,
    private val backups: BackupStore,
    http: HttpFetcher,
    private val secrets: SecretStore,
    scope: CoroutineScope,
    private val now: () -> Instant = { Clock.System.now() },
) : OnlineConnections {
    private val gate = OnlineGate(http, repository)
    private val mutations = Mutex()
    val sync = ConnectionSync(repository, gate, secrets, now)
    override val connections = repository.connections
    override val secretLevel get() = secrets.level

    override suspend fun previewIcs(link: String, confirmedInsecure: Boolean, acceptedRedirectHost: String?): IcsPreviewResult {
        val zone = TimeZone.currentSystemDefault()
        val today = now().toLocalDateTime(zone).date
        return try {
            withTimeout(60_000) {
                when (val result = gate.preview(link, confirmedInsecure, acceptedRedirectHost, today)) {
                    OnlineGate.Result.GateClosed -> IcsPreviewResult.Failed(com.skohscripts.kairos.core.connections.ConnectionError.UNSUPPORTED, HttpPolicy.host(link))
                    is OnlineGate.Result.Failed -> IcsPreviewResult.Failed(result.error, HttpPolicy.host(link))
                    is OnlineGate.Result.Confirm -> IcsPreviewResult.NeedsConfirmation(result.kind, result.host)
                    is OnlineGate.Result.Success -> {
                        val plan = withContext(Dispatchers.Default) { CalendarImport.plan(result.response.body.decodeToString(), today, zone) }
                        val host = HttpPolicy.host(result.url).orEmpty()
                        val name = plan.name ?: host
                        val existing = ImportedCalendars.named(repository.snapshot.value, name)
                        IcsPreviewResult.Ready(name, plan, host,
                            existing != null && connections.value.none { it.sourceId == existing.id },
                            result.url.startsWith("http://"), acceptedRedirectHost, result.url, result.response.headers["etag"])
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            IcsPreviewResult.Failed(com.skohscripts.kairos.core.connections.ConnectionError.OFFLINE, HttpPolicy.host(link))
        } catch (e: IllegalArgumentException) {
            IcsPreviewResult.Failed(com.skohscripts.kairos.core.connections.ConnectionError.NOT_CALENDAR, HttpPolicy.host(link))
        }
    }

    override suspend fun addIcs(preview: IcsPreviewResult.Ready, name: String, hideTitles: Boolean): Long? = mutations.withLock {
        if (name.isBlank()) return@withLock null
        val instant = now()
        backups.save("avant-import-${instant.toEpochMilliseconds()}.json", ExportCodec.encode(repository.snapshot.value, KairosBuild.VERSION_NAME, instant))
        var secretId: Long? = null
        try {
            repository.connectCalendar(name, hideTitles, preview.plan, instant.toLocalDateTime(TimeZone.currentSystemDefault()).date,
                CalendarConnection(0, ConnectionKind.URL, host = preview.host, lastAttemptAt = instant, lastSyncAt = instant,
                    etag = preview.etag, plannedOn = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString(),
                    insecureConfirmed = preview.insecure, redirectHost = preview.redirectHost), instant,
                beforeCommit = { id -> secretId = id; secrets.put("url:$id", preview.url) })
        } catch (e: Throwable) {
            secretId?.let { secrets.delete("url:$it") }
            throw e
        }
    }

    override suspend fun syncNow(sourceId: Long) = sync.run(sourceId, force = true)

    override suspend fun setSyncEnabled(sourceId: Long, enabled: Boolean) {
        mutations.withLock { connections.value.firstOrNull { it.sourceId == sourceId }?.let { repository.updateConnection(it.copy(syncEnabled = enabled)) } }
    }

    override suspend fun setInterval(sourceId: Long, interval: SyncInterval) {
        mutations.withLock { connections.value.firstOrNull { it.sourceId == sourceId }?.let { repository.updateConnection(it.copy(interval = interval)) } }
    }

    override suspend fun replaceIcsLink(sourceId: Long, link: String, confirmedInsecure: Boolean, acceptedRedirectHost: String?): IcsPreviewResult {
        val preview = previewIcs(link, confirmedInsecure, acceptedRedirectHost)
        if (preview !is IcsPreviewResult.Ready) return preview
        mutations.withLock {
            val connection = connections.value.firstOrNull { it.sourceId == sourceId } ?: return preview
            val key = "url:$sourceId"
            val old = secrets.get(key)
            try {
                secrets.put(key, preview.url)
                repository.updateConnection(connection.copy(host = preview.host, insecureConfirmed = preview.insecure,
                    redirectHost = preview.redirectHost, etag = null, plannedOn = null, lastError = null,
                    failures = 0, nextAttemptAt = null))
            } catch (e: Throwable) {
                if (old == null) secrets.delete(key) else secrets.put(key, old)
                throw e
            }
        }
        syncNow(sourceId)
        return preview
    }

    override suspend fun disconnect(sourceId: Long) {
        mutations.withLock { eraseSecrets(sourceId); repository.disconnectCalendar(sourceId) }
    }

    override suspend fun remove(sourceId: Long) {
        mutations.withLock { eraseSecrets(sourceId); repository.removeCalendar(sourceId) }
    }

    private fun eraseSecrets(sourceId: Long) {
        val connection = connections.value.firstOrNull { it.sourceId == sourceId }
        secrets.delete("url:$sourceId")
        secrets.delete("remote:$sourceId")
        connection?.account?.takeIf { account -> connections.value.none { it.sourceId != sourceId && it.account == account } }
            ?.let { secrets.delete("account:$it") }
    }

    fun purgeOrphans() {
        val rows = connections.value
        for (key in secrets.keys()) {
            when {
                key.startsWith("url:") || key.startsWith("remote:") -> if (rows.none { it.sourceId == key.substringAfter(':').toLongOrNull() }) secrets.delete(key)
                key.startsWith("account:") -> if (rows.none { it.account == key.substringAfter(':') }) secrets.delete(key)
            }
        }
    }
}
