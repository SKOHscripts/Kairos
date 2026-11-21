package com.skohscripts.kairos.ui.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.month.MonthView
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.card_holiday_countries_title
import com.skohscripts.kairos.ui.settings.holidayCountryName
import com.skohscripts.kairos.ui.theme.KairosSpacing
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * Capture des fiches des magasins sur les jours fériés par pays (docs/spec/publication.md § Captures, docs/spec/jours-feries.md) :
 * le titre « Les jours fériés de ton pays » dans la langue de la fiche, puis la grille du même [month] pour chaque pays de
 * [countries] (code ISO, langue de la grille), ses fériés marqués comme dans la vue Mois. Une base vide par pays : seuls les
 * fériés y figurent. Rendue par l'outil des captures (`StoreScreenshots`), jamais par l'application.
 */
@Composable
fun HolidayShowcase(month: LocalDate, countries: List<Pair<String, String>>) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(KairosSpacing.l), verticalArrangement = Arrangement.spacedBy(KairosSpacing.m)) {
            Text(
                stringResource(Res.string.card_holiday_countries_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            countries.forEach { (code, language) ->
                val view = MonthView.build(KairosSnapshot(settings = Settings(holidayCountry = code, holidaysFr = false)), month)
                Column(verticalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
                    Text(
                        stringResource(holidayCountryName(code)) + " · " + Dates.monthYear(view.first, language),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // Aujourd'hui hors du mois : aucune case en surbrillance.
                    MonthGrid(view, view.first.minusOneYear(), language) {}
                }
            }
        }
    }
}

private fun LocalDate.minusOneYear() = LocalDate(year - 1, month, day)
