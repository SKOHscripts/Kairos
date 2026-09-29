package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.skohscripts.kairos.data.TaskEdit
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
import com.skohscripts.kairos.ui.generated.resources.field_type_none
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * Dialogue d'édition d'une tâche, champs essentiels (docs/spec-v3/vue-jour.md
 * § Édition) : titre, description (juste sous le titre, issue #32), priorité,
 * points, échéance, durée estimée, projet, type. Un seul « Enregistrer ».
 * Dialogue MD3 : coins de 28 dp, seul élément flottant à porter une ombre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditTaskDialog(
    task: Task,
    taskTypes: List<String>,
    onDismiss: () -> Unit,
    onSave: (TaskEdit) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description) }
    var priority by remember { mutableStateOf(task.priority) }
    var points by remember { mutableStateOf(task.fibonacciPoints) }
    var deadline by remember { mutableStateOf(task.deadline?.toString().orEmpty()) }
    var minutes by remember { mutableStateOf(task.estimatedMinutes?.toString().orEmpty()) }
    var project by remember { mutableStateOf(task.projectTag) }
    var type by remember { mutableStateOf(task.taskType) }
    var confirmDelete by remember { mutableStateOf(false) }

    val parsedDeadline = deadline.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val deadlineInvalid = deadline.isNotBlank() && parsedDeadline == null

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
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(Res.string.field_title)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    description,
                    { description = it },
                    label = { Text(stringResource(Res.string.field_description)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                PriorityPills(priority, { priority = it }, showMeaning = true)
                PointsPills(points, { points = it })
                OutlinedTextField(
                    deadline,
                    { deadline = it },
                    label = { Text(stringResource(Res.string.field_deadline)) },
                    isError = deadlineInvalid,
                    supportingText = if (deadlineInvalid) ({ Text(stringResource(Res.string.field_deadline_invalid)) }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    minutes,
                    { value -> minutes = value.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(Res.string.field_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(project, { project = it }, label = { Text(stringResource(Res.string.field_project)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TypeField(type, taskTypes) { type = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
                    Button(
                        enabled = !deadlineInvalid,
                        onClick = {
                            onSave(
                                TaskEdit(
                                    title = title,
                                    description = description,
                                    priority = priority,
                                    points = points,
                                    deadline = parsedDeadline,
                                    estimatedMinutes = minutes.toIntOrNull(),
                                    projectTag = project,
                                    taskType = type,
                                ),
                            )
                        },
                    ) { Text(stringResource(Res.string.action_save)) }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.delete_confirm_title)) },
            text = { Text(stringResource(Res.string.delete_confirm_body, task.title)) },
            confirmButton = {
                TextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/** Type de tâche : liste des Réglages ; une valeur retirée de la liste reste affichée (jamais perdue). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeField(value: String, types: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val none = stringResource(Res.string.field_type_none)
    val options = (listOf("") + types + listOfNotNull(value.takeIf { it.isNotEmpty() && it !in types })).distinct()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value.ifEmpty { none },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.field_type)) },
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
