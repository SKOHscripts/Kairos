package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Tests différentiels (docs/plan-v3-kotlin.md § 7) : rejoue les scénarios produits
 * par le moteur Python de Kairos 2 (`kmp/tools/gen_fixtures.py`) et exige les mêmes
 * sorties, à la minute et au bit près pour les scores.
 */
class DifferentialTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String): JsonElement {
        val text = checkNotNull(javaClass.getResource("/fixtures/$name")) { "fixture $name absente" }.readText()
        return json.parseToJsonElement(text)
    }

    private val JsonElement.str: String? get() = if (this is JsonNull) null else jsonPrimitive.content
    private fun JsonObject.date(key: String) = getValue(key).str?.let(LocalDate::parse)
    private fun JsonObject.dateTime(key: String) = getValue(key).str?.let(LocalDateTime::parse)
    private fun JsonObject.intOrNull(key: String) = getValue(key).let { if (it is JsonNull) null else it.jsonPrimitive.int }
    private fun JsonObject.longOrNull(key: String) = getValue(key).let { if (it is JsonNull) null else it.jsonPrimitive.long }
    private fun JsonElement.longs(): List<Long> = jsonArray.map { it.jsonPrimitive.long }

    private fun task(o: JsonObject): Task {
        val updated = Instant.parse(o.getValue("updatedAt").str!!)
        return Task(
            id = o.getValue("id").jsonPrimitive.long,
            title = o.getValue("title").str!!,
            priority = o.intOrNull("priority"),
            fibonacciPoints = o.intOrNull("fibonacciPoints"),
            estimatedMinutes = o.intOrNull("estimatedMinutes"),
            deadline = o.date("deadline"),
            scheduledDate = o.date("scheduledDate"),
            pinnedStart = o.dateTime("pinnedStart"),
            parentId = o.longOrNull("parentId"),
            status = TaskStatus.fromCode(o.getValue("status").str!!),
            createdAt = updated,
            updatedAt = updated,
        )
    }

    private fun edges(e: JsonElement) = e.jsonArray.map {
        val pair = it.longs()
        Dependencies.Edge(blocked = pair[0], blocker = pair[1])
    }

    @Test
    fun scheduling_matches_kairos2() {
        val cases = fixture("scheduling.json").jsonArray
        check(cases.size >= 400)
        for (element in cases) {
            val case = element.jsonObject
            val seed = case.getValue("seed").jsonPrimitive.int
            val day = case.date("day")!!
            val now = case.dateTime("now")
            val settings = json.decodeFromJsonElement<Settings>(case.getValue("settings"))
            val tasks = case.getValue("tasks").jsonArray.map { task(it.jsonObject) }
            val blocks = case.getValue("blocks").jsonArray.mapIndexed { i, b ->
                val o = b.jsonObject
                TimeBlock(
                    id = i + 1L, title = o.getValue("title").str!!, start = o.dateTime("start")!!, end = o.dateTime("end")!!,
                    kind = BlockKind.fromCode(o.getValue("kind").str!!), createdAt = Instant.fromEpochSeconds(0),
                )
            }
            val edges = edges(case.getValue("edges"))
            val expected = case.getValue("expected").jsonObject
            val msg = "graine $seed"

            val statusById = tasks.associate { it.id to it.status }
            val todo = tasks.filter { it.status == TaskStatus.TODO }
            val blocked = Dependencies.blockedTaskIds(edges) { statusById[it] == TaskStatus.TODO }
            assertEquals(expected.getValue("blocked").longs(), blocked.sorted(), "$msg : bloquées")
            val own = todo.associate { it.id to Scheduling.sortKey(it, day, settings) }
            val effective = Dependencies.derivedUrgency(edges, own)
            val raised = effective.filter { (id, key) -> own[id]?.let { key < it } ?: false }.keys.sorted()
            assertEquals(expected.getValue("raised").longs(), raised, "$msg : urgence héritée")

            val schedule = Scheduling.buildDaySchedule(tasks, blocks, day, now, settings, blocked, effective)
            val scheduled = schedule.scheduled.map {
                mapOf(
                    "id" to it.task.id, "start" to it.start.toString(), "duration" to it.durationMinutes,
                    "pinned" to it.pinned, "pushedAfter" to it.pushedAfter, "pushedAfterKind" to it.pushedAfterKind?.name,
                    "conflictWith" to it.conflictWith, "deepwork" to it.deepwork, "dip" to it.dipApplied,
                )
            }
            val expectedScheduled = expected.getValue("scheduled").jsonArray.map { s ->
                val o = s.jsonObject
                mapOf(
                    "id" to o.getValue("id").jsonPrimitive.long,
                    "start" to LocalDateTime.parse(o.getValue("start").str!!).toString(),
                    "duration" to o.getValue("duration").jsonPrimitive.int,
                    "pinned" to o.getValue("pinned").jsonPrimitive.boolean,
                    "pushedAfter" to o.getValue("pushedAfter").str,
                    "pushedAfterKind" to o.getValue("pushedAfterKind").str,
                    "conflictWith" to o.getValue("conflictWith").str,
                    "deepwork" to o.getValue("deepwork").jsonPrimitive.boolean,
                    "dip" to o.getValue("dip").jsonPrimitive.boolean,
                )
            }
            assertEquals(expectedScheduled, scheduled, "$msg : placement")
            assertEquals(expected.getValue("unscheduled").longs(), schedule.unscheduled.map { it.id }, "$msg : sans créneau")
            assertEquals(expected.getValue("later").longs(), schedule.later.map { it.id }, "$msg : plus tard")
            assertEquals(expected.getValue("toProcess").longs(), schedule.toProcess.map { it.id }, "$msg : à traiter")
            assertEquals(expected.getValue("required").jsonPrimitive.int, schedule.stats.requiredMinutes, "$msg : charge")
            assertEquals(expected.getValue("available").jsonPrimitive.int, schedule.stats.availableMinutes, "$msg : disponible")

            val timeline = Scheduling.buildTimeline(schedule, blocks, day, settings).map {
                listOf(it.kind.name, it.title, it.topMinutes, it.heightMinutes)
            }
            val expectedTimeline = expected.getValue("timeline").jsonArray.map { e ->
                val o = e.jsonObject
                listOf(o.getValue("kind").str, o.getValue("title").str, o.getValue("top").jsonPrimitive.int, o.getValue("height").jsonPrimitive.int)
            }
            assertEquals(expectedTimeline, timeline, "$msg : frise")

            val scores = expected.getValue("scores").jsonObject
            val buckets = expected.getValue("buckets").jsonObject
            val stale = expected.getValue("stale").jsonObject
            for (t in todo) {
                val want = scores.getValue(t.id.toString()).str!!.toDouble()
                assertEquals(want, Scheduling.wsjfScore(t, day, settings), "$msg : score WSJF de ${t.id}")
                assertEquals(buckets.getValue(t.id.toString()).jsonPrimitive.int, Scheduling.urgencyBucket(t, day), "$msg : urgence de ${t.id}")
                assertEquals(
                    stale[t.id.toString()]?.jsonPrimitive?.int,
                    Staleness.daysStale(t, day, settings.staleOverdueDays, settings.staleUntouchedDays),
                    "$msg : ancienneté de ${t.id}",
                )
            }
        }
    }

    @Test
    fun dependencies_match_kairos2() {
        for ((index, element) in fixture("dependencies.json").jsonArray.withIndex()) {
            val case = element.jsonObject
            val edges = edges(case.getValue("edges"))
            val status = case.getValue("status").jsonObject.mapKeys { it.key.toLong() }.mapValues { it.value.str }
            val own = case.getValue("own").jsonObject.mapKeys { it.key.toLong() }.mapValues { it.value.jsonPrimitive.int }
            val newEdge = case.getValue("newEdge").longs()
            val expected = case.getValue("expected").jsonObject
            val msg = "cas $index"
            assertEquals(expected.getValue("cycleNodes").longs(), Dependencies.cycleNodes(edges).sorted(), "$msg : cycles")
            assertEquals(expected.getValue("blocked").longs(), Dependencies.blockedTaskIds(edges) { status[it] == "todo" }.sorted(), "$msg : bloquées")
            val derived = expected.getValue("derived").jsonObject.mapKeys { it.key.toLong() }.mapValues { it.value.jsonPrimitive.int }
            assertEquals(derived, Dependencies.derivedUrgency(edges, own), "$msg : urgence dérivée")
            assertEquals(
                expected.getValue("wouldCreateCycle").jsonPrimitive.boolean,
                Dependencies.wouldCreateCycle(edges, newEdge[0], newEdge[1]),
                "$msg : cycle potentiel",
            )
        }
    }

    @Test
    fun calendar_matches_kairos2() {
        val cal = fixture("calendar.json").jsonObject
        val holidays = Workdays.buildHolidays(2025..2028, france = true, extra = "2026-08-14")
        assertEquals(cal.getValue("holidays").jsonArray.map { LocalDate.parse(it.str!!) }, holidays.sorted())

        for ((year, easter) in cal.getValue("easter").jsonObject) {
            assertEquals(LocalDate.parse(easter.str!!), Workdays.easterSunday(year.toInt()), "Pâques $year")
        }
        for (element in cal.getValue("nextDeadline").jsonArray) {
            val o = element.jsonObject
            val rule = TaskRecurrence.fromCode(o.getValue("rule").str!!)
            val base = o.date("base")!!
            assertEquals(o.date("expected"), Recurrence.nextDeadline(rule, base, o.intOrNull("dow")), "échéance suivante $o")
        }
        for (element in cal.getValue("business").jsonArray) {
            val o = element.jsonObject
            val start = o.date("start")!!
            assertEquals(o.date("add"), Workdays.addBusinessDays(start, o.getValue("days").jsonPrimitive.int, holidays), "jours ouvrés $o")
            assertEquals(o.date("onOrBefore"), Workdays.onOrBeforeBusinessDay(start, holidays), "ouvré précédent $o")
            assertEquals(o.getValue("between").jsonPrimitive.int, Workdays.businessDaysBetween(start, o.date("end")!!, holidays), "écart ouvré $o")
            assertEquals(o.date("snooze"), Recurrence.nextSnoozeDate(o.date("deadline"), start, holidays), "report $o")
        }
        for (element in cal.getValue("blocks").jsonArray) {
            val o = element.jsonObject
            val template = TimeBlock(
                id = 1, title = "Bloc", start = o.dateTime("start")!!, end = o.dateTime("end")!!,
                recurrence = BlockRecurrence.fromCode(o.getValue("recurrence").str!!), createdAt = Instant.fromEpochSeconds(0),
            )
            val got = Recurrence.expandRecurringBlocks(listOf(template), o.date("rangeStart")!!, o.date("rangeEnd")!!)
                .map { listOf(it.start, it.end) }
            val want = o.getValue("expected").jsonArray.map { pair -> pair.jsonArray.map { LocalDateTime.parse(it.str!!) } }
            assertEquals(want, got, "créneaux récurrents $o")
        }
    }
}
