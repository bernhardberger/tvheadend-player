# Privacy Policy

Last updated: 2026-10-28

This policy describes Tvheadend Player, an independent open-source Android TV
client for Tvheadend servers. It is not an official Tvheadend application.

## Data stored on the TV

The app stores the following data in its Android app-private storage:

- Tvheadend server host and HTSP port
- The Tvheadend username and password, each encrypted with AES-GCM using a
  non-exportable Android Keystore key
- App and playback settings: language, clock format, channel groups and the
  selected group, audio and subtitle choices, the selected stream profile, and
  whether to start playback when the app opens
- The last-played channel and a short list of recently played channels
- Accent colors taken from channel logos

The app also keeps a cache in its app-private cache directory, so that saved
channels and logos can appear before the server is ready or while it is
unreachable:

- A copy of the channel list and the programme guide, kept for up to 7 days
- Channel logos and programme pictures, kept for up to 30 days and up to 64 MiB

Android may remove cache contents at any time; the app fetches them again from
the server. Decoded images are also held in memory while the app runs.

The saved password is not displayed in connection settings. Android backup and
device-transfer extraction are disabled for the app.

## Network communication

The app connects only to the Tvheadend server configured by the user. It sends
the username and HTSP challenge-response authentication data needed to connect,
and receives channel, guide, icon, and media data from that server.

During setup, the app searches your local network for Tvheadend servers with
multicast DNS (the `_htsp._tcp` service type); this search stays on the local
network and sends nothing elsewhere.

Direct HTSP uses a raw TCP connection. It does not encrypt or authenticate the
network transport and should be used only on a trusted local network or through
a protected tunnel such as a VPN.

## Analytics and third parties

The app does not include Firebase, analytics, advertising, crash-reporting, or
user-tracking services. It does not send app usage or credential data to the
project maintainers.

The app uses open-source libraries bundled into the application. Those
libraries process data locally except when participating in the configured
Tvheadend connection.

## Accessibility service

Only the Appliance build of the app contains an Android accessibility service;
the Play build has none. The Appliance service is used only after the user
enables it in system settings. The service:

- handles only the standard GUIDE key and the known TCL TV key
- opens the app after service connection or display wake
- does not subscribe to accessibility events
- cannot retrieve window content and does not inspect text or other apps

## Retention and deletion

Settings remain on the TV until they are replaced, explicitly cleared, Android
app data is cleared, or the app is uninstalled. Cached channel, guide and image
data expires as described above and may be removed by Android at any time. Uninstalling or clearing app data also removes the Android
Keystore key, making any remaining encrypted password blob unusable.

## Source and questions

The app is open-source under GPLv3. Current source and privacy-policy changes
are available at https://github.com/bernhardberger/tvheadend-player.

For questions about this policy or a privacy concern, open an issue at
https://github.com/bernhardberger/tvheadend-player/issues.
