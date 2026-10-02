package com.skohscripts.kairos.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalDate

/**
 * Où l'on regarde dans le temps (docs/spec/vue-semaine.md § Navigation) :
 * le jour de la vue Jour (`null` = aujourd'hui, qui avance tout seul) et la
 * semaine de la vue Semaine (`null` = la semaine courante) ; et, dans l'espace
 * Équipe, l'onglet « Échanges » de l'écran Équipe (docs/spec/equipe-echanges.md),
 * que la visite guidée peut ouvrir directement.
 */
class NavState {
    var day by mutableStateOf<LocalDate?>(null)
    var week by mutableStateOf<LocalDate?>(null)
    var exchangesTab by mutableStateOf(false)
}
