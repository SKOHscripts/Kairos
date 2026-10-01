package com.skohscripts.kairos.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.team.exchange.IgnoredReason
import com.skohscripts.kairos.core.team.exchange.IgnoredReportLine
import com.skohscripts.kairos.core.team.exchange.PackField
import com.skohscripts.kairos.core.team.exchange.PackMergePlan
import com.skohscripts.kairos.core.team.exchange.PackRefusal
import com.skohscripts.kairos.core.team.exchange.ReportChange
import com.skohscripts.kairos.core.team.exchange.ReportMergePlan
import com.skohscripts.kairos.core.team.exchange.ReportRefusal
import com.skohscripts.kairos.core.team.exchange.ReportTaskUpdate
import com.skohscripts.kairos.core.team.exchange.TeamOrigin
import com.skohscripts.kairos.data.ExportCodec
import com.skohscripts.kairos.data.ImportException
import com.skohscripts.kairos.data.TeamExchangeCodec
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.app.importErrorMessage
import com.skohscripts.kairos.ui.app.saveBackup
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.day.duration
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.exchange_blocked_by
import com.skohscripts.kairos.ui.generated.resources.exchange_blocked_by_backlog
import com.skohscripts.kairos.ui.generated.resources.exchange_change_progress
import com.skohscripts.kairos.ui.generated.resources.exchange_change_spent
import com.skohscripts.kairos.ui.generated.resources.exchange_change_started
import com.skohscripts.kairos.ui.generated.resources.exchange_change_status
import com.skohscripts.kairos.ui.generated.resources.exchange_close
import com.skohscripts.kairos.ui.generated.resources.exchange_field_category
import com.skohscripts.kairos.ui.generated.resources.exchange_field_deadline
import com.skohscripts.kairos.ui.generated.resources.exchange_field_description
import com.skohscripts.kairos.ui.generated.resources.exchange_field_estimate
import com.skohscripts.kairos.ui.generated.resources.exchange_field_points
import com.skohscripts.kairos.ui.generated.resources.exchange_field_priority
import com.skohscripts.kairos.ui.generated.resources.exchange_field_title
import com.skohscripts.kairos.ui.generated.resources.exchange_former_holder
import com.skohscripts.kairos.ui.generated.resources.exchange_ignored_empty_title
import com.skohscripts.kairos.ui.generated.resources.exchange_ignored_unknown_parent
import com.skohscripts.kairos.ui.generated.resources.exchange_ignored_unknown_task
import com.skohscripts.kairos.ui.generated.resources.exchange_integrate
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_backup
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_blockers
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_created
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_deps
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_done
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_intro
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_member_changed
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_noop
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_not_applied
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_refused_own
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_refused_title
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_removed
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_removed_note
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_skipped_many
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_skipped_one
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_title_many
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_title_one
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_title_update
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_unchanged
import com.skohscripts.kairos.ui.generated.resources.exchange_pack_updated
import com.skohscripts.kairos.ui.generated.resources.exchange_receive
import com.skohscripts.kairos.ui.generated.resources.exchange_report_backup
import com.skohscripts.kairos.ui.generated.resources.exchange_report_date
import com.skohscripts.kairos.ui.generated.resources.exchange_report_done_summary
import com.skohscripts.kairos.ui.generated.resources.exchange_report_ignored
import com.skohscripts.kairos.ui.generated.resources.exchange_report_member_archived
import com.skohscripts.kairos.ui.generated.resources.exchange_report_not_applied
import com.skohscripts.kairos.ui.generated.resources.exchange_report_nothing
import com.skohscripts.kairos.ui.generated.resources.exchange_report_refused_older
import com.skohscripts.kairos.ui.generated.resources.exchange_report_refused_title
import com.skohscripts.kairos.ui.generated.resources.exchange_report_refused_unknown_member
import com.skohscripts.kairos.ui.generated.resources.exchange_report_refused_wrong_team
import com.skohscripts.kairos.ui.generated.resources.exchange_report_subtasks
import com.skohscripts.kairos.ui.generated.resources.exchange_report_title
import com.skohscripts.kairos.ui.generated.resources.exchange_report_unchanged
import com.skohscripts.kairos.ui.generated.resources.exchange_report_updates
import com.skohscripts.kairos.ui.generated.resources.exchange_status_archived
import com.skohscripts.kairos.ui.generated.resources.exchange_status_done
import com.skohscripts.kairos.ui.generated.resources.exchange_status_todo
import com.skohscripts.kairos.ui.generated.resources.exchange_subtask_under
import com.skohscripts.kairos.ui.generated.resources.exchange_unknown_manager
import com.skohscripts.kairos.ui.generated.resources.exchange_updated_adopted
import com.skohscripts.kairos.ui.generated.resources.exchange_updated_restored
import com.skohscripts.kairos.ui.generated.resources.exchange_value_none
import com.skohscripts.kairos.ui.generated.resources.exchange_who
import com.skohscripts.kairos.ui.generated.resources.progress_percent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

// Aperçus de réception d'un paquet et d'intégration d'un rapport (docs/spec/equipe-echanges.md § Interface).
// Le bouton « Importer » des Réglages lit le fichier, en reconnaît le format (`TeamExchangeCodec.detect`) et, pour un
// paquet ou un rapport, ouvre l'un de ces dialogues AVANT d'écrire quoi que ce soit.

/** Ce que le bouton « Importer » a reconnu dans le fichier choisi. */
sealed interface ImportedFile {
    /** Export complet : il remplace toutes les données, après confirmation (comportement historique). */
    class Full(val snapshot: KairosSnapshot) : ImportedFile

    /** Paquet ou rapport d'équipe : fusion ciblée, après aperçu. */
    class Exchange(val preview: ExchangePreview) : ImportedFile
}

/** Aperçu d'un fichier d'échange : le texte à appliquer et le plan calculé, sans rien écrire. */
sealed interface ExchangePreview {
    /** Paquet reçu (côté membre). */
    class Pack(val text: String, val plan: PackMergePlan) : ExchangePreview

    /** Rapport reçu (côté manager) ; [titles] : titre des tâches d'équipe par `teamUid`, pour nommer la mère d'une sous-tâche. */
    class Report(val text: String, val plan: ReportMergePlan, val titles: Map<String, String>) : ExchangePreview

    /** Le plan est accepté : « Recevoir » / « Intégrer » est proposé. Un fichier refusé n'a que « Fermer ». */
    val accepted: Boolean
        get() = when (this) {
            is Pack -> plan.accepted && plan.hasVisibleChange()
            is Report -> plan.accepted
        }
}

/**
 * Aiguillage du bouton « Importer » : lit le champ `format` d'abord, puis le décodeur du format. Export complet →
 * [ImportedFile.Full] ; paquet ou rapport → aperçu, sans écriture.
 * @throws ImportException fichier illisible, trop récent ou d'un autre format.
 */
fun readImportedFile(services: AppServices, text: String): ImportedFile = when (TeamExchangeCodec.detect(text)) {
    TeamExchangeCodec.Kind.EXPORT -> ImportedFile.Full(ExportCodec.decode(text))
    TeamExchangeCodec.Kind.PACK -> ImportedFile.Exchange(ExchangePreview.Pack(text, services.repository.previewPack(text)))
    TeamExchangeCodec.Kind.REPORT -> {
        val plan = services.repository.previewReport(text)
        val titles = services.repository.snapshot.value.tasks.mapNotNull { t -> t.teamUid?.let { it to t.title } }.toMap()
        ImportedFile.Exchange(ExchangePreview.Report(text, plan, titles))
    }
}

/**
 * Applique un paquet ou un rapport déjà prévisualisé, après une sauvegarde automatique (`avant-reception-paquet-…`,
 * `avant-integration-rapport-…`). Sans sauvegarde réussie, rien n'est écrit (`saveBackup` affiche l'erreur). Retourne
 * `true` si le fichier a été appliqué.
 */
internal suspend fun applyExchange(services: AppServices, preview: ExchangePreview, messages: (String) -> Unit): Boolean {
    val prefix = if (preview is ExchangePreview.Pack) "avant-reception-paquet" else "avant-integration-rapport"
    if (!saveBackup(services, prefix, messages)) return false
    try {
        when (preview) {
            is ExchangePreview.Pack -> {
                val result = services.repository.receivePack(preview.text)
                val plan = result.plan
                messages(
                    if (result.applied) getString(Res.string.exchange_pack_done, plan.created.size, plan.visibleUpdates.size, plan.removed.size)
                    else getString(Res.string.exchange_pack_not_applied),
                )
                return result.applied
            }
            is ExchangePreview.Report -> {
                val result = services.repository.integrateReport(preview.text)
                val plan = result.plan
                messages(
                    if (result.applied) getString(Res.string.exchange_report_done_summary, plan.member?.name.orEmpty(), plan.updates.size, plan.newSubtasks.size)
                    else getString(Res.string.exchange_report_not_applied),
                )
                return result.applied
            }
        }
    } catch (e: ImportException) {
        messages(importErrorMessage(e))
        return false
    }
}

/** Dialogue d'aperçu (carte MD3 de 28 dp, seul élément flottant à porter une ombre) : voir [ExchangePreviewCard]. */
@Composable
internal fun ExchangePreviewDialog(preview: ExchangePreview, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ExchangePreviewCard(preview, onConfirm, onDismiss)
    }
}

/**
 * Carte d'aperçu : titre, liste défilante (compteurs puis lignes), puis « Annuler » / « Recevoir » (paquet) ou
 * « Intégrer » (rapport). Un fichier refusé, ou un paquet déjà reçu tel quel, n'a que « Fermer ».
 */
@Composable
fun ExchangePreviewCard(preview: ExchangePreview, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val title: String
    val body: @Composable () -> Unit
    when (preview) {
        is ExchangePreview.Pack -> {
            title = packTitle(preview.plan)
            body = { PackBody(preview.plan) }
        }
        is ExchangePreview.Report -> {
            title = reportTitle(preview.plan)
            body = { ReportBody(preview.plan, preview.titles) }
        }
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth(),
    ) {
        Column {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.heading())
                body()
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            ) {
                if (preview.accepted) {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
                    Button(onClick = onConfirm) {
                        Text(stringResource(if (preview is ExchangePreview.Pack) Res.string.exchange_receive else Res.string.exchange_integrate))
                    }
                } else {
                    Button(onClick = onDismiss) { Text(stringResource(Res.string.exchange_close)) }
                }
            }
        }
    }
}

// --- Paquet (côté membre) ---------------------------------------------------------------------------------

/** « Corentin (Équipe Plateforme) » ; le nom de l'équipe seul, ou « un manager », si le manager n'a pas donné le sien. */
@Composable
internal fun whoText(origin: TeamOrigin): String {
    val manager = origin.managerName.trim()
    val team = origin.teamName.trim()
    return when {
        manager.isNotEmpty() && team.isNotEmpty() -> stringResource(Res.string.exchange_who, manager, team)
        manager.isNotEmpty() -> manager
        team.isNotEmpty() -> team
        else -> stringResource(Res.string.exchange_unknown_manager)
    }
}

/** Nom court du manager d'une origine, pour « retirée par … » et « de … » : le manager, à défaut l'équipe. */
@Composable
internal fun managerLabel(origin: TeamOrigin): String =
    origin.managerName.trim().ifEmpty { origin.teamName.trim() }.ifEmpty { stringResource(Res.string.exchange_unknown_manager) }

/** Le paquet change quelque chose que l'aperçu peut montrer (sinon il a déjà été reçu tel quel). */
private fun PackMergePlan.hasVisibleChange() =
    created.isNotEmpty() || visibleUpdates.isNotEmpty() || removed.isNotEmpty() || dependencyAdds.isNotEmpty() || dependencyRemoves.isNotEmpty()

/** Nombre de tâches que le paquet décrit : celles qu'il crée, met à jour, ou laisse telles quelles (hors tâches déjà retirées). */
private fun packTaskCount(plan: PackMergePlan) = plan.created.size + plan.updated.size + plan.unchanged.count { !it.originRemoved }

@Composable
private fun packTitle(plan: PackMergePlan): String {
    if (!plan.accepted) return stringResource(Res.string.exchange_pack_refused_title)
    val who = whoText(plan.origin)
    return when (val n = packTaskCount(plan)) {
        0 -> stringResource(Res.string.exchange_pack_title_update, who)
        1 -> stringResource(Res.string.exchange_pack_title_one, who)
        else -> stringResource(Res.string.exchange_pack_title_many, n, who)
    }
}

@Composable
private fun PackBody(plan: PackMergePlan) {
    if (plan.refusal == PackRefusal.OWN_TEAM) {
        Text(stringResource(Res.string.exchange_pack_refused_own), style = MaterialTheme.typography.bodyMedium)
        return
    }
    if (!plan.accepted) return
    if (!plan.hasVisibleChange()) {
        Text(stringResource(Res.string.exchange_pack_noop), style = MaterialTheme.typography.bodyMedium)
        return
    }
    val manager = managerLabel(plan.origin)
    Text(stringResource(Res.string.exchange_pack_intro, manager), style = MaterialTheme.typography.bodyMedium)
    if (plan.memberChanged) Flag(stringResource(Res.string.exchange_pack_member_changed, plan.origin.memberName), flagged = true, style = MaterialTheme.typography.bodyMedium)

    if (plan.created.isNotEmpty()) {
        Section(stringResource(Res.string.exchange_pack_created, plan.created.size)) {
            plan.created.forEach { Line(it.task.title) }
        }
    }
    val updates = plan.visibleUpdates
    if (updates.isNotEmpty()) {
        Section(stringResource(Res.string.exchange_pack_updated, updates.size)) {
            updates.forEach { u ->
                val what = buildList {
                    if (u.restored) add(stringResource(Res.string.exchange_updated_restored))
                    if (u.adopted) add(stringResource(Res.string.exchange_updated_adopted))
                    u.changed.forEach { add(fieldLabel(it)) }
                }
                Line(u.after.title, what.joinToString(", "))
            }
        }
    }
    if (plan.removed.isNotEmpty()) {
        Section(stringResource(Res.string.exchange_pack_removed, plan.removed.size)) {
            plan.removed.forEach { Line(it.title) }
            Text(stringResource(Res.string.exchange_pack_removed_note, manager), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val unchanged = plan.unchanged.count { !it.originRemoved } + (plan.updated.size - updates.size)
    if (unchanged > 0) Text(stringResource(Res.string.exchange_pack_unchanged, unchanged), style = MaterialTheme.typography.bodyMedium)

    if (plan.dependencyAdds.isNotEmpty() || plan.dependencyRemoves.isNotEmpty()) {
        Text(
            stringResource(Res.string.exchange_pack_deps, plan.dependencyAdds.size, plan.dependencyRemoves.size),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (plan.skippedDependencies.isNotEmpty()) {
        val n = plan.skippedDependencies.size
        Flag(
            if (n == 1) stringResource(Res.string.exchange_pack_skipped_one) else stringResource(Res.string.exchange_pack_skipped_many, n),
            flagged = true,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (plan.externalBlockers.isNotEmpty()) {
        val titles = packTitles(plan)
        Section(stringResource(Res.string.exchange_pack_blockers)) {
            plan.externalBlockers.forEach { b ->
                val blocked = titles[b.taskUid].orEmpty()
                val holder = b.assignee
                Line(
                    if (holder != null) stringResource(Res.string.exchange_blocked_by, blocked, b.title, holder)
                    else stringResource(Res.string.exchange_blocked_by_backlog, blocked, b.title),
                )
            }
        }
    }
    Text(stringResource(Res.string.exchange_pack_backup), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Titre des tâches du paquet par `teamUid` (celles qu'il crée, met à jour ou laisse telles quelles). */
private fun packTitles(plan: PackMergePlan): Map<String, String> = buildMap {
    plan.unchanged.forEach { t -> t.teamUid?.let { put(it, t.title) } }
    plan.updated.forEach { u -> u.after.teamUid?.let { put(it, u.after.title) } }
    plan.created.forEach { n -> n.task.teamUid?.let { put(it, n.task.title) } }
}

@Composable
private fun fieldLabel(field: PackField): String = stringResource(
    when (field) {
        PackField.TITLE -> Res.string.exchange_field_title
        PackField.DESCRIPTION -> Res.string.exchange_field_description
        PackField.PRIORITY -> Res.string.exchange_field_priority
        PackField.POINTS -> Res.string.exchange_field_points
        PackField.CATEGORY -> Res.string.exchange_field_category
        PackField.DEADLINE -> Res.string.exchange_field_deadline
        PackField.ESTIMATE -> Res.string.exchange_field_estimate
    },
)

// --- Rapport (côté manager) -------------------------------------------------------------------------------

@Composable
private fun reportTitle(plan: ReportMergePlan): String =
    if (plan.accepted) stringResource(Res.string.exchange_report_title, plan.member?.name.orEmpty()) else stringResource(Res.string.exchange_report_refused_title)

@Composable
private fun ReportBody(plan: ReportMergePlan, titles: Map<String, String>) {
    val language = Locale.current.language
    val zone = TimeZone.currentSystemDefault()
    when (plan.refusal) {
        ReportRefusal.WRONG_TEAM -> Text(stringResource(Res.string.exchange_report_refused_wrong_team), style = MaterialTheme.typography.bodyMedium)
        ReportRefusal.UNKNOWN_MEMBER -> Text(stringResource(Res.string.exchange_report_refused_unknown_member), style = MaterialTheme.typography.bodyMedium)
        ReportRefusal.OLDER -> Text(
            stringResource(Res.string.exchange_report_refused_older, plan.lastReportAt?.let { Dates.short(it.toLocalDateTime(zone).date, language) }.orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
        )
        null -> Unit
    }
    if (!plan.accepted) return

    val reported = plan.reportedAt.toLocalDateTime(zone)
    Text(
        stringResource(Res.string.exchange_report_date, "${Dates.short(reported.date, language)} ${Dates.time(reported, language)}"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (plan.memberArchived) Flag(stringResource(Res.string.exchange_report_member_archived), flagged = true, style = MaterialTheme.typography.bodyMedium)
    if (plan.updates.isEmpty() && plan.newSubtasks.isEmpty()) {
        Text(stringResource(Res.string.exchange_report_nothing), style = MaterialTheme.typography.bodyMedium)
    }

    if (plan.updates.isNotEmpty()) {
        Section(stringResource(Res.string.exchange_report_updates, plan.updates.size)) {
            plan.updates.forEach { UpdateBlock(it, language) }
        }
    }
    if (plan.newSubtasks.isNotEmpty()) {
        Section(stringResource(Res.string.exchange_report_subtasks, plan.newSubtasks.size)) {
            val own = plan.newSubtasks.associate { it.uid to it.title }
            plan.newSubtasks.forEach { s ->
                val parent = titles[s.parentUid] ?: own[s.parentUid]
                Line(if (parent != null) stringResource(Res.string.exchange_subtask_under, s.title, parent) else s.title)
            }
        }
    }
    if (plan.unchanged.isNotEmpty()) Text(stringResource(Res.string.exchange_report_unchanged, plan.unchanged.size), style = MaterialTheme.typography.bodyMedium)
    if (plan.ignored.isNotEmpty()) {
        Section(stringResource(Res.string.exchange_report_ignored, plan.ignored.size)) {
            plan.ignored.forEach { Line(ignoredText(it)) }
        }
    }
    Text(stringResource(Res.string.exchange_report_backup), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Une tâche mise à jour : son titre, « avancement reçu de l'ancien titulaire » le cas échéant, puis chaque changement de → vers. */
@Composable
private fun UpdateBlock(update: ReportTaskUpdate, language: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(update.after.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        if (update.formerHolder) Flag(stringResource(Res.string.exchange_former_holder), flagged = true)
        update.changes.forEach { change ->
            Text(changeText(change, language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun changeText(change: ReportChange, language: String): String {
    val none = stringResource(Res.string.exchange_value_none)
    return when (change) {
        is ReportChange.Status -> stringResource(Res.string.exchange_change_status, statusLabel(change.from), statusLabel(change.to))
        is ReportChange.Progress -> stringResource(
            Res.string.exchange_change_progress,
            change.from?.let { stringResource(Res.string.progress_percent, it) } ?: none,
            change.to?.let { stringResource(Res.string.progress_percent, it) } ?: none,
        )
        is ReportChange.Started -> stringResource(
            Res.string.exchange_change_started,
            change.from?.let { dayText(it, language) } ?: none,
            change.to?.let { dayText(it, language) } ?: none,
        )
        is ReportChange.Spent -> stringResource(Res.string.exchange_change_spent, change.from?.let { duration(it) } ?: none, duration(change.to))
    }
}

private fun dayText(date: LocalDate, language: String) = Dates.short(date, language)

@Composable
private fun statusLabel(status: TaskStatus): String = stringResource(
    when (status) {
        TaskStatus.TODO -> Res.string.exchange_status_todo
        TaskStatus.DONE -> Res.string.exchange_status_done
        TaskStatus.ARCHIVED -> Res.string.exchange_status_archived
    },
)

@Composable
private fun ignoredText(line: IgnoredReportLine): String = when (line.reason) {
    IgnoredReason.UNKNOWN_TASK -> stringResource(Res.string.exchange_ignored_unknown_task)
    IgnoredReason.UNKNOWN_PARENT -> stringResource(Res.string.exchange_ignored_unknown_parent, line.title.orEmpty())
    IgnoredReason.EMPTY_TITLE -> stringResource(Res.string.exchange_ignored_empty_title)
}

// --- Briques communes -------------------------------------------------------------------------------------

/** Intertitre d'une liste (titre de section pour les lecteurs d'écran) puis ses lignes. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.heading())
        content()
    }
}

/** Une ligne de liste : le titre, et sous lui ce qui change (secondaire). */
@Composable
private fun Line(text: String, detail: String = "") {
    Column {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
