package com.skohscripts.kairos.ui.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.intl.Locale
import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.core.legacy.Kairos2Import
import com.skohscripts.kairos.core.legacy.LegacyDatabase
import com.skohscripts.kairos.core.legacy.LegacyReport
import com.skohscripts.kairos.core.legacy.NotALegacyDatabase
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.ExportCodec
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.file_error
import com.skohscripts.kairos.ui.generated.resources.legacy_confirm_action
import com.skohscripts.kairos.ui.generated.resources.legacy_confirm_body
import com.skohscripts.kairos.ui.generated.resources.legacy_confirm_title
import com.skohscripts.kairos.ui.generated.resources.legacy_done
import com.skohscripts.kairos.ui.generated.resources.legacy_not_a_database
import com.skohscripts.kairos.ui.generated.resources.legacy_read_error
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Horodatage des noms de fichiers : « 20260929-0915 » (heure locale). */
fun fileStamp(services: AppServices): String {
    val t = services.clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${t.year}${two(t.month.number)}${two(t.day)}-${two(t.hour)}${two(t.minute)}"
}

/**
 * Remplace toutes les données par [snapshot], après une sauvegarde des
 * données actuelles (`avant-import-…json`). Sans sauvegarde réussie, rien
 * n'est remplacé et le message d'erreur est affiché : `false`.
 */
suspend fun replaceWithBackup(services: AppServices, snapshot: KairosSnapshot, messages: (String) -> Unit): Boolean {
    val current = ExportCodec.encode(services.repository.snapshot.value, KairosBuild.VERSION_NAME, services.clock.now())
    val backed = runCatching { services.backups.save("avant-import-${fileStamp(services)}.json", current) }
    if (backed.isFailure) {
        messages(getString(Res.string.file_error))
        return false
    }
    services.repository.replaceAll(snapshot)
    return true
}

/**
 * Parcours d'import d'une base Kairos 2 (docs/spec/migration-2x.md) : on
 * lit, on convertit, on montre ce qui sera repris, puis on remplace (avec
 * sauvegarde) après confirmation.
 */
class LegacyImportFlow internal constructor(
    private val services: AppServices,
    private val launch: (suspend () -> Unit) -> Unit,
    private val messages: (String) -> Unit,
    private val language: String,
) {
    internal var pending by mutableStateOf<Pair<KairosSnapshot, LegacyReport>?>(null)

    /** Fait choisir un `tasks.db`, puis demande confirmation. */
    fun pick() = launch { load { services.legacy?.pick() } }

    /** La base trouvée à son emplacement habituel, puis demande confirmation. */
    fun importFound() = launch { load { services.legacy?.readFound() } }

    private suspend fun load(read: suspend () -> LegacyDatabase?) {
        try {
            val db = read() ?: return
            pending = Kairos2Import.convert(db, language, services.clock.now())
        } catch (e: NotALegacyDatabase) {
            messages(getString(Res.string.legacy_not_a_database))
        } catch (e: Exception) {
            messages(getString(Res.string.legacy_read_error))
        }
    }

    internal fun confirm() {
        val (snapshot, report) = pending ?: return
        pending = null
        launch {
            if (replaceWithBackup(services, snapshot, messages)) {
                messages(getString(Res.string.legacy_done, report.tasks, report.notes, report.sessions))
            }
        }
    }
}

@Composable
fun rememberLegacyImport(services: AppServices): LegacyImportFlow {
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    val language = Locale.current.language
    return remember(services) { LegacyImportFlow(services, { block -> scope.launch { block() } }, messages, language) }
}

/** Confirmation : ce qui sera repris, et le rappel que tout est remplacé (après sauvegarde). */
@Composable
fun LegacyImportDialog(flow: LegacyImportFlow) {
    val (_, report) = flow.pending ?: return
    AlertDialog(
        onDismissRequest = { flow.pending = null },
        title = { Text(stringResource(Res.string.legacy_confirm_title)) },
        text = { Text(stringResource(Res.string.legacy_confirm_body, report.tasks, report.notes, report.sessions, report.timeBlocks)) },
        confirmButton = { TextButton(onClick = flow::confirm) { Text(stringResource(Res.string.legacy_confirm_action)) } },
        dismissButton = { TextButton(onClick = { flow.pending = null }) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
