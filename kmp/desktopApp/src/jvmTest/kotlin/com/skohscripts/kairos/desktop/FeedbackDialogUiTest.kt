package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.skohscripts.kairos.core.feedback.FeedbackIssue
import com.skohscripts.kairos.ui.guide.FeedbackDialog
import com.skohscripts.kairos.ui.guide.HelpDialog
import com.skohscripts.kairos.ui.guide.HelpTopic
import com.skohscripts.kairos.ui.theme.KairosTheme
import androidx.compose.ui.test.runComposeUiTest
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** « Donner un avis » (docs/spec/retours.md § Critères de succès) : dialogue, adresse ouverte, échec sans navigateur. */
@OptIn(ExperimentalTestApi::class)
class FeedbackDialogUiTest {
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    @Test
    fun continueOpensThePrefilledIssueAndCloses() = runComposeUiTest {
        val opened = mutableListOf<String>()
        var open by mutableStateOf(true)
        setContent {
            KairosTheme {
                if (open) FeedbackDialog("4.1.0", "Windows", onDismiss = { open = false }, openLink = { opened += it; true })
            }
        }
        onNodeWithText("Continue on GitHub").assertIsNotEnabled()
        // La version et la plateforme sont jointes d'office, et le dialogue le dit.
        onNodeWithText("Added automatically: Kairos 4.1.0 · Windows").assertExists()
        onNodeWithText("Title").performTextInput("Week view")
        // Titre seul : le besoin manque encore.
        onNodeWithText("Continue on GitHub").assertIsNotEnabled()
        onNodeWithText("Need, observation or comment").performTextInput("I would like to see the month.")
        onNodeWithText("Continue on GitHub").assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(listOf(FeedbackIssue.url("Week view", "", "I would like to see the month.", "4.1.0", "Windows")), opened)
        assertTrue(!open, "le dialogue se ferme après l'ouverture")
    }

    @Test
    fun withoutBrowserTheDialogAndTheTextStay() = runComposeUiTest {
        var dismissed = 0
        var attempts = 0
        setContent {
            KairosTheme { FeedbackDialog("4.1.0", "Windows", onDismiss = { dismissed++ }, openLink = { attempts++; false }) }
        }
        onNodeWithText("Title").performTextInput("Idea")
        onNodeWithText("Need, observation or comment").performTextInput("Something")
        onNodeWithText("Continue on GitHub").performClick()
        waitForIdle()
        assertEquals(1, attempts)
        assertEquals(0, dismissed)
        onNodeWithText("Idea").assertExists()
    }

    @Test
    fun aTooLongMessageIsRefused() = runComposeUiTest {
        setContent { KairosTheme { FeedbackDialog("4.1.0", "Windows", onDismiss = {}, openLink = { true }) } }
        onNodeWithText("Title").performTextInput("Long")
        onNodeWithText("Need, observation or comment").performTextInput("x".repeat(FeedbackIssue.MAX_LOGIN_URL))
        onNodeWithText("Too long for GitHub: shorten your message.").assertExists()
        onNodeWithText("Continue on GitHub").assertIsNotEnabled()
    }

    @Test
    fun theHelpOffersGiveFeedback() = runComposeUiTest {
        var feedback = 0
        setContent {
            KairosTheme {
                HelpDialog(HelpTopic.entries.first(), team = false, onOpenHome = {}, onStartTour = {}, onDismiss = {}, onFeedback = { feedback++ })
            }
        }
        onNodeWithText("Give feedback").performClick()
        assertEquals(1, feedback)
    }
}
