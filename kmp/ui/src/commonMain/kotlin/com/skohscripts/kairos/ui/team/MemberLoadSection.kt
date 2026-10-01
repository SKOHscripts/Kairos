package com.skohscripts.kairos.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.team.LoadPlan
import com.skohscripts.kairos.core.team.MemberLoad
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.load_bar_value_plain
import com.skohscripts.kairos.ui.generated.resources.load_categories_title
import com.skohscripts.kairos.ui.generated.resources.load_computing
import com.skohscripts.kairos.ui.generated.resources.load_plan_note
import com.skohscripts.kairos.ui.generated.resources.load_task_at_risk
import com.skohscripts.kairos.ui.generated.resources.load_task_blocked
import com.skohscripts.kairos.ui.generated.resources.load_task_due
import com.skohscripts.kairos.ui.generated.resources.load_task_line
import com.skohscripts.kairos.ui.generated.resources.load_task_out
import com.skohscripts.kairos.ui.generated.resources.load_task_planned
import com.skohscripts.kairos.ui.generated.resources.load_task_unestimated
import com.skohscripts.kairos.ui.generated.resources.member_load_capacity
import com.skohscripts.kairos.ui.generated.resources.member_load_tasks_empty
import com.skohscripts.kairos.ui.generated.resources.member_load_tasks_title
import com.skohscripts.kairos.ui.generated.resources.member_load_title
import com.skohscripts.kairos.ui.generated.resources.member_load_wait
import com.skohscripts.kairos.ui.generated.resources.member_load_week
import com.skohscripts.kairos.ui.generated.resources.member_load_weeks_title
import com.skohscripts.kairos.ui.stats.BarRow
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.min

/**
 * Ce que la fiche d'un membre sait de sa charge : son [load] (`null` tant que le calcul n'est pas arrivé ou si le
 * membre n'est pas actif), l'horizon [weeks] sur lequel il a été calculé, les [tasks] du plan par identifiant (pour
 * en écrire le titre et l'échéance) et [computing] (un calcul est en route).
 */
class MemberLoadView(val load: MemberLoad?, val weeks: Int, val tasks: Map<Long, Task>, val computing: Boolean)

/**
 * Section « Charge » de la fiche d'un membre (docs/spec/equipe-charge.md § Charge individuelle) : sa capacité sur
 * l'horizon, la barre de charge, la charge **par semaine** (capacité contre heures), **par catégorie**, ses tâches
 * ouvertes dans l'ordre du plan avec leur date de fin prévue, et les heures d'attente. Plan sans aléa, et la
 * section le dit.
 */
@Composable
internal fun MemberLoadSection(view: MemberLoadView, today: LocalDate) {
    val language = Locale.current.language
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.member_load_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
        Text(stringResource(Res.string.load_plan_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val load = view.load
        if (load == null) {
            if (view.computing) Text(stringResource(Res.string.load_computing), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        val plan = load.plan
        Text(
            stringResource(Res.string.member_load_capacity, hoursText(load.capacityHours), weeksText(view.weeks)),
            style = MaterialTheme.typography.bodyLarge,
        )
        LoadBar(load.loadHours, load.capacityHours, load.ratePercent, load.level)
        if (load.unestimatedCount > 0) Flag(unestimatedText(load.unestimatedCount), flagged = true)

        // --- Par semaine : une barre par semaine de l'horizon, sur une échelle commune ; le repère est la capacité.
        SubTitle(stringResource(Res.string.member_load_weeks_title))
        val scale = plan.weeks.maxOfOrNull { max(it.hours, it.capacityHours) }?.takeIf { it > 0.0 } ?: 1.0
        plan.weeks.forEach { w ->
            val over = w.hours > w.capacityHours + 1e-9
            BarRow(
                label = stringResource(Res.string.member_load_week, Dates.short(w.start, language)),
                fraction = (min(w.hours, w.capacityHours) / scale).toFloat(),
                value = stringResource(Res.string.load_bar_value_plain, hoursText(w.hours), hoursText(w.capacityHours)),
                warn = over,
                overflow = if (over) ((w.hours - w.capacityHours) / scale).toFloat() else 0f,
                mark = (w.capacityHours / scale).toFloat(),
                stackedWhenNarrow = true,
            )
        }

        // --- Par catégorie : sa charge, en part de sa capacité.
        if (plan.byCategory.isNotEmpty()) {
            SubTitle(stringResource(Res.string.load_categories_title))
            val rows = plan.byCategory.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            val capacity = load.capacityHours
            val categoryScale = if (capacity > 0.0) max(1.0, rows.maxOf { it.value } / capacity) else 1.0
            rows.forEach { (category, hours) ->
                val share = if (capacity > 0.0) hours / capacity else null
                BarRow(
                    label = categoryName(category),
                    fraction = if (share == null) 0f else (share / categoryScale).toFloat(),
                    value = if (share == null) hoursText(hours) else "${hoursText(hours)} · ${percentText(100.0 * share)}",
                    stackedWhenNarrow = true,
                )
            }
        }

        // --- Ses tâches ouvertes, dans l'ordre du plan.
        SubTitle(stringResource(Res.string.member_load_tasks_title))
        if (plan.tasks.isEmpty()) Text(stringResource(Res.string.member_load_tasks_empty), style = MaterialTheme.typography.bodyMedium)
        plan.tasks.forEach { planned -> PlannedTaskRow(view.tasks[planned.taskId], planned, today, language) }

        if (plan.waitHours > 0.001) {
            Text(
                stringResource(Res.string.member_load_wait, hoursText(plan.waitHours)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SubTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp).heading())
}

/**
 * Une tâche du plan : son titre, puis « 6 h · Fin prévue le 14 oct., échéance le 10 oct. ». Une tâche en danger (fin
 * prévue après l'échéance) met cette ligne dans un contour avec l'icône `Warning` ; « Hors horizon » et « Attend le
 * backlog » disent pourquoi il n'y a pas de date ; une tâche non estimée porte sa pastille « non estimée ».
 */
@Composable
private fun PlannedTaskRow(task: Task?, planned: LoadPlan.PlannedTask, today: LocalDate, language: String) {
    val whenText = when (planned.placement) {
        LoadPlan.Placement.PLANNED ->
            planned.end?.let { stringResource(Res.string.load_task_planned, dateSpan(it, it, today, language).first) }.orEmpty()
        LoadPlan.Placement.OUT_OF_HORIZON -> stringResource(Res.string.load_task_out)
        LoadPlan.Placement.BLOCKED_BY_BACKLOG -> stringResource(Res.string.load_task_blocked)
    }
    val deadline = task?.deadline
    val withDue = if (deadline == null) whenText else stringResource(Res.string.load_task_due, whenText, dateSpan(deadline, deadline, today, language).first)
    val line = if (planned.unestimated) withDue else stringResource(Res.string.load_task_line, hoursText(planned.hours), withDue)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 4.dp),
    ) {
        Text(task?.title.orEmpty(), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Flag(line, flagged = planned.late, description = stringResource(Res.string.load_task_at_risk))
        }
        if (planned.unestimated) Flag(stringResource(Res.string.load_task_unestimated), flagged = true)
    }
}
