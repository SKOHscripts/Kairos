# Design system Kairos

Charte visuelle de l'application : appli perso chaleureuse, quotidienne — pas un
tableau de bord de pilotage. Tout nouveau gabarit ou composant doit réutiliser ces
jetons plutôt que d'en réinventer. Implémentés dans `static/style.css` (variables
`:root`) et `templates/base.html` (police, logo, navigation).

## Couleurs

| Rôle | Variable CSS | Valeur |
|---|---|---|
| Fond de page | `--bg` | `#FBF6EE` (papier ivoire chaud) |
| Surface (cartes) | `--surface` | `#FFFFFF` |
| Surface teintée (hero, bandeaux) | `--surface-tint` | `#FBEEDF` |
| Bordure | `--border` | `#EAE0D2` |
| Bordure forte / pointillés | `--border-strong` | `#E7B27E` |
| Texte principal | `--text` | `#2B241E` |
| Texte secondaire | `--text-2` | `#6E6155` |
| Texte tertiaire / muted | `--text-3` | `#A0917F` |
| Texte inversé (surfaces sombres) | `--text-inverse` | `#FBF6EE` |
| Accent primaire | `--accent` | `#D9713C` (terracotta) |
| Accent primaire, hover/texte foncé | `--accent-700` | `#B85A2A` |
| Accent primaire, fond doux | `--accent-soft` | `#F7DDC8` |
| Accent primaire, ligne | `--accent-line` | `#EFCDA9` |
| Lien / focus | `--link` | `#B85A2A` |
| Surface sombre (carte focus, pilule nav active) | `--dark-surface` | `#2B241E` (texte `--dark-text` `#FBF6EE`, muted `--dark-muted` `#C9B8A5`, accent `--dark-accent` `#F0A868`) |

Il n'y a qu'**un seul accent transverse** (terracotta) : pas de second accent
« repère d'emplacement » comme dans l'ancienne charte slate/sarcelle.

### Badges sémantiques (pilules, `border-radius: 999px`)

| Rôle | Classe | Fond | Texte |
|---|---|---|---|
| Priorité | `.badge.prio` | `#EFE4F2` | `#7A5E86` (prune) |
| Deep work / OK / fait | `.badge.ok` | `#E3EEDD` | `#57845A` (sauge) |
| Urgent / critique / erreur | `.badge.bad` | `#F8DED5` | `#C1503A` (terracotta foncé) |
| Neutre / tag projet | `.badge.neutral` | `#F1E6D6` | `#6E6155` |
| Info / avertissement | `.badge.info`, `.badge.warn` | `#FBEEDF` | `#B85A2A` |

## Typographie

- **Figtree** (400/500/600/700/800) — tout le texte d'interface, labels, chiffres
  (nombres en `font-variant-numeric: tabular-nums`, plus besoin de police mono
  dédiée aux identifiants/chiffres).
- **Newsreader**, italique, 500 — réservée à l'accroche du jour et aux phrases
  d'insight (classe utilitaire `.editorial`). Jamais pour du texte fonctionnel
  (boutons, badges, tableaux, formulaires).
- Échelle : corps ~13.5px · labels majuscules 10–11.5px (`letter-spacing` ~0.04em) ·
  accroche/insight 17–24px italique · chiffres clés (KPI) 18–26px en 800.

## Forme

- Rayon des cartes/panneaux : `--r` 16px (défaut), `--r-md` 14px (cartes
  imbriquées : ligne de tâche, jour de semaine, agenda), `--r-xl` 20px (bandeaux
  d'accueil, carte « en ce moment »).
- Rayon des contrôles (boutons, champs, icônes) : `--r-sm` 10px.
- Pilules (badges, nav, filtres) : `--r-pill` 999px.
- Puce de priorité/catégorie sur une ligne de tâche : `border-left: 3px solid
  <couleur>` (jamais de remplissage `box-shadow: inset`).
- Ombres : supprimées presque partout. Le seul `box-shadow` conservé est celui du
  panneau de modification de tâche ouvert (`.mj-edit-body`), qui se comporte comme
  un popup flottant.

## Logo

Mark « cadran solaire » (cercle + un seul secteur terracotta + point pivot),
`static/favicon.svg` et repris inline dans `templates/base.html` :

```html
<svg width="34" height="34" viewBox="0 0 40 40">
  <circle cx="20" cy="20" r="18.5" fill="#FBEEDF"/>
  <circle cx="20" cy="20" r="18.5" fill="none" stroke="#E7C4A2" stroke-width="1.6"/>
  <path d="M20 20 L20 4 A16 16 0 0 1 35.76 17.22 Z" fill="#D9713C"/>
  <circle cx="20" cy="20" r="2.6" fill="#2B241E"/>
</svg>
```

## Navigation

Barre horizontale sticky (`.topnav`) : logo + titre à gauche, 3 items en pilules
(`.tn-item`, actif = fond `--dark-surface` / texte `--dark-text`) — pas de sidebar.
Une sous-barre (`.topbar`) porte le titre de page et les actions contextuelles
(bascule Jour/Semaine, retour, etc.).

## Écarts assumés par rapport aux maquettes `.dc.html`

Les maquettes du dossier `design_handoff_kairos_redesign/` (non versionné) montrent
une modale de tâche avec pilules cliquables pour la priorité/les points Fibonacci et
des chips à bascule pour les tâches bloquantes. L'implémentation réelle garde des
`<select>`/`<select multiple>` HTML natifs (mêmes noms de champs, mêmes tests) — le
style pilule est appliqué en surface, mais leur interaction reste un menu déroulant
classique, pour ne pas ajouter de JavaScript ni changer la sémantique des
formulaires. Le panneau de modification reste un `<details>` ancré sous la ligne
plutôt qu'une vraie modale centrée, pour la même raison.
