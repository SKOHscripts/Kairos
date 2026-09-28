package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.StorageBanner
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.capture_add
import com.skohscripts.kairos.ui.generated.resources.capture_label
import com.skohscripts.kairos.ui.generated.resources.capture_placeholder
import com.skohscripts.kairos.ui.generated.resources.done_title
import com.skohscripts.kairos.ui.generated.resources.inbox_empty
import com.skohscripts.kairos.ui.generated.resources.inbox_help
import com.skohscripts.kairos.ui.generated.resources.inbox_title
import com.skohscripts.kairos.ui.generated.resources.todo_empty
import com.skohscripts.kairos.ui.generated.resources.todo_provisional
import com.skohscripts.kairos.ui.generated.resources.todo_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Vue Jour, jalon M1 (docs/spec-v3/vue-jour.md) : capture, « À traiter »
 * (qualification en un clic), « À faire » (ordre provisoire), « Fait »
 * (replié), dialogue d'édition. « Maintenant », l'agenda WSJF et la timeline
 * arrivent avec le moteur (M2).
 */
@Composable
fun DayScreen(services: AppServices) {
    val repository = services.repository
    val snapshot by repository.snapshot.collectAsState()
    val lists = remember(snapshot.tasks) { DayLists.of(snapshot.tasks) }
    val scope = rememberCoroutineScope()
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var doneOpen by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
        ) {
            item { StorageBanner(services, showWhenLinked = false) }
            item { Capture { title -> scope.launch { repository.createTask(title) } } }

            item { SectionTitle(stringResource(Res.string.inbox_title), lists.inbox.size, Modifier.padding(top = 8.dp)) }
            if (lists.inbox.isEmpty()) {
                item { Muted(stringResource(Res.string.inbox_empty)) }
            } else {
                item { Muted(stringResource(Res.string.inbox_help)) }
                items(lists.inbox, key = { "inbox-${it.id}" }) { task ->
                    TaskRow(task, { scope.launch { repository.toggleDone(task.id) } }, { editingId = task.id }) {
                        InboxQualify(task, onPriority = { scope.launch { repository.setPriority(task.id, it) } }, onPoints = {
                            scope.launch { repository.setPoints(task.id, it) }
                        })
                    }
                }
            }

            item { SectionTitle(stringResource(Res.string.todo_title), lists.todo.size, Modifier.padding(top = 16.dp)) }
            item { Muted(stringResource(if (lists.todo.isEmpty()) Res.string.todo_empty else Res.string.todo_provisional)) }
            items(lists.todo, key = { "todo-${it.id}" }) { task ->
                TaskRow(task, { scope.launch { repository.toggleDone(task.id) } }, { editingId = task.id })
            }

            if (lists.done.isNotEmpty()) {
                item {
                    TextButton(onClick = { doneOpen = !doneOpen }, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(if (doneOpen) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
                        Text("${stringResource(Res.string.done_title)} (${lists.done.size})", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                if (doneOpen) {
                    items(lists.done, key = { "done-${it.id}" }) { task ->
                        TaskRow(task, { scope.launch { repository.toggleDone(task.id) } }, { editingId = task.id })
                    }
                }
            }
        }
    }

    editingId?.let { id ->
        val task = snapshot.tasks.firstOrNull { it.id == id }
        if (task == null) {
            editingId = null
        } else {
            EditTaskDialog(
                task = task,
                taskTypes = snapshot.settings.taskTypeList,
                onDismiss = { editingId = null },
                onSave = { edit -> scope.launch { repository.updateEssentials(id, edit) }; editingId = null },
                onDelete = { scope.launch { repository.deleteTask(id) }; editingId = null },
            )
        }
    }
}

/**
 * Capture sans friction : titre seul, Entrée ajoute, le curseur reste dans le
 * champ pour enchaîner (audit UI de Kairos 2). Carte « filled », jamais repliée.
 */
@Composable
private fun Capture(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    fun submit() {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
        runCatching { focus.requestFocus() }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(Res.string.capture_label)) },
                placeholder = { Text(stringResource(Res.string.capture_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                        submit()
                        true
                    } else {
                        false
                    }
                },
            )
            Button(onClick = { submit() }) {
                Icon(KairosIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.capture_add), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** Qualification en ligne d'une tâche de la boîte de réception : ce qui manque, puis les pastilles. */
@Composable
private fun InboxQualify(task: Task, onPriority: (Int?) -> Unit, onPoints: (Int?) -> Unit) {
    Column(Modifier.padding(start = 48.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        task.missingQualification?.let { missing ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(Levels.missing(missing)), style = MaterialTheme.typography.labelMedium)
            }
        }
        PriorityPills(task.priority, onPriority)
        PointsPills(task.fibonacciPoints, onPoints)
    }
}

@Composable
private fun SectionTitle(text: String, count: Int, modifier: Modifier = Modifier) {
    Text("$text ($count)", style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
