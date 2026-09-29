package com.skohscripts.kairos.android

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.skohscripts.kairos.core.legacy.LegacyDatabase
import com.skohscripts.kairos.core.legacy.NotALegacyDatabase
import com.skohscripts.kairos.ui.app.LegacyImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bases Kairos 2 sur Android (docs/spec-v3/migration-2x.md § Android).
 * L'APK Python rangeait tout dans `files/kairos-data/` (`tasks.db`,
 * `settings.json`) : l'APK v3, installé par-dessus (même identifiant, même
 * clé), en hérite. Lecture seule ; l'ancienne base n'est jamais modifiée.
 */
object AndroidLegacy {
    private val TABLES = listOf("task", "time_block", "task_dependency", "work_session", "note")

    fun folder(context: Context) = File(context.filesDir, "kairos-data")

    /** `tasks.db` de Kairos 2 dans le stockage privé, s'il existe. */
    fun installed(context: Context): File? = File(folder(context), "tasks.db").takeIf { it.isFile }

    /** Renomme l'ancienne base migrée (jamais supprimée) : la migration n'est pas refaite. */
    fun markMigrated(file: File) {
        file.renameTo(File(file.parentFile, "tasks.db.migrated-to-v3"))
    }

    fun read(database: File, settings: File? = null): LegacyDatabase {
        val header = runCatching { database.inputStream().use { String(it.readNBytes(16), Charsets.US_ASCII) } }.getOrNull()
        if (header != "SQLite format 3\u0000") throw NotALegacyDatabase()
        val tables = SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val existing = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            TABLES.filter { it in existing }.associateWith { table ->
                db.rawQuery("SELECT * FROM \"$table\"", null).use { c -> rows(c) }
            }
        }
        return LegacyDatabase(tables, settings?.takeIf { it.isFile }?.readText())
    }

    private fun rows(c: Cursor): List<Map<String, Any?>> = buildList {
        while (c.moveToNext()) {
            add(
                (0 until c.columnCount).associate { i ->
                    c.getColumnName(i) to when (c.getType(i)) {
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        Cursor.FIELD_TYPE_STRING -> c.getString(i)
                        else -> null
                    }
                },
            )
        }
    }
}

/** Import manuel : un `tasks.db` choisi dans le sélecteur du système, copié dans le cache puis lu. */
internal class AndroidLegacyImport(private val context: Context, private val pickFile: suspend (File) -> Boolean) : LegacyImport {
    override val found: String? = null

    override suspend fun readFound(): LegacyDatabase = error("aucune base à l'emplacement habituel")

    override suspend fun pick(): LegacyDatabase? {
        val copy = File(context.cacheDir, "kairos2-import.db")
        if (!pickFile(copy)) return null
        return withContext(Dispatchers.IO) {
            try {
                AndroidLegacy.read(copy)
            } finally {
                copy.delete()
            }
        }
    }
}
