package com.skohscripts.kairos.core.notes

import kotlin.test.Test
import kotlin.test.assertEquals

/** Taille cible des images des notes (docs/spec/notes-images.md, issue #295). */
class ImageBudgetTest {
    @Test
    fun aLargePhotoIsReducedAndASmallOneKeepsItsSize() {
        assertEquals(1_600 to 1_200, ImageBudget.target(4_000, 3_000))
        assertEquals(1_200 to 1_600, ImageBudget.target(3_000, 4_000))
        assertEquals(800 to 600, ImageBudget.target(800, 600))
        assertEquals(1_600 to 1_600, ImageBudget.target(1_600, 1_600))
    }

    @Test
    fun aStretchedImageKeepsItsRatioAndNeverGetsAZeroSide() {
        assertEquals(80 to 1_600, ImageBudget.target(1_000, 20_000))
        assertEquals(1_600 to 1, ImageBudget.target(100_000, 10))
    }

    @Test
    fun theSampleSizeKeepsTheDecodedImageAtLeastAsLargeAsTheTarget() {
        assertEquals(1, ImageBudget.sampleSize(3_000, 2_000))
        assertEquals(2, ImageBudget.sampleSize(4_000, 3_000))
        assertEquals(4, ImageBudget.sampleSize(8_000, 6_000))
        assertEquals(1, ImageBudget.sampleSize(800, 600))
    }
}
