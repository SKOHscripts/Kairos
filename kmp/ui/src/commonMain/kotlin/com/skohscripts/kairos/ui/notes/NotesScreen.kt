package com.skohscripts.kairos.ui.notes

import com.skohscripts.kairos.ui.generated.resources.gtd_notes_goal
import com.skohscripts.kairos.ui.guide.GtdMethodButton
import com.skohscripts.kairos.ui.guide.GtdStepText
import com.skohscripts.kairos.ui.guide.GtdStep
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosRowIconButton
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.app.disclosure
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.core.model.NoteImage
import com.skohscripts.kairos.core.notes.EncodedImage
import com.skohscripts.kairos.core.notes.ImageBudget
import com.skohscripts.kairos.core.notes.NoteConversion
import com.skohscripts.kairos.core.model.NoteStatus
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.action_delete
import com.skohscripts.kairos.ui.generated.resources.action_edit
import com.skohscripts.kairos.ui.generated.resources.action_save
import com.skohscripts.kairos.ui.generated.resources.notes_archive
import com.skohscripts.kairos.ui.generated.resources.notes_capture_action
import com.skohscripts.kairos.ui.generated.resources.notes_capture_hint
import com.skohscripts.kairos.ui.generated.resources.notes_capture_hint_touch
import com.skohscripts.kairos.ui.LocalPlatform
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.generated.resources.notes_capture_label
import com.skohscripts.kairos.ui.generated.resources.notes_capture_placeholder
import com.skohscripts.kairos.ui.generated.resources.notes_converted
import com.skohscripts.kairos.ui.generated.resources.notes_delete_body
import com.skohscripts.kairos.ui.generated.resources.notes_delete_title
import com.skohscripts.kairos.ui.generated.resources.notes_done_hint
import com.skohscripts.kairos.ui.generated.resources.notes_done_title
import com.skohscripts.kairos.ui.generated.resources.notes_edit_title
import com.skohscripts.kairos.ui.generated.resources.notes_empty
import com.skohscripts.kairos.ui.generated.resources.notes_open_title
import com.skohscripts.kairos.ui.generated.resources.notes_see_task
import com.skohscripts.kairos.ui.generated.resources.notes_to_task
import com.skohscripts.kairos.ui.generated.resources.notes_to_task_desc
import com.skohscripts.kairos.ui.generated.resources.notes_needs_text
import com.skohscripts.kairos.ui.generated.resources.note_image_limit
import com.skohscripts.kairos.ui.generated.resources.note_image_none
import com.skohscripts.kairos.ui.generated.resources.note_image_unreadable
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosListSectionExtra
import com.skohscripts.kairos.ui.theme.KairosSpacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Notes (docs/spec/notes-capture.md) : capture libre en amont de « À
 * traiter ». Un seul champ, Ctrl+Entrée capture ; les notes en attente, la
 * plus récente en tête, avec « → Tâche », modifier, archiver, supprimer ;
 * « Traité / archivé » replié, avec le lien vers la tâche créée. Depuis 4.6.0, jusqu'à quatre images par note
 * (docs/spec/notes-images.md) : ajoutées à la capture et à l'édition, en vignettes, ouvertes dans la visionneuse.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotesScreen(services: AppServices, onOpenTasks: () -> Unit) {
    val repository = services.repository
    val snapshot by repository.personalSnapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    val open = snapshot.notes.filter { it.status == NoteStatus.OPEN }.sortedByDescending { it.createdAt }
    val done = snapshot.notes.filter { it.status == NoteStatus.ARCHIVED }.sortedByDescending { it.createdAt }
    var doneOpen by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var deleting by remember { mutableStateOf<Note?>(null) }
    // Images de chaque note, dans l'ordre d'ajout (le dépôt les trie), et la visionneuse ouverte.
    val imagesOf = snapshot.noteImages.groupBy { it.noteId }
    var viewing by remember { mutableStateOf<Pair<List<ShownImage>, Int>?>(null) }
    fun view(images: List<NoteImage>, index: Int) {
        viewing = images.map { ShownImage("image-${it.id}", it.data) } to index
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            // Notes d'une même liste : `s` ; un titre de section ajoute la différence pour totaliser `xl`.
            verticalArrangement = Arrangement.spacedBy(KairosSpacing.s),
            contentPadding = PaddingValues(KairosSpacing.l),
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
        ) {
            // Étape 1 de GTD (docs/spec/methode-gtd.md) : l'objectif de l'écran, et la méthode à un geste.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(KairosSpacing.xs)) {
                    GtdStepText(GtdStep.CAPTURE, MaterialTheme.typography.labelLarge, MaterialTheme.colorScheme.primary)
                    Text(stringResource(Res.string.gtd_notes_goal), style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.padding(top = KairosSpacing.xs)) { GtdMethodButton() }
                }
            }
            item { NoteCapture(services.images) { body, images -> scope.launch { repository.createNote(body, images) } } }
            item {
                Column(Modifier.padding(top = KairosListSectionExtra)) {
                    Text("${stringResource(Res.string.notes_open_title)} (${open.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
                    if (open.isEmpty()) Text(stringResource(Res.string.notes_empty), style = MaterialTheme.typography.bodySmall)
                }
            }
            items(open, key = { "open-${it.id}" }) { note ->
                val images = imagesOf[note.id].orEmpty()
                NoteRow(note, images, onOpen = { view(images, it) }) {
                    // Le titre d'une tâche vient du texte : une note faite d'images seules attend un texte (notes-images.md).
                    val convertible = NoteConversion.fields(note.body).first.isNotEmpty()
                    if (!convertible) {
                        Text(
                            stringResource(Res.string.notes_needs_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        val convertDescription = stringResource(Res.string.notes_to_task_desc)
                        KairosButton(enabled = convertible, modifier = Modifier.semantics { contentDescription = convertDescription }, onClick = {
                            scope.launch {
                                val id = repository.convertNote(note.id) ?: return@launch
                                val title = repository.personalSnapshot.value.tasks.firstOrNull { it.id == id }?.title.orEmpty()
                                messages(getString(Res.string.notes_converted, title))
                            }
                        }) { Text(stringResource(Res.string.notes_to_task)) }
                        KairosTextButton(onClick = { editing = note }) { Text(stringResource(Res.string.action_edit)) }
                        KairosTextButton(onClick = { scope.launch { repository.archiveNote(note.id) } }) { Text(stringResource(Res.string.notes_archive)) }
                        KairosRowIconButton(KairosIcons.Delete, stringResource(Res.string.action_delete), onClick = { deleting = note })
                    }
                }
            }
            if (done.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = KairosListSectionExtra)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().disclosure(doneOpen, heading = true) { doneOpen = !doneOpen }.padding(vertical = 6.dp),
                        ) {
                            Icon(if (doneOpen) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
                            Text(
                                "${stringResource(Res.string.notes_done_title)} (${done.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        if (doneOpen) Text(stringResource(Res.string.notes_done_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (doneOpen) {
                    items(done, key = { "done-${it.id}" }) { note ->
                        val images = imagesOf[note.id].orEmpty()
                        NoteRow(note, images, muted = true, onOpen = { view(images, it) }) {
                            if (note.convertedTaskId != null) {
                                KairosTextButton(onClick = onOpenTasks) { Text(stringResource(Res.string.notes_see_task)) }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { note ->
        // Curseur en fin de texte et focus d'emblée : on ouvre ce dialogue pour écrire.
        val body = remember(note.id) { TextFieldState().apply { setTextAndPlaceCursorAtEnd(note.body) } }
        val current = imagesOf[note.id].orEmpty()
        val kept = remember(note.id) { mutableStateListOf<NoteImage>().apply { addAll(current) } }
        val added = remember(note.id) { mutableStateListOf<EncodedImage>() }
        val editFocus = remember(note.id) { FocusRequester() }
        LaunchedEffect(note.id) {
            // Le dialogue s'attache une image après sa composition : la demande de focus attend qu'il le soit.
            withFrameNanos { }
            withFrameNanos { }
            runCatching { editFocus.requestFocus() }
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(Res.string.notes_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val room = ImageBudget.MAX_IMAGES - kept.size - added.size
                    OutlinedTextField(
                        body,
                        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 4),
                        modifier = Modifier.fillMaxWidth().focusRequester(editFocus)
                            .imageInput(services.images, scope, { ImageBudget.MAX_IMAGES - kept.size - added.size }, messages, added::addAll),
                    )
                    val shown = kept.map { ShownImage("image-${it.id}", it.data) } + added.mapIndexed { i, m -> m.shown(i) }
                    NoteThumbnails(shown, onOpen = { viewing = shown to it }, onRemove = { i ->
                        if (i < kept.size) kept.removeAt(i) else added.removeAt(i - kept.size)
                    })
                    services.images?.let { images ->
                        AddImageButton(images.sources, enabled = room > 0) { source ->
                            addImages(images, source, scope, { ImageBudget.MAX_IMAGES - kept.size - added.size }, messages, added::addAll)
                        }
                    }
                }
            },
            confirmButton = {
                KairosButton(enabled = body.text.isNotBlank() || kept.isNotEmpty() || added.isNotEmpty(), onClick = {
                    val text = body.text.toString()
                    val keep = kept.map { it.id }
                    val add = added.toList()
                    scope.launch { repository.editNote(note.id, text, keep, add) }
                    editing = null
                }) { Text(stringResource(Res.string.action_save)) }
            },
            dismissButton = { KairosTextButton(onClick = { editing = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    viewing?.let { (images, start) -> ImageViewer(images, start) { viewing = null } }
    deleting?.let { note ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(Res.string.notes_delete_title)) },
            text = { Text(stringResource(Res.string.notes_delete_body)) },
            confirmButton = {
                KairosTextButton(onClick = {
                    scope.launch { repository.deleteNote(note.id) }
                    deleting = null
                }) { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { KairosTextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/**
 * Capture : plusieurs lignes possibles ; Ctrl+Entrée (Cmd+Entrée sur Mac) capture et garde le curseur. Depuis 4.6.0, des images
 * en attente sous le champ (docs/spec/notes-images.md) : « Ajouter une image », collage, et « Capturer » enregistre le tout.
 */
@Composable
private fun NoteCapture(images: ImageService?, onCapture: (String, List<EncodedImage>) -> Unit) {
    val text = rememberTextFieldState()
    // Images en attente : en mémoire, perdues si l'on quitte l'écran avant « Capturer », comme un texte non capturé.
    val pending = remember { mutableStateListOf<EncodedImage>() }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    var viewing by remember { mutableStateOf<Int?>(null) }
    fun submit() {
        if (text.text.isNotBlank() || pending.isNotEmpty()) {
            onCapture(text.text.toString(), pending.toList())
            text.clearText()
            pending.clear()
        }
        runCatching { focus.requestFocus() }
    }
    val room = { ImageBudget.MAX_IMAGES - pending.size }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                state = text,
                label = { Text(stringResource(Res.string.notes_capture_label)) },
                placeholder = { Text(stringResource(Res.string.notes_capture_placeholder)) },
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter) && (it.isCtrlPressed || it.isMetaPressed)) {
                        submit()
                        true
                    } else {
                        false
                    }
                }.imageInput(images, scope, room, messages, pending::addAll),
            )
            val shown = pending.mapIndexed { i, m -> m.shown(i) }
            NoteThumbnails(shown, onOpen = { viewing = it }, onRemove = { pending.removeAt(it) })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KairosButton(onClick = { submit() }, contentPadding = KairosButtonIconPadding) {
                    Icon(KairosIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.notes_capture_action), modifier = Modifier.padding(start = 6.dp))
                }
                if (images != null) {
                    AddImageButton(images.sources, enabled = pending.size < ImageBudget.MAX_IMAGES) { source ->
                        addImages(images, source, scope, room, messages, pending::addAll)
                    }
                }
                // Pas de raccourci clavier à annoncer sur Android (comme la vue Jour).
                val hint = if (LocalPlatform.current == Platform.ANDROID) Res.string.notes_capture_hint_touch else Res.string.notes_capture_hint
                Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
    }
    viewing?.let { start -> ImageViewer(pending.mapIndexed { i, m -> m.shown(i) }, start) { viewing = null } }
}

/**
 * Ajoute les images de [source] (au plus [room] places) et dit pourquoi il en manque : plafond de quatre, image illisible,
 * presse-papiers sans image (docs/spec/notes-images.md § Comportement attendu).
 */
private fun addImages(
    images: ImageService,
    source: ImageSource,
    scope: CoroutineScope,
    room: () -> Int,
    messages: (String) -> Unit,
    add: (List<EncodedImage>) -> Unit,
) {
    val free = room()
    if (free <= 0) {
        scope.launch { messages(getString(Res.string.note_image_limit)) }
        return
    }
    scope.launch {
        // Un de plus que la place : savoir s'il a fallu en laisser de côté.
        val pick = if (source == ImageSource.CLIPBOARD) images.paste(free + 1) else images.pick(source, free + 1)
        deliver(pick, free, source == ImageSource.CLIPBOARD, messages, add)
    }
}

private suspend fun deliver(pick: ImagePick, free: Int, fromClipboard: Boolean, messages: (String) -> Unit, add: (List<EncodedImage>) -> Unit) {
    add(pick.images.take(free))
    when {
        pick.images.size > free -> messages(getString(Res.string.note_image_limit))
        pick.unreadable > 0 -> messages(getString(Res.string.note_image_unreadable))
        fromClipboard && pick.images.isEmpty() -> messages(getString(Res.string.note_image_none))
    }
}

/**
 * Collage d'images dans le champ d'une note (docs/spec/notes-images.md § Coller) : sur Android, le contenu reçu par le champ
 * (« Coller », clavier) ; sur le bureau et le web, Ctrl/Cmd+V lit aussi le presse-papiers. L'événement n'est jamais consommé :
 * un texte se colle comme avant.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.imageInput(
    images: ImageService?,
    scope: CoroutineScope,
    room: () -> Int,
    messages: (String) -> Unit,
    add: (List<EncodedImage>) -> Unit,
): Modifier {
    if (images == null) return this
    val receiving = if (!images.receivesContent) this else contentReceiver(object : ReceiveContentListener {
        override fun onReceive(transferableContent: TransferableContent): TransferableContent? {
            if (!transferableContent.hasMediaType(MediaType.Image)) return transferableContent
            val free = room()
            if (free <= 0) {
                scope.launch { messages(getString(Res.string.note_image_limit)) }
                return null
            }
            // Lu tout de suite par la plateforme (l'accès accordé par le clavier ne dure pas), réduit hors du fil principal.
            scope.launch {
                val pick = images.receive(transferableContent, free + 1) ?: return@launch
                deliver(pick, free, fromClipboard = false, messages, add)
            }
            return null
        }
    })
    if (!images.pastesOnShortcut) return receiving
    return receiving.onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key == Key.V && (it.isCtrlPressed || it.isMetaPressed)) {
            val free = room()
            scope.launch {
                val pick = images.pasteFromShortcut(free + 1)
                // Rien à dire s'il n'y a pas d'image : c'était un collage de texte.
                if (pick.images.isEmpty() && pick.unreadable == 0) return@launch
                if (free <= 0) messages(getString(Res.string.note_image_limit)) else deliver(pick, free, fromClipboard = false, messages, add)
            }
        }
        false
    }
}

/** Une note : son texte (retours à la ligne gardés), ses vignettes, puis ses actions. */
@Composable
private fun NoteRow(note: Note, images: List<NoteImage> = emptyList(), muted: Boolean = false, onOpen: (Int) -> Unit = {}, actions: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            if (note.body.isNotEmpty()) {
                Text(
                    note.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            NoteThumbnails(
                images.map { ShownImage("image-${it.id}", it.data) },
                onOpen = onOpen,
                modifier = Modifier.padding(top = if (note.body.isNotEmpty()) 8.dp else 0.dp, end = 8.dp),
            )
            actions()
        }
    }
}
