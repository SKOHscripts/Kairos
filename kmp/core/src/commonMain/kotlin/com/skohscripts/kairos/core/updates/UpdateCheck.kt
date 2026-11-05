package com.skohscripts.kairos.core.updates

import com.skohscripts.kairos.core.AppVersion
import com.skohscripts.kairos.core.settings.UiLanguage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Vérification des nouvelles versions (docs/spec/mises-a-jour.md), partie
 * pure : lire la réponse de l'API GitHub, comparer, décider s'il est temps de
 * redemander. L'appel réseau appartient au bureau (Android n'a pas la
 * permission réseau ; la version web est toujours la dernière).
 */
object UpdateCheck {
    /** Dernière version **publiée** (GitHub exclut préversions et brouillons de `/latest`). */
    const val LATEST_URL = "https://api.github.com/repos/SKOHscripts/Kairos/releases/latest"

    /** Les dernières releases, préversions comprises : ce que lit une installation « Kairos Preview » (§ Préversions). */
    const val RELEASES_URL = "https://api.github.com/repos/SKOHscripts/Kairos/releases?per_page=20"

    /**
     * Ce que demande une installation de [installed] : une version finale ne regarde que les versions finales (`/latest`) ;
     * une préversion (« Kairos Preview ») regarde aussi les préversions, sinon un testeur n'apprendrait jamais la beta
     * suivante (docs/spec/mises-a-jour.md § Préversions).
     */
    fun urlFor(installed: AppVersion): String = if (installed.isPrerelease) RELEASES_URL else LATEST_URL

    /** La réponse de [urlFor] lue pour [installed] : [parseLatest] ou [parseNewest]. */
    fun parseFor(installed: AppVersion, body: String): Release? = if (installed.isPrerelease) parseNewest(body) else parseLatest(body)

    /**
     * Page de téléchargement (GitHub Pages) dans la langue de l'interface [language] (une de `UiLanguage.SUPPORTED`) :
     * elle dit la dernière version (issue #143). Le français est à la racine, les autres langues dans leur dossier.
     */
    fun downloadPage(language: String): String =
        "https://skohscripts.github.io/Kairos/" + if (UiLanguage.isFrench(language)) "" else "${UiLanguage.resolve(language, language)}/"

    /** Au plus une vérification automatique toutes les 6 heures. */
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

    /**
     * La plus récente des releases d'une liste (`/releases`), préversions comprises ; brouillons, tags qui ne sont pas des
     * versions et liens hors de GitHub écartés ; `null` si aucune ou réponse illisible.
     */
    fun parseNewest(body: String): Release? = runCatching {
        json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
            val o = element.jsonObject
            if ((o["draft"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
            val version = (o["tag_name"] as? JsonPrimitive)?.contentOrNull?.let(AppVersion::parse) ?: return@mapNotNull null
            val url = (o["html_url"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("https://github.com/") } ?: return@mapNotNull null
            Release(version, url)
        }.maxByOrNull { it.version }
    }.getOrNull()

    /** [release] est plus récente que la version installée. */
    fun isNewer(release: Release, installed: AppVersion): Boolean = release.version > installed

    /** Faut-il vérifier maintenant ? Jamais vérifié, ou dernière vérification il y a [INTERVAL] ou plus. */
    fun due(lastCheck: Instant?, now: Instant): Boolean = lastCheck == null || now - lastCheck >= INTERVAL || now < lastCheck
}
