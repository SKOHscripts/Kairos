package com.skohscripts.kairos.core.guide

import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.GuideSettings
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.stats.BestHours
import com.skohscripts.kairos.core.stats.EstimateCorrection
import com.skohscripts.kairos.core.stats.TaskStats
import com.skohscripts.kairos.core.team.LoadLevel
import com.skohscripts.kairos.core.team.TeamLoad
import com.skohscripts.kairos.core.team.TeamMembers
import com.skohscripts.kairos.core.team.TeamSignals
import com.skohscripts.kairos.core.team.exchange.ExchangeStatus
import com.skohscripts.kairos.core.text.SearchFold
import kotlin.math.abs
import kotlin.math.ln
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Groupe d'un conseil, dans l'ordre de priorité (docs/spec/conseils.md, comportement attendu point 4). */
enum class TipGroup { DATA, STATS, SETTINGS, FEATURES, TEAM_RISK, TEAM_FEATURES }

/** Espace où un conseil est proposé : un conseil Perso dans l'espace Perso, un conseil d'équipe dans l'espace Équipe. */
enum class TipSpace { PERSONAL, TEAM }

/** Plateforme où tourne Kairos : certains conseils n'existent que sur l'une d'elles. */
enum class TipPlatform { ANDROID, DESKTOP, WEB }

/**
 * Un conseil (docs/spec/conseils.md, issue #221), dans l'ordre de priorité. [key] : identifiant stable, écrit dans la mémoire du
 * guide. [card] : la carte du catalogue que le conseil fait découvrir ; `core` ne connaît pas le catalogue (module `ui`), le lien
 * est vérifié par `TipsCatalogTest` (§ Concordance). [recurring] : revient [Tips.RECURRING_DAYS] jours après « Montre-moi ».
 */
enum class TipId(
    val key: String,
    val group: TipGroup,
    val card: String,
    val recurring: Boolean = false,
    val platforms: Set<TipPlatform> = TipPlatform.entries.toSet(),
) {
    DATA_SAFETY("data_safety", TipGroup.DATA, "data"),
    STATS_FIRST("stats_first", TipGroup.STATS, "stats"),
    STATS_CALIBRATION("stats_calibration", TipGroup.STATS, "stats"),
    STATS_CORRECT("stats_correct", TipGroup.STATS, "stats_correct"),
    STATS_HOURS("stats_hours", TipGroup.STATS, "besthours"),
    WEEK_REVIEW("week_review", TipGroup.STATS, "recap", recurring = true),
    WORK_HOURS("work_hours", TipGroup.SETTINGS, "hours"),
    BEST_HOURS("best_hours", TipGroup.SETTINGS, "besthours_apply"),
    REMINDERS("reminders", TipGroup.SETTINGS, "reminders"),
    DAILY_SUMMARY("daily_summary", TipGroup.SETTINGS, "daily_summary"),
    TIMER_LIMITS("timer_limits", TipGroup.SETTINGS, "timer_guard"),
    CALENDAR_IMPORT("calendar_import", TipGroup.SETTINGS, "calendar_import"),
    DARK_THEME("dark_theme", TipGroup.SETTINGS, "darktheme"),
    TIMER("timer", TipGroup.FEATURES, "time"),
    RECURRENCE("recurrence", TipGroup.FEATURES, "recurrence"),
    RECAP("recap", TipGroup.FEATURES, "recap"),
    WIDGETS("widgets", TipGroup.FEATURES, "widgets", platforms = setOf(TipPlatform.ANDROID)),
    SHORTCUTS("shortcuts", TipGroup.FEATURES, "shortcuts", platforms = setOf(TipPlatform.DESKTOP, TipPlatform.WEB)),
    TEAM_SIGNALS("team_signals", TipGroup.TEAM_RISK, "team_board", recurring = true),
    TEAM_LOAD("team_load", TipGroup.TEAM_RISK, "team_load", recurring = true),
    TEAM_FORECAST("team_forecast", TipGroup.TEAM_FEATURES, "team_forecast"),
    TEAM_SUGGEST("team_suggest", TipGroup.TEAM_FEATURES, "team_assign"),
    TEAM_EXCHANGES("team_exchanges", TipGroup.TEAM_FEATURES, "team_exchanges"),
    ;

    val space: TipSpace get() = if (group == TipGroup.TEAM_RISK || group == TipGroup.TEAM_FEATURES) TipSpace.TEAM else TipSpace.PERSONAL

    companion object {
        fun ofKey(key: String): TipId? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Ce que seule la plateforme sait : [darkTheme] le thème effectivement affiché, [linkedFile] un fichier lié est en place,
 * [calendarImport] l'import d'un calendrier existe ici, [widgetPlaced] un widget Kairos est posé (`null` hors Android).
 */
data class TipFacts(
    val platform: TipPlatform,
    val darkTheme: Boolean = false,
    val linkedFile: Boolean = false,
    val calendarImport: Boolean = true,
    val widgetPlaced: Boolean? = null,
)

/**
 * Le conseil dû et les chiffres de son texte : [count] le nombre principal (tâches, jours, minutes…), [ratio] un rapport
 * (réel ÷ estimé, taux de charge en %), [name] un nom (type de tâche, membre, titre), [level] un niveau de points,
 * [variant] la variante du texte (0 par défaut).
 */
data class Tip(
    val id: TipId,
    val count: Int = 0,
    val ratio: Double? = null,
    val name: String? = null,
    val level: Int? = null,
    val variant: Int = 0,
)

/**
 * Sélecteur des conseils au bon moment (docs/spec/conseils.md § Calcul pur). Pur : l'instant, le fuseau, la mémoire et les faits
 * de plateforme sont des paramètres ; rien n'est écrit ici.
 */
object Tips {
    /** Rien pendant les premiers jours d'usage. */
    const val QUIET_DAYS = 3

    /** Au plus ce nombre de conseils par période de [WEEK_DAYS] jours glissants (et un par jour). */
    const val PER_WEEK = 2
    const val WEEK_DAYS = 7

    /** « Plus tard » : le conseil revient au plus tôt après ces jours. */
    const val SNOOZE_DAYS = 14

    /** Un conseil récurrent accepté revient au plus tôt après ces jours (décision du propriétaire, 2026-11-15). */
    const val RECURRING_DAYS = 28

    /** Écrans suivis (mémoire `seenScreens`) : leur première ouverture est retenue. */
    const val SCREEN_STATS = "stats"
    const val SCREEN_RECAP = "recap"
    const val SCREEN_TEAM_FORECAST = "team_forecast"

    /** Premier raccourci clavier utilisé (même mémoire). */
    const val SCREEN_SHORTCUT = "shortcut"

    /** Écart de calibration qui vaut un conseil : réel ÷ estimé au plus 0,8 ou au moins 1,25. */
    const val BIAS_LOW = 0.8
    const val BIAS_HIGH = 1.25

    /**
     * Le conseil dû dans [space] à [now], ou `null` : interrupteur coupé, rythme épuisé, ou aucun conseil dont la condition tient
     * et que la mémoire laisse passer. Les conseils d'équipe ne sont évalués que dans l'espace Équipe activé ; les conseils Perso
     * ne lisent que les tâches propres de l'espace Perso (ni d'équipe, ni reçues), donc jamais une donnée d'équipe.
     */
    fun due(
        snapshot: KairosSnapshot,
        memory: GuideSettings?,
        facts: TipFacts,
        space: TipSpace,
        now: Instant,
        timeZone: TimeZone,
    ): Tip? {
        val mem = memory ?: GuideSettings()
        if (mem.tipsOff) return null
        if (space == TipSpace.TEAM && !snapshot.settings.teamModeEnabled) return null
        val ctx = Context(snapshot, mem, facts, now, timeZone)
        if (!pacingAllows(mem, ctx.today, ctx.baseAge(space))) return null
        return TipId.entries.asSequence()
            .filter { it.space == space && facts.platform in it.platforms && memoryAllows(it, mem, ctx.today) }
            .mapNotNull { ctx.condition(it) }
            .firstOrNull()
    }

    /**
     * Le rythme permet-il un conseil [today] ? Pas avant [QUIET_DAYS] jours d'usage ([baseAge] : `null` = aucune donnée), pas
     * deux le même jour, pas plus de [PER_WEEK] sur les [WEEK_DAYS] derniers jours.
     */
    fun pacingAllows(memory: GuideSettings, today: LocalDate, baseAge: Int?): Boolean {
        if (baseAge == null || baseAge < QUIET_DAYS) return false
        val shown = memory.tipsShown.mapNotNull(::parseDate)
        if (shown.any { it == today }) return false
        val since = today.minus(DatePeriod(days = WEEK_DAYS - 1))
        return shown.count { it in since..today } < PER_WEEK
    }

    /** La mémoire laisse-t-elle passer [id] [today] ? Ni écarté, ni reporté au-delà d'aujourd'hui, ni accepté (récurrent : depuis moins de [RECURRING_DAYS] jours). */
    fun memoryAllows(id: TipId, memory: GuideSettings, today: LocalDate): Boolean {
        if (id.key in memory.tipsDismissed) return false
        memory.tipsSnoozed[id.key]?.let(::parseDate)?.let { if (today < it) return false }
        val done = memory.tipsDone[id.key]?.let(::parseDate) ?: return true
        return id.recurring && done.daysUntil(today) >= RECURRING_DAYS
    }

    /** La mémoire après l'affichage de [id] [today] : la date rejoint les deux dernières (rythme). */
    fun shown(memory: GuideSettings, today: LocalDate): GuideSettings =
        memory.copy(tipsShown = (memory.tipsShown + today.toString()).distinct().takeLast(PER_WEEK))

    /** « Montre-moi » : [id] accepté [today] (un récurrent reviendra après [RECURRING_DAYS] jours). */
    fun accepted(memory: GuideSettings, id: TipId, today: LocalDate): GuideSettings =
        memory.copy(tipsDone = memory.tipsDone + (id.key to today.toString()), tipsSnoozed = memory.tipsSnoozed - id.key)

    /** « Plus tard » (ou dialogue fermé sans choix) : [id] attend [SNOOZE_DAYS] jours. */
    fun snoozed(memory: GuideSettings, id: TipId, today: LocalDate): GuideSettings =
        memory.copy(tipsSnoozed = memory.tipsSnoozed + (id.key to today.plus(DatePeriod(days = SNOOZE_DAYS)).toString()))

    /** « Ne plus me le proposer » : [id] écarté pour de bon. */
    fun dismissed(memory: GuideSettings, id: TipId): GuideSettings =
        memory.copy(tipsDismissed = (memory.tipsDismissed + id.key).distinct(), tipsSnoozed = memory.tipsSnoozed - id.key)

    /** L'écran suivi [screen] a été ouvert : la mémoire inchangée s'il l'était déjà. */
    fun seen(memory: GuideSettings, screen: String): GuideSettings =
        if (screen in memory.seenScreens) memory else memory.copy(seenScreens = memory.seenScreens + screen)

    private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    /** Les données lues par les conditions, calculées une fois. */
    private class Context(
        val snapshot: KairosSnapshot,
        val memory: GuideSettings,
        val facts: TipFacts,
        val now: Instant,
        val tz: TimeZone,
    ) {
        val settings: Settings = snapshot.settings
        val today: LocalDate = now.toLocalDateTime(tz).date

        /** Tâches propres de l'espace Perso, exemples exclus : jamais une tâche d'équipe ni une tâche reçue. */
        val tasks: List<Task> by lazy {
            snapshot.tasks.filter { it.space == TaskSpace.PERSONAL && it.origin == null && !ExampleData.isExample(it) }
        }
        private val taskIds by lazy { tasks.mapTo(HashSet()) { it.id } }
        val sessions: List<WorkSession> by lazy { snapshot.workSessions.filter { it.taskId in taskIds } }
        val personal: KairosSnapshot by lazy { snapshot.copy(tasks = tasks, workSessions = sessions) }
        val done: List<Task> by lazy { tasks.filter { it.status == TaskStatus.DONE } }

        /** Temps chronométré seul (sessions), par tâche. */
        val timed: Map<Long, Int> by lazy { TimeTracking.spentMinutesByTask(sessions, now) }

        /** Temps réel des statistiques (sessions et temps saisi), par tâche. */
        val spent: Map<Long, Int> by lazy { TimeTracking.spentMinutesByTask(sessions, now, tasks) }

        fun date(instant: Instant): LocalDate = instant.toLocalDateTime(tz).date
        fun daysAgo(days: Int): LocalDate = today.minus(DatePeriod(days = days))
        fun doneOn(task: Task): LocalDate? = if (task.status == TaskStatus.DONE) date(task.updatedAt) else null

        /**
         * Âge de la base en jours, depuis la plus ancienne donnée de l'utilisateur (tâches, notes, créneaux saisis ; exemples
         * exclus) ; dans l'espace Équipe, les tâches d'équipe comptent aussi. `null` : aucune donnée.
         */
        fun baseAge(space: TipSpace): Int? {
            val first = buildList {
                tasks.forEach { add(it.createdAt) }
                snapshot.notes.filter { !ExampleData.isExampleTitle(it.body) }.forEach { add(it.createdAt) }
                snapshot.timeBlocks.filter { it.source == null && !ExampleData.isExampleTitle(it.title) }.forEach { add(it.createdAt) }
                if (space == TipSpace.TEAM) teamTasks.forEach { add(it.createdAt) }
            }.minOrNull() ?: return null
            return date(first).daysUntil(today)
        }

        /**
         * Jours actifs : création ou dernière modification d'une tâche, création d'une note, début d'une session. `updatedAt` ne
         * garde que la dernière modification : le compte sous-estime l'usage, donc retarde un conseil plutôt que de l'avancer.
         */
        val activeDays: Set<LocalDate> by lazy {
            buildSet {
                tasks.forEach { add(date(it.createdAt)); add(date(it.updatedAt)) }
                snapshot.notes.filter { !ExampleData.isExampleTitle(it.body) }.forEach { add(date(it.createdAt)) }
                sessions.forEach { add(date(it.startedAt)) }
            }
        }

        fun activeIn(from: LocalDate, to: LocalDate): Int = activeDays.count { it in from..to }

        // Équipe : lues seulement par les conditions d'équipe, évaluées dans l'espace Équipe activé.
        val team: TeamSettings by lazy { settings.team ?: TeamSettings() }
        val teamTasks: List<Task> by lazy { snapshot.tasks.filter { it.space == TaskSpace.TEAM } }

        fun condition(id: TipId): Tip? = when (id) {
            TipId.DATA_SAFETY -> dataSafety()
            TipId.STATS_FIRST -> statsFirst()
            TipId.STATS_CALIBRATION -> statsCalibration()
            TipId.STATS_CORRECT -> statsCorrect()
            TipId.STATS_HOURS -> statsHours()
            TipId.WEEK_REVIEW -> weekReview()
            TipId.WORK_HOURS -> workHours()
            TipId.BEST_HOURS -> bestHours()
            TipId.REMINDERS -> reminders()
            TipId.DAILY_SUMMARY -> dailySummary()
            TipId.TIMER_LIMITS -> timerLimits()
            TipId.CALENDAR_IMPORT -> calendarImport()
            TipId.DARK_THEME -> darkTheme()
            TipId.TIMER -> timer()
            TipId.RECURRENCE -> recurrence()
            TipId.RECAP -> recap()
            TipId.WIDGETS -> widgets()
            TipId.SHORTCUTS -> shortcuts()
            TipId.TEAM_SIGNALS -> teamSignals()
            TipId.TEAM_LOAD -> teamLoad()
            TipId.TEAM_FORECAST -> teamForecast()
            TipId.TEAM_SUGGEST -> teamSuggest()
            TipId.TEAM_EXCHANGES -> teamExchanges()
        }

        private fun age(): Int = baseAge(TipSpace.PERSONAL) ?: 0

        private fun dataSafety(): Tip? {
            if (age() < 30 || tasks.size < 50 || facts.linkedFile) return null
            val last = memory.lastExport?.let(::parseDate)
            if (last != null && last.daysUntil(today) < 90) return null
            return Tip(TipId.DATA_SAFETY, count = tasks.size)
        }

        private fun statsFirst(): Tip? =
            if (age() >= 7 && done.size >= 5 && SCREEN_STATS !in memory.seenScreens) Tip(TipId.STATS_FIRST, count = done.size) else null

        /** Tâches faites, chronométrées et estimées : celles que la calibration et le biais comparent. */
        private val measured: List<Task> by lazy { done.filter { (timed[it.id] ?: 0) > 0 && (it.estimatedMinutes ?: 0) > 0 } }

        private fun statsCalibration(): Tip? =
            if (measured.size >= 10) Tip(TipId.STATS_CALIBRATION, count = measured.size) else null

        /**
         * Le groupe (type, puis niveau de points) dont le biais fiable s'écarte le plus, au moins de [BIAS_LOW] / [BIAS_HIGH], et
         * au moins une tâche à corriger (`EstimateCorrection`). `TaskStats.estimationBias` par groupe : le calcul des statistiques.
         */
        private fun statsCorrect(): Tip? {
            if (EstimateCorrection.candidates(personal, now).isEmpty()) return null
            val byType = measured.filter { it.taskType.isNotBlank() }.groupBy { it.taskType }
                .mapNotNull { (type, list) -> TaskStats.estimationBias(list, spent)?.let { Tip(TipId.STATS_CORRECT, count = it.count, ratio = it.ratio, name = type) } }
            val byPoints = measured.filter { (it.fibonacciPoints ?: 0) > 0 }.groupBy { it.fibonacciPoints!! }
                .mapNotNull { (points, list) -> TaskStats.estimationBias(list, spent)?.let { Tip(TipId.STATS_CORRECT, count = it.count, ratio = it.ratio, level = points, variant = 1) } }
            return (byType + byPoints)
                .filter { it.count >= TaskStats.MIN_SAMPLE && it.ratio != null && (it.ratio <= BIAS_LOW || it.ratio >= BIAS_HIGH) }
                .maxByOrNull { abs(ln(it.ratio!!)) }
        }

        private val reliableType: String? by lazy {
            BestHours.measure(tasks, sessions, now, settings, tz).profiles.firstOrNull { it.reliable }?.type
        }

        private fun statsHours(): Tip? = reliableType?.let { Tip(TipId.STATS_HOURS, name = it) }

        private fun weekReview(): Tip? {
            if (today.dayOfWeek !in setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)) return null
            val monday = TaskStats.monday(today)
            val lastWeek = monday.minus(DatePeriod(days = 7))..monday.minus(DatePeriod(days = 1))
            val count = done.count { doneOn(it)?.let { d -> d in lastWeek } == true }
            return if (count >= 5) Tip(TipId.WEEK_REVIEW, count = count) else null
        }

        private fun workHours(): Tip? {
            val defaults = Settings()
            if (settings.schedule != null || settings.workdayStartHour != defaults.workdayStartHour || settings.workdayEndHour != defaults.workdayEndHour) return null
            val from = daysAgo(13)
            val days = sessions.mapNotNull { s ->
                val start = s.startedAt.toLocalDateTime(tz)
                if (start.date < from || start.date > today) null
                else if (start.hour < settings.workdayStartHour || start.hour >= settings.workdayEndHour) start.date else null
            }.toSet()
            return if (days.size >= 3) Tip(TipId.WORK_HOURS, count = days.size) else null
        }

        private fun bestHours(): Tip? {
            if (settings.schedule?.bestHours?.enabled == true) return null
            val type = reliableType ?: return null
            return Tip(TipId.BEST_HOURS, name = type)
        }

        private fun reminders(): Tip? {
            if (settings.reminders?.enabled == true) return null
            val from = daysAgo(30)
            val yesterday = daysAgo(1)
            val missed = tasks.count { t ->
                val deadline = t.deadline ?: return@count false
                deadline in from..yesterday && when (t.status) {
                    TaskStatus.TODO -> true
                    TaskStatus.DONE -> (doneOn(t) ?: deadline) > deadline
                    TaskStatus.ARCHIVED -> false
                }
            }
            if (missed >= 3) return Tip(TipId.REMINDERS, count = missed)
            val until = today.plus(DatePeriod(days = 13))
            val fixed = tasks.count { t -> t.status == TaskStatus.TODO && t.pinnedStart?.date?.let { it in today..until } == true }
            return if (fixed >= 3) Tip(TipId.REMINDERS, count = fixed, variant = 1) else null
        }

        private fun dailySummary(): Tip? {
            if (settings.reminders?.dailySummary == true) return null
            val thisWeek = activeIn(daysAgo(6), today)
            val lastWeek = activeIn(daysAgo(13), daysAgo(7))
            return if (thisWeek >= 5 && lastWeek >= 5) Tip(TipId.DAILY_SUMMARY, count = thisWeek + lastWeek) else null
        }

        private fun timerLimits(): Tip? {
            val from = daysAgo(13)
            val longest = sessions.filter { it.endedAt != null && date(it.startedAt) >= from }
                .maxOfOrNull { TimeTracking.sessionMinutes(it, now) } ?: return null
            return if (longest >= settings.timerIdleAlertMinutes) Tip(TipId.TIMER_LIMITS, count = longest) else null
        }

        private fun calendarImport(): Tip? {
            if (!facts.calendarImport || snapshot.calendarSources.isNotEmpty()) return null
            val from = daysAgo(13)
            val manual = snapshot.timeBlocks.count {
                it.source == null && it.kind == BlockKind.BUSY && !ExampleData.isExampleTitle(it.title) && date(it.createdAt) >= from
            }
            return if (manual >= 5) Tip(TipId.CALENDAR_IMPORT, count = manual) else null
        }

        private fun darkTheme(): Tip? {
            if (facts.darkTheme) return null
            val from = daysAgo(13)
            val late = buildSet {
                sessions.forEach { s -> s.startedAt.toLocalDateTime(tz).let { if (it.hour >= 20) add(it.date) } }
                tasks.forEach { t -> listOf(t.createdAt, t.updatedAt).forEach { i -> i.toLocalDateTime(tz).let { if (it.hour >= 20) add(it.date) } } }
            }.count { it in from..today }
            return if (late >= 5) Tip(TipId.DARK_THEME, count = late) else null
        }

        private fun timer(): Tip? =
            if (sessions.isEmpty() && age() >= 7 && done.size >= 5) Tip(TipId.TIMER, count = done.size) else null

        private fun recurrence(): Tip? {
            if (tasks.any { it.recurrence != TaskRecurrence.NONE }) return null
            val from = daysAgo(60)
            val repeated = tasks.filter { it.recurrence == TaskRecurrence.NONE && date(it.createdAt) >= from }
                .groupBy { SearchFold.fold(it.title.trim()) }
                .values.filter { it.size >= 3 }
                .maxByOrNull { it.size } ?: return null
            return Tip(TipId.RECURRENCE, count = repeated.size, name = repeated.maxBy { it.createdAt }.title.trim())
        }

        private fun recap(): Tip? = if (age() >= 14 && SCREEN_RECAP !in memory.seenScreens) Tip(TipId.RECAP) else null

        private fun widgets(): Tip? {
            if (facts.widgetPlaced != false) return null
            val active = activeIn(daysAgo(13), today)
            return if (active >= 10) Tip(TipId.WIDGETS, count = active) else null
        }

        private fun shortcuts(): Tip? = if (age() >= 7 && SCREEN_SHORTCUT !in memory.seenScreens) Tip(TipId.SHORTCUTS) else null

        private fun teamSignals(): Tip? {
            val holidays = Workdays.holidaysFor(today, settings.holidayCountryCode, settings.extraHolidays)
            val flagged = teamTasks.count { TeamSignals.of(it, snapshot.teamEvents, today, tz, settings, holidays).isNotEmpty() }
            return if (flagged >= 3) Tip(TipId.TEAM_SIGNALS, count = flagged) else null
        }

        private fun teamLoad(): Tip? {
            val worst = TeamLoad.build(snapshot, now, tz).members
                .filter { it.level != LoadLevel.OK }
                .maxByOrNull { it.ratePercent ?: Double.MAX_VALUE } ?: return null
            return Tip(TipId.TEAM_LOAD, count = team.horizonWeeks, ratio = worst.ratePercent, name = worst.member.name)
        }

        private fun teamForecast(): Tip? {
            if (SCREEN_TEAM_FORECAST in memory.seenScreens) return null
            val finished = teamTasks.count { it.status == TaskStatus.DONE }
            if (finished < team.minSamples) return null
            if (teamTasks.none { it.status == TaskStatus.TODO && it.deadline != null }) return null
            return Tip(TipId.TEAM_FORECAST, count = finished)
        }

        private fun teamSuggest(): Tip? {
            if (TeamMembers.active(snapshot.members).size < 2) return null
            val open = teamTasks.count { it.status == TaskStatus.TODO && it.assigneeId == null && !it.needsProcessing(settings.typeRequired) }
            return if (open >= 5) Tip(TipId.TEAM_SUGGEST, count = open) else null
        }

        private fun teamExchanges(): Tip? {
            val first = teamTasks.minOfOrNull { it.createdAt } ?: return null
            if (date(first).daysUntil(today) < 14) return null
            val exchanges = ExchangeStatus.of(snapshot.members, snapshot.teamEvents)
            if (exchanges.any { it.lastPackAt != null }) return null
            val others = exchanges.mapTo(HashSet()) { it.member.id }
            val assigned = teamTasks.count { it.status == TaskStatus.TODO && it.assigneeId in others }
            return if (assigned >= 5) Tip(TipId.TEAM_EXCHANGES, count = assigned) else null
        }
    }
}
