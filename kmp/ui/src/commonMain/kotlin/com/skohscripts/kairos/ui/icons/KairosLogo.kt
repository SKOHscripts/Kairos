package com.skohscripts.kairos.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Logo Kairos (cadran solaire : cadran, anneau, secteur, axe), dessin identique
 * à `static/favicon.svg`, couleurs tirées de la graine (docs/DESIGN_SYSTEM.md
 * § Logo). Couleurs fixes : le logo n'est jamais teinté (`Image`, pas `Icon`).
 */
val KairosLogo: ImageVector by lazy {
    val circle = "M1.5,20a18.5,18.5 0 1,0 37,0a18.5,18.5 0 1,0 -37,0Z"
    ImageVector.Builder(
        name = "KairosLogo",
        defaultWidth = 40.dp,
        defaultHeight = 40.dp,
        viewportWidth = 40f,
        viewportHeight = 40f,
    )
        .addPath(addPathNodes(circle), fill = SolidColor(Color(0xFFFFEEDC)))
        .addPath(addPathNodes(circle), stroke = SolidColor(Color(0xFFFFCC85)), strokeLineWidth = 1.6f)
        .addPath(addPathNodes("M20,20L20,4A16,16 0 0,1 35.76,17.22Z"), fill = SolidColor(Color(0xFFC28417)))
        .addPath(addPathNodes("M17.4,20a2.6,2.6 0 1,0 5.2,0a2.6,2.6 0 1,0 -5.2,0Z"), fill = SolidColor(Color(0xFF2B251C)))
        .build()
}
