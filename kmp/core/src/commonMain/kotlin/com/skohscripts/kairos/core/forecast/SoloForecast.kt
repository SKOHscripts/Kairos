package com.skohscripts.kairos.core.forecast

import com.skohscripts.kairos.core.engine.Dependencies
import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.team.forecast.EffortSpread
import com.skohscripts.kairos.core.team.forecast.ForecastData
import com.skohscripts.kairos.core.team.forecast.ForecastRandom
import com.skohscripts.kairos.core.team.forecast.Percentiles
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Date de fin probable des tâches **perso** (docs/spec/previsions-taches.md § Étape C) : la file des tâches à faire, dans
 * l'ordre où Kairos les propose, rejouée jour après jour avec le temps **réellement chronométré** des huit dernières semaines
 * (jours sans travail compris) et des durées tirées dans leurs fourchettes. Pur : le jour, l'instant, le fuseau et la graine
 * sont des paramètres. Reproductible au bit près (`ForecastRandom`, `+ − × ÷` et `sqrt`). Ne lit aucune donnée d'équipe ;
 * du paquet `team.forecast`, seuls des calculs purs sont repris (hasard, percentiles, lois).
 */
object SoloForecast {
    /** Semaines complètes d'historique (du lundi au dimanche) avant la semaine courante. */
    const val HISTORY_WEEKS = 8

    /** Semaines avec du temps chronométré sous lesquelles on ne prévoit rien (rien n'est affiché). */
    const val MIN_ACTIVE_WEEKS = 4

    /** Facteurs réel ÷ estimé sous lesquels la loi par défaut complète l'historique (comme `teamMinSamples`). */
    const val MIN_FACTORS = 8

    /** Facteurs réel ÷ estimé retenus au plus, les plus récents. */
    const val MAX_FACTORS = 50

    /** Garde-fou : au-delà, la tâche est « hors horizon ». */
    const val HORIZON_DAYS = 365

    const val DEFAULT_RUNS = 500

    /** Part de la durée sous laquelle le temps déjà passé ne fait jamais descendre le restant (`Effort.MIN_REMAINING_SHARE`). */
    private const val MIN_REMAINING_SHARE = 0.10

    private const val OUT = Short.MAX_VALUE.toInt()

    /**
     * Ce que l'historique chronométré dit : minutes par jour des [HISTORY_WEEKS] semaines complètes avant la semaine de
     * [day] ([byWeekday] : huit valeurs par jour de la semaine, lundi = 0, zéros compris ; [weeks] : les huit totaux
     * hebdomadaires, du plus ancien au plus récent).
     */
    class Capacity(val byWeekday: List<IntArray>, val weeks: IntArray) {
        val activeWeeks: Int get() = weeks.count { it > 0 }
        val available: Boolean get() = activeWeeks >= MIN_ACTIVE_WEEKS

        /** Médiane basse des minutes d'un jour **travaillé** (au moins une minute chronométrée) ; 0 sans jour travaillé. */
        val workedDayMinutes: Int
            get() = byWeekday.flatMap { it.asList() }.filter { it > 0 }.sorted().let { if (it.isEmpty()) 0 else it[(it.size - 1) / 2] }

        /** Minutes d'une semaine au percentile [percent] (rang le plus proche sur les huit semaines). */
        fun weekMinutes(percent: Int): Int = Percentiles.nearestRank(weeks.sortedArray(), percent) ?: 0
    }

    /** Fin probable d'une tâche : une fois sur deux au plus tard le [p50], 85 fois sur 100 au plus tard le [p85] (`null` : hors horizon). */
    data class Outlook(
        val p50: LocalDate?,
        val p85: LocalDate?,
        /** Tirages où la tâche est finie le jour même, et où elle tient son échéance (`null` sans échéance), sur [Result.runs]. */
        val todayDraws: Int,
        val onTimeDraws: Int?,
    )

    /**
     * Le résultat, honnête : [runs] tirages, la [seed], l'historique lu ([capacity], [factorSamples] rapports réel ÷ estimé)
     * et [unestimated], le nombre de tâches de la file sans durée estimée ni apprise (posées à la durée par défaut).
     */
    class Result(
        val day: LocalDate,
        val runs: Int,
        val seed: Long,
        val capacity: Capacity,
        val factorSamples: Int,
        val unestimated: Int,
        val outlooks: Map<Long, Outlook>,
    )

    private fun personal(snapshot: KairosSnapshot): List<Task> = snapshot.tasks.filter { it.space == TaskSpace.PERSONAL }

    /** L'historique chronométré des tâches perso du [snapshot] (les sessions d'une tâche d'équipe ou disparue ne comptent pas). */
    fun capacity(snapshot: KairosSnapshot, day: LocalDate, now: Instant, timeZone: TimeZone): Capacity {
        val mine = personal(snapshot).mapTo(HashSet()) { it.id }
        val monday = day.minus(DatePeriod(days = Workdays.weekday(day)))
        val first = monday.minus(DatePeriod(days = 7 * HISTORY_WEEKS))
        val byWeekday = List(7) { IntArray(HISTORY_WEEKS) }
        val weeks = IntArray(HISTORY_WEEKS)
        for (s in TimeTracking.withoutOverlaps(snapshot.workSessions.filter { it.taskId in mine }, now)) {
            val offset = first.daysUntil(s.startedAt.toLocalDateTime(timeZone).date)
            if (offset < 0 || offset >= 7 * HISTORY_WEEKS) continue
            val minutes = TimeTracking.sessionMinutes(s, now)
            byWeekday[offset % 7][offset / 7] += minutes
            weeks[offset / 7] += minutes
        }
        return Capacity(byWeekday, weeks)
    }

    /**
     * Rapports réel ÷ estimé des tâches perso faites qui avaient une estimation saisie et du temps passé, les
     * [MAX_FACTORS] plus récentes par identifiant, bornés comme ceux de l'équipe ; triés (ordre d'entrée sans effet).
     */
    fun errorFactors(snapshot: KairosSnapshot, now: Instant): List<Double> {
        val spent = TimeTracking.spentMinutesByTask(snapshot.workSessions, now, snapshot.tasks)
        return personal(snapshot)
            .filter { it.status == TaskStatus.DONE && it.origin == null && (it.estimatedMinutes ?: 0) > 0 && (spent[it.id] ?: 0) > 0 }
            .sortedByDescending { it.id }.take(MAX_FACTORS)
            .map { (spent.getValue(it.id).toDouble() / it.estimatedMinutes!!).coerceIn(ForecastData.MIN_ERROR_FACTOR, ForecastData.MAX_ERROR_FACTOR) }
            .sorted()
    }

    /** Les dépendances entre tâches de [ids], sans cycle (un cycle bloquerait ses tâches pour toujours). */
    private fun edges(snapshot: KairosSnapshot, ids: Set<Long>): List<Dependencies.Edge> = Dependencies.acyclicEdges(
        snapshot.dependencies.map { Dependencies.Edge(blocked = it.taskId, blocker = it.blockerId) }.filter { it.blocked in ids && it.blocker in ids },
    )

    /**
     * La file : tâches perso à faire qui ne sont pas des mères (leur travail est celui de leurs filles), dans l'ordre de la
     * vue Jour (clé de tri, urgence héritée des tâches qu'elles bloquent), une tâche bloquée toujours après ses bloqueuses.
     */
    fun queue(snapshot: KairosSnapshot, day: LocalDate): List<Task> {
        val settings = snapshot.settings
        val all = personal(snapshot)
        val mothers = all.filter { it.status == TaskStatus.TODO }.mapNotNullTo(HashSet()) { it.parentId }
        val open = all.filter { it.status == TaskStatus.TODO && it.id !in mothers }
        val ids = open.mapTo(HashSet()) { it.id }
        val edges = edges(snapshot, ids)
        val own = open.associate { it.id to Scheduling.sortKey(it, day, settings) }
        val key = Dependencies.derivedUrgency(edges, own)
        val sorted = open.sortedWith(compareBy<Task> { key[it.id] ?: own.getValue(it.id) }.thenBy { it.id })
        // Tri topologique stable : on émet, dans l'ordre d'urgence, la première tâche dont toutes les bloqueuses sont émises.
        val blockers = edges.groupBy({ it.blocked }, { it.blocker })
        val emitted = LinkedHashSet<Long>()
        val out = ArrayList<Task>(sorted.size)
        while (out.size < sorted.size) {
            val next = sorted.first { it.id !in emitted && blockers[it.id].orEmpty().all { b -> b in emitted } }
            emitted += next.id
            out += next
        }
        return out
    }

    /**
     * La prévision au jour [day], ou `null` si l'historique chronométré est trop court ([Capacity.available]) ou la file vide.
     * [extra] : des tâches hypothétiques (identifiants négatifs) ajoutées à la file, pour « et si j'accepte ? » ; les tirages
     * des autres tâches et des jours sont **communs** aux deux calculs (même graine).
     */
    fun run(
        snapshot: KairosSnapshot,
        day: LocalDate,
        now: Instant,
        timeZone: TimeZone,
        seed: Long,
        runs: Int = DEFAULT_RUNS,
        extra: List<Task> = emptyList(),
    ): Result? {
        val capacity = capacity(snapshot, day, now, timeZone)
        if (!capacity.available) return null
        val all = if (extra.isEmpty()) snapshot else snapshot.copy(tasks = snapshot.tasks + extra)
        // Même ordre que la vue Jour : échéance implicite apprise du rythme, sans la tâche essayée (docs/spec/taches-qui-trainent.md).
        val learned = if (snapshot.settings.implicitDeadlineDays == null) Survival.lingerDays(snapshot, now, timeZone) else null
        val view = learned?.let { all.copy(settings = all.settings.copy(implicitDeadlineDays = it)) } ?: all
        val queue = queue(view, day)
        if (queue.isEmpty()) return null
        val settings = view.settings
        val n = queue.size
        val index = HashMap<Long, Int>(n * 2)
        queue.forEachIndexed { i, t -> index[t.id] = i }

        // Durées : la saisie, sinon la durée apprise fiable, sinon la durée par défaut ; moins le temps déjà passé.
        val estimates = DurationEstimator.estimates(view, now, timeZone)
        val applied = DurationEstimator.appliedMinutes(estimates)
        val spreads = DurationEstimator.spreads(estimates)
        val spent = TimeTracking.spentMinutesByTask(view.workSessions, now, view.tasks)
        val base = DoubleArray(n) { i ->
            val minutes = Scheduling.durationMinutes(queue[i], settings, applied).toDouble()
            maxOf(minutes - (spent[queue[i].id] ?: 0), minutes * MIN_REMAINING_SHARE)
        }
        val low = DoubleArray(n) { Double.NaN }
        val high = DoubleArray(n)
        for (i in 0 until n) spreads[queue[i].id]?.let { s -> EffortSpread.ratios(s.minutes, s.low, s.high).let { (l, h) -> low[i] = l; high[i] = h } }
        val unestimated = queue.count { (it.estimatedMinutes ?: 0) <= 0 && it.id !in applied }
        val factors = errorFactors(view, now)
        // Identité stable de chaque tâche pour les tirages communs : jamais sa position dans la file.
        val keys = LongArray(n) { ForecastRandom.mix(queue[it].id) }

        // Une tâche programmée plus tard n'est pas commencée avant ce jour (sauf si son échéance tombe avant).
        val earliest = IntArray(n) { i ->
            val t = queue[i]
            val scheduled = t.scheduledDate ?: return@IntArray 0
            val start = if (t.deadline != null && t.deadline < scheduled) t.deadline else scheduled
            maxOf(0, day.daysUntil(start))
        }
        val edges = edges(view, index.keys)
        val blockersOf = Array(n) { i -> edges.filter { it.blocked == queue[i].id }.map { index.getValue(it.blocker) }.toIntArray() }
        val deadline = IntArray(n) { queue[it].deadline?.let { d -> day.daysUntil(d) } ?: Int.MIN_VALUE }

        val holidays = Workdays.holidaysFor(day, settings.holidaysFr, settings.extraHolidays)
        val weekday = Workdays.weekday(day)
        // Un jour férié à venir se tire parmi les dimanches : c'est ce que l'historique sait d'un jour non travaillé.
        val pool = IntArray(HORIZON_DAYS + 1) { d -> if (day.plus(DatePeriod(days = d)) in holidays) 6 else (weekday + d) % 7 }
        val dayNumber = day.toEpochDays().toLong()
        val doneToday = TimeTracking.withoutOverlaps(
            TimeTracking.sessionsOnDay(view.workSessions, day, timeZone).filter { s -> view.tasks.any { it.id == s.taskId && it.space == TaskSpace.PERSONAL } }, now,
        ).sumOf { TimeTracking.sessionMinutes(it, now) }

        val ends = Array(n) { IntArray(runs) }
        val left = DoubleArray(n)
        val end = IntArray(n)
        for (r in 0 until runs) {
            val draw = ForecastRandom.drawBase(seed, r)
            for (i in 0 until n) {
                val rng = ForecastRandom.stream(draw, ForecastRandom.DOMAIN_ERROR, keys[i])
                val factor = if (!low[i].isNaN()) {
                    EffortSpread.factor(rng.nextDouble(), low[i], high[i])
                } else {
                    val u = rng.nextDouble()
                    val k = factors.size
                    if (k > 0 && (k >= MIN_FACTORS || u * MIN_FACTORS < k)) factors[rng.nextInt(k)] else ForecastData.triangular(rng.nextDouble())
                }
                left[i] = base[i] * factor
                end[i] = OUT
            }
            var remaining = n
            var d = 0
            while (remaining > 0 && d <= HORIZON_DAYS) {
                val history = capacity.byWeekday[pool[d]]
                var minutes = history[ForecastRandom.stream(draw, ForecastRandom.DOMAIN_CAPACITY, 0L, dayNumber + d).nextInt(history.size)].toDouble()
                if (d == 0) minutes = maxOf(0.0, minutes - doneToday)
                var i = 0
                while (minutes > 0.0 && i < n) {
                    // La première tâche ouverte, commençable ce jour, dont les bloqueuses sont finies.
                    if (end[i] == OUT && earliest[i] <= d && blockersOf[i].all { end[it] != OUT }) {
                        if (left[i] <= minutes) {
                            minutes -= left[i]
                            end[i] = d
                            remaining--
                        } else {
                            left[i] -= minutes
                            minutes = 0.0
                        }
                    }
                    i++
                }
                d++
            }
            for (i in 0 until n) ends[i][r] = end[i]
        }

        val outlooks = LinkedHashMap<Long, Outlook>(n * 2)
        for (i in 0 until n) {
            val own = ends[i]
            val today = own.count { it == 0 }
            val onTime = if (deadline[i] == Int.MIN_VALUE) null else own.count { it <= deadline[i] }
            own.sort()
            fun date(percent: Int): LocalDate? = Percentiles.nearestRank(own, percent)!!.let { if (it >= OUT) null else day.plus(DatePeriod(days = it)) }
            outlooks[queue[i].id] = Outlook(date(50), date(85), today, onTime)
        }
        return Result(day, runs, seed, capacity, factors.size, unestimated, outlooks)
    }

    /** Une échéance que la tâche acceptée met en danger : tenue [beforePerTen] fois sur 10 sans elle, [afterPerTen] avec. */
    data class Hurt(val taskId: Long, val title: String, val beforePerTen: Int, val afterPerTen: Int)

    /** « Et si j'accepte ? » : la fin probable de la tâche ([outlook]) et les échéances des autres qui en souffrent, les plus touchées d'abord. */
    class Impact(val runs: Int, val outlook: Outlook?, val hurt: List<Hurt>)

    /**
     * Ce que change [draft] (une tâche nouvelle, ou une tâche existante telle qu'on s'apprête à l'enregistrer) : la file est
     * rejouée **sans** elle puis **avec** elle, à la même graine, donc avec les mêmes tirages pour les autres tâches et pour les
     * jours ; tout écart vient d'elle seule. `null` : historique chronométré trop court.
     */
    fun impact(snapshot: KairosSnapshot, day: LocalDate, now: Instant, timeZone: TimeZone, seed: Long, draft: Task, runs: Int = DEFAULT_RUNS): Impact? {
        val without = snapshot.copy(tasks = snapshot.tasks.filter { it.id != draft.id })
        val after = run(without, day, now, timeZone, seed, runs, extra = listOf(draft.copy(status = TaskStatus.TODO, space = TaskSpace.PERSONAL))) ?: return null
        val before = run(without, day, now, timeZone, seed, runs)
        val titles = without.tasks.associate { it.id to it.title }
        val hurt = after.outlooks.mapNotNull { (id, o) ->
            val onTime = o.onTimeDraws ?: return@mapNotNull null
            val was = before?.outlooks?.get(id)?.onTimeDraws ?: return@mapNotNull null
            Hurt(id, titles[id].orEmpty(), was * 10 / runs, onTime * 10 / runs).takeIf { id != draft.id && it.afterPerTen < it.beforePerTen }
        }.sortedWith(compareByDescending<Hurt> { it.beforePerTen - it.afterPerTen }.thenBy { it.taskId })
        return Impact(runs, after.outlooks[draft.id], hurt)
    }

    /**
     * Ce que la semaine commençant le lundi [monday] doit contenir : les minutes restantes des tâches perso à faire dont
     * l'échéance ou la date programmée tombe dans la semaine (durée saisie, apprise fiable, ou par défaut).
     */
    fun committedMinutes(snapshot: KairosSnapshot, monday: LocalDate, now: Instant, timeZone: TimeZone): Int {
        val sunday = monday.plus(DatePeriod(days = 6))
        val applied = DurationEstimator.appliedMinutes(DurationEstimator.estimates(snapshot, now, timeZone))
        val spent = TimeTracking.spentMinutesByTask(snapshot.workSessions, now, snapshot.tasks)
        val mothers = personal(snapshot).filter { it.status == TaskStatus.TODO }.mapNotNullTo(HashSet()) { it.parentId }
        return personal(snapshot)
            .filter { it.status == TaskStatus.TODO && it.id !in mothers && ((it.scheduledDate ?: it.deadline)?.let { d -> d in monday..sunday } ?: false) }
            .sumOf { maxOf(0, Scheduling.durationMinutes(it, snapshot.settings, applied) - (spent[it.id] ?: 0)) }
    }
}
