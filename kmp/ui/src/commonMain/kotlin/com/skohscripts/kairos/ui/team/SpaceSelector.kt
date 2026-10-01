package com.skohscripts.kairos.ui.team

import com.skohscripts.kairos.ui.theme.KairosSegmentedButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.space_personal
import com.skohscripts.kairos.ui.generated.resources.space_selector
import com.skohscripts.kairos.ui.generated.resources.space_team
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.navigation.Space
import org.jetbrains.compose.resources.stringResource

/**
 * Sélecteur d'espace « Perso | Équipe » (docs/spec/equipe.md § Deux espaces) :
 * bouton segmenté MD3 à deux segments. Il agit aussitôt (navigation, pas
 * réglage) et n'est affiché par la coquille que si la gestion d'équipe est
 * activée et enregistrée.
 *
 * **Icônes seules**, libellé en `contentDescription` : le rail fait 80 dp et,
 * sous 600 dp, la barre d'application doit aussi porter le logo et le titre
 * (360 dp). Le segment choisi se lit à son conteneur `secondaryContainer`
 * (défaut MD3) ; chaque segment fait au moins 48 dp de côté.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceSelector(space: Space, onSpaceChange: (Space) -> Unit, modifier: Modifier = Modifier) {
    val group = stringResource(Res.string.space_selector)
    val options = listOf(
        Triple(Space.PERSONAL, KairosIcons.Person, stringResource(Res.string.space_personal)),
        Triple(Space.TEAM, KairosIcons.Groups, stringResource(Res.string.space_team)),
    )
    KairosSegmentedRow(modifier.semantics { contentDescription = group }) {
        options.forEachIndexed { index, (option, icon, label) ->
            KairosSegmentedButton(
                selected = option == space,
                onClick = { onSpaceChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                // L'emplacement `icon` du composant est celui de la coche du segment choisi, pas d'un pictogramme :
                // l'icône du segment est donc son contenu, et la coche est supprimée (la couleur dit le choix).
                icon = {},
                label = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                // Sans marge ni largeur imposée, le composant réserve la place de la coche et chaque segment
                // dépasse 70 dp : on fixe 48 dp de large (cible tactile) pour ne pas trop élargir le rail.
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.width(48.dp).semantics { contentDescription = label },
            )
        }
    }
}
