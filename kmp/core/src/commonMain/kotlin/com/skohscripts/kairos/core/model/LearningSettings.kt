package com.skohscripts.kairos.core.model

import kotlinx.serialization.Serializable

/**
 * Durées apprises (docs/spec/durees-apprises.md), sous-objet [Settings.learning] : `null` tant que rien n'est réglé, ce qui
 * veut dire les valeurs ci-dessous (même règle que [Settings.team] et [Settings.schedule] : sauver le formulaire sans y toucher
 * ne crée jamais cet objet). Une tâche sans estimation explicite reprend alors la durée apprise de ses occurrences passées.
 */
@Serializable
data class LearningSettings(
    /** Désactive les durées apprises : les tâches sans estimation retombent sur la durée par défaut, comme avant. */
    val enabled: Boolean = true,
    /** Nombre d'occurrences passées retenues (les plus récentes), ≥ 3. */
    val history: Int = 5,
    /** Occurrences chronométrées minimales pour qu'une durée apprise soit appliquée, ≥ 2. */
    val minSamples: Int = 3,
    /**
     * Estimer par modèle (docs/spec/estimateur-modele.md, 4.0.0) ; **`null` = actif**. Nullable plutôt qu'un booléen à
     * `true` : l'export écrit les valeurs par défaut, et un booléen écrirait `"model": true` dans le fichier de tout
     * utilisateur qui a déjà un objet `learning` (une valeur nulle n'est pas écrite).
     */
    val model: Boolean? = null,
    /**
     * Prévenir quand des estimations changent nettement (docs/spec/estimateur-modele.md, besoin 12) ; **`null` = actif**, seul
     * `false` s'écrit (même raison que [model]).
     */
    val notifyChanges: Boolean? = null,
)
