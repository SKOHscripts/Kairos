package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.engine.Dependencies
import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Filtre du Suivi et du Backlog (docs/spec/equipe-backlog-suivi.md). Tous les
 * critères se cumulent ; un critère `null` (ou faux) ne filtre pas.
 *
 * - [text] : sous-chaîne du titre, sans tenir compte de la casse ;
 * - [category] : `Task.taskType` ; `""` = « sans catégorie » ;
 * - [priority] : 0, 1 ou 2 ;
 * - [memberId] : tâches de ce membre (le backlog est alors vide, et seule sa
 *   ligne reste dans le Suivi) ;
 * - [onlyWatched] : tâches portant au moins un signal ;
 * - [withDeadline] : tâches qui ont une échéance.
 */
data class TeamBoardFilter(
    val text: String = "",
    val category: String? = null,
    val priority: Int? = null,
    val memberId: Long? = null,
    val onlyWatched: Boolean = false,
    val withDeadline: Boolean = false,
)

/** Une tâche dans le tableau : état, blocage, signaux et score. [doneOn] : jour de fin d'une tâche faite. */
data class TeamCard(
    val task: Task,
    val state: TeamState,
    val blocked: Boolean,
    val signals: Set<TeamSignal>,
    /** Score WSJF du jour (chiffre primaire du Backlog). */
    val score: Double,
    val doneOn: LocalDate? = null,
)

/** Les quatre chiffres clés du Suivi. */
data class TeamKeyFigures(
    val inProgress: Int,
    val doneThisWeek: Int,
    val overdue: Int,
    val watched: Int,
)

/** Ligne d'un membre actif : ses cartes par état et le signal « trop d'en-cours » (porté par la ligne). */
data class MemberLane(
    val member: TeamMember,
    val inProgress: List<TeamCard>,
    val todo: List<TeamCard>,
    /** Faites dans les 7 derniers jours, la plus récente d'abord. */
    val done: List<TeamCard>,
    /** Tâches en cours du membre (filtre non appliqué). */
    val inProgressCount: Int,
    val wipExceeded: Boolean,
)

/**
 * Tableau de l'espace Équipe : Suivi (chiffres clés, lignes de membres) et
 * Backlog (À qualifier, Prêtes). Pur : le jour et le fuseau sont passés.
 */
data class TeamBoard(
    val keyFigures: TeamKeyFigures,
    val lanes: List<MemberLane>,
    /** Backlog à qualifier : il manque la priorité ou les points (`needsProcessing`), dans l'ordre des identifiants. */
    val toQualify: List<TeamCard>,
    /** Backlog prêt : trié par `Scheduling.sortKey` (en retard d'abord), urgence héritée des dépendances. */
    val ready: List<TeamCard>,
) {
    companion object {
        /** Durée de la fenêtre « Faites » d'une ligne de membre. */
        const val DONE_WINDOW_DAYS = 7

        /**
         * [snapshot] est la base **complète** (journal, membres, dépendances,
         * réglages) ; seules les tâches d'équipe non archivées comptent.
         */
        fun build(
            snapshot: KairosSnapshot,
            day: LocalDate,
            timeZone: TimeZone,
            filter: TeamBoardFilter = TeamBoardFilter(),
        ): TeamBoard {
            val settings = snapshot.settings
            val holidays = Workdays.holidaysFor(day, settings.holidaysFr, settings.extraHolidays)
            val tasks = snapshot.tasks.filter { it.space == TaskSpace.TEAM && it.status != TaskStatus.ARCHIVED }
            val eventsByTask = snapshot.teamEvents.groupBy { it.taskId }
            val blockedIds = TeamStates.blockedIds(snapshot)

            // Urgence héritée comme la vue Jour : un bloqueur prend la clé la plus urgente de ce qu'il bloque.
            val teamIds = tasks.mapTo(HashSet()) { it.id }
            val edges = snapshot.dependencies
                .filter { it.taskId in teamIds && it.blockerId in teamIds }
                .map { Dependencies.Edge(blocked = it.taskId, blocker = it.blockerId) }
            val own = tasks.filter { it.status == TaskStatus.TODO }.associate { it.id to Scheduling.sortKey(it, day, settings) }
            val effective = Dependencies.derivedUrgency(edges, own)

            fun card(task: Task): TeamCard {
                val state = checkNotNull(TeamStates.of(task))
                return TeamCard(
                    task = task,
                    state = state,
                    blocked = task.id in blockedIds,
                    signals = TeamSignals.of(task, eventsByTask[task.id].orEmpty(), day, timeZone, settings, holidays),
                    score = Scheduling.wsjfScore(task, day, settings),
                    doneOn = if (state == TeamState.DONE) doneOn(task, eventsByTask[task.id].orEmpty(), timeZone) else null,
                )
            }

            val needle = filter.text.trim().lowercase()
            fun matches(card: TeamCard): Boolean {
                val t = card.task
                return (needle.isEmpty() || needle in t.title.lowercase()) &&
                    (filter.category == null || t.taskType == filter.category) &&
                    (filter.priority == null || t.priority == filter.priority) &&
                    (filter.memberId == null || t.assigneeId == filter.memberId) &&
                    (!filter.onlyWatched || card.signals.isNotEmpty()) &&
                    (!filter.withDeadline || t.deadline != null)
            }

            val cards = tasks.map(::card).filter(::matches)
            val byUrgency = compareBy<TeamCard> { effective[it.task.id] ?: Scheduling.sortKey(it.task, day, settings) }

            val weekStart = day.minus(DatePeriod(days = Workdays.weekday(day)))
            val windowStart = day.minus(DatePeriod(days = DONE_WINDOW_DAYS - 1))
            fun recentlyDone(c: TeamCard) = c.doneOn != null && c.doneOn >= windowStart && c.doneOn <= day
            val open = cards.filter { it.state != TeamState.DONE }

            val lanes = TeamMembers.active(snapshot.members)
                .filter { filter.memberId == null || it.id == filter.memberId }
                .map { member ->
                    val mine = cards.filter { it.task.assigneeId == member.id }
                    MemberLane(
                        member = member,
                        inProgress = mine.filter { it.state == TeamState.IN_PROGRESS }.sortedWith(byUrgency),
                        todo = mine.filter { it.state == TeamState.TODO }.sortedWith(byUrgency),
                        done = mine.filter { it.state == TeamState.DONE && recentlyDone(it) }
                            .sortedWith(compareByDescending<TeamCard> { it.doneOn }.thenByDescending { it.task.id }),
                        inProgressCount = TeamSignals.inProgressCount(tasks, member.id),
                        wipExceeded = TeamSignals.wipExceeded(tasks, member.id, settings),
                    )
                }

            val backlog = cards.filter { it.state == TeamState.BACKLOG }
            return TeamBoard(
                keyFigures = TeamKeyFigures(
                    inProgress = open.count { it.state == TeamState.IN_PROGRESS },
                    doneThisWeek = cards.count { it.state == TeamState.DONE && it.doneOn != null && it.doneOn >= weekStart && it.doneOn <= day },
                    overdue = open.count { TeamSignal.OVERDUE in it.signals },
                    watched = open.count { it.signals.isNotEmpty() },
                ),
                lanes = lanes,
                toQualify = backlog.filter { it.task.needsProcessing }.sortedBy { it.task.id },
                ready = backlog.filter { !it.task.needsProcessing }.sortedWith(byUrgency),
            )
        }

        /**
         * Jour de fin d'une tâche faite : date locale de son dernier événement
         * `done`, à défaut celle de `updatedAt` (règle des statistiques).
         */
        fun doneOn(task: Task, events: List<TeamEvent>, timeZone: TimeZone): LocalDate {
            val last = events.filter { it.taskId == task.id && it.kind == TeamEventKind.DONE }.maxOfOrNull { it.at }
            return (last ?: task.updatedAt).toLocalDateTime(timeZone).date
        }
    }
}
