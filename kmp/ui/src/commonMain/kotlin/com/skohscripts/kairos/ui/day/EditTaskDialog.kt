package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.ui.generated.resources.proposal_help
import com.skohscripts.kairos.ui.generated.resources.proposal_switch
import com.skohscripts.kairos.data.ProposalChange
import com.skohscripts.kairos.ui.generated.resources.exchange_note_help_pack
import com.skohscripts.kairos.ui.generated.resources.exchange_note_help_report
import com.skohscripts.kairos.ui.generated.resources.exchange_note_for_member
import com.skohscripts.kairos.ui.generated.resources.exchange_note_for_manager
import com.skohscripts.kairos.ui.generated.resources.exchange_note_from
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import com.skohscripts.kairos.core.model.ExchangeNote
import com.skohscripts.kairos.ui.guide.RecurrencePreview
import com.skohscripts.kairos.ui.app.uiLanguage
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import com.skohscripts.kairos.ui.app.expandedState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.data.Reassignment
import com.skohscripts.kairos.data.TaskEdit
import com.skohscripts.kairos.core.team.exchange.TeamOrigin
import com.skohscripts.kairos.ui.generated.resources.received_edit_help
import com.skohscripts.kairos.ui.generated.resources.received_keep
import com.skohscripts.kairos.ui.team.KeepInProgressDialog
import com.skohscripts.kairos.ui.team.ProgressField
import com.skohscripts.kairos.ui.team.managerLabel
import com.skohscripts.kairos.ui.team.TaskHistory
import com.skohscripts.kairos.ui.team.TeamTaskFields
import com.skohscripts.kairos.ui.team.TeamTaskSheet
import com.skohscripts.kairos.ui.team.TeamTaskTabs
import kotlin.math.roundToInt
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.action_delete
import com.skohscripts.kairos.ui.generated.resources.action_save
import com.skohscripts.kairos.ui.generated.resources.delete_confirm_body
import com.skohscripts.kairos.ui.generated.resources.delete_confirm_title
import com.skohscripts.kairos.ui.generated.resources.edit_title
import com.skohscripts.kairos.ui.generated.resources.edit_title_first
import com.skohscripts.kairos.ui.generated.resources.field_deadline
import com.skohscripts.kairos.ui.generated.resources.field_description
import com.skohscripts.kairos.ui.generated.resources.field_minutes
import com.skohscripts.kairos.ui.generated.resources.field_project
import com.skohscripts.kairos.ui.generated.resources.field_title
import com.skohscripts.kairos.ui.generated.resources.field_type
import com.skohscripts.kairos.ui.generated.resources.field_category
import com.skohscripts.kairos.ui.generated.resources.edit_advanced
import com.skohscripts.kairos.ui.generated.resources.field_blockers
import com.skohscripts.kairos.ui.generated.resources.team_count_in_team
import com.skohscripts.kairos.ui.generated.resources.team_count_in_team_hint
import com.skohscripts.kairos.ui.app.tabLeavesField
import com.skohscripts.kairos.ui.settings.SwitchRow
import com.skohscripts.kairos.ui.generated.resources.field_blockers_cycle
import com.skohscripts.kairos.ui.generated.resources.field_blockers_hint
import com.skohscripts.kairos.ui.generated.resources.field_blockers_none
import com.skohscripts.kairos.ui.generated.resources.field_day_invalid
import com.skohscripts.kairos.ui.generated.resources.field_day_of_month
import com.skohscripts.kairos.ui.generated.resources.field_day_of_month_hint
import com.skohscripts.kairos.ui.generated.resources.field_manual_time
import com.skohscripts.kairos.ui.generated.resources.field_pin_hint
import com.skohscripts.kairos.ui.generated.resources.field_pin_time
import com.skohscripts.kairos.ui.generated.resources.field_recurrence
import com.skohscripts.kairos.ui.generated.resources.field_scheduled
import com.skohscripts.kairos.ui.generated.resources.field_subtasks
import com.skohscripts.kairos.ui.generated.resources.task_rec_daily
import com.skohscripts.kairos.ui.generated.resources.task_rec_monthly
import com.skohscripts.kairos.ui.generated.resources.task_rec_monthly_on_day
import com.skohscripts.kairos.ui.generated.resources.task_rec_none
import com.skohscripts.kairos.ui.generated.resources.task_rec_weekdays
import com.skohscripts.kairos.ui.generated.resources.task_rec_weekly
import com.skohscripts.kairos.ui.generated.resources.time_invalid
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.ui.guide.CoachPanel
import com.skohscripts.kairos.ui.guide.CoachSession
import com.skohscripts.kairos.ui.guide.coachField
import com.skohscripts.kairos.ui.generated.resources.edit_title_first_team
import com.skohscripts.kairos.ui.guide.coachHighlight
import androidx.compose.runtime.LaunchedEffect
import com.skohscripts.kairos.core.settings.UiLanguage
import com.skohscripts.kairos.ui.generated.resources.learned_use
import com.skohscripts.kairos.ui.generated.resources.learned_weak
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Dialogue d'édition d'une tâche (docs/spec/vue-jour.md § Édition) :
 * essentiels toujours visibles (titre, description juste sous le titre,
 * priorité, points, échéance, durée, puis les sous-tâches existantes s'il y en a,
 * [SubtaskEditor]), puis « Options avancées » repliées
 * (programmée pour, projet, temps passé manuel, récurrence et jour du mois,
 * type, heure fixe, nouvelles sous-tâches, bloqueurs). Guide des points
 * sous les pastilles ; palier ou type calibré → durée suggérée. Un seul
 * « Enregistrer » pose tout. Dialogue MD3 : coins de 28 dp, seul élément
 * flottant à porter une ombre.
 */
/**
 * Ce que la fiche d'une tâche **reçue** ajoute (docs/spec/equipe-echanges.md) : son [origin], [removed] si le dernier paquet
 * l'a retirée, et l'enregistrement qui écrit l'édition puis l'avancement déclaré (`null` = inchangé, `setReceivedProgress`) et, pour une tâche
 * retirée, [onKeep] : « Garder comme tâche personnelle » (`detachOrigin`).
 */
internal class ReceivedTaskSheet(val origin: TeamOrigin, val removed: Boolean, val onKeep: () -> Unit, val onSave: (TaskEdit, Int?) -> Unit)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EditTaskDialog(
    task: Task,
    day: LocalDate,
    /** Le vrai jour d'aujourd'hui (raccourcis des champs date) ; `day` est le jour affiché, qui peut être un autre. */
    today: LocalDate = day,
    taskTypes: List<String>,
    candidates: List<Task>,
    blockerIds: Set<Long>,
    /** Sous-tâches existantes de la tâche (docs/spec/vue-jour.md § Édition, issue #69) ; vide pour une tâche d'équipe ou sans fille. */
    subtasks: List<Task> = emptyList(),
    /** Dépendances existantes et titres de toutes les tâches : libellés et cycles de « Bloquée par » ([BlockerChoices]). */
    dependencies: List<TaskDependency>,
    titles: Map<Long, String>,
    estimates: Estimates,
    onDismiss: () -> Unit,
    onSave: (TaskEdit) -> Unit,
    onDelete: () -> Unit,
    /** Tâche d'équipe : champs et historique de la fiche (docs/spec/equipe-backlog-suivi.md) ; `null` pour une tâche personnelle. */
    team: TeamTaskSheet? = null,
    /** Tâche reçue d'un manager (docs/spec/equipe-echanges.md) : sa marque, une phrase d'aide et le curseur d'avancement ; `null` sinon. */
    received: ReceivedTaskSheet? = null,
    /**
     * Interrupteur « Compter dans l'équipe » (docs/spec/equipe-moi.md) : sa valeur initiale pour une tâche perso qui peut
     * compter (gestion d'équipe activée, « moi » existe) ; `null` le masque.
     */
    countInTeam: Boolean? = null,
    /** Durée apprise de la tâche (docs/spec/durees-apprises.md) : proposée dans le champ des minutes quand il est vide. */
    learned: DurationEstimator.Estimate? = null,
    /**
     * Accompagnement de la première tâche (docs/spec/premiere-tache.md) : le panneau d'explication en bas de la fiche, le champ expliqué
     * mis en évidence ; `null` : la fiche habituelle, qui n'est alors pas modifiée.
     */
    coach: CoachSession? = null,
    /**
     * Équipes d'origine auxquelles proposer cette tâche personnelle (docs/spec/equipe-echanges.md § Propositions, issue #121) ;
     * vide : pas de section.
     */
    proposalTargets: List<TeamOrigin> = emptyList(),
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description) }
    var priority by remember { mutableStateOf(task.priority) }
    var points by remember { mutableStateOf(task.fibonacciPoints) }
    var deadline by remember { mutableStateOf(task.deadline?.toString().orEmpty()) }
    var minutes by remember { mutableStateOf(task.estimatedMinutes?.toString().orEmpty()) }
    var project by remember { mutableStateOf(task.projectTag) }
    var type by remember { mutableStateOf(task.taskType) }
    var scheduled by remember { mutableStateOf(task.scheduledDate?.toString().orEmpty()) }
    var manual by remember { mutableStateOf(task.manualTimeSpentMinutes?.toString().orEmpty()) }
    var recurrence by remember { mutableStateOf(task.recurrence) }
    var dayOfMonth by remember { mutableStateOf(task.recurrenceDayOfMonth?.toString().orEmpty()) }
    var pin by remember { mutableStateOf(task.pinnedStart?.time?.let(Dates::field).orEmpty()) }
    var newSubtasks by remember { mutableStateOf("") }
    // Brouillons des sous-tâches existantes, figés à l'ouverture : la fiche reste stable si la base change dessous.
    val originalSubtasks = remember(task.id) { subtasks }
    var subtaskDrafts by remember(task.id) { mutableStateOf(originalSubtasks.map { SubtaskDraft.of(it) }) }
    var blockers by remember { mutableStateOf(blockerIds) }
    // Un bloqueur ou une date programmée existent déjà : ils sont dans les options avancées, qu'on ouvre d'emblée.
    var advanced by remember { mutableStateOf(blockerIds.isNotEmpty() || task.scheduledDate != null) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Tâche d'équipe (espace Équipe) : titulaire en cours d'édition, réponse « garder En cours ? », avancement, onglet.
    var assignee by remember { mutableStateOf(task.assigneeId) }
    var keepInProgress by remember { mutableStateOf(false) }
    var askKeepFor by remember { mutableStateOf<Long?>(null) }
    var progress by remember { mutableStateOf((task.progressPercent ?: 0).toFloat()) }
    var tab by remember { mutableStateOf(if (team?.initialHistory == true) 1 else 0) }
    val teamEditing = team?.editable == true
    var receivedProgress by remember { mutableStateOf((task.progressPercent ?: 0).toFloat()) }
    // Note à envoyer (docs/spec/equipe-echanges.md § Notes) : tâche d'équipe ou tâche reçue seulement.
    var noteOut by remember { mutableStateOf(task.noteOut) }
    var proposedTo by remember { mutableStateOf(task.proposedTo) }
    val canPropose = proposalTargets.isNotEmpty() && task.id != 0L && task.space == TaskSpace.PERSONAL && task.origin == null && task.parentId == null
    var counted by remember { mutableStateOf(countInTeam ?: true) }
    // Création (identifiant 0, docs/spec/premiere-tache.md) : rien n'existe encore, donc pas de « Supprimer » ; le titre est requis.
    val creating = task.id == 0L
    // Le coach ouvre les options avancées quand il en explique un champ.
    LaunchedEffect(coach?.step) { if (coach?.current?.opensAdvanced == true) advanced = true }

    val dayFirst = UiLanguage.dayFirst(uiLanguage())
    val parsedDeadline = Dates.parseDate(deadline, dayFirst)
    val deadlineInvalid = deadline.isNotBlank() && parsedDeadline == null
    val parsedScheduled = Dates.parseDate(scheduled, dayFirst)
    val scheduledInvalid = scheduled.isNotBlank() && parsedScheduled == null
    val parsedPin = Dates.parseTime(pin)
    val pinInvalid = pin.isNotBlank() && parsedPin == null
    val parsedDayOfMonth = dayOfMonth.toIntOrNull()?.takeIf { it in 1..31 }
    val dayOfMonthInvalid = recurrence == TaskRecurrence.MONTHLY_ON_DAY && parsedDayOfMonth == null
    val invalid = deadlineInvalid || scheduledInvalid || pinInvalid || dayOfMonthInvalid

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(16.dp).widthIn(max = 640.dp),
        ) {
            // Fiche à onglets (tâche d'équipe) : la hauteur ne redescend jamais d'un onglet à l'autre, sinon l'en-tête
            // sautait et le clic suivant tombait sur le fond, qui ferme la fiche.
            val density = LocalDensity.current
            var tallest by remember { mutableStateOf(0.dp) }
            Column(
                modifier = Modifier
                    .heightIn(min = if (team != null && teamEditing) tallest else 0.dp)
                    .onSizeChanged { size -> with(density) { size.height.toDp() }.let { if (it > tallest) tallest = it } },
            ) {
                // Seul le contenu défile : Supprimer, Annuler et Enregistrer restent en bas de la fiche, atteignables sans
                // faire défiler (à 900x700, « Enregistrer » était hors de vue sous les options avancées).
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
                ) {
                    Text(
                        stringResource(
                            when {
                                creating && team != null -> Res.string.edit_title_first_team
                                creating -> Res.string.edit_title_first
                                else -> Res.string.edit_title
                            },
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    // Création : pas encore d'historique, pas d'onglets.
                    if (team != null && teamEditing && !creating) TeamTaskTabs(tab, team.history.size) { tab = it }
                    if (team != null && teamEditing && tab == 1) {
                        TaskHistory(team.history, team.assign, team.today)
                    }
                    if (tab == 0) {
                        OutlinedTextField(title, { title = it }, label = { Text(stringResource(Res.string.field_title)) }, modifier = Modifier.fillMaxWidth().coachField(EditField.TITLE, coach))
                        OutlinedTextField(
                            description,
                            { description = it },
                            label = { Text(stringResource(Res.string.field_description)) },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth().tabLeavesField().coachField(EditField.DESCRIPTION, coach),
                        )
                        if (canPropose) ProposalSection(proposalTargets, proposedTo) { proposedTo = it }
                        if (received != null) {
                            ReceivedMark(received.origin, received.removed)
                            Text(
                                stringResource(Res.string.received_edit_help, managerLabel(received.origin)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (task.status == TaskStatus.TODO) ProgressField(receivedProgress) { receivedProgress = it }
                            ExchangeNoteSection(task.noteIn, noteOut, toManager = true) { noteOut = it }
                        }
                        if (team != null) {
                            TeamTaskFields(
                                task = task,
                                sheet = team,
                                assignee = assignee,
                                onPickAssignee = { picked ->
                                    when {
                                        picked == assignee -> Unit
                                        // Une tâche en cours chez quelqu'un d'autre : la question se pose avant de changer de titulaire.
                                        picked != null && task.assigneeId != null && picked != task.assigneeId && task.startedOn != null -> askKeepFor = picked
                                        else -> { assignee = picked; keepInProgress = false }
                                    }
                                },
                                progress = progress,
                                onProgress = { progress = it },
                                coach = coach,
                            )
                            if (teamEditing && !creating) ExchangeNoteSection(task.noteIn, noteOut, toManager = false) { noteOut = it }
                        }
                        Column(Modifier.coachField(EditField.PRIORITY, coach)) { PriorityPills(priority, { priority = it }, showMeaning = true) }
                        // Durée suggérée (issue #15.6) : choisir un palier calibré
                        // REMPLACE la durée, même déjà saisie ; on reste libre de la retoucher.
                        Column(Modifier.coachField(EditField.POINTS, coach), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            PointsPills(points, { chosen ->
                                points = chosen
                                chosen?.let(estimates.minutesByPoints::get)?.let { minutes = it.toString() }
                            })
                            PointsGuide(estimates.references)
                        }
                        // Le type dans l'essentiel (3.14.0, issue #166) : il qualifie la tâche, au même titre que la priorité et les
                        // points. Suggestion par type (issue #7) : seulement si la durée est encore vide, pour ne jamais écraser une
                        // estimation saisie à la main.
                        TypeField(type, taskTypes, label = if (teamEditing) Res.string.field_category else Res.string.field_type, modifier = Modifier.coachField(EditField.TYPE, coach)) { chosen ->
                            type = chosen
                            if (minutes.isBlank()) estimates.minutesByType[chosen]?.let { minutes = it.toString() }
                        }
                        Column(Modifier.coachField(EditField.DEADLINE, coach)) { DateField(deadline, { deadline = it }, Res.string.field_deadline, deadlineInvalid, today) }
                        OutlinedTextField(
                            minutes,
                            { value -> minutes = value.filter(Char::isDigit).take(4) },
                            label = { Text(stringResource(Res.string.field_minutes)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().coachField(EditField.MINUTES, coach),
                        )
                        // Durée apprise : une puce qui ne remplit le champ que sur action (on ne remplit que ce qui est vide) ; peu
                        // fiable, elle est dite mais pas proposée.
                        if (learned != null && minutes.isBlank()) {
                            if (learned.reliable) {
                                KairosTextButton(onClick = { minutes = learned.minutes.toString() }, contentPadding = KairosButtonIconPadding) {
                                    Icon(KairosIcons.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(stringResource(Res.string.learned_use, duration(learned.minutes), learned.samples), modifier = Modifier.padding(start = 6.dp))
                                }
                            } else {
                                Text(stringResource(Res.string.learned_weak, learned.samples), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (subtaskDrafts.isNotEmpty()) {
                            SubtaskEditor(subtaskDrafts) { changed -> subtaskDrafts = subtaskDrafts.map { if (it.id == changed.id) changed else it } }
                        }
                        KairosTextButton(onClick = { advanced = !advanced }, modifier = Modifier.expandedState(advanced), contentPadding = KairosButtonIconPadding) {
                            Icon(if (advanced) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
                            Text(stringResource(Res.string.edit_advanced), modifier = Modifier.padding(start = 4.dp))
                        }
                        if (advanced) {
                            Column(Modifier.coachField(EditField.SCHEDULED, coach)) { DateField(scheduled, { scheduled = it }, Res.string.field_scheduled, scheduledInvalid, today) }
                            OutlinedTextField(
                                project,
                                { project = it },
                                label = { Text(stringResource(Res.string.field_project)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().coachField(EditField.PROJECT, coach),
                            )
                            OutlinedTextField(
                                manual,
                                { value -> manual = value.filter(Char::isDigit).take(5) },
                                label = { Text(stringResource(Res.string.field_manual_time)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().coachField(EditField.MANUAL_TIME, coach),
                            )
                            RecurrenceField(recurrence, Modifier.coachField(EditField.RECURRENCE, coach)) {
                                recurrence = it
                                // « Le … du mois » : proposer le jour de l'échéance (sinon du jour affiché) plutôt qu'un champ vide en erreur.
                                if (it == TaskRecurrence.MONTHLY_ON_DAY && dayOfMonth.isBlank()) {
                                    dayOfMonth = ((Dates.parseDate(deadline, dayFirst) ?: day).day).toString()
                                }
                            }
                            if (recurrence == TaskRecurrence.MONTHLY_ON_DAY) {
                                OutlinedTextField(
                                    dayOfMonth,
                                    { value -> dayOfMonth = value.filter(Char::isDigit).take(2) },
                                    label = { Text(stringResource(Res.string.field_day_of_month)) },
                                    isError = dayOfMonthInvalid,
                                    supportingText = {
                                        Text(stringResource(if (dayOfMonthInvalid) Res.string.field_day_invalid else Res.string.field_day_of_month_hint))
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            OutlinedTextField(
                                pin,
                                { pin = it },
                                label = { Text(stringResource(Res.string.field_pin_time)) },
                                isError = pinInvalid,
                                supportingText = { Text(stringResource(if (pinInvalid) Res.string.time_invalid else Res.string.field_pin_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().coachField(EditField.PIN, coach),
                            )
                            OutlinedTextField(
                                newSubtasks,
                                { newSubtasks = it },
                                label = { Text(stringResource(Res.string.field_subtasks)) },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth().tabLeavesField().coachField(EditField.SUBTASKS, coach),
                            )
                            if (countInTeam != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    SwitchRow(counted, stringResource(Res.string.team_count_in_team)) { counted = it }
                                    Text(
                                        stringResource(Res.string.team_count_in_team_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Column(Modifier.coachField(EditField.BLOCKERS, coach), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(Res.string.field_blockers), style = MaterialTheme.typography.labelLarge)
                                Text(stringResource(Res.string.field_blockers_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val language = uiLanguage()
                                val others = remember(task.id, candidates, dependencies, titles, language) {
                                    BlockerChoices.of(task, candidates, dependencies, titles, language)
                                }
                                if (others.isEmpty()) {
                                    Text(stringResource(Res.string.field_blockers_none), style = MaterialTheme.typography.bodySmall)
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    others.forEach { other ->
                                        val id = other.task.id
                                        // Cocher une tâche qui attend déjà celle-ci serait refusé à l'enregistrement : la case est grisée
                                        // et dit pourquoi (une case déjà cochée reste décochable).
                                        val locked = other.cyclic && id !in blockers
                                        LabeledCheckbox(
                                            id in blockers,
                                            { checked -> blockers = if (checked) blockers + id else blockers - id },
                                            if (locked) stringResource(Res.string.field_blockers_cycle, other.label) else other.label,
                                            enabled = !locked,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Tâche retirée par le manager : la garder (elle devient une tâche personnelle) ou la supprimer, au choix du membre.
                    if (received != null && received.removed && tab == 0) {
                        KairosOutlinedButton(onClick = received.onKeep) { Text(stringResource(Res.string.received_keep)) }
                    }
                }
                // Panneau du coach, en bas de la fiche : il ne prend pas le focus, la saisie continue dans la fiche.
                coach?.let { c ->
                    CoachPanel(
                        step = c.current,
                        index = c.step,
                        total = c.steps.size,
                        onPrevious = if (c.step > 0) ({ c.onStep(c.step - 1) }) else null,
                        onNext = { c.onStep(c.step + 1) },
                        onSkip = c.onSkip,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        extra = if (c.current.preview) {
                            // La règle part de la date saisie : échéance, sinon date programmée, sinon aujourd'hui (issue #98).
                            { RecurrencePreview(parsedDeadline ?: parsedScheduled ?: today, recurrence, parsedDayOfMonth, c.holidays, uiLanguage()) }
                        } else {
                            null
                        },
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
                ) {
                    if (!creating) {
                        KairosTextButton(onClick = { confirmDelete = true }) {
                            Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
                    KairosButton(
                        enabled = !invalid && (!creating || title.isNotBlank()),
                        modifier = Modifier.coachHighlight(coach?.current?.save == true, coach != null),
                        onClick = {
                            val edit =
                                TaskEdit(
                                    title = title,
                                    description = description,
                                    priority = priority,
                                    points = points,
                                    deadline = parsedDeadline,
                                    estimatedMinutes = minutes.toIntOrNull(),
                                    projectTag = project,
                                    taskType = type,
                                    scheduledDate = parsedScheduled,
                                    recurrence = recurrence,
                                    recurrenceDayOfMonth = parsedDayOfMonth,
                                    pinTime = parsedPin,
                                    pinDay = day,
                                    manualTimeSpentMinutes = manual.toIntOrNull(),
                                    newSubtasks = newSubtasks,
                                    subtaskEdits = subtaskDrafts.edits(originalSubtasks),
                                    blockerIds = blockers,
                                    reassign = if (teamEditing && assignee != task.assigneeId) Reassignment(assignee, keepInProgress) else null,
                                    teamExcluded = if (countInTeam != null && counted != countInTeam) !counted else null,
                                    noteOut = noteOut.takeIf { (teamEditing || received != null) && it != task.noteOut },
                                    proposal = if (canPropose && proposedTo != task.proposedTo) ProposalChange(proposedTo) else null,
                                )
                            if (team != null && teamEditing) {
                                val percent = progress.roundToInt()
                                // Réaffecté sans garder « En cours » : la tâche repart « À faire » ; le curseur (brouillon) ne la
                                // recommence pas (« En cours 50 % » après un « Non »).
                                val restarts = task.startedOn != null && assignee != task.assigneeId && !keepInProgress
                                team.onSave(edit, percent.takeIf { !restarts && assignee != null && task.status == TaskStatus.TODO && percent != (task.progressPercent ?: 0) })
                            } else if (received != null) {
                                val percent = receivedProgress.roundToInt()
                                received.onSave(edit, percent.takeIf { task.status == TaskStatus.TODO && percent != (task.progressPercent ?: 0) })
                            } else {
                                onSave(edit)
                            }
                        },
                    ) { Text(stringResource(Res.string.action_save)) }
                }
            }
        }
    }

    askKeepFor?.let { picked ->
        KeepInProgressDialog(
            taskTitle = task.title,
            toName = team?.assign?.name(picked).orEmpty(),
            onAnswer = { keep -> assignee = picked; keepInProgress = keep; askKeepFor = null },
            onCancel = { askKeepFor = null },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.delete_confirm_title)) },
            text = { Text(stringResource(Res.string.delete_confirm_body, task.title)) },
            confirmButton = {
                KairosTextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { KairosTextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

private fun recurrenceLabel(r: TaskRecurrence): StringResource = when (r) {
    TaskRecurrence.NONE -> Res.string.task_rec_none
    TaskRecurrence.DAILY -> Res.string.task_rec_daily
    TaskRecurrence.WEEKDAYS -> Res.string.task_rec_weekdays
    TaskRecurrence.WEEKLY -> Res.string.task_rec_weekly
    TaskRecurrence.MONTHLY -> Res.string.task_rec_monthly
    TaskRecurrence.MONTHLY_ON_DAY -> Res.string.task_rec_monthly_on_day
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrenceField(value: TaskRecurrence, modifier: Modifier = Modifier, onChange: (TaskRecurrence) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = stringResource(recurrenceLabel(value)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.field_recurrence)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TaskRecurrence.entries.forEach { r ->
                DropdownMenuItem(text = { Text(stringResource(recurrenceLabel(r))) }, onClick = { onChange(r); expanded = false })
            }
        }
    }
}

/**
 * Note d'échange (docs/spec/equipe-echanges.md § Notes, issue #120) : la dernière note reçue de l'autre côté (auteur, date), puis
 * celle à envoyer avec le prochain rapport ([toManager], tâche reçue) ou le prochain paquet (tâche d'équipe). Bornée à
 * `ExchangeNote.MAX_LENGTH` ; l'aide rappelle que le fichier n'est pas chiffré.
 */
@Composable
private fun ExchangeNoteSection(received: ExchangeNote?, value: String, toManager: Boolean, onChange: (String) -> Unit) {
    val language = uiLanguage()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        received?.let { note ->
            val date = Dates.short(note.at.toLocalDateTime(TimeZone.currentSystemDefault()).date, language)
            Text(stringResource(Res.string.exchange_note_from, note.from, date), style = MaterialTheme.typography.labelLarge)
            Text(note.text, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            value,
            { onChange(it.take(ExchangeNote.MAX_LENGTH)) },
            label = { Text(stringResource(if (toManager) Res.string.exchange_note_for_manager else Res.string.exchange_note_for_member)) },
            supportingText = { Text(stringResource(if (toManager) Res.string.exchange_note_help_report else Res.string.exchange_note_help_pack, ExchangeNote.MAX_LENGTH)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().tabLeavesField(),
        )
    }
}

/**
 * Proposer une tâche personnelle au manager d'une équipe dont on a reçu des tâches (docs/spec/equipe-echanges.md § Propositions,
 * issue #121) : un interrupteur par équipe (une seule cible à la fois) ; la proposition part avec le prochain rapport.
 */
@Composable
private fun ProposalSection(targets: List<TeamOrigin>, selected: String?, onChange: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        targets.forEach { origin ->
            SwitchRow(selected == origin.key, stringResource(Res.string.proposal_switch, managerLabel(origin), origin.teamName)) { on ->
                onChange(if (on) origin.key else null)
            }
        }
        Text(stringResource(Res.string.proposal_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
