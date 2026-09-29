package com.skohscripts.kairos.web

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
import com.skohscripts.kairos.ui.app.LinkedFileState
import com.skohscripts.kairos.ui.app.LinkedStorage
import com.skohscripts.kairos.ui.app.OpenedFile
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.w3c.dom.Worker
import kotlin.time.Clock

/**
 * Services de la version web (docs/spec-v3/export-import.md § Version web).
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
        )
    }
}

/** Export : téléchargement ; import : sélecteur de fichier du navigateur. */
private object BrowserFiles : FileService {
    override suspend fun saveText(suggestedName: String, text: String): Boolean {
        KairosWeb.download(suggestedName, text)
        return true
    }

    override suspend fun openText(): String? = KairosWeb.pickText().await<JsString?>()?.toString()
}

private class WebLinkedStorage(private val clock: Clock, private val dataFile: String) : LinkedStorage {
    lateinit var repository: KairosRepository
    private val mutable = MutableStateFlow<LinkedFileState>(LinkedFileState.NotLinked)
    override val state: StateFlow<LinkedFileState> = mutable.asStateFlow()

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
        if (current is LinkedFileState.Linked && !KairosWeb.linkWrite(text).await<JsBoolean>().toBoolean()) {
            mutable.value = LinkedFileState.NeedsPermission(current.name, restore = false)
        }
    }

    override suspend fun createFile(): Boolean {
        val name = KairosWeb.linkCreate("kairos.json", encode(repository.snapshot.value)).await<JsString?>()?.toString()
            ?: return false
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
            persisting = true
            repository.replaceAll(ExportCodec.decode(text))
        } else {
            persist(repository.snapshot.value)
        }
        return true
    }
}
