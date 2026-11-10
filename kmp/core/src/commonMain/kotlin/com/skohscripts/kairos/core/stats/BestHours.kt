package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.engine.Affinity
import com.skohscripts.kairos.core.engine.AffinitySource
import com.skohscripts.kairos.core.model.BestHoursSettings
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.engine.minutesBetween
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.exp
import kotlin.math.ln
import kotlin.time.Instant

/**
 * Quand on travaille vraiment, par type de tâche (docs/spec/planning-personnalise.md § Lot C, étapes C1 et C2). Pur : sessions, tâches,
 * instant, fuseau et réglages en paramètres. [measure] **observe seulement** (carte Statistiques) ; [affinity] en tire, si l'utilisateur l'a activé
 * (C2, désactivé par défaut), la préférence du planning.
 *
 * Les sessions du chrono sont la donnée : le chrono peut être lancé sur n'importe quelle tâche, depuis n'importe quel classement, donc
 * l'heure où l'on a vraiment travaillé est indépendante de l'heure où Kairos l'avait suggérée. Les minutes de chaque session sont
 * réparties entre les tranches de [BUCKET_HOURS] heures qu'elle traverse (heure locale du [TimeZone] passé) ; les sessions de moins de
 * [MIN_SESSION_MINUTES] minutes (démarrage-arrêt accidentel) sont ignorées ; les plus récentes pèsent plus que les plus anciennes de la
 * fenêtre (de 1 à 1/2). La part du type par tranche est comparée à celle de **toutes** les tâches (« lift ») : « je travaille à 10 h »
 * ne doit pas passer pour « les documents, c'est à 10 h ».
 *
 * v2 (issue #105) : l'**efficacité** par tranche, estimé ÷ réel des tâches terminées comparé à celui de tout le type, que placer une
 * tâche à une heure ne renforce pas (y travailler plus souvent n'y fait pas finir plus vite).
 */
object BestHours {
    /** Largeur d'une tranche en heures, et leur nombre dans la journée. */
    const val BUCKET_HOURS = 2
    const val BUCKETS = 24 / BUCKET_HOURS

    /** Une session plus courte n'est pas comptée (démarrage-arrêt accidentel). */
    const val MIN_SESSION_MINUTES = 5

    /** Sous ces effectifs (sessions retenues **et** minutes réelles), le profil d'un type est affiché mais « peu fiable ». */
    const val MIN_RELIABLE_SESSIONS = 5
    const val MIN_RELIABLE_MINUTES = 120

    /** Une tranche ne devient la « tranche de pointe » d'un type que si elle porte au moins cette part de son temps (pondéré). */
    private const val PEAK_MIN_SHARE = 0.2

    /**
     * Le profil d'un type ([type] vide : « sans type »). [share] : part du temps pondéré du type par tranche (somme 1) ; [lift] : cette part
     * divisée par la part de toutes les tâches dans la même tranche (> 1 : le type y est sur-représenté). [peak] : l'indice de la tranche au
     * plus fort lift parmi celles qui portent assez du temps du type, ou `null` quand le type se répartit sans pointe. [sessions] et
     * [minutes] : les effectifs retenus. [efficiency] (v2) : par tranche, l'efficacité relative (> 1 : on y finit plus vite que
     * d'habitude pour ce type), `null` sans assez de tâches ; [efficiencyTasks] : les tâches terminées et estimées comptées ; [fastest] :
     * la tranche la plus efficace si elle l'est nettement ([EFFICIENCY_NOTABLE]).
     */
    data class Profile(
        val type: String,
        val sessions: Int,
        val minutes: Int,
        val share: List<Double>,
        val lift: List<Double>,
        val peak: Int?,
        val reliable: Boolean,
        val efficiency: List<Double?> = List(BUCKETS) { null },
        val efficiencyTasks: Int = 0,
        val fastest: Int? = null,
    ) {
        /** Le type a assez de tâches terminées et estimées pour mesurer son efficacité. */
        val efficiencyReliable: Boolean get() = efficiencyTasks >= MIN_EFFICIENCY_TASKS
    }

    /** Les profils, du type le plus chronométré au moins chronométré, et la répartition [global] de tout le temps retenu. */
    data class Result(val profiles: List<Profile>, val global: List<Double>, val sessions: Int) {
        val hasData: Boolean get() = profiles.isNotEmpty()
    }

    /** Efficacité (v2) : tâches terminées et estimées nécessaires pour un type, tâches-équivalents pour une tranche. */
    const val MIN_EFFICIENCY_TASKS = 5
    const val MIN_BUCKET_TASKS = 3.0

    /** Une tranche n'est « la plus efficace » que si l'on y finit au moins 10 % plus vite que d'habitude. */
    const val EFFICIENCY_NOTABLE = 1.1

    /** Efficacité relative à laquelle le gain du planning est plein (50 % plus vite). */
    const val EFFICIENCY_FULL_GAIN = 1.5

    /** Bornes du rapport estimé ÷ réel d'une tâche : un chrono oublié ou une estimation fantaisiste ne pèse pas des centaines de fois. */
    private const val RATIO_MIN = 0.25
    private const val RATIO_MAX = 4.0

    /** Les trois forces du réglage (0 Faible, 1 Moyenne, 2 Forte) : le diviseur de l'effort est `1 + force × gain`, gain ∈ [0, 1]. */
    private val STRENGTHS = doubleArrayOf(0.15, 0.3, 0.5)

    /** La force numérique du niveau [level] du réglage (un niveau hors de 0..2 est ramené dans les bornes). */
    fun strengthOf(level: Int): Double = STRENGTHS[level.coerceIn(0, STRENGTHS.lastIndex)]

    /** Une tranche ne compte pour la détection que si elle porte au moins cette part du temps du type (pas de pointe sur du bruit). */
    private const val AFFINITY_MIN_SHARE = 0.1

    /**
     * L'affinité du réglage C2 : `null` si [BestHoursSettings.enabled] est faux (rien ne change). Détection : le gain d'une tranche est son
     * « lift » moins 1, borné à [0, 1], pour les seuls types **fiables** et les tranches qui portent assez de leur temps ; les heures
     * préférées des réglages priment sur la détection.
     */
    fun affinity(settings: Settings, result: Result): Affinity? {
        val best = settings.schedule?.bestHours?.takeIf { it.enabled } ?: return null
        val frequency = HashMap<String, List<Double>>()
        val efficiency = HashMap<String, List<Double>>()
        for (p in result.profiles) {
            if (p.type.isEmpty()) continue
            if (p.reliable) {
                frequency[p.type.lowercase()] = List(BUCKETS) { b -> if (p.share[b] >= AFFINITY_MIN_SHARE) (p.lift[b] - 1.0).coerceIn(0.0, 1.0) else 0.0 }
            }
            if (p.efficiencyReliable) efficiency[p.type.lowercase()] = List(BUCKETS) { b -> efficiencyGain(p.efficiency[b]) }
        }
        val (detected, source) = when (BestHoursSettings.normalizeMeasure(best.measure)) {
            BestHoursSettings.MEASURE_EFFICIENCY -> efficiency to AffinitySource.EFFICIENT
            // Les deux, à parts égales : une efficacité encore inconnue (gains nuls) ne rend pas la fréquence muette.
            BestHoursSettings.MEASURE_BOTH -> (frequency.keys + efficiency.keys).associateWith { type ->
                val f = frequency[type]
                val e = efficiency[type]
                List(BUCKETS) { b -> ((f?.get(b) ?: 0.0) + (e?.get(b) ?: 0.0)) / 2 }
            } to AffinitySource.COMBINED
            else -> frequency to AffinitySource.DETECTED
        }
        val preferred = HashMap<String, Int>()
        for ((type, hour) in best.preferred) if (hour in 0..23 && type.isNotBlank()) preferred[type.trim().lowercase()] = hour
        return Affinity(strengthOf(best.strength), detected, preferred, BUCKET_HOURS, source)
    }

    /** Gain d'une efficacité relative : `(relative − 1)` ramené à [0, 1], plein à [EFFICIENCY_FULL_GAIN] ; 0 sans valeur. */
    fun efficiencyGain(relative: Double?): Double =
        if (relative == null) 0.0 else ((relative - 1.0) / (EFFICIENCY_FULL_GAIN - 1.0)).coerceIn(0.0, 1.0)

    /** Début de la tranche [bucket], en heures (0, 2, 4…). */
    fun startHour(bucket: Int): Int = bucket * BUCKET_HOURS

    /** Les tranches de l'heure locale `hour` (0 à 23). */
    fun bucketOf(hour: Int): Int = hour / BUCKET_HOURS

    /**
     * Mesure sur la fenêtre des Statistiques (`statsWindowWeeks` semaines, jusqu'à aujourd'hui) : [tasks] donne le type de chaque session.
     */
    fun measure(tasks: List<Task>, sessions: List<WorkSession>, now: Instant, settings: Settings, timeZone: TimeZone): Result {
        val today = now.toLocalDateTime(timeZone).date
        val weeks = maxOf(1, settings.statsWindowWeeks)
        val windowStart = TaskStats.monday(today).minus(DatePeriod(days = 7 * (weeks - 1)))
        val windowDays = maxOf(1, windowStart.daysUntil(today) + 1)
        val typeById = tasks.associate { it.id to it.taskType.trim() }

        val weighted = LinkedHashMap<String, DoubleArray>()
        val sessionCount = LinkedHashMap<String, Int>()
        val minutesCount = LinkedHashMap<String, Int>()
        // Minutes retenues de chaque tâche par tranche, sans pondération récente : l'efficacité les répartit (v2).
        val taskBuckets = HashMap<Long, DoubleArray>()
        for (s in TimeTracking.sessionsInRange(TimeTracking.withoutOverlaps(sessions, now), windowStart, today, timeZone)) {
            val minutes = TimeTracking.sessionMinutes(s, now)
            if (minutes < MIN_SESSION_MINUTES) continue
            val start = s.startedAt.toLocalDateTime(timeZone)
            val end = (s.endedAt ?: now).toLocalDateTime(timeZone)
            // Plus récente = plus lourde : de 1 (aujourd'hui) à 1/2 (premier jour de la fenêtre).
            val weight = 1.0 - 0.5 * minOf(1.0, start.date.daysUntil(today).toDouble() / windowDays)
            val type = typeById[s.taskId] ?: ""
            val bins = weighted.getOrPut(type) { DoubleArray(BUCKETS) }
            val split = splitIntoBuckets(start, end)
            split.forEachIndexed { bucket, m -> bins[bucket] += m * weight }
            val own = taskBuckets.getOrPut(s.taskId) { DoubleArray(BUCKETS) }
            split.forEachIndexed { bucket, m -> own[bucket] += m }
            sessionCount[type] = (sessionCount[type] ?: 0) + 1
            minutesCount[type] = (minutesCount[type] ?: 0) + minutes
        }
        if (weighted.isEmpty()) return Result(emptyList(), List(BUCKETS) { 0.0 }, 0)

        val globalTotals = DoubleArray(BUCKETS)
        weighted.values.forEach { bins -> bins.forEachIndexed { i, v -> globalTotals[i] += v } }
        val globalSum = globalTotals.sum()
        val global = globalTotals.map { it / globalSum }

        val efficiency = efficiencyByType(tasks, sessions, taskBuckets, windowStart, now, timeZone)
        val profiles = weighted.map { (type, bins) ->
            val sum = bins.sum()
            val share = bins.map { it / sum }
            val lift = share.mapIndexed { i, s -> if (global[i] > 0) s / global[i] else 0.0 }
            val peak = share.indices.filter { share[it] >= PEAK_MIN_SHARE }.maxByOrNull { lift[it] }
            val sessionsKept = sessionCount.getValue(type)
            val minutesKept = minutesCount.getValue(type)
            val eff = efficiency[type]
            Profile(
                type, sessionsKept, minutesKept, share, lift, peak,
                reliable = sessionsKept >= MIN_RELIABLE_SESSIONS && minutesKept >= MIN_RELIABLE_MINUTES,
                efficiency = eff?.relative ?: List(BUCKETS) { null },
                efficiencyTasks = eff?.tasks ?: 0,
                fastest = eff?.relative?.let { r -> r.indices.filter { (r[it] ?: 0.0) >= EFFICIENCY_NOTABLE }.maxByOrNull { r[it]!! } },
            )
        }.sortedWith(compareByDescending<Profile> { it.minutes }.thenBy { it.type })
        return Result(profiles, global, sessionCount.values.sum())
    }

    private class TypeEfficiency(val tasks: Int, val relative: List<Double?>)

    /**
     * Efficacité par type (v2) : pour chaque tâche terminée dans la fenêtre, estimée et chronométrée, `ln(estimé ÷ réel)` (rapport borné
     * à [RATIO_MIN], [RATIO_MAX]) réparti entre les tranches selon la part de ses minutes retenues ([taskBuckets]). Par tranche, la
     * moyenne pondérée comparée à la moyenne du type (rapport de moyennes géométriques) : le biais d'estimation du type s'annule. Une
     * tranche sans [MIN_BUCKET_TASKS] tâches-équivalents, ou un type sans [MIN_EFFICIENCY_TASKS] tâches, n'a pas de valeur.
     */
    private fun efficiencyByType(
        tasks: List<Task>,
        sessions: List<WorkSession>,
        taskBuckets: Map<Long, DoubleArray>,
        windowStart: LocalDate,
        now: Instant,
        timeZone: TimeZone,
    ): Map<String, TypeEfficiency> {
        val spent = TimeTracking.spentMinutesByTask(sessions, now, tasks)
        val weights = HashMap<String, DoubleArray>()
        val logs = HashMap<String, DoubleArray>()
        val counts = HashMap<String, Int>()
        val totals = HashMap<String, Double>()
        for (t in tasks) {
            if (t.status != TaskStatus.DONE || t.updatedAt.toLocalDateTime(timeZone).date < windowStart) continue
            val estimate = t.estimatedMinutes ?: 0
            val real = spent[t.id] ?: 0
            val bins = taskBuckets[t.id] ?: continue
            val sum = bins.sum()
            if (estimate <= 0 || real <= 0 || sum <= 0) continue
            val log = ln((estimate.toDouble() / real).coerceIn(RATIO_MIN, RATIO_MAX))
            val type = t.taskType.trim()
            val w = weights.getOrPut(type) { DoubleArray(BUCKETS) }
            val l = logs.getOrPut(type) { DoubleArray(BUCKETS) }
            bins.forEachIndexed { b, m ->
                w[b] += m / sum
                l[b] += m / sum * log
            }
            counts[type] = (counts[type] ?: 0) + 1
            totals[type] = (totals[type] ?: 0.0) + log
        }
        return counts.mapValues { (type, count) ->
            val mean = totals.getValue(type) / count
            val w = weights.getValue(type)
            val l = logs.getValue(type)
            val relative = List(BUCKETS) { b ->
                if (count >= MIN_EFFICIENCY_TASKS && w[b] >= MIN_BUCKET_TASKS) exp(l[b] / w[b] - mean) else null
            }
            TypeEfficiency(count, relative)
        }
    }

    /** Les minutes de la session [start]..[end] (heure locale) réparties entre les tranches qu'elle traverse. */
    private fun splitIntoBuckets(start: LocalDateTime, end: LocalDateTime): DoubleArray {
        val out = DoubleArray(BUCKETS)
        var cursor = start
        while (cursor < end) {
            val bucket = bucketOf(cursor.hour)
            val boundaryHour = startHour(bucket) + BUCKET_HOURS
            val boundary = if (boundaryHour >= 24) cursor.date.plus(DatePeriod(days = 1)).atTime(0, 0) else cursor.date.atTime(boundaryHour, 0)
            val segmentEnd = minOf(end, boundary)
            out[bucket] += minutesBetween(cursor, segmentEnd).toDouble()
            cursor = segmentEnd
        }
        return out
    }
}
