package com.skohscripts.kairos.ui.navigation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.Platform

/** Forme de la navigation principale (docs/spec-v3/navigation-theme.md § Navigation). */
enum class NavigationLayout {
    /** Rail vertical à gauche : fenêtre moyenne ou large, toute plateforme. */
    RAIL,

    /** Barre de navigation basse : Android en largeur compacte, uniquement. */
    BOTTOM_BAR,

    /** Barre horizontale compacte en haut : bureau ou web en fenêtre étroite. */
    TOP_BAR,
    ;

    companion object {
        /** Seuil MD3 entre largeur compacte et moyenne. */
        val COMPACT_WIDTH_LIMIT: Dp = 600.dp

        /**
         * Jamais de barre basse hors d'Android : un navigateur ou une fenêtre de
         * bureau simplement rétrécie garde sa navigation en haut (règle reprise
         * de Kairos 2, docs/spec/accueil-navigation.md).
         */
        fun choose(platform: Platform, windowWidth: Dp): NavigationLayout = when {
            windowWidth >= COMPACT_WIDTH_LIMIT -> RAIL
            platform == Platform.ANDROID -> BOTTOM_BAR
            else -> TOP_BAR
        }
    }
}
