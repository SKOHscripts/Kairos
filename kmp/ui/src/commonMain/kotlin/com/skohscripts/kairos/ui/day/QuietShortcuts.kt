package com.skohscripts.kairos.ui.day

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Sur un champ de saisie de la vue Jour, arrête les raccourcis `N`, `/` et `D` avant l'écran.
 * Le champ reçoit d'abord la touche ; sur bureau, le caractère peut arriver ensuite en `KeyTyped`.
 * On ne consomme que le `KeyDown` du raccourci pour préserver la saisie.
 */
internal fun Modifier.quietShortcuts(): Modifier = onKeyEvent { e ->
    e.type == KeyEventType.KeyDown && !e.isCtrlPressed && !e.isMetaPressed && !e.isAltPressed &&
        (e.key == Key.N || e.key == Key.Slash || e.key == Key.D)
}
