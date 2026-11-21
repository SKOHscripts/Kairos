package com.skohscripts.kairos.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.model.Interruption
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.stats.InterruptionStats
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.ui.app.LocalShellScope
import com.skohscripts.kairos.ui.app.LocalUndoMessages
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.day.InterruptionEditDialog
import com.skohscripts.kairos.ui.day.duration
import com.skohscripts.kairos.ui.day.label
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_revert
import com.skohscripts.kairos.ui.generated.resources.interruption_delete
import com.skohscripts.kairos.ui.generated.resources.interruption_deleted
import com.skohscripts.kairos.ui.generated.resources.interruption_edit
import com.skohscripts.kairos.ui.generated.resources.interruption_source_none
import com.skohscripts.kairos.ui.generated.resources.spent_review_more
import com.skohscripts.kairos.ui.generated.resources.stats_interruptions_by_hour
import com.skohscripts.kairos.ui.generated.resources.stats_interruptions_by_source
import com.skohscripts.kairos.ui.generated.resources.stats_interruptions_by_weekday
import com.skohscripts.kairos.ui.generated.resources.stats_interruptions_calm
import com.skohscripts.kairos.ui.generated.resources.stats_interruptions_count
import com.skohscripts.kairos.ui.generated.resources.stats_interruptions_deep
import com.skohscripts.kairos.ui.generated.resources.stats_interruptions_deep_value
import com.skohscripts.kairos.ui.generated.resources.stats_interruptions_hint
import com.skohscripts.kairos.ui.generated.resources.stats_interruptions_lost
import com.skohscripts.kairos.ui.generated.resources.stats_interruptions_previous
import com.skohscripts.kairos.ui.generated.resources.stats_interruptions_recent
import com.skohscripts.kairos.ui.generated.resources.stats_interruptions_share
import com.skohscripts.kairos.ui.generated.resources.stats_interruptions_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.recap.Figure
import com.skohscripts.kairos.ui.recap.Section
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosTextButton
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * Carte « Dérangements » des Stats (docs/spec/derangements.md § Statistiques, issue #272), sur la période et les filtres de
 * l'écran : chiffres (nombre et tendance, temps perdu, part du temps mesuré, plus longue plage calme, deep work dérangé), quand
 * (heures, jours), d'où (sources), puis « Dérangements récents », chacun modifiable ou supprimable. Rien tant qu'aucun
 * dérangement n'a jamais été signalé. Hors de l'export en image ou en CSV des autres cartes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InterruptionCards(
    repository: KairosRepository,
    snapshot: KairosSnapshot,
    filter: DayFilter,
    start: LocalDate,
    end: LocalDate,
    now: Instant,
    language: String,
) {
    if (snapshot.interruptions.isEmpty()) return
    val tz = remember { TimeZone.currentSystemDefault() }
    val s = remember(snapshot, filter, start, end) { InterruptionStats.summary(snapshot, start, end, now, tz, filter) }
    val undo = LocalUndoMessages.current
    val local = rememberCoroutineScope()
    val scope = LocalShellScope.current ?: local
    var editing by remember { mutableStateOf<Interruption?>(null) }
    var showAll by remember { mutableStateOf(false) }
    Column(Modifier.testTag("interruption-stats")) {
        Panel(KairosIcons.FrontHand, stringResource(Res.string.stats_interruptions_title), stringResource(Res.string.stats_interruptions_hint)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(KairosSpacing.m),
                    verticalArrangement = Arrangement.spacedBy(KairosSpacing.s),
                    maxItemsInEachRow = if (maxWidth >= 560.dp) 4 else 2,
                ) {
                    val cell = Modifier.weight(1f)
                    Figure(s.count.toString(), stringResource(Res.string.stats_interruptions_count) + " · " + stringResource(Res.string.stats_interruptions_previous, s.previousCount), cell)
                    Figure(duration(s.minutes), stringResource(Res.string.stats_interruptions_lost), cell)
                    Figure(s.share?.let { "${(it * 100).roundToInt()} %" } ?: "—", stringResource(Res.string.stats_interruptions_share), cell)
                    Figure(s.longestCalmMedian?.let { duration(it) } ?: "—", stringResource(Res.string.stats_interruptions_calm), cell)
                }
            }
            if (s.deepWork > 0) Muted(stringResource(Res.string.stats_interruptions_deep_value, s.deepWorkInterrupted, s.deepWork) + " · " + stringResource(Res.string.stats_interruptions_deep))
            if (s.count > 0) {
                Section(stringResource(Res.string.stats_interruptions_by_hour), s.count, counted = false) {
                    val hours = s.byHour.indices.filter { s.byHour[it] > 0 }
                    val max = s.byHour.maxOrNull() ?: 0
                    // De la première à la dernière heure dérangée : les heures creuses entre elles se voient.
                    for (h in (hours.minOrNull() ?: 0)..(hours.maxOrNull() ?: 0)) {
                        BarRow("${h.toString().padStart(2, '0')} h", if (max > 0) s.byHour[h].toFloat() / max else 0f, s.byHour[h].toString())
                    }
                }
                Section(stringResource(Res.string.stats_interruptions_by_weekday), s.count, counted = false) {
                    val max = s.byWeekday.maxOrNull() ?: 0
                    s.byWeekday.forEachIndexed { d, n -> BarRow(Dates.weekdayName(d, language), if (max > 0) n.toFloat() / max else 0f, n.toString()) }
                }
                Section(stringResource(Res.string.stats_interruptions_by_source), s.count, counted = false) {
                    val max = s.bySource.values.maxOrNull() ?: 0
                    s.bySource.entries.sortedByDescending { it.value }.forEach { (source, n) ->
                        BarRow(stringResource(source?.label() ?: Res.string.interruption_source_none), if (max > 0) n.toFloat() / max else 0f, n.toString())
                    }
                }
                Section(stringResource(Res.string.stats_interruptions_recent), s.recent.size) {
                    val shown = if (showAll) s.recent else s.recent.take(RECENT)
                    shown.forEach { i ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("interruption-row-${i.id}")) {
                            val at = i.at.toLocalDateTime(tz)
                            val source = i.source?.let { stringResource(it.label()) } ?: stringResource(Res.string.interruption_source_none)
                            Text(
                                "${Dates.short(at.date, language)} ${Dates.time(at, language)} · ${duration(i.minutes)} · $source",
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { editing = i }) { Icon(KairosIcons.Edit, contentDescription = stringResource(Res.string.interruption_edit)) }
                            IconButton(onClick = {
                                scope.launch {
                                    repository.deleteInterruption(i.id)
                                    undo(getString(Res.string.interruption_deleted), getString(Res.string.action_revert)) { scope.launch { repository.restoreInterruption(i) } }
                                }
                            }, modifier = Modifier.testTag("interruption-delete-${i.id}")) {
                                Icon(KairosIcons.Delete, contentDescription = stringResource(Res.string.interruption_delete))
                            }
                        }
                    }
                    if (!showAll && s.recent.size > RECENT) {
                        KairosTextButton(onClick = { showAll = true }) { Text(stringResource(Res.string.spent_review_more, s.recent.size)) }
                    }
                }
            }
        }
    }
    editing?.let { i ->
        InterruptionEditDialog(stringResource(Res.string.interruption_edit), i, onSave = { m, source ->
            editing = null
            scope.launch { repository.updateInterruption(i.id, m, source) }
        }, onDismiss = { editing = null })
    }
}

/** Lignes de « Dérangements récents » avant « Afficher tout ». */
private const val RECENT = 5
