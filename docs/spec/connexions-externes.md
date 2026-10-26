# Connexions externes : calendriers, sans réseau ni compte

_Rôle : faire entrer dans Kairos les événements des calendriers de
l'utilisateur (Outlook, Google, Thunderbird, Nextcloud…) sous forme de
créneaux occupés, sans compte, sans permission réseau, et garder le cadre
des connexions externes (ce que F-Droid permet, ce que Kairos s'interdit).
Fichiers couverts (étape 1) : `kmp/core/.../core/calendar/` (`IcsParser.kt`,
`IcsRule.kt`, `IcsZones.kt`, `CalendarImport.kt`, `ImportedCalendars.kt`),
`kmp/core/.../core/model/TimeBlock.kt` (`BlockSource`, `CalendarSource`,
`DayMark`), `KairosSnapshot.kt`, `core/day/DayView.kt`, `core/week/WeekView.kt`,
`core/month/MonthView.kt` (journées entières) ; `kmp/data/` (`Kairos.sq`,
`10.sqm`, `KairosStore.kt`, `Mappers.kt`, `ExportCodec.kt`,
`KairosRepository.importCalendar` / `removeCalendar`) ;
`kmp/ui/.../ui/settings/CalendarImport.kt` (`CalendarsCard`,
`CalendarImportDialog`), `ui/team/ExchangeDialogs.kt` (`readImportedFile`),
`ui/settings/SettingsScreen.kt`, `ui/team/ExchangeHub.kt`, `ui/day/DayMarks.kt`,
`ui/day/Blocks.kt`, `ui/day/DayScreen.kt`, `ui/week/WeekScreen.kt`,
`ui/month/MonthScreen.kt`, `ui/KairosApp.kt` (`LocalCalendarNames`) ; sélecteurs
de fichier (`kairos-web.js`, `MainActivity.kt`). Tests : `IcsParserTest`,
`IcsRuleTest`, `IcsZonesTest`, `CalendarImportTest` (exemples réels recopiés
en constantes dans `IcsSamples.kt` : les tests de `core` tournent aussi hors
JVM), `CalendarImportRepositoryTest` (dépôt, export, migration 10 → 11),
`CalendarImportUiTest`. Étape 2 : `core/calendar/ExternalBlocks.kt`,
`ui/app/CalendarService.kt` (`withDeviceCalendars`), section du téléphone de
`settings/CalendarImport.kt`, `day/DayScreen.kt`, `week/WeekScreen.kt`,
`month/MonthScreen.kt`, `team/TeamMembersScreen.kt`,
`team/forecast/ForecastScreen.kt`, `kmp/androidApp/.../AndroidCalendarService.kt`,
`MainActivity.kt` (`requestPermission`), `AndroidManifest.xml` ; tests
`ExternalBlocksTest`, `PhoneCalendarsUiTest`._

État : étape 1, import d'un fichier `.ics` (issue #136), **implémentée en
3.10.0**. Étape 2, calendriers du téléphone sur Android (issue #138) :
**implémentée en 3.10.0** (`READ_CALENDAR` facultative, acceptée par le
propriétaire le 2026-10-24). Suivi : #140. Choix produit validés
par le propriétaire le 2026-10-24 ; à l'implémentation, le 2026-10-24 encore :
une journée entière devient un **badge** sans bloquer de temps, un créneau
importé se **supprime** seul (il ne se modifie pas), les fuseaux passent par
les `VTIMEZONE` du fichier **sans table** Windows → IANA.

**Rouvert le 2026-10-26 par `connexions-en-ligne.md`** (4.0.0, décidée, à implémenter) : toute affirmation « sans permission réseau » de
cette spec vaut **jusqu'à la 3.x**. La 4.0.0 ajoute la permission `INTERNET` (un seul APK, fonctions en ligne désactivées par défaut :
lien ICS, CalDAV, Microsoft 365 et Google par OAuth, nouvelles versions d'Android). Les étapes 1 (fichier) et 2 (téléphone) ne changent pas
et restent sans réseau.

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Les réunions vivent dans le calendrier de l'utilisateur, souvent Outlook au
travail. Aujourd'hui il les recopie à la main en créneaux occupés, sinon le
planning pose du travail ou du deep work par-dessus une réunion. Il faut
que Kairos connaisse ces événements **sans** qu'on lui confie un compte et
**sans** que l'application Android demande l'accès au réseau.

L'ancienne version Python avait un connecteur TimeTree (réseau). Il a été retiré le
2026-09-28 (`docs/plan-v3-kotlin.md` § 2.2), et `ordonnancement.md` classait
depuis les « événements de calendrier externes » hors périmètre. Cette spec
**rouvre cette ligne**, pour des événements qui arrivent par un fichier ou
par le calendrier du téléphone, jamais par le réseau.

### Paliers

| Palier | Ce que l'utilisateur obtient | Permission Android | Où |
|---|---|---|---|
| Fichier | Importer un fichier `.ics` exporté de son calendrier, sur bureau, Android et web | aucune | ici, étape 1 (#136) |
| Calendriers du téléphone | Les événements des calendriers du téléphone, lus en direct (Outlook, Google, DAVx⁵, ICSx⁵…) | lecture du calendrier, demandée à l'activation | ici, étape 2 (#138) |
| Fichier lié | Un fichier Kairos tenu à jour dans un dossier synchronisé | aucune | `export-import.md` (#137) |
| Réseau | Connexion en ligne à un compte ou à un lien de calendrier | réseau | écarté jusqu'à la 3.x ; rouvert le 2026-10-26 pour la 4.0.0 : `connexions-en-ligne.md` (#139) |

### Comportement attendu (utilisateur)

#### Importer un fichier `.ics` (étape 1)

- **Obtenir le fichier**, rappelé par l'aide de la carte « Calendriers » :
  Outlook classique « Fichier → Enregistrer le calendrier » ; Outlook sur le
  web « Publier un calendrier » puis ouvrir le lien ICS ; Google Agenda
  « Importer et exporter → Exporter » ; Thunderbird, Nextcloud, Proton,
  Apple : « Exporter ».
- **Deux entrées** : le bouton « Importer » de la carte Données reconnaît
  un fichier iCalendar par son contenu (pas par son extension), et la carte
  « Calendriers » (Réglages → Données) propose « Importer un calendrier… ».
- **Aperçu avant toute écriture** :
  - nom du calendrier, modifiable, prérempli avec le nom inscrit dans le
    fichier, sinon « Calendrier importé » (« Réimporter » le préremplit avec
    le calendrier visé) ;
  - case « Masquer les titres » (les créneaux s'appellent alors
    « Occupé »), cochée comme lors du dernier import de ce calendrier ;
  - « N créneaux du JJ/MM au JJ/MM » ;
  - « Événements sur la journée entière : N, montrés en badge sans bloquer de
    temps » ;
  - les événements écartés, par raison : libres ou « travaille ailleurs »,
    annulés, passés, au-delà de 6 mois, répétition non prise en charge,
    fuseau horaire inconnu (avec son nom), illisibles, au-delà de la limite ;
  - si un calendrier de ce nom existe déjà : « Remplace les M créneaux à
    venir de « Nom » » ;
  - « Importer » et « Annuler ». Un fichier sans aucun événement retenu n'a
    que « Fermer ».
- **Ce que devient un événement** : un ou plusieurs créneaux **occupés**,
  à l'heure locale de l'appareil :
  - un événement répété donne un créneau par occurrence, d'aujourd'hui à
    **6 mois** au plus (exceptions et occurrences déplacées respectées) ;
  - un événement qui traverse minuit est découpé par jour ;
  - un événement « journée entière » (congés, déplacement, anniversaire…),
    libre ou occupé, devient un **badge** sur le jour (vue Jour sous
    « Aujourd'hui, dans l'ordre », en-tête du jour de la vue Semaine, icône
    dans la case de la vue Mois) : il ne bloque aucun temps et ne change ni le
    planning, ni la capacité, ni la charge de « moi » ;
  - un événement provisoire ou « absent » compte comme occupé ;
  - un événement libre (ou « travaille ailleurs »), annulé ou entièrement
    passé est écarté.
- **Ce qui est gardé** : le titre (ou « Occupé »), le début et la fin. La
  description, le lieu et les participants ne sont **jamais** stockés.
- **Réimporter** le même calendrier (même nom, casse ignorée) remplace ses
  créneaux **à partir d'aujourd'hui** et ses badges qui ne sont pas finis ;
  ceux du passé restent (trace des journées). Les créneaux saisis à la main
  et les autres calendriers ne sont jamais touchés.
- **Une sauvegarde automatique** précède l'import, comme pour tout import :
  sans elle, rien n'est écrit.
- **Créneaux importés en lecture seule, sauf la suppression** : dans la liste
  des créneaux de la vue Jour, ils portent une icône de calendrier et
  « Importé de « Nom » », et un bouton « Supprimer le créneau importé » à la
  place de « Modifier » ; la confirmation dit « « Titre » reviendra au
  prochain import de « Nom » ». La vue Semaine leur met l'icône de
  calendrier. Pour les corriger, on modifie le calendrier d'origine puis on
  réimporte.
- **Carte « Calendriers »** (Réglages → Données, entre la carte Données et
  « Réglages par défaut », sur toutes les plateformes) : « Les événements
  importés deviennent des créneaux occupés, ceux sur la journée entière un
  badge sur le jour. Kairos ne se connecte à aucun compte. », l'aide pour
  obtenir le fichier, « Importer un calendrier… », puis chaque calendrier
  importé avec « N créneaux à venir · importé le JJ/MM », « Réimporter »
  (choisir un fichier, puis l'aperçu qui vise ce calendrier) et « Retirer »
  (confirmation « Retirer « Nom » et ses N créneaux ? », passés et badges
  compris). Un fichier qui n'est pas un calendrier : « Ce fichier n'est pas
  un calendrier (.ics). »
- **Fuseau horaire** : les heures sont converties dans le fuseau de
  l'appareil au moment de l'import, puis figées. Après un changement de
  fuseau, on réimporte.
- Les créneaux importés comptent partout comme des créneaux occupés : le
  planning du jour, la vue Semaine, la charge de « moi » dans l'espace
  Équipe. Ils figurent dans l'export de données et en reviennent à
  l'identique. Ils ne partent jamais dans un paquet ou un rapport d'équipe.
  Le bouton « Importer » de l'écran Équipe → Échanges ne les importe pas :
  « Ce fichier est un calendrier, pas un échange d'équipe. »

#### Calendriers du téléphone (étape 2, Android)

- Carte « Calendriers », section « Calendriers du téléphone » : interrupteur
  « Utiliser les calendriers du téléphone ». La demande d'autorisation du
  système n'apparaît **qu'à ce geste** ; refuser laisse Kairos tel quel.
- Une fois autorisé : la liste des calendriers du téléphone (nom, compte),
  **aucun coché par défaut**.
- Les événements des calendriers cochés apparaissent comme des créneaux
  occupés en **lecture seule**, avec « Calendrier du téléphone « Nom » » :
  mêmes règles qu'à l'import (libres, annulés, refusés écartés ; journée
  entière en badge ; provisoire occupé ; d'il y a un mois à dans 6 mois).
- Ils sont **lus en direct**, jamais stockés, jamais exportés : un
  changement fait dans Outlook ou Google apparaît au retour dans Kairos.
- Un même événement reçu à la fois par un fichier importé et par le
  téléphone n'apparaît qu'une fois (celui du téléphone, plus récent).
- Autorisation retirée dans le système : « Kairos n'a plus accès aux
  calendriers du téléphone. » avec « Ouvrir les réglages du système » ; plus
  aucun créneau du téléphone.
- Outlook pour Android ne copie ses calendriers dans le téléphone que si
  son réglage « Synchroniser les calendriers » est activé (désactivé par
  défaut), et une organisation peut l'interdire. L'aide de la carte le dit
  et renvoie vers l'import de fichier.
- Bureau et web : la section n'existe pas.

### Contraintes

- **Aucune permission réseau sur Android, aucun compte, aucun secret
  stocké** (jusqu'à la 3.x ; rouvert pour la 4.0.0 par `connexions-en-ligne.md`).
  C'est un choix de Kairos, pas une règle de F-Droid (§ 2,
  Décisions) : il garde l'application sans anti-fonctionnalité F-Droid.
- `core` reste pur : le fuseau de l'appareil et la date du jour sont des
  paramètres.
- Dépendances libres seulement ; ici, aucune nouvelle dépendance.
- Mode solo intact : un utilisateur qui n'importe rien a un export
  identique à l'octet près.

### Critères de succès

Étape 1 (vérifiés) :

- Un fichier Outlook classique (fuseau Windows décrit dans le fichier,
  réunion hebdomadaire avec une occurrence déplacée et une supprimée, un
  « libre », un provisoire), un fichier Google (heures UTC, « 2e mardi »,
  journée entière, annulé, transparent, passé) et un fichier
  Thunderbird/Nextcloud (ligne repliée, échappements, `DURATION`, chemin de
  fuseau Mozilla) donnent les créneaux attendus, heure par heure, y compris
  la semaine du changement d'heure (`CalendarImportTest`).
- Répétitions : quotidienne en semaine, hebdomadaire multi-jours avec
  intervalle et `WKST`, « 2e mardi », « dernier vendredi », « dernier jour
  ouvré du mois » (`BYSETPOS`), le 31 qui saute les mois courts,
  `BYMONTHDAY` négatif, annuelle par mois, `COUNT` compté depuis le début,
  `UNTIL` inclus, série sans fin commencée il y a des années ; parties non
  prises en charge refusées (`IcsRuleTest`) ; `EXDATE`, `RDATE`,
  occurrences déplacées et annulées, découpage à minuit, identifiant sans
  `UID` (`CalendarImportTest`).
- Lecture : lignes repliées, échappements, paramètres entre guillemets, `CRLF`
  et `LF`, marque d'ordre des octets (`IcsParserTest`) ; `VTIMEZONE` suivi
  jour par jour comme la base IANA, chemin Mozilla, fuseau inconnu jamais
  deviné (`IcsZonesTest`, `CalendarImportTest`).
- Libres (et « travaille ailleurs »), annulés, passés, au-delà de 6 mois :
  écartés et comptés par raison ; journée entière : un badge
  (`CalendarImportTest`).
- Réimporter le même fichier le même jour ne change rien ; réimporter plus
  tard remplace les créneaux à venir du calendrier, garde son passé et ne
  touche ni les créneaux manuels ni ceux d'un autre calendrier ; « Masquer
  les titres » est retenu ; « Retirer » supprime tout le calendrier ; un
  créneau importé ne se modifie pas mais se supprime
  (`CalendarImportRepositoryTest`).
- Aucun import sans sauvegarde réussie (`CalendarImportUiTest`).
- Export d'une base sans calendrier importé identique à l'octet près ; une
  base avec calendriers importés s'exporte et se réimporte à l'identique ;
  une source absente ou une journée à l'envers sont refusées ; migration
  10 → 11 sans créneau modifié (`CalendarImportRepositoryTest`).
- Aperçu, import par « Importer » et par la carte, « Réimporter » qui vise le
  calendrier, « Masquer les titres », « Retirer », « Fermer » seul pour un
  fichier sans rien à importer, badge et suppression d'un créneau importé
  dans la vue Jour (`CalendarImportUiTest`).
- Un fichier de 5 000 événements est analysé en moins de 2 s sur le bureau
  (`CalendarImportTest`), hors du fil d'interface (`planCalendar`,
  `Dispatchers.Default`).
- Textes FR/EN (`StringsParityTest`) ; 360 dp sans débordement ; cibles de
  48 dp (captures `calendar-*` de l'auto-test).
- Version web, dans Chromium à partir de la sortie compilée : un fichier
  Outlook (fuseau Windows et son `VTIMEZONE`) s'importe aux bonnes heures
  après le changement d'heure, un `TZID` IANA sans `VTIMEZONE` est écarté et
  nommé dans l'aperçu, les créneaux et badges apparaissent dans la vue
  Semaine (vérifié à la main le 2026-10-25).

Étape 2 :

- Mêmes règles qu'à l'import (libres, annulés, refusés écartés ; journée
  entière en badge ; découpage à minuit) ; un créneau importé de même `UID` et
  même début qu'un créneau du téléphone disparaît au profit de celui-ci
  (`ExternalBlocksTest`).
- Interrupteur, aucun calendrier coché d'office, créneau du téléphone dans la
  vue Jour sans « Modifier » ni « Supprimer », rien en base ; accès refusé ou
  retiré : message et « Ouvrir les réglages du système »
  (`PhoneCalendarsUiTest`, service simulé).
- À vérifier à la main sur un appareil : DAVx⁵ ou ICSx⁵, Outlook
  « Synchroniser les calendriers », autorisation retirée dans le système (la
  CI compile l'APK, aucun test n'y lit un vrai fournisseur de calendrier).

### Hors périmètre / différé

- **Connexion réseau** (abonnement à un lien ICS, CalDAV, Microsoft 365) :
  hors périmètre jusqu'à la 3.x, **rouvert le 2026-10-26 par `connexions-en-ligne.md`** (4.0.0). Jusque-là : pas de permission
  réseau sur Android, aucune connexion à un compte.
  L'abonnement à un lien ICS **sur le bureau seulement** est une piste
  différée, à rouvrir sur demande (#139). Sur Android, ICSx⁵ s'abonne à ce
  lien et l'étape 2 en lit le résultat.
- Écrire dans un calendrier (créer une réunion, poser un créneau de deep
  work dans Outlook).
- Convertir un créneau importé en créneau manuel (piste notée, non retenue
  pour l'instant).
- Choisir quels types d'événements deviennent du deep work (catégories
  Outlook « Focus ») : tout événement retenu est un créneau occupé.
- Écarter les réunions refusées d'un fichier `.ics` : un export ne dit pas
  quel participant est l'utilisateur. Les exports Outlook ne contiennent
  d'ailleurs pas les réunions refusées.
- Calendriers du système sur le bureau (macOS, Windows).
- Synchronisation entre appareils (#88) et fichier lié (#137,
  `export-import.md`).

## 2. Solution technique

### Modèle (`core/model`)

- `TimeBlock.source: BlockSource?` (nul par défaut : créneau manuel, comme
  avant) ; `TimeBlock.imported` = `source != null`.
- `sealed interface BlockSource { val uid: String }` :
  - `Imported(calendarId: Long, uid: String)` : créneau d'un calendrier
    importé, stocké ;
  - `Device(calendarName: String, uid: String)` (étape 2) : créneau d'un
    calendrier du téléphone, jamais stocké.
  - `uid` : propriété `UID` de l'événement iCalendar (même valeur pour
    toutes les occurrences d'une série) ; avec `start`, il identifie une
    occurrence pour le dédoublonnage.
- `CalendarSource(id, name, hideTitles, importedAt, createdAt)` ;
  `KairosSnapshot.calendarSources` (liste vide par défaut).
- `DayMark(id, title, start, end, source: BlockSource, createdAt)` : journée
  entière, du `start` au `end` inclus (`covers(jour)`) ;
  `KairosSnapshot.dayMarks`. `DayView.dayMarks`, `WeekDay.marks`,
  `MonthDay.marks` : les marques qui couvrent le jour, triées par titre ; sans
  effet sur le planning, la capacité ni le « jour libre ».
- Un créneau avec `source` non nul a toujours `kind = BUSY` et
  `recurrence = NONE`.

### Lecture iCalendar (`core/calendar`, pur, sans dépendance)

- `IcsParser.parse(text): IcsComponent` : retire un BOM, accepte `CRLF` et
  `LF`, déplie les lignes (RFC 5545 § 3.1), lit nom, paramètres (deux-points
  et points-virgules entre guillemets compris) et valeur ; `IcsParser.text`
  déséchappe (`\n`, `\,`, `\;`, `\\`). Rend l'arbre du premier `VCALENDAR`
  (`IllegalArgumentException` sinon) ; propriétés et composants inconnus sont
  gardés dans l'arbre mais jamais lus. Propriétés de `VEVENT` lues : `UID`,
  `SUMMARY`, `DTSTART`, `DTEND`, `DURATION`, `RRULE`, `RDATE`, `EXDATE`,
  `RECURRENCE-ID`, `STATUS`, `TRANSP`, `X-MICROSOFT-CDO-BUSYSTATUS` ; du
  calendrier : `X-WR-CALNAME`. Description, lieu, participants et alarmes ne
  sont jamais lus. Un `VEVENT` sans `UID` reçoit `<DTSTART brut>/<titre>`.
- Détection : `IcsParser.isCalendar(text)` = premier contenu non vide
  `BEGIN:VCALENDAR` (casse ignorée), testée **avant** l'aiguillage JSON
  (`readImportedFile`).
- `IcsTime` : `Day` (date seule), `Utc` (`…Z`), `Zoned` (`TZID`), `Floating`.
  `icsDuration` lit `P1W`, `P1DT2H30M`, `-PT15M`.
- `IcsZones.resolve(tzid): EventZone?`, dans l'ordre :
  1. un `VTIMEZONE` de même identifiant dans le fichier (`VTimeZone` :
     décalage du dernier changement passé, règles `STANDARD` / `DAYLIGHT`
     avec `TZOFFSETFROM`, `TZOFFSETTO`, `DTSTART`, `RRULE` annuelle dépliée
     par `IcsRule` ou `RDATE` ; une heure locale est convertie en deux
     passes). Couvre les noms Windows d'Outlook sans table ;
  2. `TimeZone.of(tzid)`, puis `TimeZone.of` du dernier segment
     `Région/Ville` d'un chemin (`/mozilla.org/…/Europe/Paris`) ;
  3. sinon inconnu : l'événement est écarté (`UNKNOWN_ZONE`, nom gardé dans
     `CalendarPlan.unknownZones`).
  Heure UTC : instant direct ; heure flottante et date seule : heure locale de
  l'appareil, telle quelle. L'instant est ensuite converti dans le fuseau de
  l'appareil (`deviceZone`, paramètre).
- `IcsRule.parse(value)` (sous-ensemble de RRULE) : `FREQ` (`DAILY`,
  `WEEKLY`, `MONTHLY`, `YEARLY`), `INTERVAL`, `COUNT`, `UNTIL`, `BYDAY` (avec
  rang en mensuel et annuel : `2TU`, `-1FR`), `BYMONTHDAY` (négatif compris),
  `BYMONTH`, `BYSETPOS`, `WKST` ; toute autre partie (`BYWEEKNO`,
  `BYYEARDAY`, `BYHOUR`…, fréquence infra-journalière) → `null`, l'événement
  est écarté (`UNSUPPORTED_RULE`). `expand(début, until, from, to)` : débuts en
  **heure locale du fuseau de l'événement** (une réunion de 9 h reste à 9 h
  après le changement d'heure), `DTSTART` toujours premier ; sans `COUNT`, le
  calcul part de la période qui précède `from` ; au plus 2 000 débuts et
  20 000 périodes ; le 31 d'un mois court n'existe pas (RFC 5545).
- `CalendarImport.plan(text, today, deviceZone): CalendarPlan` : fenêtre
  `[today 00:00, today + 6 mois[` (`HORIZON_MONTHS`).
  - Écartés et comptés **par événement** (`skipped: Map<SkipReason, Int>`) :
    `FREE` (`TRANSP:TRANSPARENT`, `X-MICROSOFT-CDO-BUSYSTATUS` `FREE` ou
    `WORKINGELSEWHERE`), `CANCELLED`, `PAST` (aucune occurrence qui finit
    après le début de la fenêtre), `BEYOND_HORIZON`, `UNSUPPORTED_RULE`,
    `UNKNOWN_ZONE`, `INVALID` (sans début lisible, durée nulle ou négative),
    `TOO_MANY` (au-delà de `MAX_EVENTS` = 10 000 créneaux, et autant de
    badges, les plus lointains).
  - Gardés comme occupés : `TENTATIVE`, `OOF`.
  - `EXDATE` et `RECURRENCE-ID` sont ramenés à l'heure locale du fuseau de la
    série ; un `VEVENT` avec `RECURRENCE-ID` retire l'occurrence visée de sa
    série et est importé comme un événement seul (annulé : rien).
  - Journée entière (`DTSTART;VALUE=DATE`, libre ou occupée) :
    `PlannedDay(start, end inclus, title, uid)`, durée de `DTEND` (exclu),
    sinon de `DURATION`, sinon un jour.
  - Créneau : `PlannedEvent(start, end, title, uid)` en heure de l'appareil,
    découpé à minuit (`splitAtMidnight`) ; un morceau qui commence avant
    aujourd'hui n'est pas importé (réimporter remplace tout ce qui commence à
    partir d'aujourd'hui).
  - Titre : `SUMMARY` déséchappé, une ligne, 200 caractères au plus
    (`MAX_TITLE`).
  - Résultat : `name` (`X-WR-CALNAME`), `events`, `days` (sans doublon,
    triés), `skipped`, `unknownZones`.
- `ImportedCalendars` : `of(snapshot, today)` (par nom : créneaux qui
  commencent à partir d'aujourd'hui, badges pas finis), `named(snapshot, nom)`
  (casse et espaces ignorés), `replaced(snapshot, nom, today)` (créneaux à
  venir que réimporter remplacerait).

### Stockage (`data`)

- Schéma 11 (`10.sqm`, additif ; marqueur `calendar_source` de
  `KairosStore`) :
  - table `calendar_source(id, name, hide_titles, imported_at, created_at)` ;
  - `time_block.source_id INTEGER` et `time_block.source_uid TEXT`, nuls,
    en fin de table (ordre de `insertTimeBlockWithId`, positionnel) ;
  - index `time_block_source` sur `source_id` ;
  - table `day_mark(id, title, start, end, source_id, source_uid,
    created_at)`, dates `AAAA-MM-JJ`.
  Les bases existantes : tous les créneaux restent manuels.
- `KairosRepository.importCalendar(name, hideTitles, plan, today): Long?`
  (nul si le nom est vide) : une transaction. Elle trouve
  (`ImportedCalendars.named`) ou crée le `calendar_source` du nom, supprime
  ses créneaux de début ≥ `today 00:00` (comparaison de texte `AAAA-MM-JJTHH:MM`)
  et ses journées de fin ≥ `today`, insère ceux du plan (titres vides si
  `hideTitles`), met à jour `hide_titles` et `imported_at`.
  `removeCalendar(id)` supprime la source, tous ses créneaux et ses journées.
  `updateBlock` et les exceptions d'occurrence (`skipOccurrence`…)
  refusent un créneau importé ; `deleteBlock` l'accepte (choix du
  propriétaire).
- Sauvegarde préalable : `saveBackup(services, "avant-import-calendrier",
  …)` dans `CalendarImportDialog` ; sans sauvegarde, rien n'est écrit.
- `ExportCodec` : `calendarSources` et `dayMarks` (listes nullables, absentes
  si vides), par créneau `sourceId` et `sourceUid` (absents si nuls),
  `hideTitles` écrit seulement s'il est vrai. Un export sans calendrier ne
  change pas d'un octet. `formatVersion` inchangé : un lecteur plus ancien
  ignore ces champs (`ignoreUnknownKeys`), lit les créneaux importés comme des
  créneaux manuels et ne voit aucun badge. Validation : identifiants uniques,
  nom non vide, une source qui n'existe pas ou une journée à l'envers sont
  refusées (`CORRUPTED`).

### Interface (`ui`, étape 1)

- `readImportedFile` (bouton « Importer » de la carte Données) :
  `IcsParser.isCalendar` d'abord → `ImportedFile.Calendar(plan)`, qui ouvre
  `CalendarImportDialog` ; plan illisible → « Fichier illisible ». Les deux
  appels de `readImportedFile` (Réglages, Équipe → Échanges) tournent sous
  `Dispatchers.Default`. Équipe → Échanges : `hub_calendar_file`.
- `CalendarsCard` (`settings/CalendarImport.kt`), catégorie Données, après la
  carte Données : texte, aide, « Importer un calendrier… » (`planCalendar` :
  analyse hors du fil d'interface ; pas un calendrier →
  `calendars_not_calendar`), liste des calendriers (`ImportedCalendars.of`)
  avec « Réimporter » et « Retirer » (`AlertDialog`).
- `CalendarImportDialog(services, plan, onDone, presetName)` : l'aperçu décrit
  en partie 1 ; « Masquer les titres » reprend le choix du calendrier que le
  nom désigne ; « Remplace… » suit le nom saisi ; message
  « Calendrier « Nom » importé : N créneaux. ».
- `DayBlockList` : badge « Importé de « Nom » » (icône `Event`), bouton
  « Supprimer le créneau importé » ; `DayScreen` ouvre alors
  `ImportedBlockDeleteDialog` au lieu de `BlockDialog`. Nom du calendrier :
  `LocalCalendarNames` (identifiant → nom), fourni par `KairosApp` ;
  `sourceName(source)` ; titre vide d'un créneau importé : « Occupé »
  (`blockTitle`).
- `DayMarkBadges` (`day/DayMarks.kt`) : une pastille neutre par marque,
  icône `Event`, titre ou « Toute la journée » ; vue Jour sous le titre
  « Aujourd'hui, dans l'ordre », vue Semaine sous la date. Vue Mois : icône
  `Event` dans la case, les titres dans sa description d'accessibilité
  (`day_marks_description`), et la légende le dit. Créneau importé dans la
  vue Semaine : icône `Event` sur sa pastille.
- Sélecteurs : `accept=".json,.ics,application/json,text/calendar"` (web) ;
  types `text/calendar`, `text/x-vcalendar` (Android).
- Carte du guide `calendar_import` (chapitre Organiser, `since = "3.10.0"`,
  ouvre Réglages → Données).

### Calendriers du téléphone (étape 2)

- `DeviceEvent` (`core/calendar`) : un événement lu dans le système
  (calendrier, `uid`, titre, début et fin en instants, journée entière,
  libre, annulé, refusé). `ExternalBlocks.map(événements, fuseau)` :
  `DeviceCalendarData(blocks, marks)` ; mêmes règles que `CalendarImport`
  (libres, annulés, refusés, durée nulle écartés ; journée entière en
  `DayMark`, ses dates lues en UTC comme Android les donne, fin exclue ;
  créneau découpé à minuit par `CalendarImport.splitAtMidnight`) ;
  identifiants négatifs, uniques dans la liste ; source
  `BlockSource.Device(calendrier, uid)`.
- `ExternalBlocks.merge(snapshot, data)` : `snapshot.copy` avec les créneaux et
  journées du téléphone ajoutés ; un créneau (une journée) importé de même
  `uid` et de même début est retiré. Pur, dans `core`.
- `CalendarService` (`ui/app`, `AppServices.calendars`, nul sur le bureau,
  le web et dans les tests) : `state` (`DeviceCalendarState` : `Off`,
  `Denied`, `On(calendars, selected)`), `data`, `enable()`, `disable()`,
  `select(ids)`, `openSystemSettings()`.
- `withDeviceCalendars(services, snapshot)` (composable) : seuls les écrans qui
  **calculent** un planning l'appellent : `DayScreen`, `WeekScreen`,
  `MonthScreen`, `TeamMembersScreen` (charge de « moi »), `ForecastScreen`.
  L'export, l'import, les échanges d'équipe et l'édition lisent toujours
  `repository.snapshot` : les créneaux du téléphone ne peuvent être ni
  écrits ni exportés.
- `DayBlockList` : badge « Calendrier du téléphone « Nom » » (icône `Event`,
  `sourceName`), ni « Modifier » ni « Supprimer » ; vue Semaine : icône
  `Event` comme un créneau importé. Couleur : celle d'un créneau occupé,
  aucune couleur de calendrier.
- `PhoneCalendarsCard` (`settings/CalendarImport.kt`, sous la carte
  « Calendriers ») : aide, interrupteur (`SwitchRow`), cases des calendriers
  (« Nom · compte »), état `Denied` avec « Ouvrir les réglages du système ».

### Android (`androidApp`, étape 2)

- `AndroidCalendarService` : `READ_CALENDAR` au manifeste, demandée par
  `MainActivity.requestPermission` (`ActivityResultContracts.RequestPermission`)
  seulement depuis l'interrupteur ; refus : l'interrupteur reste éteint.
- Lecture de `CalendarContract.Calendars` (`VISIBLE = 1` : nom, compte) et de
  `CalendarContract.Instances` sur `[maintenant − 31 jours, maintenant + 184
  jours]`, hors du fil principal (`Dispatchers.IO`), pour les calendriers
  cochés seulement. `Instances` déploie déjà les répétitions, les exceptions
  et les fuseaux : aucun code de répétition ici.
- Colonnes : `TITLE` (une ligne, 200 caractères), `BEGIN`, `END`, `ALL_DAY`,
  `AVAILABILITY` (`AVAILABILITY_FREE` écarté), `STATUS` (`STATUS_CANCELED`
  écarté), `SELF_ATTENDEE_STATUS` (`ATTENDEE_STATUS_DECLINED` écarté),
  `UID_2445` (sans UID : `event-<EVENT_ID>`), `CALENDAR_DISPLAY_NAME`.
- Interrupteur et sélection dans les préférences de l'application
  (`phone-calendars`), **jamais** dans `Settings` ni dans l'export.
- Rafraîchissement : à l'ouverture des services, à chaque reprise de
  l'activité (`MainActivity.onResume`) et par un `ContentObserver` sur
  `CalendarContract.Events.CONTENT_URI` (descendants compris), regroupé
  (1 s). Autorisation retirée constatée → `Denied`, plus aucun créneau,
  observateur retiré.

### Décisions et pièges tracés

- **F-Droid n'interdit pas le réseau** (vérifié le 2026-10-24 sur
  l'[Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/) et
  les [Anti-Features](https://f-droid.org/en/docs/Anti-Features/)).
  - Exigé : code et chaîne de compilation libres, dépendances compilées
    depuis les sources, aucune bibliothèque propriétaire de pistage,
    publicité ou mesure d'audience. Une anti-fonctionnalité non déclarée
    entraîne un refus.
  - Pas interdit : `INTERNET`, comptes, OAuth.
  - Étiqueté, pas exclu : `NonFreeNet` (dépend d'un service propriétaire :
    un connecteur Microsoft seul), `TetheredNet` (service irremplaçable),
    `Tracking` (appel réseau sans accord ou actif par défaut).
  - L'absence de `INTERNET` est un **choix de Kairos**, pour n'avoir aucune
    anti-fonctionnalité (`plan-v3-kotlin.md` § Android, `fdroid/README.md`).
- **Décision du 2026-10-24 : pas de permission réseau sur Android, aucune
  connexion à un compte** (#139) ; **rouverte le 2026-10-26** (voir la décision suivante). Microsoft Graph demanderait une
  application Entra tenue par le projet ; beaucoup d'organisations
  bloquent le consentement des utilisateurs (« Need admin approval »), et
  le connecteur serait vraisemblablement étiqueté `NonFreeNet`. Le
  téléphone (étape 2) donne l'effet « compte connecté » : le réseau reste
  dans l'application de calendrier (Outlook, DAVx⁵, ICSx⁵). L'abonnement
  à un lien ICS sur le bureau seul, qui fait déjà un appel réseau
  (`mises-a-jour.md`), est différé.
- **Rouvert le 2026-10-26 par `connexions-en-ligne.md`** (propriétaire, 4.0.0) : un seul APK avec `INTERNET`, toutes les
  connexions en ligne désactivées par défaut, lecture seule, rien dans l'export. Ce qui précède reste l'historique de la 3.x. Ce qui change
  pour F-Droid : `NonFreeNet` est déclaré dès que l'APK embarque un connecteur dédié à Microsoft ou Google ; jamais d'anti-fonctionnalité
  non déclarée. L'abonnement à un lien ICS, différé « sur le bureau seulement » ci-dessus, devient une connexion de toutes les plateformes
  (sur le web au mieux).
- **Occurrences déployées plutôt que récurrences Kairos** : Kairos n'a que
  quotidien, jours ouvrés et hebdomadaire, sans exception (#108, #110),
  alors que les exports Outlook sont pleins d'exceptions. Coût : un créneau
  par occurrence (un point quotidien sur 6 mois ≈ 130 lignes),
  négligeable. Écarté : traduire les RRULE simples en `BlockRecurrence`
  (deux chemins, exceptions perdues).
- **Horizon de 6 mois** : couvre la vue Semaine et la future vue Mois
  (#107) ; borne les séries sans fin. Au-delà, on réimporte.
- **Fuseaux par les `VTIMEZONE` du fichier, d'abord** : RFC 5545 impose un
  `VTIMEZONE` pour chaque `TZID` cité ; Outlook et Google les écrivent.
  Cela évite une table Windows → IANA et une base de fuseaux embarquée sur
  le web. Écartés : une table CLDR `windowsZones.xml` générée (essayée à
  l'implémentation puis retirée, confirmé par le propriétaire le
  2026-10-24 : un fichier généré de plus pour un cas rare) et ical4j (JVM
  seulement, inutilisable en wasm). La conversion vers le fuseau de
  l'appareil passe par `TimeZone.currentSystemDefault()`, déjà utilisée par
  les écrans sur les trois plateformes. Sur wasmJs, `TimeZone.of(<IANA>)`
  (repli 2) peut ne connaître que le fuseau du système et UTC
  (kotlinx-datetime sans base de fuseaux) : un `TZID` IANA sans `VTIMEZONE`
  y est alors écarté (« fuseau horaire inconnu »), jamais deviné.
- **Calendrier reconnu par son nom** : `FileService.openText` ne rend pas
  le nom du fichier, et un `.ics` n'a pas d'identifiant stable de
  calendrier. Le nom vient de `X-WR-CALNAME`, modifiable dans l'aperçu.
  Changer le nom crée un autre calendrier, ce que l'aperçu montre.
- **Réimport à partir d'aujourd'hui** : les créneaux passés sont la trace
  des journées vécues ; un nouvel export commence souvent à aujourd'hui et
  les effacerait.
- **Lecture seule, sauf la suppression** (propriétaire, 2026-10-24) : un
  créneau modifié serait écrasé au réimport sans prévenir ; une réunion
  annulée tard se supprime, et la confirmation dit qu'elle reviendra au
  prochain import. La conversion en créneau manuel reste une piste.
- **Table `calendar_source` plutôt qu'un réglage** : les métadonnées
  (nom, masquage, date) appartiennent aux données importées, pas aux
  préférences ; `Settings` passe par le formulaire des Réglages.
  « Retirer » devient une suppression par clé.
- **Créneaux du téléphone non stockés** : ils changent dans l'application
  de calendrier ; une copie vieillirait et partirait dans l'export.
  `merge` au moment du calcul garde `core` pur et le dépôt inchangé.
- **Dédoublonnage par `uid` et début**, au profit du téléphone : c'est la
  source vivante. Sans début, une série entière disparaîtrait.
- **Fusion dans `core`, pas dans `ui/app`** : pure, elle se teste avec le reste
  du calendrier ; l'interface n'en garde que l'appel composable.
- **Observateur sur `Events`** plutôt que sur `Instances` : les fournisseurs
  notifient les changements d'événements ; `Instances` est une vue calculée.
- **Pas d'état « autorisation à demander »** : l'autorisation est demandée au
  geste ; refusée, rien ne change ; retirée ensuite, c'est `Denied`.
- **Journée entière = badge, pas un créneau** (propriétaire, 2026-10-24,
  remplace « un créneau par jour, de 00:00 au lendemain ») : un congé posé
  « toute la journée » ne vide pas le planning ; c'est à l'utilisateur de
  déclarer le jour libre s'il l'est. Libre ou occupée, elle est gardée :
  Google marque ses journées entières « disponible » par défaut, les écarter
  perdrait congés et déplacements ; un badge ne gêne pas.
- **Découpage à minuit** : la vue Jour retient un créneau ponctuel qui
  chevauche la journée (`start < lendemain && end > début`, `DayView`) ; la
  vue Semaine range les créneaux par jour de début. Un morceau commencé
  avant aujourd'hui n'est pas importé, sinon le réimport (qui remplace ce qui
  commence à partir d'aujourd'hui) le doublerait.
- **Le dialogue d'aperçu reste ouvert pendant l'import** (désactivé, puis
  fermé à la fin) : fermé d'abord, sa portée de coroutines était annulée et
  l'écriture interrompue là où la base suspend (version web, worker SQL),
  constaté dans Chromium le 2026-10-25 ; les tests du bureau, où la base
  répond sans suspendre, ne le voyaient pas.
- **Raisons comptées par événement**, pas par occurrence : « 1 annulé » se
  lit mieux que le nombre de ses occurrences.
- **Identité d'un calendrier = son nom, casse ignorée** : Outlook classique
  écrit souvent « Calendrier » pour tous les comptes ; renommer dans
  l'aperçu sépare deux calendriers.
- **Provisoire et absent comptés occupés, « travaille ailleurs » libre** :
  ce que l'agenda d'Outlook montre comme indisponible.
