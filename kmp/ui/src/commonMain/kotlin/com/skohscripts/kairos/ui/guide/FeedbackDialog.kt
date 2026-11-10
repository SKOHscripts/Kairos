package com.skohscripts.kairos.ui.guide

import com.skohscripts.kairos.ui.day.verticalScrollWithThumb
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.feedback.FeedbackIssue
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.feedback_action
import com.skohscripts.kairos.ui.generated.resources.feedback_attached
import com.skohscripts.kairos.ui.generated.resources.feedback_context
import com.skohscripts.kairos.ui.generated.resources.feedback_context_hint
import com.skohscripts.kairos.ui.generated.resources.feedback_continue
import com.skohscripts.kairos.ui.generated.resources.feedback_intro
import com.skohscripts.kairos.ui.generated.resources.feedback_need
import com.skohscripts.kairos.ui.generated.resources.feedback_title
import com.skohscripts.kairos.ui.generated.resources.feedback_too_long
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.stringResource

/**
 * « Donner un avis » (docs/spec/retours.md) : titre, contexte et besoin, puis « Continuer sur GitHub » ouvre une issue
 * préremplie dans le navigateur ; Kairos n'envoie rien lui-même. [openLink] dit si le lien s'est ouvert : sans navigateur,
 * le dialogue reste ouvert et la saisie intacte. Rien n'est gardé une fois le dialogue fermé. [version] et [platform] sont
 * joints d'office, et le dialogue le dit (« Ajouté automatiquement : Kairos 4.1.0 · Android »).
 */
@Composable
fun FeedbackDialog(version: String, platform: String, onDismiss: () -> Unit, openLink: (String) -> Boolean) {
    var title by rememberSaveable { mutableStateOf("") }
    var context by rememberSaveable { mutableStateOf("") }
    var need by rememberSaveable { mutableStateOf("") }
    val url = FeedbackIssue.url(title, context, need, version, platform)
    // Seule l'adresse complète compte, pas la longueur de chaque champ : l'erreur se montre sur le dernier.
    val tooLong = url != null && !FeedbackIssue.fits(url)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(KairosIcons.Feedback, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(Res.string.feedback_action), modifier = Modifier.heading()) },
        text = {
            // Défile : sur un écran bas (téléphone en paysage, clavier ouvert), les champs restent atteignables.
            Column(Modifier.verticalScrollWithThumb(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.feedback_intro), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.feedback_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = context,
                    onValueChange = { context = it },
                    label = { Text(stringResource(Res.string.feedback_context)) },
                    supportingText = { Text(stringResource(Res.string.feedback_context_hint)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = need,
                    onValueChange = { need = it },
                    label = { Text(stringResource(Res.string.feedback_need)) },
                    isError = tooLong,
                    supportingText = if (tooLong) {
                        { Text(stringResource(Res.string.feedback_too_long)) }
                    } else {
                        null
                    },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(Res.string.feedback_attached, version, platform),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            KairosTextButton(onClick = { if (url != null && openLink(url)) onDismiss() }, enabled = url != null && !tooLong) {
                Text(stringResource(Res.string.feedback_continue))
            }
        },
        dismissButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
