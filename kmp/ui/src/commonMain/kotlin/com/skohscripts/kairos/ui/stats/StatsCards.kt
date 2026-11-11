package com.skohscripts.kairos.ui.stats

import com.skohscripts.kairos.ui.generated.resources.stats_hours_few_tasks
import com.skohscripts.kairos.ui.generated.resources.stats_hours_even
import com.skohscripts.kairos.ui.generated.resources.stats_hours_fastest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.stats.BestHours
import com.skohscripts.kairos.core.stats.StatsTrends
import com.skohscripts.kairos.core.stats.TaskStats
import com.skohscripts.kairos.ui.generated.resources.stats_bias_trend_title
import com.skohscripts.kairos.ui.generated.resources.stats_tracked_title
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
import com.skohscripts.kairos.ui.generated.resources.stats_flow_age
import com.skohscripts.kairos.ui.generated.resources.stats_flow_hint
import com.skohscripts.kairos.ui.generated.resources.stats_flow_overdue
import com.skohscripts.kairos.ui.generated.resources.stats_flow_stale
import com.skohscripts.kairos.ui.generated.resources.stats_flow_title
import com.skohscripts.kairos.ui.generated.resources.stats_flow_wip
import com.skohscripts.kairos.ui.generated.resources.stats_focus
import com.skohscripts.kairos.ui.generated.resources.stats_hours_empty
import com.skohscripts.kairos.ui.generated.resources.stats_hours_hint
import com.skohscripts.kairos.ui.generated.resources.stats_hours_peak
import com.skohscripts.kairos.ui.generated.resources.stats_hours_share
import com.skohscripts.kairos.ui.generated.resources.stats_hours_slot
import com.skohscripts.kairos.ui.generated.resources.stats_hours_spread
import com.skohscripts.kairos.ui.generated.resources.stats_hours_title
import com.skohscripts.kairos.ui.generated.resources.stats_hours_top
import com.skohscripts.kairos.ui.generated.resources.stats_key_figures
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round

/**
 * Les neuf cartes de l'écran Stats, dans l'ordre de l'écran (docs/spec/statistiques.md § Export à l'écran) : l'écran et
 * l'image exportée d'une carte les dessinent par le même [StatsCardView]. [fileKey] entre dans le nom du PNG.
 */
internal enum class StatsCard(val title: StringResource, val fileKey: String) {
    KEY_FIGURES(Res.string.stats_key_figures, "key-figures"),
    // Juste après les chiffres clés (docs/spec/statistiques.md § Ordre et cartes vides) : la seule carte qui appelle une action.
    BACKLOG(Res.string.stats_flow_title, "backlog"),
    THROUGHPUT(Res.string.stats_throughput_title, "throughput"),
    TRACKED(Res.string.stats_tracked_title, "tracked-time"),
    CALIBRATION(Res.string.stats_calibration_title, "calibration"),
    BIAS_TREND(Res.string.stats_bias_trend_title, "bias-trend"),
    TIME_BY_TYPE(Res.string.stats_types_title, "time-by-type"),
    HOURS(Res.string.stats_hours_title, "hours"),
    COMPLETENESS(Res.string.stats_completeness_title, "completeness"),
    ;

    /** Carte qui ne vit que du temps chronométré : sans aucun chrono, elles laissent place à une seule carte d'explication. */
    val timed: Boolean get() = this == TRACKED || this == CALIBRATION || this == BIAS_TREND || this == TIME_BY_TYPE || this == HOURS
}

/** Rien de chronométré dans ce que l'écran retient : les cartes [StatsCard.timed] seraient toutes vides. */
internal fun nothingTimed(stats: TaskStats.Dashboard, hours: BestHours.Result, trends: StatsTrends.Result): Boolean =
    !trends.hasTracked && !trends.hasBias && stats.calibration.isEmpty() && stats.bias == null && stats.timeByType.isEmpty() && !hours.hasData

/**
 * Une carte des Stats. Barres d'une seule teinte (le primaire) sur une piste neutre, valeur toujours écrite à côté :
 * l'information ne repose jamais sur la couleur seule. Chaque agrégat montre son effectif ; sous 3, « peu fiable ».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StatsCardView(card: StatsCard, stats: TaskStats.Dashboard, hours: BestHours.Result, trends: StatsTrends.Result, language: String) {
    when (card) {
        StatsCard.KEY_FIGURES -> TileFlow { tile ->
            StatTile(stats.completedInWindow.toString(), stringResource(Res.string.stats_kpi_done, stats.windowWeeks), tile)
            StatTile(duration(stats.trackedMinutesWindow), stringResource(Res.string.stats_kpi_tracked), tile)
            // Seuils : délai médian de plus de 7 j, moins de 70 % d'échéances tenues.
            StatTile(stats.flow.completionDelayDays?.toString() ?: "—", stringResource(Res.string.stats_kpi_delay), tile, warn = (stats.flow.completionDelayDays ?: 0) > 7)
            StatTile(stats.flow.deadlineHitPct?.let { stringResource(Res.string.stats_percent, it) } ?: "—", stringResource(Res.string.stats_kpi_deadlines), tile, warn = (stats.flow.deadlineHitPct ?: 100) < 70)
        }

        StatsCard.THROUGHPUT -> Panel(KairosIcons.TrendingUp, stringResource(Res.string.stats_throughput_title), stringResource(Res.string.stats_throughput_hint)) {
            val max = stats.throughput.maxOf { it.completed }
            stats.throughput.forEach { w ->
                BarRow(Dates.dayMonth(w.weekStart, language), fraction(w.completed, max), stringResource(Res.string.stats_throughput_row, w.completed, w.points))
            }
        }

        StatsCard.TRACKED -> TrackedCard(trends, language)

        StatsCard.BIAS_TREND -> BiasTrendCard(trends, language)

        StatsCard.CALIBRATION -> Panel(KairosIcons.Schedule, stringResource(Res.string.stats_calibration_title), stringResource(Res.string.stats_calibration_hint)) {
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
                // Biais important (hors ×0,8–×1,25) : icône d'alerte, comme le badge « à surveiller ».
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

        StatsCard.TIME_BY_TYPE -> Panel(KairosIcons.Layers, stringResource(Res.string.stats_types_title), stringResource(Res.string.stats_types_hint, stats.windowWeeks)) {
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

        StatsCard.HOURS -> Panel(KairosIcons.Schedule, stringResource(Res.string.stats_hours_title), stringResource(Res.string.stats_hours_hint, stats.windowWeeks)) {
            if (!hours.hasData) {
                Muted(stringResource(Res.string.stats_hours_empty))
            } else {
                val noType = stringResource(Res.string.stats_no_type)
                hours.profiles.forEach { profile -> HoursProfile(profile, profile.type.ifEmpty { noType }, language) }
            }
        }

        StatsCard.BACKLOG -> Panel(KairosIcons.Inbox, stringResource(Res.string.stats_flow_title), stringResource(Res.string.stats_flow_hint)) {
            TileFlow { tile ->
                StatTile(stats.flow.openCount.toString(), stringResource(Res.string.stats_flow_wip), tile, nested = true)
                StatTile(stats.flow.medianAgeDays?.toString() ?: "—", stringResource(Res.string.stats_flow_age), tile, nested = true)
                StatTile(stats.flow.overdueCount.toString(), stringResource(Res.string.stats_flow_overdue), tile, warn = stats.flow.overdueCount > 0, nested = true)
                StatTile(stats.flow.staleCount.toString(), stringResource(Res.string.stats_flow_stale), tile, warn = stats.flow.staleCount > 0, nested = true)
            }
        }

        StatsCard.COMPLETENESS -> Panel(KairosIcons.CheckCircle, stringResource(Res.string.stats_completeness_title), stringResource(Res.string.stats_completeness_hint)) {
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

/**
 * Le profil horaire d'un type : son nom et ses effectifs (« peu fiable » sous le seuil, avec l'icône d'alerte), la tranche de pointe écrite
 * (part de son temps et lift), les trois plus grandes parts écrites, puis une frise de douze barres d'une seule teinte (une par tranche de
 * 2 h, de minuit à minuit). Aucune barre sans sa valeur écrite : la frise illustre le texte, elle ne le remplace pas.
 */
@Composable
private fun HoursProfile(profile: BestHours.Profile, name: String, language: String) {
    @Composable
    fun slot(bucket: Int) = stringResource(Res.string.stats_hours_slot, Dates.hour(BestHours.startHour(bucket), language), Dates.hour(BestHours.startHour(bucket) + BestHours.BUCKET_HOURS, language))
    val sample = stringResource(if (profile.reliable) Res.string.stats_sample else Res.string.stats_unreliable, profile.sessions) + " · " + duration(profile.minutes)
    val maxShare = profile.share.max().coerceAtLeast(0.0001)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f, fill = false))
            if (!profile.reliable) Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(sample, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val peak = profile.peak
        Text(
            if (peak != null) {
                stringResource(Res.string.stats_hours_peak, slot(peak), round(profile.share[peak] * 100).toInt(), oneDecimal(profile.lift[peak]))
            } else {
                stringResource(Res.string.stats_hours_spread)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        // Efficacité (v2, issue #105) : où le type se termine le plus vite au regard de son estimation.
        val fastest = profile.fastest
        Text(
            when {
                !profile.efficiencyReliable -> stringResource(Res.string.stats_hours_few_tasks, profile.efficiencyTasks, BestHours.MIN_EFFICIENCY_TASKS)
                fastest != null -> stringResource(
                    Res.string.stats_hours_fastest, slot(fastest), round((profile.efficiency[fastest]!! - 1) * 100).toInt(), profile.efficiencyTasks,
                )
                else -> stringResource(Res.string.stats_hours_even, profile.efficiencyTasks)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().height(36.dp).clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            profile.share.forEach { share ->
                Box(Modifier.weight(1f).height((36 * (share / maxShare)).coerceAtLeast(2.0).dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
            }
        }
        // Les barres portent leur valeur écrite : les trois plus grandes parts.
        val top = profile.share.indices.sortedByDescending { profile.share[it] }.take(3).filter { profile.share[it] > 0 }
        val topShares = top.map { stringResource(Res.string.stats_hours_share, slot(it), round(profile.share[it] * 100).toInt()) }
        Text(
            stringResource(Res.string.stats_hours_top, topShares.joinToString(" · ")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Largeur minimale d'une tuile de chiffre clé. */
private val TILE_MIN = 150.dp

/**
 * Quatre tuiles souples : 4 par ligne si elles tiennent, sinon 2 (1 si deux ne tiennent pas), jamais 3 + 1 avec une
 * tuile seule étirée (vu sur l'image d'une carte à 600 dp) ; même hauteur par ligne.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileFlow(content: @Composable FlowRowScope.(tile: Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = if (maxWidth >= TILE_MIN * 4 + 12.dp * 3) 4 else 2
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = perRow) {
            content(Modifier.weight(1f).widthIn(min = TILE_MIN).fillMaxRowHeight())
        }
    }
}

private fun oneDecimal(x: Double): String {
    val tenths = round(x * 10).toLong()
    return "${tenths / 10}.${tenths % 10}"
}

private fun fraction(value: Int, max: Int): Float = if (max <= 0) 0f else value.toFloat() / max

private fun twoDecimals(x: Double): String {
    val r = round(x * 100) / 100
    val whole = r.toLong()
    val cents = round((r - whole) * 100).toInt()
    return "$whole.${cents.toString().padStart(2, '0')}"
}
