package com.skohscripts.kairos.core.engine.model

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings

/**
 * Mémoïsation du modèle (docs/spec/estimateur-modele.md § Cache de l'index) : `DayView` se recalcule chaque minute, l'index
 * et la garde ne doivent pas être reconstruits chaque fois. Clé = l'empreinte du corpus ([ModelIndex.fingerprint]) et les
 * réglages que lit la garde (durée par défaut, fenêtre de travail, `minSamples`). Une case **par espace** (Perso et Équipe :
 * la vue Jour estime les deux), passée en paramètre ; [NONE] ne mémorise rien (tests, prévisions).
 */
interface ModelCache {
    fun state(corpus: List<DurationEstimator.Sample>, settings: Settings, build: () -> ModelEstimator.State): ModelEstimator.State

    companion object {
        val NONE: ModelCache = object : ModelCache {
            override fun state(corpus: List<DurationEstimator.Sample>, settings: Settings, build: () -> ModelEstimator.State) = build()
        }

        /** Un cache à une case par espace, pour une instance d'application (`AppServices`). */
        fun memo(): ModelCache = MemoModelCache()
    }
}

private class MemoModelCache : ModelCache {
    private class Entry(val key: List<Any>, val state: ModelEstimator.State)

    // Remplacée d'un bloc (jamais modifiée) : une lecture concurrente voit l'ancienne ou la nouvelle liste.
    private var entries: List<Entry> = emptyList()

    override fun state(corpus: List<DurationEstimator.Sample>, settings: Settings, build: () -> ModelEstimator.State): ModelEstimator.State {
        val space = corpus.firstOrNull()?.space
        val learning = settings.learning ?: LearningSettings()
        val key = listOf(
            ModelIndex.fingerprint(corpus), corpus.size, settings.defaultTaskDurationMinutes,
            settings.workdayStartHour, settings.workdayEndHour, learning.minSamples,
        )
        val current = entries
        current.firstOrNull { it.state.space == space && it.key == key }?.let { return it.state }
        val state = build()
        entries = current.filter { it.state.space != space } + Entry(key, state)
        return state
    }
}
