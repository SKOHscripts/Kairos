package com.skohscripts.kairos.ui.app

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope

/** Affiche un message bref (snackbar de la coquille) : confirmation d'export, erreur d'import… */
val LocalMessages = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * Message bref avec une action (« Annuler ») : le texte, le libellé de l'action et ce qu'elle fait. Sans effet par défaut
 * (écrans testés seuls) ; la coquille le fournit avec son snackbar.
 */
val LocalUndoMessages = staticCompositionLocalOf<(String, String, () -> Unit) -> Unit> { { _, _, _ -> } }

/**
 * La portée de coroutines de la coquille : elle survit aux écrans et aux dialogues. Une action d'« Annuler » qui écrit doit s'y
 * lancer, car la snackbar peut la jouer après que l'écran ou le dialogue qui l'a proposée a disparu (docs/spec/estimateur-modele.md
 * § Estimations qui changent). `null` hors de la coquille (écrans testés seuls).
 */
val LocalShellScope = staticCompositionLocalOf<CoroutineScope?> { null }
