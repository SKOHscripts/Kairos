package com.skohscripts.kairos.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Cible sur laquelle tourne l'interface, passée par le point d'entrée de chaque
 * application (androidApp, desktopApp, webApp). En dépendent la forme de la
 * navigation (docs/spec-v3/navigation-theme.md § Navigation) et l'affichage
 * des raccourcis clavier, masqués sur Android (docs/spec-v3/vue-jour.md).
 */
enum class Platform { ANDROID, DESKTOP, WEB }

/** Plateforme courante, posée par [KairosApp]. */
val LocalPlatform = staticCompositionLocalOf { Platform.DESKTOP }
