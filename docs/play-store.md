# Google Play closed test: preparation pack

**Status:** Active plan. The owner decided on 2026-10-28 to start the Google
Play closed test with 0.3.0 instead of the 0.9 RC (roadmap S2/S11). Prepared on
2026-10-28 from `main` 0a3465ba; the upload artifact is the signed 0.3.0 Play
bundle (versionCode 162, source commit 1870e331).
**Scope:** Everything the owner needs to click through Play Console for the
first closed-test release: the Console checklist, store listing text, graphics,
App content answers, reviewer instructions, the demo video plan, tester texts
and open risks. Agents never touch Play Console, the owner's Google account, the
signing host or any key.
**Closure:** Once production access is granted, move the lasting facts (signing
enrollment, listing ownership, the review route that worked) into
`release-process.md` and archive this plan.

Every Play statement below cites a Google page fetched on 2026-10-28. Where a
page did not answer a question, this document says so instead of guessing.
Google changes Console labels often; when a label differs, follow the meaning.

## Contents

1. [Click-through checklist](#1-click-through-checklist)
2. [Store listing](#2-store-listing)
3. [Graphics](#3-graphics)
4. [App content answers](#4-app-content-answers)
5. [Reviewer instructions and demo video](#5-reviewer-instructions-and-demo-video)
6. [Testers](#6-testers)
7. [Open risks](#7-open-risks)
8. [Owner decisions](#8-owner-decisions)

## What gets uploaded

| Item | Value |
|---|---|
| Bundle | `build/release/signed/0.3.0/tvheadend-player-0.3.0-play.aab` in the main checkout (`/root/projects/tvheadend-player`) |
| Application ID | `at.bernhardberger.tvhplayer` (never the `.appliance` build) |
| Version | 0.3.0, versionCode 162, source commit 1870e331 |
| Signing | Project release key, alias `tvhplayer-release`, certificate SHA-256 in [`release-process.md`](release-process.md#release-key) |
| R8 mapping | `tvheadend-player-0.3.0-play-mapping.txt` in the same directory |
| Corresponding source | GitHub release [`v0.3.0`](https://github.com/bernhardberger/tvheadend-player/releases/tag/v0.3.0) (source archives, SDK and FFmpeg source attached) |

Read from the signed artifacts on 2026-10-28 (`aapt2 dump badging` on the Play
APK, `bundletool dump manifest` on the AAB): minSdk 28, targetSdk 36;
`android.software.leanback` required, touchscreen not required; permissions
`INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK` and the app's own
signature-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (added by AndroidX);
`allowBackup="false"`; no foreground service, no accessibility service, no
`AD_ID` permission. Native code: arm64-v8a, armeabi-v7a, x86, x86_64.

## 1. Click-through checklist

Steps are in Console order. **One-way** marks a choice that can't be undone.

### 1.1 Create app

Console: **Home → Create app**. Google lists app name, package name, default
language, app or game, free or paid, a contact email and the declarations
(<https://support.google.com/googleplay/android-developer/answer/9859152>).

| Field | Answer |
|---|---|
| App name | `Tvheadend Player` |
| Package name (if asked) | `at.bernhardberger.tvhplayer`. **One-way:** "Once you upload an artifact, the package name for that app is fixed and cannot be changed." (<https://support.google.com/googleplay/android-developer/answer/9845334>) |
| Default language | English (United States), en-US. Google's pages don't say whether it can be changed later (<https://support.google.com/googleplay/android-developer/answer/9844778> is silent), so treat it as fixed. |
| App or game | App |
| Free or paid | Free. **One-way:** "Once your app has been offered for free, the app can't be changed to paid." (<https://support.google.com/googleplay/android-developer/answer/6334373>) |
| Contact email | Your choice (see [owner decisions](#8-owner-decisions)) |
| Declarations | Developer Program Policies, US export laws and the Play App Signing Terms. Tick them. The app uses only Android's own encryption (Keystore AES-GCM for the saved password). |

### 1.2 Play App Signing with our own key

Do this **before the first release reaches any tester**. Play's default changed:
new apps are "automatically enrolled in quantum-ready, hybrid signing with
Google-generated keys". To keep our own key: "Advanced developers who want to
manage their own key can change this default before a release is rolled out in
the open testing or production track. Go to the Play app signing page
(Protected with Play > Play Store protection > Manage Play app signing) and
click Change key." The choices are "Use the same key as another app in this
developer account" or "Provide a copy of your app signing key by following the
instructions", which uses the PEPK tool
(<https://support.google.com/googleplay/android-developer/answer/9842756>).

Why it matters: Play installs must carry the same signature as the GitHub APKs,
so either can update the other (roadmap S1, [`release-process.md`](release-process.md#builds)).
With a Google-generated key, a GitHub APK could never update a Play install.

1. Open **Protected with Play → Play Store protection → Manage Play app
   signing → Change key**. If the page only appears after a bundle exists, do
   1.7 up to **Save** first, without rolling out, and come back.
2. Choose **Provide a copy of your app signing key**. Console offers the PEPK
   tool (`pepk.jar`) and an encryption public key to download, and shows the
   exact command. The fetched pages don't list PEPK's flags, so copy them from
   Console. Android's guide describes the same path: export the key with PEPK,
   then "upload the generated file with your encrypted key"
   (<https://developer.android.com/studio/publish/app-signing>).
3. On the signing host, you run that command with the keystore path from your
   ignored release configuration and the alias `tvhplayer-release`. Enter the
   keystore and key passwords at PEPK's prompts if it asks for them, never as
   command arguments. Agents don't do this step.
4. Upload the encrypted output file in Console, then delete it from wherever
   you copied it.
5. Upload key: we don't create a separate one. "If you do not generate a new
   upload key, you continue to use your app signing key as your upload key to
   sign each release" (<https://developer.android.com/studio/publish/app-signing>).
   Google recommends a separate upload key "for maximum security"
   (<https://support.google.com/googleplay/android-developer/answer/9842756>);
   our release pipeline signs the AAB with the release key, so keeping one key
   is the smaller change.
6. Check: the app signing key certificate SHA-256 on that page must equal
   `1E:18:48:62:F5:BB:A2:D1:C8:40:6D:6A:7A:79:65:7F:F3:A7:3D:25:8C:1E:1B:75:FA:25:02:58:75:E5:AB:C9`.

**One-way:** "Once you publish an app to an open track, its signing key is
fixed" (<https://support.google.com/googleplay/android-developer/answer/9859348>).
A closed track doesn't fix it yet, but changing the key after testers installed
the app makes them uninstall and reinstall.

**Decided (owner, 2026-10-28):** keep our own key. The help page ties the
quantum-ready hybrid signature (for Android 17 and newer) to Google-generated
keys; no TV we target runs Android 17, and a Google-generated key would stop
GitHub APKs from updating Play installs. Revisit the Android 17 key upgrade when
a phone version ships.

Recovery was tested on 2026-10-28 before the upload: the owner restored the
encrypted key copy from the off-site Backblaze backup (pinned snapshot, checksum
matched), unlocked it with the vault entries alone, and the certificate SHA-256
matched the value above. The drill copy was deleted afterwards. PEPK runs on the
signing host against the live keystore, not against a restored copy.

### 1.3 App content

**Policy and programs → App content**, every item in [section 4](#4-app-content-answers).
The privacy policy declaration sits at **Monitor and improve → Policy and
programs → App content → Privacy Policy → Start declaration**
(<https://support.google.com/googleplay/android-developer/answer/10144311>).

### 1.4 Store settings

**Grow users → Store presence → Store settings**
(<https://support.google.com/googleplay/android-developer/answer/9859673>):

- Category: **Video Players & Editors** (the app plays the user's own TV
  streams). Entertainment is the alternative.
- Tags: up to five ("Your app or game can have a maximum of five tags.", same
  page). Pick from Console's list what matches live TV, TV guide and recording.
- Contact details: the contact email from 1.1; website
  `https://github.com/bernhardberger/tvheadend-player`.

### 1.5 Main store listing

**Grow users → Store presence → Main store listing**: name, short and full
description from [section 2](#2-store-listing), then the graphics from
[section 3](#3-graphics). Add the German translation with **Manage translations**
and paste the German text.

### 1.6 Android TV opt-in

From <https://developer.android.com/training/tv/publishing/distribute>: upload a
TV-capable bundle, add at least one TV screenshot and the TV banner, mention
Android TV in the description (both descriptions do), then **Setup → Advanced
settings → Form factors**. "Click Add Release Type and select Android TV."
Confirm the opt-in. Google then reviews the release against the TV app quality
guidelines. A failure arrives by email with the deficiencies, and the TV status
shows **Not approved**; fix and resubmit.

### 1.7 Closed testing track, testers, countries, release

From <https://support.google.com/googleplay/android-developer/answer/9845334>:

1. **Test and release → Testing → Closed testing**. Use the first closed track
   Console provides, or **Create track** (name: `Testers`).
2. **Countries / regions:** testing tracks follow production's countries until
   you choose **Unsync**; targeting follows each tester's registered Play
   country, not where they are
   (<https://support.google.com/googleplay/android-developer/answer/7550024>).
   Add all countries and regions. Nothing in the app is country-bound, and
   Tvheadend users are everywhere. The general testing page says additional
   closed tracks may not offer country targeting, which is one more reason to
   use the first one.
3. **Testers:** an email list (`Tvheadend Player testers`) for people who send
   you their Google account address, or a Google Group they join themselves.
   Google Group members must join the group before opting in. Limits: 200 lists,
   2,000 users per list, 50 lists per track.
4. **Feedback URL or email:** `https://github.com/bernhardberger/tvheadend-player/issues`.
5. Copy the opt-in link. "Each tester needs to opt in using the link."
6. **Create new release:** upload the AAB from [What gets uploaded](#what-gets-uploaded).
   Release name: `0.3.0 (162)`. Paste both release notes from
   [section 2.3](#23-release-notes). If Console offers an upload for the
   deobfuscation (R8 mapping) file, use the Play mapping from the same directory.
7. **Save**, then **Review release**. Read every error and warning before going
   on.

### 1.8 Send for review

**Publishing overview → Send changes for review.** "Processing can take a few
hours or up to seven days (or longer in exceptional cases)"
(<https://support.google.com/googleplay/android-developer/answer/9859654>). The
closed-test release is reviewed, including the Android TV review from 1.6.

### 1.9 After approval

1. Send the opt-in link and the [tester instructions](#62-tester-instructions).
   Before production, testers can't find the app by searching Play; they need
   the link (<https://support.google.com/googleplay/android-developer/answer/9845334>).
2. Add your own account to the tester list and install it on the G10 first, to
   check the tester path on a TV (Google doesn't document it for TVs).
3. Feedback from testers arrives under **Ratings and reviews → Testing
   feedback**; "Beta feedback from users is only visible to you and can't be
   seen on Google Play"
   (<https://support.google.com/googleplay/android-developer/answer/138230>).
4. Later releases go to the same track.

### 1.10 Production access

"At least 12 testers must be opted in to your closed test when you apply for
production access, and they must have been opted in continuously for the
preceding 14 days"
(<https://support.google.com/googleplay/android-developer/answer/14151465>).
Recruit more than 12: anyone who opts out drops out of the count, and separate
periods don't add up. **Dashboard → Apply for production** asks about recruitment, how testers
used the app, the feedback and how you collected it, the target audience, the
app's value, expected first-year installs, what changed after testing and how
you decided it was ready (same page). Keep notes during the 14 days. That
review usually takes seven days or less and is separate from release review.

## 2. Store listing

Owner's README voice, US English, run through the humanizer pass. No dashes in
the copy, "•" bullets as in the app. Lengths are counted in Unicode characters;
limits from <https://support.google.com/googleplay/android-developer/answer/9859152>.
The listing says only what stays true across releases (owner, 2026-10-29), so
a release doesn't need a listing change and a second review: no screen or
view names, no individual features. What changes goes in the release notes
(2.3), which are reviewed with each release anyway.

### 2.1 English (United States), default

**App name** (16/30)

```text
Tvheadend Player
```

**Short description** (59/80)

```text
A Tvheadend client for Android TV, built around the remote.
```

**Full description** (665/4000)

```text
Tvheadend Player is an Android TV and Google TV app for your own Tvheadend server. Watch live TV, browse the program guide and play your recordings, all with the remote's D-pad.

It's an independent app, not an official Tvheadend product, and it has no channels of its own. You need a Tvheadend 4.3 server that your TV can reach.

• Live TV, the program guide and your recordings in one app
• Made for the remote, built with Google's Material Design for TV
• Live TV keeps playing while you browse
• No ads, no analytics, no account to sign up for

Open source under the GNU GPL v3, with the source code for every release: github.com/bernhardberger/tvheadend-player
```

### 2.2 German (de-DE)

Informal *du*, as in the app (`docs/translations/de.md`), and the app's own
German terms (Programmführer, Senderliste, Laufende Aufnahmen, Immersiv,
Raster, Liste).

**App name** (16/30)

```text
Tvheadend Player
```

**Short description** (67/80)

```text
Ein Tvheadend-Client für Android TV, gemacht für die Fernbedienung.
```

**Full description** (720/4000)

```text
Tvheadend Player ist eine App für Android TV und Google TV für deinen eigenen Tvheadend-Server. Live-TV schauen, im Programmführer stöbern und Aufnahmen abspielen, alles mit dem Steuerkreuz der Fernbedienung.

Die App ist unabhängig, keine offizielle Tvheadend-App, und bringt keine eigenen Sender mit. Du brauchst einen Tvheadend-4.3-Server, den dein Fernseher erreicht.

• Live-TV, Programmführer und deine Aufnahmen in einer App
• Gemacht für die Fernbedienung, gebaut mit Googles Material Design für TV
• Live-TV läuft weiter, während du stöberst
• Keine Werbung, keine Analyse, kein Konto zum Anmelden

Open Source unter der GNU GPL v3, mit dem Quellcode zu jeder Version: github.com/bernhardberger/tvheadend-player
```

### 2.3 Release notes

Up to 500 Unicode characters per language, each language between its tags
(<https://support.google.com/googleplay/android-developer/answer/9859348>).
English 458/500, German 491/500 (counted without the
tags). Paste both into the one release-notes box.

```text
<en-US>
First release on Google Play, as a closed test. New: setup in three steps that looks for Tvheadend servers on your network, reworked Channels (Immersive, Grid, List) and Guide, one details page everywhere, and menus on Hold OK or Menu. In the player: channel number entry, the channel list under the controls, the channel's schedule beside the picture and clearer error messages. All changes: github.com/bernhardberger/tvheadend-player/blob/main/CHANGELOG.md
</en-US>
<de-DE>
Erste Version auf Google Play, als geschlossener Test. Neu: Einrichtung in drei Schritten mit Serversuche im Netzwerk, überarbeitete Senderansicht (Immersiv, Raster, Liste) und Programmführer, eine Detailseite überall und Menüs mit langem OK-Druck oder der Menütaste. Im Player: Sendernummer eintippen, die Senderliste unter der Steuerung, das Programm des Senders neben dem Bild und klarere Fehlermeldungen. Alle Änderungen: github.com/bernhardberger/tvheadend-player/blob/main/CHANGELOG.md
</de-DE>
```

## 3. Graphics

Requirements from <https://support.google.com/googleplay/android-developer/answer/9866151>:
app icon 512×512, 32-bit PNG with alpha, at most 1024 KB; feature graphic
1024×500, JPEG or 24-bit PNG without alpha; screenshots JPEG or 24-bit PNG
without alpha, 320 to 3840 px, the long side at most twice the short side, up to
eight per device type, and at least one Android TV screenshot; TV banner
1280×720, JPEG or 24-bit PNG without alpha.

### 3.0 Uploaded set (2026-10-29)

Saved as a draft on the default (en-US) listing. The files come from branch
`docs/play-assets` at d0563891, under `captures/play-store/round3/`
(reproduction in that branch's `docs/presentation-captures.md`):

| Slot | File |
|---|---|
| App icon | `icon/current-rgba.png` |
| Feature graphic | `feature/final.png`: mark and name, "Built around the remote.", the Channels screen in a slim frame |
| Android TV screenshots, in order | `plain/01-channels.png`, `02-guide.png`, `03-player.png`, `04-recording.png`, `05-details.png`, `06-schedule.png` |
| TV banner | `banner/current.png` |
| Phone screenshots | none |
| 10-inch tablet screenshots | `01-channels.png`, `02-guide.png`. A full save needs two phone or tablet screenshots, and the Android TV opt-in needs a fully saved listing, so these break the loop. After the opt-in (2026-10-29) Console still refuses to save without them ("Upload at least 2 phone or tablet screenshots"). Try again once TV is approved. Android XR has no opt-out in Console; the leanback requirement keeps phones and headsets from installing |
| AI asset declaration | "Don't label assets" (owner, 2026-10-29): fictional sample art inside app screens, not depicting anything real |
| Video | none yet, see [5.2](#52-demo-video-shot-list) |

The Android TV section of the listing only appears after adding Android TV
under **Test and release → Advanced settings → Form factors**.

Console's asset library adds several selected files in a scrambled order. Add
the screenshots one at a time, in order.

Android XR shows as an active form factor that can't be removed. That's
harmless: the manifest requires `android.software.leanback`, so XR headsets
never get the app.

The sections below are the earlier candidates and plan, kept as history.

### 3.1 Candidates (not committed)

Composed from the generated brand PNGs in `artwork/` (themselves rendered by
`tools/RenderArtwork.java` from the SVG geometry) with
`artifacts/play-candidates/make_candidates.py`; `artifacts/play-candidates/` is
ignored. After your pick, the chosen files are committed under `artwork/play/`
with the recipe.

| File | What it is |
|---|---|
| `icon-a-canonical.png` | The listing icon `RenderArtwork` already writes (`app/src/main/ic_launcher-playstore.png`), mark at 78% |
| `icon-b-margin.png` | The same mark at 64%, more space around it |
| `icon-c-glow.png` | Mark at 74% over a faint cyan glow |
| `feature-a-lockup.png` | The stacked banner lockup, centered |
| `feature-b-android-tv.png` | The existing "for Android TV" lockup, centered |
| `feature-c-screen.png` | Stacked lockup on the left, the Channels screenshot on the right |

The icons are saved as 32-bit RGBA, fully opaque (Play applies its own corner
mask); the feature graphics as 24-bit RGB.

### 3.2 TV banner

`artwork/tvheadend-player-banner@4x.png`, 1280×720, 24-bit RGB, no alpha. It is
the same composition as the app's launcher banner.

### 3.3 TV screenshots

All are 1920×1080 offline renders of the production screens with the fictional
fixture pack, so they show no real channels or private data (see
[`presentation-captures.md`](presentation-captures.md)). Upload eight, in this
order:

1. `artwork/screenshots/channels.png`, Channels as picture cards
2. `artwork/screenshots/guide.png`, the program guide
3. `artwork/screenshots/player.png`, player controls over the picture
4. `artwork/screenshots/details.png`, program details
5. `artwork/screenshots/recordings-folders.png`, recording folders
6. `artwork/screenshots/player-rail.png`, the channel list in the player
7. `artwork/screenshots/channels-list.png`, Channels as a list
8. `artwork/screenshots/player-schedule.png`, the channel schedule beside the picture

Four or more 16:9 screenshots of at least 1080 px also meet the bar Google
names for recommendation eligibility (same page).

### 3.4 Phone screenshots

Google's page says "You must provide a minimum of two screenshots across
different device types to publish your store listing" and asks for at least one
Android TV screenshot. It doesn't say that an app supporting only Android TV
needs phone screenshots. The eight TV screenshots cover the count. If Console
still marks the phone screenshots as required, don't put TV screenshots there
without deciding it ([owner decisions](#8-owner-decisions)): the TV quality
guidelines ask for screenshots that accurately depict the TV app, and phone
slots imply a phone app.

## 4. App content answers

### 4.1 Privacy policy

- **Answer:** `https://github.com/bernhardberger/tvheadend-player/blob/main/privacy-policy.md`
  (public, HTTPS, no login, rendered as a web page; it answered 200 on
  2026-10-28).
- **Rules:** a policy is required even without personal data; it must be "on a
  standard, non-editable webpage (not a PDF or downloadable file)", public,
  worldwide, over valid TLS, specific to the app, and name the developer or app,
  a contact, the data handled, how it's protected and how it's retained and
  deleted. The policy also has to be linked or shown inside the app
  (<https://support.google.com/googleplay/android-developer/answer/10144311>,
  <https://support.google.com/googleplay/android-developer/answer/9859455>).
- **Evidence:** `privacy-policy.md` covers storage, the server connection, the
  local-network search (`_htsp._tcp`, added in 7fce99ba), unencrypted HTSP, no
  analytics, and retention. This branch adds a contact line (GitHub issues).
- **Gap:** 0.3.0 doesn't show or link the policy in the app: Settings → About
  has version, server, license, notices and translation only
  (`app/src/main/java/at/bernhardberger/tvhplayer/ui/screens/settings/SettingsAbout.kt`).
  See [risk 2](#7-open-risks).

### 4.2 Ads

- **Console:** "You must declare whether your app contains ads": **Ads → Start
  declaration → Yes or No**
  (<https://support.google.com/googleplay/android-developer/answer/9859455>).
- **Answer:** No.
- **Evidence:** no ad SDK in the merged manifest or dependencies; the app shows
  only what the user's own server streams. Google's page doesn't address
  commercials inside broadcast streams the user receives; they aren't ads the
  app serves.

### 4.3 Advertising ID

- **Console:** apps targeting Android 13 or newer declare whether they use the
  advertising ID; using it needs `com.google.android.gms.permission.AD_ID`,
  which SDKs can merge in
  (<https://support.google.com/googleplay/android-developer/answer/6048248>).
  The page doesn't quote the Console question.
- **Answer:** No.
- **Evidence:** no `AD_ID` permission in the signed AAB's manifest; no Google
  Play services or ad libraries.

### 4.4 App access (sign-in details)

- **Console:** Google now calls it sign-in details. "Your sign-in details must
  be accessible at all times, reusable, and valid regardless of user location."
  There are **+ Add new instructions** (up to five sets) and **Any other
  instructions**
  (<https://support.google.com/googleplay/android-developer/answer/15748846>).
  The exact option labels aren't on the page.
- **Answer (saved 2026-10-30):** **Yes**, part of the app is restricted. One
  set, "Demo server reviewer account", with the review server's reviewer
  username and password in their own fields, the [reviewer
  instructions](#51-reviewer-instructions) in **Any other information**, and
  "provide full access to all the features" ticked.
- **Evidence:** the app needs a Tvheadend server; without one it stays on
  setup. The review server (homelab `docs/operator/tvh-review.md`) is reachable
  at all times from anywhere and its account doesn't expire. Credentials live
  only in Console and the homelab private store, never in this repository.

### 4.5 Content rating (IARC questionnaire)

- **Console:** **Content rating → Start questionnaire**, choose a category,
  answer, submit
  (<https://support.google.com/googleplay/android-developer/answer/9898843>,
  <https://support.google.com/googleplay/android-developer/answer/9859655>).
  The fetched pages don't list the categories or the questions, and the IARC
  developer page (<https://www.globalratings.com/for-developers.aspx>) returned
  404.
- **Answer:** pick the category that is not a game and not social or
  communication. Answer No to violence, sexuality, language, gambling, drugs,
  in-app purchases, location sharing and user-to-user interaction: the app has
  none of them. Where it asks whether the app streams or shows content it
  doesn't control, answer **Yes**: it shows whatever channels and recordings the
  user's server delivers.
- **Evidence:** Google explains that "Certain apps function as portals (e.g. to
  stream content), offering a broad, variable range of content from which
  consumers can choose", which can earn a parental-guidance rating rather than
  "everyone" (<https://support.google.com/googleplay/android-developer/answer/9898843>).
  Expect that and accept it.

### 4.6 Target audience and content

- **Console:** "Select the age group(s) that your app targets": 5 and under,
  6–8, 9–12, 13–15, 16–17, 18 and over. Choosing only 18 and over offers a
  separate **Restrict Minor Access** checkbox; targeting children brings in the
  Families requirements
  (<https://support.google.com/googleplay/android-developer/answer/9867159>).
- **Answer (owner, 2026-10-28):** **13–15, 16–17 and 18 and over**; no group
  under 13, so the Families policy doesn't apply. Answer No to appeal to
  children. Restrict Minor Access isn't offered with teen groups selected.
- **Evidence:** a household TV app that teenagers use too; the listing and icon
  don't address children. The app has no ads, purchases or data collection (the
  login stays on the device and goes only to the user's server), so the teen
  groups add no obligations. Content comes unfiltered from the user's server;
  Tvheadend can limit an account to channel tags, and the content rating (4.5)
  is what the store shows. Google notes 13–17 "may be considered to include
  children in some locales" and may review the declaration more closely.

### 4.7 Data safety

Definitions: "'Collect' means transmitting data from your app off a user's
device" and "'Sharing' refers to transferring user data collected from your app
to a third party"
(<https://support.google.com/googleplay/android-developer/answer/10787469>).
The order is overview, data collection and security (whether required data types
are collected or shared, then encryption in transit and deletion), data types,
usage and handling, preview, submit. All developers also answer the new data
deletion questions
(<https://support.google.com/googleplay/android-developer/answer/13327111>).

What the app does (evidence: `privacy-policy.md`, the merged manifest,
`images/TvheadendArtworkLoader.kt`, `images/RemoteLogoCache.kt`):

- It stores host, port, username and password on the TV; the password is
  encrypted with AES-GCM under a non-exportable Android Keystore key.
- It sends the username and a challenge-response digest, never the password
  itself, to the server the user configured, over HTSP, which is unencrypted
  TCP.
- It searches the local network with mDNS during setup.
- It loads channel and tag logos that the server lists as web addresses from
  those hosts, which see the TV's IP address and the app's name and version.
  The app doesn't use or keep those IP addresses, and Google says to declare IP
  addresses by how they're used (FAQ on the same page), so this adds no data
  type.
- The developer receives nothing; there is no analytics, ads, crash reporting
  or account with us.

Google's definition has no exception for a server the user runs, and the
end-to-end encryption exemption doesn't fit plaintext HTSP. Two ways to answer:

**A. "No data collected."** Reasoning: the only recipient is the user's own
server, chosen and operated by the user, and nothing reaches the developer. It
reads best, but the help page gives no basis for treating the user's own server
differently, and the privacy policy says the app sends the username over an
unencrypted connection. A reviewer comparing the two can call that a
misrepresentation.

**B. Declare the username (recommended).** Collected: **Yes**. Shared: **No**.
Data type: **Personal info → User IDs** (the Tvheadend username). Processed
ephemerally: No. Required or optional: optional (the connection form accepts
both username and password left empty, for servers that allow anonymous
access). Purpose: **App functionality**. Encrypted in transit: **No**.
Deletion: the data lives only on the TV and on the user's own server; clearing
the app's data or uninstalling deletes it on the TV, and the developer holds no
copy. The listing then shows that data isn't encrypted in transit, which
matches the privacy policy and the description.

B is the honest reading of Google's definition and keeps all three texts
consistent. The channel and recording requests the app sends are the service
itself, sent to the user's own server; they are not declared as app activity.
See [owner decisions](#8-owner-decisions).

### 4.8 News apps

- **Console:** "The declaration is required as of Aug 27, 2025"; it applies to
  apps in the News and Magazines category or presenting themselves as news
  (<https://support.google.com/googleplay/android-developer/answer/16189314>).
- **Answer:** No, not a news app. The app plays whatever channels the user's
  server has, which can include news channels, but it publishes nothing itself.

### 4.9 Government apps

- **Console:** "developers must complete the Government apps declaration"
  (<https://support.google.com/googleplay/android-developer/answer/9514050>).
- **Answer:** not developed by or for a government.

### 4.10 Financial features

- **Console:** select **"My app doesn't provide any financial features"**
  (<https://support.google.com/googleplay/android-developer/answer/13849271>).

### 4.11 Health

- **Console:** select **"My app doesn't provide any health features"**
  (<https://support.google.com/googleplay/android-developer/answer/14738291>).

### 4.12 Permissions and foreground services

- **Rules:** "When your apps target Android 14 and above, you'll need to declare
  any foreground service types that you use"
  (<https://support.google.com/googleplay/android-developer/answer/13392821>);
  other declarations come from AccessibilityService, `QUERY_ALL_PACKAGES`,
  SMS and call log, background location, all-files access, photo and video
  access, `USE_EXACT_ALARM` and full-screen intents
  (<https://support.google.com/googleplay/android-developer/answer/9888170>).
- **Answer:** none apply. The Play manifest has no foreground service, no
  accessibility service (only the Appliance build has one, and it is never
  uploaded) and only `INTERNET`, `ACCESS_NETWORK_STATE` and `WAKE_LOCK`. If
  Console lists a declaration anyway, stop and check rather than answering it.

## 5. Reviewer instructions and demo video

### 5.1 Reviewer instructions

**Sign-in details → Add details** has a name, username and password fields, and
one free-text field, "Any other information required to access your app",
limited to 500 characters; reviewers can't contact you. Saved 2026-10-30 (459
characters):

```text
Tvheadend Player is a client for Tvheadend, TV server software that users self-host on their local network. The app has no content or accounts of its own, so we run a demo server for review.

To sign in:
- Welcome screen: select Continue
- Server address: tvh-demo.bernhardberger.at
- Port: 19982
- Enter the username and password above, then Connect

The server has two channels with open movies, a guide and recordings. Its account can record and play them.
```

The longer text below is for an appeal or a reply to a rejection (replace the
placeholder):

```text
Tvheadend Player is a client for Tvheadend, free and open-source TV server software that users self-host on their local network, such as a PC or NAS with a TV tuner. The app has no content, service or account of its own. Like an email app needs a mail server, it needs a Tvheadend server.

For review we run a demo server at tvh-demo.bernhardberger.at, port 19982, with the username and password given in Sign-in details. It has two channels playing open movies (Big Buck Bunny, Sintel), a program guide, recordings, and an account that can record and play them.

To sign in: on the Welcome screen select Continue, enter the server address and port, select Continue, enter the username and password, and select Connect.

Questions: <CONTACT EMAIL>
```

What a reviewer needs to see every feature: a reachable Tvheadend 4.3 server
with HTSP 36 or newer, a few channels with guide data, a timeshift buffer
(pause and rewind), a recording profile with a few finished recordings in
folders, and an account allowed to record.

### 5.2 Demo video shot list

The owner records on the G10 (TCL C655, Google TV) with 0.3.0 from Play or the
GitHub Play APK, against the test server. Aim for three to five minutes, one
take per scene, no music. Keep server addresses and the password out of the
frame: use the network search result, and the password field stays masked. The
video goes to a third party, so channels with open or fictional content are
better than broadcast TV where you have them.

1. **Launch:** Google TV home with the app's banner in Your apps; open it.
2. **Setup:** Server step finds the server; select it. Account step with
   username and masked password. Connect; channels load.
3. **Channels:** Immersive, Grid and List views; the group filter; Hold OK on a
   channel for its menu.
4. **Live TV:** play a channel. Player controls, pause and resume, rewind and
   back to live. Channel up and down, type a channel number, the channel list
   under the controls, the schedule panel, Audio and Subtitles, the (i) details.
5. **Media keys:** play/pause on the remote; D-pad center pauses.
6. **Guide:** move through the grid, change the day, open a program's details,
   record a future program and show its recording mark, cancel it.
7. **Recordings:** folders, the Recording now folder if something records, play
   a recording, stop, reopen it to show resume, mark watched, delete with its
   confirmation, free space.
8. **Browsing while watching:** open the side menu while live TV plays behind.
9. **Settings:** Connection, Playback, General (language, clock format), About
   (version 0.3.0 (162), license, notices).
10. **Back:** Back from the top-level screen returns to the Google TV home
    screen.
11. **Recovery (optional):** stop the server or unplug its network, show the
    error and Try again.

Upload it unlisted to YouTube (or another host you choose) and put the link in
5.1.

## 6. Testers

### 6.1 Invitation (Tvheadend forum and r/tvheadend)

Nothing is posted by agents. Title and text for both places:

```text
Testers wanted: Tvheadend Player for Android TV on Google Play

I'm building Tvheadend Player, a Tvheadend client for Android TV and Google TV, built around the remote. It's open source (GPLv3) and not an official Tvheadend app: https://github.com/bernhardberger/tvheadend-player

I'd like to bring it to Google Play. As a new developer account, I need at least 12 testers who stay in a closed test for 14 days in a row before Google lets me publish it.

You need:
- an Android TV or Google TV device on Android 9 or newer
- your own Tvheadend server that speaks HTSP 36 or newer (a recent 4.3 build)
- the Google account that's signed in on your TV

If you'd like to help, send me that Google account's email address [by private message / by email to <CONTACT EMAIL>], and I'll add you and send you the link. Please stay opted in for the 14 days and keep the app installed; use it like you normally would. Bugs and ideas are welcome as GitHub issues or as private feedback in the Play Store.

Thanks!
```

### 6.2 Tester instructions

Sent with the opt-in link:

```text
Thanks for testing Tvheadend Player.

1. Open this link on a phone or computer signed in with the same Google account as your TV, and select "Become a tester": <OPT-IN LINK>
2. Install the app on your TV from the Play Store (still signed in with that account). The app doesn't show up in Play search yet, so use the link from step 1.
3. Please stay opted in for at least 14 days in a row and keep the app installed. If you leave the test, your days stop counting, so please don't opt out and back in.
4. Feedback: open an issue at https://github.com/bernhardberger/tvheadend-player/issues, or leave private feedback on the app's Play Store page. Only I can read the private feedback.

If you have the GitHub APK installed, the Play version installs over it, and a newer GitHub APK installs over the Play version.
```

Step 2 isn't documented by Google for TVs; check it with your own account
first (1.9) and adjust the text. The last paragraph holds only once Play App
Signing uses our key (1.2).

### 6.3 Draft "Beta testing" section for README.md

For the README after "Download and install", once the opt-in link exists. It
replaces "I'm not promising a Google Play listing." in that section:

```markdown
## Beta testing on Google Play

Tvheadend Player is in a closed test on Google Play. Before Google lets a new
developer publish an app, at least 12 testers have to stay in the test for 14
days in a row, and I'd be glad if you joined.

You need an Android TV or Google TV device and your own Tvheadend server.
Send the email address of the Google account on your TV to <CONTACT>, and I'll
add you and send you the link. Please stay in the test for the 14 days.
Feedback goes to [GitHub issues](https://github.com/bernhardberger/tvheadend-player/issues)
or to the private feedback in the Play Store.

The Play version and the GitHub APKs are signed with the same key, so either
one updates the other.
```

## 7. Open risks

1. **Reviewer access (S4).** Google wants sign-in details that are "accessible
   at all times, reusable, and valid regardless of user location", and warns
   that if a password expires "we may not be able to review your app and,
   therefore, the app may be rejected"
   (<https://support.google.com/googleplay/android-developer/answer/15748846>).
   Resolved 2026-10-30 with an always-on review server (homelab
   `docs/operator/tvh-review.md`): isolated VM, two open-movie channels, a
   non-admin account with stream and bitrate caps. What remains: the server must
   stay up for every review, and HTSP to it is plaintext (risk 3).
2. **No privacy policy inside the app.** "Have a valid privacy policy in both
   the app's store listing and within the app itself"
   (<https://support.google.com/googleplay/android-developer/answer/10144311>);
   0.3.0 has none in the app. What I'd do: a small 0.3.1 with a **Privacy policy** row in
   Settings → About that shows the policy text on screen (a TV has no reliable
   browser for a link), uploaded before sending for review. That costs a
   release; submitting 0.3.0 risks a rejection round of up to seven days.
   Done for 0.3.1: Settings → About → Privacy policy shows the bundled
   `privacy-policy.md` in the same reader as the license, in English.
3. **Credentials over unencrypted HTSP.** Google's user data policy says to
   "Handle all personal and sensitive user data securely, including
   transmitting it using modern cryptography (for example, over HTTPS)", and
   names authentication information
   (<https://support.google.com/googleplay/android-developer/answer/10144311>).
   The password never leaves the TV (challenge-response), but the username does,
   in plain text. HTSP has no TLS; the protocol and server don't offer it. What
   I'd do: disclose it plainly (privacy policy, Data safety answer B, listing),
   and if a reviewer flags it, answer that the connection is to the user's own
   server on their own network and that a VPN is recommended. There is no app
   fix short of a protocol change in Tvheadend.
4. **Data safety answer.** See 4.7. Answer A risks a misrepresentation finding;
   B shows "not encrypted in transit" on the listing.
5. **TV quality review (S5).** The opt-in triggers a review against the TV
   checklist. Likely items, from roadmap S5: TV-DB (Back from the top level must
   return to the home screen), TV-PC/TV-PP (center and play/pause toggle
   playback), TV-OV overscan, TV-ME low-RAM devices, TV-BU/TV-BY ambient mode,
   and the Media Session Validator. The S5 audit hasn't run. The checklist also
   asks for "at least one unaltered, high resolution screenshot that accurately
   depicts the current version of the TV app experience"
   (<https://developer.android.com/docs/quality-guidelines/tv-app-quality>).
   Ours are unedited renders of the 0.3.0 screens with fictional data; the
   player shots show a still frame where video would play. What I'd do: show
   items 5 and 10 in the demo video, and if a TV rejection names specific IDs,
   fix them in a 0.3.x. A TV rejection doesn't block the phone side, but this
   app has no phone side, so the release waits for it.
6. **"Tvheadend" in the app title.** "Don't imply that your app is related to or
   authorized by someone that it isn't"
   (<https://support.google.com/googleplay/android-developer/answer/9888374>),
   and "If your app uses another party's trademarks in a way that is likely to
   cause confusion, your app may be suspended"
   (<https://support.google.com/googleplay/android-developer/answer/9888072>).
   There is no exemption for unofficial clients. In our favor: the description's
   second paragraph says it isn't official or endorsed, the icon reuses no
   Tvheadend geometry ([`NOTICE.md`](../NOTICE.md)), and the name describes
   compatibility ([`product-identity-plan.md`](product-identity-plan.md)). What
   I'd do: keep the name for the closed test. If you want to remove the risk,
   ask the Tvheadend maintainers for written permission to use the name, which
   Google accepts before review. A rename is an identity decision, not part of
   this pack.
7. **GPLv3 on Play (S9).** Not a Play-docs question. The app's code is GPLv3
   and partly the predecessor's copyright, so it can only be distributed under
   GPLv3, never relicensed. My reading: Play distribution complies when each
   Play release has its corresponding source available (the GitHub release
   `v0.3.0` carries the source archives, SDK and FFmpeg source), the listing
   points to it (both descriptions do), and we add no extra restrictions. The
   APKs Play builds from our AAB are object code from the same source. This
   isn't legal advice. Accepted 2026-10-29 and recorded in
   `product-identity-plan.md` → Google Play distribution.
8. **One-way choices.** Free can't become paid, the package name locks with the
   first upload, and the signing key locks with the first open or production
   release. Changing the key after testers installed means reinstalls (1.2).
9. **Tester recruiting.** 12 people who stay 14 days in a row is the gate for
   production. Invite 20 or more.

## 8. Owner decisions

1. Icon: decided, the existing launcher icon (3.0).
2. Feature graphic: decided, `final.png` (3.0).
3. Contact email for Console, store settings and reviewer instructions (it's
   shown on the store listing).
4. Data safety: decided, B, saved 2026-10-30 (4.7). Accounts answer: users sign
   in with accounts made outside the app, "Other": "Users sign in with an
   account on their local Tvheadend server, which they create themselves in
   Tvheadend's web interface. The developer runs no accounts." Deletion request
   question left blank (optional).
5. In-app privacy policy: build a 0.3.1 with a Privacy policy row before
   submitting, or submit 0.3.0 and fix after a rejection (risk 2).
   Recommendation: 0.3.1 first.
6. Demo video host (unlisted YouTube or other) and which channels to show (5.2).
7. Phone screenshots: none; Android TV form factor added, two TV screenshots in the 10-inch tablet slot (3.0).
8. Target audience: decided, 13–15, 16–17 and 18 and over, saved 2026-10-30
   (4.6); Console warned the Families policy applies where teens count as
   children. Category Video Players & Editors (1.4).
9. Testers: email list or Google Group (1.7), and where the invite asks people
   to send their address (6.1).
10. S9: accepted 2026-10-29, recorded in `product-identity-plan.md`.
