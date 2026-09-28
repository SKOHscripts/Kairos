package com.skohscripts.kairos.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Construit une icône Material Symbols depuis son tracé SVG. Les SVG du paquet
 * utilisent `viewBox="0 -960 960 960"` : le tracé est décalé de +960 en y.
 * Couleur noire ici, teintée par `Icon(tint = …)` comme toute icône Compose.
 */
internal fun symbol(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).addGroup(translationY = 960f)
        .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
        .clearGroup()
        .build()
