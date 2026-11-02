package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.ui.app.DemoSource
import com.skohscripts.kairos.ui.app.openDemo
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import com.skohscripts.kairos.ui.app.LinkedFileSync
import com.skohscripts.kairos.data.db.KairosDatabase
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.BackupStore
import com.skohscripts.kairos.ui.app.ChronoNotifier
import com.skohscripts.kairos.ui.app.NoNotifier
import com.skohscripts.kairos.ui.app.FileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.awt.FileDialog
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.Frame
import java.io.File
import java.util.Locale
import kotlin.time.Clock

/**
 * Services du bureau (docs/spec/distribution.md § Bureau) : base SQLite
 * `kairos.db` dans le dossier de données, boîtes de dialogue de fichiers du
 * système, sauvegardes dans `backups/`.
 */
object DesktopServices {
    const val DATABASE_FILE = "kairos.db"

    /** Ouvre (ou crée, exemples compris) la base de [dataDir] ; `inMemory` pour l'auto-test. */
    suspend fun open(
        dataDir: File,
        inMemory: Boolean = false,
        notifier: ChronoNotifier = NoNotifier,
        /** Copie portable (docs/spec/raccourci-portable.md), `null` pour une version installée. */
        portable: PortableCopy? = null,
        /** Dossier des données choisi et journal de diagnostic (docs/spec/bureau-avance.md) ; `null` pour l'auto-test. */
        folder: DesktopDataFolder? = null,
        diagnostics: FileDiagnostics? = null,
    ): AppServices = withContext(Dispatchers.IO) {
        dataDir.mkdirs()
        val url = if (inMemory) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:${File(dataDir, DATABASE_FILE).absolutePath}"
        val clock = Clock.System
        val language = Locale.getDefault().language
        val opened = KairosStore.open(JdbcSqliteDriver(url))
        // Fichier lié (docs/spec/export-import.md § Fichier lié du bureau et d'Android) : branché sur chaque nouvel état.
        val linked = LinkedFileSync(DesktopLinkedFile(dataDir), CoroutineScope(SupervisorJob() + Dispatchers.IO), clock)
        val repository = KairosRepository.open(
            opened,
            examples = { ExampleData.snapshot(clock.todayIn(TimeZone.currentSystemDefault()), clock.now(), language) },
            clock = clock,
            onChanged = { linked.changed(it) },
        )
        linked.start(repository.snapshot.value)
        if (!inMemory) current = linked
        diagnostics?.log("db.open ${if (opened.created) "created" else "schema ${KairosDatabase.Schema.version}"} tasks=${repository.snapshot.value.tasks.size}")
        AppServices(
            repository = repository,
            files = DialogFiles,
            backups = FolderBackups(File(dataDir, "backups")),
            dataLocation = dataDir.absolutePath,
            clock = clock,
            notifier = notifier,
            // L'auto-test (base en mémoire) rend l'interface habituelle, sans l'accueil ni réseau.
            firstLaunch = opened.created && !inMemory,
            updates = if (inMemory) null else DesktopUpdates(File(dataDir, "updates.json"), log = { diagnostics?.log(it) }),
            shortcuts = portable?.let { PortableShortcuts(it, File(dataDir, "shortcuts.json")) },
            systemLanguage = language,
            applyLanguage = { Locale.setDefault(Locale.forLanguageTag(it)) },
            dataFolder = folder,
            diagnostics = diagnostics,
            // L'auto-test (base en mémoire) n'écrit jamais dans un fichier de l'utilisateur.
            linkedStorage = if (inMemory) null else linked,
            // Démonstration jetable (docs/spec/visite-guidee.md § Visite interactive) : une base SQLite en mémoire neuve.
            demo = DemoSource { lang ->
                withContext(Dispatchers.IO) {
                    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
                    openDemo(KairosStore.open(driver), lang, close = { driver.close() }, clock = clock)
                }
            },
        )
    }

    /** Le fichier lié de la base ouverte : `Main` écrit ce qui attend à la fermeture de la fenêtre. */
    @Volatile
    var current: LinkedFileSync? = null
        private set
}

/** Boîtes de dialogue AWT natives (Windows, macOS ; GTK sous Linux). */
internal object DialogFiles : FileService {
    override suspend fun saveText(suggestedName: String, text: String): Boolean {
        val file = choose(FileDialog.SAVE, suggestedName) ?: return false
        withContext(Dispatchers.IO) { file.writeText(text) }
        return true
    }

    override suspend fun saveImage(suggestedName: String, image: ImageBitmap): Boolean {
        val file = choose(FileDialog.SAVE, suggestedName) ?: return false
        val png = encodePng(image)
        withContext(Dispatchers.IO) { file.writeBytes(png) }
        return true
    }

    override suspend fun openText(): String? {
        val file = choose(FileDialog.LOAD, null) ?: return null
        return withContext(Dispatchers.IO) { file.readText() }
    }

    suspend fun choose(mode: Int, name: String?): File? = withContext(Dispatchers.Main) {
        val dialog = FileDialog(null as Frame?, "Kairos", mode)
        if (name != null) dialog.file = name
        dialog.isVisible = true
        dialog.file?.let { File(dialog.directory, it) }
    }
}

/** PNG par Skia, le moteur de rendu de Compose sur le bureau (docs/spec/statistiques.md § Export à l'écran). */
internal fun encodePng(image: ImageBitmap): ByteArray =
    checkNotNull(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)) { "encodage PNG" }.bytes

/** Sauvegardes dans un dossier ; seules les [keep] plus récentes sont gardées. */
private class FolderBackups(private val folder: File, private val keep: Int = 10) : BackupStore {
    override suspend fun save(name: String, text: String) = withContext(Dispatchers.IO) {
        folder.mkdirs()
        File(folder, name).writeText(text)
        folder.listFiles { f -> f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(keep)
            ?.forEach { it.delete() }
        Unit
    }
}
