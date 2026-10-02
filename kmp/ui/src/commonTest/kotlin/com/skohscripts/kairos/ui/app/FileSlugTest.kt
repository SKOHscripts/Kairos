package com.skohscripts.kairos.ui.app

import kotlin.test.Test
import kotlin.test.assertEquals

/** Noms de fichiers des paquets et des rapports (docs/spec/equipe-echanges.md) : `kairos-paquet-<membre>-AAAAMMJJ-HHMM.json`. */
class FileSlugTest {
    @Test
    fun keepsLettersAndDigitsInLowerCase() {
        assertEquals("léa-martin", fileSlug("Léa Martin"))
        assertEquals("alex-2", fileSlug("Alex 2"))
    }

    @Test
    fun collapsesSeparatorsAndTrimsThem() {
        assertEquals("jean-pierre", fileSlug("  Jean -- Pierre / "))
    }

    @Test
    fun fallsBackWhenNothingRemains() {
        assertEquals("membre", fileSlug(""))
        assertEquals("membre", fileSlug(" /?! "))
    }
}
