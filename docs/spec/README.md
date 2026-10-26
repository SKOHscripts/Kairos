# Spécifications de Kairos : index

Registre de spécification de Kairos (`kmp/`, Kotlin Multiplatform), tenu selon
le workflow de [`CLAUDE.md`](../../CLAUDE.md). Une spec par domaine, en deux
parties (besoin métier, puis solution technique), **bijective** avec le code.

Depuis la bascule `v3.0.0` (jalon M7 de
[`docs/plan-v3-kotlin.md`](../plan-v3-kotlin.md)), ce registre, tenu jusque-là
dans `docs/spec-v3/`, est le seul.

## Domaines

| Spec | Couvre | Jalon |
|---|---|---|
| [`architecture.md`](architecture.md) | Modules Gradle, pile technique, invariants (pureté de `core`, aucune dépendance non libre), versions. | M0 |
| [`navigation-theme.md`](navigation-theme.md) | Thème MD3 « miel », typographie, icônes, logo, coquille de navigation (rail, barre haute, barre basse Android), écrans, « À propos et guide ». | M0 |
| [`densite.md`](densite.md) | Densité visuelle : boutons de 36 dp, puces de 32 dp, segmentés de 40 dp, aire tactile de 48 dp conservée, échelle d'espacement, cartes et champs des Réglages. | passe design |
| [`i18n.md`](i18n.md) | Langues (français, anglais, espagnol, allemand, russe, italien ; repli anglais), ressources de chaînes, choix de la langue dans les Réglages. | M0, 3.3.0 (#70), 3.11.0 (#75) |
| [`distribution.md`](distribution.md) | Versionnage, APK Android, installeurs et zips portables de bureau, version web, CI, releases, GitHub Pages. | M0, M1, M6, M7 |
| [`modele-donnees.md`](modele-donnees.md) | Modèle (tâches, créneaux, dépendances, sessions, notes, réglages), stockage SQLDelight, ouverture et migrations, dépôt, exemples. | M1 |
| [`vue-jour.md`](vue-jour.md) | Vue Jour : capture (tâche, créneau), « À traiter » et « Comment qualifier ? », « Maintenant », agenda ordonné, sections secondaires, « Pourquoi à cette place ? », filtres, backlog, frise, édition complète (guide des points, durées suggérées, sous-tâches existantes), raccourcis, autre jour. | M1, M2, M4, 3.3.0 (#69) |
| [`ordonnancement.md`](ordonnancement.md) | Moteur : score WSJF, placement dans la journée, creux, deep work, jours ouvrés et fériés, ancienneté ; tests sur fichiers d'or figés. | M2 |
| [`dependances.md`](dependances.md) | Blocage, cycles, urgence héritée (chemin critique), bloqueurs dans l'édition. | M2 |
| [`recurrence.md`](recurrence.md) | Tâches récurrentes (à la complétion, « le N du mois »), créneaux récurrents, « Décaler ». | M2 |
| [`widgets-android.md`](widgets-android.md) | Widgets Android « Maintenant » et « Journée » : plan du jour sur l'écran d'accueil, terminer une tâche (double toucher), chrono, capture ; `RemoteViews`, sans permission ni dépendance. | 3.12.0 (#150) |
| [`rappels.md`](rappels.md) | Rappels (désactivés par défaut) : avant une tâche à heure fixe, à la tâche suivante du plan, récapitulatif du jour (3.12.0, #151) ; notifications Android (alarmes inexactes), bureau et web tant que Kairos est ouvert. | 3.8.0 (#103) |
| [`temps-reel-chrono.md`](temps-reel-chrono.md) | Chrono (une session à la fois), temps passé et temps du jour, trois alertes, notifications Android (permanente, alarmes, redémarrage), bureau (plateau) et web. | M3 |
| [`notes-capture.md`](notes-capture.md) | Notes : capture libre, conversion en tâche (titre et description), modification, archivage, suppression. | M4 |
| [`vue-semaine.md`](vue-semaine.md) | Vue Semaine : sept jours (échéances, fait, créneaux), temps réel de la semaine, navigation vers un autre jour. | M4 |
| [`vue-mois.md`](vue-mois.md) | Vue Mois (bascule de la destination Semaine) : grille du mois, tâches prévues, échéances, surcharge, fériés et jours libres, listes, ouverture d'un jour. | 3.8.0 (#107) |
| [`statistiques.md`](statistiques.md) | Statistiques : chiffres clés, débit, calibration et biais, temps par type, meilleures heures par type (3.5), flux, complétude ; repères du guide des points ; tests sur fichiers d'or figés. | M4 |
| [`reglages.md`](reglages.md) | Écran Réglages : catégories (liste puis sous-écrans, deux volets dès 840 dp), sections, champs, bornes, validation par champ et entre champs, un seul « Enregistrer ». | M5, catégories 2026-10-22 |
| [`accueil.md`](accueil.md) | Accueil au premier lancement (présentation, exemples) ; bouton « ? » et aide de l'écran, page Accueil (raison d'être, fonctionnalités), visite guidée. | M5, E6 |
| [`demarrage.md`](demarrage.md) | Page de démarrage commune (Android, bureau, web) : l'aiguille du cadran fait un tour et se pose sur le logo, pause, fondu ; relais natifs (écran de démarrage Android, chargement web) ; rapport de plantage Android. | après 3.2.0, 3.3.0 (#65) |
| [`apparence.md`](apparence.md) | Couleur du thème : graines proposées, couleur libre, couleurs du système (Android 12 et plus), schéma MD3 dérivé. | #45 |
| [`mises-a-jour.md`](mises-a-jour.md) | Vérification des nouvelles versions sur le bureau (GitHub, 6 heures), bandeau, carte des Réglages. | M5 |
| [`bureau-avance.md`](bureau-avance.md) | Bureau : dossier des données choisi (Réglages ou `--data-dir=`, effet au prochain démarrage, copie jamais déplacement), journal de diagnostic local et « Copier le diagnostic », proxy du système. | 3.9.0 (#115) |
| [`raccourci-portable.md`](raccourci-portable.md) | Copies portables du bureau : détection (marqueur de la release), entrée de menu Linux avec icône et classe de fenêtre, raccourcis Windows (menu Démarrer, Bureau), proposés au lancement, carte des Réglages. | #46 |
| [`soutien.md`](soutien.md) | Cœur de la barre du haut et bouton de « À propos » vers la page de don (GitHub Pages). | passe design |
| [`accessibilite.md`](accessibilite.md) | Lecteurs d'écran (boutons, états, titres), cibles de 48 dp, contrastes. | M5 |
| [`publication.md`](publication.md) | Fiches Fastlane FR/EN et leurs captures, recette et vérification F-Droid (APK reproductible signé par nous), page de téléchargement GitHub Pages. | M6 |
| [`export-import.md`](export-import.md) | Export et import JSON, sauvegardes, données de la version web (OPFS, fichier lié). | M1 |
| [`connexions-externes.md`](connexions-externes.md) | Calendriers sans réseau ni compte ; ce que F-Droid permet, décision « pas de permission réseau ». Étape 1 **implémentée en 3.10.0** : import d'un fichier `.ics` en créneaux occupés (lecture seule sauf suppression, réimport à partir d'aujourd'hui, 6 mois, journées entières en badge). Étape 2 **spécifiée, non implémentée** : calendriers du téléphone sur Android (lus en direct). | #136, #138 (suivi #140) |
| [`planning-personnalise.md`](planning-personnalise.md) | Lots A et B1 **implémentés en 3.4.0** (dates, déplacer, blocage, réglages clairs, valeurs par défaut ; horaires par jour de la semaine), lot B2 **implémenté en 3.5.0** (plusieurs plages par jour et contextes), étape C1 du lot C **implémentée en 3.5.0** (carte Statistiques « Quand tu travailles chaque type ») ; détail dans `vue-jour`, `vue-semaine`, `recurrence`, `reglages`, `ordonnancement`, `statistiques` ; étape C2 du lot C **implémentée en 3.6.0** (réglage « Meilleures heures » : le planning préfère, à score voisin, les heures habituelles ou préférées de chaque type). | #64, 3.4-3.6 |
| [`durees-apprises.md`](durees-apprises.md) | **Implémentée en 3.4.0** (durée apprise par série puis par titre, `seriesId` stable, provenance, réglage ; port d'estimation prêt pour un futur modèle d'apprentissage). | #64, 3.4 |
| [`visite-guidee.md`](visite-guidee.md) | **Implémentée en 3.4.0.** Catalogue de fonctionnalités, visites en sélections, hub « Découvrir Kairos », « Nouveau dans cette version ». | #64, 3.4 |
| [`premiere-tache.md`](premiere-tache.md) | **Implémentée en 3.4.0.** Accompagnement de la création de la première tâche (champs, priorité, points, estimation, placement, félicitations). | #64, 3.4 |
| [`retrait-kairos2.md`](retrait-kairos2.md) | Retrait de l'ancienne version (Python) : import, écrans, textes, tests différentiels devenus tests sur fichiers d'or figés, test de garde. | #64, 3.4 |

## Chantier « Espace Équipe »

Specs écrites le 2026-09-30 (jalon E0). **Jalons E1 à E6 implémentés**
(fondations : espaces, vue Perso, migrations, export 2, carte Équipe,
sélecteur, coquille ; membres et absences ; backlog, suivi et journal ;
capacité, charge et répartition ; prévisions et scénarios) et **E6**
(échanges par fichier : paquets et rapports) ; chacune liste en fin de
document ses impacts sur les specs ci-dessus, reportés. Skills Claude Code associées : `.claude/skills/kairos-equipe/` et
`.claude/skills/kairos-monte-carlo/`.

| Spec | Couvre | Jalon |
|---|---|---|
| [`equipe.md`](equipe.md) | Spec chapeau : décisions de cadrage, activation, espaces Perso et Équipe, sélecteur, membres et absences, **isolation du mode solo**, modèle et stockage communs, export 2, jalons E0-E6. | E1, E2 |
| [`equipe-backlog-suivi.md`](equipe-backlog-suivi.md) | Backlog d'équipe, catégories (types), assignation, réaffectation, états et avancement, tableau de suivi, signaux, journal, tâches assignées à moi. | E3 |
| [`equipe-charge.md`](equipe-charge.md) | Capacité, effort restant, charge individuelle, globale et par catégorie, plan de charge, suggestion de répartition. | E4 |
| [`equipe-simulation.md`](equipe-simulation.md) | Prévisions Monte Carlo (effort, débit), percentiles, échéances, criticité, goulot, issue la plus probable, scénarios « Et si… ? ». | E5 |
| [`equipe-moi.md`](equipe-moi.md) | Travail perso de « moi » dans la charge et les prévisions (compter, exclure), récurrences projetées, créneaux de « moi », historique perso, temps passé déduit, périmètre « Mon travail ». | après 3.2.0 |
| [`equipe-echanges.md`](equipe-echanges.md) | Paquets de tâches (manager → membre), rapports d'avancement (membre → manager), fusion ciblée par fichier. | E6 |

## Références transverses

- [`docs/DESIGN_SYSTEM.md`](../DESIGN_SYSTEM.md) : la charte (rôles de
  couleur, formes, « où va la couleur »), en version Compose. Ses valeurs
  sont reprises telles quelles par `navigation-theme.md`.
- [`docs/plan-v3-kotlin.md`](../plan-v3-kotlin.md) : feuille de route et
  décisions de la réécriture.
