package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.ui.app.disclosure
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.model.FIBONACCI_SCALE
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.field_project
import com.skohscripts.kairos.ui.generated.resources.field_type
import com.skohscripts.kairos.ui.generated.resources.filter_active
import com.skohscripts.kairos.ui.generated.resources.filter_all
import com.skohscripts.kairos.ui.generated.resources.filter_reset
import com.skohscripts.kairos.ui.generated.resources.filter_search
import com.skohscripts.kairos.ui.generated.resources.filter_search_placeholder
import com.skohscripts.kairos.ui.generated.resources.filter_title
import com.skohscripts.kairos.ui.generated.resources.points_label
import com.skohscripts.kairos.ui.generated.resources.priority_label
import com.skohscripts.kairos.ui.icons.KairosIcons
import org.jetbrains.compose.resources.stringResource

/**
 * « Rechercher / filtrer » (`_kairos_filters.html`) : repliée par défaut,
 * ouverte quand un filtre est actif, marquée « filtre actif ». Filtre
 * d'affichage pur : l'ordonnancement porte toujours sur toutes les tâches.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterCard(
    filter: DayFilter,
    open: Boolean,
    onToggle: () -> Unit,
    onChange: (DayFilter) -> Unit,
    projects: List<String>,
    taskTypes: List<String>,
    searchFocus: FocusRequester,
    showShortcuts: Boolean,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().disclosure(open, onToggle = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(KairosIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(Res.string.filter_title), style = MaterialTheme.typography.titleSmall)
            if (showShortcuts) KeyHint("/")
            if (filter.active) Badge(stringResource(Res.string.filter_active), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Icon(if (open) KairosIcons.ExpandLess else KairosIcons.ExpandMore, contentDescription = null)
        }
        if (open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    filter.query,
                    { onChange(filter.copy(query = it)) },
                    label = { Text(stringResource(Res.string.filter_search)) },
                    placeholder = { Text(stringResource(Res.string.filter_search_placeholder)) },
                    leadingIcon = { Icon(KairosIcons.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                )
                val all = stringResource(Res.string.filter_all)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Facet(stringResource(Res.string.priority_label), filter.priority, PRIORITY_VALUES, { "P$it" }, all) { onChange(filter.copy(priority = it)) }
                    Facet(stringResource(Res.string.field_project), filter.project, projects, { it }, all) { onChange(filter.copy(project = it)) }
                    Facet(stringResource(Res.string.field_type), filter.taskType, taskTypes, { it }, all) { onChange(filter.copy(taskType = it)) }
                    Facet(stringResource(Res.string.points_label), filter.points, FIBONACCI_SCALE, { "$it" }, all) { onChange(filter.copy(points = it)) }
                }
                if (filter.active) {
                    TextButton(onClick = { onChange(DayFilter()) }) { Text(stringResource(Res.string.filter_reset)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> Facet(
    label: String,
    value: T?,
    options: List<T>,
    show: (T) -> String,
    all: String,
    width: Dp = 170.dp,
    onChange: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value?.let(show) ?: all,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = Modifier.width(width).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(all) }, onClick = { onChange(null); expanded = false })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(show(option)) }, onClick = { onChange(option); expanded = false })
            }
        }
    }
}
