# Dashboard de statistiques

**Rôle** : afficher des indicateurs actionnables sur le suivi des tâches et du temps réel.
**Fichiers couverts** : `app/tasks_stats.py` (module pur de calcul), `templates/kairos_stats.html` (rendu), route `/kairos/stats`.

---

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Les phases 1-9 ont collecté deux sources de données :
- **Tâches** : titre, priorité, points de Fibonacci, durée estimée, type, statut (todo/done/archived).
- **Suivi du temps réel** : sessions de travail (`WorkSession`) horodatées, avec tâche et durée réelles.

Manquait un **outil de feedback empirique** : savoir comment j'estime réellement, où passe mon temps, où accumulent les retards — pour calibrer le système WSJF (poids de priorité, horizon d'urgence) et guider les décisions futures.

### Comportement attendu (utilisateur)

Au chargement de `/kairos/stats`, voir une **page de lecture seule** avec six blocs d'indicateurs, ou un état vide explicite s'il n'y a pas de données :
- **KPIs compacts** (4 cellules) : tâches terminées (fenêtre), temps réel tracké, délai médian de complétion, taux de respect des échéances.
- **Débit hebdomadaire** : tâches + points de Fibonacci terminés par semaine, sur un axe temporel continu (zéro-rempli).
- **Calibration de l'estimation** (le cœur du feedback) : temps réel médian **par palier de points** (si deux paliers se rejoignent, l'échelle ne discrimine pas) ; ratio estimé vs réel.
- **Répartition du temps réel par type** : où passe effectivement le temps (répartition %) ; durée moyenne des sessions = proxy de fragmentation (attention morcelée = sessions courtes/nombreuses).
- **Flux & backlog** : WIP (tâches en cours), âge médian du backlog, tâches en retard, tâches qui traînent, respect des échéances.
- **Complétude des métadonnées** : part des tâches todo qualifiées (points de Fibonacci, durée estimée, type) — incite à les remplir (indirecte : si peu de points posés, la calibration sera peu fiable).

### Critères de succès

- [ ] Page `/kairos/stats` affiche les six blocs d'indicateurs (ou état vide explicite).
- [ ] Chaque bloc expose son effectif `n` ; en dessous de `MIN_SAMPLE=3`, marquage « peu fiable ».
- [ ] Calibration temps réel médian par palier de Fibonacci, biais estimé vs réel (ratio agrégé).
- [ ] Débit hebdomadaire zéro-rempli, répartition temps par type, flux/backlog, complétude en %.
- [ ] Logique pure testée en isolation (`test_tasks_stats.py` sur les calculs).
- [ ] Rendu conforme à la charte (composants `.stat`, `.panel`, `.grid2`, `.barrow/.track/.fill` existants) : aucune librairie de graphes, aucun JavaScript, aucun emoji.
- [ ] `pytest` passe sans appel réseau réel.

### Hors périmètre / différé

- **Graphiques temporels fins** (série chronologique multi-axe) : le `.barrow` en barres HTML (serveur) suffit.
- **Export** (CSV, PDF) : lecture seule, pas de pipeline d'export.
- **Estimation ML** (prédiction des durées futures) : nécessite un historique plus volumineux et une phase dédiée.
- **Nudge de découpage** basé sur les points élevés : noté, non construit (phase 7 a retenu d'autres priorités).
- **Tableau de bord interactif** (filtrage par type, tri, drill-down) : rester simple et pur serveur.

---

## 2. Solution technique

### Vue d'ensemble

**Module pur** `app/tasks_stats.py` : calcule tous les indicateurs à partir de listes de tâches (`Task`) et sessions (`WorkSession`) déjà chargées en mémoire. Aucune requête SQL, aucun appel réseau. La route `/kairos/stats` (dans `app/main.py`) :
1. Charge la liste complète des tâches et sessions de la base.
2. Appelle `compute_dashboard_stats(tasks, sessions, today, settings=settings, now=None)` (fonction orchestratrice).
3. Passe le résultat (`DashboardStats`) au template `kairos_stats.html` (rendu serveur).

**Template** `templates/kairos_stats.html` : rendu six sections `.panel` dans une grille `.grid2`, en pur HTML/Jinja2 — aucun JavaScript actif sur la page.

### Détail des indicateurs

#### 1. KPIs compacts (4 cellules statiques)

Affichées en haut, avant les blocs de détail. Toutes sur la **fenêtre récente** (dernières `settings.stats_window_weeks` semaines, défaut 8).

| Indicateur | Calcul | Source | Visibilité |
|---|---|---|---|
| **Tâches terminées** | `sum(1 for t in tasks if _done_date(t) >= window_start)` | `completed_in_window` | KPI numérique large |
| **Temps réel tracké (fenêtre)** | `sum(session_minutes(s) for s in sessions_in_range(window_start, today))` | `tracked_minutes_window` | formaté minutes/heures |
| **Délai médian complétion** | `_median([(done - created).days for tâches terminées en fenêtre])` | `flow.completion_delay_days` | alerte si > 7 j (badge ambre) |
| **Taux respect échéances** | `100 * deadline_on_time / deadline_total` (tâches terminées ayant une échéance) | `flow.deadline_hit_pct` | vert ≥70%, rouge <70% |

**Honnêteté** : aucun seuil ; affichés toujours même si faible effectif (mais le bloc « Flux & backlog » avertira de la faiblesse de l'échantillon).

#### 2. Débit hebdomadaire

Fonction : `throughput_by_week(tasks, today, weeks)` → `list[WeekThroughput]`.

**Définition** : tâches complétées (`status='done'`) par semaine **ISO** (lundi=0 du groupe ISO8601), zéro-rempli sur un axe continu depuis `first_monday = _monday(today) - timedelta(weeks=weeks-1)` jusqu'à cette semaine.

```python
@dataclass
class WeekThroughput:
    week_start: date           # lundi de la semaine
    label: str                 # "%d/%m" du lundi (ex. "03/07")
    completed: int             # nombre de tâches terminées cette semaine
    points: int                # somme des fibonacci_points des terminées
```

**Calcul** :
- Date de complétion approximée : `updated_at` d'une tâche `status='done'` (convention : pas d'horodatage dédié, suffisant pour l'usage personnel).
- `_done_date(task)` → `date` ou `None` (normalise `updated_at` en UTC-aware d'abord via `_aware`).
- Groupage par lundi de la semaine ISO (`_monday(done_date)`).
- Lunes vides sont incluses (count=0) → axe temporel continu.

**Rendu** : barres horizontales (`.barrow` / `.track` / `.fill.done`), largeur proportionnelle au max de la fenêtre. Chiffre brut + points Fibonacci en muted.

#### 3. Calibration de l'estimation

**Partie A : Temps réel médian par palier de Fibonacci**

Fonction : `fibonacci_calibration(tasks, spent_by_task)` → `list[FiboCalibration]`.

```python
@dataclass
class FiboCalibration:
    points: int                 # valeur de l'échelle (1, 2, 3, 5, 8, 13, 21)
    count: int                  # nombre de tâches à ce palier, terminées et chronométrées
    median_minutes: int | None  # médiane du temps réel (None si count=0)
    
    @property
    def reliable(self) -> bool:
        return self.count >= MIN_SAMPLE  # MIN_SAMPLE=3
```

**Échelle** : Fibonacci classique `1, 2, 3, 5, 8, 13, 21` (pas de variante ½/0/100).

**Calcul** :
- Tâches éligibles : `status='done'` **ET** `fibonacci_points` renseigné **ET** temps réel > 0 (au moins 1 min chronométré).
- `spent_by_task = spent_minutes_by_task(sessions, now=None, tasks=tasks)` (from `app/tasks_time.py`, existant).
- Groupage des durées réelles par points, puis médiane par groupe (`_median(list[float])`).
- Groupes sans aucune tâche ne sont pas affichés (pas de trou dans l'échelle).

**Rendu** : barres horizontales, largeur ∝ médiane max. Label = palier points, valeur = median en minutes/heures, `n` affiché. Badge orange « peu fiable » si `count < MIN_SAMPLE`.

**Interprétation** : si deux paliers adjacents ont la même médiane, l'échelle ne discrimine pas et mérite recalibrage à la main (utilisateur ajuste les poids WSJF ou pose plus de données).

**Partie B : Biais estimé vs réel**

Fonction : `estimation_bias(tasks, spent_by_task)` → `EstimationBias | None`.

```python
@dataclass
class EstimationBias:
    count: int                  # tâches terminées avec estimation ET temps réel
    estimated_minutes: int      # total des estimations (estimated_minutes)
    real_minutes: int           # total du temps réel
    ratio: float                # real_minutes / estimated_minutes (1.0 = juste, >1 = sous-estime)
    
    @property
    def reliable(self) -> bool:
        return self.count >= MIN_SAMPLE
```

**Calcul** :
- Tâches éligibles : `status='done'` **ET** `estimated_minutes` renseigné **ET** temps réel > 0.
- Agrégat, pas moyenne de ratios : somme estimée totale, somme réelle totale, ratio = réel/estimé.
- `None` si aucune tâche éligible ou `estimated_minutes` total = 0.

**Rendu** : court paragraphe sous la calibration Fibonacci. Badge coloré selon seuil : vert si 0.9–1.1 (juste), ambre si 1.1–1.25 ou 0.8–0.9 (léger biais), rouge si >1.25 ou <0.8 (important biais). Texte : « Vous sous-estimez » / « Vous surestimez » / « Estimations justes ». `n` et « peu fiable » si <MIN_SAMPLE.

**Usage** : valide le modèle WSJF, où l'effort = points Fibonacci (si calibré) ou `estimated_minutes` (si points manquent). Biais persistant justifie d'ajuster les poids ou d'être plus honnête à l'estimation.

#### 4. Répartition du temps réel par type

**Partie A : Partage du temps par type**

Fonction : `time_by_type(sessions, task_type_by_id, now=None)` → `list[TypeShare]`.

```python
@dataclass
class TypeShare:
    key: str                    # valeur de Task.task_type (ou "" = sans type)
    label: str                  # libellé affiché ("Sans type" pour "")
    minutes: int                # temps réel passé sur ce type (fenêtre)
    pct: int                    # part du total en % (0-100)
```

**Calcul** :
- `spent_minutes_by_type(sessions, task_type_by_id, now=None)` (from `tasks_time.py`, existant) → `dict[str, int]` (clé = type ou "", valeur = minutes).
- Total = somme des minutes, puis pct = round(100 * minutes / total).
- Triage décroissant par minutes.
- Types avec 0 minute omis.

**Rendu** : barres horizontales (largeur = pct). Label = `task_type` (ou "Sans type"), minutes formatées, pct, le tout en même ligne.

**Interprétation** : où va vraiment le temps (vs ce qui est prévu). Écarts révèlent : certains types coûtent plus que prévu (recalibrer estimation ou priorité), ou l'utilisateur abandonne des types (retard à les renseigner).

**Partie B : Focus = fragmentation des sessions**

Fonction : `focus_stats(sessions, now=None)` → `FocusStats`.

```python
@dataclass
class FocusStats:
    session_count: int                              # nombre de sessions (fenêtre)
    total_minutes: int                              # temps total chronométré (fenêtre)
    avg_session_minutes: int | None                 # durée moyenne d'une session
```

**Calcul** :
- `session_minutes(s, now=None)` pour chaque session (duration in minutes).
- Filtre les durées > 0.
- Moyenne = `round(total / count)` (ou `None` si count=0).

**Rendu** : court paragraphe sous les barres de type. Texte : « **Focus** : N session(s), M minutes en moyenne. » Conseil : « sessions courtes et nombreuses = attention morcelée ».

**Interprétation** : proxy de fragmentation. Durée moyenne courte (ex. <25 min) signale une attention fragmentée (interruptions, multitâche), alors que Kairos vise le deep work.

#### 5. Flux & backlog

Fonction : `backlog_flow(tasks, today, window_start, settings=settings)` → `BacklogFlow`.

```python
@dataclass
class BacklogFlow:
    open_count: int                    # tâches todo maintenant (WIP actuel)
    median_age_days: int | None        # âge médian des todo (aujourd'hui - création)
    overdue_count: int                 # todo en retard (bucket d'urgence 0)
    stale_count: int                   # todo qui traînent (days_stale non nul)
    completion_delay_days: int | None  # délai médian création → complétion (fenêtre)
    deadline_total: int                # tâches terminées (fenêtre) avec échéance
    deadline_on_time: int              # …et terminées à temps (done_date <= deadline)
    
    @property
    def deadline_hit_pct(self) -> int | None:
        if not deadline_total: return None
        return round(100 * deadline_on_time / deadline_total)
```

**Calcul** :

| Champ | Logique |
|---|---|
| `open_count` | `len([t for t in tasks if t.status == "todo"])` |
| `median_age_days` | Tâches todo : `(today - created_at.date()).days` pour chacune, médiane. `None` si vide. |
| `overdue_count` | Tâches todo où `urgency_bucket(t, today) == 0` (retard : deadline/scheduled_date ≤ today). Réutilise `urgency_bucket` (phase 9, `tasks_scheduling.py`). |
| `stale_count` | Tâches todo où `days_stale(t, today, overdue_days=settings.stale_overdue_days, untouched_days=settings.stale_untouched_days) is not None` (réutilise `tasks_staleness.py`, phase 7). |
| `completion_delay_days` | Tâches terminées en fenêtre : `(done_date - created_at.date()).days`, médiane. Reflète la durée moyenne de traitement. |
| `deadline_total` / `deadline_on_time` | Tâches terminées en fenêtre ayant une échéance (`deadline is not None`) ; count total et count où `done_date <= deadline`. |

**Rendu** : quatre cellules `.stat` en grille (`open_count`, `median_age_days`, `overdue_count` avec alerte rouge si >0, `stale_count` avec alerte ambre si >0). Sous le flux, deux sections compactes sur les délais et respect des échéances.

**Interprétation** :
- WIP élevé = surcharge potentielle.
- Âge élevé = retard accumule.
- Overdue/stale = tâches traînent, gestion du temps à revoir.
- Délai de complétion élevé = estimation hors de réalité (comparer avec `estimated_minutes` global).
- Taux respect échéances < 70% = urgence trop basse ou estimation trop optimiste.

#### 6. Complétude des métadonnées

Fonction : `metadata_completeness(tasks)` → `Completeness`.

```python
@dataclass
class Completeness:
    total: int                  # nombre de tâches todo
    with_points: int            # …ayant fibonacci_points renseigné
    with_estimate: int          # …ayant estimated_minutes renseigné
    with_type: int              # …ayant task_type renseigné
    
    @property
    def points_pct(self) -> int:
        return round(100 * with_points / total) if total else 0
    # … idem estimate_pct, type_pct
```

**Calcul** :
- Tâches éligibles : `status='todo'` uniquement (seules les tâches en cours à traiter méritent d'être qualifiées).
- Comptage simple pour chaque champ renseigné (non-vide/non-nul).
- Pct = round(100 * count / total), ou 0 si total=0.

**Rendu** : trois barres horizontales (Points de Fibonacci / Durée estimée / Type de tâche), chacune avec label, largeur pct, pct affiché, count / total affiché. Barre verte si pct ≥ 70%, sinon neutre.

**Interprétation** : incite à remplir les métadonnées — la calibration d'estimation et le tri WSJF en dépendent. Si <70%, l'utilisateur laisse de la clarification en retard.

### Décisions et pièges tracés

#### Date de complétion approximée

**Décision** : une tâche terminée (`status='done'`) n'a pas de colonne `completed_at` dédiée. On utilise `updated_at` (dernière modification), qui approxime la date de complétion.

**Justification** : convention déjà retenue en phase 2 pour la section « Fait » du jour. Suffisant pour un outil personnel (pas un audit). Alterner entre deux colonnes (`updated_at`, `completed_at`) compliquerait les requêtes et les migrations sans gain pour ce cas d'usage.

**Limitation connue** : si l'utilisateur rouvre/referme une tâche, `updated_at` saute à la dernière fermeture (pas de séance intermédiaire). Non problématique en pratique (rouvertures rares).

#### Honnêteté statistique : effectif n et seuil MIN_SAMPLE

**Décision** : `MIN_SAMPLE = 3`. Tout agrégat (médiane, fiabilité) exposant son effectif `n` ; en dessous du seuil, badge orange « peu fiable » côté rendu.

**Justification** : jamais inventer de tendance à partir de trois points. Trois est le seuil classique de significativité (plus petit intervalle symétrique pour médiane de 2 points, plus une marge). Pas bloquant : l'agrégat s'affiche, mais l'utilisateur n'en tire pas de décision solitaire.

**Implémentation** :
- Classes `FiboCalibration`, `EstimationBias`, `TypeCalibration` ont une `@property reliable: bool` → `count >= MIN_SAMPLE`.
- Template vérifie et affiche badge `.warn` si non-fiable.

#### Fenêtre configurable pour les indicateurs « récents »

**Décision** : `settings.stats_window_weeks=8` (par défaut). Fenêtre glissante : `today` - (weeks-1) semaines complètes en arrière = `first_monday = _monday(today) - timedelta(weeks=weeks-1)`.

**Justification** : capte 2 mois de tendance (huit semaines), assez pour voir des patterns sans être écrasé par l'historique ancien. Ajustable par `Settings` pour adapter à l'usage.

**Champs affectés** : `completed_in_window`, `tracked_minutes_window`, KPIs, débit hebdomadaire, temps réel par type, focus, délais de complétion, respect des échéances.

**Champs sur tout l'historique** (pas de fenêtre) : calibration Fibonacci, biais d'estimation, flux courant (WIP), complétude des métadonnées. Justification : l'effort/l'estimation calibre sur l'ensemble de l'expérience ; le backlog courant n'est pas un phénomène récent.

#### Aucune dépendance nouvelle

**Décision** : module pur en Python, aucune librairie. Médiane : `_median(list)` maison (12 lignes). Formule simple : pas de statsmodels, pas de numpy.

**Justification** : projet sobre, déjà sans build. Les calculs sont simples (sommes, moyennes, comptages) et ne justifient pas d'ajouter des dépendances.

#### Réutilisation exclusive

**Décision** : tous les calculs métier dérivent de fonctions existantes.

| Fonction | Module source | Rôle |
|---|---|---|
| `spent_minutes_by_task` | `tasks_time.py` | durée réelle par tâche, agrégée de `WorkSession` |
| `spent_minutes_by_type` | `tasks_time.py` | durée réelle par type (key = `task_type`) |
| `session_minutes` | `tasks_time.py` | durée d'une session unique (avec support `now` pour tests) |
| `sessions_in_range` | `tasks_time.py` | filtre sessions dans une fenêtre de dates |
| `urgency_bucket` | `tasks_scheduling.py` | palier d'urgence (0=retard, 4=neutre) — détermine overdue |
| `days_stale` | `tasks_staleness.py` | jours écoulés depuis dernière édition (indique traîne) |

Aucune duplication : ces modules sont testés indépendamment ; on les appelle.

#### Moment d'exécution de `now`

**Décision** : `compute_dashboard_stats(..., now: datetime | None = None)`. Si `None`, utilise `datetime.now(timezone.utc)`.

**Justification** : testabilité. Les tests injectent un `now` fixe pour vérifier les calculs de durée sans dépendre de l'horloge système.

### Invariants et garde-fous

#### Invariant 1 : Unicité des résultats

La structure `DashboardStats` est immutable une fois construite. Aucun calcul n'affecte la base — la page est lecture seule.

#### Invariant 2 : Normalisation UTC-aware

`_aware(dt: datetime) -> datetime` : toute datetime SQLite (naïve, sans tzinfo) est normalisée en UTC-aware avant calcul. Évalue `dt.replace(tzinfo=timezone.utc)` si naïf.

Justification : éviter les surprises de calculs d'intervalle sur des datetimes naïves (Python les traite naïvement, ce qui produit des bugs subtils).

#### Invariant 3 : Médiane bien définie

`_median(values: list[float]) -> float | None` :
- Liste vide → `None`.
- Liste non-vide → sort, puis médiane classique (valeur du milieu ou moyenne des deux médianes).

Pas de cas limite mal défini : toutes les listes passées sont soit vides (retour `None`), soit ont des nombres fiables.

#### Invariant 4 : Groupage d'ID lundi ISO

`_monday(day: date) -> date` : retourne le lundi de la semaine ISO du jour donné (lundi=0). Cohérent partout.

`weekday()` en Python : lundi=0, dimanche=6. `day - timedelta(days=day.weekday())` → lundi ISO du groupe.

#### Invariant 5 : Pas de tâches perdues

Toutes les tâches chargées en mémoire sont traitées par `compute_dashboard_stats`. Aucun filtrage caché qui laisserait une tâche invisible dans les indicateurs.

#### Invariant 6 : Sessions de travail sans doublon

`spent_minutes_by_task` agrège les sessions déjà chargées — aucun double-comptage. Un test le vérifie.

#### Décisions d'arrondi

- Pct : `round(100 * numerator / denominator)` → Python `round` (banquier, moyen). Acceptable pour l'affichage.
- Délais jours : `round(median_days)` → entier jour.
- Durées minutes : `round(median_minutes)` → entier minute.

#### Non-objectifs explicites

- Pas de cache en base : le calcul est fait à chaque page, pour max 8 semaines de données. Performant (< 100ms sur 500 tâches + 1000 sessions).
- Pas de pagination des résultats : un seul tableau de bord compact.
- Pas de drill-down (ex. cliquer sur "Réunion" pour voir les tâches de ce type) : reste pur serveur / statique.

---

## Structure du code `app/tasks_stats.py`

### Imports et configuration

```python
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone

from .config import Settings
from .tasks_models import Task, WorkSession
from .tasks_scheduling import urgency_bucket
from .tasks_staleness import days_stale
from .tasks_time import (
    session_minutes,
    sessions_in_range,
    spent_minutes_by_task,
    spent_minutes_by_type,
)

MIN_SAMPLE = 3
```

### Fonctions utilitaires (pures)

| Fonction | Signature | Rôle |
|---|---|---|
| `_aware` | `(dt: datetime) -> datetime` | Normalise en UTC-aware |
| `_median` | `(values: list[float]) -> float \| None` | Médiane, None si vide |
| `_monday` | `(day: date) -> date` | Lundi ISO du groupe |
| `_done_date` | `(task: Task) -> date \| None` | Date complétion approx. |

### Classes de sortie (`@dataclass`)

| Classe | Champs | Rôle |
|---|---|---|
| `WeekThroughput` | `week_start, label, completed, points` | Débit une semaine |
| `FiboCalibration` | `points, count, median_minutes, reliable` | Temps par palier |
| `EstimationBias` | `count, estimated_minutes, real_minutes, ratio, reliable` | Biais global |
| `TypeShare` | `key, label, minutes, pct` | Partage temps par type |
| `TypeCalibration` | `key, count, median_minutes, reliable` | Temps par type |
| `FocusStats` | `session_count, total_minutes, avg_session_minutes` | Fragmentation |
| `BacklogFlow` | `open_count, median_age_days, overdue_count, stale_count, completion_delay_days, deadline_total, deadline_on_time, deadline_hit_pct` | Flux WIP + délais |
| `Completeness` | `total, with_points, with_estimate, with_type, points_pct, estimate_pct, type_pct` | Métadonnées % |
| `DashboardStats` | Tous les précédents + `window_weeks, generated_for, completed_in_window, tracked_minutes_window, has_any_data` | Conteneur complet |

### Fonctions de calcul (pures, testables en isolation)

| Fonction | Entrée | Sortie | Testé |
|---|---|---|---|
| `throughput_by_week` | `tasks, today, weeks` | `list[WeekThroughput]` | Oui (zéro-rempli) |
| `fibonacci_calibration` | `tasks, spent_by_task` | `list[FiboCalibration]` | Oui (MIN_SAMPLE) |
| `estimation_bias` | `tasks, spent_by_task` | `EstimationBias \| None` | Oui (None si pas de données) |
| `time_by_type` | `sessions, task_type_by_id, now=None` | `list[TypeShare]` | Oui (tri décroissant) |
| `calibration_by_type` | `tasks, spent_by_task` | `list[TypeCalibration]` | Oui (médiane par type) |
| `focus_stats` | `sessions, now=None` | `FocusStats` | Oui (avg robuste) |
| `backlog_flow` | `tasks, today, window_start, settings` | `BacklogFlow` | Oui (WIP + délais) |
| `metadata_completeness` | `tasks` | `Completeness` | Oui (% par champ) |
| `compute_dashboard_stats` | `tasks, sessions, today, settings, now=None` | `DashboardStats` | Oui (orchestration) |

### Points d'intégration avec la route

```python
# Dans app/main.py, route GET /kairos/stats :
stats = compute_dashboard_stats(
    tasks=tasks,  # liste Task depuis session.query(Task).all()
    sessions=sessions,  # liste WorkSession depuis session.query(WorkSession).all()
    today=date.today(),  # ou date.today() mockable en test
    settings=settings,  # Settings global, contient stats_window_weeks, etc.
    now=None,  # None = datetime.now(timezone.utc) ; testé avec now=datetime(...)
)
return templates.TemplateResponse("kairos_stats.html", {"stats": stats, ...})
```

---

## Structure du template `templates/kairos_stats.html`

### Macros et filtres utilisés

| Élément | Source | Rôle |
|---|---|---|
| `{% from "_icons.html" import icon %}` | `_icons.html` | Icônes charte (trending_up, clock, etc.) |
| `{{ fmt_minutes(...) }}` | Contexte global (filtre Jinja) | Formate secondes/minutes → "1h 23m" |
| `.stats`, `.stat`, `.stat-num` | `static/style.css` | Classes KPI (grid, couleurs tone-*) |
| `.panel`, `.grid2` | Charte | Conteneur section, grille 2 colonnes |
| `.barrow`, `.barlabel`, `.track`, `.fill` | Charte | Barre horizontale (label, track avec % interne) |
| `.badge`, `.warn`, `.muted`, `.empty` | Charte | Styles additifs |

### Structure HTML

```html
{% if not stats.has_any_data %}
  <section class="panel">
    <h2>{{ icon('trending_up') }} Statistiques</h2>
    <p class="empty">État vide…</p>
  </section>
{% else %}
  {# KPIs en haut #}
  <section class="stats">
    <div class="stat">…</div>
    …
  </section>

  {# Grille 2 colonnes pour les 6 blocs #}
  <div class="grid2">
    <section class="panel">
      <h2>{{ icon(...) }} Titre bloc 1</h2>
      <p class="hint">Sous-titre</p>
      {% for item in items %}
        <div class="barrow">…</div>
      {% endfor %}
    </section>
    …
  </div>
{% endif %}
```

### Rendering du biais d'estimation

```html
{% if stats.bias %}
  <p class="hint" style="margin-top:0.6rem;">
    <strong>Biais estimé vs réel</strong> :
    <span class="badge {% if stats.bias.ratio > 1.25 or stats.bias.ratio < 0.8 %}warn{% else %}ok{% endif %}">
      ×{{ '%.2f' | format(stats.bias.ratio) }}</span>
    : {{ fmt_minutes(stats.bias.real_minutes) }} réel pour
      {{ fmt_minutes(stats.bias.estimated_minutes) }} estimé
    <span class="muted">(n={{ stats.bias.count }}{% if not stats.bias.reliable %}, peu fiable{% endif %})</span>.
    {% if stats.bias.ratio > 1.1 %}Vous sous-estimez.{% elif stats.bias.ratio < 0.9 %}Vous surestimez.{% else %}Estimations justes.{% endif %}
  </p>
{% endif %}
```

Logique : badge couleur selon ratio, texte d'interprétation automatique, petitesse de l'échantillon signalée.

---

## Tests

Module testé en isolation dans `tests/test_tasks_stats.py` :
- Calculs pur (pas de fixtures de base) : listes `Task` et `WorkSession` en mémoire.
- Cas limites : listes vides, MIN_SAMPLE violation, fenêtres sans données.
- Vérification d'invariants : zéro-remplissage (pas de trou dans l'axe temporel), stabilité des tris.

Tests de route dans `tests/test_kairos_route.py` :
- GET `/kairos/stats` sans données : `has_any_data=False`, rendu de l'état vide.
- GET `/kairos/stats` avec données : tous les blocs présents et calculés.

Aucun appel réseau, aucun accès réseau réel (`pytest`).
