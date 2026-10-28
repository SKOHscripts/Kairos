package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.ui.guide.demo.DemoSpot
import com.skohscripts.kairos.ui.guide.demo.DemoMark
import com.skohscripts.kairos.ui.theme.KairosRowIconButton
import com.skohscripts.kairos.ui.app.disclosure
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.day.DayView
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.ui.generated.resources.learned_badge_description
import com.skohscripts.kairos.ui.generated.resources.action_move
import com.skohscripts.kairos.ui.generated.resources.tag_blocks_many
import com.skohscripts.kairos.ui.generated.resources.tag_blocks_one
import com.skohscripts.kairos.ui.generated.resources.date_choose
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_done
import com.skohscripts.kairos.ui.generated.resources.action_edit
import com.skohscripts.kairos.ui.generated.resources.action_snooze
import com.skohscripts.kairos.ui.generated.resources.action_timer_start
import com.skohscripts.kairos.ui.generated.resources.action_timer_stop
import com.skohscripts.kairos.ui.generated.resources.spent_of_estimate
import com.skohscripts.kairos.ui.generated.resources.action_undo
import com.skohscripts.kairos.ui.generated.resources.deadline_badge
import com.skohscripts.kairos.ui.generated.resources.description_expand
import com.skohscripts.kairos.ui.generated.resources.tag_recurring
import com.skohscripts.kairos.ui.generated.resources.tag_scheduled
import com.skohscripts.kairos.ui.generated.resources.tag_stale
import com.skohscripts.kairos.core.team.exchange.ReceivedTasks
import com.skohscripts.kairos.core.team.exchange.TeamOrigin
import com.skohscripts.kairos.ui.generated.resources.received_mark
import com.skohscripts.kairos.ui.generated.resources.received_mark_description
import com.skohscripts.kairos.ui.generated.resources.received_removed
import com.skohscripts.kairos.ui.generated.resources.received_removed_description
import com.skohscripts.kairos.ui.team.managerLabel
import com.skohscripts.kairos.ui.generated.resources.team_mark
import com.skohscripts.kairos.ui.generated.resources.team_mark_description
import com.skohscripts.kairos.ui.chrono.rememberLiveMinutes
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.LocalKairosExtraColors
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** Ce dont une ligne de tâche a besoin, commun à toutes les sections de la vue Jour. */
internal class RowContext(
    val view: DayView,
    val language: String,
    /** Largeur étroite : priorité et points passent sous le corps. */
    val compact: Boolean,
    val onToggleDone: (Task) -> Unit,
    val onSnooze: (Task) -> Unit,
    /** « Déplacer… » : programme la tâche personnelle pour ce jour (docs/spec/planning-personnalise.md § Lot A, 2). */
    val onMove: (Task, LocalDate) -> Unit,
    /** Le vrai jour d'aujourd'hui : base des choix de « Déplacer… ». */
    val today: LocalDate,
    val onEdit: (Task) -> Unit,
    /** Démarre le chrono sur la tâche, ou l'arrête si c'est elle qui tourne. */
    val onToggleTimer: (Task) -> Unit = {},
    val clock: Clock = Clock.System,
    /** Réglages des durées apprises (seuil de fiabilité affiché par « Pourquoi à cette place ? »). */
    val learning: LearningSettings = LearningSettings(),
    /** La tâche que l'accompagnement de la première tâche explique (docs/spec/premiere-tache.md) : sa ligne est cernée, jamais par la couleur seule (le panneau la nomme). */
    val highlight: Long? = null,
)

/**
 * Ligne de tâche en colonnes fixes (docs/spec/vue-jour.md § Ligne de
 * tâche, issue #33) : [coche] [corps : heure et titre,
 * étiquettes, description] [score, priorité, points] [décaler, modifier]. Le
 * corps grandit vers le bas, jamais vers la droite : les colonnes de droite
 * restent alignées d'une ligne à l'autre. En largeur étroite, la colonne
 * « clés » passe sous le corps et les actions la rejoignent sur une même ligne ;
 * la coche ne bouge pas.
 *
 * [before] : étiquettes propres à la section (épinglée, deep work, chemin
 * critique…) ; [after] : notes de placement (repoussée, creux, conflit).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskRow(
    task: Task,
    ctx: RowContext,
    time: LocalDateTime? = null,
    blocked: Boolean = false,
    editable: Boolean = true,
    showDescription: Boolean = true,
    before: @Composable FlowRowScope.() -> Unit = {},
    after: @Composable FlowRowScope.() -> Unit = {},
    extra: @Composable () -> Unit = {},
) {
    val done = task.status == TaskStatus.DONE
    val scheme = MaterialTheme.colorScheme
    val critical = ctx.view.buckets[task.id] == 0
    val errorColor = scheme.error
    Surface(
        // Bloquée : fond de surface et contour, jamais d'opacité (décision tracée dans
        // docs/spec/vue-jour.md).
        color = if (blocked) scheme.surface else scheme.surfaceContainerLow,
        border = when {
            ctx.highlight == task.id -> BorderStroke(2.dp, scheme.primary)
            blocked -> BorderStroke(1.dp, scheme.outlineVariant)
            else -> null
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                // Liseré critique : 3 dp rouges à gauche, jamais de remplissage (charte).
                .drawBehind { if (critical && !done) drawRect(errorColor, size = Size(3.dp.toPx(), size.height)) }
                .padding(vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                IconButton(onClick = { ctx.onToggleDone(task) }) {
                    Icon(
                        if (done) KairosIcons.CheckCircleFilled else KairosIcons.RadioUnchecked,
                        contentDescription = stringResource(if (done) Res.string.action_undo else Res.string.action_done),
                        tint = if (done) LocalKairosExtraColors.current.ok else scheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Title(task, ctx, time, done, blocked)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        before()
                        TaskTags(task, ctx)
                        after()
                    }
                    if (showDescription && !done) Description(task, ctx)
                    // Étroit : les clés et les actions partagent une ligne sous le corps, le titre garde la largeur (à 400 dp,
                    // trois boutons à droite ne lui laissaient que 175 px : trois ou quatre lignes par titre).
                    if (ctx.compact) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { KeyBadges(task, ctx) }
                            RowActions(task, ctx, editable)
                        }
                    }
                }
                if (!ctx.compact) {
                    Row(Modifier.padding(top = 10.dp, start = 8.dp)) { KeyBadges(task, ctx) }
                    RowActions(task, ctx, editable)
                }
                // Sans bouton en fin de ligne (étroit, ou « Fait » non modifiable), la marge du bord reste celle des autres lignes.
                if (ctx.compact || (task.status != TaskStatus.TODO && !editable)) Spacer(Modifier.width(12.dp))
            }
            extra()
        }
    }
}

/** Chrono, décaler (tâche à faire) et modifier : à droite du corps en largeur bureau, sous lui en largeur étroite. */
@Composable
private fun RowActions(task: Task, ctx: RowContext, editable: Boolean) {
    if (task.status == TaskStatus.TODO) {
        val running = ctx.view.running?.taskId == task.id
        DemoMark(DemoSpot.TIMER_START, task.id) {
            KairosRowIconButton(
                if (running) KairosIcons.Stop else KairosIcons.PlayArrow,
                stringResource(if (running) Res.string.action_timer_stop else Res.string.action_timer_start),
                onClick = { ctx.onToggleTimer(task) },
                tint = if (running) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
        KairosRowIconButton(KairosIcons.Redo, stringResource(Res.string.action_snooze), onClick = { ctx.onSnooze(task) })
        // Une tâche d'équipe n'a pas de date programmée (docs/spec/planning-personnalise.md) : pas de « Déplacer… ».
        if (task.space == TaskSpace.PERSONAL) DemoMark(DemoSpot.MOVE, task.id) { MoveButton(task, ctx) }
    }
    if (editable) {
        KairosRowIconButton(KairosIcons.Edit, stringResource(Res.string.action_edit), onClick = { ctx.onEdit(task) })
    }
}

/**
 * « Déplacer… » : un menu de dates (demain, lundi prochain, dans une semaine, ou « Choisir une date… » avec le sélecteur
 * Material 3) ; choisir une date programme la tâche pour ce jour ([RowContext.onMove]).
 */
@Composable
private fun MoveButton(task: Task, ctx: RowContext) {
    var open by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    Box {
        KairosRowIconButton(KairosIcons.DateRange, stringResource(Res.string.action_move), onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            dateShortcuts(ctx.today).filter { it.first != DateShortcut.TODAY }.forEach { (shortcut, date) ->
                DropdownMenuItem(
                    text = { Text("${stringResource(shortcut.label)} · ${Dates.short(date, ctx.language)}") },
                    onClick = { open = false; ctx.onMove(task, date) },
                )
            }
            DropdownMenuItem(text = { Text(stringResource(Res.string.date_choose)) }, onClick = { open = false; picking = true })
        }
    }
    if (picking) {
        DateChoice(task.scheduledDate ?: ctx.today, onPick = { picking = false; ctx.onMove(task, it) }, onDismiss = { picking = false })
    }
}

@Composable
private fun Title(task: Task, ctx: RowContext, time: LocalDateTime?, done: Boolean, blocked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val parent = ctx.view.parentTitle[task.id]
    val text = buildAnnotatedString {
        if (time != null) {
            withStyle(SpanStyle(color = scheme.primary, fontWeight = FontWeight.Medium)) { append(Dates.time(time, ctx.language)) }
            append("  ")
        }
        if (parent != null) withStyle(SpanStyle(color = scheme.onSurfaceVariant)) { append("$parent › ") }
        append(task.title)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        textDecoration = if (done) TextDecoration.LineThrough else null,
        color = if (done || blocked) scheme.onSurfaceVariant else scheme.onSurface,
    )
}

/**
 * Durée apprise appliquée à une tâche sans estimation (docs/spec/durees-apprises.md) : « ~45 min » avec l'icône d'horloge, et
 * pour le lecteur d'écran d'où elle vient. Jamais la couleur seule (la valeur est écrite).
 */
@Composable
internal fun LearnedBadge(estimate: DurationEstimator.Estimate) {
    val description = stringResource(Res.string.learned_badge_description, estimate.samples)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(KairosIcons.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Muted("~" + duration(estimate.minutes))
    }
}

/** Étiquettes de contexte : projet, type, durée, dates, récurrence, ancienneté. */
@Composable
private fun TaskTags(task: Task, ctx: RowContext) {
    // Tâche d'équipe assignée à « moi » (seule façon d'en voir une dans l'espace Perso).
    if (task.space == TaskSpace.TEAM) TeamMark()
    // Tâche reçue d'un manager par paquet (docs/spec/equipe-echanges.md) : « de Corentin », ou « retirée par Corentin ».
    ReceivedTasks.originOf(task)?.let { ReceivedMark(it, task.originRemoved) }
    if (task.projectTag.isNotEmpty()) Badge(task.projectTag)
    if (task.taskType.isNotEmpty()) Badge(task.taskType)
    task.estimatedMinutes?.takeIf { it > 0 }?.let { Muted(duration(it)) }
        ?: ctx.view.learned[task.id]?.takeIf { it.reliable && task.status == TaskStatus.TODO }?.let { LearnedBadge(it) }
    task.deadline?.let { Muted(stringResource(Res.string.deadline_badge, Dates.short(it, ctx.language, ctx.view.day))) }
    task.scheduledDate?.let { Muted(stringResource(Res.string.tag_scheduled, Dates.short(it, ctx.language, ctx.view.day))) }
    if (task.recurrence != TaskRecurrence.NONE) {
        Icon(
            KairosIcons.Repeat,
            contentDescription = stringResource(Res.string.tag_recurring),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
    // Cette tâche en bloque d'autres : on le voit sur le bloqueur (« bloque X »), pas seulement sur la tâche bloquée.
    if (task.status == TaskStatus.TODO) {
        ctx.view.blocking[task.id]?.takeIf { it.isNotEmpty() }?.let { titles ->
            Badge(
                if (titles.size == 1) stringResource(Res.string.tag_blocks_one, titles.single()) else stringResource(Res.string.tag_blocks_many, titles.size),
                nowrap = true,
            )
        }
    }
    TimeSpent(task, ctx)
    if (task.status == TaskStatus.TODO) {
        ctx.view.staleDays[task.id]?.takeIf { it > 0 }?.let { WarnBadge(stringResource(Res.string.tag_stale, it)) }
    }
}

/**
 * Marque d'une tâche d'équipe dans l'espace Perso (docs/spec/equipe-backlog-suivi.md
 * § Tâches assignées à moi) : icône `Groups` et le mot « Équipe » ; le lecteur
 * d'écran lit « tâche d'équipe ». Badge neutre (aucune couleur de marque).
 */
@Composable
internal fun TeamMark() {
    val description = stringResource(Res.string.team_mark_description)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = CircleShape,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Icon(KairosIcons.Groups, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(stringResource(Res.string.team_mark), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Marque d'une tâche **reçue** d'un manager (docs/spec/equipe-echanges.md § Vue Jour du membre) : badge neutre, icône
 * `Groups` et « de Corentin ». Une tâche retirée du dernier paquet ([removed]) montre « retirée par Corentin » en
 * contour et icône d'alerte, jamais en couleur. Le lecteur d'écran lit la phrase complète.
 */
@Composable
internal fun ReceivedMark(origin: TeamOrigin, removed: Boolean) {
    val manager = managerLabel(origin)
    val description = stringResource(if (removed) Res.string.received_removed_description else Res.string.received_mark_description, manager)
    if (removed) {
        Box(Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
            WarnBadge(stringResource(Res.string.received_removed, manager))
        }
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = CircleShape,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Icon(KairosIcons.Groups, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(stringResource(Res.string.received_mark, manager), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Temps passé : minuteur vivant si le chrono
 * tourne sur la tâche ; sinon « 40 min / 30 » (rouge au-delà de l'estimé).
 */
@Composable
private fun TimeSpent(task: Task, ctx: RowContext) {
    val scheme = MaterialTheme.colorScheme
    val running = ctx.view.running
    if (running != null && running.taskId == task.id) {
        val minutes by rememberLiveMinutes(running.startedAt, ctx.view.runningBaseMinutes, ctx.clock)
        Badge(duration(minutes), scheme.secondaryContainer, scheme.onSecondaryContainer, icon = KairosIcons.Schedule)
        return
    }
    val spent = ctx.view.spentByTask[task.id] ?: return
    if (spent <= 0) return
    val estimate = task.estimatedMinutes?.takeIf { it > 0 }
    when {
        estimate == null -> Badge(duration(spent))
        spent > estimate -> Badge(stringResource(Res.string.spent_of_estimate, duration(spent), duration(estimate)), scheme.errorContainer, scheme.onErrorContainer)
        else -> Badge(stringResource(Res.string.spent_of_estimate, duration(spent), duration(estimate)))
    }
}

/** Signaux de tri (`task_key_badges`) : score (s'ouvre sur son explication), priorité, points. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyBadges(task: Task, ctx: RowContext) {
    // FlowRow : à 360 dp, score, priorité et points passent à la ligne plutôt que d'écraser un libellé.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        if (task.status == TaskStatus.TODO) ctx.view.why[task.id]?.let { ScoreBadge(task, it, ctx.language, ctx.view.learned[task.id], ctx.learning, ctx.view.schedule.scheduled.firstOrNull { s -> s.task.id == task.id }?.affinity) }
        task.priority?.let { PriorityBadge(it) }
        task.fibonacciPoints?.let { Badge(Levels.pointsShort(it), nowrap = true) }
    }
}

/**
 * Description dans la ligne (issue #32) : une ligne atténuée,
 * dépliable sur place (retours à la ligne préservés). Rien si elle est vide.
 */
@Composable
private fun Description(task: Task, ctx: RowContext) {
    if (task.description.isBlank()) return
    var open by rememberSaveable(task.id) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.disclosure(open, minHeight = false) { open = !open }.padding(vertical = 2.dp),
    ) {
        Icon(
            KairosIcons.Description,
            contentDescription = stringResource(Res.string.description_expand),
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            if (open) task.description.trim() else task.description.trim().lineSequence().joinToString(" "),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = if (open) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Badge neutre en pilule (charte § Badges). */
@Composable
internal fun Badge(
    text: String,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    bold: Boolean = false,
    icon: ImageVector? = null,
    /** Court libellé qui ne se coupe jamais (« 2 pts » à 360 dp se repliait lettre par lettre). */
    nowrap: Boolean = false,
) {
    Surface(color = container, contentColor = content, shape = CircleShape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = if (bold) FontWeight.Bold else null, softWrap = !nowrap, maxLines = if (nowrap) 1 else Int.MAX_VALUE)
        }
    }
}

/**
 * « À surveiller » : contour et icône, jamais d'ambre (indiscernable du miel,
 * charte § Pas d'ambre).
 */
@Composable
internal fun WarnBadge(text: String, icon: ImageVector = KairosIcons.Warning) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = CircleShape,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Échec ou dépassement réel (conflit d'heure fixe) : conteneur d'erreur. */
@Composable
internal fun ErrorBadge(text: String) {
    val scheme = MaterialTheme.colorScheme
    Badge(text, scheme.errorContainer, scheme.onErrorContainer, icon = KairosIcons.Warning)
}

@Composable
internal fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Priorité : seule P0 est rouge (conteneur d'erreur), P1/P2 neutres en gras ; un nom choisi remplace le code, pas la couleur. */
@Composable
internal fun PriorityBadge(priority: Int) {
    val scheme = MaterialTheme.colorScheme
    val text = Levels.priorityShort(priority)
    if (priority == 0) {
        Badge(text, scheme.errorContainer, scheme.onErrorContainer, bold = true)
    } else {
        Badge(text, content = scheme.onSurface, bold = true)
    }
}
