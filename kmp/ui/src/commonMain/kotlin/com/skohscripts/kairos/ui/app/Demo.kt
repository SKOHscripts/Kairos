package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.team.forecast.TeamUiState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Ouvre une démonstration jetable (docs/spec/visite-guidee.md § Visite interactive) : une base **neuve** en mémoire, garnie du
 * jeu d'exemples dans [language] (la langue de l'interface). Chaque plateforme ne fournit que le pilote en mémoire.
 */
fun interface DemoSource {
    suspend fun open(language: String): DemoSession
}

/** Une démonstration ouverte : son dépôt, et [close] qui ferme sa base (le worker sur le web). */
class DemoSession(val repository: KairosRepository, val close: suspend () -> Unit)

/**
 * La démonstration sur une base [opened] en mémoire : le jeu d'exemples (`ExampleData`, donc des réglages par défaut et des
 * scénarios identiques pour tous), **sans `onChanged`** : ni stockage du navigateur, ni fichier lié, ni sauvegarde.
 */
suspend fun openDemo(opened: KairosStore.Opened, language: String, close: suspend () -> Unit, clock: Clock = Clock.System): DemoSession {
    val repository = KairosRepository.open(
        opened,
        examples = { ExampleData.snapshot(clock.todayIn(TimeZone.currentSystemDefault()), clock.now(), language) },
        clock = clock,
    )
    return DemoSession(repository, close)
}

/** Fichiers refusés : la démonstration n'exporte ni n'importe rien. */
private object NoFiles : FileService {
    override suspend fun saveText(suggestedName: String, text: String) = false
    override suspend fun openText(): String? = null
}

/**
 * Les services de la démonstration, construits **champ par champ** : le dépôt de [session] et rien de la plateforme (ni
 * fichier lié, mises à jour, dossier, diagnostic, calendriers du téléphone, raccourcis, notifications, ni démonstration
 * dans la démonstration). Seules l'horloge et la langue sont reprises. Un champ ajouté à [AppServices] se décide ici
 * (`DemoServicesTest` le garde).
 */
fun AppServices.forDemo(session: DemoSession): AppServices = AppServices(
    repository = session.repository,
    files = NoFiles,
    backups = BackupStore { _, _ -> },
    dataLocation = null,
    linkedStorage = null,
    clock = clock,
    notifier = NoNotifier,
    firstLaunch = false,
    updates = null,
    downloadPage = false,
    dataFolder = null,
    diagnostics = null,
    calendars = null,
    shortcuts = null,
    teamUi = TeamUiState(),
    systemLanguage = systemLanguage,
    applyLanguage = applyLanguage,
    demo = null,
)
