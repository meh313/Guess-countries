# World Flags & Continents

An offline Android app for learning world geography: browse all 196 countries of the world plus Antarctica,
drill them with flashcards, and test yourself with quizzes. Everything runs on the device; there is no network access.

## Features

- **Explore** – search, filter by continent or Saved, sort by name, population, area or continent, open a detail
  sheet and have it read aloud.
- **Flashcards** – browse the deck (it follows the Explore filters) with Previous and Next, shuffle it, and
  *study*: a session of up to 20 cards from the flags due today, the capitals due today (flag and name on the
  front, the capital on the back), the weak spots or this deck, each graded Again / Good / Easy after the flip
  (Again brings the card back a few places later; the last grade can be undone). A session of five or more cards is
  logged and counts towards the daily goal.
- **Learn 5 new** – from the study chooser (or the Today card): the next five flags you have never seen, one card at
  a time with the flag, the name, the region, the cue to spot it and, where one exists, the flag it is most often
  confused with (and how to tell the two apart). The focus continent's flags come first. Seeing a card counts as
  light evidence, so the next session moves on to five others; *Quiz me* then runs a ten-question flag quiz over the
  five (each asked twice, wrong answers from the whole world), logged under *Learn*; it does not count against the
  day's allowance of new countries.
- **Quiz** – flag → country, country → flag (pick the right flag out of four), country → capital,
  flag → continent (always the whole world), map → country (the country is highlighted on the map, pick its name;
  the wrong answers are its neighbours), a 10-second-per-question speed round and a 60-second blitz
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
- **Progress** – the Today card (what is due, what you learnt today, the daily goal), a practice streak (a day
  counts when its sessions reach the goal or one of them has ten items; days follow the device's time zone), an
  optional daily reminder (an inexact alarm wakes the app at the time you pick and posts a notification, only on
  days short of the goal; a reboot or a time-zone change plans it again; it asks for the notification permission on
  Android 13 and newer), mastered flags and capitals, what is due today and in the coming week, how many countries
  are still being learnt, the pairs you confuse most (with their tell-apart tips), mastery per continent and a log
  of recent quizzes and study sessions; a quiz in the log opens its full result again.

### How mastery works

Each country is tracked per skill (its flag, its capital, its continent, its location) on a Leitner schedule: a confidence score
from 0 to 100 and a box from 1 to 5 whose review intervals are 1, 3, 7, 21 and 60 days. A right quiz answer raises the
score and, when the review was due, moves the box up; an answer before the due date keeps its schedule, so nothing is
mastered in one sitting. A wrong answer drops the country two boxes (never below box 1); a miss under time pressure
(Speed Round, Blitz) drops it one and costs half the score. The continent quiz has its own skill, so a right continent
pick never counts as knowing the flag; its answers and the flashcard grades are light evidence: they move the score a
little and never past 60. The map quiz feeds a fourth skill, *Location*, with its own due dates (the *Due today* and
*Weak spots* scopes follow it in that mode); it is shown on a country's detail sheet and on Progress, not as a card pip. Studying a card, *Again* drops it two boxes, *Good* reschedules a due card at its current
box (so it leaves the due list until its interval passes) and keeps the date of one not yet due, and *Easy* climbs to
box 2 at most; the longer intervals come only from quiz answers. A flag counts as *mastered* at box 3 or higher with a
score of 75 or more, and lapses out of that if it goes unreviewed for twice its interval. *Weak spots* are the
countries reviewed but not (or no longer) mastered.

A ten-question quiz is filled with due reviews first (up to seven), then weak countries, then at most three new
countries, and the rest from whatever is left, so reviews are cleared before new flags pile on; a country asked about
in the same skill in the last 45 minutes is nearly skipped, unless it is due.

The streak runs on a daily goal of 5, 10 or 20 items (Settings › Quiz; 10 to begin with). An item is a question
answered or a card graded, so due reviews and new countries both count. A day is practised when its sessions of at
least five items add up to the goal, or when any single session has ten items or more; a quiz of four questions or a
blitz with no answer counts for nothing. The Today card on Explore and Progress shows the day's items against the
goal and offers the one next thing to do: the due reviews, or five new countries. On the first run it asks for a focus
continent, whose never-seen flags the quizzes then introduce before the rest of the world's.

## Languages

Every word the screens show is a string resource (`app/src/main/res/values/strings*.xml`, one file per screen plus
the shared one); `ui/Labels.kt` maps the stored keys (quiz modes, scopes, continents, sort orders, standings) to
them, so a translation is a `values-xx/` folder and touches no Kotlin. The layout mirrors in right-to-left locales.
The catalog itself (names, capitals, flag texts, facts) is English; translating it is a content decision for later.

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
needed. They run the screens at phone, landscape and tablet sizes and at twice the font size; a window 600dp or
wider gets a navigation rail and columns held to a reading width.

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
   and the R8 mapping. Pushing a tag `v1.6` does the same, provided the tagged commit is on `main`.

The workflow is two jobs: `build` (read-only token) lints, tests, builds, signs and verifies, and keeps the files as
a workflow artifact; `publish` (write token, GitHub environment `release`) downloads them and creates the release.
Adding required reviewers to the `release` environment (Settings > Environments) makes every publish wait for an
approval. `versionCode` is derived from the version (`major*10000 + minor*100 + patch`, so `1.6` is `10600`), which
keeps it increasing without a manual bump. A file `release-notes/<version>.md`, when there is one, is appended
to the generated release notes. Tick "dry run" to build and verify without publishing; the `publish` job is then
skipped and the files stay as a workflow artifact for a week.

## Project layout

| Path | What lives there |
| --- | --- |
| `quiz/` | Pure quiz rules (`QuizEngine`, the weighted `QuestionPicker`), the immutable `QuizSession` and its `QuizSnapshot` for saved state; no Android types. |
| `progress/` | Pure rules: the practice streak (`PracticeStreak`), computed from when quizzes finished, and per-skill mastery (`SkillMastery`: score, Leitner box, due date). |
| `reminder/` | The daily reminder: pure schedule rules, the alarm scheduler, the receiver that turns the alarm (and a reboot or time-zone change) into a run, the worker, the notification. |
| `data/settings/` | `UserSettings`: the preferences kept in SharedPreferences (answers difficulty, reminder, theme, timer pace, daily goal, focus continent, the day's new-item count, the first-run and Today-card state). |
| `data/transfer/` | `ProgressTransfer`: the export file format (JSON, format 1) and its reader. |
| `learn/` | The pure `LearnSession`: five never-seen flags as cards with their closest look-alike, then the quiz over them. |
| `flashcards/` | Deck ordering for the flashcard screen and the immutable `StudySession` (queue, grades, Again re-insertion, undo). |
| `speech/` | Text-to-speech behind a small `Speech` interface. |
| `data/model/` | The country catalog, one file per continent (`EuropeCatalog.kt`…, alphabetical), `CountryRepository`, `CountrySources` (the per-country source pages, read from the asset `sources.json`) and the generated `Geo` (each country's bounding box, label and capital on the map). |
| `data/local/` | Room database (version 2): bookmarks, skill states, quiz history with every answer. Schemas are exported to `app/schemas/`; `AppDatabase.MIGRATION_1_2` is tested against the committed version 1 schema. |
| `ui/` | Compose screens, components, theme, `Labels.kt` (the words for the stored keys) and `CountryViewModel`, which hands progress, the quiz and the study session to the holders in `ui/viewmodel/`. |
| `tools/` | Content pipeline: flag rendering (`render-flags.mjs`, `to-webp.py`, `contact-sheet.py`), catalog generation (`validate-research.py`, `gen-entries.py`, `apply-edits.py`, `gen-flag-art.py`, `flag-checksums.py`), audit input (`export-entries.py`), quiz data (`gen-lookalikes.py` writes `FlagLookAlikes.kt`), the sources asset (`export-sources.py` writes `app/src/main/assets/sources.json` from the research and audit files; `--check` verifies it), the notices asset (`export-notices.py` writes `app/src/main/assets/NOTICES.txt` from `licenses/THIRD_PARTY_NOTICES.md`; `--check` verifies it), the map (`gen-worldmap.py` downloads Natural Earth and writes `app/src/main/assets/world.json` and `Geo.kt`; `--check` verifies the committed files offline). CI runs the three checks. |
| `content/` | Provenance for the catalog: `seed/` (country lists and owner rules), `research/` (first-pass entries with sources), `audit/` (second-pass fixes, doubts and owner decisions per continent). |

`CountryViewModel` receives its repository, speech engine and clock through the constructor, which is how
the tests replace them.

## Flag artwork

Each flag is a lossless 600x450 WebP in `res/drawable-nodpi/flag_xx.webp`, rendered unstretched from the
MIT-licensed [flag-icons](https://github.com/lipis/flag-icons) SVGs (`tools/render-flags.mjs` and
`tools/to-webp.py` reproduce them) and looked up by country code in `FlagArt.kt`. Flags are shown in a 4:3 box
(`FlagAspectRatio`), the shape of the artwork. A country without artwork gets a placeholder with its flag emoji,
and a test fails until it has art. The licence text is in `licenses/THIRD_PARTY_NOTICES.md`, the developers' file
(where the images came from and how to reproduce them). The player's copy is `app/src/main/assets/NOTICES.txt`, one
block per third-party work (name, use, licence, copyright, licence text), which the About screen shows;
`tools/export-notices.py` writes it from the markdown file and CI checks it is current (`--check`).

## Map data

The world map is `app/src/main/assets/world.json`, generated by `tools/gen-worldmap.py` from
[Natural Earth](https://www.naturalearthdata.com/) (public domain: the 1:50m country outlines and the 1:10m
populated places). The tool downloads the two files outside the repository, maps every feature to a catalog code
(ISO codes first, then a short hand map; Somaliland and Northern Cyprus are merged into their states, dependencies
and Western Sahara dropped), keeps the outer rings only, simplifies them with Douglas-Peucker at 0.05° and rounds to
two decimals, so the asset stays around 420 KB. The format is `{"format":1,"source":...,"countries":{"FR":{"polys":
[[[lon,lat],...],...],"bbox":[west,south,east,north],"label":[lon,lat],"capital":[lon,lat],"point":false},...}}`:
one record per catalog country (Antarctica has a label at 0, −80 and no capital), `point: true` for the seven
microstates too small for an outline, drawn as a 0.08° square around the capital, and a `bbox` whose `east` exceeds
180 for the five countries that span the antimeridian (Russia, Fiji, Kiribati, the United States, New Zealand),
while the rings themselves stay split at ±180 as Natural Earth draws them. `frame` is the box to show the country
in: the largest ring, the capital's ring and every ring within 3° of them, chained, so French Guiana, the Canaries,
the Azores, the Galápagos, Svalbard, Alaska and Hawaii stay out of their countries' frames while island chains stay
whole. `Geo.kt` carries the same boxes, labels and capitals as Kotlin constants; `GeoTest` checks both against the
catalog.

## Dependencies

The versions in `gradle/libs.versions.toml` are the newest that build against compileSdk 37; targetSdk stays
at 36 so Robolectric keeps running at a supported level. androidx.test 1.7 breaks Robolectric's setup, so
core and runner stay on 1.6 (see `.github/dependabot.yml`).
