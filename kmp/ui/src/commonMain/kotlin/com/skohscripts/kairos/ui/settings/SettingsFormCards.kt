package com.skohscripts.kairos.ui.settings

import com.skohscripts.kairos.ui.app.heading
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.settings.FieldError
import com.skohscripts.kairos.core.settings.FieldErrorKind
import com.skohscripts.kairos.core.settings.FieldKind
import com.skohscripts.kairos.core.settings.SettingsForm
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.settings_chrono_body
import com.skohscripts.kairos.ui.generated.resources.settings_chrono_title
import com.skohscripts.kairos.ui.generated.resources.settings_error_color
import com.skohscripts.kairos.ui.generated.resources.settings_error_date
import com.skohscripts.kairos.ui.generated.resources.settings_error_integer
import com.skohscripts.kairos.ui.generated.resources.settings_error_max
import com.skohscripts.kairos.ui.generated.resources.settings_error_min
import com.skohscripts.kairos.ui.generated.resources.settings_error_min_exclusive
import com.skohscripts.kairos.ui.generated.resources.settings_error_number
import com.skohscripts.kairos.ui.generated.resources.settings_error_required
import com.skohscripts.kairos.ui.generated.resources.settings_idle
import com.skohscripts.kairos.ui.generated.resources.settings_pomodoro
import com.skohscripts.kairos.ui.generated.resources.settings_section_day
import com.skohscripts.kairos.ui.generated.resources.settings_section_dip
import com.skohscripts.kairos.ui.generated.resources.settings_section_dip_body
import com.skohscripts.kairos.ui.generated.resources.settings_section_guards
import com.skohscripts.kairos.ui.generated.resources.settings_section_holidays
import com.skohscripts.kairos.ui.generated.resources.settings_section_holidays_body
import com.skohscripts.kairos.ui.generated.resources.settings_section_stats
import com.skohscripts.kairos.ui.generated.resources.settings_section_types
import com.skohscripts.kairos.ui.generated.resources.settings_section_wsjf
import com.skohscripts.kairos.ui.generated.resources.settings_section_wsjf_body
import com.skohscripts.kairos.ui.generated.resources.settings_sound
import com.skohscripts.kairos.ui.generated.resources.setting_default_duration
import com.skohscripts.kairos.ui.generated.resources.setting_default_duration_help
import com.skohscripts.kairos.ui.generated.resources.setting_default_points
import com.skohscripts.kairos.ui.generated.resources.setting_default_points_help
import com.skohscripts.kairos.ui.generated.resources.setting_dip_enabled
import com.skohscripts.kairos.ui.generated.resources.setting_dip_end
import com.skohscripts.kairos.ui.generated.resources.setting_dip_help
import com.skohscripts.kairos.ui.generated.resources.setting_dip_penalty
import com.skohscripts.kairos.ui.generated.resources.setting_dip_penalty_help
import com.skohscripts.kairos.ui.generated.resources.setting_dip_start
import com.skohscripts.kairos.ui.generated.resources.setting_dip_trough
import com.skohscripts.kairos.ui.generated.resources.setting_extra_holidays
import com.skohscripts.kairos.ui.generated.resources.setting_extra_holidays_help
import com.skohscripts.kairos.ui.generated.resources.setting_holidays_fr
import com.skohscripts.kairos.ui.generated.resources.setting_meeting_buffer
import com.skohscripts.kairos.ui.generated.resources.setting_meeting_buffer_help
import com.skohscripts.kairos.ui.generated.resources.setting_overload
import com.skohscripts.kairos.ui.generated.resources.setting_overload_help
import com.skohscripts.kairos.ui.generated.resources.setting_priority_base
import com.skohscripts.kairos.ui.generated.resources.setting_priority_base_help
import com.skohscripts.kairos.ui.generated.resources.setting_stale_overdue
import com.skohscripts.kairos.ui.generated.resources.setting_stale_overdue_help
import com.skohscripts.kairos.ui.generated.resources.setting_stale_untouched
import com.skohscripts.kairos.ui.generated.resources.setting_stale_untouched_help
import com.skohscripts.kairos.ui.generated.resources.setting_stats_window
import com.skohscripts.kairos.ui.generated.resources.setting_stats_window_help
import com.skohscripts.kairos.ui.generated.resources.setting_task_types
import com.skohscripts.kairos.ui.generated.resources.setting_team_enabled
import com.skohscripts.kairos.ui.generated.resources.setting_team_manager
import com.skohscripts.kairos.ui.generated.resources.setting_team_manager_help
import com.skohscripts.kairos.ui.generated.resources.setting_team_name
import com.skohscripts.kairos.ui.generated.resources.setting_team_name_help
import com.skohscripts.kairos.ui.generated.resources.setting_team_churn
import com.skohscripts.kairos.ui.generated.resources.setting_team_churn_help
import com.skohscripts.kairos.ui.generated.resources.setting_team_stale_progress
import com.skohscripts.kairos.ui.generated.resources.setting_team_stale_progress_help
import com.skohscripts.kairos.ui.generated.resources.setting_team_horizon
import com.skohscripts.kairos.ui.generated.resources.setting_team_horizon_help
import com.skohscripts.kairos.ui.generated.resources.setting_team_focus
import com.skohscripts.kairos.ui.generated.resources.setting_team_focus_help
import com.skohscripts.kairos.ui.generated.resources.setting_team_hours_per_point
import com.skohscripts.kairos.ui.generated.resources.setting_team_hours_per_point_help
import com.skohscripts.kairos.ui.generated.resources.setting_team_load_warn
import com.skohscripts.kairos.ui.generated.resources.setting_team_load_warn_help
import com.skohscripts.kairos.ui.generated.resources.setting_team_affinity
import com.skohscripts.kairos.ui.generated.resources.setting_team_affinity_help
import com.skohscripts.kairos.ui.generated.resources.setting_team_wip
import com.skohscripts.kairos.ui.generated.resources.setting_team_wip_help
import com.skohscripts.kairos.ui.generated.resources.setting_task_types_help
import com.skohscripts.kairos.ui.generated.resources.setting_update_check
import com.skohscripts.kairos.ui.generated.resources.setting_urgency_horizon
import com.skohscripts.kairos.ui.generated.resources.setting_urgency_horizon_help
import com.skohscripts.kairos.ui.generated.resources.setting_urgency_peak
import com.skohscripts.kairos.ui.generated.resources.setting_urgency_peak_help
import com.skohscripts.kairos.ui.generated.resources.setting_workday_end
import com.skohscripts.kairos.ui.generated.resources.setting_workday_end_help
import com.skohscripts.kairos.ui.generated.resources.setting_workday_start
import com.skohscripts.kairos.ui.generated.resources.setting_workday_start_help
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Libellé et aide (facultative) d'un champ de [SettingsForm]. */
private class FieldText(val label: StringResource, val help: StringResource? = null)

private val TEXTS: Map<String, FieldText> = mapOf(
    "defaultTaskDurationMinutes" to FieldText(Res.string.setting_default_duration, Res.string.setting_default_duration_help),
    "meetingBufferMinutes" to FieldText(Res.string.setting_meeting_buffer, Res.string.setting_meeting_buffer_help),
    "workdayStartHour" to FieldText(Res.string.setting_workday_start, Res.string.setting_workday_start_help),
    "workdayEndHour" to FieldText(Res.string.setting_workday_end, Res.string.setting_workday_end_help),
    "priorityValueBase" to FieldText(Res.string.setting_priority_base, Res.string.setting_priority_base_help),
    "urgencyHorizonDays" to FieldText(Res.string.setting_urgency_horizon, Res.string.setting_urgency_horizon_help),
    "urgencyPeak" to FieldText(Res.string.setting_urgency_peak, Res.string.setting_urgency_peak_help),
    "defaultFibonacciPoints" to FieldText(Res.string.setting_default_points, Res.string.setting_default_points_help),
    "cognitiveDipEnabled" to FieldText(Res.string.setting_dip_enabled),
    "cognitiveDipStartHour" to FieldText(Res.string.setting_dip_start),
    "cognitiveDipTroughHour" to FieldText(Res.string.setting_dip_trough, Res.string.setting_dip_help),
    "cognitiveDipEndHour" to FieldText(Res.string.setting_dip_end),
    "cognitiveDipPenalty" to FieldText(Res.string.setting_dip_penalty, Res.string.setting_dip_penalty_help),
    "staleOverdueDays" to FieldText(Res.string.setting_stale_overdue, Res.string.setting_stale_overdue_help),
    "staleUntouchedDays" to FieldText(Res.string.setting_stale_untouched, Res.string.setting_stale_untouched_help),
    "priorityOverloadThreshold" to FieldText(Res.string.setting_overload, Res.string.setting_overload_help),
    "taskTypes" to FieldText(Res.string.setting_task_types, Res.string.setting_task_types_help),
    "statsWindowWeeks" to FieldText(Res.string.setting_stats_window, Res.string.setting_stats_window_help),
    "timerIdleAlertMinutes" to FieldText(Res.string.settings_idle),
    "pomodoroFocusMinutes" to FieldText(Res.string.settings_pomodoro),
    "timerAlertSound" to FieldText(Res.string.settings_sound),
    "holidaysFr" to FieldText(Res.string.setting_holidays_fr),
    "extraHolidays" to FieldText(Res.string.setting_extra_holidays, Res.string.setting_extra_holidays_help),
    "updateCheckEnabled" to FieldText(Res.string.setting_update_check),
    "team.enabled" to FieldText(Res.string.setting_team_enabled),
    "team.name" to FieldText(Res.string.setting_team_name, Res.string.setting_team_name_help),
    "team.managerName" to FieldText(Res.string.setting_team_manager, Res.string.setting_team_manager_help),
    "team.staleProgressDays" to FieldText(Res.string.setting_team_stale_progress, Res.string.setting_team_stale_progress_help),
    "team.churnThreshold" to FieldText(Res.string.setting_team_churn, Res.string.setting_team_churn_help),
    "team.wipLimit" to FieldText(Res.string.setting_team_wip, Res.string.setting_team_wip_help),
    "team.horizonWeeks" to FieldText(Res.string.setting_team_horizon, Res.string.setting_team_horizon_help),
    "team.focusFactor" to FieldText(Res.string.setting_team_focus, Res.string.setting_team_focus_help),
    "team.hoursPerPoint" to FieldText(Res.string.setting_team_hours_per_point, Res.string.setting_team_hours_per_point_help),
    "team.loadWarnPercent" to FieldText(Res.string.setting_team_load_warn, Res.string.setting_team_load_warn_help),
    "team.affinityDays" to FieldText(Res.string.setting_team_affinity, Res.string.setting_team_affinity_help),
)

/** Sections de l'écran, dans l'ordre de Kairos 2 (moins les intégrations retirées). */
private class Section(val title: StringResource, val body: StringResource?, val keys: List<String>)

private val SECTIONS = listOf(
    Section(Res.string.settings_section_day, null, listOf("defaultTaskDurationMinutes", "meetingBufferMinutes", "workdayStartHour", "workdayEndHour")),
    Section(Res.string.settings_section_wsjf, Res.string.settings_section_wsjf_body, listOf("priorityValueBase", "urgencyHorizonDays", "urgencyPeak", "defaultFibonacciPoints")),
    Section(
        Res.string.settings_section_dip,
        Res.string.settings_section_dip_body,
        listOf("cognitiveDipEnabled", "cognitiveDipStartHour", "cognitiveDipTroughHour", "cognitiveDipEndHour", "cognitiveDipPenalty"),
    ),
    Section(Res.string.settings_section_guards, null, listOf("staleOverdueDays", "staleUntouchedDays", "priorityOverloadThreshold")),
    Section(Res.string.settings_section_types, null, listOf("taskTypes")),
    Section(Res.string.settings_section_stats, null, listOf("statsWindowWeeks")),
    Section(Res.string.settings_chrono_title, Res.string.settings_chrono_body, listOf("timerIdleAlertMinutes", "pomodoroFocusMinutes", "timerAlertSound")),
    Section(Res.string.settings_section_holidays, Res.string.settings_section_holidays_body, listOf("holidaysFr", "extraHolidays")),
)

/**
 * Les cartes du formulaire des Réglages (docs/spec/reglages.md) : une
 * carte à contour par section, un champ par réglage, l'erreur du champ à la
 * place de son aide. L'état (textes, erreurs) appartient à l'écran.
 */
@Composable
internal fun SettingsFormCards(values: Map<String, String>, errors: Map<String, FieldError>, onChange: (String, String) -> Unit) {
    SECTIONS.forEach { section ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(section.title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
                section.body?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                section.keys.forEach { key -> SettingInput(key, values[key].orEmpty(), errors[key]) { onChange(key, it) } }
            }
        }
    }
}

/** Un réglage : interrupteur pour un booléen, champ de texte sinon. */
@Composable
internal fun SettingInput(key: String, value: String, error: FieldError?, onChange: (String) -> Unit) {
    val field = SettingsForm.field(key)
    val text = TEXTS.getValue(key)
    if (field.kind == FieldKind.BOOL) {
        SwitchRow(value == "true", stringResource(text.label)) { onChange(it.toString()) }
        return
    }
    val help = text.help?.let { stringResource(it) }
    val message = error?.let { errorMessage(it, field.minExclusive) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(text.label)) },
        isError = error != null,
        supportingText = (message ?: help)?.let { { Text(it) } },
        singleLine = field.kind != FieldKind.TEXT,
        keyboardOptions = when (field.kind) {
            FieldKind.INT -> KeyboardOptions(keyboardType = KeyboardType.Number)
            FieldKind.DECIMAL -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
            else -> KeyboardOptions.Default
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Interrupteur MD3 dont toute la ligne est cliquable (48 dp, lu comme un seul contrôle). */
@Composable
internal fun SwitchRow(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun errorMessage(error: FieldError, minExclusive: Boolean): String = when (error.kind) {
    FieldErrorKind.REQUIRED -> stringResource(Res.string.settings_error_required)
    FieldErrorKind.NOT_INTEGER -> stringResource(Res.string.settings_error_integer)
    FieldErrorKind.NOT_NUMBER -> stringResource(Res.string.settings_error_number)
    FieldErrorKind.TOO_SMALL ->
        stringResource(if (minExclusive) Res.string.settings_error_min_exclusive else Res.string.settings_error_min, error.bound.orEmpty())
    FieldErrorKind.TOO_LARGE -> stringResource(Res.string.settings_error_max, error.bound.orEmpty())
    FieldErrorKind.INVALID_DATE -> stringResource(Res.string.settings_error_date, error.bound.orEmpty())
    FieldErrorKind.INVALID_COLOR -> stringResource(Res.string.settings_error_color, error.bound.orEmpty())
}
