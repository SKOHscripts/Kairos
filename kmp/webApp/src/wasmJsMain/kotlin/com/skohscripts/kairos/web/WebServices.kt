package com.skohscripts.kairos.web

import com.skohscripts.kairos.ui.app.DemoSource
import com.skohscripts.kairos.ui.app.openDemo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.ExportCodec
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.BackupStore
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.app.LinkHash
import com.skohscripts.kairos.ui.app.LinkedFileState
import com.skohscripts.kairos.ui.app.LinkedStorage
import com.skohscripts.kairos.ui.app.OpenedFile
import com.skohscripts.kairos.ui.app.mimeOf
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.w3c.dom.Worker
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock

/**
 * Services de la version web (docs/spec/export-import.md § Version web).
 *
 * La base SQLite vit **en mémoire** dans un worker (sql.js). La persistance
 * est un instantané JSON (le format d'export) écrit à chaque changement dans
 * l'OPFS (`kairos.json`) et, s'il y en a un, dans le fichier lié. Au
 * lancement, l'instantané est rechargé depuis l'OPFS, ou depuis le fichier lié
 * si le navigateur a perdu l'OPFS.
 */
object WebServices {
    private const val DATA_FILE = "kairos.json"

    suspend fun open(): AppServices {
        runCatching { KairosWeb.persist().await<JsBoolean>() }
        val clock = Clock.System
        val storage = WebLinkedStorage(clock, DATA_FILE)

        var stored = KairosWeb.opfsRead(DATA_FILE).await<JsString?>()?.toString()
        val link = if (KairosWeb.linkSupported()) KairosWeb.linkState().await<JsString>().toString() else "unsupported"
        if (stored == null && link.startsWith("granted:")) stored = KairosWeb.linkRead().await<JsString?>()?.toString()
        // Un instantané illisible fait échouer l'ouverture (écran d'erreur) plutôt que
        // de repartir de zéro et d'écraser les données au premier changement.
        val snapshot: KairosSnapshot? = stored?.let { ExportCodec.decode(it) }
        val restorePending = snapshot == null && link.startsWith("prompt:")

        val opened = KairosStore.open(WebWorkerDriver(Worker("kairos-sqljs.worker.js")))
        val repository = KairosRepository(opened.database, clock) { storage.persist(it) }
        storage.repository = repository
        val language = window.navigator.language
        repository.replaceAll(snapshot ?: ExampleData.snapshot(clock.todayIn(TimeZone.currentSystemDefault()), clock.now(), language))
        storage.start(link, restorePending, repository.snapshot.value)

        return AppServices(
            repository = repository,
            files = BrowserFiles,
            backups = BackupStore { name, text -> KairosWeb.opfsBackup(name, text, 10).await<JsAny?>() },
            dataLocation = null,
            linkedStorage = storage,
            clock = clock,
            notifier = WebNotifier,
            // Ni instantané ni fichier lié en attente : premier passage dans ce navigateur.
            firstLaunch = snapshot == null && !restorePending,
            systemLanguage = language,
            applyLanguage = { KairosWeb.setLanguage(it) },
            // Démonstration jetable (docs/spec/visite-guidee.md § Visite interactive) : un worker neuf, dont la base sql.js est
            // en mémoire et distincte de celle que l'OPFS conserve ; le fermer termine le worker.
            demo = DemoSource { lang ->
                val driver = WebWorkerDriver(Worker("kairos-sqljs.worker.js"))
                openDemo(KairosStore.open(driver), lang, close = { driver.close() }, clock = clock)
            },
        )
    }
}

/** Export : téléchargement ; import : sélecteur de fichier du navigateur. */
private object BrowserFiles : FileService {
    override suspend fun saveText(suggestedName: String, text: String): Boolean {
        KairosWeb.download(suggestedName, text, mimeOf(suggestedName))
        return true
    }

    /** PNG par Skia (le moteur de rendu de Compose dans le navigateur), puis téléchargement. */
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun saveImage(suggestedName: String, image: ImageBitmap): Boolean {
        val png = checkNotNull(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)) { "encodage PNG" }.bytes
        // Base64 : une chaîne passe la frontière wasm/JS sans recopie octet par octet.
        KairosWeb.downloadBase64(suggestedName, Base64.encode(png), "image/png")
        return true
    }

    override suspend fun openText(): String? = KairosWeb.pickText().await<JsString?>()?.toString()
}

private class WebLinkedStorage(private val clock: Clock, private val dataFile: String) : LinkedStorage {
    lateinit var repository: KairosRepository
    private val mutable = MutableStateFlow<LinkedFileState>(LinkedFileState.NotLinked)
    override val state: StateFlow<LinkedFileState> = mutable.asStateFlow()
    override val browser: Boolean = true

    /** Faux tant que des données attendent d'être rechargées du fichier lié : rien n'est écrit. */
    private var persisting = false
    private var pendingName: String? = null

    suspend fun start(link: String, restorePending: Boolean, current: KairosSnapshot) {
        mutable.value = when {
            link == "unsupported" -> LinkedFileState.Unsupported
            link == "none" -> LinkedFileState.NotLinked
            link.startsWith("granted:") -> LinkedFileState.Linked(link.substringAfter(':'))
            else -> LinkedFileState.NeedsPermission(link.substringAfter(':'), restorePending)
        }
        persisting = !restorePending
        if (persisting) persist(current)
    }

    fun encode(snapshot: KairosSnapshot) = ExportCodec.encode(snapshot, KairosBuild.VERSION_NAME, clock.now())

    suspend fun persist(snapshot: KairosSnapshot) {
        if (!persisting) return
        val text = encode(snapshot)
        try {
            KairosWeb.opfsWrite(dataFile, text).await<JsAny?>()
        } catch (e: Throwable) {
            mutable.value = LinkedFileState.SaveFailed(e.message ?: e.toString())
            return
        }
        val current = mutable.value
        if (current is LinkedFileState.SaveFailed) mutable.value = LinkedFileState.NotLinked
        if (current is LinkedFileState.Linked) writeLinked(current.name, text, force = false)
    }

    /**
     * Écrit [text] dans le fichier lié, sauf s'il a été modifié ailleurs (empreinte du dernier texte écrit, gardée avec la
     * poignée) : alors [LinkedFileState.Changed], rien n'est écrit. [force] : « Écraser ».
     */
    private suspend fun writeLinked(name: String, text: String, force: Boolean): Boolean {
        if (!force) {
            val onDisk = KairosWeb.linkRead().await<JsString?>()?.toString()
            val known = KairosWeb.linkHashGet().await<JsString?>()?.toString()
            // Première écriture après la mise à jour (aucune empreinte gardée) : le fichier est celui de cet appareil.
            if (onDisk != null && known != null && LinkHash.of(onDisk) != known) {
                mutable.value = LinkedFileState.Changed(name)
                return false
            }
        }
        if (!KairosWeb.linkWrite(text).await<JsBoolean>().toBoolean()) {
            mutable.value = LinkedFileState.NeedsPermission(name, restore = false)
            return false
        }
        KairosWeb.linkHashSet(LinkHash.of(text)).await<JsAny?>()
        mutable.value = LinkedFileState.Linked(name)
        return true
    }

    override suspend fun createFile(): Boolean {
        val text = encode(repository.snapshot.value)
        val name = KairosWeb.linkCreate("kairos.json", text).await<JsString?>()?.toString()
            ?: return false
        KairosWeb.linkHashSet(LinkHash.of(text)).await<JsAny?>()
        mutable.value = LinkedFileState.Linked(name)
        return true
    }

    override suspend fun openFile(): OpenedFile? {
        val raw = KairosWeb.linkOpen().await<JsString?>()?.toString() ?: return null
        pendingName = raw.substringBefore('\n')
        return OpenedFile(raw.substringBefore('\n'), raw.substringAfter('\n'))
    }

    override suspend fun adoptOpened(): Boolean {
        if (!KairosWeb.linkAdopt().await<JsBoolean>().toBoolean()) return false
        KairosWeb.linkRead().await<JsString?>()?.toString()?.let { KairosWeb.linkHashSet(LinkHash.of(it)).await<JsAny?>() }
        mutable.value = LinkedFileState.Linked(pendingName ?: "kairos.json")
        persisting = true
        return true
    }

    override suspend fun authorize(): Boolean {
        val current = mutable.value as? LinkedFileState.NeedsPermission ?: return false
        if (!KairosWeb.linkAuthorize().await<JsBoolean>().toBoolean()) return false
        mutable.value = LinkedFileState.Linked(current.name)
        if (current.restore) {
            val text = KairosWeb.linkRead().await<JsString?>()?.toString() ?: return false
            KairosWeb.linkHashSet(LinkHash.of(text)).await<JsAny?>()
            persisting = true
            repository.replaceAll(ExportCodec.decode(text))
        } else {
            persist(repository.snapshot.value)
        }
        return true
    }

    override suspend fun unlink() {
        KairosWeb.linkForget().await<JsAny?>()
        mutable.value = LinkedFileState.NotLinked
    }

    override suspend fun overwrite() {
        val current = mutable.value as? LinkedFileState.Changed ?: return
        writeLinked(current.name, encode(repository.snapshot.value), force = true)
    }

    override suspend fun readLinked(): OpenedFile? {
        val name = (mutable.value as? LinkedFileState.Changed)?.name ?: (mutable.value as? LinkedFileState.Linked)?.name ?: return null
        val text = KairosWeb.linkRead().await<JsString?>()?.toString() ?: return null
        return OpenedFile(name, text)
    }

    override suspend fun acknowledge(text: String) {
        val current = mutable.value as? LinkedFileState.Changed ?: return
        KairosWeb.linkHashSet(LinkHash.of(text)).await<JsAny?>()
        mutable.value = LinkedFileState.Linked(current.name)
        persist(repository.snapshot.value)
    }

    /** Sur le web, un échec d'écriture du fichier lié devient « accès à redonner » ([authorize]) : rien à réessayer ici. */
    override suspend fun retry() = Unit
}
