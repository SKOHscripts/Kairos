package com.skohscripts.kairos.data

import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.NoteStatus
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.team.MemberAbsence
import com.skohscripts.kairos.core.team.TeamEvent
import com.skohscripts.kairos.core.team.TeamEventKind
import com.skohscripts.kairos.core.team.TeamEventSource
import com.skohscripts.kairos.core.team.TeamMember
import com.skohscripts.kairos.core.team.Workspaces
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * Format d'export de Kairos 3 (docs/spec/export-import.md § Format) : un
 * fichier JSON UTF-8 lisible, versionné (`formatVersion`), qui contient toutes
 * les tables et les réglages. Les codes (statut, récurrence, type de créneau)
 * sont ceux de la base, identiques à Kairos 2.
 *
 * Isolation du mode solo (docs/spec/equipe.md § Export) : une base **sans
 * donnée d'équipe** s'exporte en `formatVersion` 1, sans aucun champ d'équipe,
 * donc octet pour octet comme avant l'espace Équipe ; `formatVersion` 2 sinon.
 */
object ExportCodec {
    const val FORMAT = "kairos-export"

    /** Version la plus récente que ce code sait lire (et écrire, dès qu'il y a des données d'équipe). */
    const val FORMAT_VERSION = 2

    /** Version écrite pour une base sans donnée d'équipe. */
    internal const val SOLO_FORMAT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(snapshot: KairosSnapshot, appVersion: String, exportedAt: Instant): String =
        json.encodeToString(ExportFile.serializer(), ExportFile.from(snapshot, appVersion, exportedAt))

    /** @throws ImportException si le texte n'est pas un export Kairos lisible. */
    fun decode(text: String): KairosSnapshot {
        val file = try {
            json.decodeFromString(ExportFile.serializer(), text)
        } catch (e: Exception) {
            throw ImportException(ImportException.Reason.NOT_AN_EXPORT, e)
        }
        if (file.format != FORMAT) throw ImportException(ImportException.Reason.NOT_AN_EXPORT)
        if (file.formatVersion > FORMAT_VERSION) throw ImportException(ImportException.Reason.TOO_NEW)
        return try {
            file.toSnapshot()
        } catch (e: Exception) {
            throw ImportException(ImportException.Reason.CORRUPTED, e)
        }
    }
}

internal fun encodeSpace(space: TaskSpace): String? = if (space == TaskSpace.TEAM) "team" else null

internal fun decodeSpace(code: String?): TaskSpace = if (code == "team") TaskSpace.TEAM else TaskSpace.PERSONAL

class ImportException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** Pas du JSON, ou pas un export Kairos. */
        NOT_AN_EXPORT,

        /** Export d'une version plus récente de Kairos (format inconnu). */
        TOO_NEW,

        /** Export Kairos aux valeurs illisibles (date invalide…). */
        CORRUPTED,
    }
}

@Serializable
private data class ExportFile(
    val format: String,
    val formatVersion: Int,
    val appVersion: String = "",
    val exportedAt: String = "",
    val settings: Settings = Settings(),
    val tasks: List<TaskJson> = emptyList(),
    val timeBlocks: List<BlockJson> = emptyList(),
    val dependencies: List<DependencyJson> = emptyList(),
    val workSessions: List<SessionJson> = emptyList(),
    val notes: List<NoteJson> = emptyList(),
    // Nullable à défaut null, et non une liste vide : avec `encodeDefaults = true`, une liste
    // vide serait écrite ; nulle, elle disparaît (`explicitNulls = false`) et l'export d'une
    // base sans donnée d'équipe reste celui d'avant, octet pour octet.
    val members: List<MemberJson>? = null,
    val absences: List<AbsenceJson>? = null,
    val teamEvents: List<TeamEventJson>? = null,
) {
    fun toSnapshot() = KairosSnapshot(
        tasks = tasks.map { it.toModel() },
        timeBlocks = timeBlocks.map { it.toModel() },
        dependencies = dependencies.map { TaskDependency(it.id, it.taskId, it.blockerId, Instant.parse(it.createdAt)) },
        workSessions = workSessions.map {
            WorkSession(it.id, it.taskId, Instant.parse(it.startedAt), it.endedAt?.let(Instant::parse), Instant.parse(it.createdAt))
        },
        notes = notes.map {
            Note(it.id, it.body, NoteStatus.fromCode(it.status), it.convertedTaskId, Instant.parse(it.createdAt), Instant.parse(it.updatedAt))
        },
        settings = settings,
        members = members.orEmpty().map { it.toModel() },
        absences = absences.orEmpty().map { it.toModel() },
        teamEvents = teamEvents.orEmpty().map { it.toModel() },
    )

    companion object {
        fun from(s: KairosSnapshot, appVersion: String, exportedAt: Instant): ExportFile {
            val team = Workspaces.hasTeamData(s)
            return ExportFile(
                format = ExportCodec.FORMAT,
                formatVersion = if (team) ExportCodec.FORMAT_VERSION else ExportCodec.SOLO_FORMAT_VERSION,
                appVersion = appVersion,
                exportedAt = exportedAt.toString(),
                settings = s.settings,
                tasks = s.tasks.map { TaskJson.from(it) },
                timeBlocks = s.timeBlocks.map { BlockJson.from(it) },
                dependencies = s.dependencies.map { DependencyJson(it.id, it.taskId, it.blockerId, it.createdAt.toString()) },
                workSessions = s.workSessions.map {
                    SessionJson(it.id, it.taskId, it.startedAt.toString(), it.endedAt?.toString(), it.createdAt.toString())
                },
                notes = s.notes.map {
                    NoteJson(it.id, it.body, it.status.code, it.convertedTaskId, it.createdAt.toString(), it.updatedAt.toString())
                },
                members = if (team) s.members.map { MemberJson.from(it) } else null,
                absences = if (team) s.absences.map { AbsenceJson.from(it) } else null,
                teamEvents = if (team) s.teamEvents.map { TeamEventJson.from(it) } else null,
            )
        }
    }
}

@Serializable
private data class TaskJson(
    val id: Long,
    val title: String,
    val description: String = "",
    val priority: Int? = null,
    val deadline: String? = null,
    val projectTag: String = "",
    val status: String = "todo",
    val estimatedMinutes: Int? = null,
    val pinnedStart: String? = null,
    val parentId: Long? = null,
    val recurrence: String = "",
    val scheduledDate: String? = null,
    val recurrenceDayOfMonth: Int? = null,
    val recurrenceDayOfWeek: Int? = null,
    val recurrencePeriod: String = "",
    val taskType: String = "",
    val fibonacciPoints: Int? = null,
    val manualTimeSpentMinutes: Int? = null,
    val createdAt: String,
    val updatedAt: String,
    // Champs d'équipe (formatVersion 2) : nullables à défaut null pour disparaître en version 1
    // (`encodeDefaults = true` écrirait sinon « "space": "personal" » sur toute tâche solo).
    // `space` n'est écrit que pour une tâche d'équipe ; absent = Perso.
    val space: String? = null,
    val assigneeId: Long? = null,
    // Suivi d'équipe (jalon E3) : mêmes règles, nuls donc omis pour toute tâche Perso.
    val progressPercent: Int? = null,
    val startedOn: String? = null,
    val teamUid: String? = null,
) {
    fun toModel() = Task(
        id = id,
        title = title,
        description = description,
        priority = priority,
        deadline = deadline?.let(LocalDate::parse),
        projectTag = projectTag,
        status = TaskStatus.fromCode(status),
        estimatedMinutes = estimatedMinutes,
        pinnedStart = pinnedStart?.let(LocalDateTime::parse),
        parentId = parentId,
        recurrence = TaskRecurrence.fromCode(recurrence),
        scheduledDate = scheduledDate?.let(LocalDate::parse),
        recurrenceDayOfMonth = recurrenceDayOfMonth,
        recurrenceDayOfWeek = recurrenceDayOfWeek,
        recurrencePeriod = recurrencePeriod,
        taskType = taskType,
        fibonacciPoints = fibonacciPoints,
        manualTimeSpentMinutes = manualTimeSpentMinutes,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        space = decodeSpace(space),
        assigneeId = assigneeId,
        progressPercent = progressPercent,
        startedOn = startedOn?.let(LocalDate::parse),
        teamUid = teamUid,
    )

    companion object {
        fun from(t: Task) = TaskJson(
            t.id, t.title, t.description, t.priority, t.deadline?.toString(), t.projectTag, t.status.code,
            t.estimatedMinutes, t.pinnedStart?.toString(), t.parentId, t.recurrence.code, t.scheduledDate?.toString(),
            t.recurrenceDayOfMonth, t.recurrenceDayOfWeek, t.recurrencePeriod, t.taskType, t.fibonacciPoints,
            t.manualTimeSpentMinutes, t.createdAt.toString(), t.updatedAt.toString(),
            encodeSpace(t.space), t.assigneeId, t.progressPercent, t.startedOn?.toString(), t.teamUid,
        )
    }
}

@Serializable
private data class BlockJson(
    val id: Long,
    val title: String,
    val start: String,
    val end: String,
    val kind: String = "busy",
    val recurrence: String = "",
    val createdAt: String,
) {
    fun toModel() = TimeBlock(
        id, title, LocalDateTime.parse(start), LocalDateTime.parse(end),
        BlockKind.fromCode(kind), BlockRecurrence.fromCode(recurrence), Instant.parse(createdAt),
    )

    companion object {
        fun from(b: TimeBlock) = BlockJson(
            b.id, b.title, b.start.toString(), b.end.toString(), b.kind.code, b.recurrence.code, b.createdAt.toString(),
        )
    }
}

@Serializable
private data class DependencyJson(val id: Long, val taskId: Long, val blockerId: Long, val createdAt: String)

@Serializable
private data class SessionJson(
    val id: Long,
    val taskId: Long,
    val startedAt: String,
    val endedAt: String? = null,
    val createdAt: String,
)

@Serializable
private data class NoteJson(
    val id: Long,
    val body: String,
    val status: String = "open",
    val convertedTaskId: Long? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
private data class MemberJson(
    val id: Long,
    val uid: String,
    val name: String,
    val role: String = "",
    val availabilityPercent: Int = 100,
    val hoursPerDay: Double,
    val isSelf: Boolean = false,
    val archived: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
) {
    fun toModel() = TeamMember(
        id, uid, name, role, availabilityPercent, hoursPerDay, isSelf, archived,
        Instant.parse(createdAt), Instant.parse(updatedAt),
    )

    companion object {
        fun from(m: TeamMember) = MemberJson(
            m.id, m.uid, m.name, m.role, m.availabilityPercent, m.hoursPerDay, m.isSelf, m.archived,
            m.createdAt.toString(), m.updatedAt.toString(),
        )
    }
}

@Serializable
private data class AbsenceJson(
    val id: Long,
    val memberId: Long,
    val start: String,
    val end: String,
    val label: String = "",
    val createdAt: String,
) {
    fun toModel() = MemberAbsence(id, memberId, LocalDate.parse(start), LocalDate.parse(end), label, Instant.parse(createdAt))

    companion object {
        fun from(a: MemberAbsence) = AbsenceJson(a.id, a.memberId, a.start.toString(), a.end.toString(), a.label, a.createdAt.toString())
    }
}

@Serializable
private data class TeamEventJson(
    val id: Long,
    val taskId: Long,
    val taskTitle: String,
    val memberId: Long? = null,
    val kind: String,
    val fromValue: String? = null,
    val toValue: String? = null,
    val source: String = "manual",
    val at: String,
) {
    fun toModel() = TeamEvent(
        id, taskId, taskTitle, memberId, TeamEventKind.fromCode(kind), fromValue, toValue,
        TeamEventSource.fromCode(source), Instant.parse(at),
    )

    companion object {
        fun from(e: TeamEvent) = TeamEventJson(
            e.id, e.taskId, e.taskTitle, e.memberId, e.kind.code, e.fromValue, e.toValue, e.source.code, e.at.toString(),
        )
    }
}
