# 7. Open risks

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
   Tvheadend geometry ([`NOTICE.md`](../../NOTICE.md)), and the name describes
   compatibility ([`product-identity-plan.md`](../product-identity-plan.md)). What
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
