package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.core.AppVersion
import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.core.updates.UpdateCheck
import com.skohscripts.kairos.ui.app.UpdateService
import com.skohscripts.kairos.ui.app.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Vérification des mises à jour du bureau (docs/spec/mises-a-jour.md) :
 * un GET sur l'API GitHub, rien d'autre ne quitte le poste. L'état (dernière
 * vérification, dernière version vue, version masquée) vit dans
 * `updates.json` du dossier de données, pour tenir la cadence de 6 heures
 * d'un lancement à l'autre.
 */
class DesktopUpdates(
    private val file: File,
    private val clock: Clock = Clock.System,
    private val installed: AppVersion = AppVersion.current,
    private val fetch: (String) -> Pair<Int, String> = ::httpGet,
) : UpdateService {
    @Serializable
    private data class Saved(
        val lastCheck: String? = null,
        val failed: Boolean = false,
        val latest: String? = null,
        val url: String? = null,
        val dismissed: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private var saved: Saved = runCatching { json.decodeFromString(Saved.serializer(), file.readText()) }.getOrDefault(Saved())

    override val status = MutableStateFlow(statusOf(saved))
    override val dismissed: StateFlow<String?> = MutableStateFlow(saved.dismissed)

    override suspend fun check(force: Boolean) = mutex.withLock {
        val now = clock.now()
        if (!force && !UpdateCheck.due(saved.lastCheck?.let(Instant::parse), now)) return@withLock
        status.value = UpdateStatus.Checking
        val result = withContext(Dispatchers.IO) { runCatching { fetch(UpdateCheck.LATEST_URL) } }
        val (code, body) = result.getOrNull() ?: (-1 to "")
        saved = when (code) {
            200 -> {
                val release = UpdateCheck.parseLatest(body)?.takeIf { UpdateCheck.isNewer(it, installed) }
                saved.copy(lastCheck = now.toString(), failed = false, latest = release?.version?.toString(), url = release?.url)
            }
            // Aucune version publiée (que des préversions) : rien de plus récent.
            404 -> saved.copy(lastCheck = now.toString(), failed = false, latest = null, url = null)
            else -> saved.copy(lastCheck = now.toString(), failed = true)
        }
        status.value = statusOf(saved)
        write()
    }

    override fun dismiss(version: String) {
        saved = saved.copy(dismissed = version)
        (dismissed as MutableStateFlow).value = version
        write()
    }

    private fun statusOf(s: Saved): UpdateStatus {
        val at = s.lastCheck?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return UpdateStatus.Never
        val latest = s.latest?.let(AppVersion::parse)
        return when {
            s.failed -> UpdateStatus.Failed(at)
            latest != null && s.url != null && latest > installed -> UpdateStatus.Available(latest.toString(), s.url, at)
            else -> UpdateStatus.UpToDate(at)
        }
    }

    private fun write() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(Saved.serializer(), saved))
        }
    }

    companion object {
        private val client: HttpClient by lazy {
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build()
        }

        /** GET de [url] : code et corps. Le proxy du système est celui de la JVM (`-Dhttps.proxyHost`…). */
        fun httpGet(url: String): Pair<Int, String> {
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Kairos/${KairosBuild.VERSION_NAME}")
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            return response.statusCode() to response.body()
        }
    }
}
