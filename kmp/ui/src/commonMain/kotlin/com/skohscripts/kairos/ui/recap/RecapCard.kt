package com.skohscripts.kairos.ui.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.recap.Recap
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.app.expandedState
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.day.Badge
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.day.WarnBadge
import com.skohscripts.kairos.ui.day.duration
import com.skohscripts.kairos.ui.day.durationText
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.block_untitled
import com.skohscripts.kairos.ui.generated.resources.deadline_badge
import com.skohscripts.kairos.ui.generated.resources.file_error
import com.skohscripts.kairos.ui.generated.resources.day_back_today
import com.skohscripts.kairos.ui.generated.resources.recap_advanced
import com.skohscripts.kairos.ui.generated.resources.recap_by_type
import com.skohscripts.kairos.ui.generated.resources.recap_copied
import com.skohscripts.kairos.ui.generated.resources.recap_copy
import com.skohscripts.kairos.ui.generated.resources.recap_detail
import com.skohscripts.kairos.ui.generated.resources.recap_done
import com.skohscripts.kairos.ui.generated.resources.recap_empty
import com.skohscripts.kairos.ui.generated.resources.recap_hint
import com.skohscripts.kairos.ui.generated.resources.recap_kpi_done
import com.skohscripts.kairos.ui.generated.resources.recap_kpi_left
import com.skohscripts.kairos.ui.generated.resources.recap_kpi_slots
import com.skohscripts.kairos.ui.generated.resources.recap_kpi_tracked
import com.skohscripts.kairos.ui.generated.resources.recap_left
import com.skohscripts.kairos.ui.generated.resources.recap_slot_line
import com.skohscripts.kairos.ui.generated.resources.recap_slots
import com.skohscripts.kairos.ui.generated.resources.recap_text_title
import com.skohscripts.kairos.ui.generated.resources.recap_text_tracked
import com.skohscripts.kairos.ui.generated.resources.stats_throughput_row
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.stats.BarRow
import com.skohscripts.kairos.ui.stats.Muted
import com.skohscripts.kairos.ui.stats.Panel
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosTextButton
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * En-tête de période des vues Semaine et Mois (docs/spec/bilan.md) : « ‹ titre › » sur une ligne, et le retour à
 * aujourd'hui hors de la période courante ([onToday] nul sinon). Chevrons collés au titre, groupe centré : aux deux bords
 * d'une fenêtre large ils étaient à 1 100 dp l'un de l'autre. Boutons à icône : entre deux boutons à texte, le titre
 * n'avait plus de place à 360 dp.
 */
@Composable
internal fun PeriodHeader(title: String, previous: String, next: String, onPrevious: () -> Unit, onNext: () -> Unit, onToday: (() -> Unit)?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) { Icon(KairosIcons.ChevronLeft, contentDescription = previous) }
            Text(
                title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(horizontal = KairosSpacing.s).heading(),
            )
            IconButton(onClick = onNext) { Icon(KairosIcons.ChevronRight, contentDescription = next) }
        }
        if (onToday != null) KairosTextButton(onClick = onToday) { Text(stringResource(Res.string.day_back_today)) }
    }
}

/**
 * Carte « Bilan » (docs/spec/bilan.md, issue #233) : quatre chiffres toujours visibles, le détail à la demande (ouvert
 * d'office pour une période passée, dont le bilan est l'objet), et « Copier le bilan » pour le coller en réunion.
 * [heading] : la période telle que le texte copié la nomme (« Semaine du 09/11 ») ; [footer] : une ligne de plus sous
 * le détail (la semaine réaliste des prévisions).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecapCard(recap: Recap, title: String, heading: String, today: LocalDate, language: String, footer: (@Composable () -> Unit)? = null) {
    Panel(KairosIcons.Checklist, title, stringResource(Res.string.recap_hint)) {
        if (recap.empty) {
            Muted(stringResource(Res.string.recap_empty))
            footer?.invoke()
            return@Panel
        }
        var open by remember(recap.start) { mutableStateOf(recap.end < today) }
        val clipboard = LocalClipboardManager.current
        val messages = LocalMessages.current
        val scope = rememberCoroutineScope()
        // Quatre chiffres sans tuile : quatre tuiles des Stats empilées prenaient 400 dp de haut sur un téléphone.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(KairosSpacing.m),
                verticalArrangement = Arrangement.spacedBy(KairosSpacing.s),
                maxItemsInEachRow = if (maxWidth >= 560.dp) 4 else 2,
            ) {
                val cell = Modifier.weight(1f)
                val done = if (recap.points > 0) stringResource(Res.string.stats_throughput_row, recap.done.size, recap.points) else recap.done.size.toString()
                Figure(done, stringResource(Res.string.recap_kpi_done), cell)
                Figure(duration(recap.spentMinutes), stringResource(Res.string.recap_kpi_tracked), cell)
                Figure(duration(recap.slotMinutes), stringResource(Res.string.recap_kpi_slots), cell)
                Figure(recap.left.size.toString(), stringResource(Res.string.recap_kpi_left), cell, warn = recap.late > 0)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
            KairosTextButton(onClick = { open = !open }, modifier = Modifier.expandedState(open), contentPadding = KairosButtonIconPadding) {
                Icon(if (open) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
                Text(stringResource(Res.string.recap_detail), modifier = Modifier.padding(start = 4.dp))
            }
            KairosTextButton(onClick = {
                scope.launch {
                    val text = recapText(recap, heading, today, language)
                    // Presse-papiers indisponible (session sans écran) : le message le dit au lieu de quitter.
                    val copied = runCatching { clipboard.setText(AnnotatedString(text)) }.isSuccess
                    messages(getString(if (copied) Res.string.recap_copied else Res.string.file_error))
                }
            }, contentPadding = KairosButtonIconPadding) {
                Icon(KairosIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.recap_copy), modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (open) {
            Section(stringResource(Res.string.recap_done), recap.done.size) {
                recap.done.forEach { RecapLine(it.task.title, it.task.projectTag, if (it.minutes > 0) duration(it.minutes) else null) }
            }
            Section(stringResource(Res.string.recap_advanced), recap.advanced.size) {
                recap.advanced.forEach { RecapLine(it.task.title, it.task.projectTag, duration(it.minutes)) }
            }
            Section(stringResource(Res.string.recap_slots), recap.slots.size) {
                recap.slots.forEach { RecapLine(slotTitle(it), "", duration(it.minutes)) }
            }
            Section(stringResource(Res.string.recap_left), recap.left.size) {
                recap.left.forEach { task ->
                    RecapLine(task.title, task.projectTag, null) {
                        val due = stringResource(Res.string.deadline_badge, Dates.short(task.deadline!!, language, today))
                        if (task.deadline!! < today) WarnBadge(due) else Badge(due)
                    }
                }
            }
            Section(stringResource(Res.string.recap_by_type), recap.spentByType.size, counted = false) {
                val max = recap.spentByType.values.maxOrNull() ?: 0
                recap.spentByType.forEach { (type, minutes) -> BarRow(type, if (max > 0) minutes.toFloat() / max else 0f, duration(minutes)) }
            }
        }
        footer?.invoke()
    }
}

/** Un chiffre du bilan : la valeur, son libellé dessous ; [warn] ajoute l'icône d'avertissement (jamais la couleur seule). */
@Composable
internal fun Figure(value: String, label: String, modifier: Modifier, warn: Boolean = false) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (warn) Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Section du détail, omise si elle est vide ; le titre porte l'effectif (« Fait (3) »). */
@Composable
internal fun Section(title: String, count: Int, counted: Boolean = true, content: @Composable () -> Unit) {
    if (count == 0) return
    Column(verticalArrangement = Arrangement.spacedBy(KairosSpacing.xs), modifier = Modifier.padding(top = KairosSpacing.s)) {
        Text(if (counted) "$title ($count)" else title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.heading())
        content()
    }
}

/** Une ligne du détail : le titre (coupé), le projet en pastille, puis la durée ou ce que [trailing] pose à droite. */
@Composable
private fun RecapLine(title: String, project: String, time: String?, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KairosSpacing.s), modifier = Modifier.fillMaxWidth()) {
        // Titre et projet prennent la place ; la durée reste au bord droit, alignée d'une ligne à l'autre.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KairosSpacing.s), modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (project.isNotEmpty()) Badge(project)
        }
        if (time != null) Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        trailing?.invoke()
    }
}

@Composable
private fun slotTitle(slot: Recap.Slot): String {
    val title = slot.title.ifEmpty { stringResource(Res.string.block_untitled) }
    return if (slot.count > 1) stringResource(Res.string.recap_slot_line, title, slot.count) else title
}

/** Le bilan en texte brut pour le presse-papiers : mêmes sections que la carte, en listes à tirets. */
internal suspend fun recapText(recap: Recap, heading: String, today: LocalDate, language: String): String {
    val out = StringBuilder(getString(Res.string.recap_text_title, heading)).append('\n')
    suspend fun section(title: org.jetbrains.compose.resources.StringResource, lines: List<String>) {
        if (lines.isEmpty()) return
        out.append('\n').append(getString(title)).append(" (").append(lines.size).append(")\n")
        lines.forEach { out.append("- ").append(it).append('\n') }
    }
    suspend fun line(item: Recap.Line) = buildString {
        append(item.task.title)
        if (item.task.projectTag.isNotEmpty()) append(" [").append(item.task.projectTag).append(']')
        if (item.minutes > 0) append(" · ").append(durationText(item.minutes))
    }
    section(Res.string.recap_done, recap.done.map { line(it) })
    section(Res.string.recap_advanced, recap.advanced.map { line(it) })
    section(Res.string.recap_slots, recap.slots.map { slot ->
        val title = slot.title.ifEmpty { getString(Res.string.block_untitled) }
        (if (slot.count > 1) getString(Res.string.recap_slot_line, title, slot.count) else title) + " · " + durationText(slot.minutes)
    })
    section(Res.string.recap_left, recap.left.map { "${it.title} · ${getString(Res.string.deadline_badge, Dates.short(it.deadline!!, language, today))}" })
    if (recap.spentMinutes > 0) {
        val types = recap.spentByType.map { (type, minutes) -> "$type ${durationText(minutes)}" }
        out.append('\n').append(getString(Res.string.recap_text_tracked, durationText(recap.spentMinutes)))
        if (types.isNotEmpty()) out.append(" (").append(types.joinToString(" · ")).append(')')
        out.append('\n')
    }
    return out.toString().trimEnd()
}
