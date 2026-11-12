package com.skohscripts.kairos.data

import com.skohscripts.kairos.core.sync.RecordKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncReviewConnectedTest {
    private suspend fun pair(f: ConnectionFixture): Pair<KairosRepository, KairosRepository> {
        val a = f.open()
        f.connect(a)
        a.enableSync("A")
        a.createTask("Task")
        val b = f.open()
        b.enableSync("B")
        b.merge(f.synced(a)) { true }
        a.merge(f.synced(b)) { true }
        return a to b
    }

    @Test
    fun connectedBlockConflictIsNotListed() = runTest {
        val f = ConnectionFixture()
        val (a, b) = pair(f)
        val id = a.connections.value.single().sourceId
        f.connect(b)
        a.syncCalendar(id, f.plan.copy(events = f.plan.events.map { it.copy(title = "A") },
            days = f.plan.days.map { it.copy(title = "A day") }), f.today, null, f.now)
        b.syncCalendar(b.connections.value.single().sourceId,
            f.plan.copy(events = f.plan.events.map { it.copy(title = "B") }, days = f.plan.days.map { it.copy(title = "B day") }),
            f.today, null, f.now)
        a.updateConnection(a.connections.value.single().copy(syncEnabled = false))
        a.merge(f.synced(b)) { true }
        assertTrue(a.syncReview.value.isEmpty())
    }

    @Test
    fun otherConflictsStillListed() = runTest {
        val f = ConnectionFixture()
        val (a, b) = pair(f)
        a.setPriority(a.snapshot.value.tasks.single().id, 0)
        b.setPriority(b.snapshot.value.tasks.single().id, 2)
        a.merge(f.synced(b)) { true }
        assertEquals(RecordKind.TASK, a.syncReview.value.single().ref?.kind)
    }

    @Test
    fun deviceWithoutConnectionStillListsCalendarEventConflicts() = runTest {
        val f = ConnectionFixture()
        val (a, b) = pair(f)
        val id = a.connections.value.single().sourceId
        val bId = f.connect(b)
        a.syncCalendar(id, f.plan.copy(events = f.plan.events.map { it.copy(title = "A") }), f.today, null, f.now)
        b.syncCalendar(bId, f.plan.copy(events = f.plan.events.map { it.copy(title = "B") }), f.today, null, f.now)
        b.disconnectCalendar(bId)
        b.merge(f.synced(a)) { true }
        assertEquals(RecordKind.BLOCK, b.syncReview.value.single().ref?.kind)
    }

    @Test
    fun connectedKeptBlockIsNotListed() = runTest {
        val f = ConnectionFixture()
        val (a, b) = pair(f)
        b.deleteBlock(b.snapshot.value.timeBlocks.single().id)
        a.syncCalendar(a.connections.value.single().sourceId,
            f.plan.copy(events = f.plan.events.map { it.copy(title = "Server update") }), f.today, null, f.now)
        a.merge(f.synced(b)) { true }
        assertFalse(a.snapshot.value.timeBlocks.isEmpty())
        assertTrue(a.syncReview.value.isEmpty())
    }

    @Test
    fun twoDevicesConverge() = runTest {
        val f = ConnectionFixture()
        val (a, b) = pair(f)
        val bId = f.connect(b)
        val changed = f.plan.copy(events = f.plan.events.map { it.copy(title = "Server update") })
        a.syncCalendar(a.connections.value.single().sourceId, changed, f.today, "etag-a", f.now)
        b.syncCalendar(bId, changed, f.today, "etag-b", f.now)
        repeat(3) {
            a.merge(f.synced(b)) { true }
            b.merge(f.synced(a)) { true }
        }
        assertEquals(a.snapshot.value.timeBlocks.map { it.title }, b.snapshot.value.timeBlocks.map { it.title })
        assertEquals(a.snapshot.value.calendarSources.map { it.importedAt }, b.snapshot.value.calendarSources.map { it.importedAt })
        assertEquals(a.sync.value!!.marks, b.sync.value!!.marks)
        assertTrue(a.syncReview.value.isEmpty())
        assertTrue(b.syncReview.value.isEmpty())
        assertEquals("etag-a", a.connections.value.single().etag)
        assertEquals("etag-b", b.connections.value.single().etag)
    }
}
