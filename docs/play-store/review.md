# 5. Reviewer instructions and demo video

## 5.1 Reviewer instructions

**Sign-in details → Add details** has a name, username and password fields, and
one free-text field, "Any other information required to access your app",
limited to 500 characters; reviewers can't contact you. Saved 2026-10-30 (459
characters):

```text
Tvheadend Player is a client for Tvheadend, TV server software that users self-host on their local network. The app has no content or accounts of its own, so we run a demo server for review.

To sign in:
- Welcome screen: select Continue
- Server address: tvh-demo.bernhardberger.at
- Port: 19982
- Enter the username and password above, then Connect

The server has two channels with open movies, a guide and recordings. Its account can record and play them.
```

The longer text below is for an appeal or a reply to a rejection (replace the
placeholder):

```text
Tvheadend Player is a client for Tvheadend, free and open-source TV server software that users self-host on their local network, such as a PC or NAS with a TV tuner. The app has no content, service or account of its own. Like an email app needs a mail server, it needs a Tvheadend server.

For review we run a demo server at tvh-demo.bernhardberger.at, port 19982, with the username and password given in Sign-in details. It has two channels playing open movies (Big Buck Bunny, Sintel), a program guide, recordings, and an account that can record and play them.

To sign in: on the Welcome screen select Continue, enter the server address and port, select Continue, enter the username and password, and select Connect.

Questions: tvhplayer@bernhardberger.at
```

What a reviewer needs to see every feature: a reachable Tvheadend 4.3 server
with HTSP 36 or newer, a few channels with guide data, a timeshift buffer
(pause and rewind), a recording profile with a few finished recordings in
folders, and an account allowed to record.

## 5.2 Demo video shot list

The owner records on the G10 (TCL C655, Google TV) with 0.3.0 from Play or the
GitHub Play APK, against the test server. Aim for three to five minutes, one
take per scene, no music. Keep server addresses and the password out of the
frame: use the network search result, and the password field stays masked. The
video goes to a third party, so channels with open or fictional content are
better than broadcast TV where you have them.

1. **Launch:** Google TV home with the app's banner in Your apps; open it.
2. **Setup:** Server step finds the server; select it. Account step with
   username and masked password. Connect; channels load.
3. **Channels:** Immersive, Grid and List views; the group filter; Hold OK on a
   channel for its menu.
4. **Live TV:** play a channel. Player controls, pause and resume, rewind and
   back to live. Channel up and down, type a channel number, the channel list
   under the controls, the schedule panel, Audio and Subtitles, the (i) details.
5. **Media keys:** play/pause on the remote; D-pad center pauses.
6. **Guide:** move through the grid, change the day, open a program's details,
   record a future program and show its recording mark, cancel it.
7. **Recordings:** folders, the Recording now folder if something records, play
   a recording, stop, reopen it to show resume, mark watched, delete with its
   confirmation, free space.
8. **Browsing while watching:** open the side menu while live TV plays behind.
9. **Settings:** Connection, Playback, General (language, clock format), About
   (version 0.3.0 (162), license, notices).
10. **Back:** Back from the top-level screen returns to the Google TV home
    screen.
11. **Recovery (optional):** stop the server or unplug its network, show the
    error and Try again.

Upload it unlisted to YouTube (or another host you choose) and put the link in
5.1.
