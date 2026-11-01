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
- **Speed.** Making it feel fast on budget Google TVs.
- **Appliance build.** For TVs that should start straight into the app.
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
- **RTL language support.** A few bugs to fix first.

## After 1.0

- **Phone and tablet app** ([issue #1](https://github.com/bernhardberger/tvheadend-player/issues/1))
  with picture-in-picture.
- **Editing timers and series rules** on touch devices or in a companion app.
- **Search.**

## Ideas

Some things I've been thinking about:

- Live preview of the focused channel, as a setting (it costs a tuner on every
  focus change)
- Tuning ahead when you hover over a channel, so OK plays sooner
- Reminders for programs that haven't started
- Catch-up markers
- More ways to browse Recordings (today it mostly focuses on folders)
- A Linux build for x86 and arm64, then game controller support for the Steam Deck,
  Steam Machine and other distros and hardware

## Out of scope for now

- **Watching several servers at once.**
- **A separate favorites list.** Tvheadend channel tags do that job.
- **Rows on the Google TV home screen.** Google only shows them for apps it has
  certified.

Missing something? [Open an issue](https://github.com/bernhardberger/tvheadend-player/issues).
