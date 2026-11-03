package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.HistoryEstimates
import com.skohscripts.kairos.core.model.Settings
import kotlinx.datetime.LocalDateTime

/**
 * Rythme selon le jour et la tranche de la journée (docs/spec/estimateur-modele.md § Rythme, besoin 11) : à partir des séances
 * chronométrées des tâches faites, un **facteur de correction** par jour de la semaine et par tranche (matin, après-midi,
 * soir), retenu seulement s'il repose sur assez de rapports, s'écarte nettement de 1 et améliore l'erreur sur son groupe.
 */
object Rhythm {
    /** Tranche de la journée de la première séance : matin avant 12 h, après-midi de 12 à 17 h, soir ensuite. */
    enum class Band { MORNING, AFTERNOON, EVENING }

    fun band(hour: Int): Band = when {
        hour < 12 -> Band.MORNING
        hour < 17 -> Band.AFTERNOON
        else -> Band.EVENING
    }

    /** Jour ISO (1 = lundi … 7 = dimanche) et tranche d'un instant local. */
    fun of(local: LocalDateTime): Pair<Int, Band> = local.date.dayOfWeek.ordinal + 1 to band(local.hour)

    const val MIN_RATIOS = 8
    const val MIN_EFFECT = 0.1
    const val MIN_FACTOR = 0.5
    const val MAX_FACTOR = 2.0
    const val MIN_GAIN = 0.95

    /** Les facteurs retenus : par jour ISO (1 à 7) et par tranche ; absents quand non retenus. */
    data class Factors(val weekdays: Map<Int, Double>, val bands: Map<Band, Double>) {
        val isEmpty: Boolean get() = weekdays.isEmpty() && bands.isEmpty()

        companion object {
            val NONE = Factors(emptyMap(), emptyMap())
        }
    }

    /**
     * Les facteurs du corpus ajusté [fit] : pour chaque échantillon qui a un jour, son rapport réel / attendu, où `attendu` est
     * l'estimation des voisins **sans lui** (base suffisante seulement) ; puis, par groupe, la médiane basse des rapports,
     * bornée, retenue si le groupe compte au moins [MIN_RATIOS] rapports, si elle s'écarte d'au moins [MIN_EFFECT] de 1 et si
     * l'erreur médiane de `attendu × f` est au plus [MIN_GAIN] fois celle d'`attendu` sur ce groupe.
     */
    fun learn(fit: ModelEstimator.Fit, settings: Settings): Factors {
        val samples = fit.index.samples
        class Point(val weekday: Int, val band: Band, val expected: Int, val real: Int)
        val points = ArrayList<Point>()
        for (i in samples.indices) {
            val s = samples[i]
            val weekday = s.weekday ?: continue
            val band = s.band ?: continue
            val expected = ModelEstimator.predict(fit, ModelEstimator.Features.of(s), settings, skip = i)?.takeIf { it.sufficient } ?: continue
            points.add(Point(weekday, band, expected.minutes, s.minutes))
        }
        fun factor(group: List<Point>): Double? {
            if (group.size < MIN_RATIOS) return null
            val f = ModelGate.lowMedian(group.map { it.real.toDouble() / it.expected }).coerceIn(MIN_FACTOR, MAX_FACTOR)
            if (f in (1.0 - MIN_EFFECT)..(1.0 + MIN_EFFECT)) return null
            val without = ModelGate.lowMedian(group.map { ModelGate.error(it.expected, it.real) })
            // Même arrondi et même borne que la production (multiple de 5, fenêtre de travail).
            val cap = HistoryEstimates.cap(settings)
            val with = ModelGate.lowMedian(group.map { ModelGate.error(HistoryEstimates.roundToFive(it.expected * f).coerceIn(5, cap), it.real) })
            return f.takeIf { with <= MIN_GAIN * without }
        }
        val weekdays = LinkedHashMap<Int, Double>()
        for (d in 1..7) factor(points.filter { it.weekday == d })?.let { weekdays[d] = it }
        val bands = LinkedHashMap<Band, Double>()
        for (b in Band.entries) factor(points.filter { it.band == b })?.let { bands[b] = it }
        return Factors(weekdays, bands)
    }

    /** Ce qui s'applique à une tâche : le facteur de son jour (planifiée) et de sa tranche (heure fixe), 1 sinon. */
    data class Applied(val weekday: Int?, val weekdayFactor: Double?, val band: Band?, val bandFactor: Double?) {
        /** Le produit des deux facteurs, borné comme chacun d'eux à [[MIN_FACTOR] ; [MAX_FACTOR]]. */
        val factor: Double get() = ((weekdayFactor ?: 1.0) * (bandFactor ?: 1.0)).coerceIn(MIN_FACTOR, MAX_FACTOR)
        val isEmpty: Boolean get() = weekdayFactor == null && bandFactor == null
    }

    fun applied(factors: Factors, weekday: Int?, band: Band?): Applied {
        val wf = weekday?.let { factors.weekdays[it] }
        val bf = band?.let { factors.bands[it] }
        return Applied(weekday.takeIf { wf != null }, wf, band.takeIf { bf != null }, bf)
    }
}
