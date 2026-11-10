package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.engine.model.ModelCache
import com.skohscripts.kairos.core.engine.model.ModelEstimator
import com.skohscripts.kairos.core.engine.model.ModelGate
import com.skohscripts.kairos.core.engine.model.Rhythm
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlin.time.Instant

/**
 * Durée apprise d'une tâche sans estimation explicite (docs/spec/durees-apprises.md, docs/spec/estimateur-modele.md) : un
 * niveau de la chaîne de repli ([EstimatorChain]) dit ce qu'il sait d'une tâche à partir des tâches déjà faites. Pur : ni
 * horloge ni accès aux données (l'instant et l'historique sont des paramètres). Les appelants passent par la façade du
 * `companion` ([estimate], [estimates], [appliedMinutes], [effectiveMinutes], [withLearnedEstimates]), qui garde les noms
 * d'avant l'extraction de l'interface (4.0.0, issue #99).
 */
interface DurationEstimator {
    /** D'où vient la durée : les occurrences de la même série, les tâches de même titre, ou le modèle (tâches proches). */
    enum class Source { SERIES, TITLE, MODEL }

    /**
     * La durée apprise : [minutes] (arrondie à 5 min), le nombre d'occurrences retenues [samples] et si elle est [reliable]
     * (au moins [LearningSettings.minSamples] occurrences retenues). Une durée peu fiable s'affiche mais ne s'applique pas.
     * [low] et [high] : l'intervalle (nuls pour une série ou un titre d'une seule occurrence) ; [evidence] : source
     * [Source.MODEL] seulement.
     */
    data class Estimate(
        val minutes: Int,
        val source: Source,
        val samples: Int,
        val reliable: Boolean,
        val low: Int? = null,
        val high: Int? = null,
        val evidence: Evidence? = null,
    )

    /** La base d'une estimation du modèle : des tâches proches, ou la régression sur le type et les points. */
    enum class Basis { NEIGHBOURS, REGRESSION }

    /** Une tâche proche : sa durée réelle et sa ressemblance en pour cent entier. */
    data class Neighbour(val taskId: Long, val title: String, val minutes: Int, val similarityPercent: Int)

    /**
     * La preuve d'une estimation du modèle : [neighbours] (les plus proches d'abord ; vide pour la régression), ou le type et
     * les points qu'a lus la régression et la taille du [corpus] ; [gate], la vérification sur l'historique.
     */
    data class Evidence(
        val basis: Basis,
        val neighbours: List<Neighbour>,
        val taskType: String?,
        val points: Int?,
        val corpus: Int,
        val gate: ModelGate.Result,
        /** Le facteur de rythme appliqué (jour, tranche), ou `null` (docs/spec/estimateur-modele.md § Rythme). */
        val rhythm: Rhythm.Applied? = null,
    )

    /**
     * Une occurrence passée : tâche terminée et le temps réellement passé dessus, avec les caractéristiques que le modèle
     * compare (type, points, priorité, étiquette de projet, récurrence ; jamais la description). Les champs ajoutés pour le
     * modèle ont une valeur par défaut.
     */
    data class Sample(
        val taskId: Long,
        val seriesId: Long?,
        val title: String,
        val space: TaskSpace,
        val minutes: Int,
        val taskType: String = "",
        val points: Int? = null,
        val priority: Int? = null,
        val projectTag: String = "",
        val recurring: Boolean = false,
        /** Jour ISO (1 = lundi) et tranche de la première séance, à l'heure locale ; nuls sans séance ou sans fuseau (rythme). */
        val weekday: Int? = null,
        val band: Rhythm.Band? = null,
        /**
         * Clé d'ordre des occurrences (« les plus récentes ») : le rang commun d'une base synchronisée
         * (`KairosSnapshot.sampleOrder`), sinon l'identifiant ; jamais `updatedAt`, modifiable après coup.
         */
        val order: Long = taskId,
    )

    /**
     * L'historique qu'un niveau consulte : les occurrences du **même espace** que la tâche, hors la tâche elle-même, et le
     * cache du modèle ([ModelCache.NONE] : aucun).
     */
    class History(val samples: List<Sample>, val cache: ModelCache = ModelCache.NONE)

    /** Au plus une estimation, ou `null` : ce niveau ne sait rien de [task]. */
    fun estimate(task: Task, history: History, settings: Settings): Estimate?

    companion object {
        /** Rang d'une occurrence sans rang commun (base synchronisée) : après toutes les autres, par identifiant. */
        private const val UNRANKED = 1L shl 40

        /** Temps minimal pour qu'une occurrence compte : un démarrage-arrêt accidentel (1 minute ou moins) est ignoré. */
        const val MIN_SAMPLE_MINUTES = 2

        /** La chaîne de repli : série, puis titre (docs/spec/durees-apprises.md), puis modèle (docs/spec/estimateur-modele.md). */
        val chain: DurationEstimator = EstimatorChain(listOf(SeriesEstimator, TitleEstimator, ModelEstimator))

        /**
         * Les occurrences passées : tâches **terminées**, au temps passé (chrono et saisie manuelle) d'au moins
         * [MIN_SAMPLE_MINUTES]. Une tâche reçue d'un manager n'en est jamais (isolation d'espace).
         */
        fun samples(
            tasks: List<Task>,
            spentByTask: Map<Long, Int>,
            firstStarts: Map<Long, LocalDateTime> = emptyMap(),
            /** Rang commun d'une base synchronisée ([KairosSnapshot.sampleOrder]) ; une tâche sans rang passe après, par identifiant. */
            order: Map<Long, Long>? = null,
        ): List<Sample> =
            tasks.filter { it.status == TaskStatus.DONE && it.origin == null }.mapNotNull { t ->
                val spent = spentByTask[t.id] ?: return@mapNotNull null
                if (spent < MIN_SAMPLE_MINUTES) {
                    null
                } else {
                    val start = firstStarts[t.id]?.let(Rhythm::of)
                    Sample(
                        t.id, t.seriesId, t.title, t.space, spent,
                        taskType = t.taskType, points = t.fibonacciPoints, priority = t.priority,
                        projectTag = t.projectTag, recurring = t.recurrence != TaskRecurrence.NONE,
                        weekday = start?.first, band = start?.second,
                        order = if (order == null) t.id else order[t.id] ?: (UNRANKED + t.id),
                    )
                }
            }

        /** L'heure locale de la première séance de chaque tâche, dans [timeZone] (rythme) ; vide sans fuseau. */
        fun firstStarts(sessions: List<WorkSession>, timeZone: TimeZone?): Map<Long, LocalDateTime> {
            if (timeZone == null) return emptyMap()
            val first = LinkedHashMap<Long, Instant>()
            for (s in sessions) {
                val known = first[s.taskId]
                if (known == null || s.startedAt < known) first[s.taskId] = s.startedAt
            }
            return first.mapValues { it.value.toLocalDateTime(timeZone) }
        }

        private fun past(snapshot: KairosSnapshot, now: Instant, timeZone: TimeZone?): List<Sample> = samples(
            snapshot.tasks,
            TimeTracking.spentMinutesByTask(snapshot.workSessions, now, snapshot.tasks),
            firstStarts(snapshot.workSessions, timeZone),
            snapshot.sampleOrder,
        )

        /**
         * La durée apprise de [task], ou `null` : désactivée, tâche reçue, estimation explicite, ou aucune occurrence passée.
         * Voir [EstimatorChain] : le premier niveau fiable gagne, à défaut le premier qui a des occurrences (peu fiable).
         * [samples] vient de [samples] ; l'ordre de la liste n'a pas d'importance (on retient les plus récentes par [Sample.order],
         * jamais par date de modification).
         */
        fun estimate(task: Task, samples: List<Sample>, settings: Settings, cache: ModelCache = ModelCache.shared): Estimate? =
            chain.estimate(task, History(samples, cache), settings)

        /**
         * Les durées apprises de toutes les tâches **à faire** du [snapshot] sans estimation explicite (fiables ou non), au
         * temps [now]. Un seul calcul des occurrences passées pour toutes les tâches.
         */
        fun estimates(snapshot: KairosSnapshot, now: Instant, timeZone: TimeZone? = null): Map<Long, Estimate> {
            val settings = snapshot.settings
            if (settings.learning?.enabled == false) return emptyMap()
            val past = past(snapshot, now, timeZone)
            if (past.isEmpty()) return emptyMap()
            // Une liste par espace, la même pour toutes ses tâches : le cache du modèle la reconnaît (corpus et empreinte une fois).
            val bySpace = past.groupBy { it.space }
            val out = LinkedHashMap<Long, Estimate>()
            for (t in snapshot.tasks) {
                if (t.status != TaskStatus.TODO) continue
                estimate(t, bySpace[t.space].orEmpty(), settings)?.let { out[t.id] = it }
            }
            return out
        }

        /**
         * Le [snapshot] où chaque tâche **à faire** sans estimation explicite porte sa durée apprise **fiable** comme
         * estimation (`estimatedMinutes`) : la lecture des calculs d'équipe (charge, prévisions, répartition, projection des
         * occurrences), qui lisent alors la durée apprise sans autre changement. Les tâches faites ne sont jamais touchées
         * (ni la calibration, ni les erreurs d'estimation passées). Sans durée apprise, le même objet est rendu.
         */
        fun withLearnedEstimates(snapshot: KairosSnapshot, now: Instant, timeZone: TimeZone? = null): KairosSnapshot =
            withLearned(snapshot, now, timeZone).snapshot

        /**
         * Ce qu'on attend de [task] en minutes pour les alertes du chrono : son estimation explicite, sinon sa durée apprise
         * **fiable**, sinon `null` (pas d'alerte de dépassement). Une seule tâche : un seul passage sur l'historique.
         */
        fun effectiveMinutes(task: Task, snapshot: KairosSnapshot, now: Instant, timeZone: TimeZone? = null): Int? {
            task.estimatedMinutes?.takeIf { it > 0 }?.let { return it }
            if (snapshot.settings.learning?.enabled == false) return null
            val past = past(snapshot, now, timeZone)
            return estimate(task, past, snapshot.settings)?.takeIf { it.reliable }?.minutes
        }

        /** Où en est le modèle d'un espace : la taille de son corpus et sa garde (carte « Durées apprises » des Réglages). */
        data class ModelStatus(val corpus: Int, val gate: ModelGate.Result)

        /**
         * L'état du modèle pour l'espace [space] du [snapshot] au temps [now], ou `null` s'il est coupé (durées apprises ou
         * modèle désactivés). Même corpus et même cache que les estimations.
         */
        fun modelStatus(
            snapshot: KairosSnapshot,
            now: Instant,
            space: TaskSpace = TaskSpace.PERSONAL,
            cache: ModelCache = ModelCache.shared,
            timeZone: TimeZone? = null,
        ): ModelStatus? {
            val settings = snapshot.settings
            val learning = settings.learning ?: LearningSettings()
            if (!learning.enabled || learning.model == false) return null
            val past = past(snapshot, now, timeZone)
            val prepared = cache.prepare(past.filter { it.space == space })
            val corpus = prepared.corpus
            if (corpus.isEmpty()) return ModelStatus(0, ModelGate.fromErrors(emptyList(), emptyList()))
            return ModelStatus(corpus.size, cache.state(prepared, settings) { ModelEstimator.build(corpus, settings) }.gate)
        }

        /** L'intervalle d'une estimation du modèle, en minutes (prévisions d'équipe, docs/spec/estimateur-modele.md § Prévisions). */
        data class Spread(val minutes: Int, val low: Int, val high: Int)

        /** Les intervalles des estimations **fiables** (modèle, et série ou titre d'au moins deux occurrences) : ce que les prévisions tirent tâche par tâche. */
        fun spreads(estimates: Map<Long, Estimate>): Map<Long, Spread> =
            estimates.filterValues { it.reliable && it.low != null && it.high != null }
                .mapValues { (_, e) -> Spread(e.minutes, e.low!!, e.high!!) }

        /** L'instantané où les durées apprises fiables valent estimation ([withLearnedEstimates]), et les intervalles du modèle. */
        class Learned(val snapshot: KairosSnapshot, val spreads: Map<Long, Spread>)

        fun withLearned(snapshot: KairosSnapshot, now: Instant, timeZone: TimeZone? = null): Learned {
            val estimates = estimates(snapshot, now, timeZone)
            val applied = appliedMinutes(estimates)
            if (applied.isEmpty()) return Learned(snapshot, emptyMap())
            return Learned(
                snapshot.copy(tasks = snapshot.tasks.map { t -> applied[t.id]?.let { t.copy(estimatedMinutes = it) } ?: t }),
                spreads(estimates),
            )
        }

        /** Les durées apprises **fiables**, en minutes : ce que le planning, les alertes et les projections appliquent. */
        fun appliedMinutes(estimates: Map<Long, Estimate>): Map<Long, Int> =
            estimates.filterValues { it.reliable }.mapValues { it.value.minutes }
    }
}
