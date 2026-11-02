package com.skohscripts.kairos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.text.SearchFold
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.day.Facet
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.features_clear
import com.skohscripts.kairos.ui.generated.resources.features_filter_all
import com.skohscripts.kairos.ui.generated.resources.features_filter_label
import com.skohscripts.kairos.ui.generated.resources.features_filter_since
import com.skohscripts.kairos.ui.generated.resources.features_none
import com.skohscripts.kairos.ui.generated.resources.features_platform_android
import com.skohscripts.kairos.ui.generated.resources.features_platform_desktop
import com.skohscripts.kairos.ui.generated.resources.features_platform_web
import com.skohscripts.kairos.ui.generated.resources.features_platforms_only
import com.skohscripts.kairos.ui.generated.resources.features_search_label
import com.skohscripts.kairos.ui.generated.resources.features_since
import com.skohscripts.kairos.ui.guide.FeatureCard
import com.skohscripts.kairos.ui.guide.FeatureCatalog
import com.skohscripts.kairos.ui.guide.GuideTarget
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.stringResource

/** Une entrée de la liste, ses textes déjà résolus dans la langue affichée (la recherche porte sur eux). */
private class FeatureEntry(val card: FeatureCard, val title: String, val body: String, val meta: String, val spokenMeta: String)

/**
 * « Liste de fonctionnalités » (docs/spec/liste-fonctionnalites.md, issue #153) : tout le catalogue, groupé par chapitre (Équipe
 * seulement avec l'espace Équipe), avec une recherche sans casse ni accents et un filtre « Nouveau depuis… ». Rien n'est mémorisé :
 * la recherche et le filtre repartent à zéro quand on ferme l'écran. Un toucher ouvre la cible de la carte ([onOpen]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeatureListScreen(teamEnabled: Boolean, onOpen: (GuideTarget) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var since by rememberSaveable { mutableStateOf<String?>(null) }
    val versions = remember { FeatureCatalog.versions() }
    val chapters = FeatureCatalog.chapters(teamEnabled)
    val platformNames = mapOf(
        Platform.ANDROID to stringResource(Res.string.features_platform_android),
        Platform.DESKTOP to stringResource(Res.string.features_platform_desktop),
        Platform.WEB to stringResource(Res.string.features_platform_web),
    )
    // Toutes les cartes résolues à chaque composition (nombre constant d'appels), filtrées ensuite.
    val entries = chapters.associateWith { chapter ->
        FeatureCatalog.chapter(chapter).map { card ->
            val title = stringResource(card.title)
            val body = stringResource(card.body)
            val sinceText = stringResource(Res.string.features_since, card.since)
            val where = Platform.entries.filter { it in card.platforms }.joinToString(", ") { platformNames.getValue(it) }
            val meta = if (card.limited) "$sinceText · $where" else sinceText
            val spoken = if (card.limited) "$sinceText, ${stringResource(Res.string.features_platforms_only, where)}" else sinceText
            FeatureEntry(card, title, body, meta, spoken)
        }
    }
    val chapterTitles = chapters.associateWith { stringResource(it.title) }
    val kept = since?.let { version -> FeatureCatalog.since(version).toSet() }
    val shown = entries.mapValues { (_, list) ->
        list.filter { (kept == null || it.card in kept) && SearchFold.matches(query, "${it.title} ${it.body}") }
    }.filterValues { it.isNotEmpty() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(KairosSpacing.s),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().testTag("feature-list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(KairosSpacing.l),
        ) {
            item(key = "controls") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(KairosSpacing.s),
                    verticalArrangement = Arrangement.spacedBy(KairosSpacing.s),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(Res.string.features_search_label)) },
                        leadingIcon = { Icon(KairosIcons.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val sinceLabel = stringResource(Res.string.features_filter_since, "%s")
                    Facet(
                        label = stringResource(Res.string.features_filter_label),
                        value = since,
                        options = versions,
                        show = { sinceLabel.replace("%s", it) },
                        all = stringResource(Res.string.features_filter_all),
                        width = 220.dp,
                    ) { since = it }
                }
            }
            if (shown.isEmpty()) {
                item(key = "none") {
                    Column(verticalArrangement = Arrangement.spacedBy(KairosSpacing.s), modifier = Modifier.padding(top = KairosSpacing.m)) {
                        Text(stringResource(Res.string.features_none), style = MaterialTheme.typography.bodyLarge)
                        KairosTextButton(onClick = { query = ""; since = null }) { Text(stringResource(Res.string.features_clear)) }
                    }
                }
            }
            shown.forEach { (chapter, list) ->
                item(key = "chapter-${chapter.name}") {
                    Text(
                        chapterTitles.getValue(chapter),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.heading().padding(top = KairosSpacing.m),
                    )
                }
                items(list, key = { it.card.id }) { entry ->
                    // La carte cliquable fusionne ses textes : un seul élément lu « titre, phrase, depuis 3.8.0, Android seulement ».
                    OutlinedCard(onClick = { onOpen(entry.card.target) }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                                leadingContent = { Icon(entry.card.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text(entry.title) },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(entry.body)
                                        Text(
                                            entry.meta,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.semantics { contentDescription = entry.spokenMeta },
                                        )
                                    }
                                },
                                trailingContent = { Icon(KairosIcons.ChevronRight, contentDescription = null) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                    }
                }
            }
        }
    }
}
