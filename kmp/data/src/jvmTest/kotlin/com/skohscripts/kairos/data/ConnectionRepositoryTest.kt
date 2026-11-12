package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.calendar.CalendarPlan
import com.skohscripts.kairos.core.calendar.PlannedDay
import com.skohscripts.kairos.core.calendar.PlannedEvent
import com.skohscripts.kairos.core.connections.CalendarConnection
import com.skohscripts.kairos.core.connections.ConnectionError
import com.skohscripts.kairos.core.connections.ConnectionKind
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.sync.Synced
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

internal class ConnectionFixture {
    val now = Instant.parse("2026-11-11T08:00:00Z")
    val today = LocalDate(2026, 11, 11)
    val connection = CalendarConnection(0, ConnectionKind.URL, host = "calendar.example")
    val plan = CalendarPlan("Work", listOf(PlannedEvent(today.atTime(10, 0), today.atTime(11, 0), "Meeting", "event")),
        listOf(PlannedDay(today, today, "Holiday", "day")), emptyMap())
    suspend fun open(onChanged: suspend (KairosSnapshot) -> Unit = {}): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() },
            object : Clock { override fun now() = now }, TimeZone.UTC, onChanged)
    suspend fun connect(repo: KairosRepository): Long = repo.connectCalendar("Work", false, plan, today, connection, now)!!
    fun synced(repo: KairosRepository) = Synced(repo.snapshot.value, repo.sync.value!!)
}

class ConnectionRepositoryTest {
    @Test
    fun aSyncWithoutChangeWritesNothing() = runTest {
        val f = ConnectionFixture()
        var notifications = 0
        val repo = f.open { notifications++ }
        val id = f.connect(repo)
        repo.enableSync("Desktop")
        val snapshot = repo.snapshot.value
        val sync = repo.sync.value
        notifications = 0
        assertFalse(repo.syncCalendar(id, f.plan, f.today, "etag", f.now + 1.hours))
        assertEquals(snapshot, repo.snapshot.value)
        assertEquals(sync, repo.sync.value)
        assertEquals(0, notifications)
        assertEquals(f.now + 1.hours, repo.connections.value.single().lastSyncAt)
        assertEquals("etag", repo.connections.value.single().etag)
    }

    @Test
    fun syncNeverWritesImportedAt() = runTest {
        val f = ConnectionFixture()
        var notifications = 0
        val repo = f.open { notifications++ }
        val id = f.connect(repo)
        val source = repo.snapshot.value.calendarSources.single()
        val oldBlock = repo.snapshot.value.timeBlocks.single()
        notifications = 0
        val changed = f.plan.copy(events = listOf(f.plan.events.single().copy(title = "Changed", end = f.today.atTime(12, 0))))
        assertTrue(repo.syncCalendar(id, changed, f.today, null, f.now + 1.hours))
        assertEquals(source, repo.snapshot.value.calendarSources.single())
        assertEquals(oldBlock.id, repo.snapshot.value.timeBlocks.single().id)
        assertEquals("Changed", repo.snapshot.value.timeBlocks.single().title)
        assertEquals(1, notifications)
    }

    @Test
    fun attachToExistingCalendarKeepsIdentity() = runTest {
        val f = ConnectionFixture()
        var notifications = 0
        val repo = f.open { notifications++ }
        val id = repo.importCalendar("Work", false, f.plan, f.today)!!
        repo.enableSync("Desktop")
        val before = f.synced(repo)
        notifications = 0
        assertEquals(id, f.connect(repo))
        assertEquals(before, f.synced(repo))
        assertEquals(0, notifications)
        val second = f.connect(repo)
        assertTrue(second != id)
        assertEquals(listOf("Work", "Work (2)"), repo.snapshot.value.calendarSources.map { it.name })
        assertEquals(1, notifications)
    }

    @Test
    fun replaceAllErasesConnectionsAndMergeKeepsThem() = runTest {
        val f = ConnectionFixture()
        val a = f.open()
        val id = f.connect(a)
        a.enableSync("A")
        val b = f.open()
        b.enableSync("B")
        b.merge(f.synced(a)) { true }
        b.createTask("New task")
        a.merge(f.synced(b)) { true }
        assertEquals(id, a.connections.value.single().sourceId)
        val saved = f.synced(a)
        a.restoreSynced(saved)
        assertEquals(id, a.connections.value.single().sourceId)
        a.replaceAll(a.snapshot.value)
        assertTrue(a.connections.value.isEmpty())
    }

    @Test
    fun removeAndDisconnectDeleteOnlyTheConnectionRequired() = runTest {
        val f = ConnectionFixture()
        val repo = f.open()
        val id = f.connect(repo)
        val before = repo.snapshot.value
        repo.disconnectCalendar(id)
        assertTrue(repo.connections.value.isEmpty())
        assertEquals(before, repo.snapshot.value)
        f.connect(repo)
        repo.removeCalendar(id)
        assertTrue(repo.connections.value.isEmpty())
        assertTrue(repo.snapshot.value.calendarSources.isEmpty())
        assertTrue(repo.snapshot.value.timeBlocks.isEmpty())
        assertTrue(repo.snapshot.value.dayMarks.isEmpty())
    }

    @Test
    fun localMetadataDoesNotChangeExportOrNotify() = runTest {
        val f = ConnectionFixture()
        var notifications = 0
        val repo = f.open { notifications++ }
        val id = f.connect(repo)
        repo.enableSync("Desktop")
        val before = ExportCodec.encode(repo.snapshot.value, "4.1.0", f.now, repo.sync.value)
        notifications = 0
        repo.touchSync(id, f.now + 1.hours)
        repo.recordSyncFailure(id, ConnectionError.OFFLINE, f.now + 2.hours, 1, f.now + 3.hours)
        repo.updateConnection(repo.connections.value.single().copy(syncEnabled = false))
        repo.load()
        assertFalse(repo.connections.value.single().syncEnabled)
        assertEquals(ConnectionError.OFFLINE, repo.connections.value.single().lastError)
        assertEquals(before, ExportCodec.encode(repo.snapshot.value, "4.1.0", f.now, repo.sync.value))
        assertEquals(0, notifications)
    }

    @Test
    fun failedSecretCommitRollsBackCalendarAndConnection() = runTest {
        val f = ConnectionFixture()
        val repo = f.open()
        assertFailsWith<IllegalStateException> {
            repo.connectCalendar("Work", false, f.plan, f.today, f.connection, f.now) { error("secret storage failed") }
        }
        repo.load()
        assertTrue(repo.snapshot.value.calendarSources.isEmpty())
        assertTrue(repo.snapshot.value.timeBlocks.isEmpty())
        assertTrue(repo.connections.value.isEmpty())
    }

    @Test
    fun vanishedRowsOnlyDisappearFromThisSourcesFuture() = runTest {
        val f = ConnectionFixture()
        val repo = f.open()
        val past = LocalDate(2026, 11, 10)
        val plan = f.plan.copy(events = f.plan.events + f.plan.events.single().copy(start = past.atTime(10, 0), end = past.atTime(11, 0)),
            days = f.plan.days + PlannedDay(past, past, "Past", "past"))
        val id = repo.connectCalendar("Work", false, plan, f.today, f.connection, f.now)!!
        val other = repo.importCalendar("Other", false, f.plan, f.today)!!
        val retained = repo.snapshot.value.timeBlocks.filter { it.start.date == past || (it.source as? com.skohscripts.kairos.core.model.BlockSource.Imported)?.calendarId == other }
        assertTrue(repo.syncCalendar(id, f.plan.copy(events = emptyList(), days = emptyList()), f.today, null, f.now))
        assertEquals(retained, repo.snapshot.value.timeBlocks)
        assertEquals(2, repo.snapshot.value.dayMarks.size)
    }

    @Test
    fun mergePurgesConnectionWhenItsSourceDisappears() = runTest {
        val f = ConnectionFixture()
        val a = f.open()
        f.connect(a)
        a.enableSync("A")
        val b = f.open()
        b.enableSync("B")
        b.merge(f.synced(a)) { true }
        a.merge(f.synced(b)) { true }
        b.removeCalendar(b.snapshot.value.calendarSources.single().id)
        a.merge(f.synced(b)) { true }
        assertTrue(a.snapshot.value.calendarSources.isEmpty())
        assertTrue(a.connections.value.isEmpty())
    }

    @Test
    fun schemaThirteenGainsAnEmptyConnectionTable() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KairosStore.open(driver)
        driver.execute(null, "DROP TABLE calendar_connection", 0).await()
        assertEquals(13L, KairosStore.actualVersion(driver))
        val opened = KairosStore.open(driver)
        assertFalse(opened.created)
        assertEquals(14L, KairosStore.actualVersion(driver))
        val repo = KairosRepository(opened.database)
        repo.load()
        assertTrue(repo.connections.value.isEmpty())
    }
}
