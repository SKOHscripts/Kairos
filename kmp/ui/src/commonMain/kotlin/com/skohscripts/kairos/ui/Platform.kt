package com.skohscripts.kairos.ui

/**
 * Cible sur laquelle tourne l'interface, passée par le point d'entrée de chaque
 * application (androidApp, desktopApp, webApp). Seule la forme de la
 * navigation en dépend (docs/spec-v3/navigation-theme.md § Navigation).
 */
enum class Platform { ANDROID, DESKTOP, WEB }
