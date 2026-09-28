package com.skohscripts.kairos.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.nav_day
import com.skohscripts.kairos.ui.generated.resources.nav_notes
import com.skohscripts.kairos.ui.generated.resources.nav_settings
import com.skohscripts.kairos.ui.generated.resources.nav_stats
import com.skohscripts.kairos.ui.generated.resources.nav_week
import com.skohscripts.kairos.ui.generated.resources.title_day
import com.skohscripts.kairos.ui.generated.resources.title_notes
import com.skohscripts.kairos.ui.generated.resources.title_settings
import com.skohscripts.kairos.ui.generated.resources.title_stats
import com.skohscripts.kairos.ui.generated.resources.title_week
import com.skohscripts.kairos.ui.icons.KairosIcons
import org.jetbrains.compose.resources.StringResource

/**
 * Les cinq destinations de premier niveau, dans l'ordre du flux GTD : capturer
 * (Notes) avant de traiter et faire (Jour). Cinq au plus : limite MD3 d'une
 * barre de navigation. « À propos et guide » n'est pas une destination : on y
 * accède depuis Réglages (docs/spec-v3/navigation-theme.md).
 */
enum class Destination(
    val label: StringResource,
    val title: StringResource,
    val icon: () -> ImageVector,
    val selectedIcon: () -> ImageVector,
) {
    NOTES(Res.string.nav_notes, Res.string.title_notes, { KairosIcons.Notes }, { KairosIcons.NotesFilled }),
    DAY(Res.string.nav_day, Res.string.title_day, { KairosIcons.Today }, { KairosIcons.TodayFilled }),
    WEEK(Res.string.nav_week, Res.string.title_week, { KairosIcons.DateRange }, { KairosIcons.DateRangeFilled }),
    STATS(Res.string.nav_stats, Res.string.title_stats, { KairosIcons.BarChart }, { KairosIcons.BarChartFilled }),
    SETTINGS(Res.string.nav_settings, Res.string.title_settings, { KairosIcons.Settings }, { KairosIcons.SettingsFilled }),
    ;

    companion object {
        /** Écran d'ouverture : la vue Jour, comme la page par défaut de Kairos 2. */
        val START = DAY
    }
}
