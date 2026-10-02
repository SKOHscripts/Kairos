package com.skohscripts.kairos.core.team.forecast

import com.skohscripts.kairos.core.model.FIBONACCI_SCALE
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.MemberAbsence
import com.skohscripts.kairos.core.team.MemberForm
import com.skohscripts.kairos.core.team.TeamMember
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * Un scénario « Et si… ? » enregistré (docs/spec/equipe-simulation.md § Scénarios) :
 * un nom et une liste de [modifications] appliquées à **une copie** des données
 * ([Scenario.apply]), jamais aux données réelles. [ignored] : modifications de
 * l'enregistrement que cette version ne sait pas lire (type inconnu, écrit par une
 * version plus récente) ; elles sont signalées, jamais appliquées, et ne sont pas
 * réécrites à l'enregistrement.
 */
data class TeamScenario(
    val id: Long,
    val name: String,
    val modifications: List<ScenarioModification>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val ignored: List<IgnoredModification> = emptyList(),
)

/** Une modification illisible d'un scénario : sa [position] dans la liste enregistrée (−1 : liste entière illisible) et son [type] s'il se lit. */
data class IgnoredModification(val position: Int, val type: String?)

/**
 * Modification d'un scénario. Sérialisée en JSON avec un discriminant `type`
 * (`addMember`, `removeMember`, `addAbsence`, `setAvailability`, `reassign`,
 * `addTasks`, `setPriority`, `setDeadline`, `setFocus`). Les tâches sont
 * désignées par leur `teamUid` (stable), les membres par leur identifiant ; un
 * membre **hypothétique** a un identifiant négatif (`AddMember.tempId`).
 */
@Serializable
sealed interface ScenarioModification {
    /** Membre hypothétique : [tempId] **négatif**, choisi par l'éditeur ; les autres modifications le désignent par lui. */
    @Serializable
    @SerialName("addMember")
    data class AddMember(val tempId: Long, val name: String, val availabilityPercent: Int = 100, val hoursPerDay: Double) : ScenarioModification

    /** Retire un membre : ses tâches à faire retournent au backlog du scénario. */
    @Serializable
    @SerialName("removeMember")
    data class RemoveMember(val memberId: Long) : ScenarioModification

    @Serializable
    @SerialName("addAbsence")
    data class AddAbsence(val memberId: Long, val start: LocalDate, val end: LocalDate) : ScenarioModification

    @Serializable
    @SerialName("setAvailability")
    data class SetAvailability(val memberId: Long, val percent: Int) : ScenarioModification

    /** Réaffecte la tâche [taskUid] à [memberId] (`null` = backlog). */
    @Serializable
    @SerialName("reassign")
    data class Reassign(val taskUid: String, val memberId: Long? = null) : ScenarioModification

    /** Ajoute [count] tâches hypothétiques au backlog (points, catégorie et priorité communs). */
    @Serializable
    @SerialName("addTasks")
    data class AddTasks(val count: Int, val points: Int? = null, val category: String = "", val priority: Int? = null) : ScenarioModification

    @Serializable
    @SerialName("setPriority")
    data class SetPriority(val taskUid: String, val priority: Int) : ScenarioModification

    @Serializable
    @SerialName("setDeadline")
    data class SetDeadline(val taskUid: String, val date: LocalDate? = null) : ScenarioModification

    /** Taux de focus de l'équipe (> 0 et ≤ 1). */
    @Serializable
    @SerialName("setFocus")
    data class SetFocus(val factor: Double) : ScenarioModification
}

/**
 * Lecture et écriture JSON de la liste de modifications d'un scénario (colonne
 * `team_scenario.modifications`, champ `teamScenarios` de l'export). Un type
 * inconnu à la lecture est **ignoré et signalé** ([Decoded.ignored]), jamais une
 * erreur : un scénario écrit par une version plus récente reste lisible.
 */
object ScenarioCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }
    private val listSerializer = ListSerializer(ScenarioModification.serializer())

    /** Résultat d'une lecture : les modifications comprises, dans l'ordre, et celles qui ne l'ont pas été. */
    data class Decoded(val modifications: List<ScenarioModification>, val ignored: List<IgnoredModification>)

    fun encode(modifications: List<ScenarioModification>): String = json.encodeToString(listSerializer, modifications)

    fun decode(text: String): Decoded {
        val array: JsonArray = try {
            json.parseToJsonElement(text) as? JsonArray ?: return Decoded(emptyList(), listOf(IgnoredModification(-1, null)))
        } catch (e: SerializationException) {
            return Decoded(emptyList(), listOf(IgnoredModification(-1, null)))
        }
        val read = ArrayList<ScenarioModification>()
        val ignored = ArrayList<IgnoredModification>()
        array.forEachIndexed { index, element ->
            try {
                read += json.decodeFromJsonElement(ScenarioModification.serializer(), element)
            } catch (e: SerializationException) {
                ignored += IgnoredModification(index, typeOf(element))
            } catch (e: IllegalArgumentException) {
                ignored += IgnoredModification(index, typeOf(element))
            }
        }
        return Decoded(read, ignored)
    }

    private fun typeOf(element: JsonElement): String? =
        ((element as? JsonObject)?.get("type") as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/**
 * Application pure des scénarios (docs/spec/equipe-simulation.md § Scénarios). Les
 * modifications s'appliquent **dans l'ordre** à une copie ; une modification qui
 * ne s'applique plus (tâche faite ou disparue, membre inconnu ou archivé, valeur
 * hors bornes) est ignorée et rendue dans les [Skipped]. Les entités hypothétiques
 * ont des identifiants **négatifs** (jamais en conflit avec la base) et des
 * `teamUid` déterministes ([hypotheticalTaskUid]), pour que la situation réelle
 * et le scénario partagent les mêmes tirages aléatoires.
 */
object Scenario {
    /** Nombre maximal de tâches d'un `AddTasks` (garde-fou contre une saisie aberrante). */
    const val MAX_ADDED_TASKS = 500

    private const val TASK_UID_PREFIX = "scenario-task-"
    private const val MEMBER_UID_PREFIX = "scenario-member-"

    enum class SkipReason {
        /** Membre inconnu (jamais existé, ou hypothétique non encore ajouté). */
        MEMBER_NOT_FOUND,

        /** Membre archivé depuis l'écriture du scénario. */
        MEMBER_ARCHIVED,

        /** Tâche inconnue (supprimée depuis). */
        TASK_NOT_FOUND,

        /** Tâche qui n'est plus à faire (faite depuis) ou n'est pas une tâche d'équipe. */
        TASK_NOT_OPEN,

        /** Valeur hors bornes (pourcentage, priorité, points, dates inversées, nombre de tâches…). */
        INVALID_VALUE,

        /** Déjà en place dans la base réelle : « Appliquer » n'a rien à changer (dépôt seulement). */
        ALREADY_APPLIED,
    }

    /** Une modification non appliquée : sa [index] dans la liste donnée, elle-même et la [reason]. */
    data class Skipped(val index: Int, val modification: ScenarioModification, val reason: SkipReason)

    /** `teamUid` de la [k]-ième (à partir de 0) tâche hypothétique de la modification d'indice [modificationIndex]. */
    fun hypotheticalTaskUid(modificationIndex: Int, k: Int): String = "$TASK_UID_PREFIX$modificationIndex-$k"

    fun isHypotheticalTask(taskUid: String): Boolean = taskUid.startsWith(TASK_UID_PREFIX)

    /** Identifiant de membre hypothétique : négatif. */
    fun isHypotheticalMember(memberId: Long): Boolean = memberId < 0

    /**
     * Ce qu'« Appliquer » exécute : tout sauf [ScenarioModification.AddMember],
     * [ScenarioModification.AddTasks] et les [ScenarioModification.Reassign] vers un
     * membre hypothétique. Précision : sont aussi écartées les modifications qui
     * **désignent** un membre ou une tâche hypothétique (absence, quotité, retrait,
     * priorité, échéance, réaffectation d'une tâche hypothétique) : elles ne
     * pourraient s'appliquer à rien de réel.
     */
    fun realChanges(modifications: List<ScenarioModification>): List<ScenarioModification> =
        modifications.filter { isReal(it) }

    /** Le contraire de [realChanges], pour une modification. */
    fun isReal(modification: ScenarioModification): Boolean = when (modification) {
        is ScenarioModification.AddMember, is ScenarioModification.AddTasks -> false
        is ScenarioModification.RemoveMember -> !isHypotheticalMember(modification.memberId)
        is ScenarioModification.AddAbsence -> !isHypotheticalMember(modification.memberId)
        is ScenarioModification.SetAvailability -> !isHypotheticalMember(modification.memberId)
        is ScenarioModification.Reassign ->
            !isHypotheticalTask(modification.taskUid) && (modification.memberId == null || !isHypotheticalMember(modification.memberId))
        is ScenarioModification.SetPriority -> !isHypotheticalTask(modification.taskUid)
        is ScenarioModification.SetDeadline -> !isHypotheticalTask(modification.taskUid)
        is ScenarioModification.SetFocus -> true
    }

    /** Copie de [snapshot] où [modifications] sont appliquées dans l'ordre, et celles qui ne l'ont pas pu être. */
    fun apply(snapshot: KairosSnapshot, modifications: List<ScenarioModification>): Pair<KairosSnapshot, List<Skipped>> {
        var members: List<TeamMember> = snapshot.members
        var absences: List<MemberAbsence> = snapshot.absences
        var tasks: List<Task> = snapshot.tasks
        var settings = snapshot.settings
        val skipped = ArrayList<Skipped>()
        val stamp = snapshot.tasks.maxOfOrNull { it.updatedAt } ?: Instant.fromEpochSeconds(0)
        var nextTaskId = -1L
        var nextAbsenceId = -1L

        fun member(id: Long) = members.firstOrNull { it.id == id }
        fun task(uid: String) = tasks.firstOrNull { it.teamUid == uid && it.space == TaskSpace.TEAM }

        modifications.forEachIndexed { index, mod ->
            fun skip(reason: SkipReason) {
                skipped += Skipped(index, mod, reason)
            }

            /** Membre actif désigné, ou la raison de l'ignorer. */
            fun activeMember(id: Long): TeamMember? {
                val m = member(id)
                when {
                    m == null -> skip(SkipReason.MEMBER_NOT_FOUND)
                    m.archived -> skip(SkipReason.MEMBER_ARCHIVED)
                    else -> return m
                }
                return null
            }

            /** Tâche d'équipe à faire désignée, ou la raison de l'ignorer. */
            fun openTask(uid: String): Task? {
                val t = task(uid)
                when {
                    t == null -> skip(SkipReason.TASK_NOT_FOUND)
                    t.status != TaskStatus.TODO -> skip(SkipReason.TASK_NOT_OPEN)
                    else -> return t
                }
                return null
            }

            fun replace(updated: Task) {
                tasks = tasks.map { if (it.id == updated.id) updated else it }
            }

            when (mod) {
                is ScenarioModification.AddMember -> {
                    val valid = mod.tempId < 0 && member(mod.tempId) == null && mod.name.isNotBlank() &&
                        mod.availabilityPercent in MemberForm.MIN_AVAILABILITY..MemberForm.MAX_AVAILABILITY &&
                        mod.hoursPerDay.isFinite() && mod.hoursPerDay in MemberForm.MIN_HOURS..MemberForm.MAX_HOURS
                    if (!valid) {
                        skip(SkipReason.INVALID_VALUE)
                    } else {
                        members = members + TeamMember(
                            id = mod.tempId, uid = "$MEMBER_UID_PREFIX${mod.tempId}", name = mod.name.trim(),
                            availabilityPercent = mod.availabilityPercent, hoursPerDay = mod.hoursPerDay,
                            createdAt = stamp, updatedAt = stamp,
                        )
                    }
                }

                is ScenarioModification.RemoveMember -> activeMember(mod.memberId)?.let { m ->
                    members = members.map { if (it.id == m.id) it.copy(archived = true, isSelf = false) else it }
                    tasks = tasks.map {
                        if (it.space == TaskSpace.TEAM && it.status == TaskStatus.TODO && it.assigneeId == m.id) it.copy(assigneeId = null, startedOn = null) else it
                    }
                }

                is ScenarioModification.AddAbsence -> activeMember(mod.memberId)?.let { m ->
                    if (!MemberForm.isValidAbsence(mod.start, mod.end)) {
                        skip(SkipReason.INVALID_VALUE)
                    } else {
                        absences = absences + MemberAbsence(nextAbsenceId--, m.id, mod.start, mod.end, createdAt = stamp)
                    }
                }

                is ScenarioModification.SetAvailability -> activeMember(mod.memberId)?.let { m ->
                    if (mod.percent !in MemberForm.MIN_AVAILABILITY..MemberForm.MAX_AVAILABILITY) {
                        skip(SkipReason.INVALID_VALUE)
                    } else {
                        members = members.map { if (it.id == m.id) it.copy(availabilityPercent = mod.percent) else it }
                    }
                }

                is ScenarioModification.Reassign -> openTask(mod.taskUid)?.let { t ->
                    val target = mod.memberId
                    if (target == null || activeMember(target) != null) {
                        replace(t.copy(assigneeId = target, startedOn = if (target == t.assigneeId) t.startedOn else null))
                    }
                }

                is ScenarioModification.AddTasks -> {
                    val valid = mod.count in 1..MAX_ADDED_TASKS &&
                        (mod.points == null || mod.points in FIBONACCI_SCALE) &&
                        (mod.priority == null || mod.priority in PRIORITY_VALUES)
                    if (!valid) {
                        skip(SkipReason.INVALID_VALUE)
                    } else {
                        val added = (0 until mod.count).map { k ->
                            Task(
                                id = nextTaskId--, title = "~${index + 1}.${k + 1}", priority = mod.priority,
                                taskType = mod.category.trim(), fibonacciPoints = mod.points,
                                createdAt = stamp, updatedAt = stamp, space = TaskSpace.TEAM,
                                teamUid = hypotheticalTaskUid(index, k),
                            )
                        }
                        tasks = tasks + added
                    }
                }

                is ScenarioModification.SetPriority -> openTask(mod.taskUid)?.let { t ->
                    if (mod.priority !in PRIORITY_VALUES) skip(SkipReason.INVALID_VALUE) else replace(t.copy(priority = mod.priority))
                }

                is ScenarioModification.SetDeadline -> openTask(mod.taskUid)?.let { t -> replace(t.copy(deadline = mod.date)) }

                is ScenarioModification.SetFocus -> {
                    if (!mod.factor.isFinite() || mod.factor <= 0.0 || mod.factor > 1.0) {
                        skip(SkipReason.INVALID_VALUE)
                    } else {
                        settings = settings.copy(team = (settings.team ?: TeamSettings()).copy(focusFactor = mod.factor))
                    }
                }
            }
        }
        return snapshot.copy(members = members, absences = absences, tasks = tasks, settings = settings) to skipped
    }
}
