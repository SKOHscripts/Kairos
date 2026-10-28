package com.skohscripts.kairos.ui.guide.demo

import com.skohscripts.kairos.core.ExampleData
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.demo_capture_body
import com.skohscripts.kairos.ui.generated.resources.demo_capture_title
import com.skohscripts.kairos.ui.generated.resources.demo_move_body
import com.skohscripts.kairos.ui.generated.resources.demo_move_title
import com.skohscripts.kairos.ui.generated.resources.demo_points_body
import com.skohscripts.kairos.ui.generated.resources.demo_points_title
import com.skohscripts.kairos.ui.generated.resources.demo_priority_body
import com.skohscripts.kairos.ui.generated.resources.demo_priority_title
import com.skohscripts.kairos.ui.generated.resources.demo_timer_start_body
import com.skohscripts.kairos.ui.generated.resources.demo_timer_start_title
import com.skohscripts.kairos.ui.generated.resources.demo_timer_stop_body
import com.skohscripts.kairos.ui.generated.resources.demo_timer_stop_title
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource

/** L'élément de l'écran qu'une étape de la démonstration cerne (docs/spec/visite-guidee.md § Visite interactive). */
enum class DemoSpot {
    /** Champ « Tâche » de la capture de la vue Jour. */
    CAPTURE,

    /** Puces de priorité de la ligne « À traiter » de la tâche suivie. */
    PRIORITY,

    /** Puces de points de la même ligne. */
    POINTS,

    /** Bouton chrono de la ligne de la tâche. */
    TIMER_START,

    /** « Arrêter le chrono » de la carte « En ce moment ». */
    TIMER_STOP,

    /** « Déplacer… » de la ligne de la tâche. */
    MOVE,
}

/**
 * Ce qu'une étape lit et fait : le dépôt **de la démonstration**, le jour, la tâche suivie (`null` avant la capture) et le
 * titre que « Faire pour moi » donne à la tâche (résolu dans la langue de l'interface par l'appelant).
 */
class DemoContext(val repository: KairosRepository, val today: LocalDate, val taskId: Long?, val taskTitle: String)

/**
 * Une étape : [act] est « Faire pour moi » (les mêmes méthodes du dépôt que l'interface), [done] lit l'**effet** dans
 * l'instantané de la démonstration, jamais le geste ni la place dans la frise (qui dépend de l'heure).
 */
class DemoStep(
    val id: String,
    val spot: DemoSpot,
    val title: StringResource,
    val body: StringResource,
    val act: suspend (DemoContext) -> Unit,
    val done: (KairosSnapshot, DemoContext) -> Boolean,
)

/** Le script de la démonstration : quatre scénarios, six étapes, une seule tâche. Données pures, testées sans interface. */
object DemoSteps {
    /** La tâche capturée par l'utilisateur dans la démonstration : la plus récente qui n'est pas un exemple. */
    fun trackedTask(snapshot: KairosSnapshot): Long? = snapshot.tasks.filter { !ExampleData.isExample(it) }.maxOfOrNull { it.id }

    /** Crée la tâche suivie si elle manque (une étape sautée), et rend son identifiant. */
    suspend fun ensureTask(ctx: DemoContext): Long? =
        ctx.taskId ?: trackedTask(ctx.repository.snapshot.value) ?: ctx.repository.createTask(ctx.taskTitle)

    private fun KairosSnapshot.task(ctx: DemoContext) = ctx.taskId?.let { id -> tasks.firstOrNull { it.id == id } }

    val ALL: List<DemoStep> = listOf(
        DemoStep(
            "capture", DemoSpot.CAPTURE, Res.string.demo_capture_title, Res.string.demo_capture_body,
            act = { ensureTask(it) },
            done = { snapshot, _ -> trackedTask(snapshot) != null },
        ),
        DemoStep(
            "priority", DemoSpot.PRIORITY, Res.string.demo_priority_title, Res.string.demo_priority_body,
            act = { ctx -> ensureTask(ctx)?.let { ctx.repository.setPriority(it, 1) } },
            done = { snapshot, ctx -> snapshot.task(ctx)?.priority != null },
        ),
        DemoStep(
            "points", DemoSpot.POINTS, Res.string.demo_points_title, Res.string.demo_points_body,
            act = { ctx -> ensureTask(ctx)?.let { ctx.repository.setPoints(it, 3) } },
            done = { snapshot, ctx -> snapshot.task(ctx)?.fibonacciPoints != null },
        ),
        DemoStep(
            "timer_start", DemoSpot.TIMER_START, Res.string.demo_timer_start_title, Res.string.demo_timer_start_body,
            act = { ctx -> ensureTask(ctx)?.let { ctx.repository.startTimer(it) } },
            done = { snapshot, ctx -> snapshot.workSessions.any { it.taskId == ctx.taskId && it.endedAt == null } },
        ),
        DemoStep(
            "timer_stop", DemoSpot.TIMER_STOP, Res.string.demo_timer_stop_title, Res.string.demo_timer_stop_body,
            // Étape précédente sautée : rien ne tourne encore, on démarre avant d'arrêter (l'effet attendu est une séance close).
            act = { ctx ->
                ensureTask(ctx)?.let { id ->
                    if (ctx.repository.snapshot.value.workSessions.none { it.taskId == id && it.endedAt == null }) ctx.repository.startTimer(id)
                    ctx.repository.stopTimer()
                }
            },
            done = { snapshot, ctx ->
                snapshot.workSessions.none { it.endedAt == null } && snapshot.workSessions.any { it.taskId == ctx.taskId && it.endedAt != null }
            },
        ),
        DemoStep(
            "move", DemoSpot.MOVE, Res.string.demo_move_title, Res.string.demo_move_body,
            act = { ctx -> ensureTask(ctx)?.let { ctx.repository.schedule(it, ctx.today.plus(DatePeriod(days = 1))) } },
            done = { snapshot, ctx -> snapshot.task(ctx)?.scheduledDate?.let { it > ctx.today } == true },
        ),
    )
}
