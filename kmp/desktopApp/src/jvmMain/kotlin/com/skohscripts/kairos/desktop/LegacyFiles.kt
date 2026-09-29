package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.core.legacy.LegacyDatabase
import com.skohscripts.kairos.core.legacy.NotALegacyDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.sql.DriverManager
import java.util.Properties

/**
 * Bases Kairos 2 sur le bureau (docs/spec/migration-2x.md § Bureau) :
 * où les trouver, et comment les lire **en lecture seule** (JDBC, le pilote
 * SQLite déjà embarqué pour SQLDelight). L'ancienne base n'est jamais
 * modifiée.
 */
object LegacyFiles {
    /** Tables de Kairos 2 reprises par la migration (`task_sync_meta` est ignorée). */
    private val TABLES = listOf("task", "time_block", "task_dependency", "work_session", "note")
    private val json = Json { ignoreUnknownKeys = true }

    /** Base trouvée : le fichier SQLite et le `settings.json` voisin, s'il existe. */
    data class Found(val database: File, val settings: File?)

    /**
     * La base de Kairos 2 à son emplacement habituel : `tasks_database_path`
     * de `<racine>/settings.json` s'il est renseigné et existe, sinon
     * `<racine>/tasks.db`. Racine : `KAIROS_DATA_DIR`, sinon le dossier
     * `Kairos` de platformdirs.
     */
    fun find(
        env: Map<String, String> = System.getenv(),
        osName: String = System.getProperty("os.name"),
        home: String = System.getProperty("user.home"),
    ): Found? {
        val root = env["KAIROS_DATA_DIR"]?.takeIf { it.isNotBlank() }?.let(::File) ?: DataDirectory.kairosRoot(env, osName, home)
        val settings = File(root, "settings.json").takeIf { it.isFile }
        val configured = settings?.let { configuredPath(it) }?.let(::File)?.takeIf { it.isFile }
        val database = configured ?: File(root, "tasks.db").takeIf { it.isFile } ?: return null
        return Found(database, settings)
    }

    private fun configuredPath(settings: File): String? = runCatching {
        val root = json.parseToJsonElement(settings.readText()).jsonObject
        (root["settings"]?.jsonObject?.get("tasks_database_path") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Lit [database] (et [settings]) ; [NotALegacyDatabase] si ce n'est pas une base SQLite de Kairos 2. */
    fun read(database: File, settings: File? = null): LegacyDatabase {
        if (!isSqlite(database)) throw NotALegacyDatabase()
        val props = Properties().apply { setProperty("open_mode", "1") } // SQLITE_OPEN_READONLY
        val tables = DriverManager.getConnection("jdbc:sqlite:${database.absolutePath}", props).use { connection ->
            val existing = connection.createStatement().use { st ->
                st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'").use { rs ->
                    buildSet { while (rs.next()) add(rs.getString(1)) }
                }
            }
            TABLES.filter { it in existing }.associateWith { table ->
                connection.createStatement().use { st ->
                    st.executeQuery("SELECT * FROM \"$table\"").use { rs ->
                        val meta = rs.metaData
                        val columns = (1..meta.columnCount).map { meta.getColumnName(it) }
                        buildList {
                            while (rs.next()) {
                                add(columns.withIndex().associate { (i, name) -> name to normalize(rs.getObject(i + 1)) })
                            }
                        }
                    }
                }
            }
        }
        return LegacyDatabase(tables, settings?.takeIf { it.isFile }?.readText())
    }

    private fun normalize(value: Any?): Any? = when (value) {
        is Int -> value.toLong()
        is Short -> value.toLong()
        is Float -> value.toDouble()
        is ByteArray -> null
        else -> value
    }

    /** En-tête « SQLite format 3\0 » : refuse tout autre fichier avant d'ouvrir une connexion. */
    private fun isSqlite(file: File): Boolean = runCatching {
        file.inputStream().use { input -> String(input.readNBytes(16), Charsets.US_ASCII) == "SQLite format 3\u0000" }
    }.getOrDefault(false)
}
