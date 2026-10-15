package com.skohscripts.kairos.ui.guide

import com.skohscripts.kairos.ui.navigation.TeamDestination
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Catalogue des fonctionnalités (docs/spec/visite-guidee.md). Les chaînes elles-mêmes : `StringsParityTest`. */
class FeatureCatalogTest {
    private val cards = FeatureCatalog.cards

    /** Le README anglais, retrouvé en remontant depuis le dossier de test (le dépôt, pas le sous-projet `kmp`). */
    private fun readme(): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "README.md").isFile) dir = dir.parentFile
        return requireNotNull(dir) { "README.md introuvable" }.resolve("README.md").readText()
    }

    @Test
    fun idsAreUniqueAndEveryChapterHasCards() {
        assertEquals(cards.size, cards.map { it.id }.toSet().size)
        GuideChapter.entries.forEach { chapter -> assertTrue(FeatureCatalog.chapter(chapter).size >= 2, chapter.name) }
        // Titres et textes ne se partagent pas : aucune carte n'en cache une autre.
        assertEquals(cards.size, cards.map { it.title }.toSet().size)
        assertEquals(cards.size, cards.map { it.body }.toSet().size)
    }

    @Test
    fun theTeamChapterOnlyOpensTeamScreensAndOnlyWithTheTeamSpace() {
        assertTrue(FeatureCatalog.chapter(GuideChapter.TEAM).all { it.target is GuideTarget.Team })
        assertTrue(cards.filter { it.chapter != GuideChapter.TEAM }.none { it.target is GuideTarget.Team })
        assertTrue(GuideChapter.TEAM !in FeatureCatalog.chapters(teamEnabled = false))
        assertEquals(GuideChapter.entries, FeatureCatalog.chapters(teamEnabled = true))
        assertTrue(FeatureCatalog.chapter(GuideChapter.TEAM).all { (it.target as GuideTarget.Team).destination != TeamDestination.HOME })
    }

    @Test
    fun theTwoToursAreSelectionsOfTheCatalogue() {
        assertEquals(6, GuideSteps.of(GuideKind.PERSONAL).size)
        assertTrue(GuideSteps.of(GuideKind.PERSONAL).all { it in cards })
        assertEquals(FeatureCatalog.chapter(GuideChapter.TEAM), GuideSteps.of(GuideKind.TEAM))
    }

    @Test
    fun aVersionListsTheCardsItAddsAndKeepsThemForItsFixesAndPrereleases() {
        val added = setOf("discover", "first_task", "hours", "learned", "recurrence", "settings")
        assertEquals(added, FeatureCatalog.newIn("3.4.0").map { it.id }.toSet())
        assertEquals(added, FeatureCatalog.newIn("3.4.1").map { it.id }.toSet())
        assertEquals(added, FeatureCatalog.newIn("3.4.0-beta.2").map { it.id }.toSet())
        assertEquals(setOf("contexts", "besthours"), FeatureCatalog.newIn("3.5.0").map { it.id }.toSet())
        // Les versions qui n'ajoutent rien (ou illisibles) n'affichent rien.
        assertEquals(emptyList(), FeatureCatalog.newIn("3.3.0"))
        assertEquals(emptyList(), FeatureCatalog.newIn("garbage"))
    }

    @Test
    fun readmeFeaturesAreCovered() {
        val section = readme().substringAfter("\n## Features").substringBefore("\n## Settings")
        val listed = Regex("(?m)^- \\*\\*(.+?)\\*\\*").findAll(section).map { it.groupValues[1] }.toList()
        assertTrue(listed.isNotEmpty(), "aucune fonctionnalité lue dans le README")
        val known = FeatureCatalog.README_FEATURES
        // Chaque fonctionnalité du README a sa carte, et la table ne garde rien qui n'y soit plus.
        assertEquals(emptyList(), listed.filter { it !in known }, "fonctionnalités du README sans carte : ajoute-les à FeatureCatalog.README_FEATURES")
        assertEquals(emptyList(), known.keys.filter { it !in listed }, "entrées de README_FEATURES absentes du README")
        val ids = cards.map { it.id }.toSet()
        assertEquals(emptyList(), known.values.filter { it !in ids })
    }
}
