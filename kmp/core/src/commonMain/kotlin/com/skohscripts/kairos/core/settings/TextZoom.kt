package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.Settings

/**
 * Zoom du texte (docs/spec/apparence.md § Taille du texte, issue #229), pur : quatre paliers en pour cent, jamais de
 * valeur libre (chaque palier est vérifié à 360 dp).
 */
object TextZoom {
    val STEPS: List<Int> = listOf(Settings.DEFAULT_TEXT_ZOOM, 115, 130, 150)

    /** Le palier le plus proche de [percent] (égalité : le plus petit) ; `null` ou absurde : 100 %. */
    fun normalize(percent: Int?): Int =
        if (percent == null) Settings.DEFAULT_TEXT_ZOOM else STEPS.minByOrNull { kotlin.math.abs(it - percent) }!!

    /** Multiplicateur de `fontScale` pour le zoom enregistré [percent]. */
    fun factor(percent: Int): Float = normalize(percent) / 100f
}
