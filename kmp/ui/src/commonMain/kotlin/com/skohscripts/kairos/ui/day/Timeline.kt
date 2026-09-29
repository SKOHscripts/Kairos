package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.engine.Scheduling.TimelineKind
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.day.DayView
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.timeline_title
import org.jetbrains.compose.resources.stringResource

/** Hauteur d'une minute sur la frise (1 min = 1 dp, comme 1 min = 1 px en Kairos 2). */
private val MINUTE = 1.dp
private val GUTTER = 44.dp

/**
 * Carte « Agenda » : la journée de travail heure par heure, créneaux et
 * tâches placées (`build_timeline`). Couleurs de la charte : occupé neutre,
 * travail en conteneur secondaire, épinglée avec contour, conflit en
 * conteneur d'erreur, deep work en tertiaire (hachures pour le bloc réservé).
 */
@Composable
internal fun TimelineCard(view: DayView, settings: Settings, language: String, modifier: Modifier = Modifier) {
    val start = settings.workdayStartHour
    val end = maxOf(settings.workdayEndHour, start)
    val scheme = MaterialTheme.colorScheme
    val lineColor = scheme.outlineVariant
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.timeline_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
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
                for (e in view.timeline) {
                    val (container, content) = when (e.kind) {
                        TimelineKind.BUSY -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
                        TimelineKind.WORK, TimelineKind.PINNED -> scheme.secondaryContainer to scheme.onSecondaryContainer
                        TimelineKind.CONFLICT -> scheme.errorContainer to scheme.onErrorContainer
                        TimelineKind.DEEPWORK -> scheme.surface to scheme.onTertiaryContainer
                        TimelineKind.DEEPWORK_TASK -> scheme.tertiaryContainer to scheme.onTertiaryContainer
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
                            if (e.heightMinutes >= 14) {
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
            }
        }
    }
}
