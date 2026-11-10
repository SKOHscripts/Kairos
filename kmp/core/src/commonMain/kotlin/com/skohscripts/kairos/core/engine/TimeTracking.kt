package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Temps réellement passé (docs/spec/temps-reel-chrono.md), portage de
 * `app/tasks_time.py` : agrégats purs sur les sessions de chrono. L'instant
 * courant et le fuseau sont des paramètres.
 */
object TimeTracking {
    /** Durée d'une session en minutes entières (jamais négative) ; ouverte, elle court jusqu'à [now]. */
    fun sessionMinutes(session: WorkSession, now: Instant): Int {
        val end = session.endedAt ?: now
        return maxOf(0L, (end - session.startedAt).inWholeSeconds.floorDiv(60L)).toInt()
    }

    /**
     * Sessions sans double compte (issue #195) : deux sessions d'une **même** tâche qui se chevauchent (import, session
     * corrigée à la main, fusion de synchronisation) ne comptent qu'une fois le temps commun. Dans l'ordre (début, id), le
     * début d'une session est rogné à la plus grande fin des précédentes de sa tâche ; une session entièrement couverte est
     * retirée. Les données ne changent pas : seul le calcul lit ces sessions. Sans chevauchement, la liste est rendue telle
     * quelle (ordre d'entrée gardé), donc les totaux sont ceux d'avant à la minute près ; deux sessions bout à bout ne se
     * fusionnent pas (l'arrondi par session resterait celui d'avant).
     */
    fun withoutOverlaps(sessions: List<WorkSession>, now: Instant): List<WorkSession> {
        val reach = HashMap<Long, Instant>()
        val kept = HashMap<Long, WorkSession?>()
        var changed = false
        for (s in sessions.sortedWith(compareBy({ it.startedAt }, { it.id }))) {
            val end = s.endedAt ?: now
            val r = reach[s.taskId]
            kept[s.id] = when {
                r == null || s.startedAt >= r -> s
                end > r -> s.copy(startedAt = r).also { changed = true }
                else -> null.also { changed = true }
            }
            reach[s.taskId] = if (r == null || end > r) end else r
        }
        return if (!changed) sessions else sessions.mapNotNull { kept[it.id] }
    }

    /**
     * Total par tâche : sessions (ouvertes comprises) **plus** le temps saisi à
     * la main (`manualTimeSpentMinutes`) **plus** le temps rapporté par un membre
     * (`reportedMinutes`, côté manager, docs/spec/equipe-echanges.md) : chacun complète les
     * autres, jamais ne les remplace. Seules les [tasks] données comptent pour les
     * deux derniers termes. Sessions qui se chevauchent : leur union ([withoutOverlaps]).
     */
    fun spentMinutesByTask(sessions: List<WorkSession>, now: Instant, tasks: List<Task> = emptyList()): Map<Long, Int> {
        val totals = LinkedHashMap<Long, Int>()
        for (s in withoutOverlaps(sessions, now)) totals[s.taskId] = (totals[s.taskId] ?: 0) + sessionMinutes(s, now)
        for (t in tasks) {
            val extra = (t.manualTimeSpentMinutes ?: 0) + (t.reportedMinutes ?: 0)
            if (extra != 0) totals[t.id] = (totals[t.id] ?: 0) + extra
        }
        return totals
    }

    /** La session ouverte, ou `null` ; la plus récente s'il en restait plusieurs (invariant : une seule). */
    fun runningSession(sessions: List<WorkSession>): WorkSession? =
        sessions.filter { it.endedAt == null }.maxByOrNull { it.startedAt }

    /** Minutes chronométrées, sessions qui se chevauchent comptées une fois ([withoutOverlaps]). */
    fun totalMinutes(sessions: List<WorkSession>, now: Instant): Int = withoutOverlaps(sessions, now).sumOf { sessionMinutes(it, now) }

    /**
     * Sessions dont le **début** tombe dans `[start, end]`, dates locales de
     * [timeZone]. C'est ce filtre qui fait du « temps travaillé aujourd'hui »
     * le temps du jour, et non tout l'historique.
     */
    fun sessionsInRange(sessions: List<WorkSession>, start: LocalDate, end: LocalDate, timeZone: TimeZone): List<WorkSession> =
        sessions.filter { it.startedAt.toLocalDateTime(timeZone).date in start..end }

    fun sessionsOnDay(sessions: List<WorkSession>, day: LocalDate, timeZone: TimeZone): List<WorkSession> =
        sessionsInRange(sessions, day, day, timeZone)

    /** Minutes par type de tâche ; tâche sans type ou disparue : clé `""`. Chevauchements comptés une fois ([withoutOverlaps]). */
    fun spentMinutesByType(sessions: List<WorkSession>, typeById: Map<Long, String>, now: Instant): Map<String, Int> {
        val totals = LinkedHashMap<String, Int>()
        for (s in withoutOverlaps(sessions, now)) {
            val key = typeById[s.taskId] ?: ""
            totals[key] = (totals[key] ?: 0) + sessionMinutes(s, now)
        }
        return totals
    }

    /**
     * Rail « réel » de la frise (`session_timeline_entries`) : les sessions du
     * jour bornées à la journée de travail ; une session ouverte court jusqu'à
     * [now]. Hauteur d'une minute au moins ; triées par position.
     */
    fun sessionTimeline(
        sessions: List<WorkSession>,
        day: LocalDate,
        titleById: Map<Long, String>,
        settings: Settings,
        now: Instant,
        timeZone: TimeZone,
    ): List<Scheduling.TimelineEntry> {
        val window = Schedule.displayWindow(day, settings)
        val workdayStart = window.start
        val workdayEnd = window.end
        return sessions.mapNotNull { s ->
            val start = s.startedAt.toLocalDateTime(timeZone)
            val end = (s.endedAt ?: now).toLocalDateTime(timeZone)
            val clampedStart = maxOf(start, workdayStart)
            val clampedEnd = minOf(end, workdayEnd)
            if (clampedEnd <= clampedStart) return@mapNotNull null
            Scheduling.TimelineEntry(
                kind = Scheduling.TimelineKind.SESSION,
                title = titleById[s.taskId] ?: "",
                start = start,
                end = end,
                topMinutes = minutesBetween(workdayStart, clampedStart).toInt(),
                heightMinutes = maxOf(1, minutesBetween(clampedStart, clampedEnd).toInt()),
            )
        }.sortedBy { it.topMinutes }
    }
}
