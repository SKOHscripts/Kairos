package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.sync.SyncFixtures.at
import com.skohscripts.kairos.core.sync.SyncFixtures.task
import kotlin.test.Test
import kotlin.test.assertEquals

/** Aperçu du premier rapprochement (docs/spec/synchronisation.md § Premier rapprochement) : valeurs seules, tâches à part. */
class SyncSummaryTest {
    private fun synced(s: KairosSnapshot) = Synced(s, SyncReconcile.legacy(s))

    @Test
    fun countsWhatEachSideHasAloneAndWhatDiffers() {
        val common = task(1, "Commune")
        val ours = KairosSnapshot(
            tasks = listOf(common, task(2, "Ici", 1), task(3, "Changée", 2)),
            notes = listOf(Note(1, "Note d'ici", createdAt = at(5), updatedAt = at(5))),
        )
        val theirs = KairosSnapshot(
            tasks = listOf(common, task(3, "Changée", 2, { it.copy(priority = 0) }), task(4, "Là-bas", 3), task(5, "Là-bas aussi", 4)),
            settings = ours.settings.copy(workdayStartHour = 7),
        )
        assertEquals(SyncSummary(hereOnly = 1, fileOnly = 2, different = 1, others = 1, settings = true), SyncSummary.of(synced(ours), synced(theirs)))
    }

    @Test
    fun theSameValuesUnderOtherStampsDoNotDiffer() {
        val s = KairosSnapshot(tasks = listOf(task(1, "Rapport")))
        val other = SyncReconcile.activate(s, "b0b0b0b0b0b0", at(60).toEpochMilliseconds())
        assertEquals(SyncSummary(0, 0, 0, 0, false), SyncSummary.of(synced(s), Synced(s, other)))
    }
}
