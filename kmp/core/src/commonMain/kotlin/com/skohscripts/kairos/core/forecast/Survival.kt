package com.skohscripts.kairos.core.forecast

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Ce qui finit par se faire (docs/spec/previsions-taches.md § Étape E) : pour une tâche ouverte depuis `a` jours, la part
 * des tâches de même priorité, encore ouvertes au même âge, qui ont été faites dans les [WINDOW_DAYS] jours suivants.
 * Estimateur de Kaplan-Meier (produit de `1 − faites / encore ouvertes` à chaque âge où une tâche est faite) : les tâches
 * toujours ouvertes comptent comme « pas encore faites à leur âge », au lieu d'être ignorées, ce qui ferait croire que
 * tout finit par se faire. `× ÷` seulement : reproductible au bit près. Pur.
 */
object Survival {
    /** Tâches du groupe (même priorité) sous lesquelles on regroupe toutes les priorités. */
    const val MIN_GROUP = 30

    /** Tâches encore ouvertes à l'âge demandé sous lesquelles on ne dit rien. */
    const val MIN_AT_RISK = 10

    /** Fenêtre de la question : « faites dans les 90 jours suivants ». */
    const val WINDOW_DAYS = 90

    /**
     * Parmi [atRisk] tâches encore ouvertes après [ageDays] jours, [donePerTen] sur 10 ont été faites dans les
     * [WINDOW_DAYS] jours suivants (arrondi vers le bas). [samePriority] : le groupe est celui de la priorité de la
     * tâche ; sinon toutes les priorités (groupe trop petit).
     */
    data class Outlook(val ageDays: Int, val donePerTen: Int, val atRisk: Int, val samePriority: Boolean)

    /** Une vie de tâche : son âge en jours à la fin de l'observation, et si elle a été faite ([done]) ou est encore ouverte. */
    internal class Life(val days: Int, val done: Boolean, val priority: Int?, val undated: Boolean = false)

    /** Les vies des tâches perso non récurrentes, préparées une fois pour toutes les questions. */
    class Model internal constructor(private val lives: List<Life>, private val today: LocalDate, private val timeZone: TimeZone) {
        /** La réponse pour [task] (à faire, perso, non récurrente), ou `null` : trop peu de tâches encore ouvertes à cet âge. */
        fun of(task: Task): Outlook? {
            if (task.status != TaskStatus.TODO || !eligible(task)) return null
            val age = maxOf(0, task.createdAt.toLocalDateTime(timeZone).date.daysUntil(today))
            val same = lives.filter { it.priority == task.priority }
            val group = if (same.size >= MIN_GROUP) same else lives
            val atRisk = group.count { it.days >= age }
            if (atRisk < MIN_AT_RISK) return null
            val open = survival(group, age)
            if (open <= 0.0) return null
            val share = 1.0 - survival(group, age + WINDOW_DAYS) / open
            return Outlook(age, (share * 10.0).toInt().coerceIn(0, 10), atRisk, samePriority = group === same)
        }
    }

    private fun eligible(task: Task) = task.space == TaskSpace.PERSONAL && task.recurrence == TaskRecurrence.NONE && task.seriesId == null

    /**
     * [tasks] : toutes les tâches ; seules comptent les tâches perso sans récurrence (une occurrence de série vit par
     * construction un jour ou une semaine). Faite : sa vie s'arrête à la fin de sa dernière session, à défaut à sa dernière
     * modification ; à faire : observée jusqu'à [now] ; archivée : observée jusqu'à sa dernière modification, sans dire
     * qu'elle a été faite.
     */
    fun prepare(tasks: List<Task>, sessions: List<WorkSession>, now: Instant, timeZone: TimeZone): Model =
        Model(lives(tasks, sessions, now, timeZone), now.toLocalDateTime(timeZone).date, timeZone)

    /** Part encore ouverte à laquelle une tâche sans date « traîne » : plus lente que 4 sur 5 (80ᵉ centile). */
    const val LINGER_OPEN = 0.2

    /**
     * Échéance implicite apprise (docs/spec/taches-qui-trainent.md § Échéance apprise) : l'âge où la part encore ouverte des
     * tâches sans date (toutes, faute de [MIN_GROUP]) tombe à [LINGER_OPEN], au moins l'horizon d'urgence ; `null` si
     * l'apprentissage est coupé, l'échéance à 0, l'historique trop court, ou si la part ne descend jamais si bas.
     */
    fun lingerDays(snapshot: KairosSnapshot, now: Instant, timeZone: TimeZone): Int? {
        val settings = snapshot.settings
        if (!settings.implicitDeadlineAuto || settings.implicitDeadlineDays <= 0) return null
        val all = lives(snapshot.tasks, snapshot.workSessions, now, timeZone)
        val undated = all.filter { it.undated }
        val group = if (undated.size >= MIN_GROUP) undated else all
        if (group.size < MIN_GROUP) return null
        // Une passe de Kaplan-Meier (même calcul que [survival]) : le premier âge où la part ouverte tombe assez bas.
        var open = 1.0
        var atRisk = group.size
        for ((age, same) in group.groupBy { it.days }.toList().sortedBy { it.first }) {
            val done = same.count { it.done }
            if (done > 0) {
                open *= 1.0 - done.toDouble() / atRisk
                if (open <= LINGER_OPEN) return maxOf(age, settings.urgencyHorizonDays)
            }
            atRisk -= same.size
        }
        return null
    }

    private fun lives(tasks: List<Task>, sessions: List<WorkSession>, now: Instant, timeZone: TimeZone): List<Life> {
        val lastEnd = HashMap<Long, Instant>()
        for (s in sessions) {
            val end = s.endedAt ?: continue
            val known = lastEnd[s.taskId]
            if (known == null || end > known) lastEnd[s.taskId] = end
        }
        fun days(task: Task, until: Instant) =
            maxOf(0, task.createdAt.toLocalDateTime(timeZone).date.daysUntil(until.toLocalDateTime(timeZone).date))
        return tasks.filter(::eligible).map { t ->
            val undated = t.deadline == null && t.scheduledDate == null
            when (t.status) {
                TaskStatus.DONE -> Life(days(t, lastEnd[t.id]?.takeIf { it >= t.createdAt } ?: t.updatedAt), done = true, t.priority, undated)
                TaskStatus.TODO -> Life(days(t, now), done = false, t.priority, undated)
                TaskStatus.ARCHIVED -> Life(days(t, t.updatedAt), done = false, t.priority, undated)
            }
        }
    }

    /** Part des tâches de [lives] encore ouvertes après [days] jours (Kaplan-Meier ; les âges dans l'ordre croissant). */
    private fun survival(lives: List<Life>, days: Int): Double {
        var s = 1.0
        var atRisk = lives.size
        // Par âge croissant : à chaque âge, les tâches faites ce jour-là réduisent la part, puis toutes celles de cet âge sortent.
        val byAge = lives.groupBy { it.days }.toList().sortedBy { it.first }
        for ((age, group) in byAge) {
            if (age > days) break
            val done = group.count { it.done }
            if (done > 0 && atRisk > 0) s *= 1.0 - done.toDouble() / atRisk
            atRisk -= group.size
        }
        return s
    }
}
