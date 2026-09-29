# Plan d'attaque : Kairos 3, réécriture Kotlin Multiplatform

_Statut : **validé le 2026-09-28** (étape 1 du workflow de `CLAUDE.md` :
« spécifier d'abord »), avec les réponses de l'utilisateur au § 12 et l'ajout
d'une version web et d'un zip portable pour les postes sans droit
d'installation (§ 1). Une fois ce plan validé, il
sert de feuille de route. Chaque jalon rédige ou met à jour sa spec de domaine
avant de coder, et la tient bijective avec le code livré._

---

## 0. En une page

**Produit final.**

1. Une application **Android native** (Kotlin, Jetpack Compose), prête pour
   **IzzyOnDroid** et **F-Droid** : compilable depuis les sources, sans
   dépendance propriétaire, sans anti-fonctionnalité.
2. Des applications **de bureau Windows, Linux et macOS**, issues du même code,
   en installeur **et en zip portable** (dézipper, lancer : aucune
   installation, aucun droit administrateur).
3. Une **page web sur GitHub Pages** qui présente Kairos et donne le bon
   installeur pour chaque OS, plus les liens F-Droid et IzzyOnDroid.
4. Une **version web** de l'application, servie par la même page GitHub
   Pages, pour les postes professionnels verrouillés : elle tourne dans Edge
   ou Chrome, sans rien installer, et garde ses données sur le poste (§ 5.4).

**Choix structurant.** On passe à **Kotlin Multiplatform + Compose
Multiplatform** : un seul code métier et un seul code d'interface, pour
Android, le bureau (JVM) et le navigateur (Kotlin/Wasm). Le serveur local Python, la WebView, Chaquopy
et PyInstaller disparaissent.

**Méthode.**

- La réécriture se fait **dans ce dépôt**, dans un dossier `kmp/`, à côté du
  Python.
- On avance **jalon par jalon**. Chaque jalon produit une préversion
  installable (`v3.0.0-alpha.N`) sur les quatre cibles.
- La parité avec la version Python est **mesurée** par des tests
  différentiels (§ 7), pas estimée.
- Le Python est supprimé à la bascule `v3.0.0`, une fois la parité atteinte.
- Sur Android, la v3 remplace l'APK Python par une simple mise à jour (même
  identifiant, même clé), avec **migration automatique des données**.

**Retiré du périmètre** (décision utilisateur du 2026-09-28) :

- TimeTree ;
- l'import GitLab direct ;
- toute mention de la base « pilotage » ;
- le service systemd.

---

## 1. Décisions actées avec l'utilisateur (2026-09-28)

| Sujet | Décision |
|---|---|
| Technologie | Kotlin Multiplatform + Compose Multiplatform (option « B ») : Android + bureau JVM. |
| Diffusion bureau | Installeurs Windows/Linux/macOS publiés en releases GitHub, présentés par une page GitHub Pages. Pas d'application web. |
| Publication | **Après** la réécriture complète : pas de soumission F-Droid ni IzzyOnDroid de la version Python. |
| Intégrations | TimeTree, import GitLab direct, liaison base pilotage : **retirées**. |
| Service systemd | **Retiré**, ainsi que l'installation « depuis les sources » (clone + venv). |
| Dépôt | Même dépôt `SKOHscripts/Kairos`, dossier `kmp/` pendant la transition. Le Python est supprimé à la bascule. |
| Données existantes | **Import** de la base de la version Python (tâches, temps, notes, réglages) : personne ne perd son historique. |
| Langues | **Français + anglais dès le départ** (français par défaut). |
| Synchronisation | Aucune. Chaque appareil a sa base locale, avec **export/import manuel** d'un fichier (sauvegarde, changement d'appareil). |
| Méthode | Plan complet d'abord (ce document), puis version packagée après version packagée, en suivant une spec complète. |
| Postes pro sans installation | Deux réponses : un **zip portable** par OS (équivalent de l'exécutable PyInstaller actuel, qui tourne déjà sur ces postes) et une **version web** (Kotlin/Wasm) sur GitHub Pages, base dans le navigateur liée à un fichier local. Navigateur visé sur ces postes : **Edge ou Chrome**. |
| `CLAUDE.md` | Peut être remis en cause : il est réécrit pour la v3 (§ 3, § 8). |
| Questions du § 12 | Toutes acceptées telles que proposées. |

---

## 2. Périmètre fonctionnel

### 2.1 Porté à l'identique : le cœur de Kairos

Le « besoin métier » (partie 1) des specs actuelles reste **valide tel quel**
pour ces domaines. Il est repris dans les specs v3. Seule la partie
« solution technique » est réécrite.

| Domaine (spec actuelle) | Contenu à porter |
|---|---|
| `ordonnancement.md` | Score WSJF (valeur exponentielle, criticité d'échéance en rampe, effort Fibonacci, repli sur la durée), paliers durs (retard, EDD), buckets d'urgence, gate « À traiter », placement autour des créneaux avec marge, épinglage et chevauchement signalé, fenêtres deep work, creux de l'après-midi, charge du jour (requis / disponible / débordement), invariant de conservation (chaque tâche dans exactement une liste), timeline avec rail « réel ». |
| `dependances.md` | Blocage transitif, neutralisation des cycles (Kahn), refus d'un cycle à l'écriture, urgence dérivée et badge « chemin critique », non destructive. |
| `recurrence.md` | Tâches : recréation à la complétion (`daily`/`weekdays`/`weekly`/`monthly`, ancre hebdo sans dérive), calendaire « le N du mois » avec recul au jour ouvré, pas de rattrapage. Blocs : projection à la volée. Snooze au prochain jour ouvré. |
| `workdays` (module `app/workdays.py`) | Jours fériés français et jours fériés supplémentaires. |
| `temps-reel-chrono.md` | Sessions (une seule ouverte à la fois), temps du jour et de la semaine par type, chrono vivant, trois alertes (dépassement, oubli, pause), « traîne depuis N j », bandeau de surcharge P0. |
| `vue-jour-gtd.md` | Capture sans friction, boîte de réception et qualification en un clic (pastilles P0/P1/P2 et 1…21 avec leur sens), carte « Maintenant » avec actions nommées, agenda ordonné, sections secondaires, filtres, recherche, backlog, ligne de tâche en quatre colonnes fixes, description visible dans la liste, « Pourquoi à cette place ? », guide des points fondé sur l'historique, dialogue d'édition (sous-tâches en lot, bloqueurs, épinglage, récurrence), raccourcis `N` et `/` sur le bureau. |
| Vue Semaine (dans `vue-jour-gtd.md` / `statistiques.md`) | Grille sur 7 jours et synthèse du temps réel par type. |
| `notes-capture.md` | Capture libre, conversion en tâche (première ligne = titre, reste = description), archivage, suppression, historique « Traité / archivé ». On ajoute l'édition d'une note, qui existait côté serveur sans interface. |
| `statistiques.md` | Six blocs (KPI, débit hebdomadaire, calibration par palier, répartition par type, flux et backlog, complétude), effectif `n` et mention « peu fiable » sous `MIN_SAMPLE`. |
| `modele-donnees.md` | Task, TimeBlock, TaskDependency, WorkSession, Note, et les exemples au premier lancement. |
| `reglages-secrets.md` | Réglages restants (§ 2.3), validation par champ, aucune configuration requise au démarrage. |
| `mises-a-jour.md` | **Bureau seulement** : prévenir d'une nouvelle version (§ 6.2). |

### 2.2 Retiré

| Élément | Conséquence |
|---|---|
| TimeTree | Plus de créneaux importés, de réglages `timetree_*`, de cache, ni de bandeau TimeTree. |
| Import GitLab direct, résolution `git credential` / `.netrc` | Plus de réglages `gitlab_*`, de `TaskSyncMeta`, de lien vers l'issue, ni de bandeau GitLab. |
| Base pilotage (`pilotage_database_path`, `linked_ticket_id`, « Fiche liée ») | Supprimée du modèle, de l'interface et des specs. Le type par défaut « Pilotage/dette technique » est retiré de `task_types`. |
| Service systemd, installation depuis les sources | Plus de section README, plus de commande `git pull` dans le bandeau de mise à jour. |
| Proxy sortant (`http_proxy`…) | Il ne servait qu'à TimeTree. La vérification des mises à jour sur le bureau utilise le proxy système de la JVM (`java.net.useSystemProxies`). |
| Trousseau système (`keyring`, `secret_store`) | Plus aucun secret à stocker : le seul restant était le jeton de mise à jour d'une forge privée (§ 2.4). |
| Source de mise à jour configurable (GitLab privé, jeton) | La source est fixée aux releases GitHub publiques. Le pipeline `.gitlab-ci.yml` disparaît. |
| Mise à jour intégrée sur Android | F-Droid et IzzyOnDroid gèrent les mises à jour. L'APK ne demande plus `REQUEST_INSTALL_PACKAGES`. |
| `log_level` | Remplacé par un journal fichier fixe (§ 6.2). |

### 2.3 Réglages conservés

Durées et journée : `default_task_duration_minutes`, `meeting_buffer_minutes`,
`workday_start_hour`, `workday_end_hour`.

Garde-fous : `stale_overdue_days`, `stale_untouched_days`,
`priority_overload_threshold`.

WSJF : `priority_value_base`, `urgency_horizon_days`, `urgency_peak`,
`default_fibonacci_points`.

Creux de l'après-midi : `cognitive_dip_*` (activation, début, creux, fin,
pénalité).

Statistiques : `stats_window_weeks`.

Chrono : `timer_idle_alert_minutes`, `pomodoro_focus_minutes`,
`timer_alert_sound`.

Jours fériés : `holidays_fr`, `extra_holidays`.

Types : `task_types`.

Mises à jour, bureau seulement : `update_check_enabled`.

Emplacement de la base : `tasks_database_path` est **remplacé** par
l'affichage du dossier de données, avec les boutons Exporter / Importer.

### 2.4 Remplacé : l'équivalent natif d'un mécanisme web

| Mécanisme actuel | Équivalent v3 |
|---|---|
| Serveur local uvicorn, WebView, port, verrou d'instance | Aucun serveur. Application native. Instance unique sur le bureau (verrou fichier, et la seconde instance ramène la fenêtre au premier plan). |
| Amélioration progressive sans JavaScript, fragments AJAX, restauration du défilement | Sans objet : l'interface est à état (Compose), rien ne se recharge. |
| Page d'accueil qui rend le `README.md` | Écran **« À propos et guide »** natif et traduit (formule du score, flux GTD). Un **accueil au premier lancement** présente les exemples. |
| Bouton « Quitter » (PyInstaller) | Fermeture de fenêtre et menu standard du bureau. |
| Notifications : pont Android, API web, `notify-send` | Android : notification native, notification permanente avec chronomètre, alarmes pour les trois seuils. Bureau : notification système (plateau de Compose Desktop), avec repli dans la fenêtre. |
| Écran de démarrage en trois relais (Chaquopy) | Écran de démarrage système Android standard. L'application démarre en moins d'une seconde : plus d'attente de Python. |
| Limite v1 « un chrono ne survit pas à une mise en veille agressive » | **Levée par construction** : le chrono est un horodatage en base, et les alertes passent par des alarmes système. |

---

## 3. Décisions déjà consignées à rouvrir explicitement

`CLAUDE.md` interdit de trancher à nouveau une décision consignée sans la
rouvrir. La réécriture en remet plusieurs en cause. Les voici, avec la
proposition. Elles sont à valider avec ce plan, puis à tracer dans les specs
v3 et dans le nouveau `CLAUDE.md`.

1. **« Rester en HTML/CSS pur, sans dépendance de build ni bibliothèque de
   composants JavaScript »** (CLAUDE.md, § Navigation et mobile).
   → Remplacé par Compose Multiplatform. Les composants MD3 deviennent ceux
   de `material3`, au lieu d'être rendus par le CSS.
2. **« Pas d'AndroidX »** (`ANDROID_PACKAGING.md`, `UpdateApkProvider`,
   `KairosNotificationBridge`).
   → AndroidX est **nécessaire** à Compose et il est libre (Apache 2.0),
   donc compatible F-Droid. La contrainte n'avait de sens que pour limiter
   l'APK Chaquopy.
3. **« Aucune détection de plateforme côté serveur, sauf `is_android` pour la
   barre basse »** (`accueil-navigation.md`).
   → Il n'y a plus de serveur. La navigation se choisit selon la **classe de
   taille de fenêtre** MD3 (compacte, moyenne, étendue), **sauf** une règle
   qui reste : jamais de barre de navigation basse sur le bureau. Android
   compact : barre basse. Bureau et tablette : rail.
4. **Six destinations de navigation.** MD3 recommande 3 à 5 destinations dans
   une barre de navigation.
   → Proposition : Notes, Jour, Semaine, Statistiques, Réglages. « Accueil »
   devient « À propos et guide », accessible depuis Réglages et au premier
   lancement.
5. **Distribution Android « releases GitHub uniquement, pas de store »**
   (`ANDROID_PACKAGING.md`).
   → IzzyOnDroid et F-Droid, en plus des releases GitHub.
6. **« Build macOS non demandé »** (`packaging-lancement.md`).
   → macOS entre dans le périmètre.
7. **Mise à jour intégrée sur Android** (`mises-a-jour.md`).
   → Retirée (§ 2.2).
8. **Icônes générées en SVG inline par `make_icons.py`, polices servies par
   l'app.**
   → Le principe est conservé (hors ligne, jamais de police distante), le
   support change : icônes Material Symbols converties en `ImageVector` par un
   script générateur, Roboto embarquée dans les ressources Compose.

---

## 4. Architecture cible

### 4.1 Pile technique (choix proposés)

| Besoin | Choix | Alternatives écartées |
|---|---|---|
| Langage | Kotlin 2.x (dernière version stable au démarrage de M0) | — |
| Interface | Compose Multiplatform (JetBrains) + `material3`, cibles Android, JVM et `wasmJs` | Interface native séparée par plateforme : deux interfaces à maintenir. |
| Base de données | **SQLDelight 2** en mode **asynchrone** (`generateAsync`) : pilote Android, pilote JDBC `sqlite-jdbc` sur le bureau, pilote *web worker* sur sql.js (SQLite en Wasm) dans le navigateur, base en mémoire sauvegardée en JSON (§ 5.3). Toute l'API de données est donc `suspend`, sur toutes les plateformes. | Room KMP : pas de cible web. SQLDelight garde un **SQL explicite**, pratique pour importer une base SQLite Python existante et pour écrire des migrations `.sqm` vérifiées. |
| Dates et heures | `kotlinx-datetime` | `java.time` : absent du code commun. |
| Sérialisation (export/import, réglages) | `kotlinx-serialization` (JSON) | — |
| Navigation | Navigation Compose multiplateforme (`org.jetbrains.androidx.navigation`) | Pile d'états maison : on la garde en repli si la bibliothèque freine. |
| État | ViewModel multiplateforme + `StateFlow` | — |
| Injection de dépendances | **Manuelle** (un objet `AppGraph`) | Koin : inutile à cette taille. |
| Réseau (bureau, mises à jour) | `HttpURLConnection` de la JVM, **une seule requête** | Ktor : trop lourd pour un seul appel. |
| i18n | Ressources Compose (`composeResources/values/strings.xml`, `values-en/`), formats de date par locale | — |
| Tests | `kotlin.test` en code commun, tests d'interface Compose en JVM | — |

**Règle d'or pour F-Droid** : aucune dépendance non libre. Pas de Google Play
Services, de Firebase, d'analytics ni de rapport de crash distant. On
**vérifie** chaque ajout de dépendance (licence, dépôt Maven Central ou
Google, pas de binaire opaque).

### 4.2 Modules Gradle

```
kmp/
├── settings.gradle.kts, gradle/libs.versions.toml, gradlew
├── core/          # KMP, code commun PUR : aucune dépendance d'interface ni de base
│   ├── model/        Task, TimeBlock, WorkSession, Note, Settings (data classes)
│   ├── scheduling/   WSJF, buckets, placement, creux, deep work, timeline
│   ├── dependencies/ blocage transitif, cycles, urgence dérivée
│   ├── recurrence/   tâches, blocs, snooze
│   ├── workdays/     jours fériés FR, jours ouvrés
│   ├── time/         agrégats de sessions, staleness, surcharge
│   ├── stats/        indicateurs, calibration, guide des points
│   └── alerts/       calcul des seuils du chrono (pur : quand notifier)
├── data/          # KMP : SQLDelight, dépôts, export/import JSON, migration legacy (créé en M1)
├── ui/            # KMP Compose : thème miel, composants, écrans, ViewModels, ressources i18n
├── androidApp/    # Activity, notifications, alarmes, icône, métadonnées
├── desktopApp/    # main(), fenêtre, plateau, notifications, instance unique, jpackage, zip portable
└── webApp/        # main() wasmJs, index.html, stockage OPFS et fichier lié
```

**Invariant d'architecture** : `core` est **pur**, comme aujourd'hui
`tasks_scheduling.py` et `tasks_dependencies.py`. Il ne connaît ni la base ni
l'horloge système : `now` est toujours passé en paramètre. C'est ce qui rend
possibles les tests différentiels du § 7.

### 4.3 Thème et design system

- Rôles MD3 du thème « miel » **repris tels quels** de `docs/DESIGN_SYSTEM.md`
  (graine `#C28417`), déclarés une seule fois dans un `ColorScheme`
  `material3`, avec la couleur personnalisée `kx-ok`.
- Tout le reste de la charte se transpose : où va la couleur, pas d'ambre,
  formes, jamais d'ombre sur une carte, ligne de tâche en quatre colonnes,
  densité en body-medium 14sp.
- `docs/DESIGN_SYSTEM.md` est réécrit en termes Compose au jalon M0. Les noms
  de classes CSS deviennent des noms de composants.

---

## 5. Données

### 5.1 Schéma v3

Un seul fichier SQLite par appareil, dans le dossier de données de l'OS :

- Android : stockage privé de l'application.
- Linux : `~/.local/share/Kairos`.
- Windows : `%APPDATA%\Kairos`.
- macOS : `~/Library/Application Support/Kairos`.

Le dossier v3 est **distinct** de celui de la version Python sur le bureau
(`tasks-v3.db`) : l'ancienne base n'est jamais modifiée.

Tables et colonnes, reprises du schéma Python moins les colonnes des
intégrations retirées :

- `task` : `id`, `title`, `description`, `priority?`, `deadline?`,
  `project_tag`, `status`, `estimated_minutes?`, `pinned_start?`, `parent_id?`,
  `recurrence`, `scheduled_date?`, `recurrence_day_of_month?`,
  `recurrence_day_of_week?`, `recurrence_period`, `task_type`,
  `fibonacci_points?`, `manual_time_spent_minutes?`, `created_at`, `updated_at`.
  Colonnes supprimées : `source`, `external_id`, `linked_ticket_id`.
- `time_block` : `id`, `title`, `start`, `end`, `kind` (`busy`/`deep`),
  `recurrence`, `created_at`. Colonnes supprimées : `source`, `external_id`.
- `task_dependency`, `work_session`, `note` : inchangées.
- `settings` : une ligne JSON versionnée. Les réglages voyagent ainsi avec la
  base et avec l'export.
- `schema_version`, géré par les migrations SQLDelight (`.sqm` numérotées,
  **additives**, testées). Le principe « une mise à jour ne perd jamais de
  données » de `modele-donnees.md` est conservé.
- Exemples au premier lancement (`[Exemple]`, tag « Exemple »), **traduits**
  selon la langue du système à la création.

### 5.2 Export et import (sauvegarde, changement d'appareil)

- **Exporter** : un fichier `kairos-export-AAAAMMJJ.json` (format documenté,
  `formatVersion`) qui contient toutes les tables et les réglages. Sur Android,
  le fichier passe par le sélecteur système (Storage Access Framework, sans
  permission de stockage). Sur le bureau, par la boîte de dialogue
  d'enregistrement.
- **Importer** : **remplace** la base après confirmation. Une sauvegarde
  automatique de la base courante est faite juste avant. Pas de fusion : sans
  synchronisation, une fusion créerait des doublons indétectables.
- Aucun appel réseau : ni le fichier ni les données ne quittent l'appareil
  sans geste de l'utilisateur.

### 5.3 Données de la version web

- La base SQLite (sql.js) tourne **en mémoire** dans un worker ; à chaque
  modification, un instantané JSON (le format d'export) est écrit dans
  l'**OPFS** du navigateur (stockage privé de l'origine
  `skohscripts.github.io`). Décision prise au jalon M1 : le stockage SQLite
  direct dans l'OPFS exige des en-têtes COOP/COEP que GitHub Pages n'envoie
  pas (`docs/spec/export-import.md` § Décisions).
- Beaucoup de postes professionnels **vident les données du navigateur** à la
  fermeture. La version web propose donc de **lier la base à un fichier
  local** (API File System Access, disponible dans Edge et Chrome) : chaque
  modification y est recopiée, et au lancement, si l'OPFS est vide, la base
  est rechargée depuis ce fichier. Le navigateur redemande l'accord de
  l'utilisateur à chaque session, sauf s'il a choisi « Autoriser à chaque
  visite ».
- Sans fichier lié, un bandeau permanent rappelle le risque et propose
  l'export. Le format du fichier lié est celui de l'export JSON (§ 5.2).
  Ainsi, le même fichier s'ouvre dans l'application de bureau ou sur Android.
- `navigator.storage.persist()` est demandé au premier lancement pour limiter
  l'éviction automatique.

### 5.4 Migration depuis la version Python

| Cible | Mécanisme |
|---|---|
| **Android** | L'APK v3 garde le même `applicationId` (`com.skohscripts.kairos`) et la même clé de signature : il s'installe **par-dessus** l'APK Python et hérite de son stockage privé. Au premier lancement, si `files/kairos-data/tasks.db` existe et qu'aucune base v3 n'existe encore, la migration est **automatique**, puis confirmée à l'écran (« N tâches, N notes, N sessions importées »). L'ancien fichier est renommé, jamais supprimé. |
| **Bureau** | Au premier lancement, on cherche l'ancienne base au chemin de `platformdirs` (`user_data_dir("Kairos")/tasks.db` et `settings.json`, ou la valeur de `tasks_database_path` lue dans ce `settings.json`). Si on la trouve, on **propose** l'import. Sinon, un bouton « Importer une base Kairos 2.x » ouvre un sélecteur de fichier. |

Règles de conversion, qui seront testées sur une vraie base de chaque
génération de schéma :

- Les tâches importées de GitLab deviennent des tâches **natives** (on garde
  titre, priorité, temps, et le projet en `project_tag`), sans lien externe.
- Les créneaux TimeTree (`time_block.source != "manual"`) sont **ignorés**. Ce
  ne sont que des copies de cache.
- Les réglages sont repris champ par champ quand le champ existe encore. Les
  autres sont ignorés et journalisés.
- La table `TaskSyncMeta` est ignorée.

Point à trancher avec l'utilisateur au jalon M5 : sur Android, cette
migration **ne marche pas** entre l'APK Python (clé personnelle) et un APK
signé par F-Droid (autre clé). Comme la v3 est la **première** version publiée
sur F-Droid, seuls les utilisateurs de l'APK GitHub sont concernés, et
l'installation par-dessus fonctionne pour eux. (Jalon M5 : migration faite, voir
`docs/spec/migration-2x.md`.)

**Tranché avant la 3.0.0 (2026-09-29, jalon M7)** :

- F-Droid distribue **notre** APK signé (`Binaries` +
  `AllowedAPKSigningKeys`, build reproductible vérifié en CI) : la question
  de la clé ne se pose plus, l'APK Python se met à jour vers Kairos 3 quelle
  que soit la source.
- La mise à jour intégrée d'Android est abandonnée (elle n'attendait que
  F-Droid). Dernier service rendu : la release 3.0.0 publie une copie de
  l'APK sous le nom que cherche Kairos 2 (`kairos-android-arm64.apk`), pour
  que l'APK Python installe Kairos 3 en un clic
  (`docs/spec/migration-2x.md` § Pont de mise à jour).

---

## 6. Plateformes et distribution

### 6.1 Android

- `minSdk 26` (Android 8), `targetSdk` = dernier niveau exigé au moment de la
  publication.
- `versionCode` et `versionName` **écrits dans `androidApp/build.gradle.kts`**.
  Ils ne viennent plus d'une variable d'environnement : F-Droid lit la version
  dans le dépôt pour détecter les nouvelles releases (`UpdateCheckMode: Tags`).
  La CI **vérifie** que le tag correspond.
- **Permissions visées** :
  - `POST_NOTIFICATIONS`, demandée au moment de l'opt-in des alertes, jamais
    au démarrage ;
  - `RECEIVE_BOOT_COMPLETED`, pour remettre la notification du chrono et ses
    alarmes après un redémarrage ;
  - **pas** de `SCHEDULE_EXACT_ALARM` ni de `USE_EXACT_ALARM` : tranché en
    M3, alarmes inexactes (`temps-reel-chrono.md` § Décisions) ;
  - **pas de `INTERNET`**. L'application Android ne fait aucun appel réseau,
    donc **aucune anti-fonctionnalité F-Droid**, ni `NonFreeNet` ni
    `Tracking`.
- Chrono : une notification permanente avec chronomètre système
  (`setUsesChronometer`), dont les actions « Arrêter » et « Ouvrir » tournent
  sans service au premier plan. Les seuils passent par `AlarmManager`.
- R8 activé, **builds reproductibles** visés : pas d'horodatage ni de chemin
  absolu dans l'APK, versions figées dans `libs.versions.toml`, JDK précisé.
  Ça permet à F-Droid de publier **l'APK signé par nous** (vérification de
  reproductibilité). Les utilisateurs peuvent alors passer d'un canal à
  l'autre (GitHub, IzzyOnDroid, F-Droid) sans désinstaller.
- **Métadonnées Fastlane** à la racine du dépôt,
  `fastlane/metadata/android/{fr-FR,en-US}/` : titre, description courte
  (80 caractères au plus), description complète, `images/icon.png` en 512 px,
  captures `phoneScreenshots/`, `changelogs/<versionCode>.txt` (500 caractères
  au plus).
- **Recette F-Droid** `fdroid/com.skohscripts.kairos.yml`, sur le modèle de
  meteocompare : `subdir: kmp/androidApp`, `gradle: [yes]`,
  `AutoUpdateMode: Version`, `UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$`,
  `License: MIT`. Validée **localement** avec `fdroid lint`, puis
  `fdroid build` dans le conteneur `fdroidserver` en CI, avant toute
  soumission.

### 6.2 Bureau (Windows, Linux, macOS) et postes sans installation

- Plugin Compose Desktop, cible `nativeDistributions` (jpackage, runtime Java
  réduit par `jlink` et embarqué) :
  - Windows : `.msi`, avec un `upgradeUuid` fixe pour que chaque version
    remplace la précédente ;
  - Linux : `.deb`, plus une archive `.tar.gz` portable ;
  - macOS : `.dmg`, une version Apple Silicon et une version Intel.
- **Zip portable** pour chaque OS (`Kairos-windows-x64-portable.zip`,
  `Kairos-linux-x64-portable.tar.gz`, `Kairos-macos-arm64-portable.zip`) :
  l'image d'application produite par `createDistributable` (lanceur + runtime
  Java embarqué), à dézipper n'importe où et à lancer, sans installation ni
  droit administrateur. C'est l'équivalent de l'exécutable PyInstaller
  actuel. Les données restent dans le dossier de données de l'utilisateur.
- jpackage **ne compile pas pour un autre OS** : chaque installeur est produit
  sur un runner de son OS (matrice GitHub Actions `ubuntu`, `windows`,
  `macos` arm64 et x64).
- Taille attendue : 50 à 90 Mo par installeur, runtime Java compris.
- **macOS sans compte Apple Developer** : le `.dmg` n'est ni signé ni
  notarisé. Gatekeeper le bloque au premier lancement. La page de
  téléchargement explique le contournement (clic droit, puis « Ouvrir »). La
  notarisation (99 $ par an) est **différée**, à décider plus tard.
  Windows SmartScreen affichera aussi un avertissement tant que l'exécutable
  n'est pas signé : c'est documenté.
- Instance unique, et journal de crash dans le dossier de données (reprise de
  `packaging-lancement.md`).
- Mises à jour :
  - vérification (au plus toutes les 6 heures, désactivable) de
    `api.github.com/repos/SKOHscripts/Kairos/releases/latest`, en ignorant les
    préversions ;
  - bandeau « Kairos X.Y.Z est disponible » avec « Télécharger », qui ouvre
    la page GitHub Pages, et « Plus tard » ;
  - **pas de remplacement automatique** de l'application : l'installeur de
    l'OS gère le remplacement.
  - Ce choix est plus simple et plus sûr que l'actuel remplacement de
    l'exécutable. C'est une **régression assumée** du « clic pour mettre à
    jour », à valider.

### 6.3 Page GitHub Pages et version web

- Site statique dans `site/`, déployé par GitHub Actions
  (`actions/deploy-pages`) à chaque release et à chaque modification de
  `site/`.
- Pas de framework ni de dépendance de build : HTML et CSS aux couleurs de la
  charte, Roboto servie localement, en français et en anglais.
- Contenu :
  - présentation en trois points, avec la formule du score ;
  - captures d'écran ;
  - boutons de téléchargement par OS ;
  - badges IzzyOnDroid, F-Droid et APK GitHub (au jalon M7, une fois
    l'application référencée ; d'ici là, « Bientôt sur F-Droid et
    IzzyOnDroid ») ;
  - note de première installation (macOS, SmartScreen) ;
  - lien vers le code et la licence.
- Les liens de téléchargement reposent sur des **noms d'assets fixes**
  (`Kairos-windows-x64.msi`, et ainsi de suite) sous
  `releases/download/<tag v3>`, le tag étant choisi par `pages.yml` à chaque
  release. Révisé en M6 : `releases/latest/` désigne une 2.x tant que la
  3.0.0 n'est pas sortie (`docs/spec/publication.md` § Décisions).
- Un petit script facultatif détecte l'OS du visiteur pour mettre son bouton
  en avant. La page reste complète sans JavaScript.
- La **version web** de l'application est servie au même endroit, sous
  `/app/`. C'est le bouton « Utiliser dans le navigateur (sans installation) »,
  pour Edge et Chrome récents : Kotlin/Wasm exige WasmGC, présent depuis
  Chrome et Edge 119.
- Pendant les alphas, la version web de préversion est publiée sous
  `/preview/`, pour ne jamais remplacer une version stable.

### 6.4 CI (GitHub Actions)

| Workflow | Déclencheur | Rôle |
|---|---|---|
| `ci.yml` | push et PR | Tests du code commun et JVM, lint (`ktlint` ou `detekt`), build de l'APK debug. Tests Python conservés **jusqu'à la bascule**, ainsi que les tests différentiels. |
| `release.yml` | tag `v*` | Vérification tag = version ; APK signé (secrets `KAIROS_KEYSTORE_*` existants, **même clé**) ; installeurs sur trois OS ; `SHA256SUMS` ; release GitHub, en **préversion** si le tag a un suffixe. |
| `pages.yml` | release publiée, modification de `site/` | Déploiement de la page et de la version web (`/app/` pour une release, `/preview/` pour une préversion). |
| `fdroid-check.yml` | tag, ou manuel | `fdroid lint` et `fdroid build` de la recette dans le conteneur officiel, puis comparaison de reproductibilité avec l'APK publié. |

---

## 7. Stratégie qualité : prouver la parité

1. **Portage des tests.** Les 28 fichiers de `tests/` (environ 8 900 lignes)
   sont la spécification exécutable du comportement. Ceux des domaines
   conservés sont portés en `kotlin.test`, cas par cas, avec les mêmes noms
   de scénario. Ceux des domaines retirés (`timetree`, `gitlab`,
   `git_credentials`, `secret_store`, `launcher`…) ne sont pas portés.
2. **Tests différentiels contre le Python** (tant qu'il est dans le dépôt) :
   - Un script `kmp/tools/gen_fixtures.py` exécute **le moteur Python actuel**
     (`tasks_scheduling`, `tasks_dependencies`, `tasks_recurrence`,
     `workdays`, `tasks_stats`, `tasks_time`, `tasks_staleness`) sur des
     centaines de scénarios tirés au hasard avec une graine fixe : tâches,
     blocs, dépendances, récurrences, sessions, réglages, `now`.
   - Il écrit les entrées et les **sorties attendues** en JSON dans
     `kmp/core/src/commonTest/resources/fixtures/`.
   - Le code Kotlin doit produire **exactement** les mêmes sorties : ordre,
     heures, listes, scores arrondis.
   - Tout écart est un bug du portage, ou une divergence **volontaire** tracée
     en spec.
   - C'est ce qui rend la réécriture sûre sur les moteurs les plus subtils
     (placement, creux, ancre hebdomadaire, cycles).
3. **Test de propriété** de l'invariant de conservation (chaque tâche dans
   exactement une liste), repris de `ordonnancement.md`.
4. **Tests d'interface Compose** (JVM) pour les critères de succès
   observables : qualification en un clic, colonnes alignées, aucune
   troncature ni débordement sur une largeur de 360 dp, raccourcis clavier.
5. **Test de migration** sur des bases Python réelles de plusieurs
   générations de schéma, archivées dans `kmp/data/src/jvmTest/resources/legacy/`.
6. **Test de démarrage** (équivalent du smoke test actuel) : chaque installeur
   de bureau est lancé en CI en mode `--self-test` (ouvre la base, calcule la
   journée, puis quitte avec le code 0). L'APK passe par un test instrumenté
   sur émulateur si la CI le permet, sinon par un test Robolectric.

---

## 8. Specs et documentation pendant la transition

- **Deux registres, un par code**, pour que chacun reste bijectif :
  - `docs/spec/` décrit le code Python **tant qu'il existe**. Il n'est plus
    modifié, sauf correctif.
  - `docs/spec-v3/` décrit le code `kmp/`. Il est rempli **jalon par jalon** :
    - partie 1 (besoin métier) : reprise des specs actuelles, moins le
      périmètre retiré ;
    - partie 2 (solution technique) : rédigée pour Kotlin.
- **Nouvelles specs v3** : `architecture.md` (modules, invariants de pureté,
  pile), `export-import-migration.md`, `distribution.md` (Android,
  F-Droid, IzzyOnDroid, bureau, page web, CI), `i18n.md`.
- À la bascule (M7) : `docs/spec-v3/` remplace `docs/spec/`,
  `ANDROID_PACKAGING.md` est réécrit ou absorbé dans `distribution.md`,
  `DESIGN_SYSTEM.md` est en version Compose, et `CLAUDE.md` est mis à jour
  (§ 3 de ce plan).
- **README** : il n'est réécrit qu'à la bascule. D'ici là, il décrit la
  version publiée (Python), avec une ligne qui annonce Kairos 3 en préversion.
  Conformément à `CLAUDE.md`, le README ne reçoit que les fonctionnalités.

---

## 9. Jalons : une version packagée à chaque étape

Chaque jalon produit :

1. la spec v3 du ou des domaines, rédigée **avant** le code ;
2. le code et ses tests ;
3. la spec mise à jour, bijective ;
4. un tag `v3.0.0-alpha.N` avec APK et installeurs, en **préversion
   GitHub**, que la vérification de mise à jour de la version Python ignore
   déjà (tags suffixés).

**Cohabitation pendant les alphas.** Les APK alpha utilisent
l'`applicationId` `com.skohscripts.kairos.preview`, avec un nom « Kairos
Preview » et une icône marquée. Ils s'installent **à côté** de la version
Python, qui reste l'outil du quotidien. Pour tester sur de vraies données, on
importe une base (§ 5.3) à partir de M5. Seule la `v3.0.0` prend
l'`applicationId` définitif.

Tailles indicatives : S (une session), M (2 à 3 sessions), L (4 sessions ou
plus).

| Jalon | Version | Contenu | Taille | Critère de sortie |
|---|---|---|---|---|
| **M0 — Fondations** | alpha.1 | Squelette Gradle `kmp/` (`core`, `ui`, `androidApp`, `desktopApp`, `webApp`), catalogue de versions, thème miel `material3`, Roboto, générateur d'icônes, logo et icône adaptative, **coquille de navigation** (rail sur le bureau, barre basse sur Android compact), écrans vides, i18n FR/EN en place, CI complète (tests, APK, trois installeurs, **zips portables**, version web, release en préversion), instance unique sur le bureau. Specs : `architecture.md`, `distribution.md` (version initiale), `i18n.md`. | M | Les installeurs et les zips portables s'ouvrent sur Windows, Linux, macOS et Android. La coquille web s'affiche dans Chrome et Edge. Ce jalon lève tôt les risques de packaging, notamment macOS. |
| **M1 — Données et tâches** | alpha.2 | Schéma SQLDelight et migrations, dépôts, exemples au premier lancement, capture, boîte de réception et qualification en un clic, dialogue d'édition (champs essentiels), fait, suppression, **export et import JSON**. Version web : base OPFS, **fichier lié** (Edge, Chrome), publication de la préversion web sous `/preview/`. Portage de `test_tasks_models`, `test_tasks_seed`. | L | On crée, qualifie, édite, supprime et exporte des tâches. L'export réimporté redonne la même base. |
| **M2 — Moteur et vue Jour** | alpha.3 | `core` : WSJF, placement, creux, deep work, épinglage, dépendances, récurrences, jours fériés, snooze, staleness, surcharge. **Tests différentiels** en place. Vue Jour complète : Maintenant, agenda ordonné, sections secondaires, créneaux et deep work, timeline, « Pourquoi à cette place ? », filtres, recherche, backlog, sous-tâches, bloqueurs, récurrence dans l'édition, raccourcis `N` et `/`. | L | Les fixtures différentielles passent à 100 %. Les critères de succès de `ordonnancement.md`, `dependances.md`, `recurrence.md` et `vue-jour-gtd.md` sont vérifiés. |
| **M3 — Temps réel** | alpha.4 | Sessions, chrono vivant, temps du jour par type (celui de la semaine est calculé, affiché avec la vue Semaine en M4), trois alertes, notifications Android (permanente, alarmes) et bureau (plateau), son en option, repli dans la fenêtre. | M | Critères de `temps-reel-chrono.md`. Un chrono survit à la fermeture de l'application et au redémarrage du téléphone. |
| **M4 — Notes, Semaine, Stats** | alpha.5 | Page Notes (avec édition), vue Semaine (avec le temps de la semaine par type, calculé depuis M3), tableau de bord de statistiques, guide des points fondé sur l'historique. | M | Critères de `notes-capture.md` et `statistiques.md`. Fixtures différentielles de `tasks_stats`. |
| **M5 — Réglages, migration, finitions** | alpha.6 | Écran Réglages (sections, validation par champ), écran « À propos et guide », accueil au premier lancement, **migration des bases 2.x** (Android automatique, bureau proposée), vérification de mise à jour sur le bureau, accessibilité (TalkBack, cibles de 48 dp, contrastes), traduction anglaise relue. | M | Une vraie base 2.x migre sans perte (comptes vérifiés). Aucune chaîne française en dur dans le code. |
| **M6 — Prêt à publier** | beta.1 | Fastlane FR/EN et captures, recette F-Droid validée par `fdroid build`, **reproductibilité vérifiée**, dossier de demande IzzyOnDroid, page GitHub Pages, README v3 rédigé (non publié), `CLAUDE.md` v3. | M | `fdroid lint` et `fdroid build` passent en CI. L'APK reconstruit est identique à l'APK publié. La page est déployée en préversion. |
| **M7 — Bascule 3.0.0** | 3.0.0 | Suppression du Python (`app/`, `templates/`, `static/`, `packaging/`, `android/`, `tests/`, `pyproject.toml`, `.gitlab-ci.yml`), `docs/spec-v3` → `docs/spec`, `applicationId` définitif, release, **demande d'inclusion IzzyOnDroid** (Codeberg) et **merge request F-Droid** (`gitlab.com/fdroid/fdroiddata`), faites par toi depuis tes comptes, page publique. | S | Kairos 3 publié. Les utilisateurs de l'APK 2.x migrent par-dessus. |

Ordre justifié :

- M0 lève d'abord les risques de packaging (macOS, jpackage, CI).
- M1 pose l'export tôt : c'est un filet de sécurité pendant les alphas.
- M2 porte le moteur, qui est le cœur de valeur, sous tests différentiels
  pendant que le Python est encore là.
- La migration (M5) vient une fois le modèle stabilisé.

---

## 10. Versionnage

- `versionName` en semver. `versionCode = MAJEUR×10000 + MINEUR×100 +
  CORRECTIF` : même formule qu'aujourd'hui, donc 3.0.0 = 30000, supérieur à
  2.6.0 = 20600, et l'installation par-dessus fonctionne.
- Préversions (`3.0.0-alpha.N`, `3.0.0-beta.N`) : `versionCode = 29000 + N`
  (alpha) et `29500 + N` (beta), sur l'`applicationId` `.preview`, donc sans
  conflit.
- Les tags suffixés restent des **préversions GitHub**. `releases/latest`
  (page web, vérification de mise à jour) ne les voit donc jamais.

---

## 11. Risques et parades

| Risque | Parade |
|---|---|
| Divergence subtile du moteur (arrondis, fuseaux, ordre de tri à égalité) | Tests différentiels (§ 7.2). Les égalités de tri sont documentées et testées. Toutes les dates en heure locale explicite (`TimeZone.currentSystemDefault()` injecté). |
| macOS : pas de Mac pour tester, pas de notarisation | Test de démarrage en CI sur un runner macOS dès M0. Contournement Gatekeeper documenté. Notarisation différée. |
| Compose Desktop moins mûr que Compose Android (texte, clavier, notifications) | Coquille et raccourcis testés dès M0 et M2. Notifications de plateau à défaut de notifications natives riches. |
| F-Droid refuse ou ralentit la revue | Recette validée en CI (`fdroid build`) avant soumission, zéro permission réseau, zéro dépendance non libre. IzzyOnDroid publie de toute façon, souvent en quelques jours. |
| Version web : Compose pour le web encore en Beta, navigateur qui vide ses données | Web ajoutée en cible dès M0 (on voit tôt ce qui casse). Fichier lié et bandeau de risque (§ 5.3). Le zip portable reste la solution de repli sûre sur les postes pro. |
| Taille de l'APK (limite IzzyOnDroid d'environ 30 Mo) | Compose + R8 : de l'ordre de 5 à 10 Mo attendus. Taille surveillée en CI. |
| Chantier long, lassitude, double maintenance | Le Python est **gelé** (correctifs seulement). Chaque jalon livre un produit installable, utilisable en « Preview ». |
| Migration Android impossible en cas de changement de clé | Même clé conservée (secrets existants). Export JSON en recours. |
| Fuite de réglages ou d'API retirés dans le nouveau code | Liste du § 2.2 vérifiée à chaque jalon. Grep de garde en CI (`timetree`, `gitlab`, `pilotage`, `systemd`) sur `kmp/` et `docs/spec-v3/` (`docs/spec/` depuis la bascule). |

---

## 12. Questions tranchées le 2026-09-28

Toutes les propositions ci-dessous ont été **acceptées** par l'utilisateur.

1. **Navigation à cinq destinations** : « Accueil » devient « À propos et
   guide » dans les Réglages (§ 3, point 4). D'accord ?
2. **Mise à jour sur le bureau** : bandeau et lien vers la page de
   téléchargement, sans remplacement automatique (§ 6.2). D'accord ?
3. **`minSdk 26`** (Android 8), au lieu de 24 aujourd'hui : on perd Android
   7.x. D'accord ?
4. **macOS** : `.dmg` non signé pour commencer, notarisation différée. D'accord ?
5. **Jours fériés** : français seulement (comme aujourd'hui), ou aussi
   d'autres pays maintenant que l'app parle anglais ? Proposition : français
   seulement en v3.0, avec des dates personnalisées libres (`extra_holidays`).
6. **Types de tâches par défaut** : traduits selon la langue à la création.
   « Pilotage/dette technique » est retiré. D'accord ?
7. **Nom et identité** : on garde « Kairos », l'`applicationId`
   `com.skohscripts.kairos` et le logo actuel. D'accord ?
