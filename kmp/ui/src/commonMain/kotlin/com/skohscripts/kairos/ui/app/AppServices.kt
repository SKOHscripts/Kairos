package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.legacy.LegacyDatabase
import com.skohscripts.kairos.core.legacy.LegacyReport
import com.skohscripts.kairos.data.KairosRepository
import kotlin.time.Clock

/**
 * Ce que chaque plateforme fournit à l'interface commune
 * (docs/spec/architecture.md § Graphe) : la base ouverte, l'accès aux
 * fichiers, les sauvegardes, et l'emplacement des données à afficher.
 */
class AppServices(
    val repository: KairosRepository,
    val files: FileService,
    val backups: BackupStore,
    /** Emplacement lisible des données (chemin sur le bureau), `null` si sans objet. */
    val dataLocation: String? = null,
    /** Version web seulement : fichier lié (docs/spec/export-import.md § Version web). */
    val linkedStorage: LinkedStorage? = null,
    val clock: Clock = Clock.System,
    /** Notifications du chrono (docs/spec/temps-reel-chrono.md). */
    val notifier: ChronoNotifier = NoNotifier,
    /** Migration d'une base Kairos 2, là où la plateforme sait la lire (bureau, Android). */
    val legacy: LegacyImport? = null,
    /** La base vient d'être créée : accueil du premier lancement (docs/spec/accueil.md). */
    val firstLaunch: Boolean = false,
    /** Android : base Kairos 2 migrée automatiquement à ce lancement (docs/spec/migration-2x.md). */
    val migrated: LegacyReport? = null,
    /** Bureau : vérification des nouvelles versions (docs/spec/mises-a-jour.md). */
    val updates: UpdateService? = null,
)

/** Lecture d'une base Kairos 2 (docs/spec/migration-2x.md) ; la conversion est commune (`Kairos2Import`). */
interface LegacyImport {
    /** Chemin lisible d'une base Kairos 2 trouvée à son emplacement habituel, `null` sinon. */
    val found: String?

    /** Lit la base trouvée ([found] non nul). */
    suspend fun readFound(): LegacyDatabase

    /** Fait choisir un fichier `tasks.db` et le lit ; `null` si l'utilisateur annule. */
    suspend fun pick(): LegacyDatabase?
}

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
