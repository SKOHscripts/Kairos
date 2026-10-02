package com.skohscripts.kairos.ui.team.forecast

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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.team.MemberForm
import com.skohscripts.kairos.core.team.TeamMembers
import com.skohscripts.kairos.core.team.forecast.Scenario
import com.skohscripts.kairos.core.team.forecast.ScenarioModification
import com.skohscripts.kairos.core.team.forecast.TeamScenario
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.action_delete
import com.skohscripts.kairos.ui.generated.resources.action_save
import com.skohscripts.kairos.ui.generated.resources.mod_hypothetical_suffix
import com.skohscripts.kairos.ui.generated.resources.mod_ignored
import com.skohscripts.kairos.ui.generated.resources.scenario_editor_add
import com.skohscripts.kairos.ui.generated.resources.scenario_editor_close
import com.skohscripts.kairos.ui.generated.resources.scenario_editor_edit_title
import com.skohscripts.kairos.ui.generated.resources.scenario_editor_empty
import com.skohscripts.kairos.ui.generated.resources.scenario_editor_name
import com.skohscripts.kairos.ui.generated.resources.scenario_editor_new_title
import com.skohscripts.kairos.ui.generated.resources.scenario_editor_mods
import com.skohscripts.kairos.ui.generated.resources.scenario_editor_remove
import com.skohscripts.kairos.ui.generated.resources.scenario_unreadable
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.navigation.LocalWindowWidth
import com.skohscripts.kairos.ui.navigation.isCompactWidth
import com.skohscripts.kairos.ui.team.Flag
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Éditeur d'un scénario « Et si… ? » ([scenario] nul : nouveau) : plein écran sous 600 dp, dialogue au-delà
 * (docs/spec/equipe-simulation.md § Interface). « Enregistrer » écrit par le dépôt (`createScenario` /
 * `updateScenario`) ; « Annuler » ne touche à rien. Le scénario est une copie de travail : rien de réel n'est modifié.
 */
@Composable
internal fun ScenarioEditor(services: AppServices, scenario: TeamScenario?, onDismiss: () -> Unit) {
    val snapshot by services.repository.snapshot.collectAsState()
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = services.clock.now().toLocalDateTime(zone).date
    val compact = LocalWindowWidth.current.isCompactWidth
    val coroutines = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ScenarioEditorContent(
            snapshot, scenario, today, compact,
            onSave = { name, mods ->
                coroutines.launch {
                    if (scenario == null) services.repository.createScenario(name, mods) else services.repository.updateScenario(scenario.id, name, mods)
                    onDismiss()
                }
            },
            onDismiss = onDismiss,
        )
    }
}

/**
 * Le contenu de l'éditeur, sans dialogue (captures de l'auto-test) : nom, liste des modifications en `ListItem`
 * (chacune en phrase lisible, supprimable ; un clic la rouvre), menu des neuf types, et le sort de chaque modification
 * sur les données actuelles : celles que `Scenario.apply` ignorerait sont signalées avec leur raison (contour + icône).
 */
@Composable
fun ScenarioEditorContent(
    snapshot: KairosSnapshot,
    scenario: TeamScenario?,
    today: LocalDate,
    compact: Boolean,
    onSave: (String, List<ScenarioModification>) -> Unit = { _, _ -> },
    onDismiss: () -> Unit = {},
) {
    val language = Locale.current.language
    var name by remember { mutableStateOf(scenario?.name.orEmpty()) }
    var mods by remember { mutableStateOf(scenario?.modifications.orEmpty()) }
    var adding by remember { mutableStateOf<ModType?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }

    val skipped = remember(snapshot, mods) { Scenario.apply(snapshot, mods).second.associate { it.index to it.reason } }
    val names = remember(snapshot, mods) { ScenarioNames(snapshot, mods) }
    val choices = rememberChoices(snapshot, mods, today, stringResource(Res.string.mod_hypothetical_suffix))

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
                if (compact) IconButton(onClick = onDismiss) { Icon(KairosIcons.Close, contentDescription = stringResource(Res.string.scenario_editor_close)) }
                Text(
                    stringResource(if (scenario == null) Res.string.scenario_editor_new_title else Res.string.scenario_editor_edit_title),
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.heading(),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f, fill = compact).verticalScroll(rememberScrollState()).padding(horizontal = padding, vertical = 12.dp),
            ) {
                OutlinedTextField(
                    name, { name = it }, label = { Text(stringResource(Res.string.scenario_editor_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(Res.string.scenario_editor_mods), style = MaterialTheme.typography.titleSmall, modifier = Modifier.heading())
                if (mods.isEmpty()) {
                    Text(stringResource(Res.string.scenario_editor_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                mods.forEachIndexed { index, mod ->
                    ListItem(
                        headlineContent = { Text(modificationText(mod, names, today, language)) },
                        supportingContent = skipped[index]?.let { reason ->
                            { Flag(stringResource(Res.string.mod_ignored, skipReasonText(reason)), flagged = true, modifier = Modifier.padding(top = 4.dp)) }
                        },
                        trailingContent = {
                            IconButton(onClick = { mods = mods.filterIndexed { i, _ -> i != index } }) {
                                Icon(KairosIcons.Delete, contentDescription = stringResource(Res.string.scenario_editor_remove))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(role = Role.Button) { editing = index },
                    )
                    HorizontalDivider()
                }
                scenario?.ignored?.takeIf { it.isNotEmpty() }?.let { unreadable ->
                    Flag(stringResource(Res.string.scenario_unreadable, unreadable.size), flagged = true)
                }
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(KairosIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.scenario_editor_add), modifier = Modifier.padding(start = 8.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ModType.entries.forEach { type ->
                            DropdownMenuItem(text = { Text(stringResource(type.title)) }, onClick = { menu = false; adding = type })
                        }
                    }
                }
            }
            Surface(color = if (compact) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = padding, vertical = if (compact) 8.dp else 16.dp),
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
                    Button(onClick = { onSave(name.trim(), mods) }, enabled = name.isNotBlank()) { Text(stringResource(Res.string.action_save)) }
                }
            }
        }
    }

    adding?.let { type ->
        ModificationDialog(type, null, choices, language, onConfirm = { mods = mods + it; adding = null }, onDismiss = { adding = null })
    }
    editing?.let { index ->
        val current = mods.getOrNull(index)
        if (current == null) {
            editing = null
        } else {
            ModificationDialog(
                current.type(), current, choices, language,
                onConfirm = { updated -> mods = mods.mapIndexed { i, m -> if (i == index) updated else m }; editing = null },
                onDismiss = { editing = null },
            )
        }
    }
}

/** Les membres et tâches que les menus du dialogue proposent, pour la copie de travail [mods] du scénario. */
@Composable
private fun rememberChoices(snapshot: KairosSnapshot, mods: List<ScenarioModification>, today: LocalDate, hypotheticalSuffix: String): EditorChoices =
    remember(snapshot, mods, today, hypotheticalSuffix) {
        val real = TeamMembers.active(snapshot.members).map { it.id to it.name }
        val imagined = mods.filterIsInstance<ScenarioModification.AddMember>().map { it.tempId to "${it.name} $hypotheticalSuffix" }
        val tasks = snapshot.tasks
            .filter { it.space == TaskSpace.TEAM && it.status == TaskStatus.TODO && it.teamUid != null }
            .sortedBy { it.title }
            .map { it.teamUid!! to it.title }
        val used = snapshot.tasks.filter { it.space == TaskSpace.TEAM }.map { it.taskType }.filter { it.isNotEmpty() }
        EditorChoices(
            members = real + imagined,
            tasks = tasks,
            categories = (snapshot.settings.taskTypeList + used).distinct(),
            nextTempId = (mods.filterIsInstance<ScenarioModification.AddMember>().minOfOrNull { it.tempId }?.coerceAtMost(0L) ?: 0L) - 1,
            today = today,
            defaultHours = MemberForm.defaultHoursPerDay(snapshot.settings),
        )
    }
