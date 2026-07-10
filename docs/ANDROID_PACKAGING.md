# Empaquetage Android — note d'exploration (document vivant)

Ce document consigne l'exploration en cours d'un empaquetage de Kairos pour
Android, sur le même principe que les exécutables Windows/Linux (voir
`packaging/README.md`) : une distribution autonome, sans que l'utilisateur
n'ait à installer Python ou à cloner le dépôt. **Rien n'est décidé de façon
définitive** — ce fichier sert à ne pas perdre le fil entre deux sessions
d'itération, pas à figer un plan.

## Décisions déjà prises

- **Distribution** : APK à télécharger (sideload / F-Droid), pas de passage
  par le Play Store dans un premier temps.
- **UI** : on part sur une **WebView** embarquant le serveur local (même
  principe que le launcher de bureau : CPython + uvicorn tournent dans
  l'appli, servent sur `127.0.0.1`, une WebView Android affiche l'interface au
  lieu du navigateur système). Un ressenti plus natif (widgets Android,
  notifications système, etc.) reste un objectif à terme, pas un prérequis du
  premier jet.
- **Dépendance native (`pydantic-core`)** : à trancher **après vérification**
  empirique — voir ci-dessous. Pas de décision Path A / Path B tant que le
  terrain n'a pas été testé concrètement.

## Le point de blocage identifié : `pydantic-core`

Inventaire des `.so` du venv du projet : le seul module compilé natif
(Rust) qui bloque un portage Android direct est **`pydantic_core`**
(FastAPI dépend de Pydantic v2, qui délègue toute sa validation à ce module
compilé). Tout le reste de la stack (FastAPI, Starlette, uvicorn côté pur
Python, Jinja2, SQLAlchemy en mode fichier, platformdirs, keyring) est du
Python pur ou n'a pas d'extension native bloquante connue.

Conséquence directe : **on ne peut pas « juste retirer pydantic »** en gardant
FastAPI, parce que FastAPI dépend structurellement de Pydantic (routes,
`BaseModel`, validation des requêtes) — ce n'est pas une dépendance optionnelle
qu'on peut découpler à la marge.

### État des lieux du portage Android pour ce genre de stack

- **PEP 738** (Python 3.13) : support Android officiel dans CPython lui-même
  (toolchain de build reconnue). Bonne nouvelle de fond, mais ne résout pas à
  lui seul la compilation des extensions natives tierces.
- **`cibuildwheel` 3.1** : a ajouté la capacité de construire des wheels
  Android en général — utile si on doit un jour recompiler `pydantic-core`
  soi-même pour Android.
- **Chaquopy** et **BeeWare/Briefcase** : les deux frameworks Python-sur-Android
  les plus utilisés aujourd'hui ont chacun des difficultés actuelles avec les
  extensions Rust/maturin comme `pydantic-core` :
  - BeeWare est en train de déprécier `mobile-forge` au profit de
    `cibuildwheel` justement pour mieux gérer ce genre de cas, mais le support
    Rust/maturin pour Android est un **item de roadmap Q4 2025** chez BeeWare,
    donc pas encore mature au moment de cette note.
  - Chaquopy a ses propres contraintes de compilation croisée pour les
    extensions natives, pas de wheel `pydantic-core` officiellement maintenue
    pour Android à ce jour.
- **Wheels tierces non officielles** : il existe un dépôt communautaire
  (`Eutalix/android-pydantic-core`) qui fournit des wheels précompilées, mais
  **seulement pour Termux** (environnement Linux userland sur Android, pas le
  bac à sable applicatif standard type Chaquopy/BeeWare) — pas directement
  réutilisable tel quel dans une APK classique.

### Les deux chemins candidats

**Path A — garder FastAPI + Pydantic, maintenir une wheel Android custom**
- Compiler soi-même `pydantic-core` pour Android (via `cibuildwheel` 3.1+ ou
  maturin cross-compilation) et la maintenir dans le temps (mises à jour de
  Pydantic, de la NDK Android, etc.).
- Avantage : zéro changement dans `app/` — tout le code métier existant
  (routes, modèles) reste identique à la version desktop.
- Risque : dette de maintenance récurrente sur une dépendance qu'on ne
  contrôle pas (il faut re-builder la wheel à chaque montée de version de
  Pydantic ou de la toolchain Android), et un chemin qui n'est éprouvé par
  personne d'autre à notre connaissance à ce stade — beaucoup d'inconnues
  tant qu'on n'a pas testé.

**Path B — migrer vers Starlette pur + dataclasses, abandonner Pydantic**
- Starlette est la base pure-Python sur laquelle FastAPI est construit ; en
  l'utilisant directement (routes, requêtes, réponses) et en remplaçant la
  validation Pydantic par des `dataclasses` + validation faite main, on
  élimine complètement la dépendance native bloquante.
- Avantage : plus aucune extension compilée dans le chemin critique →
  portabilité Android (et plus généralement) beaucoup plus simple et pérenne,
  alignée avec la philosophie actuelle du projet (dépendances minimales,
  dégradation propre).
- Coût : refactor non trivial du cœur de l'application (`app/config.py` a
  déjà été migré une fois de `pydantic_settings.BaseSettings` vers
  `pydantic.BaseModel` dans le cadre du packaging desktop — il faudrait cette
  fois sortir Pydantic entièrement, y compris des routes qui l'utilisent pour
  valider les payloads de formulaire). Ampleur exacte non encore mesurée
  précisément (pas d'audit fichier-par-fichier fait à ce stade).

## Ce qui reste à faire avant de trancher

1. Vérifier concrètement (pas juste sur la doc) si l'un des chemins de
   compilation Android pour `pydantic-core` (Chaquopy, `cibuildwheel`
   Android, maturin direct) produit une wheel utilisable dans un vrai test
   local, même minimal.
2. Si Path A s'avère trop fragile en pratique, faire un audit rapide de
   l'ampleur réelle du refactor Path B (combien de routes/modèles dépendent
   de Pydantic aujourd'hui dans `app/`) pour donner un ordre de grandeur de
   l'effort avant de s'engager.
3. Une fois la dépendance native résolue (l'un ou l'autre chemin), revenir à
   l'architecture d'empaquetage proprement dite : structure de l'APK, choix
   entre Chaquopy et BeeWare/Briefcase pour l'intégration CPython+WebView,
   packaging des assets (`templates/`, `static/`), lifecycle Android
   (mise en veille de l'appli ↔ arrêt/reprise du serveur local — probable
   écho de la subtilité SIGTERM/SIGINT déjà rencontrée côté desktop, à
   revérifier spécifiquement sur le cycle de vie Android).

## Non retenu / hors scope pour l'instant

- Passage par le Play Store (distribution restée volontairement simple :
  APK à sideloader ou F-Droid).
- `python-for-android` / Buildozer : mentionnés dans le paysage général des
  solutions Python-sur-Android mais pas creusés en détail, Chaquopy et
  BeeWare/Briefcase étant les deux options qui reviennent le plus pour ce
  type de stack (serveur web embarqué + WebView).
