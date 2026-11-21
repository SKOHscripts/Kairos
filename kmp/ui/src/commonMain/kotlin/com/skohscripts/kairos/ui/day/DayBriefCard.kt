package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.day.DayBrief
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_snooze_labelled
import com.skohscripts.kairos.ui.generated.resources.day_brief_available
import com.skohscripts.kairos.ui.generated.resources.day_brief_available_effective
import com.skohscripts.kairos.ui.generated.resources.day_brief_day_off
import com.skohscripts.kairos.ui.generated.resources.day_brief_deadlines
import com.skohscripts.kairos.ui.generated.resources.day_brief_deep_work
import com.skohscripts.kairos.ui.generated.resources.day_brief_done
import com.skohscripts.kairos.ui.generated.resources.day_brief_empty
import com.skohscripts.kairos.ui.generated.resources.day_brief_fits
import com.skohscripts.kairos.ui.generated.resources.day_brief_fits_plain
import com.skohscripts.kairos.ui.generated.resources.day_brief_fold
import com.skohscripts.kairos.ui.generated.resources.day_brief_postpone
import com.skohscripts.kairos.ui.generated.resources.day_brief_postpone_plain
import com.skohscripts.kairos.ui.generated.resources.day_brief_reserve
import com.skohscripts.kairos.ui.generated.resources.day_brief_tasks_day_many
import com.skohscripts.kairos.ui.generated.resources.day_brief_tasks_day_one
import com.skohscripts.kairos.ui.generated.resources.day_brief_tasks_rest_many
import com.skohscripts.kairos.ui.generated.resources.day_brief_tasks_rest_one
import com.skohscripts.kairos.ui.generated.resources.day_brief_title_day
import com.skohscripts.kairos.ui.generated.resources.day_brief_title_rest
import com.skohscripts.kairos.ui.generated.resources.day_brief_unfold
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.stringResource

/**
 * « Ta journée » (docs/spec/ta-journee.md, issue #274) : en tête de la vue Jour, une carte pleine en conteneur secondaire (le
 * second bloc teinté de l'écran, plus doux que « Maintenant »), toujours là ; repliée, une ligne (le verdict). Elle présente la
 * journée le matin, puis le reste de la journée, et reprend les bilans qu'affichait « Maintenant ».
 */
@Composable
internal fun DayBriefCard(
    brief: DayBrief,
    language: String,
    folded: Boolean,
    onFold: (Boolean) -> Unit,
    onReserve: (DayBrief.DeepWork) -> Unit,
    onSnooze: (Task) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("day-brief"),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(KairosIcons.Today, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(if (brief.phase == DayBrief.Phase.DAY) Res.string.day_brief_title_day else Res.string.day_brief_title_rest),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = KairosSpacing.s).heading(),
                )
                IconButton(onClick = { onFold(!folded) }, modifier = Modifier.testTag("day-brief-fold")) {
                    Icon(
                        if (folded) KairosIcons.ExpandMore else KairosIcons.ExpandLess,
                        contentDescription = stringResource(if (folded) Res.string.day_brief_unfold else Res.string.day_brief_fold),
                    )
                }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(KairosSpacing.xs)) {
                if (!folded) {
                    lines(brief).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                Text(verdict(brief, language), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("day-brief-verdict"))
                if (!folded) {
                    brief.deepWork?.let { dw ->
                        KairosTextButton(onClick = { onReserve(dw) }, contentPadding = KairosButtonIconPadding, modifier = Modifier.testTag("day-brief-reserve")) {
                            Icon(KairosIcons.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(Res.string.day_brief_reserve, duration(dw.minutes), Dates.time(dw.start, language)), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    brief.postpone?.let { task ->
                        KairosTextButton(onClick = { onSnooze(task) }, contentPadding = KairosButtonIconPadding) {
                            Icon(KairosIcons.Redo, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(Res.string.action_snooze_labelled), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Les lignes de la carte dépliée : tâches et estimation, bilan du jour, échéances, disponible. Rien pour un jour vide. */
@Composable
private fun lines(brief: DayBrief): List<String> {
    if (brief.verdict == DayBrief.Verdict.EMPTY) return emptyList()
    val out = mutableListOf<String>()
    if (brief.tasks > 0) {
        out += when {
            brief.phase == DayBrief.Phase.DAY && brief.tasks == 1 -> stringResource(Res.string.day_brief_tasks_day_one, duration(brief.estimateMinutes))
            brief.phase == DayBrief.Phase.DAY -> stringResource(Res.string.day_brief_tasks_day_many, brief.tasks, duration(brief.estimateMinutes))
            brief.tasks == 1 -> stringResource(Res.string.day_brief_tasks_rest_one, duration(brief.estimateMinutes))
            else -> stringResource(Res.string.day_brief_tasks_rest_many, brief.tasks, duration(brief.estimateMinutes))
        }
    }
    if (brief.done > 0 || brief.workedMinutes > 0) {
        val parts = mutableListOf<String>()
        for ((type, minutes) in brief.workedByType) parts += "$type ${duration(minutes)}"
        val byType = parts.joinToString(" · ")
        out += stringResource(Res.string.day_brief_done, brief.done, duration(brief.workedMinutes)) + if (byType.isEmpty()) "" else " ($byType)"
    }
    if (brief.deadlinesToday > 0 || brief.deadlinesWeek > 0) out += stringResource(Res.string.day_brief_deadlines, brief.deadlinesToday, brief.deadlinesWeek)
    if (brief.verdict != DayBrief.Verdict.DAY_OFF) {
        out += if (brief.interruptionMinutes > 0) {
            stringResource(Res.string.day_brief_available_effective, duration(brief.availableMinutes), duration(brief.interruptionMinutes), duration(brief.effectiveMinutes))
        } else {
            stringResource(Res.string.day_brief_available, duration(brief.availableMinutes))
        }
    }
    return out
}

/** Le verdict, la ligne qui reste quand la carte est repliée. */
@Composable
private fun verdict(brief: DayBrief, language: String): String = when (brief.verdict) {
    DayBrief.Verdict.FITS -> brief.dayEnd?.let { stringResource(Res.string.day_brief_fits, Dates.time(it, language)) } ?: stringResource(Res.string.day_brief_fits_plain)
    DayBrief.Verdict.DEEP_WORK -> stringResource(Res.string.day_brief_deep_work, duration(brief.overflowMinutes))
    DayBrief.Verdict.POSTPONE -> brief.postpone?.let { stringResource(Res.string.day_brief_postpone, duration(brief.overflowMinutes), it.title) }
        ?: stringResource(Res.string.day_brief_postpone_plain, duration(brief.overflowMinutes))
    DayBrief.Verdict.EMPTY -> stringResource(Res.string.day_brief_empty)
    DayBrief.Verdict.DAY_OFF -> stringResource(Res.string.day_brief_day_off)
}
