package com.skohscripts.kairos.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Notes de version Fastlane (docs/spec/distribution.md § Cycle des versions) : une version **finale** a sa note dans les six
 * langues des fiches avant d'être taguée ; une préversion n'en a pas besoin (elle ne va ni sur F-Droid ni dans les fiches,
 * ses notes sont celles de la préversion GitHub). Aucune note ne dépasse la limite de F-Droid.
 */
class ReleaseNotesTest {
    private fun root(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "CLAUDE.md").isFile) dir = dir.parentFile
        return checkNotNull(dir) { "racine du dépôt introuvable" }
    }

    private val locales = listOf("en-US", "fr-FR", "de-DE", "es-ES", "it-IT", "ru-RU")

    @Test
    fun aFinalVersionHasItsNotesInTheSixLanguages() {
        val version = AppVersion.current
        if (version.isPrerelease) return
        val missing = locales.filter { locale ->
            val note = File(root(), "fastlane/metadata/android/$locale/changelogs/${version.versionCode}.txt")
            !note.isFile || note.readText().isBlank()
        }
        assertEquals(emptyList(), missing, "notes de version ${version.versionCode}.txt absentes ou vides pour $version")
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
