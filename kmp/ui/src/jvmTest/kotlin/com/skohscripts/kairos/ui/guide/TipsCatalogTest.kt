package com.skohscripts.kairos.ui.guide

import com.skohscripts.kairos.core.guide.TipId
import com.skohscripts.kairos.core.guide.TipPlatform
import com.skohscripts.kairos.core.guide.TipSpace
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.navigation.Destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Concordance des conseils avec le catalogue et le README (docs/spec/conseils.md § Concordance, demande du propriétaire du
 * 2026-11-15) : chaque conseil désigne une carte qui existe, dont les plateformes couvrent les siennes et qui mène au même
 * écran. Supprimer ou déplacer une carte casse donc ce test pour le conseil qui la cite ; la carte `tips` elle-même est reliée
 * au README par `FeatureCatalogTest`.
 */
class TipsCatalogTest {
    private val cards = FeatureCatalog.cards.associateBy { it.id }

    /** L'écran qu'une cible ouvre : destination de l'espace, catégorie des Réglages, carte unique ou accompagnement. */
    private fun screenOf(target: GuideTarget): String = when (target) {
        is GuideTarget.Personal -> "personal:${target.destination}"
        is GuideTarget.Team -> "team:${target.destination}"
        is GuideTarget.Settings -> "settings:${target.section?.settingsCategory}"
        is GuideTarget.Card -> "card:${target.id}"
        // « Créer ma première tâche récurrente » se passe sur la vue Jour, comme la carte « Récurrence ».
        GuideTarget.RecurringTask -> "personal:${Destination.DAY}"
        GuideTarget.FirstTask -> "personal:${Destination.DAY}"
        GuideTarget.Demo -> "demo"
        GuideTarget.Feedback -> "feedback"
    }

    @Test
    fun everyTipNamesACardThatExists() {
        TipId.entries.forEach { assertNotNull(cards[it.card], "${it.key} → carte ${it.card} absente du catalogue") }
    }

    @Test
    fun aTipLeadsToTheScreenOfItsCard() {
        TipId.entries.forEach { id ->
            val target = TipContent.target(id)
            val card = cards.getValue(id.card)
            // Une carte unique ouvre la carte elle-même : c'est le même sujet par construction.
            if (target is GuideTarget.Card) {
                assertEquals(id.card, target.id, id.key)
            } else {
                assertEquals(screenOf(card.target), screenOf(target), id.key)
            }
        }
    }

    @Test
    fun theCardExistsWhereverTheTipDoes() {
        val platformOf = mapOf(TipPlatform.ANDROID to Platform.ANDROID, TipPlatform.DESKTOP to Platform.DESKTOP, TipPlatform.WEB to Platform.WEB)
        TipId.entries.forEach { id ->
            val card = cards.getValue(id.card)
            id.platforms.forEach { p -> assertTrue(platformOf.getValue(p) in card.platforms, "${id.key} sur $p, carte ${card.id} absente") }
        }
    }

    @Test
    fun teamTipsNameTeamCardsAndPersonalTipsTheOthers() {
        TipId.entries.forEach { id ->
            val team = cards.getValue(id.card).chapter == GuideChapter.TEAM
            assertEquals(id.space == TipSpace.TEAM, team, id.key)
            assertEquals(id.space == TipSpace.TEAM, TipContent.target(id) is GuideTarget.Team, id.key)
        }
    }

    @Test
    fun theTipsCardIsInTheCatalogAndTheReadme() {
        val card = cards.getValue("tips")
        assertEquals("4.2.0", card.since)
        assertEquals("tips", FeatureCatalog.README_FEATURES["Tips at the right time"])
    }
}
