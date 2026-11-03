package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.time.Instant

/**
 * Estimations qui changent et estimations saisies à revoir (docs/spec/estimateur-modele.md § Estimations qui changent, besoins
 * 12 et 13). Pur : l'instant, le fuseau et l'instantané sont des paramètres.
 */
object EstimateChanges {
    const val MIN_MINUTES = 15
    const val MIN_SHARE = 0.25

    /** Un écart **net** : au moins [MIN_MINUTES] minutes, ou [MIN_SHARE] de l'ancienne valeur. */
    fun significant(before: Int, after: Int): Boolean {
        val gap = abs(after - before)
        return gap >= MIN_MINUTES || (before > 0 && gap >= MIN_SHARE * before)
    }

    /** Une durée appliquée qui a changé : de [before] à [after] minutes. */
    data class Change(val taskId: Long, val before: Int, val after: Int)

    /** Les tâches présentes dans les deux relevés dont la durée a changé nettement, par `taskId`. */
    fun diff(before: Map<Long, Int>, after: Map<Long, Int>): List<Change> =
        before.keys.filter { it in after }.sorted().mapNotNull { id ->
            val b = before.getValue(id)
            val a = after.getValue(id)
            if (a != b && significant(b, a)) Change(id, b, a) else null
        }

    /**
     * Le relevé des durées appliquées aux tâches à faire sans estimation saisie, au temps [now] : ce que la veille compare d'un
     * changement de l'historique à l'autre. Vide si le réglage `learning.notifyChanges` est coupé.
     */
    fun applied(snapshot: KairosSnapshot, now: Instant, timeZone: TimeZone?): Map<Long, Int> {
        val learning = snapshot.settings.learning ?: LearningSettings()
        if (!learning.enabled || learning.notifyChanges == false) return emptyMap()
        return DurationEstimator.appliedMinutes(DurationEstimator.estimates(snapshot, now, timeZone))
    }

    /** Une durée saisie que le modèle conteste : la durée saisie [typed] et l'estimation fiable du modèle. */
    data class Proposal(val taskId: Long, val typed: Int, val estimate: DurationEstimator.Estimate)

    /**
     * Les tâches à faire avec une durée saisie dont l'estimation **fiable du modèle** (la chaîne interrogée sur la tâche sans sa
     * durée) s'en écarte nettement. Série et titre ne proposent rien. Écart décroissant, puis `taskId`.
     */
    fun proposals(snapshot: KairosSnapshot, now: Instant, timeZone: TimeZone?): List<Proposal> {
        val learning = snapshot.settings.learning ?: LearningSettings()
        if (!learning.enabled || learning.model == false) return emptyList()
        val typed = snapshot.tasks.filter { it.status == TaskStatus.TODO && it.origin == null && (it.estimatedMinutes ?: 0) > 0 }
        if (typed.isEmpty()) return emptyList()
        val samples = DurationEstimator.samples(
            snapshot.tasks, TimeTracking.spentMinutesByTask(snapshot.workSessions, now, snapshot.tasks),
            DurationEstimator.firstStarts(snapshot.workSessions, timeZone),
        )
        return typed.mapNotNull { proposal(it, samples, snapshot) }
            .sortedWith(compareByDescending<Proposal> { abs(it.estimate.minutes - it.typed) }.thenBy { it.taskId })
    }

    /** La proposition pour une seule tâche (fiche), ou `null`. */
    fun proposalFor(task: Task, snapshot: KairosSnapshot, now: Instant, timeZone: TimeZone?): Proposal? {
        val learning = snapshot.settings.learning ?: LearningSettings()
        if (!learning.enabled || learning.model == false) return null
        if (task.status != TaskStatus.TODO || task.origin != null || (task.estimatedMinutes ?: 0) <= 0) return null
        val samples = DurationEstimator.samples(
            snapshot.tasks, TimeTracking.spentMinutesByTask(snapshot.workSessions, now, snapshot.tasks),
            DurationEstimator.firstStarts(snapshot.workSessions, timeZone),
        )
        return proposal(task, samples, snapshot)
    }

    private fun proposal(task: Task, samples: List<DurationEstimator.Sample>, snapshot: KairosSnapshot): Proposal? {
        val typed = task.estimatedMinutes ?: return null
        val e = DurationEstimator.estimate(task.copy(estimatedMinutes = null), samples, snapshot.settings) ?: return null
        if (!e.reliable || e.source != DurationEstimator.Source.MODEL || !significant(typed, e.minutes)) return null
        return Proposal(task.id, typed, e)
    }
}
