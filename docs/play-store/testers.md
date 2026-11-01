# 6. Testers

## 6.1 Invitations

Nothing is posted by agents. Post these only when the closed track has the
current release and the install path works on a real TV with your own account
(1.9). Before posting, `ROADMAP.md` and `artwork/screenshots/home.png` have to be
on `main`, because the posts link them.

Tvheadend forum, category Client Apps, and other forums. The screenshots are inline Markdown images;
check the preview, and attach them by hand if the forum doesn't render them:

```text
Tvheadend Player for Android TV: beta testers welcome

Hi all,

I'd like to invite you to the beta of Tvheadend Player, an unofficial open source (GPLv3) Android TV client for Tvheadend that started out as a fork of tvhstream. It's focused on a D-pad-first experience and follows Google's TV design guidelines.

What's in it so far:
- A home screen that shows what's going on right now and gets you back to what you were doing
- Channels with customizable views: an immersive list, a classic PVR-style list, or a grid to see more at a glance
- A program guide with genre colors and a genre filter
- Server timeshift support
- Recordings with a folder browser
- Audio passthrough, DVB and teletext subtitles, and frame rate switching on Android 11+

Everything goes over HTSP, with no analytics, ads or accounts.

![Home](https://raw.githubusercontent.com/bernhardberger/tvheadend-player/main/artwork/screenshots/home.png)
![Channels](https://raw.githubusercontent.com/bernhardberger/tvheadend-player/main/artwork/screenshots/channels.png)
![Guide](https://raw.githubusercontent.com/bernhardberger/tvheadend-player/main/artwork/screenshots/guide.png)
![Player](https://raw.githubusercontent.com/bernhardberger/tvheadend-player/main/artwork/screenshots/player.png)
![Recordings](https://raw.githubusercontent.com/bernhardberger/tvheadend-player/main/artwork/screenshots/recordings-folders.png)

Code and more screenshots: https://github.com/bernhardberger/tvheadend-player
Roadmap: https://github.com/bernhardberger/tvheadend-player/blob/main/ROADMAP.md

I'd love your feedback and ideas, and data points from different hardware and setups. The beta is also the way onto the Play Store, which will make it much easier for everyone to install.

You'll need an Android TV or Google TV device on Android 9+ and Tvheadend 4.3 (HTSP 36 or newer).

1. Join the group with the Google account on your TV: https://groups.google.com/g/tvheadend-player-testers
2. Opt in: https://play.google.com/apps/testing/at.bernhardberger.tvhplayer
3. Install it on the TV from the Play Store. It's not in search yet, so use the link from step 2.

Google needs at least 12 testers on the beta track for 14 days before the app can be published, so I'd appreciate it if you stayed for the 14 days and gave feedback.

Please report bugs on GitHub if you can: https://github.com/bernhardberger/tvheadend-player/issues
Feedback and discussion are welcome right here in this thread or in the beta Google group.

Prefer to skip Play? The APKs are on GitHub, signed with the same key, so you can switch either way.
```

r/tvheadend, with `home.png`, `channels.png`, `guide.png` and `player.png` from
`artwork/screenshots/` as an image gallery. Check the
subreddit's rules for posts about your own project first:

```text
Title: Been working on an open source Android TV client for Tvheadend, beta testers welcome

I've been working on a Tvheadend client for Android TV and Google TV, and it's at the point where I'd like other people to try it. It's open source (GPLv3) and made for the remote, so it's D-pad first all the way through.

There's a home screen that gets you back to what you were doing, three channel views (immersive, PVR-style list, grid), a program guide, server timeshift and recordings with a folder browser. Screenshots are above.

Mostly I'm curious how it runs on your box, and what's missing before you'd use it every day.

If you've got Android TV or Google TV (Android 9+) and Tvheadend 4.3:

1. Join the group with the Google account on your TV: https://groups.google.com/g/tvheadend-player-testers
2. Opt in: https://play.google.com/apps/testing/at.bernhardberger.tvhplayer
3. Install it from the Play Store on the TV. It's not in search yet, so use the link from step 2.

Google wants 12 testers for 14 days before it can go on the Play Store for everyone, so stick around for the 14 days if you can. Don't want Play? The APKs are on GitHub.

Bugs go to GitHub issues if you can, anything else just comment here.

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
