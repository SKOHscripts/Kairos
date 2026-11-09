package com.skohscripts.kairos.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.gtd_diff_order
import com.skohscripts.kairos.ui.generated.resources.gtd_diff_review
import com.skohscripts.kairos.ui.generated.resources.gtd_diff_someday
import com.skohscripts.kairos.ui.generated.resources.gtd_diff_title
import com.skohscripts.kairos.ui.generated.resources.gtd_intro
import com.skohscripts.kairos.ui.generated.resources.gtd_label_capture
import com.skohscripts.kairos.ui.generated.resources.gtd_label_engage
import com.skohscripts.kairos.ui.generated.resources.gtd_label_process
import com.skohscripts.kairos.ui.generated.resources.gtd_label_reflect
import com.skohscripts.kairos.ui.generated.resources.gtd_open
import com.skohscripts.kairos.ui.generated.resources.gtd_step_capture_body
import com.skohscripts.kairos.ui.generated.resources.gtd_step_capture_name
import com.skohscripts.kairos.ui.generated.resources.gtd_step_clarify_body
import com.skohscripts.kairos.ui.generated.resources.gtd_step_clarify_name
import com.skohscripts.kairos.ui.generated.resources.gtd_step_engage_body
import com.skohscripts.kairos.ui.generated.resources.gtd_step_engage_name
import com.skohscripts.kairos.ui.generated.resources.gtd_step_organize_body
import com.skohscripts.kairos.ui.generated.resources.gtd_step_organize_name
import com.skohscripts.kairos.ui.generated.resources.gtd_step_reflect_body
import com.skohscripts.kairos.ui.generated.resources.gtd_step_reflect_name
import com.skohscripts.kairos.ui.generated.resources.gtd_title
import com.skohscripts.kairos.ui.generated.resources.help_close
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosTextButton
import com.skohscripts.kairos.ui.theme.KairosTonalButton
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Étape du cycle GTD là où elle a lieu (docs/spec/methode-gtd.md). Libellés entiers, sans format : « Étapes 2 et 3 » et
 * l'ordre des mots changent d'une langue à l'autre.
 */
enum class GtdStep(val label: StringResource) {
    CAPTURE(Res.string.gtd_label_capture),
    PROCESS(Res.string.gtd_label_process),
    REFLECT(Res.string.gtd_label_reflect),
    ENGAGE(Res.string.gtd_label_engage),
}

/** Ligne d'étape cliquable : elle ouvre le dialogue de la méthode (cible tactile de 48 dp par l'enveloppe). */
@Composable
fun GtdStepButton(step: GtdStep, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    KairosTextButton(onClick = { open = true }, contentPadding = KairosButtonIconPadding, modifier = modifier) {
        Icon(KairosIcons.Info, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(step.label), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
    }
    if (open) GtdDialog { open = false }
}

/** La même ligne sans action : sur la carte teintée « Maintenant », où un bouton texte se perd. */
@Composable
fun GtdStepText(step: GtdStep, style: TextStyle = MaterialTheme.typography.labelMedium, color: Color = Color.Unspecified) =
    Text(stringResource(step.label), style = style, color = color)

/** Bouton « La méthode GTD » de l'écran Notes (issue #235). */
@Composable
fun GtdMethodButton() {
    var open by remember { mutableStateOf(false) }
    KairosTonalButton(onClick = { open = true }, contentPadding = KairosButtonIconPadding) {
        Icon(KairosIcons.Help, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(Res.string.gtd_open), modifier = Modifier.padding(start = 6.dp))
    }
    if (open) GtdDialog { open = false }
}

private val STEPS = listOf(
    Res.string.gtd_step_capture_name to Res.string.gtd_step_capture_body,
    Res.string.gtd_step_clarify_name to Res.string.gtd_step_clarify_body,
    Res.string.gtd_step_organize_name to Res.string.gtd_step_organize_body,
    Res.string.gtd_step_reflect_name to Res.string.gtd_step_reflect_body,
    Res.string.gtd_step_engage_name to Res.string.gtd_step_engage_body,
)

/** La méthode : le principe, les cinq étapes et où elles sont dans Kairos, puis ce que Kairos fait autrement. */
@Composable
fun GtdDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(KairosIcons.Help, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(Res.string.gtd_title), modifier = Modifier.heading()) },
        text = {
            // Défile : sur un téléphone en paysage, tout reste lisible.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.gtd_intro), style = MaterialTheme.typography.bodyMedium)
                STEPS.forEachIndexed { index, (name, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${index + 1}. ${stringResource(name)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.heading())
                        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(Res.string.gtd_diff_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp).heading())
                listOf(Res.string.gtd_diff_order, Res.string.gtd_diff_someday, Res.string.gtd_diff_review).forEach {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.help_close)) } },
    )
}
