# Spécifications Kairos 3 (Kotlin Multiplatform) : index

Registre de spécification de la **réécriture Kotlin** (`kmp/`), tenu selon le
workflow de [`CLAUDE.md`](../../CLAUDE.md). Une spec par domaine, en deux
parties (besoin métier, puis solution technique), **bijective** avec le code de
`kmp/`.

Pendant la transition (voir [`docs/plan-v3-kotlin.md`](../plan-v3-kotlin.md)),
deux registres coexistent :

- [`docs/spec/`](../spec/README.md) décrit le code Python (`app/`,
  `templates/`, `static/`, `android/`), **gelé** (correctifs seulement) ;
- `docs/spec-v3/` (ce dossier) décrit `kmp/`, rempli jalon par jalon.

À la bascule `v3.0.0` (jalon M7), ce dossier remplace `docs/spec/`.

Le besoin métier des domaines portés est repris des specs actuelles, moins le
périmètre retiré (TimeTree, import GitLab, base pilotage, service systemd :
plan § 2.2). Un domaine n'apparaît ici qu'une fois son jalon commencé.

## Domaines

| Spec | Couvre | Jalon |
|---|---|---|
| [`architecture.md`](architecture.md) | Modules Gradle, pile technique, invariants (pureté de `core`, aucune dépendance non libre), versions. | M0 |
| [`navigation-theme.md`](navigation-theme.md) | Thème MD3 « miel », typographie, icônes, logo, coquille de navigation (rail, barre haute, barre basse Android), écrans, « À propos et guide ». | M0 |
| [`i18n.md`](i18n.md) | Langues (français par défaut, anglais), ressources de chaînes, choix de la langue. | M0 |
| [`distribution.md`](distribution.md) | Versionnage, APK Android, installeurs et zips portables de bureau, version web, CI, releases, GitHub Pages. | M0, M1 |
| [`modele-donnees.md`](modele-donnees.md) | Modèle (tâches, créneaux, dépendances, sessions, notes, réglages), stockage SQLDelight, ouverture et migrations, dépôt, exemples. | M1 |
| [`vue-jour.md`](vue-jour.md) | Vue Jour : capture (tâche, créneau), « À traiter » et « Comment qualifier ? », « Maintenant », agenda ordonné, sections secondaires, « Pourquoi à cette place ? », filtres, backlog, frise, édition complète (guide des points, durées suggérées), raccourcis, autre jour. | M1, M2, M4 |
| [`ordonnancement.md`](ordonnancement.md) | Moteur : score WSJF, placement dans la journée, creux, deep work, jours ouvrés et fériés, ancienneté ; tests différentiels contre Kairos 2. | M2 |
| [`dependances.md`](dependances.md) | Blocage, cycles, urgence héritée (chemin critique), bloqueurs dans l'édition. | M2 |
| [`recurrence.md`](recurrence.md) | Tâches récurrentes (à la complétion, « le N du mois »), créneaux récurrents, « Décaler ». | M2 |
| [`temps-reel-chrono.md`](temps-reel-chrono.md) | Chrono (une session à la fois), temps passé et temps du jour, trois alertes, notifications Android (permanente, alarmes, redémarrage), bureau (plateau) et web. | M3 |
| [`notes-capture.md`](notes-capture.md) | Notes : capture libre, conversion en tâche (titre et description), modification, archivage, suppression. | M4 |
| [`vue-semaine.md`](vue-semaine.md) | Vue Semaine : sept jours (échéances, fait, créneaux), temps réel de la semaine, navigation vers un autre jour. | M4 |
| [`statistiques.md`](statistiques.md) | Statistiques : chiffres clés, débit, calibration et biais, temps par type, flux, complétude ; repères du guide des points ; tests différentiels. | M4 |
| [`reglages.md`](reglages.md) | Écran Réglages : sections, champs, bornes de Kairos 2, validation par champ et entre champs, un seul « Enregistrer ». | M5 |
| [`migration-2x.md`](migration-2x.md) | Migration d'une base Kairos 2 : conversion (toutes générations de schéma), Android automatique, bureau proposé, import manuel, tests sur de vraies bases. | M5 |
| [`accueil.md`](accueil.md) | Accueil au premier lancement : présentation, exemples, bilan ou proposition de migration. | M5 |
| [`mises-a-jour.md`](mises-a-jour.md) | Vérification des nouvelles versions sur le bureau (GitHub, 6 heures), bandeau, carte des Réglages. | M5 |
| [`accessibilite.md`](accessibilite.md) | Lecteurs d'écran (boutons, états, titres), cibles de 48 dp, contrastes. | M5 |
| [`export-import.md`](export-import.md) | Export et import JSON, sauvegardes, données de la version web (OPFS, fichier lié). | M1 |

## Références transverses

- [`docs/DESIGN_SYSTEM.md`](../DESIGN_SYSTEM.md) : la charte (rôles de
  couleur, formes, « où va la couleur »). Ses valeurs sont reprises telles
  quelles par `navigation-theme.md` ; ses noms de classes CSS décrivent la
  version Python.
- [`docs/plan-v3-kotlin.md`](../plan-v3-kotlin.md) : feuille de route et
  décisions de la réécriture.
