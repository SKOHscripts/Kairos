package com.skohscripts.kairos.ui.day

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.alerts_prompt_body
import com.skohscripts.kairos.ui.generated.resources.alerts_prompt_enable
import com.skohscripts.kairos.ui.generated.resources.alerts_prompt_later
import com.skohscripts.kairos.ui.generated.resources.alerts_prompt_title
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.stringResource

/**
 * Au premier chrono de la session, tant que l'autorisation de notifier est à demander (docs/spec/temps-reel-chrono.md
 * § Demande au démarrage du chrono) : dit pourquoi avant que le système ne demande. « Activer » ouvre la demande du système
 * (un geste de l'utilisateur, que le navigateur exige) ; « Plus tard » ou un clic à côté la ferme sans rien demander. Le
 * chrono a déjà démarré dans les deux cas.
 */
@Composable
internal fun AlertsPromptDialog(onEnable: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(Res.string.alerts_prompt_title)) },
        text = { Text(stringResource(Res.string.alerts_prompt_body)) },
        confirmButton = { KairosButton(onClick = onEnable) { Text(stringResource(Res.string.alerts_prompt_enable)) } },
        dismissButton = { KairosTextButton(onClick = onLater) { Text(stringResource(Res.string.alerts_prompt_later)) } },
    )
}
