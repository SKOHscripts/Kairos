package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.HistoryEstimates
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Chronos à vérifier (docs/spec/previsions-taches.md § Étape G) : une session bien plus longue que ce que la tâche prend
 * d'habitude est le plus souvent un chrono oublié, et tout ce qui apprend du temps passé (durées apprises, fourchettes,
 * capacité vécue, prévisions) l'avale tel quel. Pur : il **propose**, rien n'est corrigé sans accord.
 */
object SuspectSessions {
    /** Une session est suspecte au-delà de ce multiple de la durée habituelle (la règle des aberrants des durées apprises). */
    const val FACTOR = HistoryEstimates.OUTLIER_FACTOR

    /** … et seulement si elle la dépasse d'au moins tant de minutes : une tâche de 5 min qui en prend 20 n'est pas un oubli. */
    const val MIN_EXCESS_MINUTES = 60

    /** Sessions proposées au plus, les plus récentes. */
    const val MAX_SHOWN = 50

    /**
     * [session] a duré [minutes] alors que [task] prend d'habitude au plus [usualMinutes] (son estimation saisie, sinon le
     * haut de sa fourchette apprise fiable) ; [proposedEnd] la ramène à cette durée.
     */
    data class Suspect(val session: WorkSession, val task: Task, val minutes: Int, val usualMinutes: Int, val proposedEnd: Instant)

    /**
     * Les sessions **terminées** des tâches perso (non reçues d'un manager) du [snapshot] plus longues que [FACTOR] fois la
     * durée habituelle de leur tâche, les plus récentes d'abord. La durée habituelle ne lit jamais le temps de la tâche
     * elle-même (la chaîne des durées apprises l'exclut) : un chrono oublié ne se justifie pas tout seul. Sans estimation
     * saisie ni durée apprise fiable, on ne sait pas : la session n'est pas proposée.
     */
    fun find(snapshot: KairosSnapshot, now: Instant, timeZone: TimeZone? = null): List<Suspect> {
        val tasks = snapshot.tasks.filter { it.space == TaskSpace.PERSONAL && it.origin == null }.associateBy { it.id }
        val closed = snapshot.workSessions.filter { it.endedAt != null && it.taskId in tasks }
        if (closed.isEmpty()) return emptyList()
        val history = DurationEstimator.history(snapshot, now, timeZone).filter { it.space == TaskSpace.PERSONAL }
        val usual = HashMap<Long, Int?>()
        return closed.mapNotNull { s ->
            val task = tasks.getValue(s.taskId)
            val expected = usual.getOrPut(task.id) {
                task.estimatedMinutes?.takeIf { it > 0 }
                    ?: DurationEstimator.estimate(task, history, snapshot.settings)?.takeIf { it.reliable }?.let { it.high ?: it.minutes }
            } ?: return@mapNotNull null
            val minutes = TimeTracking.sessionMinutes(s, now)
            if (minutes > expected * FACTOR && minutes - expected >= MIN_EXCESS_MINUTES) Suspect(s, task, minutes, expected, s.startedAt + expected.minutes) else null
        }.sortedWith(compareByDescending<Suspect> { it.session.startedAt }.thenBy { it.session.id }).take(MAX_SHOWN)
    }
}
