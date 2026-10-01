package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.stats.TaskStats
import com.skohscripts.kairos.core.stats.TaskStats.Calibration
import kotlin.time.Instant

/** D'où vient l'effort d'une tâche (docs/spec/equipe-charge.md § Effort restant d'une tâche). */
enum class EffortSource {
    /** La durée estimée de la tâche. */
    ESTIMATE,

    /** La médiane réelle (fiable, n ≥ 3) des tâches d'équipe faites au même palier de points. */
    CALIBRATED,

    /** Points × heures par point des réglages. */
    POINTS_RATE,

    /** Ni durée ni points : **non estimée**, jamais comptée pour zéro. */
    NONE,
}

/**
 * Effort des tâches d'équipe, en **heures**. Pur : la calibration et les
 * réglages sont des paramètres.
 */
object Effort {
    /**
     * Effort de base de [task] et sa source, par ordre de préférence : durée
     * estimée ; médiane fiable du palier de points dans [calibration] ; points ×
     * `hoursPerPoint`. `(null, NONE)` si la tâche n'a ni durée ni points : la
     * valeur est inconnue, pas nulle.
     */
    fun base(task: Task, calibration: List<Calibration>, settings: Settings): Pair<Double?, EffortSource> {
        val minutes = task.estimatedMinutes
        if (minutes != null && minutes > 0) return minutes / 60.0 to EffortSource.ESTIMATE
        val points = task.fibonacciPoints
        if (points == null || points <= 0) return null to EffortSource.NONE
        val median = calibration.firstOrNull { it.key == points.toString() }
            ?.takeIf { it.reliable }?.medianMinutes?.takeIf { it > 0 }
        if (median != null) return median / 60.0 to EffortSource.CALIBRATED
        return points * (settings.team ?: TeamSettings()).hoursPerPoint to EffortSource.POINTS_RATE
    }

    /** Effort **restant** : l'effort de base × (1 − avancement) ; `null` si non estimée. */
    fun remaining(task: Task, calibration: List<Calibration>, settings: Settings): Pair<Double?, EffortSource> {
        val (hours, source) = base(task, calibration, settings)
        if (hours == null) return null to source
        val progress = (task.progressPercent ?: 0).coerceIn(0, 100)
        return hours * (100 - progress) / 100.0 to source
    }

    /**
     * Effort restant tel que le plan de charge le pose : [hours] (avec la valeur
     * par défaut `defaultFibonacciPoints × hoursPerPoint` pour une tâche non
     * estimée, que le plan ne peut pas poser à zéro), sa [source] et
     * [unestimated] (vrai si la source est [EffortSource.NONE]).
     */
    data class Planned(val hours: Double, val source: EffortSource, val unestimated: Boolean)

    fun planned(task: Task, calibration: List<Calibration>, settings: Settings): Planned {
        val (hours, source) = remaining(task, calibration, settings)
        if (hours != null) return Planned(hours, source, unestimated = false)
        val fallback = settings.defaultFibonacciPoints * (settings.team ?: TeamSettings()).hoursPerPoint
        val progress = (task.progressPercent ?: 0).coerceIn(0, 100)
        return Planned(fallback * (100 - progress) / 100.0, EffortSource.NONE, unestimated = true)
    }

    /**
     * Calibration de l'équipe : `TaskStats.fibonacciCalibration` sur les tâches
     * d'équipe **faites**, temps passé = sessions + temps saisi à la main
     * (`TimeTracking.spentMinutesByTask`). Une session ouverte court jusqu'à
     * [now].
     */
    fun teamCalibration(snapshot: KairosSnapshot, now: Instant): List<Calibration> {
        val done = snapshot.tasks.filter { it.space == TaskSpace.TEAM && it.status == TaskStatus.DONE }
        val spent = TimeTracking.spentMinutesByTask(snapshot.workSessions, now, done)
        return TaskStats.fibonacciCalibration(done, spent)
    }
}
