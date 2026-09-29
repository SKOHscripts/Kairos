package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.why_deadline_in
import com.skohscripts.kairos.ui.generated.resources.why_deadline_overdue
import com.skohscripts.kairos.ui.generated.resources.why_deadline_today
import com.skohscripts.kairos.ui.generated.resources.why_deadline_tomorrow
import com.skohscripts.kairos.ui.generated.resources.why_effort_default
import com.skohscripts.kairos.ui.generated.resources.why_effort_minutes
import com.skohscripts.kairos.ui.generated.resources.why_effort_points
import com.skohscripts.kairos.ui.generated.resources.why_no_date
import com.skohscripts.kairos.ui.generated.resources.why_note
import com.skohscripts.kairos.ui.generated.resources.why_overdue
import com.skohscripts.kairos.ui.generated.resources.why_priority
import com.skohscripts.kairos.ui.generated.resources.why_priority_none
import com.skohscripts.kairos.ui.generated.resources.why_scheduled_in
import com.skohscripts.kairos.ui.generated.resources.why_scheduled_overdue
import com.skohscripts.kairos.ui.generated.resources.why_scheduled_today
import com.skohscripts.kairos.ui.generated.resources.why_scheduled_tomorrow
import com.skohscripts.kairos.ui.generated.resources.why_score
import com.skohscripts.kairos.ui.generated.resources.why_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import org.jetbrains.compose.resources.stringResource

/**
 * Score WSJF en chiffre primaire, sans pastille colorée (charte) ; un clic ou
 * un toucher ouvre « Pourquoi à cette place ? » : la décomposition du score
 * (`wsjf_breakdown`), en menu MD3 flottant.
 */
@Composable
internal fun ScoreBadge(task: Task, why: Scheduling.WsjfBreakdown, language: String) {
    var open by remember { mutableStateOf(false) }
    val score = Dates.number(why.score)
    val title = stringResource(Res.string.why_title)
    Box {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier.clickable { open = true }.semantics { contentDescription = "$score · $title" },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Icon(KairosIcons.TrendingUp, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(score, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.width(300.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (why.overdue) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(KairosIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Text(stringResource(Res.string.why_overdue), style = MaterialTheme.typography.bodySmall)
                    }
                }
                val priority = task.priority?.let { "P$it ${stringResource(Levels.priorityName(it))}" }
                Term(priority?.let { stringResource(Res.string.why_priority, it) } ?: stringResource(Res.string.why_priority_none), Dates.number(why.value))
                Term(dateLabel(why), "+ ${Dates.number(why.criticality)}")
                val effort = when (why.effortSource) {
                    Scheduling.EffortSource.POINTS -> stringResource(Res.string.why_effort_points, Dates.number(why.effort))
                    Scheduling.EffortSource.MINUTES -> stringResource(Res.string.why_effort_minutes)
                    Scheduling.EffortSource.DEFAULT -> stringResource(Res.string.why_effort_default)
                }
                Term(effort, "÷ ${Dates.number(why.effort)}")
                HorizontalDivider()
                Term(stringResource(Res.string.why_score), "= $score", bold = true)
                Text(stringResource(Res.string.why_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun dateLabel(why: Scheduling.WsjfBreakdown): String {
    val days = why.daysUntil ?: return stringResource(Res.string.why_no_date)
    val deadline = why.dateKind == Scheduling.DateKind.DEADLINE
    return when {
        days < 0 -> stringResource(if (deadline) Res.string.why_deadline_overdue else Res.string.why_scheduled_overdue, -days)
        days == 0 -> stringResource(if (deadline) Res.string.why_deadline_today else Res.string.why_scheduled_today)
        days == 1 -> stringResource(if (deadline) Res.string.why_deadline_tomorrow else Res.string.why_scheduled_tomorrow)
        else -> stringResource(if (deadline) Res.string.why_deadline_in else Res.string.why_scheduled_in, days)
    }
}

@Composable
private fun Term(label: String, value: String, bold: Boolean = false) {
    val weight = if (bold) FontWeight.Bold else null
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = weight, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = weight)
    }
}
