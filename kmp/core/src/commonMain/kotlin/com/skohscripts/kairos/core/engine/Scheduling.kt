package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.FIBONACCI_SCALE
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlin.math.pow

/**
 * Ordonnancement de la journée (docs/spec/ordonnancement.md) : comportement
 * figé par les fichiers d'or des tests différentiels (`fixtures/scheduling.json`, docs/spec/retrait-kairos2.md). Pur : aucune horloge,
 * aucun accès aux données ; le jour, l'heure et les réglages sont passés.
 *
 * Deux mécanismes :
 * - **l'ordre** : score WSJF `(valeur(priorité) + criticité(échéance)) / effort`,
 *   sous un palier dur « en retard » ;
 * - **le placement** : épinglées à leur heure, fenêtres de deep work remplies,
 *   puis placement au curseur autour des créneaux occupés, modulé par le creux
 *   de l'après-midi.
 *
 * Les notes (repoussée, chevauchement, creux) sont rendues sous forme de
 * données ; l'interface les formule dans sa langue.
 */
object Scheduling {
    /** Priorité la plus faible de l'échelle P0 / P1 / P2. */
    const val PRIORITY_MAX = 2
    private val MAX_FIBONACCI = FIBONACCI_SCALE.last().toDouble()

    /** `date.max` de Python : départage des tâches sans échéance. */
    private val DATE_MAX = LocalDate(9999, 12, 31)

    // --- Paliers et score -----------------------------------------------------

    /** En retard : échéance ou date programmée atteinte ou dépassée (palier dur du tri). */
    fun isOverdue(task: Task, day: LocalDate): Boolean =
        (task.deadline != null && task.deadline <= day) || (task.scheduledDate != null && task.scheduledDate <= day)

    /**
     * Palier d'urgence 0 à 4, signal **visuel** (bordure), découplé de l'ordre :
     * 0 en retard, 1 P0, 2 programmée aujourd'hui, 3 échéance cette semaine, 4 le reste.
     */
    fun urgencyBucket(task: Task, day: LocalDate): Int = when {
        isOverdue(task, day) -> 0
        task.priority == 0 -> 1
        task.scheduledDate != null && task.scheduledDate == day -> 2
        task.deadline != null && task.deadline <= Workdays.endOfWeek(day) -> 3
        else -> 4
    }

    /** Valeur exponentielle : `base ^ (2 − priorité)` ; sans priorité : `base ^ −1`. */
    fun priorityValue(priority: Int?, settings: Settings): Double {
        val base = settings.priorityValueBase
        if (priority == null) return base.pow(-1)
        val clamped = priority.coerceIn(0, PRIORITY_MAX)
        return base.pow(PRIORITY_MAX - clamped)
    }

    /** Rampe linéaire 0 → `urgencyPeak` vers la date la plus proche (échéance ou date programmée). */
    fun timeCriticality(task: Task, day: LocalDate, settings: Settings): Double {
        val nearest = listOfNotNull(task.deadline, task.scheduledDate).minOrNull() ?: return 0.0
        val horizon = settings.urgencyHorizonDays
        val peak = settings.urgencyPeak
        val daysUntil = day.daysUntil(nearest)
        if (daysUntil >= horizon) return 0.0
        if (daysUntil <= 0) return peak
        return peak * (horizon - daysUntil) / horizon
    }

    enum class EffortSource { POINTS, MINUTES, DEFAULT }

    /** Effort (dénominateur) : points, sinon minutes / 30 bornées à 1…21, sinon points par défaut. */
    fun effort(task: Task, settings: Settings): Pair<Double, EffortSource> {
        val points = task.fibonacciPoints
        if (points != null && points > 0) return points.toDouble() to EffortSource.POINTS
        val minutes = task.estimatedMinutes
        if (minutes != null && minutes > 0) return minOf(21.0, maxOf(1.0, minutes / 30.0)) to EffortSource.MINUTES
        return settings.defaultFibonacciPoints.toDouble() to EffortSource.DEFAULT
    }

    enum class DateKind { DEADLINE, SCHEDULED }

    /** Les termes du score, pour « Pourquoi à cette place ? ». */
    data class WsjfBreakdown(
        val value: Double,
        val criticality: Double,
        val effort: Double,
        val effortSource: EffortSource,
        val dateKind: DateKind?,
        val daysUntil: Int?,
        val overdue: Boolean,
    ) {
        val score: Double get() = (value + criticality) / effort
    }

    fun wsjfBreakdown(task: Task, day: LocalDate, settings: Settings): WsjfBreakdown {
        val (effort, source) = effort(task, settings)
        // Échéance en premier : à date égale, c'est elle qui est citée (min stable de Python).
        val dated = listOfNotNull(task.deadline?.let { it to DateKind.DEADLINE }, task.scheduledDate?.let { it to DateKind.SCHEDULED })
        val nearest = dated.minByOrNull { it.first }
        return WsjfBreakdown(
            value = priorityValue(task.priority, settings),
            criticality = timeCriticality(task, day, settings),
            effort = effort,
            effortSource = source,
            dateKind = nearest?.second,
            daysUntil = nearest?.let { day.daysUntil(it.first) },
            overdue = isOverdue(task, day),
        )
    }

    fun wsjfScore(task: Task, day: LocalDate, settings: Settings): Double = wsjfBreakdown(task, day, settings).score

    /**
     * Clé de tri, **plus petite = plus urgente** : palier (0 en retard, 1 sinon),
     * −score, priorité (999 si absente), échéance (`9999-12-31` si absente), id.
     */
    data class SortKey(val tier: Int, val negScore: Double, val priority: Int, val deadline: LocalDate, val id: Long) :
        Comparable<SortKey> {
        override fun compareTo(other: SortKey): Int =
            compareValuesBy(this, other, { it.tier }, { it.negScore }, { it.priority }, { it.deadline }, { it.id })
    }

    fun sortKey(task: Task, day: LocalDate, settings: Settings): SortKey = SortKey(
        tier = if (isOverdue(task, day)) 0 else 1,
        negScore = -wsjfScore(task, day, settings),
        priority = task.priority ?: 999,
        deadline = task.deadline ?: DATE_MAX,
        id = task.id,
    )

    // --- Creux de l'après-midi --------------------------------------------------

    /** Intensité du creux ∈ [0, 1] : triangle 0 aux bords, 1 au tronc ; 0 si désactivé ou hors fenêtre. */
    fun dipIntensity(time: LocalTime, settings: Settings): Double {
        if (!settings.cognitiveDipEnabled || settings.cognitiveDipPenalty <= 0) return 0.0
        val start = settings.cognitiveDipStartHour
        val trough = settings.cognitiveDipTroughHour
        val end = settings.cognitiveDipEndHour
        val hour = time.hour + time.minute / 60.0
        if (hour <= start || hour >= end) return 0.0
        if (hour <= trough) return if (trough > start) (hour - start) / (trough - start) else 1.0
        return if (end > trough) (end - hour) / (end - trough) else 1.0
    }

    private fun dipAdjustedEffort(task: Task, time: LocalTime, settings: Settings): Double {
        val effort = effort(task, settings).first
        val intensity = dipIntensity(time, settings)
        if (intensity <= 0) return effort
        val norm = maxOf(0.0, minOf(1.0, (effort - 1.0) / (MAX_FIBONACCI - 1.0)))
        return effort * (1.0 + settings.cognitiveDipPenalty * intensity * norm)
    }

    /**
     * Score au créneau : coût du retard ÷ effort, l'effort étant majoré par le creux puis **minoré** par l'affinité du type pour
     * l'heure (`÷ (1 + force × gain)`, étape C2). Sans [affinity] : le code d'avant.
     */
    private fun placementScore(task: Task, day: LocalDate, time: LocalTime, settings: Settings, affinity: Affinity?): Double {
        val costOfDelay = priorityValue(task.priority, settings) + timeCriticality(task, day, settings)
        val effort = dipAdjustedEffort(task, time, settings)
        return costOfDelay / (if (affinity == null) effort else effort / affinity.factor(task.taskType, time))
    }

    /**
     * Clé de choix POUR le créneau : clé d'urgence, sauf pendant le creux ou quand le type a de l'affinité pour l'heure, pour une
     * tâche non remontée. Une tâche en retard (palier 0) ne bouge jamais : l'affinité ne réordonne que les autres.
     */
    private fun selectionKey(
        task: Task,
        day: LocalDate,
        cursor: LocalDateTime,
        settings: Settings,
        urgencyKeys: Map<Long, SortKey>,
        affinity: Affinity? = null,
    ): SortKey {
        val own = sortKey(task, day, settings)
        val effective = urgencyKeys[task.id] ?: own
        val liked = affinity != null && own.tier != 0 && affinity.boost(task.taskType, cursor.time) > 0
        if (effective != own || (dipIntensity(cursor.time, settings) <= 0 && !liked)) return effective
        return own.copy(negScore = -placementScore(task, day, cursor.time, settings, if (liked) affinity else null))
    }

    // --- Éligibilité ---------------------------------------------------------------

    /** Nombre de P0 (garde-fou de surcharge). */
    fun countMaxPriority(tasks: List<Task>): Int = tasks.count { it.priority == 0 }

    /** Masquée du jour seulement si programmée plus tard **et** sans échéance atteinte ; épinglée = toujours visible. */
    fun isEligibleToday(task: Task, day: LocalDate, pinnedForToday: Boolean = false): Boolean {
        if (pinnedForToday) return true
        val hidden = task.scheduledDate != null && task.scheduledDate > day && (task.deadline == null || task.deadline > day)
        return !hidden
    }

    /**
     * Durée de placement : estimée, sinon (absente ou 0) la durée **apprise** de [learned] si la tâche en a une (docs/spec/durees-apprises.md),
     * sinon le réglage par défaut. Une estimation explicite l'emporte toujours ; sans [learned], le code est celui d'avant.
     */
    fun durationMinutes(task: Task, settings: Settings, learned: Map<Long, Int> = emptyMap()): Int =
        // Seule une estimation positive est explicite, comme pour la chaîne des durées apprises (QA du 2026-11-04).
        task.estimatedMinutes?.takeIf { it > 0 } ?: learned[task.id] ?: settings.defaultTaskDurationMinutes

    // --- Résultat ----------------------------------------------------------------

    /** [DEEPWORK] : fenêtre de deep work (rangée avec les épinglées dans les fichiers d'or ; la note de la vue Jour les distingue). */
    enum class ObstacleKind { BUSY, PINNED, DEEPWORK }

    data class ScheduledTask(
        val task: Task,
        val start: LocalDateTime,
        val durationMinutes: Int,
        val pinned: Boolean = false,
        /** Repoussée derrière un obstacle : titre de l'obstacle et sa nature. */
        val pushedAfter: String? = null,
        val pushedAfterKind: ObstacleKind? = null,
        /** Épinglée sur un créneau occupé : titre du créneau (signalé, jamais résolu). */
        val conflictWith: String? = null,
        val deepwork: Boolean = false,
        /** Le creux de l'après-midi a fait préférer cette tâche légère sur ce créneau. */
        val dipApplied: Boolean = false,
        /** L'heure habituelle ou préférée du type de la tâche l'a fait préférer sur ce créneau (étape C2) ; `null` : sans effet. */
        val affinity: AffinitySource? = null,
    ) {
        val affinityApplied: Boolean get() = affinity != null
        val end: LocalDateTime get() = start.plusMinutes(durationMinutes)
        val pushed: Boolean get() = pushedAfter != null
        val conflict: Boolean get() = conflictWith != null
    }

    data class DayStats(val requiredMinutes: Int = 0, val availableMinutes: Int = 0) {
        val overflowMinutes: Int get() = maxOf(0, requiredMinutes - availableMinutes)
    }

    /**
     * Répartition du jour. Invariant de conservation : chaque tâche `todo` qui
     * n'est pas une mère à filles ouvertes est dans **exactement une** des listes
     * `scheduled`, `unscheduled`, `later`, `toProcess`, ou bloquée.
     */
    data class ScheduledDay(
        val scheduled: List<ScheduledTask> = emptyList(),
        val unscheduled: List<Task> = emptyList(),
        val later: List<Task> = emptyList(),
        val toProcess: List<Task> = emptyList(),
        val stats: DayStats = DayStats(),
        /**
         * Tâches « Sans créneau » dont le **contexte** n'a aucune plage ce jour-là (docs/spec/planning-personnalise.md § Lot B2) :
         * identifiant → nom du contexte, pour dire pourquoi (« pas de plage Maison aujourd'hui »). Vide sans contexte.
         */
        val noWindow: Map<Long, String> = emptyMap(),
    )

    private class Obstacle(
        val start: LocalDateTime,
        val end: LocalDateTime,
        val title: String,
        val bufferMinutes: Int,
        val kind: ObstacleKind,
    )

    /**
     * Ordonne les tâches `todo` du jour [day] autour de [busyBlocks] (occupés et
     * deep work, récurrences déjà projetées). [now] (heure locale) décale le
     * début de la fenêtre si c'est aujourd'hui. [blockedIds] : tâches bloquées
     * par une dépendance (ni placées, ni comptées). [urgencyKeys] : clés
     * d'urgence dérivées (chemin critique), repli sur la clé propre.
     */
    fun buildDaySchedule(
        tasks: List<Task>,
        busyBlocks: List<TimeBlock>,
        day: LocalDate,
        now: LocalDateTime?,
        settings: Settings,
        blockedIds: Set<Long> = emptySet(),
        urgencyKeys: Map<Long, SortKey> = emptyMap(),
        /** Durées apprises à appliquer (minutes par tâche, [DurationEstimator.appliedMinutes]) ; vide : comme avant. */
        learned: Map<Long, Int> = emptyMap(),
        /** Affinité des types pour les heures (étape C2, [Affinity]) ; `null` : comme avant. */
        affinity: Affinity? = null,
    ): ScheduledDay {
        fun key(task: Task) = urgencyKeys[task.id] ?: sortKey(task, day, settings)

        val todo = tasks.filter { it.status == TaskStatus.TODO }
        val parentsWithOpenChildren = todo.mapNotNull { it.parentId }.toSet()
        val workUnits = todo.filter { it.id !in parentsWithOpenChildren }
        val toProcess = workUnits.filter { it.needsProcessing }
        val toProcessIds = toProcess.map { it.id }.toSet()
        val nonBlocked = workUnits.filter { it.id !in toProcessIds && it.id !in blockedIds }
        val pinnedToday = nonBlocked.filter { it.pinnedStart != null && it.pinnedStart.date == day }
        val pinnedIds = pinnedToday.map { it.id }.toSet()

        val schedulable = mutableListOf<Task>()
        val later = mutableListOf<Task>()
        for (t in nonBlocked) {
            if (isEligibleToday(t, day, pinnedForToday = t.id in pinnedIds)) schedulable += t else later += t
        }
        val auto = schedulable.filter { it.id !in pinnedIds }.sortedWith(compareBy { key(it) })

        // Plages du jour (docs/spec/planning-personnalise.md § Lots B1 et B2). Jour libre : une plage vide, aucune tâche n'est
        // placée (« Sans créneau » ; les épinglées gardent leur heure). Sans horaire propre : la fenêtre des réglages, comme avant.
        val resolved = Schedule.windowsFor(day, settings)
        val windows = resolved.ifEmpty { listOf(Schedule.Window(day.atTime(0, 0), day.atTime(0, 0))) }
        val workdayStart = windows.first().start
        val workdayEnd = windows.last().end
        val effectiveStart = if (now != null && now.date == day && now > workdayStart) now else workdayStart
        // Le contexte d'une tâche (son type) ne sert qu'à choisir la plage : jamais au tri ni au score affiché.
        val contextOf = auto.associate { it.id to Schedule.contextOf(it.taskType, settings) }

        val buffer = settings.meetingBufferMinutes
        val inWindow = busyBlocks.filter { it.start < workdayEnd && it.end > workdayStart }
        val dayBlocks = inWindow.filter { it.kind != BlockKind.DEEPWORK }
        // Fenêtres de deep work rognées à maintenant (aujourd'hui) : leur part passée n'accueille plus de tâche, une
        // fenêtre déjà finie disparaît (écart volontaire avec les fichiers d'or, qui remplissent la fenêtre entière, passé compris).
        val nowToday = now?.takeIf { it.date == day }
        val deepworkBlocks = inWindow.filter { it.kind == BlockKind.DEEPWORK }
            .map { b -> if (nowToday != null && nowToday > b.start) b.copy(start = nowToday) else b }
            .filter { it.start < it.end && it.start < workdayEnd }
            .sortedBy { it.start }

        val scheduled = mutableListOf<ScheduledTask>()
        val unscheduled = mutableListOf<Task>()
        val assigned = mutableSetOf<Long>()

        // 1. Épinglées : à leur heure, chevauchements signalés.
        val obstacles = dayBlocks.map { Obstacle(it.start, it.end, it.title, buffer, ObstacleKind.BUSY) }.toMutableList()
        for (task in pinnedToday.sortedBy { it.pinnedStart!! }) {
            val duration = durationMinutes(task, settings, learned)
            val start = task.pinnedStart!!
            val end = start.plusMinutes(duration)
            val clash = dayBlocks.firstOrNull { it.start < end && it.end > start }
            scheduled += ScheduledTask(task, start, duration, pinned = true, conflictWith = clash?.title)
            assigned += task.id
            obstacles += Obstacle(start, end, task.title, 0, ObstacleKind.PINNED)
        }

        // 1 bis. Fenêtres de deep work : remplies par les plus urgentes, chacune sa durée.
        for (block in deepworkBlocks) {
            var slot = block.start
            while (slot < block.end) {
                val candidate = auto.firstOrNull { it.id !in assigned } ?: break
                val duration = durationMinutes(candidate, settings, learned)
                if (slot.plusMinutes(duration) > block.end) break
                scheduled += ScheduledTask(candidate, slot, duration, deepwork = true)
                assigned += candidate.id
                slot = slot.plusMinutes(duration)
            }
            obstacles += Obstacle(block.start, block.end, block.title, 0, ObstacleKind.DEEPWORK)
        }

        // 2. Placement au curseur, la meilleure tâche pour chaque créneau, plage après plage. Le filtre de contexte ne joue que
        // sur le choix : une tâche dont le contexte n'a pas cette plage attend la suivante.
        val remaining = auto.filter { it.id !in assigned }.toMutableList()
        obstacles.sortBy { it.start }
        var cursor = effectiveStart
        for ((index, window) in windows.withIndex()) {
            cursor = maxOf(cursor, window.start)
            val nextWindows = windows.drop(index + 1)
            val skipped = mutableSetOf<Long>()
            while (true) {
                val candidates = remaining.filter { it.id !in skipped && Schedule.accepts(window, contextOf[it.id]) }
                if (candidates.isEmpty()) break
                val slotStart = advancePastObstacles(cursor, obstacles)
                val liking = affinity?.takeUnless { it.isEmpty }
                val task = candidates.minWith(compareBy { selectionKey(it, day, slotStart, settings, urgencyKeys, liking) })
                // Sans l'affinité : le choix que le creux seul aurait fait (pour savoir lequel des deux a joué).
                val withoutAffinity = if (liking == null) task else candidates.minWith(compareBy { selectionKey(it, day, slotStart, settings, urgencyKeys) })
                val affinityChanged = task.id != withoutAffinity.id
                val dipActive = dipIntensity(slotStart.time, settings) > 0
                val urgencyPick = if (dipActive) candidates.minWith(compareBy { urgencyKeys[it.id] ?: sortKey(it, day, settings) }) else task

                val duration = durationMinutes(task, settings, learned)
                var start = cursor
                var pushedAfter: Obstacle? = null
                while (true) {
                    val end = start.plusMinutes(duration)
                    val blocking = obstacles.firstOrNull { it.start < end && it.end > start } ?: break
                    pushedAfter = blocking
                    start = blocking.end.plusMinutes(blocking.bufferMinutes)
                }
                if (start >= window.end) {
                    // Pas de place dans cette plage : une plage suivante qui l'accueille la prend ; sinon « Sans créneau ».
                    if (nextWindows.any { Schedule.accepts(it, contextOf[task.id]) }) {
                        skipped += task.id
                    } else {
                        remaining.remove(task)
                        unscheduled += task
                    }
                    continue // une tâche plus courte peut encore tenir
                }
                remaining.remove(task)
                scheduled += ScheduledTask(
                    task = task,
                    start = start,
                    durationMinutes = duration,
                    pushedAfter = pushedAfter?.title,
                    pushedAfterKind = pushedAfter?.kind,
                    dipApplied = dipActive && !affinityChanged && task.id != urgencyPick.id,
                    affinity = if (affinityChanged) liking?.source(task.taskType) else null,
                )
                cursor = start.plusMinutes(duration)
            }
        }
        // Aucune plage du jour n'accueille le contexte de ces tâches : « Sans créneau », avec la raison.
        val noWindow = mutableMapOf<Long, String>()
        if (resolved.isNotEmpty()) {
            for (task in remaining) contextOf[task.id]?.let { noWindow[task.id] = it }
        }
        unscheduled += remaining
        scheduled.sortBy { it.start }

        // 3. Charge : requis (unités de travail du jour) vs disponible (fenêtre restante − occupé).
        val required = schedulable.sumOf { durationMinutes(it, settings, learned) }
        // Disponible : la somme des plages (de maintenant à leur fin aujourd'hui), moins l'occupé qu'elles contiennent.
        val available = windows.sumOf { w ->
            val from = maxOf(effectiveStart, w.start)
            val windowMinutes = maxOf(0L, minutesBetween(from, w.end)).toInt()
            maxOf(0, windowMinutes - busyMinutesInWindow(dayBlocks, from, w.end))
        }
        return ScheduledDay(
            scheduled = scheduled,
            unscheduled = unscheduled,
            later = later,
            toProcess = toProcess,
            stats = DayStats(required, available),
            noWindow = noWindow,
        )
    }

    /** Premier instant ≥ [cursor] hors de tout obstacle (marge incluse). */
    private fun advancePastObstacles(cursor: LocalDateTime, obstacles: List<Obstacle>): LocalDateTime {
        var c = cursor
        var moved = true
        while (moved) {
            moved = false
            for (o in obstacles) {
                if (o.start <= c && c < o.end) {
                    c = o.end.plusMinutes(o.bufferMinutes)
                    moved = true
                }
            }
        }
        return c
    }

    /** Minutes occupées dans la fenêtre, chevauchements fusionnés. */
    fun busyMinutesInWindow(blocks: List<TimeBlock>, windowStart: LocalDateTime, windowEnd: LocalDateTime): Int {
        val merged = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        for (b in blocks.sortedBy { it.start }) {
            val start = maxOf(b.start, windowStart)
            val end = minOf(b.end, windowEnd)
            if (end <= start) continue
            val last = merged.lastOrNull()
            if (last != null && start <= last.second) {
                merged[merged.lastIndex] = last.first to maxOf(last.second, end)
            } else {
                merged += start to end
            }
        }
        return merged.sumOf { minutesBetween(it.first, it.second) }.toInt()
    }

    // --- Timeline -------------------------------------------------------------------

    /** `SESSION` : temps réellement chronométré (rail « réel », `TimeTracking.sessionTimeline`). */
    enum class TimelineKind { BUSY, DEEPWORK, WORK, PINNED, CONFLICT, DEEPWORK_TASK, SESSION }

    /** Bloc de la timeline : décalage et hauteur en minutes depuis le début de la journée de travail. */
    data class TimelineEntry(
        val kind: TimelineKind,
        val title: String,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val topMinutes: Int,
        val heightMinutes: Int,
    )

    /** Créneaux et tâches placées, bornés à la journée de travail ; les fonds d'abord à hauteur égale. */
    fun buildTimeline(schedule: ScheduledDay, busyBlocks: List<TimeBlock>, day: LocalDate, settings: Settings): List<TimelineEntry> {
        val window = Schedule.displayWindow(day, settings)
        val workdayStart = window.start
        val workdayEnd = window.end
        fun entry(kind: TimelineKind, title: String, start: LocalDateTime, end: LocalDateTime): TimelineEntry? {
            val s = maxOf(start, workdayStart)
            val e = minOf(end, workdayEnd)
            if (e <= s) return null
            return TimelineEntry(kind, title, start, end, minutesBetween(workdayStart, s).toInt(), minutesBetween(s, e).toInt())
        }
        val entries = mutableListOf<TimelineEntry>()
        for (b in busyBlocks) {
            val kind = if (b.kind == BlockKind.DEEPWORK) TimelineKind.DEEPWORK else TimelineKind.BUSY
            entry(kind, b.title, b.start, b.end)?.let { entries += it }
        }
        for (item in schedule.scheduled) {
            val kind = when {
                item.conflict -> TimelineKind.CONFLICT
                item.deepwork -> TimelineKind.DEEPWORK_TASK
                item.pinned -> TimelineKind.PINNED
                else -> TimelineKind.WORK
            }
            entry(kind, item.task.title, item.start, item.end)?.let { entries += it }
        }
        return entries.sortedWith(compareBy({ it.topMinutes }, { it.kind != TimelineKind.BUSY && it.kind != TimelineKind.DEEPWORK }))
    }
}
