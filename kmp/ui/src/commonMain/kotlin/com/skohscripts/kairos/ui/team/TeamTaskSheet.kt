package com.skohscripts.kairos.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.Effort
import com.skohscripts.kairos.core.team.EffortSource
import com.skohscripts.kairos.core.team.TeamEvent
import com.skohscripts.kairos.data.TaskEdit
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.assign_unassigned
import com.skohscripts.kairos.ui.generated.resources.effort_calibrated
import com.skohscripts.kairos.ui.generated.resources.effort_estimate
import com.skohscripts.kairos.ui.generated.resources.effort_label
import com.skohscripts.kairos.ui.generated.resources.effort_none
import com.skohscripts.kairos.ui.generated.resources.effort_points
import com.skohscripts.kairos.ui.generated.resources.effort_remaining
import com.skohscripts.kairos.ui.generated.resources.field_assignee
import com.skohscripts.kairos.ui.generated.resources.field_progress
import com.skohscripts.kairos.ui.generated.resources.progress_percent
import com.skohscripts.kairos.ui.generated.resources.progress_start
import com.skohscripts.kairos.ui.generated.resources.progress_started_on
import com.skohscripts.kairos.ui.generated.resources.sheet_tab_details
import com.skohscripts.kairos.ui.generated.resources.sheet_tab_history
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * L'effort d'une tâche d'équipe et d'où il vient (docs/spec/equipe-charge.md § Effort restant d'une tâche) :
 * [hours] de base (`null` si [source] est `NONE`, tâche non estimée), [remainingHours] après avancement,
 * [points] de la tâche, [calibratedCount] (n de la médiane réelle quand [source] est `CALIBRATED`) et
 * [hoursPerPoint] des réglages.
 */
internal class EffortInfo(
    val hours: Double?,
    val remainingHours: Double?,
    val source: EffortSource,
    val points: Int?,
    val calibratedCount: Int?,
    val hoursPerPoint: Double,
) {
    companion object {
        /** Lit l'effort de [task] dans [snapshot] : calibration de l'équipe, réglages. Léger : une seule passe sur les tâches faites. */
        fun of(task: Task, snapshot: KairosSnapshot, now: Instant): EffortInfo {
            val calibration = Effort.teamCalibration(snapshot, now)
            val (hours, source) = Effort.base(task, calibration, snapshot.settings)
            val points = task.fibonacciPoints
            return EffortInfo(
                hours = hours,
                remainingHours = Effort.remaining(task, calibration, snapshot.settings).first,
                source = source,
                points = points,
                calibratedCount = if (source == EffortSource.CALIBRATED) calibration.firstOrNull { it.key == points.toString() }?.count else null,
                hoursPerPoint = (snapshot.settings.team ?: TeamSettings()).hoursPerPoint,
            )
        }
    }
}

/**
 * Ce que la fiche d'une tâche ajoute pour une tâche d'équipe (docs/spec/equipe-backlog-suivi.md
 * § Interface) :
 *
 * - [editable] (espace Équipe) : champ « Assigné à » modifiable, curseur d'avancement,
 *   bouton « Commencer », onglet « Historique », catégorie libellée « Catégorie » ;
 *   l'enregistrement passe par [onSave] (l'édition puis l'avancement, dans l'ordre) ;
 * - sinon (espace Perso, tâche assignée à « moi ») : « Assigné à » en lecture seule,
 *   le reste comme une tâche personnelle.
 */
internal class TeamTaskSheet(
    val assign: AssignContext,
    val editable: Boolean,
    val today: LocalDate,
    val history: List<TeamEvent> = emptyList(),
    /** Ouvre la fiche sur l'onglet « Historique » (captures). */
    val initialHistory: Boolean = false,
    val onStart: () -> Unit = {},
    /** Effort et sa source, sous « Assigné à » (espace Équipe seulement ; `null` : pas de ligne). */
    val effort: EffortInfo? = null,
    /** Enregistrement d'équipe : l'édition, puis l'avancement (`null` = inchangé). */
    val onSave: (TaskEdit, Int?) -> Unit = { _, _ -> },
)

/** Onglets « Détails » / « Historique (n) » de la fiche d'une tâche d'équipe. */
@Composable
internal fun TeamTaskTabs(selected: Int, historyCount: Int, onSelect: (Int) -> Unit) {
    SecondaryTabRow(selectedTabIndex = selected, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Tab(selected == 0, { onSelect(0) }, text = { Text(stringResource(Res.string.sheet_tab_details)) })
        Tab(
            selected == 1,
            { onSelect(1) },
            text = { Text(stringResource(Res.string.sheet_tab_history) + " ($historyCount)") },
        )
    }
}

/**
 * « Assigné à » (menu, ou lecture seule en espace Perso) puis, pour une tâche à
 * faire assignée, « Commencer » et le curseur d'avancement (0 à 100 %, pas de 10).
 * [assignee] et [progress] sont l'état en cours d'édition ; rien n'est écrit avant
 * « Enregistrer » sauf « Commencer », geste à part.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TeamTaskFields(
    task: Task,
    sheet: TeamTaskSheet,
    assignee: Long?,
    onPickAssignee: (Long?) -> Unit,
    progress: Float,
    onProgress: (Float) -> Unit,
) {
    val unassigned = stringResource(Res.string.assign_unassigned)
    val shown = sheet.assign.name(assignee) ?: unassigned
    val label = stringResource(Res.string.field_assignee)
    val open = task.status == TaskStatus.TODO
    if (!sheet.editable || !open) {
        OutlinedTextField(shown, {}, readOnly = true, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    } else {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = shown,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AssignMenuItems(sheet.assign, assignee, showBacklog = true) {
                    expanded = false
                    onPickAssignee(it)
                }
            }
        }
    }
    sheet.effort?.let { EffortLine(it) }
    if (!sheet.editable || !open || assignee == null) return

    // Avancement : seulement pour le titulaire actuel, l'avancement appartient à la tâche assignée.
    ProgressField(progress, onProgress)
    val started = task.startedOn
    if (assignee == task.assigneeId) {
        if (started == null) {
            OutlinedButton(onClick = sheet.onStart) {
                Icon(KairosIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.progress_start), modifier = Modifier.padding(start = 6.dp))
            }
        } else {
            Text(
                stringResource(Res.string.progress_started_on, dateSpan(started, started, sheet.today, Locale.current.language).first),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * « Avancement » : le pourcentage et son curseur (0 à 100 %, pas de 10). Commun aux tâches d'équipe et aux tâches
 * reçues d'un manager (docs/spec/equipe-echanges.md : l'avancement d'une tâche reçue est celui du membre).
 */
@Composable
internal fun ProgressField(progress: Float, onProgress: (Float) -> Unit) {
    val title = stringResource(Res.string.field_progress)
    val value = stringResource(Res.string.progress_percent, progress.roundToInt())
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = progress,
            onValueChange = onProgress,
            valueRange = 0f..100f,
            steps = 9,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$title $value" },
        )
    }
}

/**
 * « Effort » : les heures et leur source, « 6 h, médiane des tâches à 3 points, n=8 », « 4 h, estimation »,
 * « 6 h, 3 points × 2 h » ; « non estimée » (contour + icône `Warning`) pour une tâche sans durée ni points. Une
 * tâche avancée ajoute ce qu'il reste (« · reste 3 h »).
 */
@Composable
private fun EffortLine(effort: EffortInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(Res.string.effort_label), style = MaterialTheme.typography.labelLarge)
        val hours = effort.hours
        if (hours == null) {
            Flag(stringResource(Res.string.effort_none), flagged = true, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        val base = hoursText(hours)
        val text = when (effort.source) {
            EffortSource.ESTIMATE -> stringResource(Res.string.effort_estimate, base)
            EffortSource.CALIBRATED -> stringResource(Res.string.effort_calibrated, base, effort.points ?: 0, effort.calibratedCount ?: 0)
            EffortSource.POINTS_RATE -> stringResource(Res.string.effort_points, base, effort.points ?: 0, decimalText(effort.hoursPerPoint, Locale.current.language))
            EffortSource.NONE -> stringResource(Res.string.effort_none)
        }
        val remaining = effort.remainingHours
        Text(
            if (remaining != null && remaining < hours - 1e-9) stringResource(Res.string.effort_remaining, text, hoursText(remaining)) else text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
