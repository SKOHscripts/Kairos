package com.skohscripts.kairos.ui.week

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
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.skohscripts.kairos.ui.generated.resources.backlog_title
import com.skohscripts.kairos.ui.generated.resources.week_block
import com.skohscripts.kairos.ui.generated.resources.week_next
import com.skohscripts.kairos.ui.generated.resources.week_nothing
import com.skohscripts.kairos.ui.generated.resources.week_prev
import com.skohscripts.kairos.ui.generated.resources.week_see_day
import com.skohscripts.kairos.ui.generated.resources.week_spent
import com.skohscripts.kairos.ui.generated.resources.week_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.navigation.NavState
import com.skohscripts.kairos.ui.theme.LocalKairosExtraColors
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Vue Semaine (docs/spec/vue-semaine.md) : navigation d'une semaine à
 * l'autre, temps réel de la semaine par type, recherche et filtres, backlog
 * (sans date, donc absent de la grille), puis la grille des sept jours ;
 * « Voir le détail » ouvre la vue Jour de ce jour-là.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekScreen(services: AppServices, nav: NavState, onOpenDay: (LocalDate) -> Unit) {
    val snapshot by services.repository.personalSnapshot.collectAsState()
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val now = services.clock.now()
    val today = now.toLocalDateTime(timeZone).date
    val language = Locale.current.language
    var filter by remember { mutableStateOf(DayFilter()) }
    var filterOpen by remember { mutableStateOf(false) }
    var backlogOpen by remember { mutableStateOf(false) }
    val week = remember(snapshot, nav.week, filter, today) { WeekView.build(snapshot, nav.week ?: today, now, timeZone, filter) }
    val backlog = remember(snapshot, filter, today) { DayView.build(snapshot, today, null, timeZone, filter, instant = now).backlog }
    val projects = remember(snapshot) { snapshot.tasks.map { it.projectTag }.filter { it.isNotEmpty() }.distinct().sorted() }

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 1400.dp).fillMaxWidth().padding(KairosSpacing.l), verticalArrangement = Arrangement.spacedBy(KairosSpacing.m)) {
            // Titre au-dessus des boutons : entre eux, il n'avait plus de place sur un
            // téléphone et s'affichait une lettre par ligne.
            Text(stringResource(Res.string.week_title, Dates.dayMonth(week.monday, language)), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                KairosOutlinedButton(onClick = { nav.week = week.monday.minus(DatePeriod(days = 7)) }, contentPadding = KairosButtonIconPadding) {
                    Icon(KairosIcons.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.week_prev), modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(Modifier.weight(1f))
                KairosOutlinedButton(onClick = { nav.week = week.monday.plus(DatePeriod(days = 7)) }) {
                    Text(stringResource(Res.string.week_next), modifier = Modifier.padding(end = 4.dp))
                    Icon(KairosIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            val parts = mutableListOf<String>()
            for ((type, minutes) in week.spentByType) parts += "$type ${duration(minutes)}"
            Text(
                stringResource(Res.string.week_spent, duration(week.spentMinutes)) + if (parts.isEmpty()) "" else " (${parts.joinToString(" · ")})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilterCard(
                filter, filterOpen || filter.active, { filterOpen = !(filterOpen || filter.active) }, { filter = it },
                projects, snapshot.settings.taskTypeList, remember { FocusRequester() }, LocalPlatform.current != Platform.ANDROID,
            )
            BacklogList(backlog.map { it.title }, backlogOpen) { backlogOpen = !backlogOpen }
            // Sept colonnes égales quand elles tiennent (140 dp chacune au moins),
            // sinon des cartes de 184 dp qui passent à la ligne (mobile).
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 140.dp * 7 + 8.dp * 6) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
                        week.days.forEach { day ->
                            DayCard(day, day.date == today, language, Modifier.weight(1f).fillMaxHeight()) { onOpenDay(day.date) }
                        }
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        week.days.forEach { day -> DayCard(day, day.date == today, language, Modifier.width(184.dp)) { onOpenDay(day.date) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun BacklogList(titles: List<String>, open: Boolean, onToggle: () -> Unit) {
    Column {
        KairosTextButton(onClick = onToggle, modifier = Modifier.expandedState(open), contentPadding = KairosButtonIconPadding) {
            Icon(if (open) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
            Text("${stringResource(Res.string.backlog_title)} (${titles.size})", modifier = Modifier.padding(start = 4.dp))
        }
        if (open) {
            Text(stringResource(Res.string.backlog_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            titles.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
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
private fun DayCard(day: WeekDay, isToday: Boolean, language: String, modifier: Modifier, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (isToday) scheme.secondaryContainer else scheme.surfaceContainerLow,
        border = if (isToday) BorderStroke(1.dp, scheme.secondary) else null,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Dates.dayShort(day.date, language), style = MaterialTheme.typography.titleSmall)
            day.blocks.forEach { b ->
                Badge(stringResource(Res.string.week_block, b.title, Dates.time(b.start, language), Dates.time(b.end, language)))
            }
            if (day.tasks.isEmpty() && day.done.isEmpty()) {
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
