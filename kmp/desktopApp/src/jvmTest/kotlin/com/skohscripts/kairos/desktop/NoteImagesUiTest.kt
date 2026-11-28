package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.notes.EncodedImage
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.notes.ImagePick
import com.skohscripts.kairos.ui.notes.ImageService
import com.skohscripts.kairos.ui.notes.ImageSource
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Images des notes à l'écran (docs/spec/notes-images.md § Critères de succès) : ajout à la capture, vignettes, visionneuse,
 * « → Tâche » inactif sans texte, lien de la fiche vers les images de la note d'origine. Sélecteur simulé, réduction réelle.
 */
@OptIn(ExperimentalTestApi::class)
class NoteImagesUiTest {
    private val clock = object : Clock {
        var now = Instant.parse("2026-09-29T07:00:00Z")
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun image(color: Color): EncodedImage {
        val png = BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB).apply { createGraphics().apply { this.color = color; fillRect(0, 0, 80, 60); dispose() } }
        return checkNotNull(DesktopImages.encode(ByteArrayOutputStream().also { ImageIO.write(png, "png", it) }.toByteArray()))
    }

    /** Le « sélecteur de fichiers » rend deux images. */
    private val picker = object : ImageService {
        override val sources = listOf(ImageSource.FILE, ImageSource.CLIPBOARD)
        override suspend fun pick(source: ImageSource, max: Int) = ImagePick(listOf(image(Color.RED), image(Color.BLUE)).take(max))
        override suspend fun paste(max: Int) = ImagePick.NONE
    }

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock, images = picker)
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitDescription(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun imagesAreCapturedShownAndOpenedAndAnImageOnlyNoteWaitsForText() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.NOTES, TeamDestination.BOARD) { services } }
        waitText("Nothing waiting.")
        onNodeWithContentDescription("Add an image").performClick()
        waitText("Choose a file")
        onNodeWithText("Paste from the clipboard").assertExists()
        onNodeWithText("Choose a file").performClick()
        waitDescription("Open image 2")
        // Une image retirée avant de capturer n'entre pas dans la note.
        onAllNodesWithContentDescription("Remove the image").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Open image 2").fetchSemanticsNodes().isEmpty() }
        onNodeWithText("Capture").performClick()
        waitUntil(timeoutMillis = 5_000) { repository.snapshot.value.notes.isNotEmpty() }
        val note = repository.snapshot.value.notes.single()
        assertEquals("", note.body)
        assertEquals(1, repository.snapshot.value.noteImages.count { it.noteId == note.id })

        waitText("Add some text to turn it into a task")
        onNodeWithText("→ Task").assertIsNotEnabled()
        waitDescription("Open image 1")
        onNodeWithContentDescription("Open image 1").performClick()
        waitText("Image 1 of 1")
        onNodeWithContentDescription("Close").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Image 1 of 1").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun theTaskSheetLinksToTheImagesOfItsNote() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        runBlocking {
            repository.createNote("Fix the leak", listOf(image(Color.RED), image(Color.GREEN)))
            val id = checkNotNull(repository.convertNote(repository.snapshot.value.notes.single().id))
            repository.setPriority(id, 2)
            repository.setPoints(id, 3)
            repository.setTaskType(id, "Development")
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Fix the leak", substring = true)
        onAllNodesWithContentDescription("Edit").onFirst().performClick()
        waitText("Edit task")
        waitText("Images of the original note (2)")
        onNodeWithText("Images of the original note (2)").performScrollTo().performClick()
        waitText("Image 1 of 2")
    }
}
