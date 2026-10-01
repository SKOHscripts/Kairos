package com.skohscripts.kairos.ui.team

import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedRow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.LoadLevel
import com.skohscripts.kairos.core.team.MemberLoad
import com.skohscripts.kairos.core.team.TeamLoad
import com.skohscripts.kairos.ui.stats.BarRow
import com.skohscripts.kairos.ui.stats.Panel
import com.skohscripts.kairos.ui.stats.StatTile
import com.skohscripts.kairos.ui.generated.resources.load_bar_value_plain
import com.skohscripts.kairos.ui.generated.resources.load_categories_empty
import com.skohscripts.kairos.ui.generated.resources.load_categories_hint
import com.skohscripts.kairos.ui.generated.resources.load_categories_title
import com.skohscripts.kairos.ui.generated.resources.load_category_backlog
import com.skohscripts.kairos.ui.generated.resources.load_at_risk_many
import com.skohscripts.kairos.ui.generated.resources.load_at_risk_one
import com.skohscripts.kairos.ui.generated.resources.load_computing
import com.skohscripts.kairos.ui.generated.resources.load_horizon_option
import com.skohscripts.kairos.ui.generated.resources.load_horizon_title
import com.skohscripts.kairos.ui.generated.resources.load_member_counts
import com.skohscripts.kairos.ui.generated.resources.load_plan_note
import com.skohscripts.kairos.ui.generated.resources.load_spread_gap
import com.skohscripts.kairos.ui.generated.resources.load_spread_hint
import com.skohscripts.kairos.ui.generated.resources.load_spread_title
import com.skohscripts.kairos.ui.generated.resources.load_tile_assigned
import com.skohscripts.kairos.ui.generated.resources.load_tile_backlog
import com.skohscripts.kairos.ui.generated.resources.load_tile_backlog_plain
import com.skohscripts.kairos.ui.generated.resources.load_tile_capacity
import com.skohscripts.kairos.ui.generated.resources.load_tile_rate
import kotlin.math.max
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.skohscripts.kairos.ui.app.LocalMessages
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
 * Destination Équipe (docs/spec/equipe.md § Destination Équipe, jalon E2, et
 * docs/spec/equipe-charge.md, jalon E4). De haut en bas : le sélecteur d'horizon
 * (1, 2, 4 ou 8 semaines ; le réglage est la valeur de départ), quatre chiffres clés
 * (capacité, charge assignée, taux de charge, backlog non assigné), le bouton « Ajouter
 * un membre » (ou, sans aucun membre, un état vide qui l'explique), une carte par
 * membre actif (« moi » d'abord, puis par nom) avec sa barre de charge, les panneaux
 * « Par catégorie » et « Répartition », et la section repliable « Anciens membres (n) ».
 * Toucher une carte ouvre la fiche ([MemberSheet]). Lit la base **complète**
 * (`snapshot`), comme tous les écrans d'équipe.
 *
 * La charge ([TeamLoad]) n'est **jamais** calculée dans la composition : elle l'est dans
 * `Dispatchers.Default` à chaque changement de la base ou de l'horizon
 * ([rememberTeamLoad]). L'ancien résultat reste à l'écran jusqu'au nouveau, avec un
 * indicateur de progression ; avant le premier, les cartes n'ont pas de barre.
 *
 * [initialFormerExpanded] ouvre la section des anciens membres au départ (captures) ;
 * [initialSheetMemberId] ouvre d'emblée la fiche de ce membre (captures) ;
 * [initialHorizonWeeks] fixe l'horizon de départ (captures, sinon le réglage).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamMembersScreen(
    services: AppServices,
    initialFormerExpanded: Boolean = false,
    initialSheetMemberId: Long? = null,
    initialHorizonWeeks: Int? = null,
) {
    val snapshot by services.repository.snapshot.collectAsState()
    val today = services.clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val active = remember(snapshot.members) { TeamMembers.active(snapshot.members) }
    val former = remember(snapshot.members) { TeamMembers.archived(snapshot.members) }
    var formerExpanded by rememberSaveable { mutableStateOf(initialFormerExpanded) }
    // Fiche ouverte : `sheetOpen` avec `sheetMemberId` nul = nouveau membre.
    var sheetOpen by rememberSaveable { mutableStateOf(initialSheetMemberId != null) }
    var sheetMemberId by rememberSaveable { mutableStateOf(initialSheetMemberId) }
    // Horizon choisi à l'écran ; `null` = celui des réglages.
    var horizonChoice by rememberSaveable { mutableStateOf(initialHorizonWeeks) }
    val settingsHorizon = (snapshot.settings.team ?: TeamSettings()).horizonWeeks
    val horizon = horizonChoice ?: settingsHorizon
    val computed = rememberTeamLoad(services, snapshot, horizon)
    val load = computed.value

    fun open(id: Long?) {
        sheetMemberId = id
        sheetOpen = true
    }

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            verticalArrangement = Arrangement.spacedBy(KairosSpacing.m),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(KairosSpacing.l),
        ) {
            if (snapshot.members.isEmpty()) {
                EmptyMembers(onAdd = { open(null) })
            } else {
                if (active.isNotEmpty()) {
                    HorizonSelector(horizon, settingsHorizon, computed.computing) { horizonChoice = it }
                    if (load != null) LoadTiles(load)
                }
                AddMemberButton(onClick = { open(null) })
                active.forEach { member ->
                    MemberCard(
                        member, TeamMembers.nextAbsence(snapshot.absences, member.id, today), today,
                        load = load?.members?.firstOrNull { it.member.id == member.id },
                    ) { open(member.id) }
                }
                if (load != null && active.isNotEmpty()) {
                    CategoriesPanel(load)
                    SpreadPanel(load)
                }
                if (former.isNotEmpty()) {
                    FormerHeader(former.size, formerExpanded) { formerExpanded = !formerExpanded }
                    if (formerExpanded) former.forEach { member -> MemberCard(member, null, today, load = null) { open(member.id) } }
                }
            }
        }
    }

    if (sheetOpen) MemberSheet(services, sheetMemberId, computed) { sheetOpen = false }
}

@Composable
private fun AddMemberButton(onClick: () -> Unit) {
    KairosButton(onClick = onClick, contentPadding = KairosButtonIconPadding) {
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
        // Titre de section : `xl` avant lui (la colonne espace ses blocs de `m`).
        modifier = Modifier.fillMaxWidth().padding(top = KairosSpacing.xl - KairosSpacing.m).disclosure(expanded, heading = true, onToggle = onToggle),
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
 * Carte d'un membre : nom (et marque « moi »), rôle, « 80 % · 7 h/j », la prochaine
 * absence à venir ou en cours, puis (membre actif, charge calculée) sa [LoadBar]
 * « 112 h / 96 h · 117 % », ses tâches en cours et à faire, ses tâches non estimées et
 * ses échéances en danger (contour + icône `Warning`). Carte à contour, sans ombre ni
 * remplissage : l'unique bloc teinté de l'écran, s'il y en avait un, serait ailleurs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberCard(member: TeamMember, absence: MemberAbsence?, today: LocalDate, load: MemberLoad?, onClick: () -> Unit) {
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
                if (load != null) {
                    LoadBar(load.loadHours, load.capacityHours, load.ratePercent, load.level, Modifier.padding(top = 4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(Res.string.load_member_counts, load.inProgressCount, load.todoCount), style = MaterialTheme.typography.bodySmall)
                        if (load.unestimatedCount > 0) Flag(unestimatedText(load.unestimatedCount), flagged = true)
                        if (load.atRiskCount > 0) {
                            Flag(
                                if (load.atRiskCount == 1) stringResource(Res.string.load_at_risk_one) else stringResource(Res.string.load_at_risk_many, load.atRiskCount),
                                flagged = true,
                            )
                        }
                    }
                }
            }
            Icon(KairosIcons.ChevronRight, contentDescription = null)
        }
    }
}

/**
 * Sélecteur d'horizon : bouton segmenté MD3 à 1, 2, 4 et 8 semaines, plus le réglage s'il n'en fait pas partie
 * (un réglage de 3 semaines donne un cinquième segment). Un indicateur de progression sous le titre (4 dp
 * réservés, pour que la page ne saute pas) montre qu'un calcul est en route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HorizonSelector(horizon: Int, settingsHorizon: Int, computing: Boolean, onSelect: (Int) -> Unit) {
    val options = (HORIZON_OPTIONS + settingsHorizon).distinct().sorted()
    val title = stringResource(Res.string.load_horizon_title)
    val computingText = stringResource(Res.string.load_computing)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
        KairosSegmentedRow(Modifier.fillMaxWidth().semantics { contentDescription = title }) {
            options.forEachIndexed { index, weeks ->
                val description = weeksText(weeks)
                KairosSegmentedButton(
                    selected = weeks == horizon,
                    onClick = { onSelect(weeks) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(stringResource(Res.string.load_horizon_option, weeks), maxLines = 1) },
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (computing) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = computingText })
        }
    }
}

private val HORIZON_OPTIONS = listOf(1, 2, 4, 8)

/**
 * Les quatre chiffres clés de la charge sur l'horizon (docs/spec/equipe-charge.md § Charge globale) : capacité de
 * l'équipe et son taux de focus, charge assignée, taux de charge (contour + icône au-delà du seuil d'alerte), backlog
 * non assigné en heures et en semaines d'équipe. Ce que ces chiffres ne comptent pas, les tâches non estimées, est
 * dit à part sous le chiffre (« + 3 non estimées », contour + icône). Une ligne rappelle que le plan est sans aléa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoadTiles(load: TeamLoad) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val tile = Modifier.weight(1f).widthIn(min = 150.dp).fillMaxRowHeight()
            StatTile(
                hoursText(load.capacityHours),
                stringResource(Res.string.load_tile_capacity, percentText(100.0 * load.focusFactor)),
                tile,
            )
            StatTile(
                hoursText(load.assignedHours),
                stringResource(Res.string.load_tile_assigned),
                tile,
                note = load.unestimatedAssigned.takeIf { it > 0 }?.let { unestimatedText(it) },
            )
            StatTile(
                percentText(load.ratePercent),
                stringResource(Res.string.load_tile_rate),
                tile,
                warn = load.level != LoadLevel.OK,
            )
            StatTile(
                hoursText(load.backlogHours),
                load.backlogTeamWeeks?.let { stringResource(Res.string.load_tile_backlog, teamWeeksText(it)) } ?: stringResource(Res.string.load_tile_backlog_plain),
                tile,
                note = load.unestimatedBacklog.takeIf { it > 0 }?.let { unestimatedText(it) },
            )
        }
        Text(stringResource(Res.string.load_plan_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Panneau « Par catégorie » : pour chaque catégorie (« Sans catégorie » comprise), du plus au moins lourd, ses heures
 * (assignées + backlog) et sa part de la capacité de l'équipe sur l'horizon ; le détail dit ce qui est au backlog et
 * les tâches non estimées. Une part au-delà de 100 % n'est pas un dépassement de membre : la barre reste au primaire.
 */
@Composable
private fun CategoriesPanel(load: TeamLoad) {
    Panel(KairosIcons.Layers, stringResource(Res.string.load_categories_title), stringResource(Res.string.load_categories_hint)) {
        if (load.categories.isEmpty()) {
            Text(stringResource(Res.string.load_categories_empty), style = MaterialTheme.typography.bodyMedium)
            return@Panel
        }
        val scale = max(1.0, load.categories.maxOf { it.capacityShare ?: 0.0 })
        load.categories.forEach { c ->
            val share = c.capacityShare
            val details = listOfNotNull(
                c.backlogHours.takeIf { it > 0.0 }?.let { stringResource(Res.string.load_category_backlog, hoursText(it)) },
                c.unestimatedCount.takeIf { it > 0 }?.let { unestimatedText(it) },
            ).joinToString(" · ").ifEmpty { null }
            BarRow(
                label = categoryName(c.category),
                fraction = if (share == null) 0f else (share / scale).toFloat(),
                value = if (share == null) hoursText(c.totalHours) else "${hoursText(c.totalHours)} · ${percentText(100.0 * share)}",
                detail = details,
                stackedWhenNarrow = true,
            )
        }
    }
}

/**
 * Panneau « Répartition » : le taux de charge de chaque membre sur une même échelle (au moins 100 %), la part
 * au-delà de 100 % en rouge, et l'écart maximal (« Écart max : Marc 117 %, Léa 54 % »).
 */
@Composable
private fun SpreadPanel(load: TeamLoad) {
    // Un ancien résultat peut ne plus avoir de membre (le premier vient d'être ajouté) : rien à comparer.
    if (load.members.isEmpty()) return
    Panel(KairosIcons.Groups, stringResource(Res.string.load_spread_title), stringResource(Res.string.load_spread_hint)) {
        val scale = max(100.0, load.members.maxOf { it.ratePercent ?: 100.0 })
        load.members.forEach { m ->
            val parts = LoadFractions.of(m.ratePercent, m.loadHours, scale)
            val value = if (m.ratePercent == null) {
                stringResource(Res.string.load_bar_value_plain, hoursText(m.loadHours), hoursText(m.capacityHours))
            } else {
                percentText(m.ratePercent)
            }
            // Une seule annonce par barre (« Alex 137 % ») ; les textes de la ligne, redondants avec la carte du membre
            // juste au-dessus, ne sont pas exposés un à un.
            Box(Modifier.clearAndSetSemantics { contentDescription = "${m.member.name} $value" }) {
                BarRow(
                    label = m.member.name,
                    fraction = parts.fill,
                    value = value,
                    warn = m.level != LoadLevel.OK,
                    overflow = parts.overflow,
                    stackedWhenNarrow = true,
                )
            }
        }
        load.spread?.let { spread ->
            Text(
                stringResource(
                    Res.string.load_spread_gap,
                    spread.highest.member.name, percentText(spread.highest.ratePercent),
                    spread.lowest.member.name, percentText(spread.lowest.ratePercent),
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Marque « moi » : icône `Person` et le mot, en pastille à contour (8 dp), sans couleur de remplissage. */
@Composable
internal fun SelfBadge() {
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
internal fun MemberSheet(services: AppServices, memberId: Long?, load: Computed<TeamLoad>, onDismiss: () -> Unit) {
    val snapshot by services.repository.snapshot.collectAsState()
    val member = memberId?.let { id -> snapshot.members.firstOrNull { it.id == id } }
    // Membre disparu (supprimé ailleurs) : rien à montrer.
    if (memberId != null && member == null) {
        LaunchedEffect(memberId) { onDismiss() }
        return
    }
    val repository = services.repository
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    val today = services.clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val compact = LocalWindowWidth.current.isCompactWidth

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MemberSheetContent(
            member = member,
            absences = member?.let { TeamMembers.absencesOf(snapshot.absences, it.id) }.orEmpty(),
            settings = snapshot.settings,
            today = today,
            openTaskCount = member?.let { TeamMembers.openTaskCount(snapshot.tasks, it.id) } ?: 0,
            canDelete = member != null && !TeamMembers.hasHadTask(snapshot.tasks, member.id, snapshot.teamEvents),
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
            activity = member?.let { memberActivity(snapshot.teamEvents, it.id, today, TimeZone.currentSystemDefault()) }.orEmpty(),
            members = snapshot.members,
            onSendTasks = { member?.let { m -> scope.launch { sendPack(services, m, messages) } } },
            loadView = member?.takeIf { !it.archived }?.let { m ->
                val memberLoad = load.value?.members?.firstOrNull { it.member.id == m.id }
                MemberLoadView(
                    load = memberLoad,
                    weeks = load.value?.horizonWeeks ?: 0,
                    tasks = memberLoad?.plan?.tasks?.mapNotNull { p -> snapshot.tasks.firstOrNull { it.id == p.taskId } }?.associateBy { it.id }.orEmpty(),
                    computing = load.computing,
                )
            },
        )
    }
}
