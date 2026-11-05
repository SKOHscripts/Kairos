package com.skohscripts.kairos.core.model

/**
 * Arbre des tâches par `parentId` (docs/spec/sous-taches.md § Arbre des tâches). Pur. Les sous-tâches **découpent** un
 * travail ; les dépendances « Bloquée par » l'**enchaînent** (docs/spec/dependances.md).
 */
object TaskTree {
    /** Profondeur au-delà de laquelle la lignée s'arrête : garde contre une boucle de mères ou un fichier aberrant. */
    const val MAX_DEPTH = 64

    /** Ancêtres affichés en entier ; au-delà, la racine, « … », puis les deux derniers. */
    const val MAX_SHOWN = 3

    const val SEPARATOR = " › "
    const val ELLIPSIS = "…"

    /**
     * Les ancêtres de [id], de la racine à la mère directe. La lignée s'arrête à une mère absente de [byId] (supprimée,
     * archivée si l'appelant les écarte) et à une mère déjà vue (boucle : jamais de parcours sans fin).
     */
    fun ancestors(id: Long, byId: Map<Long, Task>): List<Task> {
        val chain = ArrayList<Task>()
        val seen = HashSet<Long>().apply { add(id) }
        var parent = byId[id]?.parentId
        while (parent != null && chain.size < MAX_DEPTH) {
            val task = byId[parent] ?: break
            if (!seen.add(task.id)) break
            chain += task
            parent = task.parentId
        }
        return chain.asReversed()
    }

    /** Préfixe du titre de [id] : ses ancêtres joints par « › » (« A › C » pour D), tronqué au-delà de [MAX_SHOWN] ; `null` sans mère. */
    fun path(id: Long, byId: Map<Long, Task>): String? {
        val titles = ancestors(id, byId).map { it.title }
        if (titles.isEmpty()) return null
        val shown = if (titles.size > MAX_SHOWN) listOf(titles.first(), ELLIPSIS) + titles.takeLast(MAX_SHOWN - 1) else titles
        return shown.joinToString(SEPARATOR)
    }
}
