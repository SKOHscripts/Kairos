package com.skohscripts.kairos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.day.LabeledCheckbox
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_save
import com.skohscripts.kairos.ui.generated.resources.settings_chrono_body
import com.skohscripts.kairos.ui.generated.resources.settings_chrono_title
import com.skohscripts.kairos.ui.generated.resources.settings_idle
import com.skohscripts.kairos.ui.generated.resources.settings_pomodoro
import com.skohscripts.kairos.ui.generated.resources.settings_saved
import com.skohscripts.kairos.ui.generated.resources.settings_sound
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Réglages du chrono (docs/spec-v3/temps-reel-chrono.md § Réglages) :
 * « chrono oublié » après N min, pause suggérée après N min (0 = désactivé),
 * son de dernier recours (désactivé par défaut). Le reste des réglages
 * arrive au jalon M5.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChronoSettingsCard(services: AppServices) {
    val settings by services.repository.snapshot.collectAsState()
    val current = settings.settings
    var idle by remember(current.timerIdleAlertMinutes) { mutableStateOf(current.timerIdleAlertMinutes.toString()) }
    var pomodoro by remember(current.pomodoroFocusMinutes) { mutableStateOf(current.pomodoroFocusMinutes.toString()) }
    var sound by remember(current.timerAlertSound) { mutableStateOf(current.timerAlertSound) }
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.settings_chrono_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.settings_chrono_body), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MinutesField(idle, { idle = it }, stringResource(Res.string.settings_idle))
                MinutesField(pomodoro, { pomodoro = it }, stringResource(Res.string.settings_pomodoro))
            }
            LabeledCheckbox(sound, { sound = it }, stringResource(Res.string.settings_sound))
            Button(onClick = {
                scope.launch {
                    val updated = current.copy(
                        timerIdleAlertMinutes = idle.toIntOrNull() ?: 0,
                        pomodoroFocusMinutes = pomodoro.toIntOrNull() ?: 0,
                        timerAlertSound = sound,
                    )
                    services.repository.updateSettings(updated)
                    messages(getString(Res.string.settings_saved))
                }
            }) { Text(stringResource(Res.string.action_save)) }
        }
    }
}

@Composable
private fun MinutesField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value,
        { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.width(260.dp),
    )
}
