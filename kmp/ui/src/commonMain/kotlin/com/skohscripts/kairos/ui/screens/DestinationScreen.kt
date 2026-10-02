package com.skohscripts.kairos.ui.screens

import androidx.compose.runtime.Composable
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.day.DayScreen
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.NavState
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.notes.NotesScreen
import com.skohscripts.kairos.ui.settings.SettingsScreen
import com.skohscripts.kairos.ui.stats.StatsScreen
import com.skohscripts.kairos.ui.team.TeamBacklogScreen
import com.skohscripts.kairos.ui.team.TeamBoardScreen
import com.skohscripts.kairos.ui.team.TeamMembersScreen
import com.skohscripts.kairos.ui.team.forecast.ForecastScreen
import com.skohscripts.kairos.ui.week.WeekScreen
import kotlinx.datetime.LocalDate

/** Contenu d'une destination (les cinq sont portées depuis le jalon M4). */
@Composable
fun DestinationScreen(
    destination: Destination,
    services: AppServices,
    nav: NavState,
    onOpenDay: (LocalDate?) -> Unit,
    onNavigate: (Destination) -> Unit,
    onOpenAbout: () -> Unit,
) {
    when (destination) {
        Destination.NOTES -> NotesScreen(services, onOpenTasks = { onNavigate(Destination.DAY) })
        Destination.DAY -> DayScreen(services, nav.day, onBackToToday = { onOpenDay(null) })
        Destination.WEEK -> WeekScreen(services, nav, onOpenDay)
        Destination.STATS -> StatsScreen(services)
        Destination.SETTINGS -> SettingsScreen(services, onOpenAbout)
    }
}

/**
 * Contenu d'une destination de l'espace Équipe. Les Réglages sont le même
 * écran que dans l'espace Perso ; Équipe liste les membres (jalon E2), Suivi et
 * Backlog sont ceux du jalon E3, Prévisions (simulations et scénarios) celui du jalon E5.
 */
@Composable
fun TeamDestinationScreen(destination: TeamDestination, services: AppServices, nav: NavState, onOpenAbout: () -> Unit, onTour: () -> Unit) {
    when (destination) {
        TeamDestination.BOARD -> TeamBoardScreen(services)
        TeamDestination.BACKLOG -> TeamBacklogScreen(services)
        TeamDestination.MEMBERS -> TeamMembersScreen(services, nav = nav, onTour = onTour)
        TeamDestination.FORECAST -> ForecastScreen(services)
        TeamDestination.SETTINGS -> SettingsScreen(services, onOpenAbout)
    }
}
