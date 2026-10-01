package com.skohscripts.kairos.ui.team

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.team.TeamBoard
import com.skohscripts.kairos.core.team.TeamBoardFilter
import com.skohscripts.kairos.core.team.TeamCard
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.day.Badge
import com.skohscripts.kairos.ui.day.CaptureState
import com.skohscripts.kairos.ui.day.Capture
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.day.Facet
import com.skohscripts.kairos.ui.day.InboxQualify
import com.skohscripts.kairos.ui.day.Muted
import com.skohscripts.kairos.ui.day.PriorityBadge
import com.skohscripts.kairos.ui.day.ScoreBadge
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.action_delete
import com.skohscripts.kairos.ui.generated.resources.backlog_actions
import com.skohscripts.kairos.ui.generated.resources.backlog_assign
import com.skohscripts.kairos.ui.generated.resources.backlog_bulk_assign
import com.skohscripts.kairos.ui.generated.resources.backlog_bulk_category
import com.skohscripts.kairos.ui.generated.resources.backlog_bulk_clear
import com.skohscripts.kairos.ui.generated.resources.backlog_bulk_count
import com.skohscripts.kairos.ui.generated.resources.backlog_bulk_priority
import com.skohscripts.kairos.ui.generated.resources.backlog_empty
import com.skohscripts.kairos.ui.generated.resources.backlog_filter_category
import com.skohscripts.kairos.ui.generated.resources.backlog_filter_deadline
import com.skohscripts.kairos.ui.generated.resources.backlog_filter_none
import com.skohscripts.kairos.ui.generated.resources.backlog_no_category
import com.skohscripts.kairos.ui.generated.resources.backlog_no_priority
import com.skohscripts.kairos.ui.generated.resources.backlog_ready_hint
import com.skohscripts.kairos.ui.generated.resources.backlog_ready_title
import com.skohscripts.kairos.ui.generated.resources.backlog_select
import com.skohscripts.kairos.ui.generated.resources.backlog_to_qualify_empty
import com.skohscripts.kairos.ui.generated.resources.backlog_to_qualify_hint
import com.skohscripts.kairos.ui.generated.resources.backlog_to_qualify_title
import com.skohscripts.kairos.ui.generated.resources.delete_confirm_body
import com.skohscripts.kairos.ui.generated.resources.delete_confirm_title
import com.skohscripts.kairos.ui.generated.resources.deadline_badge
import com.skohscripts.kairos.ui.generated.resources.filter_all
import com.skohscripts.kairos.ui.generated.resources.filter_search
import com.skohscripts.kairos.ui.generated.resources.filter_search_placeholder
import com.skohscripts.kairos.ui.generated.resources.points_badge
import com.skohscripts.kairos.ui.generated.resources.priority_label
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.navigation.LocalWindowWidth
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/** En dessous, priorité et points passent sous le titre (comme la vue Jour) et la sélection se fait à l'appui long. */
private val WIDE_WINDOW = 600.dp

/**
 * Backlog de l'équipe (docs/spec/equipe-backlog-suivi.md § Backlog) : la capture
 * en tête, « À qualifier » (mêmes chips de qualification que « À traiter » de la
 * vue Jour) et « Prêtes » (triées par score WSJF, en retard d'abord), des filtres,
 * et la sélection multiple avec sa barre d'actions. Lit la base **complète**.
 * Toucher une ligne ouvre la fiche ([TeamTaskDialog]) ; le menu ⋮ d'une ligne
 * assigne ou supprime. Les tâches capturées ici sont des tâches d'équipe : elles
 * n'entrent jamais dans la vue Jour tant qu'elles ne sont pas assignées à « moi ».
 * [initialSelection] ouvre l'écran avec des lignes déjà sélectionnées (captures).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamBacklogScreen(services: AppServices, initialSelection: Set<Long> = emptySet()) {
    val repository = services.repository
    val snapshot by repository.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = services.clock.now().toLocalDateTime(timeZone).date
    val language = Locale.current.language
    val wide = LocalWindowWidth.current >= WIDE_WINDOW

    var text by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var priority by rememberSaveable { mutableStateOf<Int?>(null) }
    var withDeadline by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selection by remember { mutableStateOf(initialSelection) }
    val capture = remember { CaptureState() }

    val filter = TeamBoardFilter(text = text, category = category, priority = priority, withDeadline = withDeadline)
    val board = remember(snapshot, today, filter) { TeamBoard.build(snapshot, today, timeZone, filter) }
    val context = remember(snapshot, today) { AssignContext.of(snapshot, today) }
    val unfiltered = filter == TeamBoardFilter()
    val visibleIds = remember(board) { (board.toQualify + board.ready).mapTo(HashSet()) { it.task.id } }
    // Une tâche assignée ou supprimée sort de la sélection.
    val selected = selection.filter { it in visibleIds }.toSet()
    val selecting = selected.isNotEmpty()
    val categories = remember(snapshot.tasks, snapshot.settings) {
        (snapshot.settings.taskTypeList + snapshot.tasks.filter { it.space == TaskSpace.TEAM }.map { it.taskType }.filter { it.isNotEmpty() }).distinct()
    }

    fun toggle(id: Long) {
        selection = if (id in selected) selected - id else selected + id
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (selecting) 176.dp else 16.dp),
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
        ) {
            item(key = "capture") {
                Capture(
                    capture, today, emptyList(), language, showShortcuts = false,
                    onAddTask = { title -> scope.launch { repository.createTeamTask(title) } },
                    onAddBlock = {},
                    onEditBlock = {},
                    taskOnly = true,
                )
            }
            item(key = "filters") {
                BacklogFilters(
                    text, { text = it }, category, { category = it }, priority, { priority = it }, withDeadline, { withDeadline = it },
                    categories,
                )
            }

            // --- À qualifier
            item(key = "toqualify-title") { SectionTitle(stringResource(Res.string.backlog_to_qualify_title), board.toQualify.size, Modifier.padding(top = 8.dp)) }
            item(key = "toqualify-help") {
                Hint(stringResource(if (board.toQualify.isEmpty()) Res.string.backlog_to_qualify_empty else Res.string.backlog_to_qualify_hint))
            }
            items(board.toQualify, key = { "q-${it.task.id}" }) { card ->
                BacklogRow(
                    card, context, language, wide, selecting, card.task.id in selected, null, today,
                    onOpen = { openId = card.task.id }, onToggle = { toggle(card.task.id) },
                    onAssign = { member -> scope.launch { repository.assign(listOf(card.task.id), member) } },
                    onDelete = { deleteId = card.task.id },
                    onPriority = { scope.launch { repository.setPriority(card.task.id, it) } },
                    onPoints = { scope.launch { repository.setPoints(card.task.id, it) } },
                )
            }

            // --- Prêtes
            item(key = "ready-title") { SectionTitle(stringResource(Res.string.backlog_ready_title), board.ready.size, Modifier.padding(top = 8.dp)) }
            item(key = "ready-help") { Hint(stringResource(Res.string.backlog_ready_hint)) }
            items(board.ready, key = { "r-${it.task.id}" }) { card ->
                BacklogRow(
                    card, context, language, wide, selecting, card.task.id in selected, Scheduling.wsjfBreakdown(card.task, today, snapshot.settings), today,
                    onOpen = { openId = card.task.id }, onToggle = { toggle(card.task.id) },
                    onAssign = { member -> scope.launch { repository.assign(listOf(card.task.id), member) } },
                    onDelete = { deleteId = card.task.id },
                    onPriority = {}, onPoints = {},
                )
            }
            if (board.toQualify.isEmpty() && board.ready.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(if (unfiltered) Res.string.backlog_empty else Res.string.backlog_filter_none),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
        if (selecting) {
            SelectionBar(
                count = selected.size,
                context = context,
                categories = categories,
                onClear = { selection = emptySet() },
                onAssign = { member -> scope.launch { repository.assign(selected.toList(), member) }; selection = emptySet() },
                onCategory = { type -> scope.launch { selected.forEach { repository.setTaskType(it, type) } }; selection = emptySet() },
                onPriority = { p -> scope.launch { selected.forEach { repository.setPriority(it, p) } }; selection = emptySet() },
                modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 840.dp),
            )
        }
    }

    openId?.let { id -> TeamTaskDialog(services, id, onDismiss = { openId = null }) }
    deleteId?.let { id ->
        val task = snapshot.tasks.firstOrNull { it.id == id }
        if (task == null) {
            deleteId = null
        } else {
            AlertDialog(
                onDismissRequest = { deleteId = null },
                title = { Text(stringResource(Res.string.delete_confirm_title)) },
                text = { Text(stringResource(Res.string.delete_confirm_body, task.title)) },
                confirmButton = {
                    TextButton(onClick = { deleteId = null; scope.launch { repository.deleteTask(id) } }) {
                        Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(Res.string.action_cancel)) } },
            )
        }
    }
}

/** Recherche dans le titre, catégorie, priorité, « avec échéance » : une carte à contour, toujours ouverte. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BacklogFilters(
    text: String,
    onText: (String) -> Unit,
    category: String?,
    onCategory: (String?) -> Unit,
    priority: Int?,
    onPriority: (Int?) -> Unit,
    withDeadline: Boolean,
    onWithDeadline: (Boolean) -> Unit,
    categories: List<String>,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                text,
                onText,
                label = { Text(stringResource(Res.string.filter_search)) },
                placeholder = { Text(stringResource(Res.string.filter_search_placeholder)) },
                leadingIcon = { Icon(KairosIcons.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val all = stringResource(Res.string.filter_all)
            val none = stringResource(Res.string.backlog_no_category)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Facet(stringResource(Res.string.backlog_filter_category), category, categories + "", { it.ifEmpty { none } }, all, width = 140.dp) { onCategory(it) }
                Facet(stringResource(Res.string.priority_label), priority, PRIORITY_VALUES, { "P$it" }, all, width = 140.dp) { onPriority(it) }
                FilterChip(
                    selected = withDeadline,
                    onClick = { onWithDeadline(!withDeadline) },
                    label = { Text(stringResource(Res.string.backlog_filter_deadline)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

/**
 * Ligne du backlog : [coche] [titre, catégorie, échéance, signaux] [score, priorité,
 * points] [⋮]. Liseré rouge à gauche pour P0, jamais de remplissage ; ligne
 * sélectionnée en `secondaryContainer`. Toucher ouvre la fiche (ou bascule la
 * sélection en mode sélection), l'appui long sélectionne.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun BacklogRow(
    card: TeamCard,
    context: AssignContext,
    language: String,
    wide: Boolean,
    selecting: Boolean,
    selected: Boolean,
    why: Scheduling.WsjfBreakdown?,
    today: LocalDate,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onAssign: (Long?) -> Unit,
    onDelete: () -> Unit,
    onPriority: (Int?) -> Unit,
    onPoints: (Int?) -> Unit,
) {
    val task = card.task
    val scheme = MaterialTheme.colorScheme
    val errorColor = scheme.error
    val checkbox = wide || selecting
    val qualify = task.needsProcessing
    val selectLabel = stringResource(Res.string.backlog_select)
    var menu by remember { mutableStateOf(false) }
    var assign by remember { mutableStateOf(false) }
    Surface(
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .drawBehind { if (task.priority == 0) drawRect(errorColor, size = Size(3.dp.toPx(), size.height)) }
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = selectLabel,
                    onLongClick = onToggle,
                    onClick = { if (selecting) onToggle() else onOpen() },
                )
                .semantics { this.selected = selected }
                .padding(vertical = 4.dp),
        ) {
            BoxWithConstraints {
                val compact = maxWidth < 420.dp
                Row(verticalAlignment = Alignment.Top) {
                    if (checkbox) Checkbox(checked = selected, onCheckedChange = { onToggle() })
                    Column(
                        Modifier.weight(1f).padding(start = if (checkbox) 0.dp else 16.dp, top = 12.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(task.title, style = MaterialTheme.typography.bodyLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (task.taskType.isNotEmpty()) Badge(task.taskType)
                            task.deadline?.let { Muted(stringResource(Res.string.deadline_badge, Dates.short(it, language))) }
                            card.signals.forEach { SignalBadge(it) }
                        }
                        if (compact) RowKeys(card, why, language)
                    }
                    if (!compact) Box(Modifier.padding(top = 10.dp, start = 8.dp)) { RowKeys(card, why, language) }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(KairosIcons.MoreVert, contentDescription = stringResource(Res.string.backlog_actions))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.backlog_assign)) },
                                leadingIcon = { Icon(KairosIcons.Person, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { menu = false; assign = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.action_delete)) },
                                leadingIcon = { Icon(KairosIcons.Delete, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { menu = false; onDelete() },
                            )
                        }
                        AssignMenu(assign, { assign = false }, context, currentId = null, showBacklog = false, onPick = onAssign)
                    }
                }
            }
            if (qualify) InboxQualify(task, onPriority, onPoints, start = if (checkbox) 48.dp else 16.dp)
        }
    }
}

/** Score (chiffre primaire, « Pourquoi à cette place ? »), priorité et points d'une ligne du backlog. */
@Composable
private fun RowKeys(card: TeamCard, why: Scheduling.WsjfBreakdown?, language: String) {
    val task = card.task
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        why?.let { ScoreBadge(task, it, language) }
        task.priority?.let { PriorityBadge(it) }
        task.fibonacciPoints?.let { Badge(stringResource(Res.string.points_badge, it)) }
    }
}

/**
 * Barre d'actions de la sélection multiple, en bas : « Assigner à… », « Changer la
 * catégorie », « Changer la priorité », et la fermeture de la sélection. Surface
 * pleine sans ombre (elle recouvre la liste, elle ne flotte pas).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectionBar(
    count: Int,
    context: AssignContext,
    categories: List<String>,
    onClear: () -> Unit,
    onAssign: (Long?) -> Unit,
    onCategory: (String) -> Unit,
    onPriority: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var assign by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(false) }
    var priority by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClear) {
                    Icon(KairosIcons.Close, contentDescription = stringResource(Res.string.backlog_bulk_clear))
                }
                Text(stringResource(Res.string.backlog_bulk_count, count), style = MaterialTheme.typography.titleSmall, modifier = Modifier.heading())
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            ) {
                Box {
                    Button(onClick = { assign = true }) {
                        Icon(KairosIcons.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.backlog_bulk_assign), modifier = Modifier.padding(start = 6.dp))
                    }
                    AssignMenu(assign, { assign = false }, context, currentId = null, showBacklog = false, onPick = onAssign)
                }
                Box {
                    OutlinedButton(onClick = { category = true }) { Text(stringResource(Res.string.backlog_bulk_category)) }
                    DropdownMenu(expanded = category, onDismissRequest = { category = false }) {
                        val none = stringResource(Res.string.backlog_no_category)
                        (categories + "").forEach { type ->
                            DropdownMenuItem(text = { Text(type.ifEmpty { none }) }, onClick = { category = false; onCategory(type) })
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { priority = true }) { Text(stringResource(Res.string.backlog_bulk_priority)) }
                    DropdownMenu(expanded = priority, onDismissRequest = { priority = false }) {
                        PRIORITY_VALUES.forEach { p ->
                            DropdownMenuItem(text = { Text("P$p") }, onClick = { priority = false; onPriority(p) })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(Res.string.backlog_no_priority)) }, onClick = { priority = false; onPriority(null) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, count: Int, modifier: Modifier = Modifier) {
    Text("$text ($count)", style = MaterialTheme.typography.titleMedium, modifier = modifier.heading())
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
