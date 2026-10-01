package com.skohscripts.kairos.ui.app

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.update_banner
import com.skohscripts.kairos.ui.generated.resources.update_download
import com.skohscripts.kairos.ui.generated.resources.update_later
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Résultat de la dernière vérification des mises à jour (docs/spec/mises-a-jour.md). */
sealed interface UpdateStatus {
    data object Never : UpdateStatus
    data object Checking : UpdateStatus
    data class UpToDate(val at: Instant) : UpdateStatus
    data class Available(val version: String, val url: String, val at: Instant) : UpdateStatus
    data class Failed(val at: Instant) : UpdateStatus
}

/** Vérification des mises à jour, là où la plateforme a le réseau (bureau seulement). */
interface UpdateService {
    val status: StateFlow<UpdateStatus>

    /** Version dont le bandeau a été masqué (« Plus tard ») ; la suivante réapparaît. */
    val dismissed: StateFlow<String?>

    /** Vérifie si [force], ou si la dernière vérification date de 6 heures ou plus. */
    suspend fun check(force: Boolean)

    fun dismiss(version: String)
}

/**
 * Veille des mises à jour, pour toute l'application : tant que le réglage
 * est actif, redemande toutes les 30 minutes si une vérification est due
 * (au plus une toutes les 6 heures, `UpdateCheck.due`).
 */
@Composable
fun UpdateWatcher(services: AppServices) {
    val updates = services.updates ?: return
    val snapshot by services.repository.snapshot.collectAsState()
    val enabled = snapshot.settings.updateCheckEnabled
    LaunchedEffect(updates, enabled) {
        while (enabled) {
            runCatching { updates.check(force = false) }
            delay(30.minutes)
        }
    }
}

/** « Kairos X.Y.Z est disponible (tu as A.B.C) » : « Télécharger » ouvre la page de la version, « Plus tard » masque. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateBanner(services: AppServices, modifier: Modifier = Modifier) {
    val updates = services.updates ?: return
    val status by updates.status.collectAsState()
    val dismissed by updates.dismissed.collectAsState()
    val available = status as? UpdateStatus.Available ?: return
    if (available.version == dismissed) return
    val uri = LocalUriHandler.current
    // Bannière neutre (charte : pas de couleur pour une information), icône pour la repérer.
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Icon(KairosIcons.Download, contentDescription = null)
            Text(
                stringResource(Res.string.update_banner, available.version, KairosBuild.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
            )
            KairosTextButton(onClick = { runCatching { uri.openUri(available.url) } }) { Text(stringResource(Res.string.update_download)) }
            KairosTextButton(onClick = { updates.dismiss(available.version) }) { Text(stringResource(Res.string.update_later)) }
        }
    }
}
