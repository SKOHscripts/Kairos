package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task

/**
 * La chaîne de repli (docs/spec/estimateur-modele.md § Interface extraite) : les garde-fous communs d'abord (durées apprises
 * désactivées, tâche reçue d'un manager, estimation explicite → `null`), puis les [levels] dans l'ordre sur les seules
 * occurrences du même espace, hors la tâche elle-même. **Le premier niveau fiable gagne** ; à défaut, le premier qui a quelque
 * chose à dire est rendu (peu fiable : affiché, jamais appliqué).
 */
class EstimatorChain(private val levels: List<DurationEstimator>) : DurationEstimator {
    override fun estimate(task: Task, history: DurationEstimator.History, settings: Settings): DurationEstimator.Estimate? {
        val learning = settings.learning ?: LearningSettings()
        if (!learning.enabled || task.origin != null) return null
        if (task.estimatedMinutes != null && task.estimatedMinutes > 0) return null
        // Déjà réduit à l'espace de la tâche, sans elle (la façade le fait une fois) : la même liste est gardée, le cache du modèle
        // la reconnaît par identité.
        val samples = history.samples
        val sameSpace = if (samples.all { it.space == task.space && it.taskId != task.id }) {
            history
        } else {
            DurationEstimator.History(samples.filter { it.space == task.space && it.taskId != task.id }, history.cache)
        }
        var weak: DurationEstimator.Estimate? = null
        for (level in levels) {
            val estimate = level.estimate(task, sameSpace, settings) ?: continue
            if (estimate.reliable) return estimate
            if (weak == null) weak = estimate
        }
        return weak
    }
}
