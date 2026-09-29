package com.skohscripts.kairos.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalDate

/**
 * Où l'on regarde dans le temps (docs/spec-v3/vue-semaine.md § Navigation) :
 * le jour de la vue Jour (`null` = aujourd'hui, qui avance tout seul) et la
 * semaine de la vue Semaine (`null` = la semaine courante).
 */
class NavState {
    var day by mutableStateOf<LocalDate?>(null)
    var week by mutableStateOf<LocalDate?>(null)
}
