package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.FIBONACCI_SCALE
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.points_label
import com.skohscripts.kairos.ui.generated.resources.priority_label
import org.jetbrains.compose.resources.stringResource

/**
 * Pastilles de priorité (P0 · P1 · P2) : un clic pose la valeur, le sens est
 * écrit sur la pastille (jamais seulement dans une infobulle). Recliquer la
 * valeur choisie la retire. Cible tactile de 48 dp (charte).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PriorityPills(selected: Int?, onSelect: (Int?) -> Unit, showMeaning: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.priority_label), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRIORITY_VALUES.forEach { p ->
                FilterChip(
                    selected = selected == p,
                    onClick = { onSelect(if (selected == p) null else p) },
                    label = {
                        val name = "P$p ${stringResource(Levels.priorityName(p))}"
                        Text(if (showMeaning) "$name · ${stringResource(Levels.priorityMeaning(p))}" else name)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

/** Pastilles de points de Fibonacci (1 · 2 · 3 · 5 · 8 · 13 · 21), sens écrit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PointsPills(selected: Int?, onSelect: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.points_label), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FIBONACCI_SCALE.forEach { points ->
                FilterChip(
                    selected = selected == points,
                    onClick = { onSelect(if (selected == points) null else points) },
                    label = { Text("$points ${stringResource(Levels.pointsName(points))}") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}
