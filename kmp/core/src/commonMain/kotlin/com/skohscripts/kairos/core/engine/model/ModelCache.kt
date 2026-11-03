package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import kotlin.concurrent.Volatile

/**
 * Mémoïsation du modèle (docs/spec/estimateur-modele.md § Cache de l'index) : `DayView` se recalcule chaque minute, l'index
 * et la garde ne doivent pas être reconstruits chaque fois. Clé = l'empreinte du corpus ([ModelIndex.fingerprint]) et les
 * réglages que lit la garde (durée par défaut, fenêtre de travail, `minSamples`). Une case **par espace** (Perso et Équipe :
 * la vue Jour estime les deux), passée en paramètre ; [NONE] ne mémorise rien (tests) ; [shared] sert la façade, donc aussi les prévisions.
 */
interface ModelCache {
    /** Le corpus d'un historique ([ModelEstimator.corpus]) et son empreinte, calculée à la demande. */
    class Prepared(val corpus: List<DurationEstimator.Sample>) {
        val fingerprint: Long by lazy { ModelIndex.fingerprint(corpus) }
    }

    /**
     * Le corpus de [samples]. Le cache mémorise par **identité** de la liste : la façade passe la même liste pour toutes les
     * tâches d'un espace dans un même calcul (`DurationEstimator.estimates`), le tri et l'empreinte ne sont faits qu'une fois.
     */
    fun prepare(samples: List<DurationEstimator.Sample>): Prepared = Prepared(ModelEstimator.corpus(samples))

    fun state(prepared: Prepared, settings: Settings, build: () -> ModelEstimator.State): ModelEstimator.State

    companion object {
        val NONE: ModelCache = object : ModelCache {
            override fun state(prepared: Prepared, settings: Settings, build: () -> ModelEstimator.State) = build()
        }

        /** Un cache à une case par espace. */
        fun memo(): ModelCache = MemoModelCache()

        /**
         * Le cache de la façade (`DurationEstimator.estimate`, `estimates`, `effectiveMinutes`, `withLearnedEstimates`) quand
         * l'appelant n'en passe pas : vue Jour et Semaine, rappels, widgets, chrono et calculs d'équipe en profitent sans
         * changer. Il ne change aucun résultat (`ModelEstimatorTest.theCacheGivesTheSameResultAndIsKeptPerSpace`).
         */
        val shared: ModelCache = MemoModelCache()
    }
}

private class MemoModelCache : ModelCache {
    private class Entry(val key: List<Any>, val state: ModelEstimator.State)
    private class Input(val samples: List<DurationEstimator.Sample>, val prepared: ModelCache.Prepared)

    // Listes remplacées d'un bloc (jamais modifiées), publiées par une écriture volatile : une lecture concurrente (vue Jour
    // en arrière-plan, Réglages, prévisions) voit l'ancienne ou la nouvelle liste, entière ; au pire, un modèle reconstruit.
    @Volatile
    private var entries: List<Entry> = emptyList()

    @Volatile
    private var inputs: List<Input> = emptyList()

    override fun prepare(samples: List<DurationEstimator.Sample>): ModelCache.Prepared {
        val current = inputs
        current.firstOrNull { it.samples === samples }?.let { return it.prepared }
        val prepared = ModelCache.Prepared(ModelEstimator.corpus(samples))
        // Deux entrées suffisent : une par espace dans un même calcul.
        inputs = (current + Input(samples, prepared)).takeLast(2)
        return prepared
    }

    override fun state(prepared: ModelCache.Prepared, settings: Settings, build: () -> ModelEstimator.State): ModelEstimator.State {
        val corpus = prepared.corpus
        val space = corpus.firstOrNull()?.space
        val learning = settings.learning ?: LearningSettings()
        val key = listOf(
            prepared.fingerprint, corpus.size, settings.defaultTaskDurationMinutes,
            settings.workdayStartHour, settings.workdayEndHour, learning.minSamples,
        )
        val current = entries
        current.firstOrNull { it.state.space == space && it.key == key }?.let { return it.state }
        val state = build()
        entries = current.filter { it.state.space != space } + Entry(key, state)
        return state
    }
}
