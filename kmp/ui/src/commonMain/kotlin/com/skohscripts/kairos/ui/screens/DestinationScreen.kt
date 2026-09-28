package com.skohscripts.kairos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.placeholder_body
import com.skohscripts.kairos.ui.generated.resources.placeholder_title
import com.skohscripts.kairos.ui.generated.resources.settings_about_entry
import com.skohscripts.kairos.ui.generated.resources.settings_about_entry_hint
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.navigation.Destination
import org.jetbrains.compose.resources.stringResource

/** Contenu d'une destination. Jalon M0 : écrans en construction, sauf l'entrée « À propos » des Réglages. */
@Composable
fun DestinationScreen(destination: Destination, onOpenAbout: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        if (destination == Destination.SETTINGS) {
            OutlinedCard(onClick = onOpenAbout, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                ListItem(
                    leadingContent = { Icon(KairosIcons.Info, contentDescription = null) },
                    headlineContent = { Text(stringResource(Res.string.settings_about_entry)) },
                    supportingContent = { Text(stringResource(Res.string.settings_about_entry_hint)) },
                    trailingContent = { Icon(KairosIcons.ChevronRight, contentDescription = null) },
                )
            }
        }
        UnderConstruction()
    }
}

@Composable
private fun UnderConstruction() {
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
