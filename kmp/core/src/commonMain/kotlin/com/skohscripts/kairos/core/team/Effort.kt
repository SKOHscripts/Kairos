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

    /**
     * La durée **apprise** des occurrences passées de la tâche, en l'absence d'estimation (docs/spec/durees-apprises.md). Les calculs
     * d'équipe la lisent comme une estimation ([com.skohscripts.kairos.core.engine.DurationEstimator.withLearnedEstimates]) ; la
     * source n'est distinguée qu'à l'affichage de la fiche.
     */
    LEARNED,

    /**
     * La durée estimée **par le modèle** (docs/spec/estimateur-modele.md), lue comme [LEARNED] par les calculs ; seule la fiche
     * la distingue, avec son intervalle.
     */
    MODEL,

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

    /** Part de l'effort de base sous laquelle le temps passé ne fait jamais descendre l'effort restant. */
    const val MIN_REMAINING_SHARE = 0.10

    /**
     * Effort **restant** (docs/spec/equipe-moi.md § Historique et temps passé) : avancement déclaré > 0 → effort de
     * base × (1 − avancement) ; sinon, temps passé [spentMinutes] > 0 → effort − temps passé, sans descendre sous
     * [MIN_REMAINING_SHARE] de l'effort (une tâche ouverte n'est jamais finie) ; sinon l'effort. `null` si non estimée.
     */
    fun remaining(task: Task, calibration: List<Calibration>, settings: Settings, spentMinutes: Int = 0): Pair<Double?, EffortSource> {
        val (hours, source) = base(task, calibration, settings)
        if (hours == null) return null to source
        return left(hours, task, spentMinutes) to source
    }

    private fun left(hours: Double, task: Task, spentMinutes: Int): Double {
        val progress = (task.progressPercent ?: 0).coerceIn(0, 100)
        return when {
            progress > 0 -> hours * (100 - progress) / 100.0
            spentMinutes > 0 -> maxOf(hours - spentMinutes / 60.0, hours * MIN_REMAINING_SHARE)
            else -> hours
        }
    }

    /**
     * Effort restant tel que le plan de charge le pose : [hours] (avec la valeur
     * par défaut `defaultFibonacciPoints × hoursPerPoint` pour une tâche non
     * estimée, que le plan ne peut pas poser à zéro), sa [source] et
     * [unestimated] (vrai si la source est [EffortSource.NONE]).
     */
    data class Planned(val hours: Double, val source: EffortSource, val unestimated: Boolean)

    fun planned(task: Task, calibration: List<Calibration>, settings: Settings, spentMinutes: Int = 0): Planned {
        val (hours, source) = remaining(task, calibration, settings, spentMinutes)
        if (hours != null) return Planned(hours, source, unestimated = false)
        val fallback = settings.defaultFibonacciPoints * (settings.team ?: TeamSettings()).hoursPerPoint
        return Planned(left(fallback, task, spentMinutes), EffortSource.NONE, unestimated = true)
    }

    /**
     * Calibration de l'équipe : `TaskStats.fibonacciCalibration` sur les tâches
     * d'équipe **faites**, temps passé = sessions + temps saisi à la main + temps rapporté par le membre
     * (`TimeTracking.spentMinutesByTask`, `Task.reportedMinutes`). Une session ouverte court jusqu'à
     * [now]. Lit la vue de calcul (`TeamWork.view`) : les tâches perso faites de « moi » qui comptent y entrent.
     */
    fun teamCalibration(snapshot: KairosSnapshot, now: Instant): List<Calibration> {
        val done = TeamWork.view(snapshot).tasks.filter { it.space == TaskSpace.TEAM && it.status == TaskStatus.DONE }
        val spent = TimeTracking.spentMinutesByTask(snapshot.workSessions, now, done)
        return TaskStats.fibonacciCalibration(done, spent)
    }
}
