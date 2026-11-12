package com.skohscripts.kairos.ui.week

import com.skohscripts.kairos.ui.guide.GtdStepButton
import com.skohscripts.kairos.ui.guide.GtdStep
import com.skohscripts.kairos.ui.app.withDeviceCalendars
import com.skohscripts.kairos.ui.day.DayMarkBadges
import com.skohscripts.kairos.ui.day.blockTitle
import com.skohscripts.kairos.ui.app.uiLanguage
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import com.skohscripts.kairos.ui.app.expandedState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.forecast.SoloForecast
import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.day.DayView
import com.skohscripts.kairos.core.week.WeekDay
import com.skohscripts.kairos.core.week.WeekView
import com.skohscripts.kairos.ui.LocalPlatform
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.day.Badge
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.day.FilterCard
import com.skohscripts.kairos.ui.day.PriorityBadge
import com.skohscripts.kairos.ui.day.duration
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.backlog_hint
import com.skohscripts.kairos.ui.generated.resources.backlog_implicit_hint
import com.skohscripts.kairos.ui.generated.resources.backlog_implicit_hint_learned
import com.skohscripts.kairos.ui.generated.resources.backlog_title_lingering
import com.skohscripts.kairos.ui.generated.resources.tag_stale
import com.skohscripts.kairos.ui.generated.resources.backlog_title
import com.skohscripts.kairos.ui.generated.resources.week_block
import com.skohscripts.kairos.ui.generated.resources.view_month
import com.skohscripts.kairos.ui.generated.resources.view_week
import com.skohscripts.kairos.ui.theme.KairosSegmentedButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedRow
import androidx.compose.material3.SegmentedButtonDefaults
import com.skohscripts.kairos.ui.generated.resources.week_next
import com.skohscripts.kairos.ui.generated.resources.week_nothing
import com.skohscripts.kairos.ui.generated.resources.week_scheduled
import com.skohscripts.kairos.ui.generated.resources.week_prev
import com.skohscripts.kairos.ui.generated.resources.week_see_day
import com.skohscripts.kairos.ui.generated.resources.week_realistic
import com.skohscripts.kairos.ui.generated.resources.recap_title_week
import com.skohscripts.kairos.ui.recap.PeriodHeader
import com.skohscripts.kairos.ui.recap.RecapCard
import com.skohscripts.kairos.ui.recap.ModelCard
import com.skohscripts.kairos.core.recap.Recap
import com.skohscripts.kairos.ui.generated.resources.week_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.navigation.NavState
import com.skohscripts.kairos.ui.theme.LocalKairosExtraColors
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.skohscripts.kairos.core.engine.Scheduling
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.ui.app.LocalUndoMessages
import com.skohscripts.kairos.ui.day.DateChoice
import com.skohscripts.kairos.ui.day.WarnBadge
import com.skohscripts.kairos.ui.generated.resources.action_move
import com.skohscripts.kairos.ui.generated.resources.action_revert
import com.skohscripts.kairos.ui.generated.resources.date_choose
import com.skohscripts.kairos.ui.generated.resources.deadline_badge
import com.skohscripts.kairos.ui.generated.resources.task_moved
import com.skohscripts.kairos.ui.generated.resources.week_move_day_off
import com.skohscripts.kairos.ui.generated.resources.week_move_holiday
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Vue Semaine (docs/spec/vue-semaine.md) : navigation d'une semaine à
 * l'autre, recherche et filtres, bilan de la semaine (docs/spec/bilan.md), backlog
 * (sans date, donc absent de la grille), puis la grille des sept jours ;
 * « Voir le détail » ouvre la vue Jour de ce jour-là.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekScreen(services: AppServices, nav: NavState, onOpenDay: (LocalDate) -> Unit) {
    val stored by services.repository.personalSnapshot.collectAsState()
    // Calendriers du téléphone (Android) : ajoutés pour le calcul, jamais écrits (docs/spec/connexions-externes.md).
    val snapshot = withDeviceCalendars(services, stored)
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val now = services.clock.now()
    val today = now.toLocalDateTime(timeZone).date
    val language = uiLanguage()
    var filter by remember { mutableStateOf(DayFilter()) }
    var filterOpen by remember { mutableStateOf(false) }
    var backlogOpen by remember { mutableStateOf(false) }
    val week = remember(snapshot, nav.week, filter, today) { WeekView.build(snapshot, nav.week ?: today, now, timeZone, filter) }
    // Backlog et réglages du score (échéance implicite apprise du rythme, docs/spec/taches-qui-trainent.md).
    val todayView = remember(snapshot, filter, today) { DayView.build(snapshot, today, null, timeZone, filter, instant = now) }
    val projects = remember(snapshot) { snapshot.tasks.map { it.projectTag }.filter { it.isNotEmpty() }.distinct().sorted() }
    val scope = rememberCoroutineScope()
    val undo = LocalUndoMessages.current
    // « Déplacer » (issue #106) : programme la tâche pour ce jour, comme la vue Jour ; « Annuler » rend la date d'avant.
    val onMove: (Task, LocalDate) -> Unit = { task, date ->
        val before = task.scheduledDate
        scope.launch {
            services.repository.schedule(task.id, date)
            undo(getString(Res.string.task_moved, task.title, Dates.short(date, language, today)), getString(Res.string.action_revert)) {
                scope.launch { services.repository.schedule(task.id, before) }
            }
        }
    }
    val move = MoveContext(week.days, today, language, onMove)

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 1400.dp).fillMaxWidth().padding(KairosSpacing.l), verticalArrangement = Arrangement.spacedBy(KairosSpacing.m)) {
            GtdStepButton(GtdStep.REFLECT)
            WeekMonthSwitch(nav, today)
            val monday = week.monday
            val sunday = monday.plus(DatePeriod(days = 6))
            // En-tête sur une ligne (docs/spec/bilan.md) : le titre tient entre deux boutons à icône, même à 360 dp.
            PeriodHeader(
                stringResource(Res.string.week_title, Dates.dayMonth(monday, language)),
                stringResource(Res.string.week_prev), stringResource(Res.string.week_next),
                onPrevious = { nav.week = monday.minus(DatePeriod(days = 7)) },
                onNext = { nav.week = monday.plus(DatePeriod(days = 7)) },
                onToday = if (today in monday..sunday) null else ({ nav.week = null }),
            )
            FilterCard(
                filter, filterOpen || filter.active, { filterOpen = !(filterOpen || filter.active) }, { filter = it },
                projects, snapshot.settings.taskTypeList, remember { FocusRequester() }, LocalPlatform.current != Platform.ANDROID,
            )
            // Bilan de la semaine (docs/spec/bilan.md, issue #233) : le filtre réduit ses tâches et leur temps.
            val recap = remember(snapshot, monday, filter, today) { Recap.build(snapshot, monday, sunday, now, timeZone, filter) }
            // Semaine réaliste (docs/spec/previsions-taches.md § Étape C) : la semaine courante ou à venir, avec assez d'historique.
            val realistic = remember(stored, today) { SoloForecast.capacity(stored, today, now, timeZone) }
            val forecast: (@Composable () -> Unit)? = if (realistic.available && sunday >= today) {
                {
                    val committed = remember(stored, monday) { SoloForecast.committedMinutes(stored, monday, now, timeZone) }
                    Text(
                        stringResource(Res.string.week_realistic, duration(realistic.weekMinutes(15)), duration(realistic.weekMinutes(85)), duration(committed)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                null
            }
            RecapCard(recap, stringResource(Res.string.recap_title_week), stringResource(Res.string.week_title, Dates.dayMonth(monday, language)), today, language, forecast)
            // Ce que le modèle apprend (docs/spec/bilan.md § Ce que Kairos apprend) : une carte à part, hors du bilan copié.
            ModelCard(services, stored, monday, sunday, today, language)
            BacklogList(todayView.backlog, todayView.scoringSettings, todayView.implicitDeadlineLearned, move, backlogOpen) { backlogOpen = !backlogOpen }
            // Sept colonnes égales quand elles tiennent (140 dp chacune au moins), sinon autant de colonnes égales de
            // 160 dp au moins que la largeur en permet : les cartes remplissent la ligne, sans moitié droite vide.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 140.dp * 7 + 8.dp * 6) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
                        week.days.forEach { day ->
                            DayCard(day, day.date == today, move, Modifier.weight(1f).fillMaxHeight()) { onOpenDay(day.date) }
                        }
                    }
                } else {
                    val columns = ((maxWidth + 8.dp) / (160.dp + 8.dp)).toInt().coerceIn(1, 7)
                    val cardWidth = (maxWidth - 8.dp * (columns - 1)) / columns
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = columns,
                    ) {
                        week.days.forEach { day -> DayCard(day, day.date == today, move, Modifier.width(cardWidth)) { onOpenDay(day.date) } }
                    }
                }
            }
        }
    }
}

/**
 * « Semaine » / « Mois » en tête de la destination Semaine (docs/spec/vue-mois.md) : la vue Mois n'est pas une sixième
 * destination (cinq au plus dans une barre MD3). Passer de l'une à l'autre garde la période : le mois de la semaine
 * affichée, la semaine du 1er du mois affiché (la semaine courante pour le mois courant).
 */
@Composable
internal fun WeekMonthSwitch(nav: NavState, today: LocalDate) {
    KairosSegmentedRow(Modifier.fillMaxWidth()) {
        KairosSegmentedButton(selected = !nav.monthMode, onClick = {
            if (nav.monthMode) {
                // Le mois courant revient à la semaine courante, un autre mois à la semaine de son 1er.
                nav.week = nav.month?.takeUnless { it.year == today.year && it.month == today.month }
                nav.monthMode = false
            }
        }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(Res.string.view_week)) }
        KairosSegmentedButton(selected = nav.monthMode, onClick = {
            if (!nav.monthMode) {
                nav.month = nav.week
                nav.monthMode = true
            }
        }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(Res.string.view_month)) }
    }
}

/**
 * Revue de ce qui traîne (docs/spec/taches-qui-trainent.md) : le backlog sans date, du plus ancien au plus récent ; une
 * tâche dont l'échéance implicite est atteinte porte « traîne depuis N j » ; toucher une tâche ouvre « Déplacer ».
 */
@Composable
private fun BacklogList(tasks: List<Task>, settings: Settings, learned: Boolean, move: MoveContext, open: Boolean, onToggle: () -> Unit) {
    val sorted = remember(tasks) { tasks.sortedBy { it.createdAt } }
    val lingering = sorted.count { Scheduling.isLingering(it, move.today, settings) }
    Column {
        KairosTextButton(onClick = onToggle, modifier = Modifier.expandedState(open), contentPadding = KairosButtonIconPadding) {
            Icon(if (open) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
            val title = if (lingering > 0) {
                stringResource(Res.string.backlog_title_lingering, sorted.size, lingering)
            } else {
                "${stringResource(Res.string.backlog_title)} (${sorted.size})"
            }
            Text(title, modifier = Modifier.padding(start = 4.dp))
        }
        if (open) {
            Text(stringResource(Res.string.backlog_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (settings.implicitDeadline > 0) {
                Text(
                    stringResource(if (learned) Res.string.backlog_implicit_hint_learned else Res.string.backlog_implicit_hint, settings.implicitDeadline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            sorted.forEach { task ->
                MovableTask(task, from = null, move) {
                    Text(task.title, style = MaterialTheme.typography.bodyMedium)
                    if (Scheduling.isLingering(task, move.today, settings)) {
                        WarnBadge(stringResource(Res.string.tag_stale, task.createdAt.toLocalDateTime(TimeZone.UTC).date.daysUntil(move.today)))
                    }
                }
            }
        }
    }
}

/**
 * Carte d'un jour : créneaux, tâches faites (barrées, coche verte), échéances
 * à faire (priorité, projet), « — » si rien ; aujourd'hui en conteneur
 * secondaire. Titres coupés sur une ligne : le détail est dans la vue Jour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayCard(day: WeekDay, isToday: Boolean, move: MoveContext, modifier: Modifier, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val language = move.language
    Surface(
        color = if (isToday) scheme.secondaryContainer else scheme.surfaceContainerLow,
        border = if (isToday) BorderStroke(1.dp, scheme.secondary) else null,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Dates.dayShort(day.date, language), style = MaterialTheme.typography.titleSmall)
            DayMarkBadges(day.marks)
            day.blocks.forEach { b ->
                Badge(
                    stringResource(Res.string.week_block, if (b.imported) blockTitle(b) else b.title, Dates.time(b.start, language), Dates.time(b.end, language)),
                    icon = if (b.imported) KairosIcons.Event else null,
                )
            }
            // « — » seulement pour un jour vraiment vide : sous une liste de créneaux il disait « rien » à tort.
            if (day.tasks.isEmpty() && day.done.isEmpty() && day.scheduled.isEmpty() && day.blocks.isEmpty() && day.marks.isEmpty()) {
                Text(stringResource(Res.string.week_nothing), color = scheme.onSurfaceVariant)
            }
            day.done.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(KairosIcons.CheckCircleFilled, contentDescription = null, tint = LocalKairosExtraColors.current.ok, modifier = Modifier.size(14.dp))
                    Text(
                        t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = TextDecoration.LineThrough,
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            day.tasks.forEach { t ->
                MovableTask(t, day.date, move) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        t.priority?.let { PriorityBadge(it) }
                        if (t.projectTag.isNotEmpty()) Badge(t.projectTag)
                    }
                }
            }
            day.scheduled.forEach { t ->
                MovableTask(t, day.date, move) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Badge(stringResource(Res.string.week_scheduled))
                        // Rappel de l'échéance, qui n'est pas ce jour-là ; contour d'avertissement si elle tombe avant.
                        t.deadline?.let { due ->
                            val text = stringResource(Res.string.deadline_badge, Dates.short(due, language, day.date))
                            if (due < day.date) WarnBadge(text) else Badge(text)
                        }
                        t.priority?.let { PriorityBadge(it) }
                        if (t.projectTag.isNotEmpty()) Badge(t.projectTag)
                    }
                }
            }
            Spacer(Modifier.size(2.dp))
            KairosTextButton(onClick = onOpen) { Text(stringResource(Res.string.week_see_day)) }
        }
    }
}

/** Ce qu'il faut à une ligne pour proposer « Déplacer » : les jours de la semaine affichée, aujourd'hui, la langue, l'action. */
private class MoveContext(val days: List<WeekDay>, val today: LocalDate, val language: String, val onMove: (Task, LocalDate) -> Unit)

/**
 * Jours proposés par « Déplacer » depuis le jour [from] : ceux de la semaine affichée à partir d'aujourd'hui, sauf [from]
 * (programmer hier ramènerait la tâche dans « Aujourd'hui » de la vue Jour ; « Choisir une date… » reste libre).
 * [from] nul : tâche sans date du backlog, aujourd'hui compris.
 */
internal fun moveTargets(days: List<WeekDay>, from: LocalDate?, today: LocalDate): List<WeekDay> =
    days.filter { it.date >= today && it.date != from }

/**
 * Ligne d'une tâche dans une carte de jour. Tâche **personnelle** à faire : toute la ligne (48 dp au moins) ouvre
 * « Déplacer vers un autre jour… » (docs/spec/vue-semaine.md, issue #106) ; une icône de plus ne tenait pas dans une
 * colonne de 140 dp. Une tâche d'équipe reste une ligne inerte (pas de date programmée, pas de faux bouton).
 */
@Composable
private fun MovableTask(task: Task, from: LocalDate?, move: MoveContext, content: @Composable ColumnScope.() -> Unit) {
    if (task.space != TaskSpace.PERSONAL || task.status != TaskStatus.TODO) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
        return
    }
    var open by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.action_move)
    Box {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = label, role = Role.Button) { open = true },
            content = content,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            moveTargets(move.days, from, move.today).forEach { target ->
                val day = Dates.dayShort(target.date, move.language)
                val text = when {
                    target.holiday -> stringResource(Res.string.week_move_holiday, day)
                    target.dayOff -> stringResource(Res.string.week_move_day_off, day)
                    else -> day
                }
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; move.onMove(task, target.date) })
            }
            DropdownMenuItem(text = { Text(stringResource(Res.string.date_choose)) }, onClick = { open = false; picking = true })
        }
    }
    if (picking) {
        DateChoice(task.scheduledDate ?: move.today, onPick = { picking = false; move.onMove(task, it) }, onDismiss = { picking = false })
    }
}
