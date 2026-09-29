# Publication de Kairos 3 sur F-Droid et IzzyOnDroid

Dossier de soumission, à dérouler par le propriétaire du dépôt **après la release
`v3.0.0`** (une préversion n’est proposée à aucun des deux dépôts : elle porte
l’identifiant `com.skohscripts.kairos.preview`). Conception et décisions :
`docs/spec/publication.md`.

## Ce que le dépôt fournit déjà

| Élément | Emplacement |
|---|---|
| Recette F-Droid | `fdroid/com.skohscripts.kairos.yml` |
| Fiches FR/EN (titre, descriptions, icône, captures, notes de version) | `fastlane/metadata/android/{fr-FR,en-US}/` |
| APK signé | asset `Kairos-android.apk` de chaque release GitHub `vX.Y.Z` |
| Vérification continue | `.github/workflows/fdroid-check.yml` (lint, construction, et après une release finale comparaison à l’APK publié) |

Fiche d’identité de l’application :

- identifiant : `com.skohscripts.kairos` (celui de Kairos 2 : la 3 s’installe par-dessus) ;
- certificat de signature, SHA-256 :
  `3885399c1116bea6fed50ce144a1cb10d516bc74d9c9e0773dad23a4e8fa2111` ;
- licence MIT ; aucune dépendance non libre, aucun service Google, aucun traqueur ;
- permissions : `POST_NOTIFICATIONS` (chrono et alertes),
  `RECEIVE_BOOT_COMPLETED` (reprogrammer les alertes au redémarrage) ;
  **pas** d’accès réseau ;
- anti-fonctionnalités : aucune.

## F-Droid (fdroiddata)

1. Vérifier que le workflow « F-Droid » est vert sur le tag `v3.0.0` (exécution
   déclenchée par la fin de « Kairos 3 release ») : l’APK reconstruit dans l’image
   des serveurs F-Droid est identique à l’APK publié.
2. Forker https://gitlab.com/fdroid/fdroiddata, copier la recette dans
   `metadata/com.skohscripts.kairos.yml`, ouvrir une merge request
   (modèle « App inclusion »).
3. Les versions suivantes sont détectées seules (`UpdateCheckMode: Tags`,
   `AutoUpdateMode: Version`) : rien à refaire à chaque release, tant que le tag
   final `vX.Y.Z` pointe sur un commit où `kmp/gradle.properties` porte cette version.

Grâce à `Binaries` et `AllowedAPKSigningKeys`, F-Droid distribue **notre** APK
signé : les utilisateurs peuvent passer de GitHub à F-Droid (et inversement) sans
désinstaller.

## IzzyOnDroid

1. Ouvrir une demande d’inclusion sur https://codeberg.org/IzzyOnDroid/repo/issues
   (modèle « App inclusion request ») avec :
   - dépôt : https://github.com/SKOHscripts/Kairos ;
   - APK : asset `Kairos-android.apk` des releases (les préversions sont marquées
     « pre-release » et doivent être ignorées) ;
   - métadonnées : Fastlane, `fastlane/metadata/android/` ;
   - certificat SHA-256 ci-dessus ;
   - construction reproductible : recette et vérification F-Droid ci-dessus.
2. Rien à faire aux versions suivantes : IzzyOnDroid relève les nouvelles releases.
