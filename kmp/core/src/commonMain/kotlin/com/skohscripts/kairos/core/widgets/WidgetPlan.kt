package com.skohscripts.kairos.core.widgets

import com.skohscripts.kairos.core.day.DayView
import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.engine.plusMinutes
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Une tâche telle que la montrent les widgets : [start]/[end] nuls quand elle n'a pas de créneau. */
data class WidgetTask(
    val id: Long,
    val title: String,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val priority: Int?,
    val project: String,
    val type: String,
    val deepWork: Boolean,
    /** Estimation explicite, sinon la durée apprise fiable (comme la carte « En ce moment »). */
    val estimateMinutes: Int?,
    val pinned: Boolean,
)

enum class NowState { RUNNING, UPCOMING, NOTHING_PLANNED, ALL_DONE }

/**
 * Contenu du widget « Maintenant » (docs/spec/widgets-android.md) : [task] est celle du chrono ([NowState.RUNNING]) ou
 * `DayView.nextUp` ([NowState.UPCOMING]) ; [next] la suivante du plan ; [inbox] le nombre de tâches à traiter.
 * [runningSince] et [spentBeforeMinutes] : début de la session ouverte et temps déjà passé sur la tâche avant elle,
 * de quoi régler le chronomètre du widget qui compte seul.
 */
data class NowWidget(
    val state: NowState,
    val task: WidgetTask? = null,
    val next: WidgetTask? = null,
    val inbox: Int = 0,
    val runningSince: Instant? = null,
    val spentBeforeMinutes: Int = 0,
)

enum class DayRowKind { DEADLINE, TASK, BUSY, DEEPWORK }

enum class DayRowState { PAST, CURRENT, UPCOMING, DONE }

/**
 * Une ligne du widget « Journée ». [start] : heure de la ligne (l'heure où la tâche a été faite pour [DayRowState.DONE],
 * `null` pour une échéance sans créneau) ; [overdue] : échéance dépassée (en rouge).
 */
data class DayRow(
    val kind: DayRowKind,
    val id: Long,
    val title: String,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val priority: Int?,
    val state: DayRowState,
    val overdue: Boolean = false,
)

/** Contenu du widget « Journée » : les lignes dans l'ordre, et le décompte « [doneCount] / [total] faites ». */
data class DayWidget(val day: LocalDate, val rows: List<DayRow>, val doneCount: Int, val total: Int)

enum class ConfirmState { IDLE, ARMED }

/**
 * Calcul des deux widgets Android (docs/spec/widgets-android.md, issue #150). Pur : l'heure et le fuseau sont des paramètres.
 * Le snapshot est la vue Perso (`KairosRepository.personalSnapshot`) ; le plan est celui de la vue Jour ([DayView.build]),
 * jamais un second moteur : une règle de la vue Jour se retrouve dans les widgets sans rien changer ici.
 */
object WidgetPlan {
    /** Délai du double toucher sur « Fait » : passé ce délai, le bouton se désarme. */
    const val CONFIRM_SECONDS = 5

    fun now(snapshot: KairosSnapshot, now: LocalDateTime, timeZone: TimeZone): NowWidget {
        val view = DayView.build(snapshot, now.date, now, timeZone)
        val inbox = view.inbox.size
        val running = view.running
        val runningTask = view.runningTask
        val shown = when {
            running != null && runningTask != null -> runningTask
            else -> view.nextUp
        }
        if (shown == null) {
            return NowWidget(if (view.doneToday.isEmpty()) NowState.NOTHING_PLANNED else NowState.ALL_DONE, inbox = inbox)
        }
        val following = (view.schedule.scheduled.map { it.task } + view.schedule.unscheduled).firstOrNull { it.id != shown.id }
        return NowWidget(
            state = if (running != null && runningTask != null) NowState.RUNNING else NowState.UPCOMING,
            task = widgetTask(shown, view),
            next = following?.let { widgetTask(it, view) },
            inbox = inbox,
            runningSince = running?.startedAt?.takeIf { runningTask != null },
            spentBeforeMinutes = if (runningTask != null) view.runningBaseMinutes else 0,
        )
    }

    fun day(snapshot: KairosSnapshot, now: LocalDateTime, timeZone: TimeZone): DayWidget {
        val day = now.date
        val view = DayView.build(snapshot, day, now, timeZone)
        val current = view.runningTask?.id ?: view.nextUp?.id
        fun timed(start: LocalDateTime, end: LocalDateTime) = when {
            end <= now -> DayRowState.PAST
            start <= now -> DayRowState.CURRENT
            else -> DayRowState.UPCOMING
        }
        val placed = view.schedule.scheduled.map { s ->
            val end = s.start.plusMinutes(s.durationMinutes)
            val state = if (s.task.id == current) DayRowState.CURRENT else if (end <= now) DayRowState.PAST else DayRowState.UPCOMING
            DayRow(DayRowKind.TASK, s.task.id, s.task.title, s.start, end, s.task.priority, state)
        }
        val placedIds = placed.mapTo(HashSet()) { it.id }
        val blockedIds = view.blocked.mapTo(HashSet()) { it.task.id }
        // Échéance aujourd'hui ou dépassée, sans créneau : en tête, le retard d'abord.
        val deadlines = view.openTasks
            .filter { it.deadline != null && it.deadline <= day && it.id !in placedIds && it.id !in blockedIds }
            .sortedWith(compareBy<Task>({ it.deadline }, { it.title }))
            .map {
                val state = if (it.id == current) DayRowState.CURRENT else DayRowState.UPCOMING
                DayRow(DayRowKind.DEADLINE, it.id, it.title, null, null, it.priority, state, overdue = it.deadline!! < day)
            }
        val blocks = view.dayBlocks.map {
            val kind = if (it.kind == BlockKind.DEEPWORK) DayRowKind.DEEPWORK else DayRowKind.BUSY
            DayRow(kind, it.id, it.title, it.start, it.end, null, timed(it.start, it.end))
        }
        val done = view.doneToday.map {
            DayRow(DayRowKind.TASK, it.id, it.title, it.updatedAt.toLocalDateTime(timeZone), null, it.priority, DayRowState.DONE)
        }
        val timedRows = (placed + blocks + done).sortedWith(compareBy<DayRow>({ it.start }, { it.kind != DayRowKind.TASK }, { it.title }))
        val doneCount = done.size
        return DayWidget(day, deadlines + timedRows, doneCount, doneCount + placed.size + deadlines.size)
    }

    /**
     * Prochaine heure, après [now], où le contenu des widgets change sans que la base change : début ou fin d'une tâche placée
     * ou d'un créneau du jour, sinon minuit. Sert à poser l'alarme de rafraîchissement (une seule, remplacée à chaque calcul).
     */
    fun nextChange(snapshot: KairosSnapshot, now: LocalDateTime, timeZone: TimeZone): LocalDateTime {
        val view = DayView.build(snapshot, now.date, now, timeZone)
        val midnight = now.date.plus(DatePeriod(days = 1)).atTime(0, 0)
        val edges = view.schedule.scheduled.flatMap { listOf(it.start, it.start.plusMinutes(it.durationMinutes)) } +
            view.dayBlocks.flatMap { listOf(it.start, it.end) }
        return (edges + midnight).filter { it > now }.min()
    }

    /** État du bouton « Fait » armé à [armedAt] (`null` : jamais armé), à l'instant [now]. */
    fun confirm(armedAt: Instant?, now: Instant): ConfirmState =
        if (armedAt != null && now >= armedAt && now - armedAt < CONFIRM_SECONDS.seconds) ConfirmState.ARMED else ConfirmState.IDLE

    /**
     * Le second toucher termine [taskId] seulement si elle est encore à faire dans la vue Perso : un broadcast tardif (widget
     * périmé) ne rouvre jamais une tâche faite, et une tâche d'un collègue n'est jamais touchée.
     */
    fun canComplete(snapshot: KairosSnapshot, taskId: Long): Boolean = snapshot.tasks.any { it.id == taskId && it.status == TaskStatus.TODO }

    private fun widgetTask(task: Task, view: DayView): WidgetTask {
        val placed: Scheduling.ScheduledTask? = view.schedule.scheduled.firstOrNull { it.task.id == task.id }
        return WidgetTask(
            id = task.id,
            title = task.title,
            start = placed?.start,
            end = placed?.let { it.start.plusMinutes(it.durationMinutes) },
            priority = task.priority,
            project = task.projectTag,
            type = task.taskType,
            deepWork = placed?.deepwork == true,
            estimateMinutes = task.estimatedMinutes?.takeIf { it > 0 } ?: view.learned[task.id]?.takeIf { it.reliable }?.minutes,
            pinned = placed?.pinned == true,
        )
    }
}
