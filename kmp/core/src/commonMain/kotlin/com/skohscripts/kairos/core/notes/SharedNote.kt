package com.skohscripts.kairos.core.notes

/**
 * Corps d'une note née d'un partage Android (docs/spec/partage-android.md § Corps de la note) : le titre que l'application
 * d'origine fournit (`EXTRA_SUBJECT`), sur une ligne, puis le texte partagé (`EXTRA_TEXT`, souvent le lien). Le titre devient
 * ainsi celui de la tâche à la conversion ([NoteConversion]) et le lien sa description.
 */
object SharedNote {
    /** Au-delà, le corps est coupé et terminé par « … » : une page entière partagée alourdirait la liste et le fichier lié. */
    const val MAX_BODY = 10_000

    private val BLANKS = Regex("\\s+")

    /** `""` : rien à créer. */
    fun body(subject: String?, text: String?): String {
        // Le titre tient sur une ligne : c'est la première ligne qui fait le titre de la tâche.
        val s = subject.orEmpty().trim().replace(BLANKS, " ")
        val t = text.orEmpty().trim()
        val body = when {
            s.isEmpty() -> t
            t.isEmpty() -> s
            // Beaucoup d'applications mettent déjà le titre dans le texte (« Titre https://… ») : pas de titre répété.
            t.contains(s, ignoreCase = true) -> t
            else -> "$s\n$t"
        }
        return truncate(body)
    }

    private fun truncate(body: String): String {
        if (body.length <= MAX_BODY) return body
        var end = MAX_BODY - 1
        // Jamais au milieu d'une paire de substitution UTF-16 : un émoji coupé en deux s'afficherait en « � ».
        if (body[end - 1].isHighSurrogate()) end--
        return body.substring(0, end) + "…"
    }
}
