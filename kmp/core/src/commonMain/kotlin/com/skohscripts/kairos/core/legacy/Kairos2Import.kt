package com.skohscripts.kairos.core.legacy

import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.FIBONACCI_SCALE
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.NoteStatus
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.settings.SettingsForm
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

/**
 * Contenu brut d'une base Kairos 2 (`tasks.db`), lu par la plateforme :
 * table → lignes, colonne → valeur (`String`, `Long`, `Double` ou `null`,
 * les types de SQLite). Une table absente (base ancienne) est une liste vide.
 * [settingsJson] : le `settings.json` voisin, s'il existe.
 */
class LegacyDatabase(val tables: Map<String, List<Map<String, Any?>>>, val settingsJson: String? = null) {
    fun rows(table: String): List<Map<String, Any?>> = tables[table].orEmpty()
}

/** Ce que la migration a repris, et ce qu'elle a laissé de côté. */
data class LegacyReport(
    val tasks: Int,
    val notes: Int,
    val sessions: Int,
    val timeBlocks: Int,
    val dependencies: Int,
    /** Créneaux TimeTree (`source != "manual"`) : de simples copies de cache, ignorés. */
    val skippedExternalBlocks: Int,
    /** Réglages de Kairos 2 sans équivalent (intégrations retirées) ou invalides, en snake_case. */
    val ignoredSettings: List<String>,
)

/** Le fichier n'est pas une base Kairos 2 (pas de table `task`). */
class NotALegacyDatabase : Exception("not a Kairos 2 database")

/**
 * Migration d'une base Kairos 2 (docs/spec/migration-2x.md), pure. Toutes
 * les générations de schéma : une colonne ajoutée après coup (phases 2 à 6)
 * et absente prend sa valeur par défaut. Règles du plan § 5.4 : tâches
 * GitLab → tâches natives (projet gardé), créneaux TimeTree ignorés,
 * `task_sync_meta` ignorée, réglages repris champ par champ.
 *
 * Horodatages : Kairos 2 écrit ses instants (`created_at`, `updated_at`,
 * sessions) en UTC **sans** fuseau ; les dates et heures métier (échéance,
 * épinglage, créneaux) sont locales et le restent.
 */
object Kairos2Import {
    /** Anciennes clés de type (avant l'issue #7 de Kairos 2) → libellé, comme `_ensure_tasks_columns`. */
    private val LEGACY_TASK_TYPES = mapOf(
        "dev" to "Développement",
        "revue_code" to "Revue de code",
        "reunion" to "Réunion",
        "documentation" to "Documentation",
        "administratif" to "Administratif",
        "veille" to "Veille/formation",
        "pilotage" to "Pilotage/dette technique",
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** [now] sert d'horodatage à une ligne qui n'en a pas ; [language] donne les réglages par défaut. */
    fun convert(db: LegacyDatabase, language: String, now: Instant): Pair<KairosSnapshot, LegacyReport> {
        if ("task" !in db.tables) throw NotALegacyDatabase()
        val tasks = db.rows("task").mapNotNull { task(it, now) }
        val taskIds = tasks.map { it.id }.toSet()
        val blockRows = db.rows("time_block")
        val manual = blockRows.filter { (text(it["source"]) ?: "manual") == "manual" }
        val blocks = manual.mapNotNull { block(it, now) }
        val dependencies = db.rows("task_dependency").mapNotNull { row ->
            val id = long(row["id"]) ?: return@mapNotNull null
            val taskId = long(row["task_id"]) ?: return@mapNotNull null
            val blockerId = long(row["blocker_id"]) ?: return@mapNotNull null
            if (taskId !in taskIds || blockerId !in taskIds || taskId == blockerId) return@mapNotNull null
            TaskDependency(id, taskId, blockerId, instant(row["created_at"]) ?: now)
        }.distinctBy { it.taskId to it.blockerId }
        val sessions = db.rows("work_session").mapNotNull { row ->
            val id = long(row["id"]) ?: return@mapNotNull null
            val taskId = long(row["task_id"]) ?: return@mapNotNull null
            val started = instant(row["started_at"]) ?: return@mapNotNull null
            WorkSession(id, taskId, started, instant(row["ended_at"]), instant(row["created_at"]) ?: started)
        }
        val notes = db.rows("note").mapNotNull { row ->
            val id = long(row["id"]) ?: return@mapNotNull null
            val created = instant(row["created_at"]) ?: now
            Note(
                id = id,
                body = text(row["body"]).orEmpty(),
                status = NoteStatus.fromCode(text(row["status"]).orEmpty()),
                convertedTaskId = long(row["converted_task_id"]),
                createdAt = created,
                updatedAt = instant(row["updated_at"]) ?: created,
            )
        }
        val (settings, ignored) = settings(db.settingsJson, language)
        val snapshot = KairosSnapshot(tasks, blocks, dependencies, sessions, notes, settings)
        val report = LegacyReport(
            tasks = tasks.size,
            notes = notes.size,
            sessions = sessions.size,
            timeBlocks = blocks.size,
            dependencies = dependencies.size,
            skippedExternalBlocks = blockRows.size - manual.size,
            ignoredSettings = ignored,
        )
        return snapshot to report
    }

    private fun task(row: Map<String, Any?>, now: Instant): Task? {
        val id = long(row["id"]) ?: return null
        val created = instant(row["created_at"]) ?: now
        val type = text(row["task_type"]).orEmpty().trim()
        return Task(
            id = id,
            title = text(row["title"]).orEmpty(),
            description = text(row["description"]).orEmpty(),
            priority = long(row["priority"])?.toInt()?.takeIf { it in PRIORITY_VALUES },
            deadline = date(row["deadline"]),
            projectTag = text(row["project_tag"]).orEmpty(),
            status = TaskStatus.fromCode(text(row["status"]).orEmpty()),
            estimatedMinutes = long(row["estimated_minutes"])?.toInt()?.takeIf { it > 0 },
            pinnedStart = dateTime(row["pinned_start"]),
            parentId = long(row["parent_id"]),
            recurrence = TaskRecurrence.fromCode(text(row["recurrence"]).orEmpty()),
            scheduledDate = date(row["scheduled_date"]),
            recurrenceDayOfMonth = long(row["recurrence_day_of_month"])?.toInt()?.takeIf { it in 1..31 },
            recurrenceDayOfWeek = long(row["recurrence_day_of_week"])?.toInt()?.takeIf { it in 0..6 },
            recurrencePeriod = text(row["recurrence_period"]).orEmpty(),
            taskType = LEGACY_TASK_TYPES[type] ?: type,
            fibonacciPoints = long(row["fibonacci_points"])?.toInt()?.takeIf { it in FIBONACCI_SCALE },
            manualTimeSpentMinutes = long(row["manual_time_spent_minutes"])?.toInt()?.takeIf { it >= 0 },
            createdAt = created,
            updatedAt = instant(row["updated_at"]) ?: created,
        )
    }

    private fun block(row: Map<String, Any?>, now: Instant): TimeBlock? {
        val id = long(row["id"]) ?: return null
        val start = dateTime(row["start"]) ?: return null
        val end = dateTime(row["end"]) ?: return null
        if (end <= start) return null
        return TimeBlock(
            id = id,
            title = text(row["title"]).orEmpty(),
            start = start,
            end = end,
            kind = BlockKind.fromCode(text(row["kind"]).orEmpty()),
            recurrence = BlockRecurrence.fromCode(text(row["recurrence"]).orEmpty()),
            createdAt = instant(row["created_at"]) ?: now,
        )
    }

    /**
     * Réglages de `settings.json` (`{"settings": {…}}`, clés snake_case) repris
     * quand le champ existe en v3 ; un champ invalide ou une règle
     * inter-champs violée retombe sur la valeur par défaut (Kairos 2 aussi
     * repartait des défauts sur un fichier invalide).
     */
    internal fun settings(text: String?, language: String): Pair<Settings, List<String>> {
        val defaults = Settings.defaults(language)
        if (text.isNullOrBlank()) return defaults to emptyList()
        val raw = runCatching { json.parseToJsonElement(text).jsonObject["settings"] as? JsonObject }.getOrNull()
            ?: return defaults to emptyList()
        val known = SettingsForm.FIELDS.map { it.key }.toSet()
        val values = LinkedHashMap<String, String>()
        val ignored = mutableListOf<String>()
        for ((snake, element) in raw) {
            val key = camel(snake)
            val primitive = element as? JsonPrimitive
            if (key !in known || primitive == null) {
                ignored += snake
                continue
            }
            values[key] = primitive.booleanOrNull?.toString() ?: primitive.content
        }
        var result = SettingsForm.validate(values, defaults)
        if (result.fieldErrors.isNotEmpty()) {
            result.fieldErrors.keys.forEach { values.remove(it); ignored += snake(it) }
            result = SettingsForm.validate(values, defaults)
        }
        if (result.general != null) {
            val group = listOf("workdayStartHour", "workdayEndHour", "cognitiveDipStartHour", "cognitiveDipTroughHour", "cognitiveDipEndHour")
            group.filter { values.remove(it) != null }.forEach { ignored += snake(it) }
            result = SettingsForm.validate(values, defaults)
        }
        return (result.settings ?: defaults) to ignored.sorted()
    }

    private fun camel(snake: String): String =
        snake.split('_').mapIndexed { i, part -> if (i == 0) part else part.replaceFirstChar { it.uppercaseChar() } }.joinToString("")

    private fun snake(camel: String): String = camel.replace(Regex("([A-Z])")) { "_" + it.value.lowercase() }

    // --- Valeurs SQLite -------------------------------------------------------

    private fun text(value: Any?): String? = value?.toString()

    private fun long(value: Any?): Long? = when (value) {
        null -> null
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong().takeIf { it.toDouble() == value }
        else -> value.toString().trim().toLongOrNull()
    }

    private fun date(value: Any?): LocalDate? = text(value)?.trim()?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** « 2026-09-28 09:30:00.123456 » (SQLAlchemy), « 2026-09-28T09:30 », fuseau éventuel ignoré. */
    internal fun dateTime(value: Any?): LocalDateTime? {
        var s = text(value)?.trim()?.takeIf { it.length >= 16 } ?: return null
        s = s.replace(' ', 'T').removeSuffix("Z")
        Regex("[+-]\\d{2}:?\\d{2}$").find(s.substring(10))?.let { s = s.substring(0, 10 + it.range.first) }
        return runCatching { LocalDateTime.parse(s) }.getOrNull()
    }

    private fun instant(value: Any?): Instant? = dateTime(value)?.toInstant(TimeZone.UTC)
}
