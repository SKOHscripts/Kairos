package com.skohscripts.kairos.ui.day

/**
 * Les champs de la fiche d'une tâche, dans l'ordre où elle les présente (docs/spec/premiere-tache.md § Un coach par-dessus la vraie
 * fiche). Le coach de la première tâche rattache une étape à chacun ; chaque champ porte la balise de test `edit-field-<NOM>`, ce
 * qui permet de vérifier que la liste du coach et la fiche disent la même chose. [advanced] : le champ est dans les options
 * avancées, que le coach ouvre quand il l'explique. [team] : champ de la seule fiche d'une tâche d'équipe (« Assigné à », 3.8.0,
 * issue #97), que seul le coach de la première tâche d'équipe explique.
 */
internal enum class EditField(val advanced: Boolean = false, val team: Boolean = false) {
    TITLE,
    DESCRIPTION,
    ASSIGNEE(team = true),
    PRIORITY,
    POINTS,
    /** Dans l'essentiel depuis 3.14.0 (issue #166) : le type qualifie la tâche au même titre que priorité et points. */
    TYPE,
    MINUTES,
    DEADLINE,
    /** Section « Découpage et enchaînement » depuis l'issue #201 (docs/spec/sous-taches.md § Section) : hors des options avancées. */
    SUBTASKS,
    BLOCKERS,
    SCHEDULED(advanced = true),
    PROJECT(advanced = true),
    MANUAL_TIME(advanced = true),
    RECURRENCE(advanced = true),
    PIN(advanced = true),
    ;

    /** Balise de test du champ. */
    val tag: String get() = "edit-field-$name"

    companion object {
        /** Les champs de la fiche d'une tâche personnelle. */
        val personal: List<EditField> get() = entries.filter { !it.team }
    }
}
