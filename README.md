# Kairos

Dashboard personnel de tâches. Il répond à une question concrète : « qu'est-ce que je fais
maintenant, et dans quel ordre, sachant qu'une réunion 13h-14h m'empêche de traiter le
sujet urgent avant 14h05 ? » Outil web local, mono-utilisateur, sans compte ni cloud. Une
base SQLite, un navigateur, et c'est tout.

## En bref

Ce que fait l'outil, au quotidien :

- Il classe les tâches du jour par un score de priorité, affiché sur chacune.
- Il pose chaque tâche dans les trous de l'agenda, autour des réunions, avec une marge
  après chaque réunion.
- Il protège des blocs de deep-work et allège le creux de l'après-midi (les tâches
  complexes évitent les heures les moins propices).
- Il suit le temps réel passé et le compare à l'estimé.
- Il importe, en option, tes issues GitLab assignées et ton agenda TimeTree.

Le score de priorité vient de la méthode WSJF (« Weighted Shortest Job First ») :

```
              valeur(priorité) + criticité(échéance)
  score  =  ──────────────────────────────────────────
                   effort (points de Fibonacci)
```

- `valeur(priorité)` est exponentielle : `4^(2 − p)`, donc P0 = 16, P1 = 4, P2 = 1.
- `criticité(échéance)` monte en rampe à l'approche de l'échéance, ou de la date
  programmée si elle est plus proche. Une tâche en retard reste un palier à part : elle
  passe toujours devant, hors score.
- `effort` est la taille en points de Fibonacci (1 à 21). Sans points, l'outil retombe
  sur la durée estimée pour le score affiché.

Le petit et prioritaire passe donc devant le gros et lointain (règle de Smith/Reinertsen).
Tous les poids se règlent depuis la page **Réglages**.

> **Pourquoi « Kairos » ?** En grec, *καιρός* désigne le moment opportun, l'instant juste
> où agir, par opposition à *Chronos*, le temps qui défile. C'est le métier de l'outil :
> trouver le bon créneau pour chaque tâche. Nom de code : **14h55**, le creux
> post-déjeuner, l'heure la moins productive de la journée. L'outil vise le bon moment et
> porte le nom du pire : le clin d'œil est assumé.

Application FastAPI autonome, extraite à l'origine de `pilotage-pleiade-gitlab`
(l'intégration reste possible, en option, voir plus bas). L'historique de conception
complet est dans [`SPEC_KAIROS.md`](SPEC_KAIROS.md).

---

## Télécharger l'application (Windows/Linux)

Pas besoin de Python, de venv ni de terminal. Les
[releases GitHub](https://github.com/SKOHscripts/Kairos/releases) proposent un exécutable
autonome par OS (`kairos-linux-x86_64`, `kairos-windows-x86_64.exe`). Télécharge,
double-clique (sous Linux, rends d'abord le fichier exécutable avec
`chmod +x kairos-linux-x86_64`), et le navigateur s'ouvre tout seul sur Kairos. Les
réglages et la base de tâches vivent dans le dossier de données standard de ton système,
entièrement éditables depuis la page **Réglages**. Aucun fichier `.env` à copier ou à
éditer à la main.

Fermer l'onglet du navigateur n'arrête pas le serveur : il continue en arrière-plan.
Utilise le bouton **Quitter** en haut à droite pour l'arrêter proprement. Sans quoi le
prochain lancement choisira un autre port (8002, 8003…) puisque 8001 restera occupé par
l'instance précédente.

Le mode « git clone + venv » ci-dessous reste disponible pour un usage avancé
(développement, service systemd démarré au boot). Il partage le même mécanisme de
configuration que l'exécutable.

---

## Démarrage rapide

Sur un poste Linux, une seule commande installe tout (venv et dépendances) et active le
service systemd utilisateur, démarrage automatique compris au prochain boot :

```bash
git clone <url-de-ce-dépôt> kairos
cd kairos
make service
```

C'est tout : http://127.0.0.1:8001. Aucune configuration requise. La base de tâches est
créée au premier démarrage, avec quelques tâches et créneaux d'exemple pour découvrir les
fonctionnalités, puis migrée automatiquement aux versions suivantes sans jamais perdre de
données. Pour personnaliser, va sur la page **Réglages** (`/kairos/settings`) : chaque
réglage y est expliqué et s'applique sans redémarrage pour la quasi-totalité d'entre eux.

Sans service (usage ponctuel, développement, plateforme sans systemd) :

```bash
make install   # crée le venv + installe les dépendances
make run       # lancement en mode normal, port 8001
make dev       # lancement en développement (rechargement auto), port 8001
make test      # venv + suite de tests complète
```

---

## Fonctionnalités

### Gestion des tâches
- **Création rapide** en une ligne (le titre seul suffit). Édition complète ensuite :
  titre, description, priorité 0-2 (P0 = la plus forte), échéance, date programmée,
  projet, durée estimée, récurrence, type, points de Fibonacci, heure fixe, fiche liée,
  sous-tâches en lot, bloqueurs. Un seul « Enregistrer » applique tout.
- **Sous-tâches** : avancement n/m sur la mère. Seules les feuilles sont planifiées (une
  mère à filles ouvertes n'est jamais une unité de travail).
- **Récurrence** : quotidienne, jours ouvrés, hebdomadaire, mensuelle (terminer une
  occurrence crée la suivante), et calendaire « le N du mois » (générée par date, décalée
  au jour ouvré précédent si week-end ou férié, calendrier français intégré).
- **« Décaler à demain »** (snooze) : atterrit toujours sur un jour ouvré (un vendredi
  passe à lundi, férié sauté).
- **Suppression** : une tâche native se supprime. Une tâche importée s'archive, jamais
  supprimée, pour préserver l'historique de priorisation.

### « À traiter » (inbox GTD)
Une tâche entre dans le tri automatique seulement quand sa **priorité** et ses **points de
Fibonacci** sont renseignés tous les deux. Tant que l'un des deux manque, elle reste « À
traiter » dans une section dédiée, non repliée, en tête de page. La clarification prime sur
tout : une tâche bloquée ou épinglée mais non qualifiée reste « À traiter ».

### Ordonnancement automatique (WSJF)
- **Score** = `(valeur(priorité) + criticité(échéance)) / effort(points Fibonacci)`, la
  formule détaillée en tête de ce README. La valeur croît de façon exponentielle par cran
  de priorité. La criticité monte en rampe à l'approche de l'échéance. « En retard » reste
  un palier dur qui passe toujours devant. Le score est affiché sur chaque tâche
  (transparence), et tous les poids sont réglables.
- **Programmer une tâche « pour aujourd'hui »** la fait passer au même palier prioritaire
  qu'une échéance dépassée. C'est voulu : une tâche que tu comptes traiter aujourd'hui
  remonte devant le reste. Si l'effet te surprend, décale sa date programmée à plus tard.
- **Placement temporel** : les tâches sont posées dans les trous de la journée avec leurs
  durées réelles, une marge après chaque réunion (13h-14h donne 14h05, avec une note
  explicative), débordement signalé. L'**épinglage** à heure fixe n'est jamais déplacé, un
  conflit est signalé. La **date programmée** (`scheduled_date`) est distincte de
  l'échéance : une tâche programmée plus tard est masquée (section « Programmées plus
  tard ») sauf si son échéance approche, car l'échéance prime toujours.
- **Aide à l'estimation** : barème Fibonacci (1 à 21, taille relative, volume ×
  complexité × incertitude), repliable dans le panneau d'édition.

#### Points de Fibonacci

Dans le panneau d'édition, chaque tâche peut recevoir un nombre de points sur l'échelle
`1, 2, 3, 5, 8, 13, 21`. C'est une taille **relative** (jamais des heures), estimée en
quelques secondes par rapport à tes tâches habituelles : volume × complexité ×
incertitude (« est-ce que je sais comment faire ? »). Repère indicatif : `1` trivial et
expédié (valider une MR triviale), `2` à `3` petit à modéré sans inconnue (dev bien
cadré), `5` conséquent ou avec un peu d'inconnu, `8` gros ou vraiment incertain, `13` et
`21` trop gros pour une seule tâche, donc à découper en sous-tâches.

Ces points forment l'**effort**, au dénominateur du score. À priorité et échéance égales,
plus une tâche a de points, plus son score baisse et plus elle recule dans l'ordre : le
petit et prioritaire passe toujours devant le gros et lointain. L'effort est distinct de
la **durée estimée (min)**, qui sert uniquement au *placement* dans l'agenda (combien de
temps le créneau occupe). Une tâche peut être courte mais tordue (peu de minutes, beaucoup
de points) ou longue mais mécanique (l'inverse).

Sans points renseignés, le **score affiché** se rabat sur la durée estimée (≈ 1 point /
30 min, borné 1-21), puis sur `DEFAULT_FIBONACCI_POINTS` (3 par défaut). Ce repli concerne
le score montré à titre indicatif : pour entrer dans le tri du jour, une tâche a de toute
façon besoin de ses points (voir « À traiter » ci-dessus).

#### Creux de l'après-midi (14h55)

Le nom de code de l'outil, **14h55**, est le creux post-déjeuner (*post-lunch dip*),
l'heure la moins propice à la réflexion. C'est un vrai phénomène circadien, le plus marqué
pour les tâches complexes, qui récupère vers 15h-16h. Kairos le matérialise dans
l'ordonnancement : pendant une fenêtre creuse configurable (par défaut 13h→16h, le plus
profond à 15h), l'outil évite d'y poser les tâches trop complexes (points de Fibonacci
élevés) et y fait remonter les tâches légères. Concrètement, à ces heures l'effort effectif
d'une tâche est gonflé en proportion de sa complexité. Une tâche de 21 points voit son
score de *placement* divisé par deux au tronc, une tâche de 1 point n'est jamais pénalisée.
Une tâche simple prend donc le créneau creux, la complexe se pose juste avant ou après.
C'est un effet gradué, pas un interdit : une tâche complexe suffisamment urgente peut
encore l'emporter.

Trois garde-fous. Les échéances et le chemin critique priment toujours (une tâche en
retard ou un bloqueur d'une tâche urgente n'est jamais décalé par le creux). Le score
affiché ne change pas, car c'est un choix de *placement*, pas de valeur ; une note
« créneau creux » signale les tâches remontées. Et la matinée reste pilotée par l'urgence
pure. Actif par défaut, réglable depuis la page **Réglages** : décale la fenêtre selon ton
chronotype (alouette matinale, creux plus tôt) ou désactive-le.

### Dépendances entre tâches
« Bloqué par » (menu de sélection multiple) : une tâche dont un bloqueur est encore à faire
sort du planning (section « Bloquées », levée automatique et transitive). Un bloqueur d'une
tâche urgente remonte dans l'ordre (chemin critique, urgence dérivée calculée au rendu,
jamais écrite). Les cycles sont détectés et refusés.

### Time blocking & deep work
- **Créneaux occupés** : réunions saisies à la main, et calendrier personnel **TimeTree**
  (optionnel, voir Configuration). Chaque créneau manuel est éditable (titre, horaires,
  deep-work, récurrence) et supprimable depuis la liste « Créneaux du jour ».
  L'intégration des agendas Google est à l'étude.
- **Blocs deep-work protégés** : une fenêtre réservée à une seule tâche (la plus urgente),
  sans fragmentation. Les autres la contournent.
- **Blocs récurrents** : quotidien, jours ouvrés ou hebdomadaire (bloc déjeuner tous les
  jours, deep-work chaque mardi matin). Le créneau saisi est le modèle ; les occurrences
  sont projetées à la volée, jamais stockées une à une. Éditer ou supprimer un créneau
  récurrent agit sur toutes ses occurrences.
- **Timeline verticale** type agenda (1 min = 1 px, rendu serveur sans JavaScript) :
  planifié, occupé, épinglé, deep-work, conflits, et un rail « réel » montrant les sessions
  effectivement chronométrées à côté du planifié.

### Suivi du temps réel & alertes
- **Chrono par tâche** (une seule en cours), minuteur vivant, réel contre estimé
  (dépassement signalé), total et ventilation par type du jour et de la semaine.
- **Titre d'onglet vivant** : le compteur reste visible en arrière-plan.
- **Alertes navigateur** (opt-in, bouton « Activer les alertes chrono ») : dépassement de
  l'estimé, chrono oublié, rappel de pause. Nécessite un contexte sécurisé
  (`127.0.0.1`/`localhost` ou HTTPS). Sinon, repli automatique sur le titre d'onglet et un
  bandeau dans la page.

### Vues & garde-fous
- **Vue jour** (agenda détaillé et « À faire maintenant ») et **vue semaine** (7 jours,
  tâches par échéance, créneaux, synthèse du temps réel par type).
- Badge **« traîne depuis N j »** (échéance dépassée de longue date, ou tâche sans date
  jamais retouchée), bandeau de **surcharge de priorité** (trop de tâches à priorité
  maximale, signal dilué), bordure colorée par urgence, badge « chemin critique ».

### Dashboard de statistiques (`/kairos/stats`)
Indicateurs constructifs, en lecture seule : débit hebdomadaire (tâches et points terminés
= vélocité), **calibration de l'estimation** (temps réel médian par palier de Fibonacci et
biais estimé contre réel), répartition du temps réel par type et focus (fragmentation),
flux et backlog (WIP, âge médian, retards), complétude des métadonnées. Honnêteté
statistique : l'effectif `n` est affiché, un faible échantillon est marqué « peu fiable ».

---

## Configuration (page Réglages, tout est optionnel)

Tous les réglages se modifient depuis la page **Réglages** (`/kairos/settings`), chacun
accompagné de son explication. Plus de fichier `.env` à copier ou éditer à la main. La
quasi-totalité s'applique immédiatement, sans redémarrage (seul le chemin de la base de
tâches en demande un, la page l'indique). Résumé des réglages disponibles :

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

Identifiants sensibles (jeton GitLab, mot de passe TimeTree) : stockés dans le trousseau
système (Windows Credential Manager, GNOME Keyring/SecretService, Keychain macOS) quand il
est disponible, sinon repli automatique et sans erreur vers le fichier de réglages local.
Jamais réaffichés en clair dans le formulaire.

**Mise à niveau depuis une ancienne installation `.env`** : au premier démarrage après
mise à jour, un `.env` existant est importé automatiquement, une seule fois, dans le
nouveau système de réglages (la page Réglages affiche la date de cette migration). Le
fichier `.env` n'est jamais supprimé automatiquement ; tu peux le retirer une fois la
migration confirmée.

### Calendrier TimeTree (optionnel)
Utilise l'API non officielle du paquet `timetree-exporter` (reverse-engineerée : elle peut
casser sans préavis, et les échecs sont toujours dégradés en bandeau, jamais en erreur).
Les créneaux importés bloquent la planification. Les événements « journée entière » ou « sur
une période » (plusieurs jours) ne sont que des indications (puces datées), jamais des
obstacles. Cache local anti rate-limiting. Pour un réseau d'entreprise avec proxy sortant,
règle-le dans la section « Réseau » de la page Réglages.

### Import des issues GitLab assignées (optionnel, lecture seule)
Deux façons **mutuellement exclusives** d'obtenir tes issues GitLab ouvertes comme tâches
(le nom d'utilisateur assigné est commun aux deux). Sans aucune des deux, la fonctionnalité
disparaît proprement de l'interface (cas normal, aucune erreur) :

1. **Via l'outil de pilotage MSI**, si tu l'utilises aussi sur ce poste (dépôt séparé) :
   renseigne le chemin absolu de sa base `pilotage.db`. Kairos relit le cache entretenu par
   son onglet « Pilotage GitLab », sans aucun appel réseau ni configuration GitLab à
   dupliquer ici. Cette voie donne en plus accès à la liaison manuelle « Fiche liée » vers
   une fiche de dette technique (badge cliquable, lecture seule, aucune écriture vers
   Redmine ou GitLab). C'est la seule des deux voies qui l'active.
2. **Import direct** (cas normal d'un collègue sans pilotage) : renseigne l'URL de
   l'instance GitLab, un jeton personnel (le scope `read_api` suffit) et le ou les projets
   (séparés par des virgules). Appel en lecture seule à l'API REST GitLab, mis en cache
   (même patron anti rate-limiting que TimeTree). Un échec (réseau, jeton invalide) se
   dégrade en bandeau, jamais en erreur, et les tâches déjà importées restent affichées. Le
   jeton est optionnel : laissé vide, il est résolu depuis les moyens d'authentification
   déjà configurés pour `git` sur ce poste, via `git credential fill` (trousseau
   GNOME/libsecret, Keychain macOS, Windows Credential Manager, ou tout autre
   `credential.helper` en place), puis `~/.netrc` en repli. Cela évite de dupliquer un
   jeton en clair (voir `app/git_credentials.py`). La résolution est mise en cache pour la
   durée du processus : redémarre l'application après une rotation de jeton.

Si la base de pilotage est renseignée, elle prime sur l'import direct (zéro appel réseau).
Dans les deux cas : une issue fermée ou réassignée archive la tâche ; ta priorité et ton
temps passé ne sont jamais écrasés. Sans ce réglage, ces deux fonctionnalités disparaissent
proprement de l'interface. Kairos n'écrit **jamais** dans la base pilotage.

---

## Service systemd (démarrage automatique)

`make service` (§ Démarrage rapide) fait tout : venv, dépendances, unité systemd
utilisateur activée. L'équivalent à la main, si tu préfères ne pas passer par `make` :

```bash
mkdir -p ~/.config/systemd/user
sed "s#__PROJECT_DIR__#$(pwd)#g" deploy/kairos.service \
  > ~/.config/systemd/user/kairos.service
systemctl --user daemon-reload && systemctl --user enable --now kairos.service
loginctl enable-linger "$USER"   # optionnel : démarre au boot sans session ouverte
```

Le service écoute sur le **port 8001** (si tu fais aussi tourner l'outil de pilotage sur le
même poste, il occupe le port 8000, les deux coexistent). Exploitation :
`systemctl --user status kairos`, `journalctl --user -u kairos -f`,
`systemctl --user restart kairos` après un `git pull` (la plupart des réglages s'appliquent
sans redémarrage depuis la page Réglages). Désinstallation : `make service-uninstall`.

---

## Développement

```bash
source .venv/bin/activate
pytest                       # aucun accès réseau réel
uvicorn app.main:app --reload --port 8001
```

### Empaquetage (exécutables Windows/Linux)
`make build-exe` construit l'exécutable de bureau pour l'OS courant via PyInstaller (voir
[`packaging/README.md`](packaging/README.md) pour le détail et les points d'attention). Les
exécutables Windows et Linux publiés en release GitHub sont construits automatiquement par
[`.github/workflows/release.yml`](.github/workflows/release.yml) au push d'un tag `vX.Y.Z`
(PyInstaller ne fait pas de cross-compile : la CI build chaque OS sur un runner de cet OS).

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
| `tasks_db.py` | Engine/sessions + migrations légères + pose des données d'exemple sur base vierge |
| `tasks_seed.py` | Données d'exemple de la première utilisation (tâches et créneaux natifs) |
| `tasks_scheduling.py` | Cœur pur : score WSJF, buckets, placement, timeline, gate « À traiter » |
| `tasks_dependencies.py` | Moteur pur : blocage transitif, cycles (Kahn), urgence dérivée |
| `tasks_recurrence.py` | Récurrence des tâches (à la complétion + calendaire) et des blocs (projection) |
| `tasks_time.py` | Agrégats purs du temps réel (sessions, totaux, ventilation) |
| `tasks_staleness.py` | Détection pure des tâches qui traînent |
| `tasks_stats.py` | Agrégats purs du dashboard de statistiques |
| `tasks_gitlab_sync.py` | Upsert pur des issues assignées → tâches (source indifférente : cache pilotage ou import direct) |
| `pilotage_link.py` | Seul point de contact (optionnel, lecture seule) avec `pilotage.db` : cache GitLab + « Fiche liée » |
| `gitlab_direct.py` | Seam GitLab direct (sans pilotage) : client REST minimal, cache, dégradation propre |
| `calendar/timetree_source.py` | Seam TimeTree : appel Python natif de `timetree-exporter`, cache, dégradation propre |
| `workdays.py` | Jours ouvrés + jours fériés français |

Principes tenus depuis la première phase : logique métier en fonctions pures (testées en
isolation, sans I/O), routes minces, jamais de perte de données (migrations additives,
archivage plutôt que suppression, invariant de non-perte vérifié par test de propriété),
dégradation propre de toute source externe (jamais de page en erreur à cause de TimeTree,
de la base pilotage ou de l'API GitLab), rendu serveur sans framework JavaScript.

### Interface
La charte graphique (« sobre et professionnel » : IBM Plex, palette slate, densité
compacte) suit celle du Tableau de bord MSI de `pilotage-pleiade-gitlab`. Pour toute
évolution notable d'interface, garde cette cohérence visuelle (voir ce dépôt pour la charte
détaillée si tu y as accès).

> **Charte visuelle** : couleurs, typographie, formes et composants sont documentés dans
> [`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md). Toute nouvelle page ou tout nouveau
> composant doit réutiliser ces jetons.
