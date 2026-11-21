package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.calendar.IcsParser
import com.skohscripts.kairos.core.connections.CalendarConnection
import com.skohscripts.kairos.core.connections.ConnectionError
import com.skohscripts.kairos.core.connections.ConnectionKind
import com.skohscripts.kairos.core.connections.HttpPolicy
import com.skohscripts.kairos.data.KairosRepository
import kotlinx.datetime.LocalDate
import kotlin.time.TimeSource

class OnlineGate(
    private val fetcher: HttpFetcher,
    private val repository: KairosRepository,
    private val log: (String) -> Unit = {},
) {
    sealed interface Result {
        data object GateClosed : Result
        /**
         * [text] : le corps décodé une seule fois (vide pour un 304), pour ne pas doubler la mémoire d'un corps de 16 Mo.
         * [url] : l'adresse lue après les redirections ; [storedUrl] : celle à garder pour les synchronisations suivantes, le
         * lien demandé, sauf si toutes les redirections suivies étaient permanentes (301, 308) : une redirection temporaire
         * (302, 303, 307) vers une adresse signée qui expire ne doit pas casser la connexion (décision du 2026-11-19).
         */
        data class Success(val response: HttpResponse, val url: String, val text: String, val storedUrl: String = url) : Result
        /** [detail] : cause technique d'une panne de transport, montrée sous l'erreur d'un ajout (WebDAV). */
        data class Failed(val error: ConnectionError, val retryAfterSeconds: Long? = null, val detail: String? = null) : Result
        data class Confirm(val kind: IcsConfirmation, val host: String) : Result
    }

    suspend fun fetch(sourceId: Long, url: String, today: LocalDate, authorization: String? = null): Result {
        val connection = repository.connections.value.firstOrNull { it.sourceId == sourceId && it.syncEnabled }
            ?: return Result.GateClosed
        if (HttpPolicy.host(url) != connection.host) return Result.GateClosed
        return request(connection, url, today, authorization) {
            repository.connections.value.any { it.sourceId == sourceId && it.syncEnabled }
        }
    }

    /**
     * Requête WebDAV d'une connexion CalDAV existante et active (docs/spec/connexions-en-ligne.md § CalDAV) : mêmes conditions que
     * [fetch] (ligne présente, synchronisation active, hôte de la connexion).
     */
    suspend fun dav(sourceId: Long, method: String, url: String, depth: Int, body: String, authorization: String): Result {
        val connection = repository.connections.value.firstOrNull { it.sourceId == sourceId && it.syncEnabled }
            ?: return Result.GateClosed
        if (connection.kind != ConnectionKind.CALDAV || HttpPolicy.host(url) != connection.host) return Result.GateClosed
        return davRequest(connection, method, url, depth, body, authorization) {
            repository.connections.value.any { it.sourceId == sourceId && it.syncEnabled }
        }
    }

    /** Requête WebDAV autorisée par l'ajout explicite d'un serveur CalDAV (découverte, aperçu), sans métadonnée persistée. */
    internal suspend fun davPreview(host: String, method: String, url: String, depth: Int, body: String, authorization: String): Result =
        davRequest(CalendarConnection(0, ConnectionKind.CALDAV, host = host), method, url, depth, body, authorization) { true }

    /**
     * `PROPFIND` / `REPORT` : `https` seulement, redirections du même hôte seulement (`HttpPolicy.redirect` pour `CALDAV`),
     * identifiants envoyés à l'hôte de la connexion seulement. `207` → [Result.Success] ; 401, 403 → `AUTH` ; 404, 410 → `GONE` ;
     * 400, 405, 415, 501 → `UNSUPPORTED` ; le reste → `UNAVAILABLE` (avec `Retry-After`).
     */
    private suspend fun davRequest(
        connection: CalendarConnection, method: String, url: String, depth: Int, body: String, authorization: String, allowed: () -> Boolean,
    ): Result {
        val checked = HttpPolicy.check(url, ConnectionKind.CALDAV, withCredentials = true, confirmedInsecure = false)
        if (checked !is HttpPolicy.Check.Ok || checked.host != connection.host) return Result.Failed(ConnectionError.UNSUPPORTED)
        var target = checked.url
        var followed = 0
        val payload = body.encodeToByteArray()
        while (true) {
            if (!allowed()) return Result.GateClosed
            val headers = mutableMapOf(
                "User-Agent" to "Kairos", "Accept-Encoding" to "gzip", "Depth" to depth.toString(),
                "Content-Type" to "application/xml; charset=utf-8",
            )
            if (HttpPolicy.host(target) == connection.host) headers["Authorization"] = authorization
            val start = TimeSource.Monotonic.markNow()
            val response = try {
                fetcher.fetch(HttpRequest(method, target, headers, payload))
            } catch (e: HttpTooBigException) {
                log(HttpPolicy.logLine(method, target, null, start.elapsedNow().inWholeMilliseconds, null))
                return Result.Failed(ConnectionError.TOO_BIG)
            } catch (e: HttpTransportException) {
                log(HttpPolicy.logLine(method, target, null, start.elapsedNow().inWholeMilliseconds, null))
                return Result.Failed(when {
                    e.invalidAddress -> ConnectionError.UNSUPPORTED
                    e.tls -> ConnectionError.TLS
                    else -> ConnectionError.OFFLINE
                }, detail = e.detail)
            }
            log(HttpPolicy.logLine(method, target, response.status, start.elapsedNow().inWholeMilliseconds, response.body.size))
            if (response.body.size > MAX_BODY_BYTES) return Result.Failed(ConnectionError.TOO_BIG)
            if (response.status in listOf(301, 302, 303, 307, 308)) {
                val location = response.headers["location"] ?: return Result.Failed(ConnectionError.UNAVAILABLE)
                when (val redirect = HttpPolicy.redirect(target, location, ConnectionKind.CALDAV, followed, null)) {
                    is HttpPolicy.Redirect.Follow -> { target = redirect.url; followed++ }
                    is HttpPolicy.Redirect.AskUser -> return Result.Failed(ConnectionError.REDIRECT_HOST)
                    is HttpPolicy.Redirect.Refuse -> return Result.Failed(redirect.error)
                }
                continue
            }
            return when (response.status) {
                207 -> Result.Success(response, target, response.body.decodeToString())
                401, 403 -> Result.Failed(ConnectionError.AUTH)
                404, 410 -> Result.Failed(ConnectionError.GONE)
                400, 405, 415, 501 -> Result.Failed(ConnectionError.UNSUPPORTED)
                else -> Result.Failed(ConnectionError.UNAVAILABLE, response.headers["retry-after"]?.trim()?.toLongOrNull()?.coerceAtLeast(0))
            }
        }
    }

    // An explicit preview authorizes only this read, without creating persistent metadata.
    internal suspend fun preview(url: String, confirmedInsecure: Boolean, acceptedRedirectHost: String?, today: LocalDate): Result =
        request(CalendarConnection(0, ConnectionKind.URL, insecureConfirmed = confirmedInsecure, redirectHost = acceptedRedirectHost), url, today, null) { true }

    private suspend fun request(connection: CalendarConnection, url: String, today: LocalDate, authorization: String?, allowed: () -> Boolean): Result {
        val checked = HttpPolicy.check(url, connection.kind, authorization != null, connection.insecureConfirmed)
        if (checked is HttpPolicy.Check.Refused) {
            return if (checked.reason == HttpPolicy.Reason.INSECURE_NEEDS_CONFIRMATION) {
                Result.Confirm(IcsConfirmation.INSECURE_HTTP, HttpPolicy.host(url).orEmpty())
            } else Result.Failed(ConnectionError.UNSUPPORTED)
        }
        val requested = (checked as HttpPolicy.Check.Ok).url
        var target = requested
        var followed = 0
        var permanent = true
        while (true) {
            if (!allowed()) return Result.GateClosed
            val headers = mutableMapOf("User-Agent" to "Kairos", "Accept-Encoding" to "gzip")
            val etag = connection.etag
            if (etag != null && connection.plannedOn == today.toString()) headers["If-None-Match"] = etag
            if (authorization != null && followed == 0 && HttpPolicy.host(target) == connection.host) headers["Authorization"] = authorization
            val start = TimeSource.Monotonic.markNow()
            val response = try {
                fetcher.fetch(HttpRequest("GET", target, headers))
            } catch (e: HttpTooBigException) {
                log(HttpPolicy.logLine("GET", target, null, start.elapsedNow().inWholeMilliseconds, null))
                return Result.Failed(ConnectionError.TOO_BIG)
            } catch (e: HttpTransportException) {
                log(HttpPolicy.logLine("GET", target, null, start.elapsedNow().inWholeMilliseconds, null))
                return Result.Failed(when {
                    e.invalidAddress -> ConnectionError.UNSUPPORTED
                    e.tls -> ConnectionError.TLS
                    else -> ConnectionError.OFFLINE
                })
            }
            log(HttpPolicy.logLine("GET", target, response.status, start.elapsedNow().inWholeMilliseconds, response.body.size))
            if (response.body.size > MAX_BODY_BYTES) return Result.Failed(ConnectionError.TOO_BIG)
            if (response.status in listOf(301, 302, 303, 307, 308)) {
                val location = response.headers["location"] ?: return Result.Failed(ConnectionError.UNAVAILABLE)
                when (val redirect = HttpPolicy.redirect(target, location, connection.kind, followed, connection.redirectHost)) {
                    is HttpPolicy.Redirect.Follow -> {
                        target = redirect.url
                        followed++
                        if (response.status !in listOf(301, 308)) permanent = false
                    }
                    is HttpPolicy.Redirect.AskUser -> return Result.Confirm(IcsConfirmation.REDIRECT_HOST, redirect.host)
                    is HttpPolicy.Redirect.Refuse -> return Result.Failed(redirect.error)
                }
                continue
            }
            return when (response.status) {
                200 -> {
                    val text = response.body.decodeToString()
                    when {
                        !IcsParser.isCalendar(text) -> Result.Failed(ConnectionError.NOT_CALENDAR)
                        !text.lineSequence().any { it.trim().equals("END:VCALENDAR", ignoreCase = true) } -> Result.Failed(ConnectionError.TRUNCATED)
                        else -> Result.Success(response, target, text, if (permanent) target else requested)
                    }
                }
                304 -> if (connection.plannedOn == today.toString() && connection.etag != null) Result.Success(response, target, "", if (permanent) target else requested) else Result.Failed(ConnectionError.UNAVAILABLE)
                401, 403 -> Result.Failed(ConnectionError.AUTH)
                404, 410 -> Result.Failed(ConnectionError.GONE)
                else -> Result.Failed(ConnectionError.UNAVAILABLE, response.headers["retry-after"]?.trim()?.toLongOrNull()?.coerceAtLeast(0))
            }
        }
    }
}
