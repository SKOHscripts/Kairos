package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_done
import com.skohscripts.kairos.ui.generated.resources.action_edit
import com.skohscripts.kairos.ui.generated.resources.action_undo
import com.skohscripts.kairos.ui.generated.resources.deadline_badge
import com.skohscripts.kairos.ui.generated.resources.minutes_badge
import com.skohscripts.kairos.ui.generated.resources.points_badge
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.LocalKairosExtraColors
import org.jetbrains.compose.resources.stringResource

/**
 * Ligne de tâche en colonnes fixes (docs/spec/vue-jour-gtd.md § Anatomie,
 * issue #33) : [coche] [corps : titre, étiquettes, extrait de description]
 * [priorité et points] [actions]. Le corps grandit vers le bas, jamais vers
 * la droite : les colonnes de droite restent alignées d'une ligne à l'autre.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskRow(task: Task, onToggleDone: () -> Unit, onEdit: () -> Unit, extra: @Composable () -> Unit = {}) {
    val done = task.status == TaskStatus.DONE
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconButton(onClick = onToggleDone) {
                    Icon(
                        if (done) KairosIcons.CheckCircleFilled else KairosIcons.RadioUnchecked,
                        contentDescription = stringResource(if (done) Res.string.action_undo else Res.string.action_done),
                        tint = if (done) LocalKairosExtraColors.current.ok else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    val tags = buildList {
                        if (task.projectTag.isNotEmpty()) add(task.projectTag)
                        if (task.taskType.isNotEmpty()) add(task.taskType)
                        task.deadline?.let { add(stringResource(Res.string.deadline_badge, Dates.short(it, Locale.current.language))) }
                        task.estimatedMinutes?.let { add(stringResource(Res.string.minutes_badge, it)) }
                    }
                    if (tags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            tags.forEach { Badge(it) }
                        }
                    }
                    if (task.description.isNotBlank()) {
                        Text(
                            task.description.lineSequence().first { it.isNotBlank() },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(Modifier.padding(top = 10.dp, start = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.priority?.let { PriorityBadge(it) }
                    task.fibonacciPoints?.let { Badge(stringResource(Res.string.points_badge, it)) }
                }
                IconButton(onClick = onEdit) {
                    Icon(KairosIcons.Edit, contentDescription = stringResource(Res.string.action_edit))
                }
            }
            extra()
        }
    }
}

/** Badge neutre en pilule (charte § Badges). */
@Composable
internal fun Badge(text: String, container: Color = MaterialTheme.colorScheme.surfaceContainerHighest, content: Color = MaterialTheme.colorScheme.onSurfaceVariant, bold: Boolean = false) {
    Surface(color = container, contentColor = content, shape = CircleShape) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (bold) FontWeight.Bold else null,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Priorité : seule P0 est rouge (conteneur d'erreur), P1/P2 neutres en gras. */
@Composable
internal fun PriorityBadge(priority: Int) {
    val scheme = MaterialTheme.colorScheme
    if (priority == 0) {
        Badge("P0", scheme.errorContainer, scheme.onErrorContainer, bold = true)
    } else {
        Badge("P$priority", content = scheme.onSurface, bold = true)
    }
}
