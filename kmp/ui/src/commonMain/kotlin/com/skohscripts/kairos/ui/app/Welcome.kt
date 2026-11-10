package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.ui.day.verticalScrollWithThumb
import com.skohscripts.kairos.ui.theme.KairosTextButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.welcome_body
import com.skohscripts.kairos.ui.generated.resources.welcome_examples
import com.skohscripts.kairos.ui.generated.resources.welcome_first_task
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.generated.resources.welcome_start
import com.skohscripts.kairos.ui.generated.resources.welcome_title
import com.skohscripts.kairos.ui.generated.resources.welcome_tour
import com.skohscripts.kairos.ui.icons.KairosLogo
import org.jetbrains.compose.resources.stringResource

/**
 * Accueil du premier lancement (docs/spec/accueil.md), une seule fois :
 * ce que fait Kairos et les exemples (avec « Visite guidée », [onStartGuide], et « Créer ma première tâche », [onFirstTask] :
 * l'accompagnement de docs/spec/premiere-tache.md).
 */
@Composable
fun WelcomeDialog(services: AppServices, onStartGuide: () -> Unit, onFirstTask: () -> Unit = {}) {
    var open by rememberSaveable { mutableStateOf(services.firstLaunch) }
    // Un dialogue passe au-dessus de tout : il attend la fin de la page de démarrage (docs/spec/demarrage.md).
    if (!open || LocalStartupSplash.current) return
    AlertDialog(
        onDismissRequest = { open = false },
        icon = { Image(KairosLogo, contentDescription = null, modifier = Modifier.size(48.dp)) },
        title = { Text(stringResource(Res.string.welcome_title)) },
        text = {
            // Défile sur un écran bas (téléphone en paysage, fenêtre réduite) : rien n’est coupé.
            Column(Modifier.verticalScrollWithThumb(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.welcome_body), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.welcome_examples), style = MaterialTheme.typography.bodyMedium)
                KairosOutlinedButton(onClick = { open = false; onFirstTask() }) { Text(stringResource(Res.string.welcome_first_task)) }
            }
        },
        confirmButton = { KairosTextButton(onClick = { open = false }) { Text(stringResource(Res.string.welcome_start)) } },
        dismissButton = { KairosTextButton(onClick = { open = false; onStartGuide() }) { Text(stringResource(Res.string.welcome_tour)) } },
    )
}
