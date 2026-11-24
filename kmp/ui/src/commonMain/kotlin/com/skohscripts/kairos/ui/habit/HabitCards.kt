package com.skohscripts.kairos.ui.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.habit.HabitStatus
import com.skohscripts.kairos.core.habit.Habits
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.ui.generated.resources.*
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.stats.Panel
import org.jetbrains.compose.resources.stringResource

/**
 * Carte « Habitudes » des Statistiques (docs/spec/habitudes.md § Interface), après les dérangements, hors des cartes exportables
 * (ni PNG ni CSV) : une ligne par habitude active (titre, statut, dernière réponse, petite courbe). Rien si les habitudes sont
 * coupées ou s'il n'y en a aucune.
 */
@Composable
fun HabitCards(snapshot: KairosSnapshot) {
    if (!snapshot.settings.habitsOn) return
    val habits = remember(snapshot.tasks) { Habits.active(snapshot.tasks).map { it to Habits.scores(it, snapshot.tasks) } }
    if (habits.isEmpty()) return
    Panel(KairosIcons.SelfImprovement, stringResource(Res.string.habit_stats_title), stringResource(Res.string.habit_stats_hint)) {
        habits.forEach { (task, scores) ->
            Column(Modifier.testTag("habit-stats-row"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall)
                val status = stringResource(
                    if (Habits.status(scores) == HabitStatus.INSTALLED) Res.string.habit_status_installed else Res.string.habit_status_progress,
                )
                val last = scores.lastOrNull()?.let { stringResource(Res.string.habit_last_answer, it) }
                Text(listOfNotNull(status, last).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HabitCurve(scores, height = 32.dp)
            }
        }
    }
}
