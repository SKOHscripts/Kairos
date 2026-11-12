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
        data class Success(val response: HttpResponse, val url: String) : Result
        data class Failed(val error: ConnectionError, val retryAfterSeconds: Long? = null) : Result
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
        var target = (checked as HttpPolicy.Check.Ok).url
        var followed = 0
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
                return Result.Failed(if (e.tls) ConnectionError.TLS else ConnectionError.OFFLINE)
            }
            log(HttpPolicy.logLine("GET", target, response.status, start.elapsedNow().inWholeMilliseconds, response.body.size))
            if (response.body.size > MAX_BODY_BYTES) return Result.Failed(ConnectionError.TOO_BIG)
            if (response.status in listOf(301, 302, 303, 307, 308)) {
                val location = response.headers["location"] ?: return Result.Failed(ConnectionError.UNAVAILABLE)
                when (val redirect = HttpPolicy.redirect(target, location, connection.kind, followed, connection.redirectHost)) {
                    is HttpPolicy.Redirect.Follow -> { target = redirect.url; followed++ }
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
                        else -> Result.Success(response, target)
                    }
                }
                304 -> if (connection.plannedOn == today.toString() && connection.etag != null) Result.Success(response, target) else Result.Failed(ConnectionError.UNAVAILABLE)
                401, 403 -> Result.Failed(ConnectionError.AUTH)
                404, 410 -> Result.Failed(ConnectionError.GONE)
                else -> Result.Failed(ConnectionError.UNAVAILABLE, response.headers["retry-after"]?.trim()?.toLongOrNull()?.coerceAtLeast(0))
            }
        }
    }
}
