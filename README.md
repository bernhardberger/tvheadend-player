# Tvheadend Player

![Tvheadend Player](artwork/tvheadend-player-logo.png)

Tvheadend Player is a live TV, programme guide and recordings app for Android TV
and Google TV. It connects to your own Tvheadend server and is built for the
remote: you can use everything with the D-pad.

> [!IMPORTANT]
> This project is under active development and does not yet have a stable public
> release. It is not an official Tvheadend application and is not affiliated
> with or endorsed by the Tvheadend project.

<!-- screenshots: added from fixture captures later -->

## What you need

- A Tvheadend server that the TV can reach over HTSP, Tvheadend's streaming
  protocol. If the server is too old, the app tells you so.
- An Android TV or Google TV device running Android 9 or newer.
- The TV's remote. Phones and tablets are not supported.

## What it does

**Live TV**

- Watch your channels and switch with channel up and down or by typing a channel
  number.
- Pause live TV and jump back, when your server provides a timeshift buffer.
- Pick audio and subtitle tracks, and set preferred languages.
- Switch the TV to the video's frame rate, when the TV supports it.
- Channel logos stay visible when the server is unreachable.

**Programme guide**

- A widescreen guide with channels in rows and time across.
- Programme details with description, episode information and recording state.
- Watch, watch from the start, record or cancel a recording from the guide.

**Recordings**

- Browse upcoming, finished and failed recordings, with your server's folders.
- Play a recording, resume where you stopped, and jump with the remote.
- Record, stop and delete, as far as your Tvheadend account is allowed to.
- See the free recording space on the server.

The app is available in English and German. Which actions you see, such as
recording, depends on the permissions of your Tvheadend account.

## Download and install

Open the [latest release](https://github.com/bernhardberger/tvheadend-player/releases/latest)
and download one APK:

| APK | Choose it when |
|---|---|
| `…-play.apk` | You want the normal app. Most people want this one. |
| `…-appliance.apk` | The TV should start straight into the app. HOME opens the app, and the GUIDE key does too once you turn on its accessibility service in Android settings. Not every TV firmware allows this. |

The Appliance build is a work in progress: expect rough edges and changes between
releases. Use the Play build for everyday use.

The two builds can be installed side by side. To install an APK without the
Play Store, allow installing from unknown sources for the app you use to open
it, such as a file manager. This project does not promise a Google Play listing.

## First start

The app asks for your server's address, the HTSP port, and your Tvheadend
username and password. After you save them, it connects and loads your channels and
guide. You can change the connection later in Settings.

The connection uses HTSP, which is not encrypted. Use it on your home network or
through a VPN.

## Privacy

The app has no analytics, advertising or external account. Your server address
and login stay on the TV, and the password is stored encrypted. The app talks
only to your Tvheadend server. See the [privacy policy](privacy-policy.md).

## License and source

Tvheadend Player is licensed under the
[GNU General Public License v3.0](LICENSE). Each GitHub release includes the
corresponding source for the APKs.

The app descends from [`Preclikos/tvhstream`](https://github.com/Preclikos/tvhstream)
and keeps its history and license. It is now maintained as a separate project,
and generic fixes can still be passed back. [`NOTICE.md`](NOTICE.md) lists the
attribution for the predecessor, artwork, dependencies and native libraries.

To build the app yourself or contribute, see [`CONTRIBUTING.md`](CONTRIBUTING.md).
