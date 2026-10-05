# Parked work: v1.8 and v1.9 patch series

State at the end of the session of 2026-10-05. Only the work branch may be pushed, so these patches (made in a throwaway
clone) are parked here to survive the container. Run `git rm -r wip` before opening the next PR.

Base: main at 850eb3e (PR #25, Oceania audit). v1.6 is released. Roadmap: plan file `cuddly-doodling-fox.md`.

## Apply order (one PR each, `git am -3`), after the four audit PRs are merged
1. `a1-lookalikes-difficulty-pickflag.patch` look-alike flags, Normal/Hard, Pick the flag
2. `a2-blitz.patch` 60-second blitz
3. `a3-weak-spots.patch` weak spots scope and flashcard deck, **versionName 1.8** (release v1.8 after this one)
4. `a4-remember-difficulty.patch` UserSettings store, remembered difficulty (ride with the a3 PR)
5. `b1-streak.patch` daily practice streak (card on Progress, line on the score screen)
6. `b2-reminder-wip.patch` daily reminder, **unfinished** (see below); release v1.9 after it

Verified in the clone: a1 to b1 together, 366 unit tests green, `./gradlew build` (lint) green, streak and worker mutations killed.

## What is left for b2
- Not yet run on the final code: full `testDebugUnitTest`, `./gradlew build` (lint, manifest merge of WorkManager),
  ScreenshotsTest (new `stats_reminder_on`, `stats_time_picker`; look at them).
- The last edit (ReminderCard re-checks notification permission at the tap, not from cached state) is untested.
- Set `versionName = "1.9"`; README: Stats bullet (reminder, notification permission), `reminder/` row in the layout table.
- Passing on their own: ReminderScheduleTest, ReminderWorkerTest (15), CountryViewModelReminderTest, UserSettingsTest,
  ReminderCardUiTest (10).

## Audit (v1.7)
Four workflows were still running: Europe w9a6unudw, Asia wjfabdl0h, Africa w4p8rrauz, Americas wchth9krx. Results land in
the session tasks folder; if the container is gone, rerun them with `audit-continent.js` (inputs from `tools/export-entries.py`).
Procedure per continent as in PR #25: review fixes critically, write `content/audit/<continent>.json`, apply with
`tools/apply-edits.py`, test, one PR, merge. After all four, bump to 1.7 and dispatch the Release workflow.
