package com.skohscripts.kairos.core.updates

import com.skohscripts.kairos.core.AppVersion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Vérification des nouvelles versions (docs/spec-v3/mises-a-jour.md), partie
 * pure : lire la réponse de l'API GitHub, comparer, décider s'il est temps de
 * redemander. L'appel réseau appartient au bureau (Android n'a pas la
 * permission réseau ; la version web est toujours la dernière).
 */
object UpdateCheck {
    /** Dernière version **publiée** (GitHub exclut préversions et brouillons de `/latest`). */
    const val LATEST_URL = "https://api.github.com/repos/SKOHscripts/Kairos/releases/latest"

    /** Au plus une vérification automatique toutes les 6 heures (comme Kairos 2). */
    val INTERVAL = 6.hours

    data class Release(val version: AppVersion, val url: String)

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * `tag_name` et `html_url` de la réponse ; `null` pour une préversion, un
     * brouillon, un tag qui n'est pas une version ou une réponse illisible.
     */
    fun parseLatest(body: String): Release? = runCatching {
        val o = json.parseToJsonElement(body).jsonObject
        fun flag(name: String) = (o[name] as? JsonPrimitive)?.booleanOrNull == true
        if (flag("prerelease") || flag("draft")) return null
        val version = (o["tag_name"] as? JsonPrimitive)?.contentOrNull?.let(AppVersion::parse) ?: return null
        if (version.isPrerelease) return null
        val url = (o["html_url"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("https://github.com/") } ?: return null
        Release(version, url)
    }.getOrNull()

    /** [release] est plus récente que la version installée. */
    fun isNewer(release: Release, installed: AppVersion): Boolean = release.version > installed

    /** Faut-il vérifier maintenant ? Jamais vérifié, ou dernière vérification il y a [INTERVAL] ou plus. */
    fun due(lastCheck: Instant?, now: Instant): Boolean = lastCheck == null || now - lastCheck >= INTERVAL || now < lastCheck
}
