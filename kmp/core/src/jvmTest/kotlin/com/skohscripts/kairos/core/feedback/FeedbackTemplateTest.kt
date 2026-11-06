package com.skohscripts.kairos.core.feedback

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Le modèle d'issue que remplit « Donner un avis » (docs/spec/retours.md § Modèle d'issue) : ses champs portent les `id` que
 * Kairos met dans l'adresse, et il pose le label `feedback` du circuit de tri (docs/ROADMAP.md).
 */
class FeedbackTemplateTest {
    private fun root(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "CLAUDE.md").isFile) dir = dir.parentFile
        return checkNotNull(dir) { "racine du dépôt introuvable" }
    }

    @Test
    fun theTemplateHasTheFieldsKairosFills() {
        val template = File(root(), ".github/ISSUE_TEMPLATE/${FeedbackIssue.TEMPLATE}")
        assertTrue(template.isFile, "modèle ${template.path} absent")
        val text = template.readText()
        for (id in listOf(FeedbackIssue.FIELD_CONTEXT, FeedbackIssue.FIELD_NEED, FeedbackIssue.FIELD_VERSION, FeedbackIssue.FIELD_PLATFORM)) {
            assertTrue(Regex("""(?m)^\s+id:\s*$id\s*$""").containsMatchIn(text), "champ id: $id absent de ${template.name}")
        }
        assertTrue(Regex("""(?m)^labels:\s*\["feedback"]\s*$""").containsMatchIn(text), "label feedback absent de ${template.name}")
    }
}
