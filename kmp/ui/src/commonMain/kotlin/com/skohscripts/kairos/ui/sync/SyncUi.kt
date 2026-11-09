package com.skohscripts.kairos.ui.sync

import com.skohscripts.kairos.ui.day.verticalScrollWithThumb
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.sync.Drift
import com.skohscripts.kairos.core.sync.MergeOutcome
import com.skohscripts.kairos.core.sync.RecordKind
import com.skohscripts.kairos.core.sync.SnapshotMerge
import com.skohscripts.kairos.core.sync.Stamp
import com.skohscripts.kairos.core.sync.SyncData
import com.skohscripts.kairos.core.sync.Synced
import com.skohscripts.kairos.data.ExportCodec
import com.skohscripts.kairos.data.ImportException
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.SyncReviewItem
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.LinkedFileState
import com.skohscripts.kairos.ui.app.LinkedMerge
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.app.importErrorMessage
import com.skohscripts.kairos.ui.app.saveBackup
import com.skohscripts.kairos.ui.app.uiLanguage
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.action_ok
import com.skohscripts.kairos.ui.generated.resources.action_save
import com.skohscripts.kairos.ui.generated.resources.field_deadline
import com.skohscripts.kairos.ui.generated.resources.field_description
import com.skohscripts.kairos.ui.generated.resources.field_minutes
import com.skohscripts.kairos.ui.generated.resources.field_pin_time
import com.skohscripts.kairos.ui.generated.resources.field_project
import com.skohscripts.kairos.ui.generated.resources.field_recurrence
import com.skohscripts.kairos.ui.generated.resources.field_scheduled
import com.skohscripts.kairos.ui.generated.resources.field_title
import com.skohscripts.kairos.ui.generated.resources.field_type
import com.skohscripts.kairos.ui.generated.resources.file_error
import com.skohscripts.kairos.ui.generated.resources.points_label
import com.skohscripts.kairos.ui.generated.resources.priority_label
import com.skohscripts.kairos.ui.generated.resources.sync_clock_ahead
import com.skohscripts.kairos.ui.generated.resources.sync_device
import com.skohscripts.kairos.ui.generated.resources.sync_last_merge
import com.skohscripts.kairos.ui.generated.resources.sync_merge
import com.skohscripts.kairos.ui.generated.resources.sync_merge_file
import com.skohscripts.kairos.ui.generated.resources.sync_merge_now
import com.skohscripts.kairos.ui.generated.resources.sync_merged
import com.skohscripts.kairos.ui.generated.resources.sync_preview_body
import com.skohscripts.kairos.ui.generated.resources.sync_preview_nothing
import com.skohscripts.kairos.ui.generated.resources.sync_preview_title
import com.skohscripts.kairos.ui.generated.resources.sync_rename
import com.skohscripts.kairos.ui.generated.resources.sync_rename_hint
import com.skohscripts.kairos.ui.generated.resources.sync_rename_title
import com.skohscripts.kairos.ui.generated.resources.sync_review_applied
import com.skohscripts.kairos.ui.generated.resources.sync_review_banner
import com.skohscripts.kairos.ui.generated.resources.sync_review_button
import com.skohscripts.kairos.ui.generated.resources.sync_review_delete_again
import com.skohscripts.kairos.ui.generated.resources.sync_review_empty
import com.skohscripts.kairos.ui.generated.resources.sync_review_intro
import com.skohscripts.kairos.ui.generated.resources.sync_review_keep
import com.skohscripts.kairos.ui.generated.resources.sync_review_keep_all
import com.skohscripts.kairos.ui.generated.resources.sync_review_kept
import com.skohscripts.kairos.ui.generated.resources.sync_review_other
import com.skohscripts.kairos.ui.generated.resources.sync_review_see
import com.skohscripts.kairos.ui.generated.resources.sync_review_settings
import com.skohscripts.kairos.ui.generated.resources.sync_review_take
import com.skohscripts.kairos.ui.generated.resources.sync_review_title
import com.skohscripts.kairos.ui.generated.resources.sync_review_value
import com.skohscripts.kairos.ui.generated.resources.sync_status
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/*
 * Interface de la synchronisation (docs/spec/synchronisation.md § Interface) : section de la carte Données, bandeau
 * « À revoir », dialogue « À revoir », fusion d'un fichier choisi à la main avec son aperçu, message après une fusion.
 */

/** Longueur maximale du nom de l'appareil (le dépôt le borne aussi). */
private const val LABEL_MAX = 30

/** Le nom d'un appareil d'après son identité : celui qu'il s'est donné, sinon ses 4 premiers caractères. */
private fun deviceName(device: String, d: SyncData?): String = when {
    d != null && device == d.device -> d.label.ifEmpty { device.take(LinkedMerge.LABEL_SUFFIX) }
    else -> d?.labels?.get(device)?.takeIf { it.isNotEmpty() } ?: device.take(LinkedMerge.LABEL_SUFFIX)
}

/** Active la synchronisation au besoin, avec le nom de l'appareil (« Android · 3f9c »), comme le fichier lié. */
private suspend fun ensureSync(services: AppServices): SyncData {
    val repository = services.repository
    repository.sync.value?.let { return it }
    val d = repository.enableSync(services.deviceLabel)
    repository.renameDevice("${services.deviceLabel} · ${d.device.take(LinkedMerge.LABEL_SUFFIX)}")
    return repository.sync.value ?: d
}

/** Une valeur canonique telle qu'on la lit : sans guillemets JSON, « — » pour rien. */
internal fun shownValue(v: String): String = when {
    v.isEmpty() || v == "null" || v == "\"\"" -> "—"
    v.length >= 2 && v.first() == '"' && v.last() == '"' -> v.substring(1, v.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
    else -> v
}

/** Le libellé d'un champ dans la langue de l'interface (ceux des fiches) ; un champ sans libellé garde son nom. */
@Composable
private fun fieldLabel(field: String): String {
    val res: StringResource? = when (field) {
        "title" -> Res.string.field_title
        "description" -> Res.string.field_description
        "priority" -> Res.string.priority_label
        "fibonacciPoints" -> Res.string.points_label
        "deadline" -> Res.string.field_deadline
        "estimatedMinutes" -> Res.string.field_minutes
        "projectTag" -> Res.string.field_project
        "taskType" -> Res.string.field_type
        "scheduledDate" -> Res.string.field_scheduled
        "recurrence" -> Res.string.field_recurrence
        "pinnedStart" -> Res.string.field_pin_time
        else -> null
    }
    return res?.let { stringResource(it) } ?: field
}

@Composable
private fun stampText(stamp: Stamp?, d: SyncData?): String {
    if (stamp == null) return "—"
    val language = uiLanguage()
    val at = Instant.fromEpochMilliseconds(stamp.hlc.wall).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${deviceName(stamp.device, d)}, ${Dates.short(at.date, language)} ${Dates.time(at, language)}"
}

/**
 * La synchronisation dans la carte Données : ce que fait le fichier lié, la dernière fusion, le nom de cet appareil,
 * « Fusionner maintenant », « À revoir (N) ». Rien tant que la base ne synchronise pas et qu'aucun fichier n'est lié.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncDataSection(services: AppServices) {
    val repository = services.repository
    val sync by repository.sync.collectAsState()
    val last by repository.lastMerge.collectAsState()
    val review by repository.syncReview.collectAsState()
    val linked = services.linkedStorage?.state?.collectAsState()?.value is LinkedFileState.Linked
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    val d = sync ?: if (!linked) return else null
    val language = uiLanguage()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (linked) Text(stringResource(Res.string.sync_status), style = MaterialTheme.typography.bodyMedium)
        last?.let { m ->
            val at = m.at.toLocalDateTime(TimeZone.currentSystemDefault())
            Text(
                stringResource(Res.string.sync_last_merge, Dates.short(at.date, language), Dates.time(at, language), m.added, m.modified, m.removed),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        // Horloge en avance d'un appareil (une marque reçue à plus d'un jour dans le futur) : dérivé du contexte, tant que l'heure
        // ne l'a pas rattrapée. À surveiller, donc icône et texte (jamais une teinte).
        if (d != null && Drift.ahead(d, services.clock.now().toEpochMilliseconds())) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(Res.string.sync_clock_ahead), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (d != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.sync_device, deviceName(d.device, d)), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.weight(1f))
                KairosTextButton(onClick = { renaming = true }) { Text(stringResource(Res.string.sync_rename)) }
            }
        }
        if (linked || review.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (linked) KairosOutlinedButton(onClick = { scope.launch { services.linkedStorage?.mergeNow() } }) { Text(stringResource(Res.string.sync_merge_now)) }
                if (review.isNotEmpty()) KairosOutlinedButton(onClick = { reviewing = true }) { Text(stringResource(Res.string.sync_review_button, review.size)) }
            }
        }
    }
    if (renaming && d != null) RenameDeviceDialog(d.label, onDismiss = { renaming = false }) { name ->
        renaming = false
        scope.launch { repository.renameDevice(name) }
    }
    if (reviewing) SyncReviewDialog(services) { reviewing = false }
}

@Composable
private fun RenameDeviceDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sync_rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.sync_rename_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(LABEL_MAX) },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.sync_rename_title)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { KairosTextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(Res.string.action_save)) } },
        dismissButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/**
 * Le bandeau « À revoir » de la vue Jour : neutre, « Voir » ([onSee]) ouvre le dialogue, que l'écran garde hors du bandeau pour
 * qu'il reste ouvert quand la liste se vide (« Rien à revoir. »). Rien si la liste est vide.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncReviewBanner(services: AppServices, onSee: () -> Unit) {
    val review by services.repository.syncReview.collectAsState()
    if (review.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainerHigh, contentColor = scheme.onSurface, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(Res.string.sync_review_banner, review.size), style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(Modifier.padding(start = 32.dp)) {
                KairosButton(onClick = onSee) { Text(stringResource(Res.string.sync_review_see)) }
            }
        }
    }
}

/**
 * « À revoir » (docs/spec/synchronisation.md § Modifier la même chose sur deux appareils) : une carte « outlined » par
 * conflit, les deux valeurs avec leur appareil et leur heure, la valeur appliquée marquée d'une icône et d'un texte ;
 * « Garder celle-ci » / « Prendre l'autre » ; un élément conservé après une suppression faite ailleurs : « Supprimer à
 * nouveau » ou le garder ; « Tout garder le plus récent » vide la liste.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncReviewDialog(services: AppServices, onDismiss: () -> Unit) {
    val repository = services.repository
    val review by repository.syncReview.collectAsState()
    val sync by repository.sync.collectAsState()
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sync_review_title)) },
        text = {
            Column(Modifier.verticalScrollWithThumb(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (review.isEmpty()) {
                    Text(stringResource(Res.string.sync_review_empty), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(Res.string.sync_review_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                review.forEach { item -> ReviewCard(item, sync, repository, scope) }
            }
        },
        confirmButton = {
            if (review.isNotEmpty()) {
                KairosTextButton(onClick = { scope.launch { repository.clearReview() }; onDismiss() }) { Text(stringResource(Res.string.sync_review_keep_all)) }
            } else {
                KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_ok)) }
            }
        },
        dismissButton = if (review.isNotEmpty()) ({ KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } }) else null,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(item: SyncReviewItem, d: SyncData?, repository: KairosRepository, scope: kotlinx.coroutines.CoroutineScope) {
    OutlinedCard(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val title = if (item.ref == null) stringResource(Res.string.sync_review_settings) else item.title.ifEmpty { "—" }
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            if (item.kept) {
                Text(stringResource(Res.string.sync_review_kept, title, deviceName(item.lost?.device.orEmpty(), d)), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KairosOutlinedButton(onClick = { scope.launch { repository.deleteKept(item.id) } }) { Text(stringResource(Res.string.sync_review_delete_again)) }
                    KairosTextButton(onClick = { scope.launch { repository.resolveReview(item.id, takeOther = false) } }) { Text(stringResource(Res.string.sync_review_keep)) }
                }
            } else {
                Text(fieldLabel(item.field), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // La valeur appliquée : icône et mot, jamais la couleur seule.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                    Icon(KairosIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(Res.string.sync_review_value, stringResource(Res.string.sync_review_applied), shownValue(item.keptValue), stampText(item.winner, d)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                    Icon(KairosIcons.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(Res.string.sync_review_value, stringResource(Res.string.sync_review_other), shownValue(item.lostValue), stampText(item.lost, d)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KairosOutlinedButton(onClick = { scope.launch { repository.resolveReview(item.id, takeOther = false) } }) { Text(stringResource(Res.string.sync_review_keep)) }
                    KairosOutlinedButton(onClick = { scope.launch { repository.resolveReview(item.id, takeOther = true) } }) { Text(stringResource(Res.string.sync_review_take)) }
                }
            }
        }
    }
}

/** Un fichier choisi à la main, décodé, et la fusion calculée sans être appliquée. */
private class MergePreview(val theirs: Synced, val outcome: MergeOutcome)

/**
 * « Fusionner un fichier Kairos… » (toutes les plateformes) : choisir un export, voir l'aperçu (ajouts, modifications,
 * suppressions, conflits), puis « Fusionner » ; le bouton reste désactivé et le dialogue ouvert pendant l'écriture. Une
 * sauvegarde `avant-fusion-…` précède toute fusion qui modifie ou supprime.
 */
@Composable
fun MergeFileButton(services: AppServices) {
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    var preview by remember { mutableStateOf<MergePreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    KairosOutlinedButton(onClick = {
        scope.launch {
            val text = runCatching { services.files.openText() }.getOrElse { return@launch messages(getString(Res.string.file_error)) } ?: return@launch
            val theirs = try {
                withContext(Dispatchers.Default) { ExportCodec.decodeSynced(text) }
            } catch (e: ImportException) {
                return@launch messages(importErrorMessage(e))
            }
            val d = ensureSync(services)
            val outcome = try {
                withContext(Dispatchers.Default) {
                    SnapshotMerge.merge(Synced(services.repository.snapshot.value, d), theirs, services.clock.now().toEpochMilliseconds())
                }
            } catch (e: IllegalArgumentException) {
                // Une marque à plus de 30 jours dans le futur : rien n'est fusionné.
                return@launch messages(getString(Res.string.file_error))
            }
            preview = MergePreview(theirs, outcome)
        }
    }) { Text(stringResource(Res.string.sync_merge_file)) }

    preview?.let { p ->
        val c = p.outcome.changes
        val tasks = RecordKind.TASK
        val others = RecordKind.entries.filter { it != tasks }.sumOf { (c.added[it] ?: 0) + (c.modified[it] ?: 0) + (c.removed[it] ?: 0) }
        val nothing = c.isEmpty && p.outcome.conflicts.isEmpty()
        AlertDialog(
            onDismissRequest = { if (!busy) preview = null },
            title = { Text(stringResource(Res.string.sync_preview_title)) },
            text = {
                Text(
                    if (nothing) {
                        stringResource(Res.string.sync_preview_nothing)
                    } else {
                        stringResource(Res.string.sync_preview_body, c.added[tasks] ?: 0, c.modified[tasks] ?: 0, c.removed[tasks] ?: 0, others, p.outcome.conflicts.size)
                    },
                )
            },
            confirmButton = {
                KairosTextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        // Le fichier lié, s'il y en a un, reçoit le résultat comme une modification locale (`LinkedFileSync.changed`).
                        services.repository.merge(p.theirs) { saveBackup(services, "avant-fusion", messages) }
                        busy = false
                        preview = null
                    }
                }) { Text(stringResource(Res.string.sync_merge)) }
            },
            dismissButton = { KairosTextButton(enabled = !busy, onClick = { preview = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/**
 * Message d'une ligne après chaque fusion qui a changé quelque chose ici (fichier lié ou fichier choisi) : la coquille le
 * montre tant que l'application est affichée. La valeur présente à l'ouverture ne déclenche rien.
 */
@Composable
fun SyncMergeMessages(services: AppServices) {
    val messages = LocalMessages.current
    val last by services.repository.lastMerge.collectAsState()
    val seen = remember { mutableStateOf(last?.atMillis) }
    LaunchedEffect(last) {
        val m = last ?: return@LaunchedEffect
        if (m.atMillis == seen.value) return@LaunchedEffect
        seen.value = m.atMillis
        messages(getString(Res.string.sync_merged, m.added, m.modified, m.removed))
    }
}
