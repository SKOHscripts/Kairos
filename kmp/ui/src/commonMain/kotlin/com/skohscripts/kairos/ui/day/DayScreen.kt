package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.day.DayView
import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.ui.LocalPlatform
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.StorageBanner
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.agenda_empty
import com.skohscripts.kairos.ui.generated.resources.agenda_hint
import com.skohscripts.kairos.ui.generated.resources.agenda_title
import com.skohscripts.kairos.ui.generated.resources.backlog_hint
import com.skohscripts.kairos.ui.generated.resources.backlog_title
import com.skohscripts.kairos.ui.generated.resources.blocked_by
import com.skohscripts.kairos.ui.generated.resources.blocked_hint
import com.skohscripts.kairos.ui.generated.resources.blocked_title
import com.skohscripts.kairos.ui.generated.resources.done_hint
import com.skohscripts.kairos.ui.generated.resources.done_title
import com.skohscripts.kairos.ui.generated.resources.inbox_empty
import com.skohscripts.kairos.ui.generated.resources.inbox_help
import com.skohscripts.kairos.ui.generated.resources.inbox_title
import com.skohscripts.kairos.ui.generated.resources.later_hint
import com.skohscripts.kairos.ui.generated.resources.later_title
import com.skohscripts.kairos.ui.generated.resources.note_conflict
import com.skohscripts.kairos.ui.generated.resources.note_dip
import com.skohscripts.kairos.ui.generated.resources.note_pushed
import com.skohscripts.kairos.ui.generated.resources.note_pushed_pinned
import com.skohscripts.kairos.ui.generated.resources.overload_banner
import com.skohscripts.kairos.ui.generated.resources.parents_hint
import com.skohscripts.kairos.ui.generated.resources.parents_progress
import com.skohscripts.kairos.ui.generated.resources.parents_title
import com.skohscripts.kairos.ui.generated.resources.tag_critical
import com.skohscripts.kairos.ui.generated.resources.tag_deepwork
import com.skohscripts.kairos.ui.generated.resources.tag_pinned
import com.skohscripts.kairos.ui.generated.resources.unscheduled_hint
import com.skohscripts.kairos.ui.generated.resources.unscheduled_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Largeur à partir de laquelle la frise passe à droite de la liste (860 px en Kairos 2). */
private val TWO_COLUMNS = 900.dp

/** En dessous, priorité et points passent sous le corps de la ligne (720 px en Kairos 2, 600 dp ici). */
private val COMPACT_ROWS = 600.dp

/**
 * Vue Jour (docs/spec-v3/vue-jour.md), dans l'ordre du flux GTD : capture,
 * « À traiter », « Maintenant », bandeau de surcharge, « Aujourd'hui, dans
 * l'ordre », sections secondaires (sans créneau, bloquées, plus tard, mères,
 * fait), recherche et backlog ; la frise « Agenda » à droite en largeur
 * bureau, en bas sinon. Tout dérive de [DayView.build], recalculé à chaque
 * changement de la base, de filtre, et chaque minute.
 */
@Composable
fun DayScreen(services: AppServices) {
    val repository = services.repository
    val snapshot by repository.snapshot.collectAsState()
    val timeZone = remember { TimeZone.currentSystemDefault() }
    // L'heure avance à la minute : le placement part de maintenant (buildDaySchedule).
    val now by produceState(services.clock.now()) {
        while (true) {
            delay(60_000 - value.toEpochMilliseconds().mod(60_000L) + 50)
            value = services.clock.now()
        }
    }
    val local = now.toLocalDateTime(timeZone)
    val today = local.date
    val minute = LocalDateTime(today, LocalTime(local.hour, local.minute))
    var filter by remember { mutableStateOf(DayFilter()) }
    val view = remember(snapshot, minute, filter) { DayView.build(snapshot, today, minute, timeZone, filter) }
    val scope = rememberCoroutineScope()
    val language = Locale.current.language
    val showShortcuts = LocalPlatform.current != Platform.ANDROID

    // Séries « le N du mois » : l'occurrence du mois courant (n'écrit que s'il y a lieu).
    LaunchedEffect(today, snapshot.tasks) { repository.ensureCalendarOccurrences(today) }

    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingBlock by remember { mutableStateOf<TimeBlock?>(null) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    val capture = remember { CaptureState() }
    val searchFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var captureRequest by remember { mutableIntStateOf(0) }
    var searchRequest by remember { mutableIntStateOf(0) }
    val positions = remember { mutableMapOf<String, Int>() }
    // Sections repliées par défaut, sauf « Sans créneau aujourd'hui » (audit UI de Kairos 2).
    val sectionOpen = remember { mutableStateMapOf("unscheduled" to true) }

    LaunchedEffect(captureRequest) {
        if (captureRequest == 0) return@LaunchedEffect
        // D'abord laisser composer le changement d'état (volet, section ouverte), puis défiler :
        // défiler dans la même image qu'une recomposition de l'élément fait échouer la liste.
        awaitComposed()
        listState.scrollToItem(positions["capture"] ?: 0)
        awaitComposed()
        runCatching { capture.taskFocus.requestFocus() }
    }
    LaunchedEffect(searchRequest) {
        if (searchRequest == 0) return@LaunchedEffect
        // D'abord laisser composer le changement d'état (volet, section ouverte), puis défiler :
        // défiler dans la même image qu'une recomposition de l'élément fait échouer la liste.
        awaitComposed()
        listState.scrollToItem(positions["filters"] ?: 0)
        awaitComposed()
        runCatching { searchFocus.requestFocus() }
    }
    // Les raccourcis écoutent l'écran : il reprend le focus à l'ouverture et à la fermeture d'un dialogue.
    LaunchedEffect(editingId == null && editingBlock == null) {
        // Après la première image : avant, l'écran n'est pas encore attaché et la demande se perd.
        withFrameNanos { }
        if (editingId == null && editingBlock == null) runCatching { rootFocus.requestFocus() }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().testTag("day-screen").focusRequester(rootFocus).focusable().onKeyEvent { e ->
            // `N` : capture ; `/` : recherche. Un champ de saisie consomme la frappe avant
            // l'écran : les raccourcis sont donc inactifs pendant la saisie (Kairos 2).
            if (e.type != KeyEventType.KeyDown || e.isCtrlPressed || e.isMetaPressed || e.isAltPressed) return@onKeyEvent false
            when (e.key) {
                Key.N -> { capture.focusTask(); captureRequest++; true }
                Key.Slash -> { filterOpen = true; searchRequest++; true }
                else -> false
            }
        },
        contentAlignment = Alignment.TopCenter,
    ) {
        val twoColumns = maxWidth >= TWO_COLUMNS
        val mainWidth = if (twoColumns) minOf(maxWidth - 340.dp, 840.dp) else minOf(maxWidth, 840.dp)
        val ctx = RowContext(
            view = view,
            language = language,
            compact = mainWidth < COMPACT_ROWS,
            onToggleDone = { t -> scope.launch { repository.toggleDone(t.id) } },
            onSnooze = { t -> scope.launch { repository.snooze(t.id) } },
            onEdit = { t -> editingId = t.id },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxHeight()) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.width(mainWidth).fillMaxHeight(),
            ) {
                val list = ListBuilder(this, positions)
                fun one(key: String, content: @Composable () -> Unit) = list.one(key, content)
                fun rows(key: String, tasks: List<Task>, row: @Composable (Task) -> Unit) = list.rows(key, tasks, row)
                fun section(key: String, title: StringResource, hint: StringResource, count: Int, showEmpty: Boolean = false, content: () -> Unit) =
                    list.section(key, title, hint, count, sectionOpen, showEmpty, content)

                one("storage") { StorageBanner(services, showWhenLinked = false) }
                one("capture") {
                    Capture(
                        capture, today, view.editableBlocks, language, showShortcuts,
                        onAddTask = { title -> scope.launch { repository.createTask(title) } },
                        onAddBlock = { edit -> scope.launch { repository.createBlock(edit) } },
                        onEditBlock = { editingBlock = it },
                    )
                }

                // --- À traiter
                one("inbox-title") { SectionTitle(stringResource(Res.string.inbox_title), view.inbox.size, Modifier.padding(top = 8.dp)) }
                one("inbox-help") { Hint(stringResource(if (view.inbox.isEmpty()) Res.string.inbox_empty else Res.string.inbox_help)) }
                rows("inbox", view.inbox) { task ->
                    TaskRow(task, ctx) {
                        InboxQualify(
                            task,
                            onPriority = { scope.launch { repository.setPriority(task.id, it) } },
                            onPoints = { scope.launch { repository.setPoints(task.id, it) } },
                        )
                    }
                }

                // --- Maintenant, puis bandeau de surcharge (un avertissement, jamais en tête)
                one("now") {
                    NowCard(
                        view, language,
                        onDone = { t -> scope.launch { repository.toggleDone(t.id) } },
                        onSnooze = { t -> scope.launch { repository.snooze(t.id) } },
                    )
                }
                if (view.priorityOverload) one("overload") { OverloadBanner(view.priorityOverloadCount) }

                // Un filtre actif remonte au-dessus des listes qu'il réduit.
                val filters: @Composable () -> Unit = {
                    FilterCard(
                        filter, filterOpen || filter.active, { filterOpen = !(filterOpen || filter.active) }, { filter = it },
                        view.projects, snapshot.settings.taskTypeList, searchFocus, showShortcuts,
                    )
                }
                if (filter.active) one("filters", filters)

                // --- Aujourd'hui, dans l'ordre
                one("agenda-title") {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(stringResource(Res.string.agenda_title), style = MaterialTheme.typography.titleMedium)
                        Hint(stringResource(if (view.agenda.isEmpty()) Res.string.agenda_empty else Res.string.agenda_hint))
                    }
                }
                list.count(view.agenda.size)
                items(view.agenda, key = { "agenda-${it.task.id}" }) { item -> AgendaRow(item, ctx, snapshot.settings.cognitiveDipTroughHour) }

                // --- Sections secondaires : repliées, sauf « Sans créneau » (des tâches du jour).
                section("unscheduled", Res.string.unscheduled_title, Res.string.unscheduled_hint, view.unscheduled.size) {
                    rows("unscheduled", view.unscheduled) { TaskRow(it, ctx) }
                }
                section("blocked", Res.string.blocked_title, Res.string.blocked_hint, view.blocked.size) {
                    rows("blocked", view.blocked.map { it.task }) { task ->
                        val reasons = view.blocked.first { it.task.id == task.id }.blockers
                        TaskRow(task, ctx, blocked = true, before = {
                            WarnBadge(stringResource(Res.string.blocked_by, reasons.joinToString(", ")), KairosIcons.Block)
                        })
                    }
                }
                section("later", Res.string.later_title, Res.string.later_hint, view.later.size) {
                    rows("later", view.later) { TaskRow(it, ctx) }
                }
                section("parents", Res.string.parents_title, Res.string.parents_hint, view.parents.size) {
                    rows("parents", view.parents.map { it.task }) { task ->
                        val progress = view.parents.first { it.task.id == task.id }
                        TaskRow(task, ctx, before = { Badge(stringResource(Res.string.parents_progress, progress.done, progress.total)) })
                    }
                }
                section("done", Res.string.done_title, Res.string.done_hint, view.doneToday.size) {
                    rows("done", view.doneToday) { TaskRow(it, ctx, editable = false, showDescription = false) }
                }

                if (!filter.active) one("filters", filters)
                section("backlog", Res.string.backlog_title, Res.string.backlog_hint, view.backlog.size, showEmpty = true) {
                    rows("backlog", view.backlog) { TaskRow(it, ctx) }
                }
                if (!twoColumns) one("timeline") { TimelineCard(view, snapshot.settings, language) }
            }
            if (twoColumns) {
                Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                    TimelineCard(view, snapshot.settings, language)
                }
            }
        }
    }

    editingId?.let { id ->
        val task = snapshot.tasks.firstOrNull { it.id == id }
        if (task == null) {
            editingId = null
        } else {
            EditTaskDialog(
                task = task,
                day = today,
                taskTypes = snapshot.settings.taskTypeList,
                candidates = view.openTasks,
                blockerIds = view.blockersOf[id].orEmpty().toSet(),
                onDismiss = { editingId = null },
                onSave = { edit -> scope.launch { repository.updateTask(id, edit) }; editingId = null },
                onDelete = { scope.launch { repository.deleteTask(id) }; editingId = null },
            )
        }
    }
    editingBlock?.let { block ->
        BlockDialog(
            block,
            onDismiss = { editingBlock = null },
            onSave = { edit -> scope.launch { repository.updateBlock(block.id, edit) }; editingBlock = null },
            onDelete = { scope.launch { repository.deleteBlock(block.id) }; editingBlock = null },
        )
    }
}

/** Attend que la liste défilée et la section ouverte soient composées (deux images). */
private suspend fun awaitComposed() {
    withFrameNanos { }
    withFrameNanos { }
}

/**
 * Construit la liste en retenant la position de chaque entrée nommée (les
 * raccourcis y font défiler la liste avant d'y mettre le curseur).
 */
private class ListBuilder(private val scope: LazyListScope, private val positions: MutableMap<String, Int>) {
    private var index = 0

    fun count(n: Int) {
        index += n
    }

    fun one(key: String, content: @Composable () -> Unit) {
        positions[key] = index++
        scope.item(key = key) { content() }
    }

    fun rows(key: String, tasks: List<Task>, row: @Composable (Task) -> Unit) {
        index += tasks.size
        scope.items(tasks, key = { "$key-${it.id}" }) { row(it) }
    }

    /** Section secondaire repliable : titre, compte, rôle ; absente si vide (sauf [showEmpty]). */
    fun section(
        key: String,
        title: StringResource,
        hint: StringResource,
        count: Int,
        open: SnapshotStateMap<String, Boolean>,
        showEmpty: Boolean,
        content: () -> Unit,
    ) {
        if (count == 0 && !showEmpty) return
        val isOpen = open[key] ?: false
        one("section-$key") {
            Column(Modifier.padding(top = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { open[key] = !isOpen }.padding(vertical = 6.dp),
                ) {
                    Icon(if (isOpen) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
                    Text("${stringResource(title)} ($count)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
                }
                if (isOpen) Hint(stringResource(hint))
            }
        }
        if (isOpen) content()
    }
}

/** Ligne de l'agenda : heure, étiquettes de placement (épinglée, deep work, chemin critique), notes. */
@Composable
private fun AgendaRow(item: Scheduling.ScheduledTask, ctx: RowContext, dipTroughHour: Int) {
    val scheme = MaterialTheme.colorScheme
    val time = Dates.time(item.start, ctx.language)
    TaskRow(
        item.task, ctx, time = item.start,
        before = {
            if (item.pinned) Badge(stringResource(Res.string.tag_pinned), scheme.secondaryContainer, scheme.onSecondaryContainer, icon = KairosIcons.PushPin)
            if (item.deepwork) Badge(stringResource(Res.string.tag_deepwork), scheme.tertiaryContainer, scheme.onTertiaryContainer, icon = KairosIcons.Layers)
            if (item.task.id in ctx.view.raised) {
                Badge(stringResource(Res.string.tag_critical), scheme.secondaryContainer, scheme.onSecondaryContainer, icon = KairosIcons.TrendingUp)
            }
        },
        after = {
            item.pushedAfter?.let { after ->
                val res = if (item.pushedAfterKind == Scheduling.ObstacleKind.PINNED) Res.string.note_pushed_pinned else Res.string.note_pushed
                WarnBadge(stringResource(res, time, after))
            }
            if (item.dipApplied) WarnBadge(stringResource(Res.string.note_dip, dipTroughHour), KairosIcons.Schedule)
            item.conflictWith?.let { ErrorBadge(stringResource(Res.string.note_conflict, it)) }
        },
    )
}

@Composable
private fun OverloadBanner(count: Int) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(Res.string.overload_banner, count), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Qualification en ligne d'une tâche de la boîte de réception : ce qui manque, puis les pastilles. */
@Composable
private fun InboxQualify(task: Task, onPriority: (Int?) -> Unit, onPoints: (Int?) -> Unit) {
    Column(Modifier.padding(start = 48.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        task.missingQualification?.let { missing ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(Levels.missing(missing)), style = MaterialTheme.typography.labelMedium)
            }
        }
        PriorityPills(task.priority, onPriority)
        PointsPills(task.fibonacciPoints, onPoints)
    }
}

@Composable
private fun SectionTitle(text: String, count: Int, modifier: Modifier = Modifier) {
    Text("$text ($count)", style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
