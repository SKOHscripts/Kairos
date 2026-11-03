package com.skohscripts.kairos.data

import com.skohscripts.kairos.core.model.ExchangeNote
import com.skohscripts.kairos.core.model.BlockException
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.FIBONACCI_SCALE
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import com.skohscripts.kairos.core.settings.SettingsForm
import com.skohscripts.kairos.core.sync.Context
import com.skohscripts.kairos.core.sync.Hlc
import com.skohscripts.kairos.core.sync.Marks
import com.skohscripts.kairos.core.sync.RecordKind
import com.skohscripts.kairos.core.sync.RecordRef
import com.skohscripts.kairos.core.sync.Stamp
import com.skohscripts.kairos.core.sync.SyncData
import com.skohscripts.kairos.core.sync.SyncReconcile
import com.skohscripts.kairos.core.sync.SyncScope
import com.skohscripts.kairos.core.sync.Synced
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.DayMark
import com.skohscripts.kairos.core.model.BlockSource
import com.skohscripts.kairos.core.model.CalendarSource
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
import com.skohscripts.kairos.core.team.forecast.ScenarioCodec
import com.skohscripts.kairos.core.team.forecast.TeamScenario
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.time.Instant

/**
 * Format d'export de Kairos 3 (docs/spec/export-import.md § Format) : un
 * fichier JSON UTF-8 lisible, versionné (`formatVersion`), qui contient toutes
 * les tables et les réglages. Les codes (statut, récurrence, type de créneau)
 * sont ceux de la base, ceux de la base.
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

    fun encode(snapshot: KairosSnapshot, appVersion: String, exportedAt: Instant, sync: SyncData? = null): String =
        json.encodeToString(ExportFile.serializer(), ExportFile.from(snapshot, appVersion, exportedAt).let { if (sync == null) it else it.synced(snapshot, sync) })

    /** Version écrite par une installation synchronisée (docs/spec/synchronisation.md § Format). */
    const val SYNC_FORMAT_VERSION = 3

    /**
     * Comme [decode], et l'état de synchronisation du fichier (docs/spec/synchronisation.md § Format) : pour un format 3, les
     * `uid`, marques et contexte qu'il porte ; pour un format 1 ou 2, celui d'un fichier d'avant la synchronisation
     * ([SyncReconcile.legacy]). Validations : `uid` uniques par genre et de [SyncScope.MAX_UID] caractères au plus, marques bien
     * formées, au plus [Context.MAX_DEVICES] appareils ; `CORRUPTED` sinon, rien n'est rendu en partie.
     */
    fun decodeSynced(text: String): Synced {
        val snapshot = decode(text)
        val root = json.parseToJsonElement(text.removePrefix("\uFEFF")) as JsonObject
        val file = try {
            json.decodeFromJsonElement(ExportFile.serializer(), root)
        } catch (e: Exception) {
            throw ImportException(ImportException.Reason.CORRUPTED, e)
        }
        val sync = file.sync ?: return Synced(snapshot, SyncReconcile.legacy(snapshot))
        return try {
            Synced(snapshot, file.syncData(snapshot, sync))
        } catch (e: Exception) {
            throw ImportException(ImportException.Reason.CORRUPTED, e)
        }
    }

    /** Titre le plus long accepté à l'import : au-delà, l'affichage d'une seule ligne gelait l'interface (2 Mo). */
    internal const val MAX_TITLE = 10_000

    /**
     * @throws ImportException si le texte n'est pas un export Kairos lisible. En deux temps : un JSON qui n'est pas un
     * objet `kairos-export` n'est « pas un export » ; un export Kairos aux champs mal typés, absents ou hors des règles
     * de l'application (réglages hors bornes, identifiants en double, priorité inconnue…) est « illisible » et n'est
     * jamais importé, même en partie (docs/spec/export-import.md § Import).
     */
    fun decode(text: String): KairosSnapshot {
        // Un fichier enregistré par le Bloc-notes de Windows commence par une marque d'ordre des octets.
        val element = try {
            json.parseToJsonElement(text.removePrefix("\uFEFF"))
        } catch (e: Exception) {
            throw ImportException(ImportException.Reason.NOT_AN_EXPORT, e)
        }
        val root = element as? JsonObject ?: throw ImportException(ImportException.Reason.NOT_AN_EXPORT)
        if ((root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content != FORMAT) throw ImportException(ImportException.Reason.NOT_AN_EXPORT)
        val version = (root["formatVersion"] as? JsonPrimitive)?.intOrNull ?: throw ImportException(ImportException.Reason.NOT_AN_EXPORT)
        if (version > SYNC_FORMAT_VERSION) throw ImportException(ImportException.Reason.TOO_NEW)
        return try {
            json.decodeFromJsonElement(ExportFile.serializer(), root).toSnapshot().also(::check)
        } catch (e: Exception) {
            throw ImportException(ImportException.Reason.CORRUPTED, e)
        }
    }

    /** Les règles que la saisie garantit, vérifiées sur un fichier venu d'ailleurs ; `IllegalArgumentException` sinon. */
    private fun check(s: KairosSnapshot) {
        // Réglages : mêmes bornes et règles que le formulaire (une heure 25 faisait planter les vues Jour et Semaine).
        require(SettingsForm.validate(SettingsForm.values(s.settings), s.settings).settings != null) { "réglages" }
        fun unique(ids: List<Long>, what: String) = require(ids.size == ids.toSet().size) { "identifiants en double : $what" }
        unique(s.tasks.map { it.id }, "tâches")
        unique(s.timeBlocks.map { it.id }, "créneaux")
        unique(s.dependencies.map { it.id }, "dépendances")
        unique(s.workSessions.map { it.id }, "sessions")
        unique(s.notes.map { it.id }, "notes")
        unique(s.members.map { it.id }, "membres")
        unique(s.absences.map { it.id }, "absences")
        unique(s.teamEvents.map { it.id }, "journal")
        unique(s.teamScenarios.map { it.id }, "scénarios")
        val ids = s.tasks.mapTo(HashSet()) { it.id }
        for (t in s.tasks) {
            require(t.title.length <= MAX_TITLE) { "titre trop long" }
            require(t.priority == null || t.priority in PRIORITY_VALUES) { "priorité" }
            require(t.fibonacciPoints == null || t.fibonacciPoints in FIBONACCI_SCALE) { "points" }
            require((t.estimatedMinutes ?: 0) >= 0 && (t.manualTimeSpentMinutes ?: 0) >= 0) { "durées" }
        }
        for (d in s.dependencies) require(d.taskId in ids && d.blockerId in ids && d.taskId != d.blockerId) { "dépendance" }
        for (b in s.timeBlocks) require(b.end >= b.start) { "créneau" }
        for (m in s.members) require(m.weekHours?.let(TeamMember::isValidWeek) ?: true) { "semaine type" }
        unique(s.blockExceptions.map { it.id }, "exceptions de créneau")
        require(s.blockExceptions.map { it.blockId to it.date }.toSet().size == s.blockExceptions.size) { "exceptions de créneau en double" }
        val blockIds = s.timeBlocks.mapTo(HashSet()) { it.id }
        for (x in s.blockExceptions) {
            require(x.blockId in blockIds) { "exception de créneau" }
            val (start, end) = x.start to x.end
            require(start == null || end == null || end > start) { "exception de créneau" }
        }
        // Calendriers importés (3.10.0) : un nom, une source qui existe, des dates dans l'ordre.
        unique(s.calendarSources.map { it.id }, "calendriers")
        unique(s.dayMarks.map { it.id }, "journées de calendrier")
        for (c in s.calendarSources) require(c.name.isNotBlank()) { "calendrier" }
        val calendarIds = s.calendarSources.mapTo(HashSet()) { it.id }
        fun known(src: BlockSource?) = src == null || (src is BlockSource.Imported && src.calendarId in calendarIds)
        for (m in s.dayMarks) require(m.end >= m.start && known(m.source)) { "journée de calendrier" }
        for (b in s.timeBlocks) require(known(b.source)) { "source de créneau" }
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
    // Scénarios « Et si… ? » (jalon E5) : même règle, nul donc omis en version 1.
    val teamScenarios: List<ScenarioJson>? = null,
    // Exceptions d'occurrences de créneaux (3.8.0, issue #110) : nul tant qu'il n'y en a aucune, donc omis, et l'export
    // d'une base qui n'en a jamais posé reste celui d'avant. Ce n'est pas une donnée d'équipe : sans effet sur formatVersion.
    val blockExceptions: List<BlockExceptionJson>? = null,
    // Calendriers importés et leurs journées entières (3.10.0, issue #136) : même règle, nuls tant qu'il n'y en a aucun.
    val calendarSources: List<CalendarSourceJson>? = null,
    val dayMarks: List<DayMarkJson>? = null,
    // Synchronisation (formatVersion 3) : nul, donc omis, pour une base qui ne synchronise pas.
    val sync: SyncJson? = null,
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
        teamScenarios = teamScenarios.orEmpty().map { it.toModel() },
        blockExceptions = blockExceptions.orEmpty().map { it.toModel() },
        calendarSources = calendarSources.orEmpty().map { it.toModel() },
        dayMarks = dayMarks.orEmpty().map { it.toModel() },
    )

    /** La même exportation, en format 3 : chaque enregistrement de la portée porte son `uid` et ses marques, la racine le contexte. */
    fun synced(s: KairosSnapshot, d: SyncData): ExportFile {
        fun tag(kind: RecordKind, id: Long): Tag? {
            val uid = d.uidOf(kind, id) ?: return null
            val m = d.marks[RecordRef(kind, uid)] ?: return null
            return Tag(uid, m.born.toString(), m.base.takeIf { it != m.born }?.toString(), m.stamps.takeIf { it.isNotEmpty() }?.mapValues { it.value.toString() }?.sortedByKey())
        }
        return copy(
            formatVersion = ExportCodec.SYNC_FORMAT_VERSION,
            tasks = tasks.map { j -> tag(RecordKind.TASK, j.id)?.let { j.copy(uid = it.uid, born = it.born, base = it.base, stamps = it.stamps) } ?: j },
            timeBlocks = timeBlocks.map { j -> tag(RecordKind.BLOCK, j.id)?.let { j.copy(uid = it.uid, born = it.born, base = it.base, stamps = it.stamps) } ?: j },
            dependencies = dependencies.map { j -> tag(RecordKind.DEPENDENCY, j.id)?.let { j.copy(uid = it.uid, born = it.born, base = it.base, stamps = it.stamps) } ?: j },
            workSessions = workSessions.map { j -> tag(RecordKind.SESSION, j.id)?.let { j.copy(uid = it.uid, born = it.born, base = it.base, stamps = it.stamps) } ?: j },
            notes = notes.map { j -> tag(RecordKind.NOTE, j.id)?.let { j.copy(uid = it.uid, born = it.born, base = it.base, stamps = it.stamps) } ?: j },
            blockExceptions = blockExceptions?.map { j -> tag(RecordKind.EXCEPTION, j.id)?.let { j.copy(uid = it.uid, born = it.born, base = it.base, stamps = it.stamps) } ?: j },
            calendarSources = calendarSources?.map { j -> tag(RecordKind.CALENDAR, j.id)?.let { j.copy(uid = it.uid, born = it.born, base = it.base, stamps = it.stamps) } ?: j },
            dayMarks = dayMarks?.map { j -> tag(RecordKind.DAYMARK, j.id)?.let { j.copy(uid = it.uid, born = it.born, base = it.base, stamps = it.stamps) } ?: j },
            sync = SyncJson(
                device = d.device,
                label = d.label,
                seen = d.seen.mapValues { it.value.toString() }.sortedByKey(),
                labels = d.labels.takeIf { it.isNotEmpty() }?.sortedByKey(),
                settings = MarksJson(d.settings.base.toString(), d.settings.stamps.takeIf { it.isNotEmpty() }?.mapValues { it.value.toString() }?.sortedByKey()),
                // Phantômes (tâches supprimées encore référencées) : leur uid, pour que les références se lisent pareil ailleurs.
                phantoms = s.tasks.mapTo(HashSet()) { it.id }.let { present -> d.uids[RecordKind.TASK].orEmpty().filterKeys { it !in present } }.takeIf { it.isNotEmpty() }
                    ?.entries?.associate { (id, uid) -> id.toString() to uid }?.sortedByKey(),
            ),
        )
    }

    /** L'état de synchronisation porté par un fichier de format 3 ; `IllegalArgumentException` s'il est mal formé. */
    fun syncData(s: KairosSnapshot, sync: SyncJson): SyncData {
        fun stamp(text: String?): Stamp = requireNotNull(text?.let(Stamp::parse)) { "marque" }
        require(sync.device.isNotEmpty() && sync.device.length <= Stamp.MAX_DEVICE) { "appareil" }
        require(sync.seen.size <= Context.MAX_DEVICES) { "trop d'appareils" }
        val seen = sync.seen.mapValues { requireNotNull(Hlc.parse(it.value)) { "contexte" } }
        val uids = HashMap<RecordKind, HashMap<Long, String>>()
        val marks = HashMap<RecordRef, Marks>()
        fun take(kind: RecordKind, id: Long, uid: String?, born: String?, base: String?, stamps: Map<String, String>?) {
            uid ?: return
            require(uid.isNotEmpty() && uid.length <= SyncScope.MAX_UID) { "uid" }
            val m = uids.getOrPut(kind) { HashMap() }
            require(uid !in m.values) { "uid en double" }
            m[id] = uid
            val b = stamp(born)
            marks[RecordRef(kind, uid)] = Marks(b, base?.let(::stamp) ?: b, stamps.orEmpty().mapValues { stamp(it.value) })
        }
        tasks.forEach { take(RecordKind.TASK, it.id, it.uid, it.born, it.base, it.stamps) }
        timeBlocks.forEach { take(RecordKind.BLOCK, it.id, it.uid, it.born, it.base, it.stamps) }
        dependencies.forEach { take(RecordKind.DEPENDENCY, it.id, it.uid, it.born, it.base, it.stamps) }
        workSessions.forEach { take(RecordKind.SESSION, it.id, it.uid, it.born, it.base, it.stamps) }
        notes.forEach { take(RecordKind.NOTE, it.id, it.uid, it.born, it.base, it.stamps) }
        blockExceptions.orEmpty().forEach { take(RecordKind.EXCEPTION, it.id, it.uid, it.born, it.base, it.stamps) }
        calendarSources.orEmpty().forEach { take(RecordKind.CALENDAR, it.id, it.uid, it.born, it.base, it.stamps) }
        dayMarks.orEmpty().forEach { take(RecordKind.DAYMARK, it.id, it.uid, it.born, it.base, it.stamps) }
        sync.phantoms.orEmpty().forEach { (id, uid) ->
            require(uid.isNotEmpty() && uid.length <= SyncScope.MAX_UID) { "uid" }
            uids.getOrPut(RecordKind.TASK) { HashMap() }[id.toLong()] = uid
        }
        // Un enregistrement de la portée sans uid (fichier écrit à la main) : ses marques sont celles d'un fichier ancien.
        val legacy = SyncReconcile.legacy(s)
        val scoped = SyncScope.of(s)
        for (kind in RecordKind.entries) for (id in scoped.ids(kind)) {
            if (uids[kind]?.containsKey(id) == true) continue
            val uid = legacy.uidOf(kind, id)!!
            require(uids[kind]?.values?.contains(uid) != true) { "uid en double" }
            uids.getOrPut(kind) { HashMap() }[id] = uid
            marks[RecordRef(kind, uid)] = legacy.marks.getValue(RecordRef(kind, uid))
        }
        val settings = sync.settings.let { stamp(it.base).let { base -> Marks(base, base, it.stamps.orEmpty().mapValues { (_, v) -> stamp(v) }) } }
        val clock = seen.values.fold(Hlc.ZERO) { a, b -> if (b > a) b else a }
        val data = SyncData(sync.device, clock, seen, uids, marks, settings, sync.label.take(LABEL_MAX), sync.labels.orEmpty())
        // Les empreintes se recalculent d'après le contenu du fichier (elles ne sont pas écrites).
        val withHashes = SyncReconcile.rehash(s, data)
        return withHashes
    }

    private data class Tag(val uid: String, val born: String, val base: String?, val stamps: Map<String, String>?)

    companion object {
        /** Longueur maximale du nom d'un appareil. */
        const val LABEL_MAX = 30

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
                teamScenarios = if (team) s.teamScenarios.map { ScenarioJson.from(it) } else null,
                blockExceptions = s.blockExceptions.takeIf { it.isNotEmpty() }?.map { BlockExceptionJson.from(it) },
                calendarSources = s.calendarSources.takeIf { it.isNotEmpty() }?.map { CalendarSourceJson.from(it) },
                dayMarks = s.dayMarks.mapNotNull { DayMarkJson.from(it) }.takeIf { it.isNotEmpty() },
            )
        }
    }
}

/** Racine `sync` d'un fichier de format 3 (docs/spec/synchronisation.md § Format). */
@Serializable
private data class SyncJson(
    val device: String,
    val label: String = "",
    val seen: Map<String, String> = emptyMap(),
    val labels: Map<String, String>? = null,
    val settings: MarksJson,
    val phantoms: Map<String, String>? = null,
)

@Serializable
private data class MarksJson(val base: String, val stamps: Map<String, String>? = null)

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
    // Échanges par fichier (jalon E6) : mêmes règles. `originRemoved` n'est écrit que s'il est vrai
    // (un booléen par défaut serait écrit sur toute tâche, `encodeDefaults = true`).
    val origin: String? = null,
    val originRemoved: Boolean? = null,
    val reportedMinutes: Int? = null,
    // Travail perso de « moi » (docs/spec/equipe-moi.md) : écrit seulement s'il est vrai, mêmes règles ; une tâche
    // exclue fait de l'export un export d'équipe (`Workspaces.hasTeamData`).
    val teamExcluded: Boolean? = null,
    // Série d'une tâche récurrente (docs/spec/durees-apprises.md) : nul, donc omis, pour toute tâche d'avant la 3.4.
    val seriesId: Long? = null,
    // Notes des échanges d'équipe (3.9.0, docs/spec/equipe-echanges.md § Notes) : omises quand vides.
    val noteOut: String? = null,
    val noteIn: ExchangeNoteJson? = null,
    // Proposition au manager (3.9.0, docs/spec/equipe-echanges.md § Propositions) : omise quand nulle.
    val proposedTo: String? = null,
    // Synchronisation (formatVersion 3, docs/spec/synchronisation.md § Format) : nuls, donc omis, hors synchronisation.
    val uid: String? = null,
    val born: String? = null,
    val base: String? = null,
    val stamps: Map<String, String>? = null,
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
        origin = origin,
        originRemoved = originRemoved == true,
        reportedMinutes = reportedMinutes,
        teamExcluded = teamExcluded == true,
        seriesId = seriesId,
        noteOut = ExchangeNote.clean(noteOut).orEmpty(),
        noteIn = noteIn?.toModel(),
        proposedTo = proposedTo,
    )

    companion object {
        fun from(t: Task) = TaskJson(
            t.id, t.title, t.description, t.priority, t.deadline?.toString(), t.projectTag, t.status.code,
            t.estimatedMinutes, t.pinnedStart?.toString(), t.parentId, t.recurrence.code, t.scheduledDate?.toString(),
            t.recurrenceDayOfMonth, t.recurrenceDayOfWeek, t.recurrencePeriod, t.taskType, t.fibonacciPoints,
            t.manualTimeSpentMinutes, t.createdAt.toString(), t.updatedAt.toString(),
            encodeSpace(t.space), t.assigneeId, t.progressPercent, t.startedOn?.toString(), t.teamUid,
            t.origin, if (t.originRemoved) true else null, t.reportedMinutes, if (t.teamExcluded) true else null,
            t.seriesId, t.noteOut.ifEmpty { null }, t.noteIn?.let(ExchangeNoteJson::from), t.proposedTo,
        )
    }
}

/** Note reçue d'un échange d'équipe (docs/spec/equipe-echanges.md § Notes) : texte borné à la lecture. */
@Serializable
private data class ExchangeNoteJson(val text: String, val at: String, val from: String = "") {
    fun toModel() = ExchangeNote(text.take(ExchangeNote.MAX_LENGTH), Instant.parse(at), from)

    companion object {
        fun from(n: ExchangeNote) = ExchangeNoteJson(n.text, n.at.toString(), n.from)
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
    // Calendrier importé d'origine (3.10.0, docs/spec/connexions-externes.md) : nuls, donc omis, pour un créneau saisi à la main.
    val sourceId: Long? = null,
    val sourceUid: String? = null,
    // Synchronisation (formatVersion 3, docs/spec/synchronisation.md § Format) : nuls, donc omis, hors synchronisation.
    val uid: String? = null,
    val born: String? = null,
    val base: String? = null,
    val stamps: Map<String, String>? = null,
) {
    fun toModel() = TimeBlock(
        id, title, LocalDateTime.parse(start), LocalDateTime.parse(end),
        BlockKind.fromCode(kind), BlockRecurrence.fromCode(recurrence), Instant.parse(createdAt),
        sourceId?.let { BlockSource.Imported(it, sourceUid.orEmpty()) },
    )

    companion object {
        fun from(b: TimeBlock): BlockJson {
            val src = b.source as? BlockSource.Imported
            return BlockJson(
                b.id, b.title, b.start.toString(), b.end.toString(), b.kind.code, b.recurrence.code, b.createdAt.toString(), src?.calendarId, src?.uid,
            )
        }
    }
}

/** Calendrier importé (3.10.0) : `hideTitles` n'est écrit que s'il est vrai. */
@Serializable
private data class CalendarSourceJson(
    val id: Long, val name: String, val hideTitles: Boolean? = null, val importedAt: String, val createdAt: String,
    // Synchronisation (formatVersion 3, docs/spec/synchronisation.md § Format) : nuls, donc omis, hors synchronisation.
    val uid: String? = null,
    val born: String? = null,
    val base: String? = null,
    val stamps: Map<String, String>? = null,
) {
    fun toModel() = CalendarSource(id, name, hideTitles == true, Instant.parse(importedAt), Instant.parse(createdAt))

    companion object {
        fun from(c: CalendarSource) = CalendarSourceJson(c.id, c.name, if (c.hideTitles) true else null, c.importedAt.toString(), c.createdAt.toString())
    }
}

/** Journée entière d'un calendrier importé (3.10.0) : dates « 2026-10-28 », fin incluse. */
@Serializable
private data class DayMarkJson(
    val id: Long, val title: String = "", val start: String, val end: String, val sourceId: Long, val sourceUid: String = "", val createdAt: String,
    // Synchronisation (formatVersion 3, docs/spec/synchronisation.md § Format) : nuls, donc omis, hors synchronisation.
    val uid: String? = null,
    val born: String? = null,
    val base: String? = null,
    val stamps: Map<String, String>? = null,
) {
    fun toModel() = DayMark(id, title, LocalDate.parse(start), LocalDate.parse(end), BlockSource.Imported(sourceId, sourceUid), Instant.parse(createdAt))

    companion object {
        fun from(m: DayMark): DayMarkJson? {
            val src = m.source as? BlockSource.Imported ?: return null
            return DayMarkJson(m.id, m.title, m.start.toString(), m.end.toString(), src.calendarId, src.uid, m.createdAt.toString())
        }
    }
}

/** Exception d'occurrence : `skipped` n'est écrit que s'il est vrai, titre et heures (« 09:30 ») seulement s'ils remplacent. */
@Serializable
private data class BlockExceptionJson(
    val id: Long,
    val blockId: Long,
    val date: String,
    val skipped: Boolean? = null,
    val title: String? = null,
    val start: String? = null,
    val end: String? = null,
    val createdAt: String,
    // Synchronisation (formatVersion 3, docs/spec/synchronisation.md § Format) : nuls, donc omis, hors synchronisation.
    val uid: String? = null,
    val born: String? = null,
    val base: String? = null,
    val stamps: Map<String, String>? = null,
) {
    fun toModel() = BlockException(
        id, blockId, LocalDate.parse(date), skipped == true, title, start?.let(LocalTime::parse), end?.let(LocalTime::parse),
        Instant.parse(createdAt),
    )

    companion object {
        fun from(x: BlockException) = BlockExceptionJson(
            x.id, x.blockId, x.date.toString(), if (x.skipped) true else null, x.title, x.start?.hm(), x.end?.hm(), x.createdAt.toString(),
        )
    }
}

@Serializable
private data class DependencyJson(
    val id: Long, val taskId: Long, val blockerId: Long, val createdAt: String,
    // Synchronisation (formatVersion 3, docs/spec/synchronisation.md § Format) : nuls, donc omis, hors synchronisation.
    val uid: String? = null,
    val born: String? = null,
    val base: String? = null,
    val stamps: Map<String, String>? = null,
)

@Serializable
private data class SessionJson(
    val id: Long,
    val taskId: Long,
    val startedAt: String,
    val endedAt: String? = null,
    val createdAt: String,
    // Synchronisation (formatVersion 3, docs/spec/synchronisation.md § Format) : nuls, donc omis, hors synchronisation.
    val uid: String? = null,
    val born: String? = null,
    val base: String? = null,
    val stamps: Map<String, String>? = null,
)

@Serializable
private data class NoteJson(
    val id: Long,
    val body: String,
    val status: String = "open",
    val convertedTaskId: Long? = null,
    val createdAt: String,
    val updatedAt: String,
    // Synchronisation (formatVersion 3, docs/spec/synchronisation.md § Format) : nuls, donc omis, hors synchronisation.
    val uid: String? = null,
    val born: String? = null,
    val base: String? = null,
    val stamps: Map<String, String>? = null,
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
    // Jalon E6 : nul donc omis tant qu'aucun rapport n'a été intégré.
    val lastReportAt: String? = null,
    // 3.9.0 (issue #104) : nulle donc omise sans semaine type ; un export d'avant ne change pas.
    val weekHours: List<Double>? = null,
) {
    fun toModel() = TeamMember(
        id, uid, name, role, availabilityPercent, hoursPerDay, isSelf, archived,
        Instant.parse(createdAt), Instant.parse(updatedAt), lastReportAt?.let(Instant::parse), weekHours,
    )

    companion object {
        fun from(m: TeamMember) = MemberJson(
            m.id, m.uid, m.name, m.role, m.availabilityPercent, m.hoursPerDay, m.isSelf, m.archived,
            m.createdAt.toString(), m.updatedAt.toString(), m.lastReportAt?.toString(), m.weekHours,
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

/**
 * Scénario exporté : [modifications] est la liste JSON des modifications telle quelle (lisible dans
 * le fichier), relue par `ScenarioCodec` (un type inconnu y est ignoré et signalé, jamais une erreur).
 */
@Serializable
private data class ScenarioJson(
    val id: Long,
    val name: String,
    val modifications: JsonArray = JsonArray(emptyList()),
    val createdAt: String,
    val updatedAt: String,
) {
    fun toModel(): TeamScenario {
        val decoded = ScenarioCodec.decode(modifications.toString())
        return TeamScenario(id, name, decoded.modifications, Instant.parse(createdAt), Instant.parse(updatedAt), decoded.ignored)
    }

    companion object {
        fun from(s: TeamScenario) = ScenarioJson(
            s.id, s.name, Json.parseToJsonElement(ScenarioCodec.encode(s.modifications)) as JsonArray,
            s.createdAt.toString(), s.updatedAt.toString(),
        )
    }
}
