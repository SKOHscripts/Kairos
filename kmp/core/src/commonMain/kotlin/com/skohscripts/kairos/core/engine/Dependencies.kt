package com.skohscripts.kairos.core.engine

/**
 * Dépendances « bloquée par » (docs/spec/ordonnancement.md § Dépendances). Pur.
 *
 * Arête = `(bloquée, bloquante)`. Les arêtes internes à un cycle sont neutralisées (ignorées) : jamais de boucle
 * infinie ni de blocage mutuel, et une tâche en aval d'un cycle reste bloquée par lui.
 */
object Dependencies {
    data class Edge(val blocked: Long, val blocker: Long)

    /**
     * Nœuds impliqués dans un cycle : ceux d'une composante fortement connexe de plus d'un nœud, ou portant une auto-arête.
     * Une tâche **en aval** d'un cycle n'y est pas (2026-11-04, QA : l'algorithme de Kahn l'y mettait, et elle perdait son
     * blocage).
     */
    fun cycleNodes(edges: List<Edge>): Set<Long> {
        val component = components(edges)
        val size = HashMap<Int, Int>()
        component.values.forEach { size[it] = (size[it] ?: 0) + 1 }
        val out = LinkedHashSet<Long>()
        for ((node, c) in component) if (size.getValue(c) > 1) out += node
        edges.forEach { if (it.blocked == it.blocker) out += it.blocked }
        return out
    }

    /**
     * Composante fortement connexe de chaque nœud (Tarjan, **itératif** : pas de récursion, une longue chaîne ne fait pas
     * déborder la pile, wasm compris). Deux nœuds ont le même numéro si et seulement si chacun atteint l'autre.
     */
    private fun components(edges: List<Edge>): Map<Long, Int> {
        val adj = LinkedHashMap<Long, MutableList<Long>>()
        for (e in edges) {
            adj.getOrPut(e.blocker) { mutableListOf() } += e.blocked
            adj.getOrPut(e.blocked) { mutableListOf() }
        }
        val index = HashMap<Long, Int>()
        val low = HashMap<Long, Int>()
        val onStack = HashSet<Long>()
        val stack = ArrayDeque<Long>()
        val component = HashMap<Long, Int>()
        var counter = 0
        var components = 0
        for (root in adj.keys) {
            if (root in index) continue
            // Pile d'appels simulée : (nœud, position du prochain voisin à visiter).
            val calls = ArrayDeque<Pair<Long, Int>>()
            index[root] = counter; low[root] = counter; counter++
            stack.addLast(root); onStack += root
            calls.addLast(root to 0)
            while (calls.isNotEmpty()) {
                val (node, next) = calls.removeLast()
                val neighbours = adj.getValue(node)
                if (next < neighbours.size) {
                    calls.addLast(node to next + 1)
                    val w = neighbours[next]
                    if (w !in index) {
                        index[w] = counter; low[w] = counter; counter++
                        stack.addLast(w); onStack += w
                        calls.addLast(w to 0)
                    } else if (w in onStack) {
                        low[node] = minOf(low.getValue(node), index.getValue(w))
                    }
                    continue
                }
                if (low.getValue(node) == index.getValue(node)) {
                    while (true) {
                        val w = stack.removeLast()
                        onStack -= w
                        component[w] = components
                        if (w == node) break
                    }
                    components++
                }
                calls.lastOrNull()?.let { (parent, _) -> low[parent] = minOf(low.getValue(parent), low.getValue(node)) }
            }
        }
        return component
    }

    /**
     * Arêtes hors cycle, dédupliquées, sans auto-boucle : seules les arêtes **internes** à un cycle (même composante
     * fortement connexe) sont neutralisées ; celle qui relie une tâche en aval à un nœud du cycle reste, et la bloque.
     */
    fun acyclicEdges(edges: List<Edge>): List<Edge> {
        val component = components(edges)
        val seen = HashSet<Edge>()
        return edges.filter { e ->
            e.blocked != e.blocker && component[e.blocked] != component[e.blocker] && seen.add(e)
        }
    }

    /**
     * Tâches bloquées : au moins un bloqueur direct encore à faire (`todo`). La
     * transitivité est naturelle (un bloqueur bloqué reste `todo`). Une tâche
     * déjà faite ou archivée n'est jamais bloquée. Statut inconnu = `todo`.
     */
    /**
     * Sous-tâches à faire d'une tâche bloquée, de proche en proche : [tâche → son ancêtre bloqué le plus proche]. Une
     * sous-tâche ne se planifie pas tant que sa mère attend un bloqueur (docs/spec/dependances.md). [blocked] :
     * [blockedTaskIds] ; [parentOf] : mère de chaque tâche à faire qui en a une.
     */
    fun inheritedBlock(blocked: Set<Long>, parentOf: Map<Long, Long>): Map<Long, Long> {
        val out = LinkedHashMap<Long, Long>()
        fun nearestBlockedAncestor(id: Long): Long? {
            var p = parentOf[id]
            var steps = 0
            while (p != null && steps++ < parentOf.size + 1) {
                if (p in blocked || p in out) return if (p in blocked) p else out.getValue(p)
                p = parentOf[p]
            }
            return null
        }
        // Ordre stable : les mères avant leurs filles n'est pas garanti, on itère jusqu'au point fixe.
        var changed = true
        while (changed) {
            changed = false
            for (id in parentOf.keys) {
                if (id in blocked || id in out) continue
                nearestBlockedAncestor(id)?.let { out[id] = it; changed = true }
            }
        }
        return out
    }

    fun blockedTaskIds(edges: List<Edge>, isTodo: (Long) -> Boolean): Set<Long> =
        acyclicEdges(edges).filter { isTodo(it.blocked) && isTodo(it.blocker) }.map { it.blocked }.toSet()

    /** Pour chaque tâche bloquée, les identifiants de ses bloqueurs **directs** encore à faire. */
    fun openBlockers(edges: List<Edge>, isTodo: (Long) -> Boolean): Map<Long, List<Long>> {
        val out = LinkedHashMap<Long, MutableList<Long>>()
        for (e in acyclicEdges(edges)) if (isTodo(e.blocker)) out.getOrPut(e.blocked) { mutableListOf() } += e.blocker
        return out
    }

    /** Vrai si ajouter `(newBlocked, newBlocker)` créerait un cycle (auto-arête comprise). */
    fun wouldCreateCycle(existing: List<Edge>, newBlocked: Long, newBlocker: Long): Boolean {
        if (newBlocked == newBlocker) return true
        return newBlocked in reachableBlockers(existing, newBlocker)
    }

    private fun reachableBlockers(edges: List<Edge>, start: Long): Set<Long> {
        val blockersOf = HashMap<Long, MutableList<Long>>()
        edges.forEach { blockersOf.getOrPut(it.blocked) { mutableListOf() } += it.blocker }
        val seen = HashSet<Long>()
        val stack = ArrayDeque(blockersOf[start].orEmpty())
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (!seen.add(node)) continue
            stack.addAll(blockersOf[node].orEmpty())
        }
        return seen
    }

    /**
     * Urgence effective : un bloqueur hérite de la clé la plus petite (la plus
     * urgente) de ce qu'il bloque, transitivement (point fixe, cycles exclus).
     * Ne modifie aucune tâche.
     */
    fun <K : Comparable<K>> derivedUrgency(edges: List<Edge>, own: Map<Long, K>): Map<Long, K> {
        val active = acyclicEdges(edges)
        val effective = own.toMutableMap()
        repeat(own.size + 1) {
            var changed = false
            for (e in active) {
                val blockedKey = effective[e.blocked] ?: continue
                val blockerKey = effective[e.blocker] ?: continue
                if (blockedKey < blockerKey) {
                    effective[e.blocker] = blockedKey
                    changed = true
                }
            }
            if (!changed) return effective
        }
        return effective
    }
}
