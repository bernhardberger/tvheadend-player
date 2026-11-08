# Media session contract

The player publishes what is playing through a standard Android media session.
Other apps, such as a Home Assistant bridge, read it with a
`MediaController` (Media3) or `android.media.session.MediaController`
(platform). The player sends nothing to other services itself.

This document is the contract for those readers. The implementation is
`client/src/main/java/at/bernhardberger/tvhplayer/playback/SessionPlaybackPlayer.kt`;
the session is built in `app/src/main/java/at/bernhardberger/tvhplayer/ui/MainActivity.kt`.

## Lifetime

The session exists while the player activity is started: it is created in
`MainActivity.onStart` and released in `onStop`. There is no
`MediaSessionService` and no notification. When the app goes to the
background, the session disappears even if the tuner stays subscribed.

The session activity is an explicit, immutable `PendingIntent` to
`MainActivity` with the private action
`at.bernhardberger.tvhplayer.action.SESSION_ACTIVITY`. The appliance entry
policy does not treat it as an autoplay or HOME entry.

## Standard fields

System UI shows title, subtitle and artwork. These stay short: at most 256
code points, trimmed, never with added prefixes or suffixes.

| Field | Live TV or radio | Recording |
|---|---|---|
| `title`, `displayTitle` | Channel name | Recording title |
| `subtitle` | Programme at the playback position | Episode subtitle, else channel name |
| `artist` | Programme at the playback position; unset when unknown | Channel name |
| `station` | Channel name | Channel name |
| `genre` | Programme genre text, else localized category | Localized category |
| `mediaType` | `TV_CHANNEL` (TV), `RADIO_STATION` (radio) | `TV_SHOW` (TV channel) |
| `artworkData` | Channel picon | Recording image, else channel picon |
| `artworkUri` | Never set | Never set |

`mediaType` is left unset for radio recordings and when the channel kind is
unknown or other.

The Android TV Now Playing card shows title and artist, so live artist names the programme.

"Programme at the playback position" follows the player's own timeline: while
watching in timeshift, the session names the programme on screen, not the one
airing now. It uses the same rule as the player chrome.

## Extras

Every value the system UI does not show is in `MediaMetadata.extras`, under the
prefix `at.bernhardberger.tvhplayer.session.`. A key is absent when the server
has no value. Text is trimmed; display text is at most 256 code points.

| Key (after prefix) | Type | Value |
|---|---|---|
| `schemaVersion` | Long | `1`. Raised when a key changes meaning or is removed. |
| `contentKind` | String | `live` or `recording` |
| `channelMediaKind` | String | `tv`, `radio`, `other` or `unknown` |
| `channelName` | String | Channel name |
| `channelNumber` | String | Channel number as the player shows it |
| `programmeTitle` | String | Programme (live) or recording title |
| `episodeSubtitle` | String | Episode subtitle |
| `episodeOnscreen` | String | Server's on-screen episode text, such as `S02E05` |
| `seasonNumber`, `seasonCount` | Long | Season number and count |
| `episodeNumber`, `episodeCount` | Long | Episode number and count |
| `partNumber`, `partCount` | Long | Part number and count |
| `programmeStartEpochMs`, `programmeStopEpochMs` | Long | Scheduled start and stop, epoch milliseconds |
| `programmeDurationMs` | Long | Stop minus start |
| `genre` | String | Same as the standard `genre` field |
| `contentGenreCode` | Long | EN 300 468 content byte: subgenre code, else category code shifted left by 4 |
| `shortDescription` | String | Summary, else description; whitespace collapsed, at most 1024 code points |
| `nextProgrammeTitle` | String | Next programme on the channel (live only) |
| `nextProgrammeStartEpochMs`, `nextProgrammeStopEpochMs` | Long | Next programme's start and stop (live only) |

The three programme timing keys appear together, and only when start is before
stop. The next-programme pair follows the same rule.

The genre is also stored under the platform key `android.media.metadata.GENRE`,
because the Media3-to-platform conversion drops the standard genre field.

## Timeline

Live items are marked live and dynamic and have no duration. A recording that
is still being written is dynamic. Finished recordings report their duration.
Next is channel up; Previous is channel down. Both use the remote channel keys'
order, scope and zap pacing. In the player, stepping gives the same feedback as
the remote channel keys. Behind browse, stepping shows no banner or other feedback.
While a channel change is still tuning, Next and Previous can be briefly unavailable.

Commands offered to controllers:

- Always: get current item, get metadata, get timeline, stop.
- Play/pause: recordings, and live channels that can be paused.
- Seek within the item: recordings that can be seeked.
- Next/Previous (including next/previous item): live channels while the player
  screen is showing, or while live playback continues behind browse with warm
  playback On or Audio only. Not offered for recordings or failed playback.

## Which controllers can send commands

Any app can read the session. Commands are accepted from the system UI, apps
holding `MEDIA_CONTENT_CONTROL` or `STATUS_BAR_SERVICE`, and apps the user has
granted notification access. Bridge apps such as the Home Assistant companion
app use notification access to read media sessions. Media3 ignores commands
from other apps.

The Player declares visibility of apps with notification access because Media3 cannot
check a controller package it cannot see on Android 11 and later.

## Artwork

Artwork is delivered only as bytes in `artworkData`; the session never hands
out server URLs, which need the player's credentials. Picons are square PNG
with transparency. Recording images are JPEG. Both are at most 512 pixels on
the long side and at most 256 KiB. When no image can be loaded, `artworkData`
is absent.

## Errors

When playback fails for good, the session reports an error with a fixed
message, never server or decoder text. The message is one of three fixed texts,
translated into the app's language. It can be less specific than what the app
shows on screen:

| Failed item | Message (English) |
|---|---|
| Recording | "Can’t play this recording" |
| Channel whose subscription failed or that is unavailable | "Channel not available" |
| Channel that failed for any other reason | "Can’t play this channel" |

Match on the error code rather than the text. The code describes the cause and
is independent of the message. The first matching row wins:

| Cause | `PlaybackException` code |
|---|---|
| Recording stopped while reading, including decoder failures (`RECORDING_READ_FAILED`) | `ERROR_CODE_IO_UNSPECIFIED` |
| Subscription failed or channel unavailable | `ERROR_CODE_REMOTE_ERROR` |
| Decoder or device fault | `ERROR_CODE_DECODING_FAILED` |
| Any other failure | `ERROR_CODE_REMOTE_ERROR` |

A recording decoder failure marked `RECORDING_READ_FAILED` reports
`ERROR_CODE_IO_UNSPECIFIED`, not `ERROR_CODE_DECODING_FAILED`.

While failed, only stop and the get commands are offered. Stop or new playback
clears the error.

## Known Media3 behavior

These come from Media3 1.11.1 and are not worked around:

- **Extras-only changes are delayed.** `MediaMetadata.equals` ignores extras,
  so a change that touches only extras reaches controllers with the next change
  to a standard field, such as the subtitle at the next programme.
- **Media3 controllers keep a cleared error.** After Stop on a failed item, a
  Media3 `MediaController` still reports the old error until the playback state
  changes again, because the session caches it. Platform controllers read it
  fresh and see it cleared.

## Verification

JVM tests drive a real Media3 `MediaSession` and `MediaController` against the
wrapper (`SessionPlaybackPlayerTest`). Robolectric cannot read metadata through
a platform `MediaController`, so the platform path is checked on the test TV
with the Media Session Validator.
