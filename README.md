# World Flags & Continents

An offline Android app for learning world geography: browse all 196 countries of the world plus Antarctica,
drill them with flashcards, and test yourself with quizzes. Everything runs on the device; there is no network access.

## Features

- **Explore** – search, filter by continent or Saved, sort by name, population, area or continent, open a detail
  sheet and have it read aloud.
- **Flashcards** – flip through the deck (it follows the Explore filters), shuffle it, and grade yourself.
- **Quiz** – flag → country, country → capital, flag → continent (always the whole world), and a
  10-second-per-question speed round. Ten questions or fewer, with a streak bonus.
- **Stats** – mastery per continent and a log of recent quizzes.

## Build

Requires JDK 21 and the Android SDK (compileSdk 36.1). The Gradle wrapper picks the right Gradle version.

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
| `flashcards/` | Deck ordering for the flashcard screen. |
| `speech/` | Text-to-speech behind a small `Speech` interface. |
| `data/model/` | The country catalog, one file per continent (`EuropeCatalog.kt`…, alphabetical), and `CountryRepository`. |
| `data/local/` | Room database; progress and quiz history. Schemas are exported to `app/schemas/`. |
| `ui/` | Compose screens, components, theme and `CountryViewModel`. |
| `tools/` | Content pipeline: flag rendering (`render-flags.mjs`, `to-webp.py`, `contact-sheet.py`), catalog generation (`validate-research.py`, `gen-entries.py`, `apply-edits.py`, `gen-flag-art.py`, `flag-checksums.py`), audit input (`export-entries.py`). |
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

The versions in `gradle/libs.versions.toml` are the newest that build against compileSdk 36. Compose BOM 2026.08+, navigation 2.10+, lifecycle 2.11+ and core 1.19+ need compileSdk 37, so
Dependabot updates to those fail until compileSdk (and probably AGP) is raised on purpose. androidx.test 1.7
breaks Robolectric 4.16, so core and runner stay on 1.6.
