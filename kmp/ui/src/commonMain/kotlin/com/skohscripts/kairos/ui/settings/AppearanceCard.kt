package com.skohscripts.kairos.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.settings.FieldError
import com.skohscripts.kairos.core.settings.SettingsForm
import com.skohscripts.kairos.core.settings.TextZoom
import com.skohscripts.kairos.core.settings.ThemeMode
import androidx.compose.material3.SegmentedButtonDefaults
import com.skohscripts.kairos.ui.generated.resources.text_zoom
import com.skohscripts.kairos.ui.generated.resources.text_zoom_help
import com.skohscripts.kairos.ui.theme.KairosSegmentedButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedRow
import com.skohscripts.kairos.ui.generated.resources.theme_dark
import com.skohscripts.kairos.ui.generated.resources.theme_dark_chosen
import com.skohscripts.kairos.ui.generated.resources.theme_dark_system
import com.skohscripts.kairos.ui.theme.LocalSystemDark
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.settings_error_color
import com.skohscripts.kairos.ui.generated.resources.settings_section_appearance
import com.skohscripts.kairos.ui.generated.resources.theme_custom
import com.skohscripts.kairos.ui.generated.resources.theme_custom_help
import com.skohscripts.kairos.ui.generated.resources.theme_help
import com.skohscripts.kairos.ui.generated.resources.theme_system
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.LocalSystemColorScheme
import com.skohscripts.kairos.ui.theme.ThemeColors
import org.jetbrains.compose.resources.stringResource

/**
 * Apparence (docs/spec/apparence.md) : interrupteur « Thème sombre » à deux états (réglage `themeMode`), pastilles des
 * graines proposées (« Couleurs du système » en tête là où la plateforme en fournit) et champ de couleur libre (réglage
 * `themeColor`), enregistrés avec le reste par « Enregistrer ».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceCard(
    mode: String, value: String, error: FieldError?, onModeChange: (String) -> Unit,
    zoom: String, onZoomChange: (String) -> Unit, onChange: (String) -> Unit,
) {
    val system = LocalSystemColorScheme.current
    val systemDark = LocalSystemDark.current
    val chosen = SettingsForm.normalizeColor(value)
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(KairosIcons.Palette, stringResource(Res.string.settings_section_appearance), stringResource(Res.string.theme_help))
        // Deux états à l'écran (issue #72) : le thème affiché ; le rebasculer vers celui du système revient à le suivre.
        Column {
            SwitchRow(ThemeMode.isDark(mode, systemDark), stringResource(Res.string.theme_dark)) { onModeChange(ThemeMode.toggled(it, systemDark)) }
            Text(
                stringResource(if (ThemeMode.normalize(mode) == Settings.THEME_MODE_SYSTEM) Res.string.theme_dark_system else Res.string.theme_dark_chosen),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Zoom du texte (issue #229) : quatre paliers, appliqué à « Enregistrer » comme le reste.
        Column(verticalArrangement = Arrangement.spacedBy(KairosSpacing.xs)) {
            Text(stringResource(Res.string.text_zoom), style = MaterialTheme.typography.labelLarge)
            val current = TextZoom.normalize(zoom.toIntOrNull())
            KairosSegmentedRow(Modifier.fillMaxWidth()) {
                TextZoom.STEPS.forEachIndexed { index, step ->
                    KairosSegmentedButton(
                        selected = step == current,
                        onClick = { onZoomChange(step.toString()) },
                        shape = SegmentedButtonDefaults.itemShape(index, TextZoom.STEPS.size),
                    ) { Text("$step %") }
                }
            }
            Text(stringResource(Res.string.text_zoom_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (system != null) {
                Swatch(system.primary, stringResource(Res.string.theme_system), chosen == Settings.SYSTEM_THEME) { onChange(Settings.SYSTEM_THEME) }
            }
            ThemeColors.PRESETS.forEach { preset ->
                Swatch(seedColor(preset.color), stringResource(preset.name), chosen == preset.color) { onChange(preset.color) }
            }
            // Une couleur libre valide a sa propre pastille, cochée.
            if (chosen != null && chosen != Settings.SYSTEM_THEME && ThemeColors.PRESETS.none { it.color == chosen }) {
                Swatch(seedColor(chosen), chosen, selected = true) { onChange(chosen) }
            }
        }
        OutlinedTextField(
            value = if (chosen == Settings.SYSTEM_THEME) "" else value,
            onValueChange = onChange,
            label = { Text(stringResource(Res.string.theme_custom)) },
            isError = error != null,
            supportingText = {
                Text(if (error != null) stringResource(Res.string.settings_error_color, error.bound.orEmpty()) else stringResource(Res.string.theme_custom_help))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(LocalErrorReveal.current.requester("themeColor")),
        )
    }
}

/** La pastille montre la graine elle-même : seule couleur de composant hors schéma (spec § Décisions). */
private fun seedColor(value: String): Color = Color(0xFF000000.toInt() or SettingsForm.parseColor(value)!!)

/**
 * Pastille de 36 dp dans une cible de 48 dp ; la choisie porte un contour
 * `onSurface` et une coche blanche ou noire, lisible sur la couleur montrée.
 */
@Composable
private fun Swatch(color: Color, name: String, selected: Boolean, onSelect: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = name },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .then(if (selected) Modifier.border(2.dp, scheme.onSurface, CircleShape) else Modifier)
                .background(color, CircleShape),
        ) {
            if (selected) {
                val check = if (color.luminance() > 0.5f) Color.Black else Color.White
                Icon(KairosIcons.Check, contentDescription = null, tint = check, modifier = Modifier.size(20.dp))
            }
        }
    }
}
