package com.skohscripts.kairos.ui.settings

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.skohscripts.kairos.core.calendar.CalendarPlan
import com.skohscripts.kairos.ui.guide.coachHighlight
import com.skohscripts.kairos.ui.guide.SettingsTourPanel
import com.skohscripts.kairos.ui.guide.SettingsTourEffect
import com.skohscripts.kairos.ui.guide.SettingsTour
import com.skohscripts.kairos.ui.generated.resources.update_see_latest
import com.skohscripts.kairos.ui.generated.resources.update_android_help
import com.skohscripts.kairos.core.settings.UiLanguage
import com.skohscripts.kairos.core.updates.UpdateCheck
import androidx.compose.ui.platform.LocalUriHandler
import com.skohscripts.kairos.ui.generated.resources.action_revert
import com.skohscripts.kairos.ui.generated.resources.guide_reset_done
import com.skohscripts.kairos.ui.generated.resources.guide_reset_action
import com.skohscripts.kairos.ui.generated.resources.guide_reset_body
import com.skohscripts.kairos.ui.generated.resources.guide_reset_title
import com.skohscripts.kairos.ui.app.LocalUndoMessages
import com.skohscripts.kairos.core.model.GuideSettings
import com.skohscripts.kairos.ui.guide.GuideSection
import com.skohscripts.kairos.ui.guide.GuideSpot
import com.skohscripts.kairos.ui.app.uiLanguage
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import com.skohscripts.kairos.ui.app.heading
import androidx.compose.foundation.layout.Arrangement
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
import com.skohscripts.kairos.core.settings.FieldKind
import com.skohscripts.kairos.ui.generated.resources.action_save
import com.skohscripts.kairos.ui.generated.resources.settings_error_dip
import com.skohscripts.kairos.ui.generated.resources.settings_error_workday
import com.skohscripts.kairos.ui.generated.resources.settings_intro
import com.skohscripts.kairos.ui.generated.resources.settings_not_saved
import com.skohscripts.kairos.ui.generated.resources.settings_reset
import com.skohscripts.kairos.ui.generated.resources.settings_saved
import com.skohscripts.kairos.ui.generated.resources.settings_restore_action
import com.skohscripts.kairos.ui.generated.resources.settings_restore_body
import com.skohscripts.kairos.ui.generated.resources.settings_restore_title
import com.skohscripts.kairos.ui.generated.resources.settings_unsaved
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.skohscripts.kairos.ui.generated.resources.settings_cat_general_hint_updates
import com.skohscripts.kairos.ui.generated.resources.settings_cat_has_error
import org.jetbrains.compose.resources.StringResource
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import com.skohscripts.kairos.ui.app.fileStamp
import com.skohscripts.kairos.ui.app.replaceWithBackup
import com.skohscripts.kairos.ui.app.StorageBanner
import com.skohscripts.kairos.ui.sync.MergeFileButton
import com.skohscripts.kairos.ui.sync.SyncDataSection
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
import com.skohscripts.kairos.ui.generated.resources.settings_discover_entry
import com.skohscripts.kairos.ui.generated.resources.settings_discover_entry_hint
import com.skohscripts.kairos.ui.generated.resources.settings_about_entry_hint
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Réglages (docs/spec/reglages.md) : un seul formulaire (validation par champ, un seul « Enregistrer » dans une barre
 * fixée en bas), montré par catégories ([SettingsCategory]) : la liste, puis une catégorie à la fois ; les deux côte à
 * côte dès [SETTINGS_TWO_PANE_MIN]. L'état du formulaire vit ici, au-dessus des catégories : une modification survit
 * au passage d'une catégorie à l'autre. Rien n'est enregistré tant qu'un champ est invalide.
 */
@Composable
fun SettingsScreen(services: AppServices, nav: SettingsNav = remember { SettingsNav() }, onDiscover: () -> Unit = {}, onOpenAbout: () -> Unit) {
    val snapshot by services.repository.snapshot.collectAsState()
    val current = snapshot.settings
    val language = uiLanguage()
    // Décimales à la virgule en français (« 0,8 », comme l'aide l'écrit) ; la validation lit les deux.
    // Le formulaire ignore ce que le guide a retenu (issue #94) : une écriture du guide (« Revoir le guide… ») ne remet pas
    // à zéro des modifications non enregistrées.
    val formKey = current.copy(guide = null)
    val saved = remember(formKey, language) {
        SettingsForm.values(current).mapValues { (key, value) ->
            if (!language.startsWith("en") && SettingsForm.field(key).kind == FieldKind.DECIMAL) value.replace('.', ',') else value
        }
    }
    var values by remember(formKey) { mutableStateOf(saved) }
    var errors by remember(formKey) { mutableStateOf<Map<String, FieldError>>(emptyMap()) }
    var general by remember(formKey) { mutableStateOf<GeneralError?>(null) }
    // « Restaurer les valeurs par défaut » remet aussi le guide à zéro, à l'enregistrement : il suffit à rendre le
    // formulaire modifié quand le guide a retenu quelque chose (docs/spec/reglages.md).
    var restored by remember(formKey) { mutableStateOf(false) }
    val dirty = values != saved || (restored && current.guide != null)
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current

    // Réglages valides en attente de confirmation : masquer l'espace Équipe demande de confirmer.
    var confirmHide by remember(formKey) { mutableStateOf<Settings?>(null) }

    fun write(settings: Settings) {
        scope.launch {
            // Le texte d'abord : masquer l'espace Équipe retire cet écran de la composition dès l'écriture faite.
            val done = getString(Res.string.settings_saved)
            services.repository.updateSettings(settings)
            messages(done)
        }
    }

    val reveal = remember { ErrorReveal() }

    fun save() {
        val result = SettingsForm.validate(values, current)
        errors = result.fieldErrors
        general = result.general
        val settings = result.settings?.let { if (restored) it.copy(guide = null) else it }
        when {
            settings == null -> scope.launch {
                // Ouvrir la catégorie du premier champ refusé (dans l'ordre des catégories, sinon celle de la règle
                // violée), puis y défiler : la catégorie doit d'abord être composée et mesurée, d'où les deux images.
                val first = result.fieldErrors.keys.minByOrNull { SettingsCategory.of(it).ordinal }
                (first?.let { SettingsCategory.of(it) } ?: result.general?.let { SettingsCategory.of(it) })?.let { nav.category = it }
                launch { withFrameNanos { }; withFrameNanos { }; runCatching { reveal.reveal(first ?: ErrorReveal.GENERAL) } }
                messages(getString(Res.string.settings_not_saved))
            }
            current.teamModeEnabled && !settings.teamModeEnabled -> confirmHide = settings
            else -> write(settings)
        }
    }

    val onChange: (String, String) -> Unit = { key, value ->
        values = values + (key to value)
        errors = errors - key
    }
    // Catégories à signaler dans la liste : un champ refusé, ou la règle entre champs violée.
    val flagged = remember(errors, general) { errors.keys.map { SettingsCategory.of(it) }.toSet() + setOfNotNull(general?.let { SettingsCategory.of(it) }) }

    val categoryContent: @Composable (SettingsCategory, Boolean) -> Unit = { category, twoPane ->
        Column(
            verticalArrangement = Arrangement.spacedBy(KairosSpacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(KairosSpacing.l),
        ) {
            // Sur deux volets, la barre du haut dit « Réglages » : le titre de la catégorie est en tête de son volet.
            if (twoPane) Text(stringResource(category.title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth().heading())
            general?.takeIf { SettingsCategory.of(it) == category }?.let {
                Box(Modifier.fillMaxWidth().bringIntoViewRequester(reveal.requester(ErrorReveal.GENERAL))) { GeneralErrorBanner(it) }
            }
            CompositionLocalProvider(LocalErrorReveal provides reveal) {
                when (category) {
                    SettingsCategory.GENERAL -> {
                        LanguageCard(values["language"].orEmpty()) { onChange("language", it) }
                        GuideSpot(GuideSection.SETTINGS_APPEARANCE) {
                            AppearanceCard(
                                values["themeMode"].orEmpty(), values["themeColor"].orEmpty(), errors["themeColor"],
                                onModeChange = { onChange("themeMode", it) },
                                zoom = values["textZoom"].orEmpty(), onZoomChange = { onChange("textZoom", it) },
                            ) { onChange("themeColor", it) }
                        }
                        services.updates?.let { updates ->
                            UpdatesCard(updates, values["updateCheckEnabled"].orEmpty()) { onChange("updateCheckEnabled", it) }
                        }
                        if (services.updates == null && services.downloadPage) DownloadPageCard()
                        services.shortcuts?.let { ShortcutCard(it) }
                    }
                    SettingsCategory.DAY, SettingsCategory.PLANNING, SettingsCategory.TRACKING -> SettingsFormCards(category, values, errors, onChange, learningStatus = { ModelStatusLine(services) }, lingerStatus = { manual -> LingerStatusLine(services, manual) })
                    SettingsCategory.TEAM -> TeamSettingsCard(values, errors, savedEnabled = current.teamModeEnabled, services = services, onChange = onChange)
                    SettingsCategory.DATA -> {
                        GuideSpot(GuideSection.SETTINGS_DATA) { DataCard(services) }
                        CalendarsCard(services)
                        if (services.online != null) OnlineConnectionsCard(services)
                        // Bureau : dossier des données choisi (docs/spec/bureau-avance.md).
                        services.dataFolder?.let { DataFolderCard(it) }
                        RestoreDefaultsCard {
                            // Décimales à la virgule en français, comme à l'ouverture de l'écran ; rien n'est écrit avant « Enregistrer ».
                            values = SettingsForm.restoreDefaults(values).mapValues { (key, value) ->
                                if (!language.startsWith("en") && SettingsForm.field(key).kind == FieldKind.DECIMAL) value.replace('.', ',') else value
                            }
                            errors = emptyMap()
                            general = null
                            restored = true
                        }
                    }
                    SettingsCategory.ABOUT -> {
                        // « Découvrir Kairos » (docs/spec/visite-guidee.md) : le hub des chapitres, rouvert à tout moment.
                        SettingsLinkCard(KairosIcons.PlayArrow, Res.string.settings_discover_entry, Res.string.settings_discover_entry_hint, onDiscover)
                        SettingsLinkCard(KairosIcons.Info, Res.string.settings_about_entry, Res.string.settings_about_entry_hint, onOpenAbout)
                        GuideResetCard(services, current.guide)
                        // Bureau : journal de diagnostic (docs/spec/bureau-avance.md).
                        services.diagnostics?.let { DiagnosticsCard(it) }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Mesuré sur la largeur offerte à l'écran, pas sur la fenêtre : le rail en prend déjà 80 dp.
            val twoPane = maxWidth >= SETTINGS_TWO_PANE_MIN
            SideEffect { nav.singlePane = !twoPane }
            val shown = nav.category ?: SettingsCategory.GENERAL.takeIf { twoPane }
            val generalHint = if (services.updates != null || services.downloadPage) Res.string.settings_cat_general_hint_updates else SettingsCategory.GENERAL.hint
            val list: @Composable () -> Unit = {
                SettingsCategoryList(selected = shown.takeIf { twoPane }, twoPane = twoPane, flagged = flagged, generalHint = generalHint) { nav.category = it }
            }
            if (twoPane) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(SETTINGS_LIST_WIDTH).fillMaxHeight().verticalScroll(rememberScrollState()).padding(KairosSpacing.l)) { list() }
                    VerticalDivider()
                    // Une catégorie choisie s'ouvre en haut de son volet (un défilement par catégorie).
                    key(shown) {
                        Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                            categoryContent(shown ?: SettingsCategory.GENERAL, true)
                        }
                    }
                }
            } else {
                key(shown) {
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                        if (shown == null) {
                            Box(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(KairosSpacing.l)) { list() }
                        } else {
                            categoryContent(shown, false)
                        }
                    }
                }
            }
        }
        // Visite des Réglages (issue #98) : son panneau au-dessus de la barre, qu'il cerne à la dernière étape.
        SettingsTourEffect(nav)
        SettingsTourPanel(nav)
        val tourOnSave = nav.tourStep?.let { SettingsTour.STEPS.getOrNull(it)?.step?.save } == true
        Box(Modifier.coachHighlight(tourOnSave, coaching = nav.tourStep != null)) {
            SaveBar(dirty, onReset = { values = saved; errors = emptyMap(); general = null; restored = false }, onSave = ::save)
        }
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
 * Liste des catégories (docs/spec/reglages.md) : phrase d'introduction, puis une ligne par catégorie (icône, titre,
 * résumé) dans une carte « filled ». Sur deux volets, la ligne choisie est en conteneur de sélection (charte) et se
 * lit comme un onglet ; sur un seul, chaque ligne ouvre sa catégorie (chevron). Une catégorie qui contient une
 * erreur le dit (icône d'avertissement, jamais la couleur seule).
 */
@Composable
private fun SettingsCategoryList(
    selected: SettingsCategory?,
    twoPane: Boolean,
    flagged: Set<SettingsCategory>,
    generalHint: StringResource,
    onOpen: (SettingsCategory) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KairosSpacing.m)) {
        Text(stringResource(Res.string.settings_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(KairosSpacing.s)) {
                SettingsCategory.entries.forEach { category ->
                    val isSelected = category == selected
                    val hasError = category in flagged
                    val action = if (twoPane) {
                        Modifier.selectable(isSelected, role = Role.Tab) { onOpen(category) }
                    } else {
                        Modifier.clickable(role = Role.Button) { onOpen(category) }
                    }
                    val content = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                    ListItem(
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            headlineColor = content,
                            leadingIconColor = if (isSelected) content else MaterialTheme.colorScheme.onSurfaceVariant,
                            supportingColor = if (isSelected) content else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        leadingContent = { Icon(category.icon, contentDescription = null) },
                        headlineContent = { Text(stringResource(category.title)) },
                        supportingContent = { Text(stringResource(if (category == SettingsCategory.GENERAL) generalHint else category.hint)) },
                        trailingContent = if (hasError || !twoPane) {
                            {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
                                    if (hasError) {
                                        Icon(KairosIcons.Warning, contentDescription = stringResource(Res.string.settings_cat_has_error), tint = MaterialTheme.colorScheme.error)
                                    }
                                    if (!twoPane) Icon(KairosIcons.ChevronRight, contentDescription = null)
                                }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).then(action).testTag("settings-category-${category.name}"),
                    )
                }
            }
        }
    }
}

/** Ligne qui ouvre un autre écran (« Découvrir Kairos », « À propos et guide ») : carte cliquable, icône, chevron. */
@Composable
private fun SettingsLinkCard(icon: ImageVector, title: StringResource, hint: StringResource, onClick: () -> Unit) {
    SettingsClickableCard(onClick = onClick) {
        ListItem(
            // Le fond de la ligne est celui de la carte « filled », pas la surface par défaut du ListItem.
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(icon, contentDescription = null) },
            headlineContent = { Text(stringResource(title)) },
            supportingContent = { Text(stringResource(hint)) },
            trailingContent = { Icon(KairosIcons.ChevronRight, contentDescription = null) },
        )
    }
}

/**
 * « Revoir le guide depuis le début » (docs/spec/visite-guidee.md § Persistance, issue #94) : efface aussitôt ce que le guide
 * a retenu, sans passer par « Enregistrer » (ce n'est pas un réglage) ; « Annuler » rend l'état d'avant. Inactif tant que
 * le guide n'a rien retenu.
 */
@Composable
private fun GuideResetCard(services: AppServices, memory: GuideSettings?) {
    val scope = rememberCoroutineScope()
    val undo = LocalUndoMessages.current
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(KairosIcons.Refresh, stringResource(Res.string.guide_reset_title), stringResource(Res.string.guide_reset_body))
        KairosOutlinedButton(enabled = memory != null, onClick = {
            val before = memory ?: return@KairosOutlinedButton
            scope.launch {
                services.repository.resetGuide()
                undo(getString(Res.string.guide_reset_done), getString(Res.string.action_revert)) {
                    scope.launch { services.repository.updateGuide { before } }
                }
            }
        }) { Text(stringResource(Res.string.guide_reset_action)) }
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
    val language = uiLanguage()
    @Composable
    fun at(instant: Instant): String {
        val t = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${Dates.dayMonth(t.date, language)} ${Dates.time(t, language)}"
    }
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(KairosIcons.Update, stringResource(Res.string.settings_section_updates), stringResource(Res.string.update_help))
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

/**
 * Mises à jour d'un APK installé à la main (docs/spec/mises-a-jour.md § Android, issue #143) : la version installée et
 * « Voir la dernière version », qui ouvre la page de téléchargement dans le navigateur. Aucun appel réseau, aucune permission.
 */
@Composable
private fun DownloadPageCard() {
    val uriHandler = LocalUriHandler.current
    val language = uiLanguage()
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(KairosIcons.Update, stringResource(Res.string.settings_section_updates), stringResource(Res.string.update_android_help))
        Text(stringResource(Res.string.update_installed, KairosBuild.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
        KairosOutlinedButton(onClick = { runCatching { uriHandler.openUri(UpdateCheck.downloadPage(language)) } }, contentPadding = KairosButtonIconPadding) {
            Icon(KairosIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.update_see_latest), modifier = Modifier.padding(start = 6.dp))
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
 * « Restaurer les valeurs par défaut » : remplit le formulaire des valeurs par défaut (hors espace Équipe), sans rien
 * enregistrer : « Enregistrer » reste nécessaire, « Annuler les modifications » revient en arrière.
 */
@Composable
private fun RestoreDefaultsCard(onRestore: () -> Unit) {
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(KairosIcons.SettingsBackupRestore, stringResource(Res.string.settings_restore_title), stringResource(Res.string.settings_restore_body))
        KairosOutlinedButton(onClick = onRestore) { Text(stringResource(Res.string.settings_restore_action)) }
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
    var pending by remember { mutableStateOf<ImportedFile.Full?>(null) }
    // Paquet ou rapport d'équipe reconnu par « Importer » : aperçu avant toute écriture (docs/spec/equipe-echanges.md).
    var exchange by remember { mutableStateOf<ExchangePreview?>(null) }
    var calendar by remember { mutableStateOf<CalendarPlan?>(null) }
    // Origines des tâches reçues : « Renvoyer l'avancement… » n'apparaît qu'avec elles (sinon rien ne change en mode solo).
    val stored by services.repository.snapshot.collectAsState()
    val origins = remember(stored) { ReceivedTasks.origins(stored) }

    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(KairosIcons.Database, stringResource(Res.string.data_title), stringResource(Res.string.data_body))
        services.dataLocation?.let {
            Text(
                stringResource(Res.string.data_location, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StorageBanner(services, showWhenLinked = true)
        // Synchronisation (docs/spec/synchronisation.md § Interface) : ce que fait le fichier lié, dernière fusion, appareil.
        SyncDataSection(services)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KairosOutlinedButton(onClick = {
                scope.launch {
                    val text = ExportCodec.encode(services.repository.snapshot.value, KairosBuild.VERSION_NAME, services.clock.now())
                    val saved = runCatching { services.files.saveText("kairos-export-${fileStamp(services)}.json", text) }
                    saved.onSuccess { if (it) { services.diagnostics?.log("export ok"); messages(getString(Res.string.export_done)) } }
                        .onFailure { services.diagnostics?.log("export failed"); messages(getString(Res.string.file_error)) }
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
                        when (val file = withContext(Dispatchers.Default) { readImportedFile(services, text) }) {
                            is ImportedFile.Full -> pending = file
                            is ImportedFile.Exchange -> exchange = file.preview
                            is ImportedFile.Calendar -> calendar = file.plan
                        }
                    } catch (e: ImportException) {
                        services.diagnostics?.log("import ${e.reason.name.lowercase()}")
                        messages(importErrorMessage(e))
                    }
                }
            }, contentPadding = KairosButtonIconPadding) {
                Icon(KairosIcons.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.data_import), modifier = Modifier.padding(start = 6.dp))
            }
            // Toutes les plateformes, Firefox et Safari compris (où le fichier lié n'existe pas).
            MergeFileButton(services)
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
    }

    pending?.let { file ->
        val snapshot = file.snapshot
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(Res.string.import_confirm_title)) },
            text = { Text(stringResource(Res.string.import_confirm_body)) },
            confirmButton = {
                KairosTextButton(onClick = {
                    pending = null
                    scope.launch {
                        if (replaceWithBackup(services, snapshot, messages, file.uids)) {
                            messages(getString(Res.string.import_done, snapshot.tasks.size))
                        }
                    }
                }) { Text(stringResource(Res.string.import_confirm_action)) }
            },
            dismissButton = { KairosTextButton(onClick = { pending = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    calendar?.let { plan -> CalendarImportDialog(services, plan, onDone = { calendar = null }) }
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
}
