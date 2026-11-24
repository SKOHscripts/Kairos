package com.skohscripts.kairos.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.skohscripts.kairos.core.guide.Tips
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.day.DayScreen
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.NavState
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.notes.NotesScreen
import com.skohscripts.kairos.ui.stats.StatsScreen
import com.skohscripts.kairos.ui.team.TeamBacklogScreen
import com.skohscripts.kairos.ui.team.TeamBoardScreen
import com.skohscripts.kairos.ui.team.TeamMembersScreen
import com.skohscripts.kairos.ui.team.forecast.ForecastScreen
import com.skohscripts.kairos.ui.week.WeekScreen
import com.skohscripts.kairos.ui.month.MonthScreen
import kotlinx.datetime.LocalDate

/**
 * Contenu d'une destination (les cinq sont portées depuis le jalon M4). [home] est l'Accueil, fourni par la coquille
 * (il ouvre les cibles de la visite et lance les visites, état de la coquille), le même dans les deux espaces.
 */
@Composable
fun DestinationScreen(
    destination: Destination,
    services: AppServices,
    nav: NavState,
    onOpenDay: (LocalDate?) -> Unit,
    onNavigate: (Destination) -> Unit,
    home: @Composable () -> Unit,
    onDiscover: () -> Unit = {},
) {
    when (destination) {
        Destination.HOME -> home()
        Destination.NOTES -> NotesScreen(services, onOpenTasks = { onNavigate(Destination.DAY) })
        Destination.DAY -> DayScreen(
            services, nav.day, onBackToToday = { onOpenDay(null) },
            firstTaskRequest = nav.firstTaskRequest, firstTaskRecurring = nav.firstTaskRecurring, firstTaskHabit = nav.firstTaskHabit,
            onFirstTaskHandled = { nav.firstTaskRequest = false; nav.firstTaskRecurring = false; nav.firstTaskHabit = false }, onDiscover = onDiscover,
            habitRequest = nav.habitRequest, onHabitRequestHandled = { nav.habitRequest = null },
        )
        Destination.WEEK -> {
            // Première ouverture du Bilan retenue (conseil « Le Bilan de la semaine et du mois », docs/spec/conseils.md).
            LaunchedEffect(Unit) { services.repository.updateGuide { Tips.seen(it, Tips.SCREEN_RECAP) } }
            if (nav.monthMode) MonthScreen(services, nav, onOpenDay) else WeekScreen(services, nav, onOpenDay)
        }
        Destination.STATS -> StatsScreen(services)
    }
}

/**
 * Contenu d'une destination de l'espace Équipe. L'Accueil est le même écran que dans l'espace Perso ; Équipe liste les
 * membres (jalon E2), Suivi et Backlog sont ceux du jalon E3, Prévisions (simulations et scénarios) celui du jalon E5.
 */
@Composable
fun TeamDestinationScreen(destination: TeamDestination, services: AppServices, nav: NavState, onTour: () -> Unit, home: @Composable () -> Unit) {
    when (destination) {
        TeamDestination.HOME -> home()
        TeamDestination.BOARD -> TeamBoardScreen(services)
        TeamDestination.BACKLOG -> TeamBacklogScreen(services)
        TeamDestination.MEMBERS -> TeamMembersScreen(services, nav = nav, onTour = onTour)
        TeamDestination.FORECAST -> ForecastScreen(services)
    }
}
