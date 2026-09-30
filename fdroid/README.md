# Publication de Kairos 3 sur F-Droid

Dossier de soumission, à dérouler par le propriétaire du dépôt **après la release
`v3.0.0`** (une préversion n’y est jamais proposée : elle porte l’identifiant
`com.skohscripts.kairos.preview`). État au 2026-09-30 : merge request fdroiddata
acceptée, en test chez F-Droid. Conception et décisions :
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
   (modèle « App inclusion »). Le champ `commit` de la recette est le **hash
   complet** du commit que désigne le tag (exigence des relecteurs : ni tag ni
   branche, un tag pouvant être déplacé) ; pour `v3.0.0` :
   `f5784cb564d42825d3c71cee45e3ec026d453a08`
   (`git rev-parse v3.0.0^{commit}`).
3. Les versions suivantes sont détectées seules (`UpdateCheckMode: Tags`,
   `AutoUpdateMode: Version`) : le robot de F-Droid ajoute lui-même l'entrée de
   construction dans fdroiddata, rien à refaire à chaque release, tant que le tag
   final `vX.Y.Z` pointe sur un commit où `kmp/gradle.properties` porte cette
   version. La copie de la recette dans ce dépôt garde l'entrée de la 3.0.0 :
   `fdroid_check.sh` réécrit de toute façon la dernière entrée à la version et
   au commit testés.

Grâce à `Binaries` et `AllowedAPKSigningKeys`, F-Droid distribue **notre** APK
signé : les utilisateurs peuvent passer de GitHub à F-Droid (et inversement) sans
désinstaller.
