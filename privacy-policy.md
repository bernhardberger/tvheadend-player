# Privacy Policy

Last updated: 2026-07-24

This policy describes TVHeadend Player for TV, an independent open-source Android
TV client for TVHeadend servers.

## Data stored on the TV

The app stores the following data in its Android app-private storage:

- TVHeadend server host, HTSP port, and username
- TVHeadend password, encrypted with AES-GCM using a non-exportable Android
  Keystore key
- Playback preferences, selected stream profile, and last-played channel
- Cached channel icons and transient guide/playback data

The saved password is not displayed in connection settings. Android backup and
device-transfer extraction are disabled for the app.

## Network communication

The app connects only to the TVHeadend server configured by the user. It sends
the username and HTSP challenge-response authentication data needed to connect,
and receives channel, guide, icon, and media data from that server.

Direct HTSP uses a raw TCP connection. It does not encrypt or authenticate the
network transport and should be used only on a trusted local network or through
a protected tunnel such as a VPN.

## Analytics and third parties

The app does not include Firebase, analytics, advertising, crash-reporting, or
user-tracking services. It does not send app usage or credential data to the
project maintainers.

The app uses open-source libraries bundled into the application. Those
libraries process data locally except when participating in the configured
TVHeadend connection.

## Accessibility service

Appliance mode can optionally use an Android accessibility service after the
user enables it in system settings. The service:

- handles only the standard GUIDE key and the known TCL TV key
- opens the app after service connection or display wake
- does not subscribe to accessibility events
- cannot retrieve window content and does not inspect text or other apps

## Retention and deletion

Settings remain on the TV until they are replaced, explicitly cleared, Android
app data is cleared, or the app is uninstalled. Cached data may be removed by
Android at any time. Uninstalling or clearing app data also removes the Android
Keystore key, making any remaining encrypted password blob unusable.

## Source and questions

The app is open-source under GPLv3. Current source and privacy-policy changes
are available at https://github.com/bernhardberger/tvheadend-player.
