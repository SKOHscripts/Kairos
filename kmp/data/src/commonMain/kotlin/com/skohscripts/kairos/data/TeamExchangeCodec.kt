package com.skohscripts.kairos.data

import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.team.exchange.ExternalBlocker
import com.skohscripts.kairos.core.team.exchange.PackDependency
import com.skohscripts.kairos.core.team.exchange.PackMember
import com.skohscripts.kairos.core.team.exchange.PackTask
import com.skohscripts.kairos.core.team.exchange.PackTeam
import com.skohscripts.kairos.core.team.exchange.ReportSubtask
import com.skohscripts.kairos.core.team.exchange.ReportTask
import com.skohscripts.kairos.core.team.exchange.TeamPack
import com.skohscripts.kairos.core.team.exchange.TeamReport
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * Formats d'échange de l'espace Équipe (docs/spec/equipe-echanges.md § Formats) : le **paquet**
 * (`kairos-team-pack`, manager -> membre) et le **rapport** (`kairos-team-report`, membre -> manager). Deux
 * fichiers JSON UTF-8 indentés, distincts de l'export complet (`kairos-export`) par leur champ `format`.
 *
 * Même contrat d'erreurs que [ExportCodec] ([ImportException]) : texte qui n'est pas du JSON, ou d'un autre
 * `format` -> `NOT_AN_EXPORT` ; `formatVersion` plus récente que [FORMAT_VERSION] -> `TOO_NEW` ; valeurs
 * illisibles (date invalide, identité vide) -> `CORRUPTED`. Champs inconnus ignorés, champs nuls omis.
 * La version est lue **avant** le reste du fichier : un fichier d'une version future, dont la structure a pu
 * changer, est refusé comme `TOO_NEW` et non comme illisible.
 */
object TeamExchangeCodec {
    const val PACK_FORMAT = "kairos-team-pack"
    const val REPORT_FORMAT = "kairos-team-report"

    /** Version des deux formats, lue et écrite. */
    const val FORMAT_VERSION = 1

    /** Ce qu'est un fichier de l'utilisateur, d'après son champ `format` seul. */
    enum class Kind { EXPORT, PACK, REPORT }

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Aiguillage du bouton « Importer » : lit `format` **d'abord**, sans interpréter le reste. Jamais de confusion
     * entre les trois formats : chacun n'est reconnu que par sa valeur exacte.
     * @throws ImportException `NOT_AN_EXPORT` si le texte n'est pas un objet JSON portant un `format` connu.
     */
    fun detect(text: String): Kind {
        val format = try {
            ((json.parseToJsonElement(text) as? JsonObject)?.get("format") as? JsonPrimitive)?.takeIf { it.isString }?.content
        } catch (e: Exception) {
            throw ImportException(ImportException.Reason.NOT_AN_EXPORT, e)
        }
        return when (format) {
            ExportCodec.FORMAT -> Kind.EXPORT
            PACK_FORMAT -> Kind.PACK
            REPORT_FORMAT -> Kind.REPORT
            else -> throw ImportException(ImportException.Reason.NOT_AN_EXPORT)
        }
    }

    fun encodePack(pack: TeamPack, appVersion: String): String = json.encodeToString(PackFile.serializer(), PackFile.from(pack, appVersion))

    /** @throws ImportException si le texte n'est pas un paquet lisible. */
    fun decodePack(text: String): TeamPack = decode(text, PACK_FORMAT, PackFile.serializer()) { it.toModel() }

    fun encodeReport(report: TeamReport, appVersion: String): String =
        json.encodeToString(ReportFile.serializer(), ReportFile.from(report, appVersion))

    /** @throws ImportException si le texte n'est pas un rapport lisible. */
    fun decodeReport(text: String): TeamReport = decode(text, REPORT_FORMAT, ReportFile.serializer()) { it.toModel() }

    private fun <F, M> decode(text: String, format: String, serializer: kotlinx.serialization.KSerializer<F>, toModel: (F) -> M): M {
        val header = try {
            json.decodeFromString(Header.serializer(), text)
        } catch (e: Exception) {
            throw ImportException(ImportException.Reason.NOT_AN_EXPORT, e)
        }
        if (header.format != format) throw ImportException(ImportException.Reason.NOT_AN_EXPORT)
        if (header.formatVersion > FORMAT_VERSION) throw ImportException(ImportException.Reason.TOO_NEW)
        return try {
            toModel(json.decodeFromString(serializer, text))
        } catch (e: Exception) {
            throw ImportException(ImportException.Reason.CORRUPTED, e)
        }
    }
}

@Serializable
private data class Header(val format: String, val formatVersion: Int = 1)

private fun String.requireText(what: String): String = also { require(isNotBlank()) { "$what manquant" } }

@Serializable
private data class TeamJson(val uid: String, val name: String = "", val manager: String = "")

@Serializable
private data class MemberRefJson(val uid: String, val name: String = "")

@Serializable
private data class PackTaskJson(
    val uid: String,
    val parentUid: String? = null,
    val title: String,
    val description: String? = null,
    val priority: Int? = null,
    val fibonacciPoints: Int? = null,
    val taskType: String? = null,
    val deadline: String? = null,
    val estimatedMinutes: Int? = null,
    val progressPercent: Int? = null,
)

@Serializable
private data class PackDependencyJson(val taskUid: String, val blockerUid: String)

@Serializable
private data class ExternalBlockerJson(val taskUid: String, val title: String, val assignee: String? = null)

@Serializable
private data class PackFile(
    val format: String,
    val formatVersion: Int,
    val appVersion: String = "",
    val packId: String,
    val exportedAt: String,
    val team: TeamJson,
    val member: MemberRefJson,
    val tasks: List<PackTaskJson> = emptyList(),
    val dependencies: List<PackDependencyJson> = emptyList(),
    val externalBlockers: List<ExternalBlockerJson> = emptyList(),
) {
    fun toModel(): TeamPack {
        packId.requireText("packId")
        team.uid.requireText("team.uid")
        member.uid.requireText("member.uid")
        return TeamPack(
            packId = packId,
            exportedAt = Instant.parse(exportedAt),
            team = PackTeam(team.uid, team.name, team.manager),
            member = PackMember(member.uid, member.name),
            tasks = tasks.map {
                PackTask(
                    uid = it.uid.requireText("task.uid"),
                    parentUid = it.parentUid,
                    title = it.title,
                    description = it.description.orEmpty(),
                    priority = it.priority,
                    fibonacciPoints = it.fibonacciPoints,
                    taskType = it.taskType.orEmpty(),
                    deadline = it.deadline?.let(LocalDate::parse),
                    estimatedMinutes = it.estimatedMinutes,
                    progressPercent = it.progressPercent,
                )
            },
            dependencies = dependencies.map { PackDependency(it.taskUid, it.blockerUid) },
            externalBlockers = externalBlockers.map { ExternalBlocker(it.taskUid, it.title, it.assignee) },
        )
    }

    companion object {
        fun from(p: TeamPack, appVersion: String) = PackFile(
            format = TeamExchangeCodec.PACK_FORMAT,
            formatVersion = TeamExchangeCodec.FORMAT_VERSION,
            appVersion = appVersion,
            packId = p.packId,
            exportedAt = p.exportedAt.toString(),
            team = TeamJson(p.team.uid, p.team.name, p.team.manager),
            member = MemberRefJson(p.member.uid, p.member.name),
            tasks = p.tasks.map {
                PackTaskJson(
                    uid = it.uid,
                    parentUid = it.parentUid,
                    title = it.title,
                    // Valeurs vides omises (comme les nuls) : un paquet reste court et lisible.
                    description = it.description.ifEmpty { null },
                    priority = it.priority,
                    fibonacciPoints = it.fibonacciPoints,
                    taskType = it.taskType.ifEmpty { null },
                    deadline = it.deadline?.toString(),
                    estimatedMinutes = it.estimatedMinutes,
                    progressPercent = it.progressPercent,
                )
            },
            dependencies = p.dependencies.map { PackDependencyJson(it.taskUid, it.blockerUid) },
            externalBlockers = p.externalBlockers.map { ExternalBlockerJson(it.taskUid, it.title, it.assignee) },
        )
    }
}

@Serializable
private data class ReportTaskJson(
    val uid: String,
    val status: String = "todo",
    val doneOn: String? = null,
    val startedOn: String? = null,
    val progressPercent: Int? = null,
    val spentMinutes: Int = 0,
)

@Serializable
private data class ReportSubtaskJson(val uid: String, val parentUid: String, val title: String, val status: String = "todo")

@Serializable
private data class ReportFile(
    val format: String,
    val formatVersion: Int,
    val appVersion: String = "",
    val reportedAt: String,
    val team: TeamRefJson,
    val member: MemberRefJson,
    val tasks: List<ReportTaskJson> = emptyList(),
    val newSubtasks: List<ReportSubtaskJson> = emptyList(),
) {
    @Serializable
    data class TeamRefJson(val uid: String)

    fun toModel(): TeamReport {
        team.uid.requireText("team.uid")
        member.uid.requireText("member.uid")
        return TeamReport(
            reportedAt = Instant.parse(reportedAt),
            teamUid = team.uid,
            member = PackMember(member.uid, member.name),
            tasks = tasks.map {
                ReportTask(
                    uid = it.uid.requireText("task.uid"),
                    status = status(it.status),
                    doneOn = it.doneOn?.let(LocalDate::parse),
                    startedOn = it.startedOn?.let(LocalDate::parse),
                    progressPercent = it.progressPercent,
                    spentMinutes = it.spentMinutes,
                )
            },
            newSubtasks = newSubtasks.map {
                ReportSubtask(it.uid.requireText("subtask.uid"), it.parentUid.requireText("subtask.parentUid"), it.title, status(it.status))
            },
        )
    }

    companion object {
        /** « done » ou « todo » : un rapport ne dit rien d'autre ; un code inconnu se lit « à faire ». */
        private fun status(code: String) = if (code == TaskStatus.DONE.code) TaskStatus.DONE else TaskStatus.TODO

        fun from(r: TeamReport, appVersion: String) = ReportFile(
            format = TeamExchangeCodec.REPORT_FORMAT,
            formatVersion = TeamExchangeCodec.FORMAT_VERSION,
            appVersion = appVersion,
            reportedAt = r.reportedAt.toString(),
            team = TeamRefJson(r.teamUid),
            member = MemberRefJson(r.member.uid, r.member.name),
            tasks = r.tasks.map {
                ReportTaskJson(
                    uid = it.uid,
                    status = if (it.status == TaskStatus.DONE) "done" else "todo",
                    doneOn = it.doneOn?.toString(),
                    startedOn = it.startedOn?.toString(),
                    progressPercent = it.progressPercent,
                    spentMinutes = it.spentMinutes,
                )
            },
            newSubtasks = r.newSubtasks.map {
                ReportSubtaskJson(it.uid, it.parentUid, it.title, if (it.status == TaskStatus.DONE) "done" else "todo")
            },
        )
    }
}
