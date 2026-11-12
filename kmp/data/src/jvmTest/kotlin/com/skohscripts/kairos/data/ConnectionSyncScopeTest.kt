package com.skohscripts.kairos.data

import com.skohscripts.kairos.core.sync.RecordKind
import com.skohscripts.kairos.core.sync.SyncScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectionSyncScopeTest {
    @Test
    fun connectionStateIsOutOfScope() = runTest {
        val f = ConnectionFixture()
        val repo = f.open()
        f.connect(repo)
        repo.enableSync("Desktop")
        val before = SyncScope.of(repo.snapshot.value, repo.sync.value)
        val marks = repo.sync.value
        val export = ExportCodec.encode(repo.snapshot.value, "4.1.0", f.now, repo.sync.value)
        repo.updateConnection(repo.connections.value.single().copy(syncEnabled = false, etag = "private-etag", redirectHost = "other.example"))
        assertEquals(before, SyncScope.of(repo.snapshot.value, repo.sync.value))
        assertEquals(marks, repo.sync.value)
        assertEquals(export, ExportCodec.encode(repo.snapshot.value, "4.1.0", f.now, repo.sync.value))
        assertNull(RecordKind.fromCode("calendar_connection"))
        assertEquals(1, before.calendars.size)
        assertEquals(1, before.blocks.size)
        assertEquals(1, before.dayMarks.size)
    }
}
