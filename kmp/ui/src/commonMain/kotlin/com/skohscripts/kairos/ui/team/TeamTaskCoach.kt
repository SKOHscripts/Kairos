package com.skohscripts.kairos.ui.team

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.day.EditTaskDialog
import com.skohscripts.kairos.ui.day.Estimates
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.discover_close
import com.skohscripts.kairos.ui.generated.resources.team_coach_done_assigned
import com.skohscripts.kairos.ui.generated.resources.team_coach_done_backlog
import com.skohscripts.kairos.ui.generated.resources.team_coach_done_title
import com.skohscripts.kairos.ui.generated.resources.team_coach_open_backlog
import com.skohscripts.kairos.ui.generated.resources.team_coach_open_board
import com.skohscripts.kairos.ui.guide.CoachSession
import com.skohscripts.kairos.ui.guide.TeamTaskCoach
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Création accompagnée d'une première tâche d'équipe (docs/spec/premiere-tache.md § Première tâche d'équipe, issue #97) : la vraie
 * fiche d'une tâche d'équipe, en création (identifiant 0, espace Équipe, sans onglets ni avancement), avec le coach
 * [TeamTaskCoach]. [step] : l'étape courante (-1 : coach passé, la fiche reste) ; « Annuler » ou fermer n'écrit rien.
 * « Enregistrer » crée la tâche (`createTeamTask`) puis écrit l'édition (assignation comprise, journalisée par le dépôt) ;
 * [onCreated] reçoit l'identifiant de la tâche **réellement** créée.
 */
@Composable
fun TeamTaskCreateDialog(services: AppServices, step: Int, onStep: (Int) -> Unit, onDismiss: () -> Unit, onCreated: (Long) -> Unit) {
    val repository = services.repository
    val snapshot by repository.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val now = services.clock.now()
    val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val draft = remember { Task(0, "", space = TaskSpace.TEAM, createdAt = now, updatedAt = now) }
    val load = rememberTeamLoad(services, snapshot).value
    val context = remember(snapshot, today, load) { AssignContext.of(snapshot, today, load) }
    val estimates = remember(snapshot.tasks, snapshot.workSessions) { Estimates.of(snapshot.tasks, snapshot.workSessions, now) }
    val candidates = remember(snapshot.tasks) {
        snapshot.tasks.filter { it.space == TaskSpace.TEAM && it.status == TaskStatus.TODO }.sortedBy { it.title }
    }
    EditTaskDialog(
        task = draft,
        day = today,
        taskTypes = snapshot.settings.taskTypeList,
        candidates = candidates,
        blockerIds = emptySet(),
        dependencies = snapshot.dependencies,
        tasksById = remember(snapshot.tasks) { snapshot.tasks.associateBy { it.id } },
        estimates = estimates,
        chainSubtasks = snapshot.settings.subtaskChain == true,
        onDismiss = onDismiss,
        onSave = {},
        onDelete = {},
        team = TeamTaskSheet(
            assign = context,
            editable = true,
            today = today,
            onSave = { edit, _ ->
                scope.launch {
                    val id = repository.createTeamTask(edit.title) ?: return@launch
                    repository.updateTask(id, edit)
                    onCreated(id)
                }
            },
        ),
        coach = if (step >= 0) CoachSession(step, onStep, onSkip = { onStep(-1) }, steps = TeamTaskCoach.STEPS) else null,
    )
}

/**
 * Félicitations, une fois la tâche d'équipe réellement créée : où elle est (Backlog, ou « À faire » chez son titulaire), et
 * le chemin vers le Backlog ou le Suivi.
 */
@Composable
fun TeamTaskCreatedDialog(services: AppServices, taskId: Long, onOpen: (TeamDestination) -> Unit, onDismiss: () -> Unit) {
    val snapshot by services.repository.snapshot.collectAsState()
    val task = snapshot.tasks.firstOrNull { it.id == taskId } ?: return
    val member = task.assigneeId?.let { id -> snapshot.members.firstOrNull { it.id == id } }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(KairosIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(Res.string.team_coach_done_title)) },
        text = {
            Text(
                if (member == null) stringResource(Res.string.team_coach_done_backlog, task.title)
                else stringResource(Res.string.team_coach_done_assigned, task.title, member.name),
            )
        },
        confirmButton = {
            val target = if (member == null) TeamDestination.BACKLOG else TeamDestination.BOARD
            KairosButton(onClick = { onOpen(target) }) {
                Text(stringResource(if (member == null) Res.string.team_coach_open_backlog else Res.string.team_coach_open_board))
            }
        },
        dismissButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.discover_close)) } },
    )
}
