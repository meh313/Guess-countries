# World Flags & Continents

An offline Android app for learning world geography: browse 33 countries, drill them with flashcards,
and test yourself with quizzes. Everything runs on the device; there is no network access.

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

## Project layout

| Path | What lives there |
| --- | --- |
| `quiz/` | Pure quiz rules (`QuizEngine`) and the immutable `QuizSession`; no Android types. |
| `flashcards/` | Deck ordering for the flashcard screen. |
| `speech/` | Text-to-speech behind a small `Speech` interface. |
| `data/model/` | The country catalog and `CountryRepository`. |
| `data/local/` | Room database; progress and quiz history. Schemas are exported to `app/schemas/`. |
| `ui/` | Compose screens, components, theme and `CountryViewModel`. |

`CountryViewModel` receives its repository, speech engine and clock through the constructor, which is how
the tests replace them.

## Flag artwork

Each flag is a lossless WebP in `res/drawable-nodpi/flag_xx.webp`, rendered from the MIT-licensed
[flag-icons](https://github.com/lipis/flag-icons) SVGs (see `THIRD_PARTY_NOTICES.md`) and looked up by country code in
`FlagArt.kt`. A country without artwork gets a placeholder with its flag emoji, and a test fails until it has art.

## Dependencies

The versions in `gradle/libs.versions.toml` are the newest that build against compileSdk 36. Compose BOM 2026.08+, navigation 2.10+, lifecycle 2.11+ and core 1.19+ need compileSdk 37, so
Dependabot updates to those fail until compileSdk (and probably AGP) is raised on purpose. androidx.test 1.7
breaks Robolectric 4.16, so core and runner stay on 1.6.
