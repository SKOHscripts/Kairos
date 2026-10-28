package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.BestHoursSettings
import com.skohscripts.kairos.core.model.DaySchedule
import com.skohscripts.kairos.core.model.FIBONACCI_SCALE
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.LevelNames
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import com.skohscripts.kairos.core.model.ReminderSettings
import com.skohscripts.kairos.core.model.ScheduleSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.WorkContext
import com.skohscripts.kairos.core.model.WorkWindow
import com.skohscripts.kairos.core.model.TeamSettings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** Nature d'un champ de réglage : ce qu'on y saisit et comment on le valide. */
enum class FieldKind { INT, DECIMAL, BOOL, TEXT, DATES, COLOR, TIME }

/**
 * Un réglage éditable (docs/spec/reglages.md) : clé stable (nom du champ
 * de [Settings]), nature, bornes **incluses**, ou
 * borne basse exclue (`gt`) si [minExclusive]. La valeur circule en texte :
 * celui du formulaire.
 */
class SettingField internal constructor(
    val key: String,
    val kind: FieldKind,
    val min: Double? = null,
    val max: Double? = null,
    val minExclusive: Boolean = false,
    internal val read: (Settings) -> String,
    internal val write: (Settings, String) -> Settings,
    /** Contrôle propre à un champ texte, en plus de sa nature (`null` : texte libre). */
    internal val validate: ((String) -> FieldError?)? = null,
)

enum class FieldErrorKind { REQUIRED, NOT_INTEGER, NOT_NUMBER, TOO_SMALL, TOO_LARGE, INVALID_DATE, INVALID_COLOR, INVALID_TIME, DUPLICATE, UNKNOWN_CONTEXT }

/** Erreur d'un champ ; [bound] est la borne franchie (« 0 », « 23 ») ou la date illisible. */
data class FieldError(val kind: FieldErrorKind, val bound: String? = null)

/** Règles entre champs, vérifiées seulement si chaque champ est valide. */
enum class GeneralError { WORKDAY_ORDER, DIP_ORDER }

data class SettingsValidation(
    /** Les réglages à enregistrer, `null` s'il y a la moindre erreur (jamais d'état partiel). */
    val settings: Settings?,
    val fieldErrors: Map<String, FieldError>,
    val general: GeneralError?,
)

/**
 * Formulaire des Réglages, pur : valeurs affichées ([values]) et validation
 * par champ ([validate]) : bornes incluses et règles entre champs
 * (docs/spec/reglages.md). Un nombre décimal accepte la virgule.
 */
object SettingsForm {
    private fun int(key: String, min: Int?, max: Int? = null, get: (Settings) -> Int, set: (Settings, Int) -> Settings) =
        SettingField(key, FieldKind.INT, min?.toDouble(), max?.toDouble(), read = { get(it).toString() }, write = { s, v -> set(s, v.trim().toInt()) })

    private fun decimal(key: String, min: Double, exclusive: Boolean, get: (Settings) -> Double, set: (Settings, Double) -> Settings) =
        SettingField(key, FieldKind.DECIMAL, min, null, exclusive, read = { formatDecimal(get(it)) }, write = { s, v -> set(s, parseDecimal(v)!!) })

    private fun bool(key: String, get: (Settings) -> Boolean, set: (Settings, Boolean) -> Settings) =
        SettingField(key, FieldKind.BOOL, read = { get(it).toString() }, write = { s, v -> set(s, v == "true") })

    private fun text(key: String, kind: FieldKind, get: (Settings) -> String, set: (Settings, String) -> Settings) =
        SettingField(key, kind, read = get, write = { s, v -> set(s, v.trim()) })

    /**
     * Réglage d'équipe : lu sur [TeamSettings] (défauts si `team` est nul), écrit
     * par [change]. Règle d'isolation du mode solo : tant que `team` est nul et
     * que la valeur écrite est celle par défaut, `team` **reste nul** (sauver le
     * formulaire en mode solo ne crée jamais d'objet `team`, donc rien n'entre
     * dans l'export ni dans la base d'une installation solo).
     */
    private fun team(
        key: String,
        kind: FieldKind,
        min: Double? = null,
        max: Double? = null,
        minExclusive: Boolean = false,
        get: (TeamSettings) -> String,
        change: (TeamSettings, String) -> TeamSettings,
    ) =
        SettingField(
            key, kind, min, max, minExclusive,
            read = { get(it.team ?: TeamSettings()) },
            write = { s, v ->
                val value = v.trim()
                val current = s.team ?: TeamSettings()
                val next = change(current, value)
                if (s.team == null && next == current) s else s.copy(team = next)
            },
        )

    private fun teamInt(key: String, min: Int, max: Int? = null, get: (TeamSettings) -> Int, change: (TeamSettings, Int) -> TeamSettings) =
        team(key, FieldKind.INT, min.toDouble(), max?.toDouble(), get = { get(it).toString() }) { t, v -> change(t, v.toInt()) }

    private fun teamDecimal(
        key: String,
        min: Double,
        exclusive: Boolean,
        max: Double? = null,
        get: (TeamSettings) -> Double,
        change: (TeamSettings, Double) -> TeamSettings,
    ) =
        team(key, FieldKind.DECIMAL, min, max, exclusive, get = { formatDecimal(get(it)) }) { t, v -> change(t, parseDecimal(v)!!) }

    /**
     * Horaire du jour [day] (0 = lundi) : lu sur [Settings.schedule], écrit par [change] (`null` = « comme d'habitude »).
     * Même règle d'isolation que [team] : sans horaire propre ni contexte, [Settings.schedule] **reste nul** (sauver le
     * formulaire sans y toucher n'en crée jamais, donc rien n'entre dans l'export). Les champs d'un jour s'écrivent dans
     * l'ordre de [FIELDS] : `mode`, `count`, puis pour chaque plage `start`, `end`, `ctx` ; les heures ne comptent que pour
     * un jour « custom ».
     */
    private fun withDay(settings: Settings, day: Int, change: (DaySchedule?) -> DaySchedule?): Settings = withSchedule(settings) { schedule ->
        val updated = change(schedule.weekdays[day])
        schedule.copy(weekdays = if (updated == null) schedule.weekdays - day else schedule.weekdays + (day to updated))
    }

    /** Écrit [change] sur [Settings.schedule] ; le sous-objet redevient nul quand il ne porte plus rien. */
    private fun withSchedule(settings: Settings, change: (ScheduleSettings) -> ScheduleSettings): Settings {
        val next = change(settings.schedule ?: ScheduleSettings())
        return settings.copy(schedule = next.takeIf { it.isCustomized() })
    }

    /** Vrai si le sous-objet porte quelque chose : sinon il redevient nul (rien n'entre dans l'export). */
    private fun ScheduleSettings.isCustomized() = weekdays.isNotEmpty() || contexts.isNotEmpty() || bestHours != null

    private fun custom(settings: Settings, day: Int): DaySchedule? = settings.schedule?.weekdays?.get(day)?.takeIf { !it.free }

    /** Les plages d'un jour « personnalisé » : la liste du lot B2, ou la plage unique de la 3.4 ; vide pour un autre jour. */
    private fun windowsOf(settings: Settings, day: Int): List<WorkWindow> {
        val own = custom(settings, day) ?: return emptyList()
        if (own.windows.isNotEmpty()) return own.windows
        val start = own.startHour
        val end = own.endHour
        return if (start != null && end != null) listOf(WorkWindow(start, end)) else emptyList()
    }

    /** Range les plages : une seule plage sans contexte garde la forme de la 3.4 (`startHour`, `endHour`), sinon la liste. */
    private fun storeWindows(settings: Settings, day: Int, windows: List<WorkWindow>): Settings = withDay(settings, day) {
        val single = windows.singleOrNull()?.takeIf { it.context == null }
        if (single != null) DaySchedule(startHour = single.startHour, endHour = single.endHour) else DaySchedule(windows = windows)
    }

    /** La plage suivante proposée quand on en ajoute une : après la dernière, une heure, sans dépasser 23 h. */
    private fun nextWindow(last: WorkWindow?, settings: Settings): WorkWindow {
        val start = (last?.endHour ?: settings.workdayEndHour).coerceAtMost(22)
        return WorkWindow(start, start + 1)
    }

    /** Préfixe des clés de la plage [index] (0-based) du jour [day] : la première garde les clés de la 3.4. */
    private fun windowKey(day: Int, index: Int) = if (index == 0) "schedule.d$day." else "schedule.d$day.w${index + 1}."

    private fun windowField(day: Int, index: Int, name: String, kind: FieldKind, min: Double?, max: Double?, read: (WorkWindow?, Settings) -> String, write: (WorkWindow, String) -> WorkWindow): SettingField =
        SettingField(
            windowKey(day, index) + name, kind, min, max,
            read = { s -> read(windowsOf(s, day).getOrNull(index), s) },
            write = { s, v ->
                val windows = windowsOf(s, day)
                if (index < windows.size) storeWindows(s, day, windows.mapIndexed { i, w -> if (i == index) write(w, v.trim()) else w }) else s
            },
        )

    /**
     * Les champs d'un jour de la semaine : `schedule.dN.mode` (`default`, `custom`, `free`), `.count` (nombre de plages, 1 à
     * [MAX_WINDOWS]), puis pour chaque plage `.start` et `.end` (heures) et `.ctx` (nom du contexte, vide : ouverte à toute tâche) ;
     * la première plage garde les clés `schedule.dN.start|end|ctx`, les suivantes `schedule.dN.wK.start|end|ctx`.
     */
    private fun scheduleFields(day: Int): List<SettingField> = listOf(
        SettingField(
            "schedule.d$day.mode", FieldKind.TEXT,
            read = { s ->
                val own = s.schedule?.weekdays?.get(day)
                when {
                    own == null -> SCHEDULE_DEFAULT
                    own.free -> SCHEDULE_FREE
                    else -> SCHEDULE_CUSTOM
                }
            },
            write = { s, v ->
                when (v.trim()) {
                    SCHEDULE_FREE -> withDay(s, day) { DaySchedule(free = true) }
                    SCHEDULE_CUSTOM -> withDay(s, day) { own ->
                        if (own != null && !own.free && (own.windows.isNotEmpty() || (own.startHour != null && own.endHour != null))) own
                        else DaySchedule(startHour = s.workdayStartHour, endHour = s.workdayEndHour)
                    }
                    else -> withDay(s, day) { null }
                }
            },
        ),
        SettingField(
            "schedule.d$day.count", FieldKind.INT, 1.0, MAX_WINDOWS.toDouble(),
            read = { s -> maxOf(1, windowsOf(s, day).size).toString() },
            write = { s, v ->
                val windows = windowsOf(s, day)
                val count = v.trim().toInt()
                if (windows.isEmpty() || count == windows.size) s
                else storeWindows(s, day, if (count < windows.size) windows.take(count) else (windows.size until count).fold(windows) { list, _ -> list + nextWindow(list.lastOrNull(), s) })
            },
        ),
    ) + (0 until MAX_WINDOWS).flatMap { index ->
        listOf(
            windowField(day, index, "start", FieldKind.INT, 0.0, 23.0, { w, s -> (w?.startHour ?: if (index == 0) s.workdayStartHour else nextWindow(windowsOf(s, day).lastOrNull(), s).startHour).toString() }) { w, v -> w.copy(startHour = v.toInt()) },
            windowField(day, index, "end", FieldKind.INT, 0.0, 23.0, { w, s -> (w?.endHour ?: if (index == 0) s.workdayEndHour else nextWindow(windowsOf(s, day).lastOrNull(), s).endHour).toString() }) { w, v -> w.copy(endHour = v.toInt()) },
            windowField(day, index, "ctx", FieldKind.TEXT, null, null, { w, _ -> w?.context.orEmpty() }) { w, v -> w.copy(context = v.ifEmpty { null }) },
        )
    }

    /** Les contextes de [settings], complétés de contextes vides jusqu'à [count] : les emplacements du formulaire sont positionnels. */
    private fun paddedContexts(settings: Settings, count: Int): List<WorkContext> {
        val list = settings.schedule?.contexts.orEmpty()
        return list + List(maxOf(0, count - list.size)) { WorkContext("") }
    }

    /**
     * Un contexte du formulaire (emplacement [slot], de 1 à [MAX_CONTEXTS]) : `schedule.cK.name` et `schedule.cK.types` (types de
     * tâche séparés par des virgules). Pendant l'écriture des champs les emplacements vides sont gardés (le formulaire est
     * positionnel) ; [validate] les retire à la fin, et rien n'est écrit tant qu'aucun contexte n'est rempli.
     */
    private fun contextFields(slot: Int): List<SettingField> {
        fun field(name: String, read: (WorkContext?) -> String, change: (WorkContext, String) -> WorkContext) = SettingField(
            "schedule.c$slot.$name", FieldKind.TEXT,
            read = { s -> read(s.schedule?.contexts?.getOrNull(slot - 1)) },
            write = { s, v ->
                val value = v.trim()
                if (s.schedule == null && value.isEmpty()) s
                else withSchedule(s) { sch ->
                    val list = paddedContexts(s, slot).toMutableList()
                    list[slot - 1] = change(list[slot - 1], value)
                    sch.copy(contexts = list)
                }
            },
        )
        return listOf(
            field("name", { it?.name.orEmpty() }) { c, v -> c.copy(name = v) },
            field("types", { it?.types.orEmpty().joinToString(",") }) { c, v -> c.copy(types = normalizeList(v).split(',').filter { it.isNotEmpty() }) },
        )
    }

    /**
     * Réglage des meilleures heures (étape C2) : lu sur [BestHoursSettings] (défauts si absent), écrit par [change]. Tant que la valeur écrite est
     * celle par défaut, `bestHours` **reste nul** (ou y revient) : sauver le formulaire sans y toucher n'écrit rien.
     */
    private fun bestHours(
        key: String,
        kind: FieldKind,
        min: Double? = null,
        max: Double? = null,
        validate: ((String) -> FieldError?)? = null,
        get: (BestHoursSettings) -> String,
        change: (BestHoursSettings, String) -> BestHoursSettings,
    ) = SettingField(
        key, kind, min, max,
        read = { get(it.schedule?.bestHours ?: BestHoursSettings()) },
        write = { s, v ->
            val next = change(s.schedule?.bestHours ?: BestHoursSettings(), v.trim())
            withSchedule(s) { it.copy(bestHours = next.takeIf { b -> b != BestHoursSettings() }) }
        },
        validate = validate,
    )

    /**
     * Les heures préférées saisies, `type=heure` séparés par des virgules (un type ne contient pas de virgule), en texte : type → heure,
     * dans l'ordre. Une entrée sans « = » est ignorée. Aucune validation (voir [validate]).
     */
    fun parsePreferred(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (entry in text.split(',')) {
            val at = entry.lastIndexOf('=')
            if (at < 0) continue
            val type = entry.substring(0, at).trim()
            if (type.isNotEmpty()) out[type] = entry.substring(at + 1).trim()
        }
        return out
    }

    /** L'inverse de [parsePreferred] : les types sans heure (texte vide) sont omis. */
    fun encodePreferred(hours: Map<String, String>): String =
        hours.filter { (type, hour) -> type.isNotBlank() && hour.isNotBlank() }.entries.joinToString(",") { (type, hour) -> "${type.trim()}=${hour.trim()}" }

    /** Une heure préférée lisible, entre 0 et 23 : sinon l'erreur de ce champ, comme un champ entier borné. */
    private fun preferredError(text: String): FieldError? {
        for (hour in parsePreferred(text).values) {
            val n = hour.toIntOrNull()
            when {
                n == null && !Regex("-?[0-9]+").matches(hour) -> return FieldError(FieldErrorKind.NOT_INTEGER)
                n == null -> return if (hour.startsWith("-")) FieldError(FieldErrorKind.TOO_SMALL, "0") else FieldError(FieldErrorKind.TOO_LARGE, "23")
                n < 0 -> return FieldError(FieldErrorKind.TOO_SMALL, "0")
                n > 23 -> return FieldError(FieldErrorKind.TOO_LARGE, "23")
            }
        }
        return null
    }

    private val BEST_HOURS_FIELDS: List<SettingField> = listOf(
        bestHours("schedule.bestHours.enabled", FieldKind.BOOL, get = { it.enabled.toString() }) { b, v -> b.copy(enabled = v == "true") },
        bestHours("schedule.bestHours.strength", FieldKind.INT, 0.0, 2.0, get = { it.strength.toString() }) { b, v -> b.copy(strength = v.toInt()) },
        bestHours(
            "schedule.bestHours.preferred", FieldKind.TEXT, validate = ::preferredError,
            get = { encodePreferred(it.preferred.mapValues { (_, hour) -> hour.toString() }) },
        ) { b, v -> b.copy(preferred = parsePreferred(v).mapValues { (_, hour) -> hour.toInt() }) },
        // Ce que mesure la détection (v2, issue #105) : normalisé à l'écriture, jamais refusé.
        bestHours("schedule.bestHours.measure", FieldKind.TEXT, get = { it.measure }) { b, v -> b.copy(measure = BestHoursSettings.normalizeMeasure(v)) },
    )

    /**
     * Réglage des durées apprises : lu sur [LearningSettings] (défauts si `learning` est nul), écrit par [change]. Même règle
     * d'isolation que [team] : tant que la valeur écrite est celle par défaut, `learning` **reste nul** (ou y revient).
     */
    private fun learning(key: String, kind: FieldKind, min: Double? = null, get: (LearningSettings) -> String, change: (LearningSettings, String) -> LearningSettings) =
        SettingField(
            key, kind, min,
            read = { get(it.learning ?: LearningSettings()) },
            write = { s, v ->
                val current = s.learning ?: LearningSettings()
                val next = change(current, v.trim())
                s.copy(learning = next.takeIf { it != LearningSettings() })
            },
        )

    private val LEARNING_FIELDS: List<SettingField> = listOf(
        learning("learning.enabled", FieldKind.BOOL, get = { it.enabled.toString() }) { l, v -> l.copy(enabled = v == "true") },
        learning("learning.history", FieldKind.INT, 3.0, get = { it.history.toString() }) { l, v -> l.copy(history = v.toInt()) },
        learning("learning.minSamples", FieldKind.INT, 2.0, get = { it.minSamples.toString() }) { l, v -> l.copy(minSamples = v.toInt()) },
    )

    /**
     * Réglage des rappels (docs/spec/rappels.md) : lu sur [ReminderSettings] (défauts si `reminders` est nul), écrit par
     * [change] ; même règle que [learning] : aux valeurs par défaut, `reminders` **reste nul** (ou y revient).
     */
    private fun reminders(key: String, kind: FieldKind, min: Double? = null, max: Double? = null, get: (ReminderSettings) -> String, change: (ReminderSettings, String) -> ReminderSettings) =
        SettingField(
            key, kind, min, max,
            read = { get(it.reminders ?: ReminderSettings()) },
            write = { s, v ->
                val next = change(s.reminders ?: ReminderSettings(), v.trim())
                s.copy(reminders = next.takeIf { it != ReminderSettings() })
            },
        )

    private val REMINDER_FIELDS: List<SettingField> = listOf(
        reminders("reminders.enabled", FieldKind.BOOL, get = { it.enabled.toString() }) { r, v -> r.copy(enabled = v == "true") },
        reminders("reminders.leadMinutes", FieldKind.INT, 0.0, ReminderSettings.MAX_LEAD.toDouble(), get = { it.leadMinutes.toString() }) { r, v -> r.copy(leadMinutes = v.toInt()) },
        reminders("reminders.nextTask", FieldKind.BOOL, get = { it.nextTask.toString() }) { r, v -> r.copy(nextTask = v == "true") },
        // Récapitulatif du jour (docs/spec/rappels.md § Récapitulatif du jour) : indépendant de « reminders.enabled ».
        reminders("reminders.dailySummary", FieldKind.BOOL, get = { it.dailySummary.toString() }) { r, v -> r.copy(dailySummary = v == "true") },
        reminders("reminders.dailySummaryTime", FieldKind.TIME, get = { it.dailySummaryTime }) { r, v -> r.copy(dailySummaryTime = normalizeTime(v)!!) },
    )

    /** Clé du champ du nom de la priorité [p] (`levelNames.p0`). */
    fun priorityNameKey(p: Int): String = "levelNames.p$p"

    /** Clé du champ du nom du palier [points] (`levelNames.pts3`). */
    fun pointsNameKey(points: Int): String = "levelNames.pts$points"

    /**
     * Nom d'un niveau (docs/spec/planning-personnalise.md § Lot D) : lu sur [LevelNames] (vide si `levelNames` est nul),
     * écrit nettoyé, vide = absent ; même règle que [learning] : sans aucun nom, `levelNames` **reste nul** (ou y revient).
     */
    private fun levelName(key: String, get: (LevelNames) -> String?, change: (LevelNames, String?) -> LevelNames) =
        SettingField(
            key, FieldKind.TEXT,
            read = { get(it.levelNames ?: LevelNames()).orEmpty() },
            write = { s, v ->
                val next = change(s.levelNames ?: LevelNames(), v.trim().ifEmpty { null })
                s.copy(levelNames = next.takeIf { it != LevelNames() })
            },
            validate = { if (it.length > LevelNames.MAX_LENGTH) FieldError(FieldErrorKind.TOO_LARGE, LevelNames.MAX_LENGTH.toString()) else null },
        )

    private fun Map<Int, String>.with(level: Int, name: String?): Map<Int, String> = if (name == null) this - level else this + (level to name)

    private val LEVEL_NAME_FIELDS: List<SettingField> =
        PRIORITY_VALUES.map { p -> levelName(priorityNameKey(p), { it.priority[p] }) { l, v -> l.copy(priority = l.priority.with(p, v)) } } +
            FIBONACCI_SCALE.map { pts -> levelName(pointsNameKey(pts), { it.points[pts] }) { l, v -> l.copy(points = l.points.with(pts, v)) } }

    const val SCHEDULE_DEFAULT = "default"
    const val SCHEDULE_CUSTOM = "custom"
    const val SCHEDULE_FREE = "free"

    /** Plages au plus par jour, et contextes au plus (docs/spec/planning-personnalise.md § Lot B2) : les champs du formulaire sont en nombre fixe. */
    const val MAX_WINDOWS = 4
    const val MAX_CONTEXTS = 4

    val FIELDS: List<SettingField> = listOf(
        int("defaultTaskDurationMinutes", 1, get = { it.defaultTaskDurationMinutes }) { s, v -> s.copy(defaultTaskDurationMinutes = v) },
        int("meetingBufferMinutes", 0, get = { it.meetingBufferMinutes }) { s, v -> s.copy(meetingBufferMinutes = v) },
        int("workdayStartHour", 0, 23, { it.workdayStartHour }) { s, v -> s.copy(workdayStartHour = v) },
        int("workdayEndHour", 0, 23, { it.workdayEndHour }) { s, v -> s.copy(workdayEndHour = v) },
        decimal("priorityValueBase", 0.0, true, { it.priorityValueBase }) { s, v -> s.copy(priorityValueBase = v) },
        int("urgencyHorizonDays", 0, get = { it.urgencyHorizonDays }) { s, v -> s.copy(urgencyHorizonDays = v) },
        decimal("urgencyPeak", 0.0, false, { it.urgencyPeak }) { s, v -> s.copy(urgencyPeak = v) },
        int("defaultFibonacciPoints", 1, get = { it.defaultFibonacciPoints }) { s, v -> s.copy(defaultFibonacciPoints = v) },
        bool("cognitiveDipEnabled", { it.cognitiveDipEnabled }) { s, v -> s.copy(cognitiveDipEnabled = v) },
        int("cognitiveDipStartHour", 0, 23, { it.cognitiveDipStartHour }) { s, v -> s.copy(cognitiveDipStartHour = v) },
        int("cognitiveDipTroughHour", 0, 23, { it.cognitiveDipTroughHour }) { s, v -> s.copy(cognitiveDipTroughHour = v) },
        int("cognitiveDipEndHour", 0, 23, { it.cognitiveDipEndHour }) { s, v -> s.copy(cognitiveDipEndHour = v) },
        decimal("cognitiveDipPenalty", 0.0, false, { it.cognitiveDipPenalty }) { s, v -> s.copy(cognitiveDipPenalty = v) },
        int("staleOverdueDays", 0, get = { it.staleOverdueDays }) { s, v -> s.copy(staleOverdueDays = v) },
        int("staleUntouchedDays", 0, get = { it.staleUntouchedDays }) { s, v -> s.copy(staleUntouchedDays = v) },
        int("priorityOverloadThreshold", 0, get = { it.priorityOverloadThreshold }) { s, v -> s.copy(priorityOverloadThreshold = v) },
        text("taskTypes", FieldKind.TEXT, { it.taskTypes }) { s, v -> s.copy(taskTypes = normalizeList(v)) },
        int("statsWindowWeeks", 1, get = { it.statsWindowWeeks }) { s, v -> s.copy(statsWindowWeeks = v) },
        int("timerIdleAlertMinutes", 0, get = { it.timerIdleAlertMinutes }) { s, v -> s.copy(timerIdleAlertMinutes = v) },
        int("pomodoroFocusMinutes", 0, get = { it.pomodoroFocusMinutes }) { s, v -> s.copy(pomodoroFocusMinutes = v) },
        bool("timerAlertSound", { it.timerAlertSound }) { s, v -> s.copy(timerAlertSound = v) },
        bool("holidaysFr", { it.holidaysFr }) { s, v -> s.copy(holidaysFr = v) },
        text("extraHolidays", FieldKind.DATES, { it.extraHolidays }) { s, v -> s.copy(extraHolidays = normalizeList(v)) },
        bool("updateCheckEnabled", { it.updateCheckEnabled }) { s, v -> s.copy(updateCheckEnabled = v) },
        SettingField(
            "themeColor", FieldKind.COLOR,
            read = { it.themeColor },
            write = { s, v -> s.copy(themeColor = normalizeColor(v)!!) },
        ),
        // Thème : normalisé à l'écriture (une valeur inconnue suit le système), jamais refusé.
        text("themeMode", FieldKind.TEXT, { it.themeMode }) { s, v -> s.copy(themeMode = ThemeMode.normalize(v)) },
        // Langue : normalisée à l'écriture (une valeur inconnue suit le système), jamais refusée.
        text("language", FieldKind.TEXT, { it.language }) { s, v -> s.copy(language = UiLanguage.normalize(v)) },
        team("team.enabled", FieldKind.BOOL, get = { it.enabled.toString() }) { t, v -> t.copy(enabled = v == "true") },
        team("team.name", FieldKind.TEXT, get = { it.name }) { t, v -> t.copy(name = v) },
        team("team.managerName", FieldKind.TEXT, get = { it.managerName }) { t, v -> t.copy(managerName = v) },
        teamInt("team.staleProgressDays", 1, get = { it.staleProgressDays }) { t, v -> t.copy(staleProgressDays = v) },
        teamInt("team.churnThreshold", 2, get = { it.churnThreshold }) { t, v -> t.copy(churnThreshold = v) },
        teamInt("team.wipLimit", 0, get = { it.wipLimit }) { t, v -> t.copy(wipLimit = v) },
        teamInt("team.horizonWeeks", 1, 26, { it.horizonWeeks }) { t, v -> t.copy(horizonWeeks = v) },
        teamDecimal("team.focusFactor", 0.0, true, 1.0, { it.focusFactor }) { t, v -> t.copy(focusFactor = v) },
        teamDecimal("team.hoursPerPoint", 0.0, true, null, { it.hoursPerPoint }) { t, v -> t.copy(hoursPerPoint = v) },
        teamInt("team.loadWarnPercent", 1, 100, { it.loadWarnPercent }) { t, v -> t.copy(loadWarnPercent = v) },
        teamInt("team.affinityDays", 0, get = { it.affinityDays }) { t, v -> t.copy(affinityDays = v) },
        teamInt("team.simulationRuns", 500, 50_000, { it.simulationRuns }) { t, v -> t.copy(simulationRuns = v) },
        teamInt("team.historyWeeks", 1, 104, { it.historyWeeks }) { t, v -> t.copy(historyWeeks = v) },
        teamInt("team.minSamples", 3, get = { it.minSamples }) { t, v -> t.copy(minSamples = v) },
        teamInt("team.deadlineRiskPercent", 1, 99, { it.deadlineRiskPercent }) { t, v -> t.copy(deadlineRiskPercent = v) },
    ) + (0..6).flatMap(::scheduleFields) + (1..MAX_CONTEXTS).flatMap(::contextFields) + BEST_HOURS_FIELDS + LEARNING_FIELDS + REMINDER_FIELDS + LEVEL_NAME_FIELDS

    private val byKey = FIELDS.associateBy { it.key }

    fun field(key: String): SettingField = byKey.getValue(key)

    /** Texte de chaque champ pour [settings] (booléens : « true » / « false »). */
    fun values(settings: Settings): Map<String, String> = FIELDS.associate { it.key to it.read(settings) }

    /**
     * « Restaurer les valeurs par défaut » : le texte de chaque champ pour [Settings] par défaut, **sauf** les champs
     * `team.*` de [values], jamais touchés (l'espace Équipe a ses propres données ; docs/spec/planning-personnalise.md,
     * lot A). Ne valide ni n'écrit rien : l'utilisateur doit encore « Enregistrer ».
     */
    fun restoreDefaults(values: Map<String, String>): Map<String, String> =
        values(Settings()).mapValues { (key, default) -> if (key.startsWith("team.")) values[key] ?: default else default }

    /**
     * Valide [values] (clé → texte ; une clé absente garde la valeur de
     * [base]) et rend les réglages complets, ou les erreurs par champ, ou la
     * règle inter-champs violée.
     */
    fun validate(values: Map<String, String>, base: Settings): SettingsValidation {
        val errors = LinkedHashMap<String, FieldError>()
        var result = base
        for (f in FIELDS) {
            val raw = values[f.key] ?: continue
            val error = check(f, raw)
            if (error != null) errors[f.key] = error else result = f.write(result, raw)
        }
        if (errors.isNotEmpty()) return SettingsValidation(null, errors, null)
        errors += scheduleErrors(result)
        // On ne peut pas exiger plus d'occurrences que l'historique n'en retient : l'erreur est sur « minimum d'occurrences ».
        result.learning?.let { if (it.minSamples > it.history) errors["learning.minSamples"] = FieldError(FieldErrorKind.TOO_LARGE, it.history.toString()) }
        errors += levelNameErrors(result)
        if (errors.isNotEmpty()) return SettingsValidation(null, errors, null)
        result = dropEmptyContexts(result)
        val general = when {
            result.workdayStartHour >= result.workdayEndHour -> GeneralError.WORKDAY_ORDER
            // Creux désactivé : ses heures ne servent à rien, leur ordre ne bloque pas l'enregistrement.
            result.cognitiveDipEnabled &&
                !(result.cognitiveDipStartHour <= result.cognitiveDipTroughHour && result.cognitiveDipTroughHour <= result.cognitiveDipEndHour) -> GeneralError.DIP_ORDER
            else -> null
        }
        return SettingsValidation(if (general == null) result else null, emptyMap(), general)
    }

    /**
     * Règles du planning (docs/spec/planning-personnalise.md § Lots B1 et B2), vérifiées une fois les champs lus : chaque plage
     * d'un jour « personnalisé » finit après avoir commencé (« Au moins … » sur sa fin) et commence après la fin de la précédente
     * (« Au moins … » sur son début) ; une plage ne cite qu'un contexte qui existe ; un contexte a un nom, unique, et un type
     * n'est que dans un contexte (`DUPLICATE`, sur le champ du second).
     */
    private fun scheduleErrors(result: Settings): Map<String, FieldError> {
        val errors = LinkedHashMap<String, FieldError>()
        val contexts = result.schedule?.contexts.orEmpty()
        val names = mutableSetOf<String>()
        val typeOwner = mutableMapOf<String, Int>()
        contexts.forEachIndexed { index, c ->
            val slot = index + 1
            if (c.name.isBlank()) {
                if (c.types.isNotEmpty()) errors["schedule.c$slot.name"] = FieldError(FieldErrorKind.REQUIRED)
                return@forEachIndexed
            }
            if (!names.add(c.name.lowercase())) errors["schedule.c$slot.name"] = FieldError(FieldErrorKind.DUPLICATE, c.name)
            c.types.firstOrNull { t ->
                val owner = typeOwner[t.lowercase()]
                if (owner == null) typeOwner[t.lowercase()] = slot
                owner != null && owner != slot
            }
                ?.let { errors["schedule.c$slot.types"] = FieldError(FieldErrorKind.DUPLICATE, it) }
        }
        for (day in 0..6) {
            val windows = windowsOf(result, day)
            windows.forEachIndexed { index, w ->
                val prefix = windowKey(day, index)
                if (w.endHour <= w.startHour) {
                    errors[prefix + "end"] = FieldError(FieldErrorKind.TOO_SMALL, (w.startHour + 1).toString())
                } else if (index > 0 && w.startHour < windows[index - 1].endHour) {
                    errors[prefix + "start"] = FieldError(FieldErrorKind.TOO_SMALL, windows[index - 1].endHour.toString())
                }
                val context = w.context
                if (context != null && names.none { it == context.lowercase() }) errors[prefix + "ctx"] = FieldError(FieldErrorKind.UNKNOWN_CONTEXT, context)
            }
        }
        return errors
    }

    /** Deux niveaux d'une même échelle ne portent pas le même nom (casse ignorée) : `DUPLICATE` sur le second. */
    private fun levelNameErrors(result: Settings): Map<String, FieldError> {
        val names = result.levelNames ?: return emptyMap()
        val errors = LinkedHashMap<String, FieldError>()
        fun check(levels: List<Int>, chosen: Map<Int, String>, key: (Int) -> String) {
            val seen = mutableSetOf<String>()
            for (level in levels) {
                val name = chosen[level] ?: continue
                if (!seen.add(name.lowercase())) errors[key(level)] = FieldError(FieldErrorKind.DUPLICATE, name)
            }
        }
        check(PRIORITY_VALUES, names.priority, ::priorityNameKey)
        check(FIBONACCI_SCALE, names.points, ::pointsNameKey)
        return errors
    }

    /** Retire les emplacements de contexte laissés vides ; le sous-objet redevient nul s'il ne porte plus rien. */
    private fun dropEmptyContexts(settings: Settings): Settings {
        val schedule = settings.schedule ?: return settings
        val kept = schedule.contexts.filter { it.name.isNotBlank() }
        return settings.copy(schedule = schedule.copy(contexts = kept).takeIf { it.isCustomized() })
    }

    private fun check(f: SettingField, raw: String): FieldError? {
        val text = raw.trim()
        return when (f.kind) {
            FieldKind.BOOL -> null
            FieldKind.TEXT -> f.validate?.invoke(text)
            FieldKind.COLOR -> if (normalizeColor(text) == null) FieldError(FieldErrorKind.INVALID_COLOR, text) else null
            FieldKind.TIME -> if (normalizeTime(text) == null) FieldError(FieldErrorKind.INVALID_TIME, text) else null
            FieldKind.DATES -> text.split(',').map { it.trim() }.firstOrNull { it.isNotEmpty() && parseDate(it) == null }
                ?.let { FieldError(FieldErrorKind.INVALID_DATE, it) }
            FieldKind.INT, FieldKind.DECIMAL -> {
                if (text.isEmpty()) return FieldError(FieldErrorKind.REQUIRED)
                val number = if (f.kind == FieldKind.INT) {
                    text.toIntOrNull()?.toDouble() ?: return if (Regex("""-?\d+""").matches(text)) {
                        // Un entier trop long pour être lu : « Au plus … », pas « un entier est attendu ».
                        if (text.startsWith("-")) FieldError(FieldErrorKind.TOO_SMALL, bound(f, f.min ?: Int.MIN_VALUE.toDouble()))
                        else FieldError(FieldErrorKind.TOO_LARGE, bound(f, f.max ?: Int.MAX_VALUE.toDouble()))
                    } else {
                        FieldError(FieldErrorKind.NOT_INTEGER)
                    }
                } else {
                    parseDecimal(text) ?: return FieldError(FieldErrorKind.NOT_NUMBER)
                }
                val min = f.min
                val max = f.max
                when {
                    min != null && (number < min || (f.minExclusive && number == min)) -> FieldError(FieldErrorKind.TOO_SMALL, bound(f, min))
                    max != null && number > max -> FieldError(FieldErrorKind.TOO_LARGE, bound(f, max))
                    else -> null
                }
            }
        }
    }

    private fun bound(f: SettingField, value: Double) = if (f.kind == FieldKind.INT) value.toInt().toString() else formatDecimal(value)

    private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    /** Liste (dates, types) nettoyée : espaces retirés, vides et doublons écartés (le premier reste), séparée par « , ». */
    private fun normalizeList(text: String): String = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

    /**
     * Couleur du thème (docs/spec/apparence.md) : `system`, ou six chiffres
     * hexadécimaux avec ou sans `#` ; forme enregistrée `#RRGGBB`, `null` si
     * illisible.
     */
    fun normalizeColor(text: String): String? {
        val t = text.trim()
        if (t.equals(Settings.SYSTEM_THEME, ignoreCase = true)) return Settings.SYSTEM_THEME
        val hex = t.removePrefix("#")
        if (hex.length != 6 || !hex.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
        return "#" + hex.uppercase()
    }

    /** Heure « HH:MM » de 00:00 à 23:59 (« 8:30 » accepté, enregistré « 08:30 ») ; `null` si illisible. */
    fun normalizeTime(text: String): String? = parseTime(text)?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }

    /** [LocalTime] d'une heure « H:MM » ou « HH:MM » valide, `null` sinon. */
    fun parseTime(text: String): LocalTime? {
        val match = Regex("""(\d{1,2}):(\d{2})""").matchEntire(text.trim()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        return if (hour in 0..23 && minute in 0..59) LocalTime(hour, minute) else null
    }

    /** Couleur `0xRRGGBB` d'une valeur `#RRGGBB`, `null` pour `system` ou une valeur illisible. */
    fun parseColor(value: String): Int? =
        normalizeColor(value)?.takeIf { it != Settings.SYSTEM_THEME }?.substring(1)?.toInt(16)

    /** « 1,5 » ou « 1.5 » ; `null` si ce n'est pas un nombre fini. */
    internal fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /** « 4 » plutôt que « 4.0 » ; sinon la forme la plus courte. */
    internal fun formatDecimal(value: Double): String =
        if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()
}
