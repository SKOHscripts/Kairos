package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.model.BlockException
import com.skohscripts.kairos.core.model.BlockSource
import com.skohscripts.kairos.core.model.CalendarSource
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.sync.SyncFixtures.Uids
import com.skohscripts.kairos.core.sync.SyncFixtures.at
import com.skohscripts.kairos.core.sync.SyncFixtures.rich
import com.skohscripts.kairos.core.sync.SyncFixtures.task
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

/** Réconciliation locale et activation (docs/spec/synchronisation.md § Réconciliation locale, § Identité). */
class SyncReconcileTest {
    private val now = at(1_000).toEpochMilliseconds()
    private val device = "a1b2c3d4e5f6"

    private fun active(s: KairosSnapshot = rich()) = SyncReconcile.activate(s, device, now)

    @Test
    fun activationGivesLegacyUidsAndMarksFromTheRecordTimes() {
        val s = rich()
        val d = active(s)
        assertEquals("legacy:task:1:${at(0).toEpochMilliseconds()}", d.uidOf(RecordKind.TASK, 1))
        assertNull(d.uidOf(RecordKind.TASK, 6), "la tâche d'équipe n'a pas d'uid")
        val m = d.marks.getValue(RecordRef(RecordKind.TASK, d.uidOf(RecordKind.TASK, 1)!!))
        assertEquals(Stamp(Hlc(at(0).toEpochMilliseconds(), 0), device), m.born)
        assertEquals(emptyMap(), m.stamps)
        assertEquals(Stamp(Hlc.ZERO, device), d.settings.base)
        assertEquals(d.clock, d.seen[device])
        assertTrue(d.clock >= Hlc(now, 0))
        assertEquals(SyncScope.of(s).size, d.marks.size)
    }

    @Test
    fun twoCopiesOfACommonOriginGetTheSameLegacyUids() {
        val a = SyncReconcile.activate(rich(), "aaaaaaaaaaaa", now)
        val b = SyncReconcile.activate(rich(), "bbbbbbbbbbbb", now + 5_000)
        assertEquals(a.uids, b.uids)
    }

    @Test
    fun examplesTakeTheirPresetUidAndTheOriginStamp() {
        val s = rich()
        val d = SyncReconcile.activate(s, device, now, presets = mapOf(RecordKind.TASK to mapOf(1L to "example:1")), origin = setOf(RecordKind.TASK to 1L))
        assertEquals("example:1", d.uidOf(RecordKind.TASK, 1))
        val m = d.marks.getValue(RecordRef(RecordKind.TASK, "example:1"))
        assertEquals(Stamp.ORIGIN, m.born)
        assertEquals(Stamp.ORIGIN, m.base)
    }

    @Test
    fun aSecondPassChangesNothing() {
        val s = rich()
        val d = active(s)
        val r = SyncReconcile.reconcile(s, d, null, now + 10, Uids())
        assertFalse(r.changed)
        assertEquals(d, r.data)
        assertFalse(SyncReconcile.reconcile(s, d, s, now + 10, Uids()).changed)
    }

    @Test
    fun aChangedFieldGetsOneFreshStampAndOnlyThatField() {
        val before = rich()
        val d = active(before)
        val after = before.copy(tasks = before.tasks.map { if (it.id == 1L) it.copy(priority = 0, updatedAt = at(900)) else it })
        val r = SyncReconcile.reconcile(after, d, before, now + 10, Uids())
        assertTrue(r.changed)
        val m = r.data.marks.getValue(RecordRef(RecordKind.TASK, d.uidOf(RecordKind.TASK, 1)!!))
        assertEquals(setOf("priority"), m.stamps.keys)
        val stamp = m.stamps.getValue("priority")
        assertEquals(device, stamp.device)
        assertTrue(stamp.hlc > d.clock)
        assertEquals(stamp.hlc, r.data.clock)
        assertEquals(stamp.hlc, r.data.seen[device])
        // Idempotence.
        assertFalse(SyncReconcile.reconcile(after, r.data, after, now + 20, Uids()).changed)
        assertFalse(SyncReconcile.reconcile(after, r.data, null, now + 20, Uids()).changed)
    }

    @Test
    fun aValueSetBackToTheOldOneIsAlsoAModification() {
        val before = rich()
        val d0 = active(before)
        val changed = before.copy(tasks = before.tasks.map { if (it.id == 1L) it.copy(priority = 0) else it })
        val d1 = SyncReconcile.reconcile(changed, d0, before, now + 10, Uids()).data
        val back = SyncReconcile.reconcile(before, d1, changed, now + 20, Uids())
        assertTrue(back.changed)
        val stamp = back.data.marks.getValue(RecordRef(RecordKind.TASK, d0.uidOf(RecordKind.TASK, 1)!!)).stamps.getValue("priority")
        assertTrue(stamp.hlc > d1.clock, "remettre l'ancienne valeur est une écriture plus récente")
    }

    @Test
    fun withoutPreviousEverythingIsCheckedAgain() {
        // Un arrêt entre la transaction et la réconciliation : la passe d'ouverture rattrape la modification.
        val before = rich()
        val d = active(before)
        val after = before.copy(notes = before.notes.map { it.copy(body = it.body + "!") })
        val r = SyncReconcile.reconcile(after, d, null, now + 10, Uids())
        assertTrue(r.changed)
        assertEquals(2, r.data.marks.count { (ref, m) -> ref.kind == RecordKind.NOTE && "body" in m.stamps })
    }

    @Test
    fun aDeletedRecordLosesItsUidAndMarks() {
        val before = rich()
        val d = active(before)
        val uid = d.uidOf(RecordKind.NOTE, 1)!!
        val after = before.copy(notes = before.notes.filter { it.id != 1L })
        val r = SyncReconcile.reconcile(after, d, before, now + 10, Uids())
        assertTrue(r.changed)
        assertNull(r.data.uidOf(RecordKind.NOTE, 1))
        assertNull(r.data.marks[RecordRef(RecordKind.NOTE, uid)])
    }

    @Test
    fun aNewRecordGetsAUidAndBornEqualsBase() {
        val before = rich()
        val d = active(before)
        val after = before.copy(tasks = before.tasks + task(20, "Nouvelle", 900))
        val r = SyncReconcile.reconcile(after, d, before, now + 10, Uids("n"))
        assertEquals("n1", r.data.uidOf(RecordKind.TASK, 20))
        val m = r.data.marks.getValue(RecordRef(RecordKind.TASK, "n1"))
        assertEquals(m.born, m.base)
        assertTrue(m.born.hlc > d.clock)
    }

    @Test
    fun theNextOccurrenceOfAFinishedRecurringTaskHasADeterministicUid() {
        val before = rich()
        val d = active(before)
        val courrier = before.tasks.first { it.id == 3L }
        val after = before.copy(
            tasks = before.tasks.map { if (it.id == 3L) it.copy(status = TaskStatus.DONE) else it } +
                courrier.copy(id = 30, deadline = LocalDate(2026, 10, 12), seriesId = 3, createdAt = at(900), updatedAt = at(900)),
        )
        val r = SyncReconcile.reconcile(after, d, before, now + 10, Uids())
        assertEquals("next:" + d.uidOf(RecordKind.TASK, 3), r.data.uidOf(RecordKind.TASK, 30))
    }

    @Test
    fun aMonthlyOccurrenceIsNamedBySeriesAndMonth() {
        val before = rich()
        val d = active(before)
        val loyer = before.tasks.first { it.id == 4L }
        val after = before.copy(tasks = before.tasks + loyer.copy(id = 40, seriesId = 4, recurrencePeriod = "2026-11", createdAt = at(900), updatedAt = at(900)))
        val r = SyncReconcile.reconcile(after, d, before, now + 10, Uids())
        assertEquals("cal:" + d.uidOf(RecordKind.TASK, 4) + ":2026-11", r.data.uidOf(RecordKind.TASK, 40))
    }

    @Test
    fun calendarsBlocksExceptionsDependenciesAndDayMarksAreDeterministic() {
        val base = rich().copy(calendarSources = emptyList(), timeBlocks = emptyList(), blockExceptions = emptyList(), dayMarks = emptyList(), dependencies = emptyList())
        val d = active(base)
        val after = base.copy(
            calendarSources = listOf(CalendarSource(1, "Agenda  Pro", importedAt = at(900), createdAt = at(900))),
            timeBlocks = listOf(
                TimeBlock(1, "x", LocalDateTime(2026, 10, 3, 10, 0), LocalDateTime(2026, 10, 3, 11, 0), createdAt = at(900), source = BlockSource.Imported(1, "ev-1")),
                TimeBlock(2, "y", LocalDateTime(2026, 10, 3, 12, 0), LocalDateTime(2026, 10, 3, 13, 0), createdAt = at(900)),
            ),
            blockExceptions = listOf(BlockException(1, 2, LocalDate(2026, 10, 5), skipped = true, createdAt = at(900))),
            dependencies = listOf(TaskDependency(1, 2, 1, at(900))),
        )
        val r = SyncReconcile.reconcile(after, d, base, now + 10, Uids("r")).data
        assertEquals("cal:agendapro", r.uidOf(RecordKind.CALENDAR, 1))
        assertEquals("ics:cal:agendapro:ev-1:2026-10-03T10:00", r.uidOf(RecordKind.BLOCK, 1))
        assertEquals("r1", r.uidOf(RecordKind.BLOCK, 2), "un créneau saisi a un uid aléatoire")
        assertEquals("exc:r1:2026-10-05", r.uidOf(RecordKind.EXCEPTION, 1))
        assertEquals("dep:${d.uidOf(RecordKind.TASK, 2)}:${d.uidOf(RecordKind.TASK, 1)}", r.uidOf(RecordKind.DEPENDENCY, 1))
    }

    @Test
    fun aTakenDeterministicUidFallsBackToRandom() {
        val base = rich()
        val d = active(base)
        // Deux exceptions le même jour pour le même créneau ne peuvent pas partager l'uid exc:… (une seule est déterministe).
        val after = base.copy(blockExceptions = base.blockExceptions + BlockException(9, 2, LocalDate(2026, 10, 6), createdAt = at(900)) + BlockException(10, 2, LocalDate(2026, 10, 6), createdAt = at(901)))
        val r = SyncReconcile.reconcile(after, d, base, now + 10, Uids("z")).data
        val a = r.uidOf(RecordKind.EXCEPTION, 9)!!
        val b = r.uidOf(RecordKind.EXCEPTION, 10)!!
        assertNotEquals(a, b)
        assertEquals("exc:${d.uidOf(RecordKind.BLOCK, 2)}:2026-10-06", a)
        assertEquals("z1", b)
    }

    @Test
    fun aDeletedParentKeepsItsUidAsAPhantom() {
        // La mère disparaît : la référence molle de la fille garde son uid (identité fantôme), donc la fille ne change pas.
        val before = rich()
        val d = active(before)
        val mother = d.uidOf(RecordKind.TASK, 1)!!
        val after = before.copy(tasks = before.tasks.filter { it.id != 1L }, dependencies = emptyList(), workSessions = before.workSessions.filter { it.taskId != 1L })
        val r = SyncReconcile.reconcile(after, d, before, now + 10, Uids()).data
        assertEquals(mother, r.uidOf(RecordKind.TASK, 1), "identité fantôme : la fille et la note convertie la référencent encore")
        assertNull(r.marks[RecordRef(RecordKind.TASK, mother)], "mais plus de marques : la tâche est supprimée")
        val fille = r.marks.getValue(RecordRef(RecordKind.TASK, d.uidOf(RecordKind.TASK, 2)!!))
        assertTrue("parentId" !in fille.stamps)
        // Plus aucune référence : l'identité fantôme disparaît.
        val orphan = after.copy(tasks = after.tasks.map { it.copy(parentId = null) }, notes = after.notes.map { it.copy(convertedTaskId = null) })
        assertNull(SyncReconcile.reconcile(orphan, r, after, now + 20, Uids()).data.uidOf(RecordKind.TASK, 1))
    }

    @Test
    fun settingsAreStampedPerField() {
        val before = rich()
        val d = active(before)
        val after = before.copy(settings = before.settings.copy(workdayEndHour = 17, language = "en"))
        val r = SyncReconcile.reconcile(after, d, before, now + 10, Uids()).data
        assertEquals(setOf("workdayEndHour"), r.settings.stamps.keys, "la langue est locale : jamais marquée")
    }

    @Test
    fun outOfScopeChangesAreIgnored() {
        val before = rich()
        val d = active(before)
        val after = before.copy(tasks = before.tasks.map { if (it.id == 6L) it.copy(title = "autre", progressPercent = 50) else it })
        assertFalse(SyncReconcile.reconcile(after, d, before, now + 10, Uids()).changed)
    }

    @Test
    fun aLongDeterministicUidIsBounded() {
        val long = SyncReconcile.bounded("ics:" + "x".repeat(200))
        assertTrue(long.length <= SyncScope.MAX_UID)
        assertTrue(long.startsWith("ics:#"))
        assertEquals(long, SyncReconcile.bounded("ics:" + "x".repeat(200)))
    }

    @Test
    fun aSingleWriteOnFiveThousandTasksIsFast() {
        val tasks = (1L..5_000L).map { task(it, "Tâche $it", (it % 500).toInt(), { t -> t.copy(recurrence = if (it % 7 == 0L) TaskRecurrence.DAILY else TaskRecurrence.NONE) }) }
        val before = KairosSnapshot(tasks = tasks)
        val d = SyncReconcile.activate(before, device, now)
        val after = before.copy(tasks = tasks.map { if (it.id == 2_500L) it.copy(title = "changée") else it })
        // Échauffement (JIT), puis la meilleure de cinq mesures : on mesure l'algorithme, pas le bruit de la machine.
        repeat(20) { SyncReconcile.reconcile(after, d, before, now + 10, Uids()) }
        val elapsed = (1..5).minOf { measureTime { SyncReconcile.reconcile(after, d, before, now + 10, Uids()) } }
        println("réconciliation d'une écriture sur 5 000 tâches : $elapsed")
        assertTrue(elapsed.inWholeMilliseconds < 20, "réconciliation d'une écriture sur 5 000 tâches : $elapsed")
    }
}
