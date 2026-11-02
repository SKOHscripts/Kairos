package com.skohscripts.kairos.ui.guide

import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.guide_spot_missing_blocked
import com.skohscripts.kairos.ui.generated.resources.guide_spot_missing_parents
import com.skohscripts.kairos.ui.generated.resources.guide_spot_missing_stats
import com.skohscripts.kairos.ui.generated.resources.help_day
import com.skohscripts.kairos.ui.generated.resources.help_notes
import com.skohscripts.kairos.ui.generated.resources.help_settings
import com.skohscripts.kairos.ui.generated.resources.help_stats
import com.skohscripts.kairos.ui.generated.resources.help_team_backlog
import com.skohscripts.kairos.ui.generated.resources.help_team_board
import com.skohscripts.kairos.ui.generated.resources.help_team_exchanges
import com.skohscripts.kairos.ui.generated.resources.help_team_forecast
import com.skohscripts.kairos.ui.generated.resources.help_team_members
import com.skohscripts.kairos.ui.generated.resources.help_week
import com.skohscripts.kairos.ui.generated.resources.title_day
import com.skohscripts.kairos.ui.generated.resources.title_notes
import com.skohscripts.kairos.ui.generated.resources.title_settings
import com.skohscripts.kairos.ui.generated.resources.title_stats
import com.skohscripts.kairos.ui.generated.resources.title_team_backlog
import com.skohscripts.kairos.ui.generated.resources.title_team_board
import com.skohscripts.kairos.ui.generated.resources.title_team_forecast
import com.skohscripts.kairos.ui.generated.resources.title_team_members
import com.skohscripts.kairos.ui.generated.resources.title_week
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.NavEntry
import com.skohscripts.kairos.ui.settings.SettingsCategory
import com.skohscripts.kairos.ui.navigation.TeamDestination
import org.jetbrains.compose.resources.StringResource

/** Quelle visite : celle de l'espace Perso (6 étapes) ou celle de l'espace Équipe (6 étapes, gestion d'équipe activée) ; sélections du catalogue ([GuideSteps]). */
enum class GuideKind { PERSONAL, TEAM }

/**
 * Écran qu'ouvre « Voir l’écran » (et les boutons « Ouvrir » de l'Accueil). La coquille change d'espace au besoin :
 * une cible d'équipe ouvre l'espace Équipe, une cible Perso l'espace Perso. [Team.exchanges] ouvre l'onglet « Échanges »
 * de l'écran Équipe (`NavState.exchangesTab`). [Settings] ouvre les Réglages (écran secondaire, les mêmes dans les deux
 * espaces) sans changer d'espace. [section] : l'endroit de l'écran à montrer (docs/spec/visite-guidee.md, issue #95).
 */
sealed interface GuideTarget {
    data class Personal(val destination: Destination, val section: GuideSection? = null) : GuideTarget
    data class Team(val destination: TeamDestination, val exchanges: Boolean = false) : GuideTarget
    data class Settings(val section: GuideSection? = null) : GuideTarget

    /** Lance l'accompagnement de la première tâche (docs/spec/premiere-tache.md) : ne change pas d'écran, ouvre la fiche. */
    data object FirstTask : GuideTarget

    /** Lance la démonstration jetable (docs/spec/visite-guidee.md § Visite interactive) : ne change pas d'écran. */
    data object Demo : GuideTarget
}

/** Écran qui porte une [GuideSection]. */
enum class GuideScreen { DAY, SETTINGS, STATS }

/**
 * Endroit précis d'un écran qu'une carte du guide fait voir : l'écran défile jusqu'à lui et le met en valeur
 * (docs/spec/visite-guidee.md, issue #95). [settingsCategory] : la catégorie des Réglages à ouvrir d'abord. [missing] :
 * le message quand la section est absente (vide), pour celles qui peuvent l'être.
 */
enum class GuideSection(
    val screen: GuideScreen,
    val settingsCategory: SettingsCategory? = null,
    val missing: StringResource? = null,
) {
    DAY_INBOX(GuideScreen.DAY),
    DAY_AGENDA(GuideScreen.DAY),
    DAY_NOW(GuideScreen.DAY),
    DAY_BLOCKED(GuideScreen.DAY, missing = Res.string.guide_spot_missing_blocked),
    DAY_PARENTS(GuideScreen.DAY, missing = Res.string.guide_spot_missing_parents),
    SETTINGS_WORKDAY(GuideScreen.SETTINGS, SettingsCategory.DAY),
    SETTINGS_CONTEXTS(GuideScreen.SETTINGS, SettingsCategory.DAY),
    SETTINGS_HOURS(GuideScreen.SETTINGS, SettingsCategory.DAY),
    SETTINGS_HOLIDAYS(GuideScreen.SETTINGS, SettingsCategory.DAY),
    SETTINGS_BEST_HOURS(GuideScreen.SETTINGS, SettingsCategory.PLANNING),
    SETTINGS_LEARNED(GuideScreen.SETTINGS, SettingsCategory.PLANNING),
    SETTINGS_LEVELS(GuideScreen.SETTINGS, SettingsCategory.PLANNING),
    SETTINGS_APPEARANCE(GuideScreen.SETTINGS, SettingsCategory.GENERAL),
    SETTINGS_DATA(GuideScreen.SETTINGS, SettingsCategory.DATA),
    SETTINGS_REMINDERS(GuideScreen.SETTINGS, SettingsCategory.TRACKING),
    STATS_HOURS(GuideScreen.STATS, missing = Res.string.guide_spot_missing_stats),
}

/**
 * Texte d'aide d'un écran (« ? » de la barre du haut). Un sujet par écran ; les Réglages (écran secondaire) sont les
 * mêmes dans les deux espaces, l'écran Équipe a un sujet par onglet. L'Accueil n'en a pas : la page est elle-même l'aide.
 */
enum class HelpTopic(
    /** Titre de l'écran dans « Aide : <écran> » : celui de la destination, pas le titre du jour affiché. */
    val screen: StringResource,
    val body: StringResource,
) {
    NOTES(Res.string.title_notes, Res.string.help_notes),
    DAY(Res.string.title_day, Res.string.help_day),
    WEEK(Res.string.title_week, Res.string.help_week),
    STATS(Res.string.title_stats, Res.string.help_stats),
    SETTINGS(Res.string.title_settings, Res.string.help_settings),
    TEAM_BOARD(Res.string.title_team_board, Res.string.help_team_board),
    TEAM_BACKLOG(Res.string.title_team_backlog, Res.string.help_team_backlog),
    TEAM_MEMBERS(Res.string.title_team_members, Res.string.help_team_members),
    TEAM_EXCHANGES(Res.string.title_team_members, Res.string.help_team_exchanges),
    TEAM_FORECAST(Res.string.title_team_forecast, Res.string.help_team_forecast),
    ;

    companion object {
        /**
         * Sujet d'aide de [entry] ; [exchanges] : l'onglet « Échanges » de l'écran Équipe est ouvert. `null` pour
         * l'Accueil, où le « ? » n'apparaît pas.
         */
        fun of(entry: NavEntry, exchanges: Boolean): HelpTopic? = when (entry) {
            Destination.HOME, TeamDestination.HOME -> null
            Destination.NOTES -> NOTES
            Destination.DAY -> DAY
            Destination.WEEK -> WEEK
            Destination.STATS -> STATS
            TeamDestination.BOARD -> TEAM_BOARD
            TeamDestination.BACKLOG -> TEAM_BACKLOG
            TeamDestination.MEMBERS -> if (exchanges) TEAM_EXCHANGES else TEAM_MEMBERS
            TeamDestination.FORECAST -> TEAM_FORECAST
        }
    }
}
