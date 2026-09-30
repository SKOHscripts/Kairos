package com.skohscripts.kairos.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Largeur de la fenêtre de l'application, celle que [NavigationLayout.choose]
 * reçoit : un écran qui s'ouvre « plein écran sous 600 dp, dialogue au-delà »
 * (fiche membre, docs/spec/equipe.md) la lit ici plutôt que de mesurer son
 * propre contenu, que le rail de navigation rétrécit. Défaut : une largeur de
 * bureau, pour un écran rendu hors de la coquille.
 */
val LocalWindowWidth = staticCompositionLocalOf<Dp> { 1024.dp }

/** Largeur compacte MD3 (< 600 dp) : la fiche membre s'ouvre alors en plein écran. */
val Dp.isCompactWidth: Boolean get() = this < NavigationLayout.COMPACT_WIDTH_LIMIT
