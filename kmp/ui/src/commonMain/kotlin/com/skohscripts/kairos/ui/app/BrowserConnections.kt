package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.calendar.ImportedCalendars
import com.skohscripts.kairos.core.connections.CalendarConnection
import com.skohscripts.kairos.core.connections.ConnectionError
import com.skohscripts.kairos.core.connections.ConnectionKind
import com.skohscripts.kairos.core.connections.SyncInterval
import com.skohscripts.kairos.data.KairosRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * Magasin de secrets du navigateur (docs/spec/connexions-en-ligne.md § Web) : en mémoire, `level = NONE` (tout script de l'origine
 * lit le stockage du navigateur). [version] change à chaque écriture, pour que [BrowserConnections.keep] réécrive le fichier.
 */
class BrowserSecrets : SecretStore {
    private val values = LinkedHashMap<String, String>()
    private val changes = MutableStateFlow(0)
    val version: StateFlow<Int> = changes.asStateFlow()

    override val level = SecretLevel.NONE

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String) {
        if (values.put(key, value) != value) changes.value++
    }

    override fun delete(key: String) {
        if (values.remove(key) != null) changes.value++
    }

    override fun keys(): Set<String> = values.keys.toSet()
}

/**
 * Connexions de la version web, gardées hors de l'instantané (docs/spec/connexions-en-ligne.md § Web) : la base du navigateur est
 * rechargée depuis `kairos.json`, qui ne les contient pas. Le fichier `kairos-connections.json` de l'OPFS garde, pour chaque lien ICS,
 * le nom du calendrier, le lien et l'état de la connexion ; au chargement, chaque entrée se rattache au calendrier du même nom.
 */
object BrowserConnections {
    const val FILE = "kairos-connections.json"

    /** Une entrée lue du fichier : [connection] porte un `sourceId` sans valeur, remplacé au rattachement. */
    data class Entry(val name: String, val url: String, val connection: CalendarConnection)

    private val json = Json { ignoreUnknownKeys = true }

    /** Le fichier : chaque connexion `URL` dont le calendrier et le lien existent, dans l'ordre des connexions. */
    fun encode(repository: KairosRepository, secrets: SecretStore): String = encode(
        repository.connections.value.mapNotNull { c ->
            if (c.kind != ConnectionKind.URL) return@mapNotNull null
            val name = repository.snapshot.value.calendarSources.firstOrNull { it.id == c.sourceId }?.name ?: return@mapNotNull null
            val url = secrets.get("url:${c.sourceId}") ?: return@mapNotNull null
            Entry(name, url, c)
        },
    )

    fun encode(entries: List<Entry>): String = buildJsonObject {
        put("version", 1)
        put("connections", buildJsonArray {
            entries.forEach { (name, url, c) ->
                add(buildJsonObject {
                    put("name", name)
                    put("url", url)
                    put("host", c.host)
                    put("syncEnabled", c.syncEnabled)
                    put("interval", c.interval.name)
                    put("lastAttemptAt", c.lastAttemptAt?.toString())
                    put("lastSyncAt", c.lastSyncAt?.toString())
                    put("lastError", c.lastError?.name)
                    put("failures", c.failures)
                    put("nextAttemptAt", c.nextAttemptAt?.toString())
                    put("plannedOn", c.plannedOn)
                    put("redirectHost", c.redirectHost)
                })
            }
        })
    }.toString()

    /** Un fichier illisible, d'une autre version ou une entrée incomplète ne rend rien, jamais une exception. */
    fun decode(text: String?): List<Entry> {
        val root = text?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return emptyList()
        if ((root["version"] as? JsonPrimitive)?.intOrNull != 1) return emptyList()
        val items = runCatching { root["connections"]!!.jsonArray }.getOrNull() ?: return emptyList()
        return items.mapNotNull { item -> runCatching { entry(item.jsonObject) }.getOrNull() }
    }

    private fun entry(o: JsonObject): Entry? {
        fun text(key: String): String? = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun instant(key: String): Instant? = text(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val name = text("name")?.takeIf { it.isNotBlank() } ?: return null
        val url = text("url")?.takeIf { it.isNotBlank() } ?: return null
        val connection = CalendarConnection(
            sourceId = 0,
            kind = ConnectionKind.URL,
            host = text("host"),
            syncEnabled = (o["syncEnabled"] as? JsonPrimitive)?.booleanOrNull ?: true,
            interval = text("interval")?.let { v -> SyncInterval.entries.firstOrNull { it.name == v } } ?: SyncInterval.ON_OPEN,
            lastAttemptAt = instant("lastAttemptAt"),
            lastSyncAt = instant("lastSyncAt"),
            lastError = text("lastError")?.let { v -> ConnectionError.entries.firstOrNull { it.name == v } },
            failures = (o["failures"] as? JsonPrimitive)?.intOrNull?.coerceAtLeast(0) ?: 0,
            nextAttemptAt = instant("nextAttemptAt"),
            plannedOn = text("plannedOn"),
            redirectHost = text("redirectHost"),
        )
        return Entry(name, url, connection)
    }

    /**
     * Au chargement, après la restauration de l'instantané : chaque entrée se rattache au calendrier du même nom, s'il existe et n'a
     * pas de connexion ; son lien revient dans [secrets]. Rend le nombre de connexions remises.
     */
    suspend fun restore(repository: KairosRepository, secrets: SecretStore, text: String?): Int {
        val snapshot = repository.snapshot.value
        val rows = decode(text).mapNotNull { entry ->
            ImportedCalendars.named(snapshot, entry.name)?.let { source -> entry to entry.connection.copy(sourceId = source.id) }
        }.distinctBy { it.second.sourceId }
        val restored = repository.restoreConnections(rows.map { it.second })
        rows.filter { it.second.sourceId in restored }.forEach { (entry, row) -> secrets.put("url:${row.sourceId}", entry.url) }
        return restored.size
    }

    /**
     * Réécrit le fichier par [write] à chaque changement des connexions, des noms de calendriers ou des secrets, seulement si le texte
     * change. Un échec d'écriture est ignoré : le changement suivant réessaie.
     */
    fun keep(scope: CoroutineScope, repository: KairosRepository, secrets: BrowserSecrets, write: suspend (String) -> Unit): Job =
        scope.launch {
            combine(repository.connections, repository.snapshot, secrets.version) { _, _, _ -> encode(repository, secrets) }
                .distinctUntilChanged()
                .collect { text -> runCatching { write(text) } }
        }
}
