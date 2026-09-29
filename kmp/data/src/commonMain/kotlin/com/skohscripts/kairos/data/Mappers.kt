package com.skohscripts.kairos.data

import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.NoteStatus
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant
import com.skohscripts.kairos.data.db.Note as NoteRow
import com.skohscripts.kairos.data.db.Task as TaskRow
import com.skohscripts.kairos.data.db.Task_dependency as DependencyRow
import com.skohscripts.kairos.data.db.Time_block as BlockRow
import com.skohscripts.kairos.data.db.Work_session as SessionRow

// Conversions ligne SQLite <-> modèle (docs/spec-v3/modele-donnees.md § Stockage) :
// dates « 2026-09-28 », heures locales « 2026-09-28T09:30 », instants ISO UTC.

internal fun LocalDate?.store(): String? = this?.toString()
internal fun LocalDateTime?.store(): String? = this?.toString()
internal fun Instant.store(): String = toString()
internal fun String?.localDate(): LocalDate? = this?.let(LocalDate::parse)
internal fun String?.localDateTime(): LocalDateTime? = this?.let(LocalDateTime::parse)
internal fun String.instant(): Instant = Instant.parse(this)

internal fun TaskRow.toModel() = Task(
    id = id,
    title = title,
    description = description,
    priority = priority?.toInt(),
    deadline = deadline.localDate(),
    projectTag = project_tag,
    status = TaskStatus.fromCode(status),
    estimatedMinutes = estimated_minutes?.toInt(),
    pinnedStart = pinned_start.localDateTime(),
    parentId = parent_id,
    recurrence = TaskRecurrence.fromCode(recurrence),
    scheduledDate = scheduled_date.localDate(),
    recurrenceDayOfMonth = recurrence_day_of_month?.toInt(),
    recurrenceDayOfWeek = recurrence_day_of_week?.toInt(),
    recurrencePeriod = recurrence_period,
    taskType = task_type,
    fibonacciPoints = fibonacci_points?.toInt(),
    manualTimeSpentMinutes = manual_time_spent_minutes?.toInt(),
    createdAt = created_at.instant(),
    updatedAt = updated_at.instant(),
)

internal fun BlockRow.toModel() = TimeBlock(
    id = id,
    title = title,
    start = LocalDateTime.parse(start),
    end = LocalDateTime.parse(end),
    kind = BlockKind.fromCode(kind),
    recurrence = BlockRecurrence.fromCode(recurrence),
    createdAt = created_at.instant(),
)

internal fun DependencyRow.toModel() = TaskDependency(id, task_id, blocker_id, created_at.instant())

internal fun SessionRow.toModel() = WorkSession(id, task_id, started_at.instant(), ended_at?.instant(), created_at.instant())

internal fun NoteRow.toModel() = Note(
    id = id,
    body = body,
    status = NoteStatus.fromCode(status),
    convertedTaskId = converted_task_id,
    createdAt = created_at.instant(),
    updatedAt = updated_at.instant(),
)
