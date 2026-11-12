package com.skohscripts.kairos.ui.app

/** Niveau de protection des secrets, pour dire la vérité à l'utilisateur (docs/spec/connexions-en-ligne.md § Secrets). */
enum class SecretLevel { HARDWARE, SYSTEM, FILE, NONE }

/**
 * Magasin de secrets d'une plateforme : lien ICS (`url:<idSource>`), identifiant du calendrier distant (`remote:<idSource>`),
 * compte (`account:<compte>`). Une valeur illisible (clé perdue, fichier altéré) est **absente** : `get` rend `null`, jamais
 * une exception. Jamais dans la base, l'export, la sauvegarde ni la synchronisation du fichier lié.
 */
interface SecretStore {
    val level: SecretLevel

    fun get(key: String): String?

    fun put(key: String, value: String)

    fun delete(key: String)

    fun keys(): Set<String>
}
