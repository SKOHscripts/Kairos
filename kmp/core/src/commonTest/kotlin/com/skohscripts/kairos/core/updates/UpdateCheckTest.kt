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
    fun at_most_every_six_hours() {
        val t = Instant.parse("2026-09-29T08:00:00Z")
        assertTrue(UpdateCheck.due(null, t))
        assertFalse(UpdateCheck.due(t, Instant.parse("2026-09-29T13:59:00Z")))
        assertTrue(UpdateCheck.due(t, Instant.parse("2026-09-29T14:00:00Z")))
        // Horloge revenue en arrière : on revérifie plutôt que d'attendre des jours.
        assertTrue(UpdateCheck.due(t, Instant.parse("2026-09-28T08:00:00Z")))
    }
}
