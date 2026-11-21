# Jours fériés : pays intégrés et calendrier importé

_Rôle : dire à Kairos quels jours sont fériés pour l'utilisateur, au-delà de la France, sans les saisir chaque année.
Fichiers couverts (voie A) : `core/engine/PublicHolidays.kt`, `core/engine/HolidayTable.kt` (généré), `core/engine/Workdays.kt`,
`core/model/Settings.kt` (`holidayCountry`, `holidayCountryCode`, `defaults`), `core/settings/SettingsForm.kt` (`holidayCountry`,
`FieldKind.CHOICE`, `restoreDefaults`), `ui/settings/SettingsFormCards.kt` (sélecteur de pays), `ui/settings/HolidayCountries.kt`,
les plateformes (région du système), `kmp/tools/make_holidays.py`, `.github/workflows/kmp-tag.yml`, `NOTICE`.
Fichiers couverts (voie B) : `14.sqm`, `Kairos.sq` (`counts_as_holidays`, `updateCalendarHolidays`), `KairosStore` (marqueur 15),
`core/model/TimeBlock.kt` (`CalendarSource.countsAsHolidays`), `Mappers.kt`, `ExportCodec.kt`, `core/sync/SyncRecords.kt`,
`KairosRepository` (`importCalendar`, `connectCalendar`, `setCalendarHolidays`), `Workdays.holidaysFor(jour, instantané)`, tous ses
appelants, `ui/settings/CalendarImport.kt`, `ui/settings/OnlineConnectionsCard.kt`, `OnlineConnections.addIcs`.
Tests : `PublicHolidaysTest`, `HolidayCountrySettingsTest`, `kmp/tools/test_make_holidays.py`, `ImportedHolidaysTest`,
`CalendarHolidaysTest`._

État : issue [#264](https://github.com/SKOHscripts/Kairos/issues/264). **Voie A (pays intégrés) et voie B (calendrier importé compté
comme fériés) implémentées pour la 4.4.0**, en deux PR (décision du propriétaire du 2026-11-21).

## 1. Besoin métier (cahier des charges)

### Objectif / problème

Kairos ne connaissait que les fériés **français** (un interrupteur « Jours fériés français » et une liste libre de dates). Il est traduit en
six langues : un utilisateur en Allemagne, en Espagne, au Royaume-Uni ou au Canada avait les fériés français ou aucun, et devait taper chaque
date chaque année. Or les fériés pilotent beaucoup : tâches et créneaux « jours ouvrés » (`recurrence.md`), « Décaler », vues Jour, Semaine
et Mois, Bilan, capacité et charge de l'équipe (`equipe-charge.md`), prévisions (`equipe-simulation.md`, `previsions-taches.md`).

« Je ne suis pas en France. Je veux que Kairos connaisse les fériés de mon pays, pour que les récurrences “jours ouvrés”, mon planning et
la capacité de mon équipe les sautent, sans les taper chaque année. »

### Comportement attendu (utilisateur)

**Voie A : pays intégrés** (hors ligne, sans compte, rien à télécharger)

- Réglages → Journée et horaires → Jours fériés : un choix **« Pays »** remplace l'interrupteur « Jours fériés français » : « Aucun », ou
  un pays parmi Allemagne, Autriche, Belgique, Canada, Espagne, États-Unis, France, Irlande, Italie, Luxembourg, Royaume-Uni, Russie,
  Suisse (les pays des six langues), triés par leur nom dans la langue de l'interface. Les dates libres restent dessous, en plus.
- Ce sont les fériés **nationaux** (communs à tout le pays), jours de remplacement compris quand le pays décale un férié tombé un week-end
  (« observed » des États-Unis, du Royaume-Uni, du Canada ; ponts décrétés en Russie, quand ils sont déjà publiés).
- **Nouvelle installation** : le pays de l'appareil (région du système) s'il est dans la liste, « Aucun » sinon. **Installation
  existante** : la France si « Jours fériés français » était activé, « Aucun » sinon ; rien ne change sans action.
- « Restaurer les valeurs par défaut » **garde le pays** : c'est un fait sur l'utilisateur, pas une préférence.
- Le pays se synchronise entre appareils comme les autres réglages partagés (et comme l'interrupteur qu'il remplace).

**Voie B : fériés d'un calendrier importé**

- Sur un calendrier importé et gardé (fichier `.ics`, lien ICS, CalDAV), un interrupteur **« Compter ses journées entières comme fériés »**,
  sur sa ligne (Réglages → Données → Calendriers), changeable à tout moment ; et une case du même nom dans l'aperçu d'un import ou d'un
  ajout par lien, si le calendrier a des journées entières, cochée d'emblée seulement quand le calendrier de ce nom l'était.
- Toutes ses journées entières, une plage de plusieurs jours comprise, sont alors sautées comme des fériés, partout où le pays l'est.
- Les calendriers du téléphone (Android, lus en direct, jamais gardés) n'y entrent pas (décision du propriétaire du 2026-11-21).
- Couvre ce que les règles ne peuvent pas : fermetures de l'entreprise, vacances scolaires, région (Alsace-Moselle, Länder, cantons…),
  pays absent de la liste, années que les règles ne prévoient pas.

### Critères de succès

- Chaque pays de la liste donne, pour 2026, les dates de python-holidays (`PublicHolidaysTest.everyCountryMatchesTheSource2026`).
- La France donne les fériés de l'ancienne règle (fixes, lundi de Pâques, Ascension, lundi de Pentecôte) de 2000 à 2050, sauf le lundi
  de Pentecôte de 2005 à 2007 (« journée de solidarité » travaillée, que la source ne compte pas, à raison) : les fichiers d'or et les
  scénarios existants ne bougent pas (`franceMatchesTheFormerRule`, `DifferentialTest`).
- Une base ou un export d'avant (`holidaysFr` seul) garde son comportement ; un export d'après reste lisible par une version d'avant
  (`HolidayCountrySettingsTest`).
- Une nouvelle installation en Allemagne a les fériés allemands sans rien régler (`defaultsFollowTheRegion`).
- La table est régénérée à chaque version : le tag d'une version refuse une table périmée et propose sa mise à jour (CI).

### Hors périmètre / différé

- Fériés **régionaux** (Alsace-Moselle, Länder, communautés espagnoles, cantons suisses, États américains) et un pays différent par membre
  d'équipe : la voie B couvre déjà une région par un calendrier importé.
- Les **samedis travaillés** de Russie (jours ouvrés reportés) : Kairos n'a pas de week-end travaillé ; seuls les jours chômés comptent.
- Les noms des fériés : seules les dates comptent, comme avant.
- Années hors de la table (avant 2000, après 2050) : aucun férié de pays ; la table se régénère bien avant.

## 2. Solution technique

### Table générée (`kmp/tools/make_holidays.py`, `core/engine/HolidayTable.kt`)

- Source : **python-holidays** (licence MIT), lue au développement et dans la CI, **jamais à l'exécution** (aucun accès réseau, aucune
  dépendance ajoutée à l'application). `holidays.country_holidays(code, years=…)` sans subdivision, `observed` par défaut : les fériés
  nationaux et leurs jours de remplacement. Un férié de dimanche (Pâques, Pentecôte) reste dans la table, sans effet.
- `python3 kmp/tools/make_holidays.py` écrit `HolidayTable.kt` : une constante par pays, une ligne par année, `AAAA MMJJ MMJJ …`, de
  `FIRST_YEAR` = 2000 à `LAST_YEAR` = 2050, et la version de python-holidays en tête (`SOURCE`). Fichier versionné, lisible en diff.
- `--check` régénère en mémoire et compare **les dates seulement** (pas la ligne de version) : code 0 si la table est à jour, 1 sinon.
- `kmp/tools/test_make_holidays.py` (lancé par `metadata.yml`, sans python-holidays installé) : format des lignes, comparaison sans la
  version, refus d'un pays inconnu.

### Recalcul à chaque version (`.github/workflows/kmp-tag.yml`)

Décision du propriétaire du 2026-11-21 : la table est recalculée à chaque version, dans la CI. L'étape « Fériés à jour » du workflow « Kairos
tag » installe la dernière python-holidays et lance `make_holidays.py --check`. Table périmée : le workflow régénère le fichier, ouvre la
PR `holidays/<version>` (« chore(holidays): refresh public holidays »), lance ses contrôles, et **refuse le tag** ; une fois la PR fusionnée,
on relance « Kairos tag ». La table reste versionnée dans le dépôt : un build depuis le tag (F-Droid, reproductible) donne le même fichier.

### Calcul (`core/engine/PublicHolidays.kt`, pur)

- `PublicHolidays.COUNTRIES` : les 13 codes ISO 3166-1 alpha-2 ; `isSupported(code)`.
- `dates(country, year)` : les dates de la table (analysées une fois par pays, à la demande) ; vide hors table ou pour un code inconnu.
- `defaultFor(region)` : le code en majuscules s'il est pris en charge (`UK` lu comme `GB`), `""` sinon.
- `Workdays.buildHolidays(years, country, extra)` et `Workdays.holidaysFor(today, country, extra)` prennent le **code du pays** (nul = aucun)
  au lieu du booléen français ; tous les appelants passent `settings.holidayCountryCode`. L'ancienne règle française (`frenchPublicHolidays`)
  est retirée : elle sert d'oracle à `PublicHolidaysTest`. `easterSunday` reste (tests, oracle).

### Réglage (`core/model/Settings.kt`, `core/settings/SettingsForm.kt`)

- `holidayCountry: String?` : `null` = jamais choisi, `holidaysFr` décide (anciennes bases et exports) ; `""` = aucun ; sinon un code.
  `holidayCountryCode` rend le code effectif (`"FR"` si `null` et `holidaysFr`, nul pour aucun ou pour un code inconnu).
- Champ de formulaire `holidayCountry`, nature `FieldKind.CHOICE` (`SettingField.choices` = `""` puis les codes) : lu au code effectif,
  écrit en posant aussi `holidaysFr = (code == "FR")`, pour qu'une version d'avant qui lit un export ou le fichier lié garde la France. Une
  valeur inconnue (import) devient `""`, jamais une erreur. Le champ `holidaysFr` quitte le formulaire (il reste dans le modèle).
- `Settings.defaults(language, region)` : `region` nul (démonstration, tests) garde le comportement d'avant ; sinon `holidayCountry =
  defaultFor(region)`. Plateformes : Android `Resources.getSystem().configuration.locales[0].country`, bureau `Locale.getDefault().country`,
  web la région de `navigator.language` (`fr-BE` → `BE` ; sans région, « Aucun »).
- `SettingsForm.restoreDefaults(values)` garde `holidayCountry` de `values`, comme les champs `team.*`.
- Synchronisation : réglage partagé (hors `SyncScope.LOCAL_SETTINGS`), comme `holidaysFr`.

### Interface (`ui/settings/SettingsFormCards.kt`, `ui/settings/HolidayCountries.kt`)

- La section « Jours fériés » liste `holidayCountry` puis `extraHolidays`. `SettingInput` rend un champ `CHOICE` par `ChoiceInput`
  (`HolidayCountries.kt` : `ExposedDropdownMenuBox` Material 3, ancré sous le champ, pleine largeur) ; libellés par
  `holidayCountryName(code)` (`holiday_country_none`, `holiday_country_<code>`), « Aucun pays » d'abord, puis les pays triés par
  leur nom dans la langue de l'interface.
- Catalogue : carte `holiday_countries` (« Les jours fériés de ton pays », depuis 4.4.0, toutes plateformes) ; le texte de la carte
  `holidays` ne dit plus « français ».
- Textes : `setting_holiday_country`, `setting_holiday_country_help`, les 13 noms de pays et « Aucun pays », `card_holiday_countries_*`,
  dans les six langues ; `setting_holidays_fr` retiré.

### Voie B : calendrier importé compté comme fériés

- **Stockage** (décision du propriétaire du 2026-11-21 : sur le calendrier, pas dans les réglages) : colonne
  `calendar_source.counts_as_holidays` (`14.sqm`, schéma 15, 0 pour les calendriers existants ; marqueur `counts_as_holidays` en tête de
  `KairosStore.MARKERS`). `CalendarSource.countsAsHolidays`. Les fixtures qui reconstruisent un ancien schéma depuis le schéma courant
  retirent aussi la colonne (`ALTER TABLE calendar_source DROP COLUMN counts_as_holidays`), sinon son marqueur saute les migrations.
- **Export** : `countsAsHolidays` n'est écrit que s'il est vrai (comme `hideTitles`) ; un export d'avant se lit faux.
- **Synchronisation** : champ `holidays` de l'enregistrement du calendrier, **toujours écrit** (`true` / `false`) : écrit seulement à
  vrai, un retour à faux ne gagnerait jamais contre un appareil qui l'a encore (un champ absent d'un côté prend la valeur de l'autre). Un
  enregistrement d'avant la 4.4.0 n'a pas le champ : faux.
- **Dépôt** : `importCalendar(…, holidays)` et `connectCalendar(…, holidays, beforeCommit)` posent le choix dans la même transaction ;
  nul garde celui d'un calendrier existant (faux pour un nouveau). `setCalendarHolidays(id, choix)` pour l'interrupteur de la ligne.
- **Calcul** (`Workdays.holidaysFor(jour, instantané)`, `buildHolidays(années, instantané)`) : les fériés des réglages, plus chaque jour
  des `day_mark` des calendriers cochés, coupés à la fenêtre (année d'avant à deux ans après). Tous les appelants passent l'instantané
  qu'ils ont déjà (vues Jour, Semaine, Mois, Bilan, prévisions, espace Équipe, conseils, dépôt, coach de récurrence) : aucun ne garde la
  seule version « réglages ».
- **Interface** : `SwitchRow` sur la ligne du calendrier (`CalendarsCard`) ; `LabeledCheckbox` dans `CalendarImportDialog` et dans
  l'aperçu d'un lien ICS (`OnlineConnectionsCard`, `OnlineConnections.addIcs(…, holidays)`), seulement si le plan a des journées
  entières ; dans l'aperçu CalDAV, une case **par calendrier** qui a des journées entières (plusieurs calendriers s'ajoutent d'un coup
  et n'ont pas le même rôle : « Fériés Alsace » oui, « Travail » non), `OnlineConnections.addCalDav(…, holidays)` recevant les
  adresses cochées (`CalDavConnectionsTest`). Texte `calendar_holidays` dans les six langues ; carte du catalogue `holiday_calendars` (« Les fériés d'un calendrier »,
  depuis 4.4.0, toutes plateformes).

### Décisions et pièges tracés

- **Table de dates plutôt que règles** (2026-11-21, propriétaire) : exacte, ponts russes et jours « observed » compris ; écartées des
  règles écrites à la main (plus de code, et les jours décidés au cas par cas resteraient faux).
- **Recalcul en CI au tag** (2026-11-21, propriétaire) : écarté un calcul au build (un build F-Droid depuis les sources n'aurait pas la
  même table : build non reproductible).
- **Défaut par la région du système** (2026-11-21, propriétaire) ; écartés la France pour tous et « Aucun » pour tous.
- **`holidaysFr` gardé et tenu à jour** : écarté son retrait, qui ferait perdre la France aux versions d'avant lisant un fichier récent.
- **Pays gardé par « Restaurer les valeurs par défaut »** : écartée la remise à la France, absurde pour un utilisateur allemand.
- **Le calendrier porte le choix** (2026-11-21, propriétaire) : il suit un renommage et disparaît avec le calendrier ; écartée une
  liste de noms dans les réglages (sans migration, mais perdue au renommage).
- **Calendriers du téléphone exclus** (2026-11-21, propriétaire) : lus seulement par l'écran, ils n'entrent dans aucun calcul.
- **2000–2050** : de quoi couvrir l'historique des statistiques et les prévisions ; `holidaysFor` ne lit que de l'année d'avant à deux ans
  après.
