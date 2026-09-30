# Spécifications de Kairos : index

Registre de spécification de Kairos (`kmp/`, Kotlin Multiplatform), tenu selon
le workflow de [`CLAUDE.md`](../../CLAUDE.md). Une spec par domaine, en deux
parties (besoin métier, puis solution technique), **bijective** avec le code.

Depuis la bascule `v3.0.0` (jalon M7 de
[`docs/plan-v3-kotlin.md`](../plan-v3-kotlin.md)), ce registre, tenu jusque-là
dans `docs/spec-v3/`, est le seul. Kairos 2 (Python) n'est plus dans le dépôt :
son code et ses specs (`vue-jour-gtd.md`, `accueil-navigation.md`,
`reglages-secrets.md`…) se lisent au tag `v2.6.0`. Les specs ci-dessous y
renvoient quand elles en reprennent le besoin métier, moins le périmètre
retiré (TimeTree, import GitLab, base pilotage, service systemd : plan § 2.2).

## Domaines

| Spec | Couvre | Jalon |
|---|---|---|
| [`architecture.md`](architecture.md) | Modules Gradle, pile technique, invariants (pureté de `core`, aucune dépendance non libre), versions. | M0 |
| [`navigation-theme.md`](navigation-theme.md) | Thème MD3 « miel », typographie, icônes, logo, coquille de navigation (rail, barre haute, barre basse Android), écrans, « À propos et guide ». | M0 |
| [`i18n.md`](i18n.md) | Langues (français par défaut, anglais), ressources de chaînes, choix de la langue. | M0 |
| [`distribution.md`](distribution.md) | Versionnage, APK Android, installeurs et zips portables de bureau, version web, CI, releases, GitHub Pages. | M0, M1, M6, M7 |
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
| [`migration-2x.md`](migration-2x.md) | Migration d'une base Kairos 2 : conversion (toutes générations de schéma), Android automatique, bureau proposé, import manuel, tests sur de vraies bases, passage de l'APK 2.x à la 3. | M5, M7 |
| [`accueil.md`](accueil.md) | Accueil au premier lancement : présentation, exemples, bilan ou proposition de migration. | M5 |
| [`apparence.md`](apparence.md) | Couleur du thème : graines proposées, couleur libre, couleurs du système (Android 12 et plus), schéma MD3 dérivé. | #45 |
| [`mises-a-jour.md`](mises-a-jour.md) | Vérification des nouvelles versions sur le bureau (GitHub, 6 heures), bandeau, carte des Réglages. | M5 |
| [`raccourci-portable.md`](raccourci-portable.md) | Copies portables du bureau : détection (marqueur de la release), entrée de menu Linux avec icône et classe de fenêtre, raccourcis Windows (menu Démarrer, Bureau), proposés au lancement, carte des Réglages. | #46 |
| [`accessibilite.md`](accessibilite.md) | Lecteurs d'écran (boutons, états, titres), cibles de 48 dp, contrastes. | M5 |
| [`publication.md`](publication.md) | Fiches Fastlane FR/EN et leurs captures, recette et vérification F-Droid (APK reproductible signé par nous), page de téléchargement GitHub Pages. | M6 |
| [`export-import.md`](export-import.md) | Export et import JSON, sauvegardes, données de la version web (OPFS, fichier lié). | M1 |

## Références transverses

- [`docs/DESIGN_SYSTEM.md`](../DESIGN_SYSTEM.md) : la charte (rôles de
  couleur, formes, « où va la couleur »), en version Compose. Ses valeurs
  sont reprises telles quelles par `navigation-theme.md`.
- [`docs/plan-v3-kotlin.md`](../plan-v3-kotlin.md) : feuille de route et
  décisions de la réécriture.
