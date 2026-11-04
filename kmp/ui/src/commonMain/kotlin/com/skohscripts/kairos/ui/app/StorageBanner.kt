package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
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
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.ExportCodec
import com.skohscripts.kairos.data.ImportException
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.import_confirm_action
import com.skohscripts.kairos.ui.generated.resources.import_done
import com.skohscripts.kairos.ui.generated.resources.import_error_corrupted
import com.skohscripts.kairos.ui.generated.resources.import_error_not_export
import com.skohscripts.kairos.ui.generated.resources.import_error_too_new
import com.skohscripts.kairos.ui.generated.resources.linked_unlink
import com.skohscripts.kairos.ui.generated.resources.linked_intro
import com.skohscripts.kairos.ui.generated.resources.linked_overwrite
import com.skohscripts.kairos.ui.generated.resources.sync_clock
import com.skohscripts.kairos.ui.generated.resources.sync_first_contact
import com.skohscripts.kairos.ui.generated.resources.sync_merge
import com.skohscripts.kairos.ui.generated.resources.sync_open_body
import com.skohscripts.kairos.ui.generated.resources.sync_open_title
import com.skohscripts.kairos.ui.generated.resources.sync_too_new
import com.skohscripts.kairos.ui.generated.resources.sync_unreadable
import com.skohscripts.kairos.ui.generated.resources.linked_write_failed
import com.skohscripts.kairos.ui.generated.resources.linked_retry
import com.skohscripts.kairos.ui.generated.resources.web_authorize
import com.skohscripts.kairos.ui.generated.resources.web_link_create
import com.skohscripts.kairos.ui.generated.resources.web_link_open
import com.skohscripts.kairos.ui.generated.resources.web_linked
import com.skohscripts.kairos.ui.generated.resources.web_not_linked
import com.skohscripts.kairos.ui.generated.resources.web_reauthorize
import com.skohscripts.kairos.ui.generated.resources.web_restore
import com.skohscripts.kairos.ui.generated.resources.web_save_failed
import com.skohscripts.kairos.ui.generated.resources.web_unsupported
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Messages d'échec d'import, communs à l'import manuel et au fichier lié. */
internal suspend fun importErrorMessage(e: ImportException): String = getString(
    when (e.reason) {
        ImportException.Reason.NOT_AN_EXPORT -> Res.string.import_error_not_export
        ImportException.Reason.TOO_NEW -> Res.string.import_error_too_new
        ImportException.Reason.CORRUPTED -> Res.string.import_error_corrupted
    },
)

/**
 * Bandeau du fichier lié (docs/spec/export-import.md § Version web, docs/spec/synchronisation.md § Interface). Version web :
 * vue Jour seulement quand il y a quelque chose à faire ([showWhenLinked] faux), Réglages toujours. Bureau et Android : la
 * carte Données propose et montre le lien ; la vue Jour ne montre que le premier rapprochement à confirmer, un fichier
 * illisible ou une écriture en échec. Bandeau neutre (une dégradation n'est pas un danger), sauf les échecs, en conteneur
 * d'erreur (charte § Bannières).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageBanner(services: AppServices, showWhenLinked: Boolean) {
    val storage = services.linkedStorage ?: return
    val state by storage.state.collectAsState()
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    // Fichier ouvert (« Ouvrir un fichier Kairos ») : « Fusionner » ou « Remplacer » quand cet appareil a déjà des données.
    var opened by remember { mutableStateOf<Pending?>(null) }

    val text: String
    var error = false
    var warning = true
    val actions = mutableListOf<Pair<String, () -> Unit>>()
    val openAction = stringResource(Res.string.web_link_open) to {
        scope.launch {
            val file = storage.openFile() ?: return@launch
            val snapshot = try {
                ExportCodec.decode(file.text)
            } catch (e: ImportException) {
                return@launch messages(importErrorMessage(e))
            }
            // Un appareil neuf (exemples intacts seulement) reçoit le fichier sans qu'on lui demande.
            if (services.repository.hasOwnData()) opened = Pending(file, snapshot) else storage.adoptOpened(merge = true)
        }
        Unit
    }
    val unlinkAction = stringResource(Res.string.linked_unlink) to { scope.launch { storage.unlink() }; Unit }
    when (val s = state) {
        LinkedFileState.Unsupported -> text = stringResource(Res.string.web_unsupported)
        LinkedFileState.NotLinked -> {
            // Bureau et Android : les données sont déjà dans un fichier ; on propose sans insister (carte Données seule).
            if (!storage.browser && !showWhenLinked) return
            text = stringResource(if (storage.browser) Res.string.web_not_linked else Res.string.linked_intro)
            warning = storage.browser
            actions += stringResource(Res.string.web_link_create) to { scope.launch { storage.createFile() }; Unit }
            actions += openAction
        }
        is LinkedFileState.Linked -> {
            if (!showWhenLinked) return
            text = stringResource(Res.string.web_linked, s.name)
            warning = false
            actions += openAction
            actions += unlinkAction
        }
        is LinkedFileState.NeedsPermission -> {
            text = stringResource(if (s.restore) Res.string.web_restore else Res.string.web_reauthorize, s.name)
            actions += stringResource(Res.string.web_authorize) to { scope.launch { storage.authorize() }; Unit }
        }
        is LinkedFileState.SaveFailed -> {
            text = stringResource(Res.string.web_save_failed, s.detail)
            error = true
        }
        is LinkedFileState.NeedsConfirmation -> {
            val c = s.summary
            text = stringResource(Res.string.sync_first_contact, s.name, c.hereOnly, c.fileOnly, c.different, c.others)
            actions += stringResource(Res.string.sync_merge) to { scope.launch { storage.confirmMerge() }; Unit }
            actions += stringResource(Res.string.action_cancel) to { scope.launch { storage.cancelMerge() }; Unit }
        }
        is LinkedFileState.Unreadable -> {
            text = stringResource(
                when (s.reason) {
                    UnreadableReason.NOT_READABLE -> Res.string.sync_unreadable
                    UnreadableReason.TOO_NEW -> Res.string.sync_too_new
                    UnreadableReason.CLOCK -> Res.string.sync_clock
                },
                s.name,
            )
            error = true
            actions += stringResource(Res.string.linked_retry) to { scope.launch { storage.retry() }; Unit }
            // Écraser un fichier d'une version plus récente perdrait ce que cette version ne sait pas lire.
            if (s.reason != UnreadableReason.TOO_NEW) actions += stringResource(Res.string.linked_overwrite) to { scope.launch { storage.overwrite() }; Unit }
            actions += unlinkAction
        }
        is LinkedFileState.WriteFailed -> {
            text = stringResource(Res.string.linked_write_failed, s.name)
            error = true
            actions += stringResource(Res.string.linked_retry) to { scope.launch { storage.retry() }; Unit }
            actions += unlinkAction
        }
    }

    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (error) scheme.errorContainer else scheme.surfaceContainerHigh,
        contentColor = if (error) scheme.onErrorContainer else scheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(if (warning || error) KairosIcons.Warning else KairosIcons.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            if (actions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 32.dp)) {
                    actions.forEachIndexed { index, (label, action) ->
                        if (index == 0) KairosButton(onClick = action) { Text(label) } else KairosOutlinedButton(onClick = action) { Text(label) }
                    }
                }
            }
        }
    }

    opened?.let { pending ->
        AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text(stringResource(Res.string.sync_open_title, pending.file.name)) },
            text = { Text(stringResource(Res.string.sync_open_body)) },
            confirmButton = {
                KairosTextButton(onClick = {
                    opened = null
                    scope.launch { storage.adoptOpened(merge = true) }
                }) { Text(stringResource(Res.string.sync_merge)) }
            },
            dismissButton = {
                Row {
                    KairosTextButton(onClick = { opened = null }) { Text(stringResource(Res.string.action_cancel)) }
                    KairosTextButton(onClick = {
                        opened = null
                        scope.launch {
                            // Comme « Importer » : sauvegarde d'abord ; le fichier devient ensuite la référence du lien.
                            if (!storage.adoptOpened(merge = false)) return@launch
                            if (replaceWithBackup(services, pending.snapshot, messages)) {
                                messages(getString(Res.string.import_done, pending.snapshot.tasks.size))
                            }
                        }
                    }) { Text(stringResource(Res.string.import_confirm_action)) }
                }
            },
        )
    }
}

/** Un fichier ouvert et décodé, en attente du choix « Fusionner » ou « Remplacer ». */
private class Pending(val file: OpenedFile, val snapshot: KairosSnapshot)
