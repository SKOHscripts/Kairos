package com.skohscripts.kairos.ui.guide

import androidx.compose.ui.graphics.vector.ImageVector
import com.skohscripts.kairos.ui.generated.resources.Res
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
)

/** Le catalogue, ses sélections et le lien avec le README. */
object FeatureCatalog {
    /**
     * Toutes les cartes, **dans l'ordre de la visite** : la visite initiale de l'espace Perso en est la sélection [FeatureCard.tour],
     * celle de l'espace Équipe le chapitre [GuideChapter.TEAM]. Les ids sont stables (ils relient la liste du README).
     */
    val cards: List<FeatureCard> = listOf(
        // --- Premiers pas
        FeatureCard("capture", GuideChapter.FIRST_STEPS, { KairosIcons.Notes }, Res.string.guide_you_capture_title, Res.string.guide_you_capture_body, GuideTarget.Personal(Destination.NOTES), tour = true),
        FeatureCard("qualify", GuideChapter.FIRST_STEPS, { KairosIcons.Edit }, Res.string.guide_you_qualify_title, Res.string.guide_you_qualify_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_INBOX), tour = true),
        FeatureCard("do", GuideChapter.FIRST_STEPS, { KairosIcons.Today }, Res.string.guide_you_do_title, Res.string.guide_you_do_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_AGENDA), tour = true),
        FeatureCard("discover", GuideChapter.FIRST_STEPS, { KairosIcons.Layers }, Res.string.card_discover_title, Res.string.card_discover_body, GuideTarget.Personal(Destination.HOME), since = "3.4.0"),
        FeatureCard("first_task", GuideChapter.FIRST_STEPS, { KairosIcons.Add }, Res.string.card_first_task_title, Res.string.card_first_task_body, GuideTarget.FirstTask, since = "3.4.0"),
        FeatureCard("guide_memory", GuideChapter.FIRST_STEPS, { KairosIcons.Spot }, Res.string.card_guide_memory_title, Res.string.card_guide_memory_body, GuideTarget.Personal(Destination.HOME), since = "3.7.0"),
        FeatureCard("guided_walks", GuideChapter.FIRST_STEPS, { KairosIcons.Repeat }, Res.string.card_guided_walks_title, Res.string.card_guided_walks_body, GuideTarget.Personal(Destination.HOME), since = "3.9.0"),
        // --- Planifier
        FeatureCard("points", GuideChapter.PLAN, { KairosIcons.Balance }, Res.string.card_points_title, Res.string.card_points_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_INBOX)),
        FeatureCard("level_names", GuideChapter.PLAN, { KairosIcons.Flag }, Res.string.card_level_names_title, Res.string.card_level_names_body, GuideTarget.Settings(GuideSection.SETTINGS_LEVELS), since = "3.9.0"),
        FeatureCard("score", GuideChapter.PLAN, { KairosIcons.Star }, Res.string.card_score_title, Res.string.card_score_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_AGENDA)),
        FeatureCard("contexts", GuideChapter.PLAN, { KairosIcons.Layers }, Res.string.card_contexts_title, Res.string.card_contexts_body, GuideTarget.Settings(GuideSection.SETTINGS_CONTEXTS), since = "3.5.0"),
        FeatureCard("besthours_apply", GuideChapter.PLAN, { KairosIcons.Schedule }, Res.string.card_besthours_apply_title, Res.string.card_besthours_apply_body, GuideTarget.Settings(GuideSection.SETTINGS_BEST_HOURS), since = "3.6.0"),
        FeatureCard("hours", GuideChapter.PLAN, { KairosIcons.Schedule }, Res.string.card_hours_title, Res.string.card_hours_body, GuideTarget.Settings(GuideSection.SETTINGS_HOURS), since = "3.4.0"),
        // --- Suivre
        FeatureCard("time", GuideChapter.FOLLOW, { KairosIcons.Schedule }, Res.string.guide_you_time_title, Res.string.guide_you_time_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_NOW), tour = true),
        FeatureCard("learned", GuideChapter.FOLLOW, { KairosIcons.TrendingUp }, Res.string.card_learned_title, Res.string.card_learned_body, GuideTarget.Settings(GuideSection.SETTINGS_LEARNED), since = "3.4.0"),
        FeatureCard("besthours", GuideChapter.FOLLOW, { KairosIcons.Schedule }, Res.string.card_besthours_title, Res.string.card_besthours_body, GuideTarget.Personal(Destination.STATS, GuideSection.STATS_HOURS), since = "3.5.0"),
        FeatureCard("besthours_efficiency", GuideChapter.FOLLOW, { KairosIcons.Timer }, Res.string.card_besthours_eff_title, Res.string.card_besthours_eff_body, GuideTarget.Personal(Destination.STATS, GuideSection.STATS_HOURS), since = "3.9.0"),
        // Widgets Android (docs/spec/widgets-android.md) : la carte mène à la vue Jour, qu'ils reprennent.
        FeatureCard("widgets", GuideChapter.FOLLOW, { KairosIcons.Today }, Res.string.card_widgets_title, Res.string.card_widgets_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_NOW), since = "3.12.0"),
        FeatureCard("week", GuideChapter.FOLLOW, { KairosIcons.DateRange }, Res.string.card_week_title, Res.string.card_week_body, GuideTarget.Personal(Destination.WEEK)),
        FeatureCard("month", GuideChapter.FOLLOW, { KairosIcons.CalendarMonth }, Res.string.card_month_title, Res.string.card_month_body, GuideTarget.Personal(Destination.WEEK), since = "3.8.0"),
        FeatureCard("week_move", GuideChapter.FOLLOW, { KairosIcons.SwapHoriz }, Res.string.card_week_move_title, Res.string.card_week_move_body, GuideTarget.Personal(Destination.WEEK), since = "3.7.0"),
        FeatureCard("stats", GuideChapter.FOLLOW, { KairosIcons.BarChart }, Res.string.guide_you_stats_title, Res.string.guide_you_stats_body, GuideTarget.Personal(Destination.STATS), tour = true),
        FeatureCard("stats_trends", GuideChapter.FOLLOW, { KairosIcons.ShowChart }, Res.string.card_trends_title, Res.string.card_trends_body, GuideTarget.Personal(Destination.STATS), since = "3.8.0"),
        FeatureCard("stats_correct", GuideChapter.FOLLOW, { KairosIcons.EditNote }, Res.string.card_correct_title, Res.string.card_correct_body, GuideTarget.Personal(Destination.STATS), since = "3.8.0"),
        FeatureCard("stats_explore", GuideChapter.FOLLOW, { KairosIcons.Download }, Res.string.card_stats_explore_title, Res.string.card_stats_explore_body, GuideTarget.Personal(Destination.STATS), since = "3.7.0"),
        // --- Organiser
        FeatureCard("subtasks", GuideChapter.ORGANIZE, { KairosIcons.Layers }, Res.string.card_subtasks_title, Res.string.card_subtasks_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_PARENTS)),
        FeatureCard("recurrence", GuideChapter.ORGANIZE, { KairosIcons.Repeat }, Res.string.card_recurrence_title, Res.string.card_recurrence_body, GuideTarget.Personal(Destination.DAY), since = "3.4.0"),
        FeatureCard("dependencies", GuideChapter.ORGANIZE, { KairosIcons.Block }, Res.string.card_dependencies_title, Res.string.card_dependencies_body, GuideTarget.Personal(Destination.DAY, GuideSection.DAY_BLOCKED)),
        FeatureCard("slots", GuideChapter.ORGANIZE, { KairosIcons.PushPin }, Res.string.card_slots_title, Res.string.card_slots_body, GuideTarget.Personal(Destination.DAY)),
        FeatureCard("holidays_slots", GuideChapter.ORGANIZE, { KairosIcons.EventBusy }, Res.string.card_holidays_slots_title, Res.string.card_holidays_slots_body, GuideTarget.Settings(GuideSection.SETTINGS_HOLIDAYS), since = "3.7.0"),
        FeatureCard("slots_month", GuideChapter.ORGANIZE, { KairosIcons.Repeat }, Res.string.card_slots_month_title, Res.string.card_slots_month_body, GuideTarget.Personal(Destination.DAY), since = "3.8.0"),
        FeatureCard("calendar_import", GuideChapter.ORGANIZE, { KairosIcons.Event }, Res.string.card_calendar_import_title, Res.string.card_calendar_import_body, GuideTarget.Settings(GuideSection.SETTINGS_DATA), since = "3.10.0"),
        // --- Équipe
        FeatureCard("team_members", GuideChapter.TEAM, { KairosIcons.Groups }, Res.string.guide_team_members_title, Res.string.guide_team_members_body, GuideTarget.Team(TeamDestination.MEMBERS)),
        FeatureCard("team_backlog", GuideChapter.TEAM, { KairosIcons.Stacks }, Res.string.guide_team_backlog_title, Res.string.guide_team_backlog_body, GuideTarget.Team(TeamDestination.BACKLOG)),
        FeatureCard("team_assign", GuideChapter.TEAM, { KairosIcons.PersonAdd }, Res.string.guide_team_assign_title, Res.string.guide_team_assign_body, GuideTarget.Team(TeamDestination.BACKLOG)),
        FeatureCard("team_board", GuideChapter.TEAM, { KairosIcons.ViewKanban }, Res.string.guide_team_board_title, Res.string.guide_team_board_body, GuideTarget.Team(TeamDestination.BOARD)),
        FeatureCard("team_forecast", GuideChapter.TEAM, { KairosIcons.Monitoring }, Res.string.guide_team_forecast_title, Res.string.guide_team_forecast_body, GuideTarget.Team(TeamDestination.FORECAST)),
        FeatureCard("team_exchanges", GuideChapter.TEAM, { KairosIcons.SwapHoriz }, Res.string.guide_team_exchanges_title, Res.string.guide_team_exchanges_body, GuideTarget.Team(TeamDestination.MEMBERS, exchanges = true)),
        // --- Réglages
        FeatureCard("settings", GuideChapter.SETTINGS, { KairosIcons.Settings }, Res.string.card_settings_title, Res.string.card_settings_body, GuideTarget.Settings(), since = "3.4.0"),
        FeatureCard("settings_categories", GuideChapter.SETTINGS, { KairosIcons.Tune }, Res.string.card_settings_categories_title, Res.string.card_settings_categories_body, GuideTarget.Settings(), since = "3.7.0"),
        FeatureCard("reminders", GuideChapter.SETTINGS, { KairosIcons.Alarm }, Res.string.card_reminders_title, Res.string.card_reminders_body, GuideTarget.Settings(GuideSection.SETTINGS_REMINDERS), since = "3.8.0"),
        FeatureCard("darktheme", GuideChapter.SETTINGS, { KairosIcons.DarkMode }, Res.string.card_darktheme_title, Res.string.card_darktheme_body, GuideTarget.Settings(GuideSection.SETTINGS_APPEARANCE), since = "3.7.0"),
        FeatureCard("languages", GuideChapter.SETTINGS, { KairosIcons.Translate }, Res.string.card_languages_title, Res.string.card_languages_body, GuideTarget.Settings(), since = "3.11.0"),
        FeatureCard("data", GuideChapter.SETTINGS, { KairosIcons.Description }, Res.string.guide_you_data_title, Res.string.guide_you_data_body, GuideTarget.Settings(GuideSection.SETTINGS_DATA), tour = true),
        FeatureCard("linked_file", GuideChapter.SETTINGS, { KairosIcons.Description }, Res.string.card_linked_file_title, Res.string.card_linked_file_body, GuideTarget.Settings(GuideSection.SETTINGS_DATA), since = "3.10.0"),
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
        "Editable subtasks" to "subtasks",
        "Keyboard shortcuts" to "capture",
        "Subtasks" to "subtasks",
        "Recurrence" to "recurrence",
        "\"Snooze\"" to "recurrence",
        "Blockers" to "dependencies",
        "Score" to "score",
        "Placement" to "score",
        "Fibonacci points" to "points",
        "Level names" to "level_names",
        "Recurring task and Settings, step by step" to "guided_walks",
        "Afternoon dip (14h55)" to "score",
        "Hours by day of the week" to "hours",
        "Several windows and contexts" to "contexts",
        "Learned durations" to "learned",
        "Working days and holidays" to "hours",
        "Busy slots" to "slots",
        "Monthly slots, one day apart" to "slots_month",
        "Calendar import" to "calendar_import",
        "Phone calendars" to "calendar_import",
        "Deep work" to "slots",
        "Timeline" to "do",
        "Timer per task" to "time",
        "Three alerts" to "time",
        "Day" to "do",
        "Week" to "week",
        "Month" to "month",
        "Statistics" to "stats",
        "Correct the estimates" to "stats_correct",
        "Trends" to "stats_trends",
        "When you work on each type" to "besthours",
        "Best hours" to "besthours_apply",
        "Web version" to "data",
        "Linked file" to "linked_file",
        "Settings" to "settings",
        "Members" to "team_members",
        "Tracking" to "team_board",
        "Load" to "team_board",
        "Forecasts" to "team_forecast",
        "Your personal work counts" to "team_members",
        "File exchanges" to "team_exchanges",
        "Theme color" to "settings",
        "Dark theme" to "darktheme",
        "Languages" to "languages",
        "Reminders" to "reminders",
        "Home screen widgets" to "widgets",
        "Guided first task" to "first_task",
        "Guided first team task" to "team_backlog",
        "Discover Kairos" to "discover",
    )
}

/** Les deux visites de l'ancien accueil, devenues des **sélections** du catalogue (6 étapes chacune). */
object GuideSteps {
    fun of(kind: GuideKind): List<FeatureCard> = when (kind) {
        GuideKind.PERSONAL -> FeatureCatalog.cards.filter { it.tour }
        GuideKind.TEAM -> FeatureCatalog.chapter(GuideChapter.TEAM)
    }
}
