package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.HistoryEstimates
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task

/**
 * Ce qui reste quand une tâche dérape (docs/spec/previsions-taches.md § Étape D) : parmi les tâches comparables déjà faites,
 * celles qui ont duré **plus longtemps** que le temps déjà passé disent combien prend, au total, une tâche arrivée là. C'est
 * une durée conditionnelle lue dans l'historique, sans loi ajustée : « estimé − passé » dirait d'une tâche qui a dépassé
 * qu'elle est presque finie, l'inverse de ce qu'on observe. Pur ; rien n'est jamais écrit dans la tâche.
 */
object Remaining {
    /** Tâches comparables plus longues que le temps passé, sous lesquelles on ne dit rien. */
    const val MIN_SURVIVORS = 3

    /**
     * Le total probable d'une tâche sur laquelle [spentMinutes] sont déjà passées : [minutes] (médiane basse), entre [low] et
     * [high] (le plus court et le plus long), d'après [samples] tâches comparables de source [source].
     */
    data class Outlook(val spentMinutes: Int, val minutes: Int, val low: Int, val high: Int, val samples: Int, val source: DurationEstimator.Source)

    /**
     * Le total probable de [task], ou `null` : durées apprises coupées, tâche reçue, temps passé sous la durée attendue
     * [expectedMinutes] (elle ne dérape pas), ou moins de [MIN_SURVIVORS] tâches comparables plus longues. Comparables, dans
     * l'ordre de la chaîne : les occurrences de sa série, les tâches de même titre, puis les tâches proches du modèle
     * (ressemblance ≥ [ModelParams.SIMILARITY_MIN], les [ModelParams.NEIGHBOURS_MAX] plus proches) ; le premier niveau qui
     * a assez de tâches plus longues répond. [history] : les occurrences passées (`DurationEstimator.history`).
     */
    fun of(
        task: Task,
        spentMinutes: Int,
        expectedMinutes: Int,
        history: List<DurationEstimator.Sample>,
        settings: Settings,
        cache: ModelCache = ModelCache.shared,
    ): Outlook? {
        val learning = settings.learning ?: LearningSettings()
        if (!learning.enabled || task.origin != null || expectedMinutes <= 0 || spentMinutes < expectedMinutes) return null
        val samples = history.filter { it.space == task.space && it.taskId != task.id }
        val title = task.title.trim().lowercase()
        val levels = listOf(
            DurationEstimator.Source.SERIES to { task.seriesId?.let { id -> samples.filter { it.seriesId == id || it.taskId == id } }.orEmpty() },
            DurationEstimator.Source.TITLE to { if (title.isEmpty()) emptyList() else samples.filter { it.title.trim().lowercase() == title } },
            DurationEstimator.Source.MODEL to { if (learning.model == false) emptyList() else similar(task, samples, settings, cache, spentMinutes) },
        )
        for ((source, pool) in levels) {
            val longer = pool().filter { it.minutes > spentMinutes }.sortedWith(compareBy({ it.minutes }, { it.order }))
            if (longer.size < MIN_SURVIVORS) continue
            // Un chrono oublié (plus de trois fois la médiane des plus longues) ne tire pas le total.
            val kept = longer.filter { it.minutes <= longer[(longer.size - 1) / 2].minutes * HistoryEstimates.OUTLIER_FACTOR }
            if (kept.size < MIN_SURVIVORS) continue
            val cap = maxOf(HistoryEstimates.cap(settings), spentMinutes + 5)
            fun round(minutes: Int) = HistoryEstimates.roundToFive(minutes.toDouble()).coerceIn(spentMinutes + 5, cap)
            return Outlook(spentMinutes, round(kept[(kept.size - 1) / 2].minutes), round(kept.first().minutes), round(kept.last().minutes), kept.size, source)
        }
        return null
    }

    /** Les tâches proches de [task] plus longues que [spentMinutes] : les plus ressemblantes d'abord (égalités par récence). */
    private fun similar(task: Task, samples: List<DurationEstimator.Sample>, settings: Settings, cache: ModelCache, spentMinutes: Int): List<DurationEstimator.Sample> {
        val prepared = cache.prepare(samples)
        val corpus = prepared.corpus
        if (corpus.size < ModelParams.MIN_CORPUS) return emptyList()
        val index = cache.state(prepared, settings) { ModelEstimator.build(corpus, settings) }.fit.index
        val f = ModelEstimator.Features.of(task)
        val target = Similarity.Target(index.query(f.title), f.taskType, f.points, f.projectTag)
        return index.samples.indices
            .filter { index.samples[it].minutes > spentMinutes }
            .map { it to Similarity.of(target, index, it) }
            .filter { it.second >= ModelParams.SIMILARITY_MIN }
            .sortedWith(compareByDescending<Pair<Int, Double>> { it.second }.thenByDescending { index.samples[it.first].order })
            .take(ModelParams.NEIGHBOURS_MAX)
            .map { index.samples[it.first] }
    }
}
