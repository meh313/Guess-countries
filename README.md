# World Flags & Continents

An offline Android app for learning world geography: browse all 196 countries of the world plus Antarctica,
drill them with flashcards, and test yourself with quizzes. Everything runs on the device; there is no network access.

## Features

- **Explore** – search, filter by continent or Saved, sort by name, population, area or continent, open a detail
  sheet and have it read aloud.
- **Flashcards** – flip through the deck (it follows the Explore filters), shuffle it, and grade yourself.
  A *Weak spots* deck holds the countries you have practiced but not yet mastered, weakest first.
- **Quiz** – flag → country, country → flag (pick the right flag out of four), country → capital,
  flag → continent (always the whole world), a 10-second-per-question speed round and a 60-second blitz
  (as many flags as you can). Ten questions or fewer, with a streak bonus. *Answers: Normal* puts one
  look-alike flag among the wrong answers, *Hard* as many as exist (the choice is remembered). The *Weak spots* scope quizzes you on
  the ten countries you know least.
- **Stats** – a daily practice streak (a day counts when you finish one quiz; days follow the device's time zone),
  mastery per continent and a log of recent quizzes.

## Build

Requires JDK 21 and the Android SDK (compileSdk 37, targetSdk 36). The Gradle wrapper picks the right Gradle version.

```sh
./gradlew assembleDebug        # debug APK
./gradlew build                # lint, unit tests and both APKs, as CI runs it
./gradlew testDebugUnitTest    # unit and Robolectric UI tests only
```

The unit tests include Compose UI tests that drive the real activity under Robolectric, so no emulator is
needed.

### Release builds

`./gradlew assembleRelease` always works and produces an unsigned, R8-shrunk APK. To sign it, provide a
keystore (alias `upload`) and its passwords:

```sh
export KEYSTORE_PATH=/path/to/upload.jks   # defaults to ./my-upload-key.jks
export STORE_PASSWORD=...
export KEY_PASSWORD=...
export VERSION_CODE=42                     # optional; defaults to 1, must be a positive integer
./gradlew assembleRelease
```

The keystore and its passwords are never read from the repository.

### Publishing a release

Releases are built, signed and published by the **Release** workflow (`.github/workflows/release.yml`),
which reads the upload keystore and its passwords from the repository secrets `KEYSTORE_BASE64`,
`STORE_PASSWORD` and `KEY_PASSWORD`. To ship a version:

1. Bump `versionName` in `app/build.gradle.kts` (for example `"1.6"`) and merge it to `main`.
2. Open Actions > Release > Run workflow on `main`. The run creates the tag `v1.6`, builds and signs the
   APK, checks the certificate, alignment and version, and publishes a release with the APK, its SHA-256
   and the R8 mapping. Pushing a tag `v1.6` does the same.

`versionCode` is derived from the version (`major*10000 + minor*100 + patch`, so `1.6` is `10600`), which
keeps it increasing without a manual bump. Tick "dry run" to build and verify without publishing; the files
are then kept as a workflow artifact for a week.

## Project layout

| Path | What lives there |
| --- | --- |
| `quiz/` | Pure quiz rules (`QuizEngine`) and the immutable `QuizSession`; no Android types. |
| `progress/` | Pure practice-streak rules (`PracticeStreak`), computed from when quizzes finished. |
| `flashcards/` | Deck ordering for the flashcard screen. |
| `speech/` | Text-to-speech behind a small `Speech` interface. |
| `data/model/` | The country catalog, one file per continent (`EuropeCatalog.kt`…, alphabetical), and `CountryRepository`. |
| `data/local/` | Room database; progress and quiz history. Schemas are exported to `app/schemas/`. |
| `ui/` | Compose screens, components, theme and `CountryViewModel`. |
| `tools/` | Content pipeline: flag rendering (`render-flags.mjs`, `to-webp.py`, `contact-sheet.py`), catalog generation (`validate-research.py`, `gen-entries.py`, `apply-edits.py`, `gen-flag-art.py`, `flag-checksums.py`), audit input (`export-entries.py`), quiz data (`gen-lookalikes.py` writes `FlagLookAlikes.kt`). |
| `content/` | Provenance for the catalog: `seed/` (country lists and owner rules), `research/` (first-pass entries with sources), `audit/` (second-pass fixes, doubts and owner decisions per continent). |

`CountryViewModel` receives its repository, speech engine and clock through the constructor, which is how
the tests replace them.

## Flag artwork

Each flag is a lossless 600x450 WebP in `res/drawable-nodpi/flag_xx.webp`, rendered unstretched from the
MIT-licensed [flag-icons](https://github.com/lipis/flag-icons) SVGs (`tools/render-flags.mjs` and
`tools/to-webp.py` reproduce them) and looked up by country code in `FlagArt.kt`. Flags are shown in a 4:3 box
(`FlagAspectRatio`), the shape of the artwork. A country without artwork gets a placeholder with its flag emoji,
and a test fails until it has art. The licence text is in `licenses/THIRD_PARTY_NOTICES.md`, which is also
packaged into the APK's assets.

## Dependencies

The versions in `gradle/libs.versions.toml` are the newest that build against compileSdk 37; targetSdk stays
at 36 so Robolectric keeps running at a supported level. androidx.test 1.7 breaks Robolectric's setup, so
core and runner stay on 1.6 (see `.github/dependabot.yml`).
