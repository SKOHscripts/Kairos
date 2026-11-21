package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.input.KeyboardType
import com.skohscripts.kairos.core.model.Interruption
import com.skohscripts.kairos.core.model.InterruptionSource
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.action_save
import com.skohscripts.kairos.ui.generated.resources.interruption_by
import com.skohscripts.kairos.ui.generated.resources.interruption_for
import com.skohscripts.kairos.ui.generated.resources.interruption_note
import com.skohscripts.kairos.ui.generated.resources.interruption_note_field
import com.skohscripts.kairos.ui.generated.resources.interruption_source_call
import com.skohscripts.kairos.ui.generated.resources.interruption_source_message
import com.skohscripts.kairos.ui.generated.resources.interruption_source_person
import com.skohscripts.kairos.ui.generated.resources.interruption_source_self
import com.skohscripts.kairos.ui.generated.resources.interruption_title
import com.skohscripts.kairos.ui.generated.resources.spent_other
import com.skohscripts.kairos.ui.generated.resources.spent_other_field
import com.skohscripts.kairos.ui.generated.resources.spent_other_help
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosFilterChip
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Nom d'une source de dérangement. */
internal fun InterruptionSource.label(): StringResource = when (this) {
    InterruptionSource.PERSON -> Res.string.interruption_source_person
    InterruptionSource.CALL -> Res.string.interruption_source_call
    InterruptionSource.MESSAGE -> Res.string.interruption_source_message
    InterruptionSource.SELF -> Res.string.interruption_source_self
}

/**
 * « J'ai été dérangé » (docs/spec/derangements.md § Comportement attendu, issue #272) : un dialogue, comme le reste de Kairos. La
 * source se choisit d'abord si on le souhaite ; toucher une durée enregistre aussitôt (source et note comprises). « Autre… »
 * demande un nombre de minutes (1 à 240), puis « Enregistrer ».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InterruptionDialog(onSave: (minutes: Int, source: InterruptionSource?, note: String?) -> Unit, onDismiss: () -> Unit) {
    var source by remember { mutableStateOf<InterruptionSource?>(null) }
    var noteOn by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var other by remember { mutableStateOf(false) }
    var minutesText by remember { mutableStateOf("") }
    val minutes = minutesText.trim().toIntOrNull()?.takeIf { it in Interruption.MIN_MINUTES..Interruption.MAX_MINUTES }
    fun save(m: Int) = onSave(m, source, note.trim().takeIf { noteOn && it.isNotEmpty() })
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("interruption-dialog"),
        title = { Text(stringResource(Res.string.interruption_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
                Text(stringResource(Res.string.interruption_by), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
                    InterruptionSource.entries.forEach { s ->
                        KairosFilterChip(
                            selected = source == s, onClick = { source = if (source == s) null else s },
                            label = { Text(stringResource(s.label())) }, modifier = Modifier.testTag("interruption-source-${s.code}"),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().toggleable(value = noteOn, role = Role.Checkbox, onValueChange = { noteOn = it }),
                ) {
                    Checkbox(checked = noteOn, onCheckedChange = null)
                    Text(stringResource(Res.string.interruption_note))
                }
                if (noteOn) {
                    OutlinedTextField(
                        note, { note = it }, placeholder = { Text(stringResource(Res.string.interruption_note_field)) },
                        modifier = Modifier.fillMaxWidth().testTag("interruption-note"),
                    )
                }
                Text(stringResource(Res.string.interruption_for), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
                    Interruption.CHOICES.forEach { m ->
                        KairosFilterChip(selected = false, onClick = { save(m) }, label = { Text(duration(m)) }, modifier = Modifier.testTag("interruption-$m"))
                    }
                    KairosFilterChip(selected = other, onClick = { other = !other }, label = { Text(stringResource(Res.string.spent_other)) })
                }
                if (other) {
                    OutlinedTextField(
                        minutesText, { v -> minutesText = v.filter { it.isDigit() }.take(3) },
                        label = { Text(stringResource(Res.string.spent_other_field)) },
                        supportingText = { Text(stringResource(Res.string.spent_other_help, Interruption.MAX_MINUTES)) },
                        isError = minutesText.isNotEmpty() && minutes == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.testTag("interruption-minutes"),
                    )
                }
            }
        },
        confirmButton = {
            if (other) {
                KairosButton(onClick = { minutes?.let(::save) }, enabled = minutes != null) { Text(stringResource(Res.string.action_save)) }
            }
        },
        dismissButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Correction d'un dérangement (Stats) : durée de 1 à 240 et source ; « Enregistrer ». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InterruptionEditDialog(title: String, initial: Interruption, onSave: (Int, InterruptionSource?) -> Unit, onDismiss: () -> Unit) {
    var source by remember { mutableStateOf(initial.source) }
    var minutesText by remember { mutableStateOf(initial.minutes.toString()) }
    val minutes = minutesText.trim().toIntOrNull()?.takeIf { it in Interruption.MIN_MINUTES..Interruption.MAX_MINUTES }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
                OutlinedTextField(
                    minutesText, { v -> minutesText = v.filter { it.isDigit() }.take(3) },
                    label = { Text(stringResource(Res.string.spent_other_field)) },
                    supportingText = { Text(stringResource(Res.string.spent_other_help, Interruption.MAX_MINUTES)) },
                    isError = minutes == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(stringResource(Res.string.interruption_by), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
                    InterruptionSource.entries.forEach { s ->
                        KairosFilterChip(selected = source == s, onClick = { source = if (source == s) null else s }, label = { Text(stringResource(s.label())) })
                    }
                }
            }
        },
        confirmButton = { KairosButton(onClick = { minutes?.let { onSave(it, source) } }, enabled = minutes != null) { Text(stringResource(Res.string.action_save)) } },
        dismissButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
