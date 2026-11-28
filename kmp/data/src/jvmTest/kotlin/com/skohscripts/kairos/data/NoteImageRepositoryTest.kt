package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.NoteStatus
import com.skohscripts.kairos.core.notes.EncodedImage
import com.skohscripts.kairos.data.db.KairosDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Images des notes dans le dépôt (docs/spec/notes-images.md § Stockage, § Export, § Synchronisation) : plafond, note sans texte,
 * suppression avec la note, conversion qui les garde, migration additive, export et fichier lié.
 */
class NoteImageRepositoryTest {
    private var now = Instant.parse("2026-11-27T09:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private suspend fun open(): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock, timeZone = TimeZone.UTC)

    private fun image(tag: Int) = EncodedImage("image/jpeg", 1_600, 1_200, Base64.encode(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), tag.toByte())))

    @Test
    fun aNoteTakesUpToFourImagesAndMayHaveNoText() = runTest {
        val r = open()
        assertFalse(r.createNote("  ", emptyList()))
        assertFalse(r.createNote("Trop", (1..5).map(::image)))
        assertTrue(r.createNote("", listOf(image(1))))
        assertTrue(r.createNote("Tableau blanc", listOf(image(2), image(3))))
        val notes = r.snapshot.value.notes
        assertEquals(listOf("", "Tableau blanc"), notes.map { it.body })
        val images = r.snapshot.value.noteImages
        assertEquals(listOf(notes[0].id, notes[1].id, notes[1].id), images.map { it.noteId })
        // L'ordre d'ajout est gardé : une milliseconde de plus par image.
        assertEquals(listOf(image(2).data, image(3).data), images.filter { it.noteId == notes[1].id }.map { it.data })
        assertTrue(images[2].createdAt > images[1].createdAt)
    }

    @Test
    fun editingKeepsRemovesAndAddsImagesWithinTheCap() = runTest {
        val r = open()
        r.createNote("Ticket", listOf(image(1), image(2)))
        val note = r.snapshot.value.notes.single()
        val (first, second) = r.snapshot.value.noteImages
        r.editNote(note.id, "Ticket", keep = listOf(second.id), add = listOf(image(3)))
        assertEquals(listOf(second.data, image(3).data), r.snapshot.value.noteImages.map { it.data })
        // Plus de quatre : rien ne change.
        val before = r.snapshot.value
        r.editNote(note.id, "Ticket", add = (4..6).map(::image))
        assertEquals(before.noteImages, r.snapshot.value.noteImages)
        // Ni texte ni image : rien ne change non plus.
        r.editNote(note.id, "", keep = emptyList())
        assertEquals(before.noteImages, r.snapshot.value.noteImages)
        assertEquals("Ticket", r.snapshot.value.notes.single().body)
        // Sans texte mais avec une image : accepté.
        r.editNote(note.id, "", keep = listOf(second.id))
        assertEquals("", r.snapshot.value.notes.single().body)
        assertEquals(listOf(second.id), r.snapshot.value.noteImages.map { it.id })
        assertTrue(first.id !in r.snapshot.value.noteImages.map { it.id })
    }

    @Test
    fun deletingANoteRemovesItsImagesConvertingOrArchivingKeepsThem() = runTest {
        val r = open()
        r.createNote("Appeler le plombier\nfuite sous l'évier", listOf(image(1)))
        r.createNote("", listOf(image(2)))
        r.createNote("À jeter", listOf(image(3)))
        val (withText, withoutText, toDelete) = r.snapshot.value.notes
        val taskId = assertNotNull(r.convertNote(withText.id))
        // Une note sans texte ne se convertit pas.
        assertEquals(null, r.convertNote(withoutText.id))
        r.archiveNote(withoutText.id)
        r.deleteNote(toDelete.id)
        val s = r.snapshot.value
        assertEquals(NoteStatus.ARCHIVED, s.notes.single { it.id == withText.id }.status)
        assertEquals(taskId, s.notes.single { it.id == withText.id }.convertedTaskId)
        assertEquals(setOf(withText.id, withoutText.id), s.noteImages.map { it.noteId }.toSet())
    }

    @Test
    fun aSchemaSeventeenDatabaseGainsAnEmptyTable() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KairosRepository(KairosStore.open(driver).database).also { it.load() }.createNote("Idée")
        driver.execute(null, "DROP TABLE note_image", 0).await()
        assertEquals(17L, KairosStore.actualVersion(driver))
        val opened = KairosStore.open(driver)
        assertEquals(KairosDatabase.Schema.version, KairosStore.actualVersion(driver))
        val repo = KairosRepository(opened.database).also { it.load() }
        assertEquals(listOf("Idée"), repo.snapshot.value.notes.map { it.body })
        assertTrue(repo.snapshot.value.noteImages.isEmpty())
    }

    @Test
    fun theExportCarriesThemAndIgnoresUnreadableOnes() = runTest {
        val r = open()
        r.createNote("Idée")
        assertTrue("noteImages" !in ExportCodec.encode(r.snapshot.value, "test", now))
        r.createNote("Photo", listOf(image(1), image(2)))
        val back = ExportCodec.decode(ExportCodec.encode(r.snapshot.value, "test", now))
        assertEquals(r.snapshot.value.noteImages, back.noteImages)
        // Une image orpheline, illisible ou de trop est ignorée ; le reste s'importe.
        val note = r.snapshot.value.notes.last().id
        val extra = r.snapshot.value.noteImages.first()
        val odd = r.snapshot.value.copy(
            noteImages = r.snapshot.value.noteImages +
                extra.copy(id = 10, noteId = 999) +
                extra.copy(id = 11, data = "pas du base64 !") +
                (12L..14L).map { extra.copy(id = it, createdAt = now + it.minutes) },
        )
        val read = ExportCodec.decode(ExportCodec.encode(odd, "test", now)).noteImages
        assertEquals(listOf(note), read.map { it.noteId }.distinct())
        assertEquals(4, read.size)
        assertTrue(10L !in read.map { it.id } && 11L !in read.map { it.id })
    }

    @Test
    fun theyTravelThroughTheLinkedFileAndLeaveWithTheirNote() = runTest {
        val a = open()
        a.enableSync("PC")
        a.createNote("Tableau blanc", listOf(image(1), image(2)))
        val b = open()
        b.enableSync("Téléphone")
        assertIs<MergeResult.Applied>(b.merge(ExportCodec.decodeSynced(ExportCodec.encode(a.snapshot.value, "test", now, a.sync.value))) { true })
        val note = b.snapshot.value.notes.single()
        assertEquals("Tableau blanc", note.body)
        assertEquals(listOf(image(1).data, image(2).data), b.snapshot.value.noteImages.filter { it.noteId == note.id }.map { it.data })
        // Retirée d'un côté qui l'avait vue : elle disparaît de l'autre.
        now += 1.minutes
        b.editNote(note.id, note.body, keep = listOf(b.snapshot.value.noteImages.last().id))
        assertIs<MergeResult.Applied>(a.merge(ExportCodec.decodeSynced(ExportCodec.encode(b.snapshot.value, "test", now, b.sync.value))) { true })
        assertEquals(listOf(image(2).data), a.snapshot.value.noteImages.map { it.data })
        // Une note supprimée emporte ses images partout.
        now += 1.minutes
        a.deleteNote(a.snapshot.value.notes.single().id)
        assertIs<MergeResult.Applied>(b.merge(ExportCodec.decodeSynced(ExportCodec.encode(a.snapshot.value, "test", now, a.sync.value))) { true })
        assertTrue(b.snapshot.value.notes.isEmpty())
        assertTrue(b.snapshot.value.noteImages.isEmpty())
    }
}
