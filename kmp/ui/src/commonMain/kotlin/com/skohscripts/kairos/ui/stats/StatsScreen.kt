package com.skohscripts.kairos.ui.stats

import com.skohscripts.kairos.ui.app.heading
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.stats.TaskStats
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.day.duration
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.stats_bias
import com.skohscripts.kairos.ui.generated.resources.stats_bias_fair
import com.skohscripts.kairos.ui.generated.resources.stats_bias_over
import com.skohscripts.kairos.ui.generated.resources.stats_bias_under
import com.skohscripts.kairos.ui.generated.resources.stats_calibration_empty
import com.skohscripts.kairos.ui.generated.resources.stats_calibration_hint
import com.skohscripts.kairos.ui.generated.resources.stats_calibration_title
import com.skohscripts.kairos.ui.generated.resources.stats_completeness_estimate
import com.skohscripts.kairos.ui.generated.resources.stats_completeness_hint
import com.skohscripts.kairos.ui.generated.resources.stats_completeness_points
import com.skohscripts.kairos.ui.generated.resources.stats_completeness_row
import com.skohscripts.kairos.ui.generated.resources.stats_completeness_title
import com.skohscripts.kairos.ui.generated.resources.stats_completeness_type
import com.skohscripts.kairos.ui.generated.resources.stats_empty
import com.skohscripts.kairos.ui.generated.resources.stats_flow_age
import com.skohscripts.kairos.ui.generated.resources.stats_flow_hint
import com.skohscripts.kairos.ui.generated.resources.stats_flow_overdue
import com.skohscripts.kairos.ui.generated.resources.stats_flow_stale
import com.skohscripts.kairos.ui.generated.resources.stats_flow_title
import com.skohscripts.kairos.ui.generated.resources.stats_flow_wip
import com.skohscripts.kairos.ui.generated.resources.stats_focus
import com.skohscripts.kairos.ui.generated.resources.stats_kpi_deadlines
import com.skohscripts.kairos.ui.generated.resources.stats_kpi_delay
import com.skohscripts.kairos.ui.generated.resources.stats_kpi_done
import com.skohscripts.kairos.ui.generated.resources.stats_kpi_tracked
import com.skohscripts.kairos.ui.generated.resources.stats_no_type
import com.skohscripts.kairos.ui.generated.resources.stats_percent
import com.skohscripts.kairos.ui.generated.resources.stats_sample
import com.skohscripts.kairos.ui.generated.resources.stats_throughput_hint
import com.skohscripts.kairos.ui.generated.resources.stats_throughput_row
import com.skohscripts.kairos.ui.generated.resources.stats_throughput_title
import com.skohscripts.kairos.ui.generated.resources.stats_types_empty
import com.skohscripts.kairos.ui.generated.resources.stats_types_hint
import com.skohscripts.kairos.ui.generated.resources.stats_types_row
import com.skohscripts.kairos.ui.generated.resources.stats_types_title
import com.skohscripts.kairos.ui.generated.resources.stats_unreliable
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round

/**
 * Statistiques (docs/spec/statistiques.md), lecture seule : chiffres clés
 * de la fenêtre récente, débit hebdomadaire, calibration de l'estimation et
 * biais, répartition du temps par type et focus, flux et backlog, complétude.
 * Chaque agrégat montre son effectif ; sous 3, « peu fiable ». Barres d'une
 * seule teinte (le primaire) sur une piste neutre, valeur toujours écrite à
 * côté : l'information ne repose jamais sur la couleur seule.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(services: AppServices) {
    val snapshot by services.repository.personalSnapshot.collectAsState()
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val now = services.clock.now()
    val today = now.toLocalDateTime(timeZone).date
    val language = Locale.current.language
    val stats = remember(snapshot, today) { TaskStats.dashboard(snapshot.tasks, snapshot.workSessions, today, snapshot.settings, now, timeZone) }

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!stats.hasAnyData) {
                Text(stringResource(Res.string.stats_empty), style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            // Tuiles souples : 4 par ligne au large, 2 sur un téléphone, jamais une seule étirée.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val tile = Modifier.weight(1f).widthIn(min = 150.dp)
                StatTile(stats.completedInWindow.toString(), stringResource(Res.string.stats_kpi_done, stats.windowWeeks), tile)
                StatTile(duration(stats.trackedMinutesWindow), stringResource(Res.string.stats_kpi_tracked), tile)
                // Seuils de Kairos 2 : délai médian de plus de 7 j, moins de 70 % d'échéances tenues.
                StatTile(stats.flow.completionDelayDays?.toString() ?: "—", stringResource(Res.string.stats_kpi_delay), tile, warn = (stats.flow.completionDelayDays ?: 0) > 7)
                StatTile(stats.flow.deadlineHitPct?.let { stringResource(Res.string.stats_percent, it) } ?: "—", stringResource(Res.string.stats_kpi_deadlines), tile, warn = (stats.flow.deadlineHitPct ?: 100) < 70)
            }

            Panel(KairosIcons.TrendingUp, stringResource(Res.string.stats_throughput_title), stringResource(Res.string.stats_throughput_hint)) {
                val max = stats.throughput.maxOf { it.completed }
                stats.throughput.forEach { w ->
                    BarRow(Dates.dayMonth(w.weekStart, language), fraction(w.completed, max), stringResource(Res.string.stats_throughput_row, w.completed, w.points))
                }
            }

            Panel(KairosIcons.Schedule, stringResource(Res.string.stats_calibration_title), stringResource(Res.string.stats_calibration_hint)) {
                if (stats.calibration.isEmpty()) {
                    Muted(stringResource(Res.string.stats_calibration_empty))
                } else {
                    val max = stats.calibration.maxOf { it.medianMinutes ?: 0 }
                    stats.calibration.forEach { c ->
                        val sample = stringResource(if (c.reliable) Res.string.stats_sample else Res.string.stats_unreliable, c.count)
                        BarRow("${c.key} pts", fraction(c.medianMinutes ?: 0, max), "${duration(c.medianMinutes ?: 0)} · $sample", warn = !c.reliable)
                    }
                }
                stats.bias?.let { b ->
                    val verdict = when {
                        b.ratio > 1.1 -> Res.string.stats_bias_under
                        b.ratio < 0.9 -> Res.string.stats_bias_over
                        else -> Res.string.stats_bias_fair
                    }
                    val sample = stringResource(if (b.reliable) Res.string.stats_sample else Res.string.stats_unreliable, b.count)
                    // Biais important (hors ×0,8–×1,25) : icône d'alerte, comme le badge « à surveiller » de Kairos 2.
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        if (b.ratio > 1.25 || b.ratio < 0.8) {
                            Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                        }
                        Text(
                            stringResource(Res.string.stats_bias, twoDecimals(b.ratio), duration(b.realMinutes), duration(b.estimatedMinutes), sample) +
                                " " + stringResource(verdict),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Panel(KairosIcons.Layers, stringResource(Res.string.stats_types_title), stringResource(Res.string.stats_types_hint, stats.windowWeeks)) {
                if (stats.timeByType.isEmpty()) {
                    Muted(stringResource(Res.string.stats_types_empty))
                } else {
                    val noType = stringResource(Res.string.stats_no_type)
                    stats.timeByType.forEach { t ->
                        BarRow(t.key.ifEmpty { noType }, t.pct / 100f, stringResource(Res.string.stats_types_row, duration(t.minutes), t.pct))
                    }
                    stats.focus.avgSessionMinutes?.let {
                        Text(stringResource(Res.string.stats_focus, stats.focus.sessionCount, duration(it)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            Panel(KairosIcons.Inbox, stringResource(Res.string.stats_flow_title), stringResource(Res.string.stats_flow_hint)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val tile = Modifier.weight(1f).widthIn(min = 150.dp)
                    StatTile(stats.flow.openCount.toString(), stringResource(Res.string.stats_flow_wip), tile)
                    StatTile(stats.flow.medianAgeDays?.toString() ?: "—", stringResource(Res.string.stats_flow_age), tile)
                    StatTile(stats.flow.overdueCount.toString(), stringResource(Res.string.stats_flow_overdue), tile, warn = stats.flow.overdueCount > 0)
                    StatTile(stats.flow.staleCount.toString(), stringResource(Res.string.stats_flow_stale), tile, warn = stats.flow.staleCount > 0)
                }
            }

            Panel(KairosIcons.CheckCircle, stringResource(Res.string.stats_completeness_title), stringResource(Res.string.stats_completeness_hint)) {
                val c = stats.completeness
                listOf(
                    Triple(Res.string.stats_completeness_points, c.pointsPct, c.withPoints),
                    Triple(Res.string.stats_completeness_estimate, c.estimatePct, c.withEstimate),
                    Triple(Res.string.stats_completeness_type, c.typePct, c.withType),
                ).forEach { (label, pct, n) ->
                    BarRow(stringResource(label), pct / 100f, stringResource(Res.string.stats_completeness_row, pct, n, c.total))
                }
            }
        }
    }
}

private fun fraction(value: Int, max: Int): Float = if (max <= 0) 0f else value.toFloat() / max

private fun twoDecimals(x: Double): String {
    val r = round(x * 100) / 100
    val whole = r.toLong()
    val cents = round((r - whole) * 100).toInt()
    return "$whole.${cents.toString().padStart(2, '0')}"
}

/** Tuile de chiffre clé : la valeur en grand, son libellé dessous. Un seuil franchi prend un contour, jamais une couleur seule. */
@Composable
private fun StatTile(value: String, label: String, modifier: Modifier, warn: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = if (warn) androidx.compose.foundation.BorderStroke(1.dp, scheme.outline) else null,
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (warn) Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Panel(icon: ImageVector, title: String, hint: String, content: @Composable () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
            }
            Muted(hint)
            content()
        }
    }
}

/** Barre horizontale : libellé, piste neutre remplie au primaire (bouts arrondis de 4 dp), valeur écrite. */
@Composable
private fun BarRow(label: String, fraction: Float, value: String, warn: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp))
        Box(Modifier.weight(1f).height(12.dp).background(scheme.surfaceContainerHighest, RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(scheme.primary, RoundedCornerShape(4.dp)))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(min = 110.dp, max = 200.dp)) {
            if (warn) Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(14.dp).padding(end = 2.dp))
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
