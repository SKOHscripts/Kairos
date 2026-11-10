package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.ui.generated.resources.calendar_device_from
import com.skohscripts.kairos.core.model.BlockSource
import com.skohscripts.kairos.ui.generated.resources.calendar_imported_from
import com.skohscripts.kairos.ui.generated.resources.calendar_block_delete
import com.skohscripts.kairos.ui.generated.resources.calendar_block_delete_title
import com.skohscripts.kairos.ui.generated.resources.calendar_block_delete_body
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skohscripts.kairos.core.day.EditableBlock
import com.skohscripts.kairos.core.engine.Recurrence
import com.skohscripts.kairos.ui.theme.KairosSegmentedButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.ui.text.style.TextDecoration
import com.skohscripts.kairos.ui.generated.resources.block_badge_changed
import com.skohscripts.kairos.ui.generated.resources.block_badge_monthly
import com.skohscripts.kairos.ui.generated.resources.block_badge_skipped
import com.skohscripts.kairos.ui.generated.resources.block_occurrence_hint
import com.skohscripts.kairos.ui.generated.resources.block_occurrence_skipped
import com.skohscripts.kairos.ui.generated.resources.block_rank_1
import com.skohscripts.kairos.ui.generated.resources.block_rank_2
import com.skohscripts.kairos.ui.generated.resources.block_rank_3
import com.skohscripts.kairos.ui.generated.resources.block_rank_4
import com.skohscripts.kairos.ui.generated.resources.block_rank_last
import com.skohscripts.kairos.ui.generated.resources.block_rec_monthly_day
import com.skohscripts.kairos.ui.generated.resources.block_rec_monthly_weekday
import com.skohscripts.kairos.ui.generated.resources.block_restore
import com.skohscripts.kairos.ui.generated.resources.block_scope_all
import com.skohscripts.kairos.ui.generated.resources.block_scope_one
import com.skohscripts.kairos.ui.generated.resources.block_skip
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
import com.skohscripts.kairos.core.settings.UiLanguage
import com.skohscripts.kairos.ui.app.uiLanguage
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
 * `datetime-local` du web n'a pas d'équivalent commun) ; la fin doit
 * suivre le début, le même jour.
 */
internal class BlockFormState(title: String, day: LocalDate, start: LocalTime?, end: LocalTime?, deepwork: Boolean, recurrence: BlockRecurrence) {
    /** Le jour proposé à l'ouverture : le repli du sélecteur de date quand le champ est vide. */
    val initialDay: LocalDate = day

    /** Langue de saisie des dates (`30/09/2026` en français), posée par [BlockFields]. */
    var dayFirst by mutableStateOf(false)
    var title by mutableStateOf(title)
    var day by mutableStateOf(day.toString())
    var start by mutableStateOf(start?.let(Dates::field).orEmpty())
    var end by mutableStateOf(end?.let(Dates::field).orEmpty())
    var deepwork by mutableStateOf(deepwork)
    var recurrence by mutableStateOf(recurrence)

    val parsedDay get() = Dates.parseDate(day, dayFirst)
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
            modifier = Modifier.fillMaxWidth().quietShortcuts(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.dayFirst = UiLanguage.dayFirst(uiLanguage())
            val dayError = state.day.isNotBlank() && state.parsedDay == null
            DateField(
                state.day, { state.day = it }, Res.string.block_date, dayError, state.initialDay,
                modifier = Modifier.width(220.dp).quietShortcuts(), shortcuts = false,
            )
            TimeField(state.start, { state.start = it }, Res.string.block_start, state.orderError)
            TimeField(state.end, { state.end = it }, Res.string.block_end, state.orderError)
        }
        if (state.orderError) Text(stringResource(Res.string.block_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            LabeledCheckbox(state.deepwork, { state.deepwork = it }, stringResource(Res.string.block_deepwork))
            BlockRecurrenceField(state.recurrence, state.parsedDay ?: state.initialDay) { state.recurrence = it }
        }
    }
}

/** Case à cocher dont le libellé est aussi cliquable (cible tactile de 48 dp). */
@Composable
internal fun LabeledCheckbox(checked: Boolean, onChange: (Boolean) -> Unit, label: String, enabled: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.heightIn(min = 48.dp).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange).padding(end = 8.dp),
    ) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(horizontal = 12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun TimeField(value: String, onChange: (String) -> Unit, label: StringResource, orderError: Boolean) {
    val invalid = value.isNotBlank() && Dates.parseTime(value) == null
    OutlinedTextField(
        value, onChange,
        // Une ligne : le libellé vide (« Début (HH:MM) ») ne doit pas rendre ce champ plus haut que son voisin.
        label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        isError = invalid || orderError,
        supportingText = if (invalid) ({ Text(stringResource(Res.string.time_invalid)) }) else null,
        singleLine = true, modifier = Modifier.width(170.dp).quietShortcuts(),
    )
}

/**
 * Libellé d'une règle pour un créneau qui commence le [day] : les mensuelles disent le jour qu'elles reprennent
 * (« mensuelle (le 15) », « mensuelle (3e mardi) », issue #108), tiré de la date saisie.
 */
@Composable
private fun recurrenceText(r: BlockRecurrence, day: LocalDate): String {
    val language = uiLanguage()
    return when (r) {
        BlockRecurrence.NONE -> stringResource(Res.string.block_rec_none)
        BlockRecurrence.DAILY -> stringResource(Res.string.block_rec_daily)
        BlockRecurrence.WEEKDAYS -> stringResource(Res.string.block_rec_weekdays)
        BlockRecurrence.WEEKLY -> stringResource(Res.string.block_rec_weekly)
        BlockRecurrence.MONTHLY_DAY -> stringResource(Res.string.block_rec_monthly_day, Dates.dayOfMonth(day, language))
        BlockRecurrence.MONTHLY_WEEKDAY -> stringResource(
            Res.string.block_rec_monthly_weekday,
            stringResource(rankLabel(Recurrence.weekdayRank(day))),
            Dates.weekdayName(day, language),
        )
    }
}

private fun rankLabel(rank: Int) = when (rank) {
    1 -> Res.string.block_rank_1
    2 -> Res.string.block_rank_2
    3 -> Res.string.block_rank_3
    4 -> Res.string.block_rank_4
    else -> Res.string.block_rank_last
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockRecurrenceField(value: BlockRecurrence, day: LocalDate, onChange: (BlockRecurrence) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = recurrenceText(value, day),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.block_recurrence)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.width(260.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BlockRecurrence.entries.forEach { r ->
                DropdownMenuItem(text = { Text(recurrenceText(r, day)) }, onClick = { onChange(r); expanded = false })
            }
        }
    }
}

/**
 * Liste des créneaux stockés du jour, chacun modifiable : un ponctuel, ou l'occurrence du jour d'un récurrent (heures
 * de l'occurrence, exception comprise ; une occurrence sautée reste listée, barrée, pour pouvoir la rétablir). Un créneau
 * importé d'un calendrier porte son origine et un bouton « Supprimer » à la place de « Modifier » ([onEdit] dans les deux cas).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayBlockList(blocks: List<EditableBlock>, language: String, onEdit: (EditableBlock) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Muted(stringResource(if (blocks.isEmpty()) Res.string.blocks_none else Res.string.blocks_today))
        blocks.forEach { eb ->
            val b = eb.occurrence
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    val decoration = if (eb.skipped) TextDecoration.LineThrough else null
                    Text(
                        "${Dates.time(b.start, language)}–${Dates.time(b.end, language)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (eb.skipped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        textDecoration = decoration,
                    )
                    Text(
                        blockTitle(b),
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = decoration,
                    )
                    // Créneau importé (issue #136) : d'où il vient ; il ne se modifie pas, il se supprime.
                    b.source?.let { src ->
                        val label = if (src is BlockSource.Device) Res.string.calendar_device_from else Res.string.calendar_imported_from
                        Badge(stringResource(label, sourceName(src)), icon = KairosIcons.Event)
                    }
                    if (b.kind == BlockKind.DEEPWORK) {
                        Badge(
                            stringResource(Res.string.tag_deepwork),
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                            icon = KairosIcons.Layers,
                        )
                    }
                    recurrenceBadge(b.recurrence)?.let { Badge(stringResource(it), icon = KairosIcons.Repeat) }
                    // L'exception du jour se lit en texte, pas seulement au trait barré (issue #110).
                    when {
                        eb.skipped -> Badge(stringResource(Res.string.block_badge_skipped))
                        eb.exception != null -> Badge(stringResource(Res.string.block_badge_changed))
                    }
                }
                if (b.source is BlockSource.Device) {
                    // Calendrier du téléphone : lu en direct, rien à supprimer dans Kairos (on le change dans l'agenda).
                } else if (b.imported) {
                    KairosRowIconButton(KairosIcons.Delete, stringResource(Res.string.calendar_block_delete), onClick = { onEdit(eb) })
                } else {
                    KairosRowIconButton(KairosIcons.Edit, stringResource(Res.string.action_edit), onClick = { onEdit(eb) })
                }
            }
        }
    }
}

private fun recurrenceBadge(r: BlockRecurrence) = when (r) {
    BlockRecurrence.DAILY -> Res.string.block_badge_daily
    BlockRecurrence.WEEKDAYS -> Res.string.block_badge_weekdays
    BlockRecurrence.WEEKLY -> Res.string.block_badge_weekly
    BlockRecurrence.MONTHLY_DAY, BlockRecurrence.MONTHLY_WEEKDAY -> Res.string.block_badge_monthly
    BlockRecurrence.NONE -> null
}

/** Supprimer un créneau importé (issue #136) : il reviendra au prochain import de son calendrier, le dialogue le dit. */
@Composable
internal fun ImportedBlockDeleteDialog(block: TimeBlock, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.calendar_block_delete_title)) },
        text = { Text(stringResource(Res.string.calendar_block_delete_body, blockTitle(block), block.source?.let { sourceName(it) }.orEmpty())) },
        confirmButton = { KairosTextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Ce que « Seulement celle-ci » peut faire de l'occurrence du jour (issue #110). */
internal class OccurrenceActions(
    val onSkip: () -> Unit,
    val onOverride: (title: String, start: LocalTime, end: LocalTime) -> Unit,
    val onRestore: () -> Unit,
)

/**
 * Dialogue de modification d'un créneau ; supprimer un récurrent le dit (toutes ses occurrences). Pour un récurrent,
 * un choix en tête : « Seulement celle-ci » (titre et heures du [day], « Sauter ce jour », « Rétablir ») ou « Toutes
 * les occurrences » (le modèle, comme avant).
 */
@Composable
internal fun BlockDialog(
    block: EditableBlock,
    day: LocalDate,
    onDismiss: () -> Unit,
    onSave: (BlockEdit) -> Unit,
    onDelete: () -> Unit,
    occurrence: OccurrenceActions,
) {
    val template = block.template
    val recurring = template.recurrence != BlockRecurrence.NONE
    val state = remember(block.id) { BlockFormState.of(template) }
    val one = remember(block.id, day) { BlockFormState.of(block.occurrence) }
    var onlyThis by remember(block.id) { mutableStateOf(recurring) }
    var confirmDelete by remember { mutableStateOf(false) }
    val language = uiLanguage()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(16.dp).widthIn(max = 640.dp),
        ) {
            Column(Modifier.verticalScrollWithThumb().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(Res.string.block_edit_title), style = MaterialTheme.typography.headlineSmall)
                if (recurring) {
                    KairosSegmentedRow(Modifier.fillMaxWidth()) {
                        KairosSegmentedButton(selected = onlyThis, onClick = { onlyThis = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                            Text(stringResource(Res.string.block_scope_one))
                        }
                        KairosSegmentedButton(selected = !onlyThis, onClick = { onlyThis = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                            Text(stringResource(Res.string.block_scope_all))
                        }
                    }
                }
                if (recurring && onlyThis) {
                    OccurrenceFields(block, one, day, language, occurrence, onDismiss)
                } else {
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
    }
    if (confirmDelete) {
        val name = template.title.ifEmpty { stringResource(Res.string.block_untitled) }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.block_delete_title)) },
            text = {
                Text(
                    stringResource(
                        if (template.recurrence == BlockRecurrence.NONE) Res.string.block_delete_body else Res.string.block_delete_recurring_body,
                        name,
                    ),
                )
            },
            confirmButton = { KairosTextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { KairosTextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/** « Seulement celle-ci » : titre et heures du jour, ou, pour une occurrence sautée, « Rétablir ». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OccurrenceFields(
    block: EditableBlock,
    form: BlockFormState,
    day: LocalDate,
    language: String,
    actions: OccurrenceActions,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (block.skipped) {
            Text(stringResource(Res.string.block_occurrence_skipped), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                stringResource(Res.string.block_occurrence_hint, Dates.long(day, language)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                form.title,
                { form.title = it },
                label = { Text(stringResource(Res.string.field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().quietShortcuts(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField(form.start, { form.start = it }, Res.string.block_start, form.orderError)
                TimeField(form.end, { form.end = it }, Res.string.block_end, form.orderError)
            }
            if (form.orderError) Text(stringResource(Res.string.block_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (block.exception != null) {
                KairosTextButton(onClick = actions.onRestore) { Text(stringResource(Res.string.block_restore)) }
            }
            if (!block.skipped) {
                KairosTextButton(onClick = actions.onSkip) { Text(stringResource(Res.string.block_skip)) }
            }
            KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            if (!block.skipped) {
                val start = form.parsedStart
                val end = form.parsedEnd
                val valid = start != null && end != null && end > start
                KairosButton(enabled = valid, onClick = { if (valid) actions.onOverride(form.title, start!!, end!!) }) {
                    Text(stringResource(Res.string.action_save))
                }
            }
        }
    }
}
