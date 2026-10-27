package com.skohscripts.kairos.data

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.SqlDriver
import com.skohscripts.kairos.data.db.KairosDatabase

/**
 * Ouverture de la base (docs/spec/modele-donnees.md § Ouverture et
 * migrations) : crée le schéma sur une base sans table `task`, sinon applique
 * les migrations SQLDelight (fichiers `.sqm`) qui lui manquent, puis tient
 * `PRAGMA user_version` à jour. Idempotent.
 *
 * La version d'une base existante est lue sur **ses tables et ses colonnes**
 * ([actualVersion]), jamais sur `user_version` : le pilote Android pose
 * lui-même cette valeur à l'ouverture, avant que ce code ne la lise, si bien
 * qu'une base restée en schéma 6 apparaissait déjà en schéma 7 et n'était pas
 * migrée (« no such column: task.team_excluded », issue #65 et suivantes).
 */
object KairosStore {
    class Opened(val database: KairosDatabase, val driver: SqlDriver, val created: Boolean)

    suspend fun open(driver: SqlDriver): Opened {
        val schema = KairosDatabase.Schema
        val stamped = userVersion(driver)
        val database = KairosDatabase(driver)
        val created = if (!hasTaskTable(driver)) {
            schema.awaitCreate(driver)
            true
        } else {
            val actual = actualVersion(driver)
            // Toutes les migrations manquantes ou aucune : une base restée à mi-chemin ne serait plus réparable.
            if (actual < schema.version) database.transaction { schema.awaitMigrate(driver, actual, schema.version) }
            false
        }
        if (stamped != schema.version) driver.execute(null, "PRAGMA user_version = ${schema.version}", 0).await()
        return Opened(database, driver, created)
    }

    /**
     * Marqueurs de schéma, du plus récent au plus ancien : la première table ou colonne présente donne la version.
     * Un marqueur par fichier `.sqm` (`N.sqm` mène à la version N + 1) ; en ajouter un à chaque migration, sans quoi
     * `KairosStoreTest.everyMigrationHasAMarker` échoue.
     */
    private val MARKERS: List<Pair<Long, String>> = listOf(
        12L to "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'migration_mark'",
        11L to "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'calendar_source'",
        10L to "SELECT count(*) FROM pragma_table_info('team_member') WHERE name = 'week_hours'",
        9L to "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'time_block_exception'",
        8L to "SELECT count(*) FROM pragma_table_info('task') WHERE name = 'series_id'",
        7L to "SELECT count(*) FROM pragma_table_info('task') WHERE name = 'team_excluded'",
        6L to "SELECT count(*) FROM pragma_table_info('task') WHERE name = 'origin'",
        5L to "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'team_scenario'",
        4L to "SELECT count(*) FROM pragma_table_info('task') WHERE name = 'progress_percent'",
        3L to "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'member_absence'",
        2L to "SELECT count(*) FROM pragma_table_info('task') WHERE name = 'space'",
    )

    /** Version réelle du schéma d'une base qui a une table `task` : 1 (celui de la 3.0.0) si aucun marqueur n'est là. */
    internal suspend fun actualVersion(driver: SqlDriver): Long {
        MARKERS.forEachIndexed { index, (version, sql) ->
            // Identifiants négatifs distincts : le pilote met les requêtes en cache par identifiant.
            val present = Query(-10 - index, driver, "KairosStore", "marker$version", sql) { it.getLong(0) ?: 0L }.awaitAsOneOrNull() ?: 0L
            if (present > 0L) return version
        }
        return 1L
    }

    // Lectures par l'API Query (comme le code généré) : un mappeur QueryResult
    // paresseux passé directement au pilote serait évalué après la fermeture du
    // curseur par le pilote JDBC synchrone, et lirait toujours 0.
    private suspend fun userVersion(driver: SqlDriver): Long =
        Query(-1, driver, "KairosStore", "userVersion", "PRAGMA user_version") { it.getLong(0) ?: 0L }
            .awaitAsOneOrNull() ?: 0L

    private suspend fun hasTaskTable(driver: SqlDriver): Boolean =
        (
            Query(-2, driver, "KairosStore", "hasTaskTable", "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'task'") {
                it.getLong(0) ?: 0L
            }.awaitAsOneOrNull() ?: 0L
        ) > 0L
}
