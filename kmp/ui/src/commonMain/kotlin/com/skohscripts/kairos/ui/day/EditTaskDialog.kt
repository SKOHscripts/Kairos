package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import com.skohscripts.kairos.ui.app.expandedState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.data.Reassignment
import com.skohscripts.kairos.data.TaskEdit
import com.skohscripts.kairos.core.team.exchange.TeamOrigin
import com.skohscripts.kairos.ui.generated.resources.received_edit_help
import com.skohscripts.kairos.ui.generated.resources.received_keep
import com.skohscripts.kairos.ui.team.KeepInProgressDialog
import com.skohscripts.kairos.ui.team.ProgressField
import com.skohscripts.kairos.ui.team.managerLabel
import com.skohscripts.kairos.ui.team.TaskHistory
import com.skohscripts.kairos.ui.team.TeamTaskFields
import com.skohscripts.kairos.ui.team.TeamTaskSheet
import com.skohscripts.kairos.ui.team.TeamTaskTabs
import kotlin.math.roundToInt
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.action_delete
import com.skohscripts.kairos.ui.generated.resources.action_save
import com.skohscripts.kairos.ui.generated.resources.delete_confirm_body
import com.skohscripts.kairos.ui.generated.resources.delete_confirm_title
import com.skohscripts.kairos.ui.generated.resources.edit_title
import com.skohscripts.kairos.ui.generated.resources.field_deadline
import com.skohscripts.kairos.ui.generated.resources.field_deadline_invalid
import com.skohscripts.kairos.ui.generated.resources.field_description
import com.skohscripts.kairos.ui.generated.resources.field_minutes
import com.skohscripts.kairos.ui.generated.resources.field_project
import com.skohscripts.kairos.ui.generated.resources.field_title
import com.skohscripts.kairos.ui.generated.resources.field_type
import com.skohscripts.kairos.ui.generated.resources.field_category
import com.skohscripts.kairos.ui.generated.resources.field_type_none
import com.skohscripts.kairos.ui.generated.resources.edit_advanced
import com.skohscripts.kairos.ui.generated.resources.field_blockers
import com.skohscripts.kairos.ui.generated.resources.field_blockers_hint
import com.skohscripts.kairos.ui.generated.resources.field_blockers_none
import com.skohscripts.kairos.ui.generated.resources.field_day_invalid
import com.skohscripts.kairos.ui.generated.resources.field_day_of_month
import com.skohscripts.kairos.ui.generated.resources.field_day_of_month_hint
import com.skohscripts.kairos.ui.generated.resources.field_manual_time
import com.skohscripts.kairos.ui.generated.resources.field_pin_hint
import com.skohscripts.kairos.ui.generated.resources.field_pin_time
import com.skohscripts.kairos.ui.generated.resources.field_recurrence
import com.skohscripts.kairos.ui.generated.resources.field_scheduled
import com.skohscripts.kairos.ui.generated.resources.field_subtasks
import com.skohscripts.kairos.ui.generated.resources.task_rec_daily
import com.skohscripts.kairos.ui.generated.resources.task_rec_monthly
import com.skohscripts.kairos.ui.generated.resources.task_rec_monthly_on_day
import com.skohscripts.kairos.ui.generated.resources.task_rec_none
import com.skohscripts.kairos.ui.generated.resources.task_rec_weekdays
import com.skohscripts.kairos.ui.generated.resources.task_rec_weekly
import com.skohscripts.kairos.ui.generated.resources.time_invalid
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Dialogue d'édition d'une tâche (docs/spec/vue-jour.md § Édition) :
 * essentiels toujours visibles (titre, description juste sous le titre,
 * priorité, points, échéance, durée), puis « Options avancées » repliées
 * (programmée pour, projet, temps passé manuel, récurrence et jour du mois,
 * type, heure fixe, nouvelles sous-tâches, bloqueurs). Guide des points
 * sous les pastilles ; palier ou type calibré → durée suggérée. Un seul
 * « Enregistrer » pose tout. Dialogue MD3 : coins de 28 dp, seul élément
 * flottant à porter une ombre.
 */
/**
 * Ce que la fiche d'une tâche **reçue** ajoute (docs/spec/equipe-echanges.md) : son [origin], [removed] si le dernier paquet
 * l'a retirée, et l'enregistrement qui écrit l'édition puis l'avancement déclaré (`null` = inchangé, `setReceivedProgress`) et, pour une tâche
 * retirée, [onKeep] : « Garder comme tâche personnelle » (`detachOrigin`).
 */
internal class ReceivedTaskSheet(val origin: TeamOrigin, val removed: Boolean, val onKeep: () -> Unit, val onSave: (TaskEdit, Int?) -> Unit)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EditTaskDialog(
    task: Task,
    day: LocalDate,
    taskTypes: List<String>,
    candidates: List<Task>,
    blockerIds: Set<Long>,
    estimates: Estimates,
    onDismiss: () -> Unit,
    onSave: (TaskEdit) -> Unit,
    onDelete: () -> Unit,
    /** Tâche d'équipe : champs et historique de la fiche (docs/spec/equipe-backlog-suivi.md) ; `null` pour une tâche personnelle. */
    team: TeamTaskSheet? = null,
    /** Tâche reçue d'un manager (docs/spec/equipe-echanges.md) : sa marque, une phrase d'aide et le curseur d'avancement ; `null` sinon. */
    received: ReceivedTaskSheet? = null,
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description) }
    var priority by remember { mutableStateOf(task.priority) }
    var points by remember { mutableStateOf(task.fibonacciPoints) }
    var deadline by remember { mutableStateOf(task.deadline?.toString().orEmpty()) }
    var minutes by remember { mutableStateOf(task.estimatedMinutes?.toString().orEmpty()) }
    var project by remember { mutableStateOf(task.projectTag) }
    var type by remember { mutableStateOf(task.taskType) }
    var scheduled by remember { mutableStateOf(task.scheduledDate?.toString().orEmpty()) }
    var manual by remember { mutableStateOf(task.manualTimeSpentMinutes?.toString().orEmpty()) }
    var recurrence by remember { mutableStateOf(task.recurrence) }
    var dayOfMonth by remember { mutableStateOf(task.recurrenceDayOfMonth?.toString().orEmpty()) }
    var pin by remember { mutableStateOf(task.pinnedStart?.time?.let(Dates::field).orEmpty()) }
    var subtasks by remember { mutableStateOf("") }
    var blockers by remember { mutableStateOf(blockerIds) }
    var advanced by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Tâche d'équipe (espace Équipe) : titulaire en cours d'édition, réponse « garder En cours ? », avancement, onglet.
    var assignee by remember { mutableStateOf(task.assigneeId) }
    var keepInProgress by remember { mutableStateOf(false) }
    var askKeepFor by remember { mutableStateOf<Long?>(null) }
    var progress by remember { mutableStateOf((task.progressPercent ?: 0).toFloat()) }
    var tab by remember { mutableStateOf(if (team?.initialHistory == true) 1 else 0) }
    val teamEditing = team?.editable == true
    var receivedProgress by remember { mutableStateOf((task.progressPercent ?: 0).toFloat()) }

    val parsedDeadline = Dates.parseDate(deadline)
    val deadlineInvalid = deadline.isNotBlank() && parsedDeadline == null
    val parsedScheduled = Dates.parseDate(scheduled)
    val scheduledInvalid = scheduled.isNotBlank() && parsedScheduled == null
    val parsedPin = Dates.parseTime(pin)
    val pinInvalid = pin.isNotBlank() && parsedPin == null
    val parsedDayOfMonth = dayOfMonth.toIntOrNull()?.takeIf { it in 1..31 }
    val dayOfMonthInvalid = recurrence == TaskRecurrence.MONTHLY_ON_DAY && parsedDayOfMonth == null
    val invalid = deadlineInvalid || scheduledInvalid || pinInvalid || dayOfMonthInvalid

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(16.dp).widthIn(max = 640.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            ) {
                Text(stringResource(Res.string.edit_title), style = MaterialTheme.typography.headlineSmall)
                if (team != null && teamEditing) TeamTaskTabs(tab, team.history.size) { tab = it }
                if (team != null && teamEditing && tab == 1) {
                    TaskHistory(team.history, team.assign, team.today)
                }
                if (tab == 0) {
                    OutlinedTextField(title, { title = it }, label = { Text(stringResource(Res.string.field_title)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        description,
                        { description = it },
                        label = { Text(stringResource(Res.string.field_description)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (received != null) {
                        ReceivedMark(received.origin, received.removed)
                        Text(
                            stringResource(Res.string.received_edit_help, managerLabel(received.origin)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (task.status == TaskStatus.TODO) ProgressField(receivedProgress) { receivedProgress = it }
                    }
                    if (team != null) {
                        TeamTaskFields(
                            task = task,
                            sheet = team,
                            assignee = assignee,
                            onPickAssignee = { picked ->
                                when {
                                    picked == assignee -> Unit
                                    // Une tâche en cours chez quelqu'un d'autre : la question se pose avant de changer de titulaire.
                                    picked != null && task.assigneeId != null && picked != task.assigneeId && task.startedOn != null -> askKeepFor = picked
                                    else -> { assignee = picked; keepInProgress = false }
                                }
                            },
                            progress = progress,
                            onProgress = { progress = it },
                        )
                    }
                    PriorityPills(priority, { priority = it }, showMeaning = true)
                    // Durée suggérée (Kairos 2, issue #15.6) : choisir un palier calibré
                    // REMPLACE la durée, même déjà saisie ; on reste libre de la retoucher.
                    PointsPills(points, { chosen ->
                        points = chosen
                        chosen?.let(estimates.minutesByPoints::get)?.let { minutes = it.toString() }
                    })
                    PointsGuide(estimates.references)
                    DateField(deadline, { deadline = it }, Res.string.field_deadline, deadlineInvalid)
                    OutlinedTextField(
                        minutes,
                        { value -> minutes = value.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(Res.string.field_minutes)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KairosTextButton(onClick = { advanced = !advanced }, modifier = Modifier.expandedState(advanced), contentPadding = KairosButtonIconPadding) {
                        Icon(if (advanced) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
                        Text(stringResource(Res.string.edit_advanced), modifier = Modifier.padding(start = 4.dp))
                    }
                    if (advanced) {
                        DateField(scheduled, { scheduled = it }, Res.string.field_scheduled, scheduledInvalid)
                        OutlinedTextField(project, { project = it }, label = { Text(stringResource(Res.string.field_project)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            manual,
                            { value -> manual = value.filter(Char::isDigit).take(5) },
                            label = { Text(stringResource(Res.string.field_manual_time)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        RecurrenceField(recurrence) { recurrence = it }
                        if (recurrence == TaskRecurrence.MONTHLY_ON_DAY) {
                            OutlinedTextField(
                                dayOfMonth,
                                { value -> dayOfMonth = value.filter(Char::isDigit).take(2) },
                                label = { Text(stringResource(Res.string.field_day_of_month)) },
                                isError = dayOfMonthInvalid,
                                supportingText = {
                                    Text(stringResource(if (dayOfMonthInvalid) Res.string.field_day_invalid else Res.string.field_day_of_month_hint))
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        // Suggestion par type (Kairos 2, issue #7) : seulement si la durée est
                        // encore vide, pour ne jamais écraser une estimation saisie à la main.
                        TypeField(type, taskTypes, label = if (teamEditing) Res.string.field_category else Res.string.field_type) { chosen ->
                            type = chosen
                            if (minutes.isBlank()) estimates.minutesByType[chosen]?.let { minutes = it.toString() }
                        }
                        OutlinedTextField(
                            pin,
                            { pin = it },
                            label = { Text(stringResource(Res.string.field_pin_time)) },
                            isError = pinInvalid,
                            supportingText = { Text(stringResource(if (pinInvalid) Res.string.time_invalid else Res.string.field_pin_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            subtasks,
                            { subtasks = it },
                            label = { Text(stringResource(Res.string.field_subtasks)) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(Res.string.field_blockers), style = MaterialTheme.typography.labelLarge)
                            Text(stringResource(Res.string.field_blockers_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val others = candidates.filter { it.id != task.id }
                            if (others.isEmpty()) {
                                Text(stringResource(Res.string.field_blockers_none), style = MaterialTheme.typography.bodySmall)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                others.forEach { other ->
                                    LabeledCheckbox(other.id in blockers, { checked -> blockers = if (checked) blockers + other.id else blockers - other.id }, other.title)
                                }
                            }
                        }
                    }
                }
                // Tâche retirée par le manager : la garder (elle devient une tâche personnelle) ou la supprimer, au choix du membre.
                if (received != null && received.removed && tab == 0) {
                    KairosOutlinedButton(onClick = received.onKeep) { Text(stringResource(Res.string.received_keep)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KairosTextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                    KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
                    KairosButton(
                        enabled = !invalid,
                        onClick = {
                            val edit =
                                TaskEdit(
                                    title = title,
                                    description = description,
                                    priority = priority,
                                    points = points,
                                    deadline = parsedDeadline,
                                    estimatedMinutes = minutes.toIntOrNull(),
                                    projectTag = project,
                                    taskType = type,
                                    scheduledDate = parsedScheduled,
                                    recurrence = recurrence,
                                    recurrenceDayOfMonth = parsedDayOfMonth,
                                    pinTime = parsedPin,
                                    pinDay = day,
                                    manualTimeSpentMinutes = manual.toIntOrNull(),
                                    newSubtasks = subtasks,
                                    blockerIds = blockers,
                                    reassign = if (teamEditing && assignee != task.assigneeId) Reassignment(assignee, keepInProgress) else null,
                                )
                            if (team != null && teamEditing) {
                                val percent = progress.roundToInt()
                                team.onSave(edit, percent.takeIf { assignee != null && task.status == TaskStatus.TODO && percent != (task.progressPercent ?: 0) })
                            } else if (received != null) {
                                val percent = receivedProgress.roundToInt()
                                received.onSave(edit, percent.takeIf { task.status == TaskStatus.TODO && percent != (task.progressPercent ?: 0) })
                            } else {
                                onSave(edit)
                            }
                        },
                    ) { Text(stringResource(Res.string.action_save)) }
                }
            }
        }
    }

    askKeepFor?.let { picked ->
        KeepInProgressDialog(
            taskTitle = task.title,
            toName = team?.assign?.name(picked).orEmpty(),
            onAnswer = { keep -> assignee = picked; keepInProgress = keep; askKeepFor = null },
            onCancel = { askKeepFor = null },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.delete_confirm_title)) },
            text = { Text(stringResource(Res.string.delete_confirm_body, task.title)) },
            confirmButton = {
                KairosTextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { KairosTextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun DateField(value: String, onChange: (String) -> Unit, label: StringResource, invalid: Boolean) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(stringResource(label)) },
        isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(Res.string.field_deadline_invalid)) }) else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun recurrenceLabel(r: TaskRecurrence): StringResource = when (r) {
    TaskRecurrence.NONE -> Res.string.task_rec_none
    TaskRecurrence.DAILY -> Res.string.task_rec_daily
    TaskRecurrence.WEEKDAYS -> Res.string.task_rec_weekdays
    TaskRecurrence.WEEKLY -> Res.string.task_rec_weekly
    TaskRecurrence.MONTHLY -> Res.string.task_rec_monthly
    TaskRecurrence.MONTHLY_ON_DAY -> Res.string.task_rec_monthly_on_day
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrenceField(value: TaskRecurrence, onChange: (TaskRecurrence) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(recurrenceLabel(value)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.field_recurrence)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TaskRecurrence.entries.forEach { r ->
                DropdownMenuItem(text = { Text(stringResource(recurrenceLabel(r))) }, onClick = { onChange(r); expanded = false })
            }
        }
    }
}

/** Type de tâche : liste des Réglages ; une valeur retirée de la liste reste affichée (jamais perdue). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeField(value: String, types: List<String>, label: StringResource, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val none = stringResource(Res.string.field_type_none)
    val options = (listOf("") + types + listOfNotNull(value.takeIf { it.isNotEmpty() && it !in types })).distinct()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value.ifEmpty { none },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.ifEmpty { none }) }, onClick = { onChange(option); expanded = false })
            }
        }
    }
}
