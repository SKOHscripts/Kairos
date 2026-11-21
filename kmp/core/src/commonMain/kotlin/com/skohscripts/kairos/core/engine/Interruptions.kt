package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.WorkSession
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Découpe de la séance de chrono par un dérangement signalé après coup (docs/spec/derangements.md § Découpe de la séance, issue
 * #272). Pur : rend les écritures, sans les faire.
 */
object Interruptions {
    /**
     * Ce que le signalement d'un dérangement de [minutes] à [now] change aux séances : [running] la séance ouverte (ou rien) ;
     * [cutAt] la nouvelle fin de [running] (`null` : retirée, le dérangement couvre toute la séance commencée) ; [resumeAt] le début
     * de la séance qui reprend (la même tâche). [before] : la séance d'origine, pour « Annuler ».
     */
    data class Change(val running: WorkSession?, val cutAt: Instant?, val resumeAt: Instant?) {
        val before: WorkSession? get() = running
    }

    /**
     * Sans séance ouverte, rien ne change aux séances. Avec une séance ouverte `s` : `cut = now − minutes` ; si `cut` est après le
     * début de `s`, `s` se termine à `cut`, sinon elle est retirée ; une nouvelle séance de la même tâche reprend à [now].
     */
    fun report(sessions: List<WorkSession>, now: Instant, minutes: Int): Change {
        val running = TimeTracking.runningSession(sessions) ?: return Change(null, null, null)
        val cut = now - minutes.minutes
        return Change(running, cutAt = cut.takeIf { it > running.startedAt }, resumeAt = now)
    }
}
