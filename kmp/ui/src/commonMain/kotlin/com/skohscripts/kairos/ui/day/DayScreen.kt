package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.core.engine.Recurrence
import com.skohscripts.kairos.core.model.BlockSource
import com.skohscripts.kairos.ui.app.withDeviceCalendars
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.ui.guide.RecurrenceCoach
import com.skohscripts.kairos.ui.generated.resources.note_affinity_combined
import com.skohscripts.kairos.ui.generated.resources.note_affinity_efficient
import com.skohscripts.kairos.ui.app.uiLanguage
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosListBlockExtra
import com.skohscripts.kairos.ui.theme.KairosListSectionExtra
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.app.disclosure
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.app.LocalUndoMessages
import com.skohscripts.kairos.ui.generated.resources.blocked_feedback
import com.skohscripts.kairos.ui.generated.resources.action_revert
import com.skohscripts.kairos.ui.generated.resources.block_skipped_message
import com.skohscripts.kairos.ui.generated.resources.task_moved
import org.jetbrains.compose.resources.getString
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.ui.guide.CoachPanel
import com.skohscripts.kairos.ui.guide.GuideMark
import com.skohscripts.kairos.ui.guide.GuideScreen
import com.skohscripts.kairos.ui.guide.GuideSection
import com.skohscripts.kairos.ui.guide.LocalGuideSpot
import com.skohscripts.kairos.ui.guide.CoachSession
import com.skohscripts.kairos.ui.guide.FirstTaskCoach
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.day.DayView
import com.skohscripts.kairos.core.engine.AffinitySource
import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.day.EditableBlock
import com.skohscripts.kairos.ui.LocalPlatform
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.StorageBanner
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.skipped_ignored
import com.skohscripts.kairos.ui.generated.resources.skipped_recreated_many
import com.skohscripts.kairos.ui.generated.resources.skipped_recreated_one
import com.skohscripts.kairos.ui.generated.resources.agenda_empty
import com.skohscripts.kairos.ui.generated.resources.agenda_hint
import com.skohscripts.kairos.ui.generated.resources.agenda_title
import com.skohscripts.kairos.ui.generated.resources.backlog_hint
import com.skohscripts.kairos.ui.generated.resources.backlog_title
import com.skohscripts.kairos.ui.generated.resources.blocked_by
import com.skohscripts.kairos.ui.generated.resources.blocked_hint
import com.skohscripts.kairos.ui.generated.resources.blocked_title
import com.skohscripts.kairos.ui.generated.resources.blocker_team
import com.skohscripts.kairos.ui.generated.resources.blocker_unassigned
import com.skohscripts.kairos.ui.generated.resources.day_back_today
import com.skohscripts.kairos.ui.generated.resources.day_no_window
import com.skohscripts.kairos.ui.generated.resources.done_hint
import com.skohscripts.kairos.ui.generated.resources.done_title
import com.skohscripts.kairos.ui.generated.resources.inbox_empty
import com.skohscripts.kairos.ui.generated.resources.inbox_empty_filtered
import com.skohscripts.kairos.ui.generated.resources.inbox_help
import com.skohscripts.kairos.ui.generated.resources.inbox_title
import com.skohscripts.kairos.ui.generated.resources.later_hint
import com.skohscripts.kairos.ui.generated.resources.later_title
import com.skohscripts.kairos.ui.generated.resources.note_conflict
import com.skohscripts.kairos.ui.generated.resources.note_affinity_detected
import com.skohscripts.kairos.ui.generated.resources.note_affinity_manual
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
import com.skohscripts.kairos.ui.team.AssignContext
import com.skohscripts.kairos.ui.team.TeamTaskSheet
import com.skohscripts.kairos.core.team.TeamWork
import com.skohscripts.kairos.core.team.exchange.ReceivedTasks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Largeur à partir de laquelle la frise passe à droite de la liste. */
private val TWO_COLUMNS = 900.dp

/** En dessous, priorité et points passent sous le corps de la ligne (600 dp). */
private val COMPACT_ROWS = 600.dp

/**
 * Vue Jour (docs/spec/vue-jour.md), dans l'ordre du flux GTD : capture,
 * « À traiter », « Maintenant », bandeau de surcharge, « Aujourd'hui, dans
 * l'ordre », sections secondaires (sans créneau, bloquées, plus tard, mères,
 * fait), recherche et backlog ; « En ce moment » (chrono) et la frise
 * « Agenda » à droite en largeur bureau, en bas sinon. Tout dérive de [DayView.build], recalculé à chaque
 * changement de la base, de filtre, et chaque minute.
 */
@Composable
fun DayScreen(
    services: AppServices,
    selectedDay: LocalDate? = null,
    onBackToToday: () -> Unit = {},
    /** L'accompagnement de la première tâche est demandé (accueil, hub « Découvrir Kairos ») ; [onFirstTaskHandled] l'acquitte. */
    firstTaskRequest: Boolean = false,
    /** La demande est celle de la première tâche récurrente (issue #98) : les étapes de `RecurrenceCoach`. */
    firstTaskRecurring: Boolean = false,
    onFirstTaskHandled: () -> Unit = {},
    /** « Découvrir Kairos » depuis les félicitations : le hub appartient à la coquille. */
    onDiscover: () -> Unit = {},
) {
    val repository = services.repository
    val stored by repository.personalSnapshot.collectAsState()
    // Calendriers du téléphone (Android) : ajoutés pour le calcul, jamais écrits (docs/spec/connexions-externes.md).
    val snapshot = withDeviceCalendars(services, stored)
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
    // Jour affiché : aujourd'hui, ou celui ouvert depuis la vue Semaine.
    val day = selectedDay ?: today
    val estimates = remember(snapshot) { Estimates.of(snapshot.tasks, snapshot.workSessions, now) }
    val view = remember(snapshot, minute, filter, day) { DayView.build(snapshot, day, minute, timeZone, filter, instant = now) }
    val notifyState by services.notifier.state.collectAsState()
    val scope = rememberCoroutineScope()
    val undo = LocalUndoMessages.current
    val showMessage = LocalMessages.current
    val language = uiLanguage()
    val showShortcuts = LocalPlatform.current != Platform.ANDROID

    // Séries « le N du mois » : l'occurrence du mois courant (n'écrit que s'il y a lieu).
    LaunchedEffect(today, snapshot.tasks) { repository.ensureCalendarOccurrences(today) }
    // Mois sautés de ces séries (issue #111) : un avis par série, calculé sans rien écrire, sur les seules tâches Perso.
    val skipIgnored = stored.settings.recurrence?.skipIgnored.orEmpty()
    val skipped = remember(stored.tasks, today, skipIgnored) { Recurrence.skippedSeries(stored.tasks, today, skipIgnored) }

    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingBlock by remember { mutableStateOf<EditableBlock?>(null) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    // Accompagnement de la première tâche (docs/spec/premiere-tache.md) : la fiche de création (l'étape du coach, -1 une fois
    // le coach passé), puis la tâche réellement créée, ses félicitations et l'explication de sa place (-1 : aucune).
    var creating by rememberSaveable { mutableStateOf(false) }
    var coachStep by rememberSaveable { mutableIntStateOf(-1) }
    // Accompagnement de la première tâche récurrente (issue #98) : la même fiche, d'autres étapes.
    var coachRecurring by rememberSaveable { mutableStateOf(false) }
    var placedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var congrats by rememberSaveable { mutableStateOf(false) }
    var placedStep by rememberSaveable { mutableIntStateOf(-1) }
    var inviteDismissed by rememberSaveable { mutableStateOf(false) }
    val draft = remember { Task(id = 0, title = "", createdAt = now, updatedAt = now) }
    LaunchedEffect(firstTaskRequest) {
        if (firstTaskRequest) {
            creating = true
            coachStep = 0
            coachRecurring = firstTaskRecurring
            onFirstTaskHandled()
        }
    }
    val rowPositions = remember { mutableMapOf<Long, Int>() }
    val capture = remember { CaptureState() }
    val searchFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var captureRequest by remember { mutableIntStateOf(0) }
    var searchRequest by remember { mutableIntStateOf(0) }
    val positions = remember { mutableMapOf<String, Int>() }
    val toggleTimer: (Task) -> Unit = { t ->
        scope.launch { if (view.running?.taskId == t.id) repository.stopTimer() else repository.startTimer(t.id) }
    }
    // Sections repliées par défaut, sauf « Sans créneau aujourd'hui ».
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
    LaunchedEffect(editingId == null && editingBlock == null && !creating) {
        // Après la première image : avant, l'écran n'est pas encore attaché et la demande se perd.
        withFrameNanos { }
        if (editingId == null && editingBlock == null && !creating) runCatching { rootFocus.requestFocus() }
    }
    // Section qu'une carte du guide demande de montrer (docs/spec/visite-guidee.md, issue #95). La liste est paresseuse :
    // une ligne hors de l'écran n'est pas composée, on y défile donc par sa position, comme les raccourcis.
    val spot = LocalGuideSpot.current
    val wantedSection = spot.pending?.takeIf { it.screen == GuideScreen.DAY }
    LaunchedEffect(wantedSection) {
        val section = wantedSection ?: return@LaunchedEffect
        spot.pending = null
        // Hors de l'effet : consommer la demande change sa clé, ce qui l'annulerait avant la fin de la mise en valeur.
        scope.launch {
            // Absente si vide (les sections repliables) : on le dit, l'écran reste en haut. Lu sur la vue, pas sur
            // `positions`, qui garde la place d'une section disparue.
            val present = when (section) {
                GuideSection.DAY_BLOCKED -> view.blocked.isNotEmpty()
                GuideSection.DAY_PARENTS -> view.parents.isNotEmpty()
                else -> true
            }
            if (!present) {
                section.missing?.let { showMessage(getString(it)) }
                return@launch
            }
            DAY_SPOT_SECTIONS[section]?.let { sectionOpen[it] = true }
            awaitComposed()
            positions[DAY_SPOT_ITEMS.getValue(section)]?.let { listState.scrollToItem(it) }
            spot.show(section)
        }
    }
    // La tâche qu'on vient de créer : la liste défile jusqu'à sa ligne, mise en évidence pendant l'explication de sa place.
    LaunchedEffect(placedId, placedStep) {
        val id = placedId ?: return@LaunchedEffect
        if (placedStep < 0) return@LaunchedEffect
        awaitComposed()
        rowPositions[id]?.let { listState.scrollToItem(it) }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().testTag("day-screen").focusRequester(rootFocus)
            .focusable()
            // Un clic dans le vide (texte, fond) rend le focus à l'écran, sinon il n'est plus nulle part et les
            // raccourcis restent sourds ; les éléments cliquables consomment leur clic avant.
            .pointerInput(Unit) { detectTapGestures { runCatching { rootFocus.requestFocus() } } }
            .onKeyEvent { e ->
            // `N` : capture ; `/` : recherche. Un champ de saisie consomme la frappe avant
            // l'écran : les raccourcis sont donc inactifs pendant la saisie.
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
            onMove = { t, date ->
                val before = t.scheduledDate
                scope.launch {
                    repository.schedule(t.id, date)
                    undo(getString(Res.string.task_moved, t.title, Dates.short(date, language, today)), getString(Res.string.action_revert)) {
                        scope.launch { repository.schedule(t.id, before) }
                    }
                }
            },
            today = today,
            onEdit = { t -> editingId = t.id },
            onToggleTimer = toggleTimer,
            clock = services.clock,
            learning = snapshot.settings.learning ?: LearningSettings(),
            highlight = placedId?.takeIf { placedStep >= 0 },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxHeight()) {
            LazyColumn(
                state = listState,
                // Rythme (docs/spec/densite.md) : lignes d'une même liste à `s` ; un bloc ajoute KairosListBlockExtra (total `m`), un titre de section KairosListSectionExtra (total `xl`).
                verticalArrangement = Arrangement.spacedBy(KairosSpacing.s),
                contentPadding = PaddingValues(KairosSpacing.l),
                modifier = Modifier.width(mainWidth).fillMaxHeight(),
            ) {
                val list = ListBuilder(this, positions, rowPositions)
                fun one(key: String, content: @Composable () -> Unit) = list.one(key, content)
                fun block(key: String, content: @Composable () -> Unit) = list.one(key) { Box(Modifier.padding(top = KairosListBlockExtra)) { content() } }
                fun rows(key: String, tasks: List<Task>, row: @Composable (Task) -> Unit) = list.rows(key, tasks, row)
                fun section(key: String, title: StringResource, hint: StringResource, count: Int, showEmpty: Boolean = false, content: () -> Unit) =
                    list.section(key, title, hint, count, sectionOpen, showEmpty, DAY_SPOT_SECTIONS.entries.firstOrNull { it.value == key }?.key, content)

                one("storage") { StorageBanner(services, showWhenLinked = false) }
                if (day != today) {
                    one("back-today") {
                        KairosOutlinedButton(onClick = onBackToToday, contentPadding = KairosButtonIconPadding) {
                            Icon(KairosIcons.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(Res.string.day_back_today), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                // Aucune tâche à soi (les exemples ne comptent pas) : l'invitation à l'accompagnement, qui disparaît d'elle-même
                // dès la première tâche, et ne revient plus une fois l'accompagnement mené à son terme (issue #94).
                if (!inviteDismissed && snapshot.settings.guide?.firstTaskDone != true && snapshot.tasks.none { !ExampleData.isExample(it) }) {
                    block("first-task") {
                        FirstTaskInvite(
                            onStart = { creating = true; coachStep = 0; coachRecurring = false },
                            onDismiss = { inviteDismissed = true },
                        )
                    }
                }
                block("capture") {
                    Capture(
                        capture, day, view.editableBlocks, language, showShortcuts,
                        onAddTask = { title -> scope.launch { repository.createTask(title) } },
                        onAddBlock = { edit -> scope.launch { repository.createBlock(edit) } },
                        onEditBlock = { editingBlock = it },
                    )
                }

                // Avis des mois sautés (docs/spec/recurrence.md § Avis des mois sautés) : le vrai aujourd'hui seulement.
                if (day == today) skipped.forEach { series ->
                    one("skipped-${series.key}") {
                        SkippedSeriesNotice(
                            series,
                            onRecreate = {
                                scope.launch {
                                    val ids = repository.recreateSkippedOccurrences(series.title, series.dayOfMonth, today)
                                    if (ids.isEmpty()) return@launch
                                    val message = if (ids.size == 1) {
                                        getString(Res.string.skipped_recreated_one, series.title)
                                    } else {
                                        getString(Res.string.skipped_recreated_many, ids.size, series.title)
                                    }
                                    undo(message, getString(Res.string.action_revert)) { scope.launch { ids.forEach { repository.deleteTask(it) } } }
                                }
                            },
                            onIgnore = {
                                scope.launch {
                                    val previous = repository.ignoreSkippedOccurrences(series.title, series.dayOfMonth, today)
                                    undo(getString(Res.string.skipped_ignored, series.title), getString(Res.string.action_revert)) {
                                        scope.launch { repository.restoreSkipNotice(series.title, series.dayOfMonth, previous) }
                                    }
                                }
                            },
                        )
                    }
                }

                // --- À traiter
                one("inbox-title") {
                    GuideMark(GuideSection.DAY_INBOX) { SectionTitle(stringResource(Res.string.inbox_title), view.inbox.size, Modifier.padding(top = KairosListSectionExtra)) }
                }
                one("inbox-help") {
                    Column {
                        // Liste vidée par le filtre seulement : ne pas annoncer que tout est clarifié.
                        val empty = if (view.schedule.toProcess.isEmpty()) Res.string.inbox_empty else Res.string.inbox_empty_filtered
                        Hint(stringResource(if (view.inbox.isEmpty()) empty else Res.string.inbox_help))
                        InboxHelp(estimates.references)
                    }
                }
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
                block("now") {
                    GuideMark(GuideSection.DAY_NOW) {
                        NowCard(
                            view, language, notifyState,
                            onDone = { t -> scope.launch { repository.toggleDone(t.id) } },
                            onSnooze = { t -> scope.launch { repository.snooze(t.id) } },
                            onToggleTimer = toggleTimer,
                            onEnableAlerts = { scope.launch { services.notifier.requestPermission() } },
                        )
                    }
                }
                if (view.priorityOverload) block("overload") { OverloadBanner(view.priorityOverloadCount) }

                // Un filtre actif remonte au-dessus des listes qu'il réduit.
                val filters: @Composable () -> Unit = {
                    FilterCard(
                        filter, filterOpen || filter.active, { filterOpen = !(filterOpen || filter.active) }, { filter = it },
                        view.projects, snapshot.settings.taskTypeList, searchFocus, showShortcuts,
                    )
                }
                if (filter.active) block("filters", filters)

                // --- Aujourd'hui, dans l'ordre
                one("agenda-title") {
                    GuideMark(GuideSection.DAY_AGENDA) {
                        Column(Modifier.padding(top = KairosListSectionExtra)) {
                            Text(stringResource(Res.string.agenda_title), style = MaterialTheme.typography.titleMedium)
                            // Journées entières des calendriers importés : un badge, sans effet sur le planning (issue #136).
                            DayMarkBadges(view.dayMarks, Modifier.padding(vertical = 4.dp))
                            Hint(stringResource(if (view.agenda.isEmpty()) Res.string.agenda_empty else Res.string.agenda_hint))
                        }
                    }
                }
                list.count(view.agenda.map { it.task.id })
                items(view.agenda, key = { "agenda-${it.task.id}" }) { item -> AgendaRow(item, ctx, snapshot.settings.cognitiveDipTroughHour) }

                // --- Sections secondaires : repliées, sauf « Sans créneau » (des tâches du jour).
                section("unscheduled", Res.string.unscheduled_title, Res.string.unscheduled_hint, view.unscheduled.size) {
                    rows("unscheduled", view.unscheduled) { task ->
                        // Son contexte n'a aucune plage ce jour-là (docs/spec/planning-personnalise.md § Lot B2) : la raison, en étiquette.
                        val noWindow = view.noWindow[task.id]
                        TaskRow(task, ctx, before = { if (noWindow != null) WarnBadge(stringResource(Res.string.day_no_window, noWindow), KairosIcons.Schedule) })
                    }
                }
                section("blocked", Res.string.blocked_title, Res.string.blocked_hint, view.blocked.size) {
                    rows("blocked", view.blocked.map { it.task }) { task ->
                        val reasons = teamBlockerLabels(view.blocked.first { it.task.id == task.id }.blockers, task, snapshot, repository.snapshot.value)
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

                if (!filter.active) block("filters", filters)
                section("backlog", Res.string.backlog_title, Res.string.backlog_hint, view.backlog.size, showEmpty = true) {
                    rows("backlog", view.backlog) { TaskRow(it, ctx) }
                }
                if (!twoColumns) {
                    block("running") { RunningCard(view, services.clock, onStop = { scope.launch { repository.stopTimer() } }) }
                    block("timeline") { TimelineCard(view, language) }
                }
            }
            if (twoColumns) {
                Column(
                    Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = KairosSpacing.l),
                    verticalArrangement = Arrangement.spacedBy(KairosSpacing.m),
                ) {
                    RunningCard(view, services.clock, onStop = { scope.launch { repository.stopTimer() } })
                    TimelineCard(view, language)
                }
            }
        }
        // Explication de la place de la tâche créée : trois étapes, non bloquantes, en bas de l'écran.
        if (placedId != null && placedStep >= 0) {
            val steps = FirstTaskCoach.placement(placed = view.agenda.any { it.task.id == placedId })
            val index = placedStep.coerceIn(0, steps.lastIndex)
            CoachPanel(
                step = steps[index],
                index = index,
                total = steps.size,
                onPrevious = if (index > 0) ({ placedStep = index - 1 }) else null,
                onNext = { placedStep = index + 1 },
                onSkip = { placedStep = -1; placedId = null },
                modifier = Modifier.align(Alignment.BottomCenter).padding(KairosSpacing.l).widthIn(max = 560.dp),
            )
        }
    }

    if (creating) {
        EditTaskDialog(
            task = draft,
            day = day,
            today = today,
            taskTypes = snapshot.settings.taskTypeList,
            candidates = view.openTasks,
            blockerIds = emptySet(),
            dependencies = snapshot.dependencies,
            titles = remember(snapshot.tasks) { snapshot.tasks.associate { it.id to it.title } },
            estimates = estimates,
            // Fermer la fiche n'écrit rien ; rouvrir recommence au titre.
            onDismiss = { creating = false; coachStep = -1 },
            onSave = { edit ->
                // Enregistrée avec l'accompagnement actif : il est mené à son terme, l'invitation ne revient plus (issue #94) ;
                // celui de la récurrente (issue #98) n'est pas celui de la première tâche.
                val coached = coachStep >= 0 && !coachRecurring
                creating = false
                coachStep = -1
                scope.launch {
                    // La création réelle (et non le dernier clic du coach) déclenche les félicitations.
                    val id = repository.createTask(edit.title) ?: return@launch
                    repository.updateTask(id, edit)
                    if (coached) repository.updateGuide { it.copy(firstTaskDone = true) }
                    placedId = id
                    congrats = true
                }
            },
            onDelete = {},
            coach = if (coachStep >= 0) {
                val steps = if (coachRecurring) RecurrenceCoach.STEPS else FirstTaskCoach.STEPS
                CoachSession(
                    step = coachStep,
                    onStep = { coachStep = it.coerceIn(0, steps.lastIndex) },
                    onSkip = { coachStep = -1 },
                    steps = steps,
                    holidays = remember(today, snapshot.settings.holidaysFr, snapshot.settings.extraHolidays) {
                        Workdays.holidaysFor(today, snapshot.settings.holidaysFr, snapshot.settings.extraHolidays)
                    },
                )
            } else {
                null
            },
        )
    }
    val created = placedId?.let { id -> snapshot.tasks.firstOrNull { it.id == id } }
    if (congrats && created != null) {
        FirstTaskCongratsDialog(
            onTimer = { congrats = false; placedId = null; scope.launch { repository.startTimer(created.id) } },
            onWhere = { congrats = false; placedStep = 0 },
            onDiscover = { congrats = false; placedId = null; onDiscover() },
            onClose = { congrats = false; placedId = null },
        )
    }
    editingId?.let { id ->
        val task = snapshot.tasks.firstOrNull { it.id == id }
        if (task == null) {
            editingId = null
        } else {
            EditTaskDialog(
                task = task,
                day = day,
                today = today,
                learned = view.learned[id],
                taskTypes = snapshot.settings.taskTypeList,
                candidates = view.openTasks,
                blockerIds = view.blockersOf[id].orEmpty().toSet(),
                // Sous-tâches d'une tâche perso modifiables ici ; une tâche d'équipe a ses propres règles (equipe-backlog-suivi.md).
                subtasks = if (task.space == TaskSpace.PERSONAL) subtasksOf(id, snapshot.tasks) else emptyList(),
                dependencies = snapshot.dependencies,
                titles = remember(snapshot.tasks) { snapshot.tasks.associate { it.id to it.title } },
                estimates = estimates,
                onDismiss = { editingId = null },
                onSave = { edit ->
                    // Un blocage ajouté : « Bloquées » s'ouvre pour cette fois et un message dit où la tâche est allée.
                    val before = view.blockersOf[id].orEmpty().toSet()
                    val openIds = view.openTasks.mapTo(HashSet()) { it.id }
                    val added = edit.blockerIds.filter { it !in before && it in openIds }
                    scope.launch {
                        repository.updateTask(id, edit)
                        if (added.isNotEmpty() && task.status == TaskStatus.TODO) {
                            val names = added.mapNotNull { blocker -> snapshot.tasks.firstOrNull { it.id == blocker }?.title }
                            sectionOpen["blocked"] = true
                            showMessage(getString(Res.string.blocked_feedback, task.title, names.joinToString(", ")))
                        }
                    }
                    editingId = null
                },
                onDelete = { scope.launch { repository.deleteTask(id) }; editingId = null },
                // Tâche d'équipe assignée à « moi » : « Assigné à » en lecture seule, le reste comme une tâche perso.
                team = if (task.space == TaskSpace.TEAM) TeamTaskSheet(AssignContext.of(repository.snapshot.value, day), editable = false, today = day) else null,
                // Tâche reçue d'un manager : sa marque et l'avancement déclaré par le membre (docs/spec/equipe-echanges.md).
                // Tâche perso qui peut compter dans l'équipe (« moi » existe) : l'interrupteur « Compter dans l'équipe ».
                countInTeam = if (TeamWork.countable(task) && TeamWork.self(snapshot) != null) !task.teamExcluded else null,
                // Proposer la tâche au manager d'une équipe dont on reçoit des tâches (issue #121).
                proposalTargets = remember(snapshot.tasks) { ReceivedTasks.origins(snapshot) },
                received = ReceivedTasks.originOf(task)?.let { origin ->
                    ReceivedTaskSheet(origin, task.originRemoved, onKeep = { scope.launch { repository.detachOrigin(id) }; editingId = null }) { edit, percent ->
                        scope.launch {
                            repository.updateTask(id, edit)
                            percent?.let { repository.setReceivedProgress(id, it) }
                        }
                        editingId = null
                    }
                },
            )
        }
    }
    editingBlock?.takeIf { it.template.source is BlockSource.Imported }?.let { block ->
        ImportedBlockDeleteDialog(
            block.template,
            onDismiss = { editingBlock = null },
            onDelete = { scope.launch { repository.deleteBlock(block.id) }; editingBlock = null },
        )
    }
    editingBlock?.takeIf { !it.template.imported }?.let { block ->
        BlockDialog(
            block,
            day,
            onDismiss = { editingBlock = null },
            onSave = { edit -> scope.launch { repository.updateBlock(block.id, edit) }; editingBlock = null },
            onDelete = { scope.launch { repository.deleteBlock(block.id) }; editingBlock = null },
            occurrence = OccurrenceActions(
                // « Sauter ce jour » se défait depuis le message (issue #110) : l'exception d'avant revient.
                onSkip = {
                    val before = block.exception
                    val name = block.occurrence.title.ifEmpty { block.template.title }
                    scope.launch {
                        if (repository.skipOccurrence(block.id, day)) {
                            undo(getString(Res.string.block_skipped_message, name, Dates.short(day, language, today)), getString(Res.string.action_revert)) {
                                scope.launch { repository.putOccurrenceException(block.id, day, before) }
                            }
                        }
                    }
                    editingBlock = null
                },
                onOverride = { title, start, end -> scope.launch { repository.overrideOccurrence(block.id, day, title, start, end) }; editingBlock = null },
                onRestore = { scope.launch { repository.restoreOccurrence(block.id, day) }; editingBlock = null },
            ),
        )
    }
}

/**
 * Libellés de « Bloquée par » : la vue Jour nomme un bloqueur hors de la vue Perso
 * (la tâche d'un collègue) par « #id » ; on y met son titre et son assigné, lus dans
 * la base complète ([full]). Sans bloqueur de ce genre (toujours le cas en mode solo),
 * la liste est rendue telle quelle.
 */
@Composable
private fun teamBlockerLabels(reasons: List<String>, task: Task, personal: KairosSnapshot, full: KairosSnapshot): List<String> {
    val known = personal.tasks.mapTo(HashSet()) { it.id }
    val outside = personal.dependencies.filter { it.taskId == task.id && it.blockerId !in known }.map { it.blockerId }
    if (outside.isEmpty()) return reasons
    val labels = reasons.toMutableList()
    for (id in outside) {
        val blocker = full.tasks.firstOrNull { it.id == id && it.space == TaskSpace.TEAM } ?: continue
        val who = full.members.firstOrNull { it.id == blocker.assigneeId }?.name ?: stringResource(Res.string.blocker_unassigned)
        val at = labels.indexOf("#$id")
        if (at >= 0) labels[at] = stringResource(Res.string.blocker_team, blocker.title, who)
    }
    return labels
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
private class ListBuilder(private val scope: LazyListScope, private val positions: MutableMap<String, Int>, private val rowPositions: MutableMap<Long, Int>) {
    private var index = 0

    init {
        // Reconstruit à chaque composition de la liste : une tâche partie d'une section n'y laisse pas sa place.
        rowPositions.clear()
    }

    /** Des éléments posés à part (l'agenda) : on retient la place de chaque tâche, pour y défiler. */
    fun count(taskIds: List<Long>) {
        taskIds.forEachIndexed { i, id -> if (id !in rowPositions) rowPositions[id] = index + i }
        index += taskIds.size
    }

    fun one(key: String, content: @Composable () -> Unit) {
        positions[key] = index++
        scope.item(key = key) { content() }
    }

    fun rows(key: String, tasks: List<Task>, row: @Composable (Task) -> Unit) {
        tasks.forEachIndexed { i, t -> if (t.id !in rowPositions) rowPositions[t.id] = index + i }
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
        /** Section que le guide sait montrer : son titre porte la mise en valeur (issue #95). */
        spot: GuideSection?,
        content: () -> Unit,
    ) {
        if (count == 0 && !showEmpty) return
        val isOpen = open[key] ?: false
        one("section-$key") {
            Spotted(spot) {
                Column(Modifier.padding(top = KairosListSectionExtra)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().disclosure(isOpen, heading = true) { open[key] = !isOpen }.padding(vertical = 6.dp),
                    ) {
                        Icon(if (isOpen) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
                        Text("${stringResource(title)} ($count)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
                    }
                    if (isOpen) Hint(stringResource(hint))
                }
            }
        }
        if (isOpen) content()
    }
}

/** [GuideMark] quand [section] est donnée, rien sinon. */
@Composable
private fun Spotted(section: GuideSection?, content: @Composable () -> Unit) {
    if (section == null) content() else GuideMark(section, content = content)
}

/** Section repliable (clé de `sectionOpen`) qu'ouvre une section du guide. */
private val DAY_SPOT_SECTIONS = mapOf(GuideSection.DAY_BLOCKED to "blocked", GuideSection.DAY_PARENTS to "parents")

/** Élément de tête de la liste (clé de `positions`) de chaque section du guide de la vue Jour. */
private val DAY_SPOT_ITEMS = mapOf(
    GuideSection.DAY_INBOX to "inbox-title",
    GuideSection.DAY_AGENDA to "agenda-title",
    GuideSection.DAY_NOW to "now",
    GuideSection.DAY_BLOCKED to "section-blocked",
    GuideSection.DAY_PARENTS to "section-parents",
)

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
            item.affinity?.let {
                val res = when (it) {
                    AffinitySource.MANUAL -> Res.string.note_affinity_manual
                    AffinitySource.EFFICIENT -> Res.string.note_affinity_efficient
                    AffinitySource.COMBINED -> Res.string.note_affinity_combined
                    AffinitySource.DETECTED -> Res.string.note_affinity_detected
                }
                WarnBadge(stringResource(res, item.task.taskType.trim()), KairosIcons.Schedule)
            }
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
internal fun InboxQualify(task: Task, onPriority: (Int?) -> Unit, onPoints: (Int?) -> Unit, start: Dp = 48.dp) {
    Column(Modifier.padding(start = start, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Text("$text ($count)", style = MaterialTheme.typography.titleMedium, modifier = modifier.heading())
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
