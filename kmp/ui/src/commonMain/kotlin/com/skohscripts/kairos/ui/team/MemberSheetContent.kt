package com.skohscripts.kairos.ui.team

import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosRowIconButton
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.settings.FieldError
import com.skohscripts.kairos.core.settings.FieldErrorKind
import com.skohscripts.kairos.core.team.MemberAbsence
import com.skohscripts.kairos.core.team.MemberForm
import com.skohscripts.kairos.core.team.TeamEvent
import com.skohscripts.kairos.core.team.TeamMember
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.action_save
import com.skohscripts.kairos.ui.generated.resources.member_absence_add
import com.skohscripts.kairos.ui.generated.resources.member_absence_delete
import com.skohscripts.kairos.ui.generated.resources.member_absence_edit
import com.skohscripts.kairos.ui.generated.resources.member_absences_empty
import com.skohscripts.kairos.ui.generated.resources.member_absences_help
import com.skohscripts.kairos.ui.generated.resources.member_absences_title
import com.skohscripts.kairos.ui.generated.resources.member_activity_empty
import com.skohscripts.kairos.ui.generated.resources.member_activity_help
import com.skohscripts.kairos.ui.generated.resources.member_activity_title
import com.skohscripts.kairos.ui.generated.resources.member_archive
import com.skohscripts.kairos.ui.generated.resources.member_archive_body_many
import com.skohscripts.kairos.ui.generated.resources.member_archive_body_none
import com.skohscripts.kairos.ui.generated.resources.member_archive_body_one
import com.skohscripts.kairos.ui.generated.resources.member_archive_title
import com.skohscripts.kairos.ui.generated.resources.member_delete
import com.skohscripts.kairos.ui.generated.resources.member_delete_body
import com.skohscripts.kairos.ui.generated.resources.member_delete_title
import com.skohscripts.kairos.ui.generated.resources.member_error_name
import com.skohscripts.kairos.ui.generated.resources.member_field_availability
import com.skohscripts.kairos.ui.generated.resources.member_field_availability_help
import com.skohscripts.kairos.ui.generated.resources.member_field_hours
import com.skohscripts.kairos.ui.generated.resources.member_field_hours_help
import com.skohscripts.kairos.ui.generated.resources.member_field_name
import com.skohscripts.kairos.ui.generated.resources.member_field_role
import com.skohscripts.kairos.ui.generated.resources.member_field_role_help
import com.skohscripts.kairos.ui.generated.resources.member_field_self
import com.skohscripts.kairos.ui.generated.resources.member_field_self_help
import com.skohscripts.kairos.ui.generated.resources.member_restore
import com.skohscripts.kairos.ui.generated.resources.member_sheet_close
import com.skohscripts.kairos.ui.generated.resources.member_sheet_new_title
import com.skohscripts.kairos.ui.generated.resources.member_sheet_title
import com.skohscripts.kairos.ui.generated.resources.settings_error_integer
import com.skohscripts.kairos.ui.generated.resources.settings_error_max
import com.skohscripts.kairos.ui.generated.resources.settings_error_min
import com.skohscripts.kairos.ui.generated.resources.settings_error_number
import com.skohscripts.kairos.ui.generated.resources.settings_error_required
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.settings.SwitchRow
import androidx.compose.ui.text.intl.Locale
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.exchange_last_report
import com.skohscripts.kairos.ui.generated.resources.exchange_no_report
import com.skohscripts.kairos.ui.generated.resources.exchange_section_title
import com.skohscripts.kairos.ui.generated.resources.exchange_send_action
import com.skohscripts.kairos.ui.generated.resources.exchange_send_help
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/** Absence en cours d'édition dans la fiche : nouvelle ([existing] nul) ou existante. */
private class AbsenceTarget(val existing: MemberAbsence?)

/**
 * Contenu de la fiche membre (docs/spec/equipe.md § Destination Équipe), sans
 * dépôt : les textes des champs, les absences et les gestes remontent par
 * rappels ([MemberSheet] les branche). [member] nul : nouveau membre (ni
 * absences, ni archivage, ni suppression). [compact] : plein écran (< 600 dp) ;
 * sinon une carte de dialogue (28 dp, seul élément flottant à porter une
 * ombre). Un seul « Enregistrer » pour les champs, avec l'erreur à la place de
 * l'aide ; les absences s'enregistrent une à une ([onAddAbsence],
 * [onUpdateAbsence], [onDeleteAbsence]). [canDelete] : le membre n'a jamais eu
 * de tâche ; [openTaskCount] : tâches ouvertes qu'un archivage remet au backlog.
 * [loadView] ajoute, après les champs, la section « Charge » du jalon E4
 * (`equipe-charge.md` : capacité, charge par semaine et par catégorie, tâches du plan).
 * Un membre autre que « moi » a aussi la section « Échanges » du jalon E6
 * (`equipe-echanges.md` : « Envoyer ses tâches… », date du dernier rapport intégré).
 */
@Composable
fun MemberSheetContent(
    member: TeamMember?,
    absences: List<MemberAbsence>,
    settings: Settings,
    today: LocalDate,
    openTaskCount: Int,
    canDelete: Boolean,
    compact: Boolean,
    onSave: (MemberForm.Valid, Boolean) -> Unit = { _, _ -> },
    onClose: () -> Unit = {},
    onArchive: () -> Unit = {},
    onRestore: () -> Unit = {},
    onDelete: () -> Unit = {},
    onAddAbsence: (LocalDate, LocalDate, String) -> Unit = { _, _, _ -> },
    onUpdateAbsence: (Long, LocalDate, LocalDate, String) -> Unit = { _, _, _, _ -> },
    onDeleteAbsence: (Long) -> Unit = {},
    /** Événements du journal de ses tâches sur 30 jours (section « Activité », membre existant seulement). */
    activity: List<TeamEvent> = emptyList(),
    /** Tous les membres, pour nommer les titulaires cités dans l'activité. */
    members: List<TeamMember> = emptyList(),
    /** Charge du membre sur l'horizon (section « Charge », membre actif seulement ; `null` : pas de section). */
    loadView: MemberLoadView? = null,
    /** « Envoyer ses tâches… » (échanges, jalon E6) : enregistre le paquet du membre (docs/spec/equipe-echanges.md). */
    onSendTasks: () -> Unit = {},
) {
    var input by remember(member?.id) { mutableStateOf(if (member == null) MemberForm.newInput(settings) else MemberForm.inputOf(member)) }
    var isSelf by remember(member?.id) { mutableStateOf(member?.isSelf == true) }
    var errors by remember(member?.id) { mutableStateOf<Map<String, FieldError>>(emptyMap()) }
    var editing by remember { mutableStateOf<AbsenceTarget?>(null) }
    var confirmArchive by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val archived = member?.archived == true
    val padding = if (compact) 16.dp else 24.dp

    fun change(key: String, update: (MemberForm.Input) -> MemberForm.Input) {
        input = update(input)
        errors = errors - key
    }

    fun save() {
        val result = MemberForm.validate(input)
        errors = result.errors
        result.valid?.let { onSave(it, isSelf && !archived) }
    }

    Surface(
        shape = if (compact) RectangleShape else MaterialTheme.shapes.extraLarge,
        color = if (compact) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (compact) 0.dp else 6.dp,
        shadowElevation = if (compact) 0.dp else 6.dp,
        modifier = if (compact) Modifier.fillMaxSize() else Modifier.padding(16.dp).widthIn(max = 640.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            // En-tête : fermer (plein écran seulement) et titre.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = if (compact) 4.dp else padding, end = padding, top = if (compact) 4.dp else padding, bottom = if (compact) 4.dp else 0.dp),
            ) {
                if (compact) {
                    IconButton(onClick = onClose) { Icon(KairosIcons.Close, contentDescription = stringResource(Res.string.member_sheet_close)) }
                }
                Text(
                    stringResource(if (member == null) Res.string.member_sheet_new_title else Res.string.member_sheet_title),
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.heading(),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f, fill = compact).verticalScroll(rememberScrollState()).padding(horizontal = padding, vertical = 12.dp),
            ) {
                OutlinedTextField(
                    value = input.name,
                    onValueChange = { v -> change(MemberForm.NAME) { it.copy(name = v) } },
                    label = { Text(stringResource(Res.string.member_field_name)) },
                    isError = MemberForm.NAME in errors,
                    supportingText = errors[MemberForm.NAME]?.let { e -> { Text(memberErrorMessage(MemberForm.NAME, e)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = input.role,
                    onValueChange = { v -> change("role") { it.copy(role = v) } },
                    label = { Text(stringResource(Res.string.member_field_role)) },
                    supportingText = { Text(stringResource(Res.string.member_field_role_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = input.availabilityPercent,
                    onValueChange = { v -> change(MemberForm.AVAILABILITY) { it.copy(availabilityPercent = v) } },
                    label = { Text(stringResource(Res.string.member_field_availability)) },
                    isError = MemberForm.AVAILABILITY in errors,
                    supportingText = { Text(errors[MemberForm.AVAILABILITY]?.let { memberErrorMessage(MemberForm.AVAILABILITY, it) } ?: stringResource(Res.string.member_field_availability_help)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = input.hoursPerDay,
                    onValueChange = { v -> change(MemberForm.HOURS) { it.copy(hoursPerDay = v) } },
                    label = { Text(stringResource(Res.string.member_field_hours)) },
                    isError = MemberForm.HOURS in errors,
                    supportingText = { Text(errors[MemberForm.HOURS]?.let { memberErrorMessage(MemberForm.HOURS, it) } ?: stringResource(Res.string.member_field_hours_help)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Un membre archivé ne peut pas être « moi » : la ligne n'est pas proposée.
                if (!archived) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SwitchRow(isSelf, stringResource(Res.string.member_field_self)) { isSelf = it }
                        Text(
                            stringResource(Res.string.member_field_self_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (member != null) {
                    if (loadView != null && !archived) {
                        HorizontalDivider()
                        MemberLoadSection(loadView, today)
                    }
                    if (!member.isSelf && (!archived || member.lastReportAt != null)) {
                        HorizontalDivider()
                        ExchangeSection(member, onSendTasks)
                    }
                    HorizontalDivider()
                    AbsencesSection(absences, today, onAdd = { editing = AbsenceTarget(null) }, onEdit = { editing = AbsenceTarget(it) }, onDelete = onDeleteAbsence)
                    HorizontalDivider()
                    ActivitySection(activity, AssignContext(members, emptyList(), emptyList(), settings, today), today)
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (archived) {
                            KairosOutlinedButton(onClick = onRestore, contentPadding = KairosButtonIconPadding) {
                                Icon(KairosIcons.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(Res.string.member_restore), modifier = Modifier.padding(start = 6.dp))
                            }
                        } else {
                            KairosOutlinedButton(onClick = { confirmArchive = true }, contentPadding = KairosButtonIconPadding) {
                                Icon(KairosIcons.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(Res.string.member_archive), modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        if (canDelete) {
                            KairosTextButton(
                                onClick = { confirmDelete = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = KairosButtonIconPadding,
                            ) {
                                Icon(KairosIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(Res.string.member_delete), modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }

            // Pied : Annuler / Enregistrer, toujours visible.
            Surface(color = if (compact) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = padding, vertical = if (compact) 8.dp else 16.dp),
                ) {
                    KairosTextButton(onClick = onClose) { Text(stringResource(Res.string.action_cancel)) }
                    KairosButton(onClick = ::save) { Text(stringResource(Res.string.action_save)) }
                }
            }
        }
    }

    editing?.let { target ->
        AbsenceEditor(
            initial = target.existing,
            today = today,
            onConfirm = { start, end, label ->
                editing = null
                val existing = target.existing
                if (existing == null) onAddAbsence(start, end, label) else onUpdateAbsence(existing.id, start, end, label)
            },
            onDismiss = { editing = null },
        )
    }

    if (confirmArchive && member != null) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text(stringResource(Res.string.member_archive_title, member.name)) },
            text = {
                Text(
                    when (openTaskCount) {
                        0 -> stringResource(Res.string.member_archive_body_none)
                        1 -> stringResource(Res.string.member_archive_body_one)
                        else -> stringResource(Res.string.member_archive_body_many, openTaskCount)
                    },
                )
            },
            confirmButton = {
                KairosTextButton(onClick = { confirmArchive = false; onArchive() }) { Text(stringResource(Res.string.member_archive)) }
            },
            dismissButton = { KairosTextButton(onClick = { confirmArchive = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }

    if (confirmDelete && member != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.member_delete_title, member.name)) },
            text = { Text(stringResource(Res.string.member_delete_body)) },
            confirmButton = {
                KairosTextButton(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.member_delete)) }
            },
            dismissButton = { KairosTextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/**
 * « Échanges » (docs/spec/equipe-echanges.md § Côté manager) : « Envoyer ses tâches… » (absent pour un membre archivé : son
 * paquet est impossible), la phrase qui rappelle que le fichier n'est pas chiffré, et la date du dernier rapport intégré.
 */
@Composable
private fun ExchangeSection(member: TeamMember, onSend: () -> Unit) {
    val language = Locale.current.language
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.exchange_section_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
        if (!member.archived) {
            Text(stringResource(Res.string.exchange_send_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            KairosOutlinedButton(onClick = onSend, contentPadding = KairosButtonIconPadding) {
                Icon(KairosIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.exchange_send_action), modifier = Modifier.padding(start = 6.dp))
            }
        }
        val last = member.lastReportAt?.toLocalDateTime(TimeZone.currentSystemDefault())
        Text(
            if (last == null) stringResource(Res.string.exchange_no_report)
            else stringResource(Res.string.exchange_last_report, "${Dates.short(last.date, language)} ${Dates.time(last, language)}"),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * « Activité » : les événements des tâches du membre sur 30 jours, du plus récent au
 * plus ancien, avec le titre de la tâche (« 30 sept. · Refonte API : Assignée à Léa »).
 */
@Composable
private fun ActivitySection(activity: List<TeamEvent>, context: AssignContext, today: LocalDate) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.member_activity_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
        Text(stringResource(Res.string.member_activity_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (activity.isEmpty()) {
            Text(stringResource(Res.string.member_activity_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        }
        activity.forEach { event ->
            Text(
                eventLine(event, context, today, withTitle = true),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(vertical = 6.dp),
            )
        }
    }
}

/** Liste des absences du membre (la plus proche d'abord) : modifier, supprimer, « Ajouter une absence ». */
@Composable
private fun AbsencesSection(
    absences: List<MemberAbsence>,
    today: LocalDate,
    onAdd: () -> Unit,
    onEdit: (MemberAbsence) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.member_absences_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
        Text(stringResource(Res.string.member_absences_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (absences.isEmpty()) {
            Text(
                stringResource(Res.string.member_absences_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        absences.forEach { absence ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button) { onEdit(absence) },
                ) {
                    Icon(KairosIcons.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Column {
                        Text(absenceDates(absence, today), style = MaterialTheme.typography.bodyLarge)
                        if (absence.label.isNotBlank()) {
                            Text(absence.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                KairosRowIconButton(KairosIcons.Edit, stringResource(Res.string.member_absence_edit), onClick = { onEdit(absence) })
                KairosRowIconButton(KairosIcons.Delete, stringResource(Res.string.member_absence_delete), onClick = { onDelete(absence.id) })
            }
        }
        KairosOutlinedButton(onClick = onAdd, contentPadding = KairosButtonIconPadding) {
            Icon(KairosIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.member_absence_add), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** Texte d'une erreur de champ de la fiche : celui du nom, sinon les messages communs des Réglages. */
@Composable
private fun memberErrorMessage(key: String, error: FieldError): String = when {
    key == MemberForm.NAME -> stringResource(Res.string.member_error_name)
    else -> when (error.kind) {
        FieldErrorKind.REQUIRED -> stringResource(Res.string.settings_error_required)
        FieldErrorKind.NOT_INTEGER -> stringResource(Res.string.settings_error_integer)
        FieldErrorKind.NOT_NUMBER -> stringResource(Res.string.settings_error_number)
        FieldErrorKind.TOO_SMALL -> stringResource(Res.string.settings_error_min, error.bound.orEmpty())
        FieldErrorKind.TOO_LARGE -> stringResource(Res.string.settings_error_max, error.bound.orEmpty())
        // Sans objet pour ce formulaire (dates, couleurs des Réglages).
        FieldErrorKind.INVALID_DATE, FieldErrorKind.INVALID_COLOR -> stringResource(Res.string.settings_error_required)
    }
}
