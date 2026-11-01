# 6. Testers

## 6.1 Invitations

Nothing is posted by agents. Post these only when the closed track has the
current release and the install path works on a real TV with your own account
(1.9). Before posting, `ROADMAP.md` and `artwork/screenshots/home.png` have to be
on `main`, because the posts link them.

Tvheadend forum, category Client Apps, with the image
`https://raw.githubusercontent.com/bernhardberger/tvheadend-player/main/artwork/screenshots/home.png`:

```text
Tvheadend Player for Android TV: join the beta

Hi all,

I'd like to invite you to the beta of Tvheadend Player, an unofficial open source (GPLv3) Android TV client for Tvheadend that started out as a fork of tvhstream. You can do everything with just the D-pad.

What's in it so far:
- Home with what's playing or recording, recent channels and what's on now
- Channels as cards, a grid or a list
- A program guide with genre colors and a genre filter
- Pause and rewind through your server's timeshift buffer
- Recordings with your server's folders, resume, watched status and free space
- Audio passthrough, DVB and teletext subtitles, and frame rate switching on Android 11+

Everything goes over HTSP, with no analytics, ads or accounts.

Screenshots and code: https://github.com/bernhardberger/tvheadend-player
Roadmap: https://github.com/bernhardberger/tvheadend-player/blob/main/ROADMAP.md

I'd love your feedback and ideas, and data points from different hardware and setups. The beta is also the way onto the Play Store, which will make it much easier for everyone to install.

You'll need an Android TV or Google TV device on Android 9+ and Tvheadend 4.3 (HTSP 36 or newer).

1. Join the group with the Google account on your TV: https://groups.google.com/g/tvheadend-player-testers
2. Opt in: https://play.google.com/apps/testing/at.bernhardberger.tvhplayer
3. Install it on the TV from the Play Store. It's not in search yet, so use the link from step 2.

Google needs at least 12 testers on the beta track for 14 days before the app can be published, so please keep it installed and stay in the whole time.

Please report bugs on GitHub if you can: https://github.com/bernhardberger/tvheadend-player/issues
Feedback and discussion are welcome right here in this thread or in the beta Google group.

Prefer to skip Play? The APKs are on GitHub, signed with the same key, so you can switch either way.
```

r/tvheadend, with `artwork/screenshots/home.png` as the image. Check the
subreddit's rules for posts about your own project first:

```text
Title: Tvheadend Player for Android TV: join the beta

I'd like to invite you to the beta of Tvheadend Player, an unofficial open source (GPLv3) Android TV and Google TV client for Tvheadend: Home, Channels, a program guide, timeshift and recordings over HTSP, all with just the D-pad.

I'd love your feedback and ideas, and data points from different hardware and setups. The beta is also the way onto the Play Store, which will make it much easier for everyone to install.

You'll need an Android TV or Google TV device on Android 9+ and Tvheadend 4.3.

1. Join the group with the Google account on your TV: https://groups.google.com/g/tvheadend-player-testers
2. Opt in: https://play.google.com/apps/testing/at.bernhardberger.tvhplayer
3. Install it on the TV from the Play Store. It's not in search yet, so use the link from step 2.

Google needs at least 12 testers on the beta track for 14 days before the app can be published, so please keep it installed and stay in the whole time.

Please report bugs on GitHub if you can: https://github.com/bernhardberger/tvheadend-player/issues
Feedback and discussion are welcome right here on this post or in the beta Google group.

GitHub: https://github.com/bernhardberger/tvheadend-player
Roadmap: https://github.com/bernhardberger/tvheadend-player/blob/main/ROADMAP.md
```

## 6.2 Tester instructions

Posted in the group as a welcome message:

```text
Thanks for testing Tvheadend Player.

1. Join this group with the same Google account that's signed in on your TV (you're here if you can read this).
2. Open this link on a phone or computer signed in with that account, and select "Become a tester": https://play.google.com/apps/testing/at.bernhardberger.tvhplayer
3. Install the app on your TV from the Play Store (still signed in with that account). The app doesn't show up in Play search yet, so use the link from step 2.
4. Please stay opted in for at least 14 days in a row and keep the app installed. Google requires those 14 days before the app can be published, so please don't opt out and back in. Leaving the group also removes you from the test.
5. Feedback: open an issue at https://github.com/bernhardberger/tvheadend-player/issues, post in this group, or leave private feedback on the app's Play Store page. Only I can read the private feedback.

If you have the GitHub APK installed, the Play version installs over it, and a newer GitHub APK installs over the Play version.
```

Step 3 isn't documented by Google for TVs; check it with your own account
first (1.9) and adjust the text. The last paragraph holds: Play App Signing
uses our key (SHA-256 checked in Console 2026-10-30, 1.2).
