# Feuille de route et GitHub Project

_Rôle : planifier les prochaines versions de Kairos avec les issues, les milestones
et un GitHub Project, et faire entrer les retours des utilisateurs dans ce circuit.
Ce document décrit le circuit ; les décisions de conception restent dans
`docs/spec/` (voir `CLAUDE.md`)._

Les issues, les milestones et les labels sont en **anglais** (comme les issues des
utilisateurs) ; ce document est en français comme le reste de `docs/`.

## Ce qui est en place dans le dépôt

### Milestones

| Milestone | Sens |
|---|---|
| `3.7.0` | Version préparée (`30700`, notes de version FR/EN ; tag `v3.7.0` posé par le propriétaire) : suites du guide (#94, #95), déplacer depuis la vue Semaine (#106), fériés des créneaux « jours ouvrés » par défaut (#109), export et filtres des statistiques (#113, #114), thème sombre (#72), Réglages par catégories (PR #124). |
| `3.8.0` | Version préparée (`30800`, notes de version FR/EN ; tag `v3.8.0` posé par le propriétaire) : vue Mois (#107), créneaux mensuels (#108), une seule occurrence d'un créneau (#110), évolutions des statistiques (#112), corriger les estimations (#101), rappels (#103, spec `rappels.md`), première tâche d'équipe guidée (#97). |
| `3.9.0` | Version préparée (`30900`, notes de version FR/EN ; tag `v3.9.0` posé par le propriétaire) : noms des niveaux (#93), meilleures heures mesurées par l'efficacité (#105), accompagnement des récurrences et des Réglages (#98), jours et heures par membre (#104), notes dans les échanges (#120), propositions d'un membre (#121), dossier des données, diagnostic et proxy du bureau (#115, spec `bureau-avance.md`), lien « Voir la dernière version » sur Android (#143). #75 en sort (choix du propriétaire, 2026-10-24), rangé en `3.11.0`. |
| `3.10.0` | Version préparée (`31000`, notes de version FR/EN ; tag `v3.10.0` posé par le propriétaire) : connexions externes **sans réseau ni compte** (suivi : #140) : import d'un calendrier `.ics` en créneaux occupés, journées entières en badge (#136, spec `connexions-externes.md`), fichier lié sur le bureau et Android avec garde contre une modification faite ailleurs (#137, la moitié « écriture » de #88), calendriers du téléphone lus en direct (#138, `READ_CALENDAR` facultative). |
| `3.11.0` | Version préparée (`31100`, notes de version dans les six langues ; tag `v3.11.0` posé par le propriétaire) : espagnol, allemand, russe et italien en plus du français et de l'anglais, suivant la langue du système (#75, spec `i18n.md`) ; fiches du magasin et page web dans ces langues. Les langues que la police ne couvre pas ou qui s'écrivent de droite à gauche : #147 (Backlog). |
| `3.12.0` | Prochaine version : widgets Android « Maintenant » et « Journée » (#150, spec `widgets-android.md`) et récapitulatif du jour en notification (#151, `rappels.md`). Spécifiée le 2026-10-26, à implémenter. Sans issue, version à fixer par le propriétaire : la couleur par défaut passe du miel à l'océan (thème, icônes d'installation, boutons de téléchargement, site, captures ; `apparence.md` § Couleur par défaut). |
| `4.0.0` | **Version majeure** : ce qui change le modèle de données, le format d'échange ou une décision fondatrice de `docs/spec/`, donc ne peut pas entrer dans une version mineure. Spécifiée le 2026-10-26 : synchronisation par fichier avec fusion à la lecture (#88) et durées apprises partagées (#100) (`synchronisation.md`) ; connexions en ligne facultatives (#139, #144 ; lien ICS, CalDAV, Microsoft 365, Google, vérification des versions Android) qui **rouvrent « sans permission réseau »** (`connexions-en-ligne.md`, un seul APK avec `INTERNET`, fonctions désactivées par défaut) ; estimateur par modèle (#99, `estimateur-modele.md`). Décision du même jour : **plusieurs équipes par base (#118) : non**, une équipe par base (`equipe.md`). |
| `3.13.0` | Proposée : liste complète des fonctionnalités sur l'Accueil et tableau du README (#153, `liste-fonctionnalites.md`, spécifiée le 2026-10-26) ; avis des mois sautés d'une tâche mensuelle (#111, `recurrence.md`). Visite guidée interactive spécifiée (#96, `visite-guidee.md`), version à fixer. Glisser-déposer (#102) : décision « non » confirmée (`accessibilite.md`). |
| `Backlog` | Non planifié : décision ou spec à écrire d'abord, dépendance externe (signature, synchronisation) ou attente de retours. Signatures macOS et Windows (#116, #117 : `deferred`), intégrations Jira/GitLab (#119), langues hors police (#147). |

Le contenu de `4.0.0` est un **brouillon de planification**
(issues #93 à #121 réparties le 2026-10-19, `3.9.0` et `4.0.0` remplis le 2026-10-24,
`3.10.0` créé le même jour pour les connexions externes) :
un milestone se déplace librement, rien n'est promis. Seule la **prochaine version** (`3.12.0` depuis la préparation de
la `3.11.0`, le 2026-10-25 ; ses issues #150 et #151 n'ont plus de label d'état une fois la spec écrite) doit
être « propre » (voir ci-dessous) ; `4.0.0` est un horizon et garde les
labels d'état de ses issues, qui disent ce qu'il reste à trancher avant de les tirer
vers la prochaine version.
Une version n'est plus « brouillon » quand ses issues ont une spec à jour dans
`docs/spec/` (étape 1 du workflow de `CLAUDE.md`).

### Labels

| Famille | Labels |
|---|---|
| Type | `bug`, `enhancement` ; `documentation` (documentation seule) ; `feedback` (retour utilisateur à trier) |
| Domaine | `planning`, `guide`, `statistics`, `recurrence`, `week-view`, `team`, `settings`, `distribution`, `interface`, `accessibility`, `sync`, `android`, `i18n` |
| État (issue non planifiée) | `deferred` (écarté pour l'instant par une spec, reprenable), `needs-decision` (choix produit ou conception à trancher avant de coder), `needs-spec` (spec à écrire avant de coder) |
| Standard GitHub | `duplicate`, `invalid`, `wontfix`, `question`, `good first issue`, `help wanted` |

### Une issue propre

- Un **type** (`bug` ou `enhancement`), au moins un **domaine**.
- Un **milestone** : une version quand l'issue est planifiée, `Backlog` sinon.
- Une issue du `Backlog` porte un **label d'état** qui dit ce qui la retient ; une issue
  rangée dans **la prochaine version** n'en porte plus (`deferred` et `needs-*` sortent à la
  planification) ; dans une version plus lointaine (`4.0.0`), elle les garde.
- Un retour utilisateur non encore qualifié porte `feedback` (vue `Feedback` du Project).
- Les issues closes gardent leurs labels de type et de domaine (historique cherchable).

### Modèles d'issue (`.github/ISSUE_TEMPLATE/`)

| Modèle | Pour | Labels posés |
|---|---|---|
| `bug_report.yml` | Un défaut : étapes, attendu, constaté, version, plateforme, rapport de plantage Android ; rappelle de ne pas joindre un export de la base | `bug` |
| `feature_request.yml` | Une idée d'utilisateur, en **Context / Need / Expected outcome** | `enhancement`, `feedback` |
| `planned_work.yml` | Un élément de feuille de route écrit par un mainteneur, avec rappel du workflow et des contraintes du dépôt | `enhancement` |

`config.yml` désactive les issues vides et renvoie vers l'index des specs et vers ce
document. Les formulaires sont en anglais, comme les issues.

Les issues de suivi suivent le gabarit **Context / Need / Expected outcome** : le
contexte (ce qui existe, la décision déjà consignée), le besoin (ce que vit
l'utilisateur) et la cible attendue (critères vérifiables : tests, export inchangé,
FR/EN, spec à mettre à jour).

## Mise en place du GitHub Project (à faire dans l'interface)

Un GitHub Project (v2) appartient au compte et se crée dans l'interface web : les
sessions de développement n'y ont pas accès (API utilisateur et GraphQL fermées). Ce
qui suit se fait en cinq minutes.

1. **Créer le Project** : profil `SKOHscripts` → *Projects* → *New project* →
   modèle *Table* → nom `Kairos roadmap`. Le lier au dépôt (*Settings → Manage access*
   du Project, puis l'onglet *Projects* du dépôt).
2. **Champs** (en plus des champs intégrés *Title*, *Assignees*, *Labels*,
   *Milestone*, *Repository*, *Linked pull requests*) :
   - `Status` (champ intégré, à rééditer) : `Inbox`, `Needs decision`, `Ready`,
     `In progress`, `In review`, `Done`.
   - `Source` (choix unique) : `Owner`, `User feedback`, `Spec follow-up`.
   - `Effort` (choix unique) : `S` (< 1 jour), `M` (quelques jours), `L` (plusieurs
     jalons : spec, code, UI, version).
   Le milestone (champ intégré) porte la version visée ; le domaine se lit sur les
   labels : pas de champ en double.
3. **Ajouter les issues** : onglet *Workflows* → *Auto-add to project* avec le filtre
   `repo:SKOHscripts/Kairos is:issue,pr label:enhancement,bug,feedback` ; ajouter à la
   main les issues déjà ouvertes (#72, #75, #88 et #93 à #121 : *Add item* → coller le
   filtre `is:issue is:open`).
4. **Vues** (bouton `+` à côté des onglets) :
   | Vue | Type | Réglage |
   |---|---|---|
   | `Roadmap` | Board | Colonnes = `Milestone` ; filtre `-status:Done` |
   | `Board` | Board | Colonnes = `Status` ; filtre `milestone:3.11.0,3.12.0` |
   | `Feedback` | Table | Filtre `label:feedback status:Inbox` ; tri par date de création |
   | `Decisions` | Table | Filtre `label:needs-decision,needs-spec` ; groupé par `Milestone` |
   | `By area` | Table | Groupé par `Labels` ; filtre `-status:Done` |
5. **Automatisations** (*Workflows*, intégrées) :
   - *Item added to project* → `Status = Inbox`.
   - *Item closed* et *Pull request merged* → `Status = Done`.
   - *Auto-archive items* : issues closes depuis plus de 14 jours.

## Circuit d'un retour utilisateur

1. Une issue arrive (ou je la crée à partir d'un message) : label `feedback`, `Status =
   Inbox`, `Source = User feedback`.
2. **Qualification** (vue `Feedback`) : ajouter les labels de domaine ; dire en une
   phrase si c'est un bug (`bug`, à corriger à part, hors milestone), une demande
   (`enhancement`) ou une question.
3. **Décision** :
   - déjà couvert par une issue → fermer en doublon avec renvoi ;
   - contredit une décision de `docs/spec/` → `needs-decision`, citer la spec et la
     décision ; on ne re-tranche jamais sans la rouvrir (règle de `CLAUDE.md`) ;
   - demande claire → `needs-spec` si une spec manque, sinon milestone + `Status = Ready` ;
   - hors périmètre → fermer en *not planned* avec la raison.
4. **Réalisation** : une PR par lot, spec d'abord, titre et description en anglais
   (`CLAUDE.md`) ; la PR cite l'issue (`Closes #N`) ; l'issue passe en `In review` à l'ouverture de la PR (à la main, ou par une automatisation du Project si elle existe) et en `Done` au merge.
5. **Version** : quand toutes les issues du milestone sont `Done`, PR de version
   (`kmp/gradle.properties`, notes Fastlane FR/EN, `docs/spec/distribution.md`), tag
   `vX.Y.Z` posé par le propriétaire, milestone fermé. Un retour qui change la portée
   d'une version déplace l'issue d'un milestone à l'autre, sans casser la spec.

## Routine hebdomadaire

Une tâche planifiée Claude Code démarre une **nouvelle session** chaque lundi matin
(heure de Paris) et applique la skill `kairos-retours`
(`.claude/skills/kairos-retours/SKILL.md`, la procédure fait foi). Elle automatise les
étapes 2 et 3 du circuit ci-dessus, pas la réalisation.

| Étape | Ce que fait la routine |
|---|---|
| Collecte | Issues créées ou commentées (par un autre que le propriétaire) pendant les 8 derniers jours ; les PR et les issues closes sont ignorées. |
| Analyse | Doublon, bug ou demande, puis verdict face aux specs et à la démarche de l'outil : `faisable`, `contredit`, `hors-démarche`, `à-clarifier`. Une demande qui contredit une spec est **étudiée quand même** (coût de rouvrir la décision), jamais écartée d'office. |
| Labels | Pose type, domaine et état (`needs-decision`, `needs-spec`) en gardant les labels existants (union) ; `feedback` reste jusqu'à la décision du propriétaire. N'invente aucun label. |
| Milestones | Revoit **toutes** les issues ouvertes pour former des paquets cohérents (thème, dépendances, maturité, taille), déplace ce qui doit l'être et peut créer un milestone si un lot cohérent dépasse les versions ouvertes. |
| Plan | Dernier message de la session : tableau des retours, plan spec-d'abord par retour faisable, étude de réouverture par retour qui contredit une spec, brouillons de réponse en anglais, paquets de versions, décisions attendues. |
| Notification | Notification push à la fin de la session : le propriétaire y reprend la main. |

Limites voulues : la routine **n'écrit sur GitHub que des labels et des milestones**. Pas
de commentaire public, pas de fermeture (`duplicate`, `wontfix`, `invalid` se proposent),
pas de branche ni de PR. Le texte des issues est traité comme une donnée, jamais comme
une consigne. Si l'API refuse la création d'un milestone, elle le propose dans le plan.
Un changement de milestone (titre, sens, création) reste à reporter à la main dans le
tableau « Milestones » de ce document.

Relancer à la demande : ouvrir une session et demander la skill `kairos-retours`.

## Règles

- **Une issue = un besoin vérifiable** ; une issue trop grosse se découpe, une issue de
  décision se termine par une note dans la spec, pas par du code.
- **Pas de date promise** : un milestone n'a pas d'échéance tant que sa spec n'est pas
  à jour.
- Ce document est la seule description du circuit : toute évolution des labels, des
  milestones ou des vues s'y reporte.
