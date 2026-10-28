package com.skohscripts.kairos.ui.settings

import com.skohscripts.kairos.ui.guide.GuideSection
import com.skohscripts.kairos.ui.guide.GuideSpot
import com.skohscripts.kairos.ui.app.tabLeavesField
import com.skohscripts.kairos.ui.app.heading
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import com.skohscripts.kairos.ui.app.expandedState
import com.skohscripts.kairos.ui.generated.resources.setting_learning_enabled
import com.skohscripts.kairos.ui.generated.resources.setting_learning_enabled_help
import com.skohscripts.kairos.ui.generated.resources.setting_learning_history
import com.skohscripts.kairos.ui.generated.resources.setting_learning_history_help
import com.skohscripts.kairos.ui.generated.resources.setting_learning_min
import com.skohscripts.kairos.ui.generated.resources.setting_learning_min_help
import com.skohscripts.kairos.ui.generated.resources.settings_section_learning
import com.skohscripts.kairos.ui.generated.resources.settings_section_learning_body
import com.skohscripts.kairos.ui.generated.resources.settings_advanced_body
import com.skohscripts.kairos.ui.generated.resources.settings_advanced_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosTextButton
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.settings.FieldError
import com.skohscripts.kairos.core.settings.FieldErrorKind
import com.skohscripts.kairos.core.settings.FieldKind
import com.skohscripts.kairos.core.settings.SettingsForm
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.setting_reminders_next
import com.skohscripts.kairos.ui.generated.resources.setting_reminders_summary
import com.skohscripts.kairos.ui.generated.resources.setting_reminders_summary_time
import com.skohscripts.kairos.ui.generated.resources.setting_reminders_summary_time_help
import com.skohscripts.kairos.ui.generated.resources.setting_reminders_lead_help
import com.skohscripts.kairos.ui.generated.resources.setting_reminders_lead
import com.skohscripts.kairos.ui.generated.resources.setting_reminders_enabled
import com.skohscripts.kairos.ui.generated.resources.settings_reminders_body
import com.skohscripts.kairos.ui.generated.resources.settings_reminders_title
import com.skohscripts.kairos.ui.generated.resources.settings_chrono_body
import com.skohscripts.kairos.ui.generated.resources.settings_chrono_title
import com.skohscripts.kairos.ui.generated.resources.settings_error_color
import com.skohscripts.kairos.ui.generated.resources.settings_error_time
import com.skohscripts.kairos.ui.generated.resources.settings_error_date
import com.skohscripts.kairos.ui.generated.resources.settings_error_integer
import com.skohscripts.kairos.ui.generated.resources.settings_error_max
import com.skohscripts.kairos.ui.generated.resources.settings_error_min
import com.skohscripts.kairos.ui.generated.resources.settings_error_min_exclusive
import com.skohscripts.kairos.ui.generated.resources.settings_error_number
import com.skohscripts.kairos.ui.generated.resources.settings_error_context
import com.skohscripts.kairos.ui.generated.resources.settings_error_duplicate
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
import com.skohscripts.kairos.ui.generated.resources.setting_team_history_weeks
import com.skohscripts.kairos.ui.generated.resources.setting_team_history_weeks_help
import com.skohscripts.kairos.ui.generated.resources.setting_team_min_samples
import com.skohscripts.kairos.ui.generated.resources.setting_team_min_samples_help
import com.skohscripts.kairos.ui.generated.resources.setting_team_risk
import com.skohscripts.kairos.ui.generated.resources.setting_team_risk_help
import com.skohscripts.kairos.ui.generated.resources.setting_team_sim_runs
import com.skohscripts.kairos.ui.generated.resources.setting_team_sim_runs_help
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
import com.skohscripts.kairos.ui.theme.KairosSpacing
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
    "learning.enabled" to FieldText(Res.string.setting_learning_enabled, Res.string.setting_learning_enabled_help),
    "learning.history" to FieldText(Res.string.setting_learning_history, Res.string.setting_learning_history_help),
    "learning.minSamples" to FieldText(Res.string.setting_learning_min, Res.string.setting_learning_min_help),
    "timerIdleAlertMinutes" to FieldText(Res.string.settings_idle),
    "pomodoroFocusMinutes" to FieldText(Res.string.settings_pomodoro),
    "timerAlertSound" to FieldText(Res.string.settings_sound),
    "reminders.enabled" to FieldText(Res.string.setting_reminders_enabled),
    "reminders.leadMinutes" to FieldText(Res.string.setting_reminders_lead, Res.string.setting_reminders_lead_help),
    "reminders.nextTask" to FieldText(Res.string.setting_reminders_next),
    "reminders.dailySummary" to FieldText(Res.string.setting_reminders_summary),
    "reminders.dailySummaryTime" to FieldText(Res.string.setting_reminders_summary_time, Res.string.setting_reminders_summary_time_help),
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
    "team.simulationRuns" to FieldText(Res.string.setting_team_sim_runs, Res.string.setting_team_sim_runs_help),
    "team.historyWeeks" to FieldText(Res.string.setting_team_history_weeks, Res.string.setting_team_history_weeks_help),
    "team.minSamples" to FieldText(Res.string.setting_team_min_samples, Res.string.setting_team_min_samples_help),
    "team.deadlineRiskPercent" to FieldText(Res.string.setting_team_risk, Res.string.setting_team_risk_help),
)

/** Sections du formulaire, dans l'ordre de l'écran ; chacune est rangée dans la catégorie de ses champs. */
private class Section(
    val title: StringResource,
    val body: StringResource?,
    val icon: ImageVector,
    val keys: List<String>,
    val advanced: Boolean = false,
) {
    val category: SettingsCategory get() = SettingsCategory.of(keys.first())
}

private val WORKDAY = Section(
    Res.string.settings_section_day, null, KairosIcons.Work,
    listOf("defaultTaskDurationMinutes", "meetingBufferMinutes", "workdayStartHour", "workdayEndHour"),
)
private val HOLIDAYS = Section(Res.string.settings_section_holidays, Res.string.settings_section_holidays_body, KairosIcons.EventBusy, listOf("holidaysFr", "extraHolidays"))
private val TYPES = Section(Res.string.settings_section_types, null, KairosIcons.Label, listOf("taskTypes"))
private val LEARNING = Section(
    Res.string.settings_section_learning, Res.string.settings_section_learning_body, KairosIcons.History,
    listOf("learning.enabled", "learning.history", "learning.minSamples"),
)
private val GUARDS = Section(Res.string.settings_section_guards, null, KairosIcons.Shield, listOf("staleOverdueDays", "staleUntouchedDays", "priorityOverloadThreshold"))
private val CHRONO = Section(
    Res.string.settings_chrono_title, Res.string.settings_chrono_body, KairosIcons.NotificationsActive,
    listOf("timerIdleAlertMinutes", "pomodoroFocusMinutes", "timerAlertSound"),
)
private val REMINDERS = Section(
    Res.string.settings_reminders_title, Res.string.settings_reminders_body, KairosIcons.Alarm,
    listOf("reminders.enabled", "reminders.leadMinutes", "reminders.nextTask", "reminders.dailySummary", "reminders.dailySummaryTime"),
)
private val STATS = Section(Res.string.settings_section_stats, null, KairosIcons.BarChart, listOf("statsWindowWeeks"))

// « Avancé : comment les tâches sont ordonnées » : replié, rien à régler pour s'en servir (docs/spec/planning-personnalise.md).
private val ADVANCED = listOf(
    Section(
        Res.string.settings_section_wsjf, Res.string.settings_section_wsjf_body, KairosIcons.Leaderboard,
        listOf("priorityValueBase", "urgencyHorizonDays", "urgencyPeak", "defaultFibonacciPoints"), advanced = true,
    ),
    Section(
        Res.string.settings_section_dip,
        Res.string.settings_section_dip_body,
        KairosIcons.Bedtime,
        listOf("cognitiveDipEnabled", "cognitiveDipStartHour", "cognitiveDipTroughHour", "cognitiveDipEndHour", "cognitiveDipPenalty"),
        advanced = true,
    ),
)

/**
 * Carte de section des Réglages (docs/spec/densite.md § Réglages) : carte
 * « filled » (`surfaceContainerLow`), sans contour ni ombre, marge intérieure
 * [KairosSpacing.l]. Les champs, qui portent leur propre contour, s'y
 * détachent sans empiler un second contour autour d'eux. [spacing] sépare les
 * éléments de la carte : `l` entre champs, `m` pour une carte de texte et de
 * boutons.
 */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    spacing: Dp = KairosSpacing.l,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(KairosSpacing.l), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/** Carte de section cliquable (« À propos et guide ») : mêmes couleurs, sans marge intérieure (la ligne a la sienne). */
@Composable
internal fun SettingsClickableCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

/**
 * Titre de carte en `titleMedium` (annoncé comme titre) précédé de son icône (décorative, neutre : la charte réserve
 * la couleur) et, facultative, sa phrase d'introduction dessous sur toute la largeur, groupés (`s`).
 */
@Composable
internal fun SettingsCardHeader(icon: ImageVector, title: String, body: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
        }
        body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/**
 * Les cartes du formulaire d'une catégorie des Réglages (docs/spec/reglages.md) : une carte « filled » par section,
 * un champ par réglage, l'erreur du champ à la place de son aide. L'état (textes, erreurs) appartient à l'écran.
 * Les catégories Général, Équipe, Données et À propos n'ont pas de section ici : l'écran y pose ses propres cartes.
 */
@Composable
internal fun SettingsFormCards(
    category: SettingsCategory,
    values: Map<String, String>,
    errors: Map<String, FieldError>,
    onChange: (String, String) -> Unit,
) {
    when (category) {
        SettingsCategory.DAY -> {
            GuideSpot(GuideSection.SETTINGS_WORKDAY) { SectionCard(WORKDAY, values, errors, onChange) }
            // Les horaires par jour suivent la journée de travail dont ils sont les exceptions (docs/spec/planning-personnalise.md § Lot B1).
            // Cartes que le guide sait montrer (docs/spec/visite-guidee.md, issue #95).
            GuideSpot(GuideSection.SETTINGS_HOURS) { ScheduleCard(values, errors, onChange) }
            GuideSpot(GuideSection.SETTINGS_CONTEXTS) { ContextsCard(values, errors, onChange) }
            GuideSpot(GuideSection.SETTINGS_HOLIDAYS) { SectionCard(HOLIDAYS, values, errors, onChange) }
        }
        SettingsCategory.PLANNING -> {
            SectionCard(TYPES, values, errors, onChange)
            // Les noms des niveaux sont du vocabulaire, comme les types (docs/spec/planning-personnalise.md § Lot D).
            GuideSpot(GuideSection.SETTINGS_LEVELS) { LevelNamesCard(values, errors, onChange) }
            // Les heures préférées se donnent par type : la carte suit les types.
            GuideSpot(GuideSection.SETTINGS_BEST_HOURS) { BestHoursCard(values, errors, onChange) }
            GuideSpot(GuideSection.SETTINGS_LEARNED) { SectionCard(LEARNING, values, errors, onChange) }
            SectionCard(GUARDS, values, errors, onChange)
            AdvancedGroup(values, errors, onChange)
        }
        SettingsCategory.TRACKING -> {
            SectionCard(CHRONO, values, errors, onChange)
            GuideSpot(GuideSection.SETTINGS_REMINDERS) { SectionCard(REMINDERS, values, errors, onChange) }
            SectionCard(STATS, values, errors, onChange)
        }
        else -> Unit
    }
}

@Composable
private fun SectionCard(section: Section, values: Map<String, String>, errors: Map<String, FieldError>, onChange: (String, String) -> Unit) {
    SettingsCard {
        SettingsCardHeader(section.icon, stringResource(section.title), section.body?.let { stringResource(it) })
        section.keys
            // Un champ qui ne sert qu'avec son interrupteur n'apparaît qu'avec lui (sauf s'il est en erreur).
            .filter { key -> SHOWN_WITH[key]?.let { values[it] == "true" } != false || key in errors }
            .forEach { key -> SettingInput(key, values[key].orEmpty(), errors[key]) { onChange(key, it) } }
    }
}

/** Champ → interrupteur dont il dépend : « À quelle heure » du récapitulatif n'apparaît que s'il est activé (docs/spec/rappels.md). */
private val SHOWN_WITH = mapOf("reminders.dailySummaryTime" to "reminders.dailySummary")

/**
 * « Avancé : comment les tâches sont ordonnées » : le score de priorité et le creux de l'après-midi, repliés. Ils
 * s'ouvrent d'office quand un de leurs champs est en erreur (un enregistrement refusé ne doit pas cacher le champ fautif).
 */
@Composable
private fun AdvancedGroup(values: Map<String, String>, errors: Map<String, FieldError>, onChange: (String, String) -> Unit) {
    val advanced = ADVANCED
    var userOpen by rememberSaveable { mutableStateOf(false) }
    val open = userOpen || advanced.any { section -> section.keys.any { it in errors } }
    SettingsCard {
        KairosTextButton(onClick = { userOpen = !open }, modifier = Modifier.fillMaxWidth().expandedState(open), contentPadding = KairosButtonIconPadding) {
            Icon(if (open) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(stringResource(Res.string.settings_advanced_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
                Text(stringResource(Res.string.settings_advanced_body), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (open) advanced.forEach { section -> SectionCard(section, values, errors, onChange) }
    }
}

/**
 * Où défiler après un enregistrement refusé : chaque champ s'y inscrit sous sa clé (`themeColor`, `team.name`…),
 * le bandeau des règles entre champs sous [GENERAL]. Sans cela, l'erreur pouvait être hors de l'écran (à 360 dp, le
 * bandeau est en tête de page, le champ fautif trois cartes plus bas) et le message « Réglages non enregistrés » ne
 * disait pas où regarder.
 */
internal class ErrorReveal {
    private val requesters = HashMap<String, BringIntoViewRequester>()

    fun requester(key: String): BringIntoViewRequester = requesters.getOrPut(key) { BringIntoViewRequester() }

    suspend fun reveal(key: String) = requester(key).bringIntoView()

    companion object {
        const val GENERAL = "_general"
    }
}

internal val LocalErrorReveal = compositionLocalOf { ErrorReveal() }

/** Un réglage : interrupteur pour un booléen, champ de texte sinon. */
@Composable
internal fun SettingInput(key: String, value: String, error: FieldError?, onChange: (String) -> Unit) {
    val field = SettingsForm.field(key)
    val reveal = LocalErrorReveal.current.requester(key)
    val text = TEXTS.getValue(key)
    if (field.kind == FieldKind.BOOL) {
        SwitchRow(value == "true", stringResource(text.label)) { onChange(it.toString()) }
        return
    }
    val help = text.help?.let { stringResource(it) }
    val message = error?.let { errorMessage(it, field.minExclusive) }
    // Champ multiligne : Tab change de champ au lieu d'insérer une tabulation (le parent lit la touche avant le champ).
    Box(if (field.kind == FieldKind.TEXT) Modifier.tabLeavesField() else Modifier) {
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
            modifier = if (field.kind == FieldKind.INT || field.kind == FieldKind.DECIMAL) {
                // widthIn AVANT fillMaxWidth : l'ordre inverse laisse la contrainte entrante (pleine largeur) l'emporter sur la borne.
                Modifier.widthIn(max = NUMBER_FIELD_MAX_WIDTH).fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            }.bringIntoViewRequester(reveal),
        )
    }
}

/**
 * Largeur maximale d'un champ numérique (un nombre à quelques chiffres n'a pas
 * à s'étirer sur 700 dp) ; son aide, `supportingText`, suit la même largeur. Les
 * champs de texte libre et les interrupteurs gardent la pleine largeur.
 */
internal val NUMBER_FIELD_MAX_WIDTH = 320.dp

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
internal fun errorMessage(error: FieldError, minExclusive: Boolean): String = when (error.kind) {
    FieldErrorKind.REQUIRED -> stringResource(Res.string.settings_error_required)
    FieldErrorKind.NOT_INTEGER -> stringResource(Res.string.settings_error_integer)
    FieldErrorKind.NOT_NUMBER -> stringResource(Res.string.settings_error_number)
    FieldErrorKind.TOO_SMALL ->
        stringResource(if (minExclusive) Res.string.settings_error_min_exclusive else Res.string.settings_error_min, error.bound.orEmpty())
    FieldErrorKind.TOO_LARGE -> stringResource(Res.string.settings_error_max, error.bound.orEmpty())
    FieldErrorKind.INVALID_DATE -> stringResource(Res.string.settings_error_date, error.bound.orEmpty())
    FieldErrorKind.INVALID_COLOR -> stringResource(Res.string.settings_error_color, error.bound.orEmpty())
    FieldErrorKind.INVALID_TIME -> stringResource(Res.string.settings_error_time, error.bound.orEmpty())
    FieldErrorKind.DUPLICATE -> stringResource(Res.string.settings_error_duplicate, error.bound.orEmpty())
    FieldErrorKind.UNKNOWN_CONTEXT -> stringResource(Res.string.settings_error_context, error.bound.orEmpty())
}
