package com.skohscripts.kairos.ui.app

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.data.ExportCodec
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Fichier lié du bureau et d'Android (docs/spec/synchronisation.md § Fichier lié : cycle) : délai, lien gardé, et surtout deux
 * appareils réels (deux bases en mémoire) autour d'un même fichier : convergence, pas de ping-pong, premier rapprochement
 * confirmé, fichier illisible, version plus récente, échec d'écriture.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LinkedFileSyncTest {
    private class TestClock(var now: Instant) : Clock {
        override fun now() = now
    }

    private val clock = TestClock(Instant.parse("2026-11-04T08:00:00Z"))
    private val path = "/sync/kairos.json"

    /** Le dossier synchronisé : un seul fichier par cible, partagé par les appareils ; nombre d'écritures. */
    private class Disk {
        val files = mutableMapOf<String, String>()
        var writes = 0
        var broken = false
    }

    /** Un appareil devant le [disk] : son lien gardé, son sélecteur. */
    private class FakeIo(val disk: Disk) : LinkedFileIo {
        var saved: SavedLink? = null
        var next: String? = "/sync/kairos.json"

        override suspend fun pickCreate(suggestedName: String) = next
        override suspend fun pickOpen() = next
        override suspend fun read(target: String): String {
            check(!disk.broken) { "dossier disparu" }
            return disk.files[target] ?: error("absent")
        }
        override suspend fun write(target: String, text: String) {
            check(!disk.broken) { "dossier disparu" }
            disk.files[target] = text
            disk.writes++
        }
        override fun displayName(target: String) = target.substringAfterLast('/')
        override suspend fun loadLink() = saved
        override suspend fun saveLink(link: SavedLink?) {
            saved = link
        }
    }

    private class Device(val repository: KairosRepository, val sync: LinkedFileSync, val io: FakeIo)

    /** Un appareil neuf, sans exemples, branché sur [disk] (comme `DesktopServices`). */
    private suspend fun TestScope.device(disk: Disk, scope: CoroutineScope = backgroundScope): Device {
        val io = FakeIo(disk)
        lateinit var sync: LinkedFileSync
        val repository = KairosRepository.open(
            KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)),
            examples = { KairosSnapshot() },
            clock = clock,
            onChanged = { sync.changed(it) },
        )
        sync = LinkedFileSync(io, scope, clock, "Test")
        sync.start(repository)
        return Device(repository, sync, io)
    }

    private fun Device.notes() = repository.snapshot.value.notes.map(Note::body).sorted()

    private suspend fun Device.note(body: String) {
        clock.now += 1.seconds
        repository.createNote(body)
    }

    @Test
    fun aBurstOfChangesGivesOneWriteAfterTheDelay() = runTest {
        val disk = Disk()
        val a = device(disk)
        assertTrue(a.sync.createFile())
        assertEquals(1, disk.writes)
        assertEquals(LinkedFileState.Linked("kairos.json"), a.sync.state.value)
        for (n in 1..5) {
            a.note("note $n")
            advanceTimeBy(500)
        }
        runCurrent()
        assertEquals(1, disk.writes)
        advanceTimeBy(LinkedFileSync.DEBOUNCE_MS + 1)
        runCurrent()
        assertEquals(2, disk.writes)
        // Le fichier est un export de format 3 de la dernière base, avec son état de synchronisation.
        val synced = ExportCodec.decodeSynced(disk.files.getValue(path))
        assertEquals(a.notes(), synced.snapshot.notes.map(Note::body).sorted())
        assertEquals(a.repository.sync.value!!.device, synced.data.device)
    }

    @Test
    fun flushWritesAtOnceAndTheLinkIsFoundAgainAtStartup() = runTest {
        val disk = Disk()
        val a = device(disk)
        a.sync.createFile()
        a.note("une")
        a.sync.flush()
        assertEquals(2, disk.writes)
        // Rien à écrire : flush ne réécrit pas.
        a.sync.flush()
        assertEquals(2, disk.writes)
        val again = LinkedFileSync(a.io, backgroundScope, clock, "Test")
        again.start(a.repository)
        assertEquals(LinkedFileState.Linked("kairos.json"), again.state.value)
        assertEquals(LinkHash.of(disk.files.getValue(path)), a.io.saved!!.hash)
        assertEquals(2, disk.writes)
    }

    @Test
    fun twoDevicesConvergeThroughTheFileWithoutPingPong() = runTest {
        val disk = Disk()
        val a = device(disk)
        a.note("de A")
        a.sync.createFile()
        // B, appareil neuf, ouvre le fichier et le fusionne (sans confirmation : il n'a rien à lui).
        val b = device(disk)
        assertNotNull(b.sync.openFile())
        assertTrue(b.sync.adoptOpened(merge = true))
        assertEquals(listOf("de A"), b.notes())
        b.note("de B")
        b.sync.flush()
        a.sync.mergeNow()
        assertEquals(listOf("de A", "de B"), a.notes())
        assertEquals(a.notes(), b.notes())
        // Convergés : chacun relit le fichier de l'autre sans plus rien écrire (l'outil tiers n'est plus réveillé).
        a.sync.mergeNow()
        b.sync.mergeNow()
        val settled = disk.writes
        repeat(3) {
            a.sync.mergeNow()
            b.sync.mergeNow()
        }
        assertEquals(settled, disk.writes)
        assertIs<LinkedFileState.Linked>(a.sync.state.value)
        assertIs<LinkedFileState.Linked>(b.sync.state.value)
        // Une suppression passe aussi par le fichier.
        val noteId = b.repository.snapshot.value.notes.single { it.body == "de A" }.id
        clock.now += 1.seconds
        b.repository.deleteNote(noteId)
        b.sync.flush()
        a.sync.mergeNow()
        assertEquals(listOf("de B"), a.notes())
    }

    @Test
    fun theFirstMeetingAsksBeforeMergingAndCancelUnlinksWithoutWriting() = runTest {
        val disk = Disk()
        val a = device(disk)
        a.note("de A")
        a.sync.createFile()
        // B, qui a ses propres données, a créé son fichier au même endroit (deux appareils liés avant la 4.0).
        val b = device(disk)
        b.note("de B")
        assertTrue(b.sync.createFile())
        val text = disk.files.getValue(path)
        val writes = disk.writes
        a.sync.mergeNow()
        val asking = assertIs<LinkedFileState.NeedsConfirmation>(a.sync.state.value)
        // Une note de chaque côté : des « autres éléments » (seules les tâches sont détaillées).
        assertEquals(0, asking.summary.hereOnly)
        assertEquals(2, asking.summary.others)
        assertEquals(listOf("de A"), a.notes())
        // En attente : ni les relectures ni les écritures programmées n'agissent.
        a.note("encore de A")
        advanceTimeBy(LinkedFileSync.DEBOUNCE_MS + 1)
        runCurrent()
        a.sync.mergeNow()
        assertEquals(text, disk.files.getValue(path))
        assertEquals(writes, disk.writes)
        a.sync.cancelMerge()
        assertEquals(LinkedFileState.NotLinked, a.sync.state.value)
        assertNull(a.io.saved)
        assertEquals(text, disk.files.getValue(path))
    }

    @Test
    fun aConfirmedFirstMeetingKeepsEverythingFromBothSides() = runTest {
        val disk = Disk()
        val a = device(disk)
        a.note("de A")
        a.sync.createFile()
        val b = device(disk)
        b.note("de B")
        b.sync.createFile()
        a.sync.mergeNow()
        assertIs<LinkedFileState.NeedsConfirmation>(a.sync.state.value)
        a.sync.confirmMerge()
        assertIs<LinkedFileState.Linked>(a.sync.state.value)
        assertEquals(listOf("de A", "de B"), a.notes())
        // B reçoit en retour sans être interrogé : son contexte connaît déjà A par le fichier.
        b.sync.mergeNow()
        assertIs<LinkedFileState.Linked>(b.sync.state.value)
        assertEquals(listOf("de A", "de B"), b.notes())
    }

    @Test
    fun anUnreadableFileIsRetriedThenReportedAndNeverWritten() = runTest {
        val disk = Disk()
        val a = device(disk)
        a.sync.createFile()
        disk.files[path] = "{ coupé"
        val writes = disk.writes
        a.sync.mergeNow()
        a.sync.mergeNow()
        assertIs<LinkedFileState.Linked>(a.sync.state.value)
        a.sync.mergeNow()
        assertEquals(LinkedFileState.Unreadable("kairos.json", UnreadableReason.NOT_READABLE), a.sync.state.value)
        // Une modification locale attend sans être écrite.
        a.note("pendant")
        a.sync.flush()
        assertEquals(writes, disk.writes)
        assertEquals("{ coupé", disk.files.getValue(path))
        // « Écraser » y remet les données de cet appareil.
        a.sync.overwrite()
        assertIs<LinkedFileState.Linked>(a.sync.state.value)
        assertEquals(listOf("pendant"), ExportCodec.decode(disk.files.getValue(path)).notes.map(Note::body))
    }

    @Test
    fun aFileFromANewerVersionStopsAtOnce() = runTest {
        val disk = Disk()
        val a = device(disk)
        a.sync.createFile()
        disk.files[path] = """{"format":"kairos-export","formatVersion":99}"""
        a.sync.mergeNow()
        assertEquals(LinkedFileState.Unreadable("kairos.json", UnreadableReason.TOO_NEW), a.sync.state.value)
    }

    @Test
    fun aFailedWriteIsReportedThenRetried() = runTest {
        val disk = Disk()
        val a = device(disk)
        a.sync.createFile()
        disk.broken = true
        a.note("une")
        a.sync.flush()
        assertEquals(LinkedFileState.WriteFailed("kairos.json"), a.sync.state.value)
        disk.broken = false
        a.sync.retry()
        assertIs<LinkedFileState.Linked>(a.sync.state.value)
        assertEquals(listOf("une"), ExportCodec.decode(disk.files.getValue(path)).notes.map(Note::body))
    }

    @Test
    fun replacingWithAnOpenedFileMakesItTheReference() = runTest {
        val disk = Disk()
        val a = device(disk)
        a.note("de A")
        a.sync.createFile()
        val b = device(disk)
        b.note("de B")
        val opened = assertNotNull(b.sync.openFile())
        assertTrue(b.sync.adoptOpened(merge = false))
        // L'interface remplace ensuite les données (`replaceWithBackup`) : le fichier n'est pas fusionné entre-temps.
        b.repository.replaceAll(ExportCodec.decode(opened.text))
        assertEquals(listOf("de A"), b.notes())
        b.sync.mergeNow()
        assertIs<LinkedFileState.Linked>(b.sync.state.value)
        assertEquals(listOf("de A"), b.notes())
    }
}
