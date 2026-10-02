package com.skohscripts.kairos.core.team.forecast

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
import com.skohscripts.kairos.core.team.TeamFixtures
import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.snapshot
import com.skohscripts.kairos.core.team.TeamMember
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** Données communes aux tests de prévision (jalon E5) : le lundi de `TeamFixtures`, 7 h UTC (journée de « moi » entière). */
internal object ForecastFixtures {
    val now: Instant = TeamFixtures.mondayMorning
    val timeZone: TimeZone = TimeZone.UTC

    /** Facteurs d'erreur tous égaux à 1, en nombre suffisant (≥ 8) pour que l'historique seul serve : aucune incertitude. */
    val exact = ForecastData(errorFactors = List(10) { 1.0 })

    /** Tâche d'équipe avec un `teamUid` (identité des tirages communs), durée en [hours]. */
    fun task(id: Long, assignee: Long?, hours: Double? = 8.0, priority: Int? = 1, points: Int? = 3, deadline: kotlinx.datetime.LocalDate? = null, type: String = ""): Task =
        TeamFixtures.task(id, assignee, hours, priority, points, deadline, type).copy(teamUid = "uid-$id")

    fun request(
        snapshot: KairosSnapshot,
        data: ForecastData = exact,
        seed: Long = 42L,
        runs: Int = 500,
        scope: ForecastScope = ForecastScope.Assigned,
        model: ForecastModel = ForecastModel.EFFORT,
        options: ForecastOptions = ForecastOptions(capacityRandomness = false),
    ) = ForecastRequest(snapshot, now, timeZone, data, seed, scope, model, options, runs)

    fun run(
        snapshot: KairosSnapshot,
        data: ForecastData = exact,
        seed: Long = 42L,
        runs: Int = 500,
        scope: ForecastScope = ForecastScope.Assigned,
        model: ForecastModel = ForecastModel.EFFORT,
        options: ForecastOptions = ForecastOptions(capacityRandomness = false),
    ): ForecastResult = MonteCarlo.prepare(request(snapshot, data, seed, runs, scope, model, options)).run()

    /** Équipe de [members] membres (8 h/j, sans focus) et [tasksPerMember] tâches de 4 à 12 h chacun, sans dépendance. */
    fun team(members: Int, tasksPerMember: Int, deadlines: Boolean = false): KairosSnapshot {
        val ms: List<TeamMember> = (1..members).map { member(it.toLong()) }
        val tasks = ArrayList<Task>()
        var id = 1L
        for (m in 1..members) {
            for (k in 0 until tasksPerMember) {
                val hours = 4.0 + ((m * 7 + k * 3) % 9)
                val deadline = if (deadlines && k % 3 == 0) TeamFixtures.monday.plusDays(5 + 3 * k) else null
                tasks += task(id++, m.toLong(), hours = hours, priority = k % 3, deadline = deadline)
            }
        }
        return snapshot(tasks, ms)
    }

    private fun kotlinx.datetime.LocalDate.plusDays(n: Int) =
        kotlinx.datetime.LocalDate.fromEpochDays(toEpochDays() + n)

    fun dep(blocked: Long, blocker: Long): TaskDependency = TeamFixtures.dep(blocked, blocker)
}
