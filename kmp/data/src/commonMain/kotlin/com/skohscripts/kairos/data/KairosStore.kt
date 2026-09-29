package com.skohscripts.kairos.data

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.SqlDriver
import com.skohscripts.kairos.data.db.KairosDatabase

/**
 * Ouverture de la base (docs/spec/modele-donnees.md § Ouverture et
 * migrations) : crée le schéma sur une base sans table `task`, applique les
 * migrations SQLDelight (fichiers `.sqm`) si `PRAGMA user_version` est en
 * retard, puis tient cette version à jour. Idempotent.
 */
object KairosStore {
    class Opened(val database: KairosDatabase, val driver: SqlDriver, val created: Boolean)

    suspend fun open(driver: SqlDriver): Opened {
        val schema = KairosDatabase.Schema
        val current = userVersion(driver)
        // La présence de la table `task` décide de la création, pas `user_version` :
        // le pilote Android pose lui-même la version (schéma factice, voir androidApp).
        val created = when {
            !hasTaskTable(driver) -> {
                schema.awaitCreate(driver)
                true
            }
            current in 1 until schema.version -> {
                schema.awaitMigrate(driver, current, schema.version)
                false
            }
            else -> false
        }
        if (current != schema.version) driver.execute(null, "PRAGMA user_version = ${schema.version}", 0).await()
        return Opened(KairosDatabase(driver), driver, created)
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
