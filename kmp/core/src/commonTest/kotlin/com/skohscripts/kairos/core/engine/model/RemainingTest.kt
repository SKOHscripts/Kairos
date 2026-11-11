package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.DurationEstimator.Sample
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Restant conditionnel (docs/spec/previsions-taches.md § Étape D). */
class RemainingTest {
    private val t0 = Instant.parse("2026-11-16T07:00:00Z")
    private val settings = Settings(taskTypes = "")
    private fun task(title: String = "Rapport", series: Long? = null, origin: String? = null, type: String = "", points: Int? = null) =
        Task(100, title, seriesId = series, origin = origin, taskType = type, fibonacciPoints = points, createdAt = t0, updatedAt = t0)
    private fun sample(id: Long, minutes: Int, title: String = "Rapport", series: Long? = null, space: TaskSpace = TaskSpace.PERSONAL, type: String = "", points: Int? = null) =
        Sample(id, series, title, space, minutes, taskType = type, points = points)
    private fun of(task: Task, spent: Int, expected: Int, history: List<Sample>, s: Settings = settings) = Remaining.of(task, spent, expected, history, s, ModelCache.NONE)

    @Test
    fun onlyTasksLongerThanTheTimeSpentCount() {
        // Même titre : 30, 40, 45, 70, 80, 120. À 50 min passées (attendu 45), restent 70, 80, 120 : total 80, entre 70 et 120.
        val history = listOf(30, 40, 45, 70, 80, 120).mapIndexed { i, m -> sample(i + 1L, m) }
        val out = assertNotNull(of(task(), spent = 50, expected = 45, history))
        assertEquals(Remaining.Outlook(50, 80, 70, 120, 3, DurationEstimator.Source.TITLE), out)
        // Le total annoncé dépasse toujours le temps déjà passé, ce que « estimé − passé » ne garantit pas.
        assertTrue(out.low > 50)
        // À 75 min, il n'en reste que deux : on ne dit rien plutôt qu'une phrase sur deux tâches.
        assertNull(of(task(), spent = 75, expected = 45, history))
    }

    @Test
    fun nothingWhileTheTaskIsWithinItsExpectedDuration() {
        val history = listOf(30, 40, 45, 70, 80, 120).mapIndexed { i, m -> sample(i + 1L, m) }
        assertNull(of(task(), spent = 44, expected = 45, history))
        assertNull(of(task(), spent = 50, expected = 0, history))
    }

    @Test
    fun theSeriesAnswersBeforeTheTitleAndAForgottenTimerIsDropped() {
        val series = listOf(60, 70, 80, 600).mapIndexed { i, m -> sample(i + 1L, m, title = "Autre", series = 7) }
        val titles = listOf(90, 100, 110).mapIndexed { i, m -> sample(i + 10L, m) }
        val out = of(task(series = 7), spent = 50, expected = 45, series + titles)!!
        assertEquals(DurationEstimator.Source.SERIES, out.source)
        assertEquals(listOf(70, 60, 80, 3), listOf(out.minutes, out.low, out.high, out.samples), "600 > 3 × 70 est écarté")
    }

    @Test
    fun similarTasksAnswerWhenNoTitleRepeats() {
        // Vingt rapports aux titres tous différents, de type « Rédaction » : 40 à 135 min.
        val history = (1..20).map { sample(it.toLong(), 35 + 5 * it, title = "Rapport numéro $it pour le service", type = "Rédaction", points = 3) }
        val out = assertNotNull(of(task("Rapport pour la direction", type = "Rédaction", points = 3), spent = 100, expected = 60, history))
        assertEquals(DurationEstimator.Source.MODEL, out.source)
        assertTrue(out.samples in Remaining.MIN_SURVIVORS..ModelParams.NEIGHBOURS_MAX)
        assertTrue(out.low > 100 && out.low <= out.minutes && out.minutes <= out.high)
        // Modèle coupé : plus de tâches proches, donc rien.
        assertNull(of(task("Rapport pour la direction", type = "Rédaction", points = 3), 100, 60, history, settings.copy(learning = LearningSettings(model = false))))
    }

    @Test
    fun isolationAndSwitches() {
        val history = listOf(70, 80, 120).mapIndexed { i, m -> sample(i + 1L, m) }
        assertNotNull(of(task(), 50, 45, history))
        assertNull(of(task(), 50, 45, history.map { it.copy(space = TaskSpace.TEAM) }), "un autre espace n'enseigne rien")
        assertNull(of(task(origin = "manager"), 50, 45, history), "une tâche reçue n'apprend rien")
        assertNull(of(task(), 50, 45, history, settings.copy(learning = LearningSettings(enabled = false))))
    }
}
