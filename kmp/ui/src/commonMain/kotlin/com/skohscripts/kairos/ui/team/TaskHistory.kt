package com.skohscripts.kairos.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.team.QualifiedField
import com.skohscripts.kairos.core.team.TeamEvent
import com.skohscripts.kairos.core.team.TeamEventKind
import com.skohscripts.kairos.core.team.TeamEvents
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.event_assigned_to
import com.skohscripts.kairos.ui.generated.resources.event_assigned_to_from
import com.skohscripts.kairos.ui.generated.resources.event_category
import com.skohscripts.kairos.ui.generated.resources.event_created
import com.skohscripts.kairos.ui.generated.resources.event_deadline
import com.skohscripts.kairos.ui.generated.resources.event_deleted
import com.skohscripts.kairos.ui.generated.resources.event_done
import com.skohscripts.kairos.ui.generated.resources.event_line
import com.skohscripts.kairos.ui.generated.resources.event_line_titled
import com.skohscripts.kairos.ui.generated.resources.event_none
import com.skohscripts.kairos.ui.generated.resources.event_points
import com.skohscripts.kairos.ui.generated.resources.event_priority
import com.skohscripts.kairos.ui.generated.resources.event_progress
import com.skohscripts.kairos.ui.generated.resources.event_reopened
import com.skohscripts.kairos.ui.generated.resources.event_started
import com.skohscripts.kairos.ui.generated.resources.event_unassigned
import com.skohscripts.kairos.ui.generated.resources.event_unassigned_from
import com.skohscripts.kairos.ui.generated.resources.event_unknown
import com.skohscripts.kairos.ui.generated.resources.history_empty
import com.skohscripts.kairos.ui.generated.resources.progress_percent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Journal lisible d'une tâche d'équipe (docs/spec/equipe-backlog-suivi.md
 * § Journal) : un événement par ligne, « 30 sept. · Assignée à Léa (était :
 * Marc) », du plus récent au plus ancien ([TeamEvents.historyOf]). [context]
 * donne les noms des membres (archivés compris).
 */
@Composable
internal fun TaskHistory(events: List<TeamEvent>, context: AssignContext, today: LocalDate, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (events.isEmpty()) {
            Text(
                stringResource(Res.string.history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        events.forEach { event ->
            Text(
                eventLine(event, context, today, withTitle = false),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(vertical = 6.dp),
            )
        }
    }
}

/** « 30 sept. · phrase » ; avec [withTitle] (activité d'un membre) : « 30 sept. · Titre : phrase ». */
@Composable
internal fun eventLine(event: TeamEvent, context: AssignContext, today: LocalDate, withTitle: Boolean): String {
    val day = event.at.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val date = dateSpan(day, day, today, Locale.current.language).first
    val sentence = eventSentence(event, context, today)
    return if (withTitle) stringResource(Res.string.event_line_titled, date, event.taskTitle, sentence) else stringResource(Res.string.event_line, date, sentence)
}

/** La phrase d'un événement (sans date) : « Assignée à Léa (était : Marc) », « Priorité P2 → P1 »… */
@Composable
internal fun eventSentence(event: TeamEvent, context: AssignContext, today: LocalDate): String {
    val language = Locale.current.language
    val none = stringResource(Res.string.event_none)
    return when (event.kind) {
        TeamEventKind.CREATED -> stringResource(Res.string.event_created)
        TeamEventKind.STARTED -> stringResource(Res.string.event_started)
        TeamEventKind.DONE -> stringResource(Res.string.event_done)
        TeamEventKind.REOPENED -> stringResource(Res.string.event_reopened)
        TeamEventKind.DELETED -> stringResource(Res.string.event_deleted)
        TeamEventKind.UNKNOWN -> stringResource(Res.string.event_unknown)
        TeamEventKind.ASSIGNED -> {
            val to = context.name(event.toValue?.toLongOrNull())
            val from = context.name(event.fromValue?.toLongOrNull())
            when {
                to == null && from == null -> stringResource(Res.string.event_unassigned)
                to == null -> stringResource(Res.string.event_unassigned_from, from.orEmpty())
                from == null -> stringResource(Res.string.event_assigned_to, to)
                else -> stringResource(Res.string.event_assigned_to_from, to, from)
            }
        }
        TeamEventKind.PROGRESS -> stringResource(Res.string.event_progress, percent(event.fromValue), percent(event.toValue))
        TeamEventKind.QUALIFIED -> {
            val from = TeamEvents.parseQualified(event.fromValue)
            val to = TeamEvents.parseQualified(event.toValue)
            // Le champ vient du `toValue` (écrit avec le même préfixe des deux côtés) ; préfixe inconnu : « Modification ».
            val field = (to ?: from)?.first
            fun value(pair: Pair<QualifiedField, String>?): String {
                val raw = pair?.second.orEmpty()
                if (raw.isEmpty()) return none
                return when (field) {
                    QualifiedField.PRIORITY -> "P$raw"
                    QualifiedField.DEADLINE -> runCatching { LocalDate.parse(raw) }.getOrNull()?.let { dateSpan(it, it, today, language).first } ?: raw
                    else -> raw
                }
            }
            when (field) {
                QualifiedField.PRIORITY -> stringResource(Res.string.event_priority, value(from), value(to))
                QualifiedField.POINTS -> stringResource(Res.string.event_points, value(from), value(to))
                QualifiedField.TYPE -> stringResource(Res.string.event_category, value(from), value(to))
                QualifiedField.DEADLINE -> stringResource(Res.string.event_deadline, value(from), value(to))
                null -> stringResource(Res.string.event_unknown)
            }
        }
    }
}

@Composable
private fun percent(value: String?): String = stringResource(Res.string.progress_percent, value?.toIntOrNull() ?: 0)
