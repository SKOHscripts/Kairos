package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.connections.CalDav
import com.skohscripts.kairos.core.connections.CalDavMerge
import com.skohscripts.kairos.core.connections.CalDavQueries
import com.skohscripts.kairos.core.connections.ConnectionError
import com.skohscripts.kairos.core.connections.ConnectionKind
import com.skohscripts.kairos.core.connections.HttpPolicy
import com.skohscripts.kairos.core.connections.XmlException
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

/**
 * Compte CalDAV, gardé en JSON dans le magasin de secrets sous `account:<compte>` (docs/spec/connexions-en-ligne.md § CalDAV) :
 * jamais dans la base, l'export ni un journal.
 */
data class CalDavAccount(val server: String, val user: String, val password: String) {
    /** `Basic`, identifiant et mot de passe en UTF-8. */
    val authorization: String get() = "Basic " + Base64.encode("$user:$password".encodeToByteArray())

    fun toJson(): String = buildJsonObject {
        put("server", server)
        put("user", user)
        put("password", password)
    }.toString()

    companion object {
        /** Une valeur illisible est absente (la connexion passe en `NO_SECRET`), jamais une exception. */
        fun fromJson(text: String?): CalDavAccount? = text?.let {
            runCatching {
                val json = Json.parseToJsonElement(it).jsonObject
                CalDavAccount(json["server"]!!.jsonPrimitive.content, json["user"]!!.jsonPrimitive.content, json["password"]!!.jsonPrimitive.content)
            }.getOrNull()
        }
    }
}

/** Un calendrier trouvé sur le serveur : [url] absolue (un secret : elle contient souvent l'identifiant), [name] à afficher. */
data class CalDavCalendar(val url: String, val name: String)

/**
 * Découverte et lecture CalDAV par la porte réseau (docs/spec/connexions-en-ligne.md § CalDAV). Aucune requête ne contourne
 * [OnlineGate] ; les réponses sont lues par `CalDav` (`core`, pur).
 */
class CalDavClient(private val gate: OnlineGate) {
    sealed interface Discovery {
        data class Found(val host: String, val calendars: List<CalDavCalendar>) : Discovery
        data class Failed(val error: ConnectionError, val host: String?) : Discovery
    }

    sealed interface Read {
        /** [text] : un seul calendrier, lu ensuite par `CalendarImport.plan`. */
        data class Success(val text: String) : Read
        data class Failed(val error: ConnectionError, val retryAfterSeconds: Long? = null) : Read
        data object GateClosed : Read
    }

    /**
     * Principal (`current-user-principal`, sur l'adresse puis sur `/.well-known/caldav`), dossier (`calendar-home-set`), puis
     * calendriers ; sans principal ni dossier, l'adresse saisie sert de dossier. Les adresses d'un autre hôte sont ignorées.
     */
    suspend fun discover(account: CalDavAccount): Discovery {
        val checked = HttpPolicy.check(account.server, ConnectionKind.CALDAV, withCredentials = true, confirmedInsecure = false)
        if (checked !is HttpPolicy.Check.Ok) return Discovery.Failed(ConnectionError.UNSUPPORTED, HttpPolicy.host(account.server))
        val host = checked.host
        suspend fun propfind(url: String, depth: Int, body: String) =
            gate.davPreview(host, "PROPFIND", url, depth, body, account.authorization)
        fun failed(error: ConnectionError) = Discovery.Failed(error, host)
        fun sameHost(base: String, href: String) = HttpPolicy.resolve(base, href)?.takeIf { HttpPolicy.host(it) == host }

        var principal: String? = null
        for (candidate in listOf(checked.url, HttpPolicy.resolve(checked.url, "/.well-known/caldav") ?: checked.url).distinct()) {
            when (val result = propfind(candidate, 0, CalDavQueries.PRINCIPAL)) {
                is OnlineGate.Result.Success -> {
                    principal = read(result.text) { CalDav.principal(it) }.getOrElse { return failed(it) }?.let { sameHost(result.url, it) }
                    if (principal != null) break
                }
                is OnlineGate.Result.Failed ->
                    if (result.error != ConnectionError.GONE && result.error != ConnectionError.UNSUPPORTED) return failed(result.error)
                else -> return failed(ConnectionError.UNSUPPORTED)
            }
        }
        val home = principal?.let { url ->
            when (val result = propfind(url, 0, CalDavQueries.HOME_SET)) {
                is OnlineGate.Result.Success -> read(result.text) { CalDav.homeSet(it) }.getOrElse { return failed(it) }?.let { sameHost(result.url, it) }
                is OnlineGate.Result.Failed -> if (result.error == ConnectionError.AUTH) return failed(result.error) else null
                else -> null
            }
        }
        return when (val result = propfind(home ?: principal ?: checked.url, 1, CalDavQueries.CALENDARS)) {
            is OnlineGate.Result.Success -> {
                val calendars = read(result.text) { CalDav.calendars(it) }.getOrElse { return failed(it) }
                    .mapNotNull { calendar -> sameHost(result.url, calendar.href)?.let { CalDavCalendar(it, calendar.name) } }
                    .distinctBy { it.url }
                if (calendars.isEmpty()) failed(ConnectionError.NOT_CALENDAR) else Discovery.Found(host, calendars)
            }
            is OnlineGate.Result.Failed -> failed(if (result.error == ConnectionError.GONE) ConnectionError.NOT_CALENDAR else result.error)
            else -> failed(ConnectionError.UNSUPPORTED)
        }
    }

    /** Aperçu d'un calendrier à l'ajout (autorisé par l'ajout explicite). */
    suspend fun preview(account: CalDavAccount, host: String, calendar: CalDavCalendar, today: LocalDate): Read =
        report(gate.davPreview(host, "REPORT", calendar.url, 1, CalDavQueries.calendarQuery(today), account.authorization))

    /** Synchronisation d'une connexion existante et active. */
    suspend fun read(sourceId: Long, account: CalDavAccount, url: String, today: LocalDate): Read =
        report(gate.dav(sourceId, "REPORT", url, 1, CalDavQueries.calendarQuery(today), account.authorization))

    private fun report(result: OnlineGate.Result): Read = when (result) {
        OnlineGate.Result.GateClosed -> Read.GateClosed
        is OnlineGate.Result.Failed -> Read.Failed(result.error, result.retryAfterSeconds)
        is OnlineGate.Result.Confirm -> Read.Failed(ConnectionError.REDIRECT_HOST)
        is OnlineGate.Result.Success -> read(result.text) { CalDav.calendarData(it) }
            .fold({ Read.Success(CalDavMerge.merge(it)) }, { Read.Failed(it) })
    }

    /** Une réponse `207` qui n'est pas un `multistatus` complet : `TRUNCATED` si coupée, `NOT_CALENDAR` sinon. */
    private inline fun <T> read(text: String, parse: (String) -> T): Outcome<T> = try {
        Outcome(parse(text), null)
    } catch (e: XmlException) {
        Outcome(null, if (e.truncated) ConnectionError.TRUNCATED else ConnectionError.NOT_CALENDAR)
    }

    private class Outcome<T>(private val value: T?, private val error: ConnectionError?) {
        @Suppress("UNCHECKED_CAST")
        inline fun getOrElse(onError: (ConnectionError) -> Nothing): T = if (error != null) onError(error) else value as T

        @Suppress("UNCHECKED_CAST")
        inline fun <R> fold(onValue: (T) -> R, onError: (ConnectionError) -> R): R = if (error != null) onError(error) else onValue(value as T)
    }
}
