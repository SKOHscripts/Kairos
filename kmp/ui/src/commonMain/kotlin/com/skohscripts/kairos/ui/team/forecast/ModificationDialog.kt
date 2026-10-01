package com.skohscripts.kairos.ui.team.forecast

import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.FIBONACCI_SCALE
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import com.skohscripts.kairos.core.team.MemberForm
import com.skohscripts.kairos.core.team.forecast.Scenario
import com.skohscripts.kairos.core.team.forecast.ScenarioModification
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.action_ok
import com.skohscripts.kairos.ui.generated.resources.assign_backlog
import com.skohscripts.kairos.ui.generated.resources.backlog_no_category
import com.skohscripts.kairos.ui.generated.resources.mod_none
import com.skohscripts.kairos.ui.generated.resources.mod_field_absence_end
import com.skohscripts.kairos.ui.generated.resources.mod_field_absence_start
import com.skohscripts.kairos.ui.generated.resources.mod_field_availability
import com.skohscripts.kairos.ui.generated.resources.mod_field_category
import com.skohscripts.kairos.ui.generated.resources.mod_field_count
import com.skohscripts.kairos.ui.generated.resources.mod_field_date_pick
import com.skohscripts.kairos.ui.generated.resources.mod_field_deadline
import com.skohscripts.kairos.ui.generated.resources.mod_field_deadline_hint
import com.skohscripts.kairos.ui.generated.resources.mod_field_deadline_none
import com.skohscripts.kairos.ui.generated.resources.mod_field_focus
import com.skohscripts.kairos.ui.generated.resources.mod_field_hours
import com.skohscripts.kairos.ui.generated.resources.mod_field_member
import com.skohscripts.kairos.ui.generated.resources.mod_field_name
import com.skohscripts.kairos.ui.generated.resources.mod_field_points
import com.skohscripts.kairos.ui.generated.resources.mod_field_priority
import com.skohscripts.kairos.ui.generated.resources.mod_field_target
import com.skohscripts.kairos.ui.generated.resources.mod_field_task
import com.skohscripts.kairos.ui.generated.resources.mod_invalid_dates
import com.skohscripts.kairos.ui.generated.resources.mod_nothing_to_pick
import com.skohscripts.kairos.ui.generated.resources.points_badge
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Ce que le dialogue d'une modification propose de choisir : les membres désignables (membres actifs de la base,
 * puis membres hypothétiques ajoutés par le scénario), les tâches d'équipe à faire (par `teamUid`), les catégories,
 * et l'identifiant du prochain membre hypothétique (négatif, jamais en conflit avec la base).
 */
internal class EditorChoices(
    val members: List<Pair<Long, String>>,
    val tasks: List<Pair<String, String>>,
    val categories: List<String>,
    val nextTempId: Long,
    val today: LocalDate,
    val defaultHours: Double,
)

/**
 * Le dialogue d'ajout ou de modification d'une modification de scénario ([initial] nul : ajout) : un petit formulaire
 * par type (docs/spec/equipe-simulation.md § Interface), membres et tâches par menus, dates par le `DatePicker` Material 3.
 * « OK » n'est actif que si la modification est valide (mêmes bornes que `Scenario.apply`).
 */
@Composable
internal fun ModificationDialog(
    type: ModType,
    initial: ScenarioModification?,
    choices: EditorChoices,
    language: String,
    onConfirm: (ScenarioModification) -> Unit,
    onDismiss: () -> Unit,
) {
    // --- Un état par champ, initialisé depuis la modification éditée.
    var name by remember { mutableStateOf((initial as? ScenarioModification.AddMember)?.name.orEmpty()) }
    var percent by remember {
        mutableStateOf(
            when (initial) {
                is ScenarioModification.AddMember -> initial.availabilityPercent.toString()
                is ScenarioModification.SetAvailability -> initial.percent.toString()
                else -> "100"
            },
        )
    }
    var hours by remember { mutableStateOf(((initial as? ScenarioModification.AddMember)?.hoursPerDay ?: choices.defaultHours).let { decimalInput(it, language) }) }
    var memberId by remember {
        mutableStateOf(
            when (initial) {
                is ScenarioModification.RemoveMember -> initial.memberId
                is ScenarioModification.AddAbsence -> initial.memberId
                is ScenarioModification.SetAvailability -> initial.memberId
                else -> choices.members.firstOrNull()?.first
            },
        )
    }
    var taskUid by remember {
        mutableStateOf(
            when (initial) {
                is ScenarioModification.Reassign -> initial.taskUid
                is ScenarioModification.SetPriority -> initial.taskUid
                is ScenarioModification.SetDeadline -> initial.taskUid
                else -> choices.tasks.firstOrNull()?.first
            },
        )
    }
    var target by remember { mutableStateOf((initial as? ScenarioModification.Reassign)?.memberId) }
    var start by remember { mutableStateOf((initial as? ScenarioModification.AddAbsence)?.start) }
    var end by remember { mutableStateOf((initial as? ScenarioModification.AddAbsence)?.end) }
    var count by remember { mutableStateOf((initial as? ScenarioModification.AddTasks)?.count?.toString() ?: "1") }
    var points by remember { mutableStateOf((initial as? ScenarioModification.AddTasks)?.points) }
    var category by remember { mutableStateOf((initial as? ScenarioModification.AddTasks)?.category.orEmpty()) }
    var addPriority by remember { mutableStateOf((initial as? ScenarioModification.AddTasks)?.priority) }
    var priority by remember { mutableStateOf((initial as? ScenarioModification.SetPriority)?.priority ?: 1) }
    var deadline by remember { mutableStateOf((initial as? ScenarioModification.SetDeadline)?.date) }
    var focus by remember { mutableStateOf(((initial as? ScenarioModification.SetFocus)?.factor?.let { (it * 100).roundToInt() } ?: 80).toString()) }
    var picking by remember { mutableStateOf<String?>(null) }

    val built: ScenarioModification? = when (type) {
        ModType.ADD_MEMBER -> {
            val pct = percent.trim().toIntOrNull()
            val h = hours.trim().replace(',', '.').toDoubleOrNull()
            if (name.isNotBlank() && pct != null && pct in MemberForm.MIN_AVAILABILITY..MemberForm.MAX_AVAILABILITY && h != null && h in MemberForm.MIN_HOURS..MemberForm.MAX_HOURS) {
                ScenarioModification.AddMember((initial as? ScenarioModification.AddMember)?.tempId ?: choices.nextTempId, name.trim(), pct, h)
            } else {
                null
            }
        }
        ModType.REMOVE_MEMBER -> memberId?.let { ScenarioModification.RemoveMember(it) }
        ModType.ADD_ABSENCE -> {
            val s = start
            val e = end
            if (memberId != null && s != null && e != null && MemberForm.isValidAbsence(s, e)) ScenarioModification.AddAbsence(memberId!!, s, e) else null
        }
        ModType.SET_AVAILABILITY -> {
            val pct = percent.trim().toIntOrNull()
            if (memberId != null && pct != null && pct in MemberForm.MIN_AVAILABILITY..MemberForm.MAX_AVAILABILITY) ScenarioModification.SetAvailability(memberId!!, pct) else null
        }
        ModType.REASSIGN -> taskUid?.let { ScenarioModification.Reassign(it, target) }
        ModType.ADD_TASKS -> {
            val n = count.trim().toIntOrNull()
            if (n != null && n in 1..Scenario.MAX_ADDED_TASKS) ScenarioModification.AddTasks(n, points, category, addPriority) else null
        }
        ModType.SET_PRIORITY -> taskUid?.let { ScenarioModification.SetPriority(it, priority) }
        ModType.SET_DEADLINE -> taskUid?.let { ScenarioModification.SetDeadline(it, deadline) }
        ModType.SET_FOCUS -> {
            val pct = focus.trim().toIntOrNull()
            if (pct != null && pct in 1..100) ScenarioModification.SetFocus(pct / 100.0) else null
        }
    }
    val datesInverted = type == ModType.ADD_ABSENCE && start != null && end != null && !MemberForm.isValidAbsence(start!!, end!!)
    val numberKeys = KeyboardOptions(keyboardType = KeyboardType.Number)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(type.title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val memberOptions = choices.members
                val memberLabel = memberOptions.firstOrNull { it.first == memberId }?.second.orEmpty()
                val taskOptions = choices.tasks
                val taskLabel = taskOptions.firstOrNull { it.first == taskUid }?.second.orEmpty()

                if (type == ModType.ADD_MEMBER) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.mod_field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        percent, { percent = it }, label = { Text(stringResource(Res.string.mod_field_availability)) }, singleLine = true,
                        keyboardOptions = numberKeys, isError = built == null && percent.trim().toIntOrNull() !in MemberForm.MIN_AVAILABILITY..MemberForm.MAX_AVAILABILITY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        hours, { hours = it }, label = { Text(stringResource(Res.string.mod_field_hours)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (type == ModType.REMOVE_MEMBER || type == ModType.ADD_ABSENCE || type == ModType.SET_AVAILABILITY) {
                    ChoiceField(stringResource(Res.string.mod_field_member), memberLabel, memberOptions, { memberId = it }, Modifier.fillMaxWidth())
                }
                if (type == ModType.SET_AVAILABILITY) {
                    OutlinedTextField(
                        percent, { percent = it }, label = { Text(stringResource(Res.string.mod_field_availability)) }, singleLine = true,
                        keyboardOptions = numberKeys, modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (type == ModType.ADD_ABSENCE) {
                    DateButton(stringResource(Res.string.mod_field_absence_start), start, language, choices.today) { picking = "start" }
                    DateButton(stringResource(Res.string.mod_field_absence_end), end, language, choices.today) { picking = "end" }
                    if (datesInverted) Text(stringResource(Res.string.mod_invalid_dates), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (type == ModType.REASSIGN || type == ModType.SET_PRIORITY || type == ModType.SET_DEADLINE) {
                    ChoiceField(stringResource(Res.string.mod_field_task), taskLabel, taskOptions, { taskUid = it }, Modifier.fillMaxWidth())
                }
                if (type == ModType.REASSIGN) {
                    val backlog = stringResource(Res.string.assign_backlog)
                    ChoiceField(
                        stringResource(Res.string.mod_field_target),
                        memberOptions.firstOrNull { it.first == target }?.second ?: backlog,
                        listOf<Pair<Long?, String>>(null to backlog) + memberOptions.map { it.first to it.second },
                        { target = it },
                        Modifier.fillMaxWidth(),
                    )
                }
                if (type == ModType.SET_PRIORITY) {
                    ChoiceField(stringResource(Res.string.mod_field_priority), "P$priority", PRIORITY_VALUES.map { it to "P$it" }, { priority = it }, Modifier.fillMaxWidth())
                }
                if (type == ModType.SET_DEADLINE) {
                    DateButton(stringResource(Res.string.mod_field_deadline), deadline, language, choices.today) { picking = "deadline" }
                    if (deadline != null) {
                        KairosTextButton(onClick = { deadline = null }) { Text(stringResource(Res.string.mod_field_deadline_none)) }
                    } else {
                        Text(stringResource(Res.string.mod_field_deadline_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (type == ModType.ADD_TASKS) {
                    OutlinedTextField(
                        count, { count = it }, label = { Text(stringResource(Res.string.mod_field_count)) }, singleLine = true,
                        keyboardOptions = numberKeys, isError = built == null, modifier = Modifier.fillMaxWidth(),
                    )
                    val none = stringResource(Res.string.mod_none)
                    ChoiceField(
                        stringResource(Res.string.mod_field_points), points?.let { stringResource(Res.string.points_badge, it) } ?: none,
                        listOf<Pair<Int?, String>>(null to none) + FIBONACCI_SCALE.map { it to stringResource(Res.string.points_badge, it) },
                        { points = it }, Modifier.fillMaxWidth(),
                    )
                    val noCategory = stringResource(Res.string.backlog_no_category)
                    ChoiceField(
                        stringResource(Res.string.mod_field_category), category.ifEmpty { noCategory },
                        listOf("" to noCategory) + choices.categories.map { it to it },
                        { category = it }, Modifier.fillMaxWidth(),
                    )
                    ChoiceField(
                        stringResource(Res.string.mod_field_priority), addPriority?.let { "P$it" } ?: none,
                        listOf<Pair<Int?, String>>(null to none) + PRIORITY_VALUES.map { it to "P$it" },
                        { addPriority = it }, Modifier.fillMaxWidth(),
                    )
                }
                if (type == ModType.SET_FOCUS) {
                    OutlinedTextField(
                        focus, { focus = it }, label = { Text(stringResource(Res.string.mod_field_focus)) }, singleLine = true,
                        keyboardOptions = numberKeys, isError = built == null, modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (built == null && choices.hasNothingToPick(type)) {
                    Text(stringResource(Res.string.mod_nothing_to_pick), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { KairosTextButton(enabled = built != null, onClick = { built?.let(onConfirm) }) { Text(stringResource(Res.string.action_ok)) } },
        dismissButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )

    when (picking) {
        "start" -> DateChoice(start ?: choices.today, onPick = { start = it; if (end != null && end!! < it) end = it; picking = null }, onDismiss = { picking = null })
        "end" -> DateChoice(end ?: start ?: choices.today, onPick = { end = it; picking = null }, onDismiss = { picking = null })
        "deadline" -> DateChoice(deadline ?: choices.today, onPick = { deadline = it; picking = null }, onDismiss = { picking = null })
    }
}

/** Le champ date : un bouton à contour qui montre la date (ou invite à la choisir) et ouvre le `DatePicker`. */
@Composable
private fun DateButton(label: String, value: LocalDate?, language: String, today: LocalDate, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        KairosOutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = KairosButtonIconPadding) {
            Icon(KairosIcons.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                value?.let { dateLabel(it, today, language) } ?: stringResource(Res.string.mod_field_date_pick),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Il n'y a rien à choisir pour ce type (aucun membre, aucune tâche) : le dialogue le dit au lieu de rester muet. */
private fun EditorChoices.hasNothingToPick(type: ModType): Boolean = when (type) {
    ModType.REMOVE_MEMBER, ModType.ADD_ABSENCE, ModType.SET_AVAILABILITY -> members.isEmpty()
    ModType.REASSIGN, ModType.SET_PRIORITY, ModType.SET_DEADLINE -> tasks.isEmpty()
    else -> false
}

/** « 7 » ou « 7,5 » pour un champ de saisie. */
private fun decimalInput(value: Double, language: String): String {
    val text = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    return if (language.lowercase().startsWith("en")) text else text.replace('.', ',')
}
