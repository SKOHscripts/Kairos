package com.skohscripts.kairos.ui.guide

import com.skohscripts.kairos.core.text.SearchFold
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Catalogue des fonctionnalités (docs/spec/visite-guidee.md). Les chaînes elles-mêmes : `StringsParityTest`. */
class FeatureCatalogTest {
    private val cards = FeatureCatalog.cards

    /** Un fichier du dépôt, retrouvé en remontant depuis le dossier de test (le dépôt, pas le sous-projet `kmp`). */
    private fun repoFile(path: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "README.md").isFile) dir = dir.parentFile
        return requireNotNull(dir) { "README.md introuvable" }.resolve(path)
    }

    private fun readme(): String = repoFile("README.md").readText()

    /** Chaînes d'un dossier de ressources Compose (`values`, `values-fr`), entités XML décodées. */
    private fun strings(folder: String): Map<String, String> =
        Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(File("src/commonMain/composeResources/$folder/strings.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2].replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">") }

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
        assertEquals(FeatureCatalog.chapter(GuideChapter.TEAM).filter { it.tour }, GuideSteps.of(GuideKind.TEAM))
        assertEquals(6, GuideSteps.of(GuideKind.TEAM).size)
    }

    @Test
    fun aVersionListsTheCardsItAddsAndKeepsThemForItsFixesAndPrereleases() {
        val added = setOf("discover", "first_task", "hours", "learned", "settings")
        assertEquals(added, FeatureCatalog.newIn("3.4.0").map { it.id }.toSet())
        assertEquals(added, FeatureCatalog.newIn("3.4.1").map { it.id }.toSet())
        assertEquals(added, FeatureCatalog.newIn("3.4.0-beta.2").map { it.id }.toSet())
        assertEquals(setOf("contexts", "besthours"), FeatureCatalog.newIn("3.5.0").map { it.id }.toSet())
        assertEquals(setOf("besthours_apply"), FeatureCatalog.newIn("3.6.0").map { it.id }.toSet())
        assertEquals(
            setOf("guide_memory", "week_move", "stats_explore", "holidays_slots", "settings_categories", "darktheme"),
            FeatureCatalog.newIn("3.7.0").map { it.id }.toSet(),
        )
        assertEquals(
            setOf("slots_month", "month", "stats_trends", "stats_correct", "reminders", "team_first_task"),
            FeatureCatalog.newIn("3.8.0").map { it.id }.toSet(),
        )
        assertEquals(setOf("level_names", "besthours_efficiency", "guided_walks"), FeatureCatalog.newIn("3.9.0").map { it.id }.toSet())
        assertEquals(setOf("calendar_import", "phone_calendars", "linked_file"), FeatureCatalog.newIn("3.10.0").map { it.id }.toSet())
        assertEquals(setOf("languages"), FeatureCatalog.newIn("3.11.0").map { it.id }.toSet())
        assertEquals(setOf("widgets", "daily_summary"), FeatureCatalog.newIn("3.12.0").map { it.id }.toSet())
        assertEquals(setOf("skipped_months", "demo"), FeatureCatalog.newIn("3.13.0").map { it.id }.toSet())
        assertEquals(setOf("subtasks_edit", "team_my_work"), FeatureCatalog.newIn("3.3.0").map { it.id }.toSet())
        assertEquals(
            setOf("team_members", "team_backlog", "team_assign", "team_board", "team_load", "team_forecast", "team_exchanges", "theme_color"),
            FeatureCatalog.newIn("3.1.0").map { it.id }.toSet(),
        )
        // Les versions qui n'ajoutent rien (ou illisibles) n'affichent rien.
        assertEquals(emptyList(), FeatureCatalog.newIn("3.2.0"))
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

    @Test
    fun oneCardPerReadmeFeatureAndOneReadmeFeaturePerCard() {
        // Liste des fonctionnalités (docs/spec/liste-fonctionnalites.md) : le README, le catalogue et la liste disent la même chose.
        val known = FeatureCatalog.README_FEATURES
        assertEquals(known.size, known.values.toSet().size, "deux fonctionnalités du README sur la même carte")
        assertEquals(emptyList(), cards.map { it.id }.filter { it !in known.values }, "cartes sans fonctionnalité dans le README")
    }

    @Test
    fun readmeTableMatchesCatalog() {
        val platforms = mapOf("values" to listOf("All", "Android", "Desktop", "Web"), "values-fr" to listOf("Toutes", "Android", "Bureau", "Web"))
        for ((file, section, folder) in listOf(
            Triple("README.md", "## Feature list", "values"),
            Triple("README.fr.md", "## Liste des fonctionnalités", "values-fr"),
        )) {
            val strings = strings(folder)
            val names = platforms.getValue(folder)
            val expected = cards.map { card ->
                val where = if (!card.limited) {
                    names[0]
                } else {
                    listOf(Platform.ANDROID to names[1], Platform.DESKTOP to names[2], Platform.WEB to names[3])
                        .filter { it.first in card.platforms }.joinToString(", ") { it.second }
                }
                listOf(strings.getValue(card.chapter.title.key), strings.getValue(card.title.key), card.since, where)
            }
            val text = repoFile(file).readText().substringAfter("\n$section\n").substringBefore("\n## ")
            val rows = text.lines().filter { it.startsWith("| ") }.drop(1)
                .map { line -> line.trim('|').split('|').map { it.trim() } }
            assertEquals(expected.map { row -> row.map(SearchFold::fold) }, rows.map { row -> row.map(SearchFold::fold) }, file)
        }
    }

    @Test
    fun noCardIsNewerThanTheApp() {
        // Une carte annonce une version déjà publiée ou celle en préparation, jamais une future. Le passage de version vient
        // en dernière PR d'un cycle (docs/spec/distribution.md) : la version en préparation est la mineure suivante de l'app.
        fun minor(version: String): Int = version.substringBefore('-').split('.').let { it[0].toInt() * 100 + it[1].toInt() }
        val app = repoFile("kmp/gradle.properties").readLines().first { it.startsWith("kairos.versionName=") }.substringAfter('=')
        assertEquals(emptyList(), cards.filter { minor(it.since) > minor(app) + 1 }.map { it.id }, "version de l'application : $app")
    }

    @Test
    fun theVersionFilterGoesFromNewestToOldestAndKeepsLaterCards() {
        val versions = FeatureCatalog.versions()
        assertEquals("3.0.0", versions.last())
        assertEquals(versions.distinct(), versions)
        assertTrue(versions.all { it.endsWith(".0") })
        assertTrue("3.10.0" in versions && versions.indexOf("3.10.0") < versions.indexOf("3.9.0"))
        val since = FeatureCatalog.since("3.10.0").map { it.id }
        assertTrue("languages" in since && "calendar_import" in since && "linked_file" in since)
        assertTrue("month" !in since && "capture" !in since)
        assertEquals(cards, FeatureCatalog.since("3.0.0"))
        assertEquals(cards, FeatureCatalog.since("garbage"))
    }

    @Test
    fun sectionsBelongToTheScreenTheyOpen() {
        // Une section citée par une carte est sur l'écran que la carte ouvre (docs/spec/visite-guidee.md, issue #95).
        cards.forEach { card ->
            when (val target = card.target) {
                is GuideTarget.Personal -> target.section?.let { section ->
                    val screen = when (target.destination) {
                        Destination.DAY -> GuideScreen.DAY
                        Destination.STATS -> GuideScreen.STATS
                        else -> null
                    }
                    assertEquals(screen, section.screen, card.id)
                }
                is GuideTarget.Settings -> target.section?.let { assertEquals(GuideScreen.SETTINGS, it.screen, card.id) }
                else -> Unit
            }
        }
        // Une section des Réglages ouvre sa catégorie ; les autres n'en ont pas.
        GuideSection.entries.forEach { assertEquals(it.screen == GuideScreen.SETTINGS, it.settingsCategory != null, it.name) }
        // Chaque section sert au moins une carte, ou la visite des Réglages (issue #98).
        val used = cards.mapNotNull { (it.target as? GuideTarget.Personal)?.section ?: (it.target as? GuideTarget.Settings)?.section }.toSet() +
            SettingsTour.STEPS.mapNotNull { it.section }
        assertEquals(GuideSection.entries.toSet(), used)
    }

    @Test
    fun theNewsAreHiddenPerMajorAndMinorVersion() {
        // « Masquer » vaut pour la version majeure.mineure, la même règle que newIn (issue #94).
        assertEquals("3.7", FeatureCatalog.newsKey("3.7.0"))
        assertEquals("3.7", FeatureCatalog.newsKey("3.7.1"))
        assertEquals("3.7", FeatureCatalog.newsKey("3.7.0-beta.2"))
        assertEquals(null, FeatureCatalog.newsKey("dev"))
    }
}
