# Store listing

**Title (30):** World Flags, Capitals & Maps

**Short description (80):** Learn every flag, capital and location with spaced reviews that stick.

**Short description in the other store languages (80):**
- fr: Tous les drapeaux, capitales et cartes, avec des révisions espacées qui restent.
- es: Aprende banderas, capitales y mapas con repasos espaciados que se quedan.
- de: Alle Flaggen, Hauptstädte und Karten lernen, mit Wiederholungen, die sitzen.
- ar: تعلّم كل الأعلام والعواصم والمواقع مع مراجعات متباعدة تبقى في الذاكرة.

**Full description**

Learn the flags, capitals and locations of all 196 states and Antarctica, and keep them.

World Flags is a quiz game that also teaches you. It teaches first:
• **Learn 5 new**: five flags you have not seen yet, each with a one-line cue on what to look for and the flag it is most often confused with.
• **Spot it** cues on every card, and tell-apart tips for the classic look-alikes: Chad and Romania, Monaco and Indonesia, the Nordic crosses, the Central American blues.
• **Where it is**: a map of every country, in its sheet, on the flashcard back and after a miss.

Then it reviews what you learnt at the right time:
• Spaced repetition per skill: flag, capital, location. What is due comes first; new countries arrive a few at a time.
• Eight quiz modes: flag → country, pick the flag, capital, continent, map → country, a typed name, a 10-second speed round and a 60-second blitz. Misses come back at the end of the quiz.
• Flashcards with Again / Good / Easy, decks for what is due, the weak spots, the capitals, or your own filter.

And it shows you how you are doing:
• A Today card with your daily goal, a streak that follows it, an optional reminder, and a home-screen widget with what is due.
• Progress by continent, what is coming up this week, the pairs you confuse most, and every past quiz you can open again.

And, if you turn it on, play with friends:
• **Friend challenges**: play 10 questions, share a 6-letter code, and your friends get the same questions and answers to pick from. A speed bonus rewards quick right answers; the ranking shows who leads.
• **Today's leaderboard**: your place among everyone who played the daily challenge today. Online play is optional and anonymous: a nickname from a list, your scores and times, nothing else.

Private by design: no account, and your progress stays on your phone; export it as a file whenever you like. The free version shows one short ad after every third quiz, never during a question; a one-time Remove ads purchase ends them.

Works offline, in light and dark, with TalkBack, on phones and tablets.

**What's new (3.4)**
• One short ad after every third quiz, never during a question, and none in your first two days. Remove ads: one purchase, no more ads, for good.
• A 50/50 hint in the quiz: two wrong answers go, for half the points. Watch an ad, or free with Remove ads.
• Challenge a friend: share a 6-letter code and both play the same 10 questions.
• Daily leaderboard: compare your daily challenge score with other players.
• Online play is optional and off by default. No account needed.

**Category:** Education. **Content rating:** Everyone (the IARC questionnaire: no violence, sexual content, language, controlled substances or gambling; the app shows ads, offers an in-app purchase, and shares the advertising id with Google). **Target audience (Play Console › App content):** 13 and over only; do not tick any age group under 13, so the Families policy does not apply. **Contains ads:** Yes. **In-app purchases:** Yes (`remove_ads`, one-time, $3.99 or the local equivalent).

**Data safety (Play Console › App content › Data safety):** Data is collected: Yes, by the Google Mobile Ads SDK. Declare *Device or other IDs* (the advertising id) and *App activity › App interactions* (which ads were seen and tapped), each collected and shared (with Google AdMob), for *Advertising or marketing*, not optional for free users (the Remove ads purchase ends it), not processed ephemerally; and *Diagnostics › Crash logs* and *Other app performance data* if Google's SDK data disclosure for the Mobile Ads SDK of the shipped version lists them (check developers.google.com/admob/android/privacy/play-data-disclosure, which says exactly what to tick). Data encrypted in transit: Yes. Users can request data deletion: Yes, with Settings › Reset progress for the app's own data and Android's advertising id controls for the id. Independent security review: No. The privacy policy URL is https://github.com/meh313/Guess-countries/blob/main/PRIVACY.md.

**Data safety, online play (optional, off by default):** when the user turns it on, also declare *App activity › Other actions* (quiz scores and times posted to friend challenges and the daily leaderboard) and *Device or other IDs* (an anonymous Firebase user id), collected, not shared, for *App functionality*, optional. Users can request data deletion: Settings › Online play › Delete my online data removes everything from the server. See `firebase/README.md` step 9 for the form.

**Ads (Play Console › App content › Ads):** Yes, the app contains ads. **Government apps / News / COVID:** No. **Financial features:** No.

**Screenshots:** `store/screenshots/`: phone 1080×2160, each with a caption on navy above the screen, the quiz first: `01_quiz_flag`, `02_quiz_locate`, `03_quiz_answered`, `04_learn_card`, `05_flashcard_back`, `06_progress`, `07_sheet_map`, and `dark_01_quiz_flag` in dark, eight in all, the most Play takes for one kind of device; tablet 1600×2560 (`tablet_01_home`, `tablet_06_progress`). All are 24-bit PNGs, with no alpha channel. The `storePhone()`, `storePhoneDark()` and `storeTablet()` tests of `ScreenshotsTest` write them to `app/build/screenshots/store/`; that folder is copied into `store/screenshots/` after the tests have run.

**Feature graphic and icon:** `store/feature-graphic.png` (1024×500, 24-bit PNG) and `store/icon-512.png` (512×512, 32-bit PNG), drawn from the icon's master art (`assets/app-icon-master.webp`, the origami map pin on navy, and the pin cut from it) by the `storeFeatureGraphic()` and `storeIcon()` tests of `StoreGraphicsTest`, which write them to `app/build/screenshots/store-graphics/`; the two files are copied into `store/`. `python3 tools/store-check.py --check` checks the screenshots, the graphic, the icon and the lengths of the texts above against Play's rules (CI runs it).
