package com.skohscripts.kairos.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.guide.Tip
import com.skohscripts.kairos.core.guide.TipId
import com.skohscripts.kairos.ui.app.uiLanguage
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.tip_best_hours_body
import com.skohscripts.kairos.ui.generated.resources.tip_best_hours_title
import com.skohscripts.kairos.ui.generated.resources.tip_calendar_import_body
import com.skohscripts.kairos.ui.generated.resources.tip_calendar_import_title
import com.skohscripts.kairos.ui.generated.resources.tip_daily_summary_body
import com.skohscripts.kairos.ui.generated.resources.tip_daily_summary_title
import com.skohscripts.kairos.ui.generated.resources.tip_dark_theme_body
import com.skohscripts.kairos.ui.generated.resources.tip_dark_theme_title
import com.skohscripts.kairos.ui.generated.resources.tip_data_safety_body
import com.skohscripts.kairos.ui.generated.resources.tip_data_safety_title
import com.skohscripts.kairos.ui.generated.resources.tip_later
import com.skohscripts.kairos.ui.generated.resources.tip_never
import com.skohscripts.kairos.ui.generated.resources.tip_recap_body
import com.skohscripts.kairos.ui.generated.resources.tip_recap_title
import com.skohscripts.kairos.ui.generated.resources.tip_recurrence_body
import com.skohscripts.kairos.ui.generated.resources.tip_recurrence_title
import com.skohscripts.kairos.ui.generated.resources.tip_reminders_body
import com.skohscripts.kairos.ui.generated.resources.tip_reminders_fixed
import com.skohscripts.kairos.ui.generated.resources.tip_reminders_title
import com.skohscripts.kairos.ui.generated.resources.tip_shortcuts_body
import com.skohscripts.kairos.ui.generated.resources.tip_shortcuts_title
import com.skohscripts.kairos.ui.generated.resources.tip_show
import com.skohscripts.kairos.ui.generated.resources.tip_stats_calibration_body
import com.skohscripts.kairos.ui.generated.resources.tip_stats_calibration_title
import com.skohscripts.kairos.ui.generated.resources.tip_stats_correct_body
import com.skohscripts.kairos.ui.generated.resources.tip_stats_correct_points
import com.skohscripts.kairos.ui.generated.resources.tip_stats_correct_title
import com.skohscripts.kairos.ui.generated.resources.tip_stats_first_body
import com.skohscripts.kairos.ui.generated.resources.tip_stats_first_title
import com.skohscripts.kairos.ui.generated.resources.tip_stats_hours_body
import com.skohscripts.kairos.ui.generated.resources.tip_stats_hours_title
import com.skohscripts.kairos.ui.generated.resources.tip_team_exchanges_body
import com.skohscripts.kairos.ui.generated.resources.tip_team_exchanges_title
import com.skohscripts.kairos.ui.generated.resources.tip_team_forecast_body
import com.skohscripts.kairos.ui.generated.resources.tip_team_forecast_title
import com.skohscripts.kairos.ui.generated.resources.tip_team_load_body
import com.skohscripts.kairos.ui.generated.resources.tip_team_load_title
import com.skohscripts.kairos.ui.generated.resources.tip_team_signals_body
import com.skohscripts.kairos.ui.generated.resources.tip_team_signals_title
import com.skohscripts.kairos.ui.generated.resources.tip_team_suggest_body
import com.skohscripts.kairos.ui.generated.resources.tip_team_suggest_title
import com.skohscripts.kairos.ui.generated.resources.tip_timer_body
import com.skohscripts.kairos.ui.generated.resources.tip_timer_limits_body
import com.skohscripts.kairos.ui.generated.resources.tip_timer_limits_title
import com.skohscripts.kairos.ui.generated.resources.tip_timer_title
import com.skohscripts.kairos.ui.generated.resources.tip_week_review_body
import com.skohscripts.kairos.ui.generated.resources.tip_week_review_title
import com.skohscripts.kairos.ui.generated.resources.tip_widgets_body
import com.skohscripts.kairos.ui.generated.resources.tip_widgets_title
import com.skohscripts.kairos.ui.generated.resources.tip_work_hours_body
import com.skohscripts.kairos.ui.generated.resources.tip_work_hours_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.team.forecast.decimal1
import com.skohscripts.kairos.ui.team.forecast.percentLabel
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import com.skohscripts.kairos.ui.generated.resources.tip_habit_suggest_title
import com.skohscripts.kairos.ui.generated.resources.tip_habit_suggest_body
import kotlinx.datetime.LocalTime
import com.skohscripts.kairos.ui.day.Dates

/** Balise de test du dialogue d'un conseil. */
const val TIP_DIALOG_TAG = "tip-dialog"

/**
 * Ce que l'interface sait d'un conseil (docs/spec/conseils.md § Interface) : où « Montre-moi » mène, son icône (celle de la
 * carte du catalogue qu'il fait découvrir, `TipId.card`) et son titre. `TipsCatalogTest` vérifie que la cible ouvre le même
 * écran que la carte.
 */
object TipContent {
    fun target(id: TipId): GuideTarget = when (id) {
        TipId.DATA_SAFETY -> GuideTarget.Settings(GuideSection.SETTINGS_DATA)
        TipId.STATS_FIRST -> GuideTarget.Personal(Destination.STATS)
        TipId.STATS_CALIBRATION -> GuideTarget.Personal(Destination.STATS, GuideSection.STATS_CALIBRATION)
        TipId.STATS_CORRECT -> GuideTarget.Personal(Destination.STATS, GuideSection.STATS_CORRECT)
        TipId.STATS_HOURS -> GuideTarget.Personal(Destination.STATS, GuideSection.STATS_HOURS)
        TipId.WEEK_REVIEW -> GuideTarget.Personal(Destination.WEEK, weeksBack = 1)
        TipId.WORK_HOURS -> GuideTarget.Settings(GuideSection.SETTINGS_HOURS)
        TipId.BEST_HOURS -> GuideTarget.Settings(GuideSection.SETTINGS_BEST_HOURS)
        TipId.REMINDERS, TipId.DAILY_SUMMARY -> GuideTarget.Settings(GuideSection.SETTINGS_REMINDERS)
        TipId.TIMER_LIMITS -> GuideTarget.Settings(GuideSection.SETTINGS_CHRONO)
        TipId.CALENDAR_IMPORT -> GuideTarget.Settings(GuideSection.SETTINGS_DATA)
        TipId.DARK_THEME -> GuideTarget.Settings(GuideSection.SETTINGS_APPEARANCE)
        TipId.TIMER -> GuideTarget.Personal(Destination.DAY, GuideSection.DAY_NOW)
        TipId.RECURRENCE -> GuideTarget.RecurringTask
        // Sans série (repli) : l'accompagnement de la première habitude ; la coquille ouvre la fiche de la série proposée.
        TipId.HABIT_SUGGEST -> GuideTarget.Habit()
        TipId.RECAP -> GuideTarget.Personal(Destination.WEEK)
        TipId.WIDGETS -> GuideTarget.Card(TipId.WIDGETS.card)
        TipId.SHORTCUTS -> GuideTarget.Card(TipId.SHORTCUTS.card)
        TipId.TEAM_SIGNALS -> GuideTarget.Team(TeamDestination.BOARD)
        TipId.TEAM_LOAD -> GuideTarget.Team(TeamDestination.MEMBERS)
        TipId.TEAM_FORECAST -> GuideTarget.Team(TeamDestination.FORECAST)
        TipId.TEAM_SUGGEST -> GuideTarget.Team(TeamDestination.BACKLOG)
        TipId.TEAM_EXCHANGES -> GuideTarget.Team(TeamDestination.MEMBERS, exchanges = true)
    }

    /** L'icône de la carte du catalogue que le conseil fait découvrir. */
    fun icon(id: TipId): ImageVector = FeatureCatalog.cards.firstOrNull { it.id == id.card }?.icon?.invoke() ?: KairosIcons.Lightbulb

    fun title(id: TipId): StringResource = when (id) {
        TipId.DATA_SAFETY -> Res.string.tip_data_safety_title
        TipId.STATS_FIRST -> Res.string.tip_stats_first_title
        TipId.STATS_CALIBRATION -> Res.string.tip_stats_calibration_title
        TipId.STATS_CORRECT -> Res.string.tip_stats_correct_title
        TipId.STATS_HOURS -> Res.string.tip_stats_hours_title
        TipId.WEEK_REVIEW -> Res.string.tip_week_review_title
        TipId.WORK_HOURS -> Res.string.tip_work_hours_title
        TipId.BEST_HOURS -> Res.string.tip_best_hours_title
        TipId.REMINDERS -> Res.string.tip_reminders_title
        TipId.DAILY_SUMMARY -> Res.string.tip_daily_summary_title
        TipId.TIMER_LIMITS -> Res.string.tip_timer_limits_title
        TipId.CALENDAR_IMPORT -> Res.string.tip_calendar_import_title
        TipId.DARK_THEME -> Res.string.tip_dark_theme_title
        TipId.TIMER -> Res.string.tip_timer_title
        TipId.RECURRENCE -> Res.string.tip_recurrence_title
        TipId.HABIT_SUGGEST -> Res.string.tip_habit_suggest_title
        TipId.RECAP -> Res.string.tip_recap_title
        TipId.WIDGETS -> Res.string.tip_widgets_title
        TipId.SHORTCUTS -> Res.string.tip_shortcuts_title
        TipId.TEAM_SIGNALS -> Res.string.tip_team_signals_title
        TipId.TEAM_LOAD -> Res.string.tip_team_load_title
        TipId.TEAM_FORECAST -> Res.string.tip_team_forecast_title
        TipId.TEAM_SUGGEST -> Res.string.tip_team_suggest_title
        TipId.TEAM_EXCHANGES -> Res.string.tip_team_exchanges_title
    }
}

/** Le texte d'un conseil, avec les chiffres de l'utilisateur (nombres et rapports dans la langue de l'interface). */
@Composable
fun tipBody(tip: Tip): String {
    val language = uiLanguage()
    val ratio = tip.ratio?.let { decimal1(it, language) }.orEmpty()
    val name = tip.name.orEmpty()
    return when (tip.id) {
        TipId.DATA_SAFETY -> stringResource(Res.string.tip_data_safety_body, tip.count)
        TipId.STATS_FIRST -> stringResource(Res.string.tip_stats_first_body, tip.count)
        TipId.STATS_CALIBRATION -> stringResource(Res.string.tip_stats_calibration_body, tip.count)
        TipId.STATS_CORRECT -> if (tip.variant == 1) {
            stringResource(Res.string.tip_stats_correct_points, tip.count, tip.level ?: 0, ratio)
        } else {
            stringResource(Res.string.tip_stats_correct_body, tip.count, name, ratio)
        }
        TipId.STATS_HOURS -> stringResource(Res.string.tip_stats_hours_body, name)
        TipId.WEEK_REVIEW -> stringResource(Res.string.tip_week_review_body, tip.count)
        TipId.WORK_HOURS -> stringResource(Res.string.tip_work_hours_body, tip.count)
        TipId.BEST_HOURS -> stringResource(Res.string.tip_best_hours_body, name)
        TipId.REMINDERS -> stringResource(if (tip.variant == 1) Res.string.tip_reminders_fixed else Res.string.tip_reminders_body, tip.count)
        TipId.DAILY_SUMMARY -> stringResource(Res.string.tip_daily_summary_body)
        TipId.TIMER_LIMITS -> stringResource(Res.string.tip_timer_limits_body, tip.count)
        TipId.CALENDAR_IMPORT -> stringResource(Res.string.tip_calendar_import_body, tip.count)
        TipId.DARK_THEME -> stringResource(Res.string.tip_dark_theme_body)
        TipId.TIMER -> stringResource(Res.string.tip_timer_body, tip.count)
        TipId.RECURRENCE -> stringResource(Res.string.tip_recurrence_body, name, tip.count)
        TipId.HABIT_SUGGEST -> stringResource(Res.string.tip_habit_suggest_body, name, Dates.field(LocalTime(tip.count / 60 % 24, tip.count % 60)))
        TipId.RECAP -> stringResource(Res.string.tip_recap_body)
        TipId.WIDGETS -> stringResource(Res.string.tip_widgets_body)
        TipId.SHORTCUTS -> stringResource(Res.string.tip_shortcuts_body)
        TipId.TEAM_SIGNALS -> stringResource(Res.string.tip_team_signals_body, tip.count)
        TipId.TEAM_LOAD -> stringResource(Res.string.tip_team_load_body, name, percentLabel((tip.ratio ?: 0.0).roundToInt()))
        TipId.TEAM_FORECAST -> stringResource(Res.string.tip_team_forecast_body, tip.count)
        TipId.TEAM_SUGGEST -> stringResource(Res.string.tip_team_suggest_body, tip.count)
        TipId.TEAM_EXCHANGES -> stringResource(Res.string.tip_team_exchanges_body, tip.count)
    }
}

/**
 * Le dialogue d'un conseil (docs/spec/conseils.md, comportement attendu point 1) : icône, titre, texte avec les chiffres de
 * l'utilisateur, et trois boutons empilés (lisibles à 360 dp) : « Montre-moi » ([onShow]), « Plus tard » ([onLater]) et
 * « Ne plus me le proposer » ([onNever]). Fermer autrement (clic à côté, retour, Échap) vaut « Plus tard ».
 */
@Composable
fun TipDialog(tip: Tip, onShow: () -> Unit, onLater: () -> Unit, onNever: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        modifier = Modifier.testTag(TIP_DIALOG_TAG),
        icon = { Icon(TipContent.icon(tip.id), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
        title = { Text(stringResource(TipContent.title(tip.id))) },
        text = { Text(tipBody(tip), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
                KairosButton(onClick = onShow) { Text(stringResource(Res.string.tip_show)) }
                KairosTextButton(onClick = onLater) { Text(stringResource(Res.string.tip_later)) }
                KairosTextButton(onClick = onNever) { Text(stringResource(Res.string.tip_never)) }
            }
        },
    )
}
