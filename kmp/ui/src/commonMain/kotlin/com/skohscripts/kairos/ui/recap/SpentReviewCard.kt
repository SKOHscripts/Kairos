package com.skohscripts.kairos.ui.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.skohscripts.kairos.core.engine.SpentReview
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.ui.app.LocalShellScope
import com.skohscripts.kairos.ui.app.LocalUndoMessages
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.day.SpentChips
import com.skohscripts.kairos.ui.day.SpentOtherDialog
import com.skohscripts.kairos.ui.day.durationText
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_revert
import com.skohscripts.kairos.ui.generated.resources.spent_ignored
import com.skohscripts.kairos.ui.generated.resources.spent_noted
import com.skohscripts.kairos.ui.generated.resources.spent_review_hint
import com.skohscripts.kairos.ui.generated.resources.spent_review_ignore
import com.skohscripts.kairos.ui.generated.resources.spent_review_line
import com.skohscripts.kairos.ui.generated.resources.spent_review_more
import com.skohscripts.kairos.ui.generated.resources.spent_review_title_many
import com.skohscripts.kairos.ui.generated.resources.spent_review_title_one
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.stats.Panel
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosTextButton
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Revue des tâches faites sans temps (docs/spec/durees-reelles.md, issue #271), en tête du Bilan sur la période en cours : une
 * ligne par tâche (titre, jour de fin, durées d'un toucher, « Autre… », « Ignorer »), [SHOWN] d'abord. Une durée enregistre le
 * temps saisi ; « Ignorer » pose 0 min (« connu, rien à compter »). Chaque geste a son « Annuler ». Rien quand [tasks] est vide.
 */
@Composable
internal fun SpentReviewCard(repository: KairosRepository, tasks: List<Task>, today: LocalDate, language: String) {
    if (tasks.isEmpty()) return
    val undo = LocalUndoMessages.current
    val local = rememberCoroutineScope()
    val scope = LocalShellScope.current ?: local
    val timeZone = remember { TimeZone.currentSystemDefault() }
    var all by remember { mutableStateOf(false) }
    var other by remember { mutableStateOf<Task?>(null) }
    val revert = stringResource(Res.string.action_revert)
    val note: (Task, Int) -> Unit = { task, minutes ->
        scope.launch {
            repository.setSpent(task.id, minutes)
            val message = if (minutes == 0) getString(Res.string.spent_ignored, task.title) else getString(Res.string.spent_noted, durationText(minutes))
            undo(message, revert) { scope.launch { repository.setSpent(task.id, null) } }
        }
    }
    val title = if (tasks.size == 1) stringResource(Res.string.spent_review_title_one) else stringResource(Res.string.spent_review_title_many, tasks.size)
    Column(Modifier.testTag("spent-review")) {
        Panel(KairosIcons.Schedule, title, stringResource(Res.string.spent_review_hint)) {
            val shown = if (all) tasks else tasks.take(SHOWN)
            shown.forEach { task ->
                Column(verticalArrangement = Arrangement.spacedBy(KairosSpacing.xs)) {
                    Text(
                        stringResource(Res.string.spent_review_line, task.title, Dates.short(SpentReview.doneOn(task, timeZone), language, today)),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SpentChips(onPick = { note(task, it) }, onOther = { other = task }, tag = "review-${task.id}") {
                        KairosTextButton(onClick = { note(task, 0) }, modifier = Modifier.testTag("review-${task.id}-ignore")) {
                            Text(stringResource(Res.string.spent_review_ignore))
                        }
                    }
                }
            }
            if (!all && tasks.size > SHOWN) {
                KairosTextButton(onClick = { all = true }) { Text(stringResource(Res.string.spent_review_more, tasks.size)) }
            }
        }
    }
    other?.let { task ->
        SpentOtherDialog(onSave = { minutes -> other = null; note(task, minutes) }, onDismiss = { other = null })
    }
}

/** Lignes visibles avant « Afficher tout ». */
private const val SHOWN = 5
