# Privacy

World Flags is a learning app that works entirely on your device.

**No data leaves the device.** The app declares no network permission, so it cannot send anything anywhere. There is no account, no sign-in, no analytics, no advertising and no crash reporting.

**What the app stores, and where.** Your progress (which flags and capitals you have reviewed, your quiz log with every answer, your bookmarks) lives in the app's own database on the device. A few settings (theme, reminder time, timer pace, daily goal, focus continent) live in the app's preferences. Both are included in Android's backup of the app when backup is switched on, so they move with you to a new phone through Google's or your device maker's backup service under that service's own terms.

**Export and import are yours to run.** Settings › Export writes all of the above to a file you choose; nothing is sent until you share that file yourself. Import replaces everything with such a file.

**Notifications.** The optional daily reminder posts a local notification on the device; on Android 13 and newer it asks for the notification permission first. No notification data is collected.

**Read aloud.** The speaker button uses the text-to-speech engine installed on your device. Whether that engine processes text on the device or online is decided by the engine and its own settings, not by this app.

**Online play (optional, off by default).** Play with friends and the daily leaderboard need a server. They stay off until you turn them on (Home › Play with friends, or Settings › Online play), and until then the app sends nothing to it. When you turn them on, the app signs you in anonymously with Firebase, a Google service, which gives this installation a random identifier; no name, email address, phone number, contacts or location is involved. The app then sends: a nickname it draws from a list for you (you can draw another; nothing you type is ever sent), and for each friend challenge or daily challenge you play, your points, the speed bonus, the number of right answers, the time you took and the moment you finished, together with the challenge code. Other players who have a challenge's code see your nickname and that result; the daily leaderboard shows your nickname and result to everyone who plays that day. The server keeps challenges and scores for 90 days. Settings › Online play turns it off (nothing more is sent) and **Delete my online data** removes your nickname, your results, the challenges you made and the anonymous account from the server. The identifier lives on this phone only: it is not part of Android's backup, so a new phone is a new player. Firebase processes this data under [Google's terms for Firebase](https://firebase.google.com/terms) and its data processing terms; the app sends no analytics, advertising or crash data through it.

**Deleting your data.** Settings › Reset progress deletes the database. Uninstalling the app removes the database and the preferences; Android's backup of the app is removed under the backup service's own rules.

**Children.** The app collects no personal information from anyone, of any age.

**Changes.** This statement is kept in the repository next to the code it describes; its history is the record of changes.
