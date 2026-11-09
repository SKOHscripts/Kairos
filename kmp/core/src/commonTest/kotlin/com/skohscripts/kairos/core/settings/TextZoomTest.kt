package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.Settings
import kotlin.test.Test
import kotlin.test.assertEquals

/** Zoom du texte (docs/spec/apparence.md § Taille du texte, issue #229). */
class TextZoomTest {
    @Test
    fun aValueOutsideTheStepsGoesToTheNearestOne() {
        assertEquals(100, TextZoom.normalize(null))
        assertEquals(100, TextZoom.normalize(-40))
        assertEquals(100, TextZoom.normalize(107)) // égalité 107,5 : plus proche de 100
        assertEquals(115, TextZoom.normalize(108))
        assertEquals(130, TextZoom.normalize(125)) // égalité 115 / 130 : 125 est plus près de 130
        assertEquals(150, TextZoom.normalize(900))
        TextZoom.STEPS.forEach { assertEquals(it, TextZoom.normalize(it)) }
    }

    @Test
    fun theFactorIsThePercentAsAMultiplier() {
        assertEquals(1f, TextZoom.factor(100))
        assertEquals(1.5f, TextZoom.factor(150))
    }

    @Test
    fun theFormFieldNormalizesAndNeverRefuses() {
        val field = SettingsForm.field("textZoom")
        assertEquals("100", field.read(Settings()))
        assertEquals(130, field.write(Settings(), "130").textZoom)
        assertEquals(100, field.write(Settings(), "grand").textZoom)
        assertEquals(150, field.write(Settings(), "999").textZoom)
    }
}
