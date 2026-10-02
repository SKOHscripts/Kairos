package com.skohscripts.kairos.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import com.skohscripts.kairos.core.team.exchange.ExchangeStatus
import com.skohscripts.kairos.core.team.exchange.MemberExchange
import com.skohscripts.kairos.data.ImportException
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.app.importErrorMessage
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.exchange_send_action
import com.skohscripts.kairos.ui.generated.resources.file_error
import com.skohscripts.kairos.ui.generated.resources.hub_awaiting
import com.skohscripts.kairos.ui.generated.resources.hub_full_file
import com.skohscripts.kairos.ui.generated.resources.hub_how_title
import com.skohscripts.kairos.ui.generated.resources.hub_members_title
import com.skohscripts.kairos.ui.generated.resources.hub_no_pack
import com.skohscripts.kairos.ui.generated.resources.hub_no_report
import com.skohscripts.kairos.ui.generated.resources.hub_pack_sent
import com.skohscripts.kairos.ui.generated.resources.hub_privacy
import com.skohscripts.kairos.ui.generated.resources.hub_receive
import com.skohscripts.kairos.ui.generated.resources.hub_receive_hint
import com.skohscripts.kairos.ui.generated.resources.hub_report_received
import com.skohscripts.kairos.ui.generated.resources.hub_step_1
import com.skohscripts.kairos.ui.generated.resources.hub_step_2
import com.skohscripts.kairos.ui.generated.resources.hub_step_3
import com.skohscripts.kairos.ui.generated.resources.hub_step_4
import com.skohscripts.kairos.ui.generated.resources.hub_tour
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Onglet « Échanges » de l'écran Équipe (docs/spec/equipe-echanges.md § Onglet « Échanges ») : tout ce qui concerne
 * les fichiers entre le manager et ses membres au même endroit. De haut en bas : les quatre étapes du partage, le
 * bouton « Recevoir un rapport… » (même aperçu et même sauvegarde préalable que « Importer » des Réglages), puis une
 * carte par membre actif (hors « moi ») avec la date de son dernier paquet et de son dernier rapport, le signal
 * « En attente de son rapport » (contour + icône) et « Envoyer ses tâches… ». Toucher le nom ouvre la fiche
 * ([onOpenMember]) ; [onTour] lance la visite de l'espace Équipe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExchangeHub(services: AppServices, onOpenMember: (Long) -> Unit, onTour: () -> Unit) {
    val snapshot by services.repository.snapshot.collectAsState()
    val rows = remember(snapshot.members, snapshot.teamEvents) { ExchangeStatus.of(snapshot.members, snapshot.teamEvents) }
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    var preview by remember { mutableStateOf<ExchangePreview?>(null) }

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            verticalArrangement = Arrangement.spacedBy(KairosSpacing.m),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(KairosSpacing.l),
        ) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.hub_how_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
                    listOf(Res.string.hub_step_1, Res.string.hub_step_2, Res.string.hub_step_3, Res.string.hub_step_4).forEachIndexed { i, step ->
                        Text("${i + 1}. " + stringResource(step), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(stringResource(Res.string.hub_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KairosButton(onClick = {
                    scope.launch {
                        val text = runCatching { services.files.openText() }.getOrElse {
                            messages(getString(Res.string.file_error))
                            return@launch
                        } ?: return@launch
                        try {
                            when (val file = readImportedFile(services, text)) {
                                is ImportedFile.Exchange -> preview = file.preview
                                // Jamais de remplacement de toutes les données depuis l'écran Équipe : c'est le rôle de Réglages → Données.
                                is ImportedFile.Full -> messages(getString(Res.string.hub_full_file))
                            }
                        } catch (e: ImportException) {
                            messages(importErrorMessage(e))
                        }
                    }
                }, contentPadding = KairosButtonIconPadding) {
                    Icon(KairosIcons.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.hub_receive), modifier = Modifier.padding(start = 6.dp))
                }
                Text(stringResource(Res.string.hub_receive_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(Res.string.hub_members_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.heading())
            rows.forEach { row -> MemberExchangeCard(row, onOpen = { onOpenMember(row.member.id) }, onSend = { scope.launch { sendPack(services, row.member, messages) } }) }
            KairosTextButton(onClick = onTour, contentPadding = NoStartPadding) { Text(stringResource(Res.string.hub_tour)) }
        }
    }

    preview?.let { current ->
        ExchangePreviewDialog(
            current,
            onConfirm = {
                preview = null
                scope.launch { applyExchange(services, current, messages) }
            },
            onDismiss = { preview = null },
        )
    }
}

/** Le texte d'un bouton texte s'aligne sur celui des cartes (pas les 12 dp de marge intérieure du bouton). Cible tactile : 48 dp. */
private val NoStartPadding = PaddingValues(start = 0.dp, end = 12.dp)

/** Carte d'un membre : nom, dates du dernier paquet et du dernier rapport, signal d'attente, bouton d'envoi. Carte à contour, sans ombre. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberExchangeCard(row: MemberExchange, onOpen: () -> Unit, onSend: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KairosTextButton(onClick = onOpen, contentPadding = NoStartPadding) {
                Text(row.member.name, style = MaterialTheme.typography.titleMedium)
                Icon(KairosIcons.ChevronRight, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(18.dp))
            }
            Text(
                row.lastPackAt?.let { stringResource(Res.string.hub_pack_sent, dayAndTime(it)) } ?: stringResource(Res.string.hub_no_pack),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                row.lastReportAt?.let { stringResource(Res.string.hub_report_received, dayAndTime(it)) } ?: stringResource(Res.string.hub_no_report),
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                KairosOutlinedButton(onClick = onSend, contentPadding = KairosButtonIconPadding) {
                    Icon(KairosIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.exchange_send_action), modifier = Modifier.padding(start = 6.dp))
                }
                if (row.awaitingReport) Flag(stringResource(Res.string.hub_awaiting), flagged = true, style = MaterialTheme.typography.bodyMedium, icon = KairosIcons.Schedule)
            }
        }
    }
}

/** « 28 sept. 14:05 », comme la date du dernier rapport de la fiche d'un membre. */
@Composable
private fun dayAndTime(at: Instant): String {
    val language = Locale.current.language
    val local = at.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${Dates.short(local.date, language)} ${Dates.time(local, language)}"
}
