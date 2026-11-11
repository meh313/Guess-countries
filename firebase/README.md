# Firebase for online play

Online play (friend challenges and the daily leaderboard, `app/src/main/java/com/example/online/`) runs on Firebase:
Anonymous Authentication for the player's identity and Cloud Firestore for the challenges and scores. The app is
built without any Firebase project; this folder holds what the project needs and the steps that are done once, by hand,
in the [Firebase console](https://console.firebase.google.com/).

Nothing here costs money at the app's scale: the Spark (free) plan covers anonymous sign-in and 50,000 document reads
a day. Set a budget alert if the project is ever moved to Blaze.

## One-time setup (owner)

1. **Create the project.** Console > Add project > name it (for example `world-flags`) > turn Google Analytics **off**
   (the app ships no analytics, and the privacy statement says so) > Create.
2. **Register the Android app.** Project overview > Add app > Android. Package name `com.aistudio.worldexplorer.flags`.
   Add the **SHA-256 fingerprint of the upload certificate** (`151c68bfdc8dba3cfdba818ff827309344382e268dc45cd5bc3ac88460189d7a`,
   the one the Release workflow checks; also add the Play App Signing certificate's SHA-256 from Play Console > Setup >
   App signing, since Play re-signs the bundle). Download **`google-services.json`**.
3. **Give the build the file.** Two ways, both fine:
   - Locally: copy it to `app/google-services.json` (gitignored). `app/build.gradle.kts` reads the project id, app id
     and API key out of it; without the file every screen says online play is not available in this build.
   - For releases: GitHub > Settings > Secrets and variables > Actions > New repository secret
     `GOOGLE_SERVICES_JSON` = the file's whole content. The Release workflow writes it to `app/google-services.json`
     before building. CI (pull requests) builds without it on purpose: the tests never touch Firebase.
4. **Turn on Anonymous sign-in.** Build > Authentication > Get started > Sign-in method > Anonymous > Enable > Save.
   Also, under Authentication > Settings > User actions, leave "Enable create" on; under "Blocking functions" nothing.
5. **Create the Firestore database.** Build > Firestore Database > Create database > production mode > a location near
   most players (`europe-west` for Europe and the Middle East) > Create.
6. **Publish the security rules.** Firestore Database > Rules > replace the editor's content with
   [`firestore.rules`](firestore.rules) > Publish. Without them the database refuses every request (production mode), so
   the app's screens show "Something went wrong online".
7. **Set the TTL policies** so old data goes away by itself (90 days; the app writes `expiresAt` on every document):
   Firestore Database > Time-to-live > Create policy, three times:
   - collection group `challenges`, field `expiresAt`
   - collection group `results`, field `expiresAt`
   - collection group `scores`, field `expiresAt`
8. **Restrict the API key** (recommended). Google Cloud console > APIs & Services > Credentials > the "Android key
   (auto created by Firebase)" > Application restrictions: Android apps, package `com.aistudio.worldexplorer.flags`
   with the SHA-1 fingerprints of the upload and the Play App Signing certificates > Save. The key is in the APK anyway;
   the restriction stops anyone else's app from using it.
9. **Play Console, Data safety form.** Declare, under *Data collected*: **App activity > Other actions** (the quiz
   scores and times posted to challenges and leaderboards) and **Device or other IDs** (the anonymous Firebase user
   id); each collected, not shared, optional (the player turns online play on), for app functionality. Not
   user-generated content: the nickname is drawn from a list, nothing is typed. Data is encrypted in transit; users
   can request deletion (Settings > Online play > Delete my online data). See the Data safety paragraph of
   `store/listing.md`.

No composite indexes are needed: the leaderboard orders by one field and counts with aggregation queries, and
challenges are read by id.

## What is stored

```
players/{uid}                    name, updatedAt
challenges/{code}                owner, ownerName, mode, scope, difficulty, seed, targets, createdAt, expiresAt
challenges/{code}/results/{uid}  name, score, speedBonus, total, correct, answered, timeMs, finishedAt, expiresAt
daily/{day}/scores/{uid}         the same as a result, plus mode
```

`uid` is the anonymous Firebase user id, created on the phone the first time online play is turned on; it does not
move to a new phone (Android's backup does not carry Firebase's sign-in), so a restored phone is a new player with
the same nickname. A result is written once per player and never updated, so the first run of a challenge or of the
day's challenge counts. Scores come from the phone and are not verified; at this scale that is accepted.

## Deleting a player's data

Settings > Online play > Delete my online data does it from the phone (`OnlineService.deleteMyData`): the name, the
player's results, the challenges the player made and the anonymous account. From the console, for a request by other
means: Authentication > Users > delete the user, then Firestore > delete `players/{uid}`, the player's `results` and
`scores` documents (search by document id = uid) and the challenges whose `owner` is the uid.
