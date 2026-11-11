package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.settings.UiLanguage
import com.skohscripts.kairos.ui.app.uiLanguage
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.action_ok
import com.skohscripts.kairos.ui.generated.resources.date_clear
import com.skohscripts.kairos.ui.generated.resources.date_in_a_week
import com.skohscripts.kairos.ui.generated.resources.date_next_monday
import com.skohscripts.kairos.ui.generated.resources.date_pick
import com.skohscripts.kairos.ui.generated.resources.date_today
import com.skohscripts.kairos.ui.generated.resources.date_tomorrow
import com.skohscripts.kairos.ui.generated.resources.field_date_invalid
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.team.toPickerDate
import com.skohscripts.kairos.ui.team.toPickerMillis
import com.skohscripts.kairos.ui.theme.KairosFilterChip
import com.skohscripts.kairos.ui.theme.KairosRowIconButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Les raccourcis de date proposés sous un champ date (docs/spec/planning-personnalise.md § Lot A, 1). */
internal enum class DateShortcut(val label: StringResource) {
    TODAY(Res.string.date_today),
    TOMORROW(Res.string.date_tomorrow),
    NEXT_MONDAY(Res.string.date_next_monday),
    IN_A_WEEK(Res.string.date_in_a_week),
}

/**
 * Les dates des raccourcis à partir de [today] : aujourd'hui, demain, le lundi **suivant** (le prochain, jamais
 * aujourd'hui même un lundi) et dans une semaine ; « dans une semaine » est omis quand c'est aussi le lundi prochain
 * (un lundi), pour ne pas proposer deux fois la même date. L'heure est un paramètre : `core` et ce calcul restent purs.
 */
internal fun dateShortcuts(today: LocalDate): List<Pair<DateShortcut, LocalDate>> {
    val nextMonday = today.plus(DatePeriod(days = 7 - today.dayOfWeek.ordinal))
    val inAWeek = today.plus(DatePeriod(days = 7))
    return buildList {
        add(DateShortcut.TODAY to today)
        add(DateShortcut.TOMORROW to today.plus(DatePeriod(days = 1)))
        add(DateShortcut.NEXT_MONDAY to nextMonday)
        if (inAWeek != nextMonday) add(DateShortcut.IN_A_WEEK to inAWeek)
    }
}

/**
 * Champ date partagé (échéance, programmée pour, jour d'un créneau) : saisie souple (`2026-9-30`, `30/09/2026` en
 * français, [Dates.parseDate]), bouton du sélecteur de date Material 3 ([DateChoice]) et, si [shortcuts], les
 * raccourcis de [dateShortcuts] et « Effacer ». [today] est le vrai jour d'aujourd'hui, pas le jour affiché.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DateField(
    value: String,
    onChange: (String) -> Unit,
    label: StringResource,
    invalid: Boolean,
    today: LocalDate,
    modifier: Modifier = Modifier.fillMaxWidth(),
    shortcuts: Boolean = true,
) {
    val dayFirst = UiLanguage.dayFirst(uiLanguage())
    var picking by remember { mutableStateOf(false) }
    val parsed = Dates.parseDate(value, dayFirst)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value,
            onChange,
            label = { Text(stringResource(label)) },
            isError = invalid,
            supportingText = if (invalid) ({ Text(stringResource(Res.string.field_date_invalid)) }) else null,
            trailingIcon = { KairosRowIconButton(KairosIcons.Today, stringResource(Res.string.date_pick), onClick = { picking = true }) },
            singleLine = true,
            modifier = modifier,
        )
        if (shortcuts) DateShortcuts(value, onChange, today)
    }
    if (picking) {
        DateChoice(parsed ?: today, onPick = { onChange(it.toString()); picking = false }, onDismiss = { picking = false })
    }
}

/**
 * Raccourcis d'un champ date (« Aujourd'hui », « Demain », « Lundi prochain », « Dans une semaine », « Effacer ») ; à part
 * pour que la fiche puisse les poser sur toute la largeur sous deux champs côte à côte (docs/spec/fiche-tache.md).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DateShortcuts(value: String, onChange: (String) -> Unit, today: LocalDate) {
    val parsed = Dates.parseDate(value, UiLanguage.dayFirst(uiLanguage()))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        dateShortcuts(today).forEach { (shortcut, date) ->
            KairosFilterChip(selected = parsed == date, onClick = { onChange(date.toString()) }, label = { Text(stringResource(shortcut.label)) })
        }
        if (value.isNotBlank()) {
            KairosFilterChip(selected = false, onClick = { onChange("") }, label = { Text(stringResource(Res.string.date_clear)) })
        }
    }
}

/** Le sélecteur de date Material 3 (une date), en dialogue : « OK » / « Annuler ». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateChoice(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis(), initialDisplayedMonthMillis = initial.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            KairosTextButton(enabled = state.selectedDateMillis != null, onClick = { state.selectedDateMillis?.let { onPick(it.toPickerDate()) } }) {
                Text(stringResource(Res.string.action_ok))
            }
        },
        dismissButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    ) {
        DatePicker(state)
    }
}
