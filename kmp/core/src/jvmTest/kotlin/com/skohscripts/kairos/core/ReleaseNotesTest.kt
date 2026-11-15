package com.skohscripts.kairos.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Notes de version Fastlane (docs/spec/distribution.md § Cycle des versions) : une version **finale** a sa note dans les six
 * langues des fiches, exigée au tag (« Kairos tag ») ; une préversion n'en a pas besoin (elle ne va ni sur F-Droid ni dans
 * les fiches, ses notes sont celles de la préversion GitHub). `main` porte la finale à venir pendant tout son développement
 * (mineures au fil de l'eau, 2026-11-15) : la CI n'exige donc la note de la version déclarée que dans les six langues dès
 * qu'elle est commencée. Aucune note ne dépasse la limite de F-Droid.
 */
class ReleaseNotesTest {
    private fun root(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "CLAUDE.md").isFile) dir = dir.parentFile
        return checkNotNull(dir) { "racine du dépôt introuvable" }
    }

    private val locales = listOf("en-US", "fr-FR", "de-DE", "es-ES", "it-IT", "ru-RU")

    @Test
    fun aStartedNoteIsInTheSixLanguages() {
        val version = AppVersion.current
        val written = locales.filter { locale ->
            File(root(), "fastlane/metadata/android/$locale/changelogs/${version.versionCode}.txt").let { it.isFile && it.readText().isNotBlank() }
        }
        if (written.isEmpty()) return
        assertEquals(emptyList(), locales - written.toSet(), "notes de version ${version.versionCode}.txt commencées pour $version, absentes ou vides")
    }

    @Test
    fun noNoteExceedsTheFDroidLimit() {
        val notes = File(root(), "fastlane/metadata/android").walkTopDown().filter { it.isFile && it.parentFile.name == "changelogs" }
        val tooLong = notes.filter { it.readText().trimEnd('\n').length > MAX_NOTE }.map { it.relativeTo(root()).invariantSeparatorsPath }.toList()
        assertTrue(tooLong.isEmpty(), "notes de plus de $MAX_NOTE caractères :\n" + tooLong.joinToString("\n"))
    }

    private companion object {
        /** Limite d'une note de version dans F-Droid (`fdroid lint`). */
        const val MAX_NOTE = 500
    }
}
