package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.sync.SyncScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Style de l'interface : Material ou Cadran (docs/spec/style-cadran.md). */
class UiStyleTest {
    @Test
    fun materialIsTheDefaultAndAnUnknownValueGivesMaterial() {
        assertEquals(Settings.UI_STYLE_MATERIAL, Settings().uiStyle)
        assertEquals(Settings.UI_STYLE_MATERIAL, UiStyle.normalize("ios"))
        assertEquals(Settings.UI_STYLE_MATERIAL, UiStyle.normalize(""))
        assertEquals(Settings.UI_STYLE_CADRAN, UiStyle.normalize(" Cadran "))
        assertTrue(UiStyle.isCadran("CADRAN"))
        assertFalse(UiStyle.isCadran("lumière"))
    }

    @Test
    fun theFormNormalisesAndNeverRefusesTheStyle() {
        val values = SettingsForm.values(Settings())
        assertEquals(Settings.UI_STYLE_MATERIAL, values["uiStyle"])
        assertEquals(Settings.UI_STYLE_CADRAN, SettingsForm.validate(values + ("uiStyle" to "Cadran"), Settings()).settings!!.uiStyle)
        assertEquals(Settings.UI_STYLE_MATERIAL, SettingsForm.validate(values + ("uiStyle" to "glass"), Settings()).settings!!.uiStyle)
    }

    @Test
    fun theStyleBelongsToTheDevice() {
        assertTrue("uiStyle" in SyncScope.LOCAL_SETTINGS)
        assertFalse("uiStyle" in SyncScope.SHARED_SETTINGS)
    }
}
