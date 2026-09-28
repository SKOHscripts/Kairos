package com.skohscripts.kairos.core.model

import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

/**
 * Créneau de la journée saisi à la main : occupé (réunion) ou fenêtre de deep
 * work réservée. Un créneau récurrent est un **modèle** unique, projeté à la
 * volée (docs/spec/recurrence.md) ; aucune occurrence n'est stockée.
 */
data class TimeBlock(
    val id: Long,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val kind: BlockKind = BlockKind.BUSY,
    val recurrence: BlockRecurrence = BlockRecurrence.NONE,
    val createdAt: Instant,
)

enum class BlockKind(val code: String) {
    BUSY("busy"),
    DEEPWORK("deepwork"),
    ;

    companion object {
        fun fromCode(code: String): BlockKind = entries.firstOrNull { it.code == code } ?: BUSY
    }
}

enum class BlockRecurrence(val code: String) {
    NONE(""),
    DAILY("daily"),
    WEEKDAYS("weekdays"),
    WEEKLY("weekly"),
    ;

    companion object {
        fun fromCode(code: String): BlockRecurrence = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/** Arête « [taskId] est bloquée par [blockerId] ». */
data class TaskDependency(
    val id: Long,
    val taskId: Long,
    val blockerId: Long,
    val createdAt: Instant,
)

/** Session chronométrée ; `endedAt == null` = en cours (au plus une à la fois). */
data class WorkSession(
    val id: Long,
    val taskId: Long,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val createdAt: Instant,
)

/** Note libre (capture GTD en amont de la boîte de réception). */
data class Note(
    val id: Long,
    val body: String,
    val status: NoteStatus = NoteStatus.OPEN,
    val convertedTaskId: Long? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class NoteStatus(val code: String) {
    OPEN("open"),
    ARCHIVED("archived"),
    ;

    companion object {
        fun fromCode(code: String): NoteStatus = entries.firstOrNull { it.code == code } ?: OPEN
    }
}
