package com.skohscripts.kairos.core.team.forecast

import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.stats.TaskStats.Calibration
import com.skohscripts.kairos.core.team.Capacity
import com.skohscripts.kairos.core.team.Effort
import com.skohscripts.kairos.core.team.TeamBoard
import com.skohscripts.kairos.core.team.TeamMembers
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Instant

/** D'où viennent les données d'une prévision ; sert à dire « historique : n tâches sur 12 semaines » ou « hypothèse par défaut ». */
enum class DataSourceKind {
    /** L'historique réel de l'équipe, au moins le minimum d'échantillons. */
    HISTORY,

    /** Un peu d'historique, sous le minimum : mêlé à l'hypothèse par défaut (peu fiable). */
    MIXED,

    /** Aucun historique : l'hypothèse par défaut seule (peu fiable). */
    DEFAULT,
}

/**
 * La source d'une donnée de prévision et son effectif, pour l'affichage honnête
 * (docs/spec/equipe-simulation.md § Honnêteté). [samples] : le nombre d'échantillons
 * de l'historique (tâches faites avec un effort de base et un temps passé ; ou, pour
 * les débits, tâches d'équipe finies sur la fenêtre) ; [activeWeeks] : semaines de la
 * fenêtre ayant au moins une tâche finie (débits seulement, 0 sinon) ; [minimum] :
 * seuil de fiabilité. [reliable] est faux sous le minimum : l'écran écrit « peu fiable ».
 */
data class SourceInfo(
    val kind: DataSourceKind,
    val samples: Int,
    val windowWeeks: Int,
    val minimum: Int,
    val activeWeeks: Int = 0,
) {
    val reliable: Boolean get() = kind == DataSourceKind.HISTORY
}

/**
 * Données d'entrée des prévisions, tirées de l'historique de l'équipe
 * (docs/spec/equipe-simulation.md § Données). Pur : le jour, l'instant, le fuseau
 * et les réglages sont des paramètres de [build]. Les listes d'échantillons sont
 * **triées par valeur** : le résultat ne dépend ni de l'ordre des tâches ni de
 * leurs identifiants.
 *
 * - [errorFactors] : rapport temps réel / effort de base des tâches d'équipe faites
 *   dans la fenêtre, bornés à [[MIN_ERROR_FACTOR], [MAX_ERROR_FACTOR]] ;
 * - [teamThroughput] / [memberThroughput] : tâches d'équipe finies par semaine
 *   (lundi-dimanche) sur les [windowWeeks] dernières semaines **complètes**, du
 *   plus ancien au plus récent, **zéros compris** ;
 * - [capacityFactors] : par membre, rapport heures réelles / capacité prévue des
 *   semaines passées, bornés à [[MIN_CAPACITY_FACTOR], [MAX_CAPACITY_FACTOR]] ; absent
 *   (ou vide) = facteur 1 (pas d'aléa), faute d'au moins [minSamples] semaines.
 */
data class ForecastData(
    val windowWeeks: Int = TeamSettings.DEFAULT_HISTORY_WEEKS,
    val minSamples: Int = TeamSettings.DEFAULT_MIN_SAMPLES,
    val errorFactors: List<Double> = emptyList(),
    val teamThroughput: List<Int> = emptyList(),
    val memberThroughput: Map<Long, List<Int>> = emptyMap(),
    val capacityFactors: Map<Long, List<Double>> = emptyMap(),
) {
    /** Source des facteurs d'erreur d'estimation (modèle par effort). */
    val estimationSource: SourceInfo
        get() {
            val n = errorFactors.size
            val kind = when {
                n >= minSamples -> DataSourceKind.HISTORY
                n > 0 -> DataSourceKind.MIXED
                else -> DataSourceKind.DEFAULT
            }
            return SourceInfo(kind, n, windowWeeks, minSamples)
        }

    /** Débits hebdomadaires de l'équipe, ou de [memberId] s'il est donné (liste vide si le membre n'a rien fini). */
    fun throughputOf(memberId: Long? = null): List<Int> =
        if (memberId == null) teamThroughput else memberThroughput[memberId].orEmpty()

    /** Le modèle par débit est disponible : au moins [MIN_THROUGHPUT_WEEKS] semaines avec une tâche finie. */
    fun throughputAvailable(memberId: Long? = null): Boolean = activeWeeks(memberId) >= MIN_THROUGHPUT_WEEKS

    private fun activeWeeks(memberId: Long?): Int = throughputOf(memberId).count { it > 0 }

    /**
     * Source des débits. [SourceInfo.samples] : tâches finies sur la fenêtre ;
     * fiable si les semaines actives atteignent `min(minSamples, windowWeeks)`
     * (une fenêtre plus courte que le minimum ne rendrait jamais fiable).
     */
    fun throughputSource(memberId: Long? = null): SourceInfo {
        val series = throughputOf(memberId)
        val active = series.count { it > 0 }
        val kind = when {
            active >= minOf(minSamples, windowWeeks) && active >= MIN_THROUGHPUT_WEEKS -> DataSourceKind.HISTORY
            active > 0 -> DataSourceKind.MIXED
            else -> DataSourceKind.DEFAULT
        }
        return SourceInfo(kind, series.sum(), windowWeeks, minSamples, active)
    }

    /**
     * Facteur d'erreur d'estimation tiré avec [rng]. Au moins [minSamples] facteurs :
     * rééchantillonnage de l'historique (bootstrap). Moins : on tire dans l'historique
     * avec la probabilité n / [minSamples], dans la loi triangulaire par défaut
     * (0,8 ; 1 ; 2) sinon, pour passer en douceur de l'une à l'autre ; sans
     * historique, la loi seule.
     */
    fun errorFactor(rng: Random): Double {
        val n = errorFactors.size
        val u = rng.nextDouble()
        if (n > 0 && (n >= minSamples || u * minSamples < n)) return errorFactors[rng.nextInt(n)]
        return triangular(rng.nextDouble())
    }

    /** Facteur de capacité de la semaine de [memberId] tiré avec [rng] ; 1 sans historique suffisant (aucun hasard consommé). */
    fun capacityFactor(memberId: Long, rng: Random): Double {
        val list = capacityFactors[memberId]
        if (list.isNullOrEmpty()) return 1.0
        return list[rng.nextInt(list.size)]
    }

    /** Le membre a un historique de capacité (donc un aléa de capacité). */
    fun hasCapacityHistory(memberId: Long): Boolean = capacityFactors[memberId].orEmpty().isNotEmpty()

    companion object {
        /** Bornes des facteurs d'erreur : une saisie aberrante (une tâche chronométrée une nuit) ne domine pas. */
        const val MIN_ERROR_FACTOR = 0.2
        const val MAX_ERROR_FACTOR = 5.0

        /** Bornes des facteurs de capacité (heures réelles / capacité prévue). */
        const val MIN_CAPACITY_FACTOR = 0.3
        const val MAX_CAPACITY_FACTOR = 1.5

        /** Loi par défaut quand l'historique manque : triangulaire (minimum, mode, maximum). */
        const val DEFAULT_MIN = 0.8
        const val DEFAULT_MODE = 1.0
        const val DEFAULT_MAX = 2.0

        /** Semaines (avec au moins une tâche finie) sous lesquelles le modèle par débit est indisponible. */
        const val MIN_THROUGHPUT_WEEKS = 4

        /**
         * Loi triangulaire par défaut, par inversion de la fonction de répartition de
         * [u] ∈ [0, 1[ (une racine carrée : exacte sur toutes les cibles).
         */
        fun triangular(u: Double): Double {
            val a = DEFAULT_MIN
            val b = DEFAULT_MAX
            val c = DEFAULT_MODE
            val pivot = (c - a) / (b - a)
            return if (u < pivot) a + sqrt(u * (b - a) * (c - a)) else b - sqrt((1.0 - u) * (b - a) * (b - c))
        }

        /**
         * Construit les données depuis [snapshot] (la base complète, **réelle** : un
         * scénario ne change pas l'historique). [calibration] : `Effort.teamCalibration`,
         * calculée si absente. Fenêtre : [TeamSettings.historyWeeks] semaines complètes
         * avant la semaine de [day] pour les débits et les capacités ; depuis le
         * lundi qui ouvre cette fenêtre jusqu'à [day] pour les facteurs d'erreur.
         *
         * Facteurs d'erreur : tâches d'équipe faites (jour de fin : `TeamBoard.doneOn`)
         * ayant un effort de base (`Effort.base`, sources estimation, calibrée ou
         * points) et un temps passé > 0 (sessions + temps saisi à la main + temps rapporté
         * par le membre, `Task.reportedMinutes`, via `TimeTracking.spentMinutesByTask`).
         *
         * Facteurs de capacité d'un membre actif : pour chaque semaine passée où sa
         * capacité prévue (`Capacity`, absences et fériés compris) est > 0 et où des
         * heures ont été chronométrées sur ses tâches (> 0), heures / capacité. Le temps rapporté
         * (un total sans date) n'y entre pas : seules les sessions datent leurs heures. Une
         * semaine sans heure enregistrée n'est pas lue comme « zéro travail » (rien
         * n'oblige à chronométrer) : elle n'est pas un échantillon.
         */
        fun build(
            snapshot: KairosSnapshot,
            day: LocalDate,
            now: Instant,
            timeZone: TimeZone,
            calibration: List<Calibration>? = null,
        ): ForecastData {
            val settings = snapshot.settings
            val team = settings.team ?: TeamSettings()
            val weeks = team.historyWeeks.coerceAtLeast(1)
            val minSamples = team.minSamples.coerceAtLeast(1)
            val monday = day.minus(DatePeriod(days = Workdays.weekday(day)))
            val windowStart = monday.minus(DatePeriod(days = 7 * weeks))
            val cal = calibration ?: Effort.teamCalibration(snapshot, now)

            val eventsByTask = snapshot.teamEvents.groupBy { it.taskId }
            val done = snapshot.tasks.filter { it.space == TaskSpace.TEAM && it.status == TaskStatus.DONE }
            val spent = TimeTracking.spentMinutesByTask(snapshot.workSessions, now, done)

            val factors = ArrayList<Double>()
            val teamCounts = IntArray(weeks)
            val memberCounts = HashMap<Long, IntArray>()
            for (t in done) {
                val doneOn = TeamBoard.doneOn(t, eventsByTask[t.id].orEmpty(), timeZone)
                if (doneOn < windowStart || doneOn > day) continue
                val base = Effort.base(t, cal, settings).first
                val minutes = spent[t.id] ?: 0
                if (base != null && base > 0.0 && minutes > 0) {
                    factors += (minutes / 60.0 / base).coerceIn(MIN_ERROR_FACTOR, MAX_ERROR_FACTOR)
                }
                if (doneOn < monday) {
                    val week = windowStart.daysUntil(doneOn) / 7
                    teamCounts[week]++
                    t.assigneeId?.let { memberCounts.getOrPut(it) { IntArray(weeks) }[week]++ }
                }
            }

            // Capacité réelle : heures chronométrées sur les tâches d'équipe d'un membre, par semaine passée.
            val assignee = snapshot.tasks.filter { it.space == TaskSpace.TEAM && it.assigneeId != null }.associate { it.id to it.assigneeId!! }
            val realHours = HashMap<Long, DoubleArray>()
            for (s in snapshot.workSessions) {
                val owner = assignee[s.taskId] ?: continue
                val started = s.startedAt.toLocalDateTime(timeZone).date
                if (started < windowStart || started >= monday) continue
                val week = windowStart.daysUntil(started) / 7
                realHours.getOrPut(owner) { DoubleArray(weeks) }[week] += TimeTracking.sessionMinutes(s, now) / 60.0
            }
            val holidays = Workdays.buildHolidays(windowStart.year..day.year, settings.holidaysFr, settings.extraHolidays)
            val capacity = LinkedHashMap<Long, List<Double>>()
            for (m in TeamMembers.active(snapshot.members)) {
                val real = realHours[m.id] ?: continue
                val mine = snapshot.absences.filter { it.memberId == m.id }
                val samples = ArrayList<Double>()
                for (w in 0 until weeks) {
                    val start = windowStart.plus(DatePeriod(days = 7 * w))
                    val planned = Capacity.overRange(m, start, start.plus(DatePeriod(days = 6)), holidays, mine, settings)
                    if (planned > 0.0 && real[w] > 0.0) samples += (real[w] / planned).coerceIn(MIN_CAPACITY_FACTOR, MAX_CAPACITY_FACTOR)
                }
                if (samples.size >= minSamples) capacity[m.id] = samples.sorted()
            }

            return ForecastData(
                windowWeeks = weeks,
                minSamples = minSamples,
                errorFactors = factors.sorted(),
                teamThroughput = teamCounts.toList(),
                memberThroughput = memberCounts.entries.sortedBy { it.key }.associate { it.key to it.value.toList() },
                capacityFactors = capacity,
            )
        }
    }
}
