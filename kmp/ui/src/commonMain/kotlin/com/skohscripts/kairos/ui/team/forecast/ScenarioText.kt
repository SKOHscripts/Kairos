package com.skohscripts.kairos.ui.team.forecast

import androidx.compose.runtime.Composable
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.team.forecast.Scenario
import com.skohscripts.kairos.core.team.forecast.ScenarioModification
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.absence_range
import com.skohscripts.kairos.ui.generated.resources.forecast_task_gone
import com.skohscripts.kairos.ui.generated.resources.member_unknown
import com.skohscripts.kairos.ui.generated.resources.mod_add_absence
import com.skohscripts.kairos.ui.generated.resources.mod_add_member
import com.skohscripts.kairos.ui.generated.resources.mod_add_tasks_many
import com.skohscripts.kairos.ui.generated.resources.mod_add_tasks_one
import com.skohscripts.kairos.ui.generated.resources.mod_clear_deadline
import com.skohscripts.kairos.ui.generated.resources.mod_hypothetical_task
import com.skohscripts.kairos.ui.generated.resources.mod_reassign
import com.skohscripts.kairos.ui.generated.resources.mod_reassign_backlog
import com.skohscripts.kairos.ui.generated.resources.mod_remove_member
import com.skohscripts.kairos.ui.generated.resources.mod_set_availability
import com.skohscripts.kairos.ui.generated.resources.mod_set_deadline
import com.skohscripts.kairos.ui.generated.resources.mod_set_focus
import com.skohscripts.kairos.ui.generated.resources.mod_set_priority
import com.skohscripts.kairos.ui.generated.resources.mod_skip_already_applied
import com.skohscripts.kairos.ui.generated.resources.mod_skip_invalid
import com.skohscripts.kairos.ui.generated.resources.mod_skip_member_archived
import com.skohscripts.kairos.ui.generated.resources.mod_skip_member_not_found
import com.skohscripts.kairos.ui.generated.resources.mod_skip_task_not_found
import com.skohscripts.kairos.ui.generated.resources.mod_skip_task_not_open
import com.skohscripts.kairos.ui.generated.resources.mod_type_add_absence
import com.skohscripts.kairos.ui.generated.resources.mod_type_add_member
import com.skohscripts.kairos.ui.generated.resources.mod_type_add_tasks
import com.skohscripts.kairos.ui.generated.resources.mod_type_reassign
import com.skohscripts.kairos.ui.generated.resources.mod_type_remove_member
import com.skohscripts.kairos.ui.generated.resources.mod_type_set_availability
import com.skohscripts.kairos.ui.generated.resources.mod_type_set_deadline
import com.skohscripts.kairos.ui.generated.resources.mod_type_set_focus
import com.skohscripts.kairos.ui.generated.resources.mod_type_set_priority
import com.skohscripts.kairos.ui.generated.resources.points_badge
import com.skohscripts.kairos.ui.team.categoryName
import com.skohscripts.kairos.ui.team.dateSpan
import com.skohscripts.kairos.ui.team.decimalText
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/*
 * Les modifications d'un scénario en phrases lisibles (docs/spec/equipe-simulation.md § Scénarios) : le même texte
 * sert à la liste de l'éditeur, au dialogue « Appliquer » et au message de fin.
 */

/** Les neuf types de modification, dans l'ordre du menu « Ajouter une modification ». */
internal enum class ModType(val title: StringResource) {
    ADD_MEMBER(Res.string.mod_type_add_member),
    REMOVE_MEMBER(Res.string.mod_type_remove_member),
    ADD_ABSENCE(Res.string.mod_type_add_absence),
    SET_AVAILABILITY(Res.string.mod_type_set_availability),
    REASSIGN(Res.string.mod_type_reassign),
    ADD_TASKS(Res.string.mod_type_add_tasks),
    SET_PRIORITY(Res.string.mod_type_set_priority),
    SET_DEADLINE(Res.string.mod_type_set_deadline),
    SET_FOCUS(Res.string.mod_type_set_focus),
}

internal fun ScenarioModification.type(): ModType = when (this) {
    is ScenarioModification.AddMember -> ModType.ADD_MEMBER
    is ScenarioModification.RemoveMember -> ModType.REMOVE_MEMBER
    is ScenarioModification.AddAbsence -> ModType.ADD_ABSENCE
    is ScenarioModification.SetAvailability -> ModType.SET_AVAILABILITY
    is ScenarioModification.Reassign -> ModType.REASSIGN
    is ScenarioModification.AddTasks -> ModType.ADD_TASKS
    is ScenarioModification.SetPriority -> ModType.SET_PRIORITY
    is ScenarioModification.SetDeadline -> ModType.SET_DEADLINE
    is ScenarioModification.SetFocus -> ModType.SET_FOCUS
}

/**
 * Les noms qu'une phrase de scénario cite : les membres de la base (archivés compris) et les membres hypothétiques
 * que le scénario ajoute lui-même ([extra]), les tâches d'équipe par leur `teamUid`.
 */
internal class ScenarioNames(snapshot: KairosSnapshot, extra: List<ScenarioModification> = emptyList()) {
    private val members: Map<Long, String> =
        snapshot.members.associate { it.id to it.name } + extra.filterIsInstance<ScenarioModification.AddMember>().associate { it.tempId to it.name }
    private val tasks: Map<String, String> =
        snapshot.tasks.filter { it.space == TaskSpace.TEAM && it.teamUid != null }.associate { it.teamUid!! to it.title }

    @Composable
    fun member(id: Long): String = members[id] ?: stringResource(Res.string.member_unknown)

    @Composable
    fun task(uid: String): String = when {
        Scenario.isHypotheticalTask(uid) -> stringResource(Res.string.mod_hypothetical_task)
        else -> tasks[uid] ?: stringResource(Res.string.forecast_task_gone)
    }
}

/** La phrase d'une modification : « Absence de Sam : 12 au 16 oct. », « Réaffecter « Migration » à Alex »… */
@Composable
internal fun modificationText(mod: ScenarioModification, names: ScenarioNames, today: LocalDate, language: String): String = when (mod) {
    is ScenarioModification.AddMember -> stringResource(
        Res.string.mod_add_member, mod.name, percentLabel(mod.availabilityPercent), decimalText(mod.hoursPerDay, language),
    )
    is ScenarioModification.RemoveMember -> stringResource(Res.string.mod_remove_member, names.member(mod.memberId))
    is ScenarioModification.AddAbsence -> {
        val span = dateSpan(mod.start, mod.end, today, language)
        val dates = if (span.second == null) span.first else stringResource(Res.string.absence_range, span.first, span.second)
        stringResource(Res.string.mod_add_absence, names.member(mod.memberId), dates)
    }
    is ScenarioModification.SetAvailability -> stringResource(Res.string.mod_set_availability, names.member(mod.memberId), percentLabel(mod.percent))
    is ScenarioModification.Reassign ->
        if (mod.memberId == null) {
            stringResource(Res.string.mod_reassign_backlog, names.task(mod.taskUid))
        } else {
            stringResource(Res.string.mod_reassign, names.task(mod.taskUid), names.member(mod.memberId!!))
        }
    is ScenarioModification.AddTasks -> {
        val details = listOfNotNull(
            mod.points?.let { stringResource(Res.string.points_badge, it) },
            mod.category.takeIf { it.isNotEmpty() }?.let { categoryName(it) },
            mod.priority?.let { "P$it" },
        )
        val suffix = if (details.isEmpty()) "" else details.joinToString(", ", prefix = " (", postfix = ")")
        if (mod.count == 1) stringResource(Res.string.mod_add_tasks_one, suffix) else stringResource(Res.string.mod_add_tasks_many, mod.count, suffix)
    }
    is ScenarioModification.SetPriority -> stringResource(Res.string.mod_set_priority, names.task(mod.taskUid), "P${mod.priority}")
    is ScenarioModification.SetDeadline ->
        if (mod.date == null) {
            stringResource(Res.string.mod_clear_deadline, names.task(mod.taskUid))
        } else {
            stringResource(Res.string.mod_set_deadline, names.task(mod.taskUid), Dates.short(mod.date!!, language))
        }
    is ScenarioModification.SetFocus -> stringResource(Res.string.mod_set_focus, percentLabel((mod.factor * 100).roundToInt()))
}

/** Pourquoi une modification n'est pas (ou plus) appliquée, en quelques mots. */
@Composable
internal fun skipReasonText(reason: Scenario.SkipReason): String = stringResource(
    when (reason) {
        Scenario.SkipReason.MEMBER_NOT_FOUND -> Res.string.mod_skip_member_not_found
        Scenario.SkipReason.MEMBER_ARCHIVED -> Res.string.mod_skip_member_archived
        Scenario.SkipReason.TASK_NOT_FOUND -> Res.string.mod_skip_task_not_found
        Scenario.SkipReason.TASK_NOT_OPEN -> Res.string.mod_skip_task_not_open
        Scenario.SkipReason.INVALID_VALUE -> Res.string.mod_skip_invalid
        Scenario.SkipReason.ALREADY_APPLIED -> Res.string.mod_skip_already_applied
    },
)
