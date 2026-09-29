package com.skohscripts.kairos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.ExportCodec
import com.skohscripts.kairos.data.ImportException
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.app.StorageBanner
import com.skohscripts.kairos.ui.app.importErrorMessage
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.data_body
import com.skohscripts.kairos.ui.generated.resources.data_export
import com.skohscripts.kairos.ui.generated.resources.data_import
import com.skohscripts.kairos.ui.generated.resources.data_location
import com.skohscripts.kairos.ui.generated.resources.data_title
import com.skohscripts.kairos.ui.generated.resources.export_done
import com.skohscripts.kairos.ui.generated.resources.file_error
import com.skohscripts.kairos.ui.generated.resources.import_confirm_action
import com.skohscripts.kairos.ui.generated.resources.import_confirm_body
import com.skohscripts.kairos.ui.generated.resources.import_confirm_title
import com.skohscripts.kairos.ui.generated.resources.import_done
import com.skohscripts.kairos.ui.generated.resources.settings_about_entry
import com.skohscripts.kairos.ui.generated.resources.settings_about_entry_hint
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.screens.UnderConstruction
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Réglages : données (export/import), alertes du chrono, « À propos et guide » ; le reste au jalon M5. */
@Composable
fun SettingsScreen(services: AppServices, onOpenAbout: () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(16.dp),
        ) {
            DataCard(services)
            ChronoSettingsCard(services)
            OutlinedCard(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    leadingContent = { Icon(KairosIcons.Info, contentDescription = null) },
                    headlineContent = { Text(stringResource(Res.string.settings_about_entry)) },
                    supportingContent = { Text(stringResource(Res.string.settings_about_entry_hint)) },
                    trailingContent = { Icon(KairosIcons.ChevronRight, contentDescription = null) },
                )
            }
            UnderConstruction()
        }
    }
}

/**
 * Export et import (docs/spec-v3/export-import.md) : l'export écrit tout dans
 * un fichier JSON ; l'import remplace tout après confirmation, avec une
 * sauvegarde automatique juste avant.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataCard(services: AppServices) {
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    var pending by remember { mutableStateOf<KairosSnapshot?>(null) }

    fun stamp(): String {
        val t = services.clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        fun two(n: Int) = n.toString().padStart(2, '0')
        return "${t.year}${two(t.month.number)}${two(t.day)}-${two(t.hour)}${two(t.minute)}"
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.data_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.data_body), style = MaterialTheme.typography.bodyMedium)
            services.dataLocation?.let {
                Text(
                    stringResource(Res.string.data_location, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StorageBanner(services, showWhenLinked = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val text = ExportCodec.encode(services.repository.snapshot.value, KairosBuild.VERSION_NAME, services.clock.now())
                        val saved = runCatching { services.files.saveText("kairos-export-${stamp()}.json", text) }
                        saved.onSuccess { if (it) messages(getString(Res.string.export_done)) }
                            .onFailure { messages(getString(Res.string.file_error)) }
                    }
                }) {
                    Icon(KairosIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.data_export), modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        val text = runCatching { services.files.openText() }.getOrElse {
                            messages(getString(Res.string.file_error))
                            return@launch
                        } ?: return@launch
                        try {
                            pending = ExportCodec.decode(text)
                        } catch (e: ImportException) {
                            messages(importErrorMessage(e))
                        }
                    }
                }) {
                    Icon(KairosIcons.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.data_import), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }

    pending?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(Res.string.import_confirm_title)) },
            text = { Text(stringResource(Res.string.import_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        val current = ExportCodec.encode(services.repository.snapshot.value, KairosBuild.VERSION_NAME, services.clock.now())
                        runCatching { services.backups.save("avant-import-${stamp()}.json", current) }
                            .onFailure {
                                // Jamais d'import sans sauvegarde préalable.
                                messages(getString(Res.string.file_error))
                                return@launch
                            }
                        services.repository.replaceAll(snapshot)
                        messages(getString(Res.string.import_done, snapshot.tasks.size))
                    }
                }) { Text(stringResource(Res.string.import_confirm_action)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}
