package com.skohscripts.kairos.core.day

import com.skohscripts.kairos.core.engine.Dependencies
import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.Recurrence
import com.skohscripts.kairos.core.engine.Schedule
import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.engine.Staleness
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.BlockException
import com.skohscripts.kairos.core.model.TaskTree
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.DayMark
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.stats.BestHours
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
 * Filtre d'affichage de la vue Jour (docs/spec/vue-jour.md § Filtres) :
 * recherche plein texte (titre, description, projet) et facettes. Ne change
 * jamais l'ordonnancement, seulement les listes montrées.
 */
data class DayFilter(
    val query: String = "",
    val priority: Int? = null,
    val project: String? = null,
    val taskType: String? = null,
    val points: Int? = null,
    /**
     * Facette « Incomplètes » (issue #166) : ne garder que les tâches à qui manque **au moins un** des champs choisis
     * (vide = facette inactive). Mêmes règles que la carte « Complétude » des statistiques (`TaskStats.completeness`).
     */
    val missing: Set<MissingField> = emptySet(),
) {
    val active: Boolean
        get() = query.isNotBlank() || priority != null || !project.isNullOrEmpty() || !taskType.isNullOrEmpty() || points != null ||
            missing.isNotEmpty()

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
        if (missing.isNotEmpty() && missing.none { it.isMissing(task) }) return false
        return true
    }
}

/** Un champ que la facette « Incomplètes » cherche vide ; « vide » comme dans la carte « Complétude » des statistiques. */
enum class MissingField(val isMissing: (Task) -> Boolean) {
    TYPE({ it.taskType.isEmpty() }),
    POINTS({ (it.fibonacciPoints ?: 0) == 0 }),
    DURATION({ (it.estimatedMinutes ?: 0) == 0 }),
}

/** Tâche bloquée et titres de ses bloqueurs directs encore à faire (« Mère › Fille » pour une sous-tâche). */
data class BlockedTask(val task: Task, val blockers: List<String>)

/**
 * Un créneau stocké qui concerne le jour affiché, pour la liste modifiable de la vue Jour : [template] est la ligne
 * stockée (ponctuel ou modèle récurrent), [occurrence] ce qu'il est ce jour-là (heures et titre de l'exception
 * appliqués), [exception] l'exception de ce jour s'il y en a une (issue #110) ; une occurrence sautée reste listée,
 * pour pouvoir la rétablir.
 */
data class EditableBlock(val template: TimeBlock, val occurrence: TimeBlock, val exception: BlockException? = null) {
    val id: Long get() = template.id
    val skipped: Boolean get() = exception?.skipped == true
}

/** Tâche mère ayant encore des sous-tâches à faire : avancement n/m. */
data class ParentProgress(val task: Task, val done: Int, val total: Int)

/**
 * Tout ce qu'affiche la vue Jour pour [day], dérivé d'un seul état de la base
 * (partie Jour du contexte). Pur : l'heure, le fuseau
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
    /**
     * Pour chaque bloqueur encore à faire, les titres des tâches qu'il bloque (blocage direct, ces tâches étant à faire) :
     * le badge « bloque … » de sa ligne (docs/spec/planning-personnalise.md § Lot A, 3).
     */
    val blocking: Map<Long, List<String>> = emptyMap(),
    val openTasks: List<Task>,
    val priorityOverloadCount: Int,
    val priorityOverloadThreshold: Int,
    val projects: List<String>,
    /** Créneaux stockés concernant ce jour (ponctuels du jour, modèles récurrents qui y tombent, occurrence sautée comprise). */
    val editableBlocks: List<EditableBlock>,
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
    /** Heures de début et de fin de la plage affichée par la frise ce jour-là (horaire du jour ou journée des réglages). */
    val workStartHour: Int = 9,
    val workEndHour: Int = 18,
    /** Jour libre (docs/spec/planning-personnalise.md § Lot B1) : rien n'est planifié automatiquement. */
    val freeDay: Boolean = false,
    /** Durées apprises (fiables ou non) des tâches à faire sans estimation explicite : badge, « Pourquoi à cette place ? », puce de la fiche. */
    val learned: Map<Long, DurationEstimator.Estimate> = emptyMap(),
    /** Les plages de travail du jour, dans l'ordre (docs/spec/planning-personnalise.md § Lot B2) ; vide un jour libre. La frise grise les trous entre elles. */
    val windows: List<Schedule.Window> = emptyList(),
    /** Tâches « Sans créneau » dont le contexte n'a aucune plage ce jour-là : identifiant → nom du contexte ([Scheduling.ScheduledDay.noWindow]). */
    val noWindow: Map<Long, String> = emptyMap(),
    /** Journées entières des calendriers importés qui couvrent ce jour (badges, sans effet sur le planning ; issue #136). */
    val dayMarks: List<DayMark> = emptyList(),
    /**
     * Sous-tâches planifiées avec l'échéance d'un ancêtre (docs/spec/sous-taches.md § Échéance) : identifiant → titre de
     * l'ancêtre. Leur `deadline` dans les listes de la vue est déjà celle-là ; la ligne dit d'où elle vient.
     */
    val inheritedDeadline: Map<Long, String> = emptyMap(),
    /** Agrégat de chaque tâche qui a des sous-tâches (docs/spec/sous-taches.md § Agrégation) : nombre, durées, temps passé. */
    val rollups: Map<Long, TaskTree.Rollup> = emptyMap(),
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
            val stored = snapshot.tasks.filter { it.status != TaskStatus.ARCHIVED }
            // Échéance héritée (docs/spec/sous-taches.md § Échéance) : une sous-tâche à faire sans échéance, ou plus tardive que
            // celle d'un ancêtre, est planifiée et listée avec celle de l'ancêtre. La fiche relit la tâche stockée : rien ne s'écrit.
            val deadlineFrom = TaskTree.inheritedDeadlines(stored, stored.associateBy { it.id })
            val all = stored.map { t -> deadlineFrom[t.id]?.let { t.copy(deadline = it.deadline) } ?: t }
            val open = all.filter { it.status == TaskStatus.TODO }
            val byId = all.associateBy { it.id }
            val statusById = snapshot.tasks.associate { it.id to it.status }
            val isTodo: (Long) -> Boolean = { (statusById[it] ?: TaskStatus.TODO) == TaskStatus.TODO }
            val edges = snapshot.dependencies.map { Dependencies.Edge(blocked = it.taskId, blocker = it.blockerId) }
            val directlyBlocked = Dependencies.blockedTaskIds(edges, isTodo)
            // Une sous-tâche d'une mère bloquée est bloquée aussi : jamais planifiée, listée dans « Bloquées ».
            val inherited = Dependencies.inheritedBlock(
                directlyBlocked,
                open.filter { it.parentId != null && it.parentId in byId }.associate { it.id to it.parentId!! },
            )
            val blockedIds = directlyBlocked + inherited.keys

            val own = open.associate { it.id to Scheduling.sortKey(it, day, settings) }
            val effective = Dependencies.derivedUrgency(edges, own)
            val raised = effective.filter { (id, key) -> own[id]?.let { key < it } ?: false }.keys

            val dayStart = day.atTime(0, 0)
            val nextDayStart = day.plus(DatePeriod(days = 1)).atTime(0, 0)
            val oneOff = snapshot.timeBlocks.filter { it.recurrence == BlockRecurrence.NONE && it.start < nextDayStart && it.end > dayStart }
            val templates = snapshot.timeBlocks.filter { it.recurrence != BlockRecurrence.NONE }
            val dayBlocks = oneOff + Recurrence.expandRecurringBlocks(templates, day, day, holidays, snapshot.blockExceptions)
            val exceptionOf = snapshot.blockExceptions.filter { it.date == day }.associateBy { it.blockId }
            val editable = snapshot.timeBlocks.mapNotNull {
                when {
                    it.recurrence == BlockRecurrence.NONE -> if (it.start.date == day) EditableBlock(it, it) else null
                    Recurrence.occursOn(it, day, holidays) -> EditableBlock(it, Recurrence.occurrence(it, day, exceptionOf[it.id]), exceptionOf[it.id])
                    else -> null
                }
            }.sortedBy { it.occurrence.start.time }

            // Durées apprises des tâches sans estimation explicite (docs/spec/durees-apprises.md) : les fiables servent au placement.
            val learned = DurationEstimator.estimates(snapshot, instant, timeZone)
            // Heures habituelles ou préférées par type (étape C2) : mesurées seulement si le réglage est actif, sinon `null` (comme avant).
            val affinity = if (settings.schedule?.bestHours?.enabled == true) {
                BestHours.affinity(settings, BestHours.measure(snapshot.tasks, snapshot.workSessions, instant, settings, timeZone))
            } else {
                null
            }
            val schedule = Scheduling.buildDaySchedule(all, dayBlocks, day, now, settings, blockedIds, effective, DurationEstimator.appliedMinutes(learned), affinity)
            val timeline = Scheduling.buildTimeline(schedule, dayBlocks, day, settings)

            // Lignée complète (« A › C » pour D, docs/spec/sous-taches.md) : la mère directe seule perdait le lien vers A.
            val parentTitle = all.filter { it.parentId != null && it.parentId in byId }
                .mapNotNull { t -> TaskTree.path(t.id, byId)?.let { t.id to it } }.toMap()
            val breadcrumb = { id: Long ->
                val title = byId[id]?.title ?: "#$id"
                parentTitle[id]?.let { "$it › $title" } ?: title
            }
            val reasons = Dependencies.openBlockers(edges, isTodo)
            val blocking = reasons.filterKeys { it in directlyBlocked }.entries
                .flatMap { (blockedId, blockers) -> blockers.map { it to breadcrumb(blockedId) } }
                .groupBy({ it.first }, { it.second })
            val blocked = blockedIds.mapNotNull { id -> byId[id] }
                .filter(filter::matches)
                // Héritée : le motif est la mère qui attend (« en attente de : Mère »).
                .map { BlockedTask(it, inherited[it.id]?.let { mother -> listOf(breadcrumb(mother)) } ?: reasons[it.id].orEmpty().map(breadcrumb)) }
                .sortedBy { it.task.title }

            val children = all.filter { it.parentId != null }.groupBy { it.parentId!! }
            val parents = children.mapNotNull { (parentId, kids) ->
                val parent = byId[parentId] ?: return@mapNotNull null
                // Une mère bloquée reste dans « Bloquées » seulement : une tâche ouverte, une seule section.
                if (parent.status != TaskStatus.TODO || parent.id in blockedIds || kids.none { it.status == TaskStatus.TODO } ||
                    !filter.matches(parent)
                ) {
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

            val shown = Schedule.displayWindow(day, settings)
            // Agrégat des mères : durée de chaque feuille estimée, sinon apprise, sinon (faite) son temps réel.
            val minutesOf = { t: Task -> t.estimatedMinutes?.takeIf { it > 0 } ?: learned[t.id]?.minutes ?: spent[t.id]?.takeIf { it > 0 && t.status == TaskStatus.DONE } }
            val rollups = all.mapNotNull { it.parentId }.toSet().mapNotNull { id -> TaskTree.rollup(id, all, minutesOf, spent)?.let { id to it } }.toMap()
            return DayView(
                day = day,
                schedule = schedule,
                // Une bloquée non qualifiée reste dans « Bloquées » seulement : une tâche ouverte, une seule section.
                // Le moteur (figé par les fichiers d'or) la compte à traiter ; le filtre est ici, sur la vue.
                inbox = schedule.toProcess.filter { it.id !in blockedIds && filter.matches(it) },
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
                blocking = blocking,
                openTasks = open.sortedBy { it.title },
                priorityOverloadCount = Scheduling.countMaxPriority(open.filter { it.id !in blockedIds }),
                priorityOverloadThreshold = settings.priorityOverloadThreshold,
                projects = open.map { it.projectTag }.filter { it.isNotEmpty() }.distinct().sorted(),
                editableBlocks = editable,
                dayBlocks = dayBlocks.sortedBy { it.start },
                timeline = timeline,
                holidays = holidays,
                dayMarks = snapshot.dayMarks.filter { it.covers(day) }.sortedBy { it.title },
                inheritedDeadline = deadlineFrom.mapValues { it.value.title },
                rollups = rollups,
                spentByTask = spent,
                running = running,
                runningTask = runningTask,
                runningBaseMinutes = running?.let { (spent[it.taskId] ?: 0) - TimeTracking.sessionMinutes(it, instant) } ?: 0,
                spentToday = TimeTracking.totalMinutes(today, instant),
                spentByTypeToday = TimeTracking.spentMinutesByType(today, typeById, instant).filter { (k, v) -> k.isNotEmpty() && v > 0 },
                sessionTimeline = TimeTracking.sessionTimeline(today, day, all.associate { it.id to it.title }, settings, instant, timeZone),
                workStartHour = shown.startHour,
                workEndHour = shown.endHour,
                freeDay = Schedule.windowFor(day, settings) == null,
                learned = learned,
                windows = Schedule.windowsFor(day, settings),
                noWindow = schedule.noWindow,
            )
        }
    }
}
