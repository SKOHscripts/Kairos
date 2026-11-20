package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.Settings

/**
 * Style de l'interface (docs/spec/style-cadran.md), pur : Material 3 par défaut, ou « Cadran », inspiré d'iOS.
 * Une valeur inconnue (import, fichier édité à la main) donne Material, jamais une erreur.
 */
object UiStyle {
    /** Valeur enregistrée : `cadran`, sinon [Settings.UI_STYLE_MATERIAL]. */
    fun normalize(setting: String): String =
        if (setting.trim().lowercase() == Settings.UI_STYLE_CADRAN) Settings.UI_STYLE_CADRAN else Settings.UI_STYLE_MATERIAL

    /** Le style enregistré est « Cadran ». */
    fun isCadran(setting: String): Boolean = normalize(setting) == Settings.UI_STYLE_CADRAN
}
