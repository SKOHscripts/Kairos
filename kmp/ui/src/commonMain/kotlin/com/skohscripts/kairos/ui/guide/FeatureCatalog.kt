package com.skohscripts.kairos.ui.guide

import androidx.compose.ui.graphics.vector.ImageVector
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.card_shortcuts_title
import com.skohscripts.kairos.ui.generated.resources.card_shortcuts_body
import com.skohscripts.kairos.ui.generated.resources.card_placement_title
import com.skohscripts.kairos.ui.generated.resources.card_placement_body
import com.skohscripts.kairos.ui.generated.resources.card_afternoon_dip_title
import com.skohscripts.kairos.ui.generated.resources.card_afternoon_dip_body
import com.skohscripts.kairos.ui.generated.resources.card_holidays_title
import com.skohscripts.kairos.ui.generated.resources.card_holidays_body
import com.skohscripts.kairos.ui.generated.resources.card_timeline_title
import com.skohscripts.kairos.ui.generated.resources.card_timeline_body
import com.skohscripts.kairos.ui.generated.resources.card_alerts_title
import com.skohscripts.kairos.ui.generated.resources.card_alerts_body
import com.skohscripts.kairos.ui.generated.resources.card_subtasks_edit_title
import com.skohscripts.kairos.ui.generated.resources.card_subtasks_edit_body
import com.skohscripts.kairos.ui.generated.resources.card_snooze_title
import com.skohscripts.kairos.ui.generated.resources.card_snooze_body
import com.skohscripts.kairos.ui.generated.resources.card_deep_work_title
import com.skohscripts.kairos.ui.generated.resources.card_deep_work_body
import com.skohscripts.kairos.ui.generated.resources.card_phone_calendars_title
import com.skohscripts.kairos.ui.generated.resources.card_phone_calendars_body
import com.skohscripts.kairos.ui.generated.resources.card_team_first_task_title
import com.skohscripts.kairos.ui.generated.resources.card_team_first_task_body
import com.skohscripts.kairos.ui.generated.resources.card_team_my_work_title
import com.skohscripts.kairos.ui.generated.resources.card_team_my_work_body
import com.skohscripts.kairos.ui.generated.resources.card_team_load_title
import com.skohscripts.kairos.ui.generated.resources.card_team_load_body
import com.skohscripts.kairos.ui.generated.resources.card_theme_color_title
import com.skohscripts.kairos.ui.generated.resources.card_theme_color_body
import com.skohscripts.kairos.ui.generated.resources.card_web_version_title
import com.skohscripts.kairos.ui.generated.resources.card_web_version_body
import com.skohscripts.kairos.ui.generated.resources.card_guide_memory_title
import com.skohscripts.kairos.ui.generated.resources.card_guide_memory_body
import com.skohscripts.kairos.ui.generated.resources.card_week_move_title
import com.skohscripts.kairos.ui.generated.resources.card_week_move_body
import com.skohscripts.kairos.ui.generated.resources.card_stats_explore_title
import com.skohscripts.kairos.ui.generated.resources.card_stats_explore_body
import com.skohscripts.kairos.ui.generated.resources.card_holidays_slots_title
import com.skohscripts.kairos.ui.generated.resources.card_holidays_slots_body
import com.skohscripts.kairos.ui.generated.resources.card_slots_month_body
import com.skohscripts.kairos.ui.generated.resources.card_calendar_import_body
import com.skohscripts.kairos.ui.generated.resources.card_calendar_import_title
import com.skohscripts.kairos.ui.generated.resources.card_linked_file_body
import com.skohscripts.kairos.ui.generated.resources.card_linked_file_title
import com.skohscripts.kairos.ui.generated.resources.card_month_body
import com.skohscripts.kairos.ui.generated.resources.card_correct_body
import com.skohscripts.kairos.ui.generated.resources.card_trends_body
import com.skohscripts.kairos.ui.generated.resources.card_reminders_body
import com.skohscripts.kairos.ui.generated.resources.card_besthours_eff_body
import com.skohscripts.kairos.ui.generated.resources.card_besthours_eff_title
import com.skohscripts.kairos.ui.generated.resources.card_guided_walks_body
import com.skohscripts.kairos.ui.generated.resources.card_guided_walks_title
import com.skohscripts.kairos.ui.generated.resources.card_languages_body
import com.skohscripts.kairos.ui.generated.resources.card_languages_title
import com.skohscripts.kairos.ui.generated.resources.card_level_names_body
import com.skohscripts.kairos.ui.generated.resources.card_level_names_title
import com.skohscripts.kairos.ui.generated.resources.card_reminders_title
import com.skohscripts.kairos.ui.generated.resources.card_trends_title
import com.skohscripts.kairos.ui.generated.resources.card_correct_title
import com.skohscripts.kairos.ui.generated.resources.card_month_title
import com.skohscripts.kairos.ui.generated.resources.card_slots_month_title
import com.skohscripts.kairos.ui.generated.resources.card_settings_categories_title
import com.skohscripts.kairos.ui.generated.resources.card_settings_categories_body
import com.skohscripts.kairos.ui.generated.resources.card_darktheme_title
import com.skohscripts.kairos.ui.generated.resources.card_daily_summary_body
import com.skohscripts.kairos.ui.generated.resources.card_daily_summary_title
import com.skohscripts.kairos.ui.generated.resources.card_darktheme_body
import com.skohscripts.kairos.ui.generated.resources.card_besthours_apply_body
import com.skohscripts.kairos.ui.generated.resources.card_besthours_apply_title
import com.skohscripts.kairos.ui.generated.resources.card_besthours_body
import com.skohscripts.kairos.ui.generated.resources.card_besthours_title
import com.skohscripts.kairos.ui.generated.resources.card_contexts_body
import com.skohscripts.kairos.ui.generated.resources.card_contexts_title
import com.skohscripts.kairos.ui.generated.resources.card_dependencies_body
import com.skohscripts.kairos.ui.generated.resources.card_dependencies_title
import com.skohscripts.kairos.ui.generated.resources.card_discover_body
import com.skohscripts.kairos.ui.generated.resources.card_demo_title
import com.skohscripts.kairos.ui.generated.resources.card_demo_body
import com.skohscripts.kairos.ui.generated.resources.card_discover_title
import com.skohscripts.kairos.ui.generated.resources.card_first_task_body
import com.skohscripts.kairos.ui.generated.resources.card_first_task_title
import com.skohscripts.kairos.ui.generated.resources.card_hours_body
import com.skohscripts.kairos.ui.generated.resources.card_hours_title
import com.skohscripts.kairos.ui.generated.resources.card_learned_body
import com.skohscripts.kairos.ui.generated.resources.card_learned_title
import com.skohscripts.kairos.ui.generated.resources.card_points_body
import com.skohscripts.kairos.ui.generated.resources.card_points_title
import com.skohscripts.kairos.ui.generated.resources.card_recurrence_body
import com.skohscripts.kairos.ui.generated.resources.card_recurrence_title
import com.skohscripts.kairos.ui.generated.resources.card_skipped_months_body
import com.skohscripts.kairos.ui.generated.resources.card_skipped_months_title
import com.skohscripts.kairos.ui.generated.resources.card_score_body
import com.skohscripts.kairos.ui.generated.resources.card_score_title
import com.skohscripts.kairos.ui.generated.resources.card_settings_body
import com.skohscripts.kairos.ui.generated.resources.card_settings_title
import com.skohscripts.kairos.ui.generated.resources.card_slots_body
import com.skohscripts.kairos.ui.generated.resources.card_slots_title
import com.skohscripts.kairos.ui.generated.resources.card_subtasks_body
import com.skohscripts.kairos.ui.generated.resources.card_subtasks_title
import com.skohscripts.kairos.ui.generated.resources.card_week_body
import com.skohscripts.kairos.ui.generated.resources.card_week_title
import com.skohscripts.kairos.ui.generated.resources.card_widgets_body
import com.skohscripts.kairos.ui.generated.resources.card_widgets_title
import com.skohscripts.kairos.ui.generated.resources.chapter_first_steps
import com.skohscripts.kairos.ui.generated.resources.chapter_follow
import com.skohscripts.kairos.ui.generated.resources.chapter_organize
import com.skohscripts.kairos.ui.generated.resources.chapter_plan
import com.skohscripts.kairos.ui.generated.resources.chapter_settings
import com.skohscripts.kairos.ui.generated.resources.chapter_team
import com.skohscripts.kairos.ui.generated.resources.guide_team_assign_body
import com.skohscripts.kairos.ui.generated.resources.guide_team_assign_title
import com.skohscripts.kairos.ui.generated.resources.guide_team_backlog_body
import com.skohscripts.kairos.ui.generated.resources.guide_team_backlog_title
import com.skohscripts.kairos.ui.generated.resources.guide_team_board_body
import com.skohscripts.kairos.ui.generated.resources.guide_team_board_title
import com.skohscripts.kairos.ui.generated.resources.guide_team_exchanges_body
import com.skohscripts.kairos.ui.generated.resources.guide_team_exchanges_title
import com.skohscripts.kairos.ui.generated.resources.guide_team_forecast_body
import com.skohscripts.kairos.ui.generated.resources.guide_team_forecast_title
import com.skohscripts.kairos.ui.generated.resources.guide_team_members_body
import com.skohscripts.kairos.ui.generated.resources.guide_team_members_title
import com.skohscripts.kairos.ui.generated.resources.guide_you_capture_body
import com.skohscripts.kairos.ui.generated.resources.guide_you_capture_title
import com.skohscripts.kairos.ui.generated.resources.guide_you_data_body
import com.skohscripts.kairos.ui.generated.resources.guide_you_data_title
import com.skohscripts.kairos.ui.generated.resources.guide_you_do_body
import com.skohscripts.kairos.ui.generated.resources.guide_you_do_title
import com.skohscripts.kairos.ui.generated.resources.guide_you_qualify_body
import com.skohscripts.kairos.ui.generated.resources.guide_you_qualify_title
import com.skohscripts.kairos.ui.generated.resources.guide_you_stats_body
import com.skohscripts.kairos.ui.generated.resources.guide_you_stats_title
import com.skohscripts.kairos.ui.generated.resources.guide_you_time_body
import com.skohscripts.kairos.ui.generated.resources.guide_you_time_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import org.jetbrains.compose.resources.StringResource

/** Les chapitres du hub « Découvrir Kairos » (docs/spec/visite-guidee.md). [TEAM] n'est proposé que si l'espace Équipe est activé. */
enum class GuideChapter(val title: StringResource, val icon: () -> ImageVector) {
    FIRST_STEPS(Res.string.chapter_first_steps, { KairosIcons.PlayArrow }),
    PLAN(Res.string.chapter_plan, { KairosIcons.Today }),
    FOLLOW(Res.string.chapter_follow, { KairosIcons.Schedule }),
    ORGANIZE(Res.string.chapter_organize, { KairosIcons.Layers }),
    TEAM(Res.string.chapter_team, { KairosIcons.Groups }),
    SETTINGS(Res.string.chapter_settings, { KairosIcons.Settings }),
}

/**
 * Une fonctionnalité de Kairos décrite une seule fois (docs/spec/visite-guidee.md § Catalogue piloté par les données) : la
 * visite, le hub « Découvrir Kairos » et la section « Nouveau dans cette version » de l'Accueil la lisent. [since] : version
 * de livraison (`"3.4.0"`) ; [tour] : l'étape fait partie de la visite initiale de l'espace Perso.
 */
data class FeatureCard(
    val id: String,
    val chapter: GuideChapter,
    val icon: () -> ImageVector,
    val title: StringResource,
    val body: StringResource,
    val target: GuideTarget,
    val since: String = "3.0.0",
    val tour: Boolean = false,
    /** Plateformes où la fonctionnalité existe (docs/spec/liste-fonctionnalites.md) ; toutes par défaut, pastille sinon. */
    val platforms: Set<Platform> = ALL_PLATFORMS,
) {
    /** La fonctionnalité n'existe pas partout : la liste et le README le disent. */
    val limited: Boolean get() = platforms != ALL_PLATFORMS

    companion object {
        val ALL_PLATFORMS: Set<Platform> = Platform.entries.toSet()
    }
}

/** Le catalogue, ses sélections et le lien avec le README. */
object FeatureCatalog {
    /**
     * Toutes les cartes, **dans l'ordre de la visite** : la visite initiale de l'espace Perso en est la sélection [FeatureCard.tour]
     * hors Équipe, celle de l'espace Équipe la sélection [FeatureCard.tour] du chapitre [GuideChapter.TEAM]. Les ids sont stables (ils relient la liste du README).
     */
    val cards: List<FeatureCard> = listOf(
        // --- Premiers pas
        FeatureCard("capture", GuideChapter.FIRST_STEPS, { KairosIcons.Notes }, Res.string.guide_you_capture_title, Res.string.guide_you_capture_body, GuideTarget.Personal(Destination.NOTES), tour = true),
        FeatureCard("shortcuts", GuideChapter.FIRST_STEPS, { KairosIcons.Keyboard }, Res.string.card_shortcuts_title, Res.string.card_shortcuts_body, GuideTarget.Personal(Destination.DAY), since = "3.0.0", platforms = setOf(Platform.DESKTOP, Platform.WEB)),
        FeatureCard("qualify", GuideChapter.FIRST_STEPS, { KairosIcons.Edit }, Res.string.guide_you_qualify_title, Res.string.guide_you_qualify_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_INBOX), tour = true),
        FeatureCard("do", GuideChapter.FIRST_STEPS, { KairosIcons.Today }, Res.string.guide_you_do_title, Res.string.guide_you_do_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_AGENDA), tour = true),
        FeatureCard("discover", GuideChapter.FIRST_STEPS, { KairosIcons.Layers }, Res.string.card_discover_title, Res.string.card_discover_body, GuideTarget.Personal(Destination.HOME), since = "3.4.0"),
        FeatureCard("first_task", GuideChapter.FIRST_STEPS, { KairosIcons.Add }, Res.string.card_first_task_title, Res.string.card_first_task_body, GuideTarget.FirstTask, since = "3.4.0"),
        FeatureCard("demo", GuideChapter.FIRST_STEPS, { KairosIcons.Science }, Res.string.card_demo_title, Res.string.card_demo_body, GuideTarget.Demo, since = "3.13.0"),
        FeatureCard("guide_memory", GuideChapter.FIRST_STEPS, { KairosIcons.Spot }, Res.string.card_guide_memory_title, Res.string.card_guide_memory_body, GuideTarget.Personal(Destination.HOME), since = "3.7.0"),
        FeatureCard("guided_walks", GuideChapter.FIRST_STEPS, { KairosIcons.Repeat }, Res.string.card_guided_walks_title, Res.string.card_guided_walks_body, GuideTarget.Personal(Destination.HOME), since = "3.9.0"),
        // --- Planifier
        FeatureCard("points", GuideChapter.PLAN, { KairosIcons.Balance }, Res.string.card_points_title, Res.string.card_points_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_INBOX)),
        FeatureCard("level_names", GuideChapter.PLAN, { KairosIcons.Flag }, Res.string.card_level_names_title, Res.string.card_level_names_body, GuideTarget.Settings(GuideSection.SETTINGS_LEVELS), since = "3.9.0"),
        FeatureCard("score", GuideChapter.PLAN, { KairosIcons.Star }, Res.string.card_score_title, Res.string.card_score_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_AGENDA)),
        FeatureCard("placement", GuideChapter.PLAN, { KairosIcons.Calculate }, Res.string.card_placement_title, Res.string.card_placement_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_AGENDA), since = "3.0.0"),
        FeatureCard("afternoon_dip", GuideChapter.PLAN, { KairosIcons.WbTwilight }, Res.string.card_afternoon_dip_title, Res.string.card_afternoon_dip_body, GuideTarget.Settings(), since = "3.0.0"),
        FeatureCard("contexts", GuideChapter.PLAN, { KairosIcons.Layers }, Res.string.card_contexts_title, Res.string.card_contexts_body, GuideTarget.Settings(GuideSection.SETTINGS_CONTEXTS), since = "3.5.0"),
        FeatureCard("besthours_apply", GuideChapter.PLAN, { KairosIcons.Schedule }, Res.string.card_besthours_apply_title, Res.string.card_besthours_apply_body, GuideTarget.Settings(GuideSection.SETTINGS_BEST_HOURS), since = "3.6.0"),
        FeatureCard("hours", GuideChapter.PLAN, { KairosIcons.Schedule }, Res.string.card_hours_title, Res.string.card_hours_body, GuideTarget.Settings(GuideSection.SETTINGS_HOURS), since = "3.4.0"),
        FeatureCard("holidays", GuideChapter.PLAN, { KairosIcons.EventAvailable }, Res.string.card_holidays_title, Res.string.card_holidays_body, GuideTarget.Settings(GuideSection.SETTINGS_HOLIDAYS), since = "3.0.0"),
        // --- Suivre
        FeatureCard("timeline", GuideChapter.FOLLOW, { KairosIcons.Timeline }, Res.string.card_timeline_title, Res.string.card_timeline_body, GuideTarget.Personal(Destination.DAY), since = "3.0.0"),
        FeatureCard("time", GuideChapter.FOLLOW, { KairosIcons.Schedule }, Res.string.guide_you_time_title, Res.string.guide_you_time_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_NOW), tour = true),
        FeatureCard("alerts", GuideChapter.FOLLOW, { KairosIcons.NotificationsActive }, Res.string.card_alerts_title, Res.string.card_alerts_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_NOW), since = "3.0.0"),
        FeatureCard("learned", GuideChapter.FOLLOW, { KairosIcons.TrendingUp }, Res.string.card_learned_title, Res.string.card_learned_body, GuideTarget.Settings(GuideSection.SETTINGS_LEARNED), since = "3.4.0"),
        FeatureCard("besthours", GuideChapter.FOLLOW, { KairosIcons.Schedule }, Res.string.card_besthours_title, Res.string.card_besthours_body, GuideTarget.Personal(Destination.STATS, GuideSection.STATS_HOURS), since = "3.5.0"),
        FeatureCard("besthours_efficiency", GuideChapter.FOLLOW, { KairosIcons.Timer }, Res.string.card_besthours_eff_title, Res.string.card_besthours_eff_body, GuideTarget.Personal(Destination.STATS, GuideSection.STATS_HOURS), since = "3.9.0"),
        // Widgets Android (docs/spec/widgets-android.md) : la carte mène à la vue Jour, qu'ils reprennent.
        FeatureCard("widgets", GuideChapter.FOLLOW, { KairosIcons.Today }, Res.string.card_widgets_title, Res.string.card_widgets_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_NOW), since = "3.12.0", platforms = setOf(Platform.ANDROID)),
        FeatureCard("week", GuideChapter.FOLLOW, { KairosIcons.DateRange }, Res.string.card_week_title, Res.string.card_week_body, GuideTarget.Personal(Destination.WEEK)),
        FeatureCard("month", GuideChapter.FOLLOW, { KairosIcons.CalendarMonth }, Res.string.card_month_title, Res.string.card_month_body, GuideTarget.Personal(Destination.WEEK), since = "3.8.0"),
        FeatureCard("week_move", GuideChapter.FOLLOW, { KairosIcons.SwapHoriz }, Res.string.card_week_move_title, Res.string.card_week_move_body, GuideTarget.Personal(Destination.WEEK), since = "3.7.0"),
        FeatureCard("stats", GuideChapter.FOLLOW, { KairosIcons.BarChart }, Res.string.guide_you_stats_title, Res.string.guide_you_stats_body, GuideTarget.Personal(Destination.STATS), tour = true),
        FeatureCard("stats_trends", GuideChapter.FOLLOW, { KairosIcons.ShowChart }, Res.string.card_trends_title, Res.string.card_trends_body, GuideTarget.Personal(Destination.STATS), since = "3.8.0"),
        FeatureCard("stats_correct", GuideChapter.FOLLOW, { KairosIcons.EditNote }, Res.string.card_correct_title, Res.string.card_correct_body, GuideTarget.Personal(Destination.STATS), since = "3.8.0"),
        FeatureCard("stats_explore", GuideChapter.FOLLOW, { KairosIcons.Download }, Res.string.card_stats_explore_title, Res.string.card_stats_explore_body, GuideTarget.Personal(Destination.STATS), since = "3.7.0"),
        // --- Organiser
        FeatureCard("subtasks", GuideChapter.ORGANIZE, { KairosIcons.Layers }, Res.string.card_subtasks_title, Res.string.card_subtasks_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_PARENTS)),
        FeatureCard("subtasks_edit", GuideChapter.ORGANIZE, { KairosIcons.Checklist }, Res.string.card_subtasks_edit_title, Res.string.card_subtasks_edit_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_PARENTS), since = "3.3.0"),
        FeatureCard("recurrence", GuideChapter.ORGANIZE, { KairosIcons.Repeat }, Res.string.card_recurrence_title, Res.string.card_recurrence_body, GuideTarget.Personal(Destination.DAY)),
        FeatureCard("skipped_months", GuideChapter.ORGANIZE, { KairosIcons.EventRepeat }, Res.string.card_skipped_months_title, Res.string.card_skipped_months_body, GuideTarget.Personal(Destination.DAY), since = "3.13.0"),
        FeatureCard("snooze", GuideChapter.ORGANIZE, { KairosIcons.Snooze }, Res.string.card_snooze_title, Res.string.card_snooze_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_AGENDA), since = "3.0.0"),
        FeatureCard("dependencies", GuideChapter.ORGANIZE, { KairosIcons.Block }, Res.string.card_dependencies_title, Res.string.card_dependencies_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_BLOCKED)),
        FeatureCard("slots", GuideChapter.ORGANIZE, { KairosIcons.PushPin }, Res.string.card_slots_title, Res.string.card_slots_body, GuideTarget.Personal(Destination.DAY)),
        FeatureCard("deep_work", GuideChapter.ORGANIZE, { KairosIcons.CenterFocusStrong }, Res.string.card_deep_work_title, Res.string.card_deep_work_body, GuideTarget.Personal(Destination.DAY), since = "3.0.0"),
        FeatureCard("holidays_slots", GuideChapter.ORGANIZE, { KairosIcons.EventBusy }, Res.string.card_holidays_slots_title, Res.string.card_holidays_slots_body, GuideTarget.Settings(GuideSection.SETTINGS_HOLIDAYS), since = "3.7.0"),
        FeatureCard("slots_month", GuideChapter.ORGANIZE, { KairosIcons.Repeat }, Res.string.card_slots_month_title, Res.string.card_slots_month_body, GuideTarget.Personal(Destination.DAY), since = "3.8.0"),
        FeatureCard("calendar_import", GuideChapter.ORGANIZE, { KairosIcons.Event }, Res.string.card_calendar_import_title, Res.string.card_calendar_import_body, GuideTarget.Settings(GuideSection.SETTINGS_DATA), since = "3.10.0"),
        FeatureCard("phone_calendars", GuideChapter.ORGANIZE, { KairosIcons.Mobile }, Res.string.card_phone_calendars_title, Res.string.card_phone_calendars_body, GuideTarget.Settings(GuideSection.SETTINGS_DATA), since = "3.10.0", platforms = setOf(Platform.ANDROID)),
        // --- Équipe
        FeatureCard("team_first_task", GuideChapter.TEAM, { KairosIcons.Assignment }, Res.string.card_team_first_task_title, Res.string.card_team_first_task_body, GuideTarget.Team(TeamDestination.BACKLOG), since = "3.8.0"),
        FeatureCard("team_members", GuideChapter.TEAM, { KairosIcons.Groups }, Res.string.guide_team_members_title, Res.string.guide_team_members_body, GuideTarget.Team(TeamDestination.MEMBERS), since = "3.1.0", tour = true),
        FeatureCard("team_my_work", GuideChapter.TEAM, { KairosIcons.Person }, Res.string.card_team_my_work_title, Res.string.card_team_my_work_body, GuideTarget.Team(TeamDestination.MEMBERS), since = "3.3.0"),
        FeatureCard("team_backlog", GuideChapter.TEAM, { KairosIcons.Stacks }, Res.string.guide_team_backlog_title, Res.string.guide_team_backlog_body, GuideTarget.Team(TeamDestination.BACKLOG), since = "3.1.0", tour = true),
        FeatureCard("team_assign", GuideChapter.TEAM, { KairosIcons.PersonAdd }, Res.string.guide_team_assign_title, Res.string.guide_team_assign_body, GuideTarget.Team(TeamDestination.BACKLOG), since = "3.1.0", tour = true),
        FeatureCard("team_board", GuideChapter.TEAM, { KairosIcons.ViewKanban }, Res.string.guide_team_board_title, Res.string.guide_team_board_body, GuideTarget.Team(TeamDestination.BOARD), since = "3.1.0", tour = true),
        FeatureCard("team_load", GuideChapter.TEAM, { KairosIcons.Speed }, Res.string.card_team_load_title, Res.string.card_team_load_body, GuideTarget.Team(TeamDestination.MEMBERS), since = "3.1.0"),
        FeatureCard("team_forecast", GuideChapter.TEAM, { KairosIcons.Monitoring }, Res.string.guide_team_forecast_title, Res.string.guide_team_forecast_body, GuideTarget.Team(TeamDestination.FORECAST), since = "3.1.0", tour = true),
        FeatureCard("team_exchanges", GuideChapter.TEAM, { KairosIcons.SwapHoriz }, Res.string.guide_team_exchanges_title, Res.string.guide_team_exchanges_body, GuideTarget.Team(TeamDestination.MEMBERS, exchanges = true), since = "3.1.0", tour = true),
        // --- Réglages
        FeatureCard("settings", GuideChapter.SETTINGS, { KairosIcons.Settings }, Res.string.card_settings_title, Res.string.card_settings_body, GuideTarget.Settings(), since = "3.4.0"),
        FeatureCard("settings_categories", GuideChapter.SETTINGS, { KairosIcons.Tune }, Res.string.card_settings_categories_title, Res.string.card_settings_categories_body, GuideTarget.Settings(), since = "3.7.0"),
        FeatureCard("reminders", GuideChapter.SETTINGS, { KairosIcons.Alarm }, Res.string.card_reminders_title, Res.string.card_reminders_body, GuideTarget.Settings(GuideSection.SETTINGS_REMINDERS), since = "3.8.0"),
        FeatureCard("daily_summary", GuideChapter.SETTINGS, { KairosIcons.Alarm }, Res.string.card_daily_summary_title, Res.string.card_daily_summary_body, GuideTarget.Settings(GuideSection.SETTINGS_REMINDERS), since = "3.12.0"),
        FeatureCard("theme_color", GuideChapter.SETTINGS, { KairosIcons.Palette }, Res.string.card_theme_color_title, Res.string.card_theme_color_body, GuideTarget.Settings(GuideSection.SETTINGS_APPEARANCE), since = "3.1.0"),
        FeatureCard("darktheme", GuideChapter.SETTINGS, { KairosIcons.DarkMode }, Res.string.card_darktheme_title, Res.string.card_darktheme_body, GuideTarget.Settings(GuideSection.SETTINGS_APPEARANCE), since = "3.7.0"),
        FeatureCard("languages", GuideChapter.SETTINGS, { KairosIcons.Translate }, Res.string.card_languages_title, Res.string.card_languages_body, GuideTarget.Settings(), since = "3.11.0"),
        FeatureCard("data", GuideChapter.SETTINGS, { KairosIcons.Description }, Res.string.guide_you_data_title, Res.string.guide_you_data_body, GuideTarget.Settings(GuideSection.SETTINGS_DATA), tour = true),
        FeatureCard("linked_file", GuideChapter.SETTINGS, { KairosIcons.Description }, Res.string.card_linked_file_title, Res.string.card_linked_file_body, GuideTarget.Settings(GuideSection.SETTINGS_DATA), since = "3.10.0"),
        FeatureCard("web_version", GuideChapter.SETTINGS, { KairosIcons.Web }, Res.string.card_web_version_title, Res.string.card_web_version_body, GuideTarget.Settings(GuideSection.SETTINGS_DATA), since = "3.0.0", platforms = setOf(Platform.WEB)),
    )

    /** Les cartes d'un chapitre, dans l'ordre. */
    fun chapter(chapter: GuideChapter): List<FeatureCard> = cards.filter { it.chapter == chapter }

    /** Les chapitres proposés : celui de l'équipe seulement si l'espace Équipe est activé. */
    fun chapters(teamEnabled: Boolean): List<GuideChapter> = GuideChapter.entries.filter { it != GuideChapter.TEAM || teamEnabled }

    /**
     * Les cartes que [version] ajoute : celles dont [FeatureCard.since] a les mêmes numéros majeur et mineur (une version corrective
     * `3.4.1` ou une préversion `3.4.0-beta.2` garde la section de la `3.4.0`). « Masquer » la retient pour [newsKey]
     * (docs/spec/visite-guidee.md § Persistance).
     */
    fun newIn(version: String): List<FeatureCard> {
        val minor = minorOf(version) ?: return emptyList()
        return cards.filter { minorOf(it.since) == minor }
    }

    /**
     * Versions du filtre « Nouveau depuis… » de la liste (docs/spec/liste-fonctionnalites.md) : les `since` distincts ramenés à
     * `X.Y.0`, du plus récent au plus ancien.
     */
    fun versions(): List<String> = cards.mapNotNull { minorOf(it.since) }.distinct()
        .sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
        .map { (major, minor) -> "$major.$minor.0" }

    /** Les cartes arrivées à partir de [version] (même minor ou après), dans l'ordre du catalogue ; toutes si illisible. */
    fun since(version: String): List<FeatureCard> {
        val from = minorOf(version) ?: return cards
        return cards.filter { card -> minorOf(card.since)?.let { compareValuesBy(it, from, { v -> v.first }, { v -> v.second }) >= 0 } ?: true }
    }

    /** « majeur.mineur » de [version] (`3.7.1` → `"3.7"`), sous lequel « Masquer » la section « Nouveau » est retenu ; nul si illisible. */
    fun newsKey(version: String): String? = minorOf(version)?.let { (major, minor) -> "$major.$minor" }

    private fun minorOf(version: String): Pair<Int, Int>? {
        val parts = version.substringBefore('-').split('.')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return major to minor
    }

    /**
     * Les fonctionnalités en gras de la section « Features » du `README.md` (anglais), chacune avec la carte qui la décrit : le test
     * `FeatureCatalogTest.readmeFeaturesAreCovered` compare cette table au README. Ajouter une fonctionnalité au README oblige donc à la
     * relier à une carte (la créer au besoin), dans le même changement.
     */
    val README_FEATURES: Map<String, String> = mapOf(
        "One-line capture" to "capture",
        "Full editing" to "qualify",
        "Editable subtasks" to "subtasks_edit",
        "Keyboard shortcuts" to "shortcuts",
        "Subtasks" to "subtasks",
        "Recurrence" to "recurrence",
        "Skipped months" to "skipped_months",
        "\"Snooze\"" to "snooze",
        "Blockers" to "dependencies",
        "Score" to "score",
        "Placement" to "placement",
        "Fibonacci points" to "points",
        "Level names" to "level_names",
        "Afternoon dip (14h55)" to "afternoon_dip",
        "Hours by day of the week" to "hours",
        "Several windows and contexts" to "contexts",
        "Learned durations" to "learned",
        "Working days and holidays" to "holidays",
        "Busy slots" to "slots",
        "Weekday slots skip holidays" to "holidays_slots",
        "Monthly slots, one day apart" to "slots_month",
        "Calendar import" to "calendar_import",
        "Phone calendars" to "phone_calendars",
        "Deep work" to "deep_work",
        "Timeline" to "timeline",
        "Timer per task" to "time",
        "Three alerts" to "alerts",
        "Reminders" to "reminders",
        "Daily summary" to "daily_summary",
        "Day" to "do",
        "Home screen widgets" to "widgets",
        "Week" to "week",
        "Move from the week" to "week_move",
        "Month" to "month",
        "Statistics" to "stats",
        "Statistics your way" to "stats_explore",
        "Trends" to "stats_trends",
        "Correct the estimates" to "stats_correct",
        "When you work on each type" to "besthours",
        "Your most efficient hours" to "besthours_efficiency",
        "Best hours" to "besthours_apply",
        "Export and import" to "data",
        "Linked file" to "linked_file",
        "Web version" to "web_version",
        "Languages" to "languages",
        "Settings" to "settings",
        "Settings by category" to "settings_categories",
        "Guided first task" to "first_task",
        "Interactive demo" to "demo",
        "Discover Kairos" to "discover",
        "The guide remembers" to "guide_memory",
        "Recurring task and Settings, step by step" to "guided_walks",
        "Guided first team task" to "team_first_task",
        "Members" to "team_members",
        "Team backlog" to "team_backlog",
        "Assignment" to "team_assign",
        "Tracking" to "team_board",
        "Load" to "team_load",
        "Forecasts" to "team_forecast",
        "Your personal work counts" to "team_my_work",
        "File exchanges" to "team_exchanges",
        "Theme color" to "theme_color",
        "Dark theme" to "darktheme",
    )
}

/** Les deux visites de l'ancien accueil, devenues des **sélections** du catalogue (6 étapes chacune). */
object GuideSteps {
    fun of(kind: GuideKind): List<FeatureCard> = when (kind) {
        GuideKind.PERSONAL -> FeatureCatalog.cards.filter { it.tour && it.chapter != GuideChapter.TEAM }
        // Les six cartes d'origine du chapitre : la liste complète en a plus (une carte par fonctionnalité, issue #153).
        GuideKind.TEAM -> FeatureCatalog.chapter(GuideChapter.TEAM).filter { it.tour }
    }
}
