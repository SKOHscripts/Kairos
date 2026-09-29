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
| [`vue-jour.md`](vue-jour.md) | Vue Jour : capture, « À traiter » et qualification en un clic, « À faire », « Fait », édition. | M1 |
| [`export-import.md`](export-import.md) | Export et import JSON, sauvegardes, données de la version web (OPFS, fichier lié). | M1 |

## Références transverses

- [`docs/DESIGN_SYSTEM.md`](../DESIGN_SYSTEM.md) : la charte (rôles de
  couleur, formes, « où va la couleur »). Ses valeurs sont reprises telles
  quelles par `navigation-theme.md` ; ses noms de classes CSS décrivent la
  version Python.
- [`docs/plan-v3-kotlin.md`](../plan-v3-kotlin.md) : feuille de route et
  décisions de la réécriture.
