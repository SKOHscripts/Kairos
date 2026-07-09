# Kairos

> **Charte visuelle** : couleurs, typographie, formes et composants sont
> documentés dans [`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md) — toute
> nouvelle page ou tout nouveau composant doit réutiliser ces jetons.

Dashboard personnel de tâches : **« qu'est-ce que je fais maintenant, et dans quel
ordre, sachant qu'une réunion 13h-14h m'empêche de traiter le sujet urgent avant
14h05 ? »**. Outil web local, mono-utilisateur, sans compte ni cloud : une base
SQLite, un navigateur, et c'est tout.

> **Pourquoi « Kairos » ?** En grec, *καιρός* désigne **le moment opportun**, l'instant
> juste où agir (par opposition à *Chronos*, le temps qui défile). C'est exactement le
> métier de l'outil : trouver le bon créneau pour chaque tâche. Nom de code : **14h55**
> — le creux post-déjeuner, l'heure la moins productive de la journée (le fameux
> *post-lunch dip*). L'outil qui vise le bon moment, dont le nom de code est le pire :
> le clin d'œil est assumé.

Application FastAPI autonome, extraite à l'origine de `pilotage-pleiade-gitlab`
(dont l'intégration reste possible, en option — voir plus bas). L'historique de
conception complet, phase par phase, est dans [`SPEC_KAIROS.md`](SPEC_KAIROS.md) —
la phase d'extraction dans ce dépôt dédié y est consignée.

---

## Télécharger l'application (Windows/Linux)

Pas besoin de Python, de venv ni de terminal : les [releases GitHub](../../releases)
proposent un exécutable autonome par OS (`kairos-linux-x86_64`,
`kairos-windows-x86_64.exe`). Télécharge, double-clique (sous Linux : rendre le
fichier exécutable au préalable — `chmod +x kairos-linux-x86_64`), le navigateur
s'ouvre tout seul sur Kairos. Réglages et base de tâches sont stockés dans le
dossier de données standard de ton système d'exploitation, entièrement
éditables depuis la page **Réglages** de l'application — pas de fichier `.env`
à copier ni éditer à la main.

Fermer l'onglet du navigateur n'arrête **pas** le serveur (il continue en
arrière-plan) : utilise le bouton **Quitter** en haut à droite de l'application
pour l'arrêter proprement — sans quoi le prochain lancement choisira un autre
port (8002, 8003…) puisque 8001 restera occupé par l'instance précédente.

Le mode « git clone + venv » ci-dessous reste disponible pour un usage avancé
(développement, service systemd démarré au boot) : il partage exactement le
même mécanisme de configuration que l'exécutable.

---

## Démarrage rapide

Sur un poste Linux, une seule commande installe tout (venv + dépendances) **et**
active le service systemd utilisateur (démarrage automatique, y compris au
prochain boot) :

```bash
git clone <url-de-ce-dépôt> kairos
cd kairos
make service
```

C'est tout : http://127.0.0.1:8001. Aucune configuration requise : la base de
tâches est créée au premier démarrage et migrée automatiquement aux versions
suivantes (aucune donnée n'est jamais perdue). Pour personnaliser : page
**Réglages** de l'application (`/kairos/settings`, chaque réglage y est
expliqué) — s'applique sans redémarrage pour la quasi-totalité des réglages.

Sans service (usage ponctuel, développement, ou plateforme sans systemd) :

```bash
make install   # crée le venv + installe les dépendances
make run       # lancement en mode normal, port 8001
make dev       # lancement en développement (rechargement auto), port 8001
make test      # venv + suite de tests complète
```

---

## Fonctionnalités

### Gestion des tâches
- **Création rapide** en une ligne (titre seul suffit) ; édition complète : titre,
  description, priorité 0-2 (P0 = la plus forte), échéance, date programmée, projet, durée estimée,
  récurrence, type, points de Fibonacci, heure fixe, fiche liée, sous-tâches en lot,
  bloqueurs. Un seul « Enregistrer » applique tout.
- **Sous-tâches** : avancement n/m sur la mère ; seules les **feuilles** sont
  planifiées (une mère à filles ouvertes n'est jamais une unité de travail).
- **Récurrence** : quotidienne, jours ouvrés, hebdomadaire, mensuelle (terminer une
  occurrence crée la suivante) et **calendaire** « le N du mois » (générée par date,
  décalée au jour ouvré précédent si week-end/férié — calendrier français intégré).
- **« Décaler à demain »** (snooze) : atterrit toujours sur un jour ouvré (un
  vendredi → lundi, férié sauté).
- **Suppression** : une tâche native se supprime ; une tâche importée s'**archive**
  (jamais supprimée, l'historique de priorisation est préservé).

### « À traiter » (inbox GTD)
Une tâche sans **priorité ni points de Fibonacci** n'entre dans aucun tri
automatique : elle apparaît dans une section dédiée, non repliée, en tête de page,
tant qu'elle n'est pas qualifiée. Force l'usage des deux axes du score WSJF.
La clarification prime sur tout (une tâche bloquée ou épinglée mais non qualifiée
reste « À traiter »).

### Ordonnancement automatique (WSJF)
- **Score** = `(valeur(priorité) + criticité(échéance)) / effort(points Fibonacci)`
  — le petit et prioritaire passe devant le gros et lointain (règle de
  Smith/Reinertsen). Valeur **exponentielle** par cran de priorité ; criticité en
  rampe à l'approche de l'échéance ; « en retard » reste un **palier dur** qui passe
  toujours devant. Le score est **affiché** sur chaque tâche (transparence), tous
  les poids sont réglables (page **Réglages**).
- **Placement temporel** : les tâches sont posées dans les trous de la journée avec
  leurs **durées réelles**, une **marge** après chaque réunion (13h-14h → 14h05,
  avec note explicative), débordement signalé. **Épinglage** à heure fixe (jamais
  déplacé, conflit signalé). **Date programmée** (`scheduled_date`) distincte de
  l'échéance : une tâche programmée plus tard est masquée (section « Programmées
  plus tard ») sauf si son échéance approche — l'échéance prime toujours.
- **Aide à l'estimation** : barème Fibonacci (1 → 21, taille relative, volume ×
  complexité × incertitude) repliable dans le panneau d'édition.

### Points de Fibonacci

Dans le panneau d'édition, chaque tâche peut recevoir un
nombre de points sur l'échelle `1, 2, 3, 5, 8, 13, 21` — une taille **relative**
(jamais des heures), estimée en quelques secondes par rapport à tes tâches
habituelles : volume × complexité × **incertitude** (« est-ce que je sais comment
faire ? »). Repère indicatif : `1` trivial et expédié (valider une MR triviale),
`2` à `3` petit à modéré sans inconnue (dev bien cadré), `5` conséquent ou avec un peu
d'inconnu, `8` gros ou vraiment incertain, `13`/`21` trop gros pour une seule tâche
→ à découper en sous-tâches. Ces points forment l'**effort**, au **dénominateur** du
score WSJF (`(valeur(priorité) + criticité(échéance)) / effort`) : à priorité et
échéance égales, plus une tâche a de points, plus son score baisse et plus elle
recule dans l'ordre — le petit et prioritaire passe toujours devant le gros et
lointain. Distinct de la **durée estimée (min)**, qui sert uniquement au
*placement* dans l'agenda (combien de temps le créneau occupe) : une tâche peut
être courte mais tordue (peu de minutes, beaucoup de points) ou longue mais
mécanique (l'inverse). Sans points renseignés, l'effort se rabat sur la durée
estimée (≈ 1 point / 30 min, borné 1-21), puis sur `DEFAULT_FIBONACCI_POINTS`
(3 par défaut) si rien n'est saisi du tout — le tri reste donc utilisable sans
estimation, mais s'affine à mesure que le Fibo est renseigné.

#### Creux de l'après-midi (14h55)

Le nom de code de l'outil — **14h55** — est le creux post-déjeuner (*post-lunch dip*),
l'heure la moins propice à la réflexion : un vrai phénomène circadien, le plus marqué
pour les tâches **complexes**, et qui récupère vers 15h-16h. Kairos le **matérialise
dans l'ordonnancement** : pendant une fenêtre creuse configurable (par défaut 13h→16h,
le plus profond à 15h), l'outil **évite d'y poser les tâches trop complexes** (points de
Fibonacci élevés) et y fait **remonter les tâches légères**. Concrètement, à ces heures
l'**effort effectif** d'une tâche est gonflé en proportion de sa complexité — une tâche
de 21 points voit son score de *placement* divisé par deux au tronc, une tâche de 1
point n'est jamais pénalisée — si bien qu'une tâche simple prend le créneau creux et la
complexe se pose juste avant ou après. C'est un **effet gradué, pas un interdit** : une
tâche complexe suffisamment urgente peut encore l'emporter.

Trois garde-fous : les **échéances et le chemin critique priment toujours** (une tâche
en retard ou un bloqueur d'une tâche urgente n'est jamais décalé par le creux) ; le
**score WSJF affiché ne change pas** (c'est un choix de *placement*, pas de valeur — la
transparence est préservée, une note « créneau creux » signale les tâches remontées) ;
et la **matinée reste pilotée par l'urgence pure**. Actif par défaut, entièrement
réglable depuis la page **Réglages** — décale la fenêtre selon ton chronotype
(alouette matinale → creux plus tôt) ou désactive-le entièrement.

### Dépendances entre tâches
« Bloqué par » (menu de sélection multiple) : une tâche dont un bloqueur est encore
à faire sort du planning (section « Bloquées », levée automatique et **transitive**) ;
un bloqueur d'une tâche urgente **remonte** dans l'ordre (chemin critique, urgence
dérivée calculée au rendu, jamais écrite) ; les **cycles** sont détectés et refusés.

### Time blocking & deep work
- **Créneaux occupés** : réunions saisies à la main, et calendrier personnel
  **TimeTree** (optionnel, voir Configuration). Chaque créneau manuel est **éditable**
  (titre, horaires, deep-work, récurrence) et **supprimable** depuis la liste « Créneaux
  du jour ».
- **Blocs deep-work protégés** : une fenêtre réservée à **une seule** tâche (la plus
  urgente), sans fragmentation — les autres la contournent.
- **Blocs récurrents** : quotidien, jours ouvrés ou hebdomadaire (bloc déjeuner tous
  les jours, deep-work chaque mardi matin). Le créneau saisi est le **modèle** ;
  les occurrences sont projetées à la volée, jamais stockées une à une — éditer ou
  supprimer un créneau récurrent agit sur toutes ses occurrences.
- **Timeline verticale** type agenda (1 min = 1 px, rendu serveur sans JavaScript) :
  planifié, occupé, épinglé, deep-work, conflits — et un **rail « réel »** montrant
  les sessions effectivement chronométrées à côté du planifié.

### Suivi du temps réel & alertes
- **Chrono par tâche** (une seule en cours), minuteur vivant, réel vs estimé
  (dépassement signalé), total et ventilation par type du jour et de la semaine.
- **Titre d'onglet vivant** : le compteur reste visible en arrière-plan.
- **Alertes navigateur** (opt-in, bouton « Activer les alertes chrono ») :
  dépassement de l'estimé, chrono oublié, rappel de pause. Nécessite un contexte
  sécurisé (`127.0.0.1`/`localhost` ou HTTPS) ; sinon repli automatique sur le titre
  d'onglet + bandeau dans la page.

### Vues & garde-fous
- **Vue jour** (agenda détaillé + « À faire maintenant ») et **vue semaine**
  (7 jours, tâches par échéance, créneaux, synthèse du temps réel par type).
- Badge **« traîne depuis N j »** (échéance dépassée de longue date, ou tâche sans
  date jamais retouchée) ; bandeau de **surcharge de priorité** (trop de tâches à
  priorité maximale = signal dilué) ; bordure colorée par urgence ; badge « chemin
  critique ».

### Dashboard de statistiques (`/kairos/stats`)
Indicateurs **constructifs**, en lecture seule : débit hebdomadaire (tâches + points
terminés = vélocité), **calibration de l'estimation** (temps réel médian par palier
de Fibonacci + biais estimé vs réel), répartition du temps réel par type + focus
(fragmentation), flux/backlog (WIP, âge médian, retards), complétude des
métadonnées. Honnêteté statistique : effectif `n` affiché, faible échantillon marqué
« peu fiable ».

---

## Configuration (page Réglages, tout est optionnel)

Tous les réglages se modifient depuis la page **Réglages** (`/kairos/settings`)
de l'application, chacun accompagné de son explication — plus de fichier
`.env` à copier ni éditer à la main. La quasi-totalité s'applique
immédiatement, sans redémarrage (seul le chemin de la base de tâches demande un
redémarrage ; la page l'indique sur place). Résumé des réglages disponibles :

| Section | Réglages | Défaut |
|---|---|---|
| Base de données | Chemin de la base de tâches | dossier de données de l'OS |
| Import GitLab assigné | Nom d'utilisateur assigné + (base de pilotage **ou** URL/jeton/projets/cache) | désactivé |
| TimeTree | E-mail, mot de passe, code du calendrier, cache | désactivé |
| Ordonnancement | Durée par défaut, marge après réunion, journée de travail | 30 min, 5 min, 9h-18h |
| WSJF | Base de valeur, horizon/poids d'urgence, points par défaut | 4.0, 14 j, 8, 3 |
| Creux après-midi | Activé, fenêtre 13h-15h-16h, force de la pénalité | activé, 1.0 |
| Garde-fous | Seuils « en retard »/« sans date », surcharge P0 | 7 j, 14 j, 5 |
| Statistiques | Fenêtre des indicateurs récents | 8 semaines |
| Alertes chrono | Chrono oublié, rappel pomodoro | 180 min, 50 min |
| Jours fériés | Calendrier français, dates supplémentaires | FR activé |
| Réseau | Proxy HTTP/HTTPS sortant, domaines exclus | aucun |

Identifiants sensibles (jeton GitLab, mot de passe TimeTree) : stockés dans le
trousseau système (Windows Credential Manager, GNOME Keyring/SecretService,
Keychain macOS) quand disponible, sinon repli automatique — sans erreur —
vers le fichier de réglages local ; jamais réaffichés en clair dans le formulaire.

**Mise à niveau depuis une ancienne installation `.env`** : au premier
démarrage après mise à jour, un `.env` existant est importé automatiquement,
une seule fois, dans le nouveau système de réglages (la page Réglages affiche
la date de cette migration). Le fichier `.env` n'est jamais supprimé
automatiquement ; tu peux le retirer une fois la migration confirmée.

### Calendrier TimeTree (optionnel)
Utilise l'API **non-officielle** du paquet `timetree-exporter` (reverse-
engineerée : peut casser sans préavis ; les échecs sont toujours dégradés en
bandeau, jamais en erreur). Les créneaux importés bloquent la planification ;
les événements « journée entière » ou « sur une période » (plusieurs jours) ne
sont que des **indications** (puces datées), jamais des obstacles. Cache local
anti rate-limiting. Réseau d'entreprise avec proxy sortant : réglable
directement dans la section « Réseau » de la page Réglages.

### Import des issues GitLab assignées (optionnel, lecture seule)
Deux façons **mutuellement exclusives** d'obtenir tes issues GitLab ouvertes comme
tâches (nom d'utilisateur assigné commun aux deux) — sans aucune des deux,
la fonctionnalité disparaît proprement de l'interface (cas normal, aucune erreur) :

1. **Via l'outil de pilotage MSI**, si tu l'utilises aussi sur ce poste (dépôt
   séparé) : renseigne le chemin absolu de sa base `pilotage.db`. Relit le
   cache entretenu par son onglet « Pilotage GitLab » — **aucun appel
   réseau**, aucune configuration GitLab à dupliquer ici. Donne accès en plus
   à la **liaison manuelle « Fiche liée »** vers une fiche de dette technique
   (badge cliquable, lecture seule — aucune écriture vers Redmine/GitLab,
   jamais). C'est la seule des deux voies qui l'active.
2. **Import direct** (cas normal d'un collègue sans pilotage) : renseigne
   l'URL de l'instance GitLab, un jeton personnel (scope `read_api` suffit) et
   le(s) projet(s) (un ou plusieurs, séparés par des virgules). Appel en
   lecture seule à l'API REST GitLab, mis en cache (même patron anti
   rate-limiting que TimeTree) ; un échec (réseau, jeton invalide) se dégrade
   en bandeau, jamais en erreur — les tâches déjà importées restent
   affichées. Le jeton est optionnel : laissé vide, il est résolu depuis les
   moyens d'authentification déjà configurés pour `git` sur ce poste —
   `git credential fill` (trousseau GNOME/libsecret, Keychain macOS, Windows
   Credential Manager, ou tout autre `credential.helper` déjà en place), puis
   `~/.netrc` en repli — pour éviter de dupliquer un jeton en clair (voir
   `app/git_credentials.py`). Résolution mise en cache pour la durée du
   processus : redémarre l'application après une rotation de jeton.

Si la base de pilotage est renseignée, elle **prime toujours** sur l'import
direct (zéro appel réseau). Dans les deux cas : issue fermée/réassignée → tâche
archivée ; ta priorité et ton temps passé ne sont jamais écrasés.

Sans ce réglage (cas normal d'un collègue), ces deux fonctionnalités disparaissent
proprement de l'interface. « Kairos » n'écrit **jamais** dans la base pilotage.

---

## Service systemd (démarrage automatique)

`make service` (§ Démarrage rapide) fait tout : venv, dépendances, unité
systemd utilisateur activée. Équivalent à la main, si tu préfères ne pas passer
par `make` :

```bash
mkdir -p ~/.config/systemd/user
sed "s#__PROJECT_DIR__#$(pwd)#g" deploy/kairos.service \
  > ~/.config/systemd/user/kairos.service
systemctl --user daemon-reload && systemctl --user enable --now kairos.service
loginctl enable-linger "$USER"   # optionnel : démarre au boot sans session ouverte
```

Le service écoute sur le **port 8001** (si tu fais aussi tourner l'outil de
pilotage sur le même poste, il occupe le port 8000 : les deux coexistent).
Exploitation : `systemctl --user status kairos`, `journalctl --user -u kairos -f`,
`systemctl --user restart kairos` après un `git pull` (la plupart des réglages
s'appliquent sans redémarrage depuis la page Réglages — voir Configuration).
Désinstallation : `make service-uninstall`.

---

## Développement

```bash
source .venv/bin/activate
pytest                       # aucun accès réseau réel
uvicorn app.main:app --reload --port 8001
```

### Empaquetage (exécutables Windows/Linux)
`make build-exe` construit l'exécutable de bureau pour l'OS courant via
PyInstaller (voir [`packaging/README.md`](packaging/README.md) pour le détail
et les points d'attention). Les exécutables Windows **et** Linux publiés en
release GitHub sont construits automatiquement par
[`.github/workflows/release.yml`](.github/workflows/release.yml) au push d'un
tag `vX.Y.Z` (PyInstaller ne fait pas de cross-compile : la CI build chaque OS
sur un runner de cet OS).

### Architecture (`app/`)
| Module | Rôle |
|---|---|
| `main.py` | Application FastAPI : routes, rendu, formulaires |
| `config.py` | Modèle des réglages (pydantic `BaseModel`) |
| `settings_store.py` | Persistance des réglages (JSON, dossier de données de l'OS) + migration `.env` unique |
| `secret_store.py` | Jeton GitLab / mot de passe TimeTree : trousseau système, repli fichier local |
| `settings_sections.py` | Regroupement des réglages pour l'affichage de la page Réglages |
| `launcher.py` | Point d'entrée de l'exécutable de bureau (choix de port, ouverture du navigateur) |
| `tasks_models.py` | Modèles SQLAlchemy : `Task`, `TimeBlock`, `TaskDependency`, `WorkSession`, `TaskSyncMeta` |
| `tasks_db.py` | Engine/sessions + **migrations légères** automatiques (ADD COLUMN, correctifs de données) |
| `tasks_scheduling.py` | Cœur **pur** : score WSJF, buckets, placement, timeline, gate « À traiter » |
| `tasks_dependencies.py` | Moteur pur : blocage transitif, cycles (Kahn), urgence dérivée |
| `tasks_recurrence.py` | Récurrence des tâches (à la complétion + calendaire) et des blocs (projection) |
| `tasks_time.py` | Agrégats purs du temps réel (sessions, totaux, ventilation) |
| `tasks_staleness.py` | Détection pure des tâches qui traînent |
| `tasks_stats.py` | Agrégats purs du dashboard de statistiques |
| `tasks_gitlab_sync.py` | Upsert **pur** des issues assignées → tâches (source indifférente : cache pilotage ou import direct) |
| `pilotage_link.py` | Seul point de contact (optionnel, lecture seule) avec `pilotage.db` — cache GitLab + « Fiche liée » |
| `gitlab_direct.py` | Seam GitLab direct (sans pilotage) : client REST minimal, cache, dégradation propre |
| `calendar/timetree_source.py` | Seam TimeTree : appel Python natif de `timetree-exporter`, cache, dégradation propre |
| `workdays.py` | Jours ouvrés + jours fériés français |

Principes tenus depuis la première phase : **logique métier en fonctions pures**
(testées en isolation, sans I/O), routes minces, **jamais de perte de données**
(migrations additives, archivage plutôt que suppression, invariant de non-perte
vérifié par test de propriété), dégradation propre de toute source externe (jamais
de page en erreur à cause de TimeTree, de la base pilotage ou de l'API GitLab),
rendu serveur sans framework JavaScript.

### Interface
La charte graphique (« sobre & professionnel » : IBM Plex, palette slate, densité
compacte) suit celle du Tableau de bord MSI de `pilotage-pleiade-gitlab` — pour
toute évolution notable d'interface, garder cette cohérence visuelle (voir ce
dépôt pour la charte détaillée si tu y as accès).
