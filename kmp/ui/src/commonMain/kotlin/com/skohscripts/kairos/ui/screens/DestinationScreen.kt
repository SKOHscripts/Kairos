package com.skohscripts.kairos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.placeholder_body
import com.skohscripts.kairos.ui.generated.resources.placeholder_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.day.DayScreen
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.settings.SettingsScreen
import org.jetbrains.compose.resources.stringResource

/** Contenu d'une destination. Jalon M1 : Jour et Réglages ; les autres sont en construction. */
@Composable
fun DestinationScreen(destination: Destination, services: AppServices, onOpenAbout: () -> Unit) {
    when (destination) {
        Destination.DAY -> DayScreen(services)
        Destination.SETTINGS -> SettingsScreen(services, onOpenAbout)
        else -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) { UnderConstruction() }
    }
}

/** Écran « En construction » d'une destination pas encore portée. */
@Composable
fun UnderConstruction() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = 480.dp).padding(top = 32.dp),
    ) {
        Icon(
            KairosIcons.Construction,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(stringResource(Res.string.placeholder_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.placeholder_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
