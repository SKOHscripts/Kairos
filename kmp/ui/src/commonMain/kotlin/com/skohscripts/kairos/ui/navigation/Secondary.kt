package com.skohscripts.kairos.ui.navigation

/**
 * Écran secondaire ouvert par-dessus les destinations (retour par la flèche, Échap ou le retour système) :
 * `SETTINGS` = les Réglages (roue crantée de la barre du haut, les mêmes dans les deux espaces),
 * `ABOUT` = « À propos et guide », `FEATURES` = la liste des fonctionnalités (bouton de l'Accueil,
 * docs/spec/liste-fonctionnalites.md). Ce ne sont pas des destinations de navigation : aucune n'est marquée
 * active tant qu'un écran secondaire est ouvert (docs/spec/navigation-theme.md).
 */
enum class Secondary { SETTINGS, ABOUT, FEATURES }
