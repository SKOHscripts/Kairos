package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.core.model.MissingQualification
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.missing_both
import com.skohscripts.kairos.ui.generated.resources.missing_points
import com.skohscripts.kairos.ui.generated.resources.missing_priority
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

/**
 * Sens des priorités et des points, affiché partout où on les choisit
 * (docs/spec/vue-jour-gtd.md § Comprendre les valeurs : définitions de
 * `app/task_guide.py`, Kairos 2).
 */
internal object Levels {
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

    fun missing(m: MissingQualification): StringResource = when (m) {
        MissingQualification.BOTH -> Res.string.missing_both
        MissingQualification.PRIORITY -> Res.string.missing_priority
        MissingQualification.POINTS -> Res.string.missing_points
    }
}
