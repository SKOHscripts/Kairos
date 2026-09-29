package com.skohscripts.kairos.ui.notes

import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.app.disclosure
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Notes (docs/spec-v3/notes-capture.md) : capture libre en amont de « À
 * traiter ». Un seul champ, Ctrl+Entrée capture ; les notes en attente, la
 * plus récente en tête, avec « → Tâche », modifier, archiver, supprimer ;
 * « Traité / archivé » replié, avec le lien vers la tâche créée.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotesScreen(services: AppServices, onOpenTasks: () -> Unit) {
    val repository = services.repository
    val snapshot by repository.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    val open = snapshot.notes.filter { it.status == NoteStatus.OPEN }.sortedByDescending { it.createdAt }
    val done = snapshot.notes.filter { it.status == NoteStatus.ARCHIVED }.sortedByDescending { it.createdAt }
    var doneOpen by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var deleting by remember { mutableStateOf<Note?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
        ) {
            item { NoteCapture { body -> scope.launch { repository.createNote(body) } } }
            item {
                Column(Modifier.padding(top = 8.dp)) {
                    Text("${stringResource(Res.string.notes_open_title)} (${open.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
                    if (open.isEmpty()) Text(stringResource(Res.string.notes_empty), style = MaterialTheme.typography.bodySmall)
                }
            }
            items(open, key = { "open-${it.id}" }) { note ->
                NoteRow(note) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        val convertDescription = stringResource(Res.string.notes_to_task_desc)
                        Button(modifier = Modifier.semantics { contentDescription = convertDescription }, onClick = {
                            scope.launch {
                                val id = repository.convertNote(note.id) ?: return@launch
                                val title = repository.snapshot.value.tasks.firstOrNull { it.id == id }?.title.orEmpty()
                                messages(getString(Res.string.notes_converted, title))
                            }
                        }) { Text(stringResource(Res.string.notes_to_task)) }
                        TextButton(onClick = { editing = note }) { Text(stringResource(Res.string.action_edit)) }
                        TextButton(onClick = { scope.launch { repository.archiveNote(note.id) } }) { Text(stringResource(Res.string.notes_archive)) }
                        IconButton(onClick = { deleting = note }) {
                            Icon(KairosIcons.Delete, contentDescription = stringResource(Res.string.action_delete))
                        }
                    }
                }
            }
            if (done.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = 8.dp)) {
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
                        NoteRow(note, muted = true) {
                            if (note.convertedTaskId != null) {
                                TextButton(onClick = onOpenTasks) { Text(stringResource(Res.string.notes_see_task)) }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { note ->
        var body by remember(note.id) { mutableStateOf(note.body) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(Res.string.notes_edit_title)) },
            text = { OutlinedTextField(body, { body = it }, minLines = 4, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(enabled = body.isNotBlank(), onClick = {
                    scope.launch { repository.editNote(note.id, body) }
                    editing = null
                }) { Text(stringResource(Res.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    deleting?.let { note ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(Res.string.notes_delete_title)) },
            text = { Text(stringResource(Res.string.notes_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteNote(note.id) }
                    deleting = null
                }) { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/** Capture : plusieurs lignes possibles ; Ctrl+Entrée (Cmd+Entrée sur Mac) capture et garde le curseur. */
@Composable
private fun NoteCapture(onCapture: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    fun submit() {
        if (text.isNotBlank()) {
            onCapture(text)
            text = ""
        }
        runCatching { focus.requestFocus() }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(Res.string.notes_capture_label)) },
                placeholder = { Text(stringResource(Res.string.notes_capture_placeholder)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter) && (it.isCtrlPressed || it.isMetaPressed)) {
                        submit()
                        true
                    } else {
                        false
                    }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { submit() }) {
                    Icon(KairosIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.notes_capture_action), modifier = Modifier.padding(start = 6.dp))
                }
                Text(stringResource(Res.string.notes_capture_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Une note : son texte (retours à la ligne gardés), puis ses actions. */
@Composable
private fun NoteRow(note: Note, muted: Boolean = false, actions: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                note.body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            actions()
        }
    }
}
