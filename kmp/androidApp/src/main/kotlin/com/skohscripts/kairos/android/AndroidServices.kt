package com.skohscripts.kairos.android

import com.skohscripts.kairos.ui.app.DemoSource
import com.skohscripts.kairos.ui.app.openDemo
import com.skohscripts.kairos.ui.app.LinkedFileSync
import android.content.Context
import android.content.res.Resources
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.updates.InstallSource
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.data.db.KairosDatabase
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.BackupStore
import com.skohscripts.kairos.ui.app.ChronoNotifier
import com.skohscripts.kairos.ui.app.FileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.io.File
import java.util.Locale
import kotlin.time.Clock

/**
 * Services Android (docs/spec/distribution.md § Android) : base
 * `kairos.db` du stockage privé, sélecteurs de fichiers du système (sans
 * permission de stockage), sauvegardes dans `files/backups/`.
 */
object AndroidServices {
    const val DATABASE_NAME = "kairos.db"

    suspend fun open(
        context: Context,
        files: FileService,
        notifier: ChronoNotifier,
    ): AppServices = withContext(Dispatchers.IO) {
        val clock = Clock.System
        // Langue du système lue dans la configuration du système, pas dans `Locale.getDefault()` : celle-ci porte
        // déjà la langue choisie dans Kairos quand l'activité est recréée (docs/spec/i18n.md).
        val language = Resources.getSystem().configuration.locales[0].language
        val driver = AndroidSqliteDriver(DeferredSchema, context, DATABASE_NAME)
        val opened = KairosStore.open(driver)
        val examples = { ExampleData.snapshot(clock.todayIn(TimeZone.currentSystemDefault()), clock.now(), language) }
        // Fichier lié (docs/spec/export-import.md § Fichier lié du bureau et d'Android) : branché sur chaque nouvel état.
        val linked = LinkedFileSync(AndroidLinkedFile(context), KairosProcess.scope, clock)
        val repository = KairosRepository.open(opened, examples = examples, clock = clock, onChanged = { linked.changed(it) })
        linked.start(repository.snapshot.value)
        AppServices(
            repository = repository,
            files = files,
            backups = FolderBackups(File(context.filesDir, "backups")),
            dataLocation = null,
            linkedStorage = linked,
            // Calendriers du téléphone (docs/spec/connexions-externes.md § Android) : lus en direct, jamais stockés.
            calendars = AndroidCalendarService(context, KairosProcess.scope, clock).also { it.refresh() },
            clock = clock,
            notifier = notifier,
            firstLaunch = opened.created,
            systemLanguage = language,
            // APK installé à la main : le lien vers la page de téléchargement (docs/spec/mises-a-jour.md § Android).
            downloadPage = InstallSource.installedByHand(installerOf(context)),
            applyLanguage = { Locale.setDefault(Locale.forLanguageTag(it)) },
            // Démonstration jetable (docs/spec/visite-guidee.md § Visite interactive) : un nom nul, une base en mémoire.
            demo = DemoSource { lang ->
                withContext(Dispatchers.IO) {
                    val memory = AndroidSqliteDriver(DeferredSchema, context, null)
                    openDemo(KairosStore.open(memory), lang, close = { memory.close() }, clock = clock)
                }
            },
        )
    }

    /** Paquet qui a installé Kairos (`null` : inconnu, ou ouvert à la main sur un ancien Android) ; aucune permission. */
    @Suppress("DEPRECATION")
    private fun installerOf(context: Context): String? = runCatching {
        val pm = context.packageManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            pm.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()

    /**
     * Schéma factice pour le pilote Android : la création et les migrations
     * sont faites par `KairosStore.open` (commun aux trois plateformes, en mode
     * asynchrone) ; le pilote ne fait qu'ouvrir le fichier.
     */
    private object DeferredSchema : SqlSchema<QueryResult.Value<Unit>> {
        override val version: Long = KairosDatabase.Schema.version
        override fun create(driver: SqlDriver) = QueryResult.Unit
        override fun migrate(driver: SqlDriver, oldVersion: Long, newVersion: Long, vararg callbacks: AfterVersion) = QueryResult.Unit
    }
}

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
