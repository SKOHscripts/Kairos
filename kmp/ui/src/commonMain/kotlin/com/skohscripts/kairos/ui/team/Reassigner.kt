package com.skohscripts.kairos.ui.team

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.team.TeamState
import com.skohscripts.kairos.core.team.TeamStates
import com.skohscripts.kairos.data.KairosRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Réaffectation d'une tâche depuis le Suivi ou le Backlog (docs/spec/equipe-backlog-suivi.md
 * § Assignation et réaffectation). Une tâche **en cours** passée à un autre
 * membre pose d'abord la question « Garder l’état En cours ? » ([pending]) ; les
 * autres cas (assignation depuis le backlog, retour au backlog, tâche à faire)
 * s'écrivent tout de suite. Le dépôt journalise ; l'interface ne fait que demander.
 */
internal class Reassigner(private val repository: KairosRepository, private val scope: CoroutineScope) {
    /** Réaffectation en attente de la réponse « garder En cours ? » : la tâche et le nouveau titulaire. */
    var pending by mutableStateOf<Pair<Task, Long>?>(null)
        private set

    fun request(task: Task, memberId: Long?) {
        if (task.assigneeId == memberId) return
        if (memberId != null && TeamStates.of(task) == TeamState.IN_PROGRESS) {
            pending = task to memberId
        } else {
            scope.launch { repository.assign(listOf(task.id), memberId) }
        }
    }

    fun answer(keep: Boolean) {
        val (task, memberId) = pending ?: return
        pending = null
        scope.launch { repository.assign(listOf(task.id), memberId, keepInProgress = keep) }
    }

    fun cancel() {
        pending = null
    }
}

@Composable
internal fun rememberReassigner(repository: KairosRepository): Reassigner {
    val scope = rememberCoroutineScope()
    return remember(repository, scope) { Reassigner(repository, scope) }
}

/** La boîte « Garder l’état En cours ? » de [reassigner], quand une réaffectation attend une réponse. */
@Composable
internal fun ReassignDialog(reassigner: Reassigner, context: AssignContext) {
    val (task, memberId) = reassigner.pending ?: return
    KeepInProgressDialog(
        taskTitle = task.title,
        toName = context.name(memberId).orEmpty(),
        onAnswer = reassigner::answer,
        onCancel = reassigner::cancel,
    )
}
