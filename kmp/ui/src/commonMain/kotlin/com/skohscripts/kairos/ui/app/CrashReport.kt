package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.ui.day.verticalScrollWithThumb
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.crash_body
import com.skohscripts.kairos.ui.generated.resources.crash_continue
import com.skohscripts.kairos.ui.generated.resources.crash_share
import com.skohscripts.kairos.ui.generated.resources.crash_title
import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import org.jetbrains.compose.resources.stringResource

/**
 * Rapport du dernier plantage (docs/spec/demarrage.md § Rapport de plantage) : affiché à la place de
 * l'application au lancement qui suit un plantage, **avant** toute ouverture de la base. [report] est le texte
 * déjà composé par la plateforme (version, appareil, trace) ; il reste sélectionnable. [onShare] le confie au
 * partage du système (aucun réseau : c'est l'utilisateur qui l'envoie), [onContinue] le supprime et ouvre
 * l'application. Volontairement sans dépendance aux réglages ni aux services : il doit s'afficher même si c'est
 * la base ou un réglage qui fait planter l'application.
 */
@Composable
fun CrashReportScreen(report: String, onShare: () -> Unit, onContinue: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.crash_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(Res.string.crash_body), style = MaterialTheme.typography.bodyMedium)
            // La trace défile seule : les boutons restent atteignables, même sur un petit écran.
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    report,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().verticalScrollWithThumb(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KairosOutlinedButton(onClick = onShare) { Text(stringResource(Res.string.crash_share)) }
                KairosButton(onClick = onContinue) { Text(stringResource(Res.string.crash_continue)) }
            }
        }
    }
}
