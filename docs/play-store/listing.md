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

**Full description** (665/4000)

```text
Tvheadend Player is an Android TV and Google TV app for your own Tvheadend server. Watch live TV, browse the program guide and play your recordings, all with the remote's D-pad.

It's an independent app, not an official Tvheadend product, and it has no channels of its own. You need a Tvheadend 4.3 server that your TV can reach.

• Live TV, the program guide and your recordings in one app
• Made for the remote, built with Google's Material Design for TV
• Live TV keeps playing while you browse
• No ads, no analytics, no account to sign up for

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

**Full description** (720/4000)

```text
Tvheadend Player ist eine App für Android TV und Google TV für deinen eigenen Tvheadend-Server. Live-TV schauen, im TV-Programm stöbern und Aufnahmen abspielen, alles mit dem Steuerkreuz der Fernbedienung.

Die App ist unabhängig, keine offizielle Tvheadend-App, und bringt keine eigenen Sender mit. Du brauchst einen Tvheadend-4.3-Server, den dein Fernseher erreicht.

• Live-TV, TV-Programm und deine Aufnahmen in einer App
• Gemacht für die Fernbedienung, gebaut mit Googles Material Design für TV
• Live-TV läuft weiter, während du stöberst
• Keine Werbung, keine Analyse, kein Konto zum Anmelden

Open Source unter der GNU GPL v3, mit dem Quellcode zu jeder Version: github.com/bernhardberger/tvheadend-player
```

## 2.3 Release notes

Up to 500 Unicode characters per language, each language between its tags
(<https://support.google.com/googleplay/android-developer/answer/9859348>).
These are the 0.4.2 notes. English 452/500, German 488/500 (counted without the
tags). Paste both into the one release-notes box.

```text
<en-US>
Home and Channels now change more softly: programme info, pictures and the backdrop move together, and holding a key leaves them in place until you let go. A new backdrop starts right away. Cards leaving under the side menu are dimmed and their text is hidden. Right at the end of a Home row stays on the last card. While Home loads you see a spinner instead of a skeleton. All changes: github.com/bernhardberger/tvheadend-player/blob/main/CHANGELOG.md
</en-US>
<de-DE>
Startseite und Sender wechseln jetzt sanfter: Programminfo, Bilder und Hintergrund ändern sich gemeinsam, und eine gehaltene Taste lässt sie stehen, bis du loslässt. Ein neuer Hintergrund startet sofort. Karten unter dem Seitenmenü werden abgedunkelt, ihr Text ausgeblendet. Rechts am Ende einer Reihe bleibt auf der letzten Karte. Beim Laden der Startseite gibt es einen Spinner statt eines Platzhalters. Alle Änderungen: github.com/bernhardberger/tvheadend-player/blob/main/CHANGELOG.md
</de-DE>
```
