package com.skohscripts.kairos.ui.guide

import com.skohscripts.kairos.ui.generated.resources.task_rec_monthly
import com.skohscripts.kairos.ui.generated.resources.task_rec_weekly
import com.skohscripts.kairos.ui.generated.resources.task_rec_weekdays
import com.skohscripts.kairos.ui.generated.resources.task_rec_daily
import com.skohscripts.kairos.ui.generated.resources.rec_preview_row_chosen
import com.skohscripts.kairos.ui.generated.resources.rec_preview_row
import com.skohscripts.kairos.ui.generated.resources.rec_preview_on_day
import com.skohscripts.kairos.ui.generated.resources.rec_preview_title
import com.skohscripts.kairos.ui.generated.resources.rec_coach_save_body
import com.skohscripts.kairos.ui.generated.resources.rec_coach_save_title
import com.skohscripts.kairos.ui.generated.resources.rec_coach_size_body
import com.skohscripts.kairos.ui.generated.resources.rec_coach_size_title
import com.skohscripts.kairos.ui.generated.resources.rec_coach_rule_body
import com.skohscripts.kairos.ui.generated.resources.rec_coach_rule_title
import com.skohscripts.kairos.ui.generated.resources.rec_coach_date_body
import com.skohscripts.kairos.ui.generated.resources.rec_coach_date_title
import com.skohscripts.kairos.ui.generated.resources.rec_coach_title_body
import com.skohscripts.kairos.ui.generated.resources.rec_coach_title_title
import kotlinx.datetime.LocalDate
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.engine.Recurrence
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.day.EditField
import com.skohscripts.kairos.ui.day.SheetSection
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.coach_deadline_body
import com.skohscripts.kairos.ui.generated.resources.coach_deadline_title
import com.skohscripts.kairos.ui.generated.resources.coach_description_body
import com.skohscripts.kairos.ui.generated.resources.coach_description_title
import com.skohscripts.kairos.ui.generated.resources.coach_minutes_body
import com.skohscripts.kairos.ui.generated.resources.coach_minutes_title
import com.skohscripts.kairos.ui.generated.resources.coach_more_body
import com.skohscripts.kairos.ui.generated.resources.coach_more_title
import com.skohscripts.kairos.ui.generated.resources.coach_placed_inbox_body
import com.skohscripts.kairos.ui.generated.resources.coach_placed_inbox_title
import com.skohscripts.kairos.ui.generated.resources.coach_placed_rank_body
import com.skohscripts.kairos.ui.generated.resources.coach_placed_rank_title
import com.skohscripts.kairos.ui.generated.resources.coach_placed_row_body
import com.skohscripts.kairos.ui.generated.resources.coach_placed_row_title
import com.skohscripts.kairos.ui.generated.resources.coach_placed_slot_body
import com.skohscripts.kairos.ui.generated.resources.coach_placed_slot_title
import com.skohscripts.kairos.ui.generated.resources.coach_points_body
import com.skohscripts.kairos.ui.generated.resources.coach_points_title
import com.skohscripts.kairos.ui.generated.resources.coach_priority_body
import com.skohscripts.kairos.ui.generated.resources.coach_priority_title
import com.skohscripts.kairos.ui.generated.resources.coach_save_body
import com.skohscripts.kairos.ui.generated.resources.coach_save_title
import com.skohscripts.kairos.ui.generated.resources.coach_title_body
import com.skohscripts.kairos.ui.generated.resources.coach_title_title
import com.skohscripts.kairos.ui.generated.resources.coach_type_body
import com.skohscripts.kairos.ui.generated.resources.coach_type_title
import com.skohscripts.kairos.ui.generated.resources.guide_finish
import com.skohscripts.kairos.ui.generated.resources.guide_next
import com.skohscripts.kairos.ui.generated.resources.guide_previous
import com.skohscripts.kairos.ui.generated.resources.guide_step_counter
import com.skohscripts.kairos.ui.generated.resources.coach_skip
import com.skohscripts.kairos.ui.generated.resources.team_coach_title_title
import com.skohscripts.kairos.ui.generated.resources.team_coach_title_body
import com.skohscripts.kairos.ui.generated.resources.team_coach_assignee_title
import com.skohscripts.kairos.ui.generated.resources.team_coach_assignee_body
import com.skohscripts.kairos.ui.generated.resources.team_coach_size_title
import com.skohscripts.kairos.ui.generated.resources.team_coach_size_body
import com.skohscripts.kairos.ui.generated.resources.team_coach_effort_title
import com.skohscripts.kairos.ui.generated.resources.team_coach_effort_body
import com.skohscripts.kairos.ui.generated.resources.team_coach_category_title
import com.skohscripts.kairos.ui.generated.resources.team_coach_category_body
import com.skohscripts.kairos.ui.generated.resources.team_coach_more_title
import com.skohscripts.kairos.ui.generated.resources.team_coach_more_body
import com.skohscripts.kairos.ui.generated.resources.team_coach_save_title
import com.skohscripts.kairos.ui.generated.resources.team_coach_save_body
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Une étape de l'accompagnement de la première tâche (docs/spec/premiere-tache.md) : [fields] les champs de la fiche qu'elle
 * explique et met en évidence ; [save] l'étape qui met en évidence « Enregistrer ». Les étapes de placement n'ont ni l'un ni l'autre.
 */
internal class CoachStep(
    val icon: () -> ImageVector,
    val title: StringResource,
    val body: StringResource,
    val fields: Set<EditField> = emptySet(),
    val save: Boolean = false,
    /** L'étape montre, sous son texte, la prochaine échéance de chaque règle de récurrence ([RecurrencePreview], issue #98). */
    val preview: Boolean = false,
) {
    /** Les sections repliables des champs qu'elle explique : la fiche les ouvre (docs/spec/fiche-tache.md). */
    val sections: Set<SheetSection> get() = fields.mapNotNullTo(LinkedHashSet()) { it.section }
}

/** Les étapes du coach : celles de la fiche (neuf, dans l'ordre de la fiche), puis celles du placement (trois, sur la vue Jour). */
internal object FirstTaskCoach {
    val STEPS: List<CoachStep> = listOf(
        CoachStep({ KairosIcons.Edit }, Res.string.coach_title_title, Res.string.coach_title_body, setOf(EditField.TITLE)),
        CoachStep({ KairosIcons.Description }, Res.string.coach_description_title, Res.string.coach_description_body, setOf(EditField.DESCRIPTION)),
        CoachStep({ KairosIcons.Warning }, Res.string.coach_priority_title, Res.string.coach_priority_body, setOf(EditField.PRIORITY)),
        CoachStep({ KairosIcons.Balance }, Res.string.coach_points_title, Res.string.coach_points_body, setOf(EditField.POINTS)),
        // Après les points, avant la durée (3.14.0, issue #166) : le type peut suggérer cette durée.
        CoachStep({ KairosIcons.Layers }, Res.string.coach_type_title, Res.string.coach_type_body, setOf(EditField.TYPE)),
        CoachStep({ KairosIcons.Schedule }, Res.string.coach_minutes_title, Res.string.coach_minutes_body, setOf(EditField.MINUTES)),
        CoachStep({ KairosIcons.DateRange }, Res.string.coach_deadline_title, Res.string.coach_deadline_body, setOf(EditField.DEADLINE, EditField.SCHEDULED)),
        CoachStep(
            { KairosIcons.ExpandMore }, Res.string.coach_more_title, Res.string.coach_more_body,
            setOf(EditField.PROJECT, EditField.MANUAL_TIME, EditField.RECURRENCE, EditField.PIN, EditField.SUBTASKS, EditField.BLOCKERS),
        ),
        CoachStep({ KairosIcons.Check }, Res.string.coach_save_title, Res.string.coach_save_body, save = true),
    )

    /** Après la création : le rang, le créneau, la ligne. Une tâche encore « À traiter » remplace le rang par ce qui lui manque. */
    fun placement(placed: Boolean): List<CoachStep> = listOf(
        if (placed) {
            CoachStep({ KairosIcons.Star }, Res.string.coach_placed_rank_title, Res.string.coach_placed_rank_body)
        } else {
            CoachStep({ KairosIcons.Inbox }, Res.string.coach_placed_inbox_title, Res.string.coach_placed_inbox_body)
        },
        CoachStep({ KairosIcons.Schedule }, Res.string.coach_placed_slot_title, Res.string.coach_placed_slot_body),
        CoachStep({ KairosIcons.CheckCircle }, Res.string.coach_placed_row_title, Res.string.coach_placed_row_body),
    )
}

/**
 * Les étapes du coach de la première tâche d'**équipe** (docs/spec/premiere-tache.md § Première tâche d'équipe, issue #97) : la même
 * fiche, avec « Assigné à » et l'effort, et une étape par groupe de champs, dans l'ordre de la fiche d'équipe.
 */
internal object TeamTaskCoach {
    val STEPS: List<CoachStep> = listOf(
        CoachStep({ KairosIcons.Edit }, Res.string.team_coach_title_title, Res.string.team_coach_title_body, setOf(EditField.TITLE, EditField.DESCRIPTION)),
        CoachStep({ KairosIcons.PersonAdd }, Res.string.team_coach_assignee_title, Res.string.team_coach_assignee_body, setOf(EditField.ASSIGNEE)),
        CoachStep({ KairosIcons.Balance }, Res.string.team_coach_size_title, Res.string.team_coach_size_body, setOf(EditField.PRIORITY, EditField.POINTS)),
        CoachStep({ KairosIcons.Schedule }, Res.string.team_coach_effort_title, Res.string.team_coach_effort_body, setOf(EditField.MINUTES, EditField.DEADLINE)),
        CoachStep({ KairosIcons.Label }, Res.string.team_coach_category_title, Res.string.team_coach_category_body, setOf(EditField.TYPE)),
        CoachStep(
            { KairosIcons.ExpandMore }, Res.string.team_coach_more_title, Res.string.team_coach_more_body,
            setOf(EditField.SCHEDULED, EditField.PROJECT, EditField.MANUAL_TIME, EditField.RECURRENCE, EditField.PIN, EditField.SUBTASKS, EditField.BLOCKERS),
        ),
        CoachStep({ KairosIcons.Check }, Res.string.team_coach_save_title, Res.string.team_coach_save_body, save = true),
    )
}

/**
 * Les étapes du coach de la première tâche **récurrente** (docs/spec/premiere-tache.md § Première tâche récurrente, issue #98) : la
 * même fiche, de ce qui revient à l'enregistrement, l'étape de la règle montrant la prochaine échéance de chaque règle.
 */
internal object RecurrenceCoach {
    val STEPS: List<CoachStep> = listOf(
        CoachStep({ KairosIcons.Edit }, Res.string.rec_coach_title_title, Res.string.rec_coach_title_body, setOf(EditField.TITLE)),
        CoachStep({ KairosIcons.DateRange }, Res.string.rec_coach_date_title, Res.string.rec_coach_date_body, setOf(EditField.DEADLINE, EditField.SCHEDULED)),
        CoachStep({ KairosIcons.Repeat }, Res.string.rec_coach_rule_title, Res.string.rec_coach_rule_body, setOf(EditField.RECURRENCE), preview = true),
        CoachStep({ KairosIcons.Balance }, Res.string.rec_coach_size_title, Res.string.rec_coach_size_body, setOf(EditField.PRIORITY, EditField.POINTS, EditField.MINUTES)),
        CoachStep({ KairosIcons.Check }, Res.string.rec_coach_save_title, Res.string.rec_coach_save_body, save = true),
    )
}

/**
 * Prochaine échéance de chaque règle à partir de [base] (`Recurrence.preview`), la règle [chosen] en gras et marquée « (ta règle) » :
 * c'est la comparaison qui explique la différence entre les règles. [dayOfMonth] : le N de « le N du mois » s'il est saisi.
 */
@Composable
internal fun RecurrencePreview(base: LocalDate, chosen: TaskRecurrence, dayOfMonth: Int?, holidays: Set<LocalDate>, language: String) {
    val preview = remember(base, dayOfMonth, holidays) { Recurrence.preview(base, dayOfMonth, holidays) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(Res.string.rec_preview_title, Dates.long(base, language)), style = MaterialTheme.typography.labelMedium)
        preview.forEach { (rule, next) ->
            val name = when (rule) {
                TaskRecurrence.DAILY -> stringResource(Res.string.task_rec_daily)
                TaskRecurrence.WEEKDAYS -> stringResource(Res.string.task_rec_weekdays)
                TaskRecurrence.WEEKLY -> stringResource(Res.string.task_rec_weekly)
                TaskRecurrence.MONTHLY -> stringResource(Res.string.task_rec_monthly)
                else -> stringResource(Res.string.rec_preview_on_day, dayOfMonth ?: base.day)
            }
            val mine = rule == chosen
            Text(
                stringResource(if (mine) Res.string.rec_preview_row_chosen else Res.string.rec_preview_row, name, Dates.long(next, language)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/**
 * L'accompagnement en cours dans la fiche : l'étape courante [step] (conservée par l'appelant, `rememberSaveable`), le moyen de la
 * changer et de l'abandonner. Fermer le coach n'écrit rien.
 */
internal class CoachSession(
    val step: Int,
    val onStep: (Int) -> Unit,
    val onSkip: () -> Unit,
    /** Les étapes : celles de la première tâche, ou celles de la première tâche d'équipe ([TeamTaskCoach.STEPS], issue #97). */
    val steps: List<CoachStep> = FirstTaskCoach.STEPS,
    /** Fériés des réglages, pour l'aperçu des règles de récurrence ([CoachStep.preview]). */
    val holidays: Set<LocalDate> = emptySet(),
) {
    val current: CoachStep get() = steps[step.coerceIn(0, steps.lastIndex)]
    fun highlights(field: EditField): Boolean = field in current.fields
}

/**
 * Panneau d'explication, non bloquant : une carte MD3 sans ombre, qui garde le focus et le clavier sur la fiche. L'étape est annoncée
 * (région vivante polie) pour TalkBack. « Passer » abandonne le coach ; « Précédent » n'existe qu'à partir de la deuxième étape.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoachPanel(
    step: CoachStep,
    index: Int,
    total: Int,
    onPrevious: (() -> Unit)?,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    /** Ce que l'étape montre sous son texte (l'aperçu des règles de récurrence) ; `null` : rien. */
    extra: (@Composable () -> Unit)? = null,
) {
    val last = index >= total - 1
    OutlinedCard(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(step.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(stringResource(step.title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.heading())
            }
            Text(
                stringResource(Res.string.guide_step_counter, index + 1, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(step.body), style = MaterialTheme.typography.bodyMedium)
            extra?.invoke()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                KairosTextButton(onClick = onSkip) { Text(stringResource(if (last) Res.string.guide_finish else Res.string.coach_skip)) }
                if (onPrevious != null) KairosTextButton(onClick = onPrevious) { Text(stringResource(Res.string.guide_previous)) }
                if (!last) KairosButton(onClick = onNext) { Text(stringResource(Res.string.guide_next)) }
            }
        }
    }
}

/**
 * Balise de test et, quand le coach explique ce champ, contour primaire (jamais la couleur seule : le panneau nomme l'étape) et
 * défilement jusqu'à lui. Sans coach, seule la balise est posée et la mise en page ne change pas.
 */
@Composable
internal fun Modifier.coachField(field: EditField, coach: CoachSession?): Modifier = coachHighlight(coach?.highlights(field) ?: false, coach != null).testTag(field.tag)

/** Le contour d'un élément que le coach explique ; [coaching] : une marge constante évite que la fiche saute d'étape en étape. */
@Composable
internal fun Modifier.coachHighlight(active: Boolean, coaching: Boolean = true): Modifier {
    if (!coaching) return this
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(active) { if (active) requester.bringIntoView() }
    val color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent
    return bringIntoViewRequester(requester).padding(2.dp).border(BorderStroke(2.dp, color), MaterialTheme.shapes.small).padding(4.dp)
}
