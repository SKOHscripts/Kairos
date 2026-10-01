package com.skohscripts.kairos.ui.team

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.MemberLane
import com.skohscripts.kairos.core.team.TeamBoard
import com.skohscripts.kairos.core.team.TeamBoardFilter
import com.skohscripts.kairos.core.team.TeamCard
import com.skohscripts.kairos.core.team.TeamSignal
import com.skohscripts.kairos.core.team.TeamState
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.disclosure
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.day.Badge
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.day.Facet
import com.skohscripts.kairos.ui.day.Muted
import com.skohscripts.kairos.ui.day.PriorityBadge
import com.skohscripts.kairos.ui.day.WarnBadge
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.backlog_filter_category
import com.skohscripts.kairos.ui.generated.resources.backlog_no_category
import com.skohscripts.kairos.ui.generated.resources.board_blocked
import com.skohscripts.kairos.ui.generated.resources.board_card_actions
import com.skohscripts.kairos.ui.generated.resources.board_card_progress
import com.skohscripts.kairos.ui.generated.resources.board_card_reassign
import com.skohscripts.kairos.ui.generated.resources.board_card_start
import com.skohscripts.kairos.ui.generated.resources.team_board_empty_body
import com.skohscripts.kairos.ui.generated.resources.board_filter_member
import com.skohscripts.kairos.ui.generated.resources.board_filter_watched
import com.skohscripts.kairos.ui.generated.resources.board_kpi_done_week
import com.skohscripts.kairos.ui.generated.resources.board_kpi_in_progress
import com.skohscripts.kairos.ui.generated.resources.board_kpi_overdue
import com.skohscripts.kairos.ui.generated.resources.board_kpi_watched
import com.skohscripts.kairos.ui.generated.resources.board_lane_count
import com.skohscripts.kairos.ui.generated.resources.board_lane_empty
import com.skohscripts.kairos.ui.generated.resources.board_lane_wip
import com.skohscripts.kairos.ui.generated.resources.board_section
import com.skohscripts.kairos.ui.generated.resources.board_state_done
import com.skohscripts.kairos.ui.generated.resources.board_state_in_progress
import com.skohscripts.kairos.ui.generated.resources.board_state_todo
import com.skohscripts.kairos.ui.generated.resources.deadline_badge
import com.skohscripts.kairos.ui.generated.resources.filter_all
import com.skohscripts.kairos.ui.generated.resources.member_summary
import com.skohscripts.kairos.ui.generated.resources.progress_percent
import com.skohscripts.kairos.ui.generated.resources.signal_churn
import com.skohscripts.kairos.ui.generated.resources.signal_no_progress
import com.skohscripts.kairos.ui.generated.resources.signal_overdue
import com.skohscripts.kairos.ui.generated.resources.signal_stale
import com.skohscripts.kairos.ui.generated.resources.team_board_empty_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.stats.StatTile
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Au-delà, les trois états d'une ligne de membre sont des colonnes côte à côte ; en dessous, des sections empilées. */
private val COLUMNS_FROM = 840.dp

/**
 * Suivi de l'équipe (docs/spec/equipe-backlog-suivi.md § Suivi) : quatre
 * chiffres clés, des filtres (catégorie, membre, « seulement à surveiller »), puis
 * **une ligne repliable par membre actif** (« moi » d'abord) dont les cartes sont
 * groupées par état : En cours, À faire, Faites (7 jours). Lit la base
 * **complète**. Toucher une carte ouvre la fiche ([TeamTaskDialog]) ; le menu ⋮ d'une
 * carte commence la tâche, pose son avancement ou la réaffecte (glisser-déposer : non
 * fait, le menu est le seul chemin, décision de `equipe-backlog-suivi.md`).
 *
 * Signaux « à surveiller » : contour + icône avec leur texte (jamais une couleur
 * seule, jamais d'ambre).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamBoardScreen(services: AppServices) {
    val repository = services.repository
    val snapshot by repository.snapshot.collectAsState()
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = services.clock.now().toLocalDateTime(timeZone).date
    val language = Locale.current.language
    val scope = rememberCoroutineScope()

    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var memberId by rememberSaveable { mutableStateOf<Long?>(null) }
    var onlyWatched by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    val collapsed = remember { mutableStateMapOf<Long, Boolean>() }

    val filter = TeamBoardFilter(category = category, memberId = memberId, onlyWatched = onlyWatched)
    val board = remember(snapshot, today, filter) { TeamBoard.build(snapshot, today, timeZone, filter) }
    // La charge de chaque membre (menu « Assigner à… ») se calcule hors composition ; le menu s'en passe en attendant.
    val load = rememberTeamLoad(services, snapshot).value
    val context = remember(snapshot, today, load) { AssignContext.of(snapshot, today, load) }
    val reassigner = rememberReassigner(repository)
    val wipLimit = (snapshot.settings.team ?: TeamSettings()).wipLimit
    val categories = remember(snapshot.tasks, snapshot.settings) {
        (snapshot.settings.taskTypeList + snapshot.tasks.filter { it.space == com.skohscripts.kairos.core.model.TaskSpace.TEAM }.map { it.taskType }.filter { it.isNotEmpty() }).distinct()
    }

    if (context.active.isEmpty()) {
        EmptyBoard()
    } else {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = maxWidth >= COLUMNS_FROM
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.widthIn(max = if (columns) 1200.dp else 720.dp).fillMaxSize(),
                ) {
                    item(key = "figures") {
                        val figures = board.keyFigures
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val tile = Modifier.weight(1f).widthIn(min = 150.dp)
                            StatTile(figures.inProgress.toString(), stringResource(Res.string.board_kpi_in_progress), tile)
                            StatTile(figures.doneThisWeek.toString(), stringResource(Res.string.board_kpi_done_week), tile)
                            StatTile(figures.overdue.toString(), stringResource(Res.string.board_kpi_overdue), tile, warn = figures.overdue > 0)
                            StatTile(figures.watched.toString(), stringResource(Res.string.board_kpi_watched), tile, warn = figures.watched > 0)
                        }
                    }
                    item(key = "filters") {
                        val all = stringResource(Res.string.filter_all)
                        val none = stringResource(Res.string.backlog_no_category)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Facet(stringResource(Res.string.backlog_filter_category), category, categories + "", { it.ifEmpty { none } }, all, width = 150.dp) { category = it }
                            Facet(
                                stringResource(Res.string.board_filter_member), memberId, context.active.map { it.id },
                                { id -> context.nameOrNull(id).orEmpty() }, all, width = 150.dp,
                            ) { memberId = it }
                            FilterChip(
                                selected = onlyWatched,
                                onClick = { onlyWatched = !onlyWatched },
                                label = { Text(stringResource(Res.string.board_filter_watched)) },
                                leadingIcon = { Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    items(board.lanes, key = { "lane-${it.member.id}" }) { lane ->
                        LaneCard(
                            lane = lane,
                            expanded = collapsed[lane.member.id] != true,
                            onToggle = { collapsed[lane.member.id] = collapsed[lane.member.id] != true },
                            columns = columns,
                            wipLimit = wipLimit,
                            context = context,
                            language = language,
                            today = today,
                            repository = repository,
                            reassigner = reassigner,
                            onOpen = { openId = it },
                            onLaunch = { block -> scope.launch { block() } },
                        )
                    }
                }
            }
        }
    }

    openId?.let { id -> TeamTaskDialog(services, id, onDismiss = { openId = null }) }
    ReassignDialog(reassigner, context)
}

/** Sans membre actif : à quoi sert l'écran et comment le remplir. Pas de bloc teinté. */
@Composable
private fun EmptyBoard() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        ) {
            Icon(KairosIcons.ViewKanban, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text(
                stringResource(Res.string.team_board_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.heading(),
            )
            Text(stringResource(Res.string.team_board_empty_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Ligne d'un membre : en-tête repliable (nom, « moi », quotité, en-cours, signal
 * « trop d'en-cours » en contour + icône), puis ses cartes par état.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LaneCard(
    lane: MemberLane,
    expanded: Boolean,
    onToggle: () -> Unit,
    columns: Boolean,
    wipLimit: Int,
    context: AssignContext,
    language: String,
    today: LocalDate,
    repository: KairosRepository,
    reassigner: Reassigner,
    onOpen: (Long) -> Unit,
    onLaunch: (suspend () -> Unit) -> Unit,
) {
    val member = lane.member
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().disclosure(expanded, heading = true, onToggle = onToggle).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Text(member.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (member.isSelf) SelfBadge()
                Muted(stringResource(Res.string.member_summary, member.availabilityPercent, com.skohscripts.kairos.core.team.MemberForm.formatHours(member.hoursPerDay)))
                Muted(stringResource(Res.string.board_lane_count, lane.inProgressCount))
                if (lane.wipExceeded) WarnBadge(stringResource(Res.string.board_lane_wip, lane.inProgressCount, wipLimit))
            }
            Icon(if (expanded) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
        }
        if (expanded) {
            val sections = listOf(
                Triple(Res.string.board_state_in_progress, lane.inProgress, TeamState.IN_PROGRESS),
                Triple(Res.string.board_state_todo, lane.todo, TeamState.TODO),
                Triple(Res.string.board_state_done, lane.done, TeamState.DONE),
            )
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                val card: @Composable (TeamCard) -> Unit = { c ->
                    TaskCard(c, member.id, context, language, today, repository, reassigner, onOpen, onLaunch)
                }
                if (columns) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        sections.forEach { (title, cards, _) ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                StateTitle(stringResource(title), cards.size)
                                if (cards.isEmpty()) Muted(stringResource(Res.string.board_lane_empty))
                                cards.forEach { card(it) }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val filled = sections.filter { it.second.isNotEmpty() }
                        if (filled.isEmpty()) Muted(stringResource(Res.string.board_lane_empty))
                        filled.forEach { (title, cards, _) ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                StateTitle(stringResource(title), cards.size)
                                cards.forEach { card(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateTitle(title: String, count: Int) {
    Text(stringResource(Res.string.board_section, title, count), style = MaterialTheme.typography.titleSmall, modifier = Modifier.heading())
}

/**
 * Carte d'une tâche : titre, catégorie, priorité (liseré rouge à gauche pour P0,
 * jamais de remplissage), échéance, « Bloquée », signaux, et pour une tâche
 * commencée la barre fine d'avancement (primaire) avec son « 60 % ». Carte pleine
 * sans ombre ; toucher l'ouvre, ⋮ propose commencer, avancement, réaffecter.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(
    card: TeamCard,
    memberId: Long,
    context: AssignContext,
    language: String,
    today: LocalDate,
    repository: KairosRepository,
    reassigner: Reassigner,
    onOpen: (Long) -> Unit,
    onLaunch: (suspend () -> Unit) -> Unit,
) {
    val task = card.task
    val scheme = MaterialTheme.colorScheme
    val errorColor = scheme.error
    val open = card.state != TeamState.DONE
    var menu by remember { mutableStateOf(false) }
    var reassign by remember { mutableStateOf(false) }
    val percent = task.progressPercent ?: 0
    Card(
        onClick = { onOpen(task.id) },
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .drawBehind { if (task.priority == 0 && open) drawRect(errorColor, size = Size(3.dp.toPx(), size.height)) }
                .padding(start = 12.dp, top = 4.dp, bottom = 8.dp, end = if (open) 0.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                )
                if (open) {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(KairosIcons.MoreVert, contentDescription = stringResource(Res.string.board_card_actions, task.title))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (card.state == TeamState.TODO) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.board_card_start)) },
                                    leadingIcon = { Icon(KairosIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                    onClick = { menu = false; onLaunch { repository.startTeamTask(task.id) } },
                                )
                            }
                            ProgressMenuItem(percent) { picked -> onLaunch { repository.setProgress(task.id, picked) } }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.board_card_reassign)) },
                                leadingIcon = { Icon(KairosIcons.SwapHoriz, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                onClick = { menu = false; reassign = true },
                            )
                        }
                        AssignMenu(reassign, { reassign = false }, context, currentId = memberId, showBacklog = true) { reassigner.request(task, it) }
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 12.dp),
            ) {
                if (task.taskType.isNotEmpty()) Badge(task.taskType)
                task.priority?.let { PriorityBadge(it) }
                task.deadline?.let { Muted(stringResource(Res.string.deadline_badge, Dates.short(it, language))) }
                if (card.blocked) WarnBadge(stringResource(Res.string.board_blocked), KairosIcons.Block)
                card.signals.forEach { SignalBadge(it) }
            }
            if (card.state == TeamState.IN_PROGRESS || (card.state == TeamState.TODO && percent > 0)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(end = 12.dp, top = 2.dp),
                ) {
                    val value = stringResource(Res.string.progress_percent, percent)
                    val description = stringResource(Res.string.board_card_progress, value)
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        color = scheme.primary,
                        trackColor = scheme.surfaceContainerHighest,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                        modifier = Modifier.weight(1f).height(4.dp).semantics { contentDescription = description },
                    )
                    Text(value, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Avancement dans le menu d'une carte : curseur discret de 0 à 100 % par pas de 10, écrit au relâchement. */
@Composable
private fun ProgressMenuItem(percent: Int, onCommit: (Int) -> Unit) {
    var value by remember(percent) { mutableStateOf(percent.toFloat()) }
    val title = stringResource(Res.string.board_card_progress, stringResource(Res.string.progress_percent, value.roundToInt()))
    Column(Modifier.widthIn(min = 240.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onCommit(value.roundToInt()) },
            valueRange = 0f..100f,
            steps = 9,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

/** Signal « à surveiller » d'une tâche : contour + icône + texte, jamais une couleur seule (charte). */
@Composable
internal fun SignalBadge(signal: TeamSignal) {
    when (signal) {
        TeamSignal.OVERDUE -> WarnBadge(stringResource(Res.string.signal_overdue), KairosIcons.EventBusy)
        TeamSignal.STALE -> WarnBadge(stringResource(Res.string.signal_stale), KairosIcons.Schedule)
        TeamSignal.NO_PROGRESS -> WarnBadge(stringResource(Res.string.signal_no_progress), KairosIcons.HourglassEmpty)
        TeamSignal.CHURN -> WarnBadge(stringResource(Res.string.signal_churn), KairosIcons.SwapHoriz)
    }
}
