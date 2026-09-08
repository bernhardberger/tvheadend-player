# Programme-window B prototype

Status: reversible product experiment using published SDK 0.9.0. Normative
presentation rules are in `tv-design-spec.md`, section 6.2. Disable the experiment
with `-Pplayer.programmeWindowB=false`; no public setting is introduced.

The SDK estimate is schedule-grade, not a precise broadcast timestamp. Network,
server and decoder timing can move apparent programme alignment. Only the SDK's
observed runtime history and subscription-scoped targets authorize a seek.
Player does not estimate server time or create subscriptions for presentation.

## Operator remote sequence

Use an independently authorized test installation with runtime history available.
No physical device is mutated by the offline fixture test.

1. Reveal controls with Up. Initial focus is Play/Pause. Press Up to reach the
   timeline. Center still toggles pause, not seek confirmation.
2. With B 20:00-21:00, live 20:30, playback 20:05 and history beginning 19:50,
   press Left eleven times without settling between presses. The target reaches
   approximately 19:59:30 and the window changes to A 19:00-20:00. The main title
   remains B until sampled playback moves to the dispatched content.
3. Check the thin timeshift-start tick at 19:50. Further rewind must stop at
   runtime history, not the displayed programme start. A held Left accelerates
   existing steps; it never snaps to a programme boundary.
4. Before dispatch, Back cancels preview. Otherwise release the remote and let
   the existing 400ms idle debounce dispatch. Pause remains unchanged by seeking.
5. Press Down from the timeline, then Right through Stop, Info, Record and Settings
   to Go live. Press Center. Go live is independent of the window's right edge.
6. Repeat while paused, with a shallow buffer, missing EPG, and a midnight
   programme. Missing mapping/EPG uses relative history; midnight edges include
   dates. Late metadata must not change the selected media target. If it is
   evicted before dispatch, expect expired-target feedback rather than clamping.

## Executable evidence

`ProgrammeWindowTest` covers scheduled geometry, gaps, missing mapping,
subscription replacement, shallow history, midnight labels, held preview snapshot
stability, late metadata and eviction using the published SDK fixture.
`ProgrammeWindowInputTest` renders production composables and drives the real
preview owner with remote input. It captures initial, preview, settled and live
states for essential/shallow/missing/midnight/paused/held/late/evicted scenarios,
plus compact held/evicted previews, a 1.5 font-scale case, and Go Live while a seek
is still pending. Native repeat
counts 12/13 exercise held acceleration. The essential input uses eleven 30-second
steps; held input uses two 5-minute steps. Assertions protect fixed control/track
positions across preview and feedback, matching visual/accessibility progress,
expired-target announcements, and target-label alignment at mid-window and during
held preview. The fixture deliberately presses Center between preview and settled
captures and asserts the pause toggle; a changed Play/Pause glyph is that input,
not a side effect of seek rejection.
Existing timeline truthfulness, navigation, key-cycle and Back tests retain
recording elapsed/duration and no-capability semantics coverage.

The offline canvas is 1920x1080 at 320dpi, English, font scale 1.0 (1.5 in the
scaled case), UTC and a bright
synthetic backdrop. Generated captures remain ignored. The programme title and
scheduled edges explain the window change; Go live remains reachable without
crossing back through every programme. This is a practical prototype, not proof
that a jumping window is preferable. Physical readability over motion, remote
feel, SurfaceView visibility, overscan and programme-time accuracy are unclaimed.

The full and compact preview share a relative-to-live target readout above the
orange fill edge. Scheduled edges remain clock labels below the track; the readout does
not suggest that estimated broadcast alignment is precise. Focus thickens the
bar and shows a thumb only while focused. Orange follows playback or preview, gray indicates buffered content ahead,
and future remains dark. Only an interior timeshift-start tick remains. Evicted
targets retain subdued readouts and explicit visual/accessibility feedback.
Metadata/readouts overlay above the stable track during preview and rejection,
without empty reserved rows. Runtime history duration is accessible but not a
visible available-duration label; orange progress before the timeshift-start tick
does not grant seek permission.

The lower layout aligns the full-width track above left transport and right
utilities, with Go live above the track and the Channels-down cue below actions.
Hidden and focused previews keep the same track anchor. Each compact channel card
contains its own Now/Next, and the duplicate header
progress strip is removed. The synthetic backdrop intentionally includes a
hard-ended dark rectangle; it is not the production overlay scrim.
