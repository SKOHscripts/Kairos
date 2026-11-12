package com.skohscripts.kairos.ui.day

/**
 * Les sections repliables de la fiche (docs/spec/fiche-tache.md), repliées à l'ouverture : chacune résume son contenu dans
 * son en-tête.
 */
internal enum class SheetSection { TEAM, QUALIFY, TIME, SPLIT }

/**
 * Les champs de la fiche d'une tâche, dans l'ordre où elle les présente (docs/spec/premiere-tache.md § Un coach par-dessus la vraie
 * fiche). Le coach de la première tâche rattache une étape à chacun ; chaque champ porte la balise de test `edit-field-<NOM>`, ce
 * qui permet de vérifier que la liste du coach et la fiche disent la même chose. [section] : la section repliable qui le
 * contient (`null` : titre et description, toujours visibles), que le coach ouvre quand il l'explique. [team] : champ de la seule
 * fiche d'une tâche d'équipe (« Assigné à », 3.8.0, issue #97), que seul le coach de la première tâche d'équipe explique.
 */
internal enum class EditField(val section: SheetSection?, val team: Boolean = false) {
    TITLE(null),
    DESCRIPTION(null),
    ASSIGNEE(SheetSection.TEAM, team = true),
    PRIORITY(SheetSection.QUALIFY),
    POINTS(SheetSection.QUALIFY),
    /** Dans « Qualification » : le type qualifie la tâche au même titre que priorité et points (3.14.0, issue #166). */
    TYPE(SheetSection.QUALIFY),
    /** « Qualification » depuis 4.1.0 (docs/spec/fiche-tache.md) : il classe la tâche ; avant, dans les options avancées. */
    PROJECT(SheetSection.QUALIFY),
    MINUTES(SheetSection.TIME),
    DEADLINE(SheetSection.TIME),
    /** Date programmée, heure fixe, récurrence et temps passé : la section de la temporalité depuis 4.1.0. */
    SCHEDULED(SheetSection.TIME),
    PIN(SheetSection.TIME),
    RECURRENCE(SheetSection.TIME),
    MANUAL_TIME(SheetSection.TIME),
    /** Section « Découpage et enchaînement » depuis l'issue #201 (docs/spec/sous-taches.md § Section). */
    SUBTASKS(SheetSection.SPLIT),
    BLOCKERS(SheetSection.SPLIT),
    ;

    /** Balise de test du champ. */
    val tag: String get() = "edit-field-$name"

    companion object {
        /** Les champs de la fiche d'une tâche personnelle. */
        val personal: List<EditField> get() = entries.filter { !it.team }
    }
}
