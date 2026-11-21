package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.Settings
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Pays des fériés dans les réglages (docs/spec/jours-feries.md) : migration de `holidaysFr`, défaut, formulaire. */
class HolidayCountrySettingsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun anOlderBaseKeepsItsBehaviour() {
        assertEquals("FR", Settings(holidaysFr = true).holidayCountryCode)
        assertNull(Settings(holidaysFr = false).holidayCountryCode)
        // Un export d'avant la 4.4.0 n'a que `holidaysFr`.
        val old = json.decodeFromString(Settings.serializer(), """{"holidaysFr":false}""")
        assertNull(old.holidayCountry)
        assertNull(old.holidayCountryCode)
    }

    @Test fun anExplicitCountryWinsAndAnUnknownOneMeansNone() {
        assertEquals("DE", Settings(holidaysFr = true, holidayCountry = "DE").holidayCountryCode)
        assertNull(Settings(holidaysFr = true, holidayCountry = "").holidayCountryCode)
        assertNull(Settings(holidayCountry = "XX").holidayCountryCode)
    }

    @Test fun defaultsFollowTheRegion() {
        assertEquals("DE", Settings.defaults("de", "DE").holidayCountryCode)
        assertEquals(false, Settings.defaults("de", "DE").holidaysFr)
        assertNull(Settings.defaults("ja", "JP").holidayCountryCode)
        assertNull(Settings.defaults("fr", "").holidayCountryCode)
        assertEquals(true, Settings.defaults("fr", "FR").holidaysFr)
        // Sans région (démonstration, tests) : la France, comme avant.
        assertEquals("FR", Settings.defaults("fr").holidayCountryCode)
    }

    @Test fun theFormWritesTheCountryAndKeepsHolidaysFrForOlderVersions() {
        val legacy = Settings(holidaysFr = true)
        assertEquals("FR", SettingsForm.values(legacy)["holidayCountry"])
        // Inchangé : rien n'est écrit.
        assertSame(legacy, SettingsForm.field("holidayCountry").write(legacy, "FR"))
        val german = SettingsForm.validate(mapOf("holidayCountry" to "DE"), legacy).settings!!
        assertEquals("DE", german.holidayCountry)
        assertEquals(false, german.holidaysFr)
        val none = SettingsForm.validate(mapOf("holidayCountry" to ""), legacy).settings!!
        assertEquals("", none.holidayCountry)
        assertEquals(false, none.holidaysFr)
        // Une valeur inconnue (import) devient « aucun », jamais une erreur.
        assertNull(SettingsForm.validate(mapOf("holidayCountry" to "Narnia"), legacy).settings!!.holidayCountryCode)
        assertTrue(SettingsForm.field("holidayCountry").choices.containsAll(listOf("", "FR", "DE", "US")))
    }

    @Test fun restoringDefaultsKeepsTheCountry() {
        val values = SettingsForm.values(Settings(holidayCountry = "IT", holidaysFr = false, defaultFibonacciPoints = 8))
        val restored = SettingsForm.restoreDefaults(values)
        assertEquals("IT", restored["holidayCountry"])
        assertEquals("3", restored["defaultFibonacciPoints"])
    }
}
