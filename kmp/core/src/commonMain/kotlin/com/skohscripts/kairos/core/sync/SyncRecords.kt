package com.skohscripts.kairos.core.sync

import com.skohscripts.kairos.core.model.BlockException
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.BlockSource
import com.skohscripts.kairos.core.model.CalendarSource
import com.skohscripts.kairos.core.model.DayMark
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.NoteStatus
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.time.Instant

/**
 * Un enregistrement de la portée sous sa forme **fusionnable** (docs/spec/synchronisation.md § Fusion) : ses champs
 * synchronisés en texte canonique JSON (`null` est une valeur, références en `uid`), et ses deux registres particuliers,
 * [createdAt] (le plus petit gagne) et [updatedAt] (le plus grand gagne, `null` pour les genres qui n'en ont pas).
 */
data class Lifted(val fields: Map<String, String>, val createdAt: Instant, val updatedAt: Instant? = null)

/**
 * Passage des modèles à leurs champs synchronisés et retour (docs/spec/synchronisation.md § Fusion, étapes 1 et 6). Les noms
 * de champs sont ceux du JSON d'export ; les valeurs, le texte JSON compact de la valeur (`"\"titre\""`, `"3"`, `"null"`).
 * Les champs **locaux** ([SyncScope.TASK_LOCAL_FIELDS]) n'y sont jamais : à la reconstruction, un enregistrement déjà présent
 * garde les siens ([template]), un enregistrement qui arrive prend les valeurs par défaut.
 */
object SyncRecords {
    /** Résout l'identifiant local d'un enregistrement référencé en `uid` ; `null` si inconnu ou hors portée. */
    fun interface UidOf {
        fun uid(kind: RecordKind, id: Long): String?
    }

    /** Résout un `uid` référencé en identifiant local ; `null` si inconnu ici. */
    fun interface IdOf {
        fun id(kind: RecordKind, uid: String): Long?
    }

    /** Champ d'un genre dont la valeur est une référence vers un autre genre. */
    val REFERENCES: Map<RecordKind, Map<String, RecordKind>> = mapOf(
        RecordKind.TASK to mapOf("parentId" to RecordKind.TASK, "seriesId" to RecordKind.TASK),
        RecordKind.BLOCK to mapOf("sourceId" to RecordKind.CALENDAR),
        RecordKind.EXCEPTION to mapOf("blockId" to RecordKind.BLOCK),
        RecordKind.DEPENDENCY to mapOf("taskId" to RecordKind.TASK, "blockerId" to RecordKind.TASK),
        RecordKind.SESSION to mapOf("taskId" to RecordKind.TASK),
        RecordKind.NOTE to mapOf("convertedTaskId" to RecordKind.TASK),
        RecordKind.DAYMARK to mapOf("sourceId" to RecordKind.CALENDAR),
    )

    // --- Levée ------------------------------------------------------------------------------------------------------

    fun lift(task: Task, uids: UidOf) = Lifted(
        linkedMapOf(
            "title" to str(task.title),
            "description" to str(task.description),
            "priority" to num(task.priority),
            "deadline" to str(task.deadline?.toString()),
            "projectTag" to str(task.projectTag),
            "status" to str(task.status.code),
            "estimatedMinutes" to num(task.estimatedMinutes),
            "pinnedStart" to str(task.pinnedStart?.toString()),
            "parentId" to ref(uids, RecordKind.TASK, task.parentId),
            "recurrence" to str(task.recurrence.code),
            "scheduledDate" to str(task.scheduledDate?.toString()),
            "recurrenceDayOfMonth" to num(task.recurrenceDayOfMonth),
            "recurrenceDayOfWeek" to num(task.recurrenceDayOfWeek),
            "recurrencePeriod" to str(task.recurrencePeriod),
            "taskType" to str(task.taskType),
            "fibonacciPoints" to num(task.fibonacciPoints),
            "manualTimeSpentMinutes" to num(task.manualTimeSpentMinutes),
            "seriesId" to ref(uids, RecordKind.TASK, task.seriesId),
        ),
        task.createdAt, task.updatedAt,
    )

    fun lift(block: TimeBlock, uids: UidOf): Lifted {
        val src = block.source as? BlockSource.Imported
        return Lifted(
            linkedMapOf(
                "title" to str(block.title),
                "start" to str(block.start.toString()),
                "end" to str(block.end.toString()),
                "kind" to str(block.kind.code),
                "recurrence" to str(block.recurrence.code),
                "sourceId" to ref(uids, RecordKind.CALENDAR, src?.calendarId),
                "sourceUid" to str(src?.uid),
            ),
            block.createdAt,
        )
    }

    fun lift(x: BlockException, uids: UidOf) = Lifted(
        linkedMapOf(
            "blockId" to ref(uids, RecordKind.BLOCK, x.blockId),
            "date" to str(x.date.toString()),
            "skipped" to x.skipped.toString(),
            "title" to str(x.title),
            "start" to str(x.start?.toString()),
            "end" to str(x.end?.toString()),
        ),
        x.createdAt,
    )

    fun lift(d: TaskDependency, uids: UidOf) = Lifted(
        linkedMapOf("taskId" to ref(uids, RecordKind.TASK, d.taskId), "blockerId" to ref(uids, RecordKind.TASK, d.blockerId)),
        d.createdAt,
    )

    fun lift(s: WorkSession, uids: UidOf) = Lifted(
        linkedMapOf(
            "taskId" to ref(uids, RecordKind.TASK, s.taskId),
            "startedAt" to str(s.startedAt.toString()),
            "endedAt" to str(s.endedAt?.toString()),
        ),
        s.createdAt,
    )

    fun lift(n: Note, uids: UidOf) = Lifted(
        linkedMapOf(
            "body" to str(n.body),
            "status" to str(n.status.code),
            "convertedTaskId" to ref(uids, RecordKind.TASK, n.convertedTaskId),
        ),
        n.createdAt, n.updatedAt,
    )

    fun lift(c: CalendarSource) = Lifted(
        linkedMapOf("name" to str(c.name), "hideTitles" to c.hideTitles.toString(), "importedAt" to str(c.importedAt.toString())),
        c.createdAt,
    )

    fun lift(m: DayMark, uids: UidOf): Lifted {
        val src = m.source as? BlockSource.Imported
        return Lifted(
            linkedMapOf(
                "title" to str(m.title),
                "start" to str(m.start.toString()),
                "end" to str(m.end.toString()),
                "sourceId" to ref(uids, RecordKind.CALENDAR, src?.calendarId),
                "sourceUid" to str(src?.uid),
            ),
            m.createdAt,
        )
    }

    /** Les réglages **partagés** ([SyncScope.SHARED_SETTINGS]), un registre par champ de premier niveau. */
    fun liftSettings(settings: Settings): Map<String, String> {
        val obj = canonicalJson.encodeToJsonElement(Settings.serializer(), settings) as JsonObject
        return SyncScope.SHARED_SETTINGS.associateWith { (obj[it] ?: JsonNull).toString() }
    }

    // --- Reconstruction ---------------------------------------------------------------------------------------------

    /** Une tâche d'après ses champs ; ses champs locaux viennent de [template] (la tâche déjà présente) ou des défauts. */
    fun buildTask(id: Long, l: Lifted, ids: IdOf, template: Task?): Task {
        val f = Fields(l.fields)
        val base = template ?: Task(id = id, title = "", createdAt = l.createdAt, updatedAt = l.updatedAt ?: l.createdAt)
        return base.copy(
            id = id,
            title = f.string("title").orEmpty(),
            description = f.string("description").orEmpty(),
            priority = f.int("priority"),
            deadline = f.string("deadline")?.let(LocalDate::parse),
            projectTag = f.string("projectTag").orEmpty(),
            status = TaskStatus.fromCode(f.string("status") ?: TaskStatus.TODO.code),
            estimatedMinutes = f.int("estimatedMinutes"),
            pinnedStart = f.string("pinnedStart")?.let(LocalDateTime::parse),
            parentId = f.string("parentId")?.let { ids.id(RecordKind.TASK, it) },
            recurrence = TaskRecurrence.fromCode(f.string("recurrence").orEmpty()),
            scheduledDate = f.string("scheduledDate")?.let(LocalDate::parse),
            recurrenceDayOfMonth = f.int("recurrenceDayOfMonth"),
            recurrenceDayOfWeek = f.int("recurrenceDayOfWeek"),
            recurrencePeriod = f.string("recurrencePeriod").orEmpty(),
            taskType = f.string("taskType").orEmpty(),
            fibonacciPoints = f.int("fibonacciPoints"),
            manualTimeSpentMinutes = f.int("manualTimeSpentMinutes"),
            seriesId = f.string("seriesId")?.let { ids.id(RecordKind.TASK, it) },
            createdAt = l.createdAt,
            updatedAt = l.updatedAt ?: l.createdAt,
        )
    }

    /** Un créneau ; `null` si son calendrier d'origine est inconnu ici (il disparaît avec lui). */
    fun buildBlock(id: Long, l: Lifted, ids: IdOf): TimeBlock? {
        val f = Fields(l.fields)
        val sourceUid = f.string("sourceId")
        val source = sourceUid?.let { ids.id(RecordKind.CALENDAR, it) ?: return null }?.let { BlockSource.Imported(it, f.string("sourceUid").orEmpty()) }
        return TimeBlock(
            id = id,
            title = f.string("title").orEmpty(),
            start = LocalDateTime.parse(f.string("start")!!),
            end = LocalDateTime.parse(f.string("end")!!),
            kind = BlockKind.fromCode(f.string("kind") ?: BlockKind.BUSY.code),
            recurrence = BlockRecurrence.fromCode(f.string("recurrence").orEmpty()),
            createdAt = l.createdAt,
            source = source,
        )
    }

    /** Une exception d'occurrence ; `null` si son créneau est inconnu ici. */
    fun buildException(id: Long, l: Lifted, ids: IdOf): BlockException? {
        val f = Fields(l.fields)
        val blockId = f.string("blockId")?.let { ids.id(RecordKind.BLOCK, it) } ?: return null
        return BlockException(
            id = id,
            blockId = blockId,
            date = LocalDate.parse(f.string("date")!!),
            skipped = f.bool("skipped"),
            title = f.string("title"),
            start = f.string("start")?.let(LocalTime::parse),
            end = f.string("end")?.let(LocalTime::parse),
            createdAt = l.createdAt,
        )
    }

    /** Une dépendance ; `null` s'il lui manque l'un de ses bouts. */
    fun buildDependency(id: Long, l: Lifted, ids: IdOf): TaskDependency? {
        val f = Fields(l.fields)
        val task = f.string("taskId")?.let { ids.id(RecordKind.TASK, it) } ?: return null
        val blocker = f.string("blockerId")?.let { ids.id(RecordKind.TASK, it) } ?: return null
        return TaskDependency(id, task, blocker, l.createdAt)
    }

    /** Une session de travail ; `null` si sa tâche est inconnue ici. */
    fun buildSession(id: Long, l: Lifted, ids: IdOf): WorkSession? {
        val f = Fields(l.fields)
        val task = f.string("taskId")?.let { ids.id(RecordKind.TASK, it) } ?: return null
        return WorkSession(id, task, Instant.parse(f.string("startedAt")!!), f.string("endedAt")?.let(Instant::parse), l.createdAt)
    }

    fun buildNote(id: Long, l: Lifted, ids: IdOf): Note {
        val f = Fields(l.fields)
        return Note(
            id = id,
            body = f.string("body").orEmpty(),
            status = NoteStatus.fromCode(f.string("status") ?: NoteStatus.OPEN.code),
            convertedTaskId = f.string("convertedTaskId")?.let { ids.id(RecordKind.TASK, it) },
            createdAt = l.createdAt,
            updatedAt = l.updatedAt ?: l.createdAt,
        )
    }

    fun buildCalendar(id: Long, l: Lifted): CalendarSource {
        val f = Fields(l.fields)
        return CalendarSource(
            id = id,
            name = f.string("name").orEmpty(),
            hideTitles = f.bool("hideTitles"),
            importedAt = f.string("importedAt")?.let(Instant::parse) ?: l.createdAt,
            createdAt = l.createdAt,
        )
    }

    /** Une journée entière ; `null` si son calendrier est inconnu ici. */
    fun buildDayMark(id: Long, l: Lifted, ids: IdOf): DayMark? {
        val f = Fields(l.fields)
        val calendar = f.string("sourceId")?.let { ids.id(RecordKind.CALENDAR, it) } ?: return null
        return DayMark(
            id = id,
            title = f.string("title").orEmpty(),
            start = LocalDate.parse(f.string("start")!!),
            end = LocalDate.parse(f.string("end")!!),
            source = BlockSource.Imported(calendar, f.string("sourceUid").orEmpty()),
            createdAt = l.createdAt,
        )
    }

    /** Les réglages : les champs partagés de [shared], les champs locaux de [local] (ceux de cet appareil). */
    fun buildSettings(shared: Map<String, String>, local: Settings): Settings {
        val obj = (canonicalJson.encodeToJsonElement(Settings.serializer(), local) as JsonObject).toMutableMap()
        for (name in SyncScope.SHARED_SETTINGS) shared[name]?.let { obj[name] = canonicalJson.parseToJsonElement(it) }
        return canonicalJson.decodeFromJsonElement(Settings.serializer(), JsonObject(obj))
    }

    // --- Texte canonique --------------------------------------------------------------------------------------------

    /** JSON des valeurs : tous les champs écrits (défauts et `null` compris), compact, ordre de déclaration. */
    val canonicalJson = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    /** Empreinte 64 bits (FNV-1a sur l'UTF-8) d'une valeur canonique : détecte un changement, ne prouve rien. */
    fun hash(text: String): Long {
        var h = FNV_OFFSET
        for (b in text.encodeToByteArray()) h = (h xor (b.toLong() and 0xFF)) * FNV_PRIME
        return h
    }

    private const val FNV_OFFSET: Long = -3750763034362895579L // 0xCBF29CE484222325
    private const val FNV_PRIME: Long = 1099511628211L // 0x100000001B3

    private fun str(value: String?): String = if (value == null) NULL else JsonPrimitive(value).toString()
    private fun num(value: Int?): String = value?.toString() ?: NULL
    private fun ref(uids: UidOf, kind: RecordKind, id: Long?): String = str(id?.let { uids.uid(kind, it) })
    private const val NULL = "null"

    /** Lecture des valeurs canoniques d'un enregistrement. */
    private class Fields(private val map: Map<String, String>) {
        private fun element(name: String): JsonElement? = map[name]?.let { canonicalJson.parseToJsonElement(it) }
        fun string(name: String): String? = (element(name) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
        fun int(name: String): Int? = (element(name) as? JsonPrimitive)?.intOrNull
        fun bool(name: String): Boolean = (element(name) as? JsonPrimitive)?.booleanOrNull == true
    }
}
