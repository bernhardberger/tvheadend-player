# 4. App content answers

## 4.1 Privacy policy

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
  See [risk 2](risks.md#7-open-risks).

## 4.2 Ads

- **Console:** "You must declare whether your app contains ads": **Ads → Start
  declaration → Yes or No**
  (<https://support.google.com/googleplay/android-developer/answer/9859455>).
- **Answer:** No.
- **Evidence:** no ad SDK in the merged manifest or dependencies; the app shows
  only what the user's own server streams. Google's page doesn't address
  commercials inside broadcast streams the user receives; they aren't ads the
  app serves.

## 4.3 Advertising ID

- **Console:** apps targeting Android 13 or newer declare whether they use the
  advertising ID; using it needs `com.google.android.gms.permission.AD_ID`,
  which SDKs can merge in
  (<https://support.google.com/googleplay/android-developer/answer/6048248>).
  The page doesn't quote the Console question.
- **Answer:** No.
- **Evidence:** no `AD_ID` permission in the signed AAB's manifest; no Google
  Play services or ad libraries.

## 4.4 App access (sign-in details)

- **Console:** Google now calls it sign-in details. "Your sign-in details must
  be accessible at all times, reusable, and valid regardless of user location."
  There are **+ Add new instructions** (up to five sets) and **Any other
  instructions**
  (<https://support.google.com/googleplay/android-developer/answer/15748846>).
  The exact option labels aren't on the page.
- **Answer (saved 2026-10-30):** **Yes**, part of the app is restricted. One
  set, "Demo server reviewer account", with the review server's reviewer
  username and password in their own fields, the [reviewer
  instructions](review.md#51-reviewer-instructions) in **Any other information**, and
  "provide full access to all the features" ticked.
- **Evidence:** the app needs a Tvheadend server; without one it stays on
  setup. The review server (homelab `docs/operator/tvh-review.md`) is reachable
  at all times from anywhere and its account doesn't expire. Credentials live
  only in Console and the homelab private store, never in this repository.

## 4.5 Content rating (IARC questionnaire)

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

## 4.6 Target audience and content

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

## 4.7 Data safety

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
See [owner decisions](decisions.md#8-owner-decisions).

## 4.8 News apps

- **Console:** "The declaration is required as of Aug 27, 2025"; it applies to
  apps in the News and Magazines category or presenting themselves as news
  (<https://support.google.com/googleplay/android-developer/answer/16189314>).
- **Answer:** No, not a news app. The app plays whatever channels the user's
  server has, which can include news channels, but it publishes nothing itself.

## 4.9 Government apps

- **Console:** "developers must complete the Government apps declaration"
  (<https://support.google.com/googleplay/android-developer/answer/9514050>).
- **Answer:** not developed by or for a government.

## 4.10 Financial features

- **Console:** select **"My app doesn't provide any financial features"**
  (<https://support.google.com/googleplay/android-developer/answer/13849271>).

## 4.11 Health

- **Console:** select **"My app doesn't provide any health features"**
  (<https://support.google.com/googleplay/android-developer/answer/14738291>).

## 4.12 Permissions and foreground services

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
