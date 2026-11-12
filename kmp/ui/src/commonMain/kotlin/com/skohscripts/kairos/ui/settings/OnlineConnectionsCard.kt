package com.skohscripts.kairos.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.calendar.ImportedCalendars
import com.skohscripts.kairos.core.calendar.SkipReason
import com.skohscripts.kairos.core.connections.ConnectionError
import com.skohscripts.kairos.core.connections.SyncInterval
import com.skohscripts.kairos.core.model.BlockSource
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.IcsConfirmation
import com.skohscripts.kairos.ui.app.IcsPreviewResult
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.app.SecretLevel
import com.skohscripts.kairos.ui.app.uiLanguage
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.day.LabeledCheckbox
import com.skohscripts.kairos.ui.day.verticalScrollWithThumb
import com.skohscripts.kairos.ui.generated.resources.*
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosTextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

internal fun connectionErrorText(error: ConnectionError): StringResource = when (error) {
    ConnectionError.OFFLINE -> Res.string.online_error_offline
    ConnectionError.TLS -> Res.string.online_error_tls
    ConnectionError.AUTH -> Res.string.online_error_auth
    ConnectionError.REAUTH -> Res.string.online_error_reauth
    ConnectionError.GONE -> Res.string.online_error_gone
    ConnectionError.NOT_CALENDAR -> Res.string.online_error_calendar
    ConnectionError.TRUNCATED -> Res.string.online_error_truncated
    ConnectionError.TOO_BIG -> Res.string.online_error_big
    ConnectionError.UNAVAILABLE -> Res.string.online_error_unavailable
    ConnectionError.REDIRECT_HOST -> Res.string.online_error_redirect
    ConnectionError.INSECURE_REDIRECT -> Res.string.online_error_downgrade
    ConnectionError.NO_SECRET -> Res.string.online_error_secret
    ConnectionError.UNSUPPORTED -> Res.string.online_error_unsupported
}

private fun intervalText(interval: SyncInterval): StringResource = when (interval) {
    SyncInterval.MANUAL -> Res.string.online_manual
    SyncInterval.ON_OPEN -> Res.string.online_on_open
    SyncInterval.HOURLY -> Res.string.online_hourly
    SyncInterval.SIX_HOURS -> Res.string.online_six_hours
    SyncInterval.DAILY -> Res.string.online_daily
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnlineConnectionsCard(services: AppServices) {
    val online = services.online ?: return
    val connections by online.connections.collectAsState()
    val snapshot by services.repository.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    val language = uiLanguage()
    var menu by remember { mutableStateOf(false) }
    var phase by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    var replacing by rememberSaveable { mutableStateOf<Long?>(null) }
    var removing by rememberSaveable { mutableStateOf<Long?>(null) }
    var insecure by rememberSaveable { mutableStateOf(false) }
    var redirect by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmationKind by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmationHost by rememberSaveable { mutableStateOf("") }
    var failure by rememberSaveable { mutableStateOf<String?>(null) }
    var failureHost by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var hideTitles by rememberSaveable { mutableStateOf(false) }
    var preview by remember { mutableStateOf<IcsPreviewResult.Ready?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun close() {
        phase = ""
        preview = null
        link = ""
        replacing = null
        confirmationKind = null
    }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                messages(getString(Res.string.file_error))
            } finally {
                busy = false
            }
        }
    }
    fun readLink() = action {
        val result = online.previewIcs(link, insecure, redirect)
        when (result) {
            is IcsPreviewResult.Ready -> {
                preview = result
                if (phase != "preview") {
                    val source = snapshot.calendarSources.firstOrNull { it.id == replacing }
                    name = source?.name ?: result.name
                    hideTitles = source?.hideTitles ?: ImportedCalendars.named(snapshot, name)?.hideTitles ?: false
                }
                phase = "preview"
                confirmationKind = null
            }
            is IcsPreviewResult.NeedsConfirmation -> {
                confirmationKind = result.kind.name
                confirmationHost = result.host
            }
            is IcsPreviewResult.Failed -> {
                failure = result.error.name
                failureHost = result.host.orEmpty()
                phase = "link"
            }
        }
    }
    // Rebuild the transient plan after rotation; the editable fields remain saved.
    LaunchedEffect(phase) {
        if (phase == "preview" && preview == null && !busy) readLink()
    }
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(KairosIcons.Sync, stringResource(Res.string.online_title), stringResource(Res.string.online_body))
        if (online.secretLevel == SecretLevel.FILE) Text(stringResource(Res.string.online_file_secrets), style = MaterialTheme.typography.bodyMedium)
        Box {
            KairosOutlinedButton(enabled = !busy, onClick = { menu = true }) { Text(stringResource(Res.string.online_add_connection)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.online_ics)) }, onClick = {
                    menu = false
                    replacing = null
                    link = ""
                    insecure = false
                    redirect = null
                    phase = if (connections.isEmpty()) "activation" else "link"
                })
            }
        }
        connections.forEach { connection ->
            val source = snapshot.calendarSources.firstOrNull { it.id == connection.sourceId } ?: return@forEach
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(source.name, style = MaterialTheme.typography.titleMedium)
                val synced = connection.lastSyncAt?.toLocalDateTime(TimeZone.currentSystemDefault())
                Text(
                    if (synced == null) stringResource(Res.string.online_never_synced, connection.host.orEmpty())
                    else stringResource(Res.string.online_synced, connection.host.orEmpty(), Dates.short(synced.date, language), Dates.time(synced, language)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                connection.lastError?.let { error ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(KairosIcons.Warning, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column {
                            Text(stringResource(connectionErrorText(error)), style = MaterialTheme.typography.bodyMedium)
                            if (error == ConnectionError.OFFLINE && synced != null) Text(stringResource(Res.string.online_events_date, Dates.short(synced.date, language)), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                SwitchRow(connection.syncEnabled, stringResource(Res.string.online_sync)) {
                    action { online.setSyncEnabled(source.id, it) }
                }
                var frequencyMenu by remember(connection.sourceId) { mutableStateOf(false) }
                Box {
                    KairosTextButton(enabled = !busy, onClick = { frequencyMenu = true }) { Text(stringResource(intervalText(connection.interval))) }
                    DropdownMenu(frequencyMenu, onDismissRequest = { frequencyMenu = false }) {
                        SyncInterval.entries.forEach { interval ->
                            DropdownMenuItem(text = { Text(stringResource(intervalText(interval))) }, onClick = {
                                frequencyMenu = false
                                action { online.setInterval(source.id, interval) }
                            })
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KairosTextButton(enabled = !busy, onClick = { action {
                        online.syncNow(source.id)
                        val error = online.connections.value.firstOrNull { it.sourceId == source.id }?.lastError
                        messages(getString(error?.let(::connectionErrorText) ?: Res.string.online_sync_done))
                    } }) { Text(stringResource(Res.string.online_sync_now)) }
                    if (connection.lastError == ConnectionError.AUTH || connection.lastError == ConnectionError.GONE ||
                        connection.lastError == ConnectionError.NO_SECRET || connection.lastError == ConnectionError.REDIRECT_HOST) {
                        KairosTextButton(enabled = !busy, onClick = {
                            replacing = source.id
                            link = ""
                            insecure = false
                            redirect = null
                            phase = "link"
                        }) { Text(stringResource(Res.string.online_replace)) }
                    }
                    KairosTextButton(enabled = !busy, onClick = { action { online.disconnect(source.id) } }) { Text(stringResource(Res.string.online_disconnect)) }
                    KairosTextButton(enabled = !busy, onClick = { removing = source.id }) { Text(stringResource(Res.string.calendars_remove)) }
                }
            }
        }
    }
    if (phase == "activation") AlertDialog(
        onDismissRequest = ::close,
        title = { Text(stringResource(Res.string.online_ics)) },
        text = { Text(stringResource(Res.string.online_activation)) },
        confirmButton = { KairosTextButton(onClick = { phase = "link" }) { Text(stringResource(Res.string.online_activate)) } },
        dismissButton = { KairosTextButton(onClick = ::close) { Text(stringResource(Res.string.action_cancel)) } },
    )
    if (phase == "link") AlertDialog(
        onDismissRequest = { if (!busy) close() },
        title = { Text(stringResource(if (replacing == null) Res.string.online_ics else Res.string.online_replace)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(link, { link = it; insecure = false; redirect = null }, label = { Text(stringResource(Res.string.online_link)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            Text(stringResource(Res.string.online_link_help), style = MaterialTheme.typography.bodyMedium)
        } },
        confirmButton = { KairosTextButton(enabled = !busy && link.isNotBlank(), onClick = { readLink() }) { Text(stringResource(Res.string.online_preview)) } },
        dismissButton = { KairosTextButton(enabled = !busy, onClick = ::close) { Text(stringResource(Res.string.action_cancel)) } },
    )
    confirmationKind?.let { kind -> AlertDialog(
        onDismissRequest = { confirmationKind = null },
        title = { Text(confirmationHost) },
        text = { Text(if (kind == IcsConfirmation.INSECURE_HTTP.name) stringResource(Res.string.online_insecure) else stringResource(Res.string.online_redirect, confirmationHost)) },
        confirmButton = { KairosTextButton(enabled = !busy, onClick = {
            if (kind == IcsConfirmation.INSECURE_HTTP.name) insecure = true else redirect = confirmationHost
            confirmationKind = null
            readLink()
        }) { Text(stringResource(if (kind == IcsConfirmation.INSECURE_HTTP.name) Res.string.online_use_anyway else Res.string.online_follow)) } },
        dismissButton = { KairosTextButton(onClick = { confirmationKind = null }) { Text(stringResource(Res.string.action_cancel)) } },
    ) }
    if (phase == "preview") preview?.let { ready ->
        val existing = ImportedCalendars.named(snapshot, name)
        val attaches = existing != null && connections.none { it.sourceId == existing.id }
        AlertDialog(
            onDismissRequest = { if (!busy) close() },
            title = { Text(stringResource(Res.string.online_preview)) },
            text = { Column(Modifier.verticalScrollWithThumb(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ready.host, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(name, { name = it.take(60) }, enabled = !busy && replacing == null, label = { Text(stringResource(Res.string.calendar_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (replacing == null) LabeledCheckbox(hideTitles, { hideTitles = it }, stringResource(Res.string.calendar_hide_titles))
                val first = ready.plan.first
                val last = ready.plan.last
                Text(if (first == null || last == null) stringResource(Res.string.calendar_events_none) else stringResource(Res.string.calendar_events, ready.plan.events.size, Dates.short(first, language), Dates.short(last, language, first)), style = MaterialTheme.typography.bodyMedium)
                if (ready.plan.days.isNotEmpty()) Text(stringResource(Res.string.calendar_days, ready.plan.days.size), style = MaterialTheme.typography.bodyMedium)
                ready.plan.skipped.forEach { (reason, count) ->
                    val text = when (reason) {
                        SkipReason.FREE -> Res.string.calendar_skipped_free
                        SkipReason.CANCELLED -> Res.string.calendar_skipped_cancelled
                        SkipReason.PAST -> Res.string.calendar_skipped_past
                        SkipReason.BEYOND_HORIZON -> Res.string.calendar_skipped_beyond
                        SkipReason.UNSUPPORTED_RULE -> Res.string.calendar_skipped_rule
                        SkipReason.UNKNOWN_ZONE -> Res.string.calendar_skipped_zone
                        SkipReason.INVALID -> Res.string.calendar_skipped_invalid
                        SkipReason.TOO_MANY -> Res.string.calendar_skipped_too_many
                    }
                    Text(if (reason == SkipReason.UNKNOWN_ZONE) stringResource(text, count, ready.plan.unknownZones.joinToString(", ")) else stringResource(text, count), style = MaterialTheme.typography.bodyMedium)
                }
                if (replacing == null && (attaches || ready.existingWithoutConnection && name == ready.name)) Text(stringResource(Res.string.online_attach), style = MaterialTheme.typography.bodyMedium)
            } },
            confirmButton = { KairosTextButton(enabled = !busy && name.isNotBlank(), onClick = { action {
                val id = replacing
                if (id == null) {
                    if (online.addIcs(ready, name.trim(), hideTitles) != null) close()
                } else {
                    when (val result = online.replaceIcsLink(id, link, insecure, redirect)) {
                        is IcsPreviewResult.Ready -> close()
                        is IcsPreviewResult.Failed -> { failure = result.error.name; failureHost = result.host.orEmpty() }
                        is IcsPreviewResult.NeedsConfirmation -> { confirmationKind = result.kind.name; confirmationHost = result.host }
                    }
                }
            } }) { Text(stringResource(if (replacing == null) Res.string.online_add else Res.string.online_replace)) } },
            dismissButton = { KairosTextButton(enabled = !busy, onClick = ::close) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    failure?.let { error -> AlertDialog(
        onDismissRequest = { failure = null },
        title = { Text(stringResource(Res.string.online_title)) },
        text = { Column { Text(failureHost); Text(stringResource(connectionErrorText(ConnectionError.valueOf(error)))) } },
        confirmButton = { KairosTextButton(onClick = { failure = null }) { Text(stringResource(Res.string.calendar_close)) } },
    ) }
    removing?.let { id ->
        snapshot.calendarSources.firstOrNull { it.id == id }?.let { source ->
            val count = snapshot.timeBlocks.count { (it.source as? BlockSource.Imported)?.calendarId == id }
            AlertDialog(
                onDismissRequest = { if (!busy) removing = null },
                title = { Text(stringResource(Res.string.calendars_remove_title, source.name, count)) },
                text = { Text(stringResource(Res.string.calendars_remove_body)) },
                confirmButton = { KairosTextButton(enabled = !busy, onClick = { action { online.remove(id); removing = null } }) { Text(stringResource(Res.string.calendars_remove), color = MaterialTheme.colorScheme.error) } },
                dismissButton = { KairosTextButton(enabled = !busy, onClick = { removing = null }) { Text(stringResource(Res.string.action_cancel)) } },
            )
        }
    }
}
