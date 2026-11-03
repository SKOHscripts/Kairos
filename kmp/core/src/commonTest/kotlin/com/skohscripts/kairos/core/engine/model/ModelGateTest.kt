package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.model.SimulatedHistory.Profile
import com.skohscripts.kairos.core.model.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** La garde (docs/spec/estimateur-modele.md § Fiabilité et garde). */
class ModelGateTest {
    @Test
    fun noiseIsGatedOff() {
        // Durées indépendantes de toute caractéristique, centrées sur la durée par défaut : rien à apprendre.
        val off = (1..20).count { seed ->
            val history = SimulatedHistory.generate(seed, Profile.NOISE)
            !ModelGate.evaluate(history, Settings()).passed
        }
        assertTrue(off >= 18, "bruit : la garde ne coupe le modèle que sur $off graines sur 20")
    }

    @Test
    fun tooLittleHistoryIsNeverApplied() {
        for (seed in 1..20) {
            val history = SimulatedHistory.generate(seed, Profile.SPARSE)
            assertTrue(Backtest(history).steps().none { it.modelReliable }, "graine $seed")
            val bt = Backtest(history)
            val next = bt.task(history.last()).copy(id = 999)
            val e = ModelEstimator.estimate(next, DurationEstimator.History(history), Settings())
            assertFalse(e?.reliable ?: false, "graine $seed : 12 tâches faites ne suffisent jamais")
        }
    }

    @Test
    fun theGateNeedsTenCandidatesAndAClearWin() {
        assertEquals(ModelGate.Reason.TOO_FEW_SAMPLES, ModelGate.fromErrors(List(9) { 0.1 }, List(9) { 0.5 }).reason)
        val win = ModelGate.fromErrors(List(10) { 0.2 }, List(10) { 0.5 })
        assertTrue(win.passed)
        assertEquals(ModelGate.Reason.OK, win.reason)
        // 0,45 > 0,8 × 0,5 : pas assez nettement meilleur.
        assertEquals(ModelGate.Reason.NOT_BETTER, ModelGate.fromErrors(List(10) { 0.45 }, List(10) { 0.5 }).reason)
    }

    @Test
    fun theSymmetricRelativeErrorIsBoundedAndSymmetric() {
        assertEquals(0.5, ModelGate.error(30, 60))
        assertEquals(0.5, ModelGate.error(60, 30))
        assertEquals(0.0, ModelGate.error(45, 45))
        assertEquals(0.2, ModelGate.lowMedian(listOf(0.5, 0.1, 0.2, 0.9)))
    }
}
