package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.skohscripts.kairos.ui.settings.SettingsCard
import com.skohscripts.kairos.ui.settings.SettingsCardHeader
import com.skohscripts.kairos.ui.theme.KairosSpacing
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.settings_section_shortcut
import com.skohscripts.kairos.ui.generated.resources.shortcut_add
import com.skohscripts.kairos.ui.generated.resources.shortcut_banner_elsewhere
import com.skohscripts.kairos.ui.generated.resources.shortcut_banner_menu
import com.skohscripts.kairos.ui.generated.resources.shortcut_banner_windows
import com.skohscripts.kairos.ui.generated.resources.shortcut_create
import com.skohscripts.kairos.ui.generated.resources.shortcut_created
import com.skohscripts.kairos.ui.generated.resources.shortcut_decline
import com.skohscripts.kairos.ui.generated.resources.shortcut_failed
import com.skohscripts.kairos.ui.generated.resources.shortcut_help_menu
import com.skohscripts.kairos.ui.generated.resources.shortcut_help_windows
import com.skohscripts.kairos.ui.generated.resources.shortcut_remove
import com.skohscripts.kairos.ui.generated.resources.shortcut_remove_failed
import com.skohscripts.kairos.ui.generated.resources.shortcut_removed
import com.skohscripts.kairos.ui.generated.resources.shortcut_status_created
import com.skohscripts.kairos.ui.generated.resources.shortcut_status_elsewhere
import com.skohscripts.kairos.ui.generated.resources.shortcut_status_missing
import com.skohscripts.kairos.ui.generated.resources.shortcut_update
import com.skohscripts.kairos.ui.generated.resources.shortcut_update_long
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Ce que crée le raccourci, pour le dire (docs/spec/raccourci-portable.md). */
enum class ShortcutTarget {
    /** Linux : entrée du menu des applications, avec l'icône. */
    APP_MENU,

    /** Windows : menu Démarrer et Bureau. */
    START_MENU_AND_DESKTOP,
}

enum class ShortcutStatus {
    MISSING,
    CREATED,

    /** Des raccourcis existent, mais ouvrent une autre copie de Kairos. */
    ELSEWHERE,
}

/** Raccourci d'une copie portable de bureau ; `AppServices.shortcuts` est nul partout ailleurs. */
interface ShortcutService {
    val target: ShortcutTarget
    val status: StateFlow<ShortcutStatus>

    /** « Non merci » : le bandeau ne revient plus (la carte des Réglages reste). */
    val declined: StateFlow<Boolean>

    /** Crée (ou fait pointer vers cette copie) le raccourci ; lève une exception en cas d'échec. */
    suspend fun create()

    /** Supprime les seuls fichiers créés. */
    suspend fun remove()

    fun decline()
}

/** Proposition de créer (ou mettre à jour) le raccourci, tant qu'il manque et qu'on n'a pas dit « Non merci ». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutBanner(services: AppServices, modifier: Modifier = Modifier) {
    val shortcuts = services.shortcuts ?: return
    val status by shortcuts.status.collectAsState()
    val declined by shortcuts.declined.collectAsState()
    if (status == ShortcutStatus.CREATED || declined) return
    val create = rememberShortcutAction(shortcuts)
    // Bannière neutre, comme celle des mises à jour (charte : pas de couleur pour une information).
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Icon(KairosIcons.InstallDesktop, contentDescription = null)
            val text = when {
                status == ShortcutStatus.ELSEWHERE -> Res.string.shortcut_banner_elsewhere
                shortcuts.target == ShortcutTarget.APP_MENU -> Res.string.shortcut_banner_menu
                else -> Res.string.shortcut_banner_windows
            }
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            KairosTextButton(onClick = create) {
                Text(stringResource(if (status == ShortcutStatus.ELSEWHERE) Res.string.shortcut_update else Res.string.shortcut_add))
            }
            KairosTextButton(onClick = shortcuts::decline) { Text(stringResource(Res.string.shortcut_decline)) }
        }
    }
}

/** Réglages → Raccourci (copie portable seulement) : l'état, créer ou mettre à jour, retirer. */
@Composable
fun ShortcutCard(shortcuts: ShortcutService) {
    val status by shortcuts.status.collectAsState()
    val create = rememberShortcutAction(shortcuts)
    val scope = rememberCoroutineScope()
    val message = LocalMessages.current
    SettingsCard(spacing = KairosSpacing.m) {
        val help = if (shortcuts.target == ShortcutTarget.APP_MENU) Res.string.shortcut_help_menu else Res.string.shortcut_help_windows
        SettingsCardHeader(stringResource(Res.string.settings_section_shortcut), stringResource(help))
        val line = when (status) {
            ShortcutStatus.MISSING -> Res.string.shortcut_status_missing
            ShortcutStatus.CREATED -> Res.string.shortcut_status_created
            ShortcutStatus.ELSEWHERE -> Res.string.shortcut_status_elsewhere
        }
        Text(stringResource(line), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ButtonRow {
            if (status != ShortcutStatus.CREATED) {
                KairosOutlinedButton(onClick = create) {
                    Text(stringResource(if (status == ShortcutStatus.ELSEWHERE) Res.string.shortcut_update_long else Res.string.shortcut_create))
                }
            }
            if (status != ShortcutStatus.MISSING) {
                KairosOutlinedButton(onClick = {
                    scope.launch {
                        runCatching { shortcuts.remove() }
                            .onSuccess { message(getString(Res.string.shortcut_removed)) }
                            .onFailure { message(getString(Res.string.shortcut_remove_failed, it.message.orEmpty())) }
                    }
                }) { Text(stringResource(Res.string.shortcut_remove)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonRow(content: @Composable () -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }

/** « Ajouter » / « Mettre à jour » : crée le raccourci, puis dit ce qu'il en est (snackbar). */
@Composable
private fun rememberShortcutAction(shortcuts: ShortcutService): () -> Unit {
    val scope = rememberCoroutineScope()
    val message = LocalMessages.current
    var busy by remember { mutableStateOf(false) }
    return {
        if (!busy) {
            busy = true
            scope.launch {
                runCatching { shortcuts.create() }
                    .onSuccess { message(getString(Res.string.shortcut_created)) }
                    .onFailure { message(getString(Res.string.shortcut_failed, it.message.orEmpty())) }
                busy = false
            }
        }
    }
}
