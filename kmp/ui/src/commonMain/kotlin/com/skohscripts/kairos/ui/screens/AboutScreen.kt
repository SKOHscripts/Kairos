package com.skohscripts.kairos.ui.screens

import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.about_brief_1
import com.skohscripts.kairos.ui.generated.resources.about_brief_2
import com.skohscripts.kairos.ui.generated.resources.about_brief_3
import com.skohscripts.kairos.ui.generated.resources.about_brief_4
import com.skohscripts.kairos.ui.generated.resources.about_brief_title
import com.skohscripts.kairos.ui.generated.resources.about_intro
import com.skohscripts.kairos.ui.generated.resources.about_license
import com.skohscripts.kairos.ui.generated.resources.about_name_body
import com.skohscripts.kairos.ui.generated.resources.about_name_title
import com.skohscripts.kairos.ui.generated.resources.about_score_body
import com.skohscripts.kairos.ui.generated.resources.about_score_formula
import com.skohscripts.kairos.ui.generated.resources.about_score_title
import com.skohscripts.kairos.ui.generated.resources.about_source
import com.skohscripts.kairos.ui.generated.resources.about_version
import com.skohscripts.kairos.ui.generated.resources.app_name
import com.skohscripts.kairos.ui.generated.resources.app_tagline
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.icons.KairosLogo
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Dépôt du code source, affiché et ouvert depuis « À propos ». */
const val SOURCE_URL = "https://github.com/SKOHscripts/Kairos"

/**
 * « À propos et guide » : remplace la page d'accueil de Kairos 2 (qui rendait
 * le README). Contenu natif et traduit : ce que fait Kairos, le score, le nom,
 * la version, la licence (docs/spec/navigation-theme.md § À propos).
 */
@Composable
fun AboutScreen() {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            verticalArrangement = Arrangement.spacedBy(KairosSpacing.m),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(KairosSpacing.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(KairosLogo, contentDescription = null, modifier = Modifier.size(56.dp))
                Column {
                    Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(Res.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(stringResource(Res.string.about_intro), style = MaterialTheme.typography.bodyLarge)

            Section(Res.string.about_brief_title) {
                listOf(Res.string.about_brief_1, Res.string.about_brief_2, Res.string.about_brief_3, Res.string.about_brief_4)
                    .forEach { Text("•  " + stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            }
            Section(Res.string.about_score_title) {
                Text(
                    stringResource(Res.string.about_score_formula),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(Res.string.about_score_body), style = MaterialTheme.typography.bodyMedium)
            }
            Section(Res.string.about_name_title) {
                Text(stringResource(Res.string.about_name_body), style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                stringResource(Res.string.about_version, KairosBuild.VERSION_NAME),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                stringResource(Res.string.about_license),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val uriHandler = LocalUriHandler.current
            KairosOutlinedButton(onClick = { uriHandler.openUri(SOURCE_URL) }, contentPadding = KairosButtonIconPadding) {
                Icon(KairosIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.about_source), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Carte « filled » (conteneur de surface), jamais d'ombre (charte). */
@Composable
private fun Section(title: StringResource, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(KairosSpacing.l), verticalArrangement = Arrangement.spacedBy(KairosSpacing.s)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
