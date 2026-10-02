package com.skohscripts.kairos.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Échelle d'espacement vertical (docs/spec/densite.md) : un niveau par rapport
 * entre deux éléments, pour que la hiérarchie se lise sans ajouter de place.
 * `xs` lie une étiquette ou une aide à son champ ; `s` sépare les éléments d'un
 * groupe ; `m` sépare les blocs et cartes d'une liste ; `l` est la marge
 * d'écran et la marge intérieure d'une carte ; `xl` précède un titre de section.
 */
object KairosSpacing {
    /** Liés : champ et aide, étiquette et valeur. */
    val xs = 4.dp

    /** Groupe : éléments d'un même ensemble, puces entre elles. */
    val s = 8.dp

    /** Bloc : cartes et blocs d'une même colonne. */
    val m = 12.dp

    /** Marge : marge d'écran, marge intérieure d'une carte. */
    val l = 16.dp

    /** Section : avant un titre de section. */
    val xl = 24.dp
}

/**
 * Rythme d'une liste paresseuse (`LazyColumn`) dont les lignes sont espacées de
 * `s` : un bloc (carte de capture, filtres…) y ajoute cette différence pour
 * totaliser `m` avec le bloc voisin.
 */
val KairosListBlockExtra = KairosSpacing.m - KairosSpacing.s

/** Idem pour un titre de section de la liste : il totalise `xl` avec ce qui le précède. */
val KairosListSectionExtra = KairosSpacing.xl - KairosSpacing.s
