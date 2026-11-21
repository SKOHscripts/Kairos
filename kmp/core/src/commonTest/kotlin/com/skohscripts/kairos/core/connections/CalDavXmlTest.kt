package com.skohscripts.kairos.core.connections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Lecteur XML des réponses WebDAV (docs/spec/connexions-en-ligne.md § CalDAV). */
class CalDavXmlTest {
    @Test
    fun namespacesAreResolvedWhateverThePrefix() {
        val radicale = CalDavXml.parse("<?xml version='1.0'?><multistatus xmlns=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\"><response><C:x/></response></multistatus>")
        val nextcloud = CalDavXml.parse("<d:multistatus xmlns:d=\"DAV:\" xmlns:cal=\"urn:ietf:params:xml:ns:caldav\"><d:response><cal:x/></d:response></d:multistatus>")
        for (root in listOf(radicale, nextcloud)) {
            assertEquals("DAV:" to "multistatus", root.ns to root.name)
            assertTrue(root.child("DAV:", "response")!!.has(CalDav.CALDAV, "x"))
        }
    }

    @Test
    fun textEntitiesCdataAndCharacterReferences() {
        val root = CalDavXml.parse("<a xmlns=\"DAV:\"><b>Perso &amp; famille &lt;3&#13;&#x41;</b><c><![CDATA[BEGIN:VCALENDAR <raw> & co]]></c><d attr='q &quot;x&quot;'/></a>")
        assertEquals("Perso & famille <3\rA", root.child("DAV:", "b")!!.text)
        assertEquals("BEGIN:VCALENDAR <raw> & co", root.child("DAV:", "c")!!.text)
        assertEquals("q \"x\"", root.child("DAV:", "d")!!.attributes["attr"])
    }

    @Test
    fun lineEndingsAreNormalized() {
        assertEquals("a\nb\nc", CalDavXml.parse("<a>a\r\nb\rc</a>").text)
    }

    @Test
    fun doctypeAndDeclaredEntitiesAreRefused() {
        assertFailsWith<XmlException> { CalDavXml.parse("<!DOCTYPE a [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><a>&x;</a>") }
        assertFailsWith<XmlException> { CalDavXml.parse("<a>&x;</a>") }
        assertFailsWith<XmlException> { CalDavXml.parse("<a><!ENTITY y \"z\"></a>") }
    }

    @Test
    fun depthIsBounded() {
        val deep = "<a>".repeat(CalDavXml.MAX_DEPTH + 1) + "</a>".repeat(CalDavXml.MAX_DEPTH + 1)
        assertFailsWith<XmlException> { CalDavXml.parse(deep) }
        val ok = "<a>".repeat(CalDavXml.MAX_DEPTH) + "</a>".repeat(CalDavXml.MAX_DEPTH)
        CalDavXml.parse(ok)
    }

    @Test
    fun truncatedDocumentsSayTheyAreTruncated() {
        val error = assertFailsWith<XmlException> { CalDavXml.parse("<multistatus xmlns=\"DAV:\"><response><href>/a/") }
        assertTrue(error.truncated)
        assertTrue(!assertFailsWith<XmlException> { CalDavXml.parse("<a></b>") }.truncated)
    }

    @Test
    fun unboundPrefixIsRefused() {
        assertFailsWith<XmlException> { CalDavXml.parse("<x:a/>") }
    }
}
