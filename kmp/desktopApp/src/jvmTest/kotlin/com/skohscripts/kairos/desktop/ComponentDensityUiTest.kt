package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosFilterChip
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosRowIconButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedRow
import com.skohscripts.kairos.ui.theme.KairosTextButton
import com.skohscripts.kairos.ui.theme.KairosTheme
import com.skohscripts.kairos.ui.theme.KairosTonalButton
import kotlin.test.Test

/**
 * Hauteur dessinée et aire tactile des composants de densité
 * (docs/spec/densite.md) : un composant plus fin que 48 dp doit garder une
 * aire tactile de 48 dp (`minimumInteractiveComponentSize`), à la largeur d'un
 * téléphone comme en fenêtre large.
 */
@OptIn(ExperimentalTestApi::class)
class ComponentDensityUiTest {
    @Test
    fun buttonsAreDrawn36dpHighWithA48dpTouchArea() = runComposeUiTest {
        setContent {
            KairosTheme {
                Column {
                    KairosButton(onClick = {}) { Text("Plain") }
                    KairosOutlinedButton(onClick = {}) { Text("Outlined") }
                    KairosTonalButton(onClick = {}) { Text("Tonal") }
                    KairosTextButton(onClick = {}) { Text("Text") }
                }
            }
        }
        for (label in listOf("Plain", "Outlined", "Tonal", "Text")) {
            onNodeWithText(label).assertHeightIsEqualTo(36.dp).assertTouchHeightIsEqualTo(48.dp)
        }
    }

    @Test
    fun disabledButtonKeepsItsTouchArea() = runComposeUiTest {
        setContent { KairosTheme { KairosButton(onClick = {}, enabled = false) { Text("Off") } } }
        onNodeWithText("Off").assertHeightIsEqualTo(36.dp).assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun filterChipIsDrawn32dpHighWithA48dpTouchArea() = runComposeUiTest {
        setContent { KairosTheme { KairosFilterChip(selected = false, onClick = {}, label = { Text("P1") }) } }
        onNodeWithText("P1").assertHeightIsEqualTo(32.dp).assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun segmentedButtonIsDrawn40dpHighWithA48dpTouchArea() = runComposeUiTest {
        setContent {
            KairosTheme {
                KairosSegmentedRow {
                    KairosSegmentedButton(selected = true, onClick = {}, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Task") }
                    KairosSegmentedButton(selected = false, onClick = {}, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Slot") }
                }
            }
        }
        for (label in listOf("Task", "Slot")) {
            onNodeWithText(label).assertHeightIsEqualTo(40.dp).assertTouchHeightIsEqualTo(48.dp)
        }
    }

    @Test
    fun rowIconButtonIs40dpWithA48dpTouchArea() = runComposeUiTest {
        setContent { KairosTheme { KairosRowIconButton(KairosIcons.Edit, "Edit", onClick = {}) } }
        onNodeWithContentDescription("Edit").assertHeightIsEqualTo(40.dp).assertWidthIsAtLeast(40.dp).assertTouchHeightIsEqualTo(48.dp)
    }
}
