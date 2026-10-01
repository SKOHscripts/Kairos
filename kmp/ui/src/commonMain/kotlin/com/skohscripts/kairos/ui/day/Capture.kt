package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.data.BlockEdit
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.capture_add
import com.skohscripts.kairos.ui.generated.resources.capture_label
import com.skohscripts.kairos.ui.generated.resources.capture_mode_block
import com.skohscripts.kairos.ui.generated.resources.capture_mode_task
import com.skohscripts.kairos.ui.generated.resources.capture_placeholder
import com.skohscripts.kairos.ui.generated.resources.capture_title
import com.skohscripts.kairos.ui.generated.resources.shortcut_hint
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** État de la capture partagé avec les raccourcis clavier (`N` bascule sur « Tâche » et y met le curseur). */
internal class CaptureState {
    var blockMode by mutableStateOf(false)
    val taskFocus = FocusRequester()

    fun focusTask() {
        blockMode = false
    }
}

/**
 * Capture, toujours visible en tête (flux GTD) : deux volets, « Tâche »
 * (titre seul, Entrée ajoute et garde le curseur) et « Créneau / deep work »
 * (avec la liste des créneaux du jour, modifiables). Carte « filled ».
 * [taskOnly] : sans le second volet (Backlog d'équipe).
 */
@Composable
internal fun Capture(
    state: CaptureState,
    day: LocalDate,
    blocks: List<TimeBlock>,
    language: String,
    showShortcuts: Boolean,
    onAddTask: (String) -> Unit,
    onAddBlock: (BlockEdit) -> Unit,
    onEditBlock: (TimeBlock) -> Unit,
    /** Volet « Tâche » seul, sans choix « Créneau / deep work » : la capture du Backlog d'équipe (les créneaux sont personnels). */
    taskOnly: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(KairosIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(Res.string.capture_title), style = MaterialTheme.typography.titleMedium)
                if (showShortcuts) KeyHint("N")
            }
            if (!taskOnly) KairosSegmentedRow(Modifier.fillMaxWidth()) {
                KairosSegmentedButton(
                    selected = !state.blockMode,
                    onClick = { state.blockMode = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(Res.string.capture_mode_task)) }
                KairosSegmentedButton(
                    selected = state.blockMode,
                    onClick = { state.blockMode = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(Res.string.capture_mode_block)) }
            }
            if (state.blockMode && !taskOnly) {
                val form = remember(day) { BlockFormState.empty(day) }
                BlockFields(form)
                val edit = form.edit()
                KairosButton(enabled = edit != null, onClick = {
                    edit?.let(onAddBlock)
                    form.title = ""
                    form.start = ""
                    form.end = ""
                }, contentPadding = KairosButtonIconPadding) {
                    Icon(KairosIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.capture_add), modifier = Modifier.padding(start = 6.dp))
                }
                DayBlockList(blocks, language, onEditBlock)
            } else {
                TaskCapture(state.taskFocus, onAddTask)
            }
        }
    }
}

@Composable
private fun TaskCapture(focus: FocusRequester, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
        runCatching { focus.requestFocus() }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(Res.string.capture_label)) },
            placeholder = { Text(stringResource(Res.string.capture_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                    submit()
                    true
                } else {
                    false
                }
            },
        )
        KairosButton(onClick = { submit() }, contentPadding = KairosButtonIconPadding) {
            Icon(KairosIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.capture_add), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** Touche de raccourci signalée à côté de son contrôle (masquée sur Android, sans clavier physique en général). */
@Composable
internal fun KeyHint(key: String) {
    val description = stringResource(Res.string.shortcut_hint, key)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}
