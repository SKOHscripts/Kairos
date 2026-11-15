package com.skohscripts.kairos.ui.guide

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import com.skohscripts.kairos.core.guide.Tip
import com.skohscripts.kairos.core.guide.TipFacts
import com.skohscripts.kairos.core.guide.TipPlatform
import com.skohscripts.kairos.core.guide.TipSpace
import com.skohscripts.kairos.core.guide.Tips
import com.skohscripts.kairos.ui.LocalPlatform
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.LinkedFileState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Délai entre l'arrivée sur l'Accueil et le conseil (docs/spec/conseils.md, comportement attendu point 2). */
const val TIP_DELAY_MILLIS = 5_000L

/**
 * Guetteur des conseils (docs/spec/conseils.md § Interface), dans la coquille réelle seulement. Tant que [onHome] et [quiet]
 * tiennent [TIP_DELAY_MILLIS] ms (l'Accueil est l'écran d'ouverture, puis on y revient), il demande à `Tips.due` le conseil dû
 * dans [space] et le passe à [onTip], au plus une fois par lancement. Un dialogue, un bandeau ou un chrono qui apparaît annule
 * l'attente sans consommer le conseil. La date est retenue (rythme) au moment où le conseil est montré.
 */
@Composable
fun TipWatcher(services: AppServices, space: TipSpace, onHome: Boolean, quiet: Boolean, onTip: (Tip) -> Unit) {
    var shown by rememberSaveable { mutableStateOf(false) }
    val platform = when (LocalPlatform.current) {
        Platform.ANDROID -> TipPlatform.ANDROID
        Platform.DESKTOP -> TipPlatform.DESKTOP
        Platform.WEB -> TipPlatform.WEB
    }
    // Le thème effectivement affiché, quel que soit le réglage (suit le système, clair ou sombre).
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    LaunchedEffect(onHome, quiet, space, dark) {
        if (shown || !onHome || !quiet) return@LaunchedEffect
        delay(TIP_DELAY_MILLIS)
        val linked = services.linkedStorage?.state?.value.let { it != null && it != LinkedFileState.NotLinked && it != LinkedFileState.Unsupported }
        val facts = TipFacts(platform, darkTheme = dark, linkedFile = linked, calendarImport = true, widgetPlaced = services.widgets?.placed())
        val snapshot = services.repository.snapshot.value
        val now = services.clock.now()
        val zone = TimeZone.currentSystemDefault()
        val tip = withContext(Dispatchers.Default) { Tips.due(snapshot, snapshot.settings.guide, facts, space, now, zone) } ?: return@LaunchedEffect
        shown = true
        services.repository.updateGuide { Tips.shown(it, now.toLocalDateTime(zone).date) }
        onTip(tip)
    }
}
