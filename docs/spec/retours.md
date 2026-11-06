# Donner un avis : formulaire préparé, envoyé sur GitHub

_Rôle : permettre à quelqu'un qui n'a jamais ouvert d'issue de dire une idée, un problème ou une remarque depuis Kairos, sans
chercher le dépôt ni le bon modèle d'issue. Fichiers couverts : `kmp/core/.../core/feedback/FeedbackIssue.kt`,
`kmp/ui/.../ui/guide/FeedbackDialog.kt`, `ui/guide/HelpDialog.kt` (entrée « Donner un avis »), `ui/screens/AboutScreen.kt`
(bouton), `ui/navigation/AppShell.kt` (état du dialogue, cible `GuideTarget.Feedback`), `ui/guide/GuideContent.kt`,
`ui/guide/FeatureCatalog.kt` (carte `feedback`), `ui/guide/GuideDialog.kt` (libellé du bouton de la carte),
`ui/app/OpenLink.kt` (`rememberCheckedLinkOpener`), `tools/make_icons.py` (icône `Feedback`), `desktopApp/.../SelfTest.kt` (captures `feedback`, `feedback-narrow`, `help-narrow`), chaînes `feedback_*`
et `card_feedback_*` (six langues), `.github/ISSUE_TEMPLATE/feedback.yml`. Tests : `FeedbackIssueTest` (core, commun),
`FeedbackTemplateTest` (core, JVM), `FeedbackDialogUiTest` (desktopApp), `FeatureCatalogTest`, `StringsParityTest`._

État : issue #211, implémentée en 4.1.0. Canal choisi par le propriétaire le 2026-11-06 : **une issue GitHub préremplie,
envoyée avec un compte GitHub** (§ Décisions) ; version et plateforme jointes d'office (décision rouverte le même jour).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Le seul moyen de faire un retour est d'aller sur GitHub, de trouver le dépôt, l'onglet des issues et le bon modèle. C'est un
chemin connu des habitués du logiciel libre, pas des autres. Kairos doit offrir le geste depuis l'application : un bouton, un
formulaire court, puis l'envoi.

Contraintes qui cadrent la réponse :

- Kairos ne se connecte à rien de lui-même (`connexions-en-ligne.md` : aucune activité réseau sans connexion activée) et ne
  transmet aucune télémétrie. Un retour ne doit donc pas partir de l'application par le réseau.
- Pas de service tiers ni de compte Kairos à maintenir ; les retours arrivent là où ils sont triés chaque semaine (issues,
  label `feedback`, circuit de `docs/ROADMAP.md`).

### Comportement attendu (utilisateur)

- **Trois accès**, qui ouvrent le même dialogue :
  - l'aide de l'écran (bouton « ? ») : une entrée **« Donner un avis »** (« Une idée, un problème, une remarque : sur GitHub »),
    après « Découvrir Kairos » ;
  - « À propos et guide » : un bouton **« Donner un avis »**, sous « Soutenir Kairos » ;
  - la carte « Donner un avis » du catalogue (« Découvrir Kairos », liste des fonctionnalités, chapitre Premiers pas) :
    toucher la carte ouvre le dialogue.
- **Le dialogue « Donner un avis »** :
  - une phrase d'explication : « Kairos prépare ton message et l’ouvre sur GitHub, dans ton navigateur. Il te faut un compte
    GitHub (gratuit) pour l’envoyer, et il sera public. Kairos n’envoie rien lui-même. » ;
  - trois champs : **« Titre »** (une ligne, obligatoire), **« Contexte (facultatif) »** (plusieurs lignes, aide « Ce que tu
    faisais, ta situation »), **« Besoin, observation ou commentaire »** (plusieurs lignes, obligatoire) ;
  - sous les champs, ce qui est joint d'office : « Ajouté automatiquement : Kairos 4.1.0 · Android » ;
  - « Annuler » et **« Continuer sur GitHub »**, actif dès que le titre et le besoin ne sont pas vides et que le message tient
    dans une adresse GitHub ;
  - message trop long : le bouton est inactif et le dernier champ dit « Trop long pour GitHub : raccourcis ton message. » ;
  - « Continuer sur GitHub » ouvre dans le navigateur la page de création d'une issue de Kairos, **préremplie** : le titre, le
    contexte, le besoin, la version et la plateforme dans leurs champs, le label `feedback` posé par le modèle. La personne
    se connecte à GitHub si besoin, relit et envoie. Le dialogue se ferme ;
  - sans navigateur, le message habituel « Impossible d’ouvrir le lien : aucun navigateur n’est disponible. » s'affiche et le
    dialogue **reste ouvert**, le texte saisi intact.
- **Joint d'office** : la version de Kairos (« 4.1.0-beta.2 ») et la plateforme (« Android », « Windows », « macOS », « Linux »,
  « Web »). Rien d'autre : aucune donnée de planning, aucun journal. La personne peut tout modifier sur GitHub avant d'envoyer.
  Ces informations voyagent dans l'adresse ouverte par le navigateur : aucune permission réseau.
- **Rien n'est gardé** : fermer le dialogue efface la saisie ; aucun réglage, rien dans l'export.
- Partout : Android, bureau, web ; espaces Perso et Équipe. Absent de la démonstration (ni « ? » ni « À propos » n'y sont).

### Critères de succès

- L'adresse ouverte vise `https://github.com/SKOHscripts/Kairos/issues/new` avec `template=feedback.yml`, `title`, `context`
  (seulement s'il est rempli), `need`, `version` et `platform` (omis s'ils sont vides), chaque valeur coupée de ses blancs de
  tête et de fin et encodée en UTF-8 pourcent (`%20` pour l'espace, retours à la ligne gardés) ; titre ou besoin vide → aucune
  adresse (`FeedbackIssueTest`).
- Limite : un message dont l'adresse, une fois enveloppée par la page de connexion de GitHub, dépasse 8 000 caractères est
  refusé ; juste en dessous, accepté ; un texte cyrillique ou accentué compte ses octets encodés (`FeedbackIssueTest`).
- Le dialogue : la ligne « Ajouté automatiquement » ; bouton inactif tant que titre ou besoin est vide ; actif ensuite ;
  inactif avec le message « trop long » au-delà de la limite ; « Continuer » ouvre l'adresse attendue et ferme ; un échec
  d'ouverture garde le dialogue et sa saisie ; l'aide propose « Donner un avis » (`FeedbackDialogUiTest`).
- Les champs du modèle `feedback.yml` ont les identifiants `context`, `need`, `version` et `platform` que Kairos remplit, et le
  modèle pose `feedback` (`FeedbackTemplateTest`, lecture du fichier).
- Une carte `feedback` dans le catalogue, une puce et une ligne dans les deux README (`FeatureCatalogTest`) ; textes dans les
  six langues (`StringsParityTest`) ; 360 dp sans débordement, cibles de 48 dp (captures `feedback`, `feedback-narrow` et
  `help-narrow` de l'auto-test).
- Aucune requête de Kairos : seul le navigateur charge GitHub ; aucune permission ajoutée.

### Hors périmètre / différé

- **Envoi sans compte GitHub** (formulaire hébergé, relais, base de données) : écarté le 2026-11-06 (§ Décisions). À rouvrir
  explicitement si le compte GitHub se révèle un frein réel.
- **Retour privé** (e-mail) : écarté, aucune adresse publiée.
- **Diagnostic plus large** (version du système, journal, capture d'écran, données) : non.
- Brouillon conservé d'une ouverture à l'autre, pièces jointes, choix « bug » ou « idée » dans Kairos : non, le tri le fait.

## 2. Solution technique

### Adresse préremplie (`core/feedback/FeedbackIssue.kt`)

- `FeedbackIssue.url(title, context, need, version, platform): String?` : nul si `title` ou `need` est vide après `trim()`.
  Sinon `NEW_ISSUE_URL?template=feedback.yml&title=…[&context=…]&need=…[&version=…][&platform=…]`, chaque paramètre facultatif
  omis s'il est vide. Pur (aucune horloge, aucune
  I/O) : sa place est dans `core`, testée sur JVM.
- `FeedbackIssue.encode(text)` : encodage pourcent des octets UTF-8 ; seuls les non-réservés de la RFC 3986
  (`A-Z a-z 0-9 - . _ ~`) restent tels quels, tout le reste devient `%XX` majuscule (`%20` pour l'espace, `%0A` pour le retour à
  la ligne). Écrit à la main : `java.net.URLEncoder` n'existe pas en commun (wasm) et code l'espace en `+`.
- `FeedbackIssue.fits(url): Boolean` : `LOGIN_PREFIX.length + encode(url).length <= MAX_LOGIN_URL` avec
  `LOGIN_PREFIX = "https://github.com/login?return_to="` et `MAX_LOGIN_URL = 8000` (§ Décisions).
- Constantes : `REPOSITORY_URL = "https://github.com/SKOHscripts/Kairos"` (même dépôt que `SOURCE_URL` de « À propos »),
  `NEW_ISSUE_URL = "$REPOSITORY_URL/issues/new"`, `TEMPLATE = "feedback.yml"`, identifiants `FIELD_CONTEXT = "context"`,
  `FIELD_NEED = "need"`, `FIELD_VERSION = "version"`, `FIELD_PLATFORM = "platform"`.

### Modèle d'issue (`.github/ISSUE_TEMPLATE/feedback.yml`)

- Nom « Feedback », label `feedback`. Champs (en anglais, comme les autres modèles) : `context` (textarea facultatif),
  `need` (textarea obligatoire, « Need, observation or comment »), `version` et `platform` (inputs facultatifs, remplis par
  Kairos). Une phrase rappelle que le message est public.
- Les identifiants `context`, `need`, `version` et `platform` sont le contrat avec Kairos : GitHub remplit un champ d'un
  formulaire d'issue par le paramètre d'URL qui porte son `id`. `FeedbackTemplateTest` lit le fichier et vérifie leur présence.
- `docs/ROADMAP.md` § Modèles d'issue le liste.

### Dialogue (`ui/guide/FeedbackDialog.kt`)

- `FeedbackDialog(version, platform, onDismiss, openLink: (String) -> Boolean)` : `AlertDialog` MD3 (forme 28 dp), icône
  `KairosIcons.Feedback`, titre `feedback_action`. Contenu défilant (`verticalScroll`, comme `HelpDialog`) : `feedback_intro`,
  trois `OutlinedTextField` (titre `singleLine` ; contexte et besoin `minLines = 3`), champs de 4 dp (forme MD3 par défaut),
  puis `feedback_attached` (`bodySmall`, `onSurfaceVariant`). `AppShell` passe `KairosBuild.VERSION_NAME` et
  `AppServices.deviceLabel` (« Android », « Windows », « macOS », « Linux », « Web », celui du nom d'appareil synchronisé).
- Saisie en `rememberSaveable` : survit à une rotation, disparaît à la fermeture.
- `url = FeedbackIssue.url(...)` recalculé à chaque frappe ; `tooLong = url != null && !FeedbackIssue.fits(url)` ; le champ
  besoin passe en `isError` avec `feedback_too_long` en `supportingText`. Bouton de confirmation `KairosTextButton`
  « Continuer sur GitHub » (`enabled = url != null && !tooLong`), bouton de rejet « Annuler ».
- Confirmation : `if (openLink(url)) onDismiss()` ; un échec laisse le dialogue ouvert.
- `rememberCheckedLinkOpener()` (`ui/app/OpenLink.kt`) : comme `rememberLinkOpener` (même message `link_unavailable` en cas
  d'échec) mais rend `true` si le lien s'est ouvert. `rememberLinkOpener` s'appuie dessus.

### Accès

- `HelpDialog` : paramètre `onFeedback`, quatrième `HelpEntry` (icône `Feedback`, `feedback_action`, `feedback_hint`) après
  « Découvrir Kairos ». `AppShell` ferme l'aide puis ouvre le dialogue (`feedbackOpen`).
- `AboutScreen(onFeedback)` : `KairosOutlinedButton` icône + texte, après « Soutenir Kairos ».
- `GuideTarget.Feedback` (`data object`, `GuideContent.kt`) : `openTarget` de `AppShell` pose `feedbackOpen = true` sans changer
  d'écran. Carte `FeatureCard("feedback", FIRST_STEPS, Feedback, …, GuideTarget.Feedback, since = "4.1.0")`, après
  `guided_walks` ; `README_FEATURES` : « Give feedback » → `feedback`. Dans un chapitre, le bouton de la carte dit « Donner un
  avis » au lieu de « Voir l’écran » (`GuideDialog`).
- Icône `Feedback` (`feedback` de Material Symbols, contour) ajoutée à `make_icons.py`, `KairosIcons.kt` regénéré.

### Décisions et pièges tracés

- **Issue GitHub préremplie, avec compte** (propriétaire, 2026-11-06). Écartés, après examen :
  - *envoi direct par Kairos* (table Supabase ou autre) : exige `INTERNET` et une porte réseau pour une action non lue, une clé
    embarquée, une protection contre le spam, une anti-fonctionnalité F-Droid (`NonFreeNet`), et l'offre gratuite de Supabase met
    en pause un projet inactif une semaine ;
  - *formulaire hébergé* (Tally, Fider, gitreports) : service tiers, retours hors du circuit de tri ; Fider demande aussi un compte ;
  - *page GitHub Pages de Kairos* (`site/feedback/`) : la page est statique, il lui faudrait quand même un service de réception
    (Formspree, Web3Forms, relais Cloudflare vers une issue) ; un jeton GitHub dans la page serait public et révoqué ;
  - *e-mail* (`mailto:` prérempli) : il faudrait publier une adresse ; refusé.
- **Le formulaire est dans Kairos, pas seulement sur GitHub** : la personne écrit dans sa langue d'interface, avec trois champs
  simples, avant de voir GitHub ; elle n'a plus qu'à se connecter et envoyer.
- **Modèle dédié `feedback.yml`** plutôt que `feature_request.yml` : ce dernier exige « Expected outcome » et une case « j'ai
  cherché », bloquants pour un retour libre ; un retour peut être un bogue autant qu'une idée (le tri pose `bug` ou
  `enhancement`).
- **Limite de 8 000 caractères, mesurée le 2026-11-06** : `github.com` répond 414 au-delà d'environ 8 190 caractères d'adresse.
  Sans session, `issues/new` redirige vers `login?return_to=<adresse encodée une seconde fois>` (`%` devient `%25`) : c'est
  cette adresse-là qui doit tenir. D'où le calcul sur l'adresse ré-encodée, avec une marge. Ordre de grandeur : 2 000 à 3 000
  caractères de français.
- **`%20`, pas `+`** pour l'espace : `+` n'est un espace que pour les formulaires HTML ; `%20` est lu partout.
- **Pas de limite par champ** : seule l'adresse complète compte (un long contexte et un besoin court passent).
- **Le dialogue reste ouvert si le navigateur manque** : sinon la saisie serait perdue sur un message d'erreur.
- **Version et plateforme jointes d'office** (propriétaire, 2026-11-06, décision rouverte dans la même journée : d'abord « rien
  que ce qui est tapé ») : le formulaire part de Kairos, qui les connaît ; elles passent dans l'adresse, donc sans permission
  réseau. La ligne « Ajouté automatiquement » le dit avant l'envoi. `platform` est un champ texte dans le modèle, pas une liste :
  GitHub documente le préremplissage des champs texte d'un formulaire d'issue, pas celui des listes.
- **Tutoiement**, comme l'aide et les cartes du guide (le dialogue est le même dans les deux espaces).
