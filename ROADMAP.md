# Roadmap

This is what I plan to work on, as of 0.4.0 (November 2026). It's a plan and not
a promise: there are no dates, and things can move or drop when testing shows
something more important. What has already shipped is in the
[changelog](CHANGELOG.md).

The app covers everyday TV already: Home, Channels, the program guide, the
player and Recordings. The work left before 1.0 is mostly getting it onto Google
Play, testing it on more devices, and finishing what's rough.

## Before 1.0

**Google Play.** The app is in a closed test. Google requires at least 12 testers
to stay in the test for 14 days in a row before it lets a new developer publish
an app. After that comes Google's review of the production release. Until then
you can install the APKs from GitHub.

**More devices.** I'm testing on more TVs and boxes, including Sony and NVIDIA
Shield, and comparing how interlaced channels look on them. Reports from hardware
I don't have help most here.

**Remote keys.** The GUIDE key toggles the channel schedule in the player today.
GUIDE and TV should work across the whole app. Remotes differ, so a small screen
that shows which key code your remote sends comes first, which lets you report a
key that does nothing.

**Polish.** Home set some new rules for how screens look and move, and Channels
and the player get adjusted to match. Focus and Back get a consistency pass across
all screens, and time and duration formats get tidied up.

**Speed.** I keep measuring startup, the first picture after tuning, and scrolling
the guide with program pictures on a budget Google TV, and I work on what feels
slow there.

**TalkBack.** Every screen gets a pass with Android's screen reader.

**Appliance build.** This is the build that makes a TV start straight into the
app. HOME, GUIDE and waking the TV still need settling, and it depends on what
each TV's firmware allows.

**Fire TV.** A Fire TV build and an Amazon Appstore listing, probably before 1.0.
Fire OS is based on Android, so I expect this to be relatively cheap.

## Maybe before 1.0, maybe after

- **Recording timers and series rules in the app.** If they come, they come
  together, either at the end of the way to 1.0 or with the phone app, which
  needs them anyway. Until then you set them up in the Tvheadend web interface,
  and what they record shows up in Recordings.
- **Sleep timer.** Stop playback after a countdown or at the end of the program,
  with a warning on screen first.
- **Arabic and Hebrew.** These need right-to-left layout fixes before they can
  ship.

## After 1.0

- **Phone and tablet app** ([issue #1](https://github.com/bernhardberger/tvheadend-player/issues/1)).
  Picture-in-picture could come with it. Android TV doesn't allow
  [media playback in picture-in-picture](https://developer.android.com/training/tv/get-started/multitasking),
  so it can't come to the TV app.
- **Search.** What it should cover is still open.
- **Multiple servers.** Maybe, and not for a while.

## Ideas, not scheduled

I've written these down, but they aren't decided:

- Live preview of the focused channel, as a setting. Each focus change tunes, so
  it costs a tuner.
- Tuning ahead when you hover over a channel, so OK plays sooner.
- Reminders for programs that haven't started.
- Markers for programs you can still watch from the start.
- A Continue watching row in Recordings.
- A Linux build, and after that one with game controller support for the Steam
  Deck and Steam Machine.

## What stays with Tvheadend or Google

- **Favorites** are your Tvheadend channel tags. The channel group button picks
  which tag you see, and I don't plan a separate favorites list for 1.0.
- **Rows on the Google TV home screen** aren't coming. I tested it on a Google TV
  in October 2026: the launcher showed none of what the app published, and Google
  only shows it for apps it has certified. Voice search from the launcher didn't
  reach the app either.

## Tell me what's missing

If something here matters to you, or something you need isn't here, please
[open an issue](https://github.com/bernhardberger/tvheadend-player/issues). That's
how the order changes.
