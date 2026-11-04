# 2. Store listing

Owner's README voice, US English, run through the humanizer pass. No dashes in
the copy, "•" bullets as in the app. Lengths are counted in Unicode characters;
limits from <https://support.google.com/googleplay/android-developer/answer/9859152>.
The listing says only what stays true across releases (owner, 2026-10-29), so
a release doesn't need a listing change and a second review: no screen or
view names, no individual features. What changes goes in the release notes
(2.3), which are reviewed with each release anyway.

## 2.1 English (United States), default

**App name** (16/30)

```text
Tvheadend Player
```

**Short description** (59/80)

```text
A Tvheadend client for Android TV, built around the remote.
```

**Full description** (1007/4000)

```text
Tvheadend Player is an Android TV and Google TV app for your own Tvheadend server. Watch live TV, browse the program guide and play your recordings, all with the remote's D-pad.

It's an independent app, not an official Tvheadend product, and it has no channels of its own. You need a Tvheadend 4.3 server that your TV can reach.

• Live TV, the program guide and your recordings in one app
• Made for the remote, built with Google's Material Design for TV
• Live TV keeps playing while you browse
• No ads, no analytics, no account to sign up for

For people who watch through Tvheadend instead of the TV's built-in tuners, the optional Tvheadend Player Appliance app from the GitHub releases makes Tvheadend Player behave like the TV's own tuner app. It starts the app when the TV turns on, catches remote buttons the manufacturer keeps for its own apps, and can replace the home screen.

Open source under the GNU GPL v3, with the source code for every release: github.com/bernhardberger/tvheadend-player
```

## 2.2 German (de-DE)

Informal *du*, as in the app (`docs/translations/de.md`), and the app's own
German terms (TV-Programm, Senderliste, Laufende Aufnahmen, Immersiv,
Raster, Liste).

**App name** (16/30)

```text
Tvheadend Player
```

**Short description** (67/80)

```text
Ein Tvheadend-Client für Android TV, gemacht für die Fernbedienung.
```

**Full description** (1084/4000)

```text
Tvheadend Player ist eine App für Android TV und Google TV für deinen eigenen Tvheadend-Server. Live-TV schauen, im TV-Programm stöbern und Aufnahmen abspielen, alles mit dem Steuerkreuz der Fernbedienung.

Die App ist unabhängig, keine offizielle Tvheadend-App, und bringt keine eigenen Sender mit. Du brauchst einen Tvheadend-4.3-Server, den dein Fernseher erreicht.

• Live-TV, TV-Programm und deine Aufnahmen in einer App
• Gemacht für die Fernbedienung, gebaut mit Googles Material Design für TV
• Live-TV läuft weiter, während du stöberst
• Keine Werbung, keine Analyse, kein Konto zum Anmelden

Wer über Tvheadend statt über die eingebauten Tuner fernsieht, kann mit der optionalen App Tvheadend Player Appliance aus den GitHub-Releases Tvheadend Player wie die eigene Tuner-App des Fernsehers nutzen. Sie startet die App beim Einschalten, fängt Fernbedienungstasten ab, die der Hersteller für seine eigenen Apps reserviert, und kann den Startbildschirm ersetzen.

Open Source unter der GNU GPL v3, mit dem Quellcode zu jeder Version: github.com/bernhardberger/tvheadend-player
```

## 2.3 Release notes

The notes of the coming release live in
[`app/src/play/play/release-notes`](../../app/src/play/play/release-notes), one
`default.txt` per language, up to 500 Unicode characters each
(<https://support.google.com/googleplay/android-developer/answer/9859348>). The
Play upload reads them from there; see the
[release process](../release-process.md#google-play).
