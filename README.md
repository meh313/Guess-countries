# World Flags & Continents

An offline Android app for learning world geography: browse all 196 countries of the world plus Antarctica,
drill them with flashcards, and test yourself with quizzes. Everything runs on the device; there is no network access.

## Features

- **Explore** – search, filter by continent or Saved, sort by name, population, area or continent, open a detail
  sheet and have it read aloud.
- **Flashcards** – browse the deck (it follows the Explore filters) with Previous and Next, shuffle it, and
  *study*: a session of up to 20 cards from the flags due today, the weak spots or this deck, each graded
  Again / Good / Easy after the flip (Again brings the card back a few places later; the last grade can be undone).
  A session of ten or more cards counts as practice for the streak.
- **Quiz** – flag → country, country → flag (pick the right flag out of four), country → capital,
  flag → continent (always the whole world), a 10-second-per-question speed round and a 60-second blitz
  (as many flags as you can). Ten questions or fewer, with a streak bonus; the questions favour countries you have
  not seen or that are due for review, and avoid the targets of the last few quizzes. *Answers: Normal* puts one
  look-alike flag among the wrong answers, *Hard* as many as exist (the choice is remembered). The *Due today* scope
  reviews what is due, the *Weak spots* scope the ten countries you know least (each in the skill the mode exercises:
  capitals for the capital quiz). The score screen lists every miss with the flag you picked beside the right one,
  and offers *Retry* and *Practise mistakes*; a wrong text answer shows the two flags side by side at once. A quiz
  survives the app being killed in the background.
- **Settings and About** – theme (system, light, dark) and wallpaper colours on Android 12+, the pace of the timed
  quizzes (normal, relaxed, no limit), *Export* and *Import* of all progress and settings as a JSON file, a reset of
  all progress, and an About page with where the facts come from, the catalog's rules, the licences and the privacy
  terms. A country's detail sheet lists the pages its facts were checked against (`Sources`), for the countries that
  went through the research pass, and a *Your progress* row per skill; every country card carries two pips (flag,
  capital) and Explore has Unseen / Learning / Mastered chips, which a tap on a continent on the Progress screen sets.
- **Stats** – a daily practice streak (a day counts when you finish one quiz or a study session of ten cards; days
  follow the device's time zone), an optional daily reminder (a notification at a time you pick, only on days without
  a finished quiz; it asks for the notification permission on Android 13 and newer), mastered flags and capitals,
  what is due today, mastery per continent and a log of recent quizzes and study sessions.

### How mastery works

Each country is tracked per skill (its flag, its capital) on a Leitner schedule: a confidence score from 0 to 100
and a box from 1 to 5 whose review intervals are 1, 3, 7, 14 and 30 days. A right quiz answer raises the score and,
when the review was due, moves the box up; an answer before the due date keeps its schedule, so nothing is mastered
in one sitting. A wrong answer sends the country back to box 1. Continent questions and flashcard grades are light
evidence: they move the score a little and never past 60. A flag counts as *mastered* at box 3 or higher with a score
of 75 or more, and lapses out of that if it goes unreviewed for twice its interval. *Weak spots* are the countries
reviewed but not (or no longer) mastered.

## Privacy

The app has no network permission. Everything stays on the device: progress in the app's own database, a few
settings in SharedPreferences, both named in the backup rules (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`)
and so included in Android's backup of the app. Settings > Export writes the same data to a file you keep; Import
replaces everything with such a file. There is no account and no analytics.

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
keeps it increasing without a manual bump. A file `release-notes/<version>.md`, when there is one, is appended
to the generated release notes. Tick "dry run" to build and verify without publishing; the files
are then kept as a workflow artifact for a week.

## Project layout

| Path | What lives there |
| --- | --- |
| `quiz/` | Pure quiz rules (`QuizEngine`, the weighted `QuestionPicker`), the immutable `QuizSession` and its `QuizSnapshot` for saved state; no Android types. |
| `progress/` | Pure rules: the practice streak (`PracticeStreak`), computed from when quizzes finished, and per-skill mastery (`SkillMastery`: score, Leitner box, due date). |
| `reminder/` | The daily reminder: pure schedule rules, the WorkManager scheduler and worker, the notification. |
| `data/settings/` | `UserSettings`: the few preferences kept in SharedPreferences (answers difficulty, reminder, theme, timer pace). |
| `data/transfer/` | `ProgressTransfer`: the export file format (JSON, format 1) and its reader. |
| `flashcards/` | Deck ordering for the flashcard screen and the immutable `StudySession` (queue, grades, Again re-insertion, undo). |
| `speech/` | Text-to-speech behind a small `Speech` interface. |
| `data/model/` | The country catalog, one file per continent (`EuropeCatalog.kt`…, alphabetical), `CountryRepository`, and `CountrySources` (the per-country source pages, read from the asset `sources.json`). |
| `data/local/` | Room database (version 2): bookmarks, skill states, quiz history with every answer. Schemas are exported to `app/schemas/`; `AppDatabase.MIGRATION_1_2` is tested against the committed version 1 schema. |
| `ui/` | Compose screens, components, theme and `CountryViewModel`, which hands progress, the quiz and the study session to the holders in `ui/viewmodel/`. |
| `tools/` | Content pipeline: flag rendering (`render-flags.mjs`, `to-webp.py`, `contact-sheet.py`), catalog generation (`validate-research.py`, `gen-entries.py`, `apply-edits.py`, `gen-flag-art.py`, `flag-checksums.py`), audit input (`export-entries.py`), quiz data (`gen-lookalikes.py` writes `FlagLookAlikes.kt`), the sources asset (`export-sources.py` writes `app/src/main/assets/sources.json` from the research and audit files; `--check` verifies it). |
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
