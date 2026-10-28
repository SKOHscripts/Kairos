package com.skohscripts.kairos.ui.guide.demo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.guide_spot_here
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * Déroulé de la démonstration (docs/spec/visite-guidee.md § Visite interactive) : l'étape courante, la tâche suivie, la fin.
 * Rien n'est sauvegardé : une rotation ou la mort du processus met fin à la démonstration, qui est jetable.
 */
class DemoTour(val repository: KairosRepository, private val today: () -> LocalDate, val steps: List<DemoStep> = DemoSteps.ALL) {
    var index by mutableIntStateOf(0)
        private set
    var taskId by mutableStateOf<Long?>(null)
        private set

    /** Les six étapes sont validées (ou sautées) : le dialogue de fin. */
    var finished by mutableStateOf(false)
        private set

    /** Demande de « À moi de jouer » : l'écran défile jusqu'au repère et lui donne le focus ; incrémenté à chaque demande. */
    var focusRequest by mutableIntStateOf(0)
        private set

    /** Dernière demande de focus servie par la vue Jour (y revenir ne la rejoue pas). */
    var handledFocus = 0

    /** Le focus de l'élément cerné, posé par son [DemoMark] tant qu'il est composé. */
    internal var target: FocusRequester? = null

    val step: DemoStep get() = steps[index]
    val spot: DemoSpot? get() = if (finished) null else step.spot

    fun context(title: String) = DemoContext(repository, today(), taskId, title)

    /**
     * Un nouvel instantané de la démonstration : la tâche suivie apparaît (étape 1) ou disparaît (retour à l'étape 1), et
     * l'étape avance tant que son effet est là. Appelé à chaque changement de données, jamais à un changement d'étape : sinon
     * « Précédent » rebondirait aussitôt sur l'étape suivante.
     */
    fun observe(snapshot: KairosSnapshot) {
        if (finished) return
        val id = taskId
        if (id != null && snapshot.tasks.none { it.id == id }) {
            taskId = null
            index = 0
        }
        if (taskId == null) taskId = DemoSteps.trackedTask(snapshot)
        while (!finished && steps[index].done(snapshot, context(""))) next()
    }

    fun next() {
        if (index < steps.size - 1) index++ else finished = true
    }

    fun previous() {
        if (index > 0) index--
    }

    fun askFocus() {
        focusRequest++
    }
}

/** Balise de test de l'élément cerné (les tests visent ainsi la ligne de la tâche suivie parmi les exemples). */
const val DEMO_MARK_TAG = "demo-mark"

/** La démonstration en cours, `null` hors démonstration (les écrans réels ne changent alors pas d'un pixel). */
val LocalDemoTour = staticCompositionLocalOf<DemoTour?> { null }

/**
 * Repère d'une étape de la démonstration : contour primaire de 2 dp et étiquette « Ici » avec icône (jamais la couleur seule),
 * posés **sur l'élément** (pas par coordonnées : ils suivent le rail, la barre basse et 360 dp), tenus tant que l'étape dure,
 * sans animation ni région vivante (le panneau annonce déjà l'étape). [taskId] : l'élément appartient à cette tâche (ligne).
 * Hors démonstration, ou pour une autre étape, [content] est rendu tel quel, sans conteneur.
 */
@Composable
fun DemoMark(spot: DemoSpot, taskId: Long? = null, content: @Composable () -> Unit) {
    val tour = LocalDemoTour.current
    val marked = tour != null && tour.spot == spot && (taskId == null || tour.taskId == taskId)
    if (!marked) {
        content()
        return
    }
    val focus = remember { FocusRequester() }
    DisposableEffect(tour, focus) {
        tour!!.target = focus
        onDispose { if (tour.target === focus) tour.target = null }
    }
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.border(BorderStroke(2.dp, scheme.primary), MaterialTheme.shapes.small).focusRequester(focus).testTag(DEMO_MARK_TAG)) {
        content()
        // L'étiquette chevauche le bord haut : sur un bouton de 48 dp, posée dedans, elle le cacherait.
        Surface(
            color = scheme.primary,
            contentColor = scheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.TopEnd).wrapContentSize(unbounded = true).offset(y = (-12).dp).zIndex(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)) {
                Icon(KairosIcons.Spot, contentDescription = null, modifier = Modifier.size(12.dp))
                Text(stringResource(Res.string.guide_spot_here), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}
