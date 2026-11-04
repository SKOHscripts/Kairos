package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/** Le lien gardé par l'appareil : la cible (chemin ou URI), le nom affiché, l'empreinte du dernier texte écrit ou vu. */
data class SavedLink(val target: String, val name: String, val hash: String)

/** Ce que la plateforme fournit au fichier lié : choisir, lire, écrire, garder le lien (docs/spec/export-import.md). */
interface LinkedFileIo {
    /** Sélecteur « enregistrer » ; la cible choisie, `null` si annulé. */
    suspend fun pickCreate(suggestedName: String): String?

    /** Sélecteur « ouvrir » ; la cible choisie, `null` si annulé. */
    suspend fun pickOpen(): String?

    suspend fun read(target: String): String

    /** Écrit [text] d'un bloc (atomique là où la plateforme le permet). */
    suspend fun write(target: String, text: String)

    fun displayName(target: String): String

    suspend fun loadLink(): SavedLink?

    /** Garde le lien (`null` : délié). */
    suspend fun saveLink(link: SavedLink?)
}

/**
 * Fichier lié du bureau et d'Android (docs/spec/synchronisation.md § Fichier lié : cycle de synchronisation). Chaque **passe**,
 * sous un verrou, relit le fichier et le **fusionne** avec la base ([LinkedMerge]) avant d'y écrire, et n'écrit que si le
 * fichier n'a pas déjà tout. Déclencheurs : [changed] (écriture [DEBOUNCE_MS] après le dernier changement), [flush]
 * (arrière-plan, fermeture), [start] (ouverture), [setVisible] (retour au premier plan, puis relecture toutes les [POLL_MS]
 * tant que l'application est visible), [mergeNow].
 */
class LinkedFileSync(
    private val io: LinkedFileIo,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    /** Nom de la plateforme, pour le nom de l'appareil (« Android · 3f9c »). */
    private val platformLabel: String = "Kairos",
    private val backups: BackupStore? = null,
) : LinkedStorage {
    private val mutable = MutableStateFlow<LinkedFileState>(LinkedFileState.NotLinked)
    override val state: StateFlow<LinkedFileState> = mutable.asStateFlow()

    private val merges = MutableSharedFlow<com.skohscripts.kairos.core.sync.MergeOutcome>(extraBufferCapacity = 4)

    /** Chaque fusion faite par une passe (le message « Fusionné avec … » de l'interface). */
    val merged: SharedFlow<com.skohscripts.kairos.core.sync.MergeOutcome> = merges.asSharedFlow()

    private val lock = Mutex()
    private lateinit var repository: KairosRepository
    private lateinit var engine: LinkedMerge
    // Lus et écrits depuis plusieurs fils (écritures du dépôt, minuterie, interface) : toujours la dernière valeur.
    @Volatile private var link: SavedLink? = null

    @Volatile private var dirty = false
    private var timer: Job? = null
    private var poll: Job? = null
    @Volatile private var visible = false
    private var pendingTarget: String? = null
    @Volatile private var inPass = false

    /** Relit le lien gardé et fait une première passe (ouverture de l'application). */
    suspend fun start(repository: KairosRepository) {
        this.repository = repository
        engine = LinkedMerge(repository, clock, platformLabel, backups)
        link = runCatching { io.loadLink() }.getOrNull()
        mutable.value = link?.let { LinkedFileState.Linked(it.name) } ?: LinkedFileState.NotLinked
        if (link != null) lock.withLock { pass() }
    }

    /** Nouvel état de la base : écriture programmée, une seule pour une rafale. */
    fun changed(snapshot: KairosSnapshot) {
        // L'état publié par la fusion d'une passe n'est pas une modification locale : la passe sait déjà s'il faut écrire. Une
        // fusion faite ailleurs (« Fusionner un fichier Kairos… ») en est une pour le fichier lié.
        if (inPass && ::repository.isInitialized && snapshot === repository.mergedSnapshot) return
        dirty = true
        if (link == null) return
        timer?.cancel()
        timer = scope.launch {
            delay(DEBOUNCE_MS)
            lock.withLock { pass() }
        }
    }

    /** Écrit tout de suite ce qui attend (arrière-plan, fermeture). */
    suspend fun flush() {
        timer?.cancel()
        if (dirty) lock.withLock { pass() }
    }

    /**
     * L'application devient visible (premier plan, focus de la fenêtre) ou ne l'est plus. Visible : une passe aussitôt, puis
     * une relecture toutes les [POLL_MS] ; cachée : plus de relecture (aucun calcul en arrière-plan).
     */
    fun setVisible(value: Boolean) {
        if (value == visible) return
        visible = value
        poll?.cancel()
        if (!value) return
        poll = scope.launch {
            while (isActive) {
                if (link != null) lock.withLock { pass() }
                delay(POLL_MS)
            }
        }
    }

    /** Retour au premier plan (focus de la fenêtre) : une passe tout de suite, sans attendre la minuterie. */
    fun resume() {
        if (link == null) return
        scope.launch { lock.withLock { pass() } }
    }

    /**
     * Une passe (sous [lock]) : lire, fusionner, écrire si besoin. Un état d'attente (confirmation, illisible) arrête les
     * passes automatiques jusqu'à la réponse de l'utilisateur. [confirmed] : premier rapprochement accepté.
     */
    private suspend fun pass(confirmed: Boolean = false, force: Boolean = false) {
        val current = link ?: return
        if (!::repository.isInitialized) return
        val waiting = mutable.value is LinkedFileState.NeedsConfirmation || mutable.value is LinkedFileState.Unreadable
        if (waiting && !confirmed && !force) return
        val text = try {
            io.read(current.target)
        } catch (e: Throwable) {
            mutable.value = LinkedFileState.WriteFailed(current.name)
            return
        }
        val pending = dirty
        dirty = false
        inPass = true
        val step = try {
            engine.pass(text, current.hash, pending, confirmed)
        } finally {
            inPass = false
        }
        when (step) {
            is LinkedMerge.Step.Done -> {
                step.outcome?.let { if (!it.changes.isEmpty) merges.tryEmit(it) }
                val text = step.write
                if (text != null) {
                    if (!write(current, text)) dirty = true
                } else {
                    remember(current.copy(hash = step.seenHash))
                    mutable.value = LinkedFileState.Linked(current.name)
                }
            }
            is LinkedMerge.Step.Confirm -> {
                dirty = dirty || pending
                mutable.value = LinkedFileState.NeedsConfirmation(current.name, step.summary)
            }
            LinkedMerge.Step.Retry -> dirty = dirty || pending
            is LinkedMerge.Step.Unreadable -> {
                dirty = dirty || pending
                mutable.value = LinkedFileState.Unreadable(current.name, step.reason)
            }
            LinkedMerge.Step.BackupFailed -> {
                dirty = dirty || pending
                mutable.value = LinkedFileState.WriteFailed(current.name)
            }
        }
    }

    private suspend fun write(target: SavedLink, text: String): Boolean {
        try {
            io.write(target.target, text)
        } catch (e: Throwable) {
            mutable.value = LinkedFileState.WriteFailed(target.name)
            return false
        }
        remember(target.copy(hash = LinkHash.of(text)))
        mutable.value = LinkedFileState.Linked(target.name)
        return true
    }

    private suspend fun remember(saved: SavedLink) {
        if (saved == link) return
        link = saved
        runCatching { io.saveLink(saved) }
    }

    override suspend fun createFile(): Boolean {
        val target = io.pickCreate(SUGGESTED_NAME) ?: return false
        if (!::repository.isInitialized) return false
        return lock.withLock {
            write(SavedLink(target, io.displayName(target), ""), engine.encode()).also { if (it) dirty = false }
        }
    }

    override suspend fun openFile(): OpenedFile? {
        val target = io.pickOpen() ?: return null
        val text = io.read(target)
        pendingTarget = target
        return OpenedFile(io.displayName(target), text)
    }

    override suspend fun adoptOpened(merge: Boolean): Boolean = lock.withLock {
        val target = pendingTarget ?: return@withLock false
        val text = runCatching { io.read(target) }.getOrElse { return@withLock false }
        // « Fusionner » : aucune empreinte connue, la passe fusionne le fichier ; « Remplacer » : il est la référence, et la base
        // synchronise dès maintenant pour que le remplacement qui suit reprenne les identités du fichier (sinon, doublons ailleurs).
        if (!merge) engine.ensureSync()
        val saved = SavedLink(target, io.displayName(target), if (merge) "" else LinkHash.of(text))
        remember(saved)
        pendingTarget = null
        mutable.value = LinkedFileState.Linked(saved.name)
        if (merge) {
            engine.resetFailures()
            pass(confirmed = true)
        }
        true
    }

    override suspend fun unlink() = lock.withLock {
        timer?.cancel()
        link = null
        runCatching { io.saveLink(null) }
        mutable.value = LinkedFileState.NotLinked
    }

    /** « Écraser » un fichier illisible : les données de cet appareil, sans fusion. */
    override suspend fun overwrite() {
        lock.withLock {
            val current = link ?: return@withLock
            if (write(current, engine.encode())) dirty = false
        }
    }

    override suspend fun confirmMerge() {
        lock.withLock {
            if (mutable.value !is LinkedFileState.NeedsConfirmation) return@withLock
            pass(confirmed = true)
        }
    }

    override suspend fun cancelMerge() {
        if (mutable.value is LinkedFileState.NeedsConfirmation) unlink()
    }

    override suspend fun mergeNow() {
        lock.withLock { pass() }
    }

    override suspend fun retry() {
        lock.withLock {
            engine.resetFailures()
            link?.let { mutable.value = LinkedFileState.Linked(it.name) }
            pass(force = true)
        }
    }

    companion object {
        /** Délai entre le dernier changement et l'écriture. */
        const val DEBOUNCE_MS = 2_000L

        /** Relecture du fichier tant que l'application est visible. */
        const val POLL_MS = 60_000L

        const val SUGGESTED_NAME = "kairos.json"
    }
}
