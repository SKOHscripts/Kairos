package com.skohscripts.kairos.ui.team

import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.team.AssignmentSuggestion
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.suggest_affinity_many
import com.skohscripts.kairos.ui.generated.resources.suggest_affinity_one
import com.skohscripts.kairos.ui.generated.resources.suggest_apply
import com.skohscripts.kairos.ui.generated.resources.suggest_change
import com.skohscripts.kairos.ui.generated.resources.suggest_close
import com.skohscripts.kairos.ui.generated.resources.suggest_computing
import com.skohscripts.kairos.ui.generated.resources.suggest_decider_earliest
import com.skohscripts.kairos.ui.generated.resources.suggest_decider_id
import com.skohscripts.kairos.ui.generated.resources.suggest_decider_load
import com.skohscripts.kairos.ui.generated.resources.suggest_decider_wip
import com.skohscripts.kairos.ui.generated.resources.suggest_empty
import com.skohscripts.kairos.ui.generated.resources.suggest_end
import com.skohscripts.kairos.ui.generated.resources.suggest_excluded_archived_many
import com.skohscripts.kairos.ui.generated.resources.suggest_excluded_archived_one
import com.skohscripts.kairos.ui.generated.resources.suggest_excluded_qualify
import com.skohscripts.kairos.ui.generated.resources.suggest_excluded_title
import com.skohscripts.kairos.ui.generated.resources.suggest_excluded_unplaceable
import com.skohscripts.kairos.ui.generated.resources.suggest_include
import com.skohscripts.kairos.ui.generated.resources.suggest_manual
import com.skohscripts.kairos.ui.generated.resources.suggest_no_date
import com.skohscripts.kairos.ui.generated.resources.suggest_over_wip
import com.skohscripts.kairos.ui.generated.resources.suggest_reason
import com.skohscripts.kairos.ui.generated.resources.suggest_scope_all
import com.skohscripts.kairos.ui.generated.resources.suggest_scope_selection
import com.skohscripts.kairos.ui.generated.resources.suggest_summary
import com.skohscripts.kairos.ui.generated.resources.suggest_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.navigation.LocalWindowWidth
import com.skohscripts.kairos.ui.navigation.isCompactWidth
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * « Suggérer une répartition » (docs/spec/equipe-charge.md § Suggestion de répartition) : une ligne par
 * proposition de `AssignmentSuggestion.suggest` pour les tâches prêtes du backlog, ou pour [taskIds] (la sélection
 * multiple en cours) : case à cocher (cochée par défaut), assigné modifiable par le menu « Assigner à… », et la
 * raison mise en phrase. En bas, ce qui est exclu : tâches à qualifier, tâches impossibles à placer, membres
 * archivés ignorés. **Rien n'est assigné avant « Appliquer »**, qui assigne les lignes cochées par
 * `repository.assign`, groupées par membre (donc journalisées) ; « Annuler » ne touche à rien.
 *
 * Plein écran sous 600 dp, dialogue au-delà. La suggestion est calculée **une fois**, à l'ouverture, hors
 * composition (`Dispatchers.Default`, plus d'une seconde pour 150 tâches) : la feuille montre un indicateur de
 * progression en attendant, et ne se recalcule pas sous les doigts du manager quand la base change.
 */
@Composable
internal fun SuggestionSheet(services: AppServices, taskIds: Set<Long>?, onDismiss: () -> Unit) {
    val repository = services.repository
    val snapshot by repository.snapshot.collectAsState()
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = services.clock.now().toLocalDateTime(zone).date
    val language = Locale.current.language
    val scope = rememberCoroutineScope()
    val compact = LocalWindowWidth.current.isCompactWidth

    val computed = rememberComputed(taskIds) {
        AssignmentSuggestion.suggest(repository.snapshot.value, services.clock.now(), zone, taskIds)
    }
    val load = rememberTeamLoad(services, snapshot).value
    val context = remember(snapshot, today, load) { AssignContext.of(snapshot, today, load) }
    val result = computed.value
    val tasksById = remember(snapshot.tasks) { snapshot.tasks.associateBy { it.id } }

    // Les lignes dont la tâche a disparu entre-temps sont ignorées.
    val rows = remember(result, tasksById) { result?.suggestions?.filter { it.taskId in tasksById }.orEmpty() }
    var unchecked by remember { mutableStateOf(emptySet<Long>()) }
    var picked by remember { mutableStateOf(emptyMap<Long, Long>()) }
    fun assigneeOf(s: AssignmentSuggestion.Suggestion): Long = picked[s.taskId] ?: s.memberId
    val kept = rows.filter { it.taskId !in unchecked }

    fun apply() {
        val groups = kept.groupBy({ assigneeOf(it) }, { it.taskId })
        scope.launch {
            groups.forEach { (member, ids) -> repository.assign(ids, member) }
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val padding = if (compact) 16.dp else 24.dp
        Surface(
            shape = if (compact) RectangleShape else MaterialTheme.shapes.extraLarge,
            color = if (compact) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = if (compact) 0.dp else 6.dp,
            shadowElevation = if (compact) 0.dp else 6.dp,
            modifier = if (compact) Modifier.fillMaxSize() else Modifier.padding(16.dp).widthIn(max = 640.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = if (compact) 4.dp else padding, end = padding, top = if (compact) 4.dp else padding, bottom = if (compact) 4.dp else 0.dp),
                ) {
                    if (compact) {
                        IconButton(onClick = onDismiss) { Icon(KairosIcons.Close, contentDescription = stringResource(Res.string.suggest_close)) }
                    }
                    Text(
                        stringResource(Res.string.suggest_title),
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.heading(),
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f, fill = compact).verticalScroll(rememberScrollState()).padding(horizontal = padding, vertical = 12.dp),
                ) {
                    Text(
                        if (taskIds == null) stringResource(Res.string.suggest_scope_all) else stringResource(Res.string.suggest_scope_selection, taskIds.size),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (result == null) {
                        val text = stringResource(Res.string.suggest_computing)
                        LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = text })
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        if (rows.isEmpty()) {
                            Text(stringResource(Res.string.suggest_empty), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(
                                stringResource(Res.string.suggest_summary, kept.size, rows.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        rows.forEach { suggestion ->
                            val task = tasksById.getValue(suggestion.taskId)
                            SuggestionRow(
                                task = task,
                                suggestion = suggestion,
                                memberId = assigneeOf(suggestion),
                                changed = picked.containsKey(suggestion.taskId),
                                checked = suggestion.taskId !in unchecked,
                                context = context,
                                today = today,
                                language = language,
                                onCheck = { on -> unchecked = if (on) unchecked - suggestion.taskId else unchecked + suggestion.taskId },
                                onPick = { member -> picked = if (member == suggestion.memberId) picked - suggestion.taskId else picked + (suggestion.taskId to member) },
                            )
                        }
                        Excluded(result, tasksById)
                    }
                }

                Surface(color = if (compact) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = padding, vertical = if (compact) 8.dp else 16.dp),
                    ) {
                        KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
                        KairosButton(onClick = ::apply, enabled = kept.isNotEmpty()) { Text(stringResource(Res.string.suggest_apply)) }
                    }
                }
            }
        }
    }
}

/**
 * Une proposition : [coche] [titre, assigné (menu), raison]. L'assigné se change par le même menu « Assigner à… » que
 * partout (avec la charge de chacun) ; une ligne modifiée ne prétend plus que la règle l'a choisie (« choisi par
 * vous »). Une limite d'en-cours atteinte par le membre proposé est dite avec contour et icône `Warning`.
 */
@Composable
private fun SuggestionRow(
    task: Task,
    suggestion: AssignmentSuggestion.Suggestion,
    memberId: Long,
    changed: Boolean,
    checked: Boolean,
    context: AssignContext,
    today: LocalDate,
    language: String,
    onCheck: (Boolean) -> Unit,
    onPick: (Long) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val name = context.nameOrNull(memberId).orEmpty()
    val include = stringResource(Res.string.suggest_include, task.title)
    val change = stringResource(Res.string.suggest_change, task.title)
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onCheck, modifier = Modifier.semantics { contentDescription = include })
        Column(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge)
            Box {
                KairosOutlinedButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = change }) {
                    Text(name)
                    Icon(KairosIcons.ExpandMore, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(18.dp))
                }
                AssignMenu(menu, { menu = false }, context, currentId = memberId, showBacklog = false) { picked -> picked?.let(onPick) }
            }
            Text(
                if (changed) stringResource(Res.string.suggest_manual, name) else reasonText(name, suggestion.reason, today, language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!changed && suggestion.reason.overWipLimit) {
                Flag(stringResource(Res.string.suggest_over_wip, name), flagged = true)
            }
        }
    }
}

/**
 * La raison d'une proposition mise en phrase depuis `Reason` : « Léa : fin prévue le 8 oct., 5 tâches Développement
 * faites en 12 semaines ». Dans l'ordre : la fin prévue (ou « sans date prévue »), l'affinité de catégorie si le
 * membre en a, puis le critère qui a départagé quand ce n'est ni la fin ni l'affinité.
 */
@Composable
private fun reasonText(name: String, reason: AssignmentSuggestion.Reason, today: LocalDate, language: String): String {
    val parts = ArrayList<String>()
    parts += reason.plannedEnd?.let { stringResource(Res.string.suggest_end, dateSpan(it, it, today, language).first) } ?: stringResource(Res.string.suggest_no_date)
    if (reason.decidedBy == AssignmentSuggestion.Decider.EARLIEST && reason.plannedEnd != null) parts += stringResource(Res.string.suggest_decider_earliest)
    if (reason.affinityTasks == 1) {
        parts += stringResource(Res.string.suggest_affinity_one, reason.category, AssignmentSuggestion.AFFINITY_WEEKS)
    } else if (reason.affinityTasks > 1) {
        parts += stringResource(Res.string.suggest_affinity_many, reason.affinityTasks, reason.category, AssignmentSuggestion.AFFINITY_WEEKS)
    }
    when (reason.decidedBy) {
        AssignmentSuggestion.Decider.WIP_LIMIT -> parts += stringResource(Res.string.suggest_decider_wip)
        AssignmentSuggestion.Decider.LOAD -> parts += stringResource(Res.string.suggest_decider_load)
        AssignmentSuggestion.Decider.ID -> parts += stringResource(Res.string.suggest_decider_id)
        AssignmentSuggestion.Decider.EARLIEST, AssignmentSuggestion.Decider.AFFINITY -> Unit
    }
    return stringResource(Res.string.suggest_reason, name, parts.joinToString(", "))
}

/** Ce qui est exclu de la suggestion (titres des tâches à qualifier et impossibles à placer, membres archivés). */
@Composable
private fun Excluded(result: AssignmentSuggestion.Result, tasksById: Map<Long, Task>) {
    val qualify = result.toQualify.mapNotNull { tasksById[it]?.title }
    val unplaceable = result.unplaceable.mapNotNull { tasksById[it]?.title }
    if (qualify.isEmpty() && unplaceable.isEmpty() && result.archivedMembers == 0) return
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.suggest_excluded_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.heading())
        if (qualify.isNotEmpty()) ExcludedList(stringResource(Res.string.suggest_excluded_qualify, qualify.size), qualify)
        if (unplaceable.isNotEmpty()) ExcludedList(stringResource(Res.string.suggest_excluded_unplaceable, unplaceable.size), unplaceable)
        if (result.archivedMembers > 0) {
            Text(
                if (result.archivedMembers == 1) stringResource(Res.string.suggest_excluded_archived_one) else stringResource(Res.string.suggest_excluded_archived_many, result.archivedMembers),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ExcludedList(heading: String, titles: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(heading, style = MaterialTheme.typography.bodyMedium)
        titles.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
