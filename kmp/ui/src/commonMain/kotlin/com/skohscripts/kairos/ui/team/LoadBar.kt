package com.skohscripts.kairos.ui.team

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.team.LoadLevel
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.load_bar_value
import com.skohscripts.kairos.ui.generated.resources.load_bar_value_plain
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.stats.BarTrack
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.min

/**
 * Parts d'une barre de charge sur une échelle de [scalePercent] % (au moins 100) : le remplissage `primary` va
 * jusqu'à 100 % de la capacité, la part au-delà est le dépassement (`error`). Un taux indéfini (capacité nulle) avec
 * de la charge est un dépassement total. Pur, donc testable.
 */
internal class LoadFractions(val fill: Float, val overflow: Float) {
    companion object {
        fun of(ratePercent: Double?, loadHours: Double, scalePercent: Double = 100.0): LoadFractions {
            val scale = max(100.0, scalePercent)
            if (ratePercent == null) return LoadFractions(0f, if (loadHours > 0.0) 1f else 0f)
            val fill = min(ratePercent, 100.0).coerceAtLeast(0.0) / scale
            val overflow = (ratePercent - 100.0).coerceAtLeast(0.0) / scale
            return LoadFractions(fill.toFloat(), overflow.toFloat())
        }
    }
}

/**
 * Barre de charge d'un membre ou de l'équipe (docs/spec/equipe-charge.md § Charge individuelle) : piste
 * `surfaceContainerHighest`, remplissage `primary` jusqu'à 100 %, part au-delà en `error`, et dessous la valeur
 * écrite « 112 h / 96 h · 117 % ». Un niveau « à surveiller » ou « surchargé » met la valeur dans un contour avec
 * l'icône `Warning` : le rouge n'est jamais le seul signal, et seule la part en dépassement le porte.
 * [scalePercent] : échelle commune de plusieurs barres (au moins 100).
 */
@Composable
internal fun LoadBar(
    loadHours: Double,
    capacityHours: Double,
    ratePercent: Double?,
    level: LoadLevel,
    modifier: Modifier = Modifier,
    scalePercent: Double = 100.0,
) {
    val parts = LoadFractions.of(ratePercent, loadHours, max(scalePercent, ratePercent ?: 100.0))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BarTrack(parts.fill, Modifier.fillMaxWidth(), parts.overflow)
        val value = if (ratePercent == null) {
            stringResource(Res.string.load_bar_value_plain, hoursText(loadHours), hoursText(capacityHours))
        } else {
            stringResource(Res.string.load_bar_value, hoursText(loadHours), hoursText(capacityHours), percentText(ratePercent))
        }
        Flag(value, flagged = level != LoadLevel.OK, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Un texte d'état. [flagged] : contour `outline` et icône `Warning` (la forme dit « à surveiller », jamais une
 * teinte ni de l'ambre) ; sinon le texte seul. [description] : lecture de l'icône par un lecteur d'écran ;
 * [icon] : une autre icône que `Warning` pour un état qui n'est pas un risque (« en attente »).
 */
@Composable
internal fun Flag(
    text: String,
    flagged: Boolean,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
    description: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector = KairosIcons.Warning,
) {
    if (!flagged) {
        Text(text, style = style, modifier = modifier)
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(16.dp))
        Text(text, style = style)
    }
}
