package com.skohscripts.kairos.ui.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.welcome_about
import com.skohscripts.kairos.ui.generated.resources.welcome_body
import com.skohscripts.kairos.ui.generated.resources.welcome_examples
import com.skohscripts.kairos.ui.generated.resources.welcome_keep_examples
import com.skohscripts.kairos.ui.generated.resources.welcome_legacy_found
import com.skohscripts.kairos.ui.generated.resources.welcome_legacy_import
import com.skohscripts.kairos.ui.generated.resources.welcome_migrated
import com.skohscripts.kairos.ui.generated.resources.welcome_start
import com.skohscripts.kairos.ui.generated.resources.welcome_title
import com.skohscripts.kairos.ui.icons.KairosLogo
import org.jetbrains.compose.resources.stringResource

/**
 * Accueil du premier lancement (docs/spec/accueil.md), une seule fois :
 * ce que fait Kairos et les exemples ; sur Android, le bilan de la migration
 * automatique de Kairos 2 ; sur le bureau, la proposition d'importer la base
 * Kairos 2 trouvée à son emplacement habituel.
 */
@Composable
fun WelcomeDialog(services: AppServices, onOpenAbout: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(services.firstLaunch) }
    val legacy = rememberLegacyImport(services)
    LegacyImportDialog(legacy)
    if (!open) return
    val migrated = services.migrated
    val found = services.legacy?.found.takeIf { migrated == null }
    AlertDialog(
        onDismissRequest = { open = false },
        icon = { Image(KairosLogo, contentDescription = null, modifier = Modifier.size(48.dp)) },
        title = { Text(stringResource(Res.string.welcome_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.welcome_body), style = MaterialTheme.typography.bodyMedium)
                when {
                    migrated != null -> Text(
                        stringResource(Res.string.welcome_migrated, migrated.tasks, migrated.notes, migrated.sessions),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    found != null -> Text(stringResource(Res.string.welcome_legacy_found, found), style = MaterialTheme.typography.bodyMedium)
                    else -> Text(stringResource(Res.string.welcome_examples), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            if (found != null) {
                TextButton(onClick = { open = false; legacy.importFound() }) { Text(stringResource(Res.string.welcome_legacy_import)) }
            } else {
                TextButton(onClick = { open = false }) { Text(stringResource(Res.string.welcome_start)) }
            }
        },
        dismissButton = {
            if (found != null) {
                TextButton(onClick = { open = false }) { Text(stringResource(Res.string.welcome_keep_examples)) }
            } else if (migrated == null) {
                TextButton(onClick = { open = false; onOpenAbout() }) { Text(stringResource(Res.string.welcome_about)) }
            }
        },
    )
}
