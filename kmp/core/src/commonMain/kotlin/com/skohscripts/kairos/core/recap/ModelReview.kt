package com.skohscripts.kairos.core.recap

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.engine.model.EstimateChanges
import com.skohscripts.kairos.core.engine.model.ModelCache
import com.skohscripts.kairos.core.engine.model.ModelGate
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.stats.TaskStats
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Ce que le modèle d'estimation a appris, vu depuis le bilan d'une période (docs/spec/bilan.md § Ce que Kairos apprend) :
 * où il en est, comment il a convergé semaine après semaine, et ce que la période lui a appris. Espace Perso seulement.
 */
object ModelReview {
    /** L'état du modèle à la fin d'une semaine ([date], un dimanche), vérifié avec ce qu'il savait alors. */
    data class Point(val date: LocalDate, val corpus: Int, val gate: ModelGate.Result)

    data class Review(
        /** `null` : durées apprises ou machine learning coupés dans les Réglages. */
        val status: DurationEstimator.Companion.ModelStatus?,
        /** Tâches de la période qui ont nourri le modèle (terminées, chronométrées). */
        val learnedInPeriod: Int,
        /** Tâches de la période parmi les dernières vérifiées par la garde, et celles tombées dans la fourchette calibrée. */
        val checked: Int,
        val inside: Int,
        /** Temps réel ÷ estimé des tâches terminées de la période (estimation et temps réel), `null` sans aucune. */
        val bias: TaskStats.EstimationBias?,
        /** Durées apprises **fiables** des tâches ouvertes sans estimation saisie, par origine. */
        val bySource: Map<DurationEstimator.Source, Int>,
        /** Estimations saisies que le modèle conteste (la revue des Réglages). */
        val contested: Int,
        /** Un point par fin de semaine, du plus ancien au plus récent. */
        val history: List<Point>,
    )

    /**
     * La base telle qu'elle était à [instant] pour le modèle : une tâche terminée après redevient « à faire », les sessions
     * commencées après disparaissent, une session à cheval s'arrête à [instant]. Le temps saisi à la main, sans date,
     * reste aux tâches terminées avant.
     */
    fun asOf(snapshot: KairosSnapshot, instant: Instant): KairosSnapshot = snapshot.copy(
        tasks = snapshot.tasks.map { t -> if (t.status == TaskStatus.DONE && t.updatedAt > instant) t.copy(status = TaskStatus.TODO) else t },
        workSessions = snapshot.workSessions.filter { it.startedAt < instant }
            .map { s -> if (s.endedAt == null || s.endedAt > instant) s.copy(endedAt = instant) else s },
    )

    fun build(snapshot: KairosSnapshot, start: LocalDate, end: LocalDate, now: Instant, timeZone: TimeZone, weeks: Int = 8): Review {
        val personal = snapshot.copy(tasks = snapshot.tasks.filter { it.space == TaskSpace.PERSONAL })
        val status = DurationEstimator.modelStatus(personal, now, timeZone = timeZone)
        val inPeriod = personal.tasks
            .filter { it.status == TaskStatus.DONE && it.updatedAt.toLocalDateTime(timeZone).date in start..end }
            .mapTo(HashSet()) { it.id }
        val samples = DurationEstimator.history(personal, now, timeZone).filter { it.space == TaskSpace.PERSONAL }
        val learned = samples.count { it.taskId in inPeriod }
        // Vérifiées : les candidats de la garde (chacun estimé par le modèle des seules tâches plus anciennes) de la période.
        var checked = 0
        var inside = 0
        if (status != null && learned > 0) {
            val corpus = samples.sortedBy { it.order }
            val stretch = status.gate.stretch
            for (c in ModelGate.candidates(corpus, personal.settings)) {
                val sample = corpus[c.index]
                if (sample.taskId !in inPeriod) continue
                val p = c.prediction?.takeIf { it.sufficient } ?: continue
                checked++
                if (ModelGate.score(p.minutes, p.low, p.high, sample.minutes) <= stretch) inside++
            }
        }
        val spent = TimeTracking.spentMinutesByTask(personal.workSessions, now, personal.tasks)
        val bias = TaskStats.estimationBias(personal.tasks.filter { it.id in inPeriod }, spent)
        val learning = personal.settings.learning ?: LearningSettings()
        val bySource = if (!learning.enabled) emptyMap() else DurationEstimator.estimates(personal, now, timeZone).values
            .filter { it.reliable }.groupingBy { it.source }.eachCount()
        val contested = EstimateChanges.proposals(personal, now, timeZone).size
        return Review(status, learned, checked, inside, bias, bySource, contested, if (status == null) emptyList() else history(personal, end, now, timeZone, weeks))
    }

    /** Un point par dimanche des [weeks] dernières semaines jusqu'à `min(end, aujourd'hui)`, reconstruit sans le futur. */
    fun history(snapshot: KairosSnapshot, end: LocalDate, now: Instant, timeZone: TimeZone, weeks: Int): List<Point> {
        val today = now.toLocalDateTime(timeZone).date
        val last = minOf(end, today)
        // Le dimanche de la semaine de `last` : le point le plus récent est l'état à la fin de cette semaine (ou maintenant).
        val sunday = TaskStats.monday(last).plus(DatePeriod(days = 6))
        return (weeks - 1 downTo 0).mapNotNull { back ->
            val day = sunday.minus(DatePeriod(days = 7 * back))
            val instant = minOf(day.plus(DatePeriod(days = 1)).atTime(LocalTime(0, 0)).toInstant(timeZone), now)
            // Un cache neuf par point : rien n'évince le cache partagé des estimations courantes.
            val status = DurationEstimator.modelStatus(asOf(snapshot, instant), instant, cache = ModelCache.NONE, timeZone = timeZone) ?: return@mapNotNull null
            Point(day, status.corpus, status.gate)
        }
    }
}
