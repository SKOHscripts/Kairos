package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.core.model.FIBONACCI_SCALE
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.skohscripts.kairos.core.model.LevelNames
import com.skohscripts.kairos.core.model.Missing
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.missing_item_points
import com.skohscripts.kairos.ui.generated.resources.missing_item_priority
import com.skohscripts.kairos.ui.generated.resources.level_named_points
import com.skohscripts.kairos.ui.generated.resources.level_named_priority
import com.skohscripts.kairos.ui.generated.resources.points_badge
import com.skohscripts.kairos.ui.generated.resources.points_13_name
import com.skohscripts.kairos.ui.generated.resources.points_1_name
import com.skohscripts.kairos.ui.generated.resources.points_21_name
import com.skohscripts.kairos.ui.generated.resources.points_2_name
import com.skohscripts.kairos.ui.generated.resources.points_3_name
import com.skohscripts.kairos.ui.generated.resources.points_5_name
import com.skohscripts.kairos.ui.generated.resources.points_8_name
import com.skohscripts.kairos.ui.generated.resources.points_1_meaning
import com.skohscripts.kairos.ui.generated.resources.points_1_example
import com.skohscripts.kairos.ui.generated.resources.points_2_meaning
import com.skohscripts.kairos.ui.generated.resources.points_2_example
import com.skohscripts.kairos.ui.generated.resources.points_3_meaning
import com.skohscripts.kairos.ui.generated.resources.points_3_example
import com.skohscripts.kairos.ui.generated.resources.points_5_meaning
import com.skohscripts.kairos.ui.generated.resources.points_5_example
import com.skohscripts.kairos.ui.generated.resources.points_8_meaning
import com.skohscripts.kairos.ui.generated.resources.points_8_example
import com.skohscripts.kairos.ui.generated.resources.points_13_meaning
import com.skohscripts.kairos.ui.generated.resources.points_13_example
import com.skohscripts.kairos.ui.generated.resources.points_21_meaning
import com.skohscripts.kairos.ui.generated.resources.points_21_example
import com.skohscripts.kairos.ui.generated.resources.priority_0_meaning
import com.skohscripts.kairos.ui.generated.resources.priority_0_name
import com.skohscripts.kairos.ui.generated.resources.priority_1_meaning
import com.skohscripts.kairos.ui.generated.resources.priority_1_name
import com.skohscripts.kairos.ui.generated.resources.priority_2_meaning
import com.skohscripts.kairos.ui.generated.resources.priority_2_name
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Noms choisis des niveaux (docs/spec/planning-personnalise.md § Lot D), posés par `KairosApp` depuis les réglages ;
 * vide par défaut. Affichage seulement : la couleur et le calcul suivent toujours la valeur.
 */
val LocalLevelNames = staticCompositionLocalOf { LevelNames() }

/**
 * Sens des priorités et des points, affiché partout où on les choisit
 * (docs/spec/vue-jour-gtd.md § Comprendre les valeurs : définitions de
 * repères repris des versions précédentes).
 */
internal object Levels {
    /** Priorité là où le code est seul (badge, filtre) : le nom choisi, sinon « P0 ». */
    @Composable
    fun priorityShort(p: Int): String = LocalLevelNames.current.priority[p] ?: "P$p"

    /** Priorité suivie de son sens (pastille, « Pourquoi ? », aide) : « Urgent (P0) » si un nom est choisi, sinon « P0 Critique ». */
    @Composable
    fun priorityLabel(p: Int): String =
        LocalLevelNames.current.priority[p]?.let { stringResource(Res.string.level_named_priority, it, p) } ?: "P$p ${stringResource(priorityName(p))}"

    /** [priorityShort] de chaque priorité, pour les listes dont l'affichage n'est pas composable. */
    @Composable
    fun priorityShorts(): Map<Int, String> = PRIORITY_VALUES.associateWith { priorityShort(it) }

    /** [pointsShort] de chaque palier, même usage que [priorityShorts]. */
    @Composable
    fun pointsShorts(): Map<Int, String> = FIBONACCI_SCALE.associateWith { pointsShort(it) }

    /** Points là où le palier est seul (badge, filtre) : le nom choisi, sinon « 3 pts ». */
    @Composable
    fun pointsShort(points: Int): String = LocalLevelNames.current.points[points] ?: stringResource(Res.string.points_badge, points)

    /** Palier suivi de son sens (pastille) : « M (3) » si un nom est choisi, sinon « 3 modéré ». */
    @Composable
    fun pointsLabel(points: Int): String =
        LocalLevelNames.current.points[points]?.let { stringResource(Res.string.level_named_points, it, points) } ?: "$points ${stringResource(pointsName(points))}"

    fun priorityName(p: Int): StringResource = when (p) {
        0 -> Res.string.priority_0_name
        1 -> Res.string.priority_1_name
        else -> Res.string.priority_2_name
    }

    fun priorityMeaning(p: Int): StringResource = when (p) {
        0 -> Res.string.priority_0_meaning
        1 -> Res.string.priority_1_meaning
        else -> Res.string.priority_2_meaning
    }

    fun pointsName(points: Int): StringResource = when (points) {
        1 -> Res.string.points_1_name
        2 -> Res.string.points_2_name
        3 -> Res.string.points_3_name
        5 -> Res.string.points_5_name
        8 -> Res.string.points_8_name
        13 -> Res.string.points_13_name
        else -> Res.string.points_21_name
    }

    fun pointsMeaning(points: Int): StringResource = when (points) {
        1 -> Res.string.points_1_meaning
        2 -> Res.string.points_2_meaning
        3 -> Res.string.points_3_meaning
        5 -> Res.string.points_5_meaning
        8 -> Res.string.points_8_meaning
        13 -> Res.string.points_13_meaning
        else -> Res.string.points_21_meaning
    }

    fun pointsExample(points: Int): StringResource = when (points) {
        1 -> Res.string.points_1_example
        2 -> Res.string.points_2_example
        3 -> Res.string.points_3_example
        5 -> Res.string.points_5_example
        8 -> Res.string.points_8_example
        13 -> Res.string.points_13_example
        else -> Res.string.points_21_example
    }

    /**
     * Les éléments manquants, dans l'ordre des contrôles (priorité, points, type) ; [typeItem] nomme le type (« type » dans
     * la vue Jour, « catégorie » dans le backlog d'équipe).
     */
    fun missing(m: Missing, typeItem: StringResource): List<StringResource> = buildList {
        if (m.priority) add(Res.string.missing_item_priority)
        if (m.points) add(Res.string.missing_item_points)
        if (m.type) add(typeItem)
    }
}
