package com.skohscripts.kairos.ui.app

import androidx.compose.runtime.staticCompositionLocalOf

/** Affiche un message bref (snackbar de la coquille) : confirmation d'export, erreur d'import… */
val LocalMessages = staticCompositionLocalOf<(String) -> Unit> { {} }
