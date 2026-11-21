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
    private val caldav = CalDavClient(gate)
    private val mutations = Mutex()
    val sync = ConnectionSync(repository, gate, secrets, now, caldav = caldav)
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
                        val plan = withContext(Dispatchers.Default) { CalendarImport.plan(result.text, today, zone) }
                        // L'adresse gardée (lien demandé, ou finale après des redirections permanentes seulement) fixe l'hôte de
                        // la connexion et son consentement HTTP ; une redirection acceptée vers un autre hôte est rejouée.
                        val host = HttpPolicy.host(result.storedUrl).orEmpty()
                        val name = plan.name ?: host
                        val existing = ImportedCalendars.named(repository.snapshot.value, name)
                        IcsPreviewResult.Ready(name, plan, host,
                            existing != null && connections.value.none { it.sourceId == existing.id },
                            result.storedUrl.startsWith("http://"), acceptedRedirectHost, result.storedUrl, result.response.headers["etag"])
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
        mutations.withLock { repository.updateConnection(sourceId) { it.copy(syncEnabled = enabled) } }
    }

    override suspend fun setInterval(sourceId: Long, interval: SyncInterval) {
        mutations.withLock { repository.updateConnection(sourceId) { it.copy(interval = interval) } }
    }

    override suspend fun replaceIcsLink(sourceId: Long, link: String, confirmedInsecure: Boolean, acceptedRedirectHost: String?): IcsPreviewResult {
        val preview = previewIcs(link, confirmedInsecure, acceptedRedirectHost)
        if (preview !is IcsPreviewResult.Ready) return preview
        mutations.withLock {
            if (connections.value.none { it.sourceId == sourceId }) return preview
            val key = "url:$sourceId"
            val old = secrets.get(key)
            try {
                secrets.put(key, preview.url)
                repository.updateConnection(sourceId) {
                    it.copy(host = preview.host, insecureConfirmed = preview.insecure, redirectHost = preview.redirectHost,
                        etag = null, plannedOn = null, lastError = null, failures = 0, nextAttemptAt = null)
                }
            } catch (e: Throwable) {
                if (old == null) secrets.delete(key) else secrets.put(key, old)
                throw e
            }
        }
        syncNow(sourceId)
        return preview
    }

    override suspend fun discoverCalDav(account: CalDavAccount): CalDavClient.Discovery = try {
        withTimeout(120_000) { caldav.discover(account) }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        CalDavClient.Discovery.Failed(com.skohscripts.kairos.core.connections.ConnectionError.OFFLINE, HttpPolicy.host(account.server))
    }

    override suspend fun previewCalDav(account: CalDavAccount, host: String, calendars: List<CalDavCalendar>): List<CalDavPreview> {
        val zone = TimeZone.currentSystemDefault()
        val today = now().toLocalDateTime(zone).date
        return calendars.map { calendar ->
            try {
                withTimeout(60_000) {
                    when (val read = caldav.preview(account, host, calendar, today)) {
                        is CalDavClient.Read.Success -> CalDavPreview(calendar, withContext(Dispatchers.Default) { CalendarImport.plan(read.text, today, zone) }, null)
                        is CalDavClient.Read.Failed -> CalDavPreview(calendar, null, read.error, read.detail)
                        CalDavClient.Read.GateClosed -> CalDavPreview(calendar, null, com.skohscripts.kairos.core.connections.ConnectionError.UNSUPPORTED)
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                CalDavPreview(calendar, null, com.skohscripts.kairos.core.connections.ConnectionError.OFFLINE)
            } catch (e: IllegalArgumentException) {
                CalDavPreview(calendar, null, com.skohscripts.kairos.core.connections.ConnectionError.NOT_CALENDAR)
            }
        }
    }

    override suspend fun addCalDav(account: CalDavAccount, host: String, previews: List<CalDavPreview>, hideTitles: Boolean): List<Long> = mutations.withLock {
        val ready = previews.filter { it.plan != null }
        if (ready.isEmpty()) return@withLock emptyList()
        val instant = now()
        val today = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
        backups.save("avant-import-${instant.toEpochMilliseconds()}.json", ExportCodec.encode(repository.snapshot.value, KairosBuild.VERSION_NAME, instant))
        // Un compte par ajout, partagé par les calendriers cochés ensemble ; jamais réutilisé d'un ajout à l'autre.
        val key = "caldav-" + kotlin.random.Random.nextBytes(8).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        secrets.put("account:$key", account.toJson())
        val added = mutableListOf<Long>()
        try {
            for (preview in ready) {
                var secretId: Long? = null
                try {
                    repository.connectCalendar(preview.calendar.name, hideTitles, preview.plan!!, today,
                        CalendarConnection(0, ConnectionKind.CALDAV, host = host, account = key, lastAttemptAt = instant, lastSyncAt = instant),
                        instant, beforeCommit = { id -> secretId = id; secrets.put("remote:$id", preview.calendar.url) })?.let { added += it }
                } catch (e: Throwable) {
                    secretId?.let { secrets.delete("remote:$it") }
                    throw e
                }
            }
        } finally {
            // Aucun calendrier ajouté : le compte ne sert à rien et ne reste pas dans le magasin.
            if (added.isEmpty()) secrets.delete("account:$key")
        }
        added
    }

    override suspend fun changeCalDavPassword(sourceId: Long, password: String) {
        val accountKey = mutations.withLock {
            val key = connections.value.firstOrNull { it.sourceId == sourceId && it.kind == ConnectionKind.CALDAV }?.account ?: return
            val account = CalDavAccount.fromJson(secrets.get("account:$key")) ?: return
            secrets.put("account:$key", account.copy(password = password).toJson())
            for (connection in connections.value.filter { it.account == key }) {
                repository.updateConnection(connection.sourceId) { it.copy(lastError = null, failures = 0, nextAttemptAt = null) }
            }
            key
        }
        for (connection in connections.value.filter { it.account == accountKey }) syncNow(connection.sourceId)
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

    /** Sous le verrou des écritures : une purge lancée en arrière-plan ne supprime pas le secret d'un ajout en cours (écrit avant la ligne). */
    suspend fun purgeOrphans() = mutations.withLock {
        val rows = connections.value
        for (key in secrets.keys()) {
            when {
                key.startsWith("url:") || key.startsWith("remote:") -> if (rows.none { it.sourceId == key.substringAfter(':').toLongOrNull() }) secrets.delete(key)
                key.startsWith("account:") -> if (rows.none { it.account == key.substringAfter(':') }) secrets.delete(key)
            }
        }
    }
}
