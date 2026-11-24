package com.skohscripts.kairos.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.skohscripts.kairos.ui.guide.GuideSpotState
import com.skohscripts.kairos.ui.settings.SettingsNav
import kotlinx.datetime.LocalDate

/**
 * Où l'on regarde dans le temps (docs/spec/vue-semaine.md § Navigation) :
 * le jour de la vue Jour (`null` = aujourd'hui, qui avance tout seul) et la
 * semaine de la vue Semaine (`null` = la semaine courante), ou son mois en vue Mois ; et, dans l'espace
 * Équipe, l'onglet « Échanges » de l'écran Équipe (docs/spec/equipe-echanges.md),
 * que la visite guidée peut ouvrir directement ; enfin la catégorie ouverte des Réglages.
 */
class NavState {
    var day by mutableStateOf<LocalDate?>(null)
    var week by mutableStateOf<LocalDate?>(null)

    /** La destination Semaine montre le mois (docs/spec/vue-mois.md) ; [month] = un jour du mois affiché, `null` = le courant. */
    var monthMode by mutableStateOf(false)
    var month by mutableStateOf<LocalDate?>(null)
    var exchangesTab by mutableStateOf(false)

    /** L'accompagnement de la première tâche est demandé (docs/spec/premiere-tache.md) : la vue Jour ouvre la fiche et l'acquitte. */
    var firstTaskRequest by mutableStateOf(false)

    /** La demande est celle de la première tâche **récurrente** (issue #98) : la fiche suit les étapes de `RecurrenceCoach`. */
    var firstTaskRecurring by mutableStateOf(false)

    /** La demande est celle de la première **habitude** (docs/spec/habitudes.md § 11) : une quotidienne, les étapes de `HabitCoach`. */
    var firstTaskHabit by mutableStateOf(false)

    /** Ouvrir la fiche de cette tâche sur sa section « Habitude » (conseil « En faire une habitude ? ») ; la vue Jour l'acquitte. */
    var habitRequest by mutableStateOf<Long?>(null)

    /** Catégorie ouverte dans les Réglages (docs/spec/reglages.md) : la coquille en tire le titre et la flèche retour. */
    val settings = SettingsNav()

    /** Section qu'une carte du guide demande de montrer, et celle mise en valeur (docs/spec/visite-guidee.md, issue #95). */
    val guideSpot = GuideSpotState()
}
