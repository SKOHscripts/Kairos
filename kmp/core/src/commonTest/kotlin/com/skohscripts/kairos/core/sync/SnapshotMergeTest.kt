package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.sync.SyncFixtures.Uids
import com.skohscripts.kairos.core.sync.SyncFixtures.at
import com.skohscripts.kairos.core.sync.SyncFixtures.task
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.time.measureTime

/** Fusion de deux états, cas par cas (docs/spec/synchronisation.md § Fusion, § Critères de succès). */
class SnapshotMergeTest {
    private var clock = at(0).toEpochMilliseconds()
    private fun tick(): Long { clock += 60_000; return clock }

    /** Un appareil de test : une base, son état de synchronisation et des écritures réconciliées. */
    private inner class Dev(val device: String, start: KairosSnapshot = KairosSnapshot()) {
        var snapshot = start
        var data = SyncReconcile.activate(start, device, tick())
        private val uids = Uids("$device-")
        val synced get() = Synced(snapshot, data)
        fun write(change: (KairosSnapshot) -> KairosSnapshot) {
            val before = snapshot
            snapshot = change(before)
            data = SyncReconcile.reconcile(snapshot, data, before, tick(), uids).data
        }
        fun pull(other: Dev): MergeOutcome = SnapshotMerge.merge(synced, other.synced, tick()).also {
            snapshot = it.merged.snapshot
            data = it.merged.data
        }
        fun task(uid: String): Task? = data.idsByUid()[RecordKind.TASK]?.get(uid)?.let { id -> snapshot.tasks.firstOrNull { it.id == id } }
        fun uidOf(id: Long) = data.uidOf(RecordKind.TASK, id)!!
    }

    /** Deux appareils qui ont déjà convergé sur une tâche « Rapport ». */
    private fun pair(): Pair<Dev, Dev> {
        val a = Dev("aaaaaaaaaaaa")
        a.write { it.copy(tasks = listOf(task(1, "Rapport", 0))) }
        val b = Dev("bbbbbbbbbbbb")
        b.pull(a)
        a.pull(b)
        return a to b
    }

    @Test
    fun differentFieldsMerge() {
        val (a, b) = pair()
        val uid = a.uidOf(1)
        a.write { s -> s.copy(tasks = s.tasks.map { it.copy(priority = 0) }) }
        b.write { s -> s.copy(tasks = s.tasks.map { it.copy(deadline = LocalDate(2026, 10, 9)) }) }
        val out = a.pull(b)
        assertEquals(emptyList(), out.conflicts)
        assertEquals(0, a.task(uid)!!.priority)
        assertEquals(LocalDate(2026, 10, 9), a.task(uid)!!.deadline)
        b.pull(a)
        assertEquals(Canonical.of(a.snapshot, a.data), Canonical.of(b.snapshot, b.data))
    }

    @Test
    fun sameFieldConflict() {
        val (a, b) = pair()
        val uid = a.uidOf(1)
        a.write { s -> s.copy(tasks = s.tasks.map { it.copy(deadline = LocalDate(2026, 10, 9)) }) }
        b.write { s -> s.copy(tasks = s.tasks.map { it.copy(deadline = LocalDate(2026, 10, 12)) }) }
        val out = a.pull(b)
        val c = out.conflicts.single()
        assertEquals(RecordRef(RecordKind.TASK, uid), c.ref)
        assertEquals("deadline", c.field)
        // La plus récente (celle de B, écrite après) est appliquée, l'autre est gardée pour « À revoir ».
        assertEquals(LocalDate(2026, 10, 12), a.task(uid)!!.deadline)
        assertEquals("\"2026-10-09\"", c.lostValue)
        // Les deux appareils affichent la même chose sans attendre.
        b.pull(a)
        assertEquals(LocalDate(2026, 10, 12), b.task(uid)!!.deadline)
    }

    @Test
    fun noConflictWhenOneSideSawTheOther() {
        val (a, b) = pair()
        val uid = a.uidOf(1)
        a.write { s -> s.copy(tasks = s.tasks.map { it.copy(deadline = LocalDate(2026, 10, 9)) }) }
        b.pull(a)
        b.write { s -> s.copy(tasks = s.tasks.map { it.copy(deadline = LocalDate(2026, 10, 12)) }) }
        val out = a.pull(b)
        assertEquals(emptyList(), out.conflicts, "B avait vu la valeur de A avant d'écrire la sienne")
        assertEquals(LocalDate(2026, 10, 12), a.task(uid)!!.deadline)
    }

    @Test
    fun deletionPropagates() {
        val (a, b) = pair()
        val uid = a.uidOf(1)
        a.write { it.copy(tasks = emptyList()) }
        val out = b.pull(a)
        assertNull(b.task(uid))
        assertEquals(1, out.changes.removed[RecordKind.TASK])
        assertTrue(out.changes.destructive)
    }

    @Test
    fun deletionKeepsUnseenEdit() {
        val (a, b) = pair()
        val uid = a.uidOf(1)
        a.write { it.copy(tasks = emptyList()) }
        b.write { s -> s.copy(tasks = s.tasks.map { it.copy(title = "Rapport final") }) }
        val out = a.pull(b)
        assertEquals("Rapport final", a.task(uid)?.title, "la suppression n'avait pas vu la modification : élément conservé")
        assertEquals(listOf(KeptAfterDeletion(RecordRef(RecordKind.TASK, uid))), out.kept)
        // Et B le garde aussi.
        b.pull(a)
        assertNotNull(b.task(uid))
    }

    @Test
    fun oldBackupDoesNotResurrect() {
        val (a, b) = pair()
        val backup = a.synced
        a.write { it.copy(tasks = emptyList()) }
        // Une sauvegarde faite depuis la synchronisation, fusionnée plus tard : la tâche supprimée depuis ne revient pas.
        val out = SnapshotMerge.merge(a.synced, backup, tick())
        assertTrue(out.merged.snapshot.tasks.isEmpty())
        assertTrue(out.changes.isEmpty)
        assertFalse(b.snapshot.tasks.isEmpty())
    }

    @Test
    fun preSyncFileAddsEverything() {
        val (a, _) = pair()
        val uid = a.uidOf(1)
        // Un export 3.x : sans uid ni marques. Ses éléments absents d'ici sont des ajouts ; il ne sait rien des suppressions.
        val old = KairosSnapshot(tasks = listOf(task(1, "Rapport", 0), task(2, "Ancienne", 5)))
        val legacy = Synced(old, SyncReconcile.legacy(old))
        a.write { it.copy(tasks = emptyList()) }
        val out = SnapshotMerge.merge(a.synced, legacy, tick())
        assertEquals(2, out.changes.added[RecordKind.TASK], "la tâche supprimée ici revient : l'export ne disait pas qu'elle l'était")
        assertTrue(out.writeNeeded)
        // Les marques « legacy » sont adoptées comme une écriture locale : un autre appareil pourra les supprimer.
        assertTrue(out.merged.data.marks.values.flatMap { it.all().toList() }.none { it.device == Stamp.LEGACY_DEVICE })
        assertTrue(out.merged.data.marks.keys.any { it.uid != uid })
    }

    @Test
    fun clockSkew() {
        val (a, b) = pair()
        val uid = a.uidOf(1)
        // B a une horloge en retard de plusieurs jours : une modification faite après avoir lu le fichier l'emporte quand même.
        a.write { s -> s.copy(tasks = s.tasks.map { it.copy(priority = 0) }) }
        b.pull(a)
        val late = clock - 5L * 86_400_000
        val before = b.snapshot
        b.snapshot = before.copy(tasks = before.tasks.map { it.copy(priority = 2) })
        b.data = SyncReconcile.reconcile(b.snapshot, b.data, before, late, Uids("late")).data
        val out = a.pull(b)
        assertEquals(2, a.task(uid)!!.priority)
        assertEquals(emptyList(), out.conflicts)
        // Une marque à plus de 30 jours dans le futur : refusée.
        val farFuture = b.data.copy(clock = Hlc(clock + Drift.MAX_MILLIS + 86_400_000, 0))
        val refused = runCatching { SnapshotMerge.merge(a.synced, Synced(b.snapshot, farFuture), clock) }
        assertTrue(refused.isFailure)
        val ahead = b.data.copy(clock = Hlc(clock + Drift.WARN_MILLIS + 60_000, 0))
        assertTrue(SnapshotMerge.merge(a.synced, Synced(b.snapshot, ahead), clock).clockAhead)
    }

    @Test
    fun noDuplicates() {
        val a = Dev("aaaaaaaaaaaa")
        a.write { it.copy(tasks = listOf(task(1, "Courrier", 0, { t -> t.copy(recurrence = TaskRecurrence.DAILY) }))) }
        val b = Dev("bbbbbbbbbbbb")
        b.pull(a)
        // Les deux appareils terminent la même récurrente : une seule occurrence suivante après fusion.
        fun complete(d: Dev) = d.write { s ->
            val t = s.tasks.single { it.status == TaskStatus.TODO }
            s.copy(tasks = s.tasks.map { if (it.id == t.id) it.copy(status = TaskStatus.DONE) else it } +
                t.copy(id = s.tasks.maxOf { it.id } + 1, seriesId = t.id, deadline = LocalDate(2026, 10, 2), createdAt = Instant.fromEpochMilliseconds(clock), updatedAt = Instant.fromEpochMilliseconds(clock)))
        }
        complete(a)
        complete(b)
        a.pull(b)
        assertEquals(2, a.snapshot.tasks.size)
        assertEquals(1, a.snapshot.tasks.count { it.status == TaskStatus.TODO })
    }

    @Test
    fun registers() {
        val (a, b) = pair()
        a.write { s -> s.copy(tasks = s.tasks + task(2, "Chrono", 1), workSessions = listOf(WorkSession(1, 2, at(100), null, at(100)))) }
        b.pull(a)
        // Les deux arrêtent la même session : la première fin vaut, sans conflit.
        a.write { s -> s.copy(workSessions = s.workSessions.map { it.copy(endedAt = at(130)) }) }
        b.write { s -> s.copy(workSessions = s.workSessions.map { it.copy(endedAt = at(120)) }) }
        val out = a.pull(b)
        assertEquals(emptyList(), out.conflicts)
        assertEquals(at(120), a.snapshot.workSessions.single().endedAt)
        // createdAt : le plus petit ; updatedAt : le plus grand.
        val uid = a.uidOf(1)
        a.write { s -> s.copy(tasks = s.tasks.map { if (it.id == 1L) it.copy(priority = 0, updatedAt = at(500)) else it }) }
        b.write { s -> s.copy(tasks = s.tasks.map { if (it.title == "Rapport") it.copy(projectTag = "X", updatedAt = at(400)) else it }) }
        a.pull(b)
        assertEquals(at(500), a.task(uid)!!.updatedAt)
        assertEquals(at(0), a.task(uid)!!.createdAt)
    }

    @Test
    fun deletedTaskKeepsSessionsEverywhere() {
        // Issue #193 : le temps passé sur une tâche supprimée reste compté, et le même, sur tous les appareils.
        val (a, b) = pair()
        a.write { s ->
            s.copy(
                tasks = s.tasks + task(2, "Chrono", 1),
                workSessions = listOf(WorkSession(1, 2, at(100), at(130), at(100)), WorkSession(2, 2, at(200), at(245), at(200))),
            )
        }
        b.pull(a)
        a.pull(b)
        val chrono = a.uidOf(2)
        fun spent(d: Dev) = TimeTracking.spentMinutesByTask(d.snapshot.workSessions, at(1_000)).values.sum()
        val before = spent(a)
        assertEquals(before, spent(b))

        a.write { s -> s.copy(tasks = s.tasks.filter { it.title != "Chrono" }) }
        assertEquals(2, a.snapshot.workSessions.size, "l'appareil qui supprime garde les sessions")
        assertEquals(chrono, a.data.uidOf(RecordKind.TASK, 2), "la tâche supprimée garde son identité (fantôme)")
        val out = b.pull(a)
        assertNull(b.task(chrono))
        assertEquals(null, out.changes.removed[RecordKind.SESSION], "les sessions ne sont pas retirées ailleurs")
        assertEquals(2, b.snapshot.workSessions.size)
        assertEquals(1, b.snapshot.workSessions.map { it.taskId }.toSet().size, "toujours sur la même tâche")
        assertEquals(before, spent(b))
        a.pull(b)
        assertEquals(Canonical.of(a.snapshot, a.data), Canonical.of(b.snapshot, b.data))

        // Un troisième appareil qui n'a jamais connu la tâche reçoit les sessions, sur une identité fantôme.
        val c = Dev("cccccccccccc")
        c.pull(b)
        assertEquals(before, spent(c))
        assertTrue(c.snapshot.workSessions.all { s -> c.snapshot.tasks.none { it.id == s.taskId } })
        b.pull(c)
        assertEquals(Canonical.of(b.snapshot, b.data), Canonical.of(c.snapshot, c.data))

        // Une tâche d'équipe supprimée n'a jamais eu d'identité partagée : ses sessions restent propres à l'appareil.
        a.write { s -> s.copy(tasks = s.tasks + task(50, "Équipe", 9, { it.copy(space = TaskSpace.TEAM, teamUid = "t") }), workSessions = s.workSessions + WorkSession(9, 50, at(300), at(360), at(300))) }
        a.write { s -> s.copy(tasks = s.tasks.filter { it.id != 50L }) }
        b.pull(a)
        assertTrue(a.snapshot.workSessions.any { it.taskId == 50L })
        assertEquals(2, b.snapshot.workSessions.size)
    }

    @Test
    fun orphanReferences() {
        val (a, b) = pair()
        a.write { s -> s.copy(tasks = s.tasks + task(2, "Bloquée", 1), dependencies = listOf(TaskDependency(1, 2, 1, at(5)))) }
        b.pull(a)
        // B supprime le bloqueur (et sa dépendance) ; A ne touche à rien : après fusion, la dépendance a disparu partout.
        b.write { s -> s.copy(tasks = s.tasks.filter { it.title != "Rapport" }, dependencies = emptyList()) }
        a.pull(b)
        assertEquals(listOf("Bloquée"), a.snapshot.tasks.map { it.title })
        assertTrue(a.snapshot.dependencies.isEmpty())
    }

    @Test
    fun outOfScopeIsUntouched() {
        val (a, b) = pair()
        val team = task(50, "Équipe", 9, { it.copy(space = TaskSpace.TEAM, teamUid = "t") })
        val received = task(51, "Reçue", 9, { it.copy(origin = "o|x|Paul") })
        a.write { s -> s.copy(tasks = s.tasks + team + received) }
        b.write { s -> s.copy(tasks = s.tasks.map { it.copy(priority = 0) }) }
        a.pull(b)
        assertTrue(team in a.snapshot.tasks && received in a.snapshot.tasks, "hors portée : recopié sans changement")
        b.pull(a)
        assertTrue(b.snapshot.tasks.none { it.space == TaskSpace.TEAM || it.origin != null }, "aucune donnée d'équipe n'entre ailleurs")
    }

    @Test
    fun writeNeededOnlyWhenTheFileLacksSomething() {
        val (a, b) = pair()
        assertFalse(SnapshotMerge.merge(a.synced, b.synced, tick()).writeNeeded, "convergés : rien à écrire")
        a.write { s -> s.copy(tasks = s.tasks.map { it.copy(priority = 0) }) }
        assertTrue(SnapshotMerge.merge(a.synced, b.synced, tick()).writeNeeded, "le fichier n'a pas la modification d'ici")
        val back = SnapshotMerge.merge(b.synced, a.synced, tick())
        assertFalse(back.writeNeeded, "le fichier contient déjà tout : rien à réécrire")
        assertEquals(1, back.changes.modified[RecordKind.TASK])
    }

    @Test
    fun aLocalReferenceOutOfScopeIsKept() {
        // Une note convertie en tâche d'équipe : la référence se lève vide, mais la fusion ne la vide pas ici.
        val (a, b) = pair()
        a.write { s -> s.copy(tasks = s.tasks + task(60, "Équipe", 9, { it.copy(space = TaskSpace.TEAM) }), notes = listOf(com.skohscripts.kairos.core.model.Note(1, "n", convertedTaskId = 60, createdAt = at(9), updatedAt = at(9)))) }
        b.write { s -> s.copy(tasks = s.tasks.map { it.copy(priority = 0) }) }
        a.pull(b)
        assertEquals(60, a.snapshot.notes.single().convertedTaskId)
    }

    @Test
    fun fiveThousandTasksMergeInUnderASecond() {
        val many = (1L..5_000L).map { task(it, "Tâche $it", (it % 500).toInt()) }
        val a = Dev("aaaaaaaaaaaa", KairosSnapshot(tasks = many))
        val b = Dev("bbbbbbbbbbbb", KairosSnapshot(tasks = many.map { it.copy(title = it.title + " (B)") }))
        repeat(2) { SnapshotMerge.merge(a.synced, b.synced, tick()) }
        val elapsed = measureTime { SnapshotMerge.merge(a.synced, b.synced, tick()) }
        assertTrue(elapsed.inWholeMilliseconds < 1_000, "fusion de 5 000 tâches de chaque côté : $elapsed")
    }
}
