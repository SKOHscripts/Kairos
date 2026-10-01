package com.skohscripts.kairos.ui.team

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.MemberAbsence
import com.skohscripts.kairos.core.team.TeamMember
import com.skohscripts.kairos.core.team.TeamMembers
import com.skohscripts.kairos.core.team.TeamSignals
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.assign_absent_today
import com.skohscripts.kairos.ui.generated.resources.assign_backlog
import com.skohscripts.kairos.ui.generated.resources.assign_in_progress
import com.skohscripts.kairos.ui.generated.resources.assign_over_limit
import com.skohscripts.kairos.ui.generated.resources.keep_in_progress_body
import com.skohscripts.kairos.ui.generated.resources.keep_in_progress_no
import com.skohscripts.kairos.ui.generated.resources.keep_in_progress_title
import com.skohscripts.kairos.ui.generated.resources.keep_in_progress_yes
import com.skohscripts.kairos.ui.generated.resources.member_self_badge
import com.skohscripts.kairos.ui.generated.resources.member_unknown
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * Ce que le menu « Assigner à… » sait des membres (docs/spec/equipe-backlog-suivi.md
 * § Assignation et réaffectation) : qui est actif, combien de tâches il a en
 * cours, s'il dépasse la limite d'en-cours des réglages, s'il est absent
 * aujourd'hui. Lit la base **complète** (écrans d'équipe). La charge en % arrive
 * avec le jalon E4 (`equipe-charge.md`).
 */
internal class AssignContext(
    val members: List<TeamMember>,
    private val tasks: List<Task>,
    private val absences: List<MemberAbsence>,
    private val settings: Settings,
    private val today: LocalDate,
) {
    /** Membres proposés : actifs, « moi » en tête puis l'ordre de `TeamMembers`. */
    val active: List<TeamMember> = TeamMembers.active(members)

    /** Limite d'en-cours par membre (0 = sans limite). */
    val wipLimit: Int = (settings.team ?: TeamSettings()).wipLimit

    fun inProgress(memberId: Long): Int = TeamSignals.inProgressCount(tasks, memberId)

    fun overLimit(memberId: Long): Boolean = TeamSignals.wipExceeded(tasks, memberId, settings)

    fun absentToday(memberId: Long): Boolean =
        TeamMembers.nextAbsence(absences, memberId, today)?.let { it.start <= today } == true

    /** Nom d'un membre (archivé compris), `null` si [id] est nul ; « Membre inconnu » si la fiche a disparu. */
    @Composable
    fun name(id: Long?): String? = id?.let { members.firstOrNull { m -> m.id == it }?.name ?: stringResource(Res.string.member_unknown) }

    fun nameOrNull(id: Long?): String? = id?.let { members.firstOrNull { m -> m.id == it }?.name }

    companion object {
        fun of(snapshot: KairosSnapshot, today: LocalDate) =
            AssignContext(snapshot.members, snapshot.tasks, snapshot.absences, snapshot.settings, today)
    }
}

/**
 * Menu « Assigner à… » : les membres actifs (« moi » en tête), chacun avec son
 * nombre de tâches en cours ; un membre qui dépasse la limite d'en-cours ou qui
 * est absent aujourd'hui porte un contour et l'icône `Warning` (jamais une
 * couleur seule). [currentId] : titulaire actuel (coché) ; [showBacklog] ajoute
 * « Remettre au backlog » en dernière entrée. Entrées de 48 dp au moins.
 */
@Composable
internal fun AssignMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    context: AssignContext,
    currentId: Long?,
    showBacklog: Boolean,
    onPick: (Long?) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        AssignMenuItems(context, currentId, showBacklog) {
            onDismiss()
            onPick(it)
        }
    }
}

/** Les entrées du menu, réutilisables dans un menu déroulant de champ (fiche de tâche). */
@Composable
internal fun ColumnScope.AssignMenuItems(context: AssignContext, currentId: Long?, showBacklog: Boolean, onPick: (Long?) -> Unit) {
    context.active.forEach { member ->
        val over = context.overLimit(member.id)
        val absent = context.absentToday(member.id)
        val alert = when {
            over && absent -> "${stringResource(Res.string.assign_over_limit, context.wipLimit)} · ${stringResource(Res.string.assign_absent_today)}"
            over -> stringResource(Res.string.assign_over_limit, context.wipLimit)
            absent -> stringResource(Res.string.assign_absent_today)
            else -> null
        }
        DropdownMenuItem(
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = if (alert != null) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp)
                    } else {
                        Modifier
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (alert != null) Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(member.name, style = MaterialTheme.typography.bodyLarge)
                        if (member.isSelf) {
                            Text(
                                stringResource(Res.string.member_self_badge),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        stringResource(Res.string.assign_in_progress, context.inProgress(member.id)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (alert != null) Text(alert, style = MaterialTheme.typography.bodySmall)
                }
            },
            leadingIcon = if (member.id == currentId) {
                { Icon(KairosIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp)) }
            } else {
                null
            },
            onClick = { onPick(member.id) },
        )
    }
    if (showBacklog) {
        if (context.active.isNotEmpty()) HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.assign_backlog)) },
            leadingIcon = { Icon(KairosIcons.Stacks, contentDescription = null, modifier = Modifier.size(20.dp)) },
            onClick = { onPick(null) },
        )
    }
}

/**
 * « Garder l’état En cours ? » : posé quand on réaffecte une tâche en cours
 * (docs/spec/equipe-backlog-suivi.md). « Oui » garde l'état chez le nouveau
 * titulaire, « Non » remet la tâche à « À faire » ; fermer la boîte sans choisir
 * annule la réaffectation.
 */
@Composable
internal fun KeepInProgressDialog(taskTitle: String, toName: String, onAnswer: (keep: Boolean) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.keep_in_progress_title)) },
        text = { Text(stringResource(Res.string.keep_in_progress_body, taskTitle, toName)) },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(stringResource(Res.string.keep_in_progress_yes)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(Res.string.keep_in_progress_no)) } },
    )
}
