package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.model.KairosSnapshot

/**
 * Réparations après une fusion (docs/spec/synchronisation.md § Réparations) : des invariants que la saisie garantit et que
 * deux appareils indépendants peuvent casser. Fonctions pures de l'état fusionné, déterministes (départage par `uid`, commun à
 * tous les appareils) : chaque appareil calcule la même réparation et l'applique comme une écriture locale ordinaire ; au
 * tour suivant les valeurs sont égales, donc sans conflit.
 */
object SyncRepair {
    sealed interface Repair {
        /** Session arrêtée parce qu'une plus récente était ouverte ailleurs : une seule session ouverte à la fois. */
        data class SessionClosed(val sessionId: Long) : Repair

        /** Tâche remise au premier niveau pour casser une boucle de tâches mères. */
        data class ParentCleared(val taskId: Long) : Repair
    }

    data class Result(val snapshot: KairosSnapshot, val repairs: List<Repair>)

    /**
     * - **Une seule session ouverte** : parmi les sessions sans fin, la plus récente (`startedAt`, puis `uid`) reste ouverte ;
     *   chacune des autres est arrêtée au `startedAt` de la suivante dans cet ordre (ce que fait « Démarrer » aujourd'hui).
     * - **Mère en boucle** : dans un cycle de `parentId`, la tâche de plus grand `uid` redevient de premier niveau.
     *
     * [uids] départage les égalités ; un enregistrement sans `uid` (hors portée) est départagé par son identifiant local.
     */
    fun repair(snapshot: KairosSnapshot, uids: SyncData?): Result {
        val repairs = ArrayList<Repair>()
        fun key(kind: RecordKind, id: Long): String = uids?.uidOf(kind, id) ?: "~local:${id.toString().padStart(19, '0')}"

        var sessions = snapshot.workSessions
        val open = sessions.filter { it.endedAt == null }.sortedWith(compareBy({ it.startedAt }, { key(RecordKind.SESSION, it.id) }))
        if (open.size > 1) {
            val endAt = open.zipWithNext().associate { (a, b) -> a.id to b.startedAt }
            sessions = sessions.map { s -> endAt[s.id]?.let { end -> repairs += Repair.SessionClosed(s.id); s.copy(endedAt = maxOf(end, s.startedAt)) } ?: s }
        }

        var tasks = snapshot.tasks
        val parent = tasks.associate { it.id to it.parentId }
        val cleared = HashSet<Long>()
        val visited = HashSet<Long>()
        for (start in tasks.map { it.id }.sorted()) {
            if (start in visited) continue
            val path = LinkedHashSet<Long>()
            var cur: Long? = start
            while (cur != null && cur !in visited && cur !in path) {
                path += cur
                cur = if (cur in cleared) null else parent[cur]?.takeIf { it in parent }
            }
            if (cur != null && cur in path) {
                val cycle = path.dropWhile { it != cur }
                val victim = cycle.maxBy { key(RecordKind.TASK, it) }
                cleared += victim
                repairs += Repair.ParentCleared(victim)
            }
            visited += path
        }
        if (cleared.isNotEmpty()) tasks = tasks.map { if (it.id in cleared) it.copy(parentId = null) else it }
        return Result(snapshot.copy(tasks = tasks, workSessions = sessions), repairs)
    }
}
