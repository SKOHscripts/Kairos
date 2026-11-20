package com.skohscripts.kairos.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.skohscripts.kairos.core.settings.UiStyle

/**
 * Langage visuel de l'interface (docs/spec/style-cadran.md) : Material 3 (par défaut, la charte) ou « Cadran », inspiré
 * d'iOS. Seuls le thème et les enveloppes de `KairosComponents` le lisent, plus la coquille de navigation : un écran ne
 * connaît pas le style.
 */
enum class KairosStyle {
    MATERIAL,
    CADRAN,
    ;

    companion object {
        /** Le style du réglage `uiStyle` ; une valeur inconnue donne Material. */
        fun of(setting: String): KairosStyle = if (UiStyle.isCadran(setting)) CADRAN else MATERIAL
    }
}

/** Style courant, posé par `KairosTheme`. */
val LocalKairosStyle = staticCompositionLocalOf { KairosStyle.MATERIAL }
