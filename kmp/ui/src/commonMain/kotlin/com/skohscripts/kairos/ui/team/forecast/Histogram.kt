package com.skohscripts.kairos.ui.team.forecast

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.team.forecast.WeekBucket
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.forecast_histogram_lt1
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.roundToInt

/** Un repère de l'histogramme : son [label] (« P50 ») et la [date] de ce percentile (`null` : hors horizon, pas de trait). */
internal class HistogramMark(val label: String, val date: LocalDate?)

/** Géométrie de l'histogramme, pure donc testable. */
internal object HistogramLayout {
    /**
     * Position horizontale de [date], en fraction (0 à 1) de la largeur totale : la semaine qui la contient occupe un
     * créneau, et la date s'y place au milieu de son jour. `null` si elle sort des semaines tracées.
     */
    fun position(buckets: List<WeekBucket>, date: LocalDate): Float? {
        if (buckets.isEmpty()) return null
        val offset = buckets.first().weekStart.daysUntil(date)
        if (offset < 0 || offset >= 7 * buckets.size) return null
        return (offset + 0.5f) / (7f * buckets.size)
    }

    /** Hauteur d'une barre : proportionnelle au plus grand créneau, au moins [minHeight] dès qu'il y a des tirages. */
    fun barHeight(draws: Int, maxDraws: Int, full: Dp, minHeight: Dp = 2.dp): Dp = when {
        draws <= 0 || maxDraws <= 0 -> 0.dp
        else -> max(full.value * draws / maxDraws, minHeight.value).dp
    }
}

private val SLOT_MIN = 40.dp
private val SLOT_MAX = 72.dp
private val PLOT_HEIGHT = 112.dp
private val VALUE_HEIGHT = 16.dp
private val MARK_ROW = 18.dp

/**
 * Histogramme des dates de fin par semaine (docs/spec/equipe-simulation.md § Interface) : une barre `Box` primaire à
 * bouts arrondis de 4 dp par semaine (la semaine commençant le lundi indiqué dessous), la part des [draws] tirages
 * écrite au-dessus, et les [marks] (P50, P85, P95) en traits `outline` avec leur libellé. Les barres ne portent pas
 * l'information seules : la valeur est écrite. Sous 40 dp par semaine (72 dp au plus), le graphique défile **dans** sa carte.
 * [description] : lecture complète pour un lecteur d'écran (le graphique, lui, est masqué).
 */
@Composable
internal fun Histogram(
    buckets: List<WeekBucket>,
    draws: Int,
    marks: List<HistogramMark>,
    language: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val maxDraws = buckets.maxOf { it.draws }
    val drawn = marks.filter { it.date != null && HistogramLayout.position(buckets, it.date) != null }
    val marksHeight = MARK_ROW * drawn.size
    val lessThanOne = stringResource(Res.string.forecast_histogram_lt1)
    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = description }) {
        // Un créneau fait 40 à 72 dp : peu de semaines, un graphique étroit ; beaucoup, il défile dans la carte.
        val width = (maxWidth.value / buckets.size).coerceIn(SLOT_MIN.value, SLOT_MAX.value).dp * buckets.size
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Box(Modifier.width(width)) {
                // Traits des percentiles, de leur libellé à l'axe, dessinés **sous** les barres et leurs valeurs :
                // tracés par-dessus, ils barraient le nombre d'une barre (« 4|1 », vu sur la capture des magasins).
                drawn.forEachIndexed { index, mark ->
                    val x = width * HistogramLayout.position(buckets, mark.date!!)!!
                    val top = MARK_ROW * index
                    Box(
                        Modifier.offset(x = x - 1.dp, y = top + MARK_ROW / 2)
                            .width(2.dp)
                            .height(marksHeight - top - MARK_ROW / 2 + PLOT_HEIGHT)
                            .background(scheme.outline),
                    )
                }
                Column {
                    Box(Modifier.height(marksHeight))
                    Row(Modifier.height(PLOT_HEIGHT)) {
                        buckets.forEach { bucket ->
                            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                                if (bucket.draws > 0) {
                                    val percent = percentOf(bucket.draws, draws)
                                    Text(
                                        if (percent == 0) lessThanOne else "$percent",
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.height(VALUE_HEIGHT).background(scheme.surface).padding(horizontal = 2.dp),
                                    )
                                }
                                Box(
                                    Modifier.width(24.dp)
                                        .height(HistogramLayout.barHeight(bucket.draws, maxDraws, PLOT_HEIGHT - VALUE_HEIGHT))
                                        .background(scheme.primary, RoundedCornerShape(4.dp)),
                                )
                            }
                        }
                    }
                    Row(Modifier.padding(top = 4.dp)) {
                        buckets.forEach { bucket ->
                            Text(
                                Dates.dayMonth(bucket.weekStart, language),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                // Libellés des percentiles (une ligne par repère, pour qu'ils ne se recouvrent pas), au-dessus de tout.
                drawn.forEachIndexed { index, mark ->
                    val fraction = HistogramLayout.position(buckets, mark.date!!)!!
                    val x = width * fraction
                    val top = MARK_ROW * index
                    // Libellé à droite du trait, ramené à gauche près du bord pour ne pas sortir du graphique.
                    Text(
                        mark.label,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .offset(y = top)
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                val total = width.roundToPx()
                                val left = (x + 4.dp).roundToPx().coerceAtMost(total - placeable.width).coerceAtLeast(0)
                                layout(total, placeable.height) { placeable.placeRelative(left, 0) }
                            }
                            .background(scheme.surface)
                            .padding(horizontal = 2.dp)
                            .semantics {},
                    )
                }
            }
        }
    }
}
