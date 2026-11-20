package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.engine.Scheduling.TimelineKind
import com.skohscripts.kairos.core.day.DayView
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.timeline_rail_hint
import com.skohscripts.kairos.ui.generated.resources.timeline_free_day
import com.skohscripts.kairos.ui.generated.resources.timeline_title
import org.jetbrains.compose.resources.stringResource
import com.skohscripts.kairos.ui.theme.KairosOutlinedCard

/** Hauteur d'une minute sur la frise (1 min = 1 dp). */
private val MINUTE = 1.dp
private val GUTTER = 44.dp

/** Hauteur d'un libellé de la frise, en minutes (1 dp par minute). */
private const val LABEL_MINUTES = 16

/**
 * Carte « Agenda » : la journée de travail heure par heure, créneaux et
 * tâches placées (`build_timeline`). Couleurs de la charte : occupé neutre,
 * travail en conteneur secondaire, épinglée avec contour, conflit en
 * conteneur d'erreur, deep work en tertiaire (hachures pour le bloc réservé).
 */
@Composable
internal fun TimelineCard(view: DayView, language: String, modifier: Modifier = Modifier) {
    // La plage du jour (docs/spec/planning-personnalise.md § Lot B1) : celle des réglages, ou l'horaire propre à ce jour de la semaine.
    val start = view.workStartHour
    val end = maxOf(view.workEndHour, start)
    val scheme = MaterialTheme.colorScheme
    val lineColor = scheme.outlineVariant
    KairosOutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.timeline_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = if (view.freeDay) 4.dp else 12.dp))
            if (view.freeDay) {
                Text(
                    stringResource(Res.string.timeline_free_day),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            // 8 dp de marge en haut et en bas : les graduations extrêmes restent lisibles.
            Box(Modifier.fillMaxWidth().height(MINUTE * ((end - start) * 60) + 16.dp).padding(vertical = 8.dp)) {
                for (h in start..end) {
                    val top = MINUTE * ((h - start) * 60)
                    HorizontalDivider(Modifier.offset(y = top).padding(start = GUTTER), color = lineColor)
                    Text(
                        Dates.hour(h, language),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.offset(y = top - 7.dp),
                    )
                }
                // Plusieurs plages (docs/spec/planning-personnalise.md § Lot B2) : les trous entre elles sont grisés (fond neutre, jamais
                // d'opacité), et chaque plage réservée à un contexte porte son nom.
                view.windows.zipWithNext().forEach { (before, after) ->
                    val top = MINUTE * ((before.endHour - start) * 60)
                    Box(
                        Modifier
                            .padding(start = GUTTER + 4.dp, end = 2.dp)
                            .offset(y = top)
                            .fillMaxWidth()
                            .height(MINUTE * ((after.startHour - before.endHour) * 60))
                            .background(scheme.surfaceContainerHigh, MaterialTheme.shapes.small),
                    )
                }
                // Rail « réel » : le temps chronométré, dans la gouttière gauche, à côté du planifié.
                for (e in view.sessionTimeline) {
                    Box(
                        Modifier
                            .padding(start = GUTTER - 2.dp)
                            .offset(y = MINUTE * e.topMinutes)
                            .width(5.dp)
                            .height(MINUTE * e.heightMinutes)
                            .background(scheme.primary, MaterialTheme.shapes.extraSmall),
                    )
                }
                // Un fond (deep work, occupé) dont le haut est recouvert par une tâche qui commence moins de LABEL_MINUTES
                // après lui tait son libellé : les deux textes se chevauchaient (une tâche posée à 10 h 13 dans une fenêtre
                // de 10 h). Les hachures et la couleur de la tâche disent déjà ce qu'est le fond.
                val foreground = view.timeline.filter { it.kind != TimelineKind.DEEPWORK && it.kind != TimelineKind.BUSY && it.kind != TimelineKind.SESSION }
                for (e in view.timeline) {
                    val background = e.kind == TimelineKind.DEEPWORK || e.kind == TimelineKind.BUSY
                    val labelCovered = background && foreground.any { it.topMinutes >= e.topMinutes && it.topMinutes < e.topMinutes + LABEL_MINUTES }
                    val (container, content) = when (e.kind) {
                        TimelineKind.BUSY -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
                        TimelineKind.WORK, TimelineKind.PINNED -> scheme.secondaryContainer to scheme.onSecondaryContainer
                        TimelineKind.CONFLICT -> scheme.errorContainer to scheme.onErrorContainer
                        TimelineKind.DEEPWORK -> scheme.surface to scheme.onTertiaryContainer
                        TimelineKind.DEEPWORK_TASK -> scheme.tertiaryContainer to scheme.onTertiaryContainer
                        TimelineKind.SESSION -> continue
                    }
                    val border = when (e.kind) {
                        TimelineKind.PINNED -> BorderStroke(1.dp, scheme.secondary)
                        TimelineKind.CONFLICT -> BorderStroke(1.dp, scheme.error)
                        TimelineKind.DEEPWORK -> BorderStroke(1.dp, scheme.tertiary)
                        else -> null
                    }
                    val hatch = if (e.kind == TimelineKind.DEEPWORK) scheme.tertiaryContainer else Color.Transparent
                    Surface(
                        color = container,
                        contentColor = content,
                        border = border,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .padding(start = GUTTER + 4.dp, end = 2.dp)
                            .offset(y = MINUTE * e.topMinutes)
                            .fillMaxWidth()
                            .height(MINUTE * maxOf(e.heightMinutes, 1)),
                    ) {
                        Box(
                            Modifier.drawBehind {
                                if (hatch != Color.Transparent) {
                                    // Hachures du bloc deep work (seul motif autorisé avec le fondu des Réglages).
                                    val step = 10.dp.toPx()
                                    var x = -size.height
                                    while (x < size.width) {
                                        drawLine(hatch, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 4.dp.toPx())
                                        x += step
                                    }
                                }
                            },
                        ) {
                            if (e.heightMinutes >= 14 && !labelCovered) {
                                Text(
                                    "${Dates.time(e.start, language)} · ${e.title}",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
                // Le nom du contexte de chaque plage réservée, en pastille à droite du haut de la plage (dessinée par-dessus les tâches).
                view.windows.filter { it.context != null }.forEach { w ->
                    Surface(
                        color = scheme.surface,
                        contentColor = scheme.primary,
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp).offset(y = MINUTE * ((w.startHour - start) * 60) + 2.dp),
                    ) {
                        Text(w.context.orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
            }
            if (view.sessionTimeline.isNotEmpty()) {
                Text(
                    stringResource(Res.string.timeline_rail_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
