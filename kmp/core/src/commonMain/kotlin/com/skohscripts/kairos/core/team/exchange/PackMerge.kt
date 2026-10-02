package com.skohscripts.kairos.core.team.exchange

import com.skohscripts.kairos.core.engine.Dependencies
import com.skohscripts.kairos.core.model.FIBONACCI_SCALE
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskDependency
import kotlin.time.Instant

/** Pourquoi un paquet n'est pas reçu. */
enum class PackRefusal {
    /** Le paquet vient de **cette** base (même identité d'équipe) : le recevoir recopierait l'équipe en tâches personnelles. */
    OWN_TEAM,
}

/** Champ du manager qu'une mise à jour change (aperçu de réception). */
enum class PackField { TITLE, DESCRIPTION, PRIORITY, POINTS, CATEGORY, DEADLINE, ESTIMATE }

/** Paire de tâches désignées par leur `teamUid` : [taskUid] est bloquée par [blockerUid]. */
data class DependencyRef(val taskUid: String, val blockerUid: String)

/** Tâche à créer chez le membre ([task].id vaut 0). [parentUid] : tâche mère du paquet, que le dépôt résout une fois les tâches insérées. */
data class NewReceivedTask(val task: Task, val parentUid: String?)

/**
 * Tâche déjà reçue (ou adoptée) à réécrire. [changed] : champs du manager qui changent ; [restored] : elle était
 * « retirée » et revient ; [adopted] : sous-tâche du membre, déjà remontée au manager, qui devient une tâche reçue.
 * Une entrée peut ne rien montrer ([isVisible] faux) : rafraîchissement des noms d'origine, rattachement à une mère.
 */
data class UpdatedReceivedTask(
    val before: Task,
    val after: Task,
    val parentUid: String?,
    val changed: Set<PackField>,
    val restored: Boolean,
    val adopted: Boolean,
) {
    val isVisible: Boolean get() = changed.isNotEmpty() || restored || adopted
}

/**
 * Plan de réception d'un paquet, côté membre (docs/spec/equipe-echanges.md § Réception) : ce que
 * `KairosRepository.receivePack` écrira, en une transaction. Sert aussi d'aperçu (« Recevoir 7 tâches de Corentin ? »).
 *
 * - [created] : nouvelles tâches ; [updated] : tâches reçues réécrites (champs du manager seulement) ;
 * - [removed] : tâches reçues absentes du paquet, à marquer `originRemoved` (jamais supprimées) ;
 * - [unchanged] : tâches reçues qui n'ont rien à changer (dont celles déjà retirées et toujours absentes) ;
 * - [dependencyAdds] / [dependencyRemoves] : changement des dépendances **entre tâches reçues** de ce manager ;
 *   [skippedDependencies] : dépendances du paquet écartées car elles fermeraient un cycle avec les dépendances du membre ;
 * - [externalBlockers] : bloqueurs tenus hors du paquet, en lecture seule (rien n'en est stocké) ;
 * - [memberChanged] : des tâches déjà reçues de cette équipe désignaient un autre membre (paquet peut-être pour un autre).
 */
data class PackMergePlan(
    val refusal: PackRefusal? = null,
    val origin: TeamOrigin,
    val packId: String,
    val created: List<NewReceivedTask> = emptyList(),
    val updated: List<UpdatedReceivedTask> = emptyList(),
    val removed: List<Task> = emptyList(),
    val unchanged: List<Task> = emptyList(),
    val dependencyAdds: List<DependencyRef> = emptyList(),
    val dependencyRemoves: List<DependencyRef> = emptyList(),
    val skippedDependencies: List<DependencyRef> = emptyList(),
    val externalBlockers: List<ExternalBlocker> = emptyList(),
    val memberChanged: Boolean = false,
) {
    /** Le paquet est accepté. */
    val accepted: Boolean get() = refusal == null

    /** Mises à jour qui changent quelque chose de visible (compteur « mises à jour » de l'aperçu). */
    val visibleUpdates: List<UpdatedReceivedTask> get() = updated.filter { it.isVisible }

    /** Rien à écrire : le paquet a déjà été reçu tel quel. */
    val isNoop: Boolean
        get() = created.isEmpty() && updated.isEmpty() && removed.isEmpty() && dependencyAdds.isEmpty() && dependencyRemoves.isEmpty()
}

/**
 * Fusion d'un paquet dans la base d'un membre (docs/spec/equipe-echanges.md § Réception). Pur.
 *
 * Chaque champ a **un seul propriétaire** : le manager (titre, description, priorité, points, catégorie, échéance,
 * durée estimée, dépendances du paquet) ou le membre (statut, avancement, temps passé, sessions, heure fixe, date
 * programmée, notes…). Il n'y a donc aucun conflit à arbitrer : les champs du manager sont écrasés, ceux du
 * membre ne sont jamais lus.
 */
object PackMerge {
    fun plan(snapshot: KairosSnapshot, pack: TeamPack, now: Instant): PackMergePlan {
        val origin = TeamOrigin.of(pack)
        val ownIdentity = snapshot.settings.team?.identity.orEmpty()
        if (ownIdentity.isNotEmpty() && ownIdentity == pack.team.uid) return PackMergePlan(PackRefusal.OWN_TEAM, origin, pack.packId)

        val encoded = origin.encode()
        val receivedAll = snapshot.tasks.filter { ReceivedTasks.originOf(it)?.teamUid == pack.team.uid }.sortedBy { it.id }
        val received = LinkedHashMap<String, Task>()
        receivedAll.forEach { t -> t.teamUid?.let { received.getOrPut(it) { t } } }
        // Sous-tâche créée par le membre sous une tâche reçue, déjà remontée au manager (rapport) : elle a un
        // `teamUid` mais pas d'origine. Le paquet suivant la contient : on la rattache au lieu de la dupliquer.
        val adoptable = LinkedHashMap<String, Task>()
        snapshot.tasks.filter { it.origin == null && it.space == TaskSpace.PERSONAL && it.teamUid != null }.sortedBy { it.id }
            .forEach { adoptable.getOrPut(it.teamUid!!) { it } }

        // Tâches du paquet : première occurrence d'un uid, mères avant leurs filles.
        val packTasks = pack.tasks.distinctBy { it.uid }
        val packUids = packTasks.map { it.uid }.toSet()
        val byUid = packTasks.associateBy { it.uid }
        fun parentOf(p: PackTask): String? = p.parentUid?.takeIf { it in packUids && it != p.uid }
        fun depth(p: PackTask): Int {
            var d = 0
            var cur = p
            val seen = HashSet<String>()
            while (seen.add(cur.uid)) {
                cur = parentOf(cur)?.let(byUid::get) ?: break
                d++
            }
            return d
        }
        val ordered = packTasks.withIndex().sortedWith(compareBy({ depth(it.value) }, { it.index })).map { it.value }

        val receivedIds = receivedAll.mapTo(HashSet()) { it.id }
        val created = ArrayList<NewReceivedTask>()
        val updated = ArrayList<UpdatedReceivedTask>()
        val unchanged = ArrayList<Task>()
        for (p in ordered) {
            val parentUid = parentOf(p)
            val existing = received[p.uid] ?: adoptable[p.uid]
            if (existing == null) {
                created += NewReceivedTask(newTask(p, encoded, now), parentUid)
                continue
            }
            val adopted = existing.origin == null
            val parentExisting = parentUid?.let { (received[it] ?: adoptable[it])?.id }
            val parentId = when {
                parentUid != null -> parentExisting ?: existing.parentId
                existing.parentId != null && existing.parentId in receivedIds -> null
                else -> existing.parentId
            }
            val after = withManagerFields(existing, p, keepWhenEmpty = adopted).copy(origin = encoded, originRemoved = false, parentId = parentId)
            val pending = parentUid != null && parentExisting == null
            if (after == existing && !pending) {
                unchanged += existing
            } else {
                updated += UpdatedReceivedTask(existing, after, parentUid, changedFields(existing, after), restored = existing.originRemoved, adopted = adopted)
            }
        }

        // Retirées : reçues d'une version précédente, absentes de celui-ci. Jamais supprimées.
        val removed = ArrayList<Task>()
        for (t in receivedAll) {
            if (t.teamUid in packUids) continue
            if (t.originRemoved) unchanged += t else removed += t
        }

        val deps = dependencyChanges(snapshot, pack, receivedAll, received, adoptable, ordered, created)

        return PackMergePlan(
            origin = origin,
            packId = pack.packId,
            created = created,
            updated = updated,
            removed = removed,
            unchanged = unchanged,
            dependencyAdds = deps.first,
            dependencyRemoves = deps.second,
            skippedDependencies = deps.third,
            externalBlockers = pack.externalBlockers.filter { it.taskUid in packUids },
            memberChanged = receivedAll.any { ReceivedTasks.originOf(it)?.memberUid != pack.member.uid },
        )
    }

    private fun newTask(p: PackTask, origin: String, now: Instant) = Task(
        id = 0,
        title = p.title.trim(),
        description = p.description,
        priority = sanitizedPriority(p.priority),
        deadline = p.deadline,
        estimatedMinutes = sanitizedEstimate(p.estimatedMinutes),
        taskType = p.taskType.trim(),
        fibonacciPoints = sanitizedPoints(p.fibonacciPoints),
        progressPercent = p.progressPercent?.coerceIn(0, 100),
        createdAt = now,
        updatedAt = now,
        space = TaskSpace.PERSONAL,
        teamUid = p.uid,
        origin = origin,
    )

    /**
     * [base] avec les champs du manager du paquet. [keepWhenEmpty] (sous-tâche du membre rattachée) : un champ que le
     * paquet laisse vide garde la valeur du membre, qui a pu qualifier lui-même sa sous-tâche.
     */
    private fun withManagerFields(base: Task, p: PackTask, keepWhenEmpty: Boolean): Task {
        val priority = sanitizedPriority(p.priority)
        val points = sanitizedPoints(p.fibonacciPoints)
        val estimate = sanitizedEstimate(p.estimatedMinutes)
        val type = p.taskType.trim()
        return base.copy(
            title = p.title.trim().ifEmpty { base.title },
            description = if (keepWhenEmpty && p.description.isEmpty()) base.description else p.description,
            priority = if (keepWhenEmpty && priority == null) base.priority else priority,
            fibonacciPoints = if (keepWhenEmpty && points == null) base.fibonacciPoints else points,
            taskType = if (keepWhenEmpty && type.isEmpty()) base.taskType else type,
            deadline = if (keepWhenEmpty && p.deadline == null) base.deadline else p.deadline,
            estimatedMinutes = if (keepWhenEmpty && estimate == null) base.estimatedMinutes else estimate,
        )
    }

    private fun changedFields(before: Task, after: Task): Set<PackField> = buildSet {
        if (before.title != after.title) add(PackField.TITLE)
        if (before.description != after.description) add(PackField.DESCRIPTION)
        if (before.priority != after.priority) add(PackField.PRIORITY)
        if (before.fibonacciPoints != after.fibonacciPoints) add(PackField.POINTS)
        if (before.taskType != after.taskType) add(PackField.CATEGORY)
        if (before.deadline != after.deadline) add(PackField.DEADLINE)
        if (before.estimatedMinutes != after.estimatedMinutes) add(PackField.ESTIMATE)
    }

    private fun sanitizedPriority(value: Int?) = value?.takeIf { it in PRIORITY_VALUES }
    private fun sanitizedPoints(value: Int?) = value?.takeIf { it in FIBONACCI_SCALE }
    private fun sanitizedEstimate(value: Int?) = value?.takeIf { it > 0 }

    /**
     * Dépendances : celles du paquet remplacent les précédentes **entre tâches reçues** de ce manager ; celles que
     * le membre a posées avec ses propres tâches ne sont pas touchées. Une dépendance du paquet qui fermerait un
     * cycle avec elles est écartée. Rend (ajouts, retraits, écartées).
     */
    private fun dependencyChanges(
        snapshot: KairosSnapshot,
        pack: TeamPack,
        receivedAll: List<Task>,
        received: Map<String, Task>,
        adoptable: Map<String, Task>,
        ordered: List<PackTask>,
        created: List<NewReceivedTask>,
    ): Triple<List<DependencyRef>, List<DependencyRef>, List<DependencyRef>> {
        val packUids = ordered.mapTo(HashSet()) { it.uid }
        val desired = pack.dependencies
            .filter { it.taskUid in packUids && it.blockerUid in packUids && it.taskUid != it.blockerUid }
            .map { DependencyRef(it.taskUid, it.blockerUid) }.distinct()

        // Un nœud par tâche : son identifiant s'il existe, un identifiant négatif sinon (tâche à créer).
        val nodeByUid = HashMap<String, Long>()
        for (uid in packUids) (received[uid] ?: adoptable[uid])?.let { nodeByUid[uid] = it.id }
        created.forEachIndexed { index, n -> nodeByUid[n.task.teamUid!!] = -(index + 1L) }
        val uidByNode = HashMap<Long, String>()
        receivedAll.forEach { t -> t.teamUid?.let { uidByNode[t.id] = it } }
        nodeByUid.forEach { (uid, node) -> uidByNode[node] = uid }

        val receivedIds = receivedAll.mapTo(HashSet()) { it.id }
        fun refOf(d: TaskDependency): DependencyRef? = uidByNode[d.taskId]?.let { a -> uidByNode[d.blockerId]?.let { b -> DependencyRef(a, b) } }
        // Toutes les dépendances déjà posées entre deux tâches du paquet (reçues, ou adoptées) : on n'en ajoute pas deux fois.
        val known = snapshot.dependencies.mapNotNull(::refOf).toSet()
        val between = snapshot.dependencies.filter { it.taskId in receivedIds && it.blockerId in receivedIds }
        val dropped = between.filter { refOf(it) !in desired }
        val removes = dropped.mapNotNull(::refOf).distinct()
        val edges = snapshot.dependencies.filter { it !in dropped }.map { Dependencies.Edge(it.taskId, it.blockerId) }.toMutableList()

        val adds = ArrayList<DependencyRef>()
        val skipped = ArrayList<DependencyRef>()
        for (d in desired.filter { it !in known }) {
            val blocked = nodeByUid.getValue(d.taskUid)
            val blocker = nodeByUid.getValue(d.blockerUid)
            if (Dependencies.wouldCreateCycle(edges, blocked, blocker)) {
                skipped += d
            } else {
                adds += d
                edges += Dependencies.Edge(blocked, blocker)
            }
        }
        return Triple(adds, removes, skipped)
    }
}
