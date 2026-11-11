<!--
Title: in English, short, with the issue reference, e.g. "Week and month recap, clearer task sheet [#233]".
Fill every section in English (CLAUDE.md § Pull requests). Delete the comments and the lines that do not apply.
Never paste private data: no database export, no real task titles from a personal backup.
-->

## Summary

<!-- What changes for the user, in two to four sentences. -->

Closes #

## Changes

<!-- One line per logical change, ideally one per commit. -->

-

## Screenshots

<!-- Before / after for any visible change: light and dark, desktop and 360 dp phone width when relevant
(`./gradlew :desktopApp:run --args=--self-test=<dir>`). Check that nothing private shows. -->

## Decisions reopened

<!-- Decisions already traced in docs/spec/ that this PR changes, with the spec section and why. "None" if none. -->

## Spec and documentation

- [ ] Spec written or updated **before** the code (`docs/spec/`, two parts: business need, technical solution)
- [ ] Spec and code are bijective: every behaviour is described, nothing describes missing code; index `docs/spec/README.md` updated
- [ ] Small decisions traced ("Décisions et pièges tracés") and the local "why" in code comments
- [ ] User-facing feature only: one catalogue card (`FeatureCatalog`, `since`, platforms, six languages), one bullet in `README.md` **and** `README.fr.md`, one row of the feature list table (`FeatureCatalogTest`)
- [ ] Bug fix, refactor or internal decision: traced in the spec only, not in the README

## Code

- [ ] `core` stays pure (no Compose, platform, clock or I/O); UI only in `ui`
- [ ] No hard-coded UI text: strings in the six languages, typographic apostrophe, French non-breaking spaces (`StringsParityTest`)
- [ ] Design system followed (`docs/DESIGN_SYSTEM.md`): `MaterialTheme.colorScheme` roles only, Material 3 components, touch targets of 48 dp, no horizontal overflow at 360 dp
- [ ] Free dependencies only, versions in `kmp/gradle/libs.versions.toml`; no new network access (F-Droid, `docs/spec/connexions-en-ligne.md`)
- [ ] Solo mode untouched unless the Team space is the point (`TeamIsolationTest`); new settings are null by default and absent from exports until used
- [ ] Data format: migrations, export/import and sync considered if stored data changes

## Verification

- [ ] `cd kmp && ./gradlew :core:jvmTest :data:jvmTest :ui:jvmTest :desktopApp:jvmTest`
- [ ] Self-test captures rendered and looked at (`--self-test`)
- [ ] Store screenshots regenerated if a captured phone screen changed (`--store-screenshots`)
- [ ] Web build checked in Chromium from the compiled output (`docs/spec/architecture.md` § Décisions), or say why not
- [ ] Release notes: for a **final** version only, Fastlane changelogs in the six languages (`ReleaseNotesTest`)

## Commits

- [ ] English conventional commits, `type(scope): description [#<issue>]`, one logical change each, no AI attribution (skill `commit`)

## Notes for the reviewer

<!-- What is not done or not checked, known limits, strings to review by a native speaker, follow-ups. -->
