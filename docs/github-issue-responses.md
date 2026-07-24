# Draft GitHub issue responses (not yet posted)

Fixes are in commit `f93c061` on `master` (ships in the next build).

---

## Fixed — comment + close

### #3 — Unauthenticated settings not working
> Fixed. Auto-connect was requiring a non-empty username **and** password before it would even attempt to connect, so an unauthenticated TVHeadend never pulled channels unless credentials were temporarily populated. Auto-connect now triggers on host + port alone, and the HTSP auth step is skipped entirely when the username/password are empty.

### #4 — Subtitles defaulting on
> Fixed. Subtitles now default to **off** unless you set a preferred subtitle language in Settings → Player. The audio/subtitle track dialogs also highlight the currently active track (and "Off") with a check mark, so the current selection is always visible.

### #5 — Top of screen banner
> Fixed. The label was simply wrong — it showed the end time but said "Ends in". It now reads **"Ends at: HH:mm"**. The programme description is now multi-line (up to 2 lines) instead of being truncated to a single line with "…".

### #8 — Stream overlay font sizes
> Fixed. Increased the font size of the programme description and the "Up next" line in the playback overlay for better 10-foot readability.

### #9 — Popup keyboard in settings
> Fixed. Settings fields are now read-only while you navigate with the D-pad, so the on-screen keyboard no longer pops up on focus. It only appears when you press OK on a field to edit it (Back closes it). You can now move through Host → Port → Username → Password → Save without the keyboard appearing each time.

---

## Addressed (server-side note) — comment

### #7 — No channel icons showing
> This is an HTSP-only client, so it intentionally won't fetch a raw `http(s)://` "User icon" URL over HTTP. Icons that TVHeadend serves **over HTSP** display fine. The recommended fix is to enable **Image cache** in TVHeadend (Configuration → General → Image caching): TVHeadend then downloads the icon and serves it as an HTSP-openable path, and it shows up in the app. On the client side I made raw URLs fall back cleanly to the placeholder instead of attempting a doomed HTSP file open.

---

## Won't do — comment + close

### #6 — Video stream in channel view
> Thanks for the suggestion. As you noticed, the left-hand side panel already covers this: it lets you browse channels (with live now/next info) while the current channel keeps playing in the background, which addresses the main use case. A full picture-in-picture tile in the channel list is out of scope for now, so I'm closing this. The side panel will keep being improved instead.

### #10 — Android tablets/phones
> Thanks for the interest. TVHeadend Player is intentionally built as a 10-foot, D-pad-first Android **TV** experience (leanback UI, focus navigation, HTSP streaming). A touch-first phone/tablet client is a substantially different app and isn't planned, so I'm closing this as out of scope.

---

## Still open
- **#2** — m3u8 / IPTV-network playback freezes after the first frame (under investigation; suspected PTS/timestamp handling in the HTSP playback pipeline).
- **#11** — Playback of recordings (feature, not started).
