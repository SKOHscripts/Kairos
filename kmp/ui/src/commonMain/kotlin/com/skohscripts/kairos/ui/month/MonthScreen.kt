package com.skohscripts.kairos.ui.month

import com.skohscripts.kairos.ui.app.withDeviceCalendars
import com.skohscripts.kairos.ui.day.markTitle
import com.skohscripts.kairos.ui.generated.resources.day_marks_description
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.month.MonthDay
import com.skohscripts.kairos.core.month.MonthView
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.uiLanguage
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.day.duration
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.month_day_desc
import com.skohscripts.kairos.ui.generated.resources.month_deadline_line
import com.skohscripts.kairos.ui.generated.resources.month_deadlines_title
import com.skohscripts.kairos.ui.generated.resources.month_desc_day_off
import com.skohscripts.kairos.ui.generated.resources.month_desc_holiday
import com.skohscripts.kairos.ui.generated.resources.month_desc_overloaded
import com.skohscripts.kairos.ui.generated.resources.month_legend
import com.skohscripts.kairos.ui.generated.resources.month_next
import com.skohscripts.kairos.ui.generated.resources.month_nothing
import com.skohscripts.kairos.ui.generated.resources.month_offdays_title
import com.skohscripts.kairos.ui.generated.resources.month_overloaded_line
import com.skohscripts.kairos.ui.generated.resources.month_overloaded_title
import com.skohscripts.kairos.ui.generated.resources.month_prev
import com.skohscripts.kairos.ui.generated.resources.week_move_day_off
import com.skohscripts.kairos.ui.generated.resources.week_move_holiday
import com.skohscripts.kairos.ui.generated.resources.week_see_day
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.navigation.NavState
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.week.WeekMonthSwitch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import com.skohscripts.kairos.core.recap.Recap
import com.skohscripts.kairos.ui.recap.PeriodHeader
import com.skohscripts.kairos.ui.recap.RecapCard
import com.skohscripts.kairos.ui.generated.resources.recap_title_month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Vue Mois (docs/spec/vue-mois.md) : la destination Semaine en mode « Mois ». Grille du lundi au dimanche ; chaque jour
 * dit le nombre de tâches prévues, une échéance (drapeau), une surcharge (avertissement) et un férié ou un jour libre
 * (icône), jamais par la couleur seule ; toucher un jour ouvre la vue Jour. Sous la grille, la même information en
 * listes (échéances, jours trop chargés, fériés et jours libres) : l'alternative textuelle de la grille.
 */
@Composable
fun MonthScreen(services: AppServices, nav: NavState, onOpenDay: (LocalDate) -> Unit) {
    val stored by services.repository.personalSnapshot.collectAsState()
    // Calendriers du téléphone (Android) : ajoutés pour le calcul, jamais écrits (docs/spec/connexions-externes.md).
    val snapshot = withDeviceCalendars(services, stored)
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val now = services.clock.now()
    val today = now.toLocalDateTime(timeZone).date
    val language = uiLanguage()
    val month = remember(snapshot, nav.month, today) { MonthView.build(snapshot, nav.month ?: today) }

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 1000.dp).fillMaxWidth().padding(KairosSpacing.l), verticalArrangement = Arrangement.spacedBy(KairosSpacing.m)) {
            WeekMonthSwitch(nav, today)
            val last = MonthView.shift(month.first, 1).minus(DatePeriod(days = 1))
            val title = Dates.monthYear(month.first, language)
            PeriodHeader(
                title, stringResource(Res.string.month_prev), stringResource(Res.string.month_next),
                onPrevious = { nav.month = MonthView.shift(month.first, -1) },
                onNext = { nav.month = MonthView.shift(month.first, 1) },
                onToday = if (today in month.first..last) null else ({ nav.month = null }),
            )
            // Bilan du mois (docs/spec/bilan.md, issue #233).
            val recap = remember(snapshot, month.first, today) { Recap.build(snapshot, month.first, last, now, timeZone) }
            RecapCard(recap, stringResource(Res.string.recap_title_month), title, today, language)
            Text(stringResource(Res.string.month_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MonthGrid(month, today, language, onOpenDay)
            MonthLists(month, today, language, onOpenDay)
        }
    }
}

/** En-tête des jours de la semaine, puis une ligne par semaine ; sept colonnes égales, de 46 dp à 360 dp. */
@Composable
private fun MonthGrid(month: MonthView, today: LocalDate, language: String, onOpenDay: (LocalDate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // L'en-tête est décoratif pour le lecteur d'écran : chaque case dit déjà son jour en entier.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clearAndSetSemantics { }) {
            (0..6).forEach { weekday ->
                Text(
                    Dates.weekdayAbbrev(weekday, language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        month.weeks.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayCell(day, day.date == today, language, Modifier.weight(1f).fillMaxHeight()) { onOpenDay(day.date) }
                    }
                }
            }
        }
    }
}

/** Ce que le lecteur d'écran dit d'une case : « mardi 10 novembre 2026. Tâches prévues : 3. Échéances : 1. Trop chargé. » */
@Composable
private fun dayDescription(day: MonthDay, language: String): String {
    val parts = mutableListOf(stringResource(Res.string.month_day_desc, Dates.long(day.date, language), day.planned.size, day.deadlines.size))
    if (day.overloaded) parts += stringResource(Res.string.month_desc_overloaded)
    if (day.holiday) parts += stringResource(Res.string.month_desc_holiday)
    if (day.dayOff) parts += stringResource(Res.string.month_desc_day_off)
    if (day.marks.isNotEmpty()) parts += stringResource(Res.string.day_marks_description, day.marks.map { markTitle(it) }.joinToString(", "))
    return parts.joinToString(" ")
}

/**
 * Case d'un jour : numéro, nombre de tâches prévues, drapeau d'échéance, avertissement de surcharge (rouge : un
 * dépassement), icône de férié ou de jour libre. 48 dp de haut au moins ; aujourd'hui en conteneur secondaire et contour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayCell(day: MonthDay, isToday: Boolean, language: String, modifier: Modifier, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val description = dayDescription(day, language)
    val openLabel = stringResource(Res.string.week_see_day)
    val off = day.holiday || day.dayOff
    Surface(
        color = when {
            isToday -> scheme.secondaryContainer
            off -> scheme.surfaceContainerHighest
            else -> scheme.surfaceContainerLow
        },
        border = if (isToday) BorderStroke(1.dp, scheme.secondary) else null,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(openLabel) { onOpen(); true }
            },
    ) {
        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                day.date.day.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.Bold else null,
                color = if (off) scheme.onSurfaceVariant else scheme.onSurface,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (day.planned.isNotEmpty()) {
                    Text(day.planned.size.toString(), style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Bold)
                }
                if (day.deadlines.isNotEmpty()) Icon(KairosIcons.Flag, contentDescription = null, modifier = Modifier.size(14.dp), tint = scheme.onSurfaceVariant)
                if (day.overloaded) Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = scheme.error)
                if (off) Icon(KairosIcons.EventBusy, contentDescription = null, modifier = Modifier.size(14.dp), tint = scheme.onSurfaceVariant)
                // Journée entière d'un calendrier importé (issue #136) : les titres sont dans la description de la case.
                if (day.marks.isNotEmpty()) Icon(KairosIcons.Event, contentDescription = null, modifier = Modifier.size(14.dp), tint = scheme.onSurfaceVariant)
            }
        }
    }
}

/** Les listes sous la grille : échéances, jours trop chargés, fériés et jours libres ; chaque ligne ouvre son jour. */
@Composable
private fun MonthLists(month: MonthView, today: LocalDate, language: String, onOpenDay: (LocalDate) -> Unit) {
    val deadlines = month.days.filter { it.deadlines.isNotEmpty() }
    val overloaded = month.days.filter { it.overloaded }
    val off = month.days.filter { it.holiday || it.dayOff }
    if (deadlines.isEmpty() && overloaded.isEmpty() && month.days.none { it.planned.isNotEmpty() }) {
        Text(stringResource(Res.string.month_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (deadlines.isNotEmpty()) {
        Section(stringResource(Res.string.month_deadlines_title)) {
            deadlines.forEach { day ->
                Line(stringResource(Res.string.month_deadline_line, Dates.short(day.date, language, today), day.deadlines.joinToString(" · ") { it.title })) { onOpenDay(day.date) }
            }
        }
    }
    if (overloaded.isNotEmpty()) {
        Section(stringResource(Res.string.month_overloaded_title)) {
            overloaded.forEach { day ->
                Line(stringResource(Res.string.month_overloaded_line, Dates.dayShort(day.date, language), duration(day.plannedMinutes), duration(day.capacityMinutes))) {
                    onOpenDay(day.date)
                }
            }
        }
    }
    if (off.isNotEmpty()) {
        Section(stringResource(Res.string.month_offdays_title)) {
            off.forEach { day ->
                val name = Dates.dayShort(day.date, language)
                Line(stringResource(if (day.holiday) Res.string.week_move_holiday else Res.string.week_move_day_off, name)) { onOpenDay(day.date) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = KairosSpacing.s).semantics { heading() })
        content()
    }
}

/** Une ligne de liste qui ouvre son jour : toute la ligne, 48 dp de haut. */
@Composable
private fun Line(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = stringResource(Res.string.week_see_day), onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
