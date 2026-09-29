package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.data.KairosRepository
import kotlin.time.Clock

/**
 * Ce que chaque plateforme fournit à l'interface commune
 * (docs/spec-v3/architecture.md § Graphe) : la base ouverte, l'accès aux
 * fichiers, les sauvegardes, et l'emplacement des données à afficher.
 */
class AppServices(
    val repository: KairosRepository,
    val files: FileService,
    val backups: BackupStore,
    /** Emplacement lisible des données (chemin sur le bureau), `null` si sans objet. */
    val dataLocation: String? = null,
    /** Version web seulement : fichier lié (docs/spec-v3/export-import.md § Version web). */
    val linkedStorage: LinkedStorage? = null,
    val clock: Clock = Clock.System,
    /** Notifications du chrono (docs/spec-v3/temps-reel-chrono.md). */
    val notifier: ChronoNotifier = NoNotifier,
)

/** Choix d'un fichier par l'utilisateur (boîtes de dialogue du système). */
interface FileService {
    /** Propose d'enregistrer [text] ; `false` si l'utilisateur annule. */
    suspend fun saveText(suggestedName: String, text: String): Boolean

    /** Propose d'ouvrir un fichier texte ; `null` si l'utilisateur annule. */
    suspend fun openText(): String?
}

/** Sauvegardes automatiques (avant un import) : garde les plus récentes. */
fun interface BackupStore {
    suspend fun save(name: String, text: String)
}
