package com.skohscripts.kairos.ui.stats

import com.skohscripts.kairos.core.stats.SuspectSessions
import com.skohscripts.kairos.ui.generated.resources.stats_suspect_open
import com.skohscripts.kairos.ui.generated.resources.stats_suspect_done
import com.skohscripts.kairos.ui.guide.GtdStepButton
import com.skohscripts.kairos.ui.guide.GtdStep
import com.skohscripts.kairos.core.stats.StatsTrends
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.generated.resources.action_revert
import com.skohscripts.kairos.ui.generated.resources.stats_correct_done
import com.skohscripts.kairos.ui.generated.resources.stats_correct_open
import com.skohscripts.kairos.ui.app.LocalUndoMessages
import com.skohscripts.kairos.core.stats.EstimateCorrection
import com.skohscripts.kairos.ui.generated.resources.stats_period_weeks
import com.skohscripts.kairos.ui.generated.resources.stats_period_all
import com.skohscripts.kairos.ui.generated.resources.stats_period
import com.skohscripts.kairos.ui.generated.resources.stats_empty_filtered
import com.skohscripts.kairos.ui.generated.resources.stats_filtered
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material3.SegmentedButtonDefaults
import com.skohscripts.kairos.ui.theme.KairosSegmentedButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedRow
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.LocalPlatform
import com.skohscripts.kairos.ui.day.FilterCard
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.stats.StatsCsv
import com.skohscripts.kairos.core.stats.StatsQuery
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.app.fileStamp
import com.skohscripts.kairos.ui.generated.resources.app_name
import com.skohscripts.kairos.ui.generated.resources.file_error
import com.skohscripts.kairos.ui.generated.resources.stats_export_done
import com.skohscripts.kairos.ui.generated.resources.stats_image_done
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import com.skohscripts.kairos.ui.guide.GuideSection
import com.skohscripts.kairos.ui.guide.GuideSpot
import com.skohscripts.kairos.ui.guide.MissingGuideSection
import com.skohscripts.kairos.ui.app.heading
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.uiLanguage
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.stats_empty
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosSpacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Statistiques (docs/spec/statistiques.md), lecture seule : la période, les filtres et « Exporter… », puis les cartes
 * ([StatsCard]) : chiffres clés de la fenêtre récente, débit hebdomadaire, calibration de l'estimation et biais,
 * répartition du temps par type et focus, quand on travaille chaque type, flux et backlog, complétude.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(services: AppServices) {
    val snapshot by services.repository.personalSnapshot.collectAsState()
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val now = services.clock.now()
    val today = now.toLocalDateTime(timeZone).date
    val language = uiLanguage()
    // Filtres de l'écran (docs/spec/statistiques.md § Filtres, issue #114) : locaux, jamais enregistrés ; par défaut, la vue
    // d'avant les filtres, à l'identique.
    var query by remember { mutableStateOf(StatsQuery()) }
    var filterOpen by remember { mutableStateOf(false) }
    val input = remember(snapshot, today, query) { query.apply(snapshot.tasks, snapshot.workSessions, snapshot.settings, today, timeZone) }
    val stats = remember(input) { query.dashboard(input, today, now, timeZone) }
    // Quand on travaille chaque type (docs/spec/planning-personnalise.md § Lot C, étape C1) : observation seule.
    val hours = remember(input) { query.hours(input, now, timeZone) }
    // Évolutions (issue #112) : même entrée, même fenêtre.
    val trends = remember(input) { StatsTrends.compute(input.tasks, input.sessions, today, input.settings.statsWindowWeeks, now, timeZone) }
    val projects = remember(snapshot) { snapshot.tasks.map { it.projectTag }.filter { it.isNotEmpty() }.distinct().sorted() }
    // Export (issue #113) : ce que l'écran montre, rien n'est écrit dans la base. [capture] = carte en cours de rendu hors écran.
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    var capture by remember { mutableStateOf<StatsCard?>(null) }
    // « Corriger les estimations… » (issue #101) : toutes les tâches terminées chronométrées, indépendamment de la période et
    // des filtres de l'écran ; l'aperçu dit lesquelles.
    val undo = LocalUndoMessages.current
    val corrections = remember(snapshot) { EstimateCorrection.candidates(snapshot, now) }
    var correcting by remember { mutableStateOf(false) }
    // Chronos à vérifier (docs/spec/previsions-taches.md § Étape G) : comme les estimations, tout l'historique, quels que soient la période et les filtres.
    val suspects = remember(snapshot) { SuspectSessions.find(snapshot, now, TimeZone.currentSystemDefault()) }
    var checking by remember { mutableStateOf(false) }
    if (checking) {
        SuspectSessionsDialog(suspects, onDismiss = { checking = false }) { chosen ->
            checking = false
            scope.launch {
                val count = services.repository.setSessionEnds(chosen.associate { it.session.id to it.proposedEnd })
                if (count > 0) {
                    undo(getString(Res.string.stats_suspect_done, count), getString(Res.string.action_revert)) {
                        scope.launch { services.repository.setSessionEnds(chosen.associate { it.session.id to it.session.endedAt!! }) }
                    }
                }
            }
        }
    }
    val caption = listOfNotNull(
        stringResource(Res.string.app_name),
        Dates.long(today, language),
        periodLabel(if (input.all) StatsQuery.ALL else stats.windowWeeks),
        if (query.filter.active) stringResource(Res.string.stats_filtered, input.tasks.size, input.total) else null,
    ).joinToString(" · ")

    if (correcting) {
        EstimateCorrectionDialog(corrections, onDismiss = { correcting = false }) { chosen ->
            correcting = false
            applyCorrections(services, chosen, scope, undo)
        }
    }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        capture?.let { card ->
            OffscreenCapture(
                onCaptured = { image ->
                    capture = null
                    scope.launch {
                        runCatching { services.files.saveImage("kairos-stats-${card.fileKey}-${fileStamp(services)}.png", image) }
                            .onSuccess { if (it) messages(getString(Res.string.stats_image_done)) }
                            .onFailure { messages(getString(Res.string.file_error)) }
                    }
                },
            ) { StatsCardImage({ StatsCardView(card, stats, hours, trends, language) }, caption) }
        }
        Column(Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(KairosSpacing.l), verticalArrangement = Arrangement.spacedBy(KairosSpacing.m)) {
            GtdStepButton(GtdStep.REFLECT)
            // Une base vide n'a rien à filtrer : l'état vide seul.
            val filterable = snapshot.tasks.isNotEmpty() || snapshot.workSessions.isNotEmpty()
            if (filterable) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
                    PeriodRow(snapshot.settings, query.weeks, Modifier.weight(1f)) { query = query.copy(weeks = it) }
                    if (stats.hasAnyData) {
                        StatsExportButton(
                            onCsv = {
                                val text = StatsCsv.encode(query, input, stats, hours, today, services.clock.now())
                                scope.launch {
                                    runCatching { services.files.saveText("kairos-stats-${fileStamp(services)}.csv", text) }
                                        .onSuccess { if (it) messages(getString(Res.string.stats_export_done)) }
                                        .onFailure { messages(getString(Res.string.file_error)) }
                                }
                            },
                            onImage = { capture = it },
                        )
                    }
                }
                FilterCard(
                    query.filter, filterOpen || query.filter.active, { filterOpen = !(filterOpen || query.filter.active) }, { query = query.copy(filter = it) },
                    projects, snapshot.settings.taskTypeList, remember { FocusRequester() }, LocalPlatform.current != Platform.ANDROID,
                )
                if (query.filter.active) {
                    Text(
                        stringResource(Res.string.stats_filtered, input.tasks.size, input.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!stats.hasAnyData) {
                Text(stringResource(if (query.filter.active) Res.string.stats_empty_filtered else Res.string.stats_empty), style = MaterialTheme.typography.bodyLarge)
                // Aucun panneau : une section demandée par le guide est absente, on le dit (issue #95).
                MissingGuideSection(GuideSection.STATS_HOURS)
                return@Column
            }
            StatsCard.entries.forEach { card ->
                if (card == StatsCard.HOURS) {
                    // Panneau que la carte « Quand tu travailles chaque type » fait voir (docs/spec/visite-guidee.md, issue #95).
                    GuideSpot(GuideSection.STATS_HOURS) { StatsCardView(card, stats, hours, trends, language) }
                } else {
                    StatsCardView(card, stats, hours, trends, language)
                }
                // Sous la carte, pas dedans : l'image exportée d'une carte ne montre pas de bouton.
                if (card == StatsCard.CALIBRATION && corrections.isNotEmpty()) {
                    KairosOutlinedButton(onClick = { correcting = true }, contentPadding = KairosButtonIconPadding) {
                        Icon(KairosIcons.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.stats_correct_open), modifier = Modifier.padding(start = 6.dp))
                    }
                }
                if (card == StatsCard.CALIBRATION && suspects.isNotEmpty()) {
                    KairosOutlinedButton(onClick = { checking = true }, contentPadding = KairosButtonIconPadding) {
                        Icon(KairosIcons.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(Res.string.stats_suspect_open, suspects.size), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

/**
 * Applique la correction (issue #101) et propose de la défaire : les estimations d'avant reviennent, « sans » compris.
 */
private fun applyCorrections(
    services: AppServices,
    chosen: List<EstimateCorrection.Candidate>,
    scope: kotlinx.coroutines.CoroutineScope,
    undo: (String, String, () -> Unit) -> Unit,
) {
    scope.launch {
        val count = services.repository.setEstimates(chosen.associate { it.task.id to it.realMinutes })
        if (count > 0) {
            undo(getString(Res.string.stats_correct_done, count), getString(Res.string.action_revert)) {
                scope.launch { services.repository.setEstimates(chosen.associate { it.task.id to it.oldMinutes }) }
            }
        }
    }
}

/**
 * Tuile de chiffre clé : la valeur en grand, son libellé dessous. Un seuil franchi prend un contour, jamais une couleur seule.
 * [note] : une mention secondaire en pastille à contour avec l'icône `Warning` (ce que le chiffre ne compte pas : les
 * tâches non estimées de l'espace Équipe, docs/spec/equipe-charge.md).
 * [nested] : la tuile est posée dans un [Panel] (carte à contour) ; règle « pas de contour dans un contour »
 * (docs/spec/densite.md) : un seuil franchi ne prend plus de contour mais le fond `surfaceContainerHigh`, l'icône
 * `Warning` gardant l'information hors de la couleur.
 */
@Composable
internal fun StatTile(value: String, label: String, modifier: Modifier, warn: Boolean = false, note: String? = null, nested: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (warn && nested) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = if (warn && !nested) androidx.compose.foundation.BorderStroke(1.dp, scheme.outline) else null,
        modifier = modifier,
    ) {
        Column(Modifier.padding(KairosSpacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (warn) Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            if (note != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(top = 8.dp)
                        .border(1.dp, scheme.outline, MaterialTheme.shapes.small)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(12.dp))
                    Text(note, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Panneau à contour : icône et titre, une phrase d'aide, puis le contenu. Aussi utilisé par l'écran Équipe. */
@Composable
internal fun Panel(icon: ImageVector, title: String, hint: String, content: @Composable () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(KairosSpacing.l), verticalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
            }
            Muted(hint)
            content()
        }
    }
}

/**
 * Piste d'une barre : fond neutre de 12 dp, remplissage au primaire de [fraction] (0 à 1, bouts arrondis de 4 dp).
 * [overflow] : part supplémentaire, à la suite du remplissage, en `error` (le dépassement d'une charge au-delà de la
 * capacité, docs/spec/equipe-charge.md) ; [mark] : repère fin à cette fraction de la piste (la capacité). Sans
 * [overflow] ni [mark], le tracé est celui des statistiques, inchangé.
 */
@Composable
internal fun BarTrack(fraction: Float, modifier: Modifier = Modifier, overflow: Float = 0f, mark: Float? = null) {
    val scheme = MaterialTheme.colorScheme
    if (overflow <= 0f && mark == null) {
        Box(modifier.height(12.dp).background(scheme.surfaceContainerHighest, RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(scheme.primary, RoundedCornerShape(4.dp)))
        }
        return
    }
    val ok = fraction.coerceIn(0f, 1f)
    val over = overflow.coerceIn(0f, 1f - ok)
    val rest = 1f - ok - over
    val markColor = scheme.outline
    Row(
        modifier.height(12.dp).clip(RoundedCornerShape(4.dp)).background(scheme.surfaceContainerHighest).drawWithContent {
            drawContent()
            if (mark != null) {
                val width = 2.dp.toPx()
                drawRect(markColor, Offset((size.width * mark.coerceIn(0f, 1f) - width / 2).coerceIn(0f, size.width - width), 0f), Size(width, size.height))
            }
        },
    ) {
        if (ok > 0.001f) Box(Modifier.weight(ok).fillMaxHeight().background(scheme.primary))
        if (over > 0.001f) Box(Modifier.weight(over).fillMaxHeight().background(scheme.error))
        if (rest > 0.001f) Spacer(Modifier.weight(rest))
    }
}

/**
 * Barre horizontale : libellé, piste neutre remplie au primaire (bouts arrondis de 4 dp), valeur écrite.
 *
 * Extensions de l'espace Équipe (docs/spec/equipe-charge.md § Interface) : [overflow] et [mark] (voir [BarTrack]),
 * [detail] (une ligne secondaire sous la barre) et [stackedWhenNarrow] (sous 420 dp, le libellé passe au-dessus de la
 * barre pour lui laisser la place). Sans ces paramètres, la barre est celle des statistiques.
 */
@Composable
internal fun BarRow(
    label: String,
    fraction: Float,
    value: String,
    warn: Boolean = false,
    overflow: Float = 0f,
    mark: Float? = null,
    detail: String? = null,
    stackedWhenNarrow: Boolean = false,
) {
    @Composable
    fun Value() {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(min = 110.dp, max = 200.dp)) {
            if (warn) Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(14.dp).padding(end = 2.dp))
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    fun Line(stacked: Boolean) {
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BarTrack(fraction, Modifier.weight(1f), overflow, mark)
                    Value()
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp))
                BarTrack(fraction, Modifier.weight(1f), overflow, mark)
                Value()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (stackedWhenNarrow) {
            BoxWithConstraints(Modifier.fillMaxWidth()) { Line(stacked = maxWidth < 420.dp) }
        } else {
            Line(stacked = false)
        }
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = if (stackedWhenNarrow) 0.dp else 108.dp),
            )
        }
    }
}

@Composable
internal fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Période des statistiques (issue #114) : « 4 sem. », « 8 sem. », « 12 sem. » (et la fenêtre des réglages si elle n'y est
 * pas), « Tout ». [weeks] nul = la fenêtre des réglages, choisie d'office.
 */
@Composable
private fun PeriodRow(settings: Settings, weeks: Int?, modifier: Modifier, onChange: (Int?) -> Unit) {
    val options = remember(settings.statsWindowWeeks) { StatsQuery.periodOptions(settings) }
    val chosen = weeks ?: maxOf(1, settings.statsWindowWeeks)
    val label = stringResource(Res.string.stats_period)
    KairosSegmentedRow(modifier.semantics { contentDescription = label }) {
        options.forEachIndexed { index, option ->
            KairosSegmentedButton(
                selected = chosen == option,
                // La fenêtre des réglages redevient la vue par défaut (weeks nul) : identique à celle d'avant les filtres.
                onClick = { onChange(if (option == maxOf(1, settings.statsWindowWeeks)) null else option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(periodLabel(option), maxLines = 1) },
            )
        }
    }
}

/** « 8 sem. », ou « Tout » pour [StatsQuery.ALL] : la ligne de la période et la légende des images. */
@Composable
private fun periodLabel(weeks: Int): String =
    if (weeks == StatsQuery.ALL) stringResource(Res.string.stats_period_all) else stringResource(Res.string.stats_period_weeks, weeks)
