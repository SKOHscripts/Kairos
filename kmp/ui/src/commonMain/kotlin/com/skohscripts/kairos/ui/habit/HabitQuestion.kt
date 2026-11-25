package com.skohscripts.kairos.ui.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.habit.Habits
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.*
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.size

/**
 * Question d'automatisme à la coche d'une habitude (docs/spec/habitudes.md § 3) : l'item validé « … c'est quelque chose que je
 * fais automatiquement » (Gardner, Lally et Rebar, 2024), de 1 « Pas du tout d'accord » à 7 « Tout à fait d'accord ». Sept
 * boutons de 48 dp au moins, qui passent à la ligne sur un téléphone étroit ; fermer = pas de réponse (la question revient à
 * l'occurrence suivante).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitQuestionDialog(title: String, onAnswer: (Int) -> Unit, onDismiss: () -> Unit) {
    val disagree = stringResource(Res.string.habit_scale_low)
    val agree = stringResource(Res.string.habit_scale_high)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("habit-question"),
        icon = { Icon(KairosIcons.SelfImprovement, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        title = { Text(stringResource(Res.string.habit_question, title), modifier = Modifier.heading()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (score in Habits.MIN_SCORE..Habits.MAX_SCORE) {
                        val label = when (score) {
                            Habits.MIN_SCORE -> "$score, $disagree"
                            Habits.MAX_SCORE -> "$score, $agree"
                            else -> score.toString()
                        }
                        KairosOutlinedButton(
                            onClick = { onAnswer(score) },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("habit-score-$score")
                                .semantics { contentDescription = label },
                        ) { Text(score.toString()) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1 · $disagree", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("7 · $agree", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                }
            }
        },
        confirmButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.habit_question_later)) } },
    )
}

/** Marque « Habitude » d'une ligne (docs/spec/habitudes.md § 8) : icône neutre de 16 dp, nommée pour le lecteur d'écran. */
@Composable
fun HabitMark() {
    Icon(
        KairosIcons.SelfImprovement,
        contentDescription = stringResource(Res.string.habit_mark),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp).testTag("habit-mark"),
    )
}
