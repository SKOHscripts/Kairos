package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.engine.Recurrence
import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlin.time.Instant

/**
 * Ce que les **calculs** de l'espace Équipe lisent (docs/spec/equipe-moi.md) : le pendant de
 * `Workspaces.personalView`. Les tâches perso de « moi » qui comptent dans l'équipe y deviennent des tâches
 * d'équipe assignées à « moi » ; les occurrences à venir des tâches récurrentes y sont projetées ; les créneaux
 * occupés de « moi » réduisent sa capacité. Pur : le jour, l'instant et l'horizon sont des paramètres.
 *
 * Les écrans d'équipe lisent toujours la base telle quelle (édition, journal, Suivi, Backlog) : seule la vue de
 * calcul voit les tâches perso. Elle est **idempotente** (une tâche convertie est déjà `TEAM`), donc un calcul qui
 * en appelle un autre ne double rien.
 */
object TeamWork {
    /** Garde-fou : occurrences projetées au plus par série (une série quotidienne sur 8 semaines en compte 56). */
    const val MAX_OCCURRENCES_PER_SERIES = 400

    private val NO_BUSY: (LocalDate) -> Double = { 0.0 }

    /** Le membre « moi » actif, si la gestion d'équipe est activée ; `null` sinon (aucune tâche perso ne compte alors). */
    fun self(snapshot: KairosSnapshot): TeamMember? =
        if (!snapshot.settings.teamModeEnabled) null else snapshot.members.firstOrNull { it.isSelf && !it.archived }

    /**
     * [task] est une tâche perso qui **peut** compter dans l'équipe (l'interrupteur de la fiche s'affiche) : perso,
     * non reçue d'un manager (le travail d'une autre équipe), non archivée.
     */
    fun countable(task: Task): Boolean =
        task.space == TaskSpace.PERSONAL && task.origin == null && task.status != TaskStatus.ARCHIVED

    /** [task] compte dans la charge et les prévisions de « moi » : comptable, non exclue, et « moi » existe. */
    fun counts(task: Task, snapshot: KairosSnapshot): Boolean = countable(task) && !task.teamExcluded && self(snapshot) != null

    /**
     * La vue de calcul : les tâches perso qui comptent deviennent des tâches `TEAM` assignées à « moi » (copies, même
     * identifiant), le reste est inchangé. Sans « moi » ou sans tâche qui compte, [snapshot] est rendu tel quel (même
     * instance).
     */
    fun view(snapshot: KairosSnapshot): KairosSnapshot {
        val me = self(snapshot) ?: return snapshot
        if (snapshot.tasks.none { countable(it) && !it.teamExcluded }) return snapshot
        return snapshot.copy(
            tasks = snapshot.tasks.map { if (countable(it) && !it.teamExcluded) it.copy(space = TaskSpace.TEAM, assigneeId = me.id) else it },
        )
    }

    /** Une occurrence **projetée** (virtuelle, jamais en base) : identifiant négatif. */
    fun isProjected(task: Task): Boolean = task.id < 0

    /**
     * Occurrences à venir des tâches récurrentes à faire de [view] (déjà passée par [view]), dont l'échéance tombe au
     * plus tard à [horizonEnd]. Une série = titre + règle + assigné, d'espace `TEAM` ; on part de sa dernière
     * occurrence à faire. Chaque occurrence copie la tâche, avec échéance et date programmée à son jour (disponible à
     * partir de ce jour, comme dans la vue Jour), avancement et temps nuls, `teamUid` dérivé stable, identifiant
     * négatif (−1, −2… dans l'ordre série puis date).
     */
    fun occurrences(view: KairosSnapshot, day: LocalDate, horizonEnd: LocalDate): List<Task> {
        val settings = view.settings
        val holidays = Workdays.holidaysFor(day, settings.holidaysFr, settings.extraHolidays)
        val recurring = view.tasks.filter { it.space == TaskSpace.TEAM && it.status == TaskStatus.TODO && it.recurrence != TaskRecurrence.NONE && it.id > 0 }
        val series = recurring.groupBy { Triple(it.title, it.recurrence, it.assigneeId) }.values.sortedBy { list -> list.minOf { it.id } }
        val out = ArrayList<Task>()
        var nextId = -1L
        for (list in series) {
            val rep = list.maxWith(compareBy<Task>({ it.deadline?.toEpochDays() ?: Long.MIN_VALUE }, { it.id }))
            val dates = seriesDates(rep, day, horizonEnd, holidays)
            for (date in dates) {
                out += project(rep, date, nextId, holidays)
                nextId--
            }
        }
        return out
    }

    private fun seriesDates(rep: Task, day: LocalDate, horizonEnd: LocalDate, holidays: Set<LocalDate>): List<LocalDate> {
        val dates = ArrayList<LocalDate>()
        when (rep.recurrence) {
            in Recurrence.COMPLETION_RULES -> {
                val anchor = if (rep.recurrence == TaskRecurrence.WEEKLY) rep.recurrenceDayOfWeek ?: rep.deadline?.let(Workdays::weekday) else null
                var base = maxOf(rep.deadline ?: day, day)
                while (dates.size < MAX_OCCURRENCES_PER_SERIES) {
                    val next = Recurrence.nextDeadline(rep.recurrence, base, anchor, holidays, Recurrence.monthlyAnchor(rep))
                    if (next > horizonEnd) break
                    dates += next
                    base = next
                }
            }
            TaskRecurrence.MONTHLY_ON_DAY -> {
                val dayOfMonth = rep.recurrenceDayOfMonth ?: return dates
                // Le mois couvert par l'occurrence à faire ; le mois courant est créé par `Recurrence.calendarOccurrences`.
                val covered = rep.deadline ?: day
                var year = covered.year
                var month = covered.month.ordinal + 1
                while (dates.size < MAX_OCCURRENCES_PER_SERIES) {
                    if (month == 12) { year++; month = 1 } else month++
                    val target = LocalDate(year, month, minOf(dayOfMonth, Recurrence.daysInMonth(year, month)))
                    val due = Workdays.onOrBeforeBusinessDay(target, holidays)
                    if (due > horizonEnd) break
                    if (due >= day) dates += due
                }
            }
            else -> Unit
        }
        return dates
    }

    private fun project(rep: Task, date: LocalDate, id: Long, holidays: Set<LocalDate>): Task = rep.copy(
        id = id,
        deadline = date,
        // Disponible dès le jour ouvré qui précède l'échéance : une hebdomadaire ancrée un dimanche ne pouvait sinon
        // commencer que le lundi, après son échéance, et la prévision la disait « en retard » en permanence.
        scheduledDate = Workdays.onOrBeforeBusinessDay(date, holidays),
        pinnedStart = rep.pinnedStart?.let { date.atTime(it.time) },
        status = TaskStatus.TODO,
        progressPercent = null,
        startedOn = null,
        manualTimeSpentMinutes = null,
        reportedMinutes = null,
        recurrencePeriod = "",
        teamUid = "${rep.teamUid ?: "task-${rep.id}"}@$date",
    )

    /**
     * Heures des créneaux **occupés** (ponctuels et récurrents) dans la journée de travail de [day] : ce que les
     * réunions retirent à la capacité de « moi » ce jour-là. Les fenêtres de deep work ne retirent rien.
     */
    fun busyHours(snapshot: KairosSnapshot, day: LocalDate): Double {
        val settings = snapshot.settings
        val start = day.atTime(settings.workdayStartHour, 0)
        val end = day.atTime(settings.workdayEndHour, 0)
        if (end <= start) return 0.0
        val busy = snapshot.timeBlocks.filter { it.kind == BlockKind.BUSY }
        if (busy.isEmpty()) return 0.0
        val oneOff = busy.filter { it.recurrence == BlockRecurrence.NONE && it.start < end && it.end > start }
        // Un créneau « jours ouvrés » n'occupe pas un férié (docs/spec/recurrence.md, issue #109).
        val holidays = Workdays.holidaysFor(day, settings.holidaysFr, settings.extraHolidays)
        val recurring = Recurrence.expandRecurringBlocks(busy.filter { it.recurrence != BlockRecurrence.NONE }, day, day, holidays, snapshot.blockExceptions)
        return Scheduling.busyMinutesInWindow(oneOff + recurring, start, end) / 60.0
    }

    /**
     * Heures occupées de « moi » par jour, mémorisées (le plan les lit pour chaque jour du garde-fou, à chaque
     * tirage) ; toujours 0 sans « moi » ou sans créneau occupé.
     */
    fun selfBusy(snapshot: KairosSnapshot): (LocalDate) -> Double {
        if (self(snapshot) == null || snapshot.timeBlocks.none { it.kind == BlockKind.BUSY }) return NO_BUSY
        val memo = HashMap<LocalDate, Double>()
        return { day -> memo.getOrPut(day) { busyHours(snapshot, day) } }
    }

    /** Temps passé (minutes) par tâche à faire : sessions jusqu'à [now], temps saisi et temps rapporté par un membre. */
    fun spentMinutes(snapshot: KairosSnapshot, now: Instant): Map<Long, Int> =
        TimeTracking.spentMinutesByTask(snapshot.workSessions, now, snapshot.tasks.filter { it.status == TaskStatus.TODO })

    /**
     * Ce que le posage reçoit (`LoadPlan.build` / `prepare`) : la vue avec les occurrences projetées jusqu'à
     * [horizonEnd], le temps passé par tâche et les heures occupées de « moi ».
     */
    class PlanInput(val snapshot: KairosSnapshot, val spent: Map<Long, Int>, val busy: (LocalDate) -> Double)

    fun planInput(snapshot: KairosSnapshot, day: LocalDate, now: Instant, horizonEnd: LocalDate): PlanInput {
        val view = view(snapshot)
        val projected = occurrences(view, day, horizonEnd)
        val planned = if (projected.isEmpty()) view else view.copy(tasks = view.tasks + projected)
        return PlanInput(planned, spentMinutes(view, now), selfBusy(view))
    }
}
