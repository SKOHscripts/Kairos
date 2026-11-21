package com.skohscripts.kairos.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * Créneau de la journée saisi à la main : occupé (réunion) ou fenêtre de deep
 * work réservée. Un créneau récurrent est un **modèle** unique, projeté à la
 * volée (docs/spec/recurrence.md) ; aucune occurrence n'est stockée.
 * [source] : `null` pour un créneau saisi à la main ; sinon le calendrier dont il vient (docs/spec/connexions-externes.md),
 * toujours ponctuel et occupé, en lecture seule sauf la suppression.
 */
data class TimeBlock(
    val id: Long,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val kind: BlockKind = BlockKind.BUSY,
    val recurrence: BlockRecurrence = BlockRecurrence.NONE,
    val createdAt: Instant,
    val source: BlockSource? = null,
) {
    /** Vient d'un calendrier externe : ne se modifie pas (le prochain import l'écraserait), se supprime. */
    val imported: Boolean get() = source != null
}

/**
 * D'où vient un créneau ou une journée d'un calendrier externe (docs/spec/connexions-externes.md § Modèle). [uid] : `UID` de
 * l'événement iCalendar, le même pour toutes les occurrences d'une série ; avec le début, il identifie une occurrence.
 */
sealed interface BlockSource {
    val uid: String

    /** Calendrier importé d'un fichier `.ics` ([CalendarSource]), stocké. */
    data class Imported(val calendarId: Long, override val uid: String) : BlockSource

    /** Calendrier du téléphone (Android), lu en direct, jamais stocké. */
    data class Device(val calendarName: String, override val uid: String) : BlockSource
}

/**
 * Un calendrier importé : son nom (il l'identifie : réimporter sous ce nom le remplace), le choix « Masquer les titres »
 * du dernier import, et la date de ce dernier import. [countsAsHolidays] : ses journées entières comptent comme fériés
 * (docs/spec/jours-feries.md § Voie B).
 */
data class CalendarSource(
    val id: Long,
    val name: String,
    val hideTitles: Boolean = false,
    val importedAt: Instant,
    val createdAt: Instant,
    val countsAsHolidays: Boolean = false,
)

/**
 * Événement « toute la journée » d'un calendrier externe (choix du propriétaire, 2026-10-24) : un badge du [start] au [end]
 * inclus, qui ne bloque aucun temps et ne change pas le planning.
 */
data class DayMark(
    val id: Long,
    val title: String,
    val start: LocalDate,
    val end: LocalDate,
    val source: BlockSource,
    val createdAt: Instant,
) {
    fun covers(day: LocalDate): Boolean = day in start..end
}

enum class BlockKind(val code: String) {
    BUSY("busy"),
    DEEPWORK("deepwork"),
    ;

    companion object {
        fun fromCode(code: String): BlockKind = entries.firstOrNull { it.code == code } ?: BUSY
    }
}

/**
 * Règle d'un créneau récurrent. Les deux mensuelles (3.8.0, issue #108) tirent tout de la date d'origine :
 * [MONTHLY_DAY] le même jour du mois, [MONTHLY_WEEKDAY] le même rang du même jour de la semaine (« 3e mardi »).
 */
enum class BlockRecurrence(val code: String) {
    NONE(""),
    DAILY("daily"),
    WEEKDAYS("weekdays"),
    WEEKLY("weekly"),
    MONTHLY_DAY("monthly_day"),
    MONTHLY_WEEKDAY("monthly_weekday"),
    ;

    companion object {
        fun fromCode(code: String): BlockRecurrence = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/**
 * Exception d'**une** occurrence d'un créneau récurrent (3.8.0, issue #110) : le jour [date] du modèle [blockId] est
 * sauté ([skipped]) ou remplacé (titre et heures de ce jour-là ; `null` = ceux du modèle). Les occurrences restent
 * calculées : seule l'exception est stockée, au plus une par (modèle, jour).
 */
data class BlockException(
    val id: Long,
    val blockId: Long,
    val date: LocalDate,
    val skipped: Boolean = false,
    val title: String? = null,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val createdAt: Instant,
)

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
