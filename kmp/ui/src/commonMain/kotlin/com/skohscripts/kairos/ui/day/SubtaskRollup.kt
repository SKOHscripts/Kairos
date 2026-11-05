package com.skohscripts.kairos.ui.day

import androidx.compose.runtime.Composable
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TaskTree
import kotlin.time.Instant
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.rollup_planned
import com.skohscripts.kairos.ui.generated.resources.rollup_spent
import com.skohscripts.kairos.ui.generated.resources.rollup_unestimated_many
import com.skohscripts.kairos.ui.generated.resources.rollup_unestimated_one
import org.jetbrains.compose.resources.stringResource

/**
 * L'agrégat d'une mère en une ligne (docs/spec/sous-taches.md § Agrégation) : « 1 h 30 prévues · 1 sans durée · 45 min
 * passées », chaque partie absente quand elle est nulle ; `null` si tout l'est.
 */
@Composable
internal fun rollupText(rollup: TaskTree.Rollup): String? {
    val parts = buildList {
        if (rollup.plannedMinutes > 0) add(stringResource(Res.string.rollup_planned, duration(rollup.plannedMinutes)))
        when {
            rollup.unestimated == 1 -> add(stringResource(Res.string.rollup_unestimated_one))
            rollup.unestimated > 1 -> add(stringResource(Res.string.rollup_unestimated_many, rollup.unestimated))
        }
        if (rollup.spentMinutes > 0) add(stringResource(Res.string.rollup_spent, duration(rollup.spentMinutes)))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Agrégat d'une tâche hors de la vue Jour (fiche d'équipe) : durées estimées, ou temps réel d'une faite ; pas de durée
 * apprise (celles de l'équipe ne valent que pour le backlog).
 */
internal fun teamRollup(taskId: Long, snapshot: KairosSnapshot, now: Instant): TaskTree.Rollup? {
    val live = snapshot.tasks.filter { it.status != TaskStatus.ARCHIVED }
    val spent = TimeTracking.spentMinutesByTask(snapshot.workSessions, now, live)
    return TaskTree.rollup(taskId, live, { t -> t.estimatedMinutes?.takeIf { it > 0 } ?: spent[t.id]?.takeIf { it > 0 && t.status == TaskStatus.DONE } }, spent)
}
