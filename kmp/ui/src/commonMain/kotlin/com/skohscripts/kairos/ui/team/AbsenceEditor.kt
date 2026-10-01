package com.skohscripts.kairos.ui.team

import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skohscripts.kairos.core.team.MemberAbsence
import com.skohscripts.kairos.core.team.MemberForm
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.absence_dialog_add_title
import com.skohscripts.kairos.ui.generated.resources.absence_dialog_dates
import com.skohscripts.kairos.ui.generated.resources.absence_dialog_edit_title
import com.skohscripts.kairos.ui.generated.resources.absence_error_order
import com.skohscripts.kairos.ui.generated.resources.absence_field_label
import com.skohscripts.kairos.ui.generated.resources.absence_field_label_help
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.action_save
import com.skohscripts.kairos.ui.navigation.LocalWindowWidth
import com.skohscripts.kairos.ui.navigation.isCompactWidth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Ajout ou modification d'une absence ([initial] nul : nouvelle) : dialogue
 * (plein écran sous 600 dp) autour de [AbsenceEditorContent].
 */
@Composable
internal fun AbsenceEditor(
    initial: MemberAbsence?,
    today: LocalDate,
    onConfirm: (LocalDate, LocalDate, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val compact = LocalWindowWidth.current.isCompactWidth
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AbsenceEditorContent(initial, today, compact, onConfirm, onDismiss)
    }
}

/**
 * Contenu de l'éditeur d'absence : le sélecteur de plage de dates Material 3
 * (`DateRangePicker`, premier et dernier jour inclus ; un seul jour touché =
 * absence d'un jour), le libellé facultatif, puis Annuler / Enregistrer
 * (inactif tant qu'aucune date n'est choisie). Le mois affiché au départ est
 * celui de l'absence, sinon celui de [today] (le sélecteur ignore l'horloge de
 * l'application).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbsenceEditorContent(
    initial: MemberAbsence?,
    today: LocalDate,
    compact: Boolean,
    onConfirm: (LocalDate, LocalDate, String) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit = {},
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.start?.toPickerMillis(),
        initialSelectedEndDateMillis = initial?.end?.toPickerMillis(),
        initialDisplayedMonthMillis = (initial?.start ?: today).toPickerMillis(),
    )
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var orderError by remember { mutableStateOf(false) }
    val start = state.selectedStartDateMillis?.toPickerDate()
    val end = state.selectedEndDateMillis?.toPickerDate() ?: start

    fun confirm() {
        if (start == null || end == null) return
        if (!MemberForm.isValidAbsence(start, end)) {
            orderError = true
            return
        }
        onConfirm(start, end, label.trim())
    }

    val padding = if (compact) 16.dp else 24.dp
    Surface(
        shape = if (compact) RectangleShape else MaterialTheme.shapes.extraLarge,
        // Même fond que le sélecteur de dates (surfaceContainerHigh) : pas de bandeau de deux tons en plein écran.
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (compact) 0.dp else 6.dp,
        shadowElevation = if (compact) 0.dp else 6.dp,
        modifier = if (compact) Modifier.fillMaxSize() else Modifier.padding(16.dp).width(480.dp).heightIn(max = 680.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(if (initial == null) Res.string.absence_dialog_add_title else Res.string.absence_dialog_edit_title),
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                modifier = Modifier.heading().padding(start = padding, end = padding, top = if (compact) 16.dp else padding, bottom = 8.dp),
            )
            DateRangePicker(
                state = state,
                title = {
                    Text(
                        stringResource(Res.string.absence_dialog_dates),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = padding, end = 12.dp, top = 8.dp),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(Res.string.absence_field_label)) },
                isError = orderError,
                supportingText = { Text(stringResource(if (orderError) Res.string.absence_error_order else Res.string.absence_field_label_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = padding),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = padding, vertical = 12.dp),
            ) {
                KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
                KairosButton(enabled = start != null, onClick = ::confirm) { Text(stringResource(Res.string.action_save)) }
            }
        }
    }
}

// Le sélecteur de dates échange des millisecondes UTC à minuit : le jour civil choisi, sans fuseau.
internal fun LocalDate.toPickerMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

internal fun Long.toPickerDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
