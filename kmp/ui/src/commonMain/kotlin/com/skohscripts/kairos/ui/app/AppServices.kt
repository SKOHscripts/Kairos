package com.skohscripts.kairos.ui.app

import androidx.compose.ui.graphics.ImageBitmap
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.ui.team.forecast.TeamUiState
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
    /** La base vient d'être créée : accueil du premier lancement (docs/spec/accueil.md). */
    val firstLaunch: Boolean = false,
    /** Bureau : vérification des nouvelles versions (docs/spec/mises-a-jour.md). */
    val updates: UpdateService? = null,
    /**
     * Android, APK installé à la main (docs/spec/mises-a-jour.md § Android, issue #143) : les Réglages montrent la carte
     * « Mises à jour » avec le lien vers la page de téléchargement. Faux ailleurs (le bureau a [updates], un magasin met à jour).
     */
    val downloadPage: Boolean = false,
    /** Dossier des données à choisir (bureau, docs/spec/bureau-avance.md) ; `null` ailleurs. */
    val dataFolder: DataFolderService? = null,
    /** Journal de diagnostic (bureau, docs/spec/bureau-avance.md) ; `null` ailleurs. */
    val diagnostics: Diagnostics? = null,
    /** Calendriers du téléphone (Android, docs/spec/connexions-externes.md § Étape 2) ; `null` ailleurs. */
    val calendars: CalendarService? = null,
    /** Bureau, copie portable seulement : raccourci (docs/spec/raccourci-portable.md). */
    val shortcuts: ShortcutService? = null,
    /** Espace Équipe : sélection du Backlog, choix et résultats des Prévisions, graine injectable (tests). */
    val teamUi: TeamUiState = TeamUiState(),
    /**
     * Langue du système (`fr`, `ru`…), lue par la plateforme **avant** toute surcharge (docs/spec/i18n.md § Choix de
     * la langue) ; `null` : celle que Compose annonce au premier affichage (tests d'un écran seul).
     */
    val systemLanguage: String? = null,
    /**
     * Applique la langue d'interface résolue (`fr` ou `en`) à la plateforme, **avant** que l'interface la lise :
     * locale par défaut de la JVM (bureau, Android) ou du navigateur (web). Sans effet par défaut (tests).
     */
    val applyLanguage: (String) -> Unit = {},
    /**
     * Démonstration jetable (docs/spec/visite-guidee.md § Visite interactive) : la plateforme fournit seulement une base en
     * mémoire. `null` (tests, captures) : pas d'entrée « Essayer en démonstration ».
     */
    val demo: DemoSource? = null,
)

/** Choix d'un fichier par l'utilisateur (boîtes de dialogue du système). */
interface FileService {
    /**
     * Propose d'enregistrer [text] ; `false` si l'utilisateur annule. Le type du fichier suit l'extension du nom
     * ([mimeOf]) : sans cela, Android ajoutait `.json` à un `.csv`.
     */
    suspend fun saveText(suggestedName: String, text: String): Boolean

    /**
     * Propose d'enregistrer [image] en PNG (image d'une carte des Stats, docs/spec/statistiques.md § Export) ; `false`
     * si l'utilisateur annule. L'encodage est celui de la plateforme. Les doublures de test qui ne l'implémentent pas
     * lèvent une erreur, reçue comme une erreur de fichier.
     */
    suspend fun saveImage(suggestedName: String, image: ImageBitmap): Boolean =
        throw UnsupportedOperationException("saveImage")

    /** Propose d'ouvrir un fichier texte ; `null` si l'utilisateur annule. */
    suspend fun openText(): String?
}

/** Type MIME d'un fichier enregistré, d'après l'extension de [name] : `.csv`, `.png`, sinon JSON (l'export de données). */
fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "csv" -> "text/csv"
    "png" -> "image/png"
    else -> "application/json"
}

/** Sauvegardes automatiques (avant un import) : garde les plus récentes. */
fun interface BackupStore {
    suspend fun save(name: String, text: String)
}
