package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.model.KairosSnapshot

/**
 * L'ordre commun des tâches de deux appareils synchronisés (docs/spec/synchronisation.md § Durées apprises partagées) :
 * `createdAt` croissant, puis `uid`, deux valeurs identiques partout après une fusion (le registre `createdAt` prend le plus
 * petit). Rang 0 = la plus ancienne. Les tâches sans `uid` (hors portée : équipe, reçues) n'ont pas de rang. Pur.
 */
object SyncOrder {
    fun of(snapshot: KairosSnapshot, data: SyncData): Map<Long, Long> {
        val uids = data.uids[RecordKind.TASK].orEmpty()
        val ranked = snapshot.tasks.mapNotNull { t -> uids[t.id]?.let { Triple(t.id, t.createdAt, it) } }
            .sortedWith(compareBy<Triple<Long, kotlin.time.Instant, String>> { it.second }.thenBy { it.third })
        val out = HashMap<Long, Long>(ranked.size * 2)
        ranked.forEachIndexed { i, (id, _, _) -> out[id] = i.toLong() }
        return out
    }
}
