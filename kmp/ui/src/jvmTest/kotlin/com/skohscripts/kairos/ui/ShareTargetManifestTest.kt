package com.skohscripts.kairos.ui

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cible de partage Android (docs/spec/partage-android.md, issue #293) : une activité dédiée, exportée, avec un filtre `SEND` sur
 * `text/plain`, et rien qui fasse de `MainActivity` une cible de partage. Lu depuis `androidApp`, que ce module ne compile pas
 * (le test tourne sur la JVM, sans SDK Android), comme [AndroidStringsParityTest].
 */
class ShareTargetManifestTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("../androidApp/src/main/AndroidManifest.xml")).documentElement

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun Element.attr(name: String): String = getAttributeNS(android, name)

    private fun activity(name: String): Element =
        manifest.children("application").single().children("activity").single { it.attr("name") == name }

    /** Les filtres d'une activité : (actions, types MIME). */
    private fun filters(activity: Element): List<Pair<List<String>, List<String>>> = activity.children("intent-filter").map { f ->
        f.children("action").map { it.attr("name") } to f.children("data").map { it.attr("mimeType") }
    }

    @Test
    fun theShareActivityAcceptsPlainTextOnly() {
        val share = activity(".ShareNoteActivity")
        assertEquals("true", share.attr("exported"))
        assertEquals("true", share.attr("noHistory"))
        assertEquals("true", share.attr("excludeFromRecents"))
        assertEquals("", share.attr("taskAffinity"))
        assertEquals(listOf(listOf("android.intent.action.SEND") to listOf("text/plain")), filters(share))
    }

    @Test
    fun theMainActivityIsNoShareTarget() {
        val actions = filters(activity(".MainActivity")).flatMap { it.first }
        assertTrue(actions.none { it.startsWith("android.intent.action.SEND") }, "$actions")
    }

    @Test
    fun sharingAddsNoPermission() {
        val permissions = manifest.children("uses-permission").map { it.attr("name").substringAfterLast('.') }.toSet()
        assertEquals(
            setOf("POST_NOTIFICATIONS", "RECEIVE_BOOT_COMPLETED", "SCHEDULE_EXACT_ALARM", "READ_CALENDAR", "INTERNET", "ACCESS_NETWORK_STATE"),
            permissions,
        )
    }
}
