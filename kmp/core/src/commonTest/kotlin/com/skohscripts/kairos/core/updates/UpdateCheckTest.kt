package com.skohscripts.kairos.core.updates

import com.skohscripts.kairos.core.AppVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class UpdateCheckTest {
    private fun release(tag: String, prerelease: Boolean = false, url: String = "https://github.com/SKOHscripts/Kairos/releases/tag/$tag") =
        """{"tag_name": "$tag", "html_url": "$url", "prerelease": $prerelease, "draft": false, "assets": []}"""

    @Test
    fun a_published_release_is_read_and_compared() {
        val r = UpdateCheck.parseLatest(release("v3.0.1"))!!
        assertEquals(AppVersion.parse("3.0.1"), r.version)
        assertEquals("https://github.com/SKOHscripts/Kairos/releases/tag/v3.0.1", r.url)
        assertTrue(UpdateCheck.isNewer(r, AppVersion.parse("3.0.0")!!))
        assertTrue(UpdateCheck.isNewer(r, AppVersion.parse("3.0.1-alpha.6")!!))
        assertFalse(UpdateCheck.isNewer(r, AppVersion.parse("3.0.1")!!))
        // Pendant les alphas, /latest rend la dernière 2.x : jamais proposée à une 3.0.0-alpha.
        assertFalse(UpdateCheck.isNewer(UpdateCheck.parseLatest(release("v2.6.0"))!!, AppVersion.parse("3.0.0-alpha.6")!!))
    }

    @Test
    fun prereleases_odd_tags_foreign_urls_and_garbage_are_ignored() {
        assertNull(UpdateCheck.parseLatest(release("v3.1.0-beta.1")))
        assertNull(UpdateCheck.parseLatest(release("v3.1.0", prerelease = true)))
        assertNull(UpdateCheck.parseLatest(release("nightly")))
        assertNull(UpdateCheck.parseLatest(release("v3.1.0", url = "https://example.com/kairos")))
        assertNull(UpdateCheck.parseLatest("""{"message": "Not Found"}"""))
        assertNull(UpdateCheck.parseLatest("<html>"))
    }

    @Test
    fun a_preview_install_also_sees_prereleases() {
        // Une installation « Kairos Preview » lit la liste des releases et y prend la plus récente, préversion comprise.
        val beta = AppVersion.parse("4.1.0-beta.1")!!
        assertEquals(UpdateCheck.RELEASES_URL, UpdateCheck.urlFor(beta))
        assertEquals(UpdateCheck.LATEST_URL, UpdateCheck.urlFor(AppVersion.parse("4.0.0")!!))
        val list = "[" + listOf(
            release("v4.0.0"), release("v4.1.0-beta.2", prerelease = true), release("v4.1.0-beta.1", prerelease = true),
            """{"tag_name": "v9.0.0", "html_url": "https://github.com/x", "draft": true}""", release("nightly"),
        ).joinToString(",") + "]"
        val newest = UpdateCheck.parseFor(beta, list)!!
        assertEquals(AppVersion.parse("4.1.0-beta.2"), newest.version)
        assertTrue(UpdateCheck.isNewer(newest, beta))
        // La finale qui suit la beta est proposée aussi (elle s'installe en « Kairos », à côté de la Preview).
        assertEquals(AppVersion.parse("4.1.0"), UpdateCheck.parseNewest("[" + release("v4.1.0") + "," + release("v4.1.0-beta.2", prerelease = true) + "]")!!.version)
        assertNull(UpdateCheck.parseNewest("""{"message": "Not Found"}"""))
        assertNull(UpdateCheck.parseNewest("[]"))
    }

    @Test
    fun at_most_every_six_hours() {
        val t = Instant.parse("2026-09-29T08:00:00Z")
        assertTrue(UpdateCheck.due(null, t))
        assertFalse(UpdateCheck.due(t, Instant.parse("2026-09-29T13:59:00Z")))
        assertTrue(UpdateCheck.due(t, Instant.parse("2026-09-29T14:00:00Z")))
        // Horloge revenue en arrière : on revérifie plutôt que d'attendre des jours.
        assertTrue(UpdateCheck.due(t, Instant.parse("2026-09-28T08:00:00Z")))
    }
}
