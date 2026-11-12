package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.connections.CalendarConnection
import com.skohscripts.kairos.core.connections.ConnectionError
import com.skohscripts.kairos.core.connections.ConnectionKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConnectionSyncTest {
    @Test fun successAndNotModifiedUpdateOnlyLocalSyncState() = runBlocking {
        val repository = onlineRepository()
        val id = repository.connected()
        val secrets = MemorySecrets().apply { put("url:$id", "https://calendar.example/private") }
        val http = FakeHttp { HttpResponse(200, mapOf("etag" to "v2"), onlineIcs.encodeToByteArray()) }
        val sync = ConnectionSync(repository, OnlineGate(http, repository), secrets, { onlineNow }, TimeZone.UTC)
        val imported = repository.snapshot.value.calendarSources.single().importedAt
        sync.run(id, true)
        assertEquals("v2", repository.connections.value.single().etag)
        assertEquals(onlineToday.toString(), repository.connections.value.single().plannedOn)
        assertEquals(imported, repository.snapshot.value.calendarSources.single().importedAt)
        val before = repository.snapshot.value
        http.respond = { HttpResponse(304, emptyMap(), byteArrayOf()) }
        sync.run(id, true)
        assertEquals(before, repository.snapshot.value)
        assertEquals(onlineNow, repository.connections.value.single().lastSyncAt)
    }

    @Test fun errorsAndMissingSecretPreserveImportedEvents() = runBlocking {
        val repository = onlineRepository()
        val id = repository.connected()
        val secrets = MemorySecrets()
        val http = FakeHttp()
        val sync = ConnectionSync(repository, OnlineGate(http, repository), secrets, { onlineNow }, TimeZone.UTC)
        val before = repository.snapshot.value
        sync.run(id, true)
        assertEquals(ConnectionError.NO_SECRET, repository.connections.value.single().lastError)
        assertTrue(http.requests.isEmpty())
        secrets.put("url:$id", "https://calendar.example/private")
        for ((status, body, expected) in listOf(
            Triple(401, "", ConnectionError.AUTH), Triple(404, "", ConnectionError.GONE),
            Triple(200, "BEGIN:VCALENDAR", ConnectionError.TRUNCATED), Triple(200, "not a calendar", ConnectionError.NOT_CALENDAR),
        )) {
            http.respond = { HttpResponse(status, emptyMap(), body.encodeToByteArray()) }
            sync.run(id, true)
            assertEquals(expected, repository.connections.value.single().lastError)
            assertEquals(before, repository.snapshot.value)
        }
    }

    @Test fun concurrentRunsUseOneTransportAtATime() = runBlocking {
        val repository = onlineRepository()
        val id = repository.connected()
        val secrets = MemorySecrets().apply { put("url:$id", "https://calendar.example/private") }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var active = 0
        var maximum = 0
        val http = FakeHttp {
            active++
            maximum = maxOf(maximum, active)
            entered.complete(Unit)
            release.await()
            active--
            HttpResponse(200, emptyMap(), onlineIcs.encodeToByteArray())
        }
        val sync = ConnectionSync(repository, OnlineGate(http, repository), secrets, { onlineNow }, TimeZone.UTC)
        val first = launch { sync.run(id, true) }
        entered.await()
        val second = launch { sync.run(id, true) }
        release.complete(Unit)
        first.join(); second.join()
        assertEquals(1, maximum)
        assertEquals(2, http.requests.size)
    }

    @Test fun previewAddAndOrphanPurgeKeepSecretsOutsideTheBase() = runBlocking {
        val repository = onlineRepository()
        val secrets = MemorySecrets()
        var backups = 0
        val online = DefaultOnlineConnections(repository, BackupStore { _, _ -> backups++ }, FakeHttp(), secrets, this, { onlineNow })
        val preview = assertIs<IcsPreviewResult.Ready>(online.previewIcs("https://calendar.example/private", false, null))
        assertTrue(repository.connections.value.isEmpty())
        val id = assertNotNull(online.addIcs(preview, preview.name, false))
        assertEquals(1, backups)
        assertEquals(preview.url, secrets.get("url:$id"))
        secrets.put("url:999", "orphan")
        secrets.put("remote:999", "orphan")
        secrets.put("account:unused", "orphan")
        online.purgeOrphans()
        assertEquals(setOf("url:$id"), secrets.keys())
        repository.disconnectCalendar(id)
        assertTrue(repository.snapshot.value.calendarSources.any { it.id == id })
        online.purgeOrphans()
        assertTrue(secrets.keys().isEmpty())
    }

    @Test fun failedSecretWriteRollsBackTheCalendar() = runBlocking {
        val repository = onlineRepository()
        val storage = MemorySecrets()
        val secrets = object : SecretStore by storage {
            override fun put(key: String, value: String) { storage.put(key, value); error("write failed") }
        }
        val online = DefaultOnlineConnections(repository, BackupStore { _, _ -> }, FakeHttp(), secrets, this, { onlineNow })
        val preview = assertIs<IcsPreviewResult.Ready>(online.previewIcs("https://calendar.example/private", false, null))
        assertTrue(runCatching { online.addIcs(preview, preview.name, false) }.isFailure)
        assertTrue(repository.connections.value.isEmpty())
        assertTrue(repository.snapshot.value.calendarSources.isEmpty())
        assertTrue(storage.keys().isEmpty())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun watcherCanStopWithoutCancellingItsScope() = runTest {
        val repository = onlineRepository()
        val http = FakeHttp()
        val secrets = MemorySecrets()
        val sync = ConnectionSync(repository, OnlineGate(http, repository), secrets, { onlineNow }, TimeZone.UTC)
        val watcher = ConnectionWatcher(sync, backgroundScope, { onlineNow })
        val job = watcher.start()
        assertTrue(job === watcher.start())
        runCurrent()
        assertTrue(http.requests.isEmpty())
        job.cancel()
        job.join()
        assertTrue(backgroundScope.coroutineContext[kotlinx.coroutines.Job]!!.isActive)
    }
}
