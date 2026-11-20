package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.congrats_body
import com.skohscripts.kairos.ui.generated.resources.congrats_close
import com.skohscripts.kairos.ui.generated.resources.congrats_discover
import com.skohscripts.kairos.ui.generated.resources.congrats_timer
import com.skohscripts.kairos.ui.generated.resources.congrats_title
import com.skohscripts.kairos.ui.generated.resources.congrats_where
import com.skohscripts.kairos.ui.generated.resources.invite_body
import com.skohscripts.kairos.ui.generated.resources.invite_later
import com.skohscripts.kairos.ui.generated.resources.invite_start
import com.skohscripts.kairos.ui.generated.resources.invite_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.stringResource
import com.skohscripts.kairos.ui.theme.KairosOutlinedCard

/**
 * Invitation à l'accompagnement de la première tâche (docs/spec/premiere-tache.md § Déclenchement), sur la vue Jour tant que
 * l'utilisateur n'a aucune tâche à lui. Une carte neutre : le seul bloc teinté de l'écran reste « Maintenant ».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FirstTaskInvite(onStart: () -> Unit, onDismiss: () -> Unit) {
    KairosOutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(KairosIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.invite_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
            }
            Text(stringResource(Res.string.invite_body), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KairosButton(onClick = onStart) { Text(stringResource(Res.string.invite_start)) }
                KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.invite_later)) }
            }
        }
    }
}

/**
 * Félicitations, une fois la tâche **réellement créée** (docs/spec/premiere-tache.md § Placement et félicitations) : chaleureuses et
 * sobres, sans animation, avec la suite. [onWhere] explique la place de la tâche, [onTimer] lance le chrono dessus, [onDiscover]
 * ouvre le hub « Découvrir Kairos ».
 */
@Composable
internal fun FirstTaskCongratsDialog(onTimer: () -> Unit, onWhere: () -> Unit, onDiscover: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(KairosIcons.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(Res.string.congrats_title), modifier = Modifier.heading()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.congrats_body), style = MaterialTheme.typography.bodyMedium)
                Next(KairosIcons.Today, Res.string.congrats_where, onWhere)
                Next(KairosIcons.PlayArrow, Res.string.congrats_discover, onDiscover)
            }
        },
        confirmButton = { KairosButton(onClick = onTimer) { Text(stringResource(Res.string.congrats_timer)) } },
        dismissButton = { KairosTextButton(onClick = onClose) { Text(stringResource(Res.string.congrats_close)) } },
    )
}

/** Ligne cliquable (≥ 48 dp), fond transparent pour garder celui du dialogue. */
@Composable
private fun Next(icon: androidx.compose.ui.graphics.vector.ImageVector, label: org.jetbrains.compose.resources.StringResource, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(stringResource(label)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}
