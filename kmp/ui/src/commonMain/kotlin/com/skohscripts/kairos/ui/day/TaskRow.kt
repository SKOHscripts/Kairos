package com.skohscripts.kairos.ui.day

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.skohscripts.kairos.core.model.TaskSpace
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
import com.skohscripts.kairos.ui.generated.resources.minutes_badge
import com.skohscripts.kairos.ui.generated.resources.points_badge
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
    val onEdit: (Task) -> Unit,
    /** Démarre le chrono sur la tâche, ou l'arrête si c'est elle qui tourne. */
    val onToggleTimer: (Task) -> Unit = {},
    val clock: Clock = Clock.System,
)

/**
 * Ligne de tâche en colonnes fixes (docs/spec/vue-jour.md § Ligne de
 * tâche, issue #33 de Kairos 2) : [coche] [corps : heure et titre,
 * étiquettes, description] [score, priorité, points] [décaler, modifier]. Le
 * corps grandit vers le bas, jamais vers la droite : les colonnes de droite
 * restent alignées d'une ligne à l'autre. En largeur étroite, la colonne
 * « clés » passe sous le corps ; coche et actions ne bougent pas.
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
        // docs/spec/vue-jour-gtd.md de Kairos 2, reprise telle quelle ; docs/spec/vue-jour.md).
        color = if (blocked) scheme.surface else scheme.surfaceContainerLow,
        border = if (blocked) BorderStroke(1.dp, scheme.outlineVariant) else null,
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
                    if (ctx.compact) KeyBadges(task, ctx)
                }
                if (!ctx.compact) {
                    Row(Modifier.padding(top = 10.dp, start = 8.dp)) { KeyBadges(task, ctx) }
                }
                if (task.status == TaskStatus.TODO) {
                    val running = ctx.view.running?.taskId == task.id
                    IconButton(onClick = { ctx.onToggleTimer(task) }) {
                        Icon(
                            if (running) KairosIcons.Stop else KairosIcons.PlayArrow,
                            contentDescription = stringResource(if (running) Res.string.action_timer_stop else Res.string.action_timer_start),
                            tint = if (running) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = { ctx.onSnooze(task) }) {
                        Icon(KairosIcons.Redo, contentDescription = stringResource(Res.string.action_snooze))
                    }
                }
                if (editable) {
                    IconButton(onClick = { ctx.onEdit(task) }) {
                        Icon(KairosIcons.Edit, contentDescription = stringResource(Res.string.action_edit))
                    }
                }
            }
            extra()
        }
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

/** Étiquettes de contexte (`task_tags` de Kairos 2) : projet, type, durée, dates, récurrence, ancienneté. */
@Composable
private fun TaskTags(task: Task, ctx: RowContext) {
    // Tâche d'équipe assignée à « moi » (seule façon d'en voir une dans l'espace Perso).
    if (task.space == TaskSpace.TEAM) TeamMark()
    // Tâche reçue d'un manager par paquet (docs/spec/equipe-echanges.md) : « de Corentin », ou « retirée par Corentin ».
    ReceivedTasks.originOf(task)?.let { ReceivedMark(it, task.originRemoved) }
    if (task.projectTag.isNotEmpty()) Badge(task.projectTag)
    if (task.taskType.isNotEmpty()) Badge(task.taskType)
    task.estimatedMinutes?.takeIf { it > 0 }?.let { Muted(stringResource(Res.string.minutes_badge, it)) }
    task.deadline?.let { Muted(stringResource(Res.string.deadline_badge, Dates.short(it, ctx.language))) }
    task.scheduledDate?.let { Muted(stringResource(Res.string.tag_scheduled, Dates.short(it, ctx.language))) }
    if (task.recurrence != TaskRecurrence.NONE) {
        Icon(
            KairosIcons.Repeat,
            contentDescription = stringResource(Res.string.tag_recurring),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
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
 * Temps passé (`time_spent` de Kairos 2) : minuteur vivant si le chrono
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
        estimate == null -> Badge(stringResource(Res.string.minutes_badge, spent))
        spent > estimate -> Badge(stringResource(Res.string.spent_of_estimate, spent, estimate), scheme.errorContainer, scheme.onErrorContainer)
        else -> Badge(stringResource(Res.string.spent_of_estimate, spent, estimate))
    }
}

/** Signaux de tri (`task_key_badges`) : score (s'ouvre sur son explication), priorité, points. */
@Composable
private fun KeyBadges(task: Task, ctx: RowContext) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (task.status == TaskStatus.TODO) ctx.view.why[task.id]?.let { ScoreBadge(task, it, ctx.language) }
        task.priority?.let { PriorityBadge(it) }
        task.fibonacciPoints?.let { Badge(stringResource(Res.string.points_badge, it)) }
    }
}

/**
 * Description dans la ligne (issue #32 de Kairos 2) : une ligne atténuée,
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
) {
    Surface(color = container, contentColor = content, shape = CircleShape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = if (bold) FontWeight.Bold else null)
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

/** Priorité : seule P0 est rouge (conteneur d'erreur), P1/P2 neutres en gras. */
@Composable
internal fun PriorityBadge(priority: Int) {
    val scheme = MaterialTheme.colorScheme
    if (priority == 0) {
        Badge("P0", scheme.errorContainer, scheme.onErrorContainer, bold = true)
    } else {
        Badge("P$priority", content = scheme.onSurface, bold = true)
    }
}
