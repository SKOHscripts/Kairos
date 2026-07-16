# Accueil & navigation
_Rôle : le gabarit commun de toutes les pages (barre de navigation horizontale
sticky, bandeau de page, bouton Quitter conditionnel, restauration de scroll) et la
page d'accueil (`/`), qui présente Kairos et rend le `README.md` comme contenu
éditorial. Fichiers couverts : `templates/base.html`, `templates/_icons.html`,
`templates/home.html`, la route `/` (`app/main.py::home`) et
`app/main.py::_render_readme`. Le contenu propre à la vue Jour/GTD (au-delà du
gabarit qu'elle hérite) est traité dans `docs/spec/vue-jour-gtd.md` — n'est repris
ici que ce qui est commun à toutes les pages (topnav) ou spécifique à l'accueil._

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos comporte plusieurs vues (Accueil, Jour, Semaine, Statistiques, Réglages) qui
doivent partager une identité visuelle et une navigation cohérentes, sans dupliquer
le HTML de la barre de navigation dans chaque template. Il faut aussi une page
d'accueil qui explique ce que fait l'outil à un nouvel utilisateur (collègue
découvrant Kairos), sans maintenir une documentation en double avec le `README.md`
du dépôt.

Contrainte additionnelle propre au bureau : l'exécutable de bureau (PyInstaller,
`console=False`, voir `docs/spec/packaging-lancement.md`) n'a pas de fenêtre de
terminal à fermer pour arrêter le serveur — il faut un moyen depuis l'interface.
Cette contrainte n'existe pas sur Android (bac à sable applicatif, on quitte par le
système) ni en développement/service (Ctrl+C, `systemctl stop`).

Autre contrainte : chaque action utilisateur (cocher une tâche, ajouter un créneau,
supprimer...) redirige en rechargement complet de page après un POST ; sans
précaution, la position de défilement saute en haut de page à chaque action, ce qui
est pénible sur une longue liste.

### Comportement attendu (utilisateur)

- Une barre de navigation horizontale, fixe en haut de l'écran (sticky), présente sur
  toutes les pages : logo/nom Kairos (lien vers l'accueil), puis les entrées Accueil,
  Jour, Semaine, Statistiques, Réglages. L'entrée correspondant à la page affichée
  est mise en évidence.
- Sous chaque page, un bandeau secondaire (topbar) affiche le titre de la page et,
  le cas échéant, des actions rapides propres à cette page.
- Sur l'exécutable de bureau uniquement, un bouton « Quitter » est visible dans la
  barre de navigation ; cliquer dessus demande confirmation puis arrête le serveur.
  Ce bouton est absent partout ailleurs (développement, service systemd, Android).
- Après toute action qui recharge la page (ajout, suppression, marquage « fait »...),
  la page revient à la même position de défilement qu'avant l'action — jamais un
  saut en haut de page.
- La page d'accueil (`/`) présente, dans l'ordre : un bandeau d'introduction (hero)
  avec le nom et le rôle de Kairos et des boutons d'accès rapide aux vues
  principales ; une section « Ce que fait Kairos » résumant les fonctionnalités et
  la formule du score de priorité ; puis le contenu du `README.md` du projet, rendu
  en HTML, avec un sommaire de navigation intercalé juste après sa section « En
  bref ».
- Le bandeau de page (topbar) de l'accueil garde son titre par défaut (« Kairos ») et
  n'affiche pas de bouton d'action dédié : le seul CTA « Ouvrir Aujourd'hui » vit
  dans le hero, pas répété ailleurs sur la page.

### Critères de succès

- La navigation reste utilisable et sans débordement horizontal sur mobile (~375px
  de large, cas de l'APK Android), avec des cibles tactiles ≥ 44px.
- Le bouton « Quitter » n'apparaît **jamais** en dehors de l'exécutable de bureau
  figé (vérifiable via `sys.frozen`).
- Toute mise à jour du `README.md` du dépôt se répercute automatiquement sur la page
  d'accueil, sans édition manuelle du template.
- La position de scroll est restaurée après un rechargement déclenché par un
  formulaire POST, y compris sur une page longue (liste de tâches du jour).
- Aucun CTA dupliqué sur la page d'accueil.

### Hors périmètre / différé

- Barre de navigation basse (bottom nav) sur mobile/APK : décision assumée de ne
  jamais en ajouter une, y compris sur petit écran — seule la topnav se redimensionne
  (voir § Décisions et pièges tracés).
- Sidebar verticale : abandonnée avec la charte visuelle actuelle (`docs/DESIGN_SYSTEM.md`)
  au profit de la topnav horizontale.
- Contenu détaillé de la vue Jour/GTD (filtres, backlog, progression du jour...) :
  `docs/spec/vue-jour-gtd.md`.
- Authentification/comptes multiples : Kairos reste mono-utilisateur, la navigation
  n'a pas de notion de session utilisateur.

## 2. Solution technique

### Vue d'ensemble

`templates/base.html` est le gabarit Jinja2 hérité par toutes les pages (`{% extends
"base.html" %}`). Il définit la structure commune (`<head>`, topnav, topbar, zone de
contenu) et expose des blocs que les templates enfants remplissent
(`title`, `topbar_title`, `topbar_actions`, `content`, `scripts`). Les icônes SVG
inline viennent de la macro `icon()` de `templates/_icons.html`, importée par les
templates qui en ont besoin. La page d'accueil (`templates/home.html`) étend ce
gabarit et affiche un contenu propre (hero, résumé fonctionnel, formule du score)
suivi du `README.md` rendu côté serveur par `app/main.py::_render_readme` et injecté
dans le template via le contexte de la route `/` (`app/main.py::home`).

### Détail par composant

#### `templates/base.html` — gabarit commun

- **`<head>`** : titre par bloc (`{% block title %}Kairos{% endblock %}`), favicon
  SVG (`/static/favicon.svg?v={{ asset_version }}`), polices Google Fonts (IBM Plex
  Sans 400/500/600/700 + Newsreader italique 500, voir `docs/DESIGN_SYSTEM.md`),
  feuille de style unique `/static/style.css?v={{ asset_version }}`. Le suffixe
  `?v=` (posé par `templates.env.globals["asset_version"]` dans `app/main.py`, valeur
  = horodatage de modification de `style.css`, ou `0` si illisible) est un
  anti-cache navigateur : sans lui, un navigateur peut continuer à servir un vieux
  `style.css` en cache après une mise à jour de l'app (nouvelle version installée,
  `git pull`), donnant une interface à moitié stylée. L'horodatage change dès que le
  fichier change (mise à jour) et reste stable entre deux requêtes d'un même
  lancement (cache normal conservé le reste du temps).
- **Topnav (`.topnav` + `.tn-brand`/`.tn-nav`)** : barre horizontale sticky en tête
  de page.
  - `.tn-brand` : lien vers `/`, logo Kairos (SVG inline, mire/cadran solaire,
    couleurs terracotta d'origine conservées volontairement — voir
    `docs/DESIGN_SYSTEM.md` § Identité) + nom « Kairos » + sous-titre
    « le bon moment, la bonne tâche » (masqué sous 720px, voir § Invariants).
  - `.tn-nav` : cinq entrées (Accueil `/`, Jour `/kairos?view=day`, Semaine
    `/kairos?view=week`, Statistiques `/kairos/stats`, Réglages
    `/kairos/settings`), chacune avec une icône (`icon('home')`, `icon('clock')`,
    `icon('calendar')`, `icon('trending_up')`, `icon('gear')`) et un libellé texte.
  - **Mise en évidence de l'entrée active** : classe `active` conditionnée sur les
    variables de contexte passées par chaque route — `page == 'home'`,
    `page == 'kairos' and (view is not defined or view != 'week')` (Jour, y compris
    quand `view` n'est pas défini — défaut jour), `page == 'kairos' and view ==
    'week'` (Semaine), `page == 'kairos_stats'`, `page == 'settings'`. Ces variables
    (`page`, `view`) sont posées par chaque route dans le contexte de rendu, pas
    déduites de l'URL côté template.
  - **Bouton Quitter (`.tn-quit`)** : bloc conditionné par `{% if is_frozen %}`.
    `is_frozen` est une variable globale Jinja2 posée une fois au chargement du
    module (`templates.env.globals["is_frozen"] = getattr(sys, "frozen", False)`,
    `app/main.py`) — vraie uniquement dans un exécutable PyInstaller (l'attribut
    `sys.frozen` n'existe que dans ce cas). Formulaire `POST /kairos/shutdown` avec
    confirmation JavaScript (`onsubmit="return confirm(...)"`) : « Quitter Kairos ?
    Le serveur va s'arrêter : il faudra relancer l'exécutable pour y revenir. » —
    voir `app/main.py::shutdown` pour le détail de l'arrêt côté serveur (SIGINT,
    tracé dans `docs/spec/packaging-lancement.md`).
- **Topbar (`.topbar`)** : sous la topnav, dans `<main class="content">`. Titre par
  bloc (`{% block topbar_title %}Kairos{% endblock %}`, par défaut le nom de
  l'app — l'accueil ne le redéfinit plus, voir § Décisions et pièges tracés) et zone
  d'actions par bloc (`{% block topbar_actions %}{% endblock %}`, vide par défaut).
- **`{% block content %}`** : contenu propre à chaque page, dans `<div
  class="page">`.
- **`{% block scripts %}`** : point d'extension pour un JS spécifique à une page
  (vide dans `base.html`, utilisé par les templates enfants qui en ont besoin — hors
  périmètre de cette spec, voir `docs/spec/vue-jour-gtd.md` pour la vue Jour).
- **Script de restauration de scroll** (fin de `<body>`, IIFE) :
  - `KEY = 'kairos-scroll-y'` (clé `sessionStorage`).
  - Sur tout `submit` d'un `<form method="post">` (écoute déléguée sur `document`,
    phase de capture — `true` en 3ᵉ argument de `addEventListener`, pour intercepter
    avant tout gestionnaire qui stopperait la propagation) : enregistre
    `window.scrollY` courant dans `sessionStorage`. Les formulaires `GET` (recherche,
    filtres) ne déclenchent pas cette sauvegarde — seule une soumission POST est
    suivie d'un rechargement plein page qui perdrait le scroll.
  - Sur `DOMContentLoaded` : relit la valeur sauvegardée, la retire immédiatement de
    `sessionStorage` (évite de la réappliquer sur une navigation ultérieure sans
    rapport), puis restaure la position via `requestAnimationFrame(() =>
    window.scrollTo(0, y))` — différé d'une frame pour laisser le layout se stabiliser
    avant de scroller (sinon la position calculée peut être fausse si le contenu
    n'est pas encore tout à fait rendu).
  - Tous les accès à `sessionStorage` sont encadrés par `try/catch` silencieux : un
    navigateur en navigation privée stricte, ou avec le stockage désactivé, ne doit
    jamais faire planter la page — la restauration de scroll est un confort, jamais
    une dépendance bloquante.

#### `templates/_icons.html` — bibliothèque d'icônes

- Macro unique `icon(name, title='')`, appelée `{% from "_icons.html" import icon
  %}` par chaque template qui en a besoin (dont `base.html`, `home.html`).
- Deux gabarits SVG : un cas spécial `refresh` (`viewBox="0 0 35 35"`, `fill=
  "currentColor"`, tracé plein), et le cas général (`viewBox="0 0 16 16"`, `fill=
  "none"`, `stroke="currentColor"`, `stroke-width="1.5"`) qui couvre toutes les
  autres icônes du projet par un grand `{% elif name == ... %}` (lock, warning,
  error, check, check_circle, plus, link, folder, tag, download, close, clipboard,
  pencil, save, trash, file_text, grid, share, blocked, comment, arrow_left,
  arrow_up_right, trending_up, chevron_down, chevron_right, export_up, import_down,
  dot, dot_empty, clock, calendar, layers, dashboard, gitlab, chevron_left, search,
  home, gear — défaut : un simple cercle si `name` ne correspond à rien).
- Accessibilité : `aria-hidden="true"` par défaut ; si `title` est fourni,
  `role="img" aria-label="{{ title }}"` à la place — jamais les deux, jamais aucun
  des deux.
- Toutes les icônes : `1em × 1em`, `currentColor`, `vertical-align:-0.15em` (alignement
  optique avec le texte adjacent), `flex-shrink:0` (ne se compriment jamais dans un
  conteneur flex serré, ex. un bouton étroit).

#### `templates/home.html` + route `/` (`app/main.py::home`) — page d'accueil

- **Route** : `@app.get("/")` → `home(request)`. Construit le contexte via
  `_render_readme()` (voir ci-dessous), ajoute `"page": "home"` (pour la mise en
  évidence de la topnav) et rend `home.html`. Pas d'accès base de données : la page
  d'accueil ne dépend que du contenu statique du README et de la structure du
  gabarit.
- **Hero (`.home-hero`)** : titre d'accroche, paragraphe de présentation, trois
  boutons d'action (`Ouvrir « Aujourd'hui »` → `/kairos`, `Vue semaine` →
  `/kairos?view=week`, `Statistiques` → `/kairos/stats`), et un bloc marque
  (`.home-hero-brand`) reprenant le logo Kairos, le nom, et le sous-titre
  « nom de code · 14h55 » (voir `SPEC_KAIROS.md` § Phase 15 pour l'origine du nom :
  *Kairos* — καιρός, le moment opportun, par opposition à *Chronos* — et du nom de
  code *14h55*, clin d'œil au « post-lunch dip », le creux post-déjeuner
  statistiquement le moins productif de la journée, en écho au « 14h05 » emblématique
  de l'outil).
- **Section « Ce que fait Kairos » (`.home-brief`)** : deux colonnes (grille CSS,
  empilées sous 760px) —
  - `.home-brief-actions` : liste à puces des cinq fonctionnalités principales
    (score de priorité, pose dans les trous d'agenda, protection du deep-work,
    suivi du temps réel, imports GitLab/TimeTree optionnels) — reprise quasi
    littérale de la section « En bref » du README, en version plus concise pour la
    lecture rapide en tête de page ;
  - `.home-formula` : formule du score de priorité (WSJF), présentée visuellement
    (fraction HTML/CSS, `role="img"` avec `aria-label` décrivant la formule en texte
    pour l'accessibilité) et sa légende (`valeur(priorité)` = `4^(2−p)`, soit P0=16,
    P1=4, P2=1 ; `criticité(échéance)` monte à l'approche de l'échéance ou de la
    date programmée, la plus proche des deux, une tâche en retard passant toujours
    devant hors score ; `effort` en points de Fibonacci, 1 à 21, ou durée estimée à
    défaut). Cohérente avec le calcul réel décrit dans
    `docs/spec/ordonnancement.md` — cette section n'en est qu'une présentation
    pédagogique, pas une seconde source de vérité.
- **README rendu (`.prose-wrap.card`)** : voir `_render_readme` ci-dessous pour le
  découpage. Structure du template :
  ```
  <div class="prose-wrap card">
    <div class="prose">{{ readme_intro_html | safe }}</div>
    {% if readme_toc %}
    <nav class="home-toc panel" aria-label="Sommaire du README">...</nav>
    {% endif %}
    <div class="prose">{{ readme_rest_html | safe }}</div>
  </div>
  ```
  Les trois éléments (`readme_intro_html`, le sommaire, `readme_rest_html`) sont des
  **enfants directs** de `.prose-wrap`, jamais imbriqués dans un même `.prose` —
  garde-fou explicite pour que les styles `.prose h2/ul/li/a` (typographie éditoriale
  du README) ne s'appliquent jamais accidentellement au sommaire (qui a sa propre
  classe `.home-toc`/`.home-toc-list`). Une seule mise en page, identique petit et
  grand écran (pas de variante mobile distincte pour ce bloc).
  - `toc_entry(entry)` (macro locale du template) : rendu récursif d'une entrée de
    sommaire (`<li><a href="#{{ entry.id }}">...</a>{% if entry.children %}<ul>...
    </ul>{% endif %}</li>`), pour représenter la hiérarchie H2/H3 du README.
- **Pas de redéfinition de `topbar_title`/`topbar_actions`** : l'accueil hérite du
  défaut de `base.html` (titre « Kairos », pas d'action) — voir § Décisions et
  pièges tracés pour l'historique de ce choix.

#### `app/main.py::_render_readme` — rendu serveur du README

- Convertit `README.md` (racine du dépôt, lu via `BASE_DIR / "README.md"`) en HTML
  avec `markdown.Markdown(extensions=["extra", "sane_lists", "toc"],
  extension_configs={"toc": {"permalink": False}})` — `extra` pour la syntaxe
  Markdown étendue (tableaux, etc.) déjà utilisée dans le README, `sane_lists` pour
  un comportement de listes plus prévisible, `toc` pour générer les identifiants
  d'ancre (`id=`) sur les titres et exposer l'arbre `toc_tokens` (sans lien
  permalien injecté dans le HTML, `permalink: False` — le sommaire séparé de
  `home.html` en tient lieu).
- **Racine unique retenue** : `converter.toc_tokens[0]["children"]` — le nœud racine
  du `toc_tokens` correspond au H1 « Kairos » du README ; ses enfants (H2/H3)
  forment le sommaire utilisé par `home.html`. Le H1 lui-même n'est pas repris dans
  le sommaire : il est déjà affiché dans le bandeau/hero d'accueil, doublon inutile.
- **Découpage en deux morceaux** (`intro_html`, `rest_html`) : le HTML complet est
  coupé juste avant le **second** H2 du README (donc juste après la section
  « En bref », la première section du document) — `toc[1]["id"]` donne l'ancre de ce
  second H2, `html.find(f'<h2 id="{toc[1]["id"]}"')` localise le point de coupe dans
  le HTML généré. Si moins de deux entrées de sommaire existent, ou si le marqueur
  n'est pas trouvé (garde-fou défensif), tout le HTML reste dans `intro_html` et
  `rest_html` reste vide plutôt que de planter.
- **Pourquoi ce découpage** : le sommaire (`readme_toc`) s'intercale dans
  `home.html` entre `readme_intro_html` et `readme_rest_html` — c'est-à-dire dans le
  flux réel du README, juste après « En bref », **pas avant tout l'article** (une
  version antérieure le plaçait en tête de page, jugée moins naturelle à la lecture
  — voir historique commits `1de8150`/`a425fd9`, « position vraiment après le titre
  En bref », « fin du sommaire dupliqué »).
- **Source unique, jamais dupliquée à la main** : toute modification du
  `README.md` du dépôt se répercute automatiquement sur la page d'accueil au
  prochain chargement (pas de cache de rendu entre requêtes — le fichier est relu et
  reconverti à chaque appel de `home()`).
- Le fichier `SPEC_KAIROS.md` référencé par un lien relatif du README est servi tel
  quel par la route dédiée `@app.get("/SPEC_KAIROS.md")` (`app/main.py::spec_kairos`,
  `FileResponse`), sans passer par `_render_readme` — hors périmètre direct de cette
  spec (pas de gabarit, pas de navigation), mentionné ici pour la complétude du lien
  README → SPEC_KAIROS depuis l'accueil.

### Décisions et pièges tracés

- **CTA dupliqué retiré de l'accueil** (commit `52030d6`, 2026-07-15) : l'accueil
  définissait auparavant `{% block topbar_title %}Bienvenue{% endblock %}` et
  `{% block topbar_actions %}<a class="btn primary" href="/kairos">... Ouvrir «
  Aujourd'hui »</a>{% endblock %}` dans le bandeau topbar, en plus du même bouton
  déjà présent dans le hero quelques lignes plus bas. Retiré : le titre de bandeau
  retombe sur le défaut de `base.html` (« Kairos »), le hero reste la seule source
  du CTA « Ouvrir Aujourd'hui ». Décision produit, pas un correctif de bug —
  tracée ici plutôt que dans le README (le README ne documente que les
  fonctionnalités, pas les décisions d'implémentation d'une page).
- **Bouton Quitter conditionné à `is_frozen` uniquement** (pas à une détection
  d'environnement plus large) : cible précisément le cas où l'utilisateur n'a
  **aucun** autre moyen d'arrêter le serveur (pas de terminal visible,
  `console=False`). En développement (Ctrl+C) et en service systemd
  (`systemctl stop`, potentiellement partagé entre plusieurs utilisateurs), le
  bouton serait soit redondant, soit dangereux (arrêt d'un service partagé d'un clic
  malheureux) — absent volontairement dans ces deux cas. Sur Android, `is_frozen`
  est également faux (pas un exécutable PyInstaller) : le bouton est naturellement
  absent, sans changement de gabarit nécessaire — on quitte par le système.
- **Restauration de scroll par `sessionStorage`, pas par `history.scrollRestoration`
  natif** : le rechargement après un POST est un **nouveau document** (redirection
  serveur), pas une navigation historique (back/forward) — `scrollRestoration`
  automatique du navigateur ne s'applique qu'à ce second cas. `sessionStorage` est le
  mécanisme qui survit à un rechargement complet de document tout en restant scopé à
  l'onglet.
- **Écoute du `submit` en phase de capture (`true`)** : garantit que la sauvegarde de
  scroll s'exécute avant tout gestionnaire de formulaire spécifique à une page qui
  pourrait appeler `stopPropagation()` — sans ça, un tel gestionnaire local
  empêcherait silencieusement la sauvegarde de scroll de se déclencher.
- **Pas de bottom nav mobile** : décision de charte visuelle (`docs/DESIGN_SYSTEM.md`
  § Navigation & mobile) — la topnav horizontale sticky reste l'unique navigation à
  toutes les tailles d'écran, y compris l'APK Android ; seul son rendu se
  redimensionne (sous-titre masqué, pilules resserrées sous 720px). Choix consigné
  dans la charte, repris ici car il conditionne directement `base.html`.
- **Logo aux couleurs terracotta d'origine, hors palette ardoise/bleu du reste de
  l'UI** : exception assumée de la charte (`docs/DESIGN_SYSTEM.md` § Identité), le
  seul point de couleur chaude volontaire au milieu d'une interface sinon neutre —
  ne pas « corriger » vers la palette neutre lors d'un futur passage sur
  `base.html`/`home.html`.
- **`toc_tokens[0]["children"]` plutôt que `toc_tokens` brut** : évite que le sommaire
  n'affiche une entrée racine unique (le H1) suivie de tous les H2/H3 en profondeur
  +1 artificielle — en ne prenant que les enfants du H1, le sommaire commence
  directement au niveau H2, cohérent avec le fait que le H1 est déjà affiché ailleurs
  sur la page.

### Invariants et garde-fous

- `is_frozen` est calculé **une seule fois**, au chargement du module `app/main.py`
  (`getattr(sys, "frozen", False)`), jamais recalculé par requête — cohérent avec le
  fait qu'un process ne change pas de mode de lancement en cours de vie.
- `asset_version` (anti-cache) est calculé une seule fois au chargement du module ;
  un changement de `style.css` en cours de vie du process (rare, développement
  local) n'est reflété qu'au redémarrage du serveur.
- Le sommaire de l'accueil (`readme_toc`) ne peut jamais faire fuir la typographie du
  README (`.prose`) vers ses propres liens : garanti structurellement par le fait que
  `readme_intro_html`, la nav du sommaire, et `readme_rest_html` sont trois enfants
  directs distincts de `.prose-wrap`, jamais imbriqués dans un `.prose` commun (voir
  § Détail par composant, `home.html`).
- Le bouton Quitter n'est jamais rendu si `is_frozen` est faux — aucun autre chemin
  du code ne l'affiche conditionnellement autrement.
- Toute page qui étend `base.html` doit fournir `page` (et `view` si pertinent) dans
  son contexte de rendu pour que la mise en évidence de la topnav reste correcte ;
  une page qui omet `page` n'active aucune entrée (dégradation silencieuse, jamais
  une erreur de rendu).
- `_render_readme` ne modifie jamais `README.md` sur disque (lecture seule) ; le
  HTML produit n'est jamais mis en cache entre requêtes, garantissant la bijection
  affichage ↔ contenu réel du fichier à tout instant.
