package com.skohscripts.kairos.ui.team

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.team.MemberAbsence
import com.skohscripts.kairos.core.team.MemberForm
import com.skohscripts.kairos.core.team.TeamMember
import com.skohscripts.kairos.core.team.TeamMembers
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.disclosure
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.member_self_badge
import com.skohscripts.kairos.ui.generated.resources.member_summary
import com.skohscripts.kairos.ui.generated.resources.team_members_add
import com.skohscripts.kairos.ui.generated.resources.team_members_empty_body
import com.skohscripts.kairos.ui.generated.resources.team_members_empty_title
import com.skohscripts.kairos.ui.generated.resources.team_members_former
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.navigation.LocalWindowWidth
import com.skohscripts.kairos.ui.navigation.isCompactWidth
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Destination Équipe, jalon E2 (docs/spec/equipe.md § Destination Équipe) : le
 * bouton « Ajouter un membre » en tête (ou, sans aucun membre, un état vide qui
 * l'explique), une carte par membre actif (« moi » d'abord, puis par nom), et la
 * section repliable « Anciens membres (n) ». Toucher une carte ouvre la fiche
 * ([MemberSheet]). Lit la base **complète** (`snapshot`), comme tous les écrans
 * d'équipe. La charge s'ajoutera aux cartes au jalon E4.
 *
 * [initialFormerExpanded] ouvre la section des anciens membres au départ (captures).
 */
@Composable
fun TeamMembersScreen(services: AppServices, initialFormerExpanded: Boolean = false) {
    val snapshot by services.repository.snapshot.collectAsState()
    val today = services.clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val active = remember(snapshot.members) { TeamMembers.active(snapshot.members) }
    val former = remember(snapshot.members) { TeamMembers.archived(snapshot.members) }
    var formerExpanded by rememberSaveable { mutableStateOf(initialFormerExpanded) }
    // Fiche ouverte : `sheetOpen` avec `sheetMemberId` nul = nouveau membre.
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var sheetMemberId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun open(id: Long?) {
        sheetMemberId = id
        sheetOpen = true
    }

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(16.dp),
        ) {
            if (snapshot.members.isEmpty()) {
                EmptyMembers(onAdd = { open(null) })
            } else {
                AddMemberButton(onClick = { open(null) })
                active.forEach { member ->
                    MemberCard(member, TeamMembers.nextAbsence(snapshot.absences, member.id, today), today) { open(member.id) }
                }
                if (former.isNotEmpty()) {
                    FormerHeader(former.size, formerExpanded) { formerExpanded = !formerExpanded }
                    if (formerExpanded) former.forEach { member -> MemberCard(member, null, today) { open(member.id) } }
                }
            }
        }
    }

    if (sheetOpen) MemberSheet(services, sheetMemberId) { sheetOpen = false }
}

@Composable
private fun AddMemberButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Icon(KairosIcons.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(Res.string.team_members_add), modifier = Modifier.padding(start = 6.dp))
    }
}

/** Sans aucun membre : à quoi servent les membres, avec le même bouton. Pas de bloc teinté. */
@Composable
private fun EmptyMembers(onAdd: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    ) {
        Icon(KairosIcons.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(
            stringResource(Res.string.team_members_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.heading(),
        )
        Text(stringResource(Res.string.team_members_empty_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        AddMemberButton(onAdd)
    }
}

/** « Anciens membres (n) » : en-tête de section qui déplie les membres archivés (48 dp, titre de section). */
@Composable
private fun FormerHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().disclosure(expanded, heading = true, onToggle = onToggle),
    ) {
        Text(
            stringResource(Res.string.team_members_former, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(if (expanded) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
    }
}

/**
 * Carte d'un membre : nom (et marque « moi »), rôle, « 80 % · 7 h/j », puis la
 * prochaine absence à venir ou en cours. Carte à contour, sans ombre ni
 * remplissage : l'unique bloc teinté de l'écran, s'il y en avait un, serait ailleurs.
 */
@Composable
private fun MemberCard(member: TeamMember, absence: MemberAbsence?, today: LocalDate, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        member.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (member.isSelf) SelfBadge()
                }
                if (member.role.isNotBlank()) {
                    Text(member.role, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    stringResource(Res.string.member_summary, member.availabilityPercent, MemberForm.formatHours(member.hoursPerDay)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (absence != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(KairosIcons.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text(absenceSummary(absence, today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Icon(KairosIcons.ChevronRight, contentDescription = null)
        }
    }
}

/** Marque « moi » : icône `Person` et le mot, en pastille à contour (8 dp), sans couleur de remplissage. */
@Composable
private fun SelfBadge() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Icon(KairosIcons.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Text(stringResource(Res.string.member_self_badge), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Fiche d'un membre ([memberId] nul : nouveau) : plein écran sous 600 dp,
 * dialogue au-delà. Enregistre par le dépôt ; les absences s'enregistrent une à
 * une, la fiche se referme après « Enregistrer », un archivage, une réactivation
 * ou une suppression.
 */
@Composable
internal fun MemberSheet(services: AppServices, memberId: Long?, onDismiss: () -> Unit) {
    val snapshot by services.repository.snapshot.collectAsState()
    val member = memberId?.let { id -> snapshot.members.firstOrNull { it.id == id } }
    // Membre disparu (supprimé ailleurs) : rien à montrer.
    if (memberId != null && member == null) {
        LaunchedEffect(memberId) { onDismiss() }
        return
    }
    val repository = services.repository
    val scope = rememberCoroutineScope()
    val today = services.clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val compact = LocalWindowWidth.current.isCompactWidth

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MemberSheetContent(
            member = member,
            absences = member?.let { TeamMembers.absencesOf(snapshot.absences, it.id) }.orEmpty(),
            settings = snapshot.settings,
            today = today,
            openTaskCount = member?.let { TeamMembers.openTaskCount(snapshot.tasks, it.id) } ?: 0,
            canDelete = member != null && !TeamMembers.hasHadTask(snapshot.tasks, member.id),
            compact = compact,
            onSave = { valid, isSelf ->
                scope.launch {
                    if (member == null) {
                        repository.createMember(valid.name, valid.role, valid.availabilityPercent, valid.hoursPerDay, isSelf)
                    } else {
                        repository.updateMember(member.id, valid.name, valid.role, valid.availabilityPercent, valid.hoursPerDay, isSelf)
                    }
                    onDismiss()
                }
            },
            onClose = onDismiss,
            onArchive = { member?.let { m -> scope.launch { repository.archiveMember(m.id); onDismiss() } } },
            onRestore = { member?.let { m -> scope.launch { repository.restoreMember(m.id); onDismiss() } } },
            onDelete = { member?.let { m -> scope.launch { repository.deleteMember(m.id); onDismiss() } } },
            onAddAbsence = { start, end, label -> member?.let { m -> scope.launch { repository.addAbsence(m.id, start, end, label) } } },
            onUpdateAbsence = { id, start, end, label -> scope.launch { repository.updateAbsence(id, start, end, label) } },
            onDeleteAbsence = { id -> scope.launch { repository.deleteAbsence(id) } },
        )
    }
}
