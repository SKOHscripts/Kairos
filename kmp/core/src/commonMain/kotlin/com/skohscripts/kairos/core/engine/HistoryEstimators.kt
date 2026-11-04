package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.stats.TaskStats
import kotlin.math.floor

/**
 * Les deux niveaux historiques de la chaîne (docs/spec/durees-apprises.md) : la médiane du temps passé sur les dernières
 * occurrences de la même **série** ([SeriesEstimator]), puis des tâches de même **titre** ([TitleEstimator]). Code d'avant
 * l'extraction de l'interface, déplacé sans changement de comportement (docs/spec/estimateur-modele.md).
 */
internal object HistoryEstimates {
    /** Une occurrence au-delà de ce multiple de la médiane (chrono oublié) est écartée ; partagé avec le modèle. */
    const val OUTLIER_FACTOR = 3.0

    /** Borne haute d'une durée apprise : la fenêtre de travail (au moins 5 min). */
    fun cap(settings: Settings): Int = maxOf(5, (settings.workdayEndHour - settings.workdayStartHour) * 60)

    /** Au multiple de 5 le plus proche, la moitié vers le haut (arithmétique propre, pas de demi-pair). */
    fun roundToFive(minutes: Double): Int = floor(minutes / 5.0 + 0.5).toInt() * 5

    /** La médiane des [LearningSettings.history] dernières occurrences, sans les aberrants ; `null` sans occurrence. */
    fun fromSamples(
        candidates: List<DurationEstimator.Sample>,
        source: DurationEstimator.Source,
        settings: Settings,
    ): DurationEstimator.Estimate? {
        if (candidates.isEmpty()) return null
        val learning = settings.learning ?: LearningSettings()
        val recent = candidates.sortedByDescending { it.order }.take(maxOf(1, learning.history)).map { it.minutes.toDouble() }
        val first = TaskStats.median(recent) ?: return null
        // Un chrono oublié (plus de trois fois la médiane) ne tire pas la durée vers le haut.
        val kept = recent.filter { it <= first * OUTLIER_FACTOR }
        val median = TaskStats.median(kept) ?: return null
        return DurationEstimator.Estimate(
            roundToFive(median).coerceIn(5, cap(settings)), source, kept.size,
            reliable = kept.size >= maxOf(1, learning.minSamples),
        )
    }
}

/** Les occurrences engendrées les unes des autres (même `seriesId`, ou l'originale) : elles survivent à un renommage. */
object SeriesEstimator : DurationEstimator {
    override fun estimate(task: Task, history: DurationEstimator.History, settings: Settings): DurationEstimator.Estimate? {
        val id = task.seriesId ?: return null
        val series = history.samples.filter { it.seriesId == id || it.taskId == id }
        return HistoryEstimates.fromSamples(series, DurationEstimator.Source.SERIES, settings)
    }
}

/** Les tâches de même titre, comparé sans tenir compte des espaces autour ni de la casse. */
object TitleEstimator : DurationEstimator {
    private fun key(title: String) = title.trim().lowercase()

    override fun estimate(task: Task, history: DurationEstimator.History, settings: Settings): DurationEstimator.Estimate? {
        val title = key(task.title)
        return HistoryEstimates.fromSamples(history.samples.filter { key(it.title) == title }, DurationEstimator.Source.TITLE, settings)
    }
}
