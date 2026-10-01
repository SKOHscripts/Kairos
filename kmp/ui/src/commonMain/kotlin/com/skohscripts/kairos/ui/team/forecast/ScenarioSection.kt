package com.skohscripts.kairos.ui.team.forecast

import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosRowIconButton
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.team.forecast.ForecastScope
import com.skohscripts.kairos.core.team.forecast.Scenario
import com.skohscripts.kairos.core.team.forecast.ScenarioModification
import com.skohscripts.kairos.core.team.forecast.TeamScenario
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.action_delete
import com.skohscripts.kairos.ui.generated.resources.action_edit
import com.skohscripts.kairos.ui.generated.resources.action_save
import com.skohscripts.kairos.ui.generated.resources.forecast_real
import com.skohscripts.kairos.ui.generated.resources.mod_ignored
import com.skohscripts.kairos.ui.generated.resources.scenario_actions
import com.skohscripts.kairos.ui.generated.resources.scenario_apply
import com.skohscripts.kairos.ui.generated.resources.scenario_apply_body
import com.skohscripts.kairos.ui.generated.resources.scenario_apply_confirm
import com.skohscripts.kairos.ui.generated.resources.scenario_apply_hypothetical
import com.skohscripts.kairos.ui.generated.resources.scenario_apply_nothing
import com.skohscripts.kairos.ui.generated.resources.scenario_apply_real
import com.skohscripts.kairos.ui.generated.resources.scenario_apply_summary
import com.skohscripts.kairos.ui.generated.resources.scenario_apply_title
import com.skohscripts.kairos.ui.generated.resources.scenario_compare
import com.skohscripts.kairos.ui.generated.resources.scenario_compare_backlog
import com.skohscripts.kairos.ui.generated.resources.scenario_check_label
import com.skohscripts.kairos.ui.generated.resources.scenario_compare_check
import com.skohscripts.kairos.ui.generated.resources.scenario_compare_limit
import com.skohscripts.kairos.ui.generated.resources.scenario_copy_name
import com.skohscripts.kairos.ui.generated.resources.scenario_delete_body
import com.skohscripts.kairos.ui.generated.resources.scenario_delete_title
import com.skohscripts.kairos.ui.generated.resources.scenario_duplicate
import com.skohscripts.kairos.ui.generated.resources.scenario_editor_name
import com.skohscripts.kairos.ui.generated.resources.scenario_empty
import com.skohscripts.kairos.ui.generated.resources.scenario_hint
import com.skohscripts.kairos.ui.generated.resources.scenario_missing
import com.skohscripts.kairos.ui.generated.resources.scenario_mods_many
import com.skohscripts.kairos.ui.generated.resources.scenario_mods_none
import com.skohscripts.kairos.ui.generated.resources.scenario_mods_one
import com.skohscripts.kairos.ui.generated.resources.scenario_new
import com.skohscripts.kairos.ui.generated.resources.scenario_no_longer
import com.skohscripts.kairos.ui.generated.resources.scenario_rename
import com.skohscripts.kairos.ui.generated.resources.scenario_rename_title
import com.skohscripts.kairos.ui.generated.resources.scenario_title
import com.skohscripts.kairos.ui.generated.resources.scenario_unreadable
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.stats.Panel
import com.skohscripts.kairos.ui.team.Flag
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Ce que fait l'éditeur de scénario ouvert : un nouveau scénario, ou l'édition d'un scénario enregistré. */
private sealed interface EditorTarget {
    data object New : EditorTarget
    data class Existing(val id: Long) : EditorTarget
}

/**
 * La section « Scénarios » des Prévisions (docs/spec/equipe-simulation.md § Scénarios « Et si… ? ») : la liste des
 * scénarios enregistrés (renommer, dupliquer, supprimer avec confirmation, appliquer), « Nouveau scénario »,
 * et la comparaison de la situation réelle avec jusqu'à trois scénarios cochés. Les scénarios ne modifient jamais
 * les données réelles : seul « Appliquer », après confirmation, écrit (par le dépôt).
 */
@Composable
internal fun ScenarioSection(
    services: AppServices,
    snapshot: KairosSnapshot,
    runner: ForecastController,
    spec: ForecastSpec,
    fingerprint: ForecastFingerprint,
    names: ForecastNames,
    today: LocalDate,
    zone: TimeZone,
    language: String,
) {
    val ui = services.teamUi
    val repository = services.repository
    val coroutines = rememberCoroutineScope()
    val messages = LocalMessages.current
    val scenarios = snapshot.teamScenarios
    var editor by remember { mutableStateOf<EditorTarget?>(null) }
    var renaming by remember { mutableStateOf<TeamScenario?>(null) }
    var deleting by remember { mutableStateOf<TeamScenario?>(null) }
    var applying by remember { mutableStateOf<TeamScenario?>(null) }

    // Ce que chaque scénario ne sait plus appliquer aux données d'aujourd'hui (tâche supprimée, membre archivé…).
    val notApplicableCounts = remember(snapshot) { scenarios.associate { it.id to (Scenario.apply(snapshot, it.modifications).second.size) } }
    val checked = remember(ui.compared, scenarios) { ui.compared.filter { id -> scenarios.any { it.id == id } }.toSet() }
    val selected = scenarios.filter { it.id in checked }
    val duplicated = scenarios.associate { it.id to stringResource(Res.string.scenario_copy_name, it.name) }

    Panel(KairosIcons.Science, stringResource(Res.string.scenario_title), stringResource(Res.string.scenario_hint)) {
        if (scenarios.isEmpty()) {
            Text(stringResource(Res.string.scenario_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            scenarios.forEachIndexed { index, scenario ->
                if (index > 0) HorizontalDivider()
                ScenarioRow(
                    scenario = scenario,
                    notApplicable = notApplicableCounts[scenario.id] ?: 0,
                    checked = scenario.id in checked,
                    canCheck = scenario.id in checked || checked.size < MAX_COMPARED,
                    onCheck = { on -> ui.compared = if (on) checked + scenario.id else checked - scenario.id },
                    onOpen = { editor = EditorTarget.Existing(scenario.id) },
                    onRename = { renaming = scenario },
                    onDuplicate = { coroutines.launch { repository.duplicateScenario(scenario.id, duplicated.getValue(scenario.id)) } },
                    onApply = { applying = scenario },
                    onDelete = { deleting = scenario },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KairosOutlinedButton(onClick = { editor = EditorTarget.New }, contentPadding = KairosButtonIconPadding) {
                Icon(KairosIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.scenario_new), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (scenarios.isNotEmpty()) {
            Text(
                stringResource(if (checked.size >= MAX_COMPARED) Res.string.scenario_compare_limit else Res.string.scenario_compare_check),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val realLabel = stringResource(Res.string.forecast_real)
            KairosButton(
                onClick = { runner.compare(spec, snapshot, selected, realLabel) },
                enabled = selected.isNotEmpty() && !runner.busy,
                contentPadding = KairosButtonIconPadding,
            ) {
                Icon(KairosIcons.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.scenario_compare, selected.size), modifier = Modifier.padding(start = 8.dp))
            }
            val backlogNeeded = spec.scope == ForecastScope.Assigned && !spec.options.includeBacklog &&
                selected.any { s -> s.modifications.any { it is ScenarioModification.AddTasks || it is ScenarioModification.RemoveMember } }
            if (backlogNeeded) Notice(stringResource(Res.string.scenario_compare_backlog), KairosIcons.Info)
        }
    }

    if (runner.busy && runner.phase.let { it is RunPhase.Preparing && it.comparison || it is RunPhase.Running && it.comparison }) {
        Progress(runner, language)
    }
    ui.comparison?.let { comparison ->
        val unchanged = comparison.scenarios.all { used -> scenarios.firstOrNull { it.id == used.id }?.let { it.name == used.name && it.modifications == used.modifications } == true }
        ScenarioComparison(comparison, stale = comparison.fingerprint != fingerprint || !unchanged, realLabel = stringResource(Res.string.forecast_real), zone = zone, language = language)
    }

    // --- Dialogues
    editor?.let { target ->
        val scenario = (target as? EditorTarget.Existing)?.id?.let { id -> scenarios.firstOrNull { it.id == id } }
        if (target is EditorTarget.Existing && scenario == null) editor = null else ScenarioEditor(services, scenario, onDismiss = { editor = null })
    }
    renaming?.let { scenario ->
        RenameDialog(scenario.name, onConfirm = { name ->
            renaming = null
            coroutines.launch { repository.updateScenario(scenario.id, name, scenario.modifications) }
        }, onDismiss = { renaming = null })
    }
    deleting?.let { scenario ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(Res.string.scenario_delete_title)) },
            text = { Text(stringResource(Res.string.scenario_delete_body, scenario.name)) },
            confirmButton = {
                KairosTextButton(onClick = {
                    deleting = null
                    ui.compared = ui.compared - scenario.id
                    coroutines.launch { repository.deleteScenario(scenario.id) }
                }) { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { KairosTextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    applying?.let { scenario ->
        ScenarioApplyDialog(
            scenario, snapshot, today, language,
            onConfirm = {
                applying = null
                coroutines.launch {
                    val report = repository.applyScenario(scenario.id)
                    messages(
                        if (!report.found) {
                            getString(Res.string.scenario_missing)
                        } else {
                            getString(Res.string.scenario_apply_summary, report.applied.size, report.skipped.size, report.notApplicable.size)
                        },
                    )
                }
            },
            onDismiss = { applying = null },
        )
    }
}

private const val MAX_COMPARED = 3

/** Une ligne de la liste : case « comparer », nom et nombre de modifications, mentions, menu ⋮. */
@Composable
private fun ScenarioRow(
    scenario: TeamScenario,
    notApplicable: Int,
    checked: Boolean,
    canCheck: Boolean,
    onCheck: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val compareLabel = stringResource(Res.string.scenario_check_label, scenario.name)
    val count = scenario.modifications.size
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Checkbox(checked, onCheckedChange = onCheck, enabled = canCheck, modifier = Modifier.semantics { contentDescription = compareLabel })
        Column(
            Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(scenario.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                when (count) {
                    0 -> stringResource(Res.string.scenario_mods_none)
                    1 -> stringResource(Res.string.scenario_mods_one)
                    else -> stringResource(Res.string.scenario_mods_many, count)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (notApplicable > 0) Flag(stringResource(Res.string.scenario_no_longer, notApplicable), flagged = true)
            if (scenario.ignored.isNotEmpty()) Flag(stringResource(Res.string.scenario_unreadable, scenario.ignored.size), flagged = true)
        }
        Box {
            KairosRowIconButton(KairosIcons.MoreVert, stringResource(Res.string.scenario_actions, scenario.name), onClick = { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_edit)) },
                    leadingIcon = { Icon(KairosIcons.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { menu = false; onOpen() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.scenario_rename)) },
                    leadingIcon = { Icon(KairosIcons.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { menu = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.scenario_duplicate)) },
                    leadingIcon = { Icon(KairosIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { menu = false; onDuplicate() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.scenario_apply)) },
                    leadingIcon = { Icon(KairosIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { menu = false; onApply() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_delete)) },
                    leadingIcon = { Icon(KairosIcons.Delete, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.scenario_rename_title)) },
        text = {
            OutlinedTextField(
                name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.scenario_editor_name)) },
            )
        },
        confirmButton = { KairosTextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text(stringResource(Res.string.action_save)) } },
        dismissButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/**
 * Confirmation de « Appliquer » : ce qui sera appliqué aux données réelles (les modifications **réelles**,
 * `Scenario.realChanges`), ce qui ne le sera pas (les membres et tâches hypothétiques ne sont jamais créés), et ce qui ne
 * s'applique plus à la base d'aujourd'hui (avec sa raison, en contour + icône).
 */
@Composable
internal fun ScenarioApplyDialog(scenario: TeamScenario, snapshot: KairosSnapshot, today: LocalDate, language: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val names = remember(snapshot, scenario) { ScenarioNames(snapshot, scenario.modifications) }
    val skipped = remember(snapshot, scenario) { Scenario.apply(snapshot, scenario.modifications).second.associate { it.index to it.reason } }
    val indexed = scenario.modifications.withIndex().toList()
    val real = indexed.filter { Scenario.isReal(it.value) }
    val hypothetical = indexed.filter { !Scenario.isReal(it.value) }
    val effective = real.count { it.index !in skipped }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.scenario_apply_title, scenario.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.scenario_apply_body), style = MaterialTheme.typography.bodyMedium)
                if (real.isNotEmpty()) {
                    Text(stringResource(Res.string.scenario_apply_real, real.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.heading())
                    real.forEach { (index, mod) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("• " + modificationText(mod, names, today, language), style = MaterialTheme.typography.bodyMedium)
                            skipped[index]?.let { Flag(stringResource(Res.string.mod_ignored, skipReasonText(it)), flagged = true) }
                        }
                    }
                }
                if (hypothetical.isNotEmpty()) {
                    Text(stringResource(Res.string.scenario_apply_hypothetical, hypothetical.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.heading())
                    hypothetical.forEach { (_, mod) ->
                        Text("• " + modificationText(mod, names, today, language), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (effective == 0) Text(stringResource(Res.string.scenario_apply_nothing), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { KairosTextButton(enabled = effective > 0, onClick = onConfirm) { Text(stringResource(Res.string.scenario_apply_confirm)) } },
        dismissButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
