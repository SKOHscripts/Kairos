package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.blockers_add
import com.skohscripts.kairos.ui.generated.resources.blockers_more
import com.skohscripts.kairos.ui.generated.resources.blockers_remove
import com.skohscripts.kairos.ui.generated.resources.blockers_search_none
import com.skohscripts.kairos.ui.generated.resources.field_blockers_cycle
import com.skohscripts.kairos.ui.generated.resources.field_blockers_none
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosRowIconButton
import com.skohscripts.kairos.ui.theme.KairosSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * « Bloquée par » de la fiche (docs/spec/fiche-tache.md) : les tâches choisies en puces (croix pour retirer), puis une
 * recherche dont les résultats ([BlockerChoices.page]) s'affichent **dans** la fiche, six au plus, seulement une fois
 * quelque chose tapé. Pas de menu flottant : sur le bureau, Échap destiné au menu fermait la fiche entière.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlockerPicker(all: List<BlockerChoice>, chosen: Set<Long>, onChange: (Set<Long>) -> Unit) {
    if (all.isEmpty()) {
        Text(stringResource(Res.string.field_blockers_none), style = MaterialTheme.typography.bodySmall)
        return
    }
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
        // Puces dans l'ordre des choix proposés : stable d'une ouverture à l'autre.
        val picked = all.filter { it.task.id in chosen }
        if (picked.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
                picked.forEach { choice ->
                    val remove = stringResource(Res.string.blockers_remove, choice.label)
                    // Pastille de puce (8 dp, contour) : le titre se lit, seule la croix retire (un toucher distrait sur le
                    // titre ne doit pas faire perdre un bloqueur, et une puce cliquable sans effet mentirait au lecteur d'écran).
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.widthIn(max = 560.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                            Text(choice.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            KairosRowIconButton(KairosIcons.Close, remove, onClick = { onChange(chosen - choice.task.id) })
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            query,
            { query = it },
            label = { Text(stringResource(Res.string.blockers_add)) },
            leadingIcon = { Icon(KairosIcons.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val page = BlockerChoices.page(all, query, chosen)
        if (page != null) {
            if (page.items.isEmpty()) {
                Text(stringResource(Res.string.blockers_search_none, query.trim()), style = MaterialTheme.typography.bodySmall)
            }
            page.items.forEach { choice ->
                // Prendre ce choix fermerait une boucle : proposé mais grisé, avec la raison.
                val enabled = !choice.cyclic
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KairosSpacing.s),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = enabled, role = Role.Button) {
                            onChange(chosen + choice.task.id)
                            query = ""
                        }
                        .padding(horizontal = KairosSpacing.s),
                ) {
                    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(KairosIcons.Add, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                    Text(
                        if (enabled) choice.label else stringResource(Res.string.field_blockers_cycle, choice.label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tint,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (page.more > 0) {
                Text(stringResource(Res.string.blockers_more, page.more), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
