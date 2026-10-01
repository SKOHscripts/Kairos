package com.skohscripts.kairos.ui.team

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.team.TeamLoad
import com.skohscripts.kairos.ui.app.AppServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Résultat d'un calcul lourd de l'espace Équipe, hors de la composition : [value] est le **dernier** résultat
 * obtenu (`null` tant qu'aucun n'est arrivé) et [computing] dit qu'un nouveau est en route. L'écran garde donc
 * l'ancien résultat à l'écran jusqu'au nouveau, avec un indicateur de progression (docs/spec/equipe-charge.md
 * § Interface, Performance).
 */
internal class Computed<T>(val value: T?, val computing: Boolean)

/**
 * Calcule [compute] dans `Dispatchers.Default` à chaque changement de [keys] (jamais dans la composition : le plan de
 * charge et surtout la suggestion peuvent dépasser la seconde pour 150 tâches). Un calcul en cours quand les clés
 * changent est abandonné (son résultat n'est jamais publié). [compute] ne doit lire aucun état Compose : tout ce
 * dont il a besoin est capturé en valeurs. `Dispatchers.Default` existe sur toutes les cibles, wasm compris (où il
 * s'exécute sur le fil principal, entre deux images).
 */
@Composable
internal fun <T : Any> rememberComputed(vararg keys: Any?, compute: () -> T): Computed<T> {
    var value by remember { mutableStateOf<T?>(null) }
    var computing by remember { mutableStateOf(true) }
    val latest by rememberUpdatedState(compute)
    LaunchedEffect(*keys) {
        computing = true
        val result = withContext(Dispatchers.Default) { latest() }
        value = result
        computing = false
    }
    return Computed(value, computing)
}

/**
 * La vue d'ensemble de la charge ([TeamLoad]) de [snapshot], sur [horizonWeeks] semaines (`null` : l'horizon des
 * réglages). Recalculée hors composition quand la base, le jour ou l'horizon changent.
 */
@Composable
internal fun rememberTeamLoad(services: AppServices, snapshot: KairosSnapshot, horizonWeeks: Int? = null): Computed<TeamLoad> {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = services.clock.now().toLocalDateTime(zone).date
    return rememberComputed(snapshot, today, horizonWeeks) { TeamLoad.build(snapshot, services.clock.now(), zone, horizonWeeks) }
}
