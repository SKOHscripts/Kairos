package com.skohscripts.kairos.ui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.a11y_collapsed
import com.skohscripts.kairos.ui.generated.resources.a11y_expanded
import org.jetbrains.compose.resources.stringResource

/*
 * Accessibilité commune (docs/spec-v3/accessibilite.md) : ce que TalkBack et
 * les lecteurs d'écran du bureau doivent entendre sur les éléments que
 * Compose ne décrit pas seul.
 */

/** Titre de section : les lecteurs d'écran permettent d'y sauter. */
fun Modifier.heading(): Modifier = semantics { heading() }

/** « déplié » / « replié », lu après le libellé d'un bouton qui ouvre une section. */
@Composable
fun Modifier.expandedState(expanded: Boolean): Modifier {
    val state = stringResource(if (expanded) Res.string.a11y_expanded else Res.string.a11y_collapsed)
    return semantics { stateDescription = state }
}

/**
 * En-tête qui déplie une section : lu comme un bouton avec son état, cible
 * de 48 dp au moins ([minHeight] faux seulement dans une ligne de tâche
 * dense, où la ligne entière reste la cible), titre de section si [heading].
 */
@Composable
fun Modifier.disclosure(expanded: Boolean, heading: Boolean = false, minHeight: Boolean = true, onToggle: () -> Unit): Modifier {
    val base = if (minHeight) heightIn(min = 48.dp) else this
    val withHeading = if (heading) base.heading() else base
    return withHeading.clickable(role = Role.Button, onClick = onToggle).expandedState(expanded)
}
