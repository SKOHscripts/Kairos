package com.skohscripts.kairos.desktop

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/**
 * Ouvre la section [title] de la fiche si elle est repliée (docs/spec/fiche-tache.md : toutes le sont à l'ouverture).
 * Interface en anglais : « Team », « Qualification », « Dates and time », « Splitting and sequencing ».
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openSheetSection(title: String) {
    val header = hasText(title) and hasClickAction() and hasAnyAncestor(isDialog())
    waitUntil(conditionDescription = "section « $title »", timeoutMillis = 10_000) { onAllNodes(header).fetchSemanticsNodes().isNotEmpty() }
    val node = onAllNodes(header).onFirst()
    if (node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) != "expanded") node.performScrollTo().performClick()
    waitForIdle()
}
