# Connexions en ligne : lien ICS, CalDAV, Microsoft 365, Google

_Rôle : permettre à Kairos, sur demande et connexion par connexion, de lire un calendrier distant.
Rouvrir explicitement la règle « l'APK Android n'a aucune permission réseau » (`connexions-externes.md`, décision du 2026-10-24).
L'application reste entièrement utilisable hors ligne, sans activité réseau de calendrier tant que rien n'est activé.
Fichiers couverts (prévus, sauf les fichiers existants à modifier) :
`kmp/core/.../core/connections/` (`ConnectionKind.kt`, `CalendarConnection.kt`, `SyncSchedule.kt`, `HttpPolicy.kt`, `CalDavXml.kt`,
`CalDavQueries.kt`, `GraphEvents.kt`, `GoogleEvents.kt`, `OAuthPkce.kt`, `Sha256.kt`, `Providers.kt`), `core/calendar/CalendarImport.kt`
(`SkipReason.DECLINED`) ; `kmp/data/` (`Kairos.sq`, table `calendar_connection`, `13.sqm`, `KairosStore.kt`,
`Mappers.kt`, `KairosRepository.syncCalendar` / `recordSyncFailure` / `disconnectCalendar`, cycle de remplacement et `recordReview`) ;
`kmp/ui/.../ui/app/` (`HttpFetcher.kt`, `SecretStore.kt`, `OnlineGate.kt`, `ConnectionSync.kt`, `ConnectionWatcher.kt`, `OAuthFlow.kt`),
`ui/settings/OnlineConnections.kt` (carte « Connexions en ligne ») ;
`kmp/androidApp/` (`AndroidHttp.kt`, `AndroidSecrets.kt`, `SyncJobService.kt`, `MainActivity.kt` pour le retour OAuth,
`AndroidManifest.xml`, `res/xml/network_security_config.xml`, `res/xml/data_extraction_rules.xml`) ; `kmp/desktopApp/`
(`DesktopHttp.kt`, `DesktopSecrets.kt`, `LoopbackRedirect.kt`, module `jdk.httpserver` du runtime embarqué) ;
`kmp/webApp/.../kairos-web.js` (`fetchText`) ; `fdroid/com.skohscripts.kairos.yml`, `fastlane/metadata/android/`.
Tests prévus : `ConnectionRepositoryTest`, `SyncReviewConnectedTest`, `ConnectionSyncScopeTest`, `ConnectionConvergenceTest` ;
voir § Critères de succès._

État : spécifiée le 2026-10-26, mise à jour le 2026-11-11, **non implémentée**.
Prévue d'abord pour la 4.0.0, reportée à la 4.1.0 par le propriétaire le 2026-11-04.
La 4.0.0 sort avec la synchronisation et l'estimateur par modèle, toujours sans permission réseau.
#139 suit les jalons ci-dessous ; @pyroMain404 propose l'implémentation dans #212 ; parent #140.
#144 a été fermée « not planned » par le propriétaire le 2026-11-06.
Le propriétaire confirme le 2026-11-11 la 4.1.0 en fin de semaine : betas par nouvelle fonctionnalité, finale ensuite, sans livraison simultanée obligatoire.
Choix du propriétaire du 2026-10-26 : **un seul APK avec `INTERNET`**, pour F-Droid et GitHub.
**Toutes les connexions de calendrier sont désactivées par défaut**, activées une par une dans les Réglages.
Les autres specs restent celles du comportement livré ; voir § Impacts sur les specs existantes.

- [ ] **O1** : socle (`INTERNET`, `HttpFetcher`, `OnlineGate`, `HttpPolicy`, `SecretStore`, migration), carte et lien ICS sur Android, bureau et web.
- [ ] **O2** : CalDAV sur Android et bureau.
- [ ] **O3** : Microsoft 365, bloqué sur l'inscription Entra du propriétaire ; ne pas commencer avant l'identifiant de client.
- [ ] **O4** : Google, bloqué sur Google Cloud et la vérification OAuth du propriétaire ; même condition de démarrage.

Sans identifiant de client, le connecteur concerné n'est ni compilé ni proposé.
Si les inscriptions manquent, la 4.1.0 livre O1+O2 sans `NonFreeNet` ; O3/O4 passent en 4.2.0.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

La 3.10.0 a fait entrer les réunions dans Kairos par un fichier `.ics` (réimporté à la main) ou par les calendriers du
téléphone. Cela laisse un manque, signalé par les utilisateurs (#139) :

- **Rester à jour sans recommencer.** Un fichier exporté vieillit le jour même ; un calendrier du téléphone n'existe que sur
  Android et que si l'application de calendrier y copie les événements (Outlook pour Android le désactive par défaut, une
  organisation peut l'interdire). Sur le bureau, rien ne remplace le geste « exporter, importer ».

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

- **Aucune activité réseau de calendrier tant qu'aucune connexion n'est active** : installation, mise à jour, import de données.
  Sans activation, les calendriers restent hors ligne. La vérification existante du bureau reste inchangée.
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
- Quatre façons d'ajouter une connexion. Chacune s'ouvre par un dialogue qui **dit ce qui est envoyé, à qui**, avec « Annuler » et
  un bouton d'activation ; rien n'est envoyé avant ce bouton.

| Connexion | Plateformes | Texte avant activation |
|---|---|---|
| **Lien de calendrier (ICS)** : Outlook (« Publier un calendrier »), Google (« adresse secrète au format iCal »), Nextcloud, Proton… | Android, bureau, web (au mieux) | « Kairos téléchargera ce lien à intervalles réguliers. Le serveur qui l’héberge voit votre adresse IP et le nom « Kairos » ; rien d’autre n’est envoyé. Ce lien donne accès à votre agenda : ne le partagez pas. » |
| **Serveur CalDAV** : Nextcloud, Radicale, SOGo, Synology… | Android, bureau | « Kairos se connectera à ce serveur avec cet identifiant et lira les calendriers que vous cochez. Le mot de passe est gardé chiffré sur cet appareil et n’est envoyé qu’à ce serveur. Utilisez de préférence un mot de passe d’application. » |
| **Microsoft 365 / Outlook.com** | Android, bureau | « Kairos ouvrira la page de connexion de Microsoft dans votre navigateur. Kairos ne voit jamais votre mot de passe ; il reçoit seulement l’autorisation de lire vos calendriers (« Calendars.Read », lecture seule). Microsoft sait que Kairos se connecte. » |
| **Google Agenda** | Android, bureau | Même forme, avec Google et les autorisations de lecture des agendas et de leurs événements. |

- **Web** : seul le lien ICS est proposé (voir § Solution technique, Web). CalDAV, Microsoft et Google n'y apparaissent pas.

#### Ajouter une connexion de calendrier

1. **Lien ICS** : coller l'adresse (`webcal://` accepté, lu comme `https://`). Un lien `http://` est refusé avec « Ce lien
   n’est pas chiffré (http). N’importez que ce que vous seriez prêt à voir lu par d’autres. » et un bouton « Utiliser quand même »
   (jamais proposé avec un identifiant, voir § Politique d'adresse et journal).
   **CalDAV** : adresse du serveur, identifiant, mot de passe ; Kairos liste les calendriers (nom), **aucun coché par défaut**.
   **Microsoft / Google** : « Se connecter » ouvre le navigateur du système ; au retour, la liste des calendriers du compte,
   **aucun coché par défaut**.
2. **Aperçu avant toute écriture**, le même que l'import d'un fichier (`CalendarImportDialog`) : nom du calendrier (prérempli
   par le nom du calendrier distant, modifiable), « Masquer les titres », « N créneaux du JJ/MM au JJ/MM », journées entières,
   événements écartés par raison (la raison « refusés » s'ajoute aux autres pour les connexions qui la connaissent). « Ajouter »
   et « Annuler ». **Une sauvegarde automatique précède l'ajout** (comme tout import) : sans elle, rien n'est écrit.
   Si un calendrier du même nom existe sans connexion ici, l'aperçu annonce :
   « Ce calendrier existe déjà : la connexion s'y rattache et remplacera ses événements à venir ».
   « Ajouter » rattache alors la connexion au calendrier existant. Sinon, un calendrier déjà connecté impose un suffixe « (2) », « (3) »…
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
- **Synchroniser** remet les événements à venir en accord avec le serveur, sans réécrire ceux qui sont identiques.
  Les créneaux à partir d'aujourd'hui et les badges pas finis suivent le serveur ; le passé reste.
  Les créneaux saisis à la main et les autres calendriers ne changent jamais.
- Créneaux en lecture seule, sauf suppression, comme à l'import ; la confirmation dit « « Titre » reviendra à la prochaine
  synchronisation de « Nom » ».
- Les calendriers connectés comptent partout comme les calendriers importés (planning, vue Semaine et Mois, charge de « moi »,
  widgets et rappels de l'espace Perso). Un événement reçu à la fois par une connexion et par le téléphone n'apparaît qu'une
  fois (celui du téléphone, `connexions-externes.md`).

#### Plusieurs appareils

- La connexion reste sur cet appareil. Les événements passent par le fichier lié comme ceux d'un calendrier importé.
- Le deuxième appareil reçoit le calendrier sans connexion. Ajouter une connexion du même nom la rattache à ce calendrier.
- Deux appareils connectés au même calendrier ne remplissent jamais « À revoir » avec leurs différences d'événements.
  Les autres conflits, notamment le nom du calendrier et « Masquer les titres », restent visibles.
- Supprimer un événement d'un calendrier connecté ne le retire pas du serveur.
  Il revient à la prochaine synchronisation, puis sur les autres appareils par le fichier lié.

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

### Contraintes

- `INTERNET` est la seule permission ajoutée **par cette fonctionnalité**, dans la PR O1, pas avant.
  L'APK 4.1.0 gagne aussi `SCHEDULE_EXACT_ALARM` pour les alertes du chrono (`temps-reel-chrono.md`).
  Les connexions n'ajoutent aucune permission d'état du réseau, alarme exacte ou service au premier plan.
- Aucun SDK propriétaire ni Google Play Services : les connexions Microsoft et Google passent par le **navigateur du système**
  et un flux OAuth 2.0 avec PKCE écrit dans Kairos.
- `core` reste pur : aucune requête, aucune horloge, aucun secret (`architecture.md`).
- Dépendances libres seulement ; **aucune dépendance ajoutée** (§ Décisions).
- **F-Droid** : `INTERNET`, comptes et OAuth ne sont pas interdits ; une anti-fonctionnalité **non déclarée** entraîne un refus
  (`connexions-externes.md` § Décisions). La 4.1.0 déclare `NonFreeNet` dès qu'elle embarque un connecteur dédié à un service
  non libre (Microsoft, Google). Voir § Solution technique, F-Droid.
- Mode solo et espace Équipe intacts (§ Isolation).

### Critères de succès

Chacun est prouvé par le test nommé (à écrire à l'implémentation ; la CI n'a **aucun accès réseau**, tout passe par un client simulé) :

- **Rien sans geste.** Base neuve, base migrée ou import d'export : aucune connexion de calendrier ne s'active.
  Le client simulé ne reçoit aucune requête (`NoConnectionNoRequestTest`).
  Seuls `ui/app/` et les modules de plateforme portent le réseau des calendriers (`NetworkIsolationTest`, analyse des sources).
- **Politique d'adresse** : `https` exigé ; `http` refusé sauf lien ICS sans identifiant confirmé ; `webcal` lu comme `https` ;
  redirections : au plus 5, même hôte suivi, autre hôte demandé (ICS) ou refusé (CalDAV, OAuth), `https` vers `http` toujours refusé
  (`HttpPolicyTest`).
- **Lien ICS** : réponse 200 → plan identique à l'import du même texte ; 304 avec `ETag` → aucun événement écrit ; `ETag` ignoré dès que le
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
- **Données** : migration additive `13.sqm` vers le schéma 14, table locale `calendar_connection`, sans créneau modifié.
  L'export d'une base sans connexion reste identique à l'octet près.
  Un calendrier connecté s'exporte comme un calendrier importé, sans table ni champ de connexion, et se réimporte sans connexion.
  Les secrets orphelins sont purgés au lancement ; sans secret, aucune requête (`ConnectionRepositoryTest`, `SecretStoreTest`).
- **Identités et écritures** : aucun événement identique réécrit, aucun identifiant ni marque changé, aucun fichier lié réécrit sans changement serveur
  (`ConnectionRepositoryTest.aSyncWithoutChangeWritesNothing`).
  Aucun rafraîchissement connecté ne change la date d'import (`ConnectionRepositoryTest.syncNeverWritesImportedAt`).
  Le rattachement conserve le calendrier existant (`ConnectionRepositoryTest.attachToExistingCalendarKeepsIdentity`).
- **Fichier lié** : état local conservé à la fusion et au rechargement synchronisé, effacé au remplacement d'export.
  Sources disparues purgées dans la transaction ; déconnexion et retrait effacent leur ligne (`ConnectionRepositoryTest`).
  Les métadonnées de connexion restent hors portée (`ConnectionSyncScopeTest.connectionStateIsOutOfScope`).
- **Conflits** : événements connectés exclus de « À revoir », y compris les éléments conservés après suppression concurrente
  (`SyncReviewConnectedTest.connectedBlockConflictIsNotListed`).
  Les autres conflits restent visibles (`SyncReviewConnectedTest.otherConflictsStillListed`).
- **Convergence** : deux appareils simulés connectés au même calendrier relisent le serveur à des instants différents pendant plusieurs tours.
  Malgré les changements du serveur, ils convergent sur les mêmes créneaux et journées, sans rien dans « À revoir ».
  Un tour sans changement serveur n'écrit rien (`ConnectionConvergenceTest`, avec `SyncSim` de `synchronisation.md`,
  et `SyncReviewConnectedTest.twoDevicesConverge`).
- **Secrets** : aller-retour chiffré, altération détectée, absence de la clé = secrets absents (jamais une exception) ; fichier de
  secrets hors du dossier de données choisi par l'utilisateur (`SecretStoreTest`, `DesktopSecretsTest`).
- **Journal** : une synchronisation qui échoue n'écrit ni adresse, ni chemin, ni identifiant, ni jeton, ni titre (`ConnectionLogTest`).
- **Android** : le manifeste déclare exactement `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `READ_CALENDAR`,
  `SCHEDULE_EXACT_ALARM` (alertes du chrono, #219, `temps-reel-chrono.md`), `INTERNET`
  (`ManifestPermissionsTest`, et `aapt2 dump permissions` sur l'APK de release en CI) ; trafic en clair autorisé par la configuration réseau mais
  gardé par `HttpPolicy` ; aucune autre permission.
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
- **Preuve de chaque PR** : réponses réelles capturées, nettoyées de toute donnée personnelle, rejouées par un faux `HttpFetcher` en CI sans réseau.
  La description de PR donne un bref compte rendu manuel : fournisseur, plateforme, version du système, gestes et résultat observé.
  Ne jamais donner de lien réel, chemin d'URL ou jeton.
  Le propriétaire n'a aucun compte de calendrier : il peut essayer un flux ICS public et un Radicale local, pas Microsoft ou Google.

### Hors périmètre / différé

- **Écrire vers un calendrier distant** (créer une réunion, poser un créneau de deep work) : jamais dans cette spec.
- **Notification poussée** (push), webhooks, serveur Kairos, relais, compte Kairos : aucun.
  Le fichier lié existant continue de transporter les événements (`synchronisation.md`), jamais la connexion.
- **Télémétrie, statistiques d'usage, rapport de plantage distant** : aucun. Le projet ne reçoit rien.
- Vérification des nouvelles versions sur Android (#144) : fermée « not planned » par le propriétaire le 2026-11-06 ; Android n'a aucune vérification, le bureau garde la sienne (`mises-a-jour.md`). Ne pas rouvrir sans nouvelle décision.
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

- `AndroidManifest.xml` : ajouter `<uses-permission android:name="android.permission.INTERNET" />` dans la PR O1 seulement.
  C'est la seule permission ajoutée par cette fonctionnalité ; `SCHEDULE_EXACT_ALARM` vient du chrono.
  Réécrire en même temps le commentaire « Aucune permission réseau » (§ Impacts).
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
- `OnlineGate` enveloppe un `HttpFetcher` : toute requête **de calendrier** y passe.
  Il refuse (`GateClosed`, sans réseau) une origine sans connexion activée (`ConnectionId`, calendrier non en pause).
  Il applique `HttpPolicy` et le journal sans secret. Le réseau existant du bureau reste inchangé.
- `NetworkIsolationTest` (JVM, analyse des sources) limite le nouveau réseau à `ui/app/` (`OnlineGate`, `ConnectionSync`, `OAuthFlow`)
  et aux fichiers `*Http.kt` des applications. `core`, `data` et le reste de `ui` n'y accèdent pas.
  Le test distingue explicitement le chemin historique de vérification du bureau, hors de cette fonctionnalité.

### Modèle (`core/connections`, `core/model`)

- `ConnectionKind` : `FILE` (calendrier importé, comportement de la 3.10.0, **défaut**), `URL`, `CALDAV`, `OAUTH` ; `OAuthProvider` :
  `MICROSOFT`, `GOOGLE`. `SyncInterval` : `MANUAL`, `ON_OPEN` (défaut), `HOURLY` (60 min), `SIX_HOURS` (360), `DAILY` (1 440).
- `CalendarSource` reste inchangé : `id`, `name`, `hideTitles`, `importedAt`, `createdAt`.
  Le nouveau modèle local `CalendarConnection` correspond à une ligne de `calendar_connection`, jamais à un objet du `KairosSnapshot`.
  Sans ligne associée, le calendrier est `FILE`. Une ligne décrit seulement `URL`, `CALDAV` ou `OAUTH`.

| Champ du modèle | Colonne de `calendar_connection` | Rôle | Défaut |
|---|---|---|---|
| `sourceId` | `source_id` | clé primaire, identifiant de `calendar_source` | obligatoire |
| `kind` | `kind` | type de connexion | obligatoire, aucun défaut `FILE` |
| `provider` | `provider` | `MICROSOFT` / `GOOGLE` pour `OAUTH` | nul |
| `host` | `host` | hôte affiché, sans chemin ni requête | nul |
| `account` | `account` | clé opaque du compte dans le magasin de secrets | nul |
| `syncEnabled` | `sync_enabled` | interrupteur « Synchroniser » après ajout explicite | vrai |
| `interval` | `interval` | `SyncInterval` | `ON_OPEN` |
| `lastAttemptAt` / `lastSyncAt` | `last_attempt_at` / `last_sync_at` | dernier essai / réussite, instants UTC | nul |
| `lastError` | `last_error` | `ConnectionError`, nul si réussi | nul |
| `failures` / `nextAttemptAt` | `failures` / `next_attempt_at` | échecs successifs / borne du prochain essai | 0 / nul |
| `etag` / `plannedOn` | `etag` / `planned_on` | ETag / jour du plan | nul |
| `insecureConfirmed` / `redirectHost` | `insecure_confirmed` / `redirect_host` | HTTP confirmé / hôte accepté | faux / nul |

- **Adresse, identifiant, mot de passe, jetons, identifiant du calendrier distant : jamais dans la base.** Le lien ICS est un secret
  (les adresses « publiées » d'Outlook et « secrètes » de Google donnent l'agenda à qui les connaît) ; l'identifiant du calendrier CalDAV
  contient souvent le nom d'utilisateur. La base ne garde que l'hôte à afficher.
- `ConnectionError` : `OFFLINE`, `TLS`, `AUTH`, `REAUTH`, `GONE`, `NOT_CALENDAR`, `TRUNCATED`, `TOO_BIG`, `UNAVAILABLE` (5xx, 429),
  `REDIRECT_HOST`, `INSECURE_REDIRECT`, `NO_SECRET`, `UNSUPPORTED` (serveur CalDAV sans `calendar-query`). Chacun a son texte (§ Hors ligne).
- `SyncSchedule.due(connection, maintenant, force)` (pur) : faux si `syncEnabled` est faux ou si les secrets manquent ;
  vrai si `force` ; `MANUAL` jamais ; avant `nextAttemptAt` jamais ; `ON_OPEN` après 15 minutes depuis `lastAttemptAt` ;
  sinon `maintenant − lastAttemptAt ≥ interval`. `SyncSchedule.afterFailure(connection, erreur, retryAfter, maintenant)` calcule `failures`
  et `nextAttemptAt` (≥ 15 minutes, 6 heures dès 5 échecs, `Retry-After` plafonné à 24 heures).
- `CalendarImport.plan` (3.10.0) gagne `SkipReason.DECLINED` (« refusés »), inutilisé par l'import de fichier (un export ne dit pas
  quel participant est l'utilisateur) et rempli par les API Microsoft et Google, qui le disent. `ExternalBlocks` (téléphone) écarte
  déjà les refusés ; l'import de fichier ne change pas.

### Stockage (`data`)

- Schéma 14 : `13.sqm` crée `calendar_connection`, table additive vide pour les bases existantes.
  `11.sqm` (schéma 12, couleur par défaut) et `12.sqm` (schéma 13, fichier lié) sont déjà implémentées.
  Ajouter en tête de `KairosStore.MARKERS` :
  `14L to "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'calendar_connection'"`.
  Le marqueur suit la convention existante ; `ExchangeMigrationTest.everyMigrationHasAMarker` doit le couvrir.
- `KairosRepository.syncCalendar(sourceId, plan, today, etag, now)` applique le plan et les métadonnées locales dans une transaction.
  `recordSyncFailure(sourceId, erreur, now, nextAttemptAt)` ne change que les métadonnées locales, jamais les événements.
  `disconnectCalendar(id)` efface la ligne de connexion, garde les créneaux et journées.
  `removeCalendar(id)` efface aussi cette ligne dans sa transaction.
  Le détail du différentiel, du rattachement et du cycle de vie figure dans la section suivante.
- `ExportCodec` ne change pas : `calendarSources` garde ses champs actuels, dont `createdAt` et les marques du format synchronisé.
  Aucune connexion n'entre dans l'export, la sauvegarde automatique ou le paquet d'équipe.
  Le format et `formatVersion` ne changent pas. Un import d'export n'active aucune connexion.

### Connexions et synchronisation du fichier lié (plusieurs appareils)

Les mécanismes ci-dessous sont proposés, non implémentés.
Les références décrivent le code de `main` consulté le 2026-11-11.

#### État local et remplacement des lignes (D1)

`KairosRepository.replaceRows` efface puis recrée `calendar_source` (`deleteAllCalendarSources`).
Ses appelants sont `replaceAll`, `restoreSynced`, `merge`, sa réparation `SyncRepair`, `resolveReview` et `deleteKept`.
Des colonnes de connexion sur `calendar_source` disparaîtraient donc à chaque fusion.
La table séparée `calendar_connection` reste hors de `replaceRows`, du `KairosSnapshot`, des exports et de la portée synchronisée.
`source_id` désigne l'identifiant local du calendrier, sans suppression en cascade lors du remplacement transitoire des sources.

`SnapshotMerge.lower` reprend `known[kind][uid]` pour les éléments connus.
Un nouvel élément reçoit le plus grand identifiant du genre + 1.
Les calendriers déjà connus gardent donc leur identifiant local à la fusion.

Seul `replaceAll` efface toutes les connexions : import d'export, restauration de sauvegarde ou « Remplacer ».
Ces opérations peuvent apporter des identifiants étrangers ; les calendriers reçus deviennent `FILE`.
Les secrets devenus orphelins sont purgés au lancement.
`restoreSynced` recharge l'état synchronisé de cet appareil, avec ses identifiants ; il conserve les lignes locales existantes.
`merge`, sa réparation, `resolveReview` et `deleteKept` les conservent aussi.
Chaque transaction purge ensuite les lignes dont la source a disparu, après réinsertion des sources.

#### Différentiel et date affichée (D2, D3)

`syncCalendar` passe par `write`. Il compare les identités des événements de cette source avec celles du plan.
Il insère les nouveaux, met à jour les champs modifiés (titre, fin, journée entière), et laisse les lignes identiques intactes.
Il supprime seulement les absents du serveur : créneaux de début ≥ `today 00:00`, journées de fin ≥ `today`, de cette source seulement.
Un changement de début change l'identité ; il suit les mêmes règles d'insertion et de suppression.
Le passé reste intact, comme les créneaux manuels et les autres calendriers.

Une réponse inchangée n'écrit aucune ligne de créneau ou de journée, ne change aucun `id` et ne pose aucune marque de synchronisation.
Seules les métadonnées locales de réussite avancent : `last_sync_at`, `last_attempt_at`, `etag`, `planned_on`, `failures = 0`, `last_error = NULL`.
Le réimport manuel de fichier (`importCalendar`, 3.10.0) garde son comportement.

`SyncRecords.lift(CalendarSource)` partage `name`, `hideTitles` et `importedAt`.
Une synchronisation connectée n'écrit donc jamais `imported_at` : les heures propres à deux appareils provoqueraient des conflits permanents.
Cette date reste celle de l'ajout ; seul « Réimporter » d'un fichier continue de la modifier.
La liste affiche `calendar_connection.last_sync_at` pour un calendrier connecté, `CalendarSource.importedAt` sinon.

#### Autorité du serveur et conflits (D4)

`KairosRepository.recordReview` consigne actuellement `out.conflicts` et `out.kept` après la fusion.
Pour un créneau ou une journée, il retrouve le calendrier dans l'état fusionné.
Si ce calendrier possède une ligne `calendar_connection` ici, il ne consigne ni conflit ni élément conservé.
Le filtre s'applique aussi à une connexion en pause : sa présence, non sa fréquence, désigne l'autorité du serveur.
La marque la plus haute gagne comme aujourd'hui ; la prochaine synchronisation rétablit ensuite la valeur du serveur.

Les autres enregistrements restent listés, notamment les calendriers eux-mêmes (`name`, `hideTitles`).
Sur un appareil sans connexion pour ce calendrier, les conflits restent également listés.
Supprimer ici un événement connecté ne le supprime pas du serveur : il revient au prochain passage.
L'« ajout gagnant » le fait ensuite revenir partout ; ce retour attendu ne produit pas d'élément « À revoir » ici.

#### Rattachement par le nom (D5)

L'identité existante du calendrier est `cal:<nom en minuscules sans blancs>` (`synchronisation.md`, § Identité).
Les créneaux ont pour uid `ics:<uid du calendrier>:<UID iCalendar>:<début>`, les journées `mark:<uid du calendrier>:<UID>:<début>`.
Si le nom existe sans connexion ici, « Ajouter » rattache la nouvelle connexion au calendrier existant après sauvegarde automatique.
L'aperçu avertit du remplacement de ses événements à venir (§ Ajouter une connexion de calendrier).
Le rattachement garde les identités et `imported_at` ; il applique ensuite le différentiel, comme le périmètre de « Réimporter ».
Si le calendrier du même nom est déjà connecté ici, le nom reçoit le premier suffixe libre « (2) », « (3) »…

#### Un seul écrivain et portée inchangée (D6)

`syncCalendar` utilise `write`, donc le même verrou que la fusion du fichier lié.
Fusion et application du plan ne s'entrelacent jamais.
Un changement d'événement suit le chemin habituel `changed`, puis l'écriture du fichier lié après 2 secondes.
Sans changement partagé, aucune écriture du fichier lié : les seules métadonnées locales ne doivent pas déclencher ce chemin.
Aujourd'hui, `write` appelle toujours `onChanged` après sa transaction, même si le snapshot est identique.
L'implémentation doit conditionner cette notification au changement du snapshot pour satisfaire l'invariant, sans contourner le verrou de `write`.

`SyncScope.of(snapshot, data)` conserve les calendriers, créneaux importés et journées de leurs sources dans sa portée.
`calendar_connection` n'a aucun `RecordKind` et n'entre ni dans `SyncScope` ni dans `SyncRecords`.
Les champs `kind`, ETag, dates, erreurs, fréquence et consentement restent locaux.
Les champs de `Settings`, dont `textZoom` et `subtaskChain`, gardent leur classement actuel.

### Secrets (`ui/app/SecretStore.kt` et plateformes)

- `SecretStore` : `get(key)`, `put(key, value)`, `delete(key)`, `keys()`, `level` (`HARDWARE`, `SYSTEM`, `FILE`, `NONE`, pour dire la
  vérité à l'utilisateur). Clés : `url:<idSource>` (lien ICS), `remote:<idSource>` (identifiant du calendrier distant), `account:<compte>`
  (JSON : serveur, identifiant, mot de passe CalDAV ; ou jetons OAuth : `access`, `refresh`, `expiresAt`). Une valeur illisible
  (clé perdue, fichier altéré) est **absente**, jamais une exception : la connexion passe en `NO_SECRET`.
- `OnlineConnections.purgeOrphans()` au lancement supprime les clés sans ligne `calendar_connection` correspondante, puis les comptes sans connexion restante.
  Vérifier la ligne de connexion, pas seulement la source : un remplacement peut réutiliser son identifiant sans conserver la connexion.
  « Retirer » et « Déconnecter » effacent leurs clés, et le compte sans autre connexion, avant de toucher la base.
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
  `redirectHost` ; CalDAV et OAuth → refusée ; `https` → `http` toujours refusée (`INSECURE_REDIRECT`).
  L'en-tête `Authorization` n'est envoyé qu'à l'hôte configuré, jamais après une redirection.
- En-têtes : `User-Agent: Kairos` pour les calendriers (sans version : rien à pister),
  `Accept-Encoding: gzip`, `If-None-Match` si un `ETag` existe et que le plan est du jour.
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
  plus, une requête, chemin et `state` vérifiés, page de fin dans la langue de l'interface). L'instance unique de la 4.0 n'ouvre aucun
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
- **Ce qu'il faut côté fournisseur** : le propriétaire termine l'inscription avant de commencer le connecteur.
  Les identifiants de client sont publics, dans `Providers.kt` ; aucun secret de client ne va dans le dépôt.
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
- `ConnectionWatcher` : au lancement, au retour dans l'application (`onResume`, reprise de la fenêtre) et
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

### Web (`kairos-web.js`, `WebServices`)

- Seul le **lien ICS** est proposé, via `fetch` : il marche si le serveur envoie `Access-Control-Allow-Origin`, ce que ni Outlook, ni Google, ni
  la plupart des serveurs de calendrier ne font toujours ; sinon `TypeError`, que le navigateur ne distingue pas d'une panne de réseau. Message :
  « Le navigateur n’a pas pu lire ce lien (hors ligne, ou serveur qui n’autorise pas les pages web). » L'aide de la section le dit **avant**
  l'ajout. Pas d'`If-None-Match` (il déclencherait une requête préliminaire inutile).
- Le stockage prévu du lien utilise IndexedDB, à côté de la poignée du fichier lié (`export-import.md`), sous réserve de l'arbitrage ci-dessous.
  Si cette persistance est retenue, l'aide dit « Cette adresse est gardée dans ce navigateur. »
  Aucun identifiant, mot de passe ni jeton n'est jamais gardé sur le web.
- **Point ouvert à régler dans la tranche web de O1** : la base est en mémoire, rechargée depuis l'export OPFS sans connexion.
  `export-import.md` (§ Web) décrit ce démarrage ; `synchronisation.md` précise `restoreSynced` pour un instantané synchronisé.
  Une ligne `calendar_connection` seule disparaîtrait donc au rechargement.
  Deux pistes restent à arbitrer : garder les métadonnées (lien, fréquence, interrupteur) dans IndexedDB, puis recréer la ligne par nom ;
  ou limiter la connexion web à la session. Aucun des deux comportements n'est encore décidé.
- **CalDAV et OAuth n'y sont pas proposés**, pour trois raisons : (1) aucun magasin de secrets (tout script de l'origine lit IndexedDB et
  `localStorage`, un mot de passe ou un jeton de rafraîchissement y serait lisible) ; (2) les serveurs CalDAV n'envoient en général pas les
  en-têtes CORS et exigent des requêtes préliminaires pour `PROPFIND`/`REPORT` ; (3) un flux OAuth de page web demande une inscription
  « application monopage » distincte et des jetons de rafraîchissement à vie courte. La version web reste le recours du poste verrouillé :
  les fichiers `.ics` y fonctionnent.

### Interface (`ui/settings/OnlineConnections.kt`, `kairos-ecran`)

- `OnlineConnectionsCard` (catégorie Données, après `CalendarsCard`) : phrase d'ouverture ; « Ajouter une connexion » (menu des connexions
  disponibles sur la plateforme) ; calendriers possédant une ligne `calendar_connection` ; état de chaque calendrier.
- Dialogues : activation (le texte du tableau ci-dessus, `AlertDialog`), saisie (champs : `OutlinedTextField`, mot de passe masqué,
  `KeyboardType.Password`), choix des calendriers (cases, aucune cochée), aperçu (`CalendarImportDialog` étendu, bouton « Ajouter »), erreurs.
- Couleurs par `MaterialTheme.colorScheme` seulement ; l'erreur est une ligne de texte avec icône (`Warning`, ajoutée à `make_icons.py` si absente),
  **pas** de conteneur d'erreur : ce sont des incidents attendus, pas des échecs de l'application (`DESIGN_SYSTEM.md`). Cibles de 48 dp.
- Carte du guide `online_connections` (chapitre Organiser, `since = "4.1.0"`, ouvre Réglages → Données) ; carte `FeatureCatalog` et
  `README_FEATURES` à l'implémentation (`FeatureCatalogTest.readmeFeaturesAreCovered`).
- Textes dans les six langues (`values*/strings.xml`), apostrophe typographique `’`.

### Isolation du mode solo et de l'espace Équipe

- **Mode solo** : tant qu'aucune connexion n'est ajoutée, aucune ligne de connexion n'existe, aucune tâche de fond n'est planifiée,
  `ConnectionWatcher` ne trouve rien de dû, l'export est identique à l'octet près. `Settings` n'a aucun champ nouveau.
- **Espace Équipe** : les créneaux synchronisés sont des créneaux importés de l'espace Perso (`personalView`, `equipe.md`) ; la synchronisation
  n'écrit que les tables de calendrier Perso et `calendar_connection`. Elle ne lit ni n'écrit aucune table `team_*`, ne passe par aucun filtre d'équipe et
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
  - aucune étiquette n'est omise « pour ne pas faire peur » : la règle de `connexions-externes.md` (« une anti-fonctionnalité non déclarée
    entraîne un refus ») s'applique à chaque version.
  `fdroid_check.sh` fait passer `fdroid lint` et `rewritemeta` sur la recette canonique avec l'étiquette ; la fiche Fastlane et la page
  de téléchargement cessent de dire « aucune permission réseau » (§ Impacts).
- **Jalons** : O1-O4 selon la checklist de l'État. O1 apporte `INTERNET`, pas avant.
  Les inscriptions conditionnent le démarrage de O3/O4 ; faute d'inscription, O1+O2 sortent en 4.1.0 sans `NonFreeNet`.
  `INTERNET` est la seule permission de cette fonctionnalité ; `SCHEDULE_EXACT_ALARM` vient des alertes du chrono.
  La permission réseau ne revient pas en arrière dans une version mineure.
- **Version** : prévue `4.1.0` (`kairos.versionCode` 40100, formule de `distribution.md`) : elle change une décision fondatrice, mais arrive dans une version mineure : la 4.0.0 a déjà ouvert la série majeure
  (choix du 2026-11-04 ; la règle « jamais en arrière dans une version mineure » vaut à partir de la 4.1.0). Note de version
  Fastlane `40100.txt` dans les six langues, qui **dit** l'ajout de la permission réseau et que tout est désactivé par défaut. README anglais et
  français (feature) à l'implémentation.

### Décisions et pièges tracés

- **Rouvrir « sans permission réseau »** (propriétaire, 2026-10-26, #139) : remplace la décision du 2026-10-24 de
  `connexions-externes.md` ; contreparties acceptées : la fiche F-Droid et les textes de publication ne disent plus « aucune permission
  réseau », `NonFreeNet` apparaît avec les connecteurs Microsoft et Google, la permission arrive avec la 4.1.0 (prévue pour la 4.0.0, report du 2026-11-04). Le calendrier du téléphone (3.10.0)
  et le fichier `.ics` restent, hors ligne, la voie de qui ne veut pas du réseau.
- **Un seul APK avec `INTERNET`**, plutôt que deux APK (un « sans réseau », un « en ligne »). Écarté : deux identifiants d'application ou deux
  signatures, donc des données non partagées, une fiche F-Droid double, une reproductibilité à prouver deux fois, et des utilisateurs qui ne savent
  pas lequel prendre. Le prix est une permission réseau accordée même sans connexion active.
- **`INTERNET` demandée à l'exécution : impossible sur Android.** C'est une permission « normale », accordée à l'installation, qu'on ne peut ni
  demander ni refuser ensuite. Le consentement est donc dans Kairos (interrupteurs, dialogue qui dit ce qui est envoyé), et la garantie est
  dans le code (`OnlineGate`, `NoConnectionNoRequestTest`, `NetworkIsolationTest`), pas dans le système.
- **Calendriers désactivés par défaut sur toutes les plateformes** : l'ajout explicite vaut consentement.
  Écarté : déduire une activation d'un import. Le comportement historique du bureau reste hors de ce changement.
- **Pas de `ACCESS_NETWORK_STATE`, pas de contrainte réseau du planificateur** : ne pas ajouter de permission pour détecter une panne.
  Une tentative hors ligne échoue et reprend plus tard. Écarté : élargir les permissions des connexions.
- **`JobScheduler`, pas `WorkManager`, pas `AlarmManager`.** `WorkManager` ajoute une dépendance et fusionne ses propres permissions dans le manifeste ;
  `AlarmManager` sans permission exacte (`rappels.md`) fonctionnerait mais réveillerait le téléphone sans condition. `JobScheduler` est le
  mécanisme du système pour un travail périodique, sans permission exacte, avec reprise au redémarrage déjà couverte.
- **Pas de Ktor, pas d'OkHttp, pas d'AppAuth, pas de bibliothèque XML.** `HttpURLConnection` (Android) et `java.net.http` (bureau, déjà au
  runtime depuis M5) font ce qu'il faut (`GET`, `POST`, et `PROPFIND`/`REPORT` sous réserve du piège ci-dessous pour Android) ; Ktor serait une dépendance de plus (graphe de construction
  reproductible, taille de l'APK de 2 Mo) pour un code partagé que l'interface `HttpFetcher` donne déjà ; AppAuth, libre, tire des
  dépendances pour un flux d'une page de code qu'on teste mieux chez soi ; un lecteur XML maison de quelques dizaines de lignes est le seul
  qui marche en wasm et sans XXE. Le propriétaire confirme cette contrainte le 2026-11-06 ([#212](https://github.com/SKOHscripts/Kairos/issues/212#issuecomment-6687966751)).
  Piège de O2 : `HttpURLConnection` peut refuser `PROPFIND` et `REPORT`.
  Tester réellement sur Android 8 à 16 : (1) méthode telle quelle ; (2) HTTP/1.1 minimal sur `SSLSocket` système, dans `AndroidHttp.kt` seulement.
  (3) OkHttp reste le dernier recours, avec **accord explicite du propriétaire après cet essai réel**, jamais une décision implicite d'implémentation.
  Écartés : réflexion sur `method` (API cachées) et `X-HTTP-Method-Override` (Radicale ne le prend pas en charge).
  Le bureau (`java.net.http`) accepte ces méthodes.
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
- **Les connexions ne sont pas exportées** : l'appareil destinataire reçoit les événements, pas les accès.
  Écarté : transporter les secrets avec les données. Le maintien des connexions déjà locales suit D1, pas un effacement à chaque fusion.
- **Corps de réponse plafonné à 16 Mio** (`MAX_BODY_BYTES`) : un fichier de 5 000 événements fait environ 3 Mio ; au-delà, c'est une
  erreur de serveur, pas un agenda.
- **Dates des journées entières de Microsoft Graph** : l'API les rend à minuit dans un fuseau ; la date retenue est celle de `start.dateTime` telle
  qu'écrite, sans conversion. **À vérifier sur de vrais comptes** (créés dans un autre fuseau) avant livraison de O3.
- **Hors périmètre de cette spec, noté** : le journal et la vue « État de la connexion » en détail (historique des synchronisations) ; le
  mode « sans réponse du serveur depuis N jours » signalé en badge ; un identifiant d'application OAuth personnalisé.
- **D1 — Table locale séparée** (proposée le 2026-11-11 dans la PR de spec, à confirmer en revue par le propriétaire).
  Choisi : `calendar_connection`, selon § État local et remplacement des lignes, pour survivre aux fusions.
  Écartés : colonnes sur `calendar_source`, connexion dans le snapshot ou l'export ; leur cycle de vie ne convient pas.
- **D2 — Différentiel par identité** (proposée le 2026-11-11 dans la PR de spec, à confirmer en revue par le propriétaire).
  Choisi : § Différentiel et date affichée, pour éviter des écritures et identifiants nouveaux chaque heure.
  Écarté : effacer puis réinsérer les événements identiques.
- **D3 — Date de réussite locale** (proposée le 2026-11-11 dans la PR de spec, à confirmer en revue par le propriétaire).
  Choisi : § Différentiel et date affichée, pour éviter les conflits d'horodatage entre appareils.
  Écarté : rafraîchir le champ partagé `importedAt`.
- **D4 — Serveur autoritaire pour les événements connectés** (proposée le 2026-11-11 dans la PR de spec, à confirmer en revue par le propriétaire).
  Choisi : § Autorité du serveur et conflits, pour ne pas demander de résoudre des valeurs transitoires.
  Écarté : remplir « À revoir » avec ces divergences.
- **D5 — Rattachement au calendrier existant** (proposée le 2026-11-11 dans la PR de spec, à confirmer en revue par le propriétaire).
  Choisi : § Rattachement par le nom, pour garder l'identité reçue du fichier lié.
  Écarté : créer systématiquement un doublon suffixé.
- **D6 — Verrou commun, état hors portée** (proposée le 2026-11-11 dans la PR de spec, à confirmer en revue par le propriétaire).
  Choisi : § Un seul écrivain et portée inchangée, pour sérialiser les données sans partager l'état de l'appareil.
  Écartés : écrivain parallèle et nouveau genre de synchronisation pour les connexions.
- **D7 — Périmètre réduit aux calendriers** (propriétaire, 2026-11-06, [#212](https://github.com/SKOHscripts/Kairos/issues/212#issuecomment-6687966751)).
  Choisi : le retrait tracé une seule fois dans § Hors périmètre, pour respecter la clôture du propriétaire.
  Écarté : maintenir la proposition abandonnée.
- **D8 — Permission avec O1 seulement** (propriétaire, 2026-11-06, [#212](https://github.com/SKOHscripts/Kairos/issues/212#issuecomment-6687966751)).
  Choisi : `INTERNET` dans la première PR réseau ; seule permission de cette fonctionnalité, distincte du chrono.
  Écarté : ajouter une permission inutilisée dans une PR préalable.
- **D9 — Inscriptions avant O3/O4** (propriétaire, 2026-11-06, [#212](https://github.com/SKOHscripts/Kairos/issues/212#issuecomment-6687966751)).
  Choisi : jalons bloqués jusqu'aux identifiants, selon l'État et § OAuth, pour livrer des connecteurs utilisables.
  Écarté : commencer ou embarquer un connecteur sans inscription ; repli O1+O2 puis 4.2.0.
- **D10 — Chaque PR apporte sa preuve** (propriétaire, 2026-11-06, [#212](https://github.com/SKOHscripts/Kairos/issues/212#issuecomment-6687966751)).
  Choisi : captures nettoyées et rapport manuel (§ Critères de succès), car le propriétaire n'a pas les comptes nécessaires.
  Écarté : reporter toute vérification réelle sur le propriétaire.

### Points ouverts

- O1 web : persistance des métadonnées dans IndexedDB avec rattachement par nom, ou connexion limitée à la session (§ Web).
- O2 Android : résultat des essais réels de `PROPFIND`/`REPORT`, avant tout recours soumis à accord à OkHttp.
- O3/O4 : inscriptions du propriétaire, adresses de retour et vérification des portées (§ OAuth).

### Impacts sur les specs existantes (à reporter à l'implémentation)

Les renvois historiques du 2026-10-26 décrivent une décision, pas une fonctionnalité livrée.
Ne pas mettre les specs du comportement livré au présent avant l'implémentation.
Reporter les changements suivants dans la PR qui livre le jalon concerné :

- `connexions-externes.md` : réseau facultatif, import manuel inchangé, rattachement et événements connectés.
- `architecture.md` : exceptions réseau de plateforme, `core` pur, absence de nouvelle dépendance.
- `distribution.md` : APK unique, `INTERNET` avec O1, consentement par connexion.
- `publication.md` et `fdroid/README.md` : permission réseau facultativement utilisée, chrono avec `SCHEDULE_EXACT_ALARM`, anti-fonctionnalités selon les connecteurs embarqués.
- `README.md` et `README.fr.md` : fonctionnalité des connexions en ligne, formulations « sans réseau », puce et tableau correspondants.
  `FeatureCatalog`, six langues et carte du guide doivent rester cohérents (`liste-fonctionnalites.md`).
- `docs/ROADMAP.md` : #139 suit O1-O4, #212 porte l'implémentation ; 4.1.0 confirmée, O3/O4 conditionnels.
  `docs/spec/README.md` : index et renvoi depuis `connexions-externes.md`.
- `soutien.md` : l'absence de permission ajoutée concerne le don, pas l'ensemble de l'APK.
- `equipe.md`, `equipe-echanges.md` et les specs `equipe*.md` concernées : l'espace Équipe reste sans réseau ni échange automatique.
  Les permissions concernent les calendriers et le chrono ; paquets et rapports ne contiennent aucune connexion.
- `synchronisation.md` : D4 filtre `recordReview` pour les événements connectés, y compris `kept`, sans changer la résolution des marques.
  D1/D6 conservent l'état local à la fusion, réparation et `restoreSynced`, mais l'effacent dans `replaceAll`.
  La portée, les identités et le classement des champs de `Settings` restent inchangés.
- `modele-donnees.md` : schéma 14, `13.sqm`, table locale `calendar_connection`, marqueur structurel ; `CalendarSource` inchangé.
- `export-import.md` : aucune donnée de connexion exportée ; préciser le stockage web après arbitrage de O1.
- `reglages.md` : carte « Connexions en ligne » dans Données.
- `bureau-avance.md` : proxy système aussi pour le nouveau réseau Android ; diagnostic sans envoi distant inchangé.
- `rappels.md` : préciser que les rappels n'utilisent pas le réseau.
  `ordonnancement.md` : créneaux connectés traités comme les créneaux importés.
  `demarrage.md` : rapport de plantage sans envoi, sans invoquer l'absence globale de `INTERNET`.
- `i18n.md` : textes dans les six langues.
  `accessibilite.md` : cibles et libellés de la carte.
  `visite-guidee.md` : carte `online_connections`.
- `docs/plan-v3-kotlin.md` : conserver l'historique M0, ajouter seulement un renvoi vers cette décision lors de l'implémentation.
- `fdroid/com.skohscripts.kairos.yml` : `NonFreeNet` à l'embarquement de Microsoft ou Google seulement.
  `fastlane/metadata/android/*/full_description.txt`, note finale dans les six langues et `site/` : remplacer l'absence de réseau par les connexions facultatives.
  `AndroidManifest.xml` : permission et commentaire avec O1 ; `kmp/desktopApp/build.gradle.kts` : `jdk.httpserver` avec OAuth.
  Ne modifier version ou tags que dans le cycle de publication prévu, pas dans cette PR de spec.
