package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.engine.Staleness
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * Statistiques (docs/spec-v3/statistiques.md), portage de `app/tasks_stats.py`
 * vérifié par tests différentiels. Pur : jour, instant et fuseau en paramètres.
 * Date de complétion d'une tâche faite = date de sa dernière modification
 * (pas d'horodatage dédié, comme Kairos 2).
 */
object TaskStats {
    /** En dessous de cet effectif, un agrégat est marqué « peu fiable » (affiché quand même). */
    const val MIN_SAMPLE = 3

    data class WeekThroughput(val weekStart: LocalDate, val completed: Int, val points: Int)

    data class Calibration(val key: String, val count: Int, val medianMinutes: Int?) {
        val reliable: Boolean get() = count >= MIN_SAMPLE
    }

    data class EstimationBias(val count: Int, val estimatedMinutes: Int, val realMinutes: Int, val ratio: Double) {
        val reliable: Boolean get() = count >= MIN_SAMPLE
    }

    /** Part du temps chronométré d'un type ; clé vide = « Sans type ». */
    data class TypeShare(val key: String, val minutes: Int, val pct: Int)

    data class Focus(val sessionCount: Int, val totalMinutes: Int, val avgSessionMinutes: Int?)

    data class BacklogFlow(
        val openCount: Int,
        val medianAgeDays: Int?,
        val overdueCount: Int,
        val staleCount: Int,
        val completionDelayDays: Int?,
        val deadlineTotal: Int,
        val deadlineOnTime: Int,
    ) {
        val deadlineHitPct: Int? get() = if (deadlineTotal == 0) null else pyRound(100.0 * deadlineOnTime / deadlineTotal)
    }

    data class Completeness(val total: Int, val withPoints: Int, val withEstimate: Int, val withType: Int) {
        private fun pct(n: Int) = if (total == 0) 0 else pyRound(100.0 * n / total)
        val pointsPct: Int get() = pct(withPoints)
        val estimatePct: Int get() = pct(withEstimate)
        val typePct: Int get() = pct(withType)
    }

    /** Repères d'un palier pour le guide des points : médiane réelle et exemples récents. */
    data class FiboReference(val points: Int, val calibration: Calibration?, val examples: List<String>)

    data class Dashboard(
        val windowWeeks: Int,
        val completedInWindow: Int,
        val trackedMinutesWindow: Int,
        val throughput: List<WeekThroughput>,
        val calibration: List<Calibration>,
        val bias: EstimationBias?,
        val timeByType: List<TypeShare>,
        val focus: Focus,
        val flow: BacklogFlow,
        val completeness: Completeness,
    ) {
        val hasAnyData: Boolean
            get() = completedInWindow > 0 || trackedMinutesWindow > 0 || flow.openCount > 0 || completeness.total > 0
    }

    /** Arrondi de Python (au pair le plus proche). */
    internal fun pyRound(x: Double): Int = kotlin.math.round(x).toInt()

    internal fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    fun monday(day: LocalDate): LocalDate = day.minus(DatePeriod(days = day.dayOfWeek.ordinal))

    private fun doneDate(task: Task, tz: TimeZone): LocalDate? =
        if (task.status == TaskStatus.DONE) task.updatedAt.toLocalDateTime(tz).date else null

    fun throughputByWeek(tasks: List<Task>, today: LocalDate, weeks: Int, tz: TimeZone): List<WeekThroughput> {
        val first = monday(today).minus(DatePeriod(days = 7 * (weeks - 1)))
        val counts = HashMap<LocalDate, Int>()
        val points = HashMap<LocalDate, Int>()
        for (t in tasks) {
            val done = doneDate(t, tz) ?: continue
            if (done < first) continue
            val wk = monday(done)
            counts[wk] = (counts[wk] ?: 0) + 1
            points[wk] = (points[wk] ?: 0) + (t.fibonacciPoints ?: 0)
        }
        return (0 until weeks).map { i ->
            val wk = first.plus(DatePeriod(days = 7 * i))
            WeekThroughput(wk, counts[wk] ?: 0, points[wk] ?: 0)
        }
    }

    private fun calibrate(groups: Map<String, List<Double>>, order: Comparator<String>): List<Calibration> =
        groups.keys.sortedWith(order).map { key ->
            val samples = groups.getValue(key)
            Calibration(key, samples.size, median(samples)?.let(::pyRound))
        }

    /** Temps réel médian par palier de points, tâches faites **et** chronométrées. */
    fun fibonacciCalibration(tasks: List<Task>, spent: Map<Long, Int>): List<Calibration> {
        val groups = LinkedHashMap<String, MutableList<Double>>()
        for (t in tasks) {
            val points = t.fibonacciPoints
            if (t.status != TaskStatus.DONE || points == null || points == 0) continue
            val minutes = spent[t.id] ?: 0
            if (minutes <= 0) continue
            groups.getOrPut(points.toString()) { mutableListOf() } += minutes.toDouble()
        }
        return calibrate(groups, compareBy { it.toInt() })
    }

    /** Temps réel médian par type (durée suggérée quand on choisit un type). */
    fun calibrationByType(tasks: List<Task>, spent: Map<Long, Int>): List<Calibration> {
        val groups = LinkedHashMap<String, MutableList<Double>>()
        for (t in tasks) {
            if (t.status != TaskStatus.DONE || t.taskType.isEmpty()) continue
            val minutes = spent[t.id] ?: 0
            if (minutes <= 0) continue
            groups.getOrPut(t.taskType) { mutableListOf() } += minutes.toDouble()
        }
        return calibrate(groups, naturalOrder())
    }

    /**
     * Repères par palier (guide des points) : la calibration du palier et les
     * titres de ses tâches faites les plus récentes, chronométrées ou non.
     * Palier sans tâche faite : absent.
     */
    fun fibonacciReferences(tasks: List<Task>, spent: Map<Long, Int>, examplesPerLevel: Int = 2): Map<Int, FiboReference> {
        val calibration = fibonacciCalibration(tasks, spent).associateBy { it.key.toInt() }
        val done = tasks.filter { it.status == TaskStatus.DONE && (it.fibonacciPoints ?: 0) != 0 }.groupBy { it.fibonacciPoints!! }
        return done.mapValues { (points, list) ->
            FiboReference(points, calibration[points], list.sortedByDescending { it.updatedAt }.take(examplesPerLevel).map { it.title })
        }
    }

    /** Biais global : réel total ÷ estimé total, sur les tâches faites ayant les deux. */
    fun estimationBias(tasks: List<Task>, spent: Map<Long, Int>): EstimationBias? {
        var est = 0
        var real = 0
        var count = 0
        for (t in tasks) {
            val estimate = t.estimatedMinutes
            if (t.status != TaskStatus.DONE || estimate == null || estimate == 0) continue
            val minutes = spent[t.id] ?: 0
            if (minutes <= 0) continue
            est += estimate
            real += minutes
            count++
        }
        if (count == 0 || est == 0) return null
        return EstimationBias(count, est, real, real.toDouble() / est)
    }

    /** Répartition du temps chronométré par type, du plus au moins chronophage. */
    fun timeByType(sessions: List<WorkSession>, typeById: Map<Long, String>, now: Instant): List<TypeShare> {
        val minutes = TimeTracking.spentMinutesByType(sessions, typeById, now)
        val total = minutes.values.sum()
        return minutes.filter { it.value > 0 }
            .map { (key, m) -> TypeShare(key, m, if (total > 0) pyRound(100.0 * m / total) else 0) }
            .sortedByDescending { it.minutes }
    }

    fun focus(sessions: List<WorkSession>, now: Instant): Focus {
        val durations = sessions.map { TimeTracking.sessionMinutes(it, now) }.filter { it > 0 }
        val total = durations.sum()
        return Focus(durations.size, total, if (durations.isEmpty()) null else pyRound(total.toDouble() / durations.size))
    }

    fun backlogFlow(tasks: List<Task>, today: LocalDate, windowStart: LocalDate, settings: Settings, tz: TimeZone): BacklogFlow {
        val todo = tasks.filter { it.status == TaskStatus.TODO }
        val ages = todo.map { it.createdAt.toLocalDateTime(tz).date.daysUntil(today).toDouble() }
        val overdue = todo.count { Scheduling.urgencyBucket(it, today) == 0 }
        val stale = todo.count { Staleness.daysStale(it, today, settings.staleOverdueDays, settings.staleUntouchedDays) != null }
        val delays = mutableListOf<Double>()
        var deadlineTotal = 0
        var onTime = 0
        for (t in tasks) {
            val done = doneDate(t, tz) ?: continue
            if (done < windowStart) continue
            delays += t.createdAt.toLocalDateTime(tz).date.daysUntil(done).toDouble()
            val deadline = t.deadline ?: continue
            deadlineTotal++
            if (done <= deadline) onTime++
        }
        return BacklogFlow(
            todo.size, median(ages)?.let(::pyRound), overdue, stale, median(delays)?.let(::pyRound), deadlineTotal, onTime,
        )
    }

    fun completeness(tasks: List<Task>): Completeness {
        val todo = tasks.filter { it.status == TaskStatus.TODO }
        return Completeness(
            todo.size,
            todo.count { (it.fibonacciPoints ?: 0) != 0 },
            todo.count { (it.estimatedMinutes ?: 0) != 0 },
            todo.count { it.taskType.isNotEmpty() },
        )
    }

    /** Tableau de bord complet (`compute_dashboard_stats`). [tasks] : toutes, archivées comprises, comme Kairos 2. */
    fun dashboard(tasks: List<Task>, sessions: List<WorkSession>, today: LocalDate, settings: Settings, now: Instant, tz: TimeZone): Dashboard {
        val weeks = maxOf(1, settings.statsWindowWeeks)
        val windowStart = monday(today).minus(DatePeriod(days = 7 * (weeks - 1)))
        val windowSessions = TimeTracking.sessionsInRange(sessions, windowStart, today, tz)
        val spent = TimeTracking.spentMinutesByTask(sessions, now, tasks)
        val typeById = tasks.associate { it.id to it.taskType }
        return Dashboard(
            windowWeeks = weeks,
            completedInWindow = tasks.count { t -> doneDate(t, tz)?.let { it >= windowStart } ?: false },
            trackedMinutesWindow = windowSessions.sumOf { TimeTracking.sessionMinutes(it, now) },
            throughput = throughputByWeek(tasks, today, weeks, tz),
            calibration = fibonacciCalibration(tasks, spent),
            bias = estimationBias(tasks, spent),
            timeByType = timeByType(windowSessions, typeById, now),
            focus = focus(windowSessions, now),
            flow = backlogFlow(tasks, today, windowStart, settings, tz),
            completeness = completeness(tasks),
        )
    }
}
