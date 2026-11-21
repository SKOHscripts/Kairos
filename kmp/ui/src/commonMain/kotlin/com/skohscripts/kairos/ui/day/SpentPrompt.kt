package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.engine.SpentReview
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.action_save
import com.skohscripts.kairos.ui.generated.resources.spent_close
import com.skohscripts.kairos.ui.generated.resources.spent_other
import com.skohscripts.kairos.ui.generated.resources.spent_other_field
import com.skohscripts.kairos.ui.generated.resources.spent_other_help
import com.skohscripts.kairos.ui.generated.resources.spent_other_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosFilterChip
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.stringResource

/**
 * Question « Temps passé ? » à la coche (docs/spec/durees-reelles.md, issue #271), servie par la snackbar de la coquille : même
 * place, mêmes couleurs et même durée longue que « Annuler ». [onPick] reçoit les minutes choisies ; ignorer ou fermer n'appelle
 * rien (la tâche ira dans la revue du Bilan).
 */
class SpentQuestion(override val message: String, val onPick: (Int) -> Unit) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = true
    override val duration: SnackbarDuration = SnackbarDuration.Long
}

/** Pose et ferme la question « Temps passé ? » ; sans effet par défaut (écrans testés seuls), la coquille la fournit. */
interface SpentPrompts {
    fun ask(question: SpentQuestion)

    /** Ferme la question ouverte, s'il y en a une (coches groupées). */
    fun close()
}

val LocalSpentPrompts = staticCompositionLocalOf<SpentPrompts> {
    object : SpentPrompts {
        override fun ask(question: SpentQuestion) {}
        override fun close() {}
    }
}

/**
 * Les durées d'un toucher ([SpentReview.CHOICES]) puis « Autre… », en ligne qui passe à la ligne (360 dp sans débordement).
 * [onSurface] : couleur des libellés, celle du fond qui les porte (la surface inverse dans la snackbar) ; [trailing] : un dernier
 * élément sur la même ligne (« Ignorer » de la revue).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpentChips(
    onPick: (Int) -> Unit,
    onOther: () -> Unit,
    onSurface: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    border: Color? = null,
    tag: String = "spent",
    trailing: @Composable () -> Unit = {},
) {
    val colors = FilterChipDefaults.filterChipColors(labelColor = onSurface)
    val stroke = border?.let { BorderStroke(1.dp, it) } ?: FilterChipDefaults.filterChipBorder(enabled = true, selected = false)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpentReview.CHOICES.forEach { minutes ->
            KairosFilterChip(
                selected = false, onClick = { onPick(minutes) }, label = { Text(duration(minutes)) },
                colors = colors, border = stroke, modifier = Modifier.testTag("$tag-$minutes"),
            )
        }
        KairosFilterChip(
            selected = false, onClick = onOther, label = { Text(stringResource(Res.string.spent_other)) },
            colors = colors, border = stroke, modifier = Modifier.testTag("$tag-other"),
        )
        trailing()
    }
}

/** La snackbar de la coquille : la question « Temps passé ? » avec ses puces, sinon la snackbar Material ordinaire. */
@Composable
fun ShellSnackbar(data: SnackbarData, onOther: (SpentQuestion) -> Unit) {
    val question = data.visuals as? SpentQuestion
    if (question == null) {
        Snackbar(data)
        return
    }
    val scheme = MaterialTheme.colorScheme
    Snackbar(
        modifier = Modifier.testTag("spent-question"),
        dismissAction = {
            IconButton(onClick = { data.dismiss() }) {
                Icon(KairosIcons.Close, contentDescription = stringResource(Res.string.spent_close))
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(question.message)
            SpentChips(
                onPick = { data.dismiss(); question.onPick(it) },
                onOther = { data.dismiss(); onOther(question) },
                onSurface = scheme.inverseOnSurface,
                border = scheme.outline,
            )
        }
    }
}

/** « Autre… » : un nombre de minutes, de 1 à [SpentReview.MAX_MINUTES] ; « Enregistrer » le passe à [onSave]. */
@Composable
internal fun SpentOtherDialog(onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val minutes = text.trim().toIntOrNull()?.takeIf { it in 1..SpentReview.MAX_MINUTES }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.spent_other_title)) },
        text = {
            OutlinedTextField(
                text, { value -> text = value.filter { it.isDigit() }.take(4) },
                label = { Text(stringResource(Res.string.spent_other_field)) },
                supportingText = { Text(stringResource(Res.string.spent_other_help, SpentReview.MAX_MINUTES)) },
                isError = text.isNotEmpty() && minutes == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.testTag("spent-other-field"),
            )
        },
        confirmButton = {
            KairosButton(onClick = { minutes?.let(onSave) }, enabled = minutes != null, modifier = Modifier.testTag("spent-other-save")) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
