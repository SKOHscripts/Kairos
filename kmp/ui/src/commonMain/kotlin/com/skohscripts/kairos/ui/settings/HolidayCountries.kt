package com.skohscripts.kairos.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.skohscripts.kairos.ui.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Nom d'un pays des fériés (docs/spec/jours-feries.md) ; `""` (ou un code inconnu) : « Aucun ». */
internal fun holidayCountryName(code: String): StringResource = when (code) {
    "AT" -> Res.string.holiday_country_AT
    "BE" -> Res.string.holiday_country_BE
    "CA" -> Res.string.holiday_country_CA
    "CH" -> Res.string.holiday_country_CH
    "DE" -> Res.string.holiday_country_DE
    "ES" -> Res.string.holiday_country_ES
    "FR" -> Res.string.holiday_country_FR
    "GB" -> Res.string.holiday_country_GB
    "IE" -> Res.string.holiday_country_IE
    "IT" -> Res.string.holiday_country_IT
    "LU" -> Res.string.holiday_country_LU
    "RU" -> Res.string.holiday_country_RU
    "US" -> Res.string.holiday_country_US
    else -> Res.string.holiday_country_none
}

/**
 * Champ `CHOICE` des Réglages (le pays des fériés) : liste déroulante Material 3 ancrée sous le champ, « Aucun » d'abord, puis les
 * pays triés par leur nom dans la langue de l'interface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChoiceInput(value: String, choices: List<String>, label: String, help: String?, modifier: Modifier, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val names = choices.associateWith { stringResource(holidayCountryName(it)) }
    val ordered = choices.filter { it.isEmpty() } + choices.filter { it.isNotEmpty() }.sortedBy { names.getValue(it).lowercase() }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = names[value] ?: names.getValue(""),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            supportingText = help?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ordered.forEach { option ->
                DropdownMenuItem(text = { Text(names.getValue(option)) }, onClick = {
                    expanded = false
                    onChange(option)
                })
            }
        }
    }
}
