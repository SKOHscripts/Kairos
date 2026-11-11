package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.WorkSession
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Corriger le temps passé d'une tâche par son total (docs/spec/fiche-tache.md § Temps passé) : plus, la différence va au
 * temps saisi à la main ; moins, le temps saisi baisse d'abord, puis les sessions terminées les plus récentes sont
 * raccourcies. Jamais de temps saisi négatif (l'import le refuse, et un chrono oublié resterait dans toutes les
 * statistiques qui lisent les sessions).
 */
object TimeCorrection {
    /**
     * Ce que l'enregistrement écrit : [manual] (`null` = aucun temps saisi), les nouvelles fins de sessions [ends]
     * (identifiant → fin), et [floor], le total sous lequel on ne peut pas descendre.
     */
    data class Plan(val manual: Int?, val ends: Map<Long, Instant>, val floor: Int)

    /** Total affiché par la fiche : le chronométré (chevauchements comptés une fois) plus le temps saisi. */
    fun total(sessions: List<WorkSession>, manual: Int?, now: Instant): Int = TimeTracking.totalMinutes(sessions, now) + (manual ?: 0)

    /**
     * Le plan qui amène le total des [sessions] d'une tâche plus [manual] à [target] minutes. [trimmable] faux (tâche
     * d'équipe ou reçue) : les sessions ne bougent pas, le plancher est tout le chronométré. Un chrono en cours n'est
     * jamais raccourci. Une cible sous le plancher y est ramenée.
     */
    fun plan(sessions: List<WorkSession>, manual: Int?, target: Int, now: Instant, trimmable: Boolean): Plan {
        val timed = TimeTracking.totalMinutes(sessions, now)
        val floor = if (trimmable) TimeTracking.totalMinutes(sessions.filter { it.endedAt == null }, now) else timed
        val goal = maxOf(target, floor)
        if (goal >= timed) return Plan((goal - timed).takeIf { it > 0 }, emptyMap(), floor)
        // Les plus récentes d'abord : le plus souvent, c'est le chrono oublié.
        val current = sessions.associateBy { it.id }.toMutableMap()
        val order = sessions.filter { it.endedAt != null }.sortedWith(compareByDescending<WorkSession> { it.startedAt }.thenByDescending { it.id })
        val ends = LinkedHashMap<Long, Instant>()
        var excess = TimeTracking.totalMinutes(current.values.toList(), now) - goal
        // Une coupe retire au plus l'excédent (chevauchements : parfois moins) : on ne passe jamais sous la cible. Chaque
        // passage raccourcit une session ou l'abandonne vide : la boucle finit.
        var index = 0
        while (excess > 0 && index < order.size) {
            val s = current.getValue(order[index].id)
            val end = s.endedAt!!
            if (end <= s.startedAt) { index++; continue }
            val cut = maxOf(s.startedAt, end - excess.minutes)
            current[s.id] = s.copy(endedAt = cut)
            ends[s.id] = cut
            val after = TimeTracking.totalMinutes(current.values.toList(), now) - goal
            // Rien gagné (session entièrement couverte par une autre) ou vidée : on passe à la suivante.
            if (after >= excess || cut == s.startedAt) index++
            excess = after
        }
        return Plan(null, ends, floor)
    }
}
