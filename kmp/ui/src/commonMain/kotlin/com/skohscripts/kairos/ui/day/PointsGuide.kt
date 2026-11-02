package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.ui.generated.resources.guide_points_named
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosTextButton
import com.skohscripts.kairos.ui.app.expandedState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.FIBONACCI_SCALE
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.stats.TaskStats
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.guide_points_example
import com.skohscripts.kairos.ui.generated.resources.guide_points_intro
import com.skohscripts.kairos.ui.generated.resources.guide_points_level
import com.skohscripts.kairos.ui.generated.resources.guide_points_none
import com.skohscripts.kairos.ui.generated.resources.guide_points_outro
import com.skohscripts.kairos.ui.generated.resources.guide_points_title
import com.skohscripts.kairos.ui.generated.resources.guide_points_yours
import com.skohscripts.kairos.ui.generated.resources.guide_points_yours_unreliable
import com.skohscripts.kairos.ui.generated.resources.inbox_help_priority
import com.skohscripts.kairos.ui.generated.resources.inbox_help_title
import com.skohscripts.kairos.ui.generated.resources.inbox_help_why
import com.skohscripts.kairos.ui.icons.KairosIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Guide d'estimation des points (docs/spec/vue-jour.md § Comprendre les
 * valeurs), replié par défaut : pour chaque palier, son sens, puis les repères
 * tirés de l'historique (`TaskStats.fibonacciReferences`) : temps réel médian
 * avec l'effectif (« peu fiable » sous `MIN_SAMPLE`) et les titres des deux
 * dernières tâches terminées à ce palier. Sans historique : l'exemple
 * générique, et le guide le dit, sans rien inventer.
 */
@Composable
internal fun PointsGuide(refs: Map<Int, TaskStats.FiboReference>) {
    Collapsible(Res.string.guide_points_title) { PointsGuideBody(refs) }
}

/** Aide « Comment qualifier ? » de la boîte de réception : pourquoi, sens des priorités, puis le guide des points. */
@Composable
internal fun InboxHelp(refs: Map<Int, TaskStats.FiboReference>) {
    Collapsible(Res.string.inbox_help_title) {
        Small(stringResource(Res.string.inbox_help_why))
        Small(stringResource(Res.string.inbox_help_priority))
        PRIORITY_VALUES.forEach { p ->
            Small("${Levels.priorityLabel(p)} · ${stringResource(Levels.priorityMeaning(p))}")
        }
        PointsGuideBody(refs)
    }
}

@Composable
private fun Collapsible(title: StringResource, content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        KairosTextButton(onClick = { open = !open }, modifier = Modifier.expandedState(open), contentPadding = KairosButtonIconPadding) {
            Icon(if (open) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
            Text(stringResource(title), modifier = Modifier.padding(start = 4.dp))
        }
        if (open) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun PointsGuideBody(refs: Map<Int, TaskStats.FiboReference>) {
    Small(stringResource(Res.string.guide_points_intro))
    FIBONACCI_SCALE.forEach { points ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Nom choisi : « M (3) : … », la valeur toujours lisible (docs/spec/planning-personnalise.md § Lot D).
            val named = LocalLevelNames.current.points[points]
            val meaning = stringResource(Levels.pointsMeaning(points))
            Text(
                named?.let { stringResource(Res.string.guide_points_named, it, points, meaning) }
                    ?: stringResource(Res.string.guide_points_level, points, stringResource(Levels.pointsName(points)), meaning),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val ref = refs[points]
            if (ref == null) {
                Small(stringResource(Res.string.guide_points_none, stringResource(Levels.pointsExample(points))))
            } else {
                val calibration = ref.calibration
                val median = calibration?.medianMinutes
                if (calibration != null && median != null) {
                    val yours = if (calibration.reliable) Res.string.guide_points_yours else Res.string.guide_points_yours_unreliable
                    Small(stringResource(yours, duration(median), calibration.count))
                }
                val examples = mutableListOf<String>()
                for (title in ref.examples) examples += stringResource(Res.string.guide_points_example, title)
                if (examples.isNotEmpty()) Small(examples.joinToString(" · "))
            }
        }
    }
    Small(stringResource(Res.string.guide_points_outro))
}

@Composable
private fun Small(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Repères d'estimation tirés de l'historique (toutes les tâches, archivées
 * comprises) : guide des points et durées suggérées du
 * dialogue d'édition. Seules les médianes fiables (`reliable`) sont suggérées.
 */
internal data class Estimates(
    val references: Map<Int, TaskStats.FiboReference> = emptyMap(),
    val minutesByPoints: Map<Int, Int> = emptyMap(),
    val minutesByType: Map<String, Int> = emptyMap(),
) {
    /** Une durée proposée pour une tâche qualifiée dans « À traiter » : [minutes] et le type d'où elle vient (`null` : ses points). */
    data class Fill(val minutes: Int, val type: String?)

    /**
     * Durée à poser quand une tâche de « À traiter » vient d'être entièrement qualifiée (3.14.0, issue #166, choix du
     * propriétaire) : priorité, points et type tous renseignés, **et** aucune durée encore (une durée saisie n'est jamais
     * touchée). La médiane fiable de son type d'abord, sinon celle de son palier de points ; `null` sans médiane fiable.
     */
    fun inboxFill(task: Task): Fill? {
        if (task.priority == null || (task.fibonacciPoints ?: 0) == 0 || task.taskType.isEmpty()) return null
        if ((task.estimatedMinutes ?: 0) != 0) return null
        minutesByType[task.taskType]?.let { return Fill(it, task.taskType) }
        return minutesByPoints[task.fibonacciPoints]?.let { Fill(it, null) }
    }

    companion object {
        fun of(tasks: List<Task>, sessions: List<WorkSession>, now: Instant): Estimates {
            val spent = TimeTracking.spentMinutesByTask(sessions, now, tasks)
            fun reliable(list: List<TaskStats.Calibration>) =
                list.mapNotNull { c -> c.medianMinutes?.takeIf { c.reliable && it > 0 }?.let { c.key to it } }.toMap()
            return Estimates(
                references = TaskStats.fibonacciReferences(tasks, spent),
                minutesByPoints = reliable(TaskStats.fibonacciCalibration(tasks, spent)).mapKeys { it.key.toInt() },
                minutesByType = reliable(TaskStats.calibrationByType(tasks, spent)),
            )
        }
    }
}
