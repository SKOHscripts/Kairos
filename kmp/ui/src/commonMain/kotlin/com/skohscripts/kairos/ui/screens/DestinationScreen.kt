package com.skohscripts.kairos.ui.screens

import androidx.compose.runtime.Composable
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.day.DayScreen
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.NavState
import com.skohscripts.kairos.ui.notes.NotesScreen
import com.skohscripts.kairos.ui.settings.SettingsScreen
import com.skohscripts.kairos.ui.stats.StatsScreen
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
