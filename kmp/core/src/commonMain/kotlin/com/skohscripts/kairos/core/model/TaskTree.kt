package com.skohscripts.kairos.core.model

import kotlinx.datetime.LocalDate

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

    /**
     * Les descendants de [id] (filles, petites-filles…), en largeur d'abord : une mère avant ses filles. Une boucle de mères
     * s'arrête au premier déjà vu.
     */
    fun descendants(id: Long, tasks: Collection<Task>): List<Task> {
        val children = tasks.filter { it.parentId != null }.groupBy { it.parentId!! }
        val out = ArrayList<Task>()
        val seen = HashSet<Long>().apply { add(id) }
        val queue = ArrayDeque(children[id].orEmpty())
        while (queue.isNotEmpty() && out.size < tasks.size) {
            val t = queue.removeFirst()
            if (!seen.add(t.id)) continue
            out += t
            queue.addAll(children[t.id].orEmpty())
        }
        return out
    }

    /**
     * Priorité plancher de [id] (docs/spec/sous-taches.md § Priorité) : la plus forte (le plus petit nombre, P0 en tête)
     * de ses ancêtres qui en ont une ; `null` si aucun n'en a.
     */
    fun priorityFloor(id: Long, byId: Map<Long, Task>): Int? = floorTask(id, byId)?.priority

    /** L'ancêtre qui impose le plancher de [id] (le plus proche à priorité égale), pour le dire dans la fiche ; `null` sans plancher. */
    fun floorTask(id: Long, byId: Map<Long, Task>): Task? =
        ancestors(id, byId).asReversed().filter { it.priority != null }.minByOrNull { it.priority!! }

    /** Priorité héritée par une nouvelle sous-tâche de [parentId] : la plus forte de la mère et de ses ancêtres ; `null` si aucune. */
    fun inheritedPriority(parentId: Long, byId: Map<Long, Task>): Int? {
        val parent = byId[parentId] ?: return null
        return (ancestors(parentId, byId) + parent).mapNotNull { it.priority }.minOrNull()
    }

    /**
     * [priority] ramenée au [floor] : jamais moins prioritaire que les ancêtres ; sans valeur, la tâche prend le plancher
     * (une fille hérite de sa mère). Sans plancher, [priority] telle quelle.
     */
    fun bounded(priority: Int?, floor: Int?): Int? = when {
        floor == null -> priority
        priority == null -> floor
        else -> minOf(priority, floor)
    }

    /**
     * Les sous-tâches **à faire** de [id] qu'il faut monter après que [id] a pris sa priorité actuelle (dans [byId]) : chacune
     * avec sa nouvelle priorité, de proche en proche (une fille montée relève le plancher de ses propres filles). Les faites
     * et archivées gardent la leur (l'historique ne change pas).
     */
    fun raisedDescendants(id: Long, byId: Map<Long, Task>): List<Task> {
        val current = HashMap(byId)
        val raised = ArrayList<Task>()
        for (child in descendants(id, byId.values)) {
            if (child.status != TaskStatus.TODO) continue
            val target = bounded(child.priority, priorityFloor(child.id, current))
            if (target != child.priority) {
                val next = child.copy(priority = target)
                current[child.id] = next
                raised += next
            }
        }
        return raised
    }

    // --- Échéance héritée et bornée (docs/spec/sous-taches.md § Échéance, lot 3) ---------------------------------------

    /** L'ancêtre à l'échéance la plus proche (le plus proche dans la lignée à date égale) ; `null` si aucun n'en a. */
    fun deadlineFloorTask(id: Long, byId: Map<Long, Task>): Task? =
        ancestors(id, byId).asReversed().filter { it.deadline != null }.minByOrNull { it.deadline!! }

    /** [deadline] jamais après le [floor] des ancêtres ; sans échéance, rien n'est posé (l'échéance s'hérite au planning). */
    fun boundedDeadline(deadline: LocalDate?, floor: LocalDate?): LocalDate? =
        if (deadline != null && floor != null && deadline > floor) floor else deadline

    /**
     * Pour chaque tâche **à faire** de [tasks] dont l'échéance vient d'un ancêtre (elle n'en a pas, ou une plus tardive) :
     * l'ancêtre qui la donne. Le planning la place avec cette échéance ; la ligne dit d'où elle vient.
     */
    fun inheritedDeadlines(tasks: Collection<Task>, byId: Map<Long, Task>): Map<Long, Task> =
        tasks.filter { it.status == TaskStatus.TODO && it.parentId != null }.mapNotNull { t ->
            val from = deadlineFloorTask(t.id, byId) ?: return@mapNotNull null
            if (t.deadline == null || t.deadline > from.deadline!!) t.id to from else null
        }.toMap()

    /**
     * Les sous-tâches **à faire** de [id] dont l'échéance dépasse désormais celle de leurs ancêtres ([id] à jour dans
     * [byId]) : chacune ramenée à cette échéance, de proche en proche. Une fille sans échéance n'en reçoit pas (elle
     * l'hérite au planning).
     */
    fun clampedDescendants(id: Long, byId: Map<Long, Task>): List<Task> {
        val current = HashMap(byId)
        val clamped = ArrayList<Task>()
        for (child in descendants(id, byId.values)) {
            if (child.status != TaskStatus.TODO) continue
            val target = boundedDeadline(child.deadline, deadlineFloorTask(child.id, current)?.deadline)
            if (target != child.deadline) {
                val next = child.copy(deadline = target)
                current[child.id] = next
                clamped += next
            }
        }
        return clamped
    }

    // --- Durées et temps passé additionnés (docs/spec/sous-taches.md § Agrégation, lot 3) ------------------------------

    /**
     * Ce que coûtent les sous-tâches d'une mère : [subtasks] descendants (non archivés) dont [done] faits ; [plannedMinutes]
     * la somme des durées de ses **feuilles** (descendants sans fille : une mère intermédiaire est comptée par ses propres
     * filles), [unestimated] les feuilles sans durée ; [spentMinutes] le temps passé sur la mère et tous ses descendants.
     */
    data class Rollup(val subtasks: Int, val done: Int, val plannedMinutes: Int, val unestimated: Int, val spentMinutes: Int)

    /**
     * L'agrégat de [id] parmi [tasks] (archivées exclues par l'appelant), `null` sans sous-tâche. [minutesOf] : la durée
     * d'une feuille (estimée, apprise, ou le temps réel d'une faite), `null` si elle n'en a pas ; [spent] : temps passé
     * par tâche.
     */
    fun rollup(id: Long, tasks: Collection<Task>, minutesOf: (Task) -> Int?, spent: Map<Long, Int>): Rollup? {
        val below = descendants(id, tasks)
        if (below.isEmpty()) return null
        val mothers = tasks.mapNotNullTo(HashSet()) { it.parentId }
        val leaves = below.filter { it.id !in mothers }
        val minutes = leaves.map(minutesOf)
        return Rollup(
            subtasks = below.size,
            done = below.count { it.status == TaskStatus.DONE },
            plannedMinutes = minutes.sumOf { it ?: 0 },
            unestimated = minutes.count { it == null },
            spentMinutes = (spent[id] ?: 0) + below.sumOf { spent[it.id] ?: 0 },
        )
    }
}
