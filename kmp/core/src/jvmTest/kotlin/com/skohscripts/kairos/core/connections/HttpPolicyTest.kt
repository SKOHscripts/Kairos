package com.skohscripts.kairos.core.connections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class HttpPolicyTest {
    private fun check(url: String, kind: ConnectionKind = ConnectionKind.URL, credentials: Boolean = false, confirmed: Boolean = false) =
        HttpPolicy.check(url, kind, credentials, confirmed)

    private fun redirect(from: String, location: String, kind: ConnectionKind = ConnectionKind.URL, followed: Int = 0, accepted: String? = null) =
        HttpPolicy.redirect(from, location, kind, followed, accepted)

    @Test
    fun httpsAndWebcalNormalizeTheSchemeHostAndDefaultPort() {
        assertEquals(HttpPolicy.Check.Ok("https://example.com/calendar?key=secret", "example.com", false), check("HTTPS://EXAMPLE.COM:443/calendar?key=secret"))
        assertEquals(HttpPolicy.Check.Ok("https://example.com/calendar", "example.com", false), check("webcal://example.com/calendar"))
        assertEquals(HttpPolicy.Check.Ok("https://example.com", "example.com", false), check("https://example.com"))
        assertEquals(HttpPolicy.Check.Ok("https://example.com/calendar", "example.com", false), check("https://example.com/calendar", ConnectionKind.CALDAV, credentials = true))
    }

    @Test
    fun httpNeedsAnAnonymousConfirmedIcsLink() {
        assertEquals(HttpPolicy.Check.Refused(HttpPolicy.Reason.INSECURE_NEEDS_CONFIRMATION), check("http://example.com/calendar"))
        assertEquals(HttpPolicy.Check.Ok("http://example.com/calendar", "example.com", true), check("http://example.com:80/calendar", confirmed = true))
        assertEquals(HttpPolicy.Check.Refused(HttpPolicy.Reason.INSECURE_NOT_ALLOWED), check("http://example.com/calendar", credentials = true, confirmed = true))
        for (kind in listOf(ConnectionKind.FILE, ConnectionKind.CALDAV, ConnectionKind.OAUTH)) {
            assertEquals(HttpPolicy.Check.Refused(HttpPolicy.Reason.INSECURE_NOT_ALLOWED), check("http://example.com/calendar", kind, confirmed = true))
        }
    }

    @Test
    fun credentialsInAnAddressAreAlwaysRefused() {
        for (url in listOf("https://user:pass@example.com/", "https://user@example.com/", "https://@example.com/")) {
            assertEquals(HttpPolicy.Check.Refused(HttpPolicy.Reason.CREDENTIALS_IN_URL), check(url))
        }
    }

    @Test
    fun malformedHostsAndUnsupportedSchemesAreRefused() {
        for (url in listOf("", "example.com/calendar", "https:///calendar", "https://", "https://:443/", "https://a b/", "https://example.com\\@other.test/", "https://example.com/\nsecret", "https://%65xample.com/", "https://[::1]other/")) {
            assertEquals(HttpPolicy.Check.Refused(HttpPolicy.Reason.BAD_URL), check(url), url)
        }
        assertEquals(HttpPolicy.Check.Refused(HttpPolicy.Reason.UNSUPPORTED_SCHEME), check("ftp://example.com/calendar"))
    }

    @Test
    fun portsMustBeDecimalAndWithinBounds() {
        for (port in listOf("0", "65536", "-1", "+80", "abc", "", "999999999999999999999999")) {
            assertEquals(HttpPolicy.Check.Refused(HttpPolicy.Reason.BAD_PORT), check("https://example.com:$port/"), port)
        }
        for (port in listOf(1, 65535)) {
            assertEquals(HttpPolicy.Check.Ok("https://example.com:$port/", "example.com:$port", false), check("https://example.com:$port/"))
        }
        assertEquals("[::1]:8443", HttpPolicy.host("https://[::1]:8443/calendar"))
        assertEquals("[2001:db8::1]", HttpPolicy.host("https://[2001:db8::1]:443/calendar"))
    }

    @Test
    fun sameHostAndSecureUpgradeRedirectsAreFollowed() {
        assertEquals(HttpPolicy.Redirect.Follow("https://example.com/next"), redirect("https://example.com/old", "https://EXAMPLE.COM:443/next"))
        assertEquals(HttpPolicy.Redirect.Follow("https://example.com/next"), redirect("http://example.com/old", "https://example.com/next"))
        for (kind in listOf(ConnectionKind.CALDAV, ConnectionKind.OAUTH)) {
            assertEquals(HttpPolicy.Redirect.Follow("https://example.com/next"), redirect("https://example.com/old", "/next", kind))
        }
    }

    @Test
    fun crossHostRedirectsRequireIcsConsentAndOtherKindsRefuse() {
        assertEquals(HttpPolicy.Redirect.AskUser("other.test:8443"), redirect("https://example.com/old", "https://other.test:8443/next"))
        assertEquals(HttpPolicy.Redirect.Follow("https://other.test/next"), redirect("https://example.com/old", "https://other.test/next", accepted = "other.test"))
        assertEquals(HttpPolicy.Redirect.AskUser("third.test"), redirect("https://example.com/old", "https://third.test/next", accepted = "other.test"))
        for (kind in listOf(ConnectionKind.CALDAV, ConnectionKind.OAUTH)) {
            assertEquals(HttpPolicy.Redirect.Refuse(ConnectionError.REDIRECT_HOST), redirect("https://example.com/old", "https://other.test/next", kind, accepted = "other.test"))
        }
        assertEquals(HttpPolicy.Redirect.AskUser("example.com:8443"), redirect("https://example.com/old", "https://example.com:8443/next"))
    }

    @Test
    fun secureDowngradesAreNeverFollowedEvenWithConsent() {
        assertEquals(HttpPolicy.Redirect.Refuse(ConnectionError.INSECURE_REDIRECT), redirect("https://example.com/old", "http://example.com/next"))
        assertEquals(HttpPolicy.Redirect.Refuse(ConnectionError.INSECURE_REDIRECT), redirect("https://example.com/old", "http://other.test/next", accepted = "other.test"))
    }

    @Test
    fun noMoreThanFiveRedirectsAreFollowed() {
        for (followed in 0 until HttpPolicy.MAX_REDIRECTS) {
            assertEquals(HttpPolicy.Redirect.Follow("https://example.com/next"), redirect("https://example.com/old", "/next", followed = followed))
        }
        for (followed in listOf(5, 6)) {
            assertEquals(HttpPolicy.Redirect.Refuse(ConnectionError.UNAVAILABLE), redirect("https://example.com/old", "/next", followed = followed))
        }
    }

    @Test
    fun relativeLocationsResolvePathsQueriesFragmentsAndAuthorities() {
        val from = "https://example.com/calendars/work/current.ics?old=1"
        for ((location, expected) in listOf(
            "next.ics" to "https://example.com/calendars/work/next.ics",
            "../next.ics?new=1" to "https://example.com/calendars/next.ics?new=1",
            "./next/../final.ics" to "https://example.com/calendars/work/final.ics",
            "/root.ics" to "https://example.com/root.ics",
            "../../../root.ics" to "https://example.com/root.ics",
            "?new=1" to "https://example.com/calendars/work/current.ics?new=1",
            "#fragment" to "https://example.com/calendars/work/current.ics?old=1#fragment",
            "" to from,
        )) {
            assertEquals(HttpPolicy.Redirect.Follow(expected), redirect(from, location), location)
        }
        assertEquals(HttpPolicy.Redirect.AskUser("other.test"), redirect(from, "//other.test/calendar"))
        assertEquals(HttpPolicy.Redirect.Follow("https://example.com/next"), redirect("https://example.com", "next"))
    }

    @Test
    fun malformedRedirectsFailClosed() {
        for (location in listOf("https://user:pass@example.com/", "https:///next", "https://example.com:0/", "javascript:alert(1)", "\\other.test/path", "/next\r\nHeader: injected")) {
            assertEquals(HttpPolicy.Redirect.Refuse(ConnectionError.UNAVAILABLE), redirect("https://example.com/old", location), location)
        }
    }

    @Test
    fun logsContainOnlyTheHostAndRequestMetrics() {
        val line = HttpPolicy.logLine("GET", "https://user:password@example.com:8443/private/calendar?token=secret#fragment", 200, 123, 456)
        assertEquals("example.com:8443 GET 200 123ms 456B", line)
        for (secret in listOf("user", "password", "private", "calendar", "token", "secret", "fragment", "https://")) {
            assertFalse(secret in line, secret)
        }
        assertEquals("example.com GET - 12ms -", HttpPolicy.logLine("GET", "https://example.com/private", null, 12, null))
        assertEquals("- GET - 0ms -", HttpPolicy.logLine("GET", "invalid/private?secret", null, 0, null))
        assertNull(HttpPolicy.host("invalid/private?secret"))
    }
}
