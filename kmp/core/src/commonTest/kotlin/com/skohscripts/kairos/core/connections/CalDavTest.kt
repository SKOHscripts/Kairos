package com.skohscripts.kairos.core.connections

import com.skohscripts.kairos.core.calendar.CalendarImport
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Réponses CalDAV (docs/spec/connexions-en-ligne.md § CalDAV) : celles d'un Radicale 3.9 local capturées le 2026-11-21 (données de
 * test inventées, aucune donnée personnelle), et une réponse au style de Nextcloud (préfixes `d:`/`cal:`, `CDATA`, `&#13;`).
 */
class CalDavTest {
    private val principal = """<?xml version='1.0' encoding='utf-8'?>
<multistatus xmlns="DAV:"><response><href>/</href><propstat><prop><current-user-principal><href>/alice/</href></current-user-principal></prop><status>HTTP/1.1 200 OK</status></propstat></response></multistatus>"""

    private val home = """<?xml version='1.0' encoding='utf-8'?>
<multistatus xmlns="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav"><response><href>/alice/</href><propstat><prop><C:calendar-home-set><href>/alice/</href></C:calendar-home-set></prop><status>HTTP/1.1 200 OK</status></propstat></response></multistatus>"""

    private val calendars = """<?xml version='1.0' encoding='utf-8'?>
<multistatus xmlns="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav" xmlns:CR="urn:ietf:params:xml:ns:carddav"><response><href>/alice/</href><propstat><prop><resourcetype><principal /><collection /></resourcetype></prop><status>HTTP/1.1 200 OK</status></propstat><propstat><prop><displayname /><C:supported-calendar-component-set /></prop><status>HTTP/1.1 404 Not Found</status></propstat></response><response><href>/alice/perso/</href><propstat><prop><resourcetype><C:calendar /><collection /></resourcetype><displayname>Perso &amp; famille</displayname><C:supported-calendar-component-set><C:comp name="VTODO" /><C:comp name="VEVENT" /><C:comp name="VJOURNAL" /></C:supported-calendar-component-set></prop><status>HTTP/1.1 200 OK</status></propstat></response><response><href>/alice/contacts/</href><propstat><prop><resourcetype><CR:addressbook /><collection /></resourcetype><displayname>Contacts</displayname><C:supported-calendar-component-set /></prop><status>HTTP/1.1 200 OK</status></propstat></response><response><href>/alice/work/</href><propstat><prop><resourcetype><C:calendar /><collection /></resourcetype><displayname>Work</displayname><C:supported-calendar-component-set><C:comp name="VTODO" /><C:comp name="VEVENT" /><C:comp name="VJOURNAL" /></C:supported-calendar-component-set></prop><status>HTTP/1.1 200 OK</status></propstat></response></multistatus>"""

    private val report = """<?xml version='1.0' encoding='utf-8'?>
<multistatus xmlns="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav"><response><href>/alice/work/ev2.ics</href><propstat><prop><C:calendar-data>BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Test//Kairos//EN
BEGIN:VEVENT
UID:review-2@test
DTSTART:20261125T130000Z
DTEND:20261125T143000Z
DTSTAMP:20261101T080000Z
SUMMARY:Client review
END:VEVENT
END:VCALENDAR
</C:calendar-data></prop><status>HTTP/1.1 200 OK</status></propstat></response><response><href>/alice/work/ev1.ics</href><propstat><prop><C:calendar-data>BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Test//Kairos//EN
BEGIN:VTIMEZONE
TZID:Europe/Paris
BEGIN:STANDARD
DTSTART:19701025T030000
RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU
TZOFFSETFROM:+0200
TZOFFSETTO:+0100
END:STANDARD
BEGIN:DAYLIGHT
DTSTART:19700329T020000
RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU
TZOFFSETFROM:+0100
TZOFFSETTO:+0200
END:DAYLIGHT
END:VTIMEZONE
BEGIN:VEVENT
UID:standup-1@test
DTSTART;TZID=Europe/Paris:20261123T093000
DTEND;TZID=Europe/Paris:20261123T094500
DTSTAMP:20261101T080000Z
RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=12
SUMMARY:Stand-up
END:VEVENT
END:VCALENDAR
</C:calendar-data></prop><status>HTTP/1.1 200 OK</status></propstat></response></multistatus>"""

    // Nextcloud écrit ses objets en CRLF encodés (&#13;) et certains serveurs en CDATA ; une réponse 404 au milieu est ignorée.
    private val nextcloudReport = "<?xml version=\"1.0\"?><d:multistatus xmlns:d=\"DAV:\" xmlns:cal=\"urn:ietf:params:xml:ns:caldav\">" +
        "<d:response><d:href>/remote.php/dav/calendars/u/personal/a.ics</d:href><d:propstat><d:prop><cal:calendar-data>" +
        "BEGIN:VCALENDAR&#13;\nVERSION:2.0&#13;\nBEGIN:VTIMEZONE&#13;\nTZID:Europe/Paris&#13;\nEND:VTIMEZONE&#13;\nBEGIN:VEVENT&#13;\nUID:a@x&#13;\n" +
        "DTSTART:20261126T080000Z&#13;\nDTEND:20261126T090000Z&#13;\nSUMMARY:Dentist&#13;\nEND:VEVENT&#13;\nEND:VCALENDAR&#13;\n" +
        "</cal:calendar-data></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>" +
        "<d:response><d:href>/remote.php/dav/calendars/u/personal/gone.ics</d:href><d:status>HTTP/1.1 404 Not Found</d:status></d:response>" +
        "<d:response><d:href>/remote.php/dav/calendars/u/personal/b.ics</d:href><d:propstat><d:prop><cal:calendar-data><![CDATA[BEGIN:VCALENDAR\n" +
        "VERSION:2.0\nBEGIN:VTIMEZONE\nTZID:Europe/Paris\nEND:VTIMEZONE\nBEGIN:VEVENT\nUID:b@x\nDTSTART:20261127T080000Z\nDTEND:20261127T090000Z\n" +
        "SUMMARY:Gym\nEND:VEVENT\nEND:VCALENDAR\n]]></cal:calendar-data></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>" +
        "</d:multistatus>"

    @Test
    fun discoveryReadsPrincipalHomeAndOnlyEventCalendars() {
        assertEquals("/alice/", CalDav.principal(principal))
        assertEquals("/alice/", CalDav.homeSet(home))
        assertEquals(listOf(RemoteCalendar("/alice/perso/", "Perso & famille"), RemoteCalendar("/alice/work/", "Work")), CalDav.calendars(calendars))
    }

    @Test
    fun aCalendarWithoutEventsOrWithoutNameIsHandled() {
        val xml = """<multistatus xmlns="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
            <response><href>/u/tasks/</href><propstat><prop><resourcetype><collection/><C:calendar/></resourcetype><C:supported-calendar-component-set><C:comp name="VTODO"/></C:supported-calendar-component-set></prop><status>HTTP/1.1 200 OK</status></propstat></response>
            <response><href>/u/R%C3%A9unions/</href><propstat><prop><resourcetype><collection/><C:calendar/></resourcetype></prop><status>HTTP/1.1 200 OK</status></propstat></response>
            </multistatus>"""
        assertEquals(listOf(RemoteCalendar("/u/R%C3%A9unions/", "Réunions")), CalDav.calendars(xml))
    }

    @Test
    fun anUnauthenticatedPrincipalIsNull() {
        assertNull(CalDav.principal("<multistatus xmlns=\"DAV:\"><response><href>/</href><propstat><prop><current-user-principal><unauthenticated/></current-user-principal></prop><status>HTTP/1.1 200 OK</status></propstat></response></multistatus>"))
    }

    @Test
    fun somethingElseThanAMultistatusIsRefused() {
        assertFailsWith<XmlException> { CalDav.parseMultistatus("<html><body>Login</body></html>") }
        assertTrue(assertFailsWith<XmlException> { CalDav.calendarData(report.dropLast(40)) }.truncated)
    }

    @Test
    fun reportObjectsAreMergedAndReadLikeAFile() {
        val merged = CalDavMerge.merge(CalDav.calendarData(report))
        val plan = CalendarImport.plan(merged, LocalDate(2026, 11, 21), TimeZone.of("Europe/Paris"))
        // Le stand-up hebdomadaire (lundi, mercredi, vendredi, 12 fois) et la revue : Kairos déploie lui-même la répétition.
        assertEquals(13, plan.events.size)
        assertTrue(plan.events.any { it.title == "Client review" })
        assertEquals(1, Regex("BEGIN:VTIMEZONE").findAll(merged).count())
    }

    @Test
    fun nextcloudStyleObjectsKeepOneTimezoneAndSkipFailedResponses() {
        val data = CalDav.calendarData(nextcloudReport)
        assertEquals(2, data.size)
        val merged = CalDavMerge.merge(data)
        assertEquals(1, Regex("BEGIN:VTIMEZONE").findAll(merged).count())
        val plan = CalendarImport.plan(merged, LocalDate(2026, 11, 21), TimeZone.of("Europe/Paris"))
        assertEquals(setOf("Dentist", "Gym"), plan.events.map { it.title }.toSet())
    }

    @Test
    fun noObjectGivesAnEmptyCalendar() {
        val plan = CalendarImport.plan(CalDavMerge.merge(emptyList()), LocalDate(2026, 11, 21), TimeZone.of("Europe/Paris"))
        assertTrue(plan.isEmpty)
    }

    @Test
    fun theQueryCoversYesterdayToSixMonths() {
        val body = CalDavQueries.calendarQuery(LocalDate(2026, 11, 21))
        assertTrue("start=\"20261120T000000Z\"" in body && "end=\"20270521T000000Z\"" in body, body)
        assertTrue("expand" !in body)
    }

    @Test
    fun percentDecodingKeepsInvalidSequences() {
        assertEquals("Réunions 100%", CalDav.percentDecode("R%C3%A9unions%20100%"))
    }
}
