package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.sync.SyncFixtures.at
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ordre commun des occurrences (docs/spec/synchronisation.md § Durées apprises partagées, #100). */
class SyncOrderTest {
    private fun done(id: Long, minutes: Int) =
        Task(id, "Courrier", status = TaskStatus.DONE, createdAt = at(minutes), updatedAt = at(minutes))

    @Test
    fun ranksByCreationThenUidAndSkipsTasksWithoutUid() {
        val tasks = listOf(done(1, 30), done(2, 10), done(3, 10), done(4, 0))
        val data = SyncReconcile.legacy(KairosSnapshot(tasks = tasks)).let { d ->
            // La tâche 4 hors portée (sans `uid`) ; 2 et 3 nées à la même minute : départagées par `uid`.
            d.copy(uids = mapOf(RecordKind.TASK to mapOf(1L to "a", 2L to "z", 3L to "m")))
        }
        assertEquals(mapOf(3L to 0L, 2L to 1L, 1L to 2L), SyncOrder.of(KairosSnapshot(tasks = tasks), data))
    }

    @Test
    fun theLearnedDurationKeepsTheMostRecentByCommonRankNotByLocalId() {
        // Identifiants locaux dans l'ordre inverse de la création : 3 occurrences retenues sur 4.
        val samples = listOf(10, 20, 30, 40).mapIndexed { i, m ->
            DurationEstimator.Sample(taskId = (4 - i).toLong(), seriesId = null, title = "Courrier", space = TaskSpace.PERSONAL, minutes = m, order = i.toLong())
        }
        val settings = Settings(learning = LearningSettings(history = 3, minSamples = 2, model = false))
        val next = Task(99, "Courrier", createdAt = at(0), updatedAt = at(0))
        // Les plus récentes par rang (20, 30, 40) : médiane 30 ; par identifiant, ce serait 10, 20, 30 (médiane 20).
        assertEquals(30, DurationEstimator.estimate(next, samples, settings)!!.minutes)
    }
}
