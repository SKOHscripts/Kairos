package com.skohscripts.kairos.ui.team

import com.skohscripts.kairos.ui.theme.KairosTextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.app.clearTeamDataWithBackup
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.team_clear_action
import com.skohscripts.kairos.ui.generated.resources.team_clear_body
import com.skohscripts.kairos.ui.generated.resources.team_clear_confirm
import com.skohscripts.kairos.ui.generated.resources.team_clear_done
import com.skohscripts.kairos.ui.generated.resources.team_clear_members_many
import com.skohscripts.kairos.ui.generated.resources.team_clear_members_one
import com.skohscripts.kairos.ui.generated.resources.team_clear_tasks_many
import com.skohscripts.kairos.ui.generated.resources.team_clear_tasks_one
import com.skohscripts.kairos.ui.generated.resources.team_clear_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * « Supprimer les données d’équipe… » (docs/spec/equipe.md § Activation) : bouton
 * texte en couleur d'erreur, confirmation qui compte les membres et les tâches
 * d'équipe, sauvegarde automatique préalable (`clearTeamDataWithBackup`), puis
 * message de confirmation. Sans sauvegarde réussie, rien n'est supprimé (le
 * message d'erreur de fichier est affiché à la place).
 */
@Composable
internal fun ClearTeamDataButton(services: AppServices) {
    val snapshot by services.repository.snapshot.collectAsState()
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }

    KairosTextButton(
        onClick = { confirm = true },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) { Text(stringResource(Res.string.team_clear_action)) }

    if (confirm) {
        val members = snapshot.members.size
        val tasks = snapshot.tasks.count { it.space == TaskSpace.TEAM }
        val membersText = if (members == 1) stringResource(Res.string.team_clear_members_one) else stringResource(Res.string.team_clear_members_many, members)
        val tasksText = if (tasks == 1) stringResource(Res.string.team_clear_tasks_one) else stringResource(Res.string.team_clear_tasks_many, tasks)
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(Res.string.team_clear_title)) },
            text = { Text(stringResource(Res.string.team_clear_body, membersText, tasksText)) },
            confirmButton = {
                KairosTextButton(
                    onClick = {
                        confirm = false
                        scope.launch {
                            if (clearTeamDataWithBackup(services, messages)) messages(getString(Res.string.team_clear_done))
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.team_clear_confirm)) }
            },
            dismissButton = { KairosTextButton(onClick = { confirm = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}
