package com.skohscripts.kairos.core.day

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.engine.minutesBetween
import com.skohscripts.kairos.core.engine.plusMinutes
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

/**
 * « Ta journée » (docs/spec/ta-journee.md, issue #274) : la journée, puis le reste de la journée, en chiffres et en verdict, et ce
 * qui aide quand elle ne tient pas (un créneau de deep work). Pur : construit sur la vue Jour du même instant ([DayView]), dont il
 * reprend aussi les bilans qu'affichait la carte « Maintenant ».
 */
data class DayBrief(
    val phase: Phase,
    val verdict: Verdict,
    /** Bilans repris de « Maintenant » : faites aujourd'hui, temps travaillé aujourd'hui et par type (types non vides). */
    val done: Int,
    val workedMinutes: Int,
    val workedByType: Map<String, Int>,
    /** Tâches du plan du jour restantes et leur estimation (durée de placement, apprise comprise, moins le temps déjà passé). */
    val tasks: Int,
    val estimateMinutes: Int,
    /** Tâches à faire d'échéance aujourd'hui, et de demain à dimanche. */
    val deadlinesToday: Int,
    val deadlinesWeek: Int,
    /** Disponible brut (plages libres restantes), dérangements attendus sur ces plages hors deep work, et l'effectif qui reste. */
    val availableMinutes: Int,
    val interruptionMinutes: Int,
    val effectiveMinutes: Int,
    val overflowMinutes: Int,
    /** Créneau de deep work proposé (verdict [Verdict.DEEP_WORK]). */
    val deepWork: DeepWork? = null,
    /** Tâche à reporter (verdict [Verdict.POSTPONE]) : la première du plan qui ne tient pas. */
    val postpone: Task? = null,
    /** Fin de la journée de travail (« ça tient avant 18 h »). */
    val dayEnd: LocalDateTime? = null,
) {
    /** Le matin ([DAY]) : la journée entière ; ensuite ([REST]) : ce qui reste. */
    enum class Phase { DAY, REST }

    enum class Verdict { FITS, DEEP_WORK, POSTPONE, EMPTY, DAY_OFF }

    /** Un créneau de deep work proposé : début et durée (minutes). */
    data class DeepWork(val start: LocalDateTime, val minutes: Int) {
        val end: LocalDateTime get() = start.plusMinutes(minutes)
    }

    companion object {
        /** Une durée de deep work proposée : au moins une demi-heure, arrondie au quart d'heure supérieur. */
        const val MIN_DEEP_WORK = 30
        const val DEEP_WORK_STEP = 15

        /**
         * [view] : la vue Jour à [now] (heure locale) ; [settings] : les réglages de l'instantané. Le dépassement compare
         * l'estimation à l'effectif ; un créneau de deep work est proposé quand les dérangements qu'il éviterait couvrent le
         * dépassement, au premier moment libre assez long ; sinon, une tâche à reporter.
         */
        fun build(view: DayView, settings: Settings, now: LocalDateTime): DayBrief {
            val day = view.day
            val stats = view.schedule.stats
            val applied = DurationEstimator.appliedMinutes(view.learned)
            val remaining = view.schedule.scheduled.map { it.task } + view.schedule.unscheduled
            val estimate = remaining.sumOf { maxOf(0, Scheduling.durationMinutes(it, settings, applied) - (view.spentByTask[it.id] ?: 0)) }
            val sunday = day.plus(DatePeriod(days = 6 - day.dayOfWeek.ordinal))
            val open = view.openTasks.filter { it.status == TaskStatus.TODO }
            val effective = stats.availableMinutes
            val lost = stats.expectedInterruptionMinutes
            val overflow = maxOf(0, estimate - effective)
            val started = view.windows.firstOrNull()?.let { now >= it.start } ?: false
            val phase = if (started && (view.doneToday.isNotEmpty() || view.spentToday > 0)) Phase.REST else Phase.DAY
            val busy = view.dayBlocks.any { it.kind == BlockKind.BUSY }
            val offDay = view.freeDay || day in view.holidays
            val dayEnd = view.windows.lastOrNull()?.end
            var deepWork: DeepWork? = null
            var postpone: Task? = null
            val verdict = when {
                offDay && remaining.isEmpty() -> Verdict.DAY_OFF
                remaining.isEmpty() && !busy -> Verdict.EMPTY
                overflow == 0 -> Verdict.FITS
                else -> {
                    deepWork = if (view.expected.none) null else proposeDeepWork(view, now, overflow)
                    if (deepWork != null) {
                        Verdict.DEEP_WORK
                    } else {
                        postpone = view.schedule.unscheduled.firstOrNull()
                            ?: view.schedule.scheduled.firstOrNull { s -> dayEnd != null && s.end > dayEnd }?.task
                            ?: view.schedule.scheduled.lastOrNull()?.task
                        Verdict.POSTPONE
                    }
                }
            }
            return DayBrief(
                phase = phase,
                verdict = verdict,
                done = view.doneToday.size,
                workedMinutes = view.spentToday,
                workedByType = view.spentByTypeToday,
                tasks = remaining.size,
                estimateMinutes = estimate,
                deadlinesToday = open.count { it.deadline == day },
                deadlinesWeek = open.count { d -> d.deadline?.let { it > day && it <= sunday } == true },
                availableMinutes = effective + lost,
                interruptionMinutes = lost,
                effectiveMinutes = effective,
                overflowMinutes = overflow,
                deepWork = deepWork,
                postpone = postpone,
                dayEnd = dayEnd,
            )
        }

        /**
         * Le premier moment libre (plages restantes du jour, hors créneaux, deep work existant et tâches à heure fixe) qui contient
         * un créneau de deep work dont les dérangements évités couvrent [overflow] ; `null` si aucun.
         */
        private fun proposeDeepWork(view: DayView, now: LocalDateTime, overflow: Int): DeepWork? {
            val taken = view.dayBlocks.map { it.start to it.end } +
                view.schedule.scheduled.filter { it.pinned }.map { it.start to it.end }
            for (w in view.windows) {
                var cursor = maxOf(now, w.start)
                while (cursor < w.end) {
                    // Un moment libre : de [cursor] jusqu'au prochain obstacle (ou la fin de la plage).
                    val inside = taken.firstOrNull { (a, b) -> a <= cursor && cursor < b }
                    if (inside != null) { cursor = inside.second; continue }
                    val freeEnd = taken.filter { (a, _) -> a > cursor && a < w.end }.minOfOrNull { it.first } ?: w.end
                    var avoided = 0.0
                    var length = 0
                    var t = cursor
                    while (t < freeEnd && avoided + 1e-9 < overflow) {
                        avoided += view.expected.rate(t.hour)
                        length++
                        t = t.plusMinutes(1)
                    }
                    if (avoided + 1e-9 >= overflow) {
                        val minutes = maxOf(MIN_DEEP_WORK, (length + DEEP_WORK_STEP - 1) / DEEP_WORK_STEP * DEEP_WORK_STEP)
                        if (minutesBetween(cursor, freeEnd) >= minutes) return DeepWork(cursor, minutes)
                    }
                    cursor = freeEnd
                }
            }
            return null
        }
    }
}
