package com.skohscripts.kairos.android

import android.content.Context
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.legacy.Kairos2Import
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
 * Services Android (docs/spec-v3/distribution.md § Android) : base
 * `kairos.db` du stockage privé, sélecteurs de fichiers du système (sans
 * permission de stockage), sauvegardes dans `files/backups/`.
 */
object AndroidServices {
    const val DATABASE_NAME = "kairos.db"

    suspend fun open(
        context: Context,
        files: FileService,
        notifier: ChronoNotifier,
        pickFile: suspend (File) -> Boolean,
    ): AppServices = withContext(Dispatchers.IO) {
        val clock = Clock.System
        val language = Locale.getDefault().language
        val driver = AndroidSqliteDriver(DeferredSchema, context, DATABASE_NAME)
        val opened = KairosStore.open(driver)
        val examples = { ExampleData.snapshot(clock.todayIn(TimeZone.currentSystemDefault()), clock.now(), language) }
        // Base v3 neuve et base Kairos 2 héritée de l'APK Python : migration automatique
        // (docs/spec-v3/migration-2x.md § Android). Illisible : les exemples, comme sans elle.
        val legacyFile = AndroidLegacy.installed(context).takeIf { opened.created }
        val migration = legacyFile?.let { file ->
            runCatching {
                Kairos2Import.convert(AndroidLegacy.read(file, File(AndroidLegacy.folder(context), "settings.json")), language, clock.now())
            }.getOrNull()
        }
        val repository = KairosRepository.open(opened, examples = { migration?.first ?: examples() }, clock = clock)
        // Renommée seulement si la base v3 contient bien ce qui a été migré.
        val report = migration?.second?.takeIf { repository.snapshot.value.tasks.size == it.tasks }
        if (report != null) legacyFile?.let(AndroidLegacy::markMigrated)
        AppServices(
            repository = repository,
            files = files,
            backups = FolderBackups(File(context.filesDir, "backups")),
            dataLocation = null,
            clock = clock,
            notifier = notifier,
            legacy = AndroidLegacyImport(context, pickFile),
            firstLaunch = opened.created,
            migrated = report,
        )
    }

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
