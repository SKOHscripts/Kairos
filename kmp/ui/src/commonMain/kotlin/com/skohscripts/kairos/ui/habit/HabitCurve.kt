package com.skohscripts.kairos.ui.habit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.habit.Habits
import com.skohscripts.kairos.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Ce que la fiche montre d'une habitude (docs/spec/habitudes.md § Interface) : [scores] les réponses de la série dans l'ordre,
 * [observedMinute] l'heure observée de la série (préremplissage de l'heure fixe), ou `null`.
 */
data class HabitSheet(val scores: List<Int>, val observedMinute: Int?)

/**
 * Courbe des réponses d'une habitude (1 à 7), dans l'ordre : une ligne neutre et ses points, sans couleur de statut (la charte
 * réserve la couleur ; le statut est dit en toutes lettres à côté). Annoncée au lecteur d'écran avec les valeurs.
 */
@Composable
fun HabitCurve(scores: List<Int>, modifier: Modifier = Modifier, height: Dp = 64.dp) {
    if (scores.isEmpty()) {
        Text(stringResource(Res.string.habit_no_answer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(Res.string.habit_curve_description, scores.joinToString(", "))
    Canvas(
        modifier.fillMaxWidth().height(height).testTag("habit-curve").semantics { contentDescription = description },
    ) {
        val pad = 6.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        fun y(score: Int) = pad + h * (Habits.MAX_SCORE - score) / (Habits.MAX_SCORE - Habits.MIN_SCORE).toFloat()
        // Repères 1, 4 et 7.
        for (s in listOf(Habits.MIN_SCORE, 4, Habits.MAX_SCORE)) drawLine(grid, Offset(pad, y(s)), Offset(pad + w, y(s)), strokeWidth = 1.dp.toPx())
        val points = scores.mapIndexed { i, s ->
            Offset(pad + if (scores.size == 1) w / 2 else w * i / (scores.size - 1).toFloat(), y(s))
        }
        points.zipWithNext { a, b -> drawLine(line, a, b, strokeWidth = 2.dp.toPx()) }
        points.forEach { drawCircle(line, radius = 3.dp.toPx(), center = it) }
    }
}
