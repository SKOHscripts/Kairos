package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.sync.RecordKind
import com.skohscripts.kairos.core.sync.RecordRef
import com.skohscripts.kairos.core.sync.Stamp
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Format 3 du fichier (docs/spec/synchronisation.md § Format) et lecture des formats 1 et 2. */
class SyncCodecTest {
    private val now = Instant.parse("2026-10-26T08:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }

    private suspend fun open(): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock, timeZone = TimeZone.UTC)

    @Test
    fun formatThreeRoundTripsUidsMarksAndContext() = runTest {
        val repo = open()
        repo.enableSync("Bureau")
        val parent = repo.createTask("Mère")!!
        repo.createTask("Fille", parentId = parent)
        repo.deleteTask(parent) // identité fantôme : la fille référence encore la mère supprimée
        val d = repo.sync.value!!
        val text = ExportCodec.encode(repo.snapshot.value, "4.0.0", now, d)
        assertTrue("\"formatVersion\": 3" in text)
        assertTrue("\"phantoms\"" in text)
        val back = ExportCodec.decodeSynced(text)
        assertEquals(repo.snapshot.value.tasks, back.snapshot.tasks)
        assertEquals(d.device, back.data.device)
        assertEquals(d.label, back.data.label)
        assertEquals(d.seen, back.data.seen)
        assertEquals(d.uids, back.data.uids)
        assertEquals(d.marks, back.data.marks, "marques et empreintes (recalculées) identiques")
        assertEquals(d.settings, back.data.settings)
        // L'ancienne lecture ignore la synchronisation et lit le même contenu.
        assertEquals(back.snapshot, ExportCodec.decode(text))
    }

    @Test
    fun preSyncFilesReadAsLegacy() {
        val golden = javaClass.getResource("/golden/solo-export-v1.json")!!.readText()
        val synced = ExportCodec.decodeSynced(golden)
        assertEquals(Stamp.LEGACY_DEVICE, synced.data.device)
        assertTrue(synced.data.seen.isEmpty())
        val first = synced.snapshot.tasks.first()
        val uid = synced.data.uidOf(RecordKind.TASK, first.id)!!
        assertEquals("legacy:task:${first.id}:${first.createdAt.toEpochMilliseconds()}", uid)
        assertEquals(Stamp.LEGACY_DEVICE, synced.data.marks.getValue(RecordRef(RecordKind.TASK, uid)).born.device)
    }

    @Test
    fun aNewerFormatIsTooNew() {
        val e = assertFailsWith<ImportException> { ExportCodec.decodeSynced("""{"format":"kairos-export","formatVersion":4}""") }
        assertEquals(ImportException.Reason.TOO_NEW, e.reason)
    }

    private fun file(tasks: String, sync: String) =
        """{"format":"kairos-export","formatVersion":3,"settings":{},"tasks":[$tasks],"sync":$sync}"""

    private fun task(id: Int, uid: String, born: String = "1.0@aaaaaaaaaaaa") =
        """{"id":$id,"title":"T$id","createdAt":"2026-10-01T08:00:00Z","updatedAt":"2026-10-01T08:00:00Z","uid":"$uid","born":"$born"}"""

    private val okSync = """{"device":"aaaaaaaaaaaa","seen":{"aaaaaaaaaaaa":"5.0"},"settings":{"base":"0.0@aaaaaaaaaaaa"}}"""

    @Test
    fun malformedSyncDataIsCorrupted() {
        // Référence : un fichier bien formé se lit.
        ExportCodec.decodeSynced(file(task(1, "u1"), okSync))
        val bad = listOf(
            file(task(1, "u1") + "," + task(2, "u1"), okSync), // uid en double
            file(task(1, "x".repeat(101)), okSync), // uid trop long
            file(task(1, "u1", born = "pas-une-marque"), okSync), // marque mal formée
            file(task(1, "u1"), """{"device":"aaaaaaaaaaaa","seen":{"aaaaaaaaaaaa":"cinq"},"settings":{"base":"0.0@aaaaaaaaaaaa"}}"""),
            file(task(1, "u1"), """{"device":"aaaaaaaaaaaa","seen":{${(1..33).joinToString(",") { "\"d$it\":\"1.0\"" }}},"settings":{"base":"0.0@aaaaaaaaaaaa"}}"""),
        )
        for (text in bad) {
            val e = assertFailsWith<ImportException>(text.take(120)) { ExportCodec.decodeSynced(text) }
            assertEquals(ImportException.Reason.CORRUPTED, e.reason)
        }
    }

    @Test
    fun aScopedRecordWithoutUidGetsALegacyIdentity() {
        val text = """{"format":"kairos-export","formatVersion":3,"settings":{},"tasks":[{"id":7,"title":"Écrite à la main","createdAt":"2026-10-01T08:00:00Z","updatedAt":"2026-10-01T08:00:00Z"}],"sync":$okSync}"""
        val d = ExportCodec.decodeSynced(text).data
        assertTrue(d.uidOf(RecordKind.TASK, 7)!!.startsWith("legacy:task:7:"))
    }

    @Test
    fun anExportWithoutSyncIsUnchanged() = runTest {
        val repo = open()
        repo.createTask("Rapport")
        val text = ExportCodec.encode(repo.snapshot.value, "4.0.0", now)
        assertEquals(text, ExportCodec.encode(repo.snapshot.value, "4.0.0", now, sync = null))
        assertTrue("\"formatVersion\": 1" in text && "\"uid\"" !in text && "\"sync\"" !in text)
    }
}
