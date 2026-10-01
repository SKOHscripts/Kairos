package com.skohscripts.kairos.ui.team.forecast

import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.TeamMembers
import com.skohscripts.kairos.core.team.forecast.ForecastModel
import com.skohscripts.kairos.core.team.forecast.ForecastScope
import com.skohscripts.kairos.core.team.forecast.MonteCarlo
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.expandedState
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.forecast_intro
import com.skohscripts.kairos.ui.generated.resources.forecast_model
import com.skohscripts.kairos.ui.generated.resources.forecast_model_effort
import com.skohscripts.kairos.ui.generated.resources.forecast_model_throughput
import com.skohscripts.kairos.ui.generated.resources.forecast_model_throughput_unavailable
import com.skohscripts.kairos.ui.generated.resources.forecast_options
import com.skohscripts.kairos.ui.generated.resources.forecast_options_backlog
import com.skohscripts.kairos.ui.generated.resources.forecast_options_backlog_help
import com.skohscripts.kairos.ui.generated.resources.forecast_options_capacity
import com.skohscripts.kairos.ui.generated.resources.forecast_options_capacity_help
import com.skohscripts.kairos.ui.generated.resources.forecast_options_runs
import com.skohscripts.kairos.ui.generated.resources.forecast_options_summary_backlog
import com.skohscripts.kairos.ui.generated.resources.forecast_options_summary_capacity
import com.skohscripts.kairos.ui.generated.resources.forecast_options_summary_runs
import com.skohscripts.kairos.ui.generated.resources.forecast_options_throughput
import com.skohscripts.kairos.ui.generated.resources.forecast_preparing
import com.skohscripts.kairos.ui.generated.resources.forecast_progress
import com.skohscripts.kairos.ui.generated.resources.forecast_progress_column
import com.skohscripts.kairos.ui.generated.resources.forecast_result_title
import com.skohscripts.kairos.ui.generated.resources.forecast_run
import com.skohscripts.kairos.ui.generated.resources.forecast_scope
import com.skohscripts.kairos.ui.generated.resources.forecast_scope_assigned
import com.skohscripts.kairos.ui.generated.resources.forecast_scope_backlog
import com.skohscripts.kairos.ui.generated.resources.forecast_scope_category
import com.skohscripts.kairos.ui.generated.resources.forecast_scope_member
import com.skohscripts.kairos.ui.generated.resources.forecast_scope_selection
import com.skohscripts.kairos.ui.generated.resources.forecast_stop
import com.skohscripts.kairos.ui.generated.resources.forecast_summary
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.settings.SwitchRow
import com.skohscripts.kairos.ui.team.categoryName
import com.skohscripts.kairos.ui.team.rememberComputed
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Prévisions (docs/spec/equipe-simulation.md § Interface) : choix du périmètre et du modèle, options repliables,
 * « Lancer la simulation », progression déterminée avec « Arrêter », puis les panneaux de résultat (date de fin et
 * histogramme, « combien d'ici », échéances, criticité, goulot, issue la plus probable) et les scénarios « Et si… ? ».
 *
 * Aucun calcul n'est lancé tout seul. Le résultat reste en mémoire ([TeamUiState]) et devient « périmé » quand les
 * données qui l'ont produit changent (empreinte [ForecastFingerprint]), sans être recalculé. Colonne de 960 dp au plus.
 */
@Composable
fun ForecastScreen(services: AppServices) {
    val ui = services.teamUi
    val snapshot by services.repository.snapshot.collectAsState()
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = services.clock.now().toLocalDateTime(zone).date
    val language = Locale.current.language
    val coroutines = rememberCoroutineScope()
    val runner = remember(coroutines) { ForecastController(services, coroutines) }
    val names = remember(snapshot) { ForecastNames(snapshot) }
    val history = rememberComputed(snapshot, today) { ForecastEngine.data(snapshot, services.clock.now(), zone) }.value
    val fingerprint = remember(snapshot, today) { ForecastFingerprint.of(snapshot, today) }

    // --- Ce qu'on peut choisir : catégories et membres présents, sélection du Backlog si elle existe.
    val open = remember(snapshot.tasks) { snapshot.tasks.filter { it.space == TaskSpace.TEAM && it.status == TaskStatus.TODO } }
    val categories = remember(open, snapshot.settings) {
        val used = open.map { it.taskType }.distinct()
        snapshot.settings.taskTypeList.filter { it in used } + used.filter { it !in snapshot.settings.taskTypeList }.sorted()
    }
    val members = remember(snapshot.members) { TeamMembers.active(snapshot.members) }
    val selection = remember(ui.backlogSelection, open) {
        val ids = open.mapTo(HashSet()) { it.id }
        ui.backlogSelection.filterTo(HashSet()) { it in ids }.toSet()
    }
    val stored = ui.spec
    val scope: ForecastScope = when (val s = stored.scope) {
        is ForecastScope.Tasks -> if (selection.isNotEmpty()) ForecastScope.Tasks(selection) else ForecastScope.Assigned
        is ForecastScope.Member -> if (members.any { it.id == s.memberId }) s else ForecastScope.Assigned
        is ForecastScope.Category -> if (s.name in categories) s else ForecastScope.Assigned
        else -> s
    }
    val memberScope = (scope as? ForecastScope.Member)?.memberId
    val throughputOk = history?.let { MonteCarlo.isAvailable(ForecastModel.THROUGHPUT, it, scope) } == true
    val spec = ForecastSpec(scope, if (stored.model == ForecastModel.THROUGHPUT && !throughputOk) ForecastModel.EFFORT else stored.model, stored.options)

    val options = buildList {
        add(ForecastScope.Assigned to stringResource(Res.string.forecast_scope_assigned))
        add(ForecastScope.AssignedAndBacklog to stringResource(Res.string.forecast_scope_backlog))
        categories.forEach { add(ForecastScope.Category(it) to stringResource(Res.string.forecast_scope_category, categoryName(it))) }
        members.forEach { add(ForecastScope.Member(it.id) to stringResource(Res.string.forecast_scope_member, it.name)) }
        if (selection.isNotEmpty()) add(ForecastScope.Tasks(selection) to stringResource(Res.string.forecast_scope_selection, selection.size))
    }

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.forecast_intro), style = MaterialTheme.typography.bodyMedium)

            // --- Périmètre et modèle
            ChoiceField(
                label = stringResource(Res.string.forecast_scope),
                value = options.firstOrNull { it.first == scope }?.second.orEmpty(),
                options = options,
                onPick = { ui.spec = stored.copy(scope = it) },
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            )
            ModelChoice(
                model = spec.model,
                throughputAvailable = throughputOk,
                explanation = if (history != null && !throughputOk) {
                    stringResource(
                        Res.string.forecast_model_throughput_unavailable,
                        com.skohscripts.kairos.core.team.forecast.ForecastData.MIN_THROUGHPUT_WEEKS,
                        history.windowWeeks,
                        history.throughputOf(memberScope).count { it > 0 },
                    )
                } else {
                    null
                },
                onPick = { ui.spec = stored.copy(model = it) },
            )
            OptionsCard(spec, snapshot, onChange = { ui.spec = stored.copy(options = it) }, language = language)

            // --- Lancer, ou progression
            if (runner.busy) {
                Progress(runner, language)
            } else {
                KairosButton(
                    onClick = { runner.run(spec, snapshot) },
                    contentPadding = KairosButtonIconPadding,
                ) {
                    Icon(KairosIcons.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.forecast_run), modifier = Modifier.padding(start = 8.dp))
                }
            }

            // --- Résultat gardé en mémoire
            ui.forecast?.let { run ->
                key(run) {
                    ResultSection(run, stale = run.fingerprint != fingerprint, snapshot = snapshot, names = names, zone = zone, language = language)
                }
            }

            // --- Scénarios « Et si… ? » et leur comparaison
            ScenarioSection(services, snapshot, runner, spec, fingerprint, names, today, zone, language)
        }
    }
}

/** « Par effort » / « Par débit » : le second est désactivé, avec sa raison, tant que l'historique ne suffit pas. */
@Composable
private fun ModelChoice(model: ForecastModel, throughputAvailable: Boolean, explanation: String?, onPick: (ForecastModel) -> Unit) {
    Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(Res.string.forecast_model), style = MaterialTheme.typography.labelLarge)
        KairosSegmentedRow(Modifier.fillMaxWidth()) {
            KairosSegmentedButton(
                selected = model == ForecastModel.EFFORT,
                onClick = { onPick(ForecastModel.EFFORT) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(Res.string.forecast_model_effort)) }
            KairosSegmentedButton(
                selected = model == ForecastModel.THROUGHPUT,
                onClick = { onPick(ForecastModel.THROUGHPUT) },
                enabled = throughputAvailable,
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(stringResource(Res.string.forecast_model_throughput)) }
        }
        if (explanation != null) {
            Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Options du modèle par effort, repliées par défaut dans une carte à contour : « Inclure le backlog non assigné »,
 * « Aléa de capacité » et le nombre de tirages (un réglage de l'équipe, affiché ici).
 */
@Composable
private fun OptionsCard(spec: ForecastSpec, snapshot: KairosSnapshot, onChange: (com.skohscripts.kairos.core.team.forecast.ForecastOptions) -> Unit, language: String) {
    var expanded by remember { mutableStateOf(false) }
    val runs = (snapshot.settings.team ?: TeamSettings()).simulationRuns
    val effort = spec.model == ForecastModel.EFFORT
    val summary = buildList {
        add(stringResource(Res.string.forecast_options_summary_runs, groupedNumber(runs, language)))
        if (effort && spec.options.includeBacklog) add(stringResource(Res.string.forecast_options_summary_backlog))
        if (effort && spec.options.capacityRandomness) add(stringResource(Res.string.forecast_options_summary_capacity))
    }.joinToString(" · ")
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .expandedState(expanded)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(KairosIcons.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.forecast_options), style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
            }
            if (expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (effort) {
                        SwitchRow(spec.options.includeBacklog, stringResource(Res.string.forecast_options_backlog)) { onChange(spec.options.copy(includeBacklog = it)) }
                        Hint(stringResource(Res.string.forecast_options_backlog_help))
                        SwitchRow(spec.options.capacityRandomness, stringResource(Res.string.forecast_options_capacity)) { onChange(spec.options.copy(capacityRandomness = it)) }
                        Hint(stringResource(Res.string.forecast_options_capacity_help))
                    } else {
                        Hint(stringResource(Res.string.forecast_options_throughput))
                    }
                    Text(stringResource(Res.string.forecast_options_runs, groupedNumber(runs, language)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Progression d'un calcul : indéterminée pendant la préparation, puis « 3 200 / 5 000 tirages » ; « Arrêter » garde ce qui est fait. */
@Composable
internal fun Progress(runner: ForecastController, language: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val phase = runner.phase) {
                RunPhase.Idle -> Unit
                is RunPhase.Preparing -> {
                    val text = stringResource(Res.string.forecast_preparing)
                    LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = text })
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
                is RunPhase.Running -> {
                    val counts = stringResource(Res.string.forecast_progress, groupedNumber(phase.done, language), groupedNumber(phase.total, language))
                    val text = if (phase.column != null) stringResource(Res.string.forecast_progress_column, phase.column, counts) else counts
                    LinearProgressIndicator(
                        progress = { if (phase.total == 0) 0f else phase.done.toFloat() / phase.total },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = text },
                    )
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            KairosOutlinedButton(onClick = runner::stop, contentPadding = KairosButtonIconPadding) {
                Icon(KairosIcons.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.forecast_stop), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Le résultat en mémoire : ce qui a été simulé, les mentions « périmé » / « interrompu », puis les panneaux. */
@Composable
private fun ResultSection(
    run: ForecastRun,
    stale: Boolean,
    snapshot: KairosSnapshot,
    names: ForecastNames,
    zone: TimeZone,
    language: String,
) {
    val result = run.result
    val riskPercent = (snapshot.settings.team ?: TeamSettings()).deadlineRiskPercent
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(Res.string.forecast_result_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.heading())
            Text(
                stringResource(Res.string.forecast_summary, scopeLabel(run.spec.scope, names, snapshot), modelLabel(run.spec.model)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ResultBanners(result, stale, language)
        FinishPanel(run, zone, language)
        if (result.scopeSize > 0) {
            FinishedByPanel(result, language)
            if (result.taskLevel) {
                DeadlinesPanel(result, names, riskPercent, language)
                CriticalityPanel(result, names)
                BottleneckPanel(result, names)
                OutcomePanel(result, names)
            } else {
                MaskedPanels()
            }
        }
    }
}

@Composable
internal fun modelLabel(model: ForecastModel): String =
    stringResource(if (model == ForecastModel.EFFORT) Res.string.forecast_model_effort else Res.string.forecast_model_throughput)

/** Le périmètre d'un résultat en mots (le menu d'origine peut avoir changé depuis). */
@Composable
internal fun scopeLabel(scope: ForecastScope, names: ForecastNames, snapshot: KairosSnapshot): String = when (scope) {
    ForecastScope.Assigned -> stringResource(Res.string.forecast_scope_assigned)
    ForecastScope.AssignedAndBacklog -> stringResource(Res.string.forecast_scope_backlog)
    is ForecastScope.Category -> stringResource(Res.string.forecast_scope_category, categoryName(scope.name))
    is ForecastScope.Member -> stringResource(Res.string.forecast_scope_member, names.member(scope.memberId))
    is ForecastScope.Tasks -> stringResource(Res.string.forecast_scope_selection, scope.taskIds.size)
}
