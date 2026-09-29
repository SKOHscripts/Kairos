package com.skohscripts.kairos.core.day

import com.skohscripts.kairos.core.engine.Dependencies
import com.skohscripts.kairos.core.engine.Recurrence
import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.engine.Staleness
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Filtre d'affichage de la vue Jour (docs/spec-v3/vue-jour.md § Filtres) :
 * recherche plein texte (titre, description, projet) et facettes. Ne change
 * jamais l'ordonnancement, seulement les listes montrées.
 */
data class DayFilter(
    val query: String = "",
    val priority: Int? = null,
    val project: String? = null,
    val taskType: String? = null,
    val points: Int? = null,
) {
    val active: Boolean
        get() = query.isNotBlank() || priority != null || !project.isNullOrEmpty() || !taskType.isNullOrEmpty() || points != null

    fun matches(task: Task): Boolean {
        val q = query.trim()
        if (q.isNotEmpty()) {
            val haystack = listOf(task.title, task.description, task.projectTag).filter { it.isNotEmpty() }.joinToString(" ")
            if (!haystack.lowercase().contains(q.lowercase())) return false
        }
        if (priority != null && task.priority != priority) return false
        if (!project.isNullOrEmpty() && task.projectTag != project) return false
        if (!taskType.isNullOrEmpty() && task.taskType != taskType) return false
        if (points != null && task.fibonacciPoints != points) return false
        return true
    }
}

/** Tâche bloquée et titres de ses bloqueurs directs encore à faire (« Mère › Fille » pour une sous-tâche). */
data class BlockedTask(val task: Task, val blockers: List<String>)

/** Tâche mère ayant encore des sous-tâches à faire : avancement n/m. */
data class ParentProgress(val task: Task, val done: Int, val total: Int)

/**
 * Tout ce qu'affiche la vue Jour pour [day], dérivé d'un seul état de la base
 * (`_build_kairos_context` de Kairos 2, partie Jour). Pur : l'heure, le fuseau
 * et le filtre sont des paramètres.
 */
data class DayView(
    val day: LocalDate,
    val schedule: Scheduling.ScheduledDay,
    /** Listes affichées (filtrées) ; le planning, lui, porte sur toutes les tâches. */
    val inbox: List<Task>,
    val agenda: List<Scheduling.ScheduledTask>,
    val unscheduled: List<Task>,
    val later: List<Task>,
    val blocked: List<BlockedTask>,
    val parents: List<ParentProgress>,
    val doneToday: List<Task>,
    val backlog: List<Task>,
    /** « Maintenant » : la première tâche placée, sinon la première sans créneau. */
    val nextUp: Task?,
    val nextUpStart: LocalDateTime?,
    /** Décomposition du score, pour les seules tâches qualifiées (priorité et points). */
    val why: Map<Long, Scheduling.WsjfBreakdown>,
    val buckets: Map<Long, Int>,
    val staleDays: Map<Long, Int>,
    /** Tâches remontées par une dépendance (« chemin critique »). */
    val raised: Set<Long>,
    val parentTitle: Map<Long, String>,
    /** Bloqueurs directs de chaque tâche (toutes arêtes), pour le dialogue d'édition. */
    val blockersOf: Map<Long, List<Long>>,
    val openTasks: List<Task>,
    val priorityOverloadCount: Int,
    val priorityOverloadThreshold: Int,
    val projects: List<String>,
    /** Créneaux stockés concernant ce jour (ponctuels du jour, modèles récurrents qui y tombent). */
    val editableBlocks: List<TimeBlock>,
    /** Créneaux effectifs du jour (occurrences des récurrents comprises). */
    val dayBlocks: List<TimeBlock>,
    val timeline: List<Scheduling.TimelineEntry>,
    val holidays: Set<LocalDate>,
    /** Temps passé par tâche (sessions et saisie manuelle), en minutes. */
    val spentByTask: Map<Long, Int> = emptyMap(),
    /** Session de chrono ouverte, sa tâche, et le temps déjà passé sur celle-ci avant la session. */
    val running: WorkSession? = null,
    val runningTask: Task? = null,
    val runningBaseMinutes: Int = 0,
    /** Temps chronométré aujourd'hui (sessions commencées ce jour), et par type (types non vides seulement). */
    val spentToday: Int = 0,
    val spentByTypeToday: Map<String, Int> = emptyMap(),
    /** Rail « réel » de la frise. */
    val sessionTimeline: List<Scheduling.TimelineEntry> = emptyList(),
) {
    /** Bandeau de surcharge : plus de P0 non bloquées que le seuil des réglages. */
    val priorityOverload: Boolean get() = priorityOverloadCount > priorityOverloadThreshold

    companion object {
        /**
         * [now] : heure locale du placement (`null` = début de journée) ;
         * [instant] : l'instant courant pour le temps passé (défaut : [now],
         * ou le début du jour, dans [timeZone]).
         */
        fun build(
            snapshot: KairosSnapshot,
            day: LocalDate,
            now: LocalDateTime?,
            timeZone: TimeZone,
            filter: DayFilter = DayFilter(),
            instant: Instant = (now ?: day.atTime(0, 0)).toInstant(timeZone),
        ): DayView {
            val settings = snapshot.settings
            val holidays = Workdays.holidaysFor(day, settings.holidaysFr, settings.extraHolidays)
            val all = snapshot.tasks.filter { it.status != TaskStatus.ARCHIVED }
            val open = all.filter { it.status == TaskStatus.TODO }
            val byId = all.associateBy { it.id }
            val statusById = snapshot.tasks.associate { it.id to it.status }
            val isTodo: (Long) -> Boolean = { (statusById[it] ?: TaskStatus.TODO) == TaskStatus.TODO }
            val edges = snapshot.dependencies.map { Dependencies.Edge(blocked = it.taskId, blocker = it.blockerId) }
            val blockedIds = Dependencies.blockedTaskIds(edges, isTodo)

            val own = open.associate { it.id to Scheduling.sortKey(it, day, settings) }
            val effective = Dependencies.derivedUrgency(edges, own)
            val raised = effective.filter { (id, key) -> own[id]?.let { key < it } ?: false }.keys

            val dayStart = day.atTime(0, 0)
            val nextDayStart = day.plus(DatePeriod(days = 1)).atTime(0, 0)
            val oneOff = snapshot.timeBlocks.filter { it.recurrence == BlockRecurrence.NONE && it.start < nextDayStart && it.end > dayStart }
            val templates = snapshot.timeBlocks.filter { it.recurrence != BlockRecurrence.NONE }
            val dayBlocks = oneOff + Recurrence.expandRecurringBlocks(templates, day, day)
            val editable = snapshot.timeBlocks.filter {
                if (it.recurrence == BlockRecurrence.NONE) it.start.date == day
                else Recurrence.expandRecurringBlocks(listOf(it), day, day).isNotEmpty()
            }.sortedBy { it.start.time }

            val schedule = Scheduling.buildDaySchedule(all, dayBlocks, day, now, settings, blockedIds, effective)
            val timeline = Scheduling.buildTimeline(schedule, dayBlocks, day, settings)

            val parentTitle = all.filter { it.parentId != null && it.parentId in byId }
                .associate { it.id to byId.getValue(it.parentId!!).title }
            val breadcrumb = { id: Long ->
                val title = byId[id]?.title ?: "#$id"
                parentTitle[id]?.let { "$it › $title" } ?: title
            }
            val reasons = Dependencies.openBlockers(edges, isTodo)
            val blocked = blockedIds.mapNotNull { id -> byId[id] }
                .filter(filter::matches)
                .map { BlockedTask(it, reasons[it.id].orEmpty().map(breadcrumb)) }
                .sortedBy { it.task.title }

            val children = all.filter { it.parentId != null }.groupBy { it.parentId!! }
            val parents = children.mapNotNull { (parentId, kids) ->
                val parent = byId[parentId] ?: return@mapNotNull null
                if (parent.status != TaskStatus.TODO || kids.none { it.status == TaskStatus.TODO } || !filter.matches(parent)) {
                    return@mapNotNull null
                }
                ParentProgress(parent, kids.count { it.status == TaskStatus.DONE }, kids.size)
            }

            val backlog = open.filter { it.deadline == null && it.scheduledDate == null && it.id !in blockedIds && filter.matches(it) }
                .sortedWith(compareBy<Task>({ it.priority == null }, { it.priority ?: 999 }, { it.title }))

            val doneToday = snapshot.tasks.filter {
                it.status == TaskStatus.DONE && it.updatedAt.toLocalDateTime(timeZone).date == day && filter.matches(it)
            }.sortedByDescending { it.updatedAt }

            val staleDays = open.mapNotNull { t ->
                Staleness.daysStale(t, day, settings.staleOverdueDays, settings.staleUntouchedDays)?.let { t.id to it }
            }.toMap()

            val directBlockers = snapshot.dependencies.groupBy({ it.taskId }, { it.blockerId })

            val sessions = snapshot.workSessions
            val spent = TimeTracking.spentMinutesByTask(sessions, instant, all)
            val running = TimeTracking.runningSession(sessions)
            val runningTask = running?.let { r -> byId[r.taskId] }
            val today = TimeTracking.sessionsOnDay(sessions, day, timeZone)
            val typeById = all.associate { it.id to it.taskType }

            return DayView(
                day = day,
                schedule = schedule,
                inbox = schedule.toProcess.filter(filter::matches),
                agenda = schedule.scheduled.filter { filter.matches(it.task) },
                unscheduled = schedule.unscheduled.filter(filter::matches),
                later = schedule.later.filter(filter::matches),
                blocked = blocked,
                parents = parents,
                doneToday = doneToday,
                backlog = backlog,
                nextUp = schedule.scheduled.firstOrNull()?.task ?: schedule.unscheduled.firstOrNull(),
                nextUpStart = schedule.scheduled.firstOrNull()?.start,
                why = open.filter { it.priority != null && it.fibonacciPoints != null }
                    .associate { it.id to Scheduling.wsjfBreakdown(it, day, settings) },
                buckets = open.associate { it.id to Scheduling.urgencyBucket(it, day) },
                staleDays = staleDays,
                raised = raised,
                parentTitle = parentTitle,
                blockersOf = directBlockers,
                openTasks = open.sortedBy { it.title },
                priorityOverloadCount = Scheduling.countMaxPriority(open.filter { it.id !in blockedIds }),
                priorityOverloadThreshold = settings.priorityOverloadThreshold,
                projects = open.map { it.projectTag }.filter { it.isNotEmpty() }.distinct().sorted(),
                editableBlocks = editable,
                dayBlocks = dayBlocks.sortedBy { it.start },
                timeline = timeline,
                holidays = holidays,
                spentByTask = spent,
                running = running,
                runningTask = runningTask,
                runningBaseMinutes = running?.let { (spent[it.taskId] ?: 0) - TimeTracking.sessionMinutes(it, instant) } ?: 0,
                spentToday = TimeTracking.totalMinutes(today, instant),
                spentByTypeToday = TimeTracking.spentMinutesByType(today, typeById, instant).filter { (k, v) -> k.isNotEmpty() && v > 0 },
                sessionTimeline = TimeTracking.sessionTimeline(today, day, all.associate { it.id to it.title }, settings, instant, timeZone),
            )
        }
    }
}
