# Player remote navigation

A map for operating TVHeadend Player with remote keys. It is derived from the
source; if a screenshot disagrees, trust the screenshot and report the difference.
Screenshots never show the video (SurfaceView); they do show controls, menus and
subtitles.

## Start

- Launch with `./tools/device launch`, not a hand-written implicit intent.
- With a configured server and autoplay on (the default), startup opens the live
  player on the last-played channel. Otherwise it opens Channels. A preparing or
  connection screen may show first.

## Browse screens

- The side rail lists Channels, Guide (only when "Show EPG menu" is on) and
  Recordings, with Settings at the bottom. Up/Down move in the rail; Center opens.
- Back in a screen's content moves focus to the rail. From another destination,
  Back selects Channels. Back at Channels may return once to the playing channel
  or exit the app.

## Live player without an overlay

- Up or Down shows the controls. Use them instead of Center: with timeshift,
  Center also pauses or resumes.
- Left seeks back with timeshift; without timeshift it opens the channel shelf.
  Right seeks forward with timeshift.
- Channel up/down tune the adjacent channel. Media play/pause pauses and resumes
  timeshift.
- Digits tune by channel number: `keys 1` for channel 1, `keys 1 0` for 10. Player
  tunes 250 ms after the last digit once the number has as many digits as the
  highest channel number, otherwise after 1.5 s.
- **Back closes the player** (it returns to Channels or wherever playback was
  opened). Check that no overlay is open before pressing Back.

## Live player controls

- The controls hide after 5 s without input while playback is steady. They stay
  during a pending seek, the channel shelf, number entry, recovery and errors.
  To hide them, wait instead of pressing Back: a Back that lands just after they
  auto-hide closes the player.
- Looking at a screenshot takes longer than 5 s, so the controls have usually
  hidden again before your next command, and a Center then pauses instead. Check a
  path once, wait 6 s, then send the whole path again from the hidden state with
  the Center in the same `keys` call, for example `keys up right right right right
  --screenshot gear`, then after 6 s `keys up right right right right center center
  --screenshot audio`. After hiding, the controls open again with focus on
  Pause/Play (on Info without timeshift).
- Action row, left to right: Pause/Play (only with timeshift), **Stop**, Info,
  Record, Settings (gear). Focus starts on Pause/Play, otherwise on Info.
- **Never press Center on Stop**: it stops playback and closes the player.
- Up from the action row reaches the timeline: Left/Right queue a seek, Center
  pauses or resumes, Up/Down commit the seek. Avoid it unless you are testing seeks.
- Down from the action row opens the channel shelf.
- "Go live" appears above the timeline only when playback is behind live.

## Overlays

- Channel shelf: Left/Right browse, Center tunes the focused channel, Up or Back
  closes it.
- Info panel: opened from Info; Back closes it. Its Record action asks for
  confirmation, with focus starting on the safe Back button and Confirm to the right.
- Playback options (the gear, not app Settings): Audio track, Subtitles, Display
  mode, Stats for nerds. Center or Right opens a row; in a list, Center selects.
  Back or Left returns to the list of rows; Back or Left there closes the sheet and
  focus returns to the gear.
- In Audio track, a checked Automatic row means Player chooses the track; the list
  does not mark which track is playing then.
- Back closes one layer at a time: confirmation, Info, options page, options
  list, number entry, channel shelf, pending seek, controls, stats. Only the next
  Back closes the player, so press Back once and look before pressing it again.

## Recording player

- Center pauses or resumes and shows the controls; Left/Right seek.
- Action row: Pause/Play, Stop, Info, Settings (no Record). No channel shelf and no
  "Go live". The playback options and the Back order match the live player.

## App Settings

- Rail → Settings: General, Channel tags, Connection, Player, Appliance.
- Player: timeshift, refresh-rate matching, audio passthrough, three audio
  languages, audio format, audio description, subtitle language, keep channel,
  stream profiles. Languages, format and keep channel open a sub-list; Back or Left
  goes up one level.
- **Never capture Connection**: its overview shows host and port, and its editor
  shows username and password fields.

## Other keys and risks

- Recordings details offer Stop, Cancel or Delete recording. Their confirmation
  starts on Back; Confirm is to the right.
- Guide is taken by the appliance accessibility service (when enabled) as an
  app-entry key. It is not a shortcut to the Guide screen from the player.
- Home leaves the app. A live channel with timeshift then pauses and stays tuned
  for the keep-channel time (default 20 minutes).
