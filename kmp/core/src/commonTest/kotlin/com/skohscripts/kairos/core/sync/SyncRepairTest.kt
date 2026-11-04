package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.sync.SyncFixtures.at
import com.skohscripts.kairos.core.sync.SyncFixtures.task
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Réparations après fusion (docs/spec/synchronisation.md § Réparations). */
class SyncRepairTest {
    @Test
    fun onlyTheMostRecentOpenSessionStaysOpen() {
        val s = KairosSnapshot(
            tasks = listOf(task(1, "a"), task(2, "b")),
            workSessions = listOf(WorkSession(1, 1, at(10), null, at(10)), WorkSession(2, 2, at(20), null, at(20)), WorkSession(3, 1, at(0), at(5), at(0))),
        )
        val r = SyncRepair.repair(s, null)
        assertEquals(at(20), r.snapshot.workSessions.single { it.id == 1L }.endedAt, "arrêtée au début de la plus récente")
        assertNull(r.snapshot.workSessions.single { it.id == 2L }.endedAt)
        assertEquals(at(5), r.snapshot.workSessions.single { it.id == 3L }.endedAt)
        assertEquals(listOf<SyncRepair.Repair>(SyncRepair.Repair.SessionClosed(1)), r.repairs)
    }

    @Test
    fun aParentCycleIsBrokenAtTheGreatestUid() {
        val s = KairosSnapshot(tasks = listOf(task(1, "x", 0, { it.copy(parentId = 2) }), task(2, "y", 0, { it.copy(parentId = 1) }), task(3, "z", 0, { it.copy(parentId = 1) })))
        val d = SyncReconcile.activate(s, "aaaaaaaaaaaa", at(100).toEpochMilliseconds())
        val r = SyncRepair.repair(s, d)
        val victim = listOf(1L, 2L).maxBy { d.uidOf(com.skohscripts.kairos.core.sync.RecordKind.TASK, it)!! }
        assertNull(r.snapshot.tasks.single { it.id == victim }.parentId)
        assertEquals(listOf<SyncRepair.Repair>(SyncRepair.Repair.ParentCleared(victim)), r.repairs)
        assertEquals(1, r.snapshot.tasks.single { it.id == 3L }.parentId)
    }

    @Test
    fun theRepairIsTheSameOnEveryDevice() {
        // Mêmes données, identifiants locaux différents : la même session reste ouverte (départage par uid, pas par id).
        val a = KairosSnapshot(tasks = listOf(task(1, "a")), workSessions = listOf(WorkSession(1, 1, at(10), null, at(1)), WorkSession(2, 1, at(10), null, at(2))))
        val da = SyncReconcile.activate(a, "aaaaaaaaaaaa", at(100).toEpochMilliseconds())
        val open = SyncRepair.repair(a, da).snapshot.workSessions.single { it.endedAt == null }
        assertEquals(listOf(1L, 2L).maxBy { da.uidOf(RecordKind.SESSION, it)!! }, open.id)
    }

    @Test
    fun nothingToRepair() {
        val s = KairosSnapshot(tasks = listOf(task(1, "a")), workSessions = listOf(WorkSession(1, 1, at(10), null, at(10))))
        assertTrue(SyncRepair.repair(s, null).repairs.isEmpty())
    }
}
