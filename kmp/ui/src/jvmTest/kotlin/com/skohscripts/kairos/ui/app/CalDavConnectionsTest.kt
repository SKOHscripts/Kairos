package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.connections.CalendarConnection
import com.skohscripts.kairos.core.connections.ConnectionError
import com.skohscripts.kairos.core.connections.ConnectionKind
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Serveur CalDAV simulé (forme des réponses d'un Radicale réel, docs/spec/connexions-en-ligne.md § CalDAV) : il exige
 * `alice` / [password], et sert un principal, un dossier, deux calendriers (et un `href` d'un autre hôte, à ignorer).
 */
private class FakeDav(var password: String = "secret") {
    var reportStatus = 207
    var truncate = false
    var rootStatus = 207
    val http = FakeHttp { request -> respond(request) }

    private fun multistatus(inner: String) =
        "<?xml version='1.0' encoding='utf-8'?><multistatus xmlns=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">$inner</multistatus>"

    private fun ok(xml: String) = HttpResponse(207, mapOf("content-type" to "text/xml"), xml.encodeToByteArray())

    private fun respond(request: HttpRequest): HttpResponse {
        val expected = CalDavAccount("https://dav.example/", "alice", password).authorization
        if (request.headers["Authorization"] != expected) return HttpResponse(401, emptyMap(), byteArrayOf())
        val path = request.url.removePrefix("https://dav.example")
        return when (request.method to path) {
            "PROPFIND" to "/" -> if (rootStatus != 207) HttpResponse(rootStatus, emptyMap(), byteArrayOf()) else
                ok(multistatus("<response><href>/</href><propstat><prop><current-user-principal><href>/alice/</href></current-user-principal></prop><status>HTTP/1.1 200 OK</status></propstat></response>"))
            "PROPFIND" to "/.well-known/caldav" -> HttpResponse(301, mapOf("location" to "/dav/"), byteArrayOf())
            "PROPFIND" to "/dav/" -> ok(multistatus("<response><href>/dav/</href><propstat><prop><current-user-principal><href>/alice/</href></current-user-principal></prop><status>HTTP/1.1 200 OK</status></propstat></response>"))
            "PROPFIND" to "/alice/" -> ok(multistatus("<response><href>/alice/</href><propstat><prop><C:calendar-home-set><href>/alice/cal/</href></C:calendar-home-set></prop><status>HTTP/1.1 200 OK</status></propstat></response>"))
            "PROPFIND" to "/alice/cal/" -> ok(multistatus(
                listOf("work" to "Work", "perso" to "Perso").joinToString("") { (id, name) ->
                    "<response><href>/alice/cal/$id/</href><propstat><prop><resourcetype><C:calendar/><collection/></resourcetype><displayname>$name</displayname></prop><status>HTTP/1.1 200 OK</status></propstat></response>"
                } + "<response><href>https://evil.example/x/</href><propstat><prop><resourcetype><C:calendar/><collection/></resourcetype><displayname>Evil</displayname></prop><status>HTTP/1.1 200 OK</status></propstat></response>"))
            "REPORT" to "/alice/cal/work/", "REPORT" to "/alice/cal/perso/" -> {
                if (reportStatus != 207) return HttpResponse(reportStatus, emptyMap(), byteArrayOf())
                val title = if ("work" in path) "Review" else "Gym"
                val xml = multistatus("<response><href>${path}a.ics</href><propstat><prop><C:calendar-data>BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:$title@x\nDTSTART:20261113T090000Z\nDTEND:20261113T100000Z\nSUMMARY:$title\nEND:VEVENT\nEND:VCALENDAR\n</C:calendar-data></prop><status>HTTP/1.1 200 OK</status></propstat></response>")
                ok(if (truncate) xml.dropLast(30) else xml)
            }
            else -> HttpResponse(404, emptyMap(), byteArrayOf())
        }
    }
}

class CalDavConnectionsTest {
    private val account = CalDavAccount("https://dav.example/", "alice", "secret")

    @Test fun discoveryFollowsPrincipalAndHomeAndIgnoresAnotherHost() = runBlocking {
        val dav = FakeDav()
        val online = DefaultOnlineConnections(onlineRepository(), BackupStore { _, _ -> }, dav.http, MemorySecrets(), this, { onlineNow })
        val found = assertIs<CalDavClient.Discovery.Found>(online.discoverCalDav(account))
        assertEquals("dav.example", found.host)
        assertEquals(listOf(CalDavCalendar("https://dav.example/alice/cal/work/", "Work"), CalDavCalendar("https://dav.example/alice/cal/perso/", "Perso")), found.calendars)
        assertTrue(dav.http.requests.all { it.method == "PROPFIND" && it.url.startsWith("https://dav.example/") })
    }

    @Test fun wellKnownIsTriedWhenTheAddressHasNoPrincipal() = runBlocking {
        val dav = FakeDav().apply { rootStatus = 404 }
        val online = DefaultOnlineConnections(onlineRepository(), BackupStore { _, _ -> }, dav.http, MemorySecrets(), this, { onlineNow })
        assertIs<CalDavClient.Discovery.Found>(online.discoverCalDav(account))
        assertTrue(dav.http.requests.any { it.url == "https://dav.example/dav/" })
    }

    @Test fun aWrongPasswordOrAnHttpAddressIsRefused() = runBlocking {
        val online = DefaultOnlineConnections(onlineRepository(), BackupStore { _, _ -> }, FakeDav().http, MemorySecrets(), this, { onlineNow })
        assertEquals(ConnectionError.AUTH, assertIs<CalDavClient.Discovery.Failed>(online.discoverCalDav(account.copy(password = "nope"))).error)
        val http = FakeHttp()
        val plain = DefaultOnlineConnections(onlineRepository(), BackupStore { _, _ -> }, http, MemorySecrets(), this, { onlineNow })
        assertEquals(ConnectionError.UNSUPPORTED, assertIs<CalDavClient.Discovery.Failed>(plain.discoverCalDav(account.copy(server = "http://dav.example/"))).error)
        assertTrue(http.requests.isEmpty())
    }

    @Test fun twoCalendarsShareOneAccountAndSecretsStayOutOfTheBase() = runBlocking {
        val dav = FakeDav()
        val repository = onlineRepository()
        val secrets = MemorySecrets()
        var backups = 0
        val online = DefaultOnlineConnections(repository, BackupStore { _, _ -> backups++ }, dav.http, secrets, this, { onlineNow })
        val found = assertIs<CalDavClient.Discovery.Found>(online.discoverCalDav(account))
        val previews = online.previewCalDav(account, found.host, found.calendars)
        assertTrue(previews.all { it.plan?.events?.size == 1 })
        assertTrue(repository.connections.value.isEmpty())
        val ids = online.addCalDav(account, found.host, previews, hideTitles = false)
        assertEquals(2, ids.size)
        assertEquals(1, backups)
        val rows = repository.connections.value
        assertTrue(rows.all { it.kind == ConnectionKind.CALDAV && it.host == "dav.example" && it.account == rows.first().account })
        val key = rows.first().account!!
        assertEquals(setOf("account:$key") + ids.map { "remote:$it" }, secrets.keys())
        assertEquals(account, CalDavAccount.fromJson(secrets.get("account:$key")))
        assertEquals("https://dav.example/alice/cal/work/", secrets.get("remote:${ids.first()}"))
        assertEquals(listOf("Work", "Perso"), ids.map { id -> repository.snapshot.value.calendarSources.single { it.id == id }.name })
        // Ni identifiant, ni mot de passe, ni chemin dans la base : seulement l'hôte.
        assertTrue(rows.none { "alice" in it.toString() || "secret" in it.toString() })

        // Retirer un calendrier garde le compte ; retirer le dernier l'efface.
        online.remove(ids.first())
        assertTrue("account:$key" in secrets.keys())
        online.remove(ids.last())
        assertTrue(secrets.keys().isEmpty())
    }

    @Test fun syncReadsTheCalendarAndMapsErrors() = runBlocking {
        val dav = FakeDav()
        val repository = onlineRepository()
        val secrets = MemorySecrets()
        val id = repository.connected(CalendarConnection(0, ConnectionKind.CALDAV, host = "dav.example", account = "caldav-1"))
        secrets.put("account:caldav-1", account.toJson())
        secrets.put("remote:$id", "https://dav.example/alice/cal/perso/")
        val sync = ConnectionSync(repository, OnlineGate(dav.http, repository), secrets, { onlineNow }, TimeZone.UTC)
        sync.run(id, force = true)
        assertNull(repository.connections.value.single().lastError)
        assertTrue(repository.snapshot.value.timeBlocks.any { it.title == "Gym" })
        assertEquals("REPORT", dav.http.requests.last().method)
        assertEquals("1", dav.http.requests.last().headers["Depth"])

        dav.reportStatus = 405
        sync.run(id, force = true)
        assertEquals(ConnectionError.UNSUPPORTED, repository.connections.value.single().lastError)
        dav.reportStatus = 207
        dav.truncate = true
        sync.run(id, force = true)
        assertEquals(ConnectionError.TRUNCATED, repository.connections.value.single().lastError)
        // Le calendrier n'a rien perdu malgré les échecs.
        assertTrue(repository.snapshot.value.timeBlocks.any { it.title == "Gym" })
    }

    @Test fun aMissingAccountIsNoSecretWithoutAnyRequest() = runBlocking {
        val dav = FakeDav()
        val repository = onlineRepository()
        val secrets = MemorySecrets()
        val id = repository.connected(CalendarConnection(0, ConnectionKind.CALDAV, host = "dav.example", account = "caldav-1"))
        secrets.put("remote:$id", "https://dav.example/alice/cal/perso/")
        ConnectionSync(repository, OnlineGate(dav.http, repository), secrets, { onlineNow }, TimeZone.UTC).run(id, force = true)
        assertEquals(ConnectionError.NO_SECRET, repository.connections.value.single().lastError)
        assertTrue(dav.http.requests.isEmpty())
    }

    @Test fun aChangedPasswordIsRewrittenAndSyncsAgain() = runBlocking {
        val dav = FakeDav()
        val repository = onlineRepository()
        val secrets = MemorySecrets()
        val online = DefaultOnlineConnections(repository, BackupStore { _, _ -> }, dav.http, secrets, this, { onlineNow })
        val found = assertIs<CalDavClient.Discovery.Found>(online.discoverCalDav(account))
        val ids = online.addCalDav(account, found.host, online.previewCalDav(account, found.host, found.calendars), false)
        dav.password = "changed"
        online.syncNow(ids.first())
        assertEquals(ConnectionError.AUTH, repository.connections.value.first { it.sourceId == ids.first() }.lastError)
        online.changeCalDavPassword(ids.first(), "changed")
        assertTrue(repository.connections.value.all { it.lastError == null })
        assertEquals("changed", CalDavAccount.fromJson(secrets.get("account:${repository.connections.value.first().account}"))!!.password)
    }

    @Test fun theGateRefusesWebDavForAnotherHostOrAPausedConnection() = runBlocking {
        val dav = FakeDav()
        val repository = onlineRepository()
        val id = repository.connected(CalendarConnection(0, ConnectionKind.CALDAV, host = "dav.example", account = "caldav-1"))
        val gate = OnlineGate(dav.http, repository)
        assertIs<OnlineGate.Result.GateClosed>(gate.dav(id, "REPORT", "https://evil.example/x/", 1, "", account.authorization))
        repository.updateConnection(id) { it.copy(syncEnabled = false) }
        assertIs<OnlineGate.Result.GateClosed>(gate.dav(id, "REPORT", "https://dav.example/alice/cal/perso/", 1, "", account.authorization))
        assertTrue(dav.http.requests.isEmpty())
    }
}
