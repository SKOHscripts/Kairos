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
import com.skohscripts.kairos.ui.generated.resources.import_confirm_title
import com.skohscripts.kairos.ui.generated.resources.import_done
import com.skohscripts.kairos.ui.generated.resources.import_error_corrupted
import com.skohscripts.kairos.ui.generated.resources.import_error_not_export
import com.skohscripts.kairos.ui.generated.resources.import_error_too_new
import com.skohscripts.kairos.ui.generated.resources.web_authorize
import com.skohscripts.kairos.ui.generated.resources.web_link_create
import com.skohscripts.kairos.ui.generated.resources.web_link_open
import com.skohscripts.kairos.ui.generated.resources.web_link_open_confirm_body
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
 * Bandeau du fichier lié (version web seulement, docs/spec/export-import.md
 * § Version web). Vue Jour : seulement quand il y a quelque chose à faire
 * ([showWhenLinked] faux). Réglages : toujours, état « lié » compris.
 * Bandeau neutre (une dégradation n'est pas un danger), sauf l'échec
 * d'enregistrement, en conteneur d'erreur (charte § Bannières).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageBanner(services: AppServices, showWhenLinked: Boolean) {
    val storage = services.linkedStorage ?: return
    val state by storage.state.collectAsState()
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    var opened by remember { mutableStateOf<Pair<OpenedFile, KairosSnapshot>?>(null) }

    val text: String
    var error = false
    val actions = mutableListOf<Pair<String, () -> Unit>>()
    val openAction = stringResource(Res.string.web_link_open) to {
        scope.launch {
            val file = storage.openFile() ?: return@launch
            try {
                opened = file to ExportCodec.decode(file.text)
            } catch (e: ImportException) {
                messages(importErrorMessage(e))
            }
        }
        Unit
    }
    when (val s = state) {
        LinkedFileState.Unsupported -> text = stringResource(Res.string.web_unsupported)
        LinkedFileState.NotLinked -> {
            text = stringResource(Res.string.web_not_linked)
            actions += stringResource(Res.string.web_link_create) to { scope.launch { storage.createFile() }; Unit }
            actions += openAction
        }
        is LinkedFileState.Linked -> {
            if (!showWhenLinked) return
            text = stringResource(Res.string.web_linked, s.name)
            actions += openAction
        }
        is LinkedFileState.NeedsPermission -> {
            text = stringResource(if (s.restore) Res.string.web_restore else Res.string.web_reauthorize, s.name)
            actions += stringResource(Res.string.web_authorize) to { scope.launch { storage.authorize() }; Unit }
        }
        is LinkedFileState.SaveFailed -> {
            text = stringResource(Res.string.web_save_failed, s.detail)
            error = true
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
                Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
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

    opened?.let { (file, snapshot) ->
        AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text(stringResource(Res.string.import_confirm_title)) },
            text = { Text(stringResource(Res.string.web_link_open_confirm_body, file.name)) },
            confirmButton = {
                KairosTextButton(onClick = {
                    opened = null
                    scope.launch {
                        if (storage.adoptOpened()) {
                            services.repository.replaceAll(snapshot)
                            messages(getString(Res.string.import_done, snapshot.tasks.size))
                        }
                    }
                }) { Text(stringResource(Res.string.import_confirm_action)) }
            },
            dismissButton = { KairosTextButton(onClick = { opened = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}
