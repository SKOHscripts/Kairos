package com.skohscripts.kairos.ui.team.forecast

import com.skohscripts.kairos.core.team.forecast.WeekBucket
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Arrondis des probabilités, séparateurs de milliers, géométrie de l'histogramme et « meilleur de la ligne » : de la logique pure. */
class ForecastFormatTest {
    @Test
    fun aProbabilityThatIsNotCertainIsNeverWrittenOneHundredPercent() {
        assertEquals(99, onTimePercent(lateDraws = 1, draws = 5000))
        assertEquals(100, onTimePercent(lateDraws = 0, draws = 5000))
        assertEquals(61, onTimePercent(lateDraws = 1923, draws = 5000))
        assertEquals(0, onTimePercent(lateDraws = 5000, draws = 5000))
        assertEquals(1, latePercent(lateDraws = 1, draws = 5000))
        assertEquals(0, latePercent(lateDraws = 0, draws = 5000))
        assertEquals(39, latePercent(lateDraws = 1923, draws = 5000))
    }

    @Test
    fun numbersAreGroupedByLanguage() {
        assertEquals("3 200", groupedNumber(3200, "fr"))
        assertEquals("3,200", groupedNumber(3200, "en"))
        assertEquals("500", groupedNumber(500, "en"))
        assertEquals("50 000", groupedNumber(50_000, "fr"))
    }

    @Test
    fun aDateIsPlacedInTheMiddleOfItsDayInItsWeek() {
        val weeks = listOf(WeekBucket(LocalDate(2026, 10, 5), 10), WeekBucket(LocalDate(2026, 10, 12), 30))
        // Lundi 5 : 0,5 jour sur 14 ; dimanche 18 : 13,5 jours sur 14.
        assertEquals(0.5f / 14f, HistogramLayout.position(weeks, LocalDate(2026, 10, 5)))
        assertEquals(13.5f / 14f, HistogramLayout.position(weeks, LocalDate(2026, 10, 18)))
        assertNull(HistogramLayout.position(weeks, LocalDate(2026, 10, 19)))
        assertNull(HistogramLayout.position(weeks, LocalDate(2026, 10, 4)))
    }

    @Test
    fun theTallestBarFillsThePlotAndASmallOneStaysVisible() {
        val full = androidx.compose.ui.unit.Dp(100f)
        assertEquals(100f, HistogramLayout.barHeight(30, 30, full).value)
        assertEquals(50f, HistogramLayout.barHeight(15, 30, full).value)
        assertEquals(2f, HistogramLayout.barHeight(1, 5000, full).value)
        assertEquals(0f, HistogramLayout.barHeight(0, 30, full).value)
    }

    @Test
    fun theBestOfARowIsMarkedUnlessEveryColumnIsTied() {
        assertEquals(setOf(1), ComparisonBest.lowest(listOf(5.0, 3.0, 4.0)))
        assertEquals(setOf(0, 2), ComparisonBest.lowest(listOf(3.0, 4.0, 3.0)))
        assertEquals(emptySet(), ComparisonBest.lowest(listOf(3.0, 3.0)))
        // « Hors horizon » (null) est pire que tout.
        assertEquals(setOf(1), ComparisonBest.lowest(listOf(null, 9.0)))
        assertEquals(setOf(2), ComparisonBest.highest(listOf(10.0, 40.0, 70.0)))
    }
}
