# Connexions en ligne : lien ICS, CalDAV, Microsoft 365, Google, nouvelles versions

_Rôle : permettre à Kairos, sur demande et connexion par connexion, de lire un calendrier distant (lien ICS, serveur CalDAV,
Microsoft 365, Google Agenda) et, sur Android, de vérifier les nouvelles versions ; **rouvrir explicitement** la règle « l'APK
Android n'a aucune permission réseau » (`connexions-externes.md`, décision du 2026-10-24) en gardant l'application entièrement
utilisable hors ligne et sans aucune activité réseau tant que rien n'est activé. Fichiers couverts (prévus, aucun n'existe
encore) : `kmp/core/.../core/connections/` (`ConnectionKind.kt`, `SyncSchedule.kt`, `HttpPolicy.kt`, `CalDavXml.kt`,
`CalDavQueries.kt`, `GraphEvents.kt`, `GoogleEvents.kt`, `OAuthPkce.kt`, `Sha256.kt`, `Providers.kt`), `core/calendar/CalendarImport.kt`
(`SkipReason.DECLINED`), `core/updates/UpdateCheck.kt` (réutilisé) ; `kmp/data/` (`Kairos.sq`, `12.sqm`, `KairosStore.kt`,
`Mappers.kt`, `KairosRepository.syncCalendar` / `recordSyncFailure` / `disconnectCalendar`) ; `kmp/ui/.../ui/app/`
(`HttpFetcher.kt`, `SecretStore.kt`, `OnlineGate.kt`, `ConnectionSync.kt`, `ConnectionWatcher.kt`, `OAuthFlow.kt`, `Updates.kt`),
`ui/settings/OnlineConnections.kt` (carte « Connexions en ligne »), `ui/settings/SettingsScreen.kt` (carte « Mises à jour » d'Android) ;
`kmp/androidApp/` (`AndroidHttp.kt`, `AndroidSecrets.kt`, `SyncJobService.kt`, `MainActivity.kt` pour le retour OAuth,
`AndroidManifest.xml`, `res/xml/network_security_config.xml`, `res/xml/data_extraction_rules.xml`) ; `kmp/desktopApp/`
(`DesktopHttp.kt`, `DesktopSecrets.kt`, `LoopbackRedirect.kt`, module `jdk.httpserver` du runtime embarqué) ;
`kmp/webApp/.../kairos-web.js` (`fetchText`) ; `fdroid/com.skohscripts.kairos.yml`, `fastlane/metadata/android/`. Tests :
voir § Critères de succès._

État : décidée le 2026-10-26, **à implémenter (4.0.0)**. Issues #139 (connexion en ligne à un calendrier), #144 (vérification
des nouvelles versions sur Android), parent #140. Choix du propriétaire du 2026-10-26 : **un seul APK avec la permission
`INTERNET`** (celui de F-Droid et des releases GitHub), **toutes les fonctions en ligne désactivées par défaut**, activées une
par une dans les Réglages. Rien n'est encore écrit : le code et les autres specs décrivent la 3.x (sans `INTERNET`), voir
§ Impacts sur les specs existantes.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

La 3.10.0 a fait entrer les réunions dans Kairos par un fichier `.ics` (réimporté à la main) ou par les calendriers du
téléphone. Cela laisse deux manques, signalés par les utilisateurs (#139, #144) :

- **Rester à jour sans recommencer.** Un fichier exporté vieillit le jour même ; un calendrier du téléphone n'existe que sur
  Android et que si l'application de calendrier y copie les événements (Outlook pour Android le désactive par défaut, une
  organisation peut l'interdire). Sur le bureau, rien ne remplace le geste « exporter, importer ».
- **Savoir qu'une version est sortie.** Le bureau le dit ; Android seulement par un lien vers la page de téléchargement (3.9.0).

La seule réponse est que Kairos aille chercher l'information lui-même, donc ouvre le réseau. Cela change une **décision
fondatrice** : Kairos 3 s'est présenté comme « sans compte, sans réseau » (F-Droid, fiches, README), et la 3.10.0 a refusé la
permission réseau le 2026-10-24. Le propriétaire la rouvre le 2026-10-26, à trois conditions qui structurent toute la spec :

1. **Rien ne se connecte sans geste.** Chaque connexion est désactivée par défaut et s'active seule, après une phrase qui dit ce
   qui est envoyé et à qui.
2. **Kairos reste un outil hors ligne.** Aucune fonction ne dépend du réseau ; une connexion qui échoue laisse les événements tels
   que lus à la dernière synchronisation.
3. **Lecture seule, rien dans l'export.** Kairos ne modifie jamais le calendrier distant, ne crée aucun compte Kairos, ne
   transmet aucune télémétrie, et aucune donnée de connexion (adresse, identifiant, mot de passe, jeton) ne sort de l'appareil
   ni n'entre dans un export, une sauvegarde ou un paquet d'équipe.

### Comportement attendu (utilisateur)

#### Principes visibles

- **Aucune activité réseau tant qu'aucune connexion n'est active** : à l'installation, après une mise à jour, après un import
  de données. Une installation de la 4.0.0 qui n'ouvre pas les Réglages se comporte comme la 3.x.
- **Une permission d'installation, une garantie dans l'application.** La permission réseau d'Android est accordée à
  l'installation et ne se demande pas ensuite : le consentement se donne donc dans Kairos, connexion par connexion. Qui veut
  une garantie au niveau du système peut restreindre le réseau de l'application (pare-feu, réglage de son appareil).
- **Lecture seule.** Les événements arrivent comme créneaux occupés et badges de journée entière, exactement comme un fichier
  importé (`connexions-externes.md` : libres, annulés, passés écartés ; horizon de 6 mois ; titre, début, fin seulement, jamais
  la description, le lieu ni les participants ; « Masquer les titres »).
- **Hors ligne, tout marche.** Une connexion qui ne peut pas aboutir (pas de réseau, serveur en panne, mot de passe changé)
  n'ouvre aucune fenêtre bloquante : sous la connexion, une ligne dit ce qui s'est passé et depuis quand les événements datent.

#### Où l'on active quoi

- **Réglages → Données → carte « Connexions en ligne »**, après la carte « Calendriers » (celle de la 3.10.0). Phrase d'ouverture :
  « Kairos ne se connecte à rien tant que vous n’ajoutez pas de connexion. Chaque connexion est en lecture seule, et ses
  identifiants restent sur cet appareil. »
- Cinq façons d'ajouter une connexion. Chacune s'ouvre par un dialogue qui **dit ce qui est envoyé, à qui**, avec « Annuler » et
  un bouton d'activation ; rien n'est envoyé avant ce bouton.

| Connexion | Plateformes | Texte avant activation |
|---|---|---|
| **Lien de calendrier (ICS)** : Outlook (« Publier un calendrier »), Google (« adresse secrète au format iCal »), Nextcloud, Proton… | Android, bureau, web (au mieux) | « Kairos téléchargera ce lien à intervalles réguliers. Le serveur qui l’héberge voit votre adresse IP et le nom « Kairos » ; rien d’autre n’est envoyé. Ce lien donne accès à votre agenda : ne le partagez pas. » |
| **Serveur CalDAV** : Nextcloud, Radicale, SOGo, Synology… | Android, bureau | « Kairos se connectera à ce serveur avec cet identifiant et lira les calendriers que vous cochez. Le mot de passe est gardé chiffré sur cet appareil et n’est envoyé qu’à ce serveur. Utilisez de préférence un mot de passe d’application. » |
| **Microsoft 365 / Outlook.com** | Android, bureau | « Kairos ouvrira la page de connexion de Microsoft dans votre navigateur. Kairos ne voit jamais votre mot de passe ; il reçoit seulement l’autorisation de lire vos calendriers (« Calendars.Read », lecture seule). Microsoft sait que Kairos se connecte. » |
| **Google Agenda** | Android, bureau | Même forme, avec Google et les autorisations de lecture des agendas et de leurs événements. |
| **Nouvelles versions** (Android ; sur le bureau elle existe déjà) | Android, bureau | « Kairos demandera à GitHub quelle est la dernière version publiée. GitHub voit votre adresse IP et la version de Kairos ; rien d’autre n’est envoyé. » |

- **Nouvelles versions** : Réglages → Général → carte « Mises à jour » (là où le bureau l'a déjà), pas dans « Connexions en ligne »,
  dont une ligne renvoie à cet endroit. La web n'a aucune connexion de ce type (la page est toujours la dernière).
- **Web** : seul le lien ICS est proposé (voir § Solution technique, Web). CalDAV, Microsoft et Google n'y apparaissent pas.

#### Ajouter une connexion de calendrier

1. **Lien ICS** : coller l'adresse (`webcal://` accepté, lu comme `https://`). Un lien `http://` est refusé avec « Ce lien
   n’est pas chiffré (http). N’importez que ce que vous seriez prêt à voir lu par d’autres. » et un bouton « Utiliser quand même »
   (jamais proposé avec un identifiant, voir § Sécurité).
   **CalDAV** : adresse du serveur, identifiant, mot de passe ; Kairos liste les calendriers (nom), **aucun coché par défaut**.
   **Microsoft / Google** : « Se connecter » ouvre le navigateur du système ; au retour, la liste des calendriers du compte,
   **aucun coché par défaut**.
2. **Aperçu avant toute écriture**, le même que l'import d'un fichier (`CalendarImportDialog`) : nom du calendrier (prérempli
   par le nom du calendrier distant, modifiable), « Masquer les titres », « N créneaux du JJ/MM au JJ/MM », journées entières,
   événements écartés par raison (la raison « refusés » s'ajoute aux autres pour les connexions qui la connaissent). « Ajouter »
   et « Annuler ». **Une sauvegarde automatique précède l'ajout** (comme tout import) : sans elle, rien n'est écrit.
3. Le calendrier apparaît dans la liste de la carte, avec ses événements.

#### Une connexion au quotidien

- Chaque calendrier connecté montre : son nom ; « <hôte> · synchronisé le JJ/MM à HH:MM » (l'hôte seul, jamais l'adresse
  complète, qui peut contenir un secret) ; la fréquence ; l'erreur éventuelle.
- **Fréquence**, par calendrier : « À la main », « À l’ouverture » (**défaut**), « Toutes les heures », « Toutes les 6 heures »,
  « Une fois par jour ». « À l’ouverture » : au lancement et au retour dans l'application, au plus une fois tous les 15 minutes.
  Les trois fréquences régulières jouent aussi **application fermée sur Android** (tâche du système), et tant que l'application
  est ouverte sur le bureau et le web.
- Boutons : « Synchroniser maintenant » (passe outre la fréquence), un interrupteur « Synchroniser » (pause sans rien perdre),
  « Déconnecter » (garde les événements lus comme un calendrier importé, efface identifiants et lien), « Retirer » (efface tout, comme
  la carte « Calendriers » : confirmation « Retirer « Nom » et ses N créneaux ? »).
- **Synchroniser = réimporter** (`connexions-externes.md`) : les créneaux du calendrier **à partir d'aujourd'hui** et ses badges pas
  finis sont remplacés par ce que le serveur dit ; ce qui est passé reste (trace des journées). Les créneaux saisis à la main
  et les autres calendriers ne sont jamais touchés.
- Créneaux en lecture seule, sauf suppression, comme à l'import ; la confirmation dit « « Titre » reviendra à la prochaine
  synchronisation de « Nom » ».
- Les calendriers connectés comptent partout comme les calendriers importés (planning, vue Semaine et Mois, charge de « moi »,
  widgets et rappels de l'espace Perso). Un événement reçu à la fois par une connexion et par le téléphone n'apparaît qu'une
  fois (celui du téléphone, `connexions-externes.md`).

#### Hors ligne et erreurs

- L'application n'attend jamais le réseau : ouverture, planning et chrono ne dépendent d'aucune réponse.
- Une synchronisation qui échoue **ne change aucun événement**. Message sous la connexion, jamais une fenêtre (sauf « Synchroniser
  maintenant », qui répond par une barre de message) :

| Cause | Texte |
|---|---|
| Pas de réseau, hôte introuvable, délai dépassé | « Pas de connexion. Les événements datent du JJ/MM. » |
| Certificat refusé | « Le certificat de ce serveur n’est pas reconnu par votre appareil. » |
| Identifiants refusés (401, 403) | « Le serveur a refusé les identifiants. » (CalDAV : bouton « Modifier le mot de passe ») |
| Autorisation retirée ou expirée (OAuth `invalid_grant`) | « Reconnectez-vous à Microsoft (ou Google). » avec « Se reconnecter » |
| Lien disparu (404, 410) | « Ce lien n’existe plus. » |
| Le serveur répond autre chose qu’un calendrier | « Cette adresse ne renvoie pas un calendrier. » |
| Trop de requêtes (429), serveur en panne (5xx) | « Le serveur est indisponible. Nouvel essai plus tard. » |
| Redirection vers un autre serveur (lien ICS) | « Ce lien renvoie vers un autre serveur : <hôte>. » avec « Suivre » et « Annuler » |
| Identifiants absents de cet appareil (base copiée d'ailleurs) | « Les identifiants de cette connexion ne sont pas sur cet appareil. » avec « Se reconnecter » |

- Après un échec, les essais automatiques espacent : au moins 15 minutes, 6 heures après 5 échecs de suite, et jamais avant
  la date que le serveur demande (429 avec `Retry-After`, plafonnée à 24 heures). « Synchroniser maintenant » passe outre.

#### Nouvelles versions sur Android

- Concerne seulement un APK **installé à la main** (la définition de la 3.9.0, `mises-a-jour.md`). Installé par F-Droid ou un autre
  magasin, la carte n'apparaît pas et **rien ne se vérifie jamais** : le magasin s'en charge.
- Réglages → Général → « Mises à jour » : interrupteur **« Vérifier les nouvelles versions », désactivé par défaut** sur Android
  (actif par défaut sur le bureau, comme aujourd'hui). Texte d'activation : voir le tableau ci-dessus.
- **Désactivée** : la carte de la 3.9.0 (« Version installée », « Voir la dernière version »), dont l'aide devient « Par défaut,
  Kairos ne se connecte pas : la page de téléchargement dit quelle est la dernière version. »
- **Activée** : la carte devient celle du bureau (version installée, résultat de la dernière vérification, « Vérifier maintenant ») et
  « Voir la dernière version » disparaît ; le bandeau de nouvelle version du bureau apparaît (« Kairos X.Y.Z est disponible (tu as
  A.B.C). », « Télécharger », « Plus tard »), « Télécharger » ouvrant la page de téléchargement dans la langue de l'interface.
- La vérification a lieu à l'ouverture et tant que l'application est ouverte, au plus toutes les 6 heures, comme sur le bureau ;
  **jamais application fermée** (aucune tâche de fond, aucune notification).

### Contraintes

- `INTERNET` est la **seule** permission ajoutée. Aucune permission d'état du réseau, aucune alarme exacte, aucun service au premier plan.
- Aucun SDK propriétaire ni Google Play Services : les connexions Microsoft et Google passent par le **navigateur du système**
  et un flux OAuth 2.0 avec PKCE écrit dans Kairos.
- `core` reste pur : aucune requête, aucune horloge, aucun secret (`architecture.md`).
- Dépendances libres seulement ; **aucune dépendance ajoutée** (§ Décisions).
- **F-Droid** : `INTERNET`, comptes et OAuth ne sont pas interdits ; une anti-fonctionnalité **non déclarée** entraîne un refus
  (`connexions-externes.md` § Décisions). La 4.0.0 déclare `NonFreeNet` dès qu'elle embarque un connecteur dédié à un service
  non libre (Microsoft, Google). Voir § Solution technique, F-Droid.
- Mode solo et espace Équipe intacts (§ Isolation).

### Critères de succès

Chacun est prouvé par le test nommé (à écrire à l'implémentation ; la CI n'a **aucun accès réseau**, tout passe par un client simulé) :

- **Rien sans geste.** Sur une base neuve, sur une base migrée de la 3.x et après un import d'export (même un export de bureau dont
  `updateCheckEnabled` vaut vrai), le client simulé ne reçoit **aucune requête** (`NoConnectionNoRequestTest`). Seul le code de
  `ui/app/` et des modules de plateforme touche au réseau (`NetworkIsolationTest`, analyse des sources).
- **Politique d'adresse** : `https` exigé ; `http` refusé sauf lien ICS sans identifiant confirmé ; `webcal` lu comme `https` ;
  redirections : au plus 5, même hôte suivi, autre hôte demandé (ICS) ou refusé (CalDAV, OAuth), `https` vers `http` toujours refusé
  (`HttpPolicyTest`).
- **Lien ICS** : réponse 200 → plan identique à l'import du même texte ; 304 avec `ETag` → rien d'écrit ; `ETag` ignoré dès que le
  jour a changé ; corps qui n'est pas un calendrier ou tronqué (pas de `END:VCALENDAR`) → erreur, **rien d'écrit** ; plus de
  16 Mio → erreur (`ConnectionSyncTest`).
- **CalDAV** : découverte (`.well-known/caldav`, `current-user-principal`, `calendar-home-set`), liste des calendriers, `REPORT`
  `calendar-query` sur l'horizon, fusion des `VCALENDAR` en un seul texte lu par `CalendarImport.plan`, séries et exceptions
  comprises, sur des réponses réelles de Nextcloud, Radicale et SOGo recopiées en constantes (`CalDavXmlTest`, `CalDavSyncTest`) ;
  XML avec `DOCTYPE` ou entité refusé (`CalDavXmlTest`).
- **Microsoft** et **Google** : correspondance des événements des deux API vers le plan (libres, annulés, refusés écartés ; journée
  entière en badge ; provisoire occupé ; pagination ; `nextLink` vers un autre hôte refusé) sur des réponses recopiées
  (`GraphEventsTest`, `GoogleEventsTest`).
- **OAuth** : le vecteur de l'annexe B de la RFC 7636 (`code_challenge`) ; `state` incorrect, réponse sans code, retour d'une autre
  application refusés ; jeton rafraîchi une seule fois pour plusieurs synchronisations simultanées ; `invalid_grant` → état à
  reconnecter, aucune boucle (`OAuthPkceTest`, `Sha256Test`, `OAuthFlowTest`).
- **Échéancier** : « À l'ouverture » au plus tous les 15 minutes ; intervalles réguliers ; espacement après échecs et `Retry-After` ;
  connexion en pause ou « À la main » jamais due sans demande (`SyncScheduleTest`).
- **Données** : migration vers le schéma 13 sans créneau modifié ; **export d'une base sans connexion identique à l'octet près** ;
  un calendrier connecté s'exporte comme un calendrier importé (sans type, adresse, identifiant, jeton, ETag) et se réimporte comme tel ;
  aucune table ni colonne de connexion dans l'export ; secrets orphelins purgés au lancement ; connexion sans secret → état
  « identifiants absents », aucune requête (`ConnectionRepositoryTest`, `SecretStoreTest`).
- **Secrets** : aller-retour chiffré, altération détectée, absence de la clé = secrets absents (jamais une exception) ; fichier de
  secrets hors du dossier de données choisi par l'utilisateur (`SecretStoreTest`, `DesktopSecretsTest`).
- **Journal** : une synchronisation qui échoue n'écrit ni adresse, ni chemin, ni identifiant, ni jeton, ni titre (`ConnectionLogTest`).
- **Android** : le manifeste déclare exactement `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `READ_CALENDAR`, `INTERNET`
  (`ManifestPermissionsTest`, et `aapt2 dump permissions` sur l'APK de release en CI) ; trafic en clair autorisé par la configuration réseau mais
  gardé par `HttpPolicy` ; aucune autre permission.
- **Nouvelles versions Android** : désactivée par défaut ; absente et inerte pour une installation par un magasin même si le
  réglage est vrai ; la carte change à l'activation ; « Télécharger » ouvre la page de la langue ; l'état est indépendant de
  `Settings.updateCheckEnabled` (`UpdateCheckAndroidUiTest`, `InstallSourceTest`, `UpdateCheckTest` inchangés).
- **Interface** : dialogue d'activation (texte et bouton), aperçu et sauvegarde préalable, liste, fréquence, pause, déconnexion,
  retrait, erreurs sous la connexion (`OnlineConnectionsUiTest`, client et magasin de secrets simulés) ; textes dans les six langues
  (`StringsParityTest`) ; 360 dp sans débordement et cibles de 48 dp (captures `connections-*` de l'auto-test).
- **Isolation** : sur une base à gestion d'équipe activée, une synchronisation n'écrit aucune ligne `team_*` et ne change pas
  `personalSnapshot` hors des créneaux de son calendrier ; ni créneau synchronisé ni donnée de connexion dans un paquet ou un rapport
  d'équipe (`TeamIsolationTest` étendu, `ConnectionRepositoryTest`).
- **À vérifier à la main, sur appareil** (la CI ne voit ni un vrai fournisseur ni un vrai système) : un lien Outlook publié, un lien Google,
  un Nextcloud et un Radicale réels ; connexion Microsoft (compte personnel et compte d'organisation) et Google par le navigateur,
  retour dans Kairos, révocation côté fournisseur ; tâche de fond sur Android en veille profonde ; mode avion ; trousseau de chaque
  système de bureau ; installation par F-Droid et par APK à la main.

### Hors périmètre / différé

- **Écrire vers un calendrier distant** (créer une réunion, poser un créneau de deep work) : jamais dans cette spec.
- **Notification poussée** (push), webhooks, serveur Kairos, relais, compte Kairos, synchronisation des données entre appareils
  (#88, `export-import.md`) : aucun.
- **Télémétrie, statistiques d'usage, rapport de plantage distant** : aucun. Le projet ne reçoit rien.
- **Téléchargement ou installation automatique d'une version** : la vérification ne fait que signaler (`mises-a-jour.md`).
- **Garder une connexion d'un appareil à l'autre** (export, sauvegarde, dossier de données synchronisé) : une base copiée ailleurs
  retrouve ses calendriers mais pas leurs connexions, à refaire.
- **Identifiant d'application OAuth personnalisé** (organisation qui exige sa propre inscription) : piste, non retenue pour l'instant.
- **Autres fournisseurs OAuth** (Yahoo, Zoho, iCloud…), **CardDAV**, tâches et contacts : non.
- **Certificats d'autorité ajoutés par l'utilisateur ou l'entreprise** (inspection TLS), épinglage de certificat, option « ignorer
  l'erreur de certificat » : non (§ Décisions). Un serveur à autorité privée se rejoint par un lien ICS publié ou un fichier.
- **Version web de CalDAV, Microsoft et Google** (§ Décisions).
- Synchroniser plus souvent que toutes les heures, ou pousser sur changement du serveur.
- Convertir un créneau synchronisé en créneau manuel (comme pour l'import : piste notée).

## 2. Solution technique

### Permission, manifeste et réseau d'Android (`androidApp`)

- `AndroidManifest.xml` : `<uses-permission android:name="android.permission.INTERNET" />`, **seule** nouvelle permission ; le
  commentaire de tête du manifeste (qui dit aujourd'hui « Aucune permission réseau ») est réécrit en même temps (§ Impacts).
- `android:networkSecurityConfig="@xml/network_security_config"` : `base-config` avec `trust-anchors` = **`system` seulement**
  (jamais `user`), et `cleartextTrafficPermitted="true"`. Le clair est autorisé **au niveau du système parce que le système ne peut
  pas confirmer une adresse choisie à l'exécution** ; la garde est `HttpPolicy` (ci-dessous), seul chemin vers le réseau
  (`NetworkIsolationTest`). Alternative écartée : interdire `http` partout (le propriétaire veut pouvoir confirmer un lien clair).
- Sauvegarde automatique d'Android (`allowBackup="true"`, déjà) : `data_extraction_rules.xml` et `fullBackupContent` **excluent**
  les préférences `online-secrets` (un chiffrat restauré sur un autre appareil serait illisible : autant ne pas le copier).
- Retour OAuth : `intent-filter` (`VIEW`, `DEFAULT`, `BROWSABLE`) sur `MainActivity` pour le schéma de redirection de chaque
  fournisseur (`Providers.kt`, § OAuth). `MainActivity` est déjà `singleTop` : le retour arrive à `onNewIntent`.
- Tâche de fond : `SyncJobService` (`JobService`, `android:permission="android.permission.BIND_JOB_SERVICE"`, attribut du service, pas
  une permission demandée). `JobScheduler` plutôt que `WorkManager` (dépendance et fusion de permissions) ou `AlarmManager`
  (§ Décisions).

### Porte réseau unique (`ui/app/OnlineGate.kt`) et isolation du code réseau

- `HttpFetcher` (interface de `ui/app`, comme `FileService`) : `suspend fun fetch(request: HttpRequest): HttpResponse`, où
  `HttpRequest(method, url, headers, body, timeoutMillis)` et `HttpResponse(status, headers, body: ByteArray)` ; ne suit **aucune**
  redirection (la politique les traite), n'envoie aucun cookie et n'en garde aucun, décompresse `gzip`, plafonne le corps à
  `MAX_BODY_BYTES` = 16 Mio, délais 10 s de connexion et 30 s au total. Implémentations : Android `HttpURLConnection` (framework), bureau
  `java.net.http` (déjà au runtime, proxy du système, `bureau-avance.md`), web `fetch` (`kairos-web.js`), tests simulé.
- `OnlineGate` enveloppe un `HttpFetcher` : **toute** requête de l'application y passe. Il refuse (`GateClosed`, sans appel réseau) toute
  requête dont l'origine n'est pas une connexion **activée** (`ConnectionId` d'un calendrier synchronisé non en pause, ou la
  vérification des versions activée) ; il applique `HttpPolicy` (adresse, redirections, en-têtes) et le journal sans secret.
- `NetworkIsolationTest` (JVM, analyse des sources) : seuls `ui/app/` (`OnlineGate`, `ConnectionSync`, `OAuthFlow`, `Updates`) et
  les fichiers `*Http.kt` des applications importent `java.net.*`, `HttpURLConnection`, `java.net.http` ou `fetch`. `core`, `data` et
  le reste de `ui` n'y ont pas accès.

### Modèle (`core/connections`, `core/model`)

- `ConnectionKind` : `FILE` (calendrier importé, comportement de la 3.10.0, **défaut**), `URL`, `CALDAV`, `OAUTH` ; `OAuthProvider` :
  `MICROSOFT`, `GOOGLE`. `SyncInterval` : `MANUAL`, `ON_OPEN` (défaut), `HOURLY` (60 min), `SIX_HOURS` (360), `DAILY` (1 440).
- `CalendarSource` (3.10.0 : `id`, `name`, `hideTitles`, `importedAt`, `createdAt`) gagne, tous **non exportés**, avec valeurs par défaut
  telles qu'un calendrier importé ne change pas :

| Champ (colonne) | Rôle | Défaut |
|---|---|---|
| `kind` (`kind`) | `FILE`, `URL`, `CALDAV`, `OAUTH` | `FILE` |
| `provider` | `MICROSOFT` / `GOOGLE` pour `OAUTH` | nul |
| `host` | nom d'hôte affiché (jamais le chemin ni la requête) | nul |
| `account` | identifiant opaque du compte (CalDAV, OAuth) dans le magasin de secrets | nul |
| `syncEnabled` | interrupteur « Synchroniser » | vrai |
| `interval` | `SyncInterval` | `ON_OPEN` |
| `lastAttemptAt` / `lastSyncAt` | dernier essai / dernière réussite (instants UTC) | nul |
| `lastError` | `ConnectionError` du dernier essai, nul si réussi | nul |
| `failures`, `nextAttemptAt` | échecs de suite ; pas d'essai automatique avant | 0, nul |
| `etag`, `plannedOn` | `ETag` de la dernière réponse ; jour du plan qui l'a produite | nul |
| `insecureConfirmed`, `redirectHost` | lien `http` confirmé ; hôte de redirection accepté | faux, nul |

- **Adresse, identifiant, mot de passe, jetons, identifiant du calendrier distant : jamais dans la base.** Le lien ICS est un secret
  (les adresses « publiées » d'Outlook et « secrètes » de Google donnent l'agenda à qui les connaît) ; l'identifiant du calendrier CalDAV
  contient souvent le nom d'utilisateur. La base ne garde que l'hôte à afficher.
- `ConnectionError` : `OFFLINE`, `TLS`, `AUTH`, `REAUTH`, `GONE`, `NOT_CALENDAR`, `TRUNCATED`, `TOO_BIG`, `UNAVAILABLE` (5xx, 429),
  `REDIRECT_HOST`, `INSECURE_REDIRECT`, `NO_SECRET`, `UNSUPPORTED` (serveur CalDAV sans `calendar-query`). Chacun a son texte (§ Hors ligne).
- `SyncSchedule.due(source, maintenant, force)` (pur) : faux si `syncEnabled` est faux ou si le magasin de secrets n'a pas les
  identifiants ; vrai si `force` ; `MANUAL` jamais ; avant `nextAttemptAt` jamais ; `ON_OPEN` après 15 minutes depuis `lastAttemptAt` ;
  sinon `maintenant − lastAttemptAt ≥ interval`. `SyncSchedule.afterFailure(source, erreur, retryAfter, maintenant)` calcule `failures`
  et `nextAttemptAt` (≥ 15 minutes, 6 heures dès 5 échecs, `Retry-After` plafonné à 24 heures).
- `CalendarImport.plan` (3.10.0) gagne `SkipReason.DECLINED` (« refusés »), inutilisé par l'import de fichier (un export ne dit pas
  quel participant est l'utilisateur) et rempli par les API Microsoft et Google, qui le disent. `ExternalBlocks` (téléphone) écarte
  déjà les refusés ; l'import de fichier ne change pas.

### Stockage (`data`)

- Schéma 13 (`12.sqm`, additif ; marqueur de `KairosStore`) : les colonnes ci-dessus sur `calendar_source`, toutes `NULL` ou avec
  défaut. Les bases existantes : tous les calendriers restent `FILE`.
  Numérotation : `11.sqm` (schéma 12) est déjà pris par la migration de la couleur par défaut (`apparence.md`) ; `connexions-en-ligne.md` et `synchronisation.md` visent toutes deux `12.sqm` (schéma 13) : celle qui est implémentée en second prend le numéro suivant.
- `KairosRepository.syncCalendar(sourceId, plan, today, etag, now)` : **une transaction** qui remplace les créneaux de début
  ≥ `today 00:00` et les journées de fin ≥ `today` **de cette source seulement** (la même règle que `importCalendar`), insère le plan
  (titres vides si `hideTitles`), met `imported_at`, `last_sync_at`, `last_attempt_at`, `etag`, `planned_on`, `failures = 0`,
  `last_error = NULL`. `recordSyncFailure(sourceId, erreur, now, nextAttemptAt)` n'écrit **que** des métadonnées : aucun événement
  ne bouge. `disconnectCalendar(id)` remet `kind = FILE` et efface les champs de connexion, garde créneaux et journées.
  `removeCalendar(id)` (3.10.0) inchangée. La création d'une connexion passe par `importCalendar`/`syncCalendar` après la sauvegarde
  préalable de l'aperçu.
- Nom unique : une connexion dont le nom existe déjà reçoit « (2) », « (3)… » (l'identité d'un calendrier importé est son nom,
  `connexions-externes.md`).
- `ExportCodec` **ne change pas** : `calendarSources` écrit `id`, `name`, `hideTitles`, `importedAt` comme avant ; un calendrier
  connecté s'y trouve donc comme un calendrier importé (`kind` absent = `FILE` à la relecture). Le format n'est pas modifié,
  `formatVersion` non plus. **L'export d'une base sans connexion reste identique à l'octet près.** Les sauvegardes automatiques
  (`saveBackup`) passent par le même codec : une restauration rend les événements, pas les connexions.
- Importer un export (même celui d'un bureau) n'active rien : `Settings.updateCheckEnabled` (vrai par défaut, exporté) reste le réglage
  **du bureau**. Sur Android la vérification des versions a son propre état (ci-dessous).

### Secrets (`ui/app/SecretStore.kt` et plateformes)

- `SecretStore` : `get(key)`, `put(key, value)`, `delete(key)`, `keys()`, `level` (`HARDWARE`, `SYSTEM`, `FILE`, `NONE`, pour dire la
  vérité à l'utilisateur). Clés : `url:<idSource>` (lien ICS), `remote:<idSource>` (identifiant du calendrier distant), `account:<compte>`
  (JSON : serveur, identifiant, mot de passe CalDAV ; ou jetons OAuth : `access`, `refresh`, `expiresAt`). Une valeur illisible
  (clé perdue, fichier altéré) est **absente**, jamais une exception : la connexion passe en `NO_SECRET`.
- `OnlineConnections.purgeOrphans()` au lancement supprime les clés dont la source ou le compte n'existe plus ; « Retirer » et
  « Déconnecter » effacent les clés de la connexion (et le compte quand plus aucune source ne s'y rattache) avant de toucher la base.
- **Android** (`AndroidSecrets`) : clé AES-256 `AES/GCM/NoPadding` dans l'**Android Keystore** (alias `kairos-secrets`, non
  exportable), valeurs chiffrées (IV + texte chiffré en Base64) dans les préférences `online-secrets`. API du framework, aucune
  dépendance (`androidx.security:security-crypto`, dépréciée, écartée). Niveau `SYSTEM` (`HARDWARE` si le Keystore le dit).
- **Bureau** (`DesktopSecrets`) : valeurs chiffrées en AES-256-GCM dans `secrets.bin`, **dans le dossier utilisateur par défaut de Kairos
  (`platformdirs`), jamais dans le dossier de données choisi** (qui peut être synchronisé, `bureau-avance.md`) ni dans une copie
  portable. La clé est confiée, par ordre : Windows, DPAPI (`ProtectedData`, portée utilisateur, par PowerShell, entrée standard) ;
  macOS, trousseau de session (`security`, la clé passe brièvement en argument : limite connue) ; Linux, `secret-tool` (libsecret) s'il est
  présent ; sinon un fichier `secrets.key` aux droits `600` (niveau `FILE`, la carte dit « Identifiants protégés par les droits du fichier
  seulement. »). Aucune bibliothèque JNA ni trousseau tiers.
- **Web** : aucun magasin de secrets (`level = NONE`) ; seul le lien ICS est gardé (§ Web).

### Politique d'adresse et journal (`core/connections/HttpPolicy.kt`, pur)

- `HttpPolicy.check(url, kind, withCredentials, confirmedInsecure)` : schéma `https` ; `webcal` → `https` ; `http` accepté **seulement**
  pour un lien ICS sans identifiant et confirmé ; jamais d'identifiant, de jeton ou de Basic en `http` ; hôte non vide, pas
  d'identifiant dans l'adresse (`https://user:pass@…` refusé) ; port 1-65535.
- Redirections traitées par `OnlineGate`, jamais par la bibliothèque (`HttpFetcher` n'en suit aucune) : au plus 5 ; même hôte (ou
  `http` → `https` du même hôte) suivie ; **autre hôte** : lien ICS → `REDIRECT_HOST` et demande à l'utilisateur, le choix retenu dans
  `redirectHost` ; CalDAV, OAuth, vérification des versions → refusée ; `https` → `http` toujours refusée (`INSECURE_REDIRECT`).
  L'en-tête `Authorization` n'est envoyé qu'à l'hôte configuré, jamais après une redirection.
- En-têtes : `User-Agent: Kairos` pour les calendriers (sans version : rien à pister), `Kairos/<version>` pour GitHub (déjà ainsi sur
  le bureau, GitHub l'exige), `Accept-Encoding: gzip`, `If-None-Match` si un `ETag` existe et que le plan est du jour.
- **Journal** (le journal du bureau, `bureau-avance.md`, et le journal Android s'il existe) : hôte, méthode, code de statut, durée, taille.
  **Jamais** le chemin ni la requête de l'adresse, un en-tête, un corps, un jeton, un identifiant, un titre d'événement. Les messages
  d'erreur montrés à l'utilisateur citent l'hôte seulement.

### Lien ICS

- `ConnectionSync.sync(source)` : lit `url:<id>`, `GET` ; 200 → `IcsParser.isCalendar` et présence de `END:VCALENDAR`, sinon `NOT_CALENDAR`
  ou `TRUNCATED` ; `CalendarImport.plan(texte, aujourd'hui, fuseau)` (hors du fil d'interface, `Dispatchers.Default`) puis
  `syncCalendar` ; 304 (même jour) → seules `last_attempt_at` et `last_sync_at` avancent ; 404/410 → `GONE` ; 401/403 → `AUTH` (le lien est
  un secret : il ne se « reconnecte » pas, on le remplace) ; 429/5xx → `UNAVAILABLE`. **Le corps n'est jamais conservé**, seul le plan l'est.
- `ETag` : le plan dépend du jour (horizon de 6 mois glissant) : un 304 d'un jour antérieur n'est donc pas pris, la requête repart sans
  `If-None-Match` au moins une fois par jour.
- Nom proposé : `X-WR-CALNAME`, sinon l'hôte.

### CalDAV (`core/connections/CalDav*.kt`, pur ; requêtes par `ConnectionSync`)

- Authentification `Basic`, en `https` seulement. Adresse saisie → découverte : `PROPFIND` de l'adresse (`/.well-known/caldav` en
  repli) pour `current-user-principal`, puis `calendar-home-set`, puis `PROPFIND` profondeur 1 des calendriers (`resourcetype` contenant
  `calendar`, `displayname`, `supported-calendar-component-set` contenant `VEVENT`). `remote:<id>` = chemin du calendrier choisi.
- Synchronisation : `REPORT` `calendar-query` filtré `VEVENT` sur `[aujourd'hui − 1 jour, aujourd'hui + 6 mois[`, avec `calendar-data` brut
  **sans** `expand` (tous les serveurs ne l'offrent pas ; Kairos déploie lui-même les répétitions, `IcsRule`). `CalDavQueries` écrit les
  corps de requête, `CalDavXml.parseMultistatus` en lit les réponses.
  `CalDavMerge.merge(objets)` rassemble les `VCALENDAR` en **un seul** texte (les `VTIMEZONE` conservés une fois par `TZID`), lu ensuite par
  `CalendarImport.plan` comme n'importe quel fichier : une seule logique d'événements pour fichier, lien et CalDAV.
- Lecteur XML **maison**, minimal (`CalDavXml`) : éléments, espaces de noms, texte, `CDATA`, entités prédéfinies ; refuse `<!DOCTYPE`,
  entités déclarées et profondeur > 32 (aucune lecture externe, pas de XXE ni d'explosion d'entités). Aucun analyseur de plateforme n'existe
  en wasm ; le même code sert les tests de `core` sur toutes les cibles.
- Pas de `sync-collection` ni de CTag en première version : une requête par synchronisation et par calendrier suffit à l'échelle d'un
  agenda personnel ; `etag` ne sert pas ici.
- Limite connue : un serveur sans `calendar-query` → `UNSUPPORTED`, avec le conseil du lien ICS.

### Microsoft 365 et Google par OAuth 2.0 (`OAuthFlow`, `OAuthPkce`, `Providers`)

- **Flux** (RFC 7636, RFC 8252) : Kairos génère un `code_verifier` (32 octets aléatoires fournis en paramètre à `core`, 43 caractères
  `base64url`), le `code_challenge` (`S256`, `Sha256` en Kotlin pur) et un `state` (16 octets) ; ouvre l'adresse d'autorisation dans le
  **navigateur du système** (`ACTION_VIEW` sur Android, `LocalUriHandler` sur le bureau ; **jamais une `WebView`**) ; reçoit le code à
  l'adresse de redirection ; vérifie `state` (une seule fois, durée de vie 10 minutes) ; échange le code au point de terminaison de
  jeton avec le `code_verifier` ; garde jetons et expiration dans `account:<compte>`. Les jetons d'accès ne sont envoyés qu'aux hôtes
  fixes de `Providers.kt` ; toute `nextLink`/`nextPageToken` qui sort de cet hôte est refusée.
- **Retour** : Android, schéma propre à l'application (`MainActivity.onNewIntent`) ; bureau, **boucle locale** (`127.0.0.1`, port libre,
  `com.sun.net.httpserver`, module `jdk.httpserver` ajouté au runtime) ouverte **le temps de la connexion seulement** (5 minutes au
  plus, une requête, chemin et `state` vérifiés, page de fin dans la langue de l'interface). L'instance unique de la 3.x n'ouvre aucun
  port (`distribution.md`) ; cette boucle est la seule exception, et ne vit que pendant une connexion.
- **Autorisations minimales, lecture seule, aucun jeton d'identité** (`openid` non demandé) :

| | Microsoft | Google |
|---|---|---|
| Autorité | `https://login.microsoftonline.com/common/oauth2/v2.0/` (comptes d'organisation et personnels) | `https://accounts.google.com/o/oauth2/v2/auth`, jeton `https://oauth2.googleapis.com/token` |
| Portées | `Calendars.Read`, `offline_access` | `calendar.calendarlist.readonly`, `calendar.events.readonly` |
| Liste des calendriers | `GET /me/calendars` | `GET /calendar/v3/users/me/calendarList` |
| Événements | `GET /me/calendars/{id}/calendarView?startDateTime=&endDateTime=` ; `$select` limité à identifiant, `iCalUId`, sujet, début, fin, journée entière, annulé, disponibilité, réponse ; `Prefer: outlook.timezone="UTC"` ; pagination `@odata.nextLink` | `GET /calendar/v3/calendars/{id}/events?singleEvents=true&timeMin=&timeMax=&orderBy=startTime` ; `fields` limité à identifiant, `iCalUID`, résumé, début, fin, état, transparence, type d'événement ; pagination `nextPageToken` |
| Libre | `showAs` = `free` ou `workingElsewhere` | `transparency` = `transparent` ; `eventType` = `workingLocation` |
| Écarté | `isCancelled`, réponse `declined` | `status` = `cancelled`, participant « moi » à `declined` |

  Les deux API **déploient déjà les répétitions** : `GraphEvents` et `GoogleEvents` (purs) produisent directement un `CalendarPlan`
  (mêmes `PlannedEvent`/`PlannedDay`, mêmes raisons d'écart, `SkipReason.DECLINED` en plus), découpé à minuit par
  `CalendarImport.splitAtMidnight`, sans passer par `IcsRule`. `remote:<id>` = identifiant du calendrier. L'identifiant de calendrier
  coché et le compte vivent dans le magasin de secrets.
- **Jeton expiré** : rafraîchi avant la requête (60 s de marge), **un seul rafraîchissement à la fois** par compte (verrou), puis la
  requête rejouée une fois ; `invalid_grant` ou refus de rafraîchir → `REAUTH`, plus aucune requête automatique jusqu'à « Se reconnecter ».
- **Retrait** : les jetons locaux sont effacés ; Google est prévenu au mieux (`/revoke`, sans bloquer) ; Microsoft n'a pas de point de
  révocation public pour ce flux : la carte renvoie, dans son aide, vers la page des applications du compte pour révoquer.
- **Ce qu'il faut côté fournisseur** (à faire par le propriétaire avant d'embarquer le connecteur ; les identifiants de client sont **publics**
  et vivent dans `Providers.kt`, jamais de secret de client dans le dépôt) :
  - **Microsoft** : une inscription d'application dans Microsoft Entra (gratuite), « comptes de n'importe quel annuaire
    d'organisation et comptes Microsoft personnels », plateforme « Applications mobiles et de bureau » avec les adresses de redirection
    (schéma propre à l'application pour Android, `http://localhost` pour le bureau), « Autoriser les flux de client public » = oui,
    autorisations déléguées `Calendars.Read` et `offline_access`. **Contrainte réelle** : sans éditeur vérifié (programme partenaire et
    domaine vérifié), l'écran de consentement affiche « non vérifié », et **beaucoup d'organisations bloquent le consentement
    des utilisateurs** (« Need admin approval ») ; un administrateur peut approuver l'application par son identifiant de client.
    Ce que la 3.10.0 avait déjà constaté (`connexions-externes.md`) reste vrai : là où le consentement est bloqué, la connexion Microsoft
    échoue, et le lien ICS publié ou le fichier restent la voie.
  - **Google** : un projet Cloud, un écran de consentement OAuth, les deux portées ci-dessus (portées « sensibles » : **vérification
    de l'application** par Google, avec page d'accueil et politique de confidentialité sur un domaine vérifié ; tant qu'elle n'est pas faite,
    l'application reste en « test » : 100 utilisateurs de test au plus, jetons de rafraîchissement expirant en 7 jours, écran
    « application non vérifiée »). Types de client : Android (nom de paquet + empreinte SHA-1 du certificat de signature) et
    ordinateur (boucle locale ; le « secret » de ce type est public, sans valeur). Le détail des adresses de redirection admises par Google pour
    le type Android et la classification actuelle des portées sont **à confirmer à l'inscription**.
  - Tant que l'inscription d'un fournisseur n'est pas faite, son connecteur n'est **ni compilé ni proposé** (identifiant de client vide =
    section absente) ; il n'y a alors pas de `NonFreeNet`.

### Synchronisation (`ConnectionSync`, `ConnectionWatcher`, tâche de fond)

- `ConnectionSync` (suspendu, `ui/app`) : `run(source, force)` — une synchronisation à la fois (verrou), 60 s au plus par source ;
  lit les secrets, appelle `OnlineGate`, calcule le plan hors du fil d'interface, écrit par `syncCalendar` ou note l'échec par
  `recordSyncFailure`. `runDue(now)` parcourt les sources dues, **séquentiellement**.
- `ConnectionWatcher` (coquille, comme `UpdateWatcher`) : au lancement, au retour dans l'application (`onResume`, reprise de la fenêtre) et
  toutes les 30 minutes tant que l'application est ouverte : `runDue`. L'horloge est celle des services (testable).
- **Android, application fermée** : `ConnectionScheduler.apply(sources)` pose, à chaque changement des connexions, **une seule**
  `JobInfo` périodique (`setPeriodic`, période = la plus courte des fréquences régulières actives, 60 minutes au moins, `setPersisted(true)` — il
  profite de `RECEIVE_BOOT_COMPLETED`, déjà déclarée —, `setRequiresBatteryNotLow(true)`), ou l'annule s'il n'y en a aucune. **Aucune
  contrainte de réseau du planificateur** (elle demanderait vraisemblablement `ACCESS_NETWORK_STATE`, à vérifier à l'implémentation) :
  une tentative hors ligne échoue vite et sera rejouée. `SyncJobService.onStartJob` ouvre les services par `KairosProcess` (base ouverte
  une fois par processus, comme `ChronoReceiver`) et lance `runDue`. Aucune alarme exacte, aucun service au premier plan, aucune notification.
  Le widget (`widgets-android.md`) et les rappels (`rappels.md`) lisent `personalSnapshot` : ils suivent la synchronisation sans rien ajouter.
- **Bureau et web** : seulement `ConnectionWatcher`, application ouverte. Le bureau ne lance aucun processus de fond.
- **Un échec ne bloque jamais** : l'écran n'attend pas ; l'erreur est écrite en base et lue par la carte.
- Pas de sauvegarde à chaque synchronisation (§ Décisions) ; la première, à l'ajout, a la sienne.

### Nouvelles versions sur Android (`Updates.kt`, `SettingsScreen.kt`)

- Réutilise sans le changer `UpdateCheck` (URL de l'API de GitHub, `parseLatest`, `isNewer`, `due` à 6 heures, `downloadPage`),
  `UpdateWatcher` et `UpdateBanner` ; `AndroidUpdates` est l'équivalent de `DesktopUpdates` (état dans `updates.json` du dossier de
  données Android, GET par `OnlineGate`).
- État **propre à Android** : préférence `online-updates` des préférences de l'application (comme `phone-calendars`), **jamais**
  `Settings.updateCheckEnabled` : celui-ci vaut vrai par défaut, voyage dans l'export et activerait le réseau en important l'export d'un bureau.
  Aujourd'hui `UpdateWatcher` lit `settings.updateCheckEnabled` et `UpdatesCard` l'écrit par le formulaire des Réglages ; à l'implémentation,
  `UpdateService` expose `enabled` et `setEnabled` : le service du bureau les adosse à `Settings.updateCheckEnabled` (comportement inchangé), celui
  d'Android à la préférence ; `UpdateWatcher` et `UpdatesCard` n'utilisent plus que le service. Sur Android, `UpdatesCard` n'inscrit donc rien
  dans le formulaire et l'interrupteur agit tout de suite (pas de « Enregistrer »).
- Garde : `AppServices.downloadPage` (installation à la main, 3.9.0) est **condition** de l'existence du service (`AppServices.updates` non
  nul) ; installé par un magasin, `AppServices.updates` reste nul et aucune requête n'est possible (`OnlineGate` ne connaît pas cette
  origine). `DownloadPageCard` (3.9.0) n'est plus montrée que si le service existe et que l'interrupteur est éteint ; sinon `UpdatesCard`.
- Pas de tâche de fond ni de notification pour cette fonction.

### Web (`kairos-web.js`, `WebServices`)

- Seul le **lien ICS** est proposé, via `fetch` : il marche si le serveur envoie `Access-Control-Allow-Origin`, ce que ni Outlook, ni Google, ni
  la plupart des serveurs de calendrier ne font toujours ; sinon `TypeError`, que le navigateur ne distingue pas d'une panne de réseau. Message :
  « Le navigateur n’a pas pu lire ce lien (hors ligne, ou serveur qui n’autorise pas les pages web). » L'aide de la section le dit **avant**
  l'ajout. Pas d'`If-None-Match` (il déclencherait une requête préliminaire inutile).
- Le lien est gardé dans IndexedDB, avec la poignée du fichier lié (`export-import.md`) : c'est l'unique donnée de connexion que le web
  garde, et l'aide le dit (« Cette adresse est gardée dans ce navigateur. »). **Aucun identifiant, mot de passe ni jeton n'est jamais gardé
  sur le web.**
- **CalDAV et OAuth n'y sont pas proposés**, pour trois raisons : (1) aucun magasin de secrets (tout script de l'origine lit IndexedDB et
  `localStorage`, un mot de passe ou un jeton de rafraîchissement y serait lisible) ; (2) les serveurs CalDAV n'envoient en général pas les
  en-têtes CORS et exigent des requêtes préliminaires pour `PROPFIND`/`REPORT` ; (3) un flux OAuth de page web demande une inscription
  « application monopage » distincte et des jetons de rafraîchissement à vie courte. La version web reste le recours du poste verrouillé :
  les fichiers `.ics` y fonctionnent.
- La vérification des versions n'existe pas (`mises-a-jour.md`, « sans objet »).

### Interface (`ui/settings/OnlineConnections.kt`, `kairos-ecran`)

- `OnlineConnectionsCard` (catégorie Données, après `CalendarsCard`) : phrase d'ouverture ; « Ajouter une connexion » (menu des connexions
  disponibles sur la plateforme) ; liste des calendriers connectés (`CalendarSource.kind ≠ FILE`) ; ligne d'état de chaque calendrier ; renvoi
  « La vérification des nouvelles versions se règle dans Général → Mises à jour. » (Android et bureau).
- Dialogues : activation (le texte du tableau ci-dessus, `AlertDialog`), saisie (champs : `OutlinedTextField`, mot de passe masqué,
  `KeyboardType.Password`), choix des calendriers (cases, aucune cochée), aperçu (`CalendarImportDialog` étendu, bouton « Ajouter »), erreurs.
- Couleurs par `MaterialTheme.colorScheme` seulement ; l'erreur est une ligne de texte avec icône (`Warning`, ajoutée à `make_icons.py` si absente),
  **pas** de conteneur d'erreur : ce sont des incidents attendus, pas des échecs de l'application (`DESIGN_SYSTEM.md`). Cibles de 48 dp.
- Carte du guide `online_connections` (chapitre Organiser, `since = "4.0.0"`, ouvre Réglages → Données) ; carte `FeatureCatalog` et
  `README_FEATURES` à l'implémentation (`FeatureCatalogTest.readmeFeaturesAreCovered`).
- Textes dans les six langues (`values*/strings.xml`), apostrophe typographique `’`.

### Isolation du mode solo et de l'espace Équipe

- **Mode solo** : tant qu'aucune connexion n'est ajoutée, aucune ligne de connexion n'existe, aucune tâche de fond n'est planifiée,
  `ConnectionWatcher` ne trouve rien de dû, l'export est identique à l'octet près. `Settings` n'a aucun champ nouveau.
- **Espace Équipe** : les créneaux synchronisés sont des créneaux importés de l'espace Perso (`personalView`, `equipe.md`) ; la synchronisation
  n'écrit que `calendar_source`, `time_block`, `day_mark`. Elle ne lit ni n'écrit aucune table `team_*`, ne passe par aucun filtre d'équipe et
  ne voit pas `teamView`. Aucune adresse, aucun hôte, aucun nom de calendrier connecté, aucun secret n'entre dans un paquet, un rapport ou une
  sauvegarde d'équipe (`equipe-echanges.md`) ; la charge de « moi » compte les créneaux synchronisés comme les créneaux importés
  (`connexions-externes.md`). Aucun collègue ne reçoit rien.

### F-Droid, distribution et publication

- **Un seul APK** (`distribution.md`) : celui de F-Droid et des releases GitHub, même signature, reproductible (`publication.md`). `INTERNET` n'est
  pas conditionné par une variante ou un drapeau de construction. Les identifiants de client OAuth sont des constantes publiques du code source.
- **Anti-fonctionnalités** : l'étiquette est **par application**, pas par fonction. Dès que le code d'un connecteur dédié à un service non
  libre (Microsoft, Google) est dans l'APK, **tous** les utilisateurs de la fiche F-Droid la voient, même s'ils n'activent rien. Donc :
  - `NonFreeNet` est **déclaré** dans la recette (`AntiFeatures: NonFreeNet`, description anglaise : « Optional, off-by-default connectors
    to Microsoft 365 and Google Calendar ») dans la **version qui embarque le premier de ces connecteurs**, et pas avant ;
  - le lien ICS et CalDAV sont des protocoles génériques : aucune étiquette à eux seuls ;
  - la vérification des versions est **désactivée par défaut** et **absente d'une installation par F-Droid** : pas d'étiquette `Tracking` (qui
    vise un appel réseau sans accord ou actif par défaut) ; une relecture de fdroiddata qui en jugerait autrement se traite en déclarant
    l'étiquette, **jamais en la taisant** ;
  - aucune étiquette n'est omise « pour ne pas faire peur » : la règle de `connexions-externes.md` (« une anti-fonctionnalité non déclarée
    entraîne un refus ») s'applique à chaque version.
  `fdroid_check.sh` fait passer `fdroid lint` et `rewritemeta` sur la recette canonique avec l'étiquette ; la fiche Fastlane et la page
  de téléchargement cessent de dire « aucune permission réseau » (§ Impacts).
- **Jalons** (dans la 4.0.0) : O1 socle (permission, `OnlineGate`, secrets, schéma 13) + lien ICS + nouvelles versions d'Android ; O2 CalDAV ;
  O3 Microsoft ; O4 Google. O3 et O4 attendent l'inscription du fournisseur (ci-dessus) : si elle n'est pas prête, la 4.0.0 sort avec O1 et O2
  (sans `NonFreeNet`) et O3/O4 suivent en 4.1.0 ; la permission, elle, ne reviendra jamais en arrière dans une version mineure.
- **Version** : prévue `4.0.0` (`kairos.versionCode` 40000, formule de `distribution.md`) : elle change une décision fondatrice. Note de version
  Fastlane `40000.txt` dans les six langues, qui **dit** l'ajout de la permission réseau et que tout est désactivé par défaut. README anglais et
  français (feature) à l'implémentation.

### Décisions et pièges tracés

- **Rouvrir « sans permission réseau »** (propriétaire, 2026-10-26, #139, #144) : remplace la décision du 2026-10-24 de
  `connexions-externes.md` ; contreparties acceptées : la fiche F-Droid et les textes de publication ne disent plus « aucune permission
  réseau », `NonFreeNet` apparaît avec les connecteurs Microsoft et Google, la 4.0.0 est une version majeure. Le calendrier du téléphone (3.10.0)
  et le fichier `.ics` restent, hors ligne, la voie de qui ne veut pas du réseau.
- **Un seul APK avec `INTERNET`**, plutôt que deux APK (un « sans réseau », un « en ligne »). Écarté : deux identifiants d'application ou deux
  signatures, donc des données non partagées, une fiche F-Droid double, une reproductibilité à prouver deux fois, et des utilisateurs qui ne savent
  pas lequel prendre. Le prix est dit plus haut : une permission au lieu de zéro.
- **`INTERNET` demandée à l'exécution : impossible sur Android.** C'est une permission « normale », accordée à l'installation, qu'on ne peut ni
  demander ni refuser ensuite. Le consentement est donc dans Kairos (interrupteurs, dialogue qui dit ce qui est envoyé), et la garantie est
  dans le code (`OnlineGate`, `NoConnectionNoRequestTest`, `NetworkIsolationTest`), pas dans le système.
- **Désactivé par défaut, y compris sur le bureau pour les calendriers** ; seule la vérification des versions du bureau reste active par défaut
  (acquise depuis M5), et son interrupteur d'Android part désactivé : l'asymétrie est voulue.
- **Réglage d'Android à part de `Settings.updateCheckEnabled`.** Ce champ est exporté, vrai par défaut et propre au bureau : l'utiliser aurait
  activé le réseau en important un export. Les préférences de l'application (`phone-calendars` en est l'exemple) ne voyagent pas.
- **Pas de `ACCESS_NETWORK_STATE`, pas de contrainte de réseau du planificateur** : une seule permission en plus. Une tentative hors ligne échoue
  sans coût et reprend plus tard.
- **`JobScheduler`, pas `WorkManager`, pas `AlarmManager`.** `WorkManager` ajoute une dépendance et fusionne ses propres permissions dans le manifeste ;
  `AlarmManager` sans permission exacte (`rappels.md`) fonctionnerait mais réveillerait le téléphone sans condition. `JobScheduler` est le
  mécanisme du système pour un travail périodique, sans permission exacte, avec reprise au redémarrage déjà couverte.
- **Pas de Ktor, pas d'OkHttp, pas d'AppAuth, pas de bibliothèque XML.** `HttpURLConnection` (Android) et `java.net.http` (bureau, déjà au
  runtime depuis M5) font ce qu'il faut (`GET`, `POST`, et `PROPFIND`/`REPORT` sous réserve du piège ci-dessous pour Android) ; Ktor serait une dépendance de plus (graphe de construction
  reproductible, taille de l'APK de 2 Mo) pour un code partagé que l'interface `HttpFetcher` donne déjà ; AppAuth, libre, tire des
  dépendances pour un flux d'une page de code qu'on teste mieux chez soi ; un lecteur XML maison de quelques dizaines de lignes est le seul
  qui marche en wasm et sans XXE. Piège connu, **à trancher à l'implémentation de O2 par un essai réel** (Android 8 à 16) : `HttpURLConnection` d'Android
  peut refuser les méthodes `PROPFIND` et `REPORT` (`setRequestMethod` n'accepte que des méthodes standard). Ordre de repli : (1) la méthode
  passe telle quelle ; (2) une écriture HTTP/1.1 minimale sur la `SSLSocket` du système (certificats du système, mêmes règles `HttpPolicy`), dans
  `AndroidHttp.kt` seulement ; (3) ajouter OkHttp (libre, Apache 2.0, Maven Central) : **décision du propriétaire**, car dépendance nouvelle. Écarté :
  la réflexion sur le champ `method` (restrictions d'API cachées) et `X-HTTP-Method-Override` (non pris en charge par Radicale). Le bureau
  (`java.net.http`) accepte ces méthodes.
- **Lien ICS et URL de calendrier = secrets.** Ils donnent l'agenda à qui les a ; ils sont chiffrés comme un mot de passe, absents de la base,
  de l'export, des journaux et de l'affichage (hôte seul).
- **Ne rien écrire si la réponse n'est pas un calendrier complet** : un portail captif ou une page de connexion répond 200 en HTML ; un calendrier
  tronqué effacerait des réunions réelles (la synchronisation remplace le futur). Un calendrier légitimement vide est écrit.
- **Pas de sauvegarde à chaque synchronisation** : la première (ajout) a sa sauvegarde comme tout import ; ensuite la synchronisation ne remplace que le
  futur de **son** calendrier, comme « Réimporter », sans toucher aux créneaux manuels ni aux autres calendriers (`ConnectionRepositoryTest`).
  Une sauvegarde par heure remplirait le dossier des sauvegardes.
- **Les événements, jamais les corps.** La réponse brute n'est ni gardée ni journalisée ; seuls titre (ou « Occupé »), début, fin, identifiant
  d'événement et journée entière entrent en base, comme à l'import. Les API Microsoft et Google sont interrogées avec une liste de champs
  réduite : Kairos ne reçoit même pas la description, le lieu ni les participants.
- **Certificats du système seulement.** Un serveur à autorité privée, ou un réseau d'entreprise qui inspecte le TLS avec sa propre autorité,
  échoue (`TLS`). Écarté : accepter les autorités de l'utilisateur ou ignorer l'erreur (une porte ouverte aux interceptions, pour un gain réservé à
  quelques réseaux) ; piste à rouvrir si les retours le demandent, avec un épinglage explicite.
- **`http` en clair confirmé pour un lien ICS, jamais avec un identifiant ni un jeton.** Un lien clair est lisible par le réseau, mais sans
  identifiant il n'y a pas d'identifiant à voler : la confirmation suffit. Un mot de passe CalDAV ou un jeton en clair est refusé sans appel.
- **Redirection vers un autre hôte** : suivie seulement si l'utilisateur le confirme (lien ICS) ; refusée avec des identifiants ; `https` vers
  `http` refusée. Google et Outlook servent souvent le fichier depuis un hôte différent de celui du lien.
- **Retour OAuth par boucle locale sur le bureau, schéma propre sur Android** (RFC 8252) plutôt qu'une page hébergée ou une `WebView` : la `WebView`
  verrait le mot de passe (interdit par les deux fournisseurs et par l'esprit du flux) ; une page de redirection hébergée suppose un serveur du projet.
- **`Déconnecter` ≠ `Retirer`** : on peut vouloir cesser de se connecter sans perdre la trace de ses journées. Déconnecter fait du calendrier un calendrier
  importé ordinaire (réimport d'un fichier toujours possible).
- **Les connexions ne sont pas exportées.** Une base copiée, restaurée ou synchronisée par dossier retrouve ses événements gelés, pas ses accès :
  c'est le prix de « aucun secret ni adresse hors de l'appareil ». Les secrets du bureau sont hors du dossier de données choisi pour la même raison.
- **Corps de réponse plafonné à 16 Mio** (`MAX_BODY_BYTES`) : un fichier de 5 000 événements fait environ 3 Mio ; au-delà, c'est une
  erreur de serveur, pas un agenda.
- **Dates des journées entières de Microsoft Graph** : l'API les rend à minuit dans un fuseau ; la date retenue est celle de `start.dateTime` telle
  qu'écrite, sans conversion. **À vérifier sur de vrais comptes** (créés dans un autre fuseau) avant livraison de O3.
- **Hors périmètre de cette spec, noté** : le journal et la vue « État de la connexion » en détail (historique des synchronisations) ; le
  mode « sans réponse du serveur depuis N jours » signalé en badge ; un identifiant d'application OAuth personnalisé.

### Impacts sur les specs existantes (à reporter à l'implémentation)

Déjà reportés **le 2026-10-26** (une phrase « rouvert le 2026-10-26 par `connexions-en-ligne.md` » qui précise « jusqu'à la 3.x ») :
`connexions-externes.md` (§ Contraintes, § Paliers, § Hors périmètre, § Décisions), `distribution.md` (§ Comportement attendu et
§ Android, manifeste), `publication.md` (description des fiches, recette et anti-fonctionnalités, § Décisions), `architecture.md`
(dépendances et invariants), `mises-a-jour.md` (§ Comportement attendu, § Hors périmètre, § Décisions), `fdroid/README.md` (permissions et
anti-fonctionnalités). Ces phrases décrivent la décision ; le code reste celui de la 3.x jusqu'à l'implémentation, et **chaque phrase est
à passer au présent (jalon livré) quand le code existe**.

Restent à reporter **à l'implémentation**, hors du périmètre de la rédaction de cette spec :

- `README.md` et `README.fr.md` (feature : connexions en ligne, nouvelles versions d'Android ; les affirmations « sans réseau »), `docs/ROADMAP.md`
  (ligne 4.0.0 : #139 et #144 spécifiées), `docs/spec/README.md` (index : ajouter `connexions-en-ligne.md`, ajuster la ligne de
  `connexions-externes.md`) ;
- `soutien.md` (« Aucune permission réseau ajoutée à l'APK », « l'APK reste sans permission réseau » : vrai de la fonction de don, pas de
  l'APK), `equipe.md` (décision 1 « sans permission réseau » et hors périmètre : l'espace Équipe reste sans réseau ni échange automatique ; la
  permission est celle des calendriers et des versions), `equipe-echanges.md` (« Kairos n'a pas de permission réseau » : le transport des échanges reste
  un fichier), `bureau-avance.md` (proxy sur Android : désormais le proxy du système s'applique aussi à Android ; « aucun réseau » du diagnostic
  reste vrai de l'envoi), `rappels.md` (« sans permission réseau » : formulation à préciser), `ordonnancement.md` (« événements de calendrier
  externes : par le réseau, hors périmètre »), `demarrage.md` (rapport de plantage : « aucun réseau » reste vrai, la phrase n'a plus pour
  raison l'absence de `INTERNET`), `export-import.md` (les connexions ne sont pas exportées), `reglages.md` (carte « Connexions en ligne » de la
  catégorie Données, interrupteur d'Android dans « Mises à jour »), `modele-donnees.md` (schéma 13), `i18n.md` (nouveaux textes),
  `accessibilite.md` (cibles et libellés de la carte), `visite-guidee.md` (carte du guide) ;
- `docs/plan-v3-kotlin.md` : **historique**, conservé tel quel (il dit « pas de `INTERNET` » au jalon M0) ; une phrase renvoie à cette spec ;
- `fdroid/com.skohscripts.kairos.yml` (`AntiFeatures: NonFreeNet`, dans la version qui embarque Microsoft ou Google), `fastlane/metadata/android/*/full_description.txt`
  (retirer « aucune permission réseau », dire les connexions facultatives et désactivées par défaut), `changelogs/40000.txt` (six langues),
  `site/` (accroche « sans compte, sans réseau »), `AndroidManifest.xml` (commentaire de tête et permission), `kmp/gradle.properties`
  (version), `kmp/desktopApp/build.gradle.kts` (`jdk.httpserver`).
