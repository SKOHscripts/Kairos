package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.InterruptionSource
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.db.KairosDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Dérangements dans le dépôt (docs/spec/derangements.md § Stockage, § Découpe de la séance) : une transaction (dérangement, séances,
 * note), « Annuler » exact, migration additive, export et synchronisation.
 */
class InterruptionRepositoryTest {
    private var now = Instant.parse("2026-11-24T09:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private suspend fun open(): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock, timeZone = TimeZone.UTC)

    private fun KairosRepository.spent(taskId: Long) =
        TimeTracking.spentMinutesByTask(snapshot.value.workSessions, now, snapshot.value.tasks)[taskId] ?: 0

    @Test
    fun aTimerIsPausedAfterTheFactAndUndoRestoresEverything() = runTest {
        val r = open()
        r.createTask("Rapport")
        val id = r.snapshot.value.tasks.single().id
        r.startTimer(id)
        now += 60.minutes
        val before = r.snapshot.value
        val reported = assertNotNull(r.reportInterruption(15, InterruptionSource.CALL, "Rappeler le client"))
        // 45 min sur la tâche, 15 min de dérangement, le chrono continue.
        assertEquals(45, r.spent(id))
        val open = r.snapshot.value.workSessions.single { it.endedAt == null }
        assertEquals(now, open.startedAt)
        assertEquals(id, open.taskId)
        val i = r.snapshot.value.interruptions.single()
        assertEquals(15, i.minutes)
        assertEquals(InterruptionSource.CALL, i.source)
        assertEquals(id, i.taskId)
        assertEquals("Rappeler le client", r.snapshot.value.notes.single { it.id == i.noteId }.body)
        r.undoInterruption(reported)
        assertEquals(before.workSessions, r.snapshot.value.workSessions)
        assertTrue(r.snapshot.value.interruptions.isEmpty())
        assertTrue(r.snapshot.value.notes.isEmpty())
    }

    @Test
    fun anInterruptionLongerThanTheSessionRemovesItAndUndoBringsItBack() = runTest {
        val r = open()
        r.createTask("Rapport")
        val id = r.snapshot.value.tasks.single().id
        r.startTimer(id)
        now += 10.minutes
        val before = r.snapshot.value.workSessions
        val reported = assertNotNull(r.reportInterruption(15, null))
        assertEquals(0, r.spent(id))
        assertEquals(1, r.snapshot.value.workSessions.size)
        assertEquals(15, r.snapshot.value.interruptions.single().minutes)
        r.undoInterruption(reported)
        assertEquals(before, r.snapshot.value.workSessions)
    }

    @Test
    fun withoutATimerOnlyTheInterruptionIsStoredAndItCanBeCorrected() = runTest {
        val r = open()
        r.reportInterruption(500, InterruptionSource.SELF)
        val i = r.snapshot.value.interruptions.single()
        assertEquals(240, i.minutes)
        assertNull(i.taskId)
        assertTrue(r.snapshot.value.workSessions.isEmpty())
        r.updateInterruption(i.id, 5, InterruptionSource.MESSAGE)
        assertEquals(listOf(5 to InterruptionSource.MESSAGE), r.snapshot.value.interruptions.map { it.minutes to it.source })
        r.deleteInterruption(i.id)
        assertTrue(r.snapshot.value.interruptions.isEmpty())
    }

    @Test
    fun aSchemaFifteenDatabaseGainsAnEmptyTable() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        KairosRepository(KairosStore.open(driver).database).also { it.load() }.createTask("Rapport")
        driver.execute(null, "DROP TABLE interruption", 0).await()
        assertEquals(15L, KairosStore.actualVersion(driver))
        val opened = KairosStore.open(driver)
        assertEquals(KairosDatabase.Schema.version, KairosStore.actualVersion(driver))
        val repo = KairosRepository(opened.database).also { it.load() }
        assertEquals(listOf("Rapport"), repo.snapshot.value.tasks.map { it.title })
        assertTrue(repo.snapshot.value.interruptions.isEmpty())
    }

    @Test
    fun theExportCarriesThemOnlyWhenThereAreSome() = runTest {
        val r = open()
        r.createTask("Rapport")
        assertTrue("interruptions" !in ExportCodec.encode(r.snapshot.value, "test", now))
        r.reportInterruption(5, InterruptionSource.PERSON)
        val back = ExportCodec.decode(ExportCodec.encode(r.snapshot.value, "test", now))
        assertEquals(r.snapshot.value.interruptions, back.interruptions)
        // Un export d'avant (sans le tableau) se lit.
        val old = ExportCodec.encode(r.snapshot.value.copy(interruptions = emptyList()), "test", now)
        assertTrue(ExportCodec.decode(old).interruptions.isEmpty())
    }

    @Test
    fun theyTravelThroughTheLinkedFile() = runTest {
        val a = open()
        a.enableSync("PC")
        a.createTask("Rapport")
        val task = a.snapshot.value.tasks.single().id
        a.startTimer(task)
        now += 30.minutes
        a.reportInterruption(10, InterruptionSource.MESSAGE, "Répondre à Paul")
        val b = open()
        b.enableSync("Téléphone")
        assertIs<MergeResult.Applied>(b.merge(ExportCodec.decodeSynced(ExportCodec.encode(a.snapshot.value, "test", now, a.sync.value))) { true })
        val i = b.snapshot.value.interruptions.single()
        assertEquals(10, i.minutes)
        assertEquals(InterruptionSource.MESSAGE, i.source)
        assertEquals("Rapport", b.snapshot.value.tasks.single { it.id == i.taskId }.title)
        assertEquals("Répondre à Paul", b.snapshot.value.notes.single { it.id == i.noteId }.body)
    }
}
