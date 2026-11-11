# Monetisation: what the owner sets up by hand

The code in `app/src/main/java/com/example/monetization/` is complete, but ads earn nothing and the purchase sells
nothing until the AdMob and Play Console accounts exist and three secrets are set. This is the checklist, in order.
Everything here is done in the two consoles; nothing needs a code change, except where a step says so.

The app is for players aged 13 and over, not for children, and the owner's rule is that no haram advertising shows:
no gambling, alcohol, dating or adult content and the like. Steps 3 and 4 are where that rule is enforced.

## 1. AdMob account and app

1. Go to https://admob.google.com and sign in with the Google account that owns the Play Console. Create the AdMob
   account (country, time zone, currency) and accept the terms.
2. **Apps › Add app** › Android › "Yes, it is listed on Google Play" › search for *World Flags, Capitals & Maps* (or
   enter the package name `com.aistudio.worldexplorer.flags`). If the app is not on Play yet, pick "No", name it
   *World Flags*, and link it to the Play listing later (Apps › the app › App settings › Link to Play).
3. Note the **App ID**: `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`. This is the secret `ADMOB_APP_ID`.

## 2. Ad units

Create two ad units under **Apps › World Flags › Ad units › Add ad unit**:

| Ad unit | Format | Name to use | Secret |
|---|---|---|---|
| The ad after every third quiz | **Interstitial** | `World Flags interstitial, after quiz` | `ADMOB_INTERSTITIAL_ID` |
| The 50/50 hint | **Rewarded** | `World Flags rewarded, 50/50 hint` | `ADMOB_REWARDED_ID` |

For the rewarded unit, set the reward to *1 hint* (the amount and name are only labels; the app grants one hint per
completed view). Leave *Apply to all platforms* off. Note each unit's id: `ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ`.

## 3. Block the categories (the halal rule)

Under **Blocking controls › All apps** (or per app, under World Flags), set the same thing at both levels so a new
app or unit inherits it:

1. **Ad content rating**: set the maximum to **G** (general audiences). The app also asks for G in every ad request,
   so this is belt and braces.
2. **Sensitive categories**: block every one of these: *Gambling & Betting*, *Dating*, *Sexual & Suggestive*,
   *Alcohol*, *Drugs & Supplements*, *Astrology & Esoteric*, *Religion*, *Politics*, *Get Rich Quick*,
   *Weight Loss*, *Significant Skin Exposure*, *Cosmetic Procedures & Body Modification*, *References to Sex &
   Sexuality*, *Downloadable Utilities*, *Ringtones & Downloadables*, *Video Games (Casual & Online)* if the games
   advertised include casino or betting games, *Consumer Loans*, *Black Magic*. AdMob's list changes; block anything
   new that touches the rule.
3. **General categories**: block *Gambling*, *Lotteries*, *Casinos*, *Alcoholic Beverages*, *Dating Services*,
   *Adult*, *Nightlife*, *Bars, Clubs & Nightlife*, *Tobacco & Smoking*, *Pork* (under Food), *Occult & Paranormal*,
   and anything else that fails the rule. Blocking lowers fill and revenue a little; the rule comes first.
4. **Ad review center**: switch it on (Blocking controls › Ad review center) and, for the first month, look at the
   new ads once a week; block any advertiser or landing page that slipped through. Later, once a month.
5. **Ad networks**: leave *Mediation* off (AdMob only). Every extra network is another source of unwanted ads; add
   one only after checking its own category controls.
6. **Personalised ads**: under Privacy & messaging, the consent flow (step 4) covers the EEA and the UK; elsewhere
   AdMob personalises by default. For a stricter line, Privacy & messaging › *Ad personalisation* lets you serve only
   non-personalised ads to everyone; expect lower revenue.

## 4. Privacy & messaging (the consent form)

The app calls Google's User Messaging Platform on every start. It shows a form only where one is configured and
required, so without this step EEA and UK users get no ads at all (and no form).

1. **Privacy & messaging › GDPR** › Create message › pick *World Flags* › choose the standard message with
   *Consent / Do not consent / Manage options*, add the privacy policy URL
   `https://github.com/meh313/Guess-countries/blob/main/PRIVACY.md`, and **publish** it. Keep the default partner
   list (Google only, since there is no mediation).
2. **Privacy & messaging › US states** › Create message › *World Flags*, standard message, publish. This is what
   makes Settings › Ads and purchases show *Ad privacy options* to the players who are entitled to it.
3. Under GDPR, set *Ad personalisation when the user declines*: non-personalised ads. (This is the default; check.)

## 5. Play Console

1. **Monetise › Products › In-app products › Create product**: product ID `remove_ads` (exactly; the app looks for
   this id), name *Remove ads*, description *No more ads after a quiz, and the 50/50 hint is free. One payment, for
   good.*, price **$3.99** (set it as the default and let Play convert; round the local prices down to a .99 in the
   big markets). Save and **Activate**. The product must be active before the Buy button works, and the app must
   have been uploaded to at least an internal testing track once with the BILLING permission (the first 3.4 bundle
   does that).
2. **Monetise › Monetisation setup**: nothing to change; Play Billing works without a licence key in the app.
3. **Test users**: Play Console › Settings › License testing › add your own Google account. Test purchases then cost
   nothing and can be cancelled from the same page. Install the app from the internal testing track, not from a
   sideloaded APK, or the store reports "not available".
4. **App content** (Policy › App content), each one must be re-answered for 3.4:
   - **Ads**: Yes, the app contains ads.
   - **Target audience and content**: age groups **13-15, 16-17, 18 and over** only. Do not tick any group under 13;
     that would put the app under the Families policy, which the ads and the AD_ID permission do not meet.
   - **Data safety**: fill it as `store/listing.md` says under *Data safety*; the exact Google SDK disclosures to
     tick are at https://developers.google.com/admob/android/privacy/play-data-disclosure. Point the privacy policy
     field at the PRIVACY.md URL above.
   - **Content rating**: redo the IARC questionnaire; answer Yes to "contains ads", "offers in-app purchases" and
     "shares the user's location or advertising id with third parties".
   - **Advertising ID**: Yes, the app uses the advertising id, for advertising.
5. **Store listing**: set *Contains ads* and *In-app purchases* as the listing text says; the full description and
   the What's new block are in `store/listing.md`.

## 6. Repository secrets and the release

1. GitHub › Settings › Secrets and variables › Actions › New repository secret, three times: `ADMOB_APP_ID`,
   `ADMOB_INTERSTITIAL_ID`, `ADMOB_REWARDED_ID`, with the ids from steps 1 and 2.
2. Actions › Release › Run workflow on `main` with **dry run** ticked once: the build must pass the signing and the
   AdMob checks. (A dry run without the secrets builds with test ids and warns; a real release without them fails.)
3. Run the release for real, upload the `.aab` to the internal testing track first, install it on a phone signed in
   as a licence tester, and check: the consent form appears (with a VPN in the EU, or AdMob's debug geography),
   an ad shows after the third quiz but not in the first two days (set the phone's date forward to see one), the
   50/50 hint shows a rewarded ad, Remove ads buys and the ads stop, Restore purchase finds the purchase after a
   reinstall. Then promote to production.

## 7. What to watch after launch

- AdMob › Ad review center weekly for the first month; block what fails the rule.
- AdMob › Policy center: any policy warning stops the ads.
- Play Console › Monetise › Orders: refunds show here; the app revokes Remove ads at the next start after one.
- Revenue expectations (not data): a geography app earns little per user; growth matters more than ad tuning.

## Test ids

The code and every CI build use Google's public test ids, which show test ads and never pay: app
`ca-app-pub-3940256099942544~3347511713`, interstitial `ca-app-pub-3940256099942544/1033173712`, rewarded
`ca-app-pub-3940256099942544/5224354917`. To see real ads on your own phone without risking an invalid-traffic
strike, add it as a test device in AdMob › Settings › Test devices instead of tapping real ads.
