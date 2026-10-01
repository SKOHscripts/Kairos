package com.skohscripts.kairos.ui.settings

import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import com.skohscripts.kairos.ui.app.heading
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.intl.Locale
import com.skohscripts.kairos.ui.app.ShortcutCard
import com.skohscripts.kairos.ui.app.UpdateService
import com.skohscripts.kairos.ui.app.UpdateStatus
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.settings_section_updates
import com.skohscripts.kairos.ui.generated.resources.update_available
import com.skohscripts.kairos.ui.generated.resources.update_check_now
import com.skohscripts.kairos.ui.generated.resources.update_checking
import com.skohscripts.kairos.ui.generated.resources.update_failed
import com.skohscripts.kairos.ui.generated.resources.update_help
import com.skohscripts.kairos.ui.generated.resources.update_installed
import com.skohscripts.kairos.ui.generated.resources.update_never
import com.skohscripts.kairos.ui.generated.resources.update_up_to_date
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import com.skohscripts.kairos.core.settings.FieldError
import com.skohscripts.kairos.core.settings.GeneralError
import com.skohscripts.kairos.core.settings.SettingsForm
import com.skohscripts.kairos.ui.generated.resources.action_save
import com.skohscripts.kairos.ui.generated.resources.settings_error_dip
import com.skohscripts.kairos.ui.generated.resources.settings_error_workday
import com.skohscripts.kairos.ui.generated.resources.settings_intro
import com.skohscripts.kairos.ui.generated.resources.settings_not_saved
import com.skohscripts.kairos.ui.generated.resources.settings_reset
import com.skohscripts.kairos.ui.generated.resources.settings_saved
import com.skohscripts.kairos.ui.generated.resources.settings_unsaved
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
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.skohscripts.kairos.ui.theme.KairosSpacing
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.ui.generated.resources.team_disable_body
import com.skohscripts.kairos.ui.generated.resources.team_disable_confirm
import com.skohscripts.kairos.ui.generated.resources.team_disable_title
import com.skohscripts.kairos.core.team.exchange.ReceivedTasks
import com.skohscripts.kairos.ui.team.ExchangePreview
import com.skohscripts.kairos.ui.team.ExchangePreviewDialog
import com.skohscripts.kairos.ui.team.ImportedFile
import com.skohscripts.kairos.ui.team.applyExchange
import com.skohscripts.kairos.ui.team.readImportedFile
import com.skohscripts.kairos.ui.team.sendReport
import com.skohscripts.kairos.ui.team.whoText
import com.skohscripts.kairos.ui.generated.resources.exchange_report_action
import com.skohscripts.kairos.ui.generated.resources.exchange_report_action_to
import com.skohscripts.kairos.ui.generated.resources.exchange_report_help
import com.skohscripts.kairos.data.ExportCodec
import com.skohscripts.kairos.data.ImportException
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.app.LegacyImportDialog
import com.skohscripts.kairos.ui.app.fileStamp
import com.skohscripts.kairos.ui.app.rememberLegacyImport
import com.skohscripts.kairos.ui.app.replaceWithBackup
import com.skohscripts.kairos.ui.generated.resources.legacy_import_action
import com.skohscripts.kairos.ui.generated.resources.legacy_import_hint
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Réglages (docs/spec/reglages.md) : le formulaire complet (une carte par
 * section, validation par champ, un seul « Enregistrer » dans une barre
 * fixée en bas), puis les données (export, import) et « À propos et guide ».
 * Rien n'est enregistré tant qu'un champ est invalide.
 */
@Composable
fun SettingsScreen(services: AppServices, onOpenAbout: () -> Unit) {
    val snapshot by services.repository.snapshot.collectAsState()
    val current = snapshot.settings
    val saved = remember(current) { SettingsForm.values(current) }
    var values by remember(current) { mutableStateOf(saved) }
    var errors by remember(current) { mutableStateOf<Map<String, FieldError>>(emptyMap()) }
    var general by remember(current) { mutableStateOf<GeneralError?>(null) }
    val dirty = values != saved
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current

    // Réglages valides en attente de confirmation : masquer l'espace Équipe demande de confirmer.
    var confirmHide by remember(current) { mutableStateOf<Settings?>(null) }

    fun write(settings: Settings) {
        scope.launch {
            // Le texte d'abord : masquer l'espace Équipe retire cet écran de la composition dès l'écriture faite.
            val done = getString(Res.string.settings_saved)
            services.repository.updateSettings(settings)
            messages(done)
        }
    }

    fun save() {
        val result = SettingsForm.validate(values, current)
        errors = result.fieldErrors
        general = result.general
        val settings = result.settings
        when {
            settings == null -> scope.launch { messages(getString(Res.string.settings_not_saved)) }
            current.teamModeEnabled && !settings.teamModeEnabled -> confirmHide = settings
            else -> write(settings)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                verticalArrangement = Arrangement.spacedBy(KairosSpacing.m),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(KairosSpacing.l),
            ) {
                Text(stringResource(Res.string.settings_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
                general?.let { GeneralErrorBanner(it) }
                SettingsFormCards(values, errors) { key, value ->
                    values = values + (key to value)
                    errors = errors - key
                }
                AppearanceCard(values["themeColor"].orEmpty(), errors["themeColor"]) {
                    values = values + ("themeColor" to it)
                    errors = errors - "themeColor"
                }
                TeamSettingsCard(values, errors, savedEnabled = current.teamModeEnabled, services = services) { key, value ->
                    values = values + (key to value)
                    errors = errors - key
                }
                services.updates?.let { updates ->
                    UpdatesCard(updates, values["updateCheckEnabled"].orEmpty()) { values = values + ("updateCheckEnabled" to it) }
                }
                services.shortcuts?.let { ShortcutCard(it) }
                DataCard(services)
                SettingsClickableCard(onClick = onOpenAbout) {
                    ListItem(
                        // Le fond de la ligne est celui de la carte « filled », pas la surface par défaut du ListItem.
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(KairosIcons.Info, contentDescription = null) },
                        headlineContent = { Text(stringResource(Res.string.settings_about_entry)) },
                        supportingContent = { Text(stringResource(Res.string.settings_about_entry_hint)) },
                        trailingContent = { Icon(KairosIcons.ChevronRight, contentDescription = null) },
                    )
                }
            }
        }
        SaveBar(dirty, onReset = { values = saved; errors = emptyMap(); general = null }, onSave = ::save)
    }

    confirmHide?.let { settings ->
        AlertDialog(
            onDismissRequest = { confirmHide = null },
            title = { Text(stringResource(Res.string.team_disable_title)) },
            text = { Text(stringResource(Res.string.team_disable_body)) },
            confirmButton = {
                KairosTextButton(onClick = {
                    confirmHide = null
                    write(settings)
                }) { Text(stringResource(Res.string.team_disable_confirm)) }
            },
            dismissButton = { KairosTextButton(onClick = { confirmHide = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/**
 * Mises à jour (bureau) : le réglage de vérification (enregistré avec le
 * reste du formulaire), la version installée, le résultat de la dernière
 * vérification et « Vérifier maintenant ».
 */
@Composable
private fun UpdatesCard(updates: UpdateService, enabled: String, onEnabled: (String) -> Unit) {
    val status by updates.status.collectAsState()
    val scope = rememberCoroutineScope()
    val language = Locale.current.language
    @Composable
    fun at(instant: Instant): String {
        val t = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${Dates.dayMonth(t.date, language)} ${Dates.time(t, language)}"
    }
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(stringResource(Res.string.settings_section_updates), stringResource(Res.string.update_help))
        SettingInput("updateCheckEnabled", enabled, null, onEnabled)
        Text(stringResource(Res.string.update_installed, KairosBuild.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
        val line = when (val s = status) {
            UpdateStatus.Never -> stringResource(Res.string.update_never)
            UpdateStatus.Checking -> stringResource(Res.string.update_checking)
            is UpdateStatus.UpToDate -> stringResource(Res.string.update_up_to_date, at(s.at))
            is UpdateStatus.Available -> stringResource(Res.string.update_available, s.version, at(s.at))
            is UpdateStatus.Failed -> stringResource(Res.string.update_failed, at(s.at))
        }
        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        KairosOutlinedButton(enabled = status != UpdateStatus.Checking, onClick = { scope.launch { runCatching { updates.check(force = true) } } }) {
            Text(stringResource(Res.string.update_check_now))
        }
    }
}

/** Règle inter-champs violée : bandeau en conteneur d'erreur (un vrai échec, charte), avec icône. */
@Composable
private fun GeneralErrorBanner(error: GeneralError) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(KairosIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                stringResource(if (error == GeneralError.WORKDAY_ORDER) Res.string.settings_error_workday else Res.string.settings_error_dip),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Barre fixée en bas : « Modifications non enregistrées », « Annuler les modifications », « Enregistrer ». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SaveBar(dirty: Boolean, onReset: () -> Unit, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (dirty) {
                Text(stringResource(Res.string.settings_unsaved), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                KairosTextButton(onClick = onReset) { Text(stringResource(Res.string.settings_reset)) }
            }
            KairosButton(enabled = dirty, onClick = onSave) { Text(stringResource(Res.string.action_save)) }
        }
    }
}

/**
 * Export et import (docs/spec/export-import.md) : l'export écrit tout dans
 * un fichier JSON ; l'import remplace tout après confirmation, avec une
 * sauvegarde automatique juste avant.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataCard(services: AppServices) {
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    var pending by remember { mutableStateOf<KairosSnapshot?>(null) }
    // Paquet ou rapport d'équipe reconnu par « Importer » : aperçu avant toute écriture (docs/spec/equipe-echanges.md).
    var exchange by remember { mutableStateOf<ExchangePreview?>(null) }
    val legacy = rememberLegacyImport(services)
    // Origines des tâches reçues : « Renvoyer l'avancement… » n'apparaît qu'avec elles (sinon rien ne change en mode solo).
    val stored by services.repository.snapshot.collectAsState()
    val origins = remember(stored) { ReceivedTasks.origins(stored) }

    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(stringResource(Res.string.data_title), stringResource(Res.string.data_body))
        services.dataLocation?.let {
            Text(
                stringResource(Res.string.data_location, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StorageBanner(services, showWhenLinked = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KairosOutlinedButton(onClick = {
                scope.launch {
                    val text = ExportCodec.encode(services.repository.snapshot.value, KairosBuild.VERSION_NAME, services.clock.now())
                    val saved = runCatching { services.files.saveText("kairos-export-${fileStamp(services)}.json", text) }
                    saved.onSuccess { if (it) messages(getString(Res.string.export_done)) }
                        .onFailure { messages(getString(Res.string.file_error)) }
                }
            }, contentPadding = KairosButtonIconPadding) {
                Icon(KairosIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.data_export), modifier = Modifier.padding(start = 6.dp))
            }
            KairosOutlinedButton(onClick = {
                scope.launch {
                    val text = runCatching { services.files.openText() }.getOrElse {
                        messages(getString(Res.string.file_error))
                        return@launch
                    } ?: return@launch
                    try {
                        when (val file = readImportedFile(services, text)) {
                            is ImportedFile.Full -> pending = file.snapshot
                            is ImportedFile.Exchange -> exchange = file.preview
                        }
                    } catch (e: ImportException) {
                        messages(importErrorMessage(e))
                    }
                }
            }, contentPadding = KairosButtonIconPadding) {
                Icon(KairosIcons.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.data_import), modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (origins.isNotEmpty()) {
            Text(stringResource(Res.string.exchange_report_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                origins.forEach { origin ->
                    KairosOutlinedButton(onClick = { scope.launch { sendReport(services, origin, messages) } }, contentPadding = KairosButtonIconPadding) {
                        Icon(KairosIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(if (origins.size == 1) Res.string.exchange_report_action else Res.string.exchange_report_action_to, whoText(origin)),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
        if (services.legacy != null) {
            Text(stringResource(Res.string.legacy_import_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            KairosOutlinedButton(onClick = legacy::pick, contentPadding = KairosButtonIconPadding) {
                Icon(KairosIcons.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.legacy_import_action), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }

    pending?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(Res.string.import_confirm_title)) },
            text = { Text(stringResource(Res.string.import_confirm_body)) },
            confirmButton = {
                KairosTextButton(onClick = {
                    pending = null
                    scope.launch {
                        if (replaceWithBackup(services, snapshot, messages)) {
                            messages(getString(Res.string.import_done, snapshot.tasks.size))
                        }
                    }
                }) { Text(stringResource(Res.string.import_confirm_action)) }
            },
            dismissButton = { KairosTextButton(onClick = { pending = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    exchange?.let { preview ->
        ExchangePreviewDialog(
            preview,
            onConfirm = {
                exchange = null
                scope.launch { applyExchange(services, preview, messages) }
            },
            onDismiss = { exchange = null },
        )
    }
    LegacyImportDialog(legacy)
}
