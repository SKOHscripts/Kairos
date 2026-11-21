package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Durées réelles (docs/spec/durees-reelles.md, issue #271) : à quelle tâche demander « Temps passé ? » à la coche, et lesquelles
 * revoir dans le Bilan. Pur : l'instantané reçu est l'instantané **perso** (l'isolation de l'espace Équipe vient de là).
 */
object SpentReview {
    /** Durées proposées en un toucher, les mêmes à la coche et dans la revue. */
    val CHOICES: List<Int> = listOf(15, 30, 45, 60, 120)

    /** Fenêtre de la revue, aujourd'hui compris : au-delà, personne ne se souvient de la durée. */
    const val WINDOW_DAYS = 7

    /** Deux coches à moins de ce délai sont un lot (rangement), pas deux fins de travail : pas de question. */
    const val BATCH_SECONDS = 60

    /** Borne de « Autre… » (minutes). */
    const val MAX_MINUTES = 1440

    /**
     * Sans aucun temps : tâche perso, sans séance de chrono (ouverte ou fermée), sans temps saisi (`null` ; 0 = ignorée par la
     * revue, donc connue), sans temps rapporté, et sans sous-tâche (son temps est celui de ses sous-tâches).
     */
    fun untimed(snapshot: KairosSnapshot, task: Task): Boolean =
        task.space == TaskSpace.PERSONAL &&
            task.manualTimeSpentMinutes == null &&
            task.reportedMinutes == null &&
            snapshot.workSessions.none { it.taskId == task.id } &&
            snapshot.tasks.none { it.parentId == task.id }

    /**
     * Faut-il demander le temps passé en cochant [task] ? Lu **avant** la coche, sur la tâche encore à faire : la coche change
     * l'instantané de façon asynchrone, et une séance ouverte existe déjà avant (la fermer ne change pas la réponse).
     */
    fun asks(snapshot: KairosSnapshot, task: Task): Boolean =
        snapshot.settings.askSpent && task.status == TaskStatus.TODO && untimed(snapshot, task)

    /** La coche à [now] suit-elle la précédente ([previous]) de moins de [BATCH_SECONDS] ? */
    fun batched(previous: Instant?, now: Instant): Boolean =
        previous != null && (now - previous).inWholeSeconds in 0 until BATCH_SECONDS

    /** Jour où une tâche faite compte comme faite : la date locale de `updatedAt`, la convention du Bilan (docs/spec/bilan.md). */
    fun doneOn(task: Task, timeZone: TimeZone): LocalDate = task.updatedAt.toLocalDateTime(timeZone).date

    /**
     * Les tâches faites sans aucun temps dont le jour de fin est dans les [WINDOW_DAYS] derniers jours (aujourd'hui compris), la
     * plus récemment faite d'abord, puis par titre.
     */
    fun review(snapshot: KairosSnapshot, today: LocalDate, timeZone: TimeZone): List<Task> {
        val first = today.minus(DatePeriod(days = WINDOW_DAYS - 1))
        return snapshot.tasks
            .filter { it.status == TaskStatus.DONE && doneOn(it, timeZone) in first..today && untimed(snapshot, it) }
            .sortedWith(compareByDescending<Task> { it.updatedAt }.thenBy { it.title })
    }
}
