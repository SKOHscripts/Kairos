package com.skohscripts.kairos.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Test d'architecture (docs/spec/densite.md § Composants) : les boutons, puces
 * et sélecteurs segmentés de Material 3 ne s'emploient que dans
 * `theme/KairosComponents.kt`. Partout ailleurs, les enveloppes `Kairos*` ;
 * sinon un composant retomberait à la taille nominale (40 dp ou plus) sans
 * que personne le voie. Lit les sources de `commonMain` (le répertoire de
 * travail de Gradle est celui du module, comme `StringsParityTest`).
 */
class ComponentDensityTest {
    private val sources = File("src/commonMain/kotlin")
    private val wrappers = "theme/KairosComponents.kt"
    private val names = listOf("Button", "OutlinedButton", "FilledTonalButton", "TextButton", "FilterChip", "SingleChoiceSegmentedButtonRow", "SegmentedButton")
    private val group = names.joinToString("|")

    /** Appel `Button(` (pas `KairosButton(` ni `IconButton(`), éventuellement qualifié. */
    private val call = Regex("""(?<![\w])($group)\(""")
    private val import = Regex("""^\s*import\s+androidx\.compose\.material3\.($group|\*)(\s+as\s+\w+)?\s*$""")

    private fun kotlinFiles() = sources.walkTopDown().filter { it.isFile && it.extension == "kt" && !it.path.replace('\\', '/').endsWith(wrappers) }.toList()

    /** Lignes de code : ni KDoc, ni commentaire de ligne (la fin de ligne après `//` est ignorée). */
    private fun codeLines(file: File): List<Pair<Int, String>> {
        var block = false
        return file.readLines().mapIndexedNotNull { index, raw ->
            var line = raw
            if (block) {
                val end = line.indexOf("*/")
                if (end < 0) return@mapIndexedNotNull null
                block = false
                line = line.substring(end + 2)
            }
            while (true) {
                val start = line.indexOf("/*")
                if (start < 0) break
                val end = line.indexOf("*/", start + 2)
                if (end < 0) { block = true; line = line.substring(0, start); break }
                line = line.removeRange(start, end + 2)
            }
            val comment = line.indexOf("//")
            if (comment >= 0) line = line.substring(0, comment)
            if (line.isBlank()) null else (index + 1) to line
        }
    }

    @Test
    fun sourcesAreFound() {
        assertTrue(sources.isDirectory, "sources introuvables : ${sources.absolutePath}")
        assertTrue(File(sources, "com/skohscripts/kairos/ui/$wrappers").isFile)
        assertTrue(kotlinFiles().size > 50)
    }

    @Test
    fun material3ButtonsAreOnlyUsedInKairosComponents() {
        val offenders = kotlinFiles().flatMap { file ->
            codeLines(file).filter { (_, line) -> !line.trimStart().startsWith("import ") && call.containsMatchIn(line) }
                .map { (number, line) -> "${file.path}:$number : ${line.trim()}" }
        }
        assertTrue(offenders.isEmpty(), "Utiliser les enveloppes Kairos* (theme/KairosComponents.kt) :\n" + offenders.joinToString("\n"))
    }

    @Test
    fun material3ButtonsAreNotImportedOutsideKairosComponents() {
        val offenders = kotlinFiles().flatMap { file ->
            codeLines(file).filter { (_, line) -> import.matches(line) }.map { (number, line) -> "${file.path}:$number : ${line.trim()}" }
        }
        assertTrue(offenders.isEmpty(), "Import direct de Material 3 interdit :\n" + offenders.joinToString("\n"))
    }

    @Test
    fun detectorSeesDirectUseButNotWrappersOrIconButtons() {
        assertTrue(call.containsMatchIn("    Button(onClick = {}) {"))
        assertTrue(call.containsMatchIn("    androidx.compose.material3.TextButton(onClick = {}) {"))
        assertTrue(!call.containsMatchIn("    KairosButton(onClick = {}) {"))
        assertTrue(!call.containsMatchIn("    IconButton(onClick = {}) {"))
        assertTrue(!call.containsMatchIn("    KairosSegmentedButton(selected = true) {"))
    }
}
