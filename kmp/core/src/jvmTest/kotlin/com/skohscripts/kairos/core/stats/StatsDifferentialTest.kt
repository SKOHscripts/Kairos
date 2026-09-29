package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Statistiques contre Kairos 2 (`kmp/tools/gen_fixtures.py`, `stats.json`) :
 * tableau de bord, repères du guide des points, calibration par type, en UTC
 * comme les horodatages naïfs de Kairos 2.
 */
class StatsDifferentialTest {
    private val json = Json

    private val JsonElement.str: String? get() = if (this is JsonNull) null else jsonPrimitive.content
    private val JsonElement.intOrNull: Int? get() = if (this is JsonNull) null else jsonPrimitive.int
    private fun JsonObject.s(k: String) = getValue(k).str

    private fun task(o: JsonObject) = Task(
        id = o.getValue("id").jsonPrimitive.long,
        title = o.s("title")!!,
        priority = o.getValue("priority").intOrNull,
        fibonacciPoints = o.getValue("fibonacciPoints").intOrNull,
        estimatedMinutes = o.getValue("estimatedMinutes").intOrNull,
        deadline = o.s("deadline")?.let(LocalDate::parse),
        scheduledDate = o.s("scheduledDate")?.let(LocalDate::parse),
        status = TaskStatus.fromCode(o.s("status")!!),
        taskType = o.s("taskType")!!,
        manualTimeSpentMinutes = o.getValue("manualTimeSpentMinutes").intOrNull,
        createdAt = Instant.parse(o.s("createdAt")!!),
        updatedAt = Instant.parse(o.s("updatedAt")!!),
    )

    private fun list(e: JsonElement): List<Any?> = e.jsonArray.map { v ->
        when {
            v is JsonNull -> null
            v is JsonArray -> list(v)
            v.jsonPrimitive.isString -> v.jsonPrimitive.content
            else -> v.jsonPrimitive.int
        }
    }

    @Test
    fun dashboard_matches_kairos2() {
        val cases = json.parseToJsonElement(javaClass.getResource("/fixtures/stats.json")!!.readText()).jsonArray
        check(cases.size >= 200)
        val utc = TimeZone.UTC
        for ((index, element) in cases.withIndex()) {
            val case = element.jsonObject
            val msg = "cas $index"
            val today = LocalDate.parse(case.s("today")!!)
            val now = Instant.parse(case.s("now")!!)
            val cfg = case.getValue("settings").jsonObject
            val settings = Settings(
                statsWindowWeeks = cfg.getValue("statsWindowWeeks").jsonPrimitive.int,
                staleOverdueDays = cfg.getValue("staleOverdueDays").jsonPrimitive.int,
                staleUntouchedDays = cfg.getValue("staleUntouchedDays").jsonPrimitive.int,
            )
            val tasks = case.getValue("tasks").jsonArray.map { task(it.jsonObject) }
            val sessions = case.getValue("sessions").jsonArray.map {
                val o = it.jsonObject
                val start = Instant.parse(o.s("startedAt")!!)
                WorkSession(o.getValue("id").jsonPrimitive.long, o.getValue("taskId").jsonPrimitive.long, start, o.s("endedAt")?.let(Instant::parse), start)
            }
            val e = case.getValue("expected").jsonObject
            val d = TaskStats.dashboard(tasks, sessions, today, settings, now, utc)
            assertEquals(e.getValue("windowWeeks").jsonPrimitive.int, d.windowWeeks, msg)
            assertEquals(e.getValue("completedInWindow").jsonPrimitive.int, d.completedInWindow, "$msg : terminées")
            assertEquals(e.getValue("trackedMinutesWindow").jsonPrimitive.int, d.trackedMinutesWindow, "$msg : temps suivi")
            assertEquals(list(e.getValue("throughput")), d.throughput.map { listOf(it.weekStart.toString(), it.completed, it.points) }, "$msg : débit")
            assertEquals(list(e.getValue("calibration")), d.calibration.map { listOf(it.key, it.count, it.medianMinutes) }, "$msg : calibration")
            val bias = e.getValue("bias")
            if (bias is JsonNull) {
                assertEquals(null, d.bias, "$msg : biais")
            } else {
                val b = bias.jsonArray
                assertEquals(listOf(b[0].jsonPrimitive.int, b[1].jsonPrimitive.int, b[2].jsonPrimitive.int), listOf(d.bias!!.count, d.bias.estimatedMinutes, d.bias.realMinutes), "$msg : biais")
                assertEquals(b[3].jsonPrimitive.content.toDouble(), d.bias.ratio, "$msg : ratio")
            }
            assertEquals(list(e.getValue("timeByType")), d.timeByType.map { listOf(it.key, it.minutes, it.pct) }, "$msg : par type")
            assertEquals(list(e.getValue("focus")), listOf(d.focus.sessionCount, d.focus.totalMinutes, d.focus.avgSessionMinutes), "$msg : focus")
            val f = d.flow
            assertEquals(
                list(e.getValue("flow")),
                listOf(f.openCount, f.medianAgeDays, f.overdueCount, f.staleCount, f.completionDelayDays, f.deadlineTotal, f.deadlineOnTime, f.deadlineHitPct),
                "$msg : flux",
            )
            val c = d.completeness
            assertEquals(list(e.getValue("completeness")), listOf(c.total, c.withPoints, c.withEstimate, c.withType, c.pointsPct, c.estimatePct, c.typePct), "$msg : complétude")

            val live = tasks.filter { it.status != TaskStatus.ARCHIVED }
            val spentLive = TimeTracking.spentMinutesByTask(sessions, now, live)
            val refs = TaskStats.fibonacciReferences(live, spentLive).mapKeys { it.key.toString() }
                .mapValues { (_, r) -> listOf(r.calibration?.let { listOf(it.count, it.medianMinutes) }, r.examples) }
            val wantRefs = e.getValue("references").jsonObject.mapValues { list(it.value) }
            assertEquals(wantRefs, refs, "$msg : repères")
            assertEquals(list(e.getValue("byType")), TaskStats.calibrationByType(live, spentLive).map { listOf(it.key, it.count, it.medianMinutes) }, "$msg : calibration par type")
        }
    }
}
