package com.skohscripts.kairos.ui.team.forecast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.team.forecast.ForecastModel
import com.skohscripts.kairos.core.team.forecast.ForecastResult
import com.skohscripts.kairos.core.team.forecast.TeamScenario
import com.skohscripts.kairos.ui.app.AppServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Lance et arrête les calculs de l'écran Prévisions (une simulation, ou la comparaison de scénarios). Vit dans la
 * coroutine de l'écran : quitter l'écran annule le calcul en cours ; un résultat terminé reste en mémoire dans
 * [TeamUiState]. La graine est tirée ici, côté interface (`TeamUiState.seed`), jamais dans `core`.
 *
 * « Arrêter » ne tue pas brutalement le calcul : il lève un drapeau lu entre deux tranches de tirages, ce qui laisse
 * un résultat partiel propre (marqué « interrompu »). Arrêtée pendant la préparation, une simulation n'a rien à
 * montrer et garde l'ancien résultat ; une comparaison arrêtée n'affiche rien (ses colonnes seraient incomparables).
 */
internal class ForecastController(private val services: AppServices, private val scope: CoroutineScope) {
    var phase: RunPhase by mutableStateOf(RunPhase.Idle)
        private set

    private var stopRequested = false
    private val zone = TimeZone.currentSystemDefault()
    private val ui get() = services.teamUi

    val busy: Boolean get() = phase != RunPhase.Idle

    fun stop() {
        stopRequested = true
    }

    /** Lance la simulation de [spec] sur [snapshot]. */
    fun run(spec: ForecastSpec, snapshot: KairosSnapshot) {
        if (busy) return
        stopRequested = false
        phase = RunPhase.Preparing(comparison = false)
        val now = services.clock.now()
        val seed = ui.seed()
        scope.launch {
            try {
                val request = withContext(Dispatchers.Default) { ForecastEngine.request(snapshot, now, zone, spec, seed) }
                var total = 0
                val result = ForecastEngine.simulate(
                    request, ui.batchSize, { stopRequested },
                    onPrepared = { total = it; phase = RunPhase.Running(0, it, comparison = false, column = null) },
                    onProgress = { phase = RunPhase.Running(it, total, comparison = false, column = null) },
                    gate = ui.batchGate,
                )
                if (result.runs > 0) {
                    ui.forecast = ForecastRun(spec, result, now, ForecastFingerprint.of(snapshot, now.toLocalDateTime(zone).date))
                }
            } finally {
                phase = RunPhase.Idle
            }
        }
    }

    /**
     * Compare la situation réelle et [scenarios] (**même graine** pour toutes les colonnes), au modèle par effort
     * avec le périmètre et les options de [spec]. Les colonnes se calculent l'une après l'autre.
     */
    fun compare(spec: ForecastSpec, snapshot: KairosSnapshot, scenarios: List<TeamScenario>, realLabel: String) {
        if (busy) return
        stopRequested = false
        phase = RunPhase.Preparing(comparison = true)
        val now = services.clock.now()
        val seed = ui.seed()
        val effort = spec.copy(model = ForecastModel.EFFORT)
        scope.launch {
            try {
                val data = withContext(Dispatchers.Default) { ForecastEngine.data(snapshot, now, zone) }
                val done = ArrayList<ComparisonColumn>()
                for (column in ForecastEngine.columns(snapshot, scenarios)) {
                    val label = column.scenario?.name ?: realLabel
                    var total = 0
                    val request = withContext(Dispatchers.Default) { ForecastEngine.request(column.snapshot, now, zone, effort, seed, data) }
                    val result: ForecastResult = ForecastEngine.simulate(
                        request, ui.batchSize, { stopRequested },
                        onPrepared = { total = it; phase = RunPhase.Running(0, it, comparison = true, column = label) },
                        onProgress = { phase = RunPhase.Running(it, total, comparison = true, column = label) },
                        gate = ui.batchGate,
                    )
                    if (result.interrupted) return@launch
                    val rate = withContext(Dispatchers.Default) { ForecastEngine.loadRate(column.snapshot, now, zone) }
                    done += ComparisonColumn(column.scenario?.id, column.scenario?.name, result, rate, column.skipped)
                }
                ui.comparison = ComparisonRun(
                    effort, seed, now, ForecastFingerprint.of(snapshot, now.toLocalDateTime(zone).date), scenarios, done,
                )
            } finally {
                phase = RunPhase.Idle
            }
        }
    }
}
