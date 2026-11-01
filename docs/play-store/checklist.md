# 1. Click-through checklist

Steps are in Console order. **One-way** marks a choice that can't be undone.

## 1.1 Create app

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
| Contact email | Your choice (see [owner decisions](decisions.md#8-owner-decisions)) |
| Declarations | Developer Program Policies, US export laws and the Play App Signing Terms. Tick them. The app uses only Android's own encryption (Keystore AES-GCM for the saved password). |

## 1.2 Play App Signing with our own key

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
so either can update the other (roadmap S1, [`release-process.md`](../release-process.md#builds)).
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

## 1.3 App content

**Policy and programs → App content**, every item in [section 4](app-content.md#4-app-content-answers).
The privacy policy declaration sits at **Monitor and improve → Policy and
programs → App content → Privacy Policy → Start declaration**
(<https://support.google.com/googleplay/android-developer/answer/10144311>).

## 1.4 Store settings

**Grow users → Store presence → Store settings**
(<https://support.google.com/googleplay/android-developer/answer/9859673>):

- Category: **Video Players & Editors** (the app plays the user's own TV
  streams). Entertainment is the alternative.
- Tags: up to five ("Your app or game can have a maximum of five tags.", same
  page). Pick from Console's list what matches live TV, TV guide and recording.
- Contact details: the contact email from 1.1; website
  `https://github.com/bernhardberger/tvheadend-player`.

## 1.5 Main store listing

**Grow users → Store presence → Main store listing**: name, short and full
description from [section 2](listing.md#2-store-listing), then the graphics from
[section 3](graphics.md#3-graphics). Add the German translation with **Manage translations**
and paste the German text.

## 1.6 Android TV opt-in

From <https://developer.android.com/training/tv/publishing/distribute>: upload a
TV-capable bundle, add at least one TV screenshot and the TV banner, mention
Android TV in the description (both descriptions do), then **Setup → Advanced
settings → Form factors**. "Click Add Release Type and select Android TV."
Confirm the opt-in. Google then reviews the release against the TV app quality
guidelines. A failure arrives by email with the deficiencies, and the TV status
shows **Not approved**; fix and resubmit.

## 1.7 Closed testing track, testers, countries, release

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
3. **Testers:** the Testers tab is a choice between **Email lists** and
   **Google Groups**, not both. We use the Google Group
   `tvheadend-player-testers@googlegroups.com`
   (<https://groups.google.com/g/tvheadend-player-testers>): anyone can join,
   members can post, and only managers see the member list. Members must join
   the group before opting in.
4. **Feedback URL or email:** `https://github.com/bernhardberger/tvheadend-player/issues`.
5. Opt-in link: <https://play.google.com/apps/testing/at.bernhardberger.tvhplayer>.
   "Each tester needs to opt in using the link."
6. **Create new release:** upload the AAB from [What gets uploaded](../play-store.md#what-gets-uploaded).
   Submitted as `0.3.2 (164)` on the track Closed testing - Alpha, all 179
   countries and regions. Paste both release notes from
   [section 2.3](listing.md#23-release-notes). If Console offers an upload for the
   deobfuscation (R8 mapping) file, use the Play mapping from the same directory.
7. **Save**, then **Review release**. Read every error and warning before going
   on.

## 1.8 Send for review

**Publishing overview → Send changes for review.** "Processing can take a few
hours or up to seven days (or longer in exceptional cases)"
(<https://support.google.com/googleplay/android-developer/answer/9859654>). The
closed-test release is reviewed, including the Android TV review from 1.6.

Sent 2026-10-30: 16 changes (listing, app content, TV opt-in, Alpha track with
0.3.2 (164)). Managed publishing is off, so the closed test opens to group
members as soon as Google approves.

Sent 2026-11-01: `0.4.0 (165)` on the same track, with the new Android TV and
10-inch tablet screenshots. In review at the time of writing.

## 1.9 After approval

1. Send the opt-in link and the [tester instructions](testers.md#62-tester-instructions).
   Before production, testers can't find the app by searching Play; they need
   the link (<https://support.google.com/googleplay/android-developer/answer/9845334>).
2. Add your own account to the tester list and install it on the G10 first, to
   check the tester path on a TV (Google doesn't document it for TVs).
3. Feedback from testers arrives under **Ratings and reviews → Testing
   feedback**; "Beta feedback from users is only visible to you and can't be
   seen on Google Play"
   (<https://support.google.com/googleplay/android-developer/answer/138230>).
4. Later releases go to the same track.

## 1.10 Production access

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
