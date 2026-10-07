# Fictional receiver-style EPG

Offline test data for dense, uneven Guide layouts, programme details and DVR
states. This is test-only data, not a production channel list or server export.
No captures or visual/Guide verification are implied by its validation.

All channels, programmes, people, teams, places and stories are fictional.
New titles were written for this fixture without borrowing real broadcasters,
brands, public figures, clubs, leagues, films or series. No online title-clearance
search was performed; coincidental resemblance cannot be ruled out.

## Provenance

Read-only input: the operator's local creative-assets batch
`grok-broadcast-20260911T004615Z` (the same batch as `fixture-art/`).

| Input relative to batch | SHA-256 |
| --- | --- |
| `catalogue/epg.json` | `272def30c91dcd0ca77570f49277d4620623067753dc36d79a541d8640511fb5` |
| `manifest.json` | `f03951a5a5c004fca6aa7ee2f13d0b3ded2c5a4bf4f2e6719f281b4a5f442b21` |

The old EPG's `channels` is the integer **27**, not a channel array. Channel IDs,
numbers, names and category/genre were therefore taken from the batch manifest,
with all 27 identities preserved. Picon names come from the checked-in
`app/src/test/resources/fixture-art/README.md` and its original PNGs; absent
channel picons are explicitly null. The extra Landfunk 1 and Reedfen 4 picons
are not source EPG channels and are not added to the line-up.

The source contains **21 artwork programme identities plus one filler identity**.
All 21 programme IDs, titles and synopses are retained verbatim in the generator's
`REUSED` table and occur in the fixture. `quiet-hours-filler` is not retained:
Quiet Hours the *channel* instead runs music/lifestyle programming. The generator
adds 48 fictional identities, episode titles, episode numbers and event genres.
The old event IDs, timing, filler and DVR fixtures are replaced completely.

`has_art` means membership of those 21 creative-batch programme IDs, **not** that
a matching key-art JPEG exists in Player's smaller exported art pack. Some have
only stills in the pack and some have no exported image. Consumers must still
resolve actual assets through the fixture-art mapping, never assume a filename
from this boolean. New programmes have `has_art: false`.

The embedded input tables make regeneration self-contained: it does not read or
write the creative-assets checkout. Its recorded input hashes are provenance,
not a runtime dependency. Original fixture-art attribution remains with that
pack; this data does not assert ownership of the original art.

## Regenerate and validate

From the Player worktree root, with Python 3.9+ and system IANA timezone data:

```sh
./tools/generate-fixture-epg
PYTHONPYCACHEPREFIX=/tmp/opencode/fixture-epg-pycache python3 -m py_compile tools/generate-fixture-epg
```

Python standard library only. The fixed seed is `20260914`; there are no network
requests, machine-time inputs, credentials or external packages. The generator
validates before writing `app/src/test/resources/fixture-epg/epg.json`, prints
the statistics below, and exits nonzero on validation failure. It does not edit
this README. When changing the schedule, update the stats and edge-case table.

Validation checks unique event/channel identities and channel numbers, exact
coverage partitioned into events plus declared gaps, positive whole-minute
intervals, no overlaps/undeclared holes, existing picons, episode fields, original
programme metadata, art membership, DVR links/state timing/series/conflicts,
hourly and evening news, long titles, endurance/overnight events, sparse and
two-hour missing-data gaps, consecutive five-minute slots, kids' hours/lengths,
off-grid evening starts, and the strict 1,500,000-byte budget.

The realism validator also enforces a declared plausible minute range for every
programme (`DURATION_RANGES`), unique subtitle ownership across programme IDs,
stable `(programme_id, season, episode)` → subtitle mappings, and increasing new
episode numbers within each channel's series run. Repeated episodes must match
the same canonical catalogue entry. No event may be clipped at the coverage end.
Adjacent identical non-series programmes are rejected except the four explicit
weather/music loops in `DELIBERATE_LOOPS`: `weather-loop`,
`news-skyglass-weather`, `music-night`, and `music-day`. The short weather loop
is intentional for the six consecutive five-minute weather editions; ordinary
drawing shorts, news noticeboards and sports highlights are not exempt.

## Schema and clock

All windows are half-open `[start, stop)`. Schedule construction uses
`Europe/Vienna`; this entire week is CEST (UTC+02:00), with no DST transition.
Strings are UTC ISO-8601 with `+00:00`, as in the source.

- `reference_start`: `2026-09-13T22:00:00+00:00`, Monday 14 September 00:00 local.
- `coverage_stop`: `2026-09-17T04:00:00+00:00`, Thursday 17 September 06:00 local.
  The 78-hour window includes the complete final late night.
- `snapshot_time`: `2026-09-15T17:40:00+00:00`, Tuesday 19:40 local, the simulated
  DVR “now”; never interpret recording state against the machine's current time.
- `timezone`, `seed`, `generator`, `source`: generation/provenance metadata.
  `source.path` is relative to the named creative batch.
- `channels[]`: `channel_id`, integer `number`, `name`, source `genre`,
  `locale` (`en` or `de`), `picon` (PNG basename under fixture-art/picons, or null).
- `events[]`: `event_id`, `channel_id`, `programme_id`, `title`, `genre`,
  `synopsis`, `start`, `stop`, integer `duration_min`, `locale`, boolean `has_art`.
  Series also have `subtitle`, positive integer `season` and `episode`.
  Some live/repeat blocks carry a subtitle without episode numbering. Films
  have a fictional synopsis and film genre, without years or real metadata.
  Sorted by UTC start, then channel ID. IDs contain the channel and *local*
  start stamp; use the timestamp fields, not the ID, for clock calculations.
- `gaps[]`: `channel_id`, `start`, `stop`, `reason`: `off-air`,
  `missing-guide-data`, or `sparse-guide-ended`. These are not events; empty Guide
  areas must stay empty. Adjacent off-air intervals are merged across midnight.
- `dvr_fixtures[]`: `dvr_id`, valid `event_id`, `channel_id`, `title`, `state`.
  Optional `series_id` identifies the series programme ID; `error` explains a
  fictional failed recording. The two scheduled entries with `conflict: true`
  and `conflict_group: "one-tuner-evening"` model one shared available tuner.
  They overlap on different channels; they are not overlapping EPG events.

## Scheduling rules and profile choices

- News desks: 12/15-minute bulletins every hour, 3/5-minute weather immediately
  afterwards, selected `:30` five-minute headlines, 25–45-minute magazines and
  short reports, 19:30–19:50 main news, and overnight recorded interview reviews.
  No news channel uses Quiet Hours or synthetic filler.
- Amber Stage, Amber Plus and Glass Harbor Drama: morning magazines, back-to-back
  45/50-minute afternoon episodes; access prime-time at 18:00, 18:47, 19:30,
  19:52 and 20:00; 20:15 films (95/125 minutes) or two 45-minute episodes;
  22:20 late film; night repeats including 00:40. Sister channels rotate their
  prime-time nights instead of airing the same film simultaneously.
- Sport: preview/post-show blocks, three-hour arena sessions, highlights and
  training magazines. Ridge Cycle has a five-hour live stage daily.
- Kids: alternating hour blocks of 7/8/20/25 or 12/13/15/20-minute shorts,
  06:00–20:00 only. Short animation uses 7–13 minutes, science/craft/adventure
  episodes 15–25 minutes. No fabricated programme-ends event occupies the off-air gap.
- Nature/documentary: 45–55-minute episodes, an evening two-part documentary,
  and a 90-minute feature. Foundry Docs is German-language factual programming.
- Harbor Lights and Quiet Hours: 4/6-hour music blocks and a three-hour concert.
  Coastal Miles uses travel/lifestyle blocks and a seven-hour overnight towpath
  journey. These follow the source descriptions rather than forcing every
  `entertainment` channel into a generalist template.
- Skyglass Weather retains source genre `news` but uses a weather-loop profile
  (including overnight six-hour cycles), not a general news-desk schedule.
- The broad source `live` category is interpreted as talk/quiz for Lumen Live,
  Bright Ring and Roundtable Late, and concerts/music for Harbor Lights.
- Anime channels retain their animation profiles; Ion Frame deliberately has
  non-rounded evening starts. City Desk and Foundry Docs are the two `de`
  channels, with German titles/subtitles and umlauts; their source channel
  identity and genre remain unchanged. Other channels stay English.
- Each of the 27 series has an ordered, genre-specific episode-title catalogue.
  Series-specific arcs and chapters generate full unique titles; no shared
  title pool or modulo cycling remains. Longer-running strands have 96–144
  entries. Sister-channel repeats of the same season/episode retain the same
  title; daily returns on a channel advance through that series' catalogue.
  Two-part documentaries have their own six-episode catalogues, avoiding
  inconsistent episode titles when other documentary strands rotate randomly.
- The drawing programme stays ten minutes. At 20:00 a separate feature preview
  runs 14/15 minutes; after the film a 60/67-minute making-of documentary runs.
  Late anime episodes remain 25 minutes, with a naturally short final programme.
  The eight-minute noticeboard at 19:52 leads into a distinct 15-minute local
  news programme at 20:00. Three-hour sport archive replays alternate with
  highlights, including across midnight. Cookery/travel blocks contain individual
  hour-long episodes rather than stretched two/three-hour ordinary episodes.
- Multiple new
  titles are 40–60 characters; long English and German subtitles exercise
  truncation. All gaps are intentional and declared; all other intervals meet.

## Exact capture targets

All times below are **Europe/Vienna local, September 2026**. Daily means 14–16
September, not the partial early-morning tail on the 17th.

| Channel ID (number / name) | Local time | Edge case |
| --- | --- | --- |
| `ch-harbor-sport-plus` (2 / Harbor Sport Plus) | 15 Sep 00:00–17 Sep 06:00 | EPG ends after day 1; 54-hour declared missing tail |
| `ch-coastal-miles` (12 / Coastal Miles) | 15 Sep 14:00–16:00 | Two-hour mid-afternoon missing-data gap |
| `ch-coastal-miles` (12 / Coastal Miles) | Daily 23:00–next day 06:00 | Single 420-minute event crosses midnight |
| `ch-kite-kids`, `ch-kite-junior`, `ch-little-orbit` (21–23) | 14 Sep 00:00–06:00; daily 20:00–next day 06:00 | Real off-air gaps, not placeholder events |
| `ch-kite-kids` (21 / Kite Kids) | Daily 07:00, 07:07, 07:15, 07:35 | 7, 8, 20, 25-minute shorts |
| `ch-skyglass-weather` (7 / Skyglass Weather) | Daily 18:00–18:30 | Six consecutive five-minute weather slots |
| `ch-ion-frame` (25 / Ion Frame) | Daily 20:14, 21:58, 23:05 | Film, making-of documentary, then a 25-minute episode start off-grid |
| `ch-ion-frame` (25 / Ion Frame) | Daily 19:50–20:00 / 20:00–20:14 | Ten-minute drawing short / distinct feature preview; no split short |
| `ch-ridge-cycle` (4 / Ridge Cycle) | Daily 11:30–12:00 / 12:00–17:00 / 17:00–17:30 | Preview / five-hour stage / post-show |
| `ch-northline-news` (5 / Northline News) | Daily 00:15–00:20 / 00:20–01:00 | Five-minute weather then overnight interview review |
| `ch-northline-news` (5 / Northline News) | Daily 19:30–19:50 / 19:50–19:55 / 19:55–20:00 | Main news followed by two short slots |
| `ch-amber-stage` (9 / Amber Stage) | Daily 18:47, 19:52, 20:15, 22:20; 15 Sep 00:40 | Odd access starts, fixed prime-time, late film and overnight film |
| `ch-amber-stage` (9 / Amber Stage) | Daily 19:52–20:00 / 20:00–20:15 | Eight-minute noticeboard / distinct local news bulletin |
| `ch-amber-stage` (9 / Amber Stage) | 14 Sep 20:15–22:20 | 125-minute film, long title: The Clockmaker Who Borrowed the Northern Wind |
| `ch-amber-stage` (9 / Amber Stage) | 15 Sep 20:15–21:00 / 21:00–21:45 | Two episodes of Parcel Office at the Thirteenth Footbridge; both scheduled |
| `ch-ridge-earth` (19 / Ridge Earth) | Daily 18:00–18:45 / 18:45–19:30 / 19:30–21:00 | Two-part documentary followed by a 90-minute feature |
| `ch-harbor-lights` (14 / Harbor Lights) | Daily 00:00–06:00 / 18:00–21:00 | Six-hour music block / three-hour concert |
| `ch-city-desk` (8 / City Desk) | Daily 19:30–19:50 | German main news, Stadtfenster: Der Abendüberblick |
| `ch-foundry-docs` (20 / Foundry Docs) | 14 Sep 02:30–03:25 | Long German episode subtitle, Die Drechselbank: Warum ein winziger Fehler den ganzen Arbeitsablauf verändert |
| `ch-northline-news` (5 / Northline News) | Snapshot 15 Sep 19:40, event 19:30–19:50 | Recording-now DVR entry |
| `ch-harbor-sport` and `ch-river-court` (1 and 3) | 16 Sep 19:00–22:00 | Scheduled one-tuner conflict pair |
| `ch-amber-stage` / `ch-ridge-cycle` (9 / 4) | 14 Sep 20:15–22:20 / 12:00–17:00 | Completed film / failed cycling recording |

## Validation output

Durations and medians are in minutes. `off_grid` counts local starts whose minute
is not 00/15/30/45 (not a claim that all other starts are implausible). `gaps` is
the number of merged declared intervals, not the count of missing events.

```text
ch-harbor-sport         events= 47 min= 30 max=180 median=   90 off_grid=  0 gaps=0
ch-harbor-sport-plus    events= 15 min= 30 max=180 median=   90 off_grid=  0 gaps=1
ch-river-court          events= 47 min= 30 max=180 median=   90 off_grid=  0 gaps=0
ch-ridge-cycle          events= 44 min= 30 max=300 median=   90 off_grid=  0 gaps=0
ch-northline-news       events=291 min=  3 max= 45 median=   12 off_grid=108 gaps=0
ch-northline-world      events=291 min=  3 max= 45 median=   12 off_grid=108 gaps=0
ch-skyglass-weather     events= 76 min=  5 max=360 median=   60 off_grid= 12 gaps=0
ch-city-desk            events=291 min=  3 max= 45 median=   12 off_grid=108 gaps=0
ch-amber-stage          events= 81 min=  8 max=180 median=   50 off_grid= 20 gaps=0
ch-amber-plus           events= 81 min=  8 max=180 median=   50 off_grid= 20 gaps=0
ch-glass-drama          events= 81 min=  8 max=180 median=   50 off_grid= 20 gaps=0
ch-coastal-miles        events= 47 min= 60 max=420 median=   60 off_grid=  0 gaps=1
ch-lumen-live           events= 78 min= 60 max= 60 median=   60 off_grid=  0 gaps=0
ch-harbor-lights        events= 28 min= 60 max=360 median=  180 off_grid=  0 gaps=0
ch-bright-ring          events= 78 min= 60 max= 60 median=   60 off_grid=  0 gaps=0
ch-roundtable           events= 78 min= 60 max= 60 median=   60 off_grid=  0 gaps=0
ch-cedar-world          events= 94 min= 45 max= 90 median=   45 off_grid= 25 gaps=0
ch-tide-watch           events= 94 min= 45 max= 90 median=   45 off_grid= 25 gaps=0
ch-ridge-earth          events= 94 min= 45 max= 90 median=   45 off_grid= 25 gaps=0
ch-foundry-docs         events= 94 min= 45 max= 90 median=   45 off_grid= 25 gaps=0
ch-kite-kids            events=168 min=  7 max= 25 median=   14 off_grid=105 gaps=4
ch-kite-junior          events=168 min=  7 max= 25 median=   14 off_grid=105 gaps=4
ch-little-orbit         events=168 min=  7 max= 25 median=   14 off_grid=105 gaps=4
ch-quiet-hours          events= 28 min= 60 max=360 median=  180 off_grid=  0 gaps=0
ch-ion-frame            events=216 min=  5 max=104 median=   25 off_grid=144 gaps=0
ch-lantern-hour         events=216 min= 10 max=105 median=   25 off_grid=138 gaps=0
ch-second-kettle        events=216 min= 10 max=105 median=   25 off_grid=138 gaps=0
programmes=69 art_programmes=21 events=3210 gaps=14 dvr=8 bytes=1366141
sha256=8288d5bfc06faf076b907cf5367593e04242f6105c8518d5e59d8d1f61f20797
episode_catalogues=27 distinct_subtitles=1014 duration_contracts=69 deliberate_loop_programmes=4
VALIDATION PASSED: 27 channels; complete declared coverage; no overlaps; DVR and realism checks passed
```
