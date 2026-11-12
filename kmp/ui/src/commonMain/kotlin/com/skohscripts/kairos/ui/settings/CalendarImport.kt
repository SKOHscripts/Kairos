package com.skohscripts.kairos.ui.settings

import com.skohscripts.kairos.ui.day.verticalScrollWithThumb
import com.skohscripts.kairos.ui.generated.resources.calendars_phone_choose
import com.skohscripts.kairos.ui.generated.resources.calendars_phone_empty
import com.skohscripts.kairos.ui.generated.resources.calendars_phone_settings
import com.skohscripts.kairos.ui.generated.resources.calendars_phone_denied
import com.skohscripts.kairos.ui.generated.resources.calendars_phone_switch
import com.skohscripts.kairos.ui.generated.resources.calendars_phone_help
import com.skohscripts.kairos.ui.generated.resources.calendars_phone_title
import com.skohscripts.kairos.ui.app.DeviceCalendarState
import com.skohscripts.kairos.ui.app.CalendarService
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skohscripts.kairos.core.calendar.CalendarImport
import com.skohscripts.kairos.core.calendar.CalendarPlan
import com.skohscripts.kairos.core.calendar.ImportedCalendar
import com.skohscripts.kairos.core.calendar.ImportedCalendars
import com.skohscripts.kairos.core.calendar.IcsParser
import com.skohscripts.kairos.core.calendar.SkipReason
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.app.saveBackup
import com.skohscripts.kairos.ui.app.uiLanguage
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.day.LabeledCheckbox
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.calendar_close
import com.skohscripts.kairos.ui.generated.resources.calendar_days
import com.skohscripts.kairos.ui.generated.resources.calendar_default_name
import com.skohscripts.kairos.ui.generated.resources.calendar_events
import com.skohscripts.kairos.ui.generated.resources.calendar_events_none
import com.skohscripts.kairos.ui.generated.resources.calendar_hide_titles
import com.skohscripts.kairos.ui.generated.resources.calendar_import_action
import com.skohscripts.kairos.ui.generated.resources.calendar_import_body
import com.skohscripts.kairos.ui.generated.resources.calendar_import_done
import com.skohscripts.kairos.ui.generated.resources.calendar_import_title
import com.skohscripts.kairos.ui.generated.resources.calendar_name
import com.skohscripts.kairos.ui.generated.resources.calendar_replaces
import com.skohscripts.kairos.ui.generated.resources.calendar_skipped_beyond
import com.skohscripts.kairos.ui.generated.resources.calendar_skipped_cancelled
import com.skohscripts.kairos.ui.generated.resources.calendar_skipped_free
import com.skohscripts.kairos.ui.generated.resources.calendar_skipped_invalid
import com.skohscripts.kairos.ui.generated.resources.calendar_skipped_past
import com.skohscripts.kairos.ui.generated.resources.calendar_skipped_rule
import com.skohscripts.kairos.ui.generated.resources.calendar_skipped_too_many
import com.skohscripts.kairos.ui.generated.resources.calendar_skipped_zone
import com.skohscripts.kairos.ui.generated.resources.calendars_body
import com.skohscripts.kairos.ui.generated.resources.calendars_body_online
import com.skohscripts.kairos.ui.generated.resources.calendars_help
import com.skohscripts.kairos.ui.generated.resources.calendars_import
import com.skohscripts.kairos.ui.generated.resources.calendars_not_calendar
import com.skohscripts.kairos.ui.generated.resources.calendars_reimport
import com.skohscripts.kairos.ui.generated.resources.calendars_remove
import com.skohscripts.kairos.ui.generated.resources.calendars_remove_body
import com.skohscripts.kairos.ui.generated.resources.calendars_remove_title
import com.skohscripts.kairos.ui.generated.resources.calendars_removed
import com.skohscripts.kairos.ui.generated.resources.calendars_row
import com.skohscripts.kairos.ui.generated.resources.calendars_title
import com.skohscripts.kairos.ui.generated.resources.online_synced
import com.skohscripts.kairos.ui.generated.resources.online_never_synced
import com.skohscripts.kairos.ui.generated.resources.file_error
import com.skohscripts.kairos.ui.generated.resources.import_error_corrupted
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

// Import d'un calendrier `.ics` (docs/spec/connexions-externes.md § Interface) : la carte « Calendriers » des Réglages
// (catégorie Données) et l'aperçu, avant toute écriture.

/** Le jour de l'appareil, pour l'horizon d'import et le « à venir » des calendriers. */
internal fun calendarToday(services: AppServices): LocalDate =
    services.clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/**
 * Analyse d'un fichier `.ics` hors du fil d'interface (un export de 5 000 événements ne fige rien) ; `null` si le texte n'est
 * pas un calendrier lisible.
 */
internal suspend fun planCalendar(services: AppServices, text: String): CalendarPlan? = withContext(Dispatchers.Default) {
    if (!IcsParser.isCalendar(text)) return@withContext null
    val zone = TimeZone.currentSystemDefault()
    runCatching { CalendarImport.plan(text, calendarToday(services), zone) }.getOrNull()
}

/**
 * Aperçu d'un calendrier : nom modifiable (il identifie le calendrier : le même nom remplace), « Masquer les titres » coché
 * comme lors du dernier import de ce calendrier, ce qui sera créé, ce qui est écarté et pourquoi, ce qui sera remplacé.
 * Rien n'est écrit avant « Importer » ; un fichier sans rien à importer n'a que « Fermer ». [presetName] : « Réimporter »
 * vise ce calendrier.
 */
@Composable
fun CalendarImportDialog(services: AppServices, plan: CalendarPlan, onDone: () -> Unit, presetName: String? = null) {
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    val language = uiLanguage()
    val defaultName = stringResource(Res.string.calendar_default_name)
    var name by remember(plan) { mutableStateOf(presetName ?: plan.name ?: defaultName) }
    val snapshot by services.repository.snapshot.collectAsState()
    val today = remember { calendarToday(services) }
    val existing = ImportedCalendars.named(snapshot, name)
    var hideTitles by remember(plan) { mutableStateOf(false) }
    // Import en cours : le dialogue reste ouvert jusqu'au bout, sinon sa portée de coroutines (annulée quand il quitte la
    // composition) interromprait l'écriture là où la base suspend (version web, worker SQL).
    var busy by remember { mutableStateOf(false) }
    // Le choix du dernier import de ce calendrier, repris quand le nom désigne un autre calendrier.
    LaunchedEffect(existing?.id) { hideTitles = existing?.hideTitles ?: false }
    val replaced = ImportedCalendars.replaced(snapshot, name, today)
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth(),
        ) {
            Column(Modifier.verticalScrollWithThumb().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.calendar_import_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(stringResource(Res.string.calendar_import_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!plan.isEmpty) {
                    OutlinedTextField(
                        name,
                        { name = it.take(NAME_MAX) },
                        label = { Text(stringResource(Res.string.calendar_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LabeledCheckbox(hideTitles, { hideTitles = it }, stringResource(Res.string.calendar_hide_titles))
                }
                val first = plan.events.minOfOrNull { it.start.date }
                val last = plan.events.maxOfOrNull { it.start.date }
                Text(
                    if (first == null || last == null) stringResource(Res.string.calendar_events_none)
                    else stringResource(Res.string.calendar_events, plan.events.size, Dates.short(first, language), Dates.short(last, language, first)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (plan.days.isNotEmpty()) Text(stringResource(Res.string.calendar_days, plan.days.size), style = MaterialTheme.typography.bodyMedium)
                SKIP_TEXTS.forEach { (reason, text) ->
                    plan.skipped[reason]?.let {
                        val line = if (reason == SkipReason.UNKNOWN_ZONE) stringResource(text, it, plan.unknownZones.joinToString(", ")) else stringResource(text, it)
                        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (replaced > 0 && !plan.isEmpty) Text(stringResource(Res.string.calendar_replaces, replaced, name.trim()), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (plan.isEmpty) {
                        KairosButton(onClick = onDone) { Text(stringResource(Res.string.calendar_close)) }
                        return@Row
                    }
                    KairosTextButton(enabled = !busy, onClick = onDone) { Text(stringResource(Res.string.action_cancel)) }
                    KairosButton(enabled = name.isNotBlank() && !busy, onClick = {
                        val chosen = name.trim()
                        val hide = hideTitles
                        busy = true
                        scope.launch {
                            try {
                                // Comme tout import : sans sauvegarde réussie, rien n'est écrit.
                                if (!saveBackup(services, "avant-import-calendrier", messages)) return@launch
                                val imported = runCatching { services.repository.importCalendar(chosen, hide, plan, today) }
                                if (imported.isFailure) {
                                    services.diagnostics?.log("import calendar failed")
                                    messages(getString(Res.string.file_error))
                                    return@launch
                                }
                                services.diagnostics?.log("import calendar ok events=${plan.events.size} days=${plan.days.size}")
                                messages(getString(Res.string.calendar_import_done, chosen, plan.events.size))
                            } finally {
                                onDone()
                            }
                        }
                    }) { Text(stringResource(Res.string.calendar_import_action)) }
                }
            }
        }
    }
}

/** Longueur d'un nom de calendrier : il s'affiche sur une ligne de badge. */
private const val NAME_MAX = 60

private val SKIP_TEXTS: List<Pair<SkipReason, StringResource>> = listOf(
    SkipReason.FREE to Res.string.calendar_skipped_free,
    SkipReason.CANCELLED to Res.string.calendar_skipped_cancelled,
    SkipReason.PAST to Res.string.calendar_skipped_past,
    SkipReason.BEYOND_HORIZON to Res.string.calendar_skipped_beyond,
    SkipReason.UNSUPPORTED_RULE to Res.string.calendar_skipped_rule,
    SkipReason.UNKNOWN_ZONE to Res.string.calendar_skipped_zone,
    SkipReason.INVALID to Res.string.calendar_skipped_invalid,
    SkipReason.TOO_MANY to Res.string.calendar_skipped_too_many,
)

/**
 * Carte « Calendriers » (Réglages → Données) : ce que devient un événement, comment obtenir le fichier, « Importer un
 * calendrier… », puis chaque calendrier importé avec « Réimporter » (l'aperçu vise ce calendrier) et « Retirer » (confirmé,
 * passé compris).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalendarsCard(services: AppServices) {
    val snapshot by services.repository.snapshot.collectAsState()
    val connections = services.online?.connections?.collectAsState()?.value.orEmpty()
    val today = calendarToday(services)
    val calendars = ImportedCalendars.of(snapshot, today)
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    val language = uiLanguage()
    var preview by remember { mutableStateOf<Pair<CalendarPlan, String?>?>(null) }
    var removing by remember { mutableStateOf<ImportedCalendar?>(null) }
    fun pick(target: String?) {
        scope.launch {
            val text = runCatching { services.files.openText() }.getOrElse {
                messages(getString(Res.string.file_error))
                return@launch
            } ?: return@launch
            val plan = planCalendar(services, text)
            if (plan == null) {
                messages(getString(if (IcsParser.isCalendar(text)) Res.string.import_error_corrupted else Res.string.calendars_not_calendar))
                return@launch
            }
            preview = plan to target
        }
    }
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(KairosIcons.Event, stringResource(Res.string.calendars_title), stringResource(if (services.online == null) Res.string.calendars_body else Res.string.calendars_body_online))
        Text(stringResource(Res.string.calendars_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        KairosOutlinedButton(onClick = { pick(null) }, contentPadding = KairosButtonIconPadding) {
            Icon(KairosIcons.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.calendars_import), modifier = Modifier.padding(start = 6.dp))
        }
        calendars.forEach { c ->
            val connection = connections.firstOrNull { it.sourceId == c.source.id }
            val synced = connection?.lastSyncAt?.toLocalDateTime(TimeZone.currentSystemDefault())
            Column {
                Text(c.source.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (connection == null) stringResource(
                        Res.string.calendars_row,
                        c.upcomingBlocks,
                        Dates.short(c.source.importedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date, language),
                    ) else if (synced == null) stringResource(Res.string.online_never_synced, connection.host.orEmpty())
                    else stringResource(Res.string.online_synced, connection.host.orEmpty(), Dates.short(synced.date, language), Dates.time(synced, language)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (connection == null) KairosTextButton(onClick = { pick(c.source.name) }) { Text(stringResource(Res.string.calendars_reimport)) }
                    KairosTextButton(onClick = { removing = c }) { Text(stringResource(Res.string.calendars_remove)) }
                }
            }
        }
    }
    services.calendars?.let { PhoneCalendarsCard(it) }
    preview?.let { (plan, target) -> CalendarImportDialog(services, plan, onDone = { preview = null }, presetName = target) }
    removing?.let { c ->
        val total = snapshot.timeBlocks.count { (it.source as? com.skohscripts.kairos.core.model.BlockSource.Imported)?.calendarId == c.source.id }
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(Res.string.calendars_remove_title, c.source.name, total)) },
            text = { Text(stringResource(Res.string.calendars_remove_body)) },
            confirmButton = {
                KairosTextButton(onClick = {
                    removing = null
                    scope.launch {
                        val online = services.online?.takeIf { connections.any { connection -> connection.sourceId == c.source.id } }
                        if (online == null) services.repository.removeCalendar(c.source.id) else online.remove(c.source.id)
                        messages(getString(Res.string.calendars_removed, c.source.name))
                    }
                }) { Text(stringResource(Res.string.calendars_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { KairosTextButton(onClick = { removing = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/**
 * Calendriers du téléphone (Android, docs/spec/connexions-externes.md § Étape 2) : l'interrupteur, qui seul demande
 * l'autorisation ; puis les calendriers du téléphone à cocher (aucun par défaut) ; autorisation retirée : le dire et
 * ouvrir les réglages du système.
 */
@Composable
private fun PhoneCalendarsCard(service: CalendarService) {
    val state by service.state.collectAsState()
    val scope = rememberCoroutineScope()
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(KairosIcons.Event, stringResource(Res.string.calendars_phone_title), stringResource(Res.string.calendars_phone_help))
        SwitchRow(state !is DeviceCalendarState.Off, stringResource(Res.string.calendars_phone_switch)) { on ->
            scope.launch { if (on) service.enable() else service.disable() }
        }
        when (val s = state) {
            DeviceCalendarState.Off -> Unit
            DeviceCalendarState.Denied -> {
                Text(stringResource(Res.string.calendars_phone_denied), style = MaterialTheme.typography.bodyMedium)
                KairosOutlinedButton(onClick = service::openSystemSettings) { Text(stringResource(Res.string.calendars_phone_settings)) }
            }
            is DeviceCalendarState.On -> {
                if (s.calendars.isEmpty()) {
                    Text(stringResource(Res.string.calendars_phone_empty), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(Res.string.calendars_phone_choose), style = MaterialTheme.typography.bodyMedium)
                    s.calendars.forEach { c ->
                        LabeledCheckbox(c.id in s.selected, { checked ->
                            scope.launch { service.select(if (checked) s.selected + c.id else s.selected - c.id) }
                        }, if (c.account.isBlank() || c.account == c.name) c.name else "${c.name} · ${c.account}")
                    }
                }
            }
        }
    }
}
