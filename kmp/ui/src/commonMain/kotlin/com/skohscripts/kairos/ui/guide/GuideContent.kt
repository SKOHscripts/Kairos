package com.skohscripts.kairos.ui.guide

import androidx.compose.ui.graphics.vector.ImageVector
import com.skohscripts.kairos.ui.generated.resources.Res
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
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.NavEntry
import com.skohscripts.kairos.ui.navigation.TeamDestination
import org.jetbrains.compose.resources.StringResource

/** Quelle visite : celle de l'espace Perso (6 étapes) ou celle de l'espace Équipe (6 étapes, gestion d'équipe activée). */
enum class GuideKind { PERSONAL, TEAM }

/**
 * Écran qu'ouvre « Voir l’écran » (et les boutons « Ouvrir » de l'Accueil). La coquille change d'espace au besoin :
 * une cible d'équipe ouvre l'espace Équipe, une cible Perso l'espace Perso. [Team.exchanges] ouvre l'onglet « Échanges »
 * de l'écran Équipe (`NavState.exchangesTab`).
 */
sealed interface GuideTarget {
    data class Personal(val destination: Destination) : GuideTarget
    data class Team(val destination: TeamDestination, val exchanges: Boolean = false) : GuideTarget
}

/** Une étape de la visite guidée (docs/spec/accueil.md § La visite guidée). */
data class GuideStep(
    val icon: () -> ImageVector,
    val title: StringResource,
    val body: StringResource,
    val target: GuideTarget,
)

object GuideSteps {
    fun of(kind: GuideKind): List<GuideStep> = when (kind) {
        GuideKind.PERSONAL -> PERSONAL
        GuideKind.TEAM -> TEAM
    }

    private val PERSONAL = listOf(
        GuideStep({ KairosIcons.Notes }, Res.string.guide_you_capture_title, Res.string.guide_you_capture_body, GuideTarget.Personal(Destination.NOTES)),
        GuideStep({ KairosIcons.Edit }, Res.string.guide_you_qualify_title, Res.string.guide_you_qualify_body, GuideTarget.Personal(Destination.DAY)),
        GuideStep({ KairosIcons.Today }, Res.string.guide_you_do_title, Res.string.guide_you_do_body, GuideTarget.Personal(Destination.DAY)),
        GuideStep({ KairosIcons.Schedule }, Res.string.guide_you_time_title, Res.string.guide_you_time_body, GuideTarget.Personal(Destination.DAY)),
        GuideStep({ KairosIcons.BarChart }, Res.string.guide_you_stats_title, Res.string.guide_you_stats_body, GuideTarget.Personal(Destination.STATS)),
        GuideStep({ KairosIcons.Settings }, Res.string.guide_you_data_title, Res.string.guide_you_data_body, GuideTarget.Personal(Destination.SETTINGS)),
    )

    private val TEAM = listOf(
        GuideStep({ KairosIcons.Groups }, Res.string.guide_team_members_title, Res.string.guide_team_members_body, GuideTarget.Team(TeamDestination.MEMBERS)),
        GuideStep({ KairosIcons.Stacks }, Res.string.guide_team_backlog_title, Res.string.guide_team_backlog_body, GuideTarget.Team(TeamDestination.BACKLOG)),
        GuideStep({ KairosIcons.PersonAdd }, Res.string.guide_team_assign_title, Res.string.guide_team_assign_body, GuideTarget.Team(TeamDestination.BACKLOG)),
        GuideStep({ KairosIcons.ViewKanban }, Res.string.guide_team_board_title, Res.string.guide_team_board_body, GuideTarget.Team(TeamDestination.BOARD)),
        GuideStep({ KairosIcons.Monitoring }, Res.string.guide_team_forecast_title, Res.string.guide_team_forecast_body, GuideTarget.Team(TeamDestination.FORECAST)),
        GuideStep({ KairosIcons.SwapHoriz }, Res.string.guide_team_exchanges_title, Res.string.guide_team_exchanges_body, GuideTarget.Team(TeamDestination.MEMBERS, exchanges = true)),
    )
}

/**
 * Texte d'aide d'un écran (« ? » de la barre du haut). Un sujet par écran ; les Réglages sont les mêmes dans les deux
 * espaces, l'écran Équipe a un sujet par onglet.
 */
enum class HelpTopic(val body: StringResource) {
    NOTES(Res.string.help_notes),
    DAY(Res.string.help_day),
    WEEK(Res.string.help_week),
    STATS(Res.string.help_stats),
    SETTINGS(Res.string.help_settings),
    TEAM_BOARD(Res.string.help_team_board),
    TEAM_BACKLOG(Res.string.help_team_backlog),
    TEAM_MEMBERS(Res.string.help_team_members),
    TEAM_EXCHANGES(Res.string.help_team_exchanges),
    TEAM_FORECAST(Res.string.help_team_forecast),
    ;

    companion object {
        /** Sujet d'aide de [entry] ; [exchanges] : l'onglet « Échanges » de l'écran Équipe est ouvert. */
        fun of(entry: NavEntry, exchanges: Boolean): HelpTopic = when (entry) {
            Destination.NOTES -> NOTES
            Destination.DAY -> DAY
            Destination.WEEK -> WEEK
            Destination.STATS -> STATS
            Destination.SETTINGS, TeamDestination.SETTINGS -> SETTINGS
            TeamDestination.BOARD -> TEAM_BOARD
            TeamDestination.BACKLOG -> TEAM_BACKLOG
            TeamDestination.MEMBERS -> if (exchanges) TEAM_EXCHANGES else TEAM_MEMBERS
            TeamDestination.FORECAST -> TEAM_FORECAST
        }
    }
}
