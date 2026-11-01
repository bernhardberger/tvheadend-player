# Roadmap

Where this is going, as of 0.4.0. No dates. What's already shipped is in the
[changelog](CHANGELOG.md).

## Before 1.0

- **Google Play.** It's in a closed test right now. Google wants 12 testers for
  14 days in a row, then reviews the production release. Until then the APKs are
  on GitHub.
- **More devices.** Sony and NVIDIA Shield are next, and I'm comparing how
  interlaced channels look on them.
- **Remote keys.** GUIDE only toggles the channel schedule in the player right
  now. GUIDE and TV should work across the whole app. There'll also be a small
  screen that shows which key code your remote sends.
- **Polish.** Channels and the player get adjusted to the rules Home set. Focus
  and Back get a pass across all screens, and time and duration formats get tidied
  up.
- **Speed.** Startup, first picture after tuning and scrolling the guide with
  pictures, all measured on a budget Google TV.
- **TalkBack.** A pass over every screen.
- **Appliance build.** The build that makes a TV start straight into the app.
  HOME, GUIDE and waking the TV still need work.
- **Fire TV.** A build and an Amazon Appstore listing.

## I want these before 1.0, if they work out

- **Server profiles.** Store multiple servers or users and switch between them.
  One at a time, not concurrent.
- **Stream profile per channel.** Mainly so you can use server deinterlacing for
  just the channels that are a problem on your hardware. Tvheadend lets a client
  pick a profile per subscription, so the app can do this. I still have to try it.
- **Timer and series rules, simple version.** Doing this with a remote is finicky
  and cumbersome, so I'd rather add proper editing on touch devices or a companion
  app first. On the TV I might add a simplified interface, or at least a read-only
  one, just so you can check your rules and where a scheduled recording comes from.
- **Sleep timer.** After a countdown or at the end of the program.
- **Arabic and Hebrew.** The right-to-left layouts need fixing first.

## After 1.0

- **Phone and tablet app** ([issue #1](https://github.com/bernhardberger/tvheadend-player/issues/1)),
  probably with picture-in-picture. Android TV
  [doesn't allow it for media playback](https://developer.android.com/training/tv/get-started/multitasking).
- **Editing timers and series rules** on touch devices or in a companion app.
- **Search.**
- **Watching several servers at once.** Maybe, not soon.

## Ideas

Written down, not decided:

- Live preview of the focused channel, as a setting (it costs a tuner on every
  focus change)
- Tuning ahead when you hover over a channel, so OK plays sooner
- Reminders for programs that haven't started
- Markers for programs you can still watch from the start
- A Continue watching row in Recordings
- A Linux build, then game controller support for the Steam Deck and Steam Machine

## Not coming

- **A separate favorites list in 1.0.** Your Tvheadend channel tags are your
  favorites, and the channel group button picks one.
- **Rows on the Google TV home screen.** Google only shows them for apps it has
  certified.

Missing something? [Open an issue](https://github.com/bernhardberger/tvheadend-player/issues).
