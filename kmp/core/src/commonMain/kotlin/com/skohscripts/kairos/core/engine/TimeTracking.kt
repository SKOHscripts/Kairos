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
 * Temps réellement passé (docs/spec-v3/temps-reel-chrono.md), portage de
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
     * Total par tâche : sessions (ouvertes comprises) **plus** le temps saisi à
     * la main (`manualTimeSpentMinutes`) : l'un complète l'autre, jamais ne le
     * remplace.
     */
    fun spentMinutesByTask(sessions: List<WorkSession>, now: Instant, tasks: List<Task> = emptyList()): Map<Long, Int> {
        val totals = LinkedHashMap<Long, Int>()
        for (s in sessions) totals[s.taskId] = (totals[s.taskId] ?: 0) + sessionMinutes(s, now)
        for (t in tasks) {
            val manual = t.manualTimeSpentMinutes ?: continue
            if (manual != 0) totals[t.id] = (totals[t.id] ?: 0) + manual
        }
        return totals
    }

    /** La session ouverte, ou `null` ; la plus récente s'il en restait plusieurs (invariant : une seule). */
    fun runningSession(sessions: List<WorkSession>): WorkSession? =
        sessions.filter { it.endedAt == null }.maxByOrNull { it.startedAt }

    fun totalMinutes(sessions: List<WorkSession>, now: Instant): Int = sessions.sumOf { sessionMinutes(it, now) }

    /**
     * Sessions dont le **début** tombe dans `[start, end]`, dates locales de
     * [timeZone]. C'est ce filtre qui fait du « temps travaillé aujourd'hui »
     * le temps du jour, et non tout l'historique (bogue de Kairos 2, phase 7).
     */
    fun sessionsInRange(sessions: List<WorkSession>, start: LocalDate, end: LocalDate, timeZone: TimeZone): List<WorkSession> =
        sessions.filter { it.startedAt.toLocalDateTime(timeZone).date in start..end }

    fun sessionsOnDay(sessions: List<WorkSession>, day: LocalDate, timeZone: TimeZone): List<WorkSession> =
        sessionsInRange(sessions, day, day, timeZone)

    /** Minutes par type de tâche ; tâche sans type ou disparue : clé `""`. */
    fun spentMinutesByType(sessions: List<WorkSession>, typeById: Map<Long, String>, now: Instant): Map<String, Int> {
        val totals = LinkedHashMap<String, Int>()
        for (s in sessions) {
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
        val workdayStart = day.atTime(settings.workdayStartHour, 0)
        val workdayEnd = day.atTime(settings.workdayEndHour, 0)
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
