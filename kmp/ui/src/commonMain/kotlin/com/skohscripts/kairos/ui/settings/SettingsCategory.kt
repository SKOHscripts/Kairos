package com.skohscripts.kairos.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.settings.GeneralError
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.settings_cat_about
import com.skohscripts.kairos.ui.generated.resources.settings_cat_about_hint
import com.skohscripts.kairos.ui.generated.resources.settings_cat_data
import com.skohscripts.kairos.ui.generated.resources.settings_cat_data_hint
import com.skohscripts.kairos.ui.generated.resources.settings_cat_day
import com.skohscripts.kairos.ui.generated.resources.settings_cat_day_hint
import com.skohscripts.kairos.ui.generated.resources.settings_cat_general
import com.skohscripts.kairos.ui.generated.resources.settings_cat_general_hint
import com.skohscripts.kairos.ui.generated.resources.settings_cat_planning
import com.skohscripts.kairos.ui.generated.resources.settings_cat_planning_hint
import com.skohscripts.kairos.ui.generated.resources.settings_cat_team
import com.skohscripts.kairos.ui.generated.resources.settings_cat_team_hint
import com.skohscripts.kairos.ui.generated.resources.settings_cat_tracking
import com.skohscripts.kairos.ui.generated.resources.settings_cat_tracking_hint
import com.skohscripts.kairos.ui.icons.KairosIcons
import org.jetbrains.compose.resources.StringResource

/**
 * Catégories des Réglages (docs/spec/reglages.md), dans l'ordre de la liste : chacune a son titre, son résumé et son
 * icône. Le formulaire reste un seul formulaire : une catégorie n'est qu'une façon d'en montrer une partie.
 */
enum class SettingsCategory(val title: StringResource, val hint: StringResource, val icon: ImageVector) {
    GENERAL(Res.string.settings_cat_general, Res.string.settings_cat_general_hint, KairosIcons.Tune),
    DAY(Res.string.settings_cat_day, Res.string.settings_cat_day_hint, KairosIcons.Schedule),
    PLANNING(Res.string.settings_cat_planning, Res.string.settings_cat_planning_hint, KairosIcons.Sort),
    TRACKING(Res.string.settings_cat_tracking, Res.string.settings_cat_tracking_hint, KairosIcons.Timer),
    TEAM(Res.string.settings_cat_team, Res.string.settings_cat_team_hint, KairosIcons.Groups),
    DATA(Res.string.settings_cat_data, Res.string.settings_cat_data_hint, KairosIcons.Database),
    ABOUT(Res.string.settings_cat_about, Res.string.settings_cat_about_hint, KairosIcons.Info),
    ;

    companion object {
        private val DAY_KEYS = setOf("defaultTaskDurationMinutes", "meetingBufferMinutes", "workdayStartHour", "workdayEndHour", "holidaysFr", "extraHolidays")
        private val PLANNING_KEYS = setOf(
            "taskTypes", "staleOverdueDays", "staleUntouchedDays", "priorityOverloadThreshold",
            "priorityValueBase", "urgencyHorizonDays", "urgencyPeak", "defaultFibonacciPoints",
            "cognitiveDipEnabled", "cognitiveDipStartHour", "cognitiveDipTroughHour", "cognitiveDipEndHour", "cognitiveDipPenalty",
        )
        private val TRACKING_KEYS = setOf("timerIdleAlertMinutes", "timerAutoStopMinutes", "pomodoroFocusMinutes", "timerAlertSound", "statsWindowWeeks")
        private val GENERAL_KEYS = setOf("language", "themeColor", "themeMode", "updateCheckEnabled")

        /**
         * Catégorie d'un champ de `SettingsForm` (son erreur y ouvre l'écran). Une clé inconnue lève une erreur : un
         * champ ajouté au formulaire sans catégorie ne doit pas disparaître de l'écran en silence (`SettingsCategoryTest`).
         */
        fun of(key: String): SettingsCategory = when {
            key in GENERAL_KEYS -> GENERAL
            // Les meilleures heures vivent sous `schedule.` mais servent à ordonner, pas à borner la journée.
            key.startsWith("schedule.bestHours.") -> PLANNING
            key in DAY_KEYS || key.startsWith("schedule.") -> DAY
            key in PLANNING_KEYS || key.startsWith("learning.") || key.startsWith("levelNames.") -> PLANNING
            key in TRACKING_KEYS || key.startsWith("reminders.") -> TRACKING
            key.startsWith("team.") -> TEAM
            else -> error("Champ des Réglages sans catégorie : $key")
        }

        /** Catégorie d'une règle entre champs : son bandeau s'y affiche. */
        fun of(error: GeneralError): SettingsCategory = when (error) {
            GeneralError.WORKDAY_ORDER -> DAY
            GeneralError.DIP_ORDER -> PLANNING
        }
    }
}

/**
 * Où l'on est dans les Réglages, lu aussi par la coquille (titre de la barre, flèche retour) : [category] ouverte
 * (`null` = la liste) et [singlePane], posé par l'écran selon sa largeur. Sur deux volets, la flèche ferme les Réglages.
 */
class SettingsNav {
    var category by mutableStateOf<SettingsCategory?>(null)
    var singlePane by mutableStateOf(true)

    /** Étape de la visite des Réglages (docs/spec/premiere-tache.md, issue #98) ; `null` : pas de visite. */
    var tourStep by mutableStateOf<Int?>(null)

    /** Une catégorie est ouverte seule à l'écran : la flèche retour ramène à la liste. */
    val inCategory: Boolean get() = singlePane && category != null
}

/** Largeur offerte à l'écran à partir de laquelle la liste et la catégorie sont côte à côte (classe « étendue » MD3). */
internal val SETTINGS_TWO_PANE_MIN = 840.dp

/** Largeur de la liste des catégories sur deux volets. */
internal val SETTINGS_LIST_WIDTH = 320.dp
