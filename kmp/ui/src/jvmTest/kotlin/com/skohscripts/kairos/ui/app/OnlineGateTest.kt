package com.skohscripts.kairos.ui.app

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.calendar.CalendarImport
import com.skohscripts.kairos.core.connections.CalendarConnection
import com.skohscripts.kairos.core.connections.ConnectionError
import com.skohscripts.kairos.core.connections.ConnectionKind
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

internal val onlineNow = Instant.parse("2026-11-11T08:00:00Z")
internal val onlineToday = LocalDate(2026, 11, 11)
internal val onlineIcs = """
    BEGIN:VCALENDAR
    VERSION:2.0
    X-WR-CALNAME:Work
    BEGIN:VEVENT
    UID:meeting-1
    DTSTART:20261112T090000Z
    DTEND:20261112T100000Z
    SUMMARY:Meeting
    END:VEVENT
    END:VCALENDAR
""".trimIndent()

internal class MemorySecrets : SecretStore {
    override val level = SecretLevel.NONE
    private val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun delete(key: String) { values.remove(key) }
    override fun keys() = values.keys.toSet()
}

internal class FakeHttp(var respond: suspend (HttpRequest) -> HttpResponse = { HttpResponse(200, emptyMap(), onlineIcs.encodeToByteArray()) }) : HttpFetcher {
    val requests = mutableListOf<HttpRequest>()
    override suspend fun fetch(request: HttpRequest): HttpResponse { requests += request; return respond(request) }
}

internal suspend fun onlineRepository(): KairosRepository = KairosRepository.open(
    KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)),
    examples = { KairosSnapshot() }, clock = object : Clock { override fun now() = onlineNow },
)

internal suspend fun KairosRepository.connected(connection: CalendarConnection = CalendarConnection(0, ConnectionKind.URL, host = "calendar.example")): Long =
    connectCalendar("Work", false, CalendarImport.plan(onlineIcs, onlineToday, TimeZone.UTC), onlineToday, connection, onlineNow)!!

class OnlineGateTest {
    @Test fun closedGateNeverTouchesTransport() = runTest {
        val http = FakeHttp()
        assertIs<OnlineGate.Result.GateClosed>(OnlineGate(http, onlineRepository()).fetch(99, "https://calendar.example/private", onlineToday))
        assertTrue(http.requests.isEmpty())
    }

    @Test fun statusesAndCalendarValidation() = runTest {
        val repository = onlineRepository()
        val id = repository.connected()
        val http = FakeHttp()
        val gate = OnlineGate(http, repository)
        for ((status, body, expected) in listOf(
            Triple(401, "", ConnectionError.AUTH), Triple(404, "", ConnectionError.GONE),
            Triple(200, "<html>login</html>", ConnectionError.NOT_CALENDAR),
            Triple(200, "BEGIN:VCALENDAR\nVERSION:2.0", ConnectionError.TRUNCATED),
        )) {
            http.respond = { HttpResponse(status, emptyMap(), body.encodeToByteArray()) }
            assertEquals(expected, assertIs<OnlineGate.Result.Failed>(gate.fetch(id, "https://calendar.example/private", onlineToday)).error)
        }
        http.respond = { HttpResponse(200, emptyMap(), onlineIcs.encodeToByteArray()) }
        assertIs<OnlineGate.Result.Success>(gate.fetch(id, "https://calendar.example/private", onlineToday))
    }

    @Test fun redirectsRequireConsentAndNeverForwardAuthorization() = runTest {
        val repository = onlineRepository()
        val id = repository.connected()
        val http = FakeHttp { request ->
            if (request.url.endsWith("/start")) HttpResponse(302, mapOf("location" to "/final"), byteArrayOf())
            else HttpResponse(200, emptyMap(), onlineIcs.encodeToByteArray())
        }
        val logs = mutableListOf<String>()
        val gate = OnlineGate(http, repository, logs::add)
        assertIs<OnlineGate.Result.Success>(gate.fetch(id, "https://calendar.example/start", onlineToday, "test-token"))
        assertEquals("test-token", http.requests[0].headers["Authorization"])
        assertEquals(null, http.requests[1].headers["Authorization"])
        assertTrue(logs.none { it.contains("/start") || it.contains("test-token") })
        http.respond = { HttpResponse(302, mapOf("location" to "https://other.example/final"), byteArrayOf()) }
        assertEquals("other.example", assertIs<OnlineGate.Result.Confirm>(gate.fetch(id, "https://calendar.example/start", onlineToday)).host)
        http.respond = { HttpResponse(302, mapOf("location" to "http://calendar.example/final"), byteArrayOf()) }
        assertEquals(ConnectionError.INSECURE_REDIRECT, assertIs<OnlineGate.Result.Failed>(gate.fetch(id, "https://calendar.example/start", onlineToday)).error)
    }

    @Test fun etagIsOnlySentForTodaysPlan() = runTest {
        val repository = onlineRepository()
        val id = repository.connected(CalendarConnection(0, ConnectionKind.URL, host = "calendar.example", etag = "v1", plannedOn = onlineToday.toString()))
        val http = FakeHttp { HttpResponse(304, emptyMap(), byteArrayOf()) }
        val gate = OnlineGate(http, repository)
        assertIs<OnlineGate.Result.Success>(gate.fetch(id, "https://calendar.example/a", onlineToday))
        assertEquals("v1", http.requests.last().headers["If-None-Match"])
        gate.fetch(id, "https://calendar.example/a", LocalDate(2026, 11, 12))
        assertEquals(null, http.requests.last().headers["If-None-Match"])
    }

    @Test fun previewsAskBeforeHttpAndBeforeChangingHosts() = runTest {
        val http = FakeHttp { request ->
            if (request.url.contains("calendar.example")) HttpResponse(302, mapOf("location" to "https://other.example/final"), byteArrayOf())
            else HttpResponse(200, emptyMap(), onlineIcs.encodeToByteArray())
        }
        val gate = OnlineGate(http, onlineRepository())
        assertEquals(IcsConfirmation.INSECURE_HTTP, assertIs<OnlineGate.Result.Confirm>(
            gate.preview("http://calendar.example/a", false, null, onlineToday)).kind)
        assertTrue(http.requests.isEmpty())
        assertIs<OnlineGate.Result.Confirm>(gate.preview("https://calendar.example/a", false, null, onlineToday))
        assertIs<OnlineGate.Result.Success>(gate.preview("https://calendar.example/a", false, "other.example", onlineToday))
    }

    @Test fun disabledConnectionAndUnrelatedOriginNeverReachTransport() = runTest {
        val repository = onlineRepository()
        val id = repository.connected()
        val http = FakeHttp()
        val gate = OnlineGate(http, repository)
        assertIs<OnlineGate.Result.GateClosed>(gate.fetch(id, "https://other.example/a", onlineToday))
        repository.updateConnection(repository.connections.value.single().copy(syncEnabled = false))
        assertIs<OnlineGate.Result.GateClosed>(gate.fetch(id, "https://calendar.example/a", onlineToday))
        assertTrue(http.requests.isEmpty())
    }

    @Test fun redirectLoopStopsAfterFiveFollows() = runTest {
        val repository = onlineRepository()
        val id = repository.connected()
        val http = FakeHttp { HttpResponse(302, mapOf("location" to "/loop"), byteArrayOf()) }
        val result = OnlineGate(http, repository).fetch(id, "https://calendar.example/loop", onlineToday)
        assertEquals(ConnectionError.UNAVAILABLE, assertIs<OnlineGate.Result.Failed>(result).error)
        assertEquals(6, http.requests.size)
    }
}
