package com.skohscripts.kairos.core.habit

import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.engine.SpentReview
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Statut d'une habitude, dérivé des seules réponses (docs/spec/habitudes.md § 4). */
enum class HabitStatus { IN_PROGRESS, INSTALLED }

/** Une réponse à la question d'automatisme : [at] la date de fin de l'occurrence cochée, [score] de 1 à 7. */
data class HabitAnswer(val at: Instant, val score: Int)

/**
 * Régularité observée d'une série (docs/spec/habitudes.md § 7) : [minuteOfDay] l'heure médiane arrondie au quart d'heure
 * (préremplissage de l'heure fixe), [done] et [expected] les occurrences faites et prévues de la fenêtre, [stable] celles faites
 * à une heure au plus de la médiane.
 */
data class Regularity(val minuteOfDay: Int, val done: Int, val expected: Int, val stable: Int)

/** Série régulière à proposer : [task] son occurrence ouverte, [regularity] ce qui la rend régulière. */
data class HabitSuggestion(val task: Task, val regularity: Regularity)

/**
 * Habitudes (docs/spec/habitudes.md, issue #284). Une habitude est une **série récurrente** de l'espace Perso dont les
 * occurrences portent [Task.habitSince] et [Task.habitAfter] (recopiés comme l'heure fixe) ; chaque réponse à la question
 * d'automatisme est notée sur l'occurrence cochée ([Task.habitScore]). Pur : heure, fuseau et fériés en paramètres.
 *
 * Les seuils sont des **décisions de produit** (§ 7 de la spec), pas des faits scientifiques.
 */
object Habits {
    /** Règles éligibles : les études portent sur des gestes quotidiens ou hebdomadaires ; une règle mensuelle est trop rare. */
    val ELIGIBLE_RULES: Set<TaskRecurrence> = setOf(TaskRecurrence.DAILY, TaskRecurrence.WEEKDAYS, TaskRecurrence.WEEKLY)

    /** Longueur maximale de la phrase « Après… ». */
    const val MAX_AFTER = 80

    const val MIN_SCORE = 1
    const val MAX_SCORE = 7

    /** « Installée » : au moins tant de réponses… */
    const val INSTALLED_MIN_ANSWERS = 3

    /** … et les deux dernières à cette valeur au moins. */
    const val INSTALLED_SCORE = 6

    /** Une installée redevient « en cours » quand une réponse tombe à cette valeur ou moins. */
    const val DROP_SCORE = 4

    /** Au plus une question tous les tant de jours par habitude. */
    const val ASK_EVERY_DAYS = 14

    /** Série régulière : ancienneté minimale (jours). */
    const val MIN_AGE_DAYS = 14

    /** Série régulière : fenêtre examinée (jours pleins avant aujourd'hui), 6 semaines. */
    const val WINDOW_DAYS = 42

    /** Série régulière : occurrences faites minimales dans la fenêtre. */
    const val MIN_DONE = 5

    /** Série régulière : part minimale des occurrences prévues qui sont faites. */
    const val MIN_DONE_RATIO = 0.75

    /** Série régulière : écart maximal à l'heure médiane (minutes) d'une occurrence « à heure stable ». */
    const val HOUR_TOLERANCE_MINUTES = 60

    /** Série régulière : part minimale des occurrences faites à heure stable. */
    const val MIN_STABLE_RATIO = 2.0 / 3.0

    /** Une tâche peut-elle être (ou rester) une habitude ? Perso, non reçue, règle éligible, pas un exemple. */
    fun eligible(task: Task): Boolean =
        task.space == TaskSpace.PERSONAL && task.origin == null && task.recurrence in ELIGIBLE_RULES && !ExampleData.isExample(task)

    /** La tâche est-elle une habitude active ? Déclarée et toujours éligible. */
    fun isHabit(task: Task): Boolean = task.habitSince != null && eligible(task)

    /** Clé de la série : l'identifiant de sa première tâche (docs/spec/durees-apprises.md § Série stable). */
    fun seriesKey(task: Task): Long = task.seriesId ?: task.id

    /** Les occurrences de la série de [task] dans [tasks] (Perso, non reçues). */
    fun seriesOf(task: Task, tasks: List<Task>): List<Task> {
        val key = seriesKey(task)
        return tasks.filter { it.space == TaskSpace.PERSONAL && it.origin == null && seriesKey(it) == key }
    }

    /**
     * Les réponses d'une série, dans l'ordre : les occurrences faites notées, postérieures au dernier [Task.habitSince] connu
     * (redéclarer repart de zéro). Date d'une réponse : la date de fin de l'occurrence (`updatedAt`, que la réponse ne touche pas).
     */
    fun answers(series: List<Task>): List<HabitAnswer> {
        val since = series.mapNotNull { it.habitSince }.maxOrNull() ?: return emptyList()
        return series
            .filter { it.status == TaskStatus.DONE && it.habitScore != null && it.updatedAt >= since }
            .sortedWith(compareBy<Task> { it.updatedAt }.thenBy { it.id })
            .map { HabitAnswer(it.updatedAt, it.habitScore!!.coerceIn(MIN_SCORE, MAX_SCORE)) }
    }

    /**
     * Statut après ces réponses, parcourues dans l'ordre, avec hystérésis : « installée » dès [INSTALLED_MIN_ANSWERS] réponses
     * dont les deux dernières ≥ [INSTALLED_SCORE] ; une installée revient « en cours » à une réponse ≤ [DROP_SCORE].
     */
    fun status(scores: List<Int>): HabitStatus {
        var status = HabitStatus.IN_PROGRESS
        scores.forEachIndexed { i, score ->
            status = when (status) {
                HabitStatus.IN_PROGRESS ->
                    if (i + 1 >= INSTALLED_MIN_ANSWERS && score >= INSTALLED_SCORE && scores[i - 1] >= INSTALLED_SCORE) {
                        HabitStatus.INSTALLED
                    } else {
                        HabitStatus.IN_PROGRESS
                    }
                HabitStatus.INSTALLED -> if (score <= DROP_SCORE) HabitStatus.IN_PROGRESS else HabitStatus.INSTALLED
            }
        }
        return status
    }

    /** La réponse [score] fait-elle passer l'habitude à « installée » ? Déclenche les félicitations, une fois par passage. */
    fun justInstalled(before: List<Int>, score: Int): Boolean =
        status(before) == HabitStatus.IN_PROGRESS && status(before + score) == HabitStatus.INSTALLED

    /**
     * Faut-il poser la question d'automatisme en cochant [task] ? Lu **avant** la coche, sur l'instantané perso, comme
     * `SpentReview.asks`. Réglage actif, coche non groupée ([batched]), tâche à faire et habitude active, et aucune réponse depuis
     * la déclaration ou la dernière il y a [ASK_EVERY_DAYS] jours au moins. La règle « reposer après 28 jours sans occurrence
     * faite » en découle : 28 jours sans occurrence faite, c'est 28 jours sans réponse.
     */
    fun asks(snapshot: KairosSnapshot, task: Task, now: Instant, batched: Boolean): Boolean {
        if (!snapshot.settings.habitsOn || batched || task.status != TaskStatus.TODO || !isHabit(task)) return false
        val last = answers(seriesOf(task, snapshot.tasks)).lastOrNull() ?: return true
        return now - last.at >= ASK_EVERY_DAYS.days
    }

    /** Les scores de la série de [task], dans l'ordre (courbe, statut). */
    fun scores(task: Task, tasks: List<Task>): List<Int> = answers(seriesOf(task, tasks)).map { it.score }

    /** Statut de l'habitude de [task], ou `null` si ce n'est pas une habitude active. */
    fun statusOf(task: Task, tasks: List<Task>): HabitStatus? = if (isHabit(task)) status(scores(task, tasks)) else null

    /**
     * Les coches groupées, reconstituées après coup (l'écran ne les retient pas) : une tâche Perso faite dont une autre tâche
     * Perso faite a sa date de fin moins de `SpentReview.BATCH_SECONDS` avant. Rend leurs identifiants.
     */
    fun batchedChecks(tasks: List<Task>): Set<Long> {
        val done = tasks.filter { it.space == TaskSpace.PERSONAL && it.status == TaskStatus.DONE }.sortedBy { it.updatedAt }
        val out = HashSet<Long>()
        for (i in 1 until done.size) {
            if (SpentReview.batched(done[i - 1].updatedAt, done[i].updatedAt)) out += done[i].id
        }
        return out
    }

    /**
     * Heure locale (minutes depuis minuit) où une occurrence faite a été faite : le début de son premier chrono s'il y en a eu
     * un, sinon sa date de fin (`updatedAt`) ; `null` pour une coche groupée sans chrono (cocher par lots ne dit rien de l'heure).
     */
    fun doneMinute(task: Task, firstStarts: Map<Long, kotlinx.datetime.LocalDateTime>, batched: Set<Long>, timeZone: TimeZone): Int? {
        firstStarts[task.id]?.let { return it.hour * 60 + it.minute }
        if (task.id in batched) return null
        val t = task.updatedAt.toLocalDateTime(timeZone)
        return t.hour * 60 + t.minute
    }

    /**
     * La série est-elle régulière (docs/spec/habitudes.md § 7) ? Ancienneté ≥ [MIN_AGE_DAYS] ; fenêtre = les [WINDOW_DAYS] jours
     * pleins avant [today], bornée au début de la série ; faites ≥ [MIN_DONE] et ≥ [MIN_DONE_RATIO] des prévues ; à heure
     * stable ≥ [MIN_STABLE_RATIO] des faites. Rend la régularité, ou `null`.
     */
    fun regular(
        series: List<Task>,
        today: LocalDate,
        timeZone: TimeZone,
        holidays: Set<LocalDate>,
        firstStarts: Map<Long, kotlinx.datetime.LocalDateTime>,
        batched: Set<Long>,
    ): Regularity? {
        if (series.isEmpty()) return null
        val current = series.maxWith(compareBy<Task> { it.createdAt }.thenBy { it.id })
        if (!eligible(current)) return null
        val start = series.minOf { it.createdAt }.toLocalDateTime(timeZone).date
        if (start.daysUntil(today) < MIN_AGE_DAYS) return null
        val from = maxOf(start, today.minus(DatePeriod(days = WINDOW_DAYS)))
        val to = today.minus(DatePeriod(days = 1))
        val expected = expected(current, from, to, holidays)
        if (expected <= 0) return null
        val done = series.filter {
            it.status == TaskStatus.DONE && SpentReview.doneOn(it, timeZone).let { d -> d >= from && d <= to }
        }
        val doneCount = minOf(done.size, expected)
        if (doneCount < MIN_DONE || doneCount < MIN_DONE_RATIO * expected) return null
        val minutes = done.map { doneMinute(it, firstStarts, batched, timeZone) }
        val known = minutes.filterNotNull().sorted()
        if (known.isEmpty()) return null
        val median = known[(known.size - 1) / 2]
        val stable = known.count { kotlin.math.abs(it - median) <= HOUR_TOLERANCE_MINUTES }
        if (stable < MIN_STABLE_RATIO * done.size) return null
        val rounded = ((median + 7) / 15 * 15) % (24 * 60)
        return Regularity(rounded, doneCount, expected, stable)
    }

    /** Occurrences prévues par la règle de [task] entre [from] et [to] inclus (jours ouvrés hors week-ends et fériés). */
    internal fun expected(task: Task, from: LocalDate, to: LocalDate, holidays: Set<LocalDate>): Int {
        if (to < from) return 0
        var count = 0
        var day = from
        val anchor = task.recurrenceDayOfWeek ?: task.deadline?.let(Workdays::weekday)
        while (day <= to) {
            count += when (task.recurrence) {
                TaskRecurrence.DAILY -> 1
                TaskRecurrence.WEEKDAYS -> if (Workdays.isWorkday(day, holidays)) 1 else 0
                TaskRecurrence.WEEKLY -> if (anchor == null || Workdays.weekday(day) == anchor) 1 else 0
                else -> 0
            }
            day = day.plus(DatePeriod(days = 1))
        }
        // Une hebdomadaire sans jour connu compte une occurrence par semaine pleine.
        if (task.recurrence == TaskRecurrence.WEEKLY && anchor == null) count /= 7
        return count
    }

    /**
     * Séries régulières à proposer (le conseil « En faire une habitude ? ») : séries Perso éligibles avec une occurrence à faire,
     * pas déjà habitude, pas écartées ([dismissed], clés de série), régulières. Les plus régulières d'abord (part à heure stable,
     * puis nombre de faites), puis par titre. Vide si les habitudes sont désactivées.
     */
    fun suggestions(snapshot: KairosSnapshot, today: LocalDate, timeZone: TimeZone, holidays: Set<LocalDate>, dismissed: Set<Long>): List<HabitSuggestion> {
        if (!snapshot.settings.habitsOn) return emptyList()
        val personal = snapshot.tasks.filter { it.space == TaskSpace.PERSONAL && it.origin == null }
        val firstStarts = DurationEstimator.firstStarts(snapshot.workSessions, timeZone)
        val batched = batchedChecks(snapshot.tasks)
        return personal.groupBy(::seriesKey)
            .filterKeys { it !in dismissed }
            .mapNotNull { (_, series) ->
                val open = series.filter { it.status == TaskStatus.TODO && eligible(it) }.maxByOrNull { it.createdAt }
                    ?: return@mapNotNull null
                if (series.any { isHabit(it) && it.status == TaskStatus.TODO }) return@mapNotNull null
                regular(series, today, timeZone, holidays, firstStarts, batched)?.let { HabitSuggestion(open, it) }
            }
            .sortedWith(
                compareByDescending<HabitSuggestion> { it.regularity.stable.toDouble() / it.regularity.done }
                    .thenByDescending { it.regularity.done }
                    .thenBy { it.task.title },
            )
    }

    /** Heure observée de la série de [task] (préremplissage), ou `null` si la série n'est pas régulière. */
    fun observedMinute(snapshot: KairosSnapshot, task: Task, today: LocalDate, timeZone: TimeZone, holidays: Set<LocalDate>): Int? =
        regular(
            seriesOf(task, snapshot.tasks), today, timeZone, holidays,
            DurationEstimator.firstStarts(snapshot.workSessions, timeZone), batchedChecks(snapshot.tasks),
        )?.minuteOfDay

    /** Les habitudes actives (une occurrence à faire par série), par titre : la carte des Statistiques. */
    fun active(tasks: List<Task>): List<Task> =
        tasks.filter { it.status == TaskStatus.TODO && isHabit(it) }
            .groupBy(::seriesKey).values.map { s -> s.maxBy { it.createdAt } }
            .sortedBy { it.title.lowercase() }

    /** Phrase « Après… » nettoyée : espaces de bord retirés, [MAX_AFTER] caractères au plus. */
    fun cleanAfter(text: String): String = text.trim().take(MAX_AFTER)
}
