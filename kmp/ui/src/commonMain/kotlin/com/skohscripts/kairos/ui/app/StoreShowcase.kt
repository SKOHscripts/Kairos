package com.skohscripts.kairos.ui.app

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Vitrine des captures des magasins (docs/spec/publication.md § Captures) : l'identifiant de la tâche dont la vue Jour ouvre
 * la fiche d'emblée, « Tâches proches » dépliée, pour montrer l'estimation par machine learning sans interaction
 * (docs/spec/estimateur-modele.md § Interface). `null` partout ailleurs : ni l'application ni les tests ne la posent.
 */
val LocalShowcaseTask = staticCompositionLocalOf<Long?> { null }
