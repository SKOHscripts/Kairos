package com.skohscripts.kairos.ui.notes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skohscripts.kairos.core.notes.EncodedImage
import com.skohscripts.kairos.ui.LocalPlatform
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.note_image_add
import com.skohscripts.kairos.ui.generated.resources.note_image_camera
import com.skohscripts.kairos.ui.generated.resources.note_image_close
import com.skohscripts.kairos.ui.generated.resources.note_image_file
import com.skohscripts.kairos.ui.generated.resources.note_image_gallery
import com.skohscripts.kairos.ui.generated.resources.note_image_next
import com.skohscripts.kairos.ui.generated.resources.note_image_open
import com.skohscripts.kairos.ui.generated.resources.note_image_paste
import com.skohscripts.kairos.ui.generated.resources.note_image_position
import com.skohscripts.kairos.ui.generated.resources.note_image_previous
import com.skohscripts.kairos.ui.generated.resources.note_image_remove
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosRowIconButton
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import kotlin.io.encoding.Base64

/** Côté d'une vignette (docs/spec/notes-images.md § Voir). */
internal val THUMBNAIL = 72.dp

/** Une image à montrer : son identité stable (clé de composition) et son JPEG en Base64. */
data class ShownImage(val key: String, val data: String)

internal fun EncodedImage.shown(index: Int) = ShownImage("pending-$index-${data.hashCode()}", data)

/**
 * L'image décodée, gardée tant que [data] ne change pas : redécoder un JPEG de 1 600 px à chaque recomposition figerait la liste.
 * `null` si elle est illisible (une vignette vide la remplace).
 */
@Composable
internal fun rememberImage(data: String): ImageBitmap? = remember(data) {
    runCatching { Base64.decode(data).decodeToImageBitmap() }.getOrNull()
}

/**
 * Vignettes d'une note, sur une ligne qui passe à la ligne si besoin (360 dp) : carrées, coins de 8 dp, recadrées. Un toucher ouvre
 * la visionneuse sur l'image ; [onRemove] (édition, capture) ajoute « Retirer » sur chacune.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoteThumbnails(images: List<ShownImage>, onOpen: (Int) -> Unit, onRemove: ((Int) -> Unit)? = null, modifier: Modifier = Modifier) {
    if (images.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        images.forEachIndexed { i, image ->
            val open = stringResource(Res.string.note_image_open, i + 1)
            Box {
                val bitmap = rememberImage(image.data)
                Box(
                    Modifier.size(THUMBNAIL)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                        .clickable { onOpen(i) }
                        .semantics { contentDescription = open },
                ) {
                    if (bitmap != null) Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (onRemove != null) {
                    // Pastille « Retirer » dans le coin : cible de 48 dp, posée sur une surface pour rester lisible sur l'image.
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                    ) {
                        KairosRowIconButton(KairosIcons.Close, stringResource(Res.string.note_image_remove), onClick = { onRemove(i) })
                    }
                }
            }
        }
    }
}

/** Texte et icône d'une entrée du menu « Ajouter une image ». */
private fun ImageSource.label(): Pair<StringResource, androidx.compose.ui.graphics.vector.ImageVector> = when (this) {
    ImageSource.GALLERY -> Res.string.note_image_gallery to KairosIcons.PhotoLibrary
    ImageSource.CAMERA -> Res.string.note_image_camera to KairosIcons.PhotoCamera
    ImageSource.FILE -> Res.string.note_image_file to KairosIcons.Image
    ImageSource.CLIPBOARD -> Res.string.note_image_paste to KairosIcons.ContentPaste
}

/** Le bouton « Ajouter une image » et son menu, une entrée par source de la plateforme ; inactif à [enabled] faux (4 images). */
@Composable
internal fun AddImageButton(sources: List<ImageSource>, enabled: Boolean, onPick: (ImageSource) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        KairosRowIconButton(KairosIcons.AddPhotoAlternate, stringResource(Res.string.note_image_add), onClick = { open = true }, enabled = enabled)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (source in sources) {
                val (text, icon) = source.label()
                DropdownMenuItem(
                    text = { Text(stringResource(text)) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    onClick = {
                        open = false
                        onPick(source)
                    },
                )
            }
        }
    }
}

/**
 * Visionneuse plein écran (docs/spec/notes-images.md § Voir) : l'image entière sur le voile `scrim`, « Image 2 sur 3 », balayage
 * (et flèches, ←/→ hors Android), zoom par pincement, double toucher ou molette, « Fermer » et Échap.
 */
@Composable
fun ImageViewer(images: List<ShownImage>, start: Int, onClose: () -> Unit) {
    if (images.isEmpty()) return
    val pager = rememberPagerState(initialPage = start.coerceIn(0, images.lastIndex)) { images.size }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val touch = LocalPlatform.current == Platform.ANDROID
    fun go(delta: Int) = scope.launch { pager.animateScrollToPage((pager.currentPage + delta).coerceIn(0, images.lastIndex)) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim).focusRequester(focus).focusable().onKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (it.key) {
                    Key.DirectionLeft -> { go(-1); true }
                    Key.DirectionRight -> { go(1); true }
                    Key.Escape -> { onClose(); true }
                    else -> false
                }
            },
        ) {
            HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { page -> ZoomableImage(images[page]) }
            Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(Res.string.note_image_position, pager.currentPage + 1, images.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    KairosRowIconButton(KairosIcons.Close, stringResource(Res.string.note_image_close), onClick = onClose, tint = MaterialTheme.colorScheme.inverseOnSurface)
                }
            }
            if (!touch && images.size > 1) {
                Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KairosRowIconButton(KairosIcons.ChevronLeft, stringResource(Res.string.note_image_previous), onClick = { go(-1) }, enabled = pager.currentPage > 0, tint = MaterialTheme.colorScheme.inverseOnSurface)
                    KairosRowIconButton(KairosIcons.ChevronRight, stringResource(Res.string.note_image_next), onClick = { go(1) }, enabled = pager.currentPage < images.lastIndex, tint = MaterialTheme.colorScheme.inverseOnSurface)
                }
            }
        }
    }
}

/** Une image de la visionneuse : ajustée à l'écran, zoom de 1 à 5 par pincement, molette ou double toucher (×2.5 ou retour). */
@Composable
private fun ZoomableImage(image: ShownImage) {
    val bitmap = rememberImage(image.data) ?: return
    var scale by remember(image.key) { mutableFloatStateOf(1f) }
    var offset by remember(image.key) { mutableStateOf(Offset.Zero) }
    fun zoom(to: Float) {
        scale = to.coerceIn(1f, 5f)
        if (scale == 1f) offset = Offset.Zero
    }
    Image(
        bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
            .pointerInput(image.key) { detectTapGestures(onDoubleTap = { zoom(if (scale > 1f) 1f else 2.5f) }) }
            .pointerInput(image.key) {
                // Au zoom 1, le geste horizontal reste au défilement des images : seul le pincement est pris.
                detectTransformGestures { _, pan, gesture, _ ->
                    zoom(scale * gesture)
                    if (scale > 1f) offset += pan
                }
            }
            .pointerInput(image.key) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (dy != 0f) zoom(scale * if (dy < 0) 1.15f else 1 / 1.15f)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
