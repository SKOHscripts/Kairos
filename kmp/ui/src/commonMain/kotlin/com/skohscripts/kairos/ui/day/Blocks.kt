package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.ui.theme.KairosRowIconButton
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.data.BlockEdit
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.action_delete
import com.skohscripts.kairos.ui.generated.resources.action_edit
import com.skohscripts.kairos.ui.generated.resources.action_save
import com.skohscripts.kairos.ui.generated.resources.block_badge_daily
import com.skohscripts.kairos.ui.generated.resources.block_badge_weekdays
import com.skohscripts.kairos.ui.generated.resources.block_badge_weekly
import com.skohscripts.kairos.ui.generated.resources.block_date
import com.skohscripts.kairos.ui.generated.resources.block_deepwork
import com.skohscripts.kairos.ui.generated.resources.block_delete_body
import com.skohscripts.kairos.ui.generated.resources.block_delete_recurring_body
import com.skohscripts.kairos.ui.generated.resources.block_delete_title
import com.skohscripts.kairos.ui.generated.resources.block_edit_title
import com.skohscripts.kairos.ui.generated.resources.block_end
import com.skohscripts.kairos.ui.generated.resources.block_invalid
import com.skohscripts.kairos.ui.generated.resources.block_placeholder
import com.skohscripts.kairos.ui.generated.resources.block_rec_daily
import com.skohscripts.kairos.ui.generated.resources.block_rec_none
import com.skohscripts.kairos.ui.generated.resources.block_rec_weekdays
import com.skohscripts.kairos.ui.generated.resources.block_rec_weekly
import com.skohscripts.kairos.ui.generated.resources.block_recurrence
import com.skohscripts.kairos.ui.generated.resources.block_start
import com.skohscripts.kairos.ui.generated.resources.block_untitled
import com.skohscripts.kairos.ui.generated.resources.blocks_none
import com.skohscripts.kairos.ui.generated.resources.blocks_today
import com.skohscripts.kairos.ui.generated.resources.field_deadline_invalid
import com.skohscripts.kairos.ui.generated.resources.field_title
import com.skohscripts.kairos.ui.generated.resources.tag_deepwork
import com.skohscripts.kairos.ui.generated.resources.time_invalid
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Saisie d'un créneau (création et modification) : titre, jour, début, fin,
 * deep work, récurrence. Le jour et les heures sont saisis à part (le
 * `datetime-local` de Kairos 2 n'a pas d'équivalent commun) ; la fin doit
 * suivre le début, le même jour.
 */
internal class BlockFormState(title: String, day: LocalDate, start: LocalTime?, end: LocalTime?, deepwork: Boolean, recurrence: BlockRecurrence) {
    var title by mutableStateOf(title)
    var day by mutableStateOf(day.toString())
    var start by mutableStateOf(start?.let(Dates::field).orEmpty())
    var end by mutableStateOf(end?.let(Dates::field).orEmpty())
    var deepwork by mutableStateOf(deepwork)
    var recurrence by mutableStateOf(recurrence)

    val parsedDay get() = Dates.parseDate(day)
    val parsedStart get() = Dates.parseTime(start)
    val parsedEnd get() = Dates.parseTime(end)
    val orderError get() = parsedStart != null && parsedEnd != null && parsedEnd!! <= parsedStart!!

    fun edit(): BlockEdit? {
        val d = parsedDay ?: return null
        val s = parsedStart ?: return null
        val e = parsedEnd ?: return null
        if (e <= s) return null
        return BlockEdit(title, d.atTime(s), d.atTime(e), if (deepwork) BlockKind.DEEPWORK else BlockKind.BUSY, recurrence)
    }

    companion object {
        fun empty(day: LocalDate) = BlockFormState("", day, null, null, false, BlockRecurrence.NONE)
        fun of(b: TimeBlock) = BlockFormState(b.title, b.start.date, b.start.time, b.end.time, b.kind == BlockKind.DEEPWORK, b.recurrence)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlockFields(state: BlockFormState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            state.title,
            { state.title = it },
            label = { Text(stringResource(Res.string.field_title)) },
            placeholder = { Text(stringResource(Res.string.block_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val dayError = state.day.isNotBlank() && state.parsedDay == null
            OutlinedTextField(
                state.day, { state.day = it },
                label = { Text(stringResource(Res.string.block_date)) },
                isError = dayError,
                supportingText = if (dayError) ({ Text(stringResource(Res.string.field_deadline_invalid)) }) else null,
                singleLine = true, modifier = Modifier.width(190.dp),
            )
            TimeField(state.start, { state.start = it }, Res.string.block_start, state.orderError)
            TimeField(state.end, { state.end = it }, Res.string.block_end, state.orderError)
        }
        if (state.orderError) Text(stringResource(Res.string.block_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            LabeledCheckbox(state.deepwork, { state.deepwork = it }, stringResource(Res.string.block_deepwork))
            BlockRecurrenceField(state.recurrence) { state.recurrence = it }
        }
    }
}

/** Case à cocher dont le libellé est aussi cliquable (cible tactile de 48 dp). */
@Composable
internal fun LabeledCheckbox(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange).padding(end = 8.dp),
    ) {
        Checkbox(checked, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TimeField(value: String, onChange: (String) -> Unit, label: StringResource, orderError: Boolean) {
    val invalid = value.isNotBlank() && Dates.parseTime(value) == null
    OutlinedTextField(
        value, onChange,
        label = { Text(stringResource(label)) },
        isError = invalid || orderError,
        supportingText = if (invalid) ({ Text(stringResource(Res.string.time_invalid)) }) else null,
        singleLine = true, modifier = Modifier.width(150.dp),
    )
}

private fun recurrenceLabel(r: BlockRecurrence) = when (r) {
    BlockRecurrence.NONE -> Res.string.block_rec_none
    BlockRecurrence.DAILY -> Res.string.block_rec_daily
    BlockRecurrence.WEEKDAYS -> Res.string.block_rec_weekdays
    BlockRecurrence.WEEKLY -> Res.string.block_rec_weekly
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockRecurrenceField(value: BlockRecurrence, onChange: (BlockRecurrence) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(recurrenceLabel(value)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.block_recurrence)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.width(260.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BlockRecurrence.entries.forEach { r ->
                DropdownMenuItem(text = { Text(stringResource(recurrenceLabel(r))) }, onClick = { onChange(r); expanded = false })
            }
        }
    }
}

/** Liste des créneaux stockés du jour, chacun modifiable (un récurrent : son modèle). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayBlockList(blocks: List<TimeBlock>, language: String, onEdit: (TimeBlock) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Muted(stringResource(if (blocks.isEmpty()) Res.string.blocks_none else Res.string.blocks_today))
        blocks.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${Dates.time(b.start, language)}–${Dates.time(b.end, language)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(b.title.ifEmpty { stringResource(Res.string.block_untitled) }, style = MaterialTheme.typography.bodyMedium)
                    if (b.kind == BlockKind.DEEPWORK) {
                        Badge(
                            stringResource(Res.string.tag_deepwork),
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                            icon = KairosIcons.Layers,
                        )
                    }
                    when (b.recurrence) {
                        BlockRecurrence.DAILY -> Res.string.block_badge_daily
                        BlockRecurrence.WEEKDAYS -> Res.string.block_badge_weekdays
                        BlockRecurrence.WEEKLY -> Res.string.block_badge_weekly
                        BlockRecurrence.NONE -> null
                    }?.let { Badge(stringResource(it), icon = KairosIcons.Repeat) }
                }
                KairosRowIconButton(KairosIcons.Edit, stringResource(Res.string.action_edit), onClick = { onEdit(b) })
            }
        }
    }
}

/** Dialogue de modification d'un créneau ; supprimer un récurrent le dit (toutes ses occurrences). */
@Composable
internal fun BlockDialog(block: TimeBlock, onDismiss: () -> Unit, onSave: (BlockEdit) -> Unit, onDelete: () -> Unit) {
    val state = remember(block.id) { BlockFormState.of(block) }
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(16.dp).widthIn(max = 640.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(Res.string.block_edit_title), style = MaterialTheme.typography.headlineSmall)
                BlockFields(state)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KairosTextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                    KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
                    val edit = state.edit()
                    KairosButton(enabled = edit != null, onClick = { edit?.let(onSave) }) { Text(stringResource(Res.string.action_save)) }
                }
            }
        }
    }
    if (confirmDelete) {
        val name = block.title.ifEmpty { stringResource(Res.string.block_untitled) }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.block_delete_title)) },
            text = {
                Text(
                    stringResource(
                        if (block.recurrence == BlockRecurrence.NONE) Res.string.block_delete_body else Res.string.block_delete_recurring_body,
                        name,
                    ),
                )
            },
            confirmButton = { KairosTextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { KairosTextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}
