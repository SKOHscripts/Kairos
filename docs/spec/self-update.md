# Self-update (desktop)

_Role: let Kairos Desktop download, verify and install a new version by itself. Today the update banner only opens the GitHub release page.
Existing files that change: `core/updates/UpdateCheck.kt`, `core/model/Settings.kt`, `core/sync/SyncScope.kt`, `core/settings/SettingsForm.kt`,
`desktopApp/.../DesktopUpdates.kt`, `desktopApp/.../DesktopServices.kt`, `desktopApp/.../Main.kt`, `ui/.../app/Updates.kt`, `ui/.../settings/SettingsScreen.kt`, string resources.
New files (proposed names): `core/updates/ReleaseAssets.kt`, `desktopApp/.../UpdateInstaller.kt`, `desktopApp/.../UpdateVerifier.kt`.
Tests: see section 1, "Success criteria". All test names in this file are proposed._

State: issue #249. **Specified 2026-11-19, not implemented.**
This spec reopens one decision of `mises-a-jour.md`: "Téléchargement, vérification de somme et remplacement automatiques" were out of scope.

## 1. Functional needs

### Objective and problem

A user who sees the update banner must leave Kairos, open GitHub, pick the right file, download it and run the `.msi` by hand.
Many users stop at that point and stay on an old version.
Kairos must do these steps itself, and must stay safe and easy to switch off.

### Expected behavior (user)

- **Scope of the first version.** Windows, installed with the `.msi`. Every other case keeps today's banner with "Télécharger" (see "Out of scope").
- **Default mode: ask.** The banner is the one that exists today: "Kairos X.Y.Z est disponible (tu as A.B.C)." It has two buttons:
  - "Mettre à jour" (new; replaces "Télécharger" when self-update is possible): Kairos downloads the new version, checks it, closes, installs it and starts again.
  - "Plus tard": hides the banner for this version, as today. The next version shows the banner again.
- **Progress.** After "Mettre à jour", the banner shows the download progress and a cancel button. "Plus tard" or cancel stops the download and deletes the partial file.
- **Opt-out.** The existing switch "Vérifier les nouvelles versions" (Réglages → Général → Mises à jour, default on) stays the opt-out.
  When it is off, Kairos does not check, shows no banner and never downloads. "Vérifier maintenant" still works, as today.
- **Automatic mode.** A new switch, "Installer les mises à jour automatiquement au démarrage", is off by default. It is disabled while the check switch is off.
  - Kairos downloads and verifies the new version in the background while it runs. It never installs while it runs.
  - At the next start, before the first window, Kairos installs the verified version and starts again. The user is not asked.
  - In this mode the banner does not appear. The card shows "Kairos X.Y.Z sera installé au prochain démarrage." and a button "Redémarrer et mettre à jour".
- **Failure.** A failed download, check or install never blocks Kairos. The banner says the update failed and offers "Télécharger" (the release page, as today).
  In automatic mode, Kairos does not try the same version again at each start. A new version, or "Mettre à jour" in the banner, tries again.
- **Unsigned or unverifiable release.** Kairos never installs a file that fails a check. It falls back to today's banner with "Télécharger".
- **Privacy.** The only new traffic is the download of two release files from GitHub: the installer and `SHA256SUMS`. The API call of the check stays as today.
  Nothing else leaves the device. The sentence of the card help text (`update_help`) changes to say that Kairos can download and install.

### Success criteria

Each criterion names the proposed test that proves it.

- A release list gives the installer and `SHA256SUMS` asset for `windows-x64`. A URL outside `https://github.com/SKOHscripts/Kairos/releases/download/` is refused (`ReleaseAssetsTest`).
- A file with a wrong SHA-256, a missing line in `SHA256SUMS` or a wrong size is refused and deleted (`UpdateInstallerTest`).
- A file with no valid Authenticode signature, or a signer that is not the pinned publisher, is refused (`UpdateVerifierTest`, with a fake signature reader).
- Self-update is offered only for a Windows install that is not portable and not managed (`SelfUpdateEligibilityTest`).
- "Mettre à jour" goes through downloading, verifying and installing, and starts the helper before Kairos exits. If the helper does not start, Kairos stays open and shows the failure (`UpdateInstallerTest`).
- Automatic mode never installs while the application runs. It installs a staged and re-verified version at start, and does not retry a failed version (`UpdateInstallerTest`).
- With the check switch off, no request and no download happen (`DesktopUpdatesTest`).
- The banner shows "Mettre à jour" when self-update is possible and "Télécharger" otherwise (`M5ScreensUiTest`).
- The new setting is local to the device and is never merged by synchronization (`SyncScopeTest.everySettingIsClassified`).
- Every new string exists in the six languages (`StringsParityTest`).
- Manual proof, on a Windows machine: install an older `.msi`, publish a test release, use "Mettre à jour", and read the version after the restart.

### Out of scope

- **Portable zip** (`kairos-portable` marker). Reason: see "Decisions and pitfalls". The banner keeps "Télécharger".
- **Linux (`.deb`)** and **macOS (`.dmg`)**. Linux needs `root`. macOS has no signing yet (#116).
- **Managed installs** (`kairos-managed`, AUR). The package manager updates them. Kairos has no update service there, as today.
- **Android.** #144 is closed "not planned". Android stays without a version check.
- Delta updates, return to an older version, release notes in the app, a channel selector (a beta keeps reading pre-releases), and a background service.
- Microsoft Store (#215) and `winget`. They are other channels with their own updates.

## 2. Technical solution

### Eligibility

`SelfUpdate.eligible(osName, appPath, portable, managed)` (new, pure, in `desktopApp`) is true when all of these hold:
`osName` starts with "Windows", `jpackage.app-path` is set, `PortableCopy.detect` returns `null`, and `SystemPackage.detect()` is false.
A run from Gradle has no `jpackage.app-path`, so it is not eligible. `DesktopUpdates` receives the result as a constructor parameter and exposes it as `installable`.

### Settings

- `Settings.updateAutoInstall: Boolean = false`, next to `updateCheckEnabled`. A field with a default needs no data migration. Confirm against `modele-donnees.md` when implementing.
- Add `"updateAutoInstall"` to `SyncScope.LOCAL_SETTINGS`. Otherwise the field is shared by default and one device could turn on automatic installs on the others.
- `SettingsForm` gets one more `bool(...)`. `UpdatesCard` shows the new switch, disabled when `updateCheckEnabled` is false. The label is `setting_update_auto` (proposed).

### Release assets

- `UpdateCheck.parseFor` keeps today's rules. The release value it returns gains `assets`: a list of `name`, `browser_download_url` and `size` read from the API response.
- `ReleaseAssets.pick(assets, id)` (new, pure) returns the installer and `SHA256SUMS`. For `windows-x64` the installer is `Kairos-windows-x64.msi`, the name written by `kmp-release.yml`.
- A URL must start with `https://github.com/SKOHscripts/Kairos/releases/download/`. This is stricter than the `https://github.com/` rule for `html_url`, because Kairos now runs the file.
- A release without both assets is not installable. The banner then shows "Télécharger".

### Download and staging

- `DesktopUpdates.download(release, progress)` uses the existing `HttpClient`: system proxy, redirects, 10 s connect timeout. It does not use the 10 s total timeout of `httpGet`, because the body is large.
- The body streams to `<dataDir>/updates/<version>/Kairos-windows-x64.msi.part`. Kairos renames the file only after the size matches.
- A canceled or failed download deletes the `.part` file.
- At each start, Kairos deletes every folder under `<dataDir>/updates/` except the staged version, if its version is newer than the installed one.

### Verification

1. SHA-256 of the file equals the line for the file name in `SHA256SUMS` (`sha256sum` format: hash, two spaces, name).
2. Authenticode: `UpdateVerifier` reads the signature of the `.msi` and requires status `Valid` and a signer subject equal to the constant `SIGNER_SUBJECT`.
   The value of `SIGNER_SUBJECT` comes from the SignPath certificate, after the owner gets accepted (#117). It is unknown today.
3. Automatic mode runs both checks again at apply time. The file may have changed on disk after staging.

Step 1 guards against a damaged download only. `SHA256SUMS` comes from the same release as the installer, so a hostile release can fake both.
Step 2 is the authenticity guard. Self-update therefore stays off (banner with "Télécharger") until signed releases exist.

### Apply (Windows)

`UpdateInstaller.apply(msi)` writes `apply-update.ps1` into the staging folder and starts it:
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File apply-update.ps1 <pid> <msi> <Kairos.exe> <log>`.

The script does these steps:
1. Wait for process `<pid>` to exit, with a timeout of 60 s. The `SingleInstance` lock and the locked program files are released at that moment.
2. Run `msiexec /i <msi> /passive /norestart /l*v <log>` and wait. Exit code 0 or 3010 is a success.
3. Start `Kairos.exe` from the path in `jpackage.app-path`.
4. On any other exit code, write `update-failed.json` (`version`, `exitCode`) and still start `Kairos.exe`.

Kairos checks that the helper process is alive about one second after the start, and only then calls `exitApplication()`. Otherwise it stays open and reports the failure.
At the next start, `DesktopUpdates` reads `update-failed.json`, copies it to `updates.json` as `installFailed`, and deletes the file.

Facts that are not verified yet. The first implementation task is a prototype that proves them on Windows:
- the jpackage `.msi` upgrades in place with these flags, including the beta to final case of `distribution.md` § Cycle des versions;
- Windows Installer rolls a failed upgrade back to the installed version;
- `powershell.exe` can run the script on a managed Windows machine (execution policy, AppLocker).

### Automatic mode flow

- `UpdateWatcher`: when a check finds a newer release and `updateAutoInstall` is on, it calls `DesktopUpdates.stage()`: download, verify, then record `staged` in `updates.json`.
- At start, after `SingleInstance.acquire` and before the first window, Kairos reads the settings. If `updateAutoInstall` is on, `staged` is newer than `AppVersion.current`, and both checks pass again, it calls `apply` and exits.
- Today the database opens inside `application { }` in `Main.kt` (`DesktopServices.open`). The exact hook for this step is decided at implementation.
- `installFailed` equal to the staged version blocks the automatic apply for that version.

### State and API

- `updates.json` gets two optional fields: `staged` (version) and `installFailed` (version). Older files stay valid because `ignoreUnknownKeys` is on and the fields have defaults.
- `UpdateStatus` gets `Downloading(version, percent)`, `Ready(version)` (staged) and `Installing`. `Failed` keeps its meaning for the check.
- `UpdateService` gets `val installable: Boolean`, `suspend fun install()`, `suspend fun stage()` and `fun cancel()`. Android keeps `updates = null`.
- `UpdateBanner` calls `updates.install()` when `installable`, and opens the release URL otherwise.

### Interface and text

- Strings (all six languages, typographic apostrophe `’`): `update_install`, `update_downloading`, `update_installing`, `update_install_failed`, `update_pending`, `update_restart_now`, `setting_update_auto`. `update_help` changes.
- The progress bar and the cancel button have accessibility labels (`accessibilite.md`).
- The banner stays neutral (`surfaceContainerHigh`), as the design system requires. A failure uses the same banner with text, not the error container.

### Diagnostics

Log lines in the local diagnostic journal (`bureau-avance.md`): `update download ok|failed <code>`, `update verify sha=ok|bad sig=ok|bad|unsigned`, `update apply started|failed`. No file path and no URL in the log.

### Decisions and pitfalls

- **Stage in the background, install at the next start** (user request: never update an open application). The alternative, check and download at start, delays the first window by the download time.
- **Two switches, not one three-value setting.** `updateCheckEnabled` stays the opt-out, so existing users keep their choice and no migration is needed.
- **The new setting is local.** A shared setting would let one device turn on code installs on every linked device.
- **The portable zip is out of the first version.**
  - Replacing the files of a running folder needs a swap helper and handles its own locks.
  - `mise` installs this zip: `mise install github:SKOHscripts/Kairos@4.1.0` picked `Kairos-windows-x64-portable.zip` in a test run on 2026-11-19.
    A self-update would change files inside the versioned folder of `mise`, which breaks its bookkeeping.
  - Reopen this choice with a marker like `kairos-managed` for `mise` installs.
- **Quit first, then install.** The program files are locked while Kairos runs, and the single-instance lock would stop the new start. Hence the helper script.
- **No retry loop.** A failing version must not run `msiexec` at every start. `installFailed` stops it.
- **Alternatives rejected:**
  - Velopack or Squirrel: a second installer format and a new dependency, next to the signed `.msi` that exists.
  - MSIX or the Store: a separate decision (#215).
  - A Windows service or scheduled task: more permissions and more surface than the problem needs.
  - Install with a hash check only: a hostile release defeats it.
- **No new permission.** The desktop has no permission model. The only new network use is to `github.com`, as for the check.

### Open decisions for the owner

| Question | Proposed default |
|---|---|
| Pin `SIGNER_SUBJECT` and enable self-update | Wait for SignPath acceptance (#117); keep "Télécharger" until then |
| Support the portable zip | No, in the first version |
| Linux and macOS | No, in the first version; revisit with #116 |

### Impacts on existing specs (to apply at implementation)

- `mises-a-jour.md`: remove the "Hors périmètre" line on automatic download and replacement. Describe the new banner button, the new card switch, the new `UpdateService` members and the new states. Add the decision of 2026-11-19.
- `reglages.md`: the new switch and its dependency on the check switch.
- `synchronisation.md` and `modele-donnees.md`: `updateAutoInstall` in the local settings.
- `distribution.md`: the asset names and the signature are now a contract with the application. Add `SIGNER_SUBJECT` to § Signature Windows.
- `bureau-avance.md`: the `updates/` staging folder in the data folder, and the new diagnostic lines.
- `raccourci-portable.md`: one line, saying that portable copies do not self-update.
- `i18n.md` and `accessibilite.md`: the new strings and labels.
- `liste-fonctionnalites.md`, `README.md`, `README.fr.md` and `FeatureCatalog`: this is a user feature. Add the card, the bullet and the table row in both languages.
