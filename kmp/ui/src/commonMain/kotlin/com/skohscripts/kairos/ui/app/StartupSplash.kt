package com.skohscripts.kairos.ui.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.app_name
import com.skohscripts.kairos.ui.generated.resources.loading
import com.skohscripts.kairos.ui.icons.LogoColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource

/** Vrai tant que la page de démarrage couvre l'application : les dialogues (accueil) attendent sa fin. */
val LocalStartupSplash = compositionLocalOf { false }

/** Tour de l'aiguille, pause sur le logo, fondu (docs/spec/demarrage.md : ≈ 3 s en tout). */
private const val SWEEP_MS = 2200
private const val HOLD_MS = 400L
private const val FADE_MS = 500

/** Un tour complet plus l'angle du secteur du logo : l'ombre finit sur [360°, 440°] ≡ [0°, 80°]. */
private const val WEDGE_DEGREES = 80f
private const val TURN_DEGREES = 360f + WEDGE_DEGREES

/**
 * 288 dp × 0,64 : la boîte du cadran dans les relais natifs (écran de démarrage Android,
 * `kairos_splash_dial.xml` ; SVG de `index.html`), pour que la première image coïncide.
 */
private val DialSize = 184.32.dp

/**
 * Page de démarrage (docs/spec/demarrage.md) : l'aiguille fait un tour du cadran et se pose
 * sur le logo, pause, puis fondu. Attend [ready] avant le fondu ; un toucher abrège.
 */
@Composable
fun StartupSplash(ready: Boolean, onFinished: () -> Unit) {
    val sweep = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    var skipped by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }
    val isReady by rememberUpdatedState(ready)
    val finish by rememberUpdatedState(onFinished)
    // Relancé par le toucher : le logo se pose aussitôt, sans pause.
    LaunchedEffect(skipped) {
        if (skipped) {
            sweep.snapTo(1f)
        } else {
            sweep.animateTo(1f, tween(SWEEP_MS, easing = FastOutSlowInEasing))
            delay(HOLD_MS)
        }
        waiting = !isReady
        snapshotFlow { isReady }.first { it }
        waiting = false
        alpha.animateTo(0f, tween(FADE_MS))
        finish()
    }
    SplashFrame(
        progress = sweep.value,
        loading = waiting,
        // Pas de couche d'état : la page n'est pas un contrôle. Le clic consomme aussi les gestes destinés à l'application dessous.
        modifier = Modifier
            .alpha(alpha.value)
            .clickable(interactionSource = null, indication = null) { skipped = true },
    )
}

/**
 * Une image de la page de démarrage : [progress] de 0 (cadran vide, aiguille à midi) à 1 (logo).
 * Publique pour l'auto-test, qui la rend à mi-course.
 */
@Composable
fun SplashFrame(progress: Float, loading: Boolean = false, modifier: Modifier = Modifier) {
    val appName = stringResource(Res.string.app_name)
    Box(
        contentAlignment = Alignment.Center,
        // Fond du thème océan par défaut (composé hors du thème des réglages) : celui des relais natifs.
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("startup-splash")
            .semantics { contentDescription = appName },
    ) {
        Canvas(Modifier.size(DialSize)) {
            val unit = size.width / 40f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(LogoColors.Dial, radius = 18.5f * unit, center = center)
            drawCircle(LogoColors.Ring, radius = 18.5f * unit, center = center, style = Stroke(width = 1.6f * unit))
            // Ombre de largeur constante qui traîne derrière l'aiguille (elle grandit seulement au départ).
            val hand = TURN_DEGREES * progress
            val start = (hand - WEDGE_DEGREES).coerceAtLeast(0f)
            if (hand > start) {
                drawArc(
                    color = LogoColors.Wedge,
                    // 0° de Compose = trois heures ; midi = -90°.
                    startAngle = -90f + start,
                    sweepAngle = hand - start,
                    useCenter = true,
                    topLeft = Offset(center.x - 16f * unit, center.y - 16f * unit),
                    size = Size(32f * unit, 32f * unit),
                )
            }
            drawCircle(LogoColors.Axis, radius = 2.6f * unit, center = center)
        }
        if (loading) {
            Text(
                stringResource(Res.string.loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(y = DialSize / 2 + 32.dp),
            )
        }
    }
}
