package com.skohscripts.kairos.ui.team

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.team.TeamEvents
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.day.EditTaskDialog
import com.skohscripts.kairos.ui.day.Estimates
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Fiche d'une tâche d'équipe, ouverte depuis le Backlog, le Suivi ou la fiche
 * d'un membre (docs/spec/equipe-backlog-suivi.md § Interface) : la fiche
 * d'édition de la vue Jour ([EditTaskDialog]) avec « Assigné à », l'avancement,
 * « Commencer » et l'onglet « Historique ». Lit la base **complète** : les
 * bloqueurs proposés sont ceux de l'équipe. « Enregistrer » écrit l'édition
 * (réaffectation comprise) puis l'avancement, dans cet ordre ; le dépôt journalise.
 *
 * [initialHistory] ouvre l'onglet « Historique » (captures).
 */
@Composable
fun TeamTaskDialog(services: AppServices, taskId: Long, onDismiss: () -> Unit, initialHistory: Boolean = false) {
    val repository = services.repository
    val snapshot by repository.snapshot.collectAsState()
    val task = snapshot.tasks.firstOrNull { it.id == taskId && it.space == TaskSpace.TEAM }
    // Tâche disparue (supprimée ailleurs) ou d'un autre espace : rien à montrer.
    if (task == null) {
        LaunchedEffect(taskId) { onDismiss() }
        return
    }
    val scope = rememberCoroutineScope()
    val now = services.clock.now()
    val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
    // La charge de chaque membre (menu « Assigner à… ») se calcule hors composition ; le menu s'en passe en attendant.
    val load = rememberTeamLoad(services, snapshot).value
    val context = remember(snapshot, today, load) { AssignContext.of(snapshot, today, load) }
    val estimates = remember(snapshot.tasks, snapshot.workSessions) { Estimates.of(snapshot.tasks, snapshot.workSessions, now) }
    val candidates = remember(snapshot.tasks) {
        snapshot.tasks.filter { it.space == TaskSpace.TEAM && it.status == TaskStatus.TODO }.sortedBy { it.title }
    }
    val effort = remember(task, snapshot.tasks, snapshot.workSessions, snapshot.settings) { EffortInfo.of(task, snapshot, now) }
    EditTaskDialog(
        task = task,
        day = today,
        taskTypes = snapshot.settings.taskTypeList,
        candidates = candidates,
        blockerIds = snapshot.dependencies.filter { it.taskId == taskId }.mapTo(HashSet()) { it.blockerId },
        estimates = estimates,
        onDismiss = onDismiss,
        onSave = {},
        // Fermer après l'écriture : la portée du dialogue disparaît avec lui.
        onDelete = { scope.launch { repository.deleteTask(taskId); onDismiss() } },
        team = TeamTaskSheet(
            assign = context,
            editable = true,
            today = today,
            effort = effort,
            history = TeamEvents.historyOf(snapshot.teamEvents, taskId),
            initialHistory = initialHistory,
            onStart = { scope.launch { repository.startTeamTask(taskId) } },
            onSave = { edit, percent ->
                scope.launch {
                    repository.updateTask(taskId, edit)
                    percent?.let { repository.setProgress(taskId, it) }
                    onDismiss()
                }
            },
        ),
    )
}
