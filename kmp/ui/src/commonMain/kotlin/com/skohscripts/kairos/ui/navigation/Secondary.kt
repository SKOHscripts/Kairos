package com.skohscripts.kairos.ui.navigation

/**
 * Écran secondaire ouvert par-dessus les destinations (retour par la flèche, Échap ou le retour système) :
 * `HOME` = la page « Accueil » (docs/spec/accueil.md), `ABOUT` = « À propos et guide ». Ce ne sont pas des
 * destinations de navigation : aucune n'est marquée active tant qu'un écran secondaire est ouvert.
 */
enum class Secondary { HOME, ABOUT }
